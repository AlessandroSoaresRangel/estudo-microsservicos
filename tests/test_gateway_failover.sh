#!/bin/sh
set -eu

config="$(dirname "$0")/../gateway/src/main/resources/application.yml"

[ "$(grep -c 'name: Retry' "$config")" -eq 2 ]
grep -Eq 'retries: 2' "$config"
grep -Eq 'statuses: BAD_GATEWAY,SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT' "$config"
grep -Eq 'lb://task-service' "$config"

grep -Eq 'name: CircuitBreaker' "$config"
grep -Eq 'name: taskServiceCircuitBreaker' "$config"
grep -Eq 'fallbackUri: forward:/fallback/tasks' "$config"
grep -Eq 'name: taskServiceHealthCircuitBreaker' "$config"
grep -Eq 'fallbackUri: forward:/fallback/health' "$config"
