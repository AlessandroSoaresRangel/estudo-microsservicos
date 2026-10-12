#!/bin/sh
set -eu

exec sh "$(dirname "$0")/run_gateway_tests.sh" \
    'GatewayRoutesIntegrationTest#timesOutASlowProjectBackend'
