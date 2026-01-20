package zio.blocks.typeid

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

trait TypeIdCompanionVersionSpecific {
  def derive[A]: TypeId[A] = macro TypeIdMacros.deriveMacro[A]
}

private[typeid] object TypeIdMacros {
  def deriveMacro[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[TypeId[A]] = {
    import c.universe._

    val tpe = weakTypeOf[A].dealias

    def extractOwner(symbol: Symbol): c.Expr[Owner] = {
      val segments = scala.collection.mutable.ListBuffer.empty[c.Tree]

      def collectOwner(sym: Symbol): Unit =
        if (sym != NoSymbol && !sym.isPackageClass) {
          collectOwner(sym.owner)
          if (sym.isPackage) {
            segments += q"_root_.zio.blocks.typeid.Owner.Package(${sym.name.decodedName.toString})"
          } else if (sym.isModuleClass || sym.isModule) {
            segments += q"_root_.zio.blocks.typeid.Owner.Term(${sym.name.decodedName.toString.stripSuffix("$")})"
          } else if (sym.isClass || sym.isType) {
            segments += q"_root_.zio.blocks.typeid.Owner.Type(${sym.name.decodedName.toString})"
          }
        }

      collectOwner(symbol.owner)
      c.Expr[Owner](q"_root_.zio.blocks.typeid.Owner(_root_.scala.List(..$segments))")
    }

    def extractTypeParams(symbol: Symbol): c.Expr[List[TypeParam]] = {
      val params = symbol.asType.typeParams.zipWithIndex.map { case (param, idx) =>
        val name = param.name.decodedName.toString
        q"_root_.zio.blocks.typeid.TypeParam($name, $idx)"
      }
      c.Expr[List[TypeParam]](q"_root_.scala.List(..$params)")
    }

    val symbol = tpe.typeSymbol
    val name   = symbol.name.decodedName.toString
    val owner  = extractOwner(symbol)
    val params = extractTypeParams(symbol)

    val isTypeAlias = symbol.isType && !symbol.isClass && symbol.asType.isAliasType
    val isOpaque    = false

    if (isOpaque) {
      c.abort(c.enclosingPosition, "Opaque types are not supported in Scala 2")
    } else if (isTypeAlias) {
      val aliasedType = tpe.dealias
      val aliasedRepr = buildTypeRepr(c)(aliasedType)
      c.Expr[TypeId[A]](
        q"""_root_.zio.blocks.typeid.TypeId.alias[$tpe](
            $name,
            $owner,
            $params,
            $aliasedRepr
          )"""
      )
    } else {
      c.Expr[TypeId[A]](
        q"""_root_.zio.blocks.typeid.TypeId.nominal[$tpe](
            $name,
            $owner,
            $params
          )"""
      )
    }
  }

  private def buildTypeRepr(c: blackbox.Context)(tpe: c.Type): c.Tree = {
    import c.universe._

    val dealiased = tpe.dealias

    if (dealiased.typeArgs.nonEmpty) {
      val tycon = buildTypeRepr(c)(dealiased.typeConstructor)
      val args  = dealiased.typeArgs.map(arg => buildTypeRepr(c)(arg))
      q"_root_.zio.blocks.typeid.TypeRepr.Applied($tycon, _root_.scala.List(..$args))"
    } else {
      val symbol = dealiased.typeSymbol
      val name   = symbol.name.decodedName.toString
      val owner  = buildOwner(c)(symbol)
      val params = buildTypeParams(c)(symbol)
      q"_root_.zio.blocks.typeid.TypeRepr.Ref(_root_.zio.blocks.typeid.TypeId.nominal[$dealiased]($name, $owner, $params))"
    }
  }

  private def buildOwner(c: blackbox.Context)(symbol: c.Symbol): c.Tree = {
    import c.universe._

    val segments = scala.collection.mutable.ListBuffer.empty[c.Tree]

    def collectOwner(sym: Symbol): Unit =
      if (sym != NoSymbol && !sym.isPackageClass) {
        collectOwner(sym.owner)
        if (sym.isPackage) {
          segments += q"_root_.zio.blocks.typeid.Owner.Package(${sym.name.decodedName.toString})"
        } else if (sym.isModuleClass || sym.isModule) {
          segments += q"_root_.zio.blocks.typeid.Owner.Term(${sym.name.decodedName.toString.stripSuffix("$")})"
        } else if (sym.isClass || sym.isType) {
          segments += q"_root_.zio.blocks.typeid.Owner.Type(${sym.name.decodedName.toString})"
        }
      }

    collectOwner(symbol.owner)
    q"_root_.zio.blocks.typeid.Owner(_root_.scala.List(..$segments))"
  }

  private def buildTypeParams(c: blackbox.Context)(symbol: c.Symbol): c.Tree = {
    import c.universe._

    if (symbol.isType) {
      val params = symbol.asType.typeParams.zipWithIndex.map { case (param, idx) =>
        val name = param.name.decodedName.toString
        q"_root_.zio.blocks.typeid.TypeParam($name, $idx)"
      }
      q"_root_.scala.List(..$params)"
    } else {
      q"_root_.scala.Nil"
    }
  }
}
