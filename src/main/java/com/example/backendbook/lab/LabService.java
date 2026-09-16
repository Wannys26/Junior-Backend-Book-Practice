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
    // hikari cp가 관리하는 커넥션 풀에서 db 연결을 빌리는데 사용함
    private final DataSource dataSource;

    public ConnectionResult checkConnection() throws SQLException {
        // 서비스 시작 시간 기록
        long started = System.nanoTime();
        String phase = "커넥션 획득";
        try {
            log.info("1. 커넥션 획득 시도");
            long waitStarted = System.nanoTime();
            double connectionWaitMs;
            int value;
            // 풀에서 연결을 빌리며, 여유가 없으면 설정된 시간만큼 기다림
            // 정상 반환 코드를 주석 처리하고, 자동 반환 없이 연결만 빌리는 실험임
            // try (Connection connection = dataSource.getConnection()) {
            {
                Connection connection = dataSource.getConnection();
                connectionWaitMs = elapsedMs(waitStarted);
                log.info("2. 커넥션 획득 완료: connectionWaitMs={}", connectionWaitMs);
                phase = "SQL 실행 및 결과 정리";
                // 테이블 변경 없이 DB 연결이 동작하는지만 확인하는 SQL임
                try (Statement statement = connection.createStatement();
                        ResultSet result = statement.executeQuery("SELECT 1")) {
                    if (!result.next()) {
                        throw new SQLException("SELECT 1 결과가 없습니다.");
                    }
                    value = result.getInt(1);
                    log.info("3. SELECT 1 실행 완료: value={}", value);
                }
            }
            // 이번 실험에서는 연결 반환 없이 여기까지 걸린 시간을 측정함
            double totalMs = elapsedMs(started);
            log.info("4. 실습용 커넥션 반환 누락: totalMs={}", totalMs);
            return new ConnectionResult(value, connectionWaitMs, totalMs);
        } catch (SQLException exception) {
            // 풀이 고갈되면 SQL 실행 전인 커넥션 획득 단계에서 실패했음을 확인할 수 있음
            log.warn("실패 단계={}; 경과 시간={}ms; 원인={}",
                    phase, elapsedMs(started), exception.getMessage());
            throw exception;
        }
    }

    // 시작 시점부터 현재까지의 경과 시간을 밀리초로 계산함
    private double elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000.0;
    }
}
