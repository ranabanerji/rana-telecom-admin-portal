package com.example.telecom;

import org.flywaydb.core.Flyway;

/** Run backend-owned migrations during setup, without starting the server. */
public final class DatabaseMigration {
    private DatabaseMigration() {
    }

    public static void main(String[] args) {
        String url = System.getenv("TELECOM_JDBC_URL");
        if (url == null) {
            url = "jdbc:sqlite:app.db?foreign_keys=true&date_class=INTEGER&date_precision=MILLISECONDS";
        }
        Flyway flyway = Flyway.configure().dataSource(url, null, null).load();
        // Only for a separately converted and verified legacy copy; never automatic.
        if (Boolean.getBoolean("telecom.adoptLegacy")) {
            flyway.baseline();
        }
        flyway.migrate();
    }
}
