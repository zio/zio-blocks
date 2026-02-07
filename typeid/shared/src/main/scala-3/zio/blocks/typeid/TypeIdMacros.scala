package zio.blocks.typeid

import scala.quoted.*
import java.util.concurrent.ConcurrentHashMap

object TypeIdMacros {
  private[this] val typeIdCache = new ConcurrentHashMap[Any, TypeId[?]]

  // ============================================================================
  // ToExpr Instances (needed for top-level cache to convert TypeId values to Expr)
  // ============================================================================

  given ownerSegmentToExpr: ToExpr[Owner.Segment] with {
    def apply(seg: Owner.Segment)(using Quotes): Expr[Owner.Segment] = seg match {
      case Owner.Package(name) => '{ Owner.Package(${ Expr(name) }) }
      case Owner.Term(name)    => '{ Owner.Term(${ Expr(name) }) }
      case Owner.Type(name)    => '{ Owner.Type(${ Expr(name) }) }
    }
  }

  given ToExpr[Owner] with {
    def apply(owner: Owner)(using Quotes): Expr[Owner] =
      if (owner.segments.isEmpty) '{ Owner.Root }
      else {
        val (pkgPrefix, rest) = owner.segments.span(_.isInstanceOf[Owner.Package])
        val base              =
          if (pkgPrefix.isEmpty) '{ Owner.Root }
          else {
            val path = pkgPrefix.map(_.name).mkString(".")
            '{ Owner.fromPackagePath(${ Expr(path) }) }
          }
        rest.foldLeft(base) {
          case (acc, Owner.Term(name))    => '{ $acc.term(${ Expr(name) }) }
          case (acc, Owner.Type(name))    => '{ $acc.tpe(${ Expr(name) }) }
          case (acc, Owner.Package(name)) => '{ $acc / ${ Expr(name) } }
        }
      }
  }

  given ToExpr[Variance] with {
    def apply(v: Variance)(using Quotes): Expr[Variance] = v match {
      case Variance.Covariant     => '{ Variance.Covariant }
      case Variance.Contravariant => '{ Variance.Contravariant }
      case Variance.Invariant     => '{ Variance.Invariant }
    }
  }

  given ToExpr[Kind] with {
    def apply(k: Kind)(using Quotes): Expr[Kind] = k match {
      case Kind.Type           => '{ Kind.Type }
      case Kind.Arrow(ps, res) =>
        val paramsExpr = Expr.ofList(ps.map(p => Expr(p)))
        val resExpr    = Expr(res)
        '{ Kind.Arrow($paramsExpr, $resExpr) }
    }
  }

  given typeReprToExpr: ToExpr[TypeRepr] with {
    def apply(tr: TypeRepr)(using Quotes): Expr[TypeRepr] = tr match {
      case TypeRepr.Ref(id) =>
        val idExpr = typeIdToExpr(id)
        '{ TypeRepr.Ref($idExpr) }
      case TypeRepr.ParamRef(param, binderDepth) =>
        '{ TypeRepr.ParamRef(${ Expr(param) }, ${ Expr(binderDepth) }) }
      case TypeRepr.Applied(tycon, args) =>
        val tyconExpr = Expr(tycon)
        val argsExpr  = Expr.ofList(args.map(a => Expr(a)))
        '{ TypeRepr.Applied($tyconExpr, $argsExpr) }
      case TypeRepr.Structural(parents, members) =>
        val parentsExpr = Expr.ofList(parents.map(p => Expr(p)))
        val membersExpr = Expr.ofList(members.map(m => Expr(m)))
        '{ TypeRepr.Structural($parentsExpr, $membersExpr) }
      case TypeRepr.Intersection(types) =>
        val typesExpr = Expr.ofList(types.map(t => Expr(t)))
        '{ TypeRepr.Intersection($typesExpr) }
      case TypeRepr.Union(types) =>
        val typesExpr = Expr.ofList(types.map(t => Expr(t)))
        '{ TypeRepr.Union($typesExpr) }
      case TypeRepr.Tuple(elems) =>
        val elemsExpr = Expr.ofList(elems.map(e => Expr(e)))
        '{ TypeRepr.Tuple($elemsExpr) }
      case TypeRepr.Function(params, result) =>
        val paramsExpr = Expr.ofList(params.map(p => Expr(p)))
        val resultExpr = Expr(result)
        '{ TypeRepr.Function($paramsExpr, $resultExpr) }
      case TypeRepr.ContextFunction(params, result) =>
        val paramsExpr = Expr.ofList(params.map(p => Expr(p)))
        val resultExpr = Expr(result)
        '{ TypeRepr.ContextFunction($paramsExpr, $resultExpr) }
      case TypeRepr.TypeLambda(params, body) =>
        val paramsExpr = Expr.ofList(params.map(p => Expr(p)))
        val bodyExpr   = Expr(body)
        '{ TypeRepr.TypeLambda($paramsExpr, $bodyExpr) }
      case TypeRepr.ByName(underlying) =>
        '{ TypeRepr.ByName(${ Expr(underlying) }) }
      case TypeRepr.Repeated(element) =>
        '{ TypeRepr.Repeated(${ Expr(element) }) }
      case TypeRepr.Wildcard(bounds) =>
        '{ TypeRepr.Wildcard(${ Expr(bounds) }) }
      case TypeRepr.Singleton(path) =>
        '{ TypeRepr.Singleton(${ Expr(path) }) }
      case TypeRepr.ThisType(owner) =>
        '{ TypeRepr.ThisType(${ Expr(owner) }) }
      case TypeRepr.TypeProjection(qualifier, name) =>
        '{ TypeRepr.TypeProjection(${ Expr(qualifier) }, ${ Expr(name) }) }
      case TypeRepr.TypeSelect(qualifier, name) =>
        '{ TypeRepr.TypeSelect(${ Expr(qualifier) }, ${ Expr(name) }) }
      case TypeRepr.Annotated(underlying, annotations) =>
        val annotationsExpr = Expr.ofList(annotations.map(a => Expr(a)))
        '{ TypeRepr.Annotated(${ Expr(underlying) }, $annotationsExpr) }
      case TypeRepr.Constant.IntConst(v)       => '{ TypeRepr.Constant.IntConst(${ Expr(v) }) }
      case TypeRepr.Constant.LongConst(v)      => '{ TypeRepr.Constant.LongConst(${ Expr(v) }) }
      case TypeRepr.Constant.FloatConst(v)     => '{ TypeRepr.Constant.FloatConst(${ Expr(v) }) }
      case TypeRepr.Constant.DoubleConst(v)    => '{ TypeRepr.Constant.DoubleConst(${ Expr(v) }) }
      case TypeRepr.Constant.BooleanConst(v)   => '{ TypeRepr.Constant.BooleanConst(${ Expr(v) }) }
      case TypeRepr.Constant.CharConst(v)      => '{ TypeRepr.Constant.CharConst(${ Expr(v) }) }
      case TypeRepr.Constant.StringConst(v)    => '{ TypeRepr.Constant.StringConst(${ Expr(v) }) }
      case TypeRepr.Constant.NullConst         => '{ TypeRepr.Constant.NullConst }
      case TypeRepr.Constant.UnitConst         => '{ TypeRepr.Constant.UnitConst }
      case TypeRepr.Constant.ClassOfConst(tpe) =>
        '{ TypeRepr.Constant.ClassOfConst(${ Expr(tpe) }) }
      case TypeRepr.AnyType     => '{ TypeRepr.AnyType }
      case TypeRepr.NothingType => '{ TypeRepr.NothingType }
      case TypeRepr.NullType    => '{ TypeRepr.NullType }
      case TypeRepr.UnitType    => '{ TypeRepr.UnitType }
      case TypeRepr.AnyKindType => '{ TypeRepr.AnyKindType }
    }
  }

  private def typeIdToExpr(id: TypeId[?])(using Quotes): Expr[TypeId[?]] = {
    val nameExpr        = Expr(id.name)
    val ownerExpr       = Expr(id.owner)
    val typeParamsExpr  = Expr.ofList(id.typeParams.map(p => Expr(p)))
    val typeArgsExpr    = Expr.ofList(id.typeArgs.map(a => Expr(a)))
    val annotationsExpr = Expr.ofList(id.annotations.map(a => Expr(a)))

    (id.aliasedTo, id.representation) match {
      case (Some(aliased), _) =>
        val aliasedExpr = Expr(aliased)
        '{
          TypeId.alias[Any](
            $nameExpr,
            $ownerExpr,
            $typeParamsExpr,
            $aliasedExpr,
            $typeArgsExpr,
            $annotationsExpr
          )
        }

      case (_, Some(repr)) =>
        val reprExpr   = Expr(repr)
        val boundsExpr = id.defKind match {
          case TypeDefKind.OpaqueType(bounds) => Expr(bounds)
          case _                              => '{ TypeBounds.Unbounded }
        }
        '{
          TypeId.opaque[Any](
            $nameExpr,
            $ownerExpr,
            $typeParamsExpr,
            $reprExpr,
            $typeArgsExpr,
            $boundsExpr,
            $annotationsExpr
          )
        }

