package com.dpi.platform;

import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Disabled;

public class DatabaseConnectionTest {

    @Test
    @Disabled("Integration test requiring active external Supabase PostgreSQL connection")
    public void testSupabaseConnection() {
        String envPassword = System.getenv("SUPABASE_DB_PASSWORD");
        String url = System.getenv("SUPABASE_DB_URL");
        String user = System.getenv("SUPABASE_DB_USERNAME");

        if (envPassword == null || envPassword.isBlank() || url == null || url.isBlank()) {
            System.out.println("DEBUG: SUPABASE_DB_PASSWORD or SUPABASE_DB_URL not set. Skipping live external connection test.");
            return;
        }

        if (user == null || user.isBlank()) {
            user = "postgres";
        }

        System.out.println("DEBUG: Testing connection to: " + url.replaceAll("password=[^&]*", "password=*****") + " (User: " + user + ")");
        try (Connection conn = DriverManager.getConnection(url, user, envPassword)) {
            System.out.println("DEBUG: >>> SUCCESS connecting to Supabase database! <<<");
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT version(), current_user")) {
                if (rs.next()) {
                    System.out.println("DEBUG: Postgres Version: " + rs.getString(1));
                    System.out.println("DEBUG: Current User: " + rs.getString(2));
                }
            }
        } catch (Exception e) {
            System.out.println("DEBUG: FAILED connecting to Supabase database: " + e.getMessage());
            org.junit.jupiter.api.Assertions.fail("Database connection failed: " + e.getMessage(), e);
        }
    }

    @Test
    @Disabled("Integration test requiring active external Supabase PostgreSQL connection")
    public void testDatabaseOperations() {
        String password = System.getenv("SUPABASE_DB_PASSWORD");
        String url = System.getenv("SUPABASE_DB_URL");
        String user = System.getenv("SUPABASE_DB_USERNAME");

        if (password == null || password.isBlank() || url == null || url.isBlank()) {
            System.out.println("DEBUG: SUPABASE_DB_PASSWORD or SUPABASE_DB_URL not set. Skipping live external DDL test.");
            return;
        }

        if (user == null || user.isBlank()) {
            user = "postgres";
        }

        System.out.println("DEBUG: Testing DDL and CRUD operations on Supabase PostgreSQL...");
        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {

            // 1. Create a verification table
            stmt.execute("CREATE TABLE IF NOT EXISTS _dpip_connection_check (id SERIAL PRIMARY KEY, check_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, message TEXT);");
            System.out.println("DEBUG: Table _dpip_connection_check created or exists.");

            // 2. Insert verification row
            stmt.execute("INSERT INTO _dpip_connection_check (message) VALUES ('Supabase connection verified successfully');");
            System.out.println("DEBUG: Inserted verification record.");

            // 3. Query verification row
            try (ResultSet rs = stmt.executeQuery("SELECT id, check_time, message FROM _dpip_connection_check ORDER BY id DESC LIMIT 1;")) {
                if (rs.next()) {
                    System.out.println("DEBUG: Verified record -> ID: " + rs.getInt("id") + ", Time: " + rs.getTimestamp("check_time") + ", Message: " + rs.getString("message"));
                }
            }

            // 4. Clean up test table
            stmt.execute("DROP TABLE _dpip_connection_check;");
            System.out.println("DEBUG: Table _dpip_connection_check cleaned up successfully.");

        } catch (Exception e) {
            System.out.println("DEBUG: Database operation failed: " + e.getMessage());
            org.junit.jupiter.api.Assertions.fail("Database operation failed: " + e.getMessage(), e);
        }
    }
}
