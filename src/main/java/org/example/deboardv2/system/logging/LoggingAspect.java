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
//    // ProceedingJoinPoint 내부에 결과
//    // excution의 값을 가져다 사용함
//    // execution(* org.example.deboardv2..service..*(..)) =>
//    // excution // 어떤 메서드가 실행되는 지점
//    // (해당 메서드의 반환타입 org.example.deboardv2..service.. 해당메서드시그니처(타입포함)
//    @Around("execution(* org.example.deboardv2..service..*(..))")
//    public Object log(ProceedingJoinPoint joinPoint) throws Throwable {
//        // ProceedingJoinPoint 사용법
////        joinPoint.getSignature().getName() → 메서드 이름
////        joinPoint.getArgs() → 전달된 매개변수 배열
////        joinPoint.getTarget() → 호출된 실제 객체
////        joinPoint.getSignature().toShortString() → 간단한 시그니처
////        joinPoint.getSignature().toLongString() → 자세한 시그니처
//
//        Long start = System.currentTimeMillis();
//        Object result = joinPoint.proceed();
//        Long ms = System.currentTimeMillis() - start;
//        if (ms > 500) log.warn("{} 실행시간 {}ms", joinPoint.getSignature().toShortString(), ms);
//        else log.info("{} 실행시간 {}ms", joinPoint.getSignature().toShortString(), ms);
//        return result;
//    }

//    @Around("execution(* org.example.deboardv2..controller..*(..))")
//    public Object logController(ProceedingJoinPoint joinPoint) throws Throwable {
//        long start = System.currentTimeMillis();
//
//        // 메서드명
//        String methodName = joinPoint.getSignature().toShortString();
//
//        Object[] args = Arrays.stream(joinPoint.getArgs()) // joinPoint.getArgs() = <statusCode statusCodeName reasonPhrase, body, headers>
//                .filter(arg -> !(arg instanceof ServletRequest) && !(arg instanceof ServletResponse))
//                .toArray();
//
//        log.debug("Controller-method: {}, args: {}", methodName, Arrays.toString(args));
//
//        try {
//            Object result = joinPoint.proceed();
//            long endTime = System.currentTimeMillis();
//            long ms =  endTime - start;
//            log.debug("Controller-method: {}, time: {} ms", methodName, ms);
//            return result;
//        } catch (Exception e) {
//            log.error("Controller-Method: {}, Error: {}",
//                    methodName,
//                    e.getMessage(), e);
//            throw e;
//        }
//    }

// Controller는 요청/응답 로깅, Service는 실행 시간만 로깅
// Controller는 요청/응답 로깅, Service는 실행 시간만 로깅
    @Pointcut("execution(* org.example.deboardv2..controller..*(..))")
    public void controllerLayer() {}

    @Pointcut("execution(* org.example.deboardv2..service..*(..))")
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
        log.info("[{}] START {} {} {} params={}",
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
                log.info("[{}] END {} - {}ms", traceId, method, duration);
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
