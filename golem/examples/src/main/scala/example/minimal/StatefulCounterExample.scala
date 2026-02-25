package example.minimal

import golem.runtime.annotations.{agentDefinition, description}
import golem.{AgentCompanion, BaseAgent}

import scala.concurrent.Future

final case class CounterState(initialCount: Int)

object CounterState {
  implicit val schema: zio.blocks.schema.Schema[CounterState] = zio.blocks.schema.Schema.derived
}

@agentDefinition(typeName = "stateful-counter")
@description("Counter whose constructor takes a custom case class (CounterState) instead of String.")
trait StatefulCounter extends BaseAgent[CounterState] {

  @description("Increments the counter and returns the new value.")
  def increment(): Future[Int]

  @description("Returns the current count without modifying it.")
  def current(): Future[Int]
}

object StatefulCounter extends AgentCompanion[StatefulCounter, CounterState]

@agentDefinition(typeName = "stateful-caller")
@description("Calls StatefulCounter remotely to exercise agent-to-agent RPC with a custom state type.")
trait StatefulCaller extends BaseAgent[CounterState] {

  @description("Increments the remote stateful counter and returns its new value.")
  def remoteIncrement(): Future[Int]
}

object StatefulCaller extends AgentCompanion[StatefulCaller, CounterState]
