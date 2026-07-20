package smally.server.core.logging;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import smally.server.core.aop.ExecutionTimeLog;

@Aspect
@Component
@Slf4j
public class LoggingServiceImpl{

    @Around("@annotation(executionTimeLog)")
    public Object executionTime(ProceedingJoinPoint joinPoint, ExecutionTimeLog executionTimeLog) throws Throwable {
        long startTime = System.currentTimeMillis();

        try {
            return joinPoint.proceed();
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.info("{} 실행 시간: {}ms", executionTimeLog.value(), duration);
        }

    }
}
