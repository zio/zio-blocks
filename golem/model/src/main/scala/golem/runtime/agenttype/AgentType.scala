package golem.runtime.agenttype

import golem.data.GolemSchema
import golem.runtime.MethodMetadata

/**
 * Reflected structure of an agent trait for client-side calling: schemas, WIT
 * function names, and invocation kind.
 */
final case class AgentType[Trait, Constructor](
  traitClassName: String,
  typeName: String,
  constructor: ConstructorType[Constructor],
  methods: List[AgentType.AnyMethod[Trait]]
)

final case class ConstructorType[Input](schema: GolemSchema[Input])

final case class AgentMethod[Trait, Input, Output](
  metadata: MethodMetadata,
  functionName: String,
  inputSchema: GolemSchema[Input],
  outputSchema: GolemSchema[Output],
  invocation: MethodInvocation
)

object AgentType {
  type AnyMethod[Trait] = AgentMethod[Trait, _, _]
}

sealed trait MethodInvocation

object MethodInvocation {
  case object Awaitable     extends MethodInvocation
  case object FireAndForget extends MethodInvocation
}
