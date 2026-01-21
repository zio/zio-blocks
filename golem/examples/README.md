Examples overview

This directory ships a single Scala.js component (`scala:examples`) and a set of
focused sample scripts under `samples/`. Each script exercises a specific
workflow and keeps the code path small.

Run any sample:

- `./counter-local-repl.sh` (simple RPC)
- `./agent2agent-local-repl.sh` (agent-to-agent)
- `./json-tasks-local-repl.sh`
- `./snapshot-counter-local-repl.sh`
- `./hitl-local-repl.sh` (human-in-the-loop)
- `./llm-chat-local-repl.sh` (requires `golem:llm`)
- `./websearch-summary-local-repl.sh` (requires `golem:web-search`)

Sample scripts and their RIB sources:

| Sample | RIB script | Shell script |
| --- | --- | --- |
| Simple RPC | `samples/simple-rpc/repl-counter.rib` | `counter-local-repl.sh` |
| Agent-to-agent | `samples/agent-to-agent/repl-minimal-agent-to-agent.rib` | `agent2agent-local-repl.sh` |
| JSON tasks | `samples/json-tasks/repl-json-tasks.rib` | `json-tasks-local-repl.sh` |
| Snapshot counter | `samples/snapshot-counter/repl-snapshot-counter.rib` | `snapshot-counter-local-repl.sh` |
| Human-in-the-loop | `samples/human-in-the-loop/repl-human-in-the-loop.rib` | `hitl-local-repl.sh` |
| LLM chat | `samples/llm-chat/repl-llm-chat.rib` | `llm-chat-local-repl.sh` |
| Websearch summary | `samples/websearch-summary/repl-websearch-summary.rib` | `websearch-summary-local-repl.sh` |
