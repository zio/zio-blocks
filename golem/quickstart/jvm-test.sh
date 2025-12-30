#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../.."

# Build + wire the Scala.js component into `.golem-apps/`, then use golem-cli as the driver.
sbt -batch -no-colors -Dsbt.supershell=false "zioGolemQuickstartJS/golemWire"

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
    echo "[quickstart-jvm-test] Local router not reachable at $host:$port." >&2
    echo "[quickstart-jvm-test] Start it in another terminal, then rerun:" >&2
    echo "  golem server run --clean --data-dir .golem-local --router-port $port" >&2
    exit 1
  fi
fi

app_dir="$PWD/.golem-apps/scala-quickstart"

# First-run safety: on a totally clean workspace, the manifest/shim may not exist
# when the initial compilation happens (sbt caches source generator results for the session).
# If the wired bundle is missing the shim export, rerun golemWire once more.
bundle_file="$app_dir/components-ts/scala-quickstart-counter/src/scala-quickstart.js"
if [[ -f "$bundle_file" ]] && ! grep -q "__golemInternalScalaAgents" "$bundle_file"; then
  echo "[quickstart-jvm-test] Scala shim export missing from wired bundle; re-running golemWire..." >&2
  sbt -batch -no-colors -Dsbt.supershell=false "zioGolemQuickstartJS/golemWire"
fi

(cd "$app_dir" && env -u ARGV0 golem-cli "${flags[@]}" --yes app deploy scala:quickstart-counter)

sbt -batch -no-colors -Dsbt.supershell=false 'zioGolemQuickstartJVM/runMain cloud.golem.quickstart.QuickstartClient'