#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../.."

if ! command -v golem-cli >/dev/null 2>&1; then
  echo "[agent2agent-local-repl] error: golem-cli not found on PATH" >&2
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
    echo "[agent2agent-local-repl] Local router not reachable at $host:$port." >&2
    echo "[agent2agent-local-repl] Start it in another terminal, then rerun:" >&2
    echo "  golem server run --clean --data-dir .golem-local --router-port $port" >&2
    exit 1
  fi
fi

app_dir="$PWD/golem/examples/app"
script_file="$PWD/golem/examples/repl-minimal-agent-to-agent.rib"

# Use fresh agent ids on each run to avoid "Previous Invocation Failed" poisoning.
coord_id="demo-$(date +%s)-$RANDOM"
shard_name="demo-$(date +%s)-$RANDOM"
tmp_script="$(mktemp)"
trap 'rm -f "$tmp_script"' EXIT
sed \
  -e "s/coordinator(\"demo\")/coordinator(\"$coord_id\")/" \
  -e "s/\\.route(\"demo\", 42/\\.route(\"$shard_name\", 42/" \
  -e "s/\\.route-typed(\"demo\", 42/\\.route-typed(\"$shard_name\", 42/" \
  "$script_file" > "$tmp_script"

out="$(
  cd "$app_dir"
  env -u ARGV0 golem-cli "${flags[@]}" --yes --app-manifest-path "$app_dir/golem.yaml" deploy
  env -u ARGV0 golem-cli "${flags[@]}" --yes --app-manifest-path "$app_dir/golem.yaml" repl scala:examples --script-file "$tmp_script" --disable-stream
)"
echo "$out"
echo "$out" | grep -q "${shard_name}:42:olleh"
echo "$out" | grep -q 'cba'



