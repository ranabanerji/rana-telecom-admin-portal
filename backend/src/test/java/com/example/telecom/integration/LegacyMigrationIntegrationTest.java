package com.example.telecom.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Retained mixed-format copy -> explicit timezone conversion -> Flyway ->
 * actual API/JPA.
 */
@SpringBootTest
class LegacyMigrationIntegrationTest {
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;
    private MockMvc mvc;

    @DynamicPropertySource
    static void convertedDatabase(DynamicPropertyRegistry registry) throws Exception {
        Path directory = Files.createTempDirectory("telecom-legacy-test-");
        Path source = directory.resolve("legacy.db");
        Path target = directory.resolve("converted.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + source);
                var statement = connection.createStatement()) {
            String schema = Files.readString(Path.of("src/main/resources/db/migration/V1__baseline.sql"));
            for (String sql : schema.split(";"))
                if (!sql.isBlank())
                    statement.execute(sql);
            statement.execute("INSERT INTO plans(id,name,monthly_price) VALUES(1,'Basic',20)");
            statement.execute(
                    "INSERT INTO customers(id,first_name,last_name,email,phone,plan_id,created_at) VALUES(1,'Ada','Lovelace','ada@example.com','555',1,'2026-09-30 08:00:00.000')");
            statement.execute(
                    "INSERT INTO usage_records(customer_id,type,quantity,cost,recorded_at) VALUES(1,'SMS',1,1,'2026-09-30 08:00:00.000'),(1,'SMS',1,1,1790771696123),(1,'SMS',1,1,'2026-10-01 07:59:59.999'),(1,'SMS',1,1,'2026-10-01 08:00:00.000')");
            statement.execute(
                    "INSERT INTO support_tickets(customer_id,subject,priority,status,created_at) VALUES(1,'Legacy','MEDIUM','OPEN','2026-09-30 08:00:00.123')");
        }
        byte[] original = Files.readAllBytes(source);
        Process process = new ProcessBuilder("python3", "../scripts/migrate-legacy-database.py", source.toString(),
                "--target", target.toString(), "--source-timezone", "Asia/Singapore").redirectErrorStream(true).start();
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Legacy converter did not finish");
        assertEquals(0, process.exitValue(),
                new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        assertArrayEquals(original, Files.readAllBytes(source), "Source must be preserved");
        String url = "jdbc:sqlite:" + target + "?foreign_keys=true&date_class=INTEGER&date_precision=MILLISECONDS";
        Flyway flyway = Flyway.configure().dataSource(url, null, null).load();
        flyway.baseline(); // Explicit adoption only after a successful conversion into a separate copy.
        flyway.migrate();
        registry.add("spring.datasource.url", () -> url);
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void convertedMixedStorageFiltersAndOrdersLikeCanonicalSqlAndApiWrites() throws Exception {
        assertEquals(1, jdbc.queryForObject("PRAGMA foreign_keys", Integer.class));
        assertEquals(4, jdbc.queryForObject("SELECT count(*) FROM usage_records WHERE typeof(recorded_at)='integer'",
                Integer.class));
        mvc.perform(get("/api/usage").param("dateFrom", "2026-09-30").param("dateTo", "2026-09-30"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].recorded_at").value("2026-09-30T23:59:59.999Z"));
        assertEquals(3, jdbc.queryForObject(
                "SELECT count(*) FROM usage_records WHERE recorded_at>=1790726400000 AND recorded_at<1790812800000",
                Integer.class));
        mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customerId\":1,\"subject\":\"API ticket\",\"priority\":\"MEDIUM\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/tickets").param("size", "1"))
                .andExpect(jsonPath("$.content[0].subject").value("API ticket"));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM support_tickets WHERE typeof(created_at)='integer'",
                Integer.class));
        mvc.perform(get("/api/tickets").param("size", "100"))
                .andExpect(jsonPath("$.content[1].created_at").value("2026-09-30T00:00:00.123Z"));
    }
}
