package org.example.deboardv2.system.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.example.deboardv2.system.config.TraceIdFilter;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.*;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class LoggingAspect {

    private final ObjectMapper objectMapper; // java , json 직렬화 역직렬화 도구
// Controller는 요청/응답 로깅, Service는 실행 시간만 로깅
    @Pointcut("execution(* org.example.deboardv2..controller..*(..))")
    public void controllerLayer() {}

    @Pointcut("within(org.example.deboardv2..service..*)")
    public void serviceLayer() {}

    // Controller 로깅: 입력 파라미터 포함
    @Around("controllerLayer()")
    public Object logController(ProceedingJoinPoint joinPoint) throws Throwable {
        String traceId = org.slf4j.MDC.get(TraceIdFilter.TRACE_ID);
        String method = joinPoint.getSignature().toShortString();
        HttpServletRequest request =
                ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        // 파라미터 로깅
        String params = formatParams(joinPoint.getArgs());
        log.debug("[{}] START {} {} {} params={}",
                traceId,
                request.getMethod(),
                request.getRequestURI(),
                method,
                params);

        long startTime = System.currentTimeMillis();

            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;

            // 200ms 이상 걸린 요청만 WARN으로 기록
            if (duration > 500) {
                log.warn("[{}] SLOW {} - {}ms", traceId, method, duration);
            } else {
                log.debug("[{}] END {} - {}ms", traceId, method, duration);
            }

            return result;
    }

    private String formatParams(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }

        try {
            // HttpServletRequest/Response 같은 불필요한 객체 제외
            List<Object> filtered = Arrays.stream(args)
                    .filter(arg -> arg != null)
                    .filter(arg -> !(arg instanceof HttpServletRequest))
                    .filter(arg -> !(arg instanceof HttpServletResponse))
                    .filter(arg -> !(arg instanceof Authentication))
                    .toList();

            return objectMapper.writeValueAsString(filtered);
        } catch (Exception e) {
            return "[serialize error]";
        }
    }

    // Service 로깅: 성능 측정 중심
    @Around("serviceLayer()")
    public Object logService(ProceedingJoinPoint joinPoint) throws Throwable {
        String traceId = org.slf4j.MDC.get(TraceIdFilter.TRACE_ID);
        String method = joinPoint.getSignature().toShortString();

        long startTime = System.currentTimeMillis();

            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;

            // 500ms 이상 걸린 서비스 로직만 로깅
            if (duration > 500) {
                log.warn("[{}] SLOW SERVICE {} - {}ms", traceId, method, duration);
            }

            return result;
    }

}
