package org.example.deboardv2.system.monitor.sechdulerMonitor;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.example.deboardv2.system.monitor.query.QueryType;
import org.example.deboardv2.system.monitor.query.RequestContext;
import org.example.deboardv2.system.monitor.query.RequestContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class SchedulerQueryCountAspect {
    private final MeterRegistry meterRegistry;

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object aroundScheduledMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        // 메서드 명 추출
        String methodName = joinPoint.getSignature().toShortString();
        long startTime = System.currentTimeMillis();
        log.info("[SCHEDULER START] {}", methodName);

        RequestContext ctx = RequestContext.builder()
                .httpMethod("SCHEDULER")
                .path(methodName)
                .build();

        RequestContextHolder.init(ctx);
        try {
            return joinPoint.proceed();
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("[SCHEDULER TOTAL TIME] {} - {}ms", methodName, duration);

            RequestContext context = RequestContextHolder.get();
            if (context != null) {
                Map<QueryType, Integer> queryCountsType = context.getQueryCountsType();
                queryCountsType.forEach((queryType, count) -> {
                            recordMetric(context, queryType, count);
                        }
                );
            }
            RequestContextHolder.clear();
        }

    }

    private void recordMetric(RequestContext ctx, QueryType queryType, Integer count) {
        DistributionSummary summary = DistributionSummary.builder("app.query.per_request")
                .description("Number of SQL queries per request")
                .tag("path", ctx.getPath())
                .tag("http_method", ctx.getHttpMethod())
                .tag("query_type", queryType.name())
                .publishPercentiles(0.5, 0.95)
                .register(meterRegistry);

        summary.record(count);
    }
}
