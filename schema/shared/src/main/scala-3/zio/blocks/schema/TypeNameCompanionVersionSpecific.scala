package zio.blocks.schema

import scala.collection.mutable
import scala.quoted._

trait TypeNameCompanionVersionSpecific {
  inline implicit def derived[A]: TypeName[A] = ${ TypeNameMacros.derived[A] }
}

private[schema] object TypeNameMacros {
  def derived[A: Type](using Quotes): Expr[TypeName[A]] = new TypeNameMacrosImpl().deriveTypeName[A]
}

private class TypeNameMacrosImpl(using Quotes) {
  import quotes.reflect._

  private val typeNameCache = new mutable.HashMap[TypeRepr, TypeName[?]]

  private def typeName[T: Type](tpe: TypeRepr): TypeName[T] = CommonMacroOps.typeName(typeNameCache, tpe)

  private def toExpr[T: Type](tpeName: TypeName[T])(using Quotes): Expr[TypeName[T]] = CommonMacroOps.toExpr(tpeName)

  def deriveTypeName[A: Type]: Expr[TypeName[A]] = toExpr(typeName[A](TypeRepr.of[A].dealias))
}
