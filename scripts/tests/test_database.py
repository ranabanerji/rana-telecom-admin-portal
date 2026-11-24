import contextlib
import importlib.util
import io
import os
import sqlite3
import tempfile
import time
import unittest
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def module(name, file):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts' / file)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


migration = module('migration', 'migrate-legacy-database.py')
seed = module('seed', 'seed-database.py')
SCHEMA = migration.SCHEMA.read_text()


class MigrationTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.source = Path(self.directory.name) / 'source.db'
        self.target = Path(self.directory.name) / 'converted.db'
        with sqlite3.connect(self.source) as conn:
            # Legacy SQLite permits mixed timestamp storage regardless of declared affinity.
            conn.executescript(SCHEMA)
            conn.execute("INSERT INTO plans(id,name,monthly_price) VALUES(1,'Basic',20)")
            conn.execute("INSERT INTO customers(id,first_name,last_name,email,phone,plan_id,created_at) VALUES(1,'A','B','a@b.com','555',1,0)")
            for instant in ['2026-09-30 08:00:00.000', 1790771696123, '2026-10-01 07:59:59.999', '2026-10-01 08:00:00.000']:
                conn.execute("INSERT INTO usage_records(customer_id,type,quantity,cost,recorded_at) VALUES(1,'SMS',1,1,?)", (instant,))
            conn.execute("INSERT INTO support_tickets(customer_id,subject,priority,status,created_at,resolved_at) VALUES(1,'Old','MEDIUM','OPEN','2026-09-30 08:00:00.123',NULL)")

    def test_inventory_conversion_order_bounds_original_preserved_and_idempotency(self):
        original_bytes = self.source.read_bytes()
        with sqlite3.connect(self.source) as conn:
            self.assertEqual(migration.inventory(conn)['usage_records']['recorded_at'], {'integer': 1, 'text': 3})
        migration.convert(self.source, self.target, 'Asia/Singapore')
        self.assertEqual(self.source.read_bytes(), original_bytes)
        with sqlite3.connect(self.target) as conn:
            self.assertEqual(migration.inventory(conn)['usage_records']['recorded_at'], {'integer': 4})
            start, end = 1790726400000, 1790812800000  # Sep 30 / Oct 1, UTC
            self.assertEqual(conn.execute('SELECT count(*) FROM usage_records WHERE recorded_at>=? AND recorded_at<?', (start, end)).fetchone()[0], 3)
            self.assertEqual(conn.execute('SELECT recorded_at FROM usage_records ORDER BY recorded_at DESC LIMIT 1').fetchone()[0], end)
            self.assertEqual(conn.execute('SELECT created_at FROM support_tickets').fetchone()[0], start + 123)
            self.assertEqual(conn.execute('PRAGMA foreign_key_check').fetchall(), [])
        again = self.target.with_name('again.db')
        migration.convert(self.target, again)
        with sqlite3.connect(self.target) as first, sqlite3.connect(again) as second:
            for table in migration.TABLES:
                self.assertEqual(first.execute(f'SELECT * FROM {table}').fetchall(), second.execute(f'SELECT * FROM {table}').fetchall())
        with self.assertRaises(FileExistsError):
            migration.convert(self.source, self.target, 'UTC')

    def test_missing_timezone_leaves_no_partial_output_and_source_intact(self):
        original_bytes = self.source.read_bytes()
        with self.assertRaisesRegex(ValueError, 'requires --source-timezone'):
            migration.convert(self.source, self.target)
        self.assertFalse(self.target.exists())
        self.assertEqual(self.source.read_bytes(), original_bytes)

    def test_orphans_fail_without_dropping_rows_or_touching_source(self):
        with sqlite3.connect(self.source) as conn:
            conn.execute('DELETE FROM customers')
        with self.assertRaises(sqlite3.IntegrityError):
            migration.convert(self.source, self.target, 'UTC')
        self.assertFalse(self.target.exists())
        with sqlite3.connect(self.source) as conn:
            self.assertEqual(conn.execute('SELECT count(*) FROM usage_records').fetchone()[0], 4)

    def test_ambiguous_and_nonexistent_local_times_are_not_guessed(self):
        for instant in ['2026-11-01 01:30:00', '2026-03-08 02:30:00']:
            with self.assertRaisesRegex(ValueError, 'Ambiguous or nonexistent'):
                migration.epoch(instant, 'America/New_York')
        self.assertEqual(migration.epoch('2026-09-30T00:00:00.123+00:00', None), 1790726400123)
        self.assertEqual(migration.epoch(1790726400123, None), 1790726400123)
        self.assertIsNone(migration.epoch(None, None))


class SeederTests(unittest.TestCase):
    def generate(self, zone):
        previous = os.environ.get('TZ')
        try:
            os.environ['TZ'] = zone
            time.tzset()
            seed.random.seed(seed.SEED)
            seed.Faker.seed(seed.SEED)
            seed.SEED_NOW = datetime(2026, 11, 24, 0, 0, 0, 123000, tzinfo=timezone.utc)
            with sqlite3.connect(':memory:') as conn, contextlib.redirect_stdout(io.StringIO()):
                conn.executescript(SCHEMA)
                conn.execute('PRAGMA foreign_keys=ON')
                plans = seed.seed_plans(conn)
                customers = seed.seed_customers(conn, plans, count=12)
                seed.seed_devices(conn, customers, count=15)
                seed.seed_usage_records(conn, customers, days=2)
                seed.seed_support_tickets(conn, customers, count=30)
                now = seed.to_sqlite_timestamp(seed.SEED_NOW)
                for table, columns in migration.TIMESTAMPS.items():
                    for column in columns:
                        self.assertEqual(conn.execute(f'SELECT count(*) FROM {table} WHERE {column}>?', (now,)).fetchone()[0], 0)
                        self.assertEqual(conn.execute(f"SELECT count(*) FROM {table} WHERE {column} IS NOT NULL AND typeof({column})!='integer'").fetchone()[0], 0)
                return {table: conn.execute(f'SELECT * FROM {table} ORDER BY id').fetchall() for table in migration.TABLES}
        finally:
            if previous is None:
                os.environ.pop('TZ', None)
            else:
                os.environ['TZ'] = previous
            time.tzset()

    def test_deterministic_utc_across_host_timezones_and_never_future(self):
        self.assertEqual(self.generate('UTC'), self.generate('Asia/Singapore'))

    def test_naive_seed_timestamp_rejected(self):
        with self.assertRaises(ValueError):
            seed.to_sqlite_timestamp(datetime(2026, 11, 24))


if __name__ == '__main__':
    unittest.main()
