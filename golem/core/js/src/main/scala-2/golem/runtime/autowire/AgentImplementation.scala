package golem.runtime.autowire

import scala.language.experimental.macros

import scala.reflect.macros.blackbox

// format: off
private[golem] object AgentImplementation {

  def registerAnyCtorType[Trait](
    typeName: String,
    mode: AgentMode,
    implType: _root_.golem.runtime.agenttype.AgentImplementationType[Trait, _]
  ): AgentDefinition[Trait] =
    AgentImplementationRuntime.register(typeName, mode, implType.asInstanceOf[_root_.golem.runtime.agenttype.AgentImplementationType[Trait, Any]])

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
  def registerClass[Trait, Impl <: Trait]: AgentDefinition[Trait] =
    macro AgentImplementationMacroFacade.registerClassImpl[Trait, Impl]
}

private[golem] object AgentImplementationMacroFacade {
  def registerClassImpl[Trait: c.WeakTypeTag, Impl: c.WeakTypeTag](c: blackbox.Context): c.Expr[AgentDefinition[Trait]] = {
    import c.universe._

    val traitType = weakTypeOf[Trait]

    val typeNameExpr = c.Expr[String](q"_root_.golem.runtime.macros.AgentNameMacro.typeName[$traitType]")

    c.Expr[AgentDefinition[Trait]](
      q"""
      {
        val implType = _root_.golem.runtime.macros.AgentImplementationMacro.implementationTypeFromClass[$traitType, ${weakTypeOf[Impl]}]
        val metadataMode = implType.metadata.mode.flatMap(_root_.golem.runtime.autowire.AgentMode.fromString)
        val effectiveMode = metadataMode.getOrElse(_root_.golem.runtime.autowire.AgentMode.Durable)
        _root_.golem.runtime.autowire.AgentImplementation.registerAnyCtorType($typeNameExpr, effectiveMode, implType)
      }
      """
    )
  }
}
