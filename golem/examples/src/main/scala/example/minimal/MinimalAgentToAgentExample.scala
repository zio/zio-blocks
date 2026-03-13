package example.minimal

import golem.runtime.annotations.{agentDefinition, description, DurabilityMode}
import golem.{AgentCompanion, BaseAgent}
import zio.blocks.schema.Schema

import scala.concurrent.Future

final case class TypedNested(x: Double, tags: List[String])
object TypedNested {
  implicit val schema: Schema[TypedNested] = Schema.derived
}
final case class TypedPayload(
  name: String,
  count: Int,
  note: Option[String],
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
trait Worker extends BaseAgent[(String, Int)] {
  def reverse(input: String): Future[String]
  def handle(payload: TypedPayload): Future[TypedReply]
}
object Worker extends AgentCompanion[Worker, (String, Int)]

@agentDefinition(mode = DurabilityMode.Ephemeral)
@description("A minimal coordinator agent that calls Worker via agent RPC inside Golem.")
trait Coordinator extends BaseAgent[String] {
  def route(shardName: String, shardIndex: Int, input: String): Future[String]
  def routeTyped(shardName: String, shardIndex: Int, payload: TypedPayload): Future[TypedReply]
}
object Coordinator extends AgentCompanion[Coordinator, String]
