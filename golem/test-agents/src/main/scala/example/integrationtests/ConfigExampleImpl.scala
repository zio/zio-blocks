package example.integrationtests

import golem.config.Config
import golem.runtime.annotations.agentImplementation

import scala.concurrent.Future

@agentImplementation()
final class ConfigAgentImpl(input: String, config: Config[MyAppConfig]) extends ConfigAgent {
  override def greet(): Future[String] = {
    val cfg     = config.value
    val appName = cfg.appName
    val host    = cfg.db.host
    val port    = cfg.db.port
    Future.successful(s"Hello from $appName! DB at $host:$port, input=$input")
  }
}

@agentImplementation()
final class ConfigCallerAgentImpl(input: String) extends ConfigCallerAgent {
  override def callWithOverride(): Future[String] = {
    val configAgent = ConfigAgentClient.getWithConfig(
      input,
      appName = Some("OverriddenApp"),
      dbHost = Some("overridden-host.example.com"),
      dbPort = Some(9999)
    )
    configAgent.greet()
  }
}
