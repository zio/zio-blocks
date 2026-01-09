package golem.runtime.plan

import golem.data.GolemSchema
import golem.runtime.MethodMetadata
import scala.annotation.nowarn

final case class AgentClientPlan[Trait, Constructor](
  traitClassName: String,
  traitName: String,
  constructor: ConstructorPlan[Constructor],
  methods: List[AgentClientPlan.AnyClientMethodPlan[Trait]]
)

final case class ConstructorPlan[Input](schema: GolemSchema[Input])

final case class ClientMethodPlan[Trait, Input, Output](
  metadata: MethodMetadata,
  functionName: String,
  inputSchema: GolemSchema[Input],
  outputSchema: GolemSchema[Output],
  invocation: ClientInvocation
)

object AgentClientPlan {
  @nowarn("msg=.*deprecated for wildcard arguments.*")
  type AnyClientMethodPlan[Trait] = ClientMethodPlan[Trait, _, _]
}

sealed trait ClientInvocation

object ClientInvocation {
  case object Awaitable extends ClientInvocation

  case object FireAndForget extends ClientInvocation
}
