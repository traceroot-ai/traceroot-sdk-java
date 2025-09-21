# TraceRoot Python SDK

<div align="center">
  <a href="https://traceroot.ai/">
    <img src="https://raw.githubusercontent.com/traceroot-ai/traceroot/main/misc/images/traceroot_logo.png" alt="TraceRoot Logo">
  </a>
</div>

<div align="center">

\[![Testing Status][testing-image]\][testing-url]
\[![Documentation][docs-image]\][docs-url]
\[![PyPI Version][pypi-image]\][pypi-url]
\[![PyPI SDK Downloads][pypi-sdk-downloads-image]\][pypi-sdk-downloads-url]
\[![TraceRoot.AI Website][company-website-image]\][company-website-url]

</div>

Please see the [Java SDK Docs](https://docs.traceroot.ai/sdk/java) for details.

## Spring Boot Example

In the root directory, run:

```bash
mvn clean install -Dgpg.skip=true
```

then

```bash
cd examples/spring-boot-example
mvn spring-boot:run
```

## Standalone Example

In the root directory, run:

```bash
cd examples/standalone-example
mvn clean compile exec:java
```
