package golem.runtime.macros

import scala.reflect.macros.blackbox

object AgentNameMacro {
  def typeName[T]: String = macro AgentNameMacroImpl.typeNameImpl[T]
}

object AgentNameMacroImpl {
  def typeNameImpl[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[String] = {
    import c.universe._

    val tpe = weakTypeOf[T]
    val sym = tpe.typeSymbol

    def defaultTypeNameFromTrait(sym: Symbol): String =
      sym.name.decodedName.toString

    val maybe = sym.annotations.collectFirst {
      case ann
          if ann.tree.tpe != null && ann.tree.tpe.typeSymbol.fullName == "golem.runtime.annotations.agentDefinition" =>
        ann.tree.children.tail.collectFirst { case Literal(Constant(s: String)) => s }
    }.flatten

    maybe match {
      case Some(value) if value.trim.nonEmpty => c.Expr[String](Literal(Constant(value)))
      case _                                  =>
        // In Scala 2, macro annotations are stripped after expansion, so the trait may not
        // retain @agentDefinition. The macro annotation injects a `def typeName: String`
        // into the companion; use that as a fallback.
        val companion0 = sym.companion
        val companion1 =
          if (companion0 != NoSymbol) companion0
          else {
            // Nested traits (e.g. inside an object) sometimes don't have a companion set at macro time.
            val owner = sym.owner
            if (owner != NoSymbol) owner.typeSignature.member(sym.name.toTermName)
            else NoSymbol
          }

        val companion2 =
          if (companion1 != NoSymbol && companion1.isModule) companion1
          else {
            // Last-resort lookup by fully-qualified name (works for nested objects too).
            try c.mirror.staticModule(sym.fullName)
            catch { case _: Throwable => NoSymbol }
          }

        if (companion2 != NoSymbol && companion2.isModule) {
          // Prefer checking the module class (more reliable than the singleton type's decls),
          // but ultimately just emit `<companion>.typeName` and let the typer decide.
          val _   = companion2.asModule.moduleClass.typeSignature.decls // force completion
          val ref = Ident(companion2.asModule)
          c.Expr[String](q"$ref.typeName")
        } else {
          // If the trait had @agentDefinition but the typeName was omitted/empty, derive a default.
          val hasAnn =
            sym.annotations.exists(ann =>
              ann.tree.tpe != null && ann.tree.tpe.typeSymbol.fullName == "golem.runtime.annotations.agentDefinition"
            )
          if (!hasAnn) c.abort(c.enclosingPosition, s"Missing @agentDefinition(...) on agent trait: ${sym.fullName}")
          c.Expr[String](Literal(Constant(defaultTypeNameFromTrait(sym))))
        }
    }
  }

}
