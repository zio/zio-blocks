#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../.."

if ! command -v golem-cli >/dev/null 2>&1; then
  echo "[quickstart-jvm-test] error: golem-cli not found on PATH" >&2
  exit 1
fi

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

app_dir="$PWD/golem/quickstart/app"

(
  cd "$app_dir"
  env -u ARGV0 golem-cli "${flags[@]}" --yes app deploy scala:quickstart-counter
)

sbt -batch -no-colors -Dsbt.supershell=false 'zioGolemQuickstartJVM/runMain golem.quickstart.QuickstartClient'