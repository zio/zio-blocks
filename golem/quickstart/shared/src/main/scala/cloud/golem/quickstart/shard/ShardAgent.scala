package cloud.golem.quickstart.shard

import cloud.golem.runtime.annotations.{DurabilityMode, agentDefinition, description}
import cloud.golem.sdk.{AgentCompanion, BaseAgent}

import scala.concurrent.Future

@agentDefinition("shard-agent", mode = DurabilityMode.Durable)
trait ShardAgent extends BaseAgent {
  final type AgentInput = (String, Int)

  @description("Returns the shard id.")
  def id(): Future[Int]

  @description("Get a value from the table")
  def get(key: String): Future[Option[String]]

  @description("Set a value in the table")
  def set(key: String, value: String): Future[Unit]
}

object ShardAgent extends AgentCompanion[ShardAgent]
