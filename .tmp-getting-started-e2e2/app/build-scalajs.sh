#!/usr/bin/env bash
set -euo pipefail

component="${1:-}"
if [[ -z "$component" ]]; then
  echo "usage: $0 <component_name>" >&2
  exit 2
fi

app_dir="$(cd "$(dirname "$0")" && pwd)"
repo_root="$(cd "$app_dir/.." && pwd)"
component_dir="$PWD"

( cd "$repo_root/scala" && sbt -batch -no-colors -Dsbt.supershell=false \
    "compile" \
    "golemEnsureAgentGuestWasm" \
    "golemEnsureBridgeSpecManifest" \
    "golemGenerateScalaShim" \
    "fastLinkJS" )

bundle="$(
  find "$repo_root/scala/target" -type f -name 'main.js' -path '*fastopt*' -printf '%T@ %p\n' 2>/dev/null \
    | sort -nr \
    | head -n 1 \
    | cut -d' ' -f2- \
    || true
)"
if [[ -z "$bundle" ]]; then
  bundle="$(
    find "$repo_root/scala/target" -type f -name 'main.js' -path '*fullopt*' -printf '%T@ %p\n' 2>/dev/null \
      | sort -nr \
      | head -n 1 \
      | cut -d' ' -f2- \
      || true
  )"
fi
if [[ -z "$bundle" ]]; then
  echo "[scala.js] Could not locate Scala.js bundle under $repo_root/scala/target" >&2
  exit 1
fi

mkdir -p "$component_dir/src"
cp "$bundle" "$component_dir/src/scala.js"
