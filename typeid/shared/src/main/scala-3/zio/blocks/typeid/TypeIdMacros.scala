package zio.blocks.typeid

import scala.quoted.*

object TypeIdMacros {

  // ============================================================================
  // Caching Infrastructure
  // ============================================================================
  // Cache intermediate DATA (plain case classes), not Expr values.
  // This allows us to avoid re-analyzing symbols while still running
  // Implicits.search() on every invocation.

  import java.util.concurrent.ConcurrentHashMap

  // --- Intermediate Data Classes ---

  private sealed trait OwnerSegmentData
  private object OwnerSegmentData {
    case class Package(name: String) extends OwnerSegmentData
    case class Term(name: String)    extends OwnerSegmentData
    case class Type(name: String)    extends OwnerSegmentData
  }

  private case class OwnerData(segments: List[OwnerSegmentData])

  private case class TypeParamData(
    name: String,
    index: Int,
    variance: Int // -1 = contravariant, 0 = invariant, 1 = covariant
  )

  private sealed trait TypeDefKindData
  private object TypeDefKindData {
    case class ClassData(
      isFinal: Boolean,
      isAbstract: Boolean,
      isCase: Boolean,
      isValue: Boolean,
      bases: List[TypeReprData]
    ) extends TypeDefKindData
    case class TraitData(isSealed: Boolean, knownSubtypes: List[TypeReprData], bases: List[TypeReprData])
        extends TypeDefKindData
    case class ObjectData(bases: List[TypeReprData])                                       extends TypeDefKindData
    case class EnumData(cases: List[EnumCaseInfoData], bases: List[TypeReprData])          extends TypeDefKindData
    case class EnumCaseData(parentEnum: TypeReprData, ordinal: Int, isObjectCase: Boolean) extends TypeDefKindData
    case object TypeAliasData                                                              extends TypeDefKindData
    case class OpaqueTypeData(publicBounds: TypeBoundsData)                                extends TypeDefKindData
    case object AbstractTypeData                                                           extends TypeDefKindData
    case object UnknownData                                                                extends TypeDefKindData
  }

  private case class EnumCaseInfoData(
    name: String,
    ordinal: Int,
    params: List[EnumCaseParamData],
    isObjectCase: Boolean
  )

  private case class EnumCaseParamData(name: String, tpe: TypeReprData)

  private case class TypeBoundsData(lower: Option[TypeReprData], upper: Option[TypeReprData])

  private sealed trait TypeReprData
  private object TypeReprData {
    case class RefData(name: String, owner: OwnerData, typeParams: List[TypeParamData], defKind: TypeDefKindData)
        extends TypeReprData
    case class ParamRefData(param: TypeParamData, binderDepth: Int)                   extends TypeReprData
    case class AppliedData(tycon: TypeReprData, args: List[TypeReprData])             extends TypeReprData
    case class StructuralData(parents: List[TypeReprData], members: List[MemberData]) extends TypeReprData
    case class IntersectionData(types: List[TypeReprData])                            extends TypeReprData
    case class UnionData(types: List[TypeReprData])                                   extends TypeReprData
    case class TupleData(elems: List[TupleElementData])                               extends TypeReprData
    case class FunctionData(params: List[TypeReprData], result: TypeReprData)         extends TypeReprData
    case class ContextFunctionData(params: List[TypeReprData], result: TypeReprData)  extends TypeReprData
    case class TypeLambdaData(params: List[TypeParamData], body: TypeReprData)        extends TypeReprData
    case class ByNameData(underlying: TypeReprData)                                   extends TypeReprData
    case class WildcardData(bounds: TypeBoundsData)                                   extends TypeReprData
    case class SingletonData(path: TermPathData)                                      extends TypeReprData
    case class ThisTypeData(owner: OwnerData)                                         extends TypeReprData
    case class IntConstData(value: Int)                                               extends TypeReprData
    case class LongConstData(value: Long)                                             extends TypeReprData
    case class FloatConstData(value: Float)                                           extends TypeReprData
    case class DoubleConstData(value: Double)                                         extends TypeReprData
    case class BooleanConstData(value: Boolean)                                       extends TypeReprData
    case class CharConstData(value: Char)                                             extends TypeReprData
    case class StringConstData(value: String)                                         extends TypeReprData
    case object NullConstData                                                         extends TypeReprData
    case object UnitConstData                                                         extends TypeReprData
    case object AnyTypeData                                                           extends TypeReprData
    case object NothingTypeData                                                       extends TypeReprData
    case object NullTypeData                                                          extends TypeReprData
    // Predefined type references (for primitives and common types)
    case class PredefinedData(name: String) extends TypeReprData
    // Alias type reference
    case class AliasRefData(name: String, owner: OwnerData, aliased: TypeReprData) extends TypeReprData
  }

  private case class TupleElementData(label: Option[String], tpe: TypeReprData)

  private sealed trait MemberData
  private object MemberData {
    case class ValData(name: String, tpe: TypeReprData, isVar: Boolean) extends MemberData
    case class DefData(name: String, result: TypeReprData)              extends MemberData
  }

