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
echo "Running guards-block demo..."
golem-cli repl scala:examples --script-file samples/guards/repl-guards-block.rib
echo "Running guards-resource demo..."
golem-cli repl scala:examples --script-file samples/guards/repl-guards-resource.rib
echo "Running oplog demo..."
golem-cli repl scala:examples --script-file samples/guards/repl-oplog.rib
