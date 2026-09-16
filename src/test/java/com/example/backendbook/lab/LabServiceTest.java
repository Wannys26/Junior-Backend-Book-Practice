package com.example.backendbook.lab;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LabServiceTest {
    private HikariDataSource createPool() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(System.getenv("PRACTICE_DB_URL"));
        config.setUsername(System.getenv("PRACTICE_DB_USERNAME"));
        config.setPassword(System.getenv("PRACTICE_DB_PASSWORD"));
        config.setMaximumPoolSize(2);
        config.setConnectionTimeout(5000);
        return new HikariDataSource(config);
    }

    @Test
    void returnsConnectionsSoThreeSequentialCallsSucceed() throws Exception {
        try (HikariDataSource pool = createPool()) {
            LabService service = new LabService(pool);
            for (int request = 0; request < 3; request++) {
                ConnectionResult result = service.checkConnection();
                assertEquals(1, result.getValue());
                assertTrue(result.getTotalMs() >= result.getConnectionWaitMs());
                assertEquals(0, pool.getHikariPoolMXBean().getActiveConnections());
            }
        }
    }

    @Test
    void measuresTimeSpentWaitingForAnAvailableConnection() throws Exception {
        try (HikariDataSource pool = createPool();
                var worker = Executors.newSingleThreadExecutor();
                Connection first = pool.getConnection()) {
            Connection second = pool.getConnection();
            try {
                var pending = worker.submit(new LabService(pool)::checkConnection);
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (pool.getHikariPoolMXBean().getThreadsAwaitingConnection() == 0
                        && System.nanoTime() < deadline) {
                    Thread.sleep(5);
                }
                assertEquals(1, pool.getHikariPoolMXBean().getThreadsAwaitingConnection());
                Thread.sleep(100);
                second.close();

                ConnectionResult result = pending.get(5, TimeUnit.SECONDS);
                assertEquals(1, result.getValue());
                assertTrue(result.getConnectionWaitMs() >= 80, "풀 대기가 측정값에 포함되어야 합니다.");
                assertTrue(result.getTotalMs() >= result.getConnectionWaitMs());
                assertEquals(1, pool.getHikariPoolMXBean().getActiveConnections());
            } finally {
                second.close();
            }
        }
    }
}
