# TraceRoot Tencent Log4j2 Example

This example demonstrates how to use the TraceRoot SDK with Tencent Cloud CLS (Cloud Log Service) and Log4j2 as the logging framework in a standalone Java application.

## Prerequisites

- Java 11 or higher
- Maven 3.6+
- Tencent Cloud account with CLS (Cloud Log Service) access
- TraceRoot account with APM trace token

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

## 3: Run the Application

```bash
mvn clean compile exec:java
```
