#!/bin/bash

# Color codes for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

BASE_URL="http://localhost:8080/api/tasks"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}TraceRoot Spring Boot Example - API Test${NC}"
echo -e "${BLUE}========================================${NC}\n"

# Helper function to print section headers
print_section() {
    echo -e "\n${YELLOW}$1${NC}"
    echo "---"
}

# Helper function to make requests with formatted output
make_request() {
    echo -e "${GREEN}Request:${NC} $1"
    echo "$2"
    echo -e "${GREEN}Response:${NC}"
    eval "$2"
    echo -e "\n"
    sleep 1
}

# 1. List all tasks (initially empty)
print_section "1. List all tasks (should be empty)"
make_request "GET /api/tasks" "curl -s $BASE_URL"

# 2. Create first task
print_section "2. Create a new task: 'Buy groceries'"
make_request "POST /api/tasks" "curl -s -X POST $BASE_URL \\
  -H 'Content-Type: application/json' \\
  -d '{\"title\": \"Buy groceries\"}'"

# 3. Create second task
print_section "3. Create another task: 'Complete JDK 11 migration'"
make_request "POST /api/tasks" "curl -s -X POST $BASE_URL \\
  -H 'Content-Type: application/json' \\
  -d '{\"title\": \"Complete JDK 11 migration\"}'"

# 4. List all tasks
print_section "4. List all tasks (should show 2 tasks)"
make_request "GET /api/tasks" "curl -s $BASE_URL"

# 5. Toggle task #1 to mark as done
print_section "5. Toggle task #1 to mark it as done"
make_request "PUT /api/tasks/1/toggle" "curl -s -X PUT $BASE_URL/1/toggle"

# 6. Toggle task #1 again to mark as not done
print_section "6. Toggle task #1 again to mark it as not done"
make_request "PUT /api/tasks/1/toggle" "curl -s -X PUT $BASE_URL/1/toggle"

# 7. Delete task #2
print_section "7. Delete task #2"
make_request "DELETE /api/tasks/2" "curl -s -X DELETE $BASE_URL/2"

# 8. Try to delete a non-existent task (should return 404)
print_section "8. Try to delete non-existent task #999 (should return 404)"
make_request "DELETE /api/tasks/999" "curl -s -w '\\nHTTP Status: %{http_code}\\n' -X DELETE $BASE_URL/999"

# 9. Final list
print_section "9. List all tasks again (should show only task #1)"
make_request "GET /api/tasks" "curl -s $BASE_URL"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}API Test Complete!${NC}"
echo -e "${BLUE}========================================${NC}\n"
echo -e "Check the application logs for trace information."
echo -e "All API calls were traced with the @Trace annotation.\n"
