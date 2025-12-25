package cloud.golem.runtime.autowire

import cloud.golem.data.GolemSchema
import cloud.golem.runtime.plan.{AgentImplementationPlan, AgentImplementationPlanWithCtor, AsyncMethodPlan, SyncMethodPlan}

private[autowire] object AgentImplementationRuntime {
  def register[Trait](typeName: String, mode: AgentMode, plan: AgentImplementationPlan[Trait]): AgentDefinition[Trait] = {
    val constructor =
      AgentConstructor.noArgs[Trait](
        description = plan.metadata.description.getOrElse(typeName),
        prompt = None
      )(plan.buildInstance())

    val bindings = plan.methods.map {
      case sync: SyncMethodPlan[Trait @unchecked, in, out] =>
        buildSyncBinding[Trait, in, out](sync)
      case async: AsyncMethodPlan[Trait @unchecked, in, out] =>
        buildAsyncBinding[Trait, in, out](async)
    }

    val definition = new AgentDefinition[Trait](
      typeName = typeName,
      metadata = plan.metadata,
      constructor = constructor,
      bindings = bindings,
      mode = mode
    )

    AgentRegistry.register(definition)
    definition
  }

  def registerWithCtor[Trait, Ctor](
    typeName: String,
    mode: AgentMode,
    plan: AgentImplementationPlanWithCtor[Trait, Ctor]
  ): AgentDefinition[Trait] = {
    implicit val ctorSchema: GolemSchema[Ctor] = plan.constructorSchema

    val constructor =
      AgentConstructor.sync[Ctor, Trait](
        ConstructorMetadata(
          name = None,
          description = plan.metadata.description.getOrElse(typeName),
          promptHint = None
        )
      )(plan.buildInstance)

    val bindings = plan.methods.map {
      case sync: SyncMethodPlan[Trait @unchecked, in, out] =>
        buildSyncBinding[Trait, in, out](sync)
      case async: AsyncMethodPlan[Trait @unchecked, in, out] =>
        buildAsyncBinding[Trait, in, out](async)
    }

    val definition = new AgentDefinition[Trait](
      typeName = typeName,
      metadata = plan.metadata,
      constructor = constructor,
      bindings = bindings,
      mode = mode
    )

    AgentRegistry.register(definition)
    definition
  }

  private def buildSyncBinding[Trait, In, Out](plan: SyncMethodPlan[Trait, In, Out]): MethodBinding[Trait] = {
    implicit val inSchema: GolemSchema[In]   = plan.inputSchema
    implicit val outSchema: GolemSchema[Out] = plan.outputSchema

    MethodBinding.sync[Trait, In, Out](plan.metadata) { (instance, input) =>
      plan.handler(instance, input)
    }
  }

  private def buildAsyncBinding[Trait, In, Out](plan: AsyncMethodPlan[Trait, In, Out]): MethodBinding[Trait] = {
    implicit val inSchema: GolemSchema[In]   = plan.inputSchema
    implicit val outSchema: GolemSchema[Out] = plan.outputSchema

    MethodBinding.async[Trait, In, Out](plan.metadata) { (instance, input) =>
      plan.handler(instance, input)
    }
  }
}


