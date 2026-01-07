package golem.runtime.plan

import golem.data.GolemSchema
import golem.runtime.{AgentMetadata, MethodMetadata}

import scala.concurrent.Future

final case class AgentImplementationPlan[Instance, Ctor](
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
