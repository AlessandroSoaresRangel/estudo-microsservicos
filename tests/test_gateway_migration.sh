#!/bin/sh
set -eu

compose="$(dirname "$0")/../docker-compose.yml"
gateway="$(dirname "$0")/../gateway"

[ -f "$gateway/pom.xml" ]
[ -f "$gateway/Dockerfile" ]
[ -f "$gateway/src/main/resources/application.yml" ]
grep -Eq 'spring-cloud-starter-gateway-server-webflux' "$gateway/pom.xml"
grep -Eq 'spring-cloud-starter-loadbalancer' "$gateway/pom.xml"
grep -Eq 'spring-cloud.version>2025\.0\.' "$gateway/pom.xml"
grep -Eq 'lb://task-service' "$gateway/src/main/resources/application.yml"
grep -Eq 'Retry' "$gateway/src/main/resources/application.yml"
grep -Eq 'StripPrefix=2' "$gateway/src/main/resources/application.yml"
grep -Eq 'context: ./gateway' "$compose"
grep -Eq '"8081:8080"' "$compose"
! grep -Eq 'image:[[:space:]]*nginx|nginx\.conf' "$compose"