  private sealed trait TermPathSegmentData
  private object TermPathSegmentData {
    case class Package(name: String) extends TermPathSegmentData
    case class Term(name: String)    extends TermPathSegmentData
  }

  private case class TermPathData(segments: List[TermPathSegmentData])

  private sealed trait AnnotationArgData
  private object AnnotationArgData {
    case class ConstData(value: Any)                                                   extends AnnotationArgData
    case class ArrayArgData(values: List[AnnotationArgData])                           extends AnnotationArgData
    case class NamedData(name: String, value: AnnotationArgData)                       extends AnnotationArgData
    case class NestedData(typeId: AnnotationTypeIdData, args: List[AnnotationArgData]) extends AnnotationArgData
    case class ClassOfData(tpe: TypeReprData)                                          extends AnnotationArgData
    case class EnumValueData(enumType: AnnotationTypeIdData, valueName: String)        extends AnnotationArgData
  }

  private case class AnnotationTypeIdData(name: String, owner: OwnerData)

  private case class AnnotationData(typeId: AnnotationTypeIdData, args: List[AnnotationArgData])

  // --- Caches ---

  private val ownerCache       = new ConcurrentHashMap[String, OwnerData]()
  private val typeParamsCache  = new ConcurrentHashMap[String, List[TypeParamData]]()
  private val defKindCache     = new ConcurrentHashMap[String, TypeDefKindData]()
  private val annotationsCache = new ConcurrentHashMap[String, List[AnnotationData]]()

  // --- Data to Expr Conversion Functions ---

  private def ownerSegmentDataToExpr(data: OwnerSegmentData)(using Quotes): Expr[Owner.Segment] = data match {
    case OwnerSegmentData.Package(name) => '{ Owner.Package(${ Expr(name) }) }
    case OwnerSegmentData.Term(name)    => '{ Owner.Term(${ Expr(name) }) }
    case OwnerSegmentData.Type(name)    => '{ Owner.Type(${ Expr(name) }) }
  }

  private def ownerDataToExpr(data: OwnerData)(using Quotes): Expr[Owner] = {
    val segmentsExpr = Expr.ofList(data.segments.map(s => ownerSegmentDataToExpr(s)))
    '{ Owner($segmentsExpr) }
  }

  private def typeParamDataToExpr(data: TypeParamData)(using Quotes): Expr[TypeParam] = {
    val varianceExpr = data.variance match {
      case -1 => '{ Variance.Contravariant }
      case 1  => '{ Variance.Covariant }
      case _  => '{ Variance.Invariant }
    }
    '{ TypeParam(${ Expr(data.name) }, ${ Expr(data.index) }, $varianceExpr) }
  }

  private def typeBoundsDataToExpr(data: TypeBoundsData)(using Quotes): Expr[TypeBounds] = {
    val lowerExpr: Expr[Option[TypeRepr]] = data.lower match {
      case Some(l) => '{ Some(${ typeReprDataToExpr(l) }) }
      case None    => '{ None }
    }
    val upperExpr: Expr[Option[TypeRepr]] = data.upper match {
      case Some(u) => '{ Some(${ typeReprDataToExpr(u) }) }
      case None    => '{ None }
    }
    '{ TypeBounds($lowerExpr, $upperExpr) }
  }

