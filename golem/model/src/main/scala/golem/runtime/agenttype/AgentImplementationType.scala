package golem.runtime.agenttype

import golem.data.GolemSchema
import golem.runtime.{AgentMetadata, MethodMetadata}

import scala.concurrent.Future

/**
 * Reflected structure + handlers for an agent implementation.
 *
 * This is produced by macros and consumed by the runtime to wire incoming
 * calls.
 */
final case class AgentImplementationType[Instance, Ctor](
  metadata: AgentMetadata,
  constructorSchema: GolemSchema[Ctor],
  buildInstance: Ctor => Instance,
  methods: List[ImplementationMethod[Instance]]
)

sealed trait ImplementationMethod[Instance] {
  def metadata: MethodMetadata
}

final case class SyncImplementationMethod[Instance, In, Out](
  metadata: MethodMetadata,
  inputSchema: GolemSchema[In],
  outputSchema: GolemSchema[Out],
  handler: (Instance, In) => Out
) extends ImplementationMethod[Instance]

final case class AsyncImplementationMethod[Instance, In, Out](
  metadata: MethodMetadata,
  inputSchema: GolemSchema[In],
  outputSchema: GolemSchema[Out],
  handler: (Instance, In) => Future[Out]
) extends ImplementationMethod[Instance]
