package golem.runtime.agenttype

import golem.Principal
import golem.config.ConfigBuilder
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
  buildInstance: (Ctor, Principal) => Instance,
  methods: List[ImplementationMethod[Instance]],
  configBuilder: Option[ConfigBuilder[_]] = None,
  configInjectedViaConstructor: Boolean = false,
  principalInjectedViaConstructor: Boolean = false,
  snapshotHandlers: Option[SnapshotHandlers[Instance]] = None
)

sealed trait ImplementationMethod[Instance] {
  def metadata: MethodMetadata
}

final case class SyncImplementationMethod[Instance, In, Out](
  metadata: MethodMetadata,
  inputSchema: GolemSchema[In],
  outputSchema: GolemSchema[Out],
  handler: (Instance, In, Principal) => Out
) extends ImplementationMethod[Instance]

final case class AsyncImplementationMethod[Instance, In, Out](
  metadata: MethodMetadata,
  inputSchema: GolemSchema[In],
  outputSchema: GolemSchema[Out],
  handler: (Instance, In, Principal) => Future[Out]
) extends ImplementationMethod[Instance]
