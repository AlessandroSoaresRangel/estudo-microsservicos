#!/bin/sh
set -eu

compose="$(dirname "$0")/../docker-compose.yml"
count="$(grep -c 'APP_VERSION:' "$compose" || true)"
[ "$count" -eq 5 ]
grep -Eq 'APP_VERSION:[[:space:]]*"\$\{APP_VERSION:-1\.0\.0\}"' "$compose"
