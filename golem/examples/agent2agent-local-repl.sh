#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../.."

# Uses only public plugin primitives:
# - deploy (implicit by golemAppRunScript default)
# - golem-cli repl script execution
out="$(
  sbt -batch -no-colors -Dsbt.supershell=false \
    "zioGolemExamplesJS/golemAppRunScript golem/examples/repl-minimal-agent-to-agent.rib"
)"
echo "$out"
echo "$out" | grep -q 'demo:42:olleh'
echo "$out" | grep -q 'cba'



