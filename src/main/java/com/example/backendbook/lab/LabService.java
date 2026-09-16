package com.example.backendbook.lab;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("connection-leak")
@RequiredArgsConstructor
@Slf4j
public class LabService {
    private final DataSource dataSource;

    public ConnectionResult checkConnection() throws SQLException {
        long started = System.nanoTime();
        String phase = "커넥션 획득";
        try {
            log.info("1. 커넥션 획득 시도");
            long waitStarted = System.nanoTime();
            double connectionWaitMs;
            int value;
            try (Connection connection = dataSource.getConnection()) {
                connectionWaitMs = elapsedMs(waitStarted);
                log.info("2. 커넥션 획득 완료: connectionWaitMs={}", connectionWaitMs);
                phase = "SQL 실행 및 결과 정리";
                try (Statement statement = connection.createStatement();
                        ResultSet result = statement.executeQuery("SELECT 1")) {
                    if (!result.next()) {
                        throw new SQLException("SELECT 1 결과가 없습니다.");
                    }
                    value = result.getInt(1);
                    log.info("3. SELECT 1 실행 완료: value={}", value);
                }
                phase = "커넥션 반환";
            }
            double totalMs = elapsedMs(started);
            log.info("4. 커넥션 반환 완료: totalMs={}", totalMs);
            return new ConnectionResult(value, connectionWaitMs, totalMs);
        } catch (SQLException exception) {
            log.warn("실패 단계={}; 경과 시간={}ms; 원인={}",
                    phase, elapsedMs(started), exception.getMessage());
            throw exception;
        }
    }

    private double elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000.0;
    }
}
