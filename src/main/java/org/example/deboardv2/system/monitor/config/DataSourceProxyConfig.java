package org.example.deboardv2.system.monitor.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.example.deboardv2.system.monitor.batchQuery.BatchContext;
import org.example.deboardv2.system.monitor.batchQuery.BatchContextHolder;
import org.example.deboardv2.system.monitor.query.RequestContext;
import org.example.deboardv2.system.monitor.query.RequestContextHolder;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.List;

@Slf4j
@Configuration
public class DataSourceProxyConfig {

    // spring.datasource.hikari.* 설정(maximum-pool-size 등)을 HikariDataSource에 바인딩
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public HikariDataSource hikariDataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    /*
     * 모든 쿼리를 interceptor하는 DataSource Proxy
     * - Hibernate 쿼리
     * - JDBC Template 쿼리
     * - 배치 작업 쿼리 */
    @Bean
    @Primary
    public DataSource dataSource(HikariDataSource hikariDataSource) {
        return ProxyDataSourceBuilder
                .create(hikariDataSource)
                .name("query-monitoring-proxy")
                .listener(new QueryCountExecutionListener())
                .build();
    }

    /*
     * 쿼리 실행 리스너
     * - RequestContext (HTTP/Scheduler) 또는 BatchContext에 쿼리 카운트 기록
     * - 비동기 스레드에서도 ThreadLocal을 통해 정확히 추적 */
    public static class QueryCountExecutionListener implements QueryExecutionListener {

        @Override
        public void beforeQuery(ExecutionInfo executionInfo, List<QueryInfo> list) {

        }

        @Override
        public void afterQuery(ExecutionInfo executionInfo, List<QueryInfo> queryInfoList) {
            // executionInfo에서도 유용한 정보를 뽑을 수 있습니다.
            for (QueryInfo queryInfo : queryInfoList){
                String sql = queryInfo.getQuery();
                RequestContext requestContext = RequestContextHolder.get();
                if (requestContext != null) {
                    requestContext.incrementQueryCount(sql);
                }

                // BatchContext에 기록
                BatchContext batchContext = BatchContextHolder.get();
                if (batchContext != null) {
                    batchContext.incrementQueryCount(sql);
                }
            }
        }
    }
}
