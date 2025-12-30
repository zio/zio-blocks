#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../.."

# Build + wire the Scala.js bundle + generated bridge into a deterministic local app scaffold under `.golem-apps/`.
sbt -batch -no-colors -Dsbt.supershell=false "zioGolemExamplesJS/golemWire"

GOLEM_CLI_FLAGS="${GOLEM_CLI_FLAGS:---local}"
read -r -a flags <<<"$GOLEM_CLI_FLAGS"

is_cloud=0
for f in "${flags[@]}"; do
  [[ "$f" == "--cloud" ]] && is_cloud=1
done

if [[ "$is_cloud" -eq 0 ]]; then
  host="${GOLEM_ROUTER_HOST:-127.0.0.1}"
  port="${GOLEM_ROUTER_PORT:-9881}"
  if ! timeout 1 bash -lc "cat < /dev/null > /dev/tcp/$host/$port" 2>/dev/null; then
    echo "[agent2agent-local] Local router not reachable at $host:$port." >&2
    echo "[agent2agent-local] Start it in another terminal, then rerun:" >&2
    echo "  golem server run --clean --data-dir .golem-local --router-port $port" >&2
    exit 1
  fi
fi

app_dir="$PWD/.golem-apps/scala-examples"
script_file="$PWD/golem/examples/repl-minimal-agent-to-agent.rib"

# First-run safety: if the wired bundle is missing the shim export, rerun golemWire once more.
bundle_file="$app_dir/components-ts/scala-examples/src/scala-examples.js"
if [[ -f "$bundle_file" ]] && ! grep -q "__golemInternalScalaAgents" "$bundle_file"; then
  echo "[agent2agent-local] Scala shim export missing from wired bundle; re-running golemWire..." >&2
  sbt -batch -no-colors -Dsbt.supershell=false "zioGolemExamplesJS/golemWire"
fi

(
  cd "$app_dir"
  env -u ARGV0 golem-cli "${flags[@]}" --yes app deploy scala:examples
  env -u ARGV0 golem-cli "${flags[@]}" --yes repl scala:examples --script-file "$script_file" --disable-stream
)