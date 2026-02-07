package golem.runtime.autowire

import golem.data.GolemSchema
import golem.data.StructuredSchema
import golem.runtime.agenttype.{AgentImplementationType, AsyncImplementationMethod, SyncImplementationMethod}

private[autowire] object AgentImplementationRuntime {
  def register[Trait, Ctor](
    typeName: String,
    mode: AgentMode,
    implType: AgentImplementationType[Trait, Ctor]
  ): AgentDefinition[Trait] = {
    val constructor =
      implType.constructorSchema.schema match {
        case StructuredSchema.Tuple(elements) if elements.isEmpty =>
          val instance = implType.buildInstance(().asInstanceOf[Ctor])
          AgentConstructor.noArgs[Trait](
            description = implType.metadata.description.getOrElse(typeName),
            prompt = None
          )(instance)
        case _ =>
          implicit val ctorSchema: GolemSchema[Ctor] = implType.constructorSchema
          AgentConstructor.sync[Ctor, Trait](
            ConstructorMetadata(
              name = None,
              description = implType.metadata.description.getOrElse(typeName),
              promptHint = None
            )
          )(implType.buildInstance)
      }

    val bindings = implType.methods.map {
      case sync: SyncImplementationMethod[Trait @unchecked, in, out] =>
        buildSyncBinding[Trait, in, out](sync)
      case async: AsyncImplementationMethod[Trait @unchecked, in, out] =>
        buildAsyncBinding[Trait, in, out](async)
    }

    val definition = new AgentDefinition[Trait](
      typeName = typeName,
      metadata = implType.metadata,
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
    implType: AgentImplementationType[Trait, Ctor]
  ): AgentDefinition[Trait] =
    register[Trait, Ctor](typeName, mode, implType)

  private def buildSyncBinding[Trait, In, Out](
    method: SyncImplementationMethod[Trait, In, Out]
  ): MethodBinding[Trait] = {
    implicit val inSchema: GolemSchema[In]   = method.inputSchema
    implicit val outSchema: GolemSchema[Out] = method.outputSchema

    MethodBinding.sync[Trait, In, Out](method.metadata) { (instance, input) =>
      method.handler(instance, input)
    }
  }

  private def buildAsyncBinding[Trait, In, Out](
    method: AsyncImplementationMethod[Trait, In, Out]
  ): MethodBinding[Trait] = {
    implicit val inSchema: GolemSchema[In]   = method.inputSchema
    implicit val outSchema: GolemSchema[Out] = method.outputSchema

    MethodBinding.async[Trait, In, Out](method.metadata) { (instance, input) =>
      method.handler(instance, input)
    }
  }
}
