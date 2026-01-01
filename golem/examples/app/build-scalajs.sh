#!/usr/bin/env bash
set -euo pipefail

component="${1:-}"
if [[ -z "$component" ]]; then
  echo "usage: $0 <component_name>" >&2
  exit 2
fi

# Invoked by `golem-cli` as a build step (via `template: scala.js`).
# Working directory is the component folder (e.g. components-js/<component>/).

app_dir="$(cd "$(dirname "$0")" && pwd)"
repo_root="$(cd "$app_dir/../../.." && pwd)"
component_dir="$PWD"

tool="${GOLEM_SCALA_BUILD_TOOL:-sbt}"

case "$component" in
  scala:examples)
    sbt_project="zioGolemExamplesJS"
    sbt_bundle_glob="$repo_root/golem/examples/js/target/scala-*/zio-golem-examples-js-fastopt/main.js"
    out_file="$component_dir/src/scala.js"
    ;;
  *)
    echo "[scala.js] Unknown component: $component" >&2
    exit 2
    ;;
esac

echo "[scala.js] Building Scala.js bundle for $component (tool=$tool)..." >&2

if [[ "$tool" == "mill" ]]; then
  if ! command -v mill >/dev/null 2>&1; then
    echo "[scala.js] mill not found on PATH" >&2
    exit 2
  fi

  if [[ -z "${GOLEM_MILL_TASK:-}" ]]; then
    echo "[scala.js] GOLEM_SCALA_BUILD_TOOL=mill requires GOLEM_MILL_TASK to be set (e.g. myModule.fastLinkJS)" >&2
    exit 2
  fi

  ( cd "$repo_root" && mill -i "$GOLEM_MILL_TASK" )

  bundle="$(
    find "$repo_root/out" -type f -name '*fastopt*.js' -printf '%T@ %p\n' 2>/dev/null \
      | sort -nr \
      | head -n 1 \
      | cut -d' ' -f2- \
      || true
  )"
  if [[ -z "$bundle" ]]; then
    bundle="$(
      find "$repo_root/out" -type f -name '*fullopt*.js' -printf '%T@ %p\n' 2>/dev/null \
        | sort -nr \
        | head -n 1 \
        | cut -d' ' -f2- \
        || true
    )"
  fi
  if [[ -z "$bundle" ]]; then
    echo "[scala.js] Could not locate Scala.js bundle under $repo_root/out (mill)" >&2
    exit 1
  fi
else
  ( cd "$repo_root" && sbt -batch -no-colors -Dsbt.supershell=false \
      "$sbt_project/compile" \
      "$sbt_project/fastLinkJS" )

  bundle="$(ls -t $sbt_bundle_glob 2>/dev/null | head -n1 || true)"
  if [[ -z "$bundle" ]]; then
    echo "[scala.js] Could not find Scala.js bundle at: $sbt_bundle_glob" >&2
    exit 1
  fi
fi

mkdir -p "$(dirname "$out_file")"
cp "$bundle" "$out_file"
echo "[scala.js] Wrote Scala.js bundle to $out_file" >&2

