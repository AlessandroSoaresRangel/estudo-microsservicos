#!/bin/sh
set -eu

config="$(dirname "$0")/../prometheus/prometheus.yaml"

test -f "$config"
grep -Fq 'job_name: gateway' "$config"
grep -Fq 'metrics_path: /actuator/prometheus' "$config"
grep -Fq "targets: ['gateway:8080']" "$config"

compose="$(dirname "$0")/../docker-compose.yml"
grep -Fq 'image: prom/prometheus' "$compose"
grep -Fq '"9090:9090"' "$compose"
grep -Fq './prometheus/prometheus.yaml:/etc/prometheus/prometheus.yml:ro' "$compose"
