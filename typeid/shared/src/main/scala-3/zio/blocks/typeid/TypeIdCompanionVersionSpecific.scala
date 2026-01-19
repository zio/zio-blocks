package zio.blocks.typeid

import scala.quoted.*

trait TypeIdCompanionVersionSpecific {
  inline def derive[A]: TypeId[A] = ${ TypeIdMacros.deriveMacro[A] }
}

private[typeid] object TypeIdMacros {
  type TR = zio.blocks.typeid.TypeRepr

  def deriveMacro[A: Type](using q: Quotes): Expr[TypeId[A]] = {
    import q.reflect.*

    val tpe = TypeRepr.of[A].dealias

    def extractOwner(symbol: Symbol): Expr[Owner] = {
      val segments = scala.collection.mutable.ListBuffer.empty[Expr[Owner.Segment]]

      def collectOwnerFull(sym: Symbol): Unit = {
        val owner = sym.owner
        if (owner != Symbol.noSymbol) {
          collectOwnerFull(owner)
          val name = owner.name
          if (owner.flags.is(Flags.Package) && name != "<root>" && name != "<empty>") {
            segments += '{ Owner.Package(${ Expr(name) }) }
          } else if (owner.flags.is(Flags.Module)) {
            segments += '{ Owner.Term(${ Expr(name.stripSuffix("$")) }) }
          } else if (owner.isClassDef || owner.isTypeDef) {
            segments += '{ Owner.Type(${ Expr(name) }) }
          }
        }
      }

      collectOwnerFull(symbol)
      '{ Owner(${ Expr.ofList(segments.toList) }) }
    }

    def extractTypeParams(symbol: Symbol): Expr[List[TypeParam]] = {
      val typeParamSymbols = if (symbol.isClassDef) {
        symbol.primaryConstructor.paramSymss.flatten.filter(_.isTypeParam)
      } else if (symbol.isTypeDef) {
        symbol.typeMembers.filter(_.isTypeParam)
      } else {
        Nil
      }

      val params = typeParamSymbols.zipWithIndex.map { case (param, idx) =>
        val name = param.name
        '{ TypeParam(${ Expr(name) }, ${ Expr(idx) }) }
      }
      Expr.ofList(params)
    }

    def buildOwner(symbol: Symbol): Expr[Owner] = extractOwner(symbol)

    def buildTypeParams(symbol: Symbol): Expr[List[TypeParam]] = extractTypeParams(symbol)

    def buildTypeRepr(tpe: TypeRepr): Expr[TR] =
      tpe.dealias match {
        case AppliedType(tycon, args) =>
          val tyconExpr = buildTypeRepr(tycon)
          val argsExpr  = Expr.ofList(args.map(buildTypeRepr))
          '{ zio.blocks.typeid.TypeRepr.Applied($tyconExpr, $argsExpr) }

        case tpe =>
          val symbol = tpe.typeSymbol
          val name   = symbol.name
          val owner  = buildOwner(symbol)
          val params = buildTypeParams(symbol)
          tpe.asType match {
            case '[t] =>
              '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.nominal[t](${ Expr(name) }, $owner, $params)) }
          }
      }

    val symbol = tpe.typeSymbol
    val name   = Expr(symbol.name)
    val owner  = extractOwner(symbol)
    val params = extractTypeParams(symbol)

    val isTypeAlias = symbol.isTypeDef && !symbol.isClassDef && symbol.flags.is(Flags.Deferred) == false
    val isOpaque    = symbol.flags.is(Flags.Opaque)

    if (isOpaque) {
      val underlying = tpe match {
        case TypeBounds(_, hi) => hi
        case _                 => tpe
      }
      val reprExpr = buildTypeRepr(underlying)
      '{ TypeId.opaque[A]($name, $owner, $params, $reprExpr) }
    } else if (isTypeAlias) {
      val aliased  = tpe.dealias
      val reprExpr = buildTypeRepr(aliased)
      '{ TypeId.alias[A]($name, $owner, $params, $reprExpr) }
    } else {
      '{ TypeId.nominal[A]($name, $owner, $params) }
    }
  }
}
