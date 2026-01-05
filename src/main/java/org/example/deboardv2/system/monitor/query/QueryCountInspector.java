package org.example.deboardv2.system.monitor.query;

import org.example.deboardv2.system.monitor.batchQuery.BatchContext;
import org.example.deboardv2.system.monitor.batchQuery.BatchContextHolder;
import org.hibernate.resource.jdbc.spi.StatementInspector;

public class QueryCountInspector implements StatementInspector {
    @Override
    public String inspect(String sql) {
        // HTTP 요청 컨텍스트
        RequestContext requestContext = RequestContextHolder.get();
        if (requestContext != null) {
            requestContext.incrementQueryCount(sql);
        }

        // 배치 컨텍스트
        BatchContext batchContext = BatchContextHolder.get();
        if (batchContext != null) {
            batchContext.incrementQueryCount(sql);
        }

        return sql;
    }
}
