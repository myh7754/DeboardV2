package org.example.deboardv2.system.monitor.query;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
@AllArgsConstructor
@Builder
public class RequestContext {
    private String httpMethod;
    private String path;
    private final Map<QueryType, Integer> queryCountsType = new HashMap<>();

    //  SQL 실행 시 호출 → QueryType별 카운트 증가
    public void incrementQueryCount(String sql) {
        QueryType type = QueryType.from(sql);
        queryCountsType.merge(type, 1, Integer::sum);
    }
}
