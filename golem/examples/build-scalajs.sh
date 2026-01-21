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
repo_root="$(cd "$app_dir/../.." && pwd)"
component_dir="$PWD"
agent_wasm="$app_dir/golem-temp/agent_guest.wasm"

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

  # Ensure base guest runtime wasm exists (and is up-to-date) before golem-cli inject step runs.
  # This must happen during the build step, because golem-cli may not read/write app/wasm itself.
  ( cd "$repo_root" && mill -i "$GOLEM_MILL_TASK" )

  bundle="$(
  python3 - <<'PY' "$repo_root"
import sys
from pathlib import Path

repo_root = Path(sys.argv[1])
out_dir = repo_root / "out"
if not out_dir.exists():
  print("")
  raise SystemExit(0)

def newest(glob_pat: str) -> str:
  files = list(out_dir.rglob(glob_pat))
  if not files:
    return ""
  files.sort(key=lambda p: p.stat().st_mtime, reverse=True)
  return str(files[0])

print(newest("*fastopt*.js") or "")
PY
  )"
  if [[ -z "$bundle" ]]; then
    bundle="$(
    python3 - <<'PY' "$repo_root"
import sys
from pathlib import Path

repo_root = Path(sys.argv[1])
out_dir = repo_root / "out"
if not out_dir.exists():
  print("")
  raise SystemExit(0)

def newest(glob_pat: str) -> str:
  files = list(out_dir.rglob(glob_pat))
  if not files:
    return ""
  files.sort(key=lambda p: p.stat().st_mtime, reverse=True)
  return str(files[0])

print(newest("*fullopt*.js") or "")
PY
    )"
  fi
  if [[ -z "$bundle" ]]; then
    echo "[scala.js] Could not locate Scala.js bundle under $repo_root/out (mill)" >&2
    exit 1
  fi
else
  build_log="$(mktemp)"
  trap 'rm -f "$build_log"' EXIT
  if ! ( cd "$repo_root" && sbt -batch -no-colors -Dsbt.supershell=false \
      "project $sbt_project" \
      "set golemAgentGuestWasmFile := file(\"$agent_wasm\")" \
      "golemEnsureAgentGuestWasm" \
      "compile" \
      "fastLinkJS" ) >"$build_log" 2>&1; then
    cat "$build_log" >&2
    echo "[scala.js] sbt failed; see output above." >&2
    exit 1
  fi

  bundle="$(ls -t $sbt_bundle_glob 2>/dev/null | head -n1 || true)"
  if [[ -z "$bundle" ]]; then
    echo "[scala.js] Could not find Scala.js bundle at: $sbt_bundle_glob" >&2
    exit 1
  fi
fi

mkdir -p "$(dirname "$out_file")"
cp "$bundle" "$out_file"


echo "[scala.js] Wrote Scala.js bundle to $out_file" >&2

