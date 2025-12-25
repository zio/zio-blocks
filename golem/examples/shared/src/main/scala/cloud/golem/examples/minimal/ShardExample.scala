package cloud.golem.examples.minimal

import cloud.golem.runtime.annotations.{DurabilityMode, agentDefinition, agentImplementation, description}
import cloud.golem.sdk.{AgentCompanion, BaseAgent}

/**
 * Minimal, Scala-only example matching the intended SDK user experience.
 *
 * Any packaging/deploy plumbing remains repo-local and is not part of the user story.
 */
@agentDefinition(mode = DurabilityMode.Durable)
trait Shard extends BaseAgent {
  type AgentInput = (String, Int)

  @description("Get a value from the table")
  def get(key: String): Option[String]

  @description("Set a value in the table")
  def set(key: String, value: String): Unit
}

// Today this one-liner is required by Scala to attach the companion API.
object Shard extends AgentCompanion[Shard]

@agentImplementation()
final class ShardImpl(private val tableName: String, private val shardId: Int) extends Shard {
  override def get(key: String): Option[String] =
    Some(s"$tableName:$shardId:$key")

  override def set(key: String, value: String): Unit = ()
}


