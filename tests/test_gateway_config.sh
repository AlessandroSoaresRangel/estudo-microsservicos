#!/bin/sh
set -eu

compose="$(dirname "$0")/../docker-compose.yml"
config="$(dirname "$0")/../gateway/src/main/resources/application.yml"

grep -Eq 'context: ./gateway' "$compose"
grep -Eq '"8081:8080"' "$compose"
grep -Eq 'uri: lb://task-service' "$config"
grep -Eq 'Path=/api/v1/tasks,/api/v1/tasks/\*\*' "$config"
grep -Eq 'Path=/api/v1/health,/api/v1/health/\*\*' "$config"
grep -Eq 'StripPrefix=2' "$config"
grep -Eq 'RewritePath=/api/v1/health' "$config"
