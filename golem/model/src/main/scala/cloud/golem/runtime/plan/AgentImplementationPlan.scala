package cloud.golem.runtime.plan

import cloud.golem.data.GolemSchema
import cloud.golem.runtime.{AgentMetadata, MethodMetadata}

import scala.concurrent.Future

final case class AgentImplementationPlan[Instance](
  metadata: AgentMetadata,
  buildInstance: () => Instance,
  methods: List[MethodPlan[Instance]]
)

/**
 * Agent implementation plan where the agent instance depends on constructor
 * input.
 *
 * @param constructorSchema
 *   Codec for the agent constructor input (derived from `type AgentInput = ...`
 *   on the agent trait)
 * @param buildInstance
 *   Builds an instance from decoded constructor input
 */
final case class AgentImplementationPlanWithCtor[Instance, Ctor](
  metadata: AgentMetadata,
  constructorSchema: GolemSchema[Ctor],
  buildInstance: Ctor => Instance,
  methods: List[MethodPlan[Instance]]
)

sealed trait MethodPlan[Instance] {
  def metadata: MethodMetadata
}

final case class SyncMethodPlan[Instance, In, Out](
  metadata: MethodMetadata,
  inputSchema: GolemSchema[In],
  outputSchema: GolemSchema[Out],
  handler: (Instance, In) => Out
) extends MethodPlan[Instance]

final case class AsyncMethodPlan[Instance, In, Out](
  metadata: MethodMetadata,
  inputSchema: GolemSchema[In],
  outputSchema: GolemSchema[Out],
  handler: (Instance, In) => Future[Out]
) extends MethodPlan[Instance]
