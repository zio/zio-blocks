package zio.blocks.schema

import zio.blocks.schema.{TypeName => SchemaTypeName}
import scala.collection.mutable
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

trait TypeNameCompanionVersionSpecific {
  implicit def derived[A]: SchemaTypeName[A] = macro TypeNameMacros.derived[A]
}

private[schema] object TypeNameMacros {
  def derived[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[SchemaTypeName[A]] = {
    import c.universe._

    val typeNameCache = new mutable.HashMap[Type, SchemaTypeName[?]]

    def typeName(tpe: Type): SchemaTypeName[?] = CommonMacroOps.typeName(c)(typeNameCache, tpe)

    def toTree(tpeName: SchemaTypeName[?]): Tree = CommonMacroOps.toTree(c)(tpeName)

    c.Expr[SchemaTypeName[A]](toTree(typeName(weakTypeOf[A].dealias)))
  }
}
