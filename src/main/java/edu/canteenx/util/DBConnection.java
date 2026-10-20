package edu.canteenx.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Reads credentials from environment; defaults target a local development database. */
public final class DBConnection {
    private DBConnection() {}
    public static Connection getConnection() throws SQLException {
        String url = env("CANTEENX_DB_URL", "jdbc:mysql://localhost:3306/canteenx?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
        return DriverManager.getConnection(url, env("CANTEENX_DB_USER", "root"), env("CANTEENX_DB_PASSWORD", ""));
    }
    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
