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

# Support both layouts:
# - recommended: <root>/scala + <root>/app
# - older/mistaken: <root>/ (Scala project) + <root>/app (nested)
scala_dir="$repo_root/scala"
if [[ ! -f "$scala_dir/build.sbt" ]]; then
  scala_dir="$repo_root"
fi

( cd "$scala_dir" && sbt -batch -no-colors -Dsbt.supershell=false \
    "compile" \
    "fastLinkJS" )

bundle="$(
  find "$scala_dir/target" -type f -name 'main.js' -path '*fastopt*' -printf '%T@ %p\n' 2>/dev/null \
    | sort -nr \
    | head -n 1 \
    | cut -d' ' -f2- \
    || true
)"
if [[ -z "$bundle" ]]; then
  bundle="$(
    find "$scala_dir/target" -type f -name 'main.js' -path '*fullopt*' -printf '%T@ %p\n' 2>/dev/null \
      | sort -nr \
      | head -n 1 \
      | cut -d' ' -f2- \
      || true
  )"
fi
if [[ -z "$bundle" ]]; then
  echo "[scala.js] Could not locate Scala.js bundle under $scala_dir/target" >&2
  exit 1
fi

mkdir -p "$component_dir/src"
cp "$bundle" "$component_dir/src/scala.js"

