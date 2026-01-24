package zio.blocks.schema

import zio.blocks.schema.{TypeName => SchemaTypeName}
import scala.collection.mutable
import scala.language.experimental.macros
import scala.reflect.NameTransformer
import scala.reflect.macros.blackbox

trait TypeNameCompanionVersionSpecific {
  implicit def derived[A]: SchemaTypeName[A] = macro TypeNameMacros.derived[A]
}

private[schema] object TypeNameMacros {
  def derived[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[SchemaTypeName[A]] = {
    import c.universe._

    def typeArgs(tpe: Type): List[Type] = CommonMacroOps.typeArgs(c)(tpe)

    def companion(tpe: Type): Symbol = {
      val comp = tpe.typeSymbol.companion
      if (comp.isModule) comp
      else {
        val ownerChainOf = (s: Symbol) => Iterator.iterate(s)(_.owner).takeWhile(_ != NoSymbol).toArray.reverseIterator
        val path         = ownerChainOf(tpe.typeSymbol)
          .zipAll(ownerChainOf(c.internal.enclosingOwner), NoSymbol, NoSymbol)
          .dropWhile(x => x._1 == x._2)
          .takeWhile(x => x._1 != NoSymbol)
          .map(x => x._1.name.toTermName)
        if (path.isEmpty) NoSymbol
        else c.typecheck(path.foldLeft[Tree](Ident(path.next()))(Select(_, _)), silent = true).symbol
      }
    }

    val typeNameCache = new mutable.HashMap[Type, SchemaTypeName[?]]

    def typeName(tpe: Type): SchemaTypeName[?] = {
      def calculateTypeName(tpe: Type): SchemaTypeName[?] =
        if (tpe =:= typeOf[java.lang.String]) SchemaTypeName.string
        else {
          var packages  = List.empty[String]
          var values    = List.empty[String]
          val tpeSymbol = tpe.typeSymbol
          var name      = NameTransformer.decode(tpeSymbol.name.toString)
          val comp      = companion(tpe)
          var owner     =
            if (comp == null) tpeSymbol
            else if (comp == NoSymbol) {
              name += ".type"
              tpeSymbol.asClass.module
            } else comp
          while ({
            owner = owner.owner
            owner.owner != NoSymbol
          }) {
            val ownerName = NameTransformer.decode(owner.name.toString)
            if (owner.isPackage || owner.isPackageClass) packages = ownerName :: packages
            else values = ownerName :: values
          }
          new SchemaTypeName(new Namespace(packages, values), name, typeArgs(tpe).map(typeName))
        }

      typeNameCache.getOrElseUpdate(
        tpe,
        tpe match {
          case TypeRef(compTpe, typeSym, Nil) if typeSym.name.toString == "Type" =>
            var tpeName = calculateTypeName(compTpe)
            if (tpeName.name.endsWith(".type")) tpeName = tpeName.copy(name = tpeName.name.stripSuffix(".type"))
            tpeName
          case _ =>
            calculateTypeName(tpe)
        }
      )
    }

    def toTree(tpeName: SchemaTypeName[?]): Tree = {
      val packages = tpeName.namespace.packages.toList
      val values   = tpeName.namespace.values.toList
      val name     = tpeName.name
      val params   = tpeName.params.map(toTree).toList
      q"new _root_.zio.blocks.schema.TypeName(new _root_.zio.blocks.schema.Namespace($packages, $values), $name, $params)"
    }

    val tpe     = weakTypeOf[A].dealias
    val tpeName = typeName(tpe)
    c.Expr[SchemaTypeName[A]](toTree(tpeName))
  }
}
