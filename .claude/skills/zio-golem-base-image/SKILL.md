---
name: zio-golem-base-image
description: Explains the zio-golem WIT folder structure and how to regenerate the agent_guest.wasm base image. Use when working with WIT definitions, upgrading Golem versions, or regenerating the guest runtime WASM.
---

# zio-golem Base Image (agent_guest.wasm)

The base image `agent_guest.wasm` is a QuickJS-based WASM component that serves as the guest runtime for Scala.js agents on Golem. It must be regenerated whenever WIT definitions change.

## WIT Folder Structure

```
golem/wit/
├── main.wit      # Hand-maintained world definition (golem:agent-guest)
├── deps.toml     # wit-deps manifest — points to golemcloud/golem main branch
├── deps.lock     # Auto-generated lock file (gitignored)
└── deps/         # Auto-populated by wit-deps (gitignored)
    ├── golem-core/
    ├── golem-agent/
    ├── golem-1.x/
    ├── golem-rdbms/
    ├── golem-durability/
    ├── blobstore/
    ├── cli/
    ├── clocks/
    ├── config/
    ├── ...
    └── sockets/
```

- **`main.wit`** defines the `golem:agent-guest` world — the set of imports/exports the agent component uses. This file is checked in and maintained manually.
- **`deps.toml`** declares a single dependency source: the golem repo's main branch tarball. `wit-deps` downloads and extracts the WIT packages from it.
- **`deps/`** and **`deps.lock`** are gitignored — they are populated by running `wit-deps` from `golem/`.

## When to Regenerate

The base image **must be regenerated** whenever:

1. **`wit/main.wit` changes** — adding/removing imports or exports
2. **WIT dependencies update** — e.g., upgrading from Golem v1.5.0 to a newer version (update `deps.toml` then regenerate)
3. **`wasm-rquickjs` updates** — a new version of the wrapper generator may produce different output

The generated `agent_guest.wasm` is checked in at two locations (embedded in the sbt and mill plugins):
- `golem/sbt/src/main/resources/golem/wasm/agent_guest.wasm`
- `golem/mill/resources/golem/wasm/agent_guest.wasm`

## Prerequisites

### 1. Rust toolchain

```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
rustup target add wasm32-wasip1
```

### 2. cargo-component

```bash
cargo install cargo-component
```

### 3. wit-deps

```bash
cargo install wit-deps-cli
```

### 4. wasm-rquickjs (pinned to 0.1.0)

The script enforces a specific version of `wasm-rquickjs` and will refuse to run if the installed version does not match. The required version is defined by `REQUIRED_WASM_RQUICKJS_VERSION` in `generate-agent-guest-wasm.sh`.

```bash
cargo install wasm-rquickjs-cli@0.1.0
```

## How to Regenerate

From the **repository root** (`zio-blocks/`):

```bash
./golem/tools/generate-agent-guest-wasm.sh
```

The script performs these steps:

1. Runs `wit-deps update` in `golem/` to fetch the latest WIT dependencies into `golem/wit/deps/`
2. Stages a clean WIT package in `.generated/agent-wit-root/` (copies `main.wit` + `deps/`)
3. Runs `wasm-rquickjs generate-wrapper-crate` to produce a Rust crate from the WIT
4. Builds with `cargo component build --release` targeting `wasm32-wasip1`
5. Installs the resulting `agent_guest.wasm` into both plugin resource directories

## Fetching Dependencies Only

To update the WIT dependencies without regenerating the WASM:

```bash
cd golem && wit-deps
```

The generation script always uses `wit-deps update` to ensure deps are fresh. To update deps without a full regeneration:

```bash
cd golem && wit-deps update
```

## How It Fits Together

At build time, the sbt/mill `GolemPlugin` extracts the embedded `agent_guest.wasm` from plugin resources and writes it to the user project's `.generated/agent_guest.wasm`. Then `golem-cli` uses this base runtime to compose the final component: it injects the user's Scala.js bundle into the QuickJS runtime and wraps it as a proper Golem agent component.

The Scala SDK does **not** parse WIT to generate Scala bindings. Instead, Scala macros + ZIO Schema produce `AgentMetadata` at compile time, and `WitTypeBuilder` maps schema types to WIT-compatible JS representations at runtime. The WIT definitions only flow through the WASM guest runtime.
