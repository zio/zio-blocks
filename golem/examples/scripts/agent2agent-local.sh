#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
cd "$script_dir/../../.."

source "$script_dir/lib.sh"

name="agent2agent-local"
examples_require_cli "$name"
examples_parse_flags
examples_check_router "$name"

app_dir="$PWD/golem/examples"
script_file="$PWD/golem/examples/samples/agent-to-agent/repl-minimal-agent-to-agent.rib"

examples_build_js "$name"
examples_run_repl "$app_dir" "$script_file"
