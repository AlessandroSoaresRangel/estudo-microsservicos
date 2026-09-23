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

grafana="$(dirname "$0")/../grafana"
grep -Fq 'image: grafana/grafana' "$compose"
grep -Fq '"3000:3000"' "$compose"
test -f "$grafana/provisioning/datasources/prometheus.yaml"
grep -Fq 'url: http://prometheus:9090' "$grafana/provisioning/datasources/prometheus.yaml"
test -f "$grafana/provisioning/dashboards/provider.yaml"
test -f "$grafana/dashboards/gateway.json"
grep -Fq 'jvm_memory_used_bytes' "$grafana/dashboards/gateway.json"

gateway_config="$(dirname "$0")/../gateway/src/main/resources/application.yml"
grep -Fq 'http.server.requests": true' "$gateway_config"
grep -Fq 'job_name: task-service' "$config"
grep -Fq "targets: ['task-service-1:8080', 'task-service-2:8080', 'task-service-3:8080']" "$config"
task_config="$(dirname "$0")/../task-service/src/main/resources/application.properties"
grep -Fq 'management.metrics.distribution.percentiles-histogram.http.server.requests=true' "$task_config"
python3 -c 'import json,sys; d=json.load(open(sys.argv[1])); assert any("job=\"task-service\"" in t["expr"] for p in d["panels"] for t in p["targets"]); assert any("histogram_quantile" in t["expr"] and "task-service" in t["expr"] for p in d["panels"] for t in p["targets"])' "$(dirname "$0")/../grafana/dashboards/gateway.json"
