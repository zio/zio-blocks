#!/usr/bin/env bash
set -euo pipefail

# Generates a QuickJS-based `agent_guest.wasm` (guest runtime) for Scala.js-style agents.
#
# Why this exists:
# - The guest runtime is version-sensitive to the Golem server/CLI WIT surface.
# - When upgrading Golem, regenerating the guest runtime avoids mysterious linker/discovery failures.
#
# This script:
# 1) downloads `golem` WIT for a chosen tag (default: v1.4.1)
# 2) creates a minimal WIT package for `golem:agent` including snapshot exports
# 3) runs `wasm-rquickjs generate-wrapper-crate` and patches it to load user JS via `@composition` (`get-script`)
# 4) builds the component with `cargo component`
# 5) copies it into `golem/quickstart/wasm/agent_guest.wasm` and `golem/examples/wasm/agent_guest.wasm`
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
wit_dir="$gen_dir/golem-${tag}/wit"
agent_wit_root="$gen_dir/agent-wit-root-${tag}"
wrapper_dir="$gen_dir/agent-guest-wrapper-${tag}"
out_wasm="$wrapper_dir/target/wasm32-wasip1/release/agent_guest.wasm"

echo "[agent-guest] repo_root=$repo_root" >&2
echo "[agent-guest] tag=$tag" >&2

mkdir -p "$gen_dir"

if [[ ! -d "$wit_dir" ]]; then
  echo "[agent-guest] Downloading golem WIT for $tag..." >&2
  rm -rf "$gen_dir/golem-${tag}"
  mkdir -p "$gen_dir/golem-${tag}"

  # codeload tarballs unpack to `golem-<tag-without-leading-v>`; we strip that path.
  tag_no_v="${tag#v}"
  curl -L -sSf "https://codeload.github.com/golemcloud/golem/tar.gz/refs/tags/${tag}" \
    | tar -xz -C "$gen_dir/golem-${tag}" --strip-components=1 "golem-${tag_no_v}/wit"
fi

echo "[agent-guest] Building minimal golem:agent WIT root..." >&2
rm -rf "$agent_wit_root"
mkdir -p "$agent_wit_root/deps"

# Optional package versions vary across Golem releases; derive from the WIT we are building against.
rdbms_version=""
if [[ -f "$wit_dir/deps/golem-rdbms/world.wit" ]]; then
  rdbms_version="$(sed -n 's/^package golem:rdbms@\\([^;]*\\);$/\\1/p' "$wit_dir/deps/golem-rdbms/world.wit" | head -n 1)"
fi

{
  echo 'package golem:agent;'
  echo
  # Strip the original `package ...;` + following blank line from each file.
  sed '1,2d' "$wit_dir/deps/golem-agent/common.wit"
  echo
  sed '1,2d' "$wit_dir/deps/golem-agent/host.wit"
  echo
  # Insert snapshot exports into `world agent-guest`.
  sed '1,2d' "$wit_dir/deps/golem-agent/guest.wit" | awk -v rdbms_ver="$rdbms_version" '
    # Also import `host` into the agent-guest world so JS code can `import "golem:agent/host"`.
    # This matches the module surface used by the TS SDK and is required for agent-to-agent examples.
    $0 ~ /^[[:space:]]*import golem:rpc\/types@0\.2\.2;[[:space:]]*$/ {
      print $0
      print "  import host;"
      # Built-in host capabilities (feature parity with other SDKs):
      print ""
      if (rdbms_ver != "") {
        print "  import golem:rdbms/postgres@" rdbms_ver ";"
        print "  import golem:rdbms/mysql@" rdbms_ver ";"
        print "  import golem:rdbms/types@" rdbms_ver ";"
        print ""
      }
      print ""
      print "  import golem:api/context@1.3.0;"
      print "  import golem:api/oplog@1.3.0;"
      print "  import golem:durability/durability@1.3.0;"
      print ""
      print "  import wasi:cli/environment@0.2.3;"
      print ""
      print "  import wasi:blobstore/blobstore;"
      print "  import wasi:blobstore/container;"
      print "  import wasi:blobstore/types;"
      print ""
      print "  import wasi:keyvalue/eventual@0.1.0;"
      print "  import wasi:keyvalue/eventual-batch@0.1.0;"
      print "  import wasi:keyvalue/types@0.1.0;"
      print "  import wasi:keyvalue/wasi-keyvalue-error@0.1.0;"
      print ""
      print "  import wasi:config/store@0.2.0-draft;"
      print ""
      # AI capabilities (opt-in for users; imported so JS code can `import "golem:llm/llm@1.0.0"` etc):
      print "  import golem:llm/llm@1.0.0;"
      print "  import golem:web-search/web-search@1.0.0;"
      print "  import golem:web-search/types@1.0.0;"
      next
    }
    $0 ~ /^[[:space:]]*export guest;[[:space:]]*$/ {
      print $0
      print "  export golem:api/save-snapshot@1.3.0;"
      print "  export golem:api/load-snapshot@1.3.0;"
      next
    }
    { print }
  '
} > "$agent_wit_root/agent.wit"

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
  --world agent-guest \
  --js-modules 'user=@composition' \
  --output "$wrapper_dir"

