package cloud.golem.examples

import cloud.golem.examples.minimal.{
  Coordinator,
  CoordinatorImpl,
  Shard,
  ShardImpl,
  Worker,
  WorkerImpl
}
import cloud.golem.runtime.autowire.AgentImplementation
import scala.scalajs.js.annotation.JSExportTopLevel

/**
 * Forces agent registration to run at module initialization time (ES module import).
 *
 * This is intentionally a single exported value whose initializer registers all agents.
 */
object RegisterAgents {
  @JSExportTopLevel("__golemRegisterAgents")
  val __golemRegisterAgents: Unit = {
    AgentImplementation.register[Worker, (String, Int)](in => new WorkerImpl(in._1, in._2))
    AgentImplementation.register[Coordinator, String](id => new CoordinatorImpl(id))
    AgentImplementation.register[Shard, (String, Int)](in => new ShardImpl(in._1, in._2))
    ()
  }
}

