#!/usr/bin/env bash
set -euo pipefail

if [[ "${RUN_LLM_EXAMPLES:-}" != "1" ]]; then
  echo "[llm-chat-local-repl] SKIP: set RUN_LLM_EXAMPLES=1 to run (requires golem:llm support + model availability)."
  exit 0
fi

script_dir="$(cd "$(dirname "$0")" && pwd)"
cd "$script_dir/../.."

source "$script_dir/lib.sh"

name="llm-chat-local-repl"
examples_require_cli "$name"
examples_parse_flags

if [[ "${EXAMPLE_IS_CLOUD:-0}" -eq 0 && "${FORCE_AI_ON_LOCAL:-}" != "1" ]]; then
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

examples_check_router "$name"

app_dir="$PWD/golem/examples"
script_file="$PWD/golem/examples/samples/llm-chat/repl-llm-chat.rib"

examples_build_js "$name"

out="$(examples_run_repl "$app_dir" "$script_file" 2>&1)"
examples_check_repl_errors "$name" "$out" "Hint: check your LLM provider env vars and server configuration."

echo "$out"
echo "$out" | grep -F -q 'h:'

