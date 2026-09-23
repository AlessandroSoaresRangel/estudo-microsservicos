#!/bin/sh
set -eu

compose="$(dirname "$0")/../docker-compose.yml"
gateway_pom="$(dirname "$0")/../gateway/pom.xml"
gateway_config="$(dirname "$0")/../gateway/src/main/resources/application.yml"
task_pom="$(dirname "$0")/../task-service/pom.xml"
task_config="$(dirname "$0")/../task-service/src/main/resources/application.properties"
registry="$(dirname "$0")/../eureka-server"

[ -f "$registry/pom.xml" ]
[ -f "$registry/Dockerfile" ]
grep -Eq 'EnableEurekaServer' "$registry/src/main/java/com/taskflow/eureka/EurekaServerApplication.java"
grep -Eq 'spring-cloud-starter-netflix-eureka-server' "$registry/pom.xml"
grep -Eq 'spring-cloud-starter-netflix-eureka-client' "$gateway_pom"
grep -Eq 'spring-cloud-starter-netflix-eureka-client' "$task_pom"
grep -Eq 'defaultZone: http://eureka-server:8761/eureka/' "$gateway_config"
grep -Eq 'defaultZone=http://eureka-server:8761/eureka/' "$task_config"
! grep -Eq 'simple:|task-service-[123]:8080' "$gateway_config"
grep -Eq '^[[:space:]]*eureka-server:' "$compose"
grep -Eq '8761:8761' "$compose"
grep -Eq 'depends_on:' "$compose"
