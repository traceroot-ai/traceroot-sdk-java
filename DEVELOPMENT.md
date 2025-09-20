# Development

## Setup

Setup Java development environment with OpenJDK 17 and Maven for MacOS:

```bash
brew install openjdk@17
brew install maven
```

## Building

In the root directory, run:

```bash
mvn clean install
# or
mvn clean install -Dgpg.skip=true
```

## Running Examples

For the standalone example, in the root directory, run:

```bash
cd examples/standalone-example
mvn clean compile exec:java
```

For the spring-boot example, in the root directory, run:

```bash
cd examples/spring-boot-example
mvn spring-boot:run
```

## Formatting

To format the code, in the root directory run:

```bash
curl -L https://github.com/google/google-java-format/releases/download/v1.15.0/google-java-format-1.15.0-all-deps.jar -o google-java-format.jar
java -jar google-java-format.jar --replace src/**/*.java examples/**/*.java
```
