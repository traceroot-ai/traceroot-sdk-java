# TraceRoot Log4j2 Local Example

This example demonstrates how to use the TraceRoot SDK with Log4j2 as the logging framework in a standalone Java application with local file logging.

## Prerequisites

- Java 11 or higher
- Maven 3.6+
- TraceRoot OTLP endpoint (for trace export)

## Quick Start

### 1. Build and Install the Java Environment

From the repository root:

```bash
mvn clean install -Dgpg.skip=true
```

### 2. Configure Environment Variables

Copy the example environment file and populate it with your values:

```bash
cp .env.example .env
```

Edit `.env` and set:

- `TRACEROOT_OTLP_ENDPOINT` - Your OTLP endpoint
- `TRACEROOT_ROOT_PATH` - Absolute path to this project directory

### 3. Run the Application

```bash
mvn clean compile exec:java
```

## Setting Up Jaeger on Tencent CVM

This section guides you through setting up a Jaeger instance on Tencent CVM to collect and visualize traces from your application.

### Step 1: Install Docker

SSH into your Tencent CVM and install Docker:

```bash
# Install Docker
sudo apt-get update
sudo apt-get install -y docker.io

# Start Docker and enable on boot
sudo systemctl start docker
sudo systemctl enable docker

# Add current user to docker group (optional, avoids needing sudo)
sudo usermod -aG docker $USER
# Note: Log out and log back in for group changes to take effect
```

### Step 2: Start Jaeger Container

Run Jaeger All-in-One with OTLP support enabled:

```bash
docker run -d --name jaeger \
  --restart unless-stopped \
  -e COLLECTOR_OTLP_ENABLED=true \
  -p 16686:16686 \
  -p 14268:14268 \
  -p 14250:14250 \
  -p 4317:4317 \
  -p 4318:4318 \
  cr.jaegertracing.io/jaegertracing/jaeger:2.8.0
```

**Port Mappings:**

- `16686` - Jaeger UI
- `4318` - OTLP HTTP endpoint (used by TraceRoot SDK)
- `4317` - OTLP gRPC endpoint
- `14268` - Jaeger Collector HTTP
- `14250` - Jaeger Collector gRPC

### Step 3: Verify Jaeger is Running

Check that the container is running properly:

```bash
# Check container status
sudo docker ps | grep jaeger

# Test Jaeger API endpoint
curl http://localhost:16686/api/services
```

**Expected output:** `{"data":null}` or `{"data":[]}` (empty because no traces have been sent yet)

## Setting Up Tencent CLS LogListener

This section guides you through setting up Tencent CLS LogListener agent to automatically collect local log files and send them to CLS (Cloud Log Service).

### Step 1: Install LogListener Agent

SSH into your Tencent CVM and install the LogListener agent:

```bash
# Download LogListener installer
cd /tmp
wget http://mirrors.tencent.com/install/cls/loglistener-linux-x64.tar.gz

# Extract to /usr/local
sudo tar -zxvf loglistener-linux-x64.tar.gz -C /usr/local/

# Navigate to tools directory
cd /usr/local/loglistener/tools

# Install the agent
sudo ./loglistener.sh install
```

### Step 2: Initialize LogListener with Your Credentials

Initialize the agent with your Tencent Cloud credentials:

```bash
# Initialize with your credentials
sudo ./loglistener.sh init \
  --secretid YOUR_SECRET_ID \
  --secretkey YOUR_SECRET_KEY \
  --region YOUR_LOCATION \
  --network internet
```

### Step 3: Start LogListener Service

Start and verify the LogListener daemon:

```bash
# Start the service
sudo systemctl start loglistenerd

# Check service status
sudo systemctl status loglistenerd

# Enable on boot (optional)
sudo systemctl enable loglistenerd
```

### Step 4: Configure Collection in CLS Console

Configure the log collection in Tencent CLS Console:

1. **Navigate to CLS Console** → Select your **Log Topic** → **Collection Configuration**

1. **Create Machine Group**:

   - Go to **Machine Group** section
   - Click **Create Machine Group**
   - Add your CVM's **private IP address**
   - Save the machine group

1. **Create Collection Configuration**:

   - Click **Create Collection Config**
   - **Collection Path**:
     ```
     /path/to/traceroot-sdk-java/examples/log4j2-local-example/logs/traceroot-sdk.log
     ```
   - **Key-Value Extraction Mode**: Select `JSON`
   - **Associate Machine Group**: Select the machine group you created
   - Click **Save**
