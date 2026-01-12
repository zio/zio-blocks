package golem.runtime.autowire

import golem.runtime.macros.AgentImplementationMacro
import golem.runtime.macros.AgentNameMacro

/**
 * @hidden
 *   Internal registration wiring used by generated code; not part of the user
 *   API.
 */
private[golem] object AgentImplementation {

  /**
   * Internal implementation hook used by inline expansions/macros.
   *
   * Keeping this indirection means call-site expansions only reference the
   * public `AgentImplementation`, allowing the underlying runtime
   * (`AgentImplementationRuntime`) to remain package-private.
   */
  def registerPlan[Trait](
    typeName: String,
    mode: AgentMode,
    plan: golem.runtime.plan.AgentImplementationPlan[Trait, Unit]
  ): AgentDefinition[Trait] =
    AgentImplementationRuntime.register(typeName, mode, plan)

  def registerWithCtorPlan[Trait, Ctor](
    typeName: String,
    mode: AgentMode,
    plan: golem.runtime.plan.AgentImplementationPlan[Trait, Ctor]
  ): AgentDefinition[Trait] =
    AgentImplementationRuntime.registerWithCtor(typeName, mode, plan)

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
   * `type AgentInput = ...` on the agent trait.
   *
   * The agent mode is taken from `@agentDefinition(mode = ...)` on the trait,
   * or defaults to Durable.
   */
  inline def register[Trait <: AnyRef { type AgentInput }, Ctor](inline build: Ctor => Trait): AgentDefinition[Trait] =
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

  private inline def registerInternal[Trait](typeName: String, modeOverride: Option[AgentMode])(
    inline build: => Trait
  ): AgentDefinition[Trait] = {
    val plan          = AgentImplementationMacro.plan[Trait](build)
    val metadataMode  = plan.metadata.mode.flatMap(AgentMode.fromString)
    val effectiveMode = modeOverride.orElse(metadataMode).getOrElse(AgentMode.Durable)
    registerPlan(typeName, effectiveMode, plan)
  }

  private inline def registerWithCtorInternal[Trait <: AnyRef { type AgentInput }, Ctor](typeName: String)(
    inline build: Ctor => Trait
  ): AgentDefinition[Trait] = {
    val plan         = AgentImplementationMacro.planWithCtor[Trait, Ctor](build)
    val metadataMode = plan.metadata.mode.flatMap(AgentMode.fromString)
    val effective    = metadataMode.getOrElse(AgentMode.Durable)
    registerWithCtorPlan(typeName, effective, plan)
  }
}
