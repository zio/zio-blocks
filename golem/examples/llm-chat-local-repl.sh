#!/usr/bin/env bash
set -euo pipefail

if [[ "${RUN_LLM_EXAMPLES:-}" != "1" ]]; then
  echo "[llm-chat-local-repl] SKIP: set RUN_LLM_EXAMPLES=1 to run (requires golem:llm support + model availability)."
  exit 0
fi

cd "$(dirname "$0")/../.."

if ! command -v golem-cli >/dev/null 2>&1; then
  echo "[llm-chat-local-repl] error: golem-cli not found on PATH" >&2
  exit 1
fi

GOLEM_CLI_FLAGS="${GOLEM_CLI_FLAGS:---local}"
read -r -a flags <<<"$GOLEM_CLI_FLAGS"

is_cloud=0
for f in "${flags[@]}"; do
  [[ "$f" == "--cloud" ]] && is_cloud=1
done

if [[ "$is_cloud" -eq 0 && "${FORCE_AI_ON_LOCAL:-}" != "1" ]]; then
  echo "[llm-chat-local-repl] SKIP: golem:llm is not available on the builtin local server."
  echo "[llm-chat-local-repl] Use GOLEM_CLI_FLAGS=--cloud (and configure credentials) to run this example," \
    "or set FORCE_AI_ON_LOCAL=1 to attempt running on a non-builtin local server." >&2
  exit 0
fi

required_any=(
  "ANTHROPIC_API_KEY"
  "OPENAI_API_KEY"
  "OPENROUTER_API_KEY"
  "XAI_API_KEY"
  "GOLEM_OLLAMA_BASE_URL"
)
bedrock_keys=("AWS_ACCESS_KEY_ID" "AWS_SECRET_ACCESS_KEY" "AWS_REGION")

has_any=0
for key in "${required_any[@]}"; do
  [[ -n "${!key:-}" ]] && has_any=1
done

has_bedrock=1
for key in "${bedrock_keys[@]}"; do
  [[ -z "${!key:-}" ]] && has_bedrock=0
done

if [[ "$has_any" -eq 0 && "$has_bedrock" -eq 0 ]]; then
  echo "[llm-chat-local-repl] error: no LLM provider env vars found." >&2
  echo "[llm-chat-local-repl] Set one of:" >&2
  echo "  - ANTHROPIC_API_KEY" >&2
  echo "  - OPENAI_API_KEY" >&2
  echo "  - OPENROUTER_API_KEY" >&2
  echo "  - XAI_API_KEY" >&2
  echo "  - GOLEM_OLLAMA_BASE_URL (defaults to http://localhost:11434)" >&2
  echo "  - or AWS_ACCESS_KEY_ID + AWS_SECRET_ACCESS_KEY + AWS_REGION (Bedrock)" >&2
  echo "[llm-chat-local-repl] If running locally, ensure your server has golem:llm providers configured." >&2
  exit 1
fi

if [[ "$is_cloud" -eq 0 ]]; then
  host="${GOLEM_ROUTER_HOST:-127.0.0.1}"
  port="${GOLEM_ROUTER_PORT:-9881}"
  if ! timeout 1 bash -lc "cat < /dev/null > /dev/tcp/$host/$port" 2>/dev/null; then
    echo "[llm-chat-local-repl] Local router not reachable at $host:$port." >&2
    echo "[llm-chat-local-repl] Start it in another terminal, then rerun:" >&2
    echo "  golem server run --clean --data-dir .golem-local --router-port $port" >&2
    exit 1
  fi
fi

app_dir="$PWD/golem/examples"
script_file="$PWD/golem/examples/samples/llm-chat/repl-llm-chat.rib"

# Build Scala.js up-front (no golem-cli needed). This also runs `golemPrepare` automatically,
# ensuring the base guest runtime wasm is present next to the app manifest.
build_log="$(mktemp)"
trap 'rm -f "$build_log"' EXIT
if ! ( cd "$PWD" && sbt -batch -no-colors -Dsbt.supershell=false "zioGolemExamplesJS/fastLinkJS" ) >"$build_log" 2>&1; then
  cat "$build_log" >&2
  echo "[llm-chat-local-repl] sbt failed; see output above." >&2
  exit 1
fi

out="$(
  cd "$app_dir"
  env -u ARGV0 golem-cli "${flags[@]}" --yes --app-manifest-path "$app_dir/golem.yaml" deploy
  env -u ARGV0 golem-cli "${flags[@]}" --yes --app-manifest-path "$app_dir/golem.yaml" \
    repl scala:examples --script-file "$script_file" --disable-stream < /dev/null 2>&1
)"

if echo "$out" | grep -F -q 'CustomError(' || \
   echo "$out" | grep -F -q 'JavaScript error:' || \
   echo "$out" | grep -F -q 'Exception during call' || \
   echo "$out" | grep -F -q '[ERROR'; then
  echo "[llm-chat-local-repl] ERROR: repl output contains an error:" >&2
  echo "[llm-chat-local-repl] Hint: check your LLM provider env vars and server configuration." >&2
  echo "$out" >&2
  exit 1
fi

echo "$out"
echo "$out" | grep -F -q 'h:'

