#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
main_dir=$(pwd)
repo_root="$(cd ../.. && pwd)"

echo "Preparing for Build..."
cd $repo_root
sbt "zioGolemExamples/golemPrepare"

cd $main_dir
echo "Building..."
golem-cli build --yes
echo "Deploying..."
golem-cli deploy --yes
echo "Running registry demo..."
golem-cli repl scala:examples --script-file samples/agent-registry/repl-registry.rib
echo "Running agent query demo..."
golem-cli repl scala:examples --script-file samples/agent-registry/repl-agent-query.rib
echo "Running phantom demo..."
golem-cli repl scala:examples --script-file samples/agent-registry/repl-phantom.rib
