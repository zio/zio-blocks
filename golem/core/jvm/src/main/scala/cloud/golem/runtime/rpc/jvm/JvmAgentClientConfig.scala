package cloud.golem.runtime.rpc.jvm

/**
 * Repo-local JVM client configuration used by the CLI-backed testing client.
 *
 * This is **not** meant to be a public/production client API.
 */
final case class JvmAgentClientConfig(
  component: String,
  golemCli: String = "golem-cli",
  golemCliFlags: Vector[String] = Vector("--local")
)

object JvmAgentClientConfig {
  def fromEnv(
    defaultComponent: String,
    defaultFlags: Vector[String] = Vector("--local")
  ): JvmAgentClientConfig = {
    val component = sys.env.getOrElse("GOLEM_COMPONENT", defaultComponent)
    val golemCli  = sys.env.getOrElse("GOLEM_CLI", "golem-cli")
    val flags =
      sys.env
        .get("GOLEM_CLI_FLAGS")
        .map(_.trim)
        .filter(_.nonEmpty)
        .map(_.split("\\s+").toVector)
        .getOrElse(defaultFlags)

    JvmAgentClientConfig(component = component, golemCli = golemCli, golemCliFlags = flags)
  }
}


