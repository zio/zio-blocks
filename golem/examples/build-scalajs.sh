#!/usr/bin/env bash
set -euo pipefail

component="${1:-}"
if [[ -z "$component" ]]; then
  echo "usage: $0 <component_name>" >&2
  exit 2
fi

component_dir="$PWD"
app_dir="$(cd "$(dirname "$0")" && pwd)"
repo_root="$(cd "$app_dir/../.." && pwd)"
bundle_glob="$app_dir/target/scala-*/zio-golem-examples-js-fastopt/main.js"
out_file="$component_dir/.golem/scala.js"

echo "[scala.js] Building Scala.js bundle for $component..." >&2

build_log="$(mktemp)"
trap 'rm -f "$build_log"' EXIT
if ! ( cd "$repo_root" && sbt -batch -no-colors -Dsbt.supershell=false \
    "++3.3.7!" \
    "set zioGolemExamples / golemAgentGuestWasmFile := file(\\\"$app_dir/.generated/agent_guest.wasm\\\")" \
    "zioGolemExamples/fastLinkJS" ) >"$build_log" 2>&1; then
  cat "$build_log" >&2
  echo "[scala.js] sbt failed; see output above." >&2
  exit 1
fi

bundle="$(ls -t $bundle_glob 2>/dev/null | head -n1 || true)"
if [[ -z "$bundle" ]]; then
  echo "[scala.js] Could not find Scala.js bundle at: $bundle_glob" >&2
  exit 1
fi

mkdir -p "$(dirname "$out_file")"
cp "$bundle" "$out_file"

echo "[scala.js] Wrote Scala.js bundle to $out_file" >&2
