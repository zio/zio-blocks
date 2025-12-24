package zio.blocks.schema

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

// Extension methods for Into object in Scala 2
object IntoVersionSpecific {
  implicit class IntoOps(val into: Into.type) extends AnyVal {
    def derived[A, B]: Into[A, B] = macro IntoVersionSpecificMacros.derivedImpl[A, B]
  }
}

private object IntoVersionSpecificMacros {
  def derivedImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context): c.Expr[Into[A, B]] = {
    // Delegate to the version-specific macro implementation
    val intoAsVersionSpecificMacros = c.mirror.staticModule("zio.blocks.schema.IntoAsVersionSpecificMacros")
    val derivedIntoImplSym          = intoAsVersionSpecificMacros.info.member(c.universe.TermName("derivedIntoImpl"))

    if (derivedIntoImplSym == c.universe.NoSymbol) {
      c.abort(
        c.enclosingPosition,
        "Cannot find IntoAsVersionSpecificMacros.derivedIntoImpl. " +
          "This may indicate a compilation order issue."
      )
    }

    import c.universe._
    c.Expr[Into[A, B]](
      Apply(
        TypeApply(
          Select(Ident(intoAsVersionSpecificMacros), derivedIntoImplSym),
          List(TypeTree(c.weakTypeOf[A]), TypeTree(c.weakTypeOf[B]))
        ),
        List(Ident(TermName("c")))
      )
    )
  }
}
