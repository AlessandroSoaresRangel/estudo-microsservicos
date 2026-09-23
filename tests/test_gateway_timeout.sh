#!/bin/sh
set -eu

config="$(dirname "$0")/../gateway/src/main/resources/application.yml"
grep -Eq 'connect-timeout: 1000' "$config"
grep -Eq 'response-timeout: 3s' "$config"
