# TraceRoot Java SDK

<div align="center">
  <a href="https://traceroot.ai/">
    <img src="https://raw.githubusercontent.com/traceroot-ai/traceroot/main/misc/images/traceroot_logo.png" alt="TraceRoot Logo">
  </a>
</div>

<div align="center">

[![Documentation][docs-image]][docs-url]
[![TraceRoot.AI Website][company-website-image]][company-website-url]
[![Maven Central][maven-image]][maven-url]

</div>

Please see the [Java SDK Docs](https://docs.traceroot.ai/sdk/java) for details.

## Spring Boot Example

In the root directory, run:

```bash
mvn clean install -Dgpg.skip=true
export TRACEROOT_TOKEN=your_token
export TRACEROOT_ROOT_PATH=your_absolute_path_to_git_repo
cd examples/spring-boot-example
mvn spring-boot:run
```

then

```bash
# List tasks
curl http://localhost:8080/api/tasks

# Create task
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"title":"read Spring docs"}'

# Toggle task
curl -X PUT http://localhost:8080/api/tasks/1/toggle

# Delete task
curl -X DELETE http://localhost:8080/api/tasks/1
```

## Contact Us

Please reach out to founders@traceroot.ai if you have any questions.

[company-website-image]: https://img.shields.io/badge/website-traceroot.ai-black
[company-website-url]: https://traceroot.ai
[docs-image]: https://img.shields.io/badge/docs-traceroot.ai-0dbf43
[docs-url]: https://docs.traceroot.ai
[maven-image]: https://img.shields.io/maven-central/v/ai.traceroot/traceroot-sdk-java?color=f89820
[maven-url]: http://central.sonatype.com/artifact/ai.traceroot/traceroot-sdk-java
