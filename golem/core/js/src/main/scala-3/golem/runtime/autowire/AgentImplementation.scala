package golem.runtime.autowire

import golem.runtime.macros.AgentImplementationMacro
import golem.runtime.macros.AgentNameMacro

private[golem] object AgentImplementation {

  def registerAnyCtorType[Trait](
    typeName: String,
    mode: AgentMode,
    implType: golem.runtime.agenttype.AgentImplementationType[Trait, _]
  ): AgentDefinition[Trait] =
    AgentImplementationRuntime.register(typeName, mode, implType.asInstanceOf[golem.runtime.agenttype.AgentImplementationType[Trait, Any]])

  /**
   * Registers an agent implementation by class type.
   *
   * The macro inspects the Impl class constructor, separates identity params
   * from Config[T] params, and generates the registration automatically.
   * Config[T] params are excluded from agent identity and lazily loaded at runtime.
   *
   * @tparam Trait The agent trait type
   * @tparam Impl  The implementation class type
   * @return The registered agent definition
   */
  inline def registerClass[Trait, Impl <: Trait]: AgentDefinition[Trait] = {
    val implType = AgentImplementationMacro.implementationTypeFromClass[Trait, Impl]
    val metadataMode = implType.metadata.mode.flatMap(AgentMode.fromString)
    val effectiveMode = metadataMode.getOrElse(AgentMode.Durable)
    val typeName = AgentNameMacro.typeName[Trait]
    registerAnyCtorType(typeName, effectiveMode, implType)
  }
}
