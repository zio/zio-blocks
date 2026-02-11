package golem.runtime.autowire

import golem.BaseAgent
import scala.language.experimental.macros

import scala.reflect.macros.blackbox

// format: off
private[golem] object AgentImplementation {

  def registerType[Trait](
    typeName: String,
    mode: AgentMode,
    implType: _root_.golem.runtime.agenttype.AgentImplementationType[Trait, _root_.scala.Unit]
  ): AgentDefinition[Trait] =
    AgentImplementationRuntime.register(typeName, mode, implType)

  def registerWithCtorType[Trait, Ctor](
    typeName: String,
    mode: AgentMode,
    implType: _root_.golem.runtime.agenttype.AgentImplementationType[Trait, Ctor]
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
  def register[Trait](typeName: String)(build: => Trait): AgentDefinition[Trait] =
    macro AgentImplementationMacroFacade.registerImpl[Trait]

  /**
   * Registers an agent implementation using the agent type name from
   * `@agentDefinition("...")` on the trait.
   */
  def register[Trait](build: => Trait): AgentDefinition[Trait] =
    macro AgentImplementationMacroFacade.registerImplCustomAgentTypeName[Trait]

  /**
   * Registers an agent implementation using constructor input, as defined by
   * `BaseAgent[Input]` on the agent trait.
   *
   * The agent mode is taken from `@agentDefinition(mode = ...)` on the trait,
   * or defaults to Durable.
   */
  def register[Trait <: BaseAgent[_], Ctor](build: Ctor => Trait): AgentDefinition[Trait] =
    macro AgentImplementationMacroFacade.registerFromAnnotationCtorImpl[Trait, Ctor]

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
  def registerWithMode[Trait](typeName: String, mode: AgentMode)(build: => Trait): AgentDefinition[Trait] =
    macro AgentImplementationMacroFacade.registerWithModeImpl[Trait]

  /**
   * Registers an agent implementation using the agent type name from
   * `@agentDefinition("...")` on the trait, with a mode override.
   */
  def register[Trait](mode: AgentMode)(build: => Trait): AgentDefinition[Trait] =
    macro AgentImplementationMacroFacade.registerFromAnnotationWithModeImpl[Trait]
}

private[golem] object AgentImplementationMacroFacade {
  private def defaultTypeNameFromTrait(c: blackbox.Context)(sym: c.universe.Symbol): String = {
    val raw = sym.name.decodedName.toString
    raw
      .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
      .replaceAll("([A-Z]+)([A-Z][a-z])", "$1-$2")
      .toLowerCase
  }

  def registerImpl[Trait: c.WeakTypeTag](
    c: blackbox.Context
  )(typeName: c.Expr[String])(build: c.Expr[Trait]): c.Expr[AgentDefinition[Trait]] = {
    import c.universe._

    val traitType = weakTypeOf[Trait]

    c.Expr[AgentDefinition[Trait]](q"""
      {
        val implType = _root_.golem.runtime.macros.AgentImplementationMacro.implementationType[$traitType]($build)
        val metadataMode = implType.metadata.mode.flatMap(_root_.golem.runtime.autowire.AgentMode.fromString)
        val effectiveMode = metadataMode.getOrElse(_root_.golem.runtime.autowire.AgentMode.Durable)
        _root_.golem.runtime.autowire.AgentImplementation.registerType($typeName, effectiveMode, implType)
      }
    """)
  }

  def registerWithModeImpl[Trait: c.WeakTypeTag](
    c: blackbox.Context
  )(typeName: c.Expr[String], mode: c.Expr[AgentMode])(build: c.Expr[Trait]): c.Expr[AgentDefinition[Trait]] = {
    import c.universe._

    val traitType = weakTypeOf[Trait]

    c.Expr[AgentDefinition[Trait]](q"""
      {
        val implType = _root_.golem.runtime.macros.AgentImplementationMacro.implementationType[$traitType]($build)
        val metadataMode = implType.metadata.mode.flatMap(_root_.golem.runtime.autowire.AgentMode.fromString)
        val effectiveMode = Some($mode).orElse(metadataMode).getOrElse(_root_.golem.runtime.autowire.AgentMode.Durable)
        _root_.golem.runtime.autowire.AgentImplementation.registerType($typeName, effectiveMode, implType)
      }
    """)
  }

  def registerImplCustomAgentTypeName[Trait: c.WeakTypeTag](c: blackbox.Context)(
    build: c.Expr[Trait]
  ): c.Expr[AgentDefinition[Trait]] = {
    import c.universe._

    val traitType = weakTypeOf[Trait]
    val traitSym  = traitType.typeSymbol

    val agentDefinitionType = typeOf[_root_.golem.runtime.annotations.agentDefinition]
    val raw                 = traitSym.annotations.collectFirst {
      case ann if ann.tree.tpe != null && ann.tree.tpe =:= agentDefinitionType =>
        ann.tree.children.tail.collectFirst { case Literal(Constant(s: String)) => s }.getOrElse("")
    }.getOrElse("")
    val typeName: String = {
      val trimmed = raw.trim
      if (trimmed.nonEmpty) trimmed
      else {
        val hasAnn = traitSym.annotations.exists(a => a.tree.tpe != null && a.tree.tpe =:= agentDefinitionType)
        if (!hasAnn) c.abort(c.enclosingPosition, s"Missing @agentDefinition(...) on agent trait: ${traitSym.fullName}")
        defaultTypeNameFromTrait(c)(traitSym)
      }
    }

    registerImpl[Trait](c)(c.Expr[String](Literal(Constant(typeName))))(build)
  }

  def registerFromAnnotationWithModeImpl[Trait: c.WeakTypeTag](c: blackbox.Context)(
    mode: c.Expr[AgentMode]
  )(build: c.Expr[Trait]): c.Expr[AgentDefinition[Trait]] = {
    import c.universe._

    val traitType = weakTypeOf[Trait]
    val traitSym  = traitType.typeSymbol

    val agentDefinitionType = typeOf[_root_.golem.runtime.annotations.agentDefinition]
    val raw                 = traitSym.annotations.collectFirst {
      case ann if ann.tree.tpe != null && ann.tree.tpe =:= agentDefinitionType =>
        ann.tree.children.tail.collectFirst { case Literal(Constant(s: String)) => s }.getOrElse("")
    }.getOrElse("")
    val typeName: String = {
      val trimmed = raw.trim
      if (trimmed.nonEmpty) trimmed
      else {
        val hasAnn = traitSym.annotations.exists(a => a.tree.tpe != null && a.tree.tpe =:= agentDefinitionType)
        if (!hasAnn) c.abort(c.enclosingPosition, s"Missing @agentDefinition(...) on agent trait: ${traitSym.fullName}")
        defaultTypeNameFromTrait(c)(traitSym)
      }
    }

    registerWithModeImpl[Trait](c)(c.Expr[String](Literal(Constant(typeName))), mode)(build)
  }

  def registerFromAnnotationCtorImpl[Trait: c.WeakTypeTag, Ctor: c.WeakTypeTag](c: blackbox.Context)(
    build: c.Expr[Any]
  ): c.Expr[AgentDefinition[Trait]] = {
    import c.universe._

    val traitType = weakTypeOf[Trait]
    val traitSym  = traitType.typeSymbol

    val agentDefinitionType = typeOf[_root_.golem.runtime.annotations.agentDefinition]
    val raw                 = traitSym.annotations.collectFirst {
      case ann if ann.tree.tpe != null && ann.tree.tpe =:= agentDefinitionType =>
        ann.tree.children.tail.collectFirst { case Literal(Constant(s: String)) => s }.getOrElse("")
    }.getOrElse("")
    val typeName: String = {
      val trimmed = raw.trim
      if (trimmed.nonEmpty) trimmed
      else {
        val hasAnn = traitSym.annotations.exists(a => a.tree.tpe != null && a.tree.tpe =:= agentDefinitionType)
        if (!hasAnn) c.abort(c.enclosingPosition, s"Missing @agentDefinition(...) on agent trait: ${traitSym.fullName}")
        defaultTypeNameFromTrait(c)(traitSym)
      }
    }

    c.Expr[AgentDefinition[Trait]](
      q"""
      {
        val implType = _root_.golem.runtime.macros.AgentImplementationMacro.implementationTypeWithCtor[$traitType, ${weakTypeOf[
          Ctor
        ]}]($build)
        val metadataMode = implType.metadata.mode.flatMap(_root_.golem.runtime.autowire.AgentMode.fromString)
        val effectiveMode = metadataMode.getOrElse(_root_.golem.runtime.autowire.AgentMode.Durable)
        _root_.golem.runtime.autowire.AgentImplementation.registerWithCtorType($typeName, effectiveMode, implType)
      }
      """
    )
  }
}
