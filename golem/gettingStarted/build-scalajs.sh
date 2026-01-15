#!/usr/bin/env bash
set -euo pipefail

component="${1:-}"
if [[ -z "$component" ]]; then
  echo "usage: $0 <component_name>" >&2
  exit 2
fi

# golem-cli runs this script from the component directory.
component_dir="$PWD"

# Preferred layout: app root at `gettingStarted/`, Scala build at `gettingStarted/scala/`.
app_root="$(cd "$(dirname "$0")" && pwd)"
scala_dir="$app_root/scala"

if [[ ! -f "$scala_dir/build.sbt" ]]; then
  echo "[scala.js] expected Scala project at $scala_dir (missing build.sbt)" >&2
  exit 1
fi

echo "[scala.js] Building Scala.js bundle for $component (tool=sbt)..." >&2
( cd "$scala_dir" && sbt -batch -no-colors -Dsbt.supershell=false "compile" "fastLinkJS" >/dev/null )

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

