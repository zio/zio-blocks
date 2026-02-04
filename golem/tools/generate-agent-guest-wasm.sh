#!/usr/bin/env bash
set -euo pipefail

# Generates a QuickJS-based `agent_guest.wasm` (guest runtime) for Scala.js-style agents.
#
# Why this exists:
# - The guest runtime is version-sensitive to the Golem server/CLI WIT surface.
# - When upgrading Golem, regenerating the guest runtime avoids mysterious linker/discovery failures.
#
# This script:
# 1) uses a local `golem` WIT directory for a chosen tag (default: v1.4.1)
#    (set GOLEM_WIT_DIR to override)
# 2) stages a WIT package for `golem:agent` (using a static agent.wit + deps)
# 3) runs `wasm-rquickjs generate-wrapper-crate` (injecting `@composition` for golem-cli)
#    Note: unlike the TS SDK, we do NOT embed a separate SDK JS module here.
#    Scala.js bundles the SDK into the user's `scala.js`, which golem-cli injects later.
# 4) builds the component with `cargo component`
# 5) updates embedded plugin resources (used by sbt/mill plugins).
#
# Requirements:
# - `curl`, `tar`
# - `wasm-rquickjs` (from crate `wasm-rquickjs-cli`)
# - Rust toolchain + `cargo-component` (installed by `cargo install cargo-component`)
#
# Usage:
#   ./golem/tools/generate-agent-guest-wasm.sh              # defaults to v1.4.1
#   ./golem/tools/generate-agent-guest-wasm.sh v1.4.1
#

repo_root="$(cd "$(dirname "$0")/../.." && pwd)"

tag="${1:-v1.4.1}"

gen_dir="$repo_root/.generated"
wit_dir="${GOLEM_WIT_DIR:-$repo_root/golem/tools/wit-${tag}/wit}"
agent_wit_root="$gen_dir/agent-wit-root-${tag}"
agent_wit_template="$repo_root/golem/tools/agent-wit/agent.wit"
wrapper_dir="$gen_dir/agent-guest-wrapper-${tag}"
out_wasm="$wrapper_dir/target/wasm32-wasip1/release/agent_guest.wasm"

echo "[agent-guest] repo_root=$repo_root" >&2
echo "[agent-guest] tag=$tag" >&2

mkdir -p "$gen_dir"

if [[ ! -d "$wit_dir" ]]; then
  echo "[agent-guest] ERROR: golem WIT dir not found: $wit_dir" >&2
  echo "[agent-guest] Provide a local WIT checkout via GOLEM_WIT_DIR or vendor it at $repo_root/golem/tools/wit-${tag}/wit" >&2
  exit 1
fi
echo "[agent-guest] Using local WIT dir: $wit_dir" >&2

echo "[agent-guest] Staging WIT package for golem:agent..." >&2
rm -rf "$agent_wit_root"
mkdir -p "$agent_wit_root/deps"

if [[ ! -f "$agent_wit_template" ]]; then
  echo "[agent-guest] ERROR: missing WIT template at $agent_wit_template" >&2
  exit 1
fi

cp "$agent_wit_template" "$agent_wit_root/agent.wit"

cp -r "$wit_dir/deps/golem-rpc" "$agent_wit_root/deps/"
cp -r "$wit_dir/deps/golem-1.x" "$agent_wit_root/deps/"
cp -r "$wit_dir/deps/golem-rdbms" "$agent_wit_root/deps/"
cp -r "$wit_dir/deps/golem-durability" "$agent_wit_root/deps/"
cp -r "$wit_dir/deps/clocks" "$agent_wit_root/deps/"
cp -r "$wit_dir/deps/io" "$agent_wit_root/deps/"
cp -r "$wit_dir/deps/logging" "$agent_wit_root/deps/"
cp -r "$wit_dir/deps/cli" "$agent_wit_root/deps/"
cp -r "$wit_dir/deps/filesystem" "$agent_wit_root/deps/"
cp -r "$wit_dir/deps/random" "$agent_wit_root/deps/"
cp -r "$wit_dir/deps/sockets" "$agent_wit_root/deps/"
cp -r "$wit_dir/deps/blobstore" "$agent_wit_root/deps/"
cp -r "$wit_dir/deps/keyvalue" "$agent_wit_root/deps/"
cp -r "$wit_dir/deps/config" "$agent_wit_root/deps/"

# AI WIT packages are not part of the golem v1.4.1 WIT tarball; we vendor the stable definitions we need.
cp -r "$repo_root/golem/tools/wit-ai/golem-llm" "$agent_wit_root/deps/"
cp -r "$repo_root/golem/tools/wit-ai/golem-web-search" "$agent_wit_root/deps/"
cp -r "$repo_root/golem/tools/wit-ai/golem-embed" "$agent_wit_root/deps/"
cp -r "$repo_root/golem/tools/wit-ai/golem-graph" "$agent_wit_root/deps/"
cp -r "$repo_root/golem/tools/wit-ai/golem-video-generation" "$agent_wit_root/deps/"

echo "[agent-guest] Generating wrapper crate with wasm-rquickjs..." >&2
rm -rf "$wrapper_dir"
wasm-rquickjs generate-wrapper-crate \
  --wit "$agent_wit_root" \
  --world golem:agent/agent-guest \
  --js-modules "user=@composition" \
  --js-modules "@composition=@composition" \
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

echo "[agent-guest] Done." >&2

