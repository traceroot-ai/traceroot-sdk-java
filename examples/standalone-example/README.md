# TraceRoot Standalone Java Example

This example demonstrates how to use the TraceRoot SDK in a standalone Java application (without Spring Boot).

## Prerequisites

- Java 11 or higher
- Maven 3.6 or higher
- A TraceRoot account and API token

## Quick Start

### 1. Build and Install the Java Environment

```bash
mvn clean install -Dgpg.skip=true
```

### 2. Configure Environment Variables

Copy the example environment file and populate it with your values:

```bash
cp .env.example .env
```

Edit `.env` and set:

- `TRACEROOT_TOKEN` - Your TraceRoot API token
- `TRACEROOT_ROOT_PATH` - Absolute path to this project directory

### 3. Run the Example

Run:

```bash
mvn clean compile exec:java -Dexec.mainClass="com.example.StandaloneExample"
```
