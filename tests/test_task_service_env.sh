#!/bin/sh
set -eu

root="$(dirname "$0")/.."
compose="$root/docker-compose.yml"
service_env="$root/task-service/.env.example"
application="$root/task-service/src/main/resources/application.properties"

[ "$(grep -c './task-service/.env' "$compose")" -eq 4 ]
grep -Fq 'POSTGRES_DB=' "$service_env"
grep -Fq 'POSTGRES_USER=' "$service_env"
grep -Fq 'POSTGRES_PASSWORD=' "$service_env"
grep -Fq 'spring.datasource.url=jdbc:postgresql://task-db:5432/${POSTGRES_DB}' "$application"
grep -Fq 'spring.datasource.username=${POSTGRES_USER}' "$application"
grep -Fq 'spring.datasource.password=${POSTGRES_PASSWORD}' "$application"
grep -Fq '!.env.example' "$root/.gitignore"
