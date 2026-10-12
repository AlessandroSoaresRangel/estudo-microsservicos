#!/bin/sh
set -eu

gateway_directory="$(CDPATH= cd "$(dirname "$0")/../gateway" && pwd)"
test_selection="${1:-GatewayRoutesIntegrationTest}"

if command -v mvn >/dev/null 2>&1; then
    exec mvn -B -ntp -f "$gateway_directory/pom.xml" "-Dtest=$test_selection" test
fi

# Docker oferece o mesmo Java 21 do projeto quando Maven não está instalado.
docker build --progress=plain --build-arg "GATEWAY_TEST_SELECTION=$test_selection" \
    -f - "$gateway_directory" <<'DOCKERFILE'
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
ARG GATEWAY_TEST_SELECTION
RUN mvn -B -ntp "-Dtest=$GATEWAY_TEST_SELECTION" test
DOCKERFILE
