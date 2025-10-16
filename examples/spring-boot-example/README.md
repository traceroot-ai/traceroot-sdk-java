# TraceRoot Spring Boot Example

A simple REST API for managing tasks, demonstrating TraceRoot SDK integration with Spring Boot.

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

### 3. Run the Application

```bash
mvn spring-boot:run
```

The server will start on `http://localhost:8080`

### 4. Test the API

In a new terminal, you can either:

```bash
chmod +x test.sh
./test.sh
```
