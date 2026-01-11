#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../.."

if ! command -v golem-cli >/dev/null 2>&1; then
  echo "[quickstart-script-test] error: golem-cli not found on PATH" >&2
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
    echo "[quickstart-script-test] Local router not reachable at $host:$port." >&2
    echo "[quickstart-script-test] Start it in another terminal, then rerun:" >&2
    echo "  golem server run --clean --data-dir .golem-local --router-port $port" >&2
    exit 1
  fi
fi

app_dir="$PWD/golem/quickstart/app"
script_file="$PWD/golem/quickstart/script-test.rib"

agent_id="agent-$(date +%s)"

(
  cd "$app_dir"
  env -u ARGV0 golem-cli "${flags[@]}" --yes --app-manifest-path "$app_dir/golem.yaml" deploy
  env -u ARGV0 golem-cli "${flags[@]}" --yes --app-manifest-path "$app_dir/golem.yaml" agent invoke "scala:quickstart-counter/counter-agent(\"$agent_id\")" 'increment'
  env -u ARGV0 golem-cli "${flags[@]}" --yes --app-manifest-path "$app_dir/golem.yaml" agent invoke "scala:quickstart-counter/counter-agent(\"$agent_id\")" 'increment'
  env -u ARGV0 golem-cli "${flags[@]}" --yes --app-manifest-path "$app_dir/golem.yaml" agent invoke 'scala:quickstart-counter/counter-agent("another")' 'increment'
  env -u ARGV0 golem-cli "${flags[@]}" --yes --app-manifest-path "$app_dir/golem.yaml" agent invoke 'scala:quickstart-counter/shard-agent("demo",42)' 'id'
)