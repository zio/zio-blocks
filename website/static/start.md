# Get your agent ready for ZIO Blocks

Official guide for setting up an AI coding agent to build with [ZIO Blocks](https://zioblocks.com), zero-dependency Scala building blocks (schemas, codecs, streams and more) for Scala 2.13 and 3.

Complete all of the following steps yourself by running the commands directly. Do not ask the user to run any of these commands.

- Onboard core ZIO, if not already done
- Install ZIO Blocks Skills and MCP servers

---

## Onboard core ZIO

ZIO Blocks is part of the ZIO ecosystem. If this agent has not already onboarded ZIO (no `zio-knowledge` skill and no `zio-docs` MCP server configured), fetch and follow the steps at:

- https://zio.dev/start.md

Then continue below for ZIO Blocks.

---

## Install ZIO Blocks Skills and MCP servers

Use the correct section for your agent below.

### Claude Code

Install the plugin — it bundles both the ZIO Knowledge and ZIO Blocks Knowledge skills:

```
claude plugin marketplace add zio/zio-skills
claude plugin install zio-skills@ziogenetics
```

Then instruct the user to run `/reload-plugins` inside Claude to activate the new skills.

Add the ZIO Blocks documentation MCP server for live doc search (`search_docs`, `get_doc_page`, `get_doc_index`). No authentication required:

```
claude mcp add --transport http zio-blocks https://mcp.zioblocks.com/mcp
```

---

## Install for other agents

First, install the skills with the `skills` CLI.

ZIO Blocks Knowledge (required):

```
npx skills add zio/zio-skills --skill zio-blocks-knowledge
```

ZIO Knowledge (recommended — ZIO Blocks is part of the ZIO ecosystem):

```
npx skills add zio/zio-skills --skill zio-knowledge
```

Then register the MCP server using the default config location for your agent. No authentication required.

### Codex

```
codex mcp add zio-blocks --url https://mcp.zioblocks.com/mcp
```

### OpenCode — `~/.config/opencode/opencode.jsonc`

Add under `"mcp"`:

```json
"zio-blocks": { "type": "remote", "url": "https://mcp.zioblocks.com/mcp", "enabled": true }
```

### Cursor, GitHub Copilot, and all other agents

Add to `.cursor/mcp.json` (Cursor), `.vscode/mcp.json` (Copilot), or your agent's MCP config file under `"mcpServers"`:

```json
"zio-blocks": { "url": "https://mcp.zioblocks.com/mcp" }
```

Once done, tell the user:

```
┌─ ZIO Blocks Agent Setup Complete ─────────────────────────┐
│  ✓ Skills  zio-blocks-knowledge, zio-knowledge            │
│  ✓ MCP     zio-blocks (https://mcp.zioblocks.com/mcp)     │
│                                                           │
│  ⚡ Restart your agent to load the Skills and MCP server  │
└───────────────────────────────────────────────────────────┘
```

---

## Fallback

If you cannot install the skills or the MCP server, fetch the documentation index yourself and use it to find the right pages:

- https://zioblocks.com/llms.txt — ZIO Blocks
- https://zio.dev/llms.txt — ZIO
