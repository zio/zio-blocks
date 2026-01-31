#!/usr/bin/env bash
set -euo pipefail

if [[ "${RUN_WEBSEARCH_EXAMPLES:-}" != "1" ]]; then
  echo "[websearch-summary-local-repl] SKIP: set RUN_WEBSEARCH_EXAMPLES=1 to run (requires golem:web-search + golem:llm support)."
  exit 0
fi

script_dir="$(cd "$(dirname "$0")" && pwd)"
cd "$script_dir/../../.."

source "$script_dir/lib.sh"

name="websearch-summary-local-repl"
examples_require_cli "$name"
examples_parse_flags

if [[ "${EXAMPLE_IS_CLOUD:-0}" -eq 0 && "${FORCE_AI_ON_LOCAL:-}" != "1" ]]; then
  echo "[websearch-summary-local-repl] SKIP: golem:web-search / golem:llm are not available on the builtin local server."
  echo "[websearch-summary-local-repl] Use GOLEM_CLI_FLAGS=--cloud (and configure credentials) to run this example," \
    "or set FORCE_AI_ON_LOCAL=1 to attempt running on a non-builtin local server." >&2
  exit 0
fi

examples_check_router "$name"

app_dir="$PWD/golem/examples"
script_file="$PWD/golem/examples/samples/websearch-summary/repl-websearch-summary.rib"

examples_build_js "$name"

out="$(examples_run_repl "$app_dir" "$script_file" 2>&1)"
examples_check_repl_errors "$name" "$out"

echo "$out"
echo "$out" | grep -F -q 'Finished research for topic'
