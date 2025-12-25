package cloud.golem.runtime.plan

import cloud.golem.data.GolemSchema
import cloud.golem.runtime.MethodMetadata
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

object ClientMethodPlan {
  type Aux[Trait, In, Out] = ClientMethodPlan[Trait, In, Out]
}

object AgentClientPlan {
  // Cross-build note: Scala 3 prefers `?` for wildcard type arguments, but Scala 2 requires `_`.
  // Keep the Scala 2 form here and suppress the Scala 3 deprecation warning locally.
  @nowarn("msg=.*deprecated for wildcard arguments.*")
  type AnyClientMethodPlan[Trait] = ClientMethodPlan[Trait, _, _]
}

sealed trait ClientInvocation

object ClientInvocation {
  case object Awaitable extends ClientInvocation

  case object FireAndForget extends ClientInvocation
}
