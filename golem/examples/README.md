Examples overview

This directory ships a single Scala.js component (`scala:examples`) and a set of
focused sample RIB scripts under `samples/`. Each script exercises a specific
workflow and keeps the code path small.

Implementation details (component templates, build wiring) live in `golem.yaml`
and tracked `components-js/*/golem.yaml` manifests. Keep `golem.yaml` in the
module root (the directory you run `golem-cli` from); `.golem/` is generated.

Scala sources live under `shared/`, `js/`, and `jvm/` (shared traits + JS/JVM implementations).

Run any sample:

1) Build and deploy the component:

```bash
GOLEM_CLI_FLAGS="${GOLEM_CLI_FLAGS:---local}"
env -u ARGV0 golem-cli $GOLEM_CLI_FLAGS --yes --app-manifest-path "$PWD/golem.yaml" deploy
```

2) Invoke the sample with its RIB script:

```bash
env -u ARGV0 golem-cli $GOLEM_CLI_FLAGS --yes --app-manifest-path "$PWD/golem.yaml" \
  repl scala:examples --script-file "samples/<sample>/repl-<name>.rib" --disable-stream < /dev/null
```

Sample RIB scripts:

| Sample | RIB script |
| --- | --- |
| Simple RPC | `samples/simple-rpc/repl-counter.rib` |
| Agent-to-agent | `samples/agent-to-agent/repl-minimal-agent-to-agent.rib` |
| JSON tasks | `samples/json-tasks/repl-json-tasks.rib` |
| Snapshot counter | `samples/snapshot-counter/repl-snapshot-counter.rib` |
| Human-in-the-loop | `samples/human-in-the-loop/repl-human-in-the-loop.rib` |
| LLM chat | `samples/llm-chat/repl-llm-chat.rib` |
| Websearch summary | `samples/websearch-summary/repl-websearch-summary.rib` |

LLM chat prerequisites:

- Set `RUN_LLM_EXAMPLES=1` (the script skips otherwise).
- Use a non-builtin local server (set `FORCE_AI_ON_LOCAL=1`) or run in cloud mode:
  `GOLEM_CLI_FLAGS="--cloud -p <profile>"`.
- Provide the LLM provider WASM dependency in the component definition (examples use
  `golem.yaml` + `components-js/scala-examples/golem.yaml`). For example:
  `dependencies: [{ type: wasm, url: https://github.com/golemcloud/golem-ai/releases/download/v0.4.0/golem_llm_ollama.wasm }]`.
- Configure one provider env var (for local builtin, set it on the Golem server process):
  `ANTHROPIC_API_KEY`, `OPENAI_API_KEY`, `OPENROUTER_API_KEY`, `XAI_API_KEY`,
  `GOLEM_OLLAMA_BASE_URL`, or `AWS_ACCESS_KEY_ID` + `AWS_SECRET_ACCESS_KEY` + `AWS_REGION`.

Websearch summary prerequisites:

- Set `RUN_WEBSEARCH_EXAMPLES=1`.
- Ensure both the web-search and LLM provider WASM dependencies are configured for the component.
- Configure the same LLM provider env var(s) as above plus any web-search provider settings.

If you see `The environment was changed concurrently while diffing` during deploy,
retry the deploy command once; it is safe and typically resolves the race.
