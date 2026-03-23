package example.minimal

import golem.config.{ConfigHolder, ConfigOverride}
import golem.runtime.annotations.agentImplementation

import scala.concurrent.Future

@agentImplementation()
final class ConfigAgentImpl(input: String) extends ConfigAgent {
  private val config = ConfigHolder.current[MyAppConfig]

  override def greet(): Future[String] = {
    val appName = config.value.appName
    val host = config.value.db.host
    val port = config.value.db.port
    Future.successful(s"Hello from $appName! DB at $host:$port, input=$input")
  }
}

@agentImplementation()
final class ConfigCallerAgentImpl(input: String) extends ConfigCallerAgent {
  override def callWithOverride(): Future[String] = {
    val overrides = List(
      ConfigOverride[String](List("appName"), "OverriddenApp"),
      ConfigOverride[String](List("db", "host"), "overridden-host.example.com"),
      ConfigOverride[Int](List("db", "port"), 9999)
    )
    val configAgent = ConfigAgent.getWithConfig(input, overrides)
    configAgent.greet()
  }
}
