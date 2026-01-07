package golem.quickstart

import golem.quickstart.counter.{CounterAgent, CounterAgentImpl}
import golem.quickstart.shard.{ShardAgent, ShardAgentImpl}
import golem.runtime.autowire.AgentImplementation

import scala.scalajs.js.annotation.JSExportTopLevel

/**
 * Forces agent registration to run at module initialization time (ES module
 * import).
 *
 * This is intentionally a single exported value whose initializer registers all
 * agents.
 */
object RegisterAgents {
  @JSExportTopLevel("__golemRegisterAgents")
  val __golemRegisterAgents: Unit = {
    AgentImplementation.register[CounterAgent, String](name => new CounterAgentImpl(name))
    AgentImplementation.register[ShardAgent, (String, Int)](in => new ShardAgentImpl(in._1, in._2))
    ()
  }
}
