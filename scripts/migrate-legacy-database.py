#!/usr/bin/env python3
"""Inventory legacy storage, or convert into a NEW database; never alter the source."""
import argparse
import json
import sqlite3
from datetime import datetime, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

SCHEMA = Path(__file__).resolve().parents[1] / 'backend/src/main/resources/db/migration/V1__baseline.sql'
TIMESTAMPS = {
    'customers': ('activated_at', 'created_at'),
    'devices': ('assigned_at', 'created_at'),
    'usage_records': ('recorded_at',),
    'support_tickets': ('created_at', 'resolved_at'),
}
TABLES = ('plans', 'customers', 'devices', 'usage_records', 'support_tickets')


def inventory(conn):
    return {table: {column: dict(conn.execute(
        f'SELECT typeof({column}), count(*) FROM {table} GROUP BY typeof({column})'
    )) for column in columns} for table, columns in TIMESTAMPS.items()}


def epoch(value, source_timezone):
    if value is None or isinstance(value, int):
        return value
    if not isinstance(value, str):
        raise ValueError(f'Unsupported timestamp storage: {type(value).__name__}')
    parsed = datetime.fromisoformat(value)
    if parsed.tzinfo is None:
        if source_timezone is None:
            raise ValueError('Timezone-naive text requires --source-timezone; do not guess')
        zone = ZoneInfo(source_timezone)
        early = parsed.replace(tzinfo=zone, fold=0)
        late = parsed.replace(tzinfo=zone, fold=1)
        if early.utcoffset() != late.utcoffset():
            raise ValueError(f'Ambiguous or nonexistent local time: {value}; resolve explicitly first')
        parsed = early
    return int(parsed.astimezone(timezone.utc).timestamp() * 1000)


def convert(source, target, source_timezone=None):
    """Copy rows transactionally using the authoritative schema, verifying references."""
    source, target = Path(source), Path(target)
    # Exclusive creation prevents overwriting the source or any retained target.
    with target.open('xb'):
        pass
    try:
        with sqlite3.connect(f'{source.resolve().as_uri()}?mode=ro', uri=True) as original, sqlite3.connect(target) as output:
            original.execute('BEGIN')  # Consistent read snapshot, including when WAL is used.
            extra_tables = {row[0] for row in original.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")} - set(TABLES) - {'flyway_schema_history'}
            if extra_tables:
                raise ValueError(f'Unrecognized tables must be handled explicitly: {sorted(extra_tables)}')
            output.execute('PRAGMA foreign_keys=ON')
            output.executescript(SCHEMA.read_text())
            output.execute('BEGIN')
            for table in TABLES:
                rows = original.execute(f'SELECT * FROM {table} ORDER BY id')
                columns = [column[0] for column in rows.description]
                indexes = [columns.index(column) for column in TIMESTAMPS.get(table, ())]
                def converted_rows(rows, indexes):
                    for row in rows:
                        values = list(row)
                        for index in indexes:
                            values[index] = epoch(values[index], source_timezone)
                        yield values
                output.executemany(
                    f'INSERT INTO {table} ({",".join(columns)}) VALUES ({",".join("?" for _ in columns)})',
                    converted_rows(rows, indexes),
                )
                if original.execute(f'SELECT count(*) FROM {table}').fetchone() != output.execute(f'SELECT count(*) FROM {table}').fetchone():
                    raise ValueError(f'Row count changed in {table}')
            if output.execute('PRAGMA foreign_key_check').fetchall():
                raise ValueError('Broken references; repair the source copy explicitly before conversion')
            output.commit()
    except BaseException:
        target.unlink(missing_ok=True)
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('--target', type=Path)
    parser.add_argument('--source-timezone', help='Confirmed IANA timezone of legacy naive text, e.g. Asia/Singapore')
    args = parser.parse_args()
    with sqlite3.connect(f'{args.source.resolve().as_uri()}?mode=ro', uri=True) as conn:
        print(json.dumps(inventory(conn), indent=2))
    if args.target:
        convert(args.source, args.target, args.source_timezone)
        print(f'Converted copy: {args.target}. Original preserved. Verify before explicit Flyway baseline adoption.')


if __name__ == '__main__':
    main()