echo "[agent-guest] Patching wrapper crate for golem-cli injection (@composition/get-script)..." >&2

# 1) cargo-component needs an explicit target world (the package contains agent-guest and agent-host).
python - <<PY
from pathlib import Path
p = Path("$wrapper_dir/Cargo.toml")
text = p.read_text()
if 'world = "golem:agent/agent-guest"' not in text:
    text = text.replace('[package.metadata.component.target]\\n', '[package.metadata.component.target]\\nworld = "golem:agent/agent-guest"\\n')
    text = text.replace('[package.metadata.component.target]\\n\\n', '[package.metadata.component.target]\\nworld = "golem:agent/agent-guest"\\n\\n')
    p.write_text(text)
PY

# 2) Make the runtime load the injected JS from the imported `get-script` as the virtual module `@composition`.
python - <<PY
from pathlib import Path
p = Path("$wrapper_dir/src/lib.rs")
text = p.read_text()
start = text.find('static JS_ADDITIONAL_MODULES:')
end = text.find('struct Component;', start)
if start == -1 or end == -1:
    raise SystemExit("Could not locate JS_ADDITIONAL_MODULES block in src/lib.rs")
new_block = '''static JS_ADDITIONAL_MODULES: std::sync::LazyLock<
    Vec<(&str, Box<dyn (Fn() -> String) + Send + Sync>)>,
> = std::sync::LazyLock::new(|| {
    vec![("@composition", Box::new(|| crate::bindings::get_script()))]
});
'''
p.write_text(text[:start] + new_block + text[end:])
PY

# 3) Make the embedded "user" module just re-export the injected @composition module.
cat > "$wrapper_dir/src/user.js" <<'JS'
// Loader stub.
//
// golem-cli's inject step provides the real JS bundle via the imported `get-script` function,
// exposed to QuickJS as a virtual module named `@composition`.
export * from '@composition';
JS

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

echo "[agent-guest] Installing into repo app runtimes..." >&2
install -m 0644 "$out_wasm" "$repo_root/golem/quickstart/wasm/agent_guest.wasm"
install -m 0644 "$out_wasm" "$repo_root/golem/examples/wasm/agent_guest.wasm"
install -m 0644 "$out_wasm" "$repo_root/golem/quickstart/js/wasm/agent_guest.wasm"
install -m 0644 "$out_wasm" "$repo_root/golem/examples/js/wasm/agent_guest.wasm"

echo "[agent-guest] Installing into plugin embedded resources..." >&2
install -m 0644 "$out_wasm" "$repo_root/golem/sbt/src/main/resources/golem/wasm/agent_guest.wasm"
install -m 0644 "$out_wasm" "$repo_root/golem/mill/resources/golem/wasm/agent_guest.wasm"

echo "[agent-guest] Done." >&2

