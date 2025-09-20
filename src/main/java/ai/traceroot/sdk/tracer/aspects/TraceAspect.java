package ai.traceroot.sdk.tracer.aspects;

import ai.traceroot.sdk.TraceRootSDK;
import ai.traceroot.sdk.tracer.annotations.Trace;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

/**
 * AspectJ aspect to handle @Trace annotation processing.
 *
 * <p>This aspect intercepts methods annotated with @Trace and automatically wraps them with
 * TraceRoot tracing functionality.
 */
@Aspect
public class TraceAspect {

  /**
   * Around advice for methods annotated with @Trace. This method intercepts the execution and wraps
   * it with tracing.
   */
  @Around("execution(* *(..)) && @annotation(trace)")
  public Object traceMethod(ProceedingJoinPoint joinPoint, Trace trace) throws Throwable {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Method method = signature.getMethod();

    // Determine span name: use annotation value if provided, otherwise use method name
    String spanName = getSpanName(trace, method.getName());

    // Execute the method within a trace
    return TraceRootSDK.trace(
        spanName,
        () -> {
          try {
            return joinPoint.proceed();
          } catch (Throwable throwable) {
            // Re-throw as runtime exception to maintain lambda compatibility
            if (throwable instanceof RuntimeException) {
              throw (RuntimeException) throwable;
            } else {
              throw new RuntimeException(throwable);
            }
          }
        });
  }

  /**
   * Determines the span name based on the @Trace annotation and method name.
   *
   * @param trace The @Trace annotation instance
   * @param methodName The name of the method being traced
   * @return The span name to use
   */
  private String getSpanName(Trace trace, String methodName) {
    // If value() is explicitly set (Python decorator-like), use it (takes precedence)
    if (trace.value() != null && !trace.value().trim().isEmpty()) {
      return trace.value();
    }

    // If spanName is explicitly set, use it
    if (trace.spanName() != null && !trace.spanName().trim().isEmpty()) {
      return trace.spanName();
    }

    // If spanNameSuffix is set, append it to method name
    if (trace.spanNameSuffix() != null && !trace.spanNameSuffix().trim().isEmpty()) {
      return methodName + trace.spanNameSuffix();
    }

    // Default to method name
    return methodName;
  }
}
