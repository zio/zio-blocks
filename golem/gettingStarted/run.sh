#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if ! command -v golem-cli >/dev/null 2>&1; then
  echo "[gettingStarted/run.sh] error: golem-cli not found on PATH" >&2
  exit 1
fi

GOLEM_CLI_FLAGS="${GOLEM_CLI_FLAGS:---local}"
read -r -a flags <<<"$GOLEM_CLI_FLAGS"

echo "[gettingStarted/run.sh] Building Scala.js (compile + fastLinkJS)..." >&2
( cd components-js/scala-demo/scala && sbt -batch -no-colors -Dsbt.supershell=false "compile" "fastLinkJS" >/dev/null )

echo "[gettingStarted/run.sh] Deploying app..." >&2
( env -u ARGV0 golem-cli "${flags[@]}" --yes --app-manifest-path "$PWD/golem.yaml" deploy >/dev/null )

echo "[gettingStarted/run.sh] Running repl script..." >&2
(
  agent_id="demo-$(date +%s)"
  cat > repl-counter.rib <<EOF
let c = counter-agent("$agent_id");
let a = c.increment();
let b = c.increment();
{ a: a, b: b }
EOF

  out="$(env -u ARGV0 golem-cli "${flags[@]}" --yes --app-manifest-path "$PWD/golem.yaml" \
    repl scala:demo --script-file repl-counter.rib --disable-stream < /dev/null)"

  echo "$out"

  # Basic verification: ensure the two increments happened.
  echo "$out" | grep -F -q "a: 1"
  echo "$out" | grep -F -q "b: 2"
)

echo "[gettingStarted/run.sh] OK" >&2

