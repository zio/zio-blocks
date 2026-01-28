package zio.blocks.schema

import scala.annotation.tailrec
import scala.collection.mutable
import scala.quoted._

private[schema] object CommonMacroOps {
  def fail(msg: String)(using q: Quotes): Nothing = {
    import q.reflect._

    report.errorAndAbort(msg, Position.ofMacroExpansion)
  }

  def typeArgs(using q: Quotes)(tpe: q.reflect.TypeRepr): List[q.reflect.TypeRepr] = {
    import q.reflect._

    tpe match {
      case AppliedType(_, args) => args.map(_.dealias)
      case _                    => Nil
    }
  }

  def isProductType(using q: Quotes)(symbol: q.reflect.Symbol): Boolean = {
    import q.reflect._

    symbol.flags.is(Flags.Case) && !symbol.flags.is(Flags.Abstract)
  }

  def isSealedTraitOrAbstractClass(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect._

    tpe.classSymbol.fold(false) { symbol =>
      val flags = symbol.flags
      flags.is(Flags.Sealed) && (flags.is(Flags.Abstract) || flags.is(Flags.Trait))
    }
  }

  def isNonAbstractScalaClass(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect._

    tpe.classSymbol.fold(false) { symbol =>
      val flags = symbol.flags
      !(flags.is(Flags.Abstract) || flags.is(Flags.JavaDefined) || flags.is(Flags.Trait))
    }
  }

  def isEnumValue(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect._

    tpe.termSymbol.flags.is(Flags.Enum)
  }

  def isEnumOrModuleValue(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect._

    isEnumValue(tpe) || tpe.typeSymbol.flags.is(Flags.Module)
  }

  def isNamedTuple(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect._

    tpe match {
      case AppliedType(ntTpe, _) => ntTpe.typeSymbol.fullName == "scala.NamedTuple$.NamedTuple"
      case _                     => false
    }
  }

  def isOpaque(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect._

    tpe.typeSymbol.flags.is(Flags.Opaque)
  }

  def opaqueDealias(using q: Quotes)(tpe: q.reflect.TypeRepr): q.reflect.TypeRepr = {
    import q.reflect._

    @tailrec
    def loop(tpe: TypeRepr): TypeRepr = tpe match {
      case trTpe: TypeRef =>
        if (trTpe.isOpaqueAlias) loop(trTpe.translucentSuperType.dealias)
        else tpe
      case AppliedType(atTpe, _)   => loop(atTpe.dealias)
      case TypeLambda(_, _, tlTpe) => loop(tlTpe.dealias)
      case _                       => tpe
    }

    val sTpe = loop(tpe)
    if (sTpe =:= tpe) fail(s"Cannot dealias opaque type: ${tpe.show}.")
    sTpe
  }

  def isZioPreludeNewtype(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect._

    tpe match {
      case TypeRef(compTpe, "Type") => compTpe.baseClasses.exists(_.fullName == "zio.prelude.Newtype")
      case _                        => false
    }
  }

  def zioPreludeNewtypeDealias(using q: Quotes)(tpe: q.reflect.TypeRepr): q.reflect.TypeRepr = {
    import q.reflect._

    tpe match {
      case TypeRef(compTpe, _) =>
        compTpe.baseClasses.find(_.fullName == "zio.prelude.Newtype") match {
          case Some(cls) => compTpe.baseType(cls).typeArgs.head.dealias
          case _         => fail(s"Cannot dealias zio-prelude newtype: ${tpe.show}.")
        }
      case _ => fail(s"Cannot dealias zio-prelude newtype: ${tpe.show}.")
    }
  }

  def isTypeRef(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect._

    tpe match {
      case trTpe: TypeRef =>
        val typeSymbol = trTpe.typeSymbol
        typeSymbol.isTypeDef && typeSymbol.isAliasType
      case _ => false
    }
  }

  def typeRefDealias(using q: Quotes)(tpe: q.reflect.TypeRepr): q.reflect.TypeRepr = {
    import q.reflect._

    tpe match {
      case trTpe: TypeRef =>
        val sTpe = trTpe.translucentSuperType.dealias
        if (sTpe == trTpe) fail(s"Cannot dealias type reference: ${tpe.show}.")
        sTpe
      case _ => fail(s"Cannot dealias type reference: ${tpe.show}.")
    }
  }

  def dealiasOnDemand(using q: Quotes)(tpe: q.reflect.TypeRepr): q.reflect.TypeRepr =
    if (isOpaque(tpe)) opaqueDealias(tpe)
    else if (isZioPreludeNewtype(tpe)) zioPreludeNewtypeDealias(tpe)
    else if (isTypeRef(tpe)) typeRefDealias(tpe)
    else tpe

  def isGenericTuple(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect._

    tpe <:< TypeRepr.of[Tuple] && !defn.isTupleClass(tpe.typeSymbol)
  }

  // Borrowed from an amazing work of Aleksander Rainko:
  // https://github.com/arainko/ducktape/blob/8d779f0303c23fd45815d3574467ffc321a8db2b/ducktape/src/main/scala/io/github/arainko/ducktape/internal/Structure.scala#L253-L270
  def genericTupleTypeArgs(using q: Quotes)(tpe: q.reflect.TypeRepr): List[q.reflect.TypeRepr] = {
    import q.reflect._

    def loop(tp: Type[?]): List[TypeRepr] = tp match {
      case '[h *: t] => TypeRepr.of[h].dealias :: loop(Type.of[t])
      case _         => Nil
    }

    loop(tpe.asType)
  }

  // Borrowed from an amazing work of Aleksander Rainko:
  // https://github.com/arainko/ducktape/blob/8d779f0303c23fd45815d3574467ffc321a8db2b/ducktape/src/main/scala/io/github/arainko/ducktape/internal/Structure.scala#L277-L295
  def normalizeGenericTuple(using q: Quotes)(typeArgs: List[q.reflect.TypeRepr]): q.reflect.TypeRepr = {
    import q.reflect._

    val size = typeArgs.size
    if (size > 0 && size <= 22) defn.TupleClass(size).typeRef.appliedTo(typeArgs)
    else {
      typeArgs.foldRight(TypeRepr.of[EmptyTuple]) {
        val tupleCons = TypeRepr.of[*:]
        (curr, acc) => tupleCons.appliedTo(List(curr, acc))
      }
    }
  }

  def isUnion(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
    import q.reflect._

    tpe match {
      case _: OrType => true
      case _         => false
    }
  }

  def allUnionTypes(using q: Quotes)(tpe: q.reflect.TypeRepr): List[q.reflect.TypeRepr] = {
    import q.reflect._

    val seen  = new mutable.HashSet[TypeRepr]
    val types = new mutable.ListBuffer[TypeRepr]

    def loop(tpe: TypeRepr): Unit = tpe.dealias match {
      case OrType(left, right) => loop(left); loop(right)
      case dealiased           => if (seen.add(dealiased)) types.addOne(dealiased)
    }

    loop(tpe)
    // Sort by full type symbol name to ensure consistent ordering across macro
    // contexts. The OrType tree structure can differ when types pass through
    // quotes, so we need a stable sort key that doesn't depend on tree structure.
    types.toList.sortBy(_.typeSymbol.fullName)
  }

  def directSubTypes(using q: Quotes)(tpe: q.reflect.TypeRepr): List[q.reflect.TypeRepr] = {
    import q.reflect._

    val tpeTypeSymbol = tpe.typeSymbol
    val subTypes      = tpeTypeSymbol.children.map { symbol =>
      if (symbol.isType) {
        val subtype = symbol.typeRef
        subtype.memberType(symbol.primaryConstructor) match {
          case _: MethodType                                              => subtype
          case PolyType(names, _, MethodType(_, _, AppliedType(base, _))) =>
            base.appliedTo(names.map {
              val binding = typeArgs(subtype.baseType(tpeTypeSymbol))
                .zip(typeArgs(tpe))
                .foldLeft(Map.empty[String, TypeRepr]) { case (binding, (childTypeArg, parentTypeArg)) =>
                  val childTypeSymbol = childTypeArg.typeSymbol
                  if (childTypeSymbol.isTypeParam) binding.updated(childTypeSymbol.name, parentTypeArg)
                  else binding
                }
              name =>
                binding.getOrElse(
                  name,
                  fail(s"Type parameter '$name' of '$symbol' can't be deduced from type arguments of '${tpe.show}'.")
                )
            })
          case _ => fail(s"Cannot resolve free type parameters for ADT cases with base '${tpe.show}'.")
        }
      } else Ref(symbol).tpe
    }
    if (tpe <:< TypeRepr.of[Option[?]]) subTypes.sortBy(_.typeSymbol.fullName)
    else subTypes
  }

  def typeName[T: Type](using
    q: Quotes
  )(
    typeNameCache: mutable.HashMap[q.reflect.TypeRepr, TypeName[?]],
    tpe: q.reflect.TypeRepr,
    nestedTpes: List[q.reflect.TypeRepr] = Nil
  ): TypeName[T] = {
    import q.reflect._

    def calculateTypeName(tpe: TypeRepr): TypeName[?] =
      if (tpe =:= TypeRepr.of[java.lang.String]) TypeName.string
      else {
        var packages: List[String] = Nil
        var values: List[String]   = Nil
        var name: String           = null
        val isUnionTpe             = isUnion(tpe)
        if (isUnionTpe) name = "|"
        else {
          val tpeTypeSymbol = tpe.typeSymbol
          name = tpeTypeSymbol.name
          if (isEnumValue(tpe)) {
            values = name :: values
            name = tpe.termSymbol.name
          } else if (tpeTypeSymbol.flags.is(Flags.Module)) name = name.substring(0, name.length - 1)
          var owner = tpeTypeSymbol.owner
          while (owner != defn.RootClass) {
            val ownerName = owner.name
            if (owner.flags.is(Flags.Package)) packages = ownerName :: packages
            else if (owner.flags.is(Flags.Module)) values = ownerName.substring(0, ownerName.length - 1) :: values
            else values = ownerName :: values
            owner = owner.owner
          }
        }
        val tpeTypeArgs =
          if (isUnionTpe) allUnionTypes(tpe)
          else if (isNamedTuple(tpe)) {
            val tpeTypeArgs = typeArgs(tpe)
            val nTpe        = tpeTypeArgs.head
            val tTpe        = tpeTypeArgs.last
            val nTypeArgs   =
              if (isGenericTuple(nTpe)) genericTupleTypeArgs(nTpe)
              else typeArgs(nTpe)
            var comma  = false
            val labels = new java.lang.StringBuilder(name)
            labels.append('[')
            nTypeArgs.foreach { case ConstantType(StringConstant(str)) =>
              if (comma) labels.append(',')
              else comma = true
              labels.append(str)
            }
            labels.append(']')
            name = labels.toString
            if (isGenericTuple(tTpe)) genericTupleTypeArgs(tTpe)
            else typeArgs(tTpe)
          } else if (isGenericTuple(tpe)) genericTupleTypeArgs(tpe)
          else typeArgs(tpe)
        new TypeName(
          new Namespace(packages, values),
          name,
          tpeTypeArgs.map { x =>
            if (nestedTpes.contains(x)) typeName[Any](typeNameCache, defn.AnyClass.typeRef)
            else typeName(typeNameCache, x, x :: nestedTpes)
          }
        )
      }

    typeNameCache
      .getOrElseUpdate(
        tpe,
        calculateTypeName(tpe match {
          case TypeRef(compTpe, "Type") => compTpe
          case _                        => tpe
        })
      )
      .asInstanceOf[TypeName[T]]
  }

  def toExpr[T: Type](tpeName: TypeName[T])(using Quotes): Expr[TypeName[T]] = {
    val packages = Varargs(tpeName.namespace.packages.map(Expr(_)))
    val vs       = tpeName.namespace.values
    val values   = if (vs.isEmpty) '{ Nil } else Varargs(vs.map(Expr(_)))
    val name     = Expr(tpeName.name)
    val ps       = tpeName.params
    val params   = if (ps.isEmpty) '{ Nil } else Varargs(ps.map(param => toExpr(param.asInstanceOf[TypeName[T]])))
    '{ new TypeName[T](new Namespace($packages, $values), $name, $params) }
  }
}
