#!/usr/bin/env bash
set -euo pipefail

# Cloud-gated golem-cli integration runner.
#
# This is intentionally thin: the source-of-truth lives in the sbt task
# `zioGolemExamplesJS/golemSdkIntegrationIfEnabled`.
#
# Prereqs:
# - golem-cli installed and authenticated/configured for your cloud profile
# - GOLEM_CLI_FLAGS set to include --cloud (and typically a profile), e.g.:
#     export GOLEM_CLI_FLAGS="--cloud -p my-profile"
#
# Then:
#   export GOLEM_SDK_INTEGRATION=1
#   ./golem/integration-tests/run-golem-cli-integration-cloud.sh

export GOLEM_SDK_INTEGRATION="${GOLEM_SDK_INTEGRATION:-1}"

if [[ "${GOLEM_SDK_INTEGRATION}" != "1" ]]; then
  echo "[golem-cli-integration-cloud] Skipping: GOLEM_SDK_INTEGRATION != 1"
  exit 0
fi

if [[ -z "${GOLEM_CLI_FLAGS:-}" ]]; then
  echo "[golem-cli-integration-cloud] Missing GOLEM_CLI_FLAGS. Example:"
  echo "  export GOLEM_CLI_FLAGS=\"--cloud -p my-profile\""
  exit 1
fi

echo "[golem-cli-integration-cloud] GOLEM_SDK_INTEGRATION=${GOLEM_SDK_INTEGRATION}"
echo "[golem-cli-integration-cloud] GOLEM_CLI_FLAGS=${GOLEM_CLI_FLAGS}"
echo "[golem-cli-integration-cloud] Running: sbt golemCliIntegrationIfEnabled"

sbt -no-colors golemCliIntegrationIfEnabled
