## Scala.js quickstart (Counter agent)

This quickstart lives **in-repo** as the `zioGolemQuickstart` cross-project:

- **shared**: agent traits + companions
- **js**: agent implementations (Scala.js)

### What you write (Scala)

- **Agent trait + annotations**: `golem/quickstart/shared/src/main/scala/cloud/golem/quickstart/counter/CounterAgent.scala`
- **Agent implementation**: `golem/quickstart/js/src/main/scala/cloud/golem/quickstart/counter/CounterAgentImpl.scala`

### What you configure (sbt)

In the root `build.sbt`, `zioGolemQuickstartJS` configures only:

- which component to deploy (`golemAppName`, `golemComponent`)

Everything else needed for deployment (internal TS scaffold + `src/main.ts` + Scala shim code) is generated into `target/` by the plugin.

### Constructor shape

This quickstart uses a **scalar constructor** (`type AgentInput = String`), so in the REPL you create agents like:

- `counter-agent("agent-1")`

Other supported shapes (repo-internal bridge support):

- `Unit` (no-arg constructor): `agent()`
- tuples (e.g. `(String, Int)`): `agent("a", 1)` (positional constructor)

For example, a multi-arg constructor agent trait would look like:

```scala
import cloud.golem.runtime.annotations.{DurabilityMode, agentDefinition}
import cloud.golem.sdk.BaseAgent
import scala.concurrent.Future

@agentDefinition("shard-agent", mode = DurabilityMode.Durable)
trait ShardAgent extends BaseAgent {
  type AgentInput = (String, Int)
  def ping(): Future[String]
}
```

### Run locally

Use `golem-cli` as the driver from the checked-in app directory:

```bash
GOLEM_CLI_FLAGS="${GOLEM_CLI_FLAGS:---local}"
cd golem/quickstart/app
npm install
env -u ARGV0 golem-cli $GOLEM_CLI_FLAGS --yes app deploy scala:quickstart-counter
```

Then run the REPL script:

```bash
env -u ARGV0 golem-cli $GOLEM_CLI_FLAGS --yes repl scala:quickstart-counter --script-file "$PWD/../../golem/quickstart/script-test.rib" --disable-stream
```

### Agent-to-agent calling inside Golem (the core requirement)

The primary goal of this SDK is **Scala agents calling other agents while running inside Golem**.

You can see agent-to-agent calling patterns in the repo’s examples (Coordinator → Shard style), where an agent
implementation calls another agent via the companion API (`AgentCompanion.get(...)`) and the RPC happens inside Golem.

### Prerequisites

- **`golem-cli`** on your `PATH`
- **Node.js** + **npm** on your `PATH` (used internally by the component template; you do not write TS)
- A reachable Golem router/executor (local or cloud, depending on `GOLEM_CLI_FLAGS`)


