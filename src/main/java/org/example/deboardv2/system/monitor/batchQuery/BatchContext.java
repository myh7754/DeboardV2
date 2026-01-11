package org.example.deboardv2.system.monitor.batchQuery;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.example.deboardv2.system.monitor.query.QueryType;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Getter
@AllArgsConstructor
public class BatchContext {
    private final BatchName batchName;
    private final LocalDateTime startTime;
    private final Map<QueryType, Integer> queryCountByType = new ConcurrentHashMap<>();

    // SQL 실행 시 호출 → QueryType별 카운트 증가
    public void incrementQueryCount(String sql) {
        QueryType type = QueryType.from(sql);
        queryCountByType.merge(type, 1, Integer::sum);
    }

    // 배치 종료 시 로그 호출
    public void log() {
        long executionTime = ChronoUnit.MILLIS.between(startTime, LocalDateTime.now());

        StringBuilder sb = new StringBuilder("\n");
        sb.append("========================================\n");
        sb.append("# Batch Query Count Report\n");
        sb.append("- Batch Name: ").append(batchName).append("\n");
        sb.append("- Execution Time(ms): ").append(executionTime).append("\n");
        sb.append("- Query Statistics:\n");
        queryCountByType.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append(String.format("  - %-7s: %d\n", e.getKey(), e.getValue())));
        sb.append("========================================\n");

        log.info(sb.toString());
    }
}
