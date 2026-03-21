#!/usr/bin/env bash
set -euo pipefail

# Generates a QuickJS-based `agent_guest.wasm` (guest runtime) for Scala.js-style agents.
#
# Why this exists:
# - The guest runtime is version-sensitive to the Golem server/CLI WIT surface.
# - When upgrading Golem, regenerating the guest runtime avoids mysterious linker/discovery failures.
#
# This script:
# 1) resolves WIT dependencies using `wit-deps` (from wit/deps.toml)
# 2) stages a WIT package for `golem:agent-guest` (using wit/main.wit + wit/deps/)
# 3) runs `wasm-rquickjs generate-wrapper-crate` (injecting `@composition` for golem-cli)
#    Note: unlike the TS SDK, we do NOT embed a separate SDK JS module here.
#    Scala.js bundles the SDK into the user's `scala.js`, which golem-cli injects later.
# 4) builds the component with `cargo component`
# 5) updates embedded plugin resources (used by sbt/mill plugins).
#
# Requirements:
# - `wit-deps` (cargo install wit-deps-cli)
# - `wasm-rquickjs` (from crate `wasm-rquickjs-cli`)
# - Rust toolchain + `cargo-component` (installed by `cargo install cargo-component`)
#
# Usage:
#   ./golem/tools/generate-agent-guest-wasm.sh
#

repo_root="$(cd "$(dirname "$0")/../.." && pwd)"

REQUIRED_WASM_RQUICKJS_VERSION="0.1.0"

actual_version="$(wasm-rquickjs --version 2>/dev/null || true)"
if [[ -z "$actual_version" ]]; then
  echo "[agent-guest] ERROR: wasm-rquickjs not found. Install it with: cargo install wasm-rquickjs-cli@${REQUIRED_WASM_RQUICKJS_VERSION}" >&2
  exit 1
fi
if [[ "$actual_version" != *"$REQUIRED_WASM_RQUICKJS_VERSION"* ]]; then
  echo "[agent-guest] ERROR: wasm-rquickjs version mismatch." >&2
  echo "[agent-guest]   Required: $REQUIRED_WASM_RQUICKJS_VERSION" >&2
  echo "[agent-guest]   Found:    $actual_version" >&2
  echo "[agent-guest]   Fix with: cargo install wasm-rquickjs-cli@${REQUIRED_WASM_RQUICKJS_VERSION}" >&2
  exit 1
fi

wit_dir="$repo_root/golem/wit"
gen_dir="$repo_root/.generated"
agent_wit_root="$gen_dir/agent-wit-root"
wrapper_dir="$gen_dir/agent-guest-wrapper"
out_wasm="$wrapper_dir/target/wasm32-wasip1/release/agent_guest.wasm"

echo "[agent-guest] repo_root=$repo_root" >&2

mkdir -p "$gen_dir"

if [[ ! -f "$wit_dir/main.wit" ]]; then
  echo "[agent-guest] ERROR: missing WIT definition at $wit_dir/main.wit" >&2
  exit 1
fi

echo "[agent-guest] Resolving WIT dependencies..." >&2
( cd "$repo_root/golem" && wit-deps update )

echo "[agent-guest] Staging WIT package for golem:agent-guest..." >&2
rm -rf "$agent_wit_root"
mkdir -p "$agent_wit_root"

cp "$wit_dir/main.wit" "$agent_wit_root/main.wit"
mkdir -p "$agent_wit_root/deps"
for dep in "$wit_dir"/deps/*/; do
  dep_name="$(basename "$dep")"
  # Skip the 'all' directory — it contains the golem repo's root host.wit which
  # declares an older golem:api version and conflicts with the actual deps.
  [[ "$dep_name" == "all" ]] && continue
  cp -r "$dep" "$agent_wit_root/deps/$dep_name"
done

dts_dir="$gen_dir/agent-guest-dts"
echo "[agent-guest] Generating TypeScript d.ts definitions..." >&2
rm -rf "$dts_dir"
wasm-rquickjs generate-dts \
  --wit "$agent_wit_root" \
  --world golem:agent-guest/agent-guest \
  --output "$dts_dir"
echo "[agent-guest] TypeScript definitions written to $dts_dir" >&2
ls -1 "$dts_dir"/*.d.ts 2>/dev/null | while read -r f; do echo "  $(basename "$f")"; done >&2

echo "[agent-guest] Generating wrapper crate with wasm-rquickjs..." >&2
rm -rf "$wrapper_dir"
wasm-rquickjs generate-wrapper-crate \
  --wit "$agent_wit_root" \
  --world golem:agent-guest/agent-guest \
  --js-modules "golem-scala-sdk=$gen_dir/golem-scala-sdk.mjs" \
  --js-modules "user=@composition" \
  --output "$wrapper_dir"

echo "[agent-guest] Building guest runtime (cargo component build --release)..." >&2
if [[ -f "$HOME/.cargo/env" ]]; then
  # shellcheck disable=SC1090
  . "$HOME/.cargo/env"
fi

( cd "$wrapper_dir" && env -u ARGV0 rustup run stable cargo component build --release )

if [[ ! -f "$out_wasm" ]]; then
  echo "[agent-guest] ERROR: build did not produce $out_wasm" >&2
  exit 1
fi

echo "[agent-guest] Built: $out_wasm" >&2
sha256sum "$out_wasm" >&2

echo "[agent-guest] Installing into plugin embedded resources..." >&2
install -m 0644 "$out_wasm" "$repo_root/golem/sbt/src/main/resources/golem/wasm/agent_guest.wasm"
install -m 0644 "$out_wasm" "$repo_root/golem/mill/resources/golem/wasm/agent_guest.wasm"

echo "[agent-guest] Copying TypeScript d.ts definitions to golem/wit/dts/..." >&2
rm -rf "$repo_root/golem/wit/dts"
cp -r "$dts_dir" "$repo_root/golem/wit/dts"

echo "[agent-guest] Done." >&2
