#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../.."

if ! command -v golem-cli >/dev/null 2>&1; then
  echo "[agent2agent-local-repl] error: golem-cli not found on PATH" >&2
  exit 1
fi
if ! command -v node >/dev/null 2>&1; then
  echo "[agent2agent-local-repl] error: node not found on PATH" >&2
  exit 1
fi
if ! command -v npm >/dev/null 2>&1; then
  echo "[agent2agent-local-repl] error: npm not found on PATH" >&2
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

out="$(
  cd "$app_dir"
  if [[ ! -d node_modules ]]; then
    # Repo dev convenience: prefer reusing the repo-local golem/node_modules if present.
    if [[ -d "$PWD/../../node_modules" ]]; then
      ln -s "$PWD/../../node_modules" node_modules
    else
      echo "[agent2agent-local-repl] Missing node_modules. Run:" >&2
      echo "  (cd $app_dir && npm install)" >&2
      exit 1
    fi
  fi
  env -u ARGV0 golem-cli "${flags[@]}" --yes app deploy scala:examples
  env -u ARGV0 golem-cli "${flags[@]}" --yes repl scala:examples --script-file "$script_file" --disable-stream
)"
echo "$out"
echo "$out" | grep -q 'demo:42:olleh'
echo "$out" | grep -q 'cba'