  private def typeReprDataToExpr(data: TypeReprData)(using Quotes): Expr[TypeRepr] = data match {
    case TypeReprData.PredefinedData(name) =>
      name match {
        case "Int"     => '{ TypeRepr.Ref(TypeId.int) }
        case "String"  => '{ TypeRepr.Ref(TypeId.string) }
        case "Long"    => '{ TypeRepr.Ref(TypeId.long) }
        case "Boolean" => '{ TypeRepr.Ref(TypeId.boolean) }
        case "Double"  => '{ TypeRepr.Ref(TypeId.double) }
        case "Float"   => '{ TypeRepr.Ref(TypeId.float) }
        case "Byte"    => '{ TypeRepr.Ref(TypeId.byte) }
        case "Short"   => '{ TypeRepr.Ref(TypeId.short) }
        case "Char"    => '{ TypeRepr.Ref(TypeId.char) }
        case "Unit"    => '{ TypeRepr.Ref(TypeId.unit) }
        case "List"    => '{ TypeRepr.Ref(TypeId.list) }
        case "Option"  => '{ TypeRepr.Ref(TypeId.option) }
        case "Map"     => '{ TypeRepr.Ref(TypeId.map) }
        case "Either"  => '{ TypeRepr.Ref(TypeId.either) }
        case "Set"     => '{ TypeRepr.Ref(TypeId.set) }
        case "Vector"  => '{ TypeRepr.Ref(TypeId.vector) }
        case _         => '{ TypeRepr.Ref(TypeId.nominal[Nothing](${ Expr(name) }, Owner.Root, Nil)) }
      }
    case TypeReprData.RefData(name, owner, typeParams, defKind) =>
      val ownerExpr      = ownerDataToExpr(owner)
      val typeParamsExpr = Expr.ofList(typeParams.map(p => typeParamDataToExpr(p)))
      val defKindExpr    = typeDefKindDataToExpr(defKind)
      '{ TypeRepr.Ref(TypeId.nominal[Nothing](${ Expr(name) }, $ownerExpr, $typeParamsExpr, Nil, $defKindExpr)) }
    case TypeReprData.AliasRefData(name, owner, aliased) =>
      val ownerExpr   = ownerDataToExpr(owner)
      val aliasedExpr = typeReprDataToExpr(aliased)
      '{ TypeRepr.Ref(TypeId.alias[Nothing](${ Expr(name) }, $ownerExpr, Nil, $aliasedExpr, Nil, Nil)) }
    case TypeReprData.ParamRefData(param, binderDepth) =>
      '{ TypeRepr.ParamRef(${ typeParamDataToExpr(param) }, ${ Expr(binderDepth) }) }
    case TypeReprData.AppliedData(tycon, args) =>
      val tyconExpr = typeReprDataToExpr(tycon)
      val argsExpr  = Expr.ofList(args.map(a => typeReprDataToExpr(a)))
      '{ TypeRepr.Applied($tyconExpr, $argsExpr) }
    case TypeReprData.StructuralData(parents, members) =>
      val parentsExpr = Expr.ofList(parents.map(p => typeReprDataToExpr(p)))
      val membersExpr = Expr.ofList(members.map(m => memberDataToExpr(m)))
      '{ TypeRepr.Structural($parentsExpr, $membersExpr) }
    case TypeReprData.IntersectionData(types) =>
      val typesExpr = Expr.ofList(types.map(t => typeReprDataToExpr(t)))
      '{ TypeRepr.Intersection($typesExpr) }
    case TypeReprData.UnionData(types) =>
      val typesExpr = Expr.ofList(types.map(t => typeReprDataToExpr(t)))
      '{ TypeRepr.Union($typesExpr) }
    case TypeReprData.TupleData(elems) =>
      val elemsExpr = Expr.ofList(elems.map(e => tupleElementDataToExpr(e)))
      '{ TypeRepr.Tuple($elemsExpr) }
    case TypeReprData.FunctionData(params, result) =>
      val paramsExpr = Expr.ofList(params.map(p => typeReprDataToExpr(p)))
      val resultExpr = typeReprDataToExpr(result)
      '{ TypeRepr.Function($paramsExpr, $resultExpr) }
    case TypeReprData.ContextFunctionData(params, result) =>
      val paramsExpr = Expr.ofList(params.map(p => typeReprDataToExpr(p)))
      val resultExpr = typeReprDataToExpr(result)
      '{ TypeRepr.ContextFunction($paramsExpr, $resultExpr) }
    case TypeReprData.TypeLambdaData(params, body) =>
      val paramsExpr = Expr.ofList(params.map(p => typeParamDataToExpr(p)))
      val bodyExpr   = typeReprDataToExpr(body)
      '{ TypeRepr.TypeLambda($paramsExpr, $bodyExpr) }
    case TypeReprData.ByNameData(underlying) =>
      '{ TypeRepr.ByName(${ typeReprDataToExpr(underlying) }) }
    case TypeReprData.WildcardData(bounds) =>
      '{ TypeRepr.Wildcard(${ typeBoundsDataToExpr(bounds) }) }
    case TypeReprData.SingletonData(path) =>
      '{ TypeRepr.Singleton(${ termPathDataToExpr(path) }) }
    case TypeReprData.ThisTypeData(owner) =>
      '{ TypeRepr.ThisType(${ ownerDataToExpr(owner) }) }
    case TypeReprData.IntConstData(v)     => '{ TypeRepr.Constant.IntConst(${ Expr(v) }) }
    case TypeReprData.LongConstData(v)    => '{ TypeRepr.Constant.LongConst(${ Expr(v) }) }
    case TypeReprData.FloatConstData(v)   => '{ TypeRepr.Constant.FloatConst(${ Expr(v) }) }
    case TypeReprData.DoubleConstData(v)  => '{ TypeRepr.Constant.DoubleConst(${ Expr(v) }) }
    case TypeReprData.BooleanConstData(v) => '{ TypeRepr.Constant.BooleanConst(${ Expr(v) }) }
    case TypeReprData.CharConstData(v)    => '{ TypeRepr.Constant.CharConst(${ Expr(v) }) }
    case TypeReprData.StringConstData(v)  => '{ TypeRepr.Constant.StringConst(${ Expr(v) }) }
    case TypeReprData.NullConstData       => '{ TypeRepr.Constant.NullConst }
    case TypeReprData.UnitConstData       => '{ TypeRepr.Constant.UnitConst }
    case TypeReprData.AnyTypeData         => '{ TypeRepr.AnyType }
    case TypeReprData.NothingTypeData     => '{ TypeRepr.NothingType }
    case TypeReprData.NullTypeData        => '{ TypeRepr.NullType }
  }

