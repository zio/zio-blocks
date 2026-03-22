package golem

/**
 * Base trait for Scala agent interfaces.
 *
 * The type parameter `Input` defines the agent's constructor parameters.
 * When the agent is mounted over HTTP via `@agentDefinition(mount = "...")`,
 * mount path variables must match the names derived from `Input`:
 *
 *   - '''Case class''' (e.g. `BaseAgent[MyParams]` where
 *     `case class MyParams(region: String, catalog: String)`):
 *     the case class fields are flattened into individual named constructor
 *     parameters. Use the field names as path variables.
 *     The case class must have a `zio.blocks.schema.Schema` instance
 *     (e.g. `Schema.derived`).
 *     Example: `@agentDefinition(mount = "/api/{region}/{catalog}")`
 *
 *   - '''Single primitive''' (e.g. `BaseAgent[String]`): one parameter named
 *     `"value"`. Use `{value}` in the mount path.
 *     Example: `@agentDefinition(mount = "/api/agents/{value}")`
 *
 *   - '''Tuple''' (e.g. `BaseAgent[(String, Int)]`): positional parameters named
 *     `"arg0"`, `"arg1"`, etc. Use `{arg0}`, `{arg1}` in the mount path.
 *     Example: `@agentDefinition(mount = "/api/{arg0}/{arg1}")`
 *
 *   - '''Unit''' (`BaseAgent[Unit]`): no constructor parameters. The mount path must
 *     not contain any variables.
 *
 * When running inside Golem, these values are provided by the host runtime.
 */
trait BaseAgent[Input] {
  final def agentId: String = BaseAgentPlatform.agentId

  final def agentType: String = BaseAgentPlatform.agentType

  final def agentName: String = BaseAgentPlatform.agentName
}
