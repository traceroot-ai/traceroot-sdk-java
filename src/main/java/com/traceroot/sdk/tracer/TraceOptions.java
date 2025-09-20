package com.traceroot.sdk.tracer;

import java.util.Arrays;
import java.util.List;

public class TraceOptions {
  private String spanName;
  private String spanNameSuffix;
  private boolean traceParams = false;
  private List<String> tracedParamNames;
  private boolean traceReturnValue = false;
  private boolean flattenAttributes = true;

  public TraceOptions() {}

  public TraceOptions(String spanName) {
    this.spanName = spanName;
  }

  // Builder pattern
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private final TraceOptions options = new TraceOptions();

    public Builder spanName(String spanName) {
      options.spanName = spanName;
      return this;
    }

    public Builder spanNameSuffix(String spanNameSuffix) {
      options.spanNameSuffix = spanNameSuffix;
      return this;
    }

    public Builder traceAllParams() {
      options.traceParams = true;
      options.tracedParamNames = null;
      return this;
    }

    public Builder traceParams(String... paramNames) {
      options.traceParams = true;
      options.tracedParamNames = Arrays.asList(paramNames);
      return this;
    }

    public Builder traceReturnValue() {
      options.traceReturnValue = true;
      return this;
    }

    public Builder flattenAttributes(boolean flatten) {
      options.flattenAttributes = flatten;
      return this;
    }

    public TraceOptions build() {
      return options;
    }
  }

  public String getSpanName(String methodName) {
    if (spanName != null && !spanName.trim().isEmpty()) {
      return spanName;
    }
    if (spanNameSuffix != null && !spanNameSuffix.trim().isEmpty()) {
      return methodName + spanNameSuffix;
    }
    return methodName != null ? methodName : "anonymous";
  }

  // Getters and setters
  public String getSpanName() {
    return spanName;
  }

  public void setSpanName(String spanName) {
    this.spanName = spanName;
  }

  public String getSpanNameSuffix() {
    return spanNameSuffix;
  }

  public void setSpanNameSuffix(String spanNameSuffix) {
    this.spanNameSuffix = spanNameSuffix;
  }

  public boolean isTraceParams() {
    return traceParams;
  }

  public void setTraceParams(boolean traceParams) {
    this.traceParams = traceParams;
  }

  public List<String> getTracedParamNames() {
    return tracedParamNames;
  }

  public void setTracedParamNames(List<String> tracedParamNames) {
    this.tracedParamNames = tracedParamNames;
  }

  public boolean isTraceReturnValue() {
    return traceReturnValue;
  }

  public void setTraceReturnValue(boolean traceReturnValue) {
    this.traceReturnValue = traceReturnValue;
  }

  public boolean isFlattenAttributes() {
    return flattenAttributes;
  }

  public void setFlattenAttributes(boolean flattenAttributes) {
    this.flattenAttributes = flattenAttributes;
  }
}