  private def tupleElementDataToExpr(data: TupleElementData)(using Quotes): Expr[TupleElement] = {
    val labelExpr = data.label match {
      case Some(l) => '{ Some(${ Expr(l) }) }
      case None    => '{ None }
    }
    '{ TupleElement($labelExpr, ${ typeReprDataToExpr(data.tpe) }) }
  }

  private def memberDataToExpr(data: MemberData)(using Quotes): Expr[Member] = data match {
    case MemberData.ValData(name, tpe, isVar) =>
      '{ Member.Val(${ Expr(name) }, ${ typeReprDataToExpr(tpe) }, ${ Expr(isVar) }) }
    case MemberData.DefData(name, result) =>
      '{ Member.Def(${ Expr(name) }, Nil, Nil, ${ typeReprDataToExpr(result) }) }
  }

  private def termPathSegmentDataToExpr(data: TermPathSegmentData)(using Quotes): Expr[TermPath.Segment] = data match {
    case TermPathSegmentData.Package(name) => '{ TermPath.Package(${ Expr(name) }) }
    case TermPathSegmentData.Term(name)    => '{ TermPath.Term(${ Expr(name) }) }
  }

  private def termPathDataToExpr(data: TermPathData)(using Quotes): Expr[TermPath] = {
    val segmentsExpr = Expr.ofList(data.segments.map(s => termPathSegmentDataToExpr(s)))
    '{ TermPath($segmentsExpr) }
  }

  private def enumCaseParamDataToExpr(data: EnumCaseParamData)(using Quotes): Expr[EnumCaseParam] =
    '{ EnumCaseParam(${ Expr(data.name) }, ${ typeReprDataToExpr(data.tpe) }) }

  private def enumCaseInfoDataToExpr(data: EnumCaseInfoData)(using Quotes): Expr[EnumCaseInfo] = {
    val paramsExpr = Expr.ofList(data.params.map(p => enumCaseParamDataToExpr(p)))
    '{ EnumCaseInfo(${ Expr(data.name) }, ${ Expr(data.ordinal) }, $paramsExpr, ${ Expr(data.isObjectCase) }) }
  }

