#!/usr/bin/env bash
set -euo pipefail

# Template-style golem-cli integration test runner.
#
# This script is intentionally thin: the "source of truth" is the sbt/mill integration tasks,
# and this script just provides a convenient entrypoint.

export GOLEM_SDK_INTEGRATION="${GOLEM_SDK_INTEGRATION:-1}"

echo "[golem-cli-integration] GOLEM_SDK_INTEGRATION=$GOLEM_SDK_INTEGRATION"
echo "[golem-cli-integration] Running: sbt golemCliIntegrationIfEnabled"

sbt -no-colors golemCliIntegrationIfEnabled
