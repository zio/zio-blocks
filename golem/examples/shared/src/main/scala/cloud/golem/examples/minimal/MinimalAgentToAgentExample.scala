package cloud.golem.examples.minimal

import cloud.golem.runtime.annotations.{agentDefinition, description}
import cloud.golem.sdk.{AgentCompanion, BaseAgent}
import zio.blocks.schema.Schema

import scala.concurrent.Future

/**
 * Minimal “in-Golem” story:
 * - Two agents (Worker + Coordinator)
 * - Coordinator calls Worker via agent RPC (inside Golem)
 * - Only Scala agent traits are defined here (no JS/TS main required; build tooling generates the bridge)
 */
final case class TypedNested(x: Double, tags: List[String])
object TypedNested {
  implicit val schema: Schema[TypedNested] = Schema.derived
}
final case class TypedPayload(
  name: String,
  count: Int,
  note: Option[String],
  // Avoid collections of primitives here: Scala.js can erase List[Boolean]/List[Int] element types to Object in bytecode,
  // which breaks reflection-based auto-exports.
  flags: List[String],
  nested: TypedNested
)
object TypedPayload {
  implicit val schema: Schema[TypedPayload] = Schema.derived
}
final case class TypedReply(shardName: String, shardIndex: Int, reversed: String, payload: TypedPayload)
object TypedReply {
  implicit val schema: Schema[TypedReply] = Schema.derived
}

@agentDefinition()
@description("A minimal worker agent used for in-Golem agent-to-agent calling examples.")
trait Worker extends BaseAgent {
  type AgentInput = (String, Int)
  def reverse(input: String): Future[String]
  def handle(payload: TypedPayload): Future[TypedReply]
}
object Worker extends AgentCompanion[Worker]

@agentDefinition()
@description("A minimal coordinator agent that calls Worker via agent RPC inside Golem.")
trait Coordinator extends BaseAgent {
  type AgentInput = Unit
  def route(shardName: String, shardIndex: Int, input: String): Future[String]
  def routeTyped(shardName: String, shardIndex: Int, payload: TypedPayload): Future[TypedReply]
}
object Coordinator extends AgentCompanion[Coordinator]