      case (None, None) =>
        val defKindExpr                          = Expr(id.defKind)
        val selfTypeExpr: Expr[Option[TypeRepr]] = id.selfType match {
          case Some(st) => '{ Some(${ Expr(st) }) }
          case None     => '{ None }
        }
        '{
          TypeId.nominal[Any](
            $nameExpr,
            $ownerExpr,
            $typeParamsExpr,
            $typeArgsExpr,
            $defKindExpr,
            $selfTypeExpr,
            $annotationsExpr
          )
        }
    }
  }

  given ToExpr[TypeBounds] with {
    def apply(tb: TypeBounds)(using Quotes): Expr[TypeBounds] = {
      val lowerExpr: Expr[Option[TypeRepr]] = tb.lower match {
        case Some(l) => '{ Some(${ Expr(l) }) }
        case None    => '{ None }
      }
      val upperExpr: Expr[Option[TypeRepr]] = tb.upper match {
        case Some(u) => '{ Some(${ Expr(u) }) }
        case None    => '{ None }
      }
      '{ TypeBounds($lowerExpr, $upperExpr) }
    }
  }

  given ToExpr[TypeParam] with {
    def apply(tp: TypeParam)(using Quotes): Expr[TypeParam] =
      '{
        TypeParam(
          ${ Expr(tp.name) },
          ${ Expr(tp.index) },
          ${ Expr(tp.variance) },
          ${ Expr(tp.bounds) },
          ${ Expr(tp.kind) }
        )
      }
  }

  given ToExpr[TupleElement] with {
    def apply(te: TupleElement)(using Quotes): Expr[TupleElement] = {
      val labelExpr = te.label match {
        case Some(l) => '{ Some(${ Expr(l) }) }
        case None    => '{ None }
      }
      '{ TupleElement($labelExpr, ${ Expr(te.tpe) }) }
    }
  }

  given ToExpr[Member] with {
    def apply(m: Member)(using Quotes): Expr[Member] = m match {
      case Member.Val(name, tpe, isVar) =>
        '{ Member.Val(${ Expr(name) }, ${ Expr(tpe) }, ${ Expr(isVar) }) }
      case Member.Def(name, typeParams, paramLists, result) =>
        val typeParamsExpr = Expr.ofList(typeParams.map(p => Expr(p)))
        val paramListsExpr = Expr.ofList(paramLists.map(pl => Expr.ofList(pl.map(p => Expr(p)))))
        '{ Member.Def(${ Expr(name) }, $typeParamsExpr, $paramListsExpr, ${ Expr(result) }) }
      case Member.TypeMember(name, typeParams, lowerBound, upperBound) =>
        val typeParamsExpr = Expr.ofList(typeParams.map(p => Expr(p)))
        val lowerExpr      = lowerBound match {
          case Some(l) => '{ Some(${ Expr(l) }) }
          case None    => '{ None }
        }
        val upperExpr = upperBound match {
          case Some(u) => '{ Some(${ Expr(u) }) }
          case None    => '{ None }
        }
        '{ Member.TypeMember(${ Expr(name) }, $typeParamsExpr, $lowerExpr, $upperExpr) }
    }
  }

  given ToExpr[Param] with {
    def apply(p: Param)(using Quotes): Expr[Param] =
      '{ Param(${ Expr(p.name) }, ${ Expr(p.tpe) }, ${ Expr(p.isImplicit) }, ${ Expr(p.hasDefault) }) }
  }

  given termPathSegmentToExpr: ToExpr[TermPath.Segment] with {
    def apply(seg: TermPath.Segment)(using Quotes): Expr[TermPath.Segment] = seg match {
      case TermPath.Package(name) => '{ TermPath.Package(${ Expr(name) }) }
      case TermPath.Term(name)    => '{ TermPath.Term(${ Expr(name) }) }
    }
  }

  given ToExpr[TermPath] with {
    def apply(tp: TermPath)(using Quotes): Expr[TermPath] = {
      val segmentsExpr = Expr.ofList(tp.segments.map(s => Expr(s)))
      '{ TermPath($segmentsExpr) }
    }
  }

  given ToExpr[EnumCaseParam] with {
    def apply(ecp: EnumCaseParam)(using Quotes): Expr[EnumCaseParam] =
      '{ EnumCaseParam(${ Expr(ecp.name) }, ${ Expr(ecp.tpe) }) }
  }

  given ToExpr[TypeDefKind] with {
    def apply(tdk: TypeDefKind)(using Quotes): Expr[TypeDefKind] = tdk match {
      case TypeDefKind.Class(isFinal, isAbstract, isCase, isValue, bases) =>
        val basesExpr = Expr.ofList(bases.map(b => Expr(b)))
        '{
          TypeDefKind.Class(
            isFinal = ${ Expr(isFinal) },
            isAbstract = ${ Expr(isAbstract) },
            isCase = ${ Expr(isCase) },
            isValue = ${ Expr(isValue) },
            bases = $basesExpr
          )
        }
      case TypeDefKind.Trait(isSealed, bases) =>
        val basesExpr = Expr.ofList(bases.map(b => Expr(b)))
        '{ TypeDefKind.Trait(isSealed = ${ Expr(isSealed) }, bases = $basesExpr) }
      case TypeDefKind.Object(bases) =>
        val basesExpr = Expr.ofList(bases.map(b => Expr(b)))
        '{ TypeDefKind.Object(bases = $basesExpr) }
      case TypeDefKind.Enum(bases) =>
        val basesExpr = Expr.ofList(bases.map(b => Expr(b)))
        '{ TypeDefKind.Enum(bases = $basesExpr) }
      case TypeDefKind.EnumCase(parentEnum, ordinal, isObjectCase) =>
        '{ TypeDefKind.EnumCase(${ Expr(parentEnum) }, ${ Expr(ordinal) }, ${ Expr(isObjectCase) }) }
      case TypeDefKind.TypeAlias          => '{ TypeDefKind.TypeAlias }
      case TypeDefKind.OpaqueType(bounds) =>
        '{ TypeDefKind.OpaqueType(${ Expr(bounds) }) }
      case TypeDefKind.AbstractType => '{ TypeDefKind.AbstractType }
      case TypeDefKind.Unknown      => '{ TypeDefKind.Unknown }
    }
  }

  given ToExpr[AnnotationArg] with {
    def apply(arg: AnnotationArg)(using Quotes): Expr[AnnotationArg] = arg match {
      case AnnotationArg.Const(value) =>
        value match {
          case s: String  => '{ AnnotationArg.Const(${ Expr(s) }) }
          case i: Int     => '{ AnnotationArg.Const(${ Expr(i) }) }
          case l: Long    => '{ AnnotationArg.Const(${ Expr(l) }) }
          case f: Float   => '{ AnnotationArg.Const(${ Expr(f) }) }
          case d: Double  => '{ AnnotationArg.Const(${ Expr(d) }) }
          case b: Boolean => '{ AnnotationArg.Const(${ Expr(b) }) }
          case c: Char    => '{ AnnotationArg.Const(${ Expr(c) }) }
          case b: Byte    => '{ AnnotationArg.Const(${ Expr(b) }) }
          case s: Short   => '{ AnnotationArg.Const(${ Expr(s) }) }
          case null       => '{ AnnotationArg.Const(null) }
          case ()         => '{ AnnotationArg.Const(()) }
          case _          => '{ AnnotationArg.Const(null) }
        }
      case AnnotationArg.ArrayArg(values) =>
        val valuesExpr = Expr.ofList(values.map(v => Expr(v)))
        '{ AnnotationArg.ArrayArg($valuesExpr) }
      case AnnotationArg.Named(name, value) =>
        '{ AnnotationArg.Named(${ Expr(name) }, ${ Expr(value) }) }
      case AnnotationArg.Nested(annotation) =>
        '{ AnnotationArg.Nested(${ Expr(annotation) }) }
      case AnnotationArg.ClassOf(tpe) =>
        '{ AnnotationArg.ClassOf(${ Expr(tpe) }) }
      case AnnotationArg.EnumValue(enumType, valueName) =>
        '{ AnnotationArg.EnumValue(${ typeIdToExpr(enumType) }, ${ Expr(valueName) }) }
    }
  }

  given ToExpr[Annotation] with {
    def apply(ann: Annotation)(using Quotes): Expr[Annotation] = {
      val argsExpr = Expr.ofList(ann.args.map(a => Expr(a)))
      '{ Annotation(${ typeIdToExpr(ann.typeId) }, $argsExpr) }
    }
  }

  // ============================================================================
  // Analysis Functions (return actual types, not Data classes)
  // ============================================================================

  private def analyzeOwner(using Quotes)(sym: quotes.reflect.Symbol): Owner = {
    import quotes.reflect.*

    def loop(s: Symbol, acc: List[Owner.Segment]): List[Owner.Segment] =
      if (s.isNoSymbol || s == defn.RootPackage || s == defn.RootClass || s == defn.EmptyPackageClass) {
        acc
      } else if (s.isPackageDef) {
        loop(s.owner, Owner.Package(s.name) :: acc)
      } else if (s.isClassDef && s.flags.is(Flags.Module)) {
        loop(s.owner, Owner.Term(s.name.stripSuffix("$")) :: acc)
      } else if (s.isClassDef) {
        loop(s.owner, Owner.Type(s.name) :: acc)
      } else {
        loop(s.owner, acc)
      }

    Owner(loop(sym, Nil))
  }

  private def analyzeTypeParams(using Quotes)(sym: quotes.reflect.Symbol): List[TypeParam] = {
    import quotes.reflect.*

    val typeParams = sym.declaredTypes.filter(_.isTypeParam)
    typeParams.zipWithIndex.map { case (p, idx) =>
      val variance: Variance = scala.util.Try(p.tree).toOption match {
        case Some(td: TypeDef) =>
          val defFlags = td.symbol.flags
          if (defFlags.is(Flags.Covariant)) Variance.Covariant
          else if (defFlags.is(Flags.Contravariant)) Variance.Contravariant
          else Variance.Invariant
        case _ =>
          if (p.flags.is(Flags.Covariant)) Variance.Covariant
          else if (p.flags.is(Flags.Contravariant)) Variance.Contravariant
          else Variance.Invariant
      }

      TypeParam(p.name, idx, variance)
    }
  }

  private def analyzeTypeReprMinimal(using Quotes)(tpe: quotes.reflect.TypeRepr): zio.blocks.typeid.TypeRepr = {
    import quotes.reflect.*

    tpe match {
      case tref: TypeRef =>
        val sym     = tref.typeSymbol
        val rawName = sym.name
        val name    = if (sym.flags.is(Flags.Module)) rawName.stripSuffix("$") else rawName

        name match {
          case "Int"     => zio.blocks.typeid.TypeRepr.Ref(TypeId.int)
          case "String"  => zio.blocks.typeid.TypeRepr.Ref(TypeId.string)
          case "Long"    => zio.blocks.typeid.TypeRepr.Ref(TypeId.long)
          case "Boolean" => zio.blocks.typeid.TypeRepr.Ref(TypeId.boolean)
          case "Double"  => zio.blocks.typeid.TypeRepr.Ref(TypeId.double)
          case "Float"   => zio.blocks.typeid.TypeRepr.Ref(TypeId.float)
          case "Byte"    => zio.blocks.typeid.TypeRepr.Ref(TypeId.byte)
          case "Short"   => zio.blocks.typeid.TypeRepr.Ref(TypeId.short)
          case "Char"    => zio.blocks.typeid.TypeRepr.Ref(TypeId.char)
          case "Unit"    => zio.blocks.typeid.TypeRepr.Ref(TypeId.unit)
          case "List"    => zio.blocks.typeid.TypeRepr.Ref(TypeId.list)
          case "Option"  => zio.blocks.typeid.TypeRepr.Ref(TypeId.option)
          case "Map"     => zio.blocks.typeid.TypeRepr.Ref(TypeId.map)
          case "Either"  => zio.blocks.typeid.TypeRepr.Ref(TypeId.either)
          case "Set"     => zio.blocks.typeid.TypeRepr.Ref(TypeId.set)
          case "Vector"  => zio.blocks.typeid.TypeRepr.Ref(TypeId.vector)
          case "Any"     => zio.blocks.typeid.TypeRepr.AnyType
          case "Nothing" => zio.blocks.typeid.TypeRepr.NothingType
          case "Null"    => zio.blocks.typeid.TypeRepr.NullType
          case _         =>
            val owner = analyzeOwner(sym.owner)
            zio.blocks.typeid.TypeRepr.Ref(TypeId.nominal[Nothing](name, owner, Nil, Nil, TypeDefKind.Unknown))
        }
      case _ =>
        val sym  = tpe.typeSymbol
        val name = if (sym.isNoSymbol) "Unknown" else sym.name
        zio.blocks.typeid.TypeRepr.Ref(TypeId.nominal[Nothing](name, Owner.Root, Nil, Nil, TypeDefKind.Unknown))
    }
  }

  private val filteredBaseTypes = Set(
    "scala.Any",
    "scala.AnyRef",
    "java.lang.Object",
    "scala.Matchable",
    "scala.Product",
    "scala.Equals",
    "scala.deriving.Mirror",
    "scala.deriving.Mirror$.Product",
    "scala.deriving.Mirror$.Singleton",
    "scala.deriving.Mirror$.Sum",
    "java.io.Serializable"
  )

  private def analyzeBaseTypes(using Quotes)(sym: quotes.reflect.Symbol): List[zio.blocks.typeid.TypeRepr] = {
    val baseClasses =
      sym.typeRef.baseClasses.filterNot(base => base == sym || filteredBaseTypes.contains(base.fullName))
    baseClasses.map(base => analyzeTypeReprMinimal(base.typeRef))
  }

  private def analyzeDefKind(using Quotes)(sym: quotes.reflect.Symbol): TypeDefKind = {
    import quotes.reflect.*

    val flags = sym.flags

    if (flags.is(Flags.Enum) && !flags.is(Flags.Case)) {
      analyzeEnumDefKind(sym)
    } else if (flags.is(Flags.Enum) && flags.is(Flags.Case)) {
      analyzeEnumCaseDefKind(sym)
    } else if (flags.is(Flags.Module)) {
      TypeDefKind.Object(analyzeBaseTypes(sym))
    } else if (flags.is(Flags.Trait)) {
      analyzeTraitDefKind(sym)
    } else if (sym.isClassDef) {
      analyzeClassDefKind(sym, flags)
    } else if (flags.is(Flags.Opaque)) {
      TypeDefKind.OpaqueType(zio.blocks.typeid.TypeBounds.Unbounded)
    } else if (sym.isAliasType) {
      TypeDefKind.TypeAlias
    } else if (sym.isAbstractType) {
      TypeDefKind.AbstractType
    } else {
      TypeDefKind.Unknown
    }
  }

  private def analyzeClassDefKind(using
    Quotes
  )(sym: quotes.reflect.Symbol, flags: quotes.reflect.Flags): TypeDefKind = {
    import quotes.reflect.*

    val isFinal    = flags.is(Flags.Final)
    val isAbstract = flags.is(Flags.Abstract)
    val isCase     = flags.is(Flags.Case)
    val isValue    = sym.typeRef.baseClasses.exists(_.fullName == "scala.AnyVal")

    TypeDefKind.Class(isFinal, isAbstract, isCase, isValue, analyzeBaseTypes(sym))
  }

  private def analyzeTraitDefKind(using Quotes)(sym: quotes.reflect.Symbol): TypeDefKind = {
    import quotes.reflect.*

    val flags    = sym.flags
    val isSealed = flags.is(Flags.Sealed)

    if (isSealed) {
      TypeDefKind.Trait(isSealed = true, analyzeBaseTypes(sym))
    } else {
      TypeDefKind.Trait(isSealed = false, analyzeBaseTypes(sym))
    }
  }

  private def analyzeEnumDefKind(using Quotes)(sym: quotes.reflect.Symbol): TypeDefKind =
    TypeDefKind.Enum(analyzeBaseTypes(sym))

  private def analyzeEnumCaseDefKind(using Quotes)(caseSym: quotes.reflect.Symbol): TypeDefKind = {
    import quotes.reflect.*

    val parentSym = caseSym.owner
    val siblings  = parentSym.children.filter(_.flags.is(Flags.Case))
    val ordinal   = siblings.indexOf(caseSym)

    val isObjectCase = caseSym.flags.is(Flags.Module) ||
      caseSym.primaryConstructor.paramSymss.flatten.isEmpty

    val parentTypeRepr = analyzeTypeReprMinimal(parentSym.typeRef)

    TypeDefKind.EnumCase(parentTypeRepr, ordinal, isObjectCase)
  }

  private def analyzeAnnotations(using Quotes)(sym: quotes.reflect.Symbol): List[Annotation] = {
    val annotations = sym.annotations.filterNot { annot =>
      val annotSym = annot.tpe.typeSymbol
      val fullName = annotSym.fullName
      fullName.startsWith("scala.annotation.internal.") ||
      fullName.startsWith("scala.annotation.unchecked.") ||
      fullName == "scala.annotation.nowarn" ||
      fullName == "scala.annotation.targetName"
    }

    annotations.flatMap(annot => analyzeAnnotation(annot))
  }

  private def analyzeAnnotation(using Quotes)(annot: quotes.reflect.Term): Option[Annotation] = {
    import quotes.reflect.*

    val annotTpe = annot.tpe
    val annotSym = annotTpe.typeSymbol

    if (annotSym.isNoSymbol) return None

    val annotName   = annotSym.name
    val annotOwner  = analyzeOwner(annotSym.owner)
    val annotTypeId = TypeId.nominal[Any](
      annotName,
      annotOwner,
      Nil,
      Nil,
      TypeDefKind.Class(isFinal = false, isAbstract = false, isCase = false, isValue = false)
    )

    val args = annot match {
      case Apply(_, args) => args.flatMap(arg => analyzeAnnotationArg(arg))
      case _              => Nil
    }

    Some(Annotation(annotTypeId, args))
  }

  private def analyzeAnnotationArg(using Quotes)(arg: quotes.reflect.Term): Option[AnnotationArg] = {
    import quotes.reflect.*

    arg match {
      case Literal(const) =>
        Some(AnnotationArg.Const(const.value))

      case NamedArg(name, value) =>
        analyzeAnnotationArg(value).map(v => AnnotationArg.Named(name, v))

      case Typed(expr, _) =>
        analyzeAnnotationArg(expr)

      case Apply(TypeApply(Select(Ident("Array"), "apply"), _), List(Typed(Repeated(elems, _), _))) =>
        val elemArgs = elems.flatMap(e => analyzeAnnotationArg(e))
        Some(AnnotationArg.ArrayArg(elemArgs))

      case Repeated(elems, _) =>
        val elemArgs = elems.flatMap(e => analyzeAnnotationArg(e))
        Some(AnnotationArg.ArrayArg(elemArgs))

      case TypeApply(Select(Ident("Predef"), "classOf"), List(tpt)) =>
        val typeRepr = analyzeTypeReprMinimal(tpt.tpe)
        Some(AnnotationArg.ClassOf(typeRepr))

      case Select(qualifier, name) if arg.tpe.typeSymbol.flags.is(Flags.Enum) =>
        val enumTypeSym = qualifier.tpe.typeSymbol
        val enumOwner   = analyzeOwner(enumTypeSym.owner)
        val enumTypeId  = TypeId.nominal[Any](
          enumTypeSym.name,
          enumOwner,
          Nil,
          Nil,
          TypeDefKind.Enum(Nil)
        )
        Some(AnnotationArg.EnumValue(enumTypeId, name))

      case Apply(Select(New(tpt), _), nestedArgs) =>
        val nestedAnnotSym = tpt.tpe.typeSymbol
        val nestedOwner    = analyzeOwner(nestedAnnotSym.owner)
        val nestedTypeId   = TypeId.nominal[Any](
          nestedAnnotSym.name,
          nestedOwner,
          Nil,
          Nil,
          TypeDefKind.Class(isFinal = false, isAbstract = false, isCase = false, isValue = false)
        )
        val nestedArgsData = nestedArgs.flatMap(a => analyzeAnnotationArg(a))
        Some(AnnotationArg.Nested(Annotation(nestedTypeId, nestedArgsData)))

      case _ =>
        arg.tpe.widenTermRefByName match {
          case ConstantType(const) =>
            Some(AnnotationArg.Const(const.value))
          case _ =>
            None
        }
    }
  }

  private def buildOwner(using Quotes)(sym: quotes.reflect.Symbol): Expr[Owner] =
    Expr(analyzeOwner(sym))

  private def buildTypeParams(using Quotes)(sym: quotes.reflect.Symbol): Expr[List[TypeParam]] =
    Expr.ofList(analyzeTypeParams(sym).map(p => Expr(p)))

  private def buildDefKind(using Quotes)(sym: quotes.reflect.Symbol): Expr[TypeDefKind] =
    Expr(analyzeDefKind(sym))

  private def buildAnnotations(using Quotes)(sym: quotes.reflect.Symbol): Expr[List[Annotation]] =
    Expr.ofList(analyzeAnnotations(sym).map(a => Expr(a)))

  /**
   * Derives a TypeId for any type or type constructor.
   *
   * This macro first searches for an existing implicit TypeId instance. If
   * found, it uses that instance. Otherwise, it derives a new one by
   * extracting:
   *   - The type's simple name
   *   - The owner path (packages, enclosing objects/classes)
   *   - Type parameters (for type constructors)
   *   - Classification (nominal, alias, or opaque)
   */
  inline def derived[A <: AnyKind]: TypeId[A] = ${ derivedImpl[A] }

  def ofImpl[A <: AnyKind: Type](using Quotes): Expr[TypeId[A]] = {
    import quotes.reflect.*

    val tpe      = TypeRepr.of[A]
    val sym      = tpe.typeSymbol
    val fullName = if (sym.isNoSymbol) "" else sym.fullName

    fullName match {
      case "scala.Int"     => '{ TypeId.int.asInstanceOf[TypeId[A]] }
      case "scala.Long"    => '{ TypeId.long.asInstanceOf[TypeId[A]] }
      case "scala.Double"  => '{ TypeId.double.asInstanceOf[TypeId[A]] }
      case "scala.Float"   => '{ TypeId.float.asInstanceOf[TypeId[A]] }
      case "scala.Boolean" => '{ TypeId.boolean.asInstanceOf[TypeId[A]] }
      case "scala.Byte"    => '{ TypeId.byte.asInstanceOf[TypeId[A]] }
      case "scala.Short"   => '{ TypeId.short.asInstanceOf[TypeId[A]] }
      case "scala.Char"    => '{ TypeId.char.asInstanceOf[TypeId[A]] }
      case "scala.Unit"    => '{ TypeId.unit.asInstanceOf[TypeId[A]] }
      case _               => deriveTypeId[A]
    }
  }

  /**
   * Entry point for given TypeId.derived[A] - always derives directly, never
   * searches at top level. Used as implicit/given for automatic derivation. The
   * compiler already searched for implicits and chose derived.
   */
  def derivedImpl[A <: AnyKind: Type](using Quotes): Expr[TypeId[A]] =
    deriveTypeId[A]

  private def deriveTypeId[A <: AnyKind: Type](using Quotes): Expr[TypeId[A]] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[A]
    Option(typeIdCache.get(tpe)) match {
      case Some(cached) => typeIdToExpr(cached).asInstanceOf[Expr[TypeId[A]]]
      case None         => deriveTypeIdCore[A](tpe)
    }
  }

  private def deriveTypeIdCore[A <: AnyKind: Type](using
    Quotes
  )(tpe: quotes.reflect.TypeRepr): Expr[TypeId[A]] = {
    import quotes.reflect.*

    tpe match {
      case OrType(_, _) =>
        deriveUnionType[A](flattenUnion(tpe))
      case AndType(_, _) =>
        deriveIntersectionType[A](flattenIntersection(tpe))
      case AppliedType(tycon, args) =>
        tycon match {
          case tr: TypeRef if tr.typeSymbol.isAliasType =>
            deriveAppliedTypeAlias[A](tr, args)
          case _ =>
            deriveAppliedTypeNew[A](tycon, args)
        }
      case tr: TypeRef if tr.typeSymbol.isAliasType && !isUserDefinedAlias(tr) =>
        getPredefinedTypeId[A](tr.dealias).getOrElse(deriveNew[A](tpe))
      case _ =>
        deriveNew[A](tpe)
    }
  }

  private def isUserDefinedAlias(using Quotes)(tr: quotes.reflect.TypeRef): Boolean = {
    val owner     = tr.typeSymbol.owner
    val ownerPath = buildOwnerPath(owner)
    val isBuiltIn = ownerPath.exists(_.contains("Predef")) ||
      (ownerPath.contains("scala") && ownerPath.exists(_.contains("package")))
    !isBuiltIn
  }

  private def buildOwnerPath(using Quotes)(sym: quotes.reflect.Symbol): List[String] = {
    import quotes.reflect.*
    def loop(s: Symbol, acc: List[String]): List[String] =
      if (s.isNoSymbol || s == defn.RootPackage || s == defn.RootClass) acc
      else loop(s.owner, s.name :: acc)
    loop(sym, Nil)
  }

  private def getPredefinedTypeId[A <: AnyKind: Type](using
    Quotes
  )(
    dealiased: quotes.reflect.TypeRepr
  ): Option[Expr[TypeId[A]]] = {
    val sym      = dealiased.typeSymbol
    val fullName = if (sym.isNoSymbol) "" else sym.fullName
    fullName match {
      case "java.lang.String"                           => Some('{ TypeId.string.asInstanceOf[TypeId[A]] })
      case "scala.math.BigInt" | "scala.BigInt"         => Some('{ TypeId.bigInt.asInstanceOf[TypeId[A]] })
      case "scala.math.BigDecimal" | "scala.BigDecimal" => Some('{ TypeId.bigDecimal.asInstanceOf[TypeId[A]] })
      case _                                            => None
    }
  }

  // Helper to flatten nested unions into a list
  private def flattenUnion(using Quotes)(tpe: quotes.reflect.TypeRepr): List[quotes.reflect.TypeRepr] = {
    import quotes.reflect.*
    tpe match {
      case OrType(left, right) => flattenUnion(left) ++ flattenUnion(right)
      case other               => List(other)
    }
  }

  // Helper to flatten nested intersections into a list
  private def flattenIntersection(using Quotes)(tpe: quotes.reflect.TypeRepr): List[quotes.reflect.TypeRepr] = {
    import quotes.reflect.*
    tpe match {
      case AndType(left, right) => flattenIntersection(left) ++ flattenIntersection(right)
      case other                => List(other)
    }
  }

  private def deriveAppliedTypeNew[A <: AnyKind: Type](using
    Quotes
  )(
    tycon: quotes.reflect.TypeRepr,
    args: List[quotes.reflect.TypeRepr]
  ): Expr[TypeId[A]] = {
    import quotes.reflect.*

    val tyconSym     = tycon.typeSymbol
    val tyconName    = tyconSym.name
    val typeArgsExpr = args.map(arg => buildTypeReprFromTypeRepr(arg, Set.empty[String]))

    getPredefinedTypeConstructor(tyconName) match {
      case Some(baseExpr) =>
        '{
          val base = $baseExpr.asInstanceOf[TypeId[A]]
          TypeId.nominal[A](
            base.name,
            base.owner,
            base.typeParams,
            ${ Expr.ofList(typeArgsExpr) },
            base.defKind,
            base.selfType,
            base.annotations
          )
        }
      case None =>
        val typeParams = tyconSym.typeMembers.filter(_.isTypeParam)
        if (typeParams.nonEmpty) {
          val wildcardArgs    = typeParams.map(_ => TypeRepr.of[Any])
          val existentialType = tycon.appliedTo(wildcardArgs)

          existentialType.asType match {
            case '[t] =>
              val typeIdType = TypeRepr.of[TypeId[t]]
              Implicits.search(typeIdType) match {
                case iss: ImplicitSearchSuccess =>
                  val foundTree = iss.tree
                  val isDerived = foundTree match {
                    case Inlined(Some(call), _, _) =>
                      call.symbol.fullName.contains("TypeIdMacros") || call.symbol.fullName.contains("derived")
                    case _ => foundTree.symbol.fullName.contains("derived")
                  }
                  if (isDerived) {
                    deriveAppliedTypeFresh[A](tycon, args)
                  } else {
                    val foundTyconId = iss.tree.asExprOf[TypeId[t]]
                    '{
                      val base = $foundTyconId.asInstanceOf[TypeId[A]]
                      TypeId.nominal[A](
                        base.name,
                        base.owner,
                        base.typeParams,
                        ${ Expr.ofList(typeArgsExpr) },
                        base.defKind,
                        base.selfType,
                        base.annotations
                      )
                    }
                  }
                case _: ImplicitSearchFailure =>
                  deriveAppliedTypeFresh[A](tycon, args)
              }
            case _ =>
              deriveAppliedTypeFresh[A](tycon, args)
          }
        } else {
          deriveAppliedTypeFresh[A](tycon, args)
        }
    }
  }

  private def getPredefinedTypeConstructor(name: String)(using Quotes): Option[Expr[TypeId[?]]] =
    name match {
      case "List"       => Some('{ TypeId.list })
      case "Option"     => Some('{ TypeId.option })
      case "Some"       => Some('{ TypeId.some })
      case "Map"        => Some('{ TypeId.map })
      case "Either"     => Some('{ TypeId.either })
      case "Set"        => Some('{ TypeId.set })
      case "Vector"     => Some('{ TypeId.vector })
      case "Seq"        => Some('{ TypeId.seq })
      case "IndexedSeq" => Some('{ TypeId.indexedSeq })
      case _            => None
    }

  private def deriveAppliedTypeFresh[A <: AnyKind: Type](using
    Quotes
  )(
    tycon: quotes.reflect.TypeRepr,
    args: List[quotes.reflect.TypeRepr]
  ): Expr[TypeId[A]] = {
    val tyconSym        = tycon.typeSymbol
    val name            = tyconSym.name
    val ownerExpr       = buildOwner(tyconSym.owner)
    val typeParamsExpr  = buildTypeParams(tyconSym)
    val typeArgsExpr    = args.map(arg => buildTypeReprFromTypeRepr(arg, Set.empty[String]))
    val defKindExpr     = buildDefKind(tyconSym)
    val annotationsExpr = buildAnnotations(tyconSym)

    '{
      TypeId.nominal[A](
        ${ Expr(name) },
        $ownerExpr,
        $typeParamsExpr,
        ${ Expr.ofList(typeArgsExpr) },
        $defKindExpr,
        None,
        $annotationsExpr
      )
    }
  }

  private def deriveAppliedTypeAlias[A <: AnyKind: Type](using
    Quotes
  )(
    tr: quotes.reflect.TypeRef,
    @annotation.unused args: List[quotes.reflect.TypeRepr]
  ): Expr[TypeId[A]] = {

    val typeSymbol      = tr.typeSymbol
    val name            = typeSymbol.name
    val ownerExpr       = buildOwner(typeSymbol.owner)
    val typeParamsExpr  = buildTypeParams(typeSymbol)
    val annotationsExpr = buildAnnotations(typeSymbol)

    val aliasedType = tr.translucentSuperType.dealias
    val aliasedExpr = buildTypeReprFromTypeRepr(aliasedType, Set.empty[String])

    '{
      TypeId.alias[A](
        ${ Expr(name) },
        ${ ownerExpr },
        ${ typeParamsExpr },
        ${ aliasedExpr },
        Nil,
        $annotationsExpr
      )
    }
  }

  private def deriveUnionType[A <: AnyKind: Type](using
    Quotes
  )(
    types: List[quotes.reflect.TypeRepr]
  ): Expr[TypeId[A]] = {
    // Build a TypeId that represents this union type
    // We create an alias that points to a Union TypeRepr
    val typeReprs = types.map(t => buildTypeReprFromTypeRepr(t, Set.empty[String]))

    '{
      TypeId.alias[A](
        "Union",
        Owner.Root,
        Nil,
        zio.blocks.typeid.TypeRepr.Union(${ Expr.ofList(typeReprs) }),
        Nil,
        Nil
      )
    }
  }

  private def deriveIntersectionType[A <: AnyKind: Type](using
    Quotes
  )(
    types: List[quotes.reflect.TypeRepr]
  ): Expr[TypeId[A]] = {
    // Build a TypeId that represents this intersection type
    // We create an alias that points to an Intersection TypeRepr
    val typeReprs = types.map(t => buildTypeReprFromTypeRepr(t, Set.empty[String]))

    '{
      TypeId.alias[A](
        "Intersection",
        Owner.Root,
        Nil,
        zio.blocks.typeid.TypeRepr.Intersection(${ Expr.ofList(typeReprs) }),
        Nil,
        Nil
      )
    }
  }

  private def deriveNew[A <: AnyKind: Type](using Quotes)(tpe: quotes.reflect.TypeRepr): Expr[TypeId[A]] = {
    import quotes.reflect.*

    tpe match {
      case tr: TypeRef if tr.typeSymbol.isAliasType => deriveTypeAlias[A](tr)
      case _                                        => deriveNominalOrOpaque[A](tpe)
    }
  }

  private def deriveTypeAlias[A <: AnyKind: Type](using
    Quotes
  )(tr: quotes.reflect.TypeRef): Expr[TypeId[A]] = {

    val typeSymbol      = tr.typeSymbol
    val name            = typeSymbol.name
    val ownerExpr       = buildOwner(typeSymbol.owner)
    val typeParamsExpr  = buildTypeParams(typeSymbol)
    val annotationsExpr = buildAnnotations(typeSymbol)

    val aliasedType = tr.translucentSuperType.dealias
    val aliasedExpr = buildTypeReprFromTypeRepr(aliasedType, Set(typeSymbol.fullName))

    '{
      TypeId.alias[A](
        ${ Expr(name) },
        ${ ownerExpr },
        ${ typeParamsExpr },
        ${ aliasedExpr },
        Nil,
        $annotationsExpr
      )
    }
  }

  private def deriveNominalOrOpaque[A <: AnyKind: Type](using
    Quotes
  )(tpe: quotes.reflect.TypeRepr): Expr[TypeId[A]] = {
    import quotes.reflect.*

    val typeSymbol        = tpe.typeSymbol
    val termSymbol        = tpe.termSymbol
    val isEnumValue       = !termSymbol.isNoSymbol && termSymbol.flags.is(Flags.Enum)
    val (name, ownerExpr) = if (isEnumValue) {
      (termSymbol.name, buildOwner(termSymbol.owner))
    } else {
      val rawName           = typeSymbol.name
      val nm                = if (typeSymbol.flags.is(Flags.Module)) rawName.stripSuffix("$") else rawName
      val directOwner       = typeSymbol.owner
      val resolvedOwnerExpr = tpe match {
        case tr: TypeRef => resolveOwnerExprFromTypeRef(tr, directOwner)
        case _           => buildOwner(directOwner)
      }
      (nm, resolvedOwnerExpr)
    }
    val typeParamsExpr  = buildTypeParams(typeSymbol)
    val annotationsExpr = buildAnnotations(if (isEnumValue) termSymbol else typeSymbol)
    val flags           = typeSymbol.flags
    val defKindExpr     =
      if (isEnumValue) buildEnumCaseDefKindFromTermSymbol(termSymbol)
      else buildDefKind(typeSymbol)
    if (flags.is(Flags.Opaque)) {
      val reprExpr     = extractOpaqueRepresentationExpr(tpe)
      val publicBounds = defKindExpr match {
        case '{ TypeDefKind.OpaqueType($bounds) } => bounds
        case _                                    => '{ zio.blocks.typeid.TypeBounds.Unbounded }
      }
      '{
        TypeId.opaque[A](
          ${ Expr(name) },
          ${ ownerExpr },
          ${ typeParamsExpr },
          ${ reprExpr },
          Nil,
          ${ publicBounds },
          $annotationsExpr
        )
      }
    } else if (isEnumValue) {
      val ownerValue       = analyzeOwner(termSymbol.owner)
      val typeParamsValue  = analyzeTypeParams(typeSymbol)
      val defKindValue     = analyzeEnumCaseDefKind(termSymbol)
      val annotationsValue = analyzeAnnotations(termSymbol)
      val typeIdValue      = TypeId.nominal[Any](
        name,
        ownerValue,
        typeParamsValue,
        Nil,
        defKindValue,
        None,
        annotationsValue
      )
      typeIdCache.put(tpe, typeIdValue)
      typeIdToExpr(typeIdValue).asInstanceOf[Expr[TypeId[A]]]
    } else if (hasSelfType(typeSymbol)) {
      val selfTypeExpr = extractSelfType(typeSymbol)
      '{
        TypeId.nominal[A](
          ${ Expr(name) },
          ${ ownerExpr },
          ${ typeParamsExpr },
          Nil,
          ${ defKindExpr },
          $selfTypeExpr,
          $annotationsExpr
        )
      }
    } else {
      val ownerValue = tpe match {
        case tr: TypeRef => resolveOwnerValueFromTypeRef(tr, typeSymbol.owner)
        case _           => analyzeOwner(typeSymbol.owner)
      }
      val typeParamsValue  = analyzeTypeParams(typeSymbol)
      val defKindValue     = analyzeDefKind(typeSymbol)
      val annotationsValue = analyzeAnnotations(typeSymbol)
      val typeIdValue      = TypeId.nominal[Any](
        name,
        ownerValue,
        typeParamsValue,
        Nil,
        defKindValue,
        None,
        annotationsValue
      )
      typeIdCache.put(tpe, typeIdValue)
      typeIdToExpr(typeIdValue).asInstanceOf[Expr[TypeId[A]]]
    }
  }

  private def extractOpaqueRepresentationExpr(using
    Quotes
  )(tpe: quotes.reflect.TypeRepr): Expr[zio.blocks.typeid.TypeRepr] = {
    import quotes.reflect.*

    tpe match {
      case tr: TypeRef if tr.isOpaqueAlias =>
        val underlying = tr.translucentSuperType.dealias
        buildTypeReprFromTypeRepr(underlying, Set.empty[String])
      case _ =>
        val underlying = tpe.dealias
        if (underlying != tpe) {
          buildTypeReprFromTypeRepr(underlying, Set.empty[String])
        } else {
          '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.string) }
        }
    }
  }

  private def buildTypeReprFromTypeRepr(using
    Quotes
  )(
    tpe: quotes.reflect.TypeRepr,
    visitingAliases: Set[String]
  ): Expr[zio.blocks.typeid.TypeRepr] = {
    import quotes.reflect.*

    val sym         = tpe.typeSymbol
    val symFullName = if (sym.isNoSymbol) "" else sym.fullName
    val isAlias     = !sym.isNoSymbol && sym.isAliasType

    if (isAlias && visitingAliases.contains(symFullName)) {
      val name      = sym.name
      val ownerExpr = buildOwner(sym.owner)
      return '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.nominal[Nothing](${ Expr(name) }, $ownerExpr, Nil)) }
    }

    val newVisiting = if (isAlias) visitingAliases + symFullName else visitingAliases

    tpe match {
      case OrType(_, _) =>
        val types     = flattenUnion(tpe)
        val typeReprs = types.map(t => buildTypeReprFromTypeRepr(t, newVisiting))
        '{ zio.blocks.typeid.TypeRepr.Union(${ Expr.ofList(typeReprs) }) }

      case AndType(_, _) =>
        val types     = flattenIntersection(tpe)
        val typeReprs = types.map(t => buildTypeReprFromTypeRepr(t, newVisiting))
        '{ zio.blocks.typeid.TypeRepr.Intersection(${ Expr.ofList(typeReprs) }) }

      case Refinement(_, _, _) =>
        buildRefinementType(tpe, newVisiting)

      case AppliedType(tycon, args) =>
        val tyconName = tycon.typeSymbol.fullName

        if (isTupleType(tyconName)) {
          buildTupleTypeRepr(args, newVisiting)
        } else if (isFunctionType(tyconName)) {
          val paramTypes = args.init.map(t => buildTypeReprFromTypeRepr(t, newVisiting))
          val resultType = buildTypeReprFromTypeRepr(args.last, newVisiting)
          '{ zio.blocks.typeid.TypeRepr.Function(${ Expr.ofList(paramTypes) }, $resultType) }
        } else if (isContextFunctionType(tyconName)) {
          val paramTypes = args.init.map(t => buildTypeReprFromTypeRepr(t, newVisiting))
          val resultType = buildTypeReprFromTypeRepr(args.last, newVisiting)
          '{ zio.blocks.typeid.TypeRepr.ContextFunction(${ Expr.ofList(paramTypes) }, $resultType) }
        } else {
          val tyconRepr = buildTypeReprFromTypeRepr(tycon, newVisiting)
          val argsRepr  = args.map(t => buildTypeReprFromTypeRepr(t, newVisiting))
          '{ zio.blocks.typeid.TypeRepr.Applied($tyconRepr, ${ Expr.ofList(argsRepr) }) }
        }

      case ConstantType(const) =>
        buildConstantTypeRepr(const)

      case tref: TypeRef =>
        buildTypeRefRepr(tref)

      case tref: TermRef =>
        val path = buildTermPath(tref)
        '{ zio.blocks.typeid.TypeRepr.Singleton($path) }

      case ThisType(tref) =>
        val ownerExpr = buildOwner(tref.typeSymbol)
        '{ zio.blocks.typeid.TypeRepr.ThisType($ownerExpr) }

      case ByNameType(underlying) =>
        val underlyingRepr = buildTypeReprFromTypeRepr(underlying, newVisiting)
        '{ zio.blocks.typeid.TypeRepr.ByName($underlyingRepr) }

      case AnnotatedType(underlying, _) =>
        buildTypeReprFromTypeRepr(underlying, newVisiting)

      case bounds: TypeBounds =>
        val lowerExpr: Expr[Option[zio.blocks.typeid.TypeRepr]] = bounds.low match {
          case nt if nt =:= TypeRepr.of[Nothing] => '{ None }
          case other                             =>
            val otherRepr = buildTypeReprFromTypeRepr(other, newVisiting)
            '{ Some($otherRepr) }
        }
        val upperExpr: Expr[Option[zio.blocks.typeid.TypeRepr]] = bounds.hi match {
          case at if at =:= TypeRepr.of[Any] => '{ None }
          case other                         =>
            val otherRepr = buildTypeReprFromTypeRepr(other, newVisiting)
            '{ Some($otherRepr) }
        }
        '{ zio.blocks.typeid.TypeRepr.Wildcard(zio.blocks.typeid.TypeBounds($lowerExpr, $upperExpr)) }

      case tl: TypeLambda =>
        val params = tl.paramNames.zipWithIndex.map { case (name, idx) =>
          '{ TypeParam(${ Expr(name) }, ${ Expr(idx) }) }
        }
        val bodyRepr = buildTypeReprFromTypeRepr(tl.resType, newVisiting)
        '{ zio.blocks.typeid.TypeRepr.TypeLambda(${ Expr.ofList(params) }, $bodyRepr) }

      // Handle param refs within type lambdas
      case pr: ParamRef =>
        val paramName = pr.binder match {
          case tl: TypeLambda => tl.paramNames(pr.paramNum)
          case _              => s"T${pr.paramNum}"
        }
        '{ zio.blocks.typeid.TypeRepr.ParamRef(TypeParam(${ Expr(paramName) }, ${ Expr(pr.paramNum) }), 0) }

      case other =>
        val sym  = other.typeSymbol
        val name = if (sym.isNoSymbol) "Unknown" else sym.name
        '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.nominal[Nothing](${ Expr(name) }, Owner.Root, Nil)) }
    }
  }

  private def buildConstantTypeRepr(using Quotes)(const: quotes.reflect.Constant): Expr[zio.blocks.typeid.TypeRepr] =

    const.value match {
      case i: Int     => '{ zio.blocks.typeid.TypeRepr.Constant.IntConst(${ Expr(i) }) }
      case l: Long    => '{ zio.blocks.typeid.TypeRepr.Constant.LongConst(${ Expr(l) }) }
      case f: Float   => '{ zio.blocks.typeid.TypeRepr.Constant.FloatConst(${ Expr(f) }) }
      case d: Double  => '{ zio.blocks.typeid.TypeRepr.Constant.DoubleConst(${ Expr(d) }) }
      case b: Boolean => '{ zio.blocks.typeid.TypeRepr.Constant.BooleanConst(${ Expr(b) }) }
      case c: Char    => '{ zio.blocks.typeid.TypeRepr.Constant.CharConst(${ Expr(c) }) }
      case s: String  => '{ zio.blocks.typeid.TypeRepr.Constant.StringConst(${ Expr(s) }) }
      case null       => '{ zio.blocks.typeid.TypeRepr.Constant.NullConst }
      case ()         => '{ zio.blocks.typeid.TypeRepr.Constant.UnitConst }
      case _          => '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.nominal[Nothing]("Constant", Owner.Root, Nil)) }
    }

  private def buildTypeRefRepr(using Quotes)(tref: quotes.reflect.TypeRef): Expr[zio.blocks.typeid.TypeRepr] = {
    import quotes.reflect.*

    val sym     = tref.typeSymbol
    val rawName = sym.name
    val name    = if (sym.flags.is(Flags.Module)) rawName.stripSuffix("$") else rawName

    val typeIdExpr = name match {
      case "Int"     => '{ TypeId.int }
      case "String"  => '{ TypeId.string }
      case "Long"    => '{ TypeId.long }
      case "Boolean" => '{ TypeId.boolean }
      case "Double"  => '{ TypeId.double }
      case "Float"   => '{ TypeId.float }
      case "Byte"    => '{ TypeId.byte }
      case "Short"   => '{ TypeId.short }
      case "Char"    => '{ TypeId.char }
      case "Unit"    => '{ TypeId.unit }
      case "List"    => '{ TypeId.list }
      case "Option"  => '{ TypeId.option }
      case "Map"     => '{ TypeId.map }
      case "Either"  => '{ TypeId.either }
      case "Set"     => '{ TypeId.set }
      case "Vector"  => '{ TypeId.vector }
      case "Any"     => return '{ zio.blocks.typeid.TypeRepr.AnyType }
      case "Nothing" => return '{ zio.blocks.typeid.TypeRepr.NothingType }
      case "Null"    => return '{ zio.blocks.typeid.TypeRepr.NullType }
      case _         =>
        def createFreshTypeId(): Expr[TypeId[Nothing]] = {
          val ownerExpr = buildOwner(sym.owner)
          if (sym.isAliasType) {
            val aliasedType = tref.translucentSuperType.dealias
            val aliasedExpr = buildTypeReprFromTypeRepr(aliasedType, Set(sym.fullName))
            '{ TypeId.alias[Nothing](${ Expr(name) }, $ownerExpr, Nil, $aliasedExpr, Nil, Nil) }
          } else {
            val defKindExpr = buildDefKindShallow(sym)
            '{ TypeId.nominal[Nothing](${ Expr(name) }, $ownerExpr, Nil, Nil, $defKindExpr) }
          }
        }

        tref.asType match {
          case '[t] =>
            val typeIdType = quotes.reflect.TypeRepr.of[TypeId[t]]
            Implicits.search(typeIdType) match {
              case iss: ImplicitSearchSuccess =>
                val foundTree = iss.tree
                val isDerived = foundTree match {
                  case Inlined(Some(call), _, _) =>
                    call.symbol.fullName.contains("TypeIdMacros") ||
                    call.symbol.fullName.contains("derived")
                  case _ if foundTree.symbol.fullName.contains("derived") => true
                  case _                                                  => false
                }
                if (isDerived) createFreshTypeId()
                else iss.tree.asExprOf[TypeId[t]].asInstanceOf[Expr[TypeId[Nothing]]]
              case _: ImplicitSearchFailure =>
                createFreshTypeId()
            }
          case _ =>
            createFreshTypeId()
        }
    }
    '{ zio.blocks.typeid.TypeRepr.Ref($typeIdExpr) }
  }

  private def buildDefKindShallow(using Quotes)(sym: quotes.reflect.Symbol): Expr[TypeDefKind] = {
    import quotes.reflect.*

    val flags = sym.flags

    if (flags.is(Flags.Enum) && !flags.is(Flags.Case)) {
      '{ TypeDefKind.Unknown }
    } else if (flags.is(Flags.Enum) && flags.is(Flags.Case)) {
      '{ TypeDefKind.Unknown }
    } else if (flags.is(Flags.Module)) {
      '{ TypeDefKind.Unknown }
    } else if (flags.is(Flags.Trait)) {
      buildTraitDefKindShallow(sym)
    } else if (sym.isClassDef) {
      buildClassDefKindShallow(sym, flags)
    } else if (flags.is(Flags.Opaque)) {
      '{ TypeDefKind.OpaqueType() }
    } else if (sym.isAliasType) {
      '{ TypeDefKind.TypeAlias }
    } else if (sym.isAbstractType) {
      '{ TypeDefKind.AbstractType }
    } else {
      '{ TypeDefKind.Unknown }
    }
  }

  private def buildClassDefKindShallow(using
    Quotes
  )(
    sym: quotes.reflect.Symbol,
    flags: quotes.reflect.Flags
  ): Expr[TypeDefKind] = {
    import quotes.reflect.*

    val isFinal    = flags.is(Flags.Final)
    val isAbstract = flags.is(Flags.Abstract)
    val isCase     = flags.is(Flags.Case)
    val isValue    = sym.typeRef.baseClasses.exists(_.fullName == "scala.AnyVal")
    val basesExpr  = buildBaseTypesMinimal(sym)

    '{
      TypeDefKind.Class(
        isFinal = ${ Expr(isFinal) },
        isAbstract = ${ Expr(isAbstract) },
        isCase = ${ Expr(isCase) },
        isValue = ${ Expr(isValue) },
        bases = $basesExpr
      )
    }
  }

  private def buildTraitDefKindShallow(using Quotes)(sym: quotes.reflect.Symbol): Expr[TypeDefKind] = {
    import quotes.reflect.*

    val flags    = sym.flags
    val isSealed = flags.is(Flags.Sealed)

    val basesExpr = buildBaseTypesMinimal(sym)

    if (isSealed) {
      '{ TypeDefKind.Trait(isSealed = true, bases = $basesExpr) }
    } else {
      '{ TypeDefKind.Trait(isSealed = false, bases = $basesExpr) }
    }
  }

  private def buildBaseTypesMinimal(using
    Quotes
  )(sym: quotes.reflect.Symbol): Expr[List[zio.blocks.typeid.TypeRepr]] = {
    val baseClasses =
      sym.typeRef.baseClasses.filterNot(base => base == sym || filteredBaseTypes.contains(base.fullName))

    val baseExprs = baseClasses.map { base =>
      buildTypeReprMinimal(base.typeRef)
    }

    Expr.ofList(baseExprs)
  }

  private def buildTypeReprMinimal(using Quotes)(tpe: quotes.reflect.TypeRepr): Expr[zio.blocks.typeid.TypeRepr] = {
    import quotes.reflect.*

    tpe match {
      case tref: TypeRef =>
        val sym     = tref.typeSymbol
        val rawName = sym.name
        val name    = if (sym.flags.is(Flags.Module)) rawName.stripSuffix("$") else rawName

        name match {
          case "Int"     => '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.int) }
          case "String"  => '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.string) }
          case "Long"    => '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.long) }
          case "Boolean" => '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.boolean) }
          case "Double"  => '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.double) }
          case "Float"   => '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.float) }
          case "Byte"    => '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.byte) }
          case "Short"   => '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.short) }
          case "Char"    => '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.char) }
          case "Unit"    => '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.unit) }
          case "List"    => '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.list) }
          case "Option"  => '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.option) }
          case "Map"     => '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.map) }
          case "Either"  => '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.either) }
          case "Set"     => '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.set) }
          case "Vector"  => '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.vector) }
          case "Any"     => '{ zio.blocks.typeid.TypeRepr.AnyType }
          case "Nothing" => '{ zio.blocks.typeid.TypeRepr.NothingType }
          case "Null"    => '{ zio.blocks.typeid.TypeRepr.NullType }
          case _         =>
            val ownerExpr = buildOwner(sym.owner)
            '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.nominal[Nothing](${ Expr(name) }, $ownerExpr, Nil)) }
        }
      case _ =>
        val sym  = tpe.typeSymbol
        val name = if (sym.isNoSymbol) "Unknown" else sym.name
        '{ zio.blocks.typeid.TypeRepr.Ref(TypeId.nominal[Nothing](${ Expr(name) }, Owner.Root, Nil)) }
    }
  }

  private def buildTermPath(using Quotes)(tref: quotes.reflect.TermRef): Expr[TermPath] = {
    import quotes.reflect.*

    def loop(t: quotes.reflect.TypeRepr, acc: List[Expr[TermPath.Segment]]): List[Expr[TermPath.Segment]] = t match {
      case tr: TermRef =>
        val segment = '{ TermPath.Term(${ Expr(tr.termSymbol.name) }) }
        tr.qualifier match {
          case NoPrefix() => segment :: acc
          case q          => loop(q, segment :: acc)
        }
      case tr: TypeRef if tr.typeSymbol.isPackageDef =>
        '{ TermPath.Package(${ Expr(tr.typeSymbol.name) }) } :: acc
      case _ => acc
    }

    val segments = loop(tref, Nil)
    '{ TermPath(${ Expr.ofList(segments) }) }
  }

  private val zioPreludeNewtypeBases = Set(
    "zio.prelude.NewtypeCustom",
    "zio.prelude.SubtypeCustom",
    "zio.prelude.Newtype",
    "zio.prelude.Subtype",
    "zio.prelude.NewtypeVersionSpecific"
  )

  private def isZioPreludeNewtypeBase(using Quotes)(sym: quotes.reflect.Symbol): Boolean =
    !sym.isNoSymbol && zioPreludeNewtypeBases.contains(sym.fullName)

  private def resolveOwnerExprFromTypeRef(using
    Quotes
  )(
    tr: quotes.reflect.TypeRef,
    fallback: quotes.reflect.Symbol
  ): Expr[Owner] = {
    import quotes.reflect.*

    val directOwner           = tr.typeSymbol.owner
    val ownerBases            = directOwner.typeRef.baseClasses.map(_.fullName)
    val isPreludeNewtypeOwner = ownerBases.exists(zioPreludeNewtypeBases.contains) ||
      isZioPreludeNewtypeBase(directOwner)

    if (isPreludeNewtypeOwner) {
      tr.qualifier match {
        case termRef: TermRef =>
          val termSym       = termRef.termSymbol
          val termName      = termSym.name.stripSuffix("$")
          val parentSegment = '{ Owner.Term(${ Expr(termName) }) }
          val parentOwner   = buildOwner(termSym.owner)
          '{ Owner($parentOwner.segments :+ $parentSegment) }
        case _ =>
          buildOwner(fallback)
      }
    } else {
      buildOwner(fallback)
    }
  }

  private def resolveOwnerValueFromTypeRef(using
    Quotes
  )(
    tr: quotes.reflect.TypeRef,
    fallback: quotes.reflect.Symbol
  ): Owner = {
    import quotes.reflect.*

    val directOwner           = tr.typeSymbol.owner
    val ownerBases            = directOwner.typeRef.baseClasses.map(_.fullName)
    val isPreludeNewtypeOwner = ownerBases.exists(zioPreludeNewtypeBases.contains) ||
      isZioPreludeNewtypeBase(directOwner)

    if (isPreludeNewtypeOwner) {
      tr.qualifier match {
        case termRef: TermRef =>
          val termSym     = termRef.termSymbol
          val termName    = termSym.name.stripSuffix("$")
          val parentOwner = analyzeOwner(termSym.owner)
          Owner(parentOwner.segments :+ Owner.Term(termName))
        case _ =>
          analyzeOwner(fallback)
      }
    } else {
      analyzeOwner(fallback)
    }
  }

  // ============================================================================
  // Type Detection Helpers
  // ============================================================================

  private def isTupleType(fullName: String): Boolean =
    fullName.startsWith("scala.Tuple") || fullName == "scala.EmptyTuple" ||
      fullName.startsWith("scala.*:")

  private def isFunctionType(fullName: String): Boolean =
    fullName.startsWith("scala.Function") && !fullName.contains("ContextFunction")

  private def isContextFunctionType(fullName: String): Boolean =
    fullName.startsWith("scala.ContextFunction")

  // ============================================================================
  // Tuple Type Building
  // ============================================================================

  private def buildTupleTypeRepr(using
    Quotes
  )(
    args: List[quotes.reflect.TypeRepr],
    visiting: Set[String]
  ): Expr[zio.blocks.typeid.TypeRepr] = {
    // For now, treat all tuple elements as unlabeled
    // Named tuple support would require additional detection
    val elements = args.map { arg =>
      val tpeRepr = buildTypeReprFromTypeRepr(arg, visiting)
      '{ TupleElement(None, $tpeRepr) }
    }
    '{ zio.blocks.typeid.TypeRepr.Tuple(${ Expr.ofList(elements) }) }
  }

  // ============================================================================
  // Refinement/Structural Type Building
  // ============================================================================

  private def buildRefinementType(using
    Quotes
  )(
    tpe: quotes.reflect.TypeRepr,
    visiting: Set[String]
  ): Expr[zio.blocks.typeid.TypeRepr] = {
    import quotes.reflect.*

    // Collect all refinement members
    def collectRefinements(
      t: quotes.reflect.TypeRepr,
      members: List[(String, quotes.reflect.TypeRepr, Boolean)]
    ): (quotes.reflect.TypeRepr, List[(String, quotes.reflect.TypeRepr, Boolean)]) =
      t match {
        case Refinement(parent, name, info) =>
          val isMethod = info match {
            case _: MethodType => true
            case _             => false
          }
          val memberType = info match {
            case mt: MethodType   => mt.resType
            case TypeBounds(_, _) => t // Abstract type member
            case other            => other
          }
          collectRefinements(parent, (name, memberType, isMethod) :: members)
        case other =>
          (other, members)
      }

    val (parent, refinements) = collectRefinements(tpe, Nil)

    // Build parent types list
    val parentTypes = parent match {
      case AndType(_, _) => flattenIntersection(parent)
      case other         => List(other)
    }
    val parentReprs = parentTypes.map(t => buildTypeReprFromTypeRepr(t, visiting))

    // Build members list
    val memberExprs = refinements.map { case (name, memberType, isMethod) =>
      val typeRepr = buildTypeReprFromTypeRepr(memberType, visiting)
      if (isMethod) {
        // Def member (simplified - no parameters for now)
        '{ Member.Def(${ Expr(name) }, Nil, Nil, $typeRepr) }
      } else {
        // Val member
        '{ Member.Val(${ Expr(name) }, $typeRepr) }
      }
    }

    '{ zio.blocks.typeid.TypeRepr.Structural(${ Expr.ofList(parentReprs) }, ${ Expr.ofList(memberExprs) }) }
  }

  // ============================================================================
  // TypeDefKind Extraction
  // ============================================================================

  private def buildEnumCaseDefKindFromTermSymbol(using
    Quotes
  )(
    termSym: quotes.reflect.Symbol
  ): Expr[TypeDefKind] = {
    import quotes.reflect.*

    val parentSym = termSym.owner

    val siblings = parentSym.children.filter(_.flags.is(Flags.Case))
    val ordinal  = siblings.indexOf(termSym)

    val isObjectCase = termSym.flags.is(Flags.Module) ||
      termSym.primaryConstructor.paramSymss.flatten.isEmpty

    val parentTypeRepr = buildTypeReprFromTypeRepr(parentSym.typeRef, Set.empty[String])

    '{ TypeDefKind.EnumCase($parentTypeRepr, ${ Expr(ordinal) }, ${ Expr(isObjectCase) }) }
  }

  // ============================================================================
  // Self Type Extraction
  // ============================================================================

  private def hasSelfType(using Quotes)(sym: quotes.reflect.Symbol): Boolean = {
    import quotes.reflect.*

    if (!sym.isClassDef) return false

    scala.util.Try(sym.tree).toOption match {
      case Some(classDef: ClassDef) =>
        classDef.self match {
          case Some(selfDef) =>
            val selfTpt = selfDef.tpt
            !(selfTpt.tpe =:= sym.typeRef || selfTpt.tpe.typeSymbol == sym)
          case None => false
        }
      case _ => false
    }
  }

  private def extractSelfType(using
    Quotes
  )(
    sym: quotes.reflect.Symbol
  ): Expr[Option[zio.blocks.typeid.TypeRepr]] = {
    import quotes.reflect.*

    if (!sym.isClassDef) return '{ None }

    scala.util.Try(sym.tree).toOption match {
      case Some(classDef: ClassDef) =>
        classDef.self match {
          case Some(selfDef) =>
            val selfTpt = selfDef.tpt
            if (selfTpt.tpe =:= sym.typeRef || selfTpt.tpe.typeSymbol == sym) {
              '{ None }
            } else {
              val selfTypeReprExpr = buildTypeReprFromTypeRepr(selfTpt.tpe, Set.empty[String])
              '{ Some($selfTypeReprExpr) }
            }
          case None => '{ None }
        }
      case _ => '{ None }
    }
  }

}
