package zio.blocks.scope

import zio.blocks.scope.internal.{MacroCore, WireCodeGen}
import zio.blocks.scope.internal.WireCodeGen.WireKind
import scala.quoted.*

private[scope] object ScopeMacros {

  // ─────────────────────────────────────────────────────────────────────────
  // shared[T] / unique[T] implementations
  // ─────────────────────────────────────────────────────────────────────────

  def sharedImpl[T: Type](using Quotes): Expr[Wire.Shared[?, T]] = {
    import quotes.reflect.*

    Expr.summon[Wireable[T]] match {
      case Some(wireableExpr) =>
        WireCodeGen.wireFromWireable(wireableExpr, WireKind.Shared).asExprOf[Wire.Shared[?, T]]

      case None =>
        val tpe = TypeRepr.of[T]
        val sym = tpe.typeSymbol

        if (!sym.isClassDef || sym.flags.is(Flags.Trait) || sym.flags.is(Flags.Abstract)) {
          MacroCore.abort(MacroCore.ScopeMacroError.NotAClass(tpe.show))
        }

        val (_, wireExpr) = WireCodeGen.deriveWire[T](WireKind.Shared)
        wireExpr.asExprOf[Wire.Shared[?, T]]
    }
  }

  def uniqueImpl[T: Type](using Quotes): Expr[Wire.Unique[?, T]] = {
    import quotes.reflect.*

    Expr.summon[Wireable[T]] match {
      case Some(wireableExpr) =>
        WireCodeGen.wireFromWireable(wireableExpr, WireKind.Unique).asExprOf[Wire.Unique[?, T]]

      case None =>
        val tpe = TypeRepr.of[T]
        val sym = tpe.typeSymbol

        if (!sym.isClassDef || sym.flags.is(Flags.Trait) || sym.flags.is(Flags.Abstract)) {
          MacroCore.abort(MacroCore.ScopeMacroError.NotAClass(tpe.show))
        }

        val (_, wireExpr) = WireCodeGen.deriveWire[T](WireKind.Unique)
        wireExpr.asExprOf[Wire.Unique[?, T]]
    }
  }
}
