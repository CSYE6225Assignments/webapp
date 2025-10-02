package com.example.healthcheckapi.config;

import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class TestDatabaseCleanup extends AbstractTestExecutionListener {

    @Override
    public void afterTestMethod(TestContext testContext) throws Exception {
        DataSource dataSource = testContext.getApplicationContext().getBean(DataSource.class);
        cleanDatabase(dataSource);
    }

    private void cleanDatabase(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Disable foreign key checks
            stmt.execute("SET FOREIGN_KEY_CHECKS = 0");

            // Get all tables
            ResultSet rs = stmt.executeQuery(
                    "SELECT table_name FROM information_schema.tables " +
                            "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'"
            );

            List<String> tables = new ArrayList<>();
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
            rs.close();

            // Truncate all tables
            for (String table : tables) {
                stmt.execute("TRUNCATE TABLE " + table);
            }

            // Re-enable foreign key checks
            stmt.execute("SET FOREIGN_KEY_CHECKS = 1");

        } catch (Exception e) {
            // Log but don't fail - tables might not exist yet
            System.err.println("Warning: Could not clean database: " + e.getMessage());
        }
    }
}