package zio.blocks.schema

import scala.quoted._

trait TypeIdCompanionVersionSpecific {
  inline def derived[A]: TypeId[A] = ${ TypeIdCompanionVersionSpecificImpl.derived[A] }
}

private object TypeIdCompanionVersionSpecificImpl {
  def derived[A: Type](using Quotes): Expr[TypeId[A]] = {
    import quotes.reflect._

    def typeArgs(tpe: TypeRepr): List[TypeRepr] = CommonMacroOps.typeArgs(tpe)

    def isEnumValue(tpe: TypeRepr): Boolean = tpe.termSymbol.flags.is(Flags.Enum)

    def isEnumOrModuleValue(tpe: TypeRepr): Boolean = isEnumValue(tpe) || tpe.typeSymbol.flags.is(Flags.Module)

    def isZioPreludeNewtype(tpe: TypeRepr): Boolean = tpe match {
      case TypeRef(compTpe, "Type") => compTpe.baseClasses.exists(_.fullName == "zio.prelude.Newtype")
      case _                        => false
    }

    def buildTypeReprTree(tpe: TypeRepr): Expr[zio.blocks.schema.TypeRepr] =
      if (isEnumOrModuleValue(tpe)) {
        val (packages, values, name) = extractTypeInfo(tpe)
        '{
          zio.blocks.schema.TypeRepr
            .Singleton(new zio.blocks.schema.Owner(${ Expr(packages) }, ${ Expr(values) }), ${ Expr(name) })
        }
      } else {
        val tArgs = typeArgs(tpe)
        if (tArgs.nonEmpty) {
          val baseType    = buildNominalTree(tpe)
          val argsAsExprs = tArgs.map(arg => buildTypeReprTree(arg))
          val argsSeq     = Expr.ofSeq(argsAsExprs)
          '{ zio.blocks.schema.TypeRepr.Applied($baseType, $argsSeq) }
        } else {
          buildNominalTree(tpe)
        }
      }

    def buildNominalTree(tpe: TypeRepr): Expr[zio.blocks.schema.TypeRepr] = {
      val (packages, values, name) = extractTypeInfo(tpe)
      '{
        zio.blocks.schema.TypeRepr
          .Nominal(new zio.blocks.schema.Owner(${ Expr(packages) }, ${ Expr(values) }), ${ Expr(name) })
      }
    }

    def extractTypeInfo(tpe: TypeRepr): (Seq[String], Seq[String], String) = {
      var packages      = List.empty[String]
      var values        = List.empty[String]
      val tpeTypeSymbol = tpe.typeSymbol
      var name          = tpeTypeSymbol.name
      if (isEnumValue(tpe)) {
        values = name :: values
        name = tpe.termSymbol.name
      } else if (tpeTypeSymbol.flags.is(Flags.Module)) {
        name = name.substring(0, name.length - 1)
      }
      var owner = tpeTypeSymbol.owner
      while (owner != defn.RootClass) {
        val ownerName = owner.name
        if (owner.flags.is(Flags.Package)) packages = ownerName :: packages
        else if (owner.flags.is(Flags.Module)) values = ownerName.substring(0, ownerName.length - 1) :: values
        else values = ownerName :: values
        owner = owner.owner
      }
      (packages, values, name)
    }

    val tpe = TypeRepr.of[A].dealias
    if (isZioPreludeNewtype(tpe)) {
      val (packages, values, name) = extractTypeInfo(tpe)
      val ownerExpr                = '{ new zio.blocks.schema.Owner(${ Expr(packages) }, ${ Expr(values) }) }
      val reprExpr                 = '{ zio.blocks.schema.TypeRepr.Opaque($ownerExpr, ${ Expr(name) }) }
      '{ zio.blocks.schema.TypeId.fromRepr[A]($reprExpr) }
    } else {
      val reprExpr = buildTypeReprTree(tpe)
      '{ zio.blocks.schema.TypeId.fromRepr[A]($reprExpr) }
    }
  }
}
