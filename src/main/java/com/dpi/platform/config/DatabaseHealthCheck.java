package com.dpi.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Startup health check that verifies the database connection is alive
 * and reports table count + connection metadata to the log.
 *
 * Runs once after the Spring context is fully initialized.
 */
@Component
public class DatabaseHealthCheck {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHealthCheck.class);

    private final DataSource dataSource;
    private final Environment environment;

    public DatabaseHealthCheck(DataSource dataSource, Environment environment) {
        this.dataSource = dataSource;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifyDatabaseConnection() {
        String activeProfile = String.join(", ", environment.getActiveProfiles());
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║             DPIP - Database Connection Health Check         ║");
        log.info("╚══════════════════════════════════════════════════════════════╝");
        log.info("Active Profile: {}", activeProfile.isEmpty() ? "default" : activeProfile);

        try (Connection conn = dataSource.getConnection()) {
            // Create agent_message table if not exists (ddl-auto=validate is active, so we create table programmatically)
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS public.agent_message (" +
                    "  id UUID PRIMARY KEY," +
                    "  task_id UUID NOT NULL," +
                    "  role VARCHAR(50) NOT NULL," +
                    "  content TEXT NOT NULL," +
                    "  created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL," +
                    "  CONSTRAINT fk_agent_message_task FOREIGN KEY (task_id) REFERENCES public.tasks(id) ON DELETE CASCADE" +
                    ")"
                );
            }

            String url = conn.getMetaData().getURL();
            String maskedUrl = maskPassword(url);
            log.info("JDBC URL: {}", maskedUrl);
            log.info("Database: {} {}", conn.getMetaData().getDatabaseProductName(),
                    conn.getMetaData().getDatabaseProductVersion());
            log.info("Driver:   {} {}", conn.getMetaData().getDriverName(),
                    conn.getMetaData().getDriverVersion());

            // Verify connectivity with SELECT 1
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {
                if (rs.next()) {
                    log.info("Ping:     SELECT 1 = {} ✓", rs.getInt(1));
                }
            }

            // Count tables in the public schema
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.tables " +
                     "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'")) {
                if (rs.next()) {
                    int tableCount = rs.getInt(1);
                    log.info("Tables:   {} table(s) in 'public' schema", tableCount);

                    if (tableCount == 0) {
                        log.warn("⚠ No tables found. Run the Supabase migration or set ddl-auto=update.");
                    }
                }
            }

            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("✅ Database connection verified successfully!");
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        } catch (Exception e) {
            log.error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.error("❌ DATABASE CONNECTION FAILED!");
            log.error("Error: {}", e.getMessage());
            log.error("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.error("Troubleshooting:");
            log.error("  1. Check environment variables: SUPABASE_DB_URL, SUPABASE_DB_USERNAME, SUPABASE_DB_PASSWORD");
            log.error("  2. Verify Supabase project is ACTIVE at https://supabase.com/dashboard");
            log.error("  3. Ensure your IP is not blocked by Supabase network restrictions");
            log.error("  4. Run: .\\set-supabase-env.ps1  then restart the application");
        }
    }

    /**
     * Masks password parameters in JDBC URLs for safe logging.
     */
    private String maskPassword(String url) {
        if (url == null) return "N/A";
        return url.replaceAll("password=[^&]*", "password=*****");
    }
}