  private def typeDefKindDataToExpr(data: TypeDefKindData)(using Quotes): Expr[TypeDefKind] = data match {
    case TypeDefKindData.ClassData(isFinal, isAbstract, isCase, isValue, bases) =>
      val basesExpr = Expr.ofList(bases.map(b => typeReprDataToExpr(b)))
      '{
        TypeDefKind.Class(
          isFinal = ${ Expr(isFinal) },
          isAbstract = ${ Expr(isAbstract) },
          isCase = ${ Expr(isCase) },
          isValue = ${ Expr(isValue) },
          bases = $basesExpr
        )
      }
    case TypeDefKindData.TraitData(isSealed, knownSubtypes, bases) =>
      val subtypesExpr = Expr.ofList(knownSubtypes.map(s => typeReprDataToExpr(s)))
      val basesExpr    = Expr.ofList(bases.map(b => typeReprDataToExpr(b)))
      '{ TypeDefKind.Trait(isSealed = ${ Expr(isSealed) }, knownSubtypes = $subtypesExpr, bases = $basesExpr) }
    case TypeDefKindData.ObjectData(bases) =>
      val basesExpr = Expr.ofList(bases.map(b => typeReprDataToExpr(b)))
      '{ TypeDefKind.Object(bases = $basesExpr) }
    case TypeDefKindData.EnumData(cases, bases) =>
      val casesExpr = Expr.ofList(cases.map(c => enumCaseInfoDataToExpr(c)))
      val basesExpr = Expr.ofList(bases.map(b => typeReprDataToExpr(b)))
      '{ TypeDefKind.Enum(cases = $casesExpr, bases = $basesExpr) }
    case TypeDefKindData.EnumCaseData(parentEnum, ordinal, isObjectCase) =>
      '{ TypeDefKind.EnumCase(${ typeReprDataToExpr(parentEnum) }, ${ Expr(ordinal) }, ${ Expr(isObjectCase) }) }
    case TypeDefKindData.TypeAliasData          => '{ TypeDefKind.TypeAlias }
    case TypeDefKindData.OpaqueTypeData(bounds) =>
      '{ TypeDefKind.OpaqueType(${ typeBoundsDataToExpr(bounds) }) }
    case TypeDefKindData.AbstractTypeData => '{ TypeDefKind.AbstractType }
    case TypeDefKindData.UnknownData      => '{ TypeDefKind.Unknown }
  }

  private def annotationTypeIdDataToExpr(data: AnnotationTypeIdData)(using Quotes): Expr[TypeId[?]] = {
    val ownerExpr = ownerDataToExpr(data.owner)
    '{
      TypeId.nominal[Any](
        ${ Expr(data.name) },
        $ownerExpr,
        Nil,
        Nil,
        TypeDefKind.Class(isFinal = false, isAbstract = false, isCase = false, isValue = false)
      )
    }
  }

  private def annotationArgDataToExpr(data: AnnotationArgData)(using Quotes): Expr[AnnotationArg] = data match {
    case AnnotationArgData.ConstData(value) =>
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
    case AnnotationArgData.ArrayArgData(values) =>
      val valuesExpr = Expr.ofList(values.map(v => annotationArgDataToExpr(v)))
      '{ AnnotationArg.ArrayArg($valuesExpr) }
    case AnnotationArgData.NamedData(name, value) =>
      '{ AnnotationArg.Named(${ Expr(name) }, ${ annotationArgDataToExpr(value) }) }
    case AnnotationArgData.NestedData(typeId, args) =>
      val argsExpr = Expr.ofList(args.map(a => annotationArgDataToExpr(a)))
      '{ AnnotationArg.Nested(Annotation(${ annotationTypeIdDataToExpr(typeId) }, $argsExpr)) }
    case AnnotationArgData.ClassOfData(tpe) =>
      '{ AnnotationArg.ClassOf(${ typeReprDataToExpr(tpe) }) }
    case AnnotationArgData.EnumValueData(enumType, valueName) =>
      '{ AnnotationArg.EnumValue(${ annotationTypeIdDataToExpr(enumType) }, ${ Expr(valueName) }) }
  }

  private def annotationDataToExpr(data: AnnotationData)(using Quotes): Expr[Annotation] = {
    val argsExpr = Expr.ofList(data.args.map(a => annotationArgDataToExpr(a)))
    '{ Annotation(${ annotationTypeIdDataToExpr(data.typeId) }, $argsExpr) }
  }

  // --- Analysis Functions (return Data, not Expr) ---

  private def analyzeOwner(using Quotes)(sym: quotes.reflect.Symbol): OwnerData = {
    import quotes.reflect.*

    def loop(s: Symbol, acc: List[OwnerSegmentData]): List[OwnerSegmentData] =
      if (s.isNoSymbol || s == defn.RootPackage || s == defn.RootClass || s == defn.EmptyPackageClass) {
        acc
      } else if (s.isPackageDef) {
        loop(s.owner, OwnerSegmentData.Package(s.name) :: acc)
      } else if (s.isClassDef && s.flags.is(Flags.Module)) {
        loop(s.owner, OwnerSegmentData.Term(s.name.stripSuffix("$")) :: acc)
      } else if (s.isClassDef) {
        loop(s.owner, OwnerSegmentData.Type(s.name) :: acc)
      } else {
        loop(s.owner, acc)
      }

    OwnerData(loop(sym, Nil))
  }

  private def analyzeTypeParams(using Quotes)(sym: quotes.reflect.Symbol): List[TypeParamData] = {
    import quotes.reflect.*

    val typeParams = sym.declaredTypes.filter(_.isTypeParam)
    typeParams.zipWithIndex.map { case (p, idx) =>
      val variance: Flags = scala.util.Try(p.tree).toOption match {
        case Some(td: TypeDef) =>
          val defFlags = td.symbol.flags
          if (defFlags.is(Flags.Covariant)) Flags.Covariant
          else if (defFlags.is(Flags.Contravariant)) Flags.Contravariant
          else Flags.EmptyFlags
        case _ =>
          if (p.flags.is(Flags.Covariant)) Flags.Covariant
          else if (p.flags.is(Flags.Contravariant)) Flags.Contravariant
          else Flags.EmptyFlags
      }

      val varianceInt =
        if (variance == Flags.Covariant) 1
        else if (variance == Flags.Contravariant) -1
        else 0

      TypeParamData(p.name, idx, varianceInt)
    }
  }

  private def analyzeTypeReprMinimal(using Quotes)(tpe: quotes.reflect.TypeRepr): TypeReprData = {
    import quotes.reflect.*

    tpe match {
      case tref: TypeRef =>
        val sym     = tref.typeSymbol
        val rawName = sym.name
        val name    = if (sym.flags.is(Flags.Module)) rawName.stripSuffix("$") else rawName

        name match {
          case "Int" | "String" | "Long" | "Boolean" | "Double" | "Float" | "Byte" | "Short" | "Char" | "Unit" |
              "List" | "Option" | "Map" | "Either" | "Set" | "Vector" =>
            TypeReprData.PredefinedData(name)
          case "Any"     => TypeReprData.AnyTypeData
          case "Nothing" => TypeReprData.NothingTypeData
          case "Null"    => TypeReprData.NullTypeData
          case _         =>
            val ownerData = analyzeOwner(sym.owner)
            TypeReprData.RefData(name, ownerData, Nil, TypeDefKindData.UnknownData)
        }
      case _ =>
        val sym  = tpe.typeSymbol
        val name = if (sym.isNoSymbol) "Unknown" else sym.name
        TypeReprData.RefData(name, OwnerData(Nil), Nil, TypeDefKindData.UnknownData)
    }
  }

  private def analyzeBaseTypes(using Quotes)(sym: quotes.reflect.Symbol): List[TypeReprData] = {
    val baseClasses = sym.typeRef.baseClasses.filterNot { base =>
      base == sym ||
      base.fullName == "scala.Any" ||
      base.fullName == "scala.AnyRef" ||
      base.fullName == "java.lang.Object" ||
      base.fullName == "scala.Matchable"
    }

    baseClasses.map(base => analyzeTypeReprMinimal(base.typeRef))
  }

  private def analyzeDefKind(using Quotes)(sym: quotes.reflect.Symbol): TypeDefKindData = {
    import quotes.reflect.*

    val flags = sym.flags

    if (flags.is(Flags.Enum) && !flags.is(Flags.Case)) {
      analyzeEnumDefKind(sym)
    } else if (flags.is(Flags.Enum) && flags.is(Flags.Case)) {
      analyzeEnumCaseDefKind(sym)
    } else if (flags.is(Flags.Module)) {
      TypeDefKindData.ObjectData(analyzeBaseTypes(sym))
    } else if (flags.is(Flags.Trait)) {
      analyzeTraitDefKind(sym)
    } else if (sym.isClassDef) {
      analyzeClassDefKind(sym, flags)
    } else if (flags.is(Flags.Opaque)) {
      TypeDefKindData.OpaqueTypeData(TypeBoundsData(None, None))
    } else if (sym.isAliasType) {
      TypeDefKindData.TypeAliasData
    } else if (sym.isAbstractType) {
      TypeDefKindData.AbstractTypeData
    } else {
      TypeDefKindData.UnknownData
    }
  }

  private def analyzeClassDefKind(using
    Quotes
  )(sym: quotes.reflect.Symbol, flags: quotes.reflect.Flags): TypeDefKindData = {
    import quotes.reflect.*

    val isFinal    = flags.is(Flags.Final)
    val isAbstract = flags.is(Flags.Abstract)
    val isCase     = flags.is(Flags.Case)
    val isValue    = sym.typeRef.baseClasses.exists(_.fullName == "scala.AnyVal")

    TypeDefKindData.ClassData(isFinal, isAbstract, isCase, isValue, analyzeBaseTypes(sym))
  }

  private def analyzeTraitDefKind(using Quotes)(sym: quotes.reflect.Symbol): TypeDefKindData = {
    import quotes.reflect.*

    val flags    = sym.flags
    val isSealed = flags.is(Flags.Sealed)

    if (isSealed) {
      val children    = sym.children
      val subtypeData = children.map(child => analyzeTypeReprMinimal(child.typeRef))
      TypeDefKindData.TraitData(isSealed = true, subtypeData, analyzeBaseTypes(sym))
    } else {
      TypeDefKindData.TraitData(isSealed = false, Nil, analyzeBaseTypes(sym))
    }
  }

  private def analyzeEnumDefKind(using Quotes)(sym: quotes.reflect.Symbol): TypeDefKindData = {
    import quotes.reflect.*

    val children = sym.children
    val caseData = children.zipWithIndex.collect {
      case (child, idx) if child.flags.is(Flags.Case) =>
        analyzeEnumCaseInfo(child, idx)
    }

    TypeDefKindData.EnumData(caseData, analyzeBaseTypes(sym))
  }

  private def analyzeEnumCaseInfo(using Quotes)(caseSym: quotes.reflect.Symbol, ordinal: Int): EnumCaseInfoData = {
    import quotes.reflect.*

    val name         = caseSym.name
    val isObjectCase = caseSym.flags.is(Flags.Module) ||
      caseSym.primaryConstructor.paramSymss.flatten.isEmpty

    if (isObjectCase) {
      EnumCaseInfoData(name, ordinal, Nil, isObjectCase = true)
    } else {
      val params = caseSym.primaryConstructor.paramSymss.flatten.filter(_.isTerm).map { param =>
        val paramName     = param.name
        val paramType     = param.termRef.widenTermRefByName
        val paramTypeData = analyzeTypeReprMinimal(paramType)
        EnumCaseParamData(paramName, paramTypeData)
      }
      EnumCaseInfoData(name, ordinal, params, isObjectCase = false)
    }
  }

  private def analyzeEnumCaseDefKind(using Quotes)(caseSym: quotes.reflect.Symbol): TypeDefKindData = {
    import quotes.reflect.*

    val parentSym = caseSym.owner
    val siblings  = parentSym.children.filter(_.flags.is(Flags.Case))
    val ordinal   = siblings.indexOf(caseSym)

    val isObjectCase = caseSym.flags.is(Flags.Module) ||
      caseSym.primaryConstructor.paramSymss.flatten.isEmpty

    val parentTypeData = analyzeTypeReprMinimal(parentSym.typeRef)

    TypeDefKindData.EnumCaseData(parentTypeData, ordinal, isObjectCase)
  }

  private def analyzeAnnotations(using Quotes)(sym: quotes.reflect.Symbol): List[AnnotationData] = {
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

  private def analyzeAnnotation(using Quotes)(annot: quotes.reflect.Term): Option[AnnotationData] = {
    import quotes.reflect.*

    val annotTpe = annot.tpe
    val annotSym = annotTpe.typeSymbol

    if (annotSym.isNoSymbol) return None

    val annotName      = annotSym.name
    val annotOwnerData = analyzeOwner(annotSym.owner)
    val annotTypeId    = AnnotationTypeIdData(annotName, annotOwnerData)

    val argsData = annot match {
      case Apply(_, args) => args.flatMap(arg => analyzeAnnotationArg(arg))
      case _              => Nil
    }

    Some(AnnotationData(annotTypeId, argsData))
  }

  private def analyzeAnnotationArg(using Quotes)(arg: quotes.reflect.Term): Option[AnnotationArgData] = {
    import quotes.reflect.*

    arg match {
      case Literal(const) =>
        Some(AnnotationArgData.ConstData(const.value))

      case NamedArg(name, value) =>
        analyzeAnnotationArg(value).map(v => AnnotationArgData.NamedData(name, v))

      case Typed(expr, _) =>
        analyzeAnnotationArg(expr)

      case Apply(TypeApply(Select(Ident("Array"), "apply"), _), List(Typed(Repeated(elems, _), _))) =>
        val elemData = elems.flatMap(e => analyzeAnnotationArg(e))
        Some(AnnotationArgData.ArrayArgData(elemData))

      case Repeated(elems, _) =>
        val elemData = elems.flatMap(e => analyzeAnnotationArg(e))
        Some(AnnotationArgData.ArrayArgData(elemData))

      case TypeApply(Select(Ident("Predef"), "classOf"), List(tpt)) =>
        val typeData = analyzeTypeReprMinimal(tpt.tpe)
        Some(AnnotationArgData.ClassOfData(typeData))

      case Select(qualifier, name) if arg.tpe.typeSymbol.flags.is(Flags.Enum) =>
        val enumTypeSym   = qualifier.tpe.typeSymbol
        val enumOwnerData = analyzeOwner(enumTypeSym.owner)
        val enumTypeId    = AnnotationTypeIdData(enumTypeSym.name, enumOwnerData)
        Some(AnnotationArgData.EnumValueData(enumTypeId, name))

      case Apply(Select(New(tpt), _), nestedArgs) =>
        val nestedAnnotSym  = tpt.tpe.typeSymbol
        val nestedOwnerData = analyzeOwner(nestedAnnotSym.owner)
        val nestedTypeId    = AnnotationTypeIdData(nestedAnnotSym.name, nestedOwnerData)
        val nestedArgsData  = nestedArgs.flatMap(a => analyzeAnnotationArg(a))
        Some(AnnotationArgData.NestedData(nestedTypeId, nestedArgsData))

      case _ =>
        arg.tpe.widenTermRefByName match {
          case ConstantType(const) =>
            Some(AnnotationArgData.ConstData(const.value))
          case _ =>
            None
        }
    }
  }

  // --- Cached Build Functions ---

  private def buildOwnerCached(using Quotes)(sym: quotes.reflect.Symbol): Expr[Owner] = {
    val key  = if (sym.isNoSymbol) "" else sym.fullName
    val data = ownerCache.computeIfAbsent(key, _ => analyzeOwner(sym))
    ownerDataToExpr(data)
  }

  private def buildTypeParamsCached(using Quotes)(sym: quotes.reflect.Symbol): Expr[List[TypeParam]] = {
    val key  = if (sym.isNoSymbol) "" else sym.fullName
    val data = typeParamsCache.computeIfAbsent(key, _ => analyzeTypeParams(sym))
    Expr.ofList(data.map(p => typeParamDataToExpr(p)))
  }

  private def buildDefKindCached(using Quotes)(sym: quotes.reflect.Symbol): Expr[TypeDefKind] = {
    val key  = if (sym.isNoSymbol) "" else sym.fullName
    val data = defKindCache.computeIfAbsent(key, _ => analyzeDefKind(sym))
    typeDefKindDataToExpr(data)
  }

  private def buildAnnotationsCached(using Quotes)(sym: quotes.reflect.Symbol): Expr[List[Annotation]] = {
    val key  = if (sym.isNoSymbol) "" else sym.fullName
    val data = annotationsCache.computeIfAbsent(key, _ => analyzeAnnotations(sym))
    Expr.ofList(data.map(a => annotationDataToExpr(a)))
  }

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

    val tpe = quotes.reflect.TypeRepr.of[A]

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
        val dealiased = tr.dealias
        getPredefinedTypeId[A](dealiased).getOrElse(deriveNew[A])
      case _ =>
        deriveNew[A]
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
    val ownerExpr       = buildOwnerCached(tyconSym.owner)
    val typeParamsExpr  = buildTypeParamsCached(tyconSym)
    val typeArgsExpr    = args.map(arg => buildTypeReprFromTypeRepr(arg, Set.empty[String]))
    val defKindExpr     = buildDefKindCached(tyconSym)
    val annotationsExpr = buildAnnotationsCached(tyconSym)

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
    val ownerExpr       = buildOwnerCached(typeSymbol.owner)
    val typeParamsExpr  = buildTypeParamsCached(typeSymbol)
    val annotationsExpr = buildAnnotationsCached(typeSymbol)

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

  private def deriveNew[A <: AnyKind: Type](using Quotes): Expr[TypeId[A]] = {
    import quotes.reflect.*

    val tpe = quotes.reflect.TypeRepr.of[A]

    // Check if this is a type alias (TypeRef with isAliasType)
    tpe match {
      case tr: TypeRef if tr.typeSymbol.isAliasType =>
        deriveTypeAlias[A](tr)
      case _ =>
        deriveNominalOrOpaque[A](tpe)
    }
  }

  private def deriveTypeAlias[A <: AnyKind: Type](using
    Quotes
  )(
    tr: quotes.reflect.TypeRef
  ): Expr[TypeId[A]] = {

    val typeSymbol      = tr.typeSymbol
    val name            = typeSymbol.name
    val ownerExpr       = buildOwnerCached(typeSymbol.owner)
    val typeParamsExpr  = buildTypeParamsCached(typeSymbol)
    val annotationsExpr = buildAnnotationsCached(typeSymbol)

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
  )(
    tpe: quotes.reflect.TypeRepr
  ): Expr[TypeId[A]] = {
    import quotes.reflect.*

    val typeSymbol = tpe.typeSymbol
    val termSymbol = tpe.termSymbol

    val isEnumValue = !termSymbol.isNoSymbol && termSymbol.flags.is(Flags.Enum)

    val (name, ownerExpr) = if (isEnumValue) {
      (termSymbol.name, buildOwnerCached(termSymbol.owner))
    } else {
      val rawName     = typeSymbol.name
      val nm          = if (typeSymbol.flags.is(Flags.Module)) rawName.stripSuffix("$") else rawName
      val directOwner = typeSymbol.owner

      val resolvedOwnerExpr = tpe match {
        case tr: TypeRef =>
          resolveOwnerExprFromTypeRef(tr, directOwner)
        case _ =>
          buildOwnerCached(directOwner)
      }

      (nm, resolvedOwnerExpr)
    }

    val typeParamsExpr  = buildTypeParamsCached(typeSymbol)
    val annotationsExpr = buildAnnotationsCached(if (isEnumValue) termSymbol else typeSymbol)
    val selfTypeExpr    = extractSelfType(typeSymbol)

    val flags = typeSymbol.flags

    val defKindExpr =
      if (isEnumValue) buildEnumCaseDefKindFromTermSymbol(termSymbol)
      else buildDefKindCached(typeSymbol)

    if (flags.is(Flags.Opaque)) {
      val reprExpr     = extractOpaqueRepresentation(tpe, typeSymbol)
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
    } else {
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
    }
  }

  private def extractOpaqueRepresentation(using
    Quotes
  )(
    tpe: quotes.reflect.TypeRepr,
    @annotation.unused typeSymbol: quotes.reflect.Symbol
  ): Expr[zio.blocks.typeid.TypeRepr] = {
    import quotes.reflect.*

    // For opaque types, use translucentSuperType to get the underlying type
    tpe match {
      case tr: TypeRef if tr.isOpaqueAlias =>
        val underlying = tr.translucentSuperType.dealias
        buildTypeReprFromTypeRepr(underlying, Set.empty[String])
      case _ =>
        // Fallback - try dealias
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
      val ownerExpr = buildOwnerCached(sym.owner)
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
        val ownerExpr = buildOwnerCached(tref.typeSymbol)
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
          val ownerExpr = buildOwnerCached(sym.owner)
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
      val children     = sym.children
      val subtypeExprs = children.map { child =>
        buildTypeReprMinimal(child.typeRef)
      }
      '{ TypeDefKind.Trait(isSealed = true, knownSubtypes = ${ Expr.ofList(subtypeExprs) }, bases = $basesExpr) }
    } else {
      '{ TypeDefKind.Trait(isSealed = false, bases = $basesExpr) }
    }
  }

  private def buildBaseTypesMinimal(using Quotes)(sym: quotes.reflect.Symbol): Expr[List[TypeRepr]] = {
    val baseClasses = sym.typeRef.baseClasses.filterNot { base =>
      base == sym ||
      base.fullName == "scala.Any" ||
      base.fullName == "scala.AnyRef" ||
      base.fullName == "java.lang.Object" ||
      base.fullName == "scala.Matchable"
    }

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
            val ownerExpr = buildOwnerCached(sym.owner)
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

    def loop(t: TypeRepr, acc: List[Expr[TermPath.Segment]]): List[Expr[TermPath.Segment]] = t match {
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
          val parentOwner   = buildOwnerCached(termSym.owner)
          '{ Owner($parentOwner.segments :+ $parentSegment) }
        case _ =>
          buildOwnerCached(fallback)
      }
    } else {
      buildOwnerCached(fallback)
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
      t: TypeRepr,
      members: List[(String, TypeRepr, Boolean)]
    ): (TypeRepr, List[(String, TypeRepr, Boolean)]) =
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
