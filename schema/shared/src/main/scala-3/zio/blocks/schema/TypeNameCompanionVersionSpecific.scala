package zio.blocks.schema

import scala.collection.mutable
import scala.quoted._

trait TypeNameCompanionVersionSpecific {
  inline implicit def derived[A]: TypeName[A] = ${ TypeNameMacros.derived[A] }
}

private object TypeNameMacros {
  def derived[A: Type](using Quotes): Expr[TypeName[A]] = {
    import quotes.reflect._

    val typeNameCache = new mutable.HashMap[TypeRepr, TypeName[?]]

    def typeName[T: Type](tpe: TypeRepr): TypeName[T] = CommonMacroOps.typeName(typeNameCache, tpe)

    def toExpr[T: Type](tpeName: TypeName[T])(using Quotes): Expr[TypeName[T]] = CommonMacroOps.toExpr(tpeName)

    toExpr(typeName[A](TypeRepr.of[A].dealias))
  }
}
