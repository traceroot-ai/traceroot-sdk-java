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

## Contact Us

Please reach out to founders@traceroot.ai if you have any questions.

[company-website-image]: https://img.shields.io/badge/website-traceroot.ai-black
[company-website-url]: https://traceroot.ai
[docs-image]: https://img.shields.io/badge/docs-traceroot.ai-0dbf43
[docs-url]: https://docs.traceroot.ai
[pypi-image]: https://badge.fury.io/py/traceroot.svg
[pypi-sdk-downloads-image]: https://static.pepy.tech/badge/traceroot
[pypi-sdk-downloads-url]: https://pypi.python.org/pypi/traceroot
[pypi-url]: https://pypi.python.org/pypi/traceroot
[testing-image]: https://github.com/traceroot-ai/traceroot/actions/workflows/test.yml/badge.svg
[testing-url]: https://github.com/traceroot-ai/traceroot/actions/workflows/test.yml
