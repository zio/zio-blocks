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
build_log="$(mktemp)"
trap 'rm -f "$build_log"' EXIT
if ! ( cd "$scala_dir" && sbt -batch -no-colors -Dsbt.supershell=false "compile" "fastLinkJS" ) >"$build_log" 2>&1; then
  cat "$build_log" >&2
  echo "[scala.js] sbt failed; see output above." >&2
  exit 1
fi

bundle="$(
  python3 - <<'PY' "$scala_dir"
import os, sys
from pathlib import Path

scala_dir = Path(sys.argv[1])
target = scala_dir / "target"
if not target.exists():
  print("")
  raise SystemExit(0)

def newest(pattern: str) -> str:
  candidates = list(target.rglob(pattern))
  if not candidates:
    return ""
  candidates.sort(key=lambda p: p.stat().st_mtime, reverse=True)
  return str(candidates[0])

# Prefer fastLinkJS output (fastopt) if present.
print(newest("*/**/*fastopt*/main.js") or newest("*/**/*fullopt*/main.js") or "")
PY
)"

if [[ -z "$bundle" ]]; then
  echo "[scala.js] Could not locate Scala.js bundle under $scala_dir/target" >&2
  exit 1
fi

mkdir -p "$component_dir/src"
cp "$bundle" "$component_dir/src/scala.js"

