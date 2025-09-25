package org.example.deboardv2.system.logging;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
@Slf4j
public class LoggingAspect {

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

    @Around("execution(* org.example.deboardv2..controller..*(..))")
    public Object logController(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();

        // 메서드명
        String methodName = joinPoint.getSignature().toShortString();

        Object[] args = Arrays.stream(joinPoint.getArgs()) // joinPoint.getArgs() = <statusCode statusCodeName reasonPhrase, body, headers>
                .filter(arg -> !(arg instanceof ServletRequest) && !(arg instanceof ServletResponse))
                .toArray();

        log.debug("Controller-method: {}, args: {}", methodName, Arrays.toString(args));

        try {
            Object result = joinPoint.proceed();
            long endTime = System.currentTimeMillis();
            long ms =  endTime - start;
            log.debug("Controller-method: {}, time: {} ms", methodName, ms);
            return result;
        } catch (Exception e) {
            log.error("Controller-Method: {}, Error: {}",
                    methodName,
                    e.getMessage(), e);
            throw e;
        }
    }
}
