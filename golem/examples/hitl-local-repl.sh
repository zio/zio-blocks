#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "$0")" && pwd)"
cd "$script_dir/../.."

source "$script_dir/lib.sh"

name="hitl-local-repl"
examples_require_cli "$name"
examples_parse_flags
examples_check_router "$name"

app_dir="$PWD/golem/examples"
script_file="$PWD/golem/examples/samples/human-in-the-loop/repl-human-in-the-loop.rib"

examples_build_js "$name"

out="$(examples_run_repl "$app_dir" "$script_file" 2>&1)"
examples_check_repl_errors "$name" "$out"

echo "$out"
echo "$out" | grep -F -q 'outcome: "approved"'

