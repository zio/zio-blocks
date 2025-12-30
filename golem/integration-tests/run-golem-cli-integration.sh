#!/usr/bin/env bash
set -euo pipefail

# Template-style golem-cli integration test runner (repo-local).
#
# `golem-cli` is the driver; the build tool only wires the Scala.js bundle + bridge artifacts.

export GOLEM_SDK_INTEGRATION="${GOLEM_SDK_INTEGRATION:-1}"

echo "[golem-cli-integration] GOLEM_SDK_INTEGRATION=$GOLEM_SDK_INTEGRATION"

if [[ "${GOLEM_SDK_INTEGRATION}" != "1" ]]; then
  echo "[golem-cli-integration] Skipping: GOLEM_SDK_INTEGRATION != 1"
  exit 0
fi

cd "$(dirname "$0")/../.."

echo "[golem-cli-integration] Running agent2agent repl smoke test"
./golem/examples/agent2agent-local-repl.sh

echo "[golem-cli-integration] Running quickstart repl smoke test"
./golem/quickstart/script-test.sh
