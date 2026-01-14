package zio.blocks.schema

import scala.language.experimental.macros
import scala.reflect.macros.blackbox
import scala.reflect.NameTransformer

trait TypeIdCompanionVersionSpecific {
  def derived[A]: TypeId[A] = macro TypeIdCompanionVersionSpecific.derivedImpl[A]
}

private object TypeIdCompanionVersionSpecific {
  def derivedImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[TypeId[A]] = {
    import c.universe._

    def typeArgs(tpe: Type): List[Type] = CommonMacroOps.typeArgs(c)(tpe)
    def isEnumOrModuleValue(tpe: Type): Boolean = tpe.typeSymbol.isModuleClass

    def isZioPreludeNewtype(tpe: Type): Boolean = tpe match {
      case TypeRef(compTpe, typeSym, Nil) if typeSym.name.toString == "Type" =>
        compTpe.baseClasses.exists(_.fullName == "zio.prelude.Newtype")
      case _ => false
    }

    def companion(tpe: Type): Symbol = {
      val comp = tpe.typeSymbol.companion
      if (comp.isModule) comp
      else {
        val ownerChainOf = (s: Symbol) => Iterator.iterate(s)(_.owner).takeWhile(_ != NoSymbol).toArray.reverseIterator
        val path = ownerChainOf(tpe.typeSymbol)
          .zipAll(ownerChainOf(c.internal.enclosingOwner), NoSymbol, NoSymbol)
          .dropWhile(x => x._1 == x._2)
          .takeWhile(x => x._1 != NoSymbol)
          .map(x => x._1.name.toTermName)
        if (path.isEmpty) NoSymbol
        else c.typecheck(path.foldLeft[Tree](Ident(path.next()))(Select(_, _)), silent = true).symbol
      }
    }

    def buildTypeReprTree(tpe: Type): Tree = {
      if (isEnumOrModuleValue(tpe)) {
        val (packages, values, name) = extractTypeInfo(tpe)
        q"_root_.zio.blocks.schema.TypeRepr.Singleton(new _root_.zio.blocks.schema.Owner($packages, $values), $name)"
      } else {
        val tArgs = typeArgs(tpe)
        if (tArgs.nonEmpty) {
          val baseType = buildNominalTree(tpe)
          val argsAsTree = tArgs.map(arg => buildTypeReprTree(arg))
          q"_root_.zio.blocks.schema.TypeRepr.Applied($baseType, _root_.scala.Seq(..$argsAsTree))"
        } else {
          buildNominalTree(tpe)
        }
      }
    }

    def buildNominalTree(tpe: Type): Tree = {
      val (packages, values, name) = extractTypeInfo(tpe)
      q"_root_.zio.blocks.schema.TypeRepr.Nominal(new _root_.zio.blocks.schema.Owner($packages, $values), $name)"
    }

    def extractTypeInfo(tpe: Type): (List[String], List[String], String) = {
      var packages = List.empty[String]
      var values = List.empty[String]
      val tpeSymbol = tpe.typeSymbol
      var name = NameTransformer.decode(tpeSymbol.name.toString)
      val comp = companion(tpe)
      var owner = if (comp == null) tpeSymbol
        else if (comp == NoSymbol) { name += ".type"; tpeSymbol.asClass.module }
        else comp
      while ({ owner = owner.owner; owner.owner != NoSymbol }) {
        val ownerName = NameTransformer.decode(owner.name.toString)
        if (owner.isPackage || owner.isPackageClass) packages = ownerName :: packages
        else values = ownerName :: values
      }
      (packages, values, name)
    }

    val tpe = weakTypeOf[A].dealias
    if (isZioPreludeNewtype(tpe)) {
      val (packages, values, name) = extractTypeInfo(tpe)
      val ownerTree = q"new _root_.zio.blocks.schema.Owner($packages, $values)"
      val reprTree = q"_root_.zio.blocks.schema.TypeRepr.Opaque($ownerTree, $name)"
      return c.Expr[TypeId[A]](q"_root_.zio.blocks.schema.TypeId.fromRepr[$tpe]($reprTree)")
    }

    val reprTree = buildTypeReprTree(tpe)
    c.Expr[TypeId[A]](q"_root_.zio.blocks.schema.TypeId.fromRepr[$tpe]($reprTree)")
  }
}
