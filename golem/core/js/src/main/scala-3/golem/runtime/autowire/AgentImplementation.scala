package golem.runtime.autowire

import golem.BaseAgent

import golem.runtime.macros.AgentImplementationMacro
import golem.runtime.macros.AgentNameMacro

private[golem] object AgentImplementation {

  def registerType[Trait](
    typeName: String,
    mode: AgentMode,
    implType: golem.runtime.agenttype.AgentImplementationType[Trait, Unit]
  ): AgentDefinition[Trait] =
    AgentImplementationRuntime.register(typeName, mode, implType)

  def registerWithCtorType[Trait, Ctor](
    typeName: String,
    mode: AgentMode,
    implType: golem.runtime.agenttype.AgentImplementationType[Trait, Ctor]
  ): AgentDefinition[Trait] =
    AgentImplementationRuntime.registerWithCtor(typeName, mode, implType)

  /**
   * Registers an agent implementation with the default mode (Durable).
   *
   * The macro inspects the trait at compile time and generates:
   *   - RPC method bindings for each abstract method
   *   - Input/output schema derivations
   *   - Agent metadata (name, description, method info)
   *
   * @tparam Trait
   *   The agent trait type
   * @param typeName
   *   Unique name for this agent type
   * @param constructor
   *   An instance of the implementation (evaluated lazily)
   * @return
   *   The registered agent definition
   */
  inline def register[Trait](typeName: String)(inline build: => Trait): AgentDefinition[Trait] =
    registerInternal[Trait](typeName, None)(build)

  /**
   * Registers an agent implementation using the agent type name from
   * `@agentDefinition("...")` on the trait.
   */
  inline def register[Trait](inline build: => Trait): AgentDefinition[Trait] =
    registerInternal[Trait](AgentNameMacro.typeName[Trait], None)(build)

  /**
   * Registers an agent implementation using constructor input, as defined by
   * `BaseAgent[Input]` on the agent trait.
   *
   * The agent mode is taken from `@agentDefinition(mode = ...)` on the trait,
   * or defaults to Durable.
   */
  inline def register[Trait <: BaseAgent[?], Ctor](inline build: Ctor => Trait): AgentDefinition[Trait] =
    registerWithCtorInternal[Trait, Ctor](AgentNameMacro.typeName[Trait])(build)

  /**
   * Registers an agent implementation with a specific mode.
   *
   * @tparam Trait
   *   The agent trait type
   * @param typeName
   *   Unique name for this agent type
   * @param mode
   *   The agent mode (Durable or Ephemeral)
   * @param constructor
   *   An instance of the implementation (evaluated lazily)
   * @return
   *   The registered agent definition
   */
  inline def register[Trait](typeName: String, mode: AgentMode)(inline build: => Trait): AgentDefinition[Trait] =
    registerInternal[Trait](typeName, Some(mode))(build)

  /**
   * Registers an agent implementation using the agent type name from
   * `@agentDefinition("...")` on the trait, with a mode override.
   */
  inline def register[Trait](mode: AgentMode)(inline build: => Trait): AgentDefinition[Trait] =
    registerInternal[Trait](AgentNameMacro.typeName[Trait], Some(mode))(build)

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

  private inline def registerInternal[Trait](typeName: String, modeOverride: Option[AgentMode])(
    inline build: => Trait
  ): AgentDefinition[Trait] = {
    val implType      = AgentImplementationMacro.implementationType[Trait](build)
    val metadataMode  = implType.metadata.mode.flatMap(AgentMode.fromString)
    val effectiveMode = modeOverride.orElse(metadataMode).getOrElse(AgentMode.Durable)
    registerType(typeName, effectiveMode, implType)
  }

  private inline def registerWithCtorInternal[Trait <: BaseAgent[?], Ctor](typeName: String)(
    inline build: Ctor => Trait
  ): AgentDefinition[Trait] = {
    val implType     = AgentImplementationMacro.implementationTypeWithCtor[Trait, Ctor](build)
    val metadataMode = implType.metadata.mode.flatMap(AgentMode.fromString)
    val effective    = metadataMode.getOrElse(AgentMode.Durable)
    registerWithCtorType(typeName, effective, implType)
  }
}
