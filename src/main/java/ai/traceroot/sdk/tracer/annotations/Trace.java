package ai.traceroot.sdk.tracer.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to automatically trace method execution.
 *
 * <p>Usage: @Trace public void myMethod() { ... } @Trace(spanName = "custom-span-name") public void
 * myMethod() { ... } @Trace(traceParams = true, traceReturnValue = true) public String
 * processData(String input) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Trace {

  /**
   * Default value for span name (Python decorator-like syntax). If provided, this takes precedence
   * over spanName().
   */
  String value() default "";

  /** Custom span name. If not provided, method name will be used. */
  String spanName() default "";

  /** Suffix to append to method name for span name. */
  String spanNameSuffix() default "";

  /** Whether to trace all method parameters. */
  boolean traceParams() default false;

  /**
   * Specific parameter names to trace. If empty and traceParams is true, all parameters are traced.
   */
  String[] tracedParamNames() default {};

  /** Whether to trace the return value. */
  boolean traceReturnValue() default false;

  /** Whether to flatten nested object attributes. */
  boolean flattenAttributes() default true;
}
