package golem.runtime.macros

import scala.reflect.macros.blackbox

object AgentSdkMacro {
  def derived[Trait]: _root_.golem.AgentApi[Trait] = macro AgentSdkMacroImpl.derivedImpl[Trait]
}

object AgentSdkMacroImpl {
  def derivedImpl[Trait: c.WeakTypeTag](c: blackbox.Context): c.Expr[_root_.golem.AgentApi[Trait]] = {
    import c.universe._

    val traitTpe = weakTypeOf[Trait]
    val traitSym = traitTpe.typeSymbol

    def defaultTypeNameFromTrait(sym: Symbol): String =
      sym.name.decodedName.toString

    val agentDefinitionType = typeOf[_root_.golem.runtime.annotations.agentDefinition]
    val rawTypeName: String =
      traitSym.annotations.collectFirst {
        case ann if ann.tree.tpe != null && ann.tree.tpe =:= agentDefinitionType =>
          ann.tree.children.tail.collectFirst { case Literal(Constant(s: String)) => s }.getOrElse("")
      }
        .map(_.trim)
        .filter(_.nonEmpty)
        .getOrElse {
          val hasAnn = traitSym.annotations.exists(a => a.tree.tpe != null && a.tree.tpe =:= agentDefinitionType)
          if (!hasAnn)
            c.abort(c.enclosingPosition, s"Missing @agentDefinition(...) on agent trait: ${traitSym.fullName}")
          defaultTypeNameFromTrait(traitSym)
        }
    val typeName: String = validateTypeName(rawTypeName)

    val ctorTpe: Type = {
      val baseSymOpt = traitTpe.baseClasses.find(_.fullName == "golem.BaseAgent")
      val baseArgs   = baseSymOpt.toList.flatMap(sym => traitTpe.baseType(sym).typeArgs)
      baseArgs.headOption.getOrElse(typeOf[Unit]).dealias
    }

    c.Expr[_root_.golem.AgentApi[Trait]](
      q"""
      new _root_.golem.AgentApi[$traitTpe] {
        override type Constructor = $ctorTpe
        override val typeName: String = $typeName
        override val agentType: _root_.golem.runtime.agenttype.AgentType[$traitTpe, $ctorTpe] =
          _root_.golem.runtime.macros.AgentClientMacro
            .agentType[$traitTpe]
            .asInstanceOf[_root_.golem.runtime.agenttype.AgentType[$traitTpe, $ctorTpe]]
      }
      """
    )
  }

  private def validateTypeName(value: String): String =
    value
}
