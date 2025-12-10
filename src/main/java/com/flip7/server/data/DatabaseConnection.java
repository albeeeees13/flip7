package com.flip7.server.data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConnection {

    // Archivo local de SQLite
    private static final String URL = "jdbc:sqlite:flip7.db";

    public static Connection connect() {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(URL);
        } catch (SQLException e) {
            System.out.println("Error fatal de BD: " + e.getMessage());
        }
        return conn;
    }

    public static void inicializarBD() {
        String sql = "CREATE TABLE IF NOT EXISTS usuarios (\n"
                + " id INTEGER PRIMARY KEY AUTOINCREMENT,\n"
                + " username TEXT NOT NULL UNIQUE,\n"
                + " password TEXT NOT NULL\n"
                + ");";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("Base de datos lista: flip7.db");
        } catch (SQLException e) {
            System.out.println("Error inicializando BD: " + e.getMessage());
        }
    }
}