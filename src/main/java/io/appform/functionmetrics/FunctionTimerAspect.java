package io.appform.functionmetrics;

import com.codahale.metrics.Timer;
import com.google.common.base.Stopwatch;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * This aspect ensures that only methods annotated with {@link MonitoredFunction} are measured.
 */
@Aspect
@SuppressWarnings("unused")
public class FunctionTimerAspect {
    private static final Logger log = LoggerFactory.getLogger(FunctionTimerAspect.class.getSimpleName());

    @Pointcut("@annotation(io.appform.functionmetrics.MonitoredFunction)")
    public void monitoredFunctionCalled() {
        //Empty as required
    }

    @Pointcut("execution(* *(..))")
    public void anyFunctionCalled() {
        //Empty as required
    }

    @Around("monitoredFunctionCalled() && anyFunctionCalled()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        final Signature callSignature = joinPoint.getSignature();
        final String className = callSignature.getDeclaringType().getSimpleName();
        final String methodName = callSignature.getName();
        log.trace("Called for class: {} method: {}", className, methodName);
        final FunctionInvocation invocation = new FunctionInvocation(className, methodName);
        Stopwatch stopwatch = Stopwatch.createStarted();
        try {
            Object response = joinPoint.proceed();
            stopwatch.stop();
            FunctionMetricsManager.timer(TimerDomain.SUCCESS, invocation)
                    .ifPresent(timer -> updateTimer(timer, stopwatch));
            return response;
        }
        catch(Throwable t) {
            stopwatch.stop();
            FunctionMetricsManager.timer(TimerDomain.FAILURE, invocation)
                    .ifPresent(timer -> updateTimer(timer, stopwatch));
            throw t;
        }
        finally {
            FunctionMetricsManager.timer(TimerDomain.ALL, invocation)
                    .ifPresent(timer -> updateTimer(timer, stopwatch));
        }
    }

    private void updateTimer(Timer timer, Stopwatch stopwatch) {
        timer.update(stopwatch.elapsed(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS);
    }

}