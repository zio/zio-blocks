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
echo "Running JSON roundtrip demo..."
golem-cli repl scala:examples --script-file samples/json-promise/repl-json-promise.rib
