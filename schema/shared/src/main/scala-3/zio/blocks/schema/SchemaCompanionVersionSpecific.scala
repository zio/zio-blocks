package zio.blocks.schema

import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.reflect.ClassTag
import scala.quoted._
import zio.blocks.schema.{Term => SchemaTerm}
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset._
import zio.blocks.schema.CommonMacroOps
import zio.blocks.typeid._

trait SchemaCompanionVersionSpecific {
  inline def derived[A]: Schema[A] = ${ SchemaCompanionVersionSpecificImpl.derived }

  def tparam(t: TypeId[?]): TypeParam =
    TypeParam("A", 0, Variance.Covariant, TypeBounds.exact(TypeRepr.Ref(t)))
}

private object SchemaCompanionVersionSpecificImpl {
  def derived[A: Type](using Quotes): Expr[Schema[A]] = new SchemaCompanionVersionSpecificImpl().derived[A]

  private implicit val fullTermNameOrdering: Ordering[Array[String]] = new Ordering[Array[String]] {
    override def compare(x: Array[String], y: Array[String]): Int = {
      val minLen = Math.min(x.length, y.length)
      var idx    = 0
      while (idx < minLen) {
        val cmp = x(idx).compareTo(y(idx))
        if (cmp != 0) return cmp
        idx += 1
      }
      x.length.compare(y.length)
    }
  }
}

private class SchemaCompanionVersionSpecificImpl(using Quotes) {
  import quotes.reflect._
  import zio.blocks.schema.SchemaCompanionVersionSpecificImpl.fullTermNameOrdering

  private val intTpe                = defn.IntClass.typeRef
  private val floatTpe              = defn.FloatClass.typeRef
  private val longTpe               = defn.LongClass.typeRef
  private val doubleTpe             = defn.DoubleClass.typeRef
  private val booleanTpe            = defn.BooleanClass.typeRef
  private val byteTpe               = defn.ByteClass.typeRef
  private val charTpe               = defn.CharClass.typeRef
  private val shortTpe              = defn.ShortClass.typeRef
  private val unitTpe               = defn.UnitClass.typeRef
  private val anyRefTpe             = defn.AnyRefClass.typeRef
  private val anyTpe                = defn.AnyClass.typeRef
  private val stringTpe             = defn.StringClass.typeRef
  private val schemaTpe             = Symbol.requiredClass("zio.blocks.schema.Schema").typeRef
  private val tupleTpe              = Symbol.requiredClass("scala.Tuple").typeRef
  private val arrayClass            = defn.ArrayClass
  private val arrayOfAnyTpe         = arrayClass.typeRef.appliedTo(anyTpe)
  private val newArray              = Select(New(TypeIdent(arrayClass)), arrayClass.primaryConstructor)
  private val newArrayOfAny         = newArray.appliedToType(anyTpe)
  private val wildcard              = TypeBounds(defn.NothingClass.typeRef, anyTpe)
  private val arrayOfWildcardTpe    = arrayClass.typeRef.appliedTo(wildcard)
  private val iterableOfWildcardTpe = Symbol.requiredClass("scala.collection.Iterable").typeRef.appliedTo(wildcard)
  private val iteratorOfWildcardTpe = Symbol.requiredClass("scala.collection.Iterator").typeRef.appliedTo(wildcard)
  private val modifierReflectTpe    = Symbol.requiredClass("zio.blocks.schema.Modifier.Reflect").typeRef
  private val modifierTermTpe       = Symbol.requiredClass("zio.blocks.schema.Modifier.Term").typeRef
  private val modifierTransientTpe  = Symbol.requiredClass("zio.blocks.schema.Modifier.transient").typeRef
  private val iArrayOfAnyRefTpe     = TypeRepr.of[IArray[AnyRef]]
  private val fromIArrayMethod      = Select.unique(Ref(Symbol.requiredModule("scala.runtime.TupleXXL")), "fromIArray")
  private val asInstanceOfMethod    = anyTpe.typeSymbol.declaredMethod("asInstanceOf").head
  private val productElementMethod  = tupleTpe.typeSymbol.methodMember("productElement").head
  private lazy val toTupleMethod    = Select.unique(Ref(Symbol.requiredModule("scala.NamedTuple")), "toTuple")

  private def fail(msg: String): Nothing = CommonMacroOps.fail(msg)

  private def isEnumValue(tpe: TypeRepr): Boolean = tpe.termSymbol.flags.is(Flags.Enum)

  private def isEnumOrModuleValue(tpe: TypeRepr): Boolean = isEnumValue(tpe) || tpe.typeSymbol.flags.is(Flags.Module)

  private def isSealedTraitOrAbstractClass(tpe: TypeRepr): Boolean = tpe.classSymbol.fold(false) { symbol =>
    val flags = symbol.flags
    flags.is(Flags.Sealed) && (flags.is(Flags.Abstract) || flags.is(Flags.Trait))
  }

  private def isOpaque(tpe: TypeRepr): Boolean = tpe.typeSymbol.flags.is(Flags.Opaque)

  private def opaqueDealias(tpe: TypeRepr): TypeRepr = {
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

  private def isNeotype(tpe: TypeRepr): Boolean = tpe match {
    case TypeRef(compTpe, "Type") =>
      compTpe.baseClasses.exists(_.fullName == "neotype.Newtype") || compTpe.baseClasses.exists(
        _.fullName == "neotype.Subtype"
      )
    case _ => false
  }

  private def neotypeDealias(tpe: TypeRepr): TypeRepr = tpe match {
    case TypeRef(compTpe, _) =>
      compTpe.baseClasses.find(c => c.fullName == "neotype.Newtype" || c.fullName == "neotype.Subtype") match {
        case Some(cls) => compTpe.baseType(cls).typeArgs.head.dealias
        case _         => fail(s"Cannot dealias neotype: ${tpe.show}.")
      }
    case _ => fail(s"Cannot dealias neotype: ${tpe.show}.")
  }

  private def isZioPreludeNewtype(tpe: TypeRepr): Boolean = tpe match {
    case TypeRef(compTpe, "Type") => compTpe.baseClasses.exists(_.fullName == "zio.prelude.Newtype")
    case _                        => false
  }

  private def zioPreludeNewtypeDealias(tpe: TypeRepr): TypeRepr = tpe match {
    case TypeRef(compTpe, _) =>
      compTpe.baseClasses.find(_.fullName == "zio.prelude.Newtype") match {
        case Some(cls) => compTpe.baseType(cls).typeArgs.head.dealias
        case _         => cannotDealiasZioPreludeNewtype(tpe)
      }
    case _ => cannotDealiasZioPreludeNewtype(tpe)
  }

  private def cannotDealiasZioPreludeNewtype(tpe: TypeRepr): Nothing =
    fail(s"Cannot dealias zio-prelude newtype: ${tpe.show}.")

  private def isTypeRef(tpe: TypeRepr): Boolean = tpe match {
    case trTpe: TypeRef =>
      val typeSymbol = trTpe.typeSymbol
      typeSymbol.isTypeDef && typeSymbol.isAliasType
    case _ => false
  }

  private def isAppliedTypeAlias(tpe: TypeRepr): Boolean = tpe match {
    case AppliedType(tycon, _) => isTypeRef(tycon)
    case _                     => false
  }

  private def typeRefDealias(tpe: TypeRepr): TypeRepr = tpe match {
    case trTpe: TypeRef =>
      val sTpe = trTpe.translucentSuperType.dealias
      if (sTpe == trTpe) fail(s"Cannot dealias type reference: ${tpe.show}.")
      sTpe
    case _ => fail(s"Cannot dealias type reference: ${tpe.show}.")
  }

  private def dealiasOnDemand(tpe: TypeRepr): TypeRepr =
    if (isOpaque(tpe)) opaqueDealias(tpe)
    else if (isZioPreludeNewtype(tpe)) zioPreludeNewtypeDealias(tpe)
    else if (isTypeRef(tpe)) typeRefDealias(tpe)
    else tpe

  private def isUnion(tpe: TypeRepr): Boolean = CommonMacroOps.isUnion(tpe)

  private def allUnionTypes(tpe: TypeRepr): List[TypeRepr] = CommonMacroOps.allUnionTypes(tpe)

  private def isNonAbstractScalaClass(tpe: TypeRepr): Boolean = tpe.classSymbol.fold(false) { symbol =>
    val flags = symbol.flags
    !(flags.is(Flags.Abstract) || flags.is(Flags.JavaDefined) || flags.is(Flags.Trait))
  }

  private def typeArgs(tpe: TypeRepr): List[TypeRepr] = CommonMacroOps.typeArgs(tpe)

  private def isGenericTuple(tpe: TypeRepr): Boolean = CommonMacroOps.isGenericTuple(tpe)

  private val genericTupleTypeArgsCache = new mutable.HashMap[TypeRepr, List[TypeRepr]]

  private def genericTupleTypeArgs(tpe: TypeRepr): List[TypeRepr] =
    genericTupleTypeArgsCache.getOrElseUpdate(tpe, CommonMacroOps.genericTupleTypeArgs(tpe))

  private def normalizeGenericTuple(tpe: TypeRepr): TypeRepr =
    CommonMacroOps.normalizeGenericTuple(genericTupleTypeArgs(tpe))

  private def isNamedTuple(tpe: TypeRepr): Boolean = tpe match {
    case AppliedType(ntTpe, _) => ntTpe.typeSymbol.fullName == "scala.NamedTuple$.NamedTuple"
    case _                     => false
  }

  private def isJavaTime(tpe: TypeRepr): Boolean = tpe.typeSymbol.fullName.startsWith("java.time.") &&
    (tpe <:< TypeRepr.of[java.time.temporal.Temporal] || tpe <:< TypeRepr.of[java.time.temporal.TemporalAmount])

  private def isIArray(tpe: TypeRepr): Boolean = tpe.typeSymbol.fullName == "scala.IArray$package$.IArray"

  private def isOption(tpe: TypeRepr): Boolean = {
    val sym = tpe.typeSymbol
    sym.fullName == "scala.Option" || sym.fullName == "scala.package.Option"
  }

  private def isCollection(tpe: TypeRepr): Boolean =
    tpe <:< iterableOfWildcardTpe || tpe <:< iteratorOfWildcardTpe || tpe <:< arrayOfWildcardTpe || isIArray(tpe)

  private def directSubTypes(tpe: TypeRepr): List[TypeRepr] = CommonMacroOps.directSubTypes(tpe)

  private val isNonRecursiveCache = new mutable.HashMap[TypeRepr, Boolean]

  private def isNonRecursive(tpe: TypeRepr, nestedTpes: List[TypeRepr] = Nil): Boolean = isNonRecursiveCache
    .getOrElseUpdate(
      tpe,
      tpe <:< intTpe || tpe <:< floatTpe || tpe <:< longTpe || tpe <:< doubleTpe || tpe <:< booleanTpe ||
        tpe <:< byteTpe || tpe <:< charTpe || tpe <:< shortTpe || tpe <:< unitTpe ||
        tpe <:< stringTpe || tpe <:< TypeRepr.of[BigDecimal] || tpe <:< TypeRepr.of[BigInt] || isJavaTime(tpe) ||
        tpe <:< TypeRepr.of[java.util.Currency] || tpe <:< TypeRepr.of[java.util.UUID] || isEnumOrModuleValue(tpe) ||
        tpe <:< TypeRepr.of[DynamicValue] || !nestedTpes.contains(tpe) && {
          val nestedTpes_ = tpe :: nestedTpes
          if (isOption(tpe) || tpe <:< TypeRepr.of[Either[?, ?]] || isCollection(tpe)) {
            typeArgs(tpe).forall(isNonRecursive(_, nestedTpes_))
          } else if (isGenericTuple(tpe)) {
            genericTupleTypeArgs(tpe).forall(isNonRecursive(_, nestedTpes_))
          } else if (isSealedTraitOrAbstractClass(tpe)) directSubTypes(tpe).forall(isNonRecursive(_, nestedTpes_))
          else if (isUnion(tpe)) allUnionTypes(tpe).forall(isNonRecursive(_, nestedTpes_))
          else if (isNamedTuple(tpe)) isNonRecursive(typeArgs(tpe).last, nestedTpes_)
          else if (isNonAbstractScalaClass(tpe)) {
            val (tpeTypeArgs, tpeTypeParams, tpeParams) = tpe.classSymbol.get.primaryConstructor.paramSymss match {
              case tps :: ps if tps.exists(_.isTypeParam) => (typeArgs(tpe), tps, ps)
              case ps                                     => (Nil, Nil, ps)
            }
            tpeParams.forall(_.forall { symbol =>
              var fTpe = tpe.memberType(symbol).dealias
              if (tpeTypeArgs ne Nil) fTpe = fTpe.substituteTypes(tpeTypeParams, tpeTypeArgs)
              isNonRecursive(fTpe, nestedTpes_)
            })
          } else if (isOpaque(tpe)) {
            isNonRecursive(opaqueDealias(tpe), nestedTpes_)
          } else if (isZioPreludeNewtype(tpe)) {
            isNonRecursive(zioPreludeNewtypeDealias(tpe), nestedTpes_)
          } else if (isTypeRef(tpe)) {
            isNonRecursive(typeRefDealias(tpe), nestedTpes_)
          } else false
        }
    )

  private val typeIdCache = new mutable.HashMap[TypeRepr, TypeId[_]]

  private def typeIdOf[T: Type](tpe: TypeRepr, nestedTpes: List[TypeRepr] = Nil): TypeId[T] = {
    def calculateTypeId(tpe: TypeRepr): TypeId[_] = {
      val dealiased = tpe.dealias
      if (dealiased =:= TypeRepr.of[java.lang.String]) zio.blocks.typeid.TypeId.String
      else {
        val sym =
          if (tpe.termSymbol.exists && tpe.termSymbol.flags.is(Flags.Enum) && tpe.termSymbol.flags.is(Flags.Case))
            tpe.termSymbol
          else if (tpe.termSymbol.exists && !tpe.typeSymbol.isClassDef) tpe.termSymbol
          else tpe.typeSymbol
        var ownerSegments = List.empty[zio.blocks.typeid.Owner.Segment]
        if (sym.exists && !isUnion(tpe)) {
          var currOwner = sym.maybeOwner
          while (currOwner.exists && currOwner != defn.RootClass && currOwner != defn.EmptyPackageClass) {
            var ownerName = currOwner.name
            if (ownerName.endsWith("$")) ownerName = ownerName.init
            if (ownerName.endsWith("$package")) ownerName = ownerName.stripSuffix("$package")

            if (ownerName != "package" && ownerName != "<empty>" && ownerName != "") {
              ownerSegments = zio.blocks.typeid.Owner.Package(ownerName) :: ownerSegments
            }
            currOwner = currOwner.maybeOwner
          }
        }
        val ownerObj = zio.blocks.typeid.Owner(ownerSegments)

        var name = if (isUnion(tpe)) "|" else if (sym.exists) sym.name else tpe.show

        if (isNeotype(tpe) || isZioPreludeNewtype(tpe)) {
          val ownerSym = sym.maybeOwner
          if ((name == "Type" || name == "Newtype") && ownerSym.exists) {
            name = ownerSym.name
            if (name.endsWith("$")) name = name.init
          }
        }
        if (name.endsWith("$")) name = name.init

        if (isOption(dealiased) && typeArgs(dealiased).nonEmpty) {
          val arg = typeArgs(dealiased).head
          arg.asType match {
            case '[t] =>
              val inner = typeIdOf[t](arg, tpe :: nestedTpes).asInstanceOf[TypeId[t]]
              zio.blocks.typeid.TypeId.Option(inner)
            case _ =>
              zio.blocks.typeid.TypeId.nominal(
                "Option",
                zio.blocks.typeid.Owner.parse("scala"),
                Nil,
                zio.blocks.typeid.TypeDefKind.Class(false, false, false, false)
              )
          }
        } else if (isCollection(dealiased) && typeArgs(dealiased).nonEmpty) {
          if (dealiased <:< TypeRepr.of[List[?]]) {
            val arg = typeArgs(dealiased).head
            arg.asType match {
              case '[t] => zio.blocks.typeid.TypeId.List(typeIdOf[t](arg, tpe :: nestedTpes).asInstanceOf[TypeId[t]])
              case _    =>
                zio.blocks.typeid.TypeId.nominal(
                  "List",
                  zio.blocks.typeid.Owner.parse("scala.collection.immutable"),
                  Nil,
                  zio.blocks.typeid.TypeDefKind.Class(false, false, false, false)
                )
            }
          } else if (dealiased <:< TypeRepr.of[Vector[?]]) {
            val arg = typeArgs(dealiased).head
            arg.asType match {
              case '[t] => zio.blocks.typeid.TypeId.Vector(typeIdOf[t](arg, tpe :: nestedTpes).asInstanceOf[TypeId[t]])
              case _    =>
                zio.blocks.typeid.TypeId.nominal(
                  "Vector",
                  zio.blocks.typeid.Owner.parse("scala.collection.immutable"),
                  Nil,
                  zio.blocks.typeid.TypeDefKind.Class(false, false, false, false)
                )
            }
          } else if (dealiased <:< TypeRepr.of[Set[?]]) {
            val arg = typeArgs(dealiased).head
            arg.asType match {
              case '[t] => zio.blocks.typeid.TypeId.Set(typeIdOf[t](arg, tpe :: nestedTpes).asInstanceOf[TypeId[t]])
              case _    =>
                zio.blocks.typeid.TypeId.nominal(
                  "Set",
                  zio.blocks.typeid.Owner.parse("scala.collection.immutable"),
                  Nil,
                  zio.blocks.typeid.TypeDefKind.Class(false, false, false, false)
                )
            }
          } else if (dealiased <:< TypeRepr.of[Map[?, ?]]) {
            val kTpe = typeArgs(dealiased).head
            val vTpe = typeArgs(dealiased).last
            (kTpe.asType, vTpe.asType) match {
              case ('[kt], '[vt]) =>
                zio.blocks.typeid.TypeId.Map(
                  typeIdOf[kt](kTpe, tpe :: nestedTpes).asInstanceOf[TypeId[kt]],
                  typeIdOf[vt](vTpe, tpe :: nestedTpes).asInstanceOf[TypeId[vt]]
                )
              case _ =>
                zio.blocks.typeid.TypeId.nominal(
                  "Map",
                  zio.blocks.typeid.Owner.parse("scala.collection.immutable"),
                  Nil,
                  zio.blocks.typeid.TypeDefKind.Class(false, false, false, false)
                )
            }
          } else {
            val cparams = typeArgs(dealiased).map { arg =>
              arg.asType match {
                case '[t] => zio.blocks.schema.Schema.tparam(typeIdOf[t](arg, tpe :: nestedTpes))
                case _    => zio.blocks.typeid.TypeParam("A", 0)
              }
            }
            zio.blocks.typeid.TypeId.nominal(
              name,
              ownerObj,
              cparams.toList,
              zio.blocks.typeid.TypeDefKind.Class(false, false, false, false)
            )
          }
        } else {
          val tparamNames = Vector("A", "B", "C", "D", "E", "F", "G", "H")
          val params      = typeArgs(tpe).zipWithIndex.map { case (arg, idx) =>
            val isTypeBounds = arg match {
              case _: TypeBounds => true
              case _             => false
            }
            val paramName = if (idx < tparamNames.length) tparamNames(idx) else s"T${idx + 1}"
            if (arg.typeSymbol.isType && !isTypeBounds) {
              arg.asType match {
                case '[t] =>
                  zio.blocks.schema.Schema.tparam(typeIdOf[t](arg, tpe :: nestedTpes))
                case _ =>
                  zio.blocks.typeid.TypeParam(
                    paramName,
                    idx,
                    zio.blocks.typeid.Variance.Covariant,
                    zio.blocks.typeid.TypeBounds.empty,
                    zio.blocks.typeid.Kind.Type
                  )
              }
            } else {
              zio.blocks.typeid.TypeParam(
                paramName,
                idx,
                zio.blocks.typeid.Variance.Covariant,
                zio.blocks.typeid.TypeBounds.empty,
                zio.blocks.typeid.Kind.Type
              )
            }
          }

          val tpeTypeSymbol = tpe.typeSymbol
          val isOpaqueType  = isOpaque(tpe) || isNeotype(tpe) || isZioPreludeNewtype(tpe)

          val kind = if (tpeTypeSymbol.exists && tpeTypeSymbol.isClassDef) {
            if (tpeTypeSymbol.flags.is(Flags.Module)) zio.blocks.typeid.TypeDefKind.Object
            else if (tpeTypeSymbol.flags.is(Flags.Trait))
              zio.blocks.typeid.TypeDefKind.Trait(tpeTypeSymbol.flags.is(Flags.Sealed), Nil)
            else if (tpeTypeSymbol.flags.is(Flags.Enum) && !tpeTypeSymbol.flags.is(Flags.Case))
              zio.blocks.typeid.TypeDefKind.Enum(Nil)
            else
              zio.blocks.typeid.TypeDefKind.Class(
                tpeTypeSymbol.flags.is(Flags.Case),
                tpeTypeSymbol.flags.is(Flags.Abstract),
                tpe <:< TypeRepr.of[Product],
                tpeTypeSymbol.flags.is(Flags.Sealed) || isUnion(tpe)
              )
          } else if (isEnumValue(tpe)) zio.blocks.typeid.TypeDefKind.Object
          else if (isOpaqueType) zio.blocks.typeid.TypeDefKind.OpaqueType(zio.blocks.typeid.TypeBounds.empty)
          else zio.blocks.typeid.TypeDefKind.Class(false, false, false, false)

          val parents     = Nil
          val selfType    = None
          val annotations = Nil

          val (innerTypeId, isActuallyOpaque) = if (isOpaque(tpe)) {
            if (nestedTpes.contains(tpe)) (None, false)
            else {
              val underlying = dealiasOnDemand(tpe)
              underlying.asType match {
                case '[u] =>
                  (Some(typeIdOf[u](underlying, tpe :: nestedTpes).asInstanceOf[zio.blocks.typeid.TypeId[Any]]), true)
                case _ => (None, false)
              }
            }
          } else if (isNeotype(tpe)) {
            if (nestedTpes.contains(tpe)) (None, false)
            else {
              val underlying = neotypeDealias(tpe)
              underlying.asType match {
                case '[u] =>
                  (Some(typeIdOf[u](underlying, tpe :: nestedTpes).asInstanceOf[zio.blocks.typeid.TypeId[Any]]), true)
                case _ => (None, false)
              }
            }
          } else if (isZioPreludeNewtype(tpe)) {
            if (nestedTpes.contains(tpe)) (None, false)
            else {
              val underlying = zioPreludeNewtypeDealias(tpe)
              underlying.asType match {
                case '[u] =>
                  (Some(typeIdOf[u](underlying, tpe :: nestedTpes).asInstanceOf[zio.blocks.typeid.TypeId[Any]]), true)
                case _ => (None, false)
              }
            }
          } else if (isTypeRef(tpe) || isAppliedTypeAlias(tpe)) {
            if (nestedTpes.contains(tpe)) (None, false)
            else {
              val underlying = if (isTypeRef(tpe)) typeRefDealias(tpe) else tpe.dealias
              underlying.asType match {
                case '[u] =>
                  (Some(typeIdOf[u](underlying, tpe :: nestedTpes).asInstanceOf[zio.blocks.typeid.TypeId[Any]]), false)
                case _ => (None, false)
              }
            }
          } else (None, false)

          innerTypeId match {
            case Some(inner) if isActuallyOpaque =>
              zio.blocks.typeid.TypeId.opaque(name, ownerObj, params, zio.blocks.typeid.TypeRepr.Ref(inner))
            case Some(inner) =>
              zio.blocks.typeid.TypeId.alias(name, ownerObj, params, zio.blocks.typeid.TypeRepr.Ref(inner), annotations)
            case None =>
              zio.blocks.typeid.TypeId.nominal(name, ownerObj, params, kind, parents, selfType, annotations)
          }
        }
      }
    }

    typeIdCache.getOrElseUpdate(tpe, calculateTypeId(tpe)).asInstanceOf[zio.blocks.typeid.TypeId[T]]
  }

  private def toExpr(typeRepr: zio.blocks.typeid.TypeRepr)(using Quotes): Expr[zio.blocks.typeid.TypeRepr] = {
    import zio.blocks.typeid.TypeRepr as TR
    typeRepr match {
      case TR.Ref(id) =>
        val idExpr = toExpr(id)
        '{ zio.blocks.typeid.TypeRepr.Ref($idExpr) }
      case TR.Applied(tycon, args) =>
        val tyconExpr = toExpr(tycon)
        val argsExpr  = Varargs(args.map(toExpr))
        '{ zio.blocks.typeid.TypeRepr.Applied($tyconExpr, $argsExpr.toList) }
      case _ =>
        // Fallback for complex types in serialization
        val str = Expr(typeRepr.toString)
        '{ zio.blocks.typeid.TypeRepr.Ref(zio.blocks.typeid.TypeId.nominal($str, "zio.blocks.typeid", Nil)) }
    }
  }

  private def toExpr(typeId: TypeId[_])(using Quotes): Expr[TypeId[_]] = {
    val name   = Expr(typeId.name)
    val owner  = Expr(typeId.owner.asString)
    val ps     = typeId.typeParams
    val params = if (ps.isEmpty) '{ Nil }
    else
      Varargs(ps.map { p =>
        val pName     = Expr(p.name)
        val pIdx      = Expr(p.index)
        val pVariance = p.variance match {
          case Variance.Covariant     => '{ zio.blocks.typeid.Variance.Covariant }
          case Variance.Contravariant => '{ zio.blocks.typeid.Variance.Contravariant }
          case _                      => '{ zio.blocks.typeid.Variance.Invariant }
        }
        val boundExpr = p.bounds.asInstanceOf[zio.blocks.typeid.TypeBounds] match {
          case zio.blocks.typeid.TypeBounds(Some(lower), Some(upper)) if lower == upper =>
            val lowerExpr = toExpr(lower.asInstanceOf[zio.blocks.typeid.TypeRepr.Ref].id)
            '{ zio.blocks.typeid.TypeBounds.exact(zio.blocks.typeid.TypeRepr.Ref($lowerExpr)) }
          case zio.blocks.typeid.TypeBounds(_, _) =>
            '{ zio.blocks.typeid.TypeBounds.empty }
        }
        '{
          zio.blocks.typeid.TypeParam(
            $pName,
            $pIdx,
            $pVariance,
            $boundExpr
          )
        }
      })

    val defKind = toExpr(typeId.defKind)
    if (typeId.isAlias && typeId.aliasedTo.isDefined) {
      val aliasedExpr = toExpr(typeId.aliasedTo.get)
      '{
        zio.blocks.typeid.TypeId.alias($name, zio.blocks.typeid.Owner.parse($owner), $params.toList, $aliasedExpr)
      }
    } else if (typeId.isOpaque && typeId.representation.isDefined) {
      val reprExpr = toExpr(typeId.representation.get)
      '{
        zio.blocks.typeid.TypeId.opaque(
          $name,
          zio.blocks.typeid.Owner.parse($owner),
          $params.toList,
          $reprExpr
        )
      }
    } else {
      '{ zio.blocks.typeid.TypeId.nominal($name, zio.blocks.typeid.Owner.parse($owner), $params.toList, $defKind) }
    }
  }

  private def toExpr(kind: zio.blocks.typeid.TypeDefKind)(using Quotes): Expr[zio.blocks.typeid.TypeDefKind] = {
    import zio.blocks.typeid.TypeDefKind
    kind match {
      case TypeDefKind.Class(abstract_, sealed_, case_, value_) =>
        '{
          zio.blocks.typeid.TypeDefKind.Class(
            ${ Expr(abstract_) },
            ${ Expr(sealed_) },
            ${ Expr(case_) },
            ${ Expr(value_) }
          )
        }
      case TypeDefKind.Trait(sealed_, _) =>
        '{ zio.blocks.typeid.TypeDefKind.Trait(${ Expr(sealed_) }, Nil) }
      case TypeDefKind.Object =>
        '{ zio.blocks.typeid.TypeDefKind.Object }
      case TypeDefKind.Enum(cases) =>
        val casesExpr = Expr.ofList(cases.map(enumCaseToExpr))
        '{ zio.blocks.typeid.TypeDefKind.Enum($casesExpr) }
      case TypeDefKind.TypeAlias     => '{ zio.blocks.typeid.TypeDefKind.TypeAlias }
      case TypeDefKind.AbstractType  => '{ zio.blocks.typeid.TypeDefKind.AbstractType }
      case TypeDefKind.OpaqueType(_) =>
        '{ zio.blocks.typeid.TypeDefKind.OpaqueType(zio.blocks.typeid.TypeBounds.empty) }
      case _ => '{ zio.blocks.typeid.TypeDefKind.Class(false, false, false, false) }
    }
  }

  private def enumCaseToExpr(
    caseInfo: zio.blocks.typeid.EnumCaseInfo
  )(using Quotes): Expr[zio.blocks.typeid.EnumCaseInfo] = {
    // Note: TypeRepr serialization is not fully implemented here. Passing Nil params for now.
    val paramsExpr = Expr.ofList(Nil)
    '{
      zio.blocks.typeid.EnumCaseInfo(
        ${ Expr(caseInfo.name) },
        ${ Expr(caseInfo.ordinal) },
        $paramsExpr,
        ${ Expr(caseInfo.isObjectCase) }
      )
    }
  }

  private def doc(tpe: TypeRepr)(using Quotes): Expr[Doc] = {
    if (isEnumValue(tpe)) tpe.termSymbol
    else tpe.typeSymbol
  }.docstring
    .fold('{ Doc.Empty })(s => '{ new Doc.Text(${ Expr(s) }) })
    .asInstanceOf[Expr[Doc]]

  private def modifiers(tpe: TypeRepr)(using Quotes): Expr[Seq[Modifier.Reflect]] = {
    var modifiers: List[Expr[Modifier.Reflect]] = Nil
    {
      if (isEnumValue(tpe)) tpe.termSymbol
      else tpe.typeSymbol
    }.annotations.foreach { annotation =>
      if (annotation.tpe <:< modifierReflectTpe) {
        modifiers = annotation.asExpr.asInstanceOf[Expr[Modifier.Reflect]] :: modifiers
      }
    }
    if (modifiers eq Nil) '{ Nil } else Varargs(modifiers)
  }

  private def summonClassTag[T: Type](using Quotes): Expr[ClassTag[T]] =
    Expr.summon[ClassTag[T]].getOrElse(fail(s"No ClassTag available for ${TypeRepr.of[T].show}"))

  private val schemaRefs = new mutable.HashMap[TypeRepr, Expr[Schema[?]]]
  private val schemaDefs = new mutable.ListBuffer[ValDef]

  private def findImplicitOrDeriveSchema[T: Type](tpe: TypeRepr): Expr[Schema[T]] = schemaRefs
    .getOrElse(
      tpe, {
        val schemaTpeApplied = schemaTpe.appliedTo(tpe)
        Implicits.search(schemaTpeApplied) match {
          case v: ImplicitSearchSuccess => v.tree.asExpr.asInstanceOf[Expr[Schema[?]]]
          case _                        =>
            val name  = s"s${schemaRefs.size}"
            val flags =
              if (isNonRecursive(tpe)) Flags.Implicit
              else Flags.Implicit | Flags.Lazy
            val symbol = Symbol.newVal(Symbol.spliceOwner, name, schemaTpeApplied, flags, Symbol.noSymbol)
            val ref    = Ref(symbol).asExpr.asInstanceOf[Expr[Schema[?]]]
            // adding the schema reference before its derivation to avoid an endless loop on recursive data structures
            schemaRefs.update(tpe, ref)
            implicit val quotes: Quotes = symbol.asQuotes
            val schema                  = deriveSchema(tpe)
            schemaDefs.addOne(ValDef(symbol, new Some(schema.asTerm)))
            ref
        }
      }
    )
    .asInstanceOf[Expr[Schema[T]]]

  private class FieldInfo(
    val name: String,
    val tpe: TypeRepr,
    val defaultValue: Option[Term],
    val getter: Symbol,
    val usedRegisters: RegisterOffset,
    val modifiers: List[Term]
  )

  private abstract class TypeInfo[T: Type] {
    def tpeTypeArgs: List[TypeRepr]

    def usedRegisters: Expr[RegisterOffset]

    def fields[S: Type](nameOverrides: Array[String])(using Quotes): Expr[Seq[SchemaTerm[Binding, S, ?]]]

    def constructor(in: Expr[Registers], offset: Expr[RegisterOffset])(using Quotes): Expr[T]

    def deconstructor(out: Expr[Registers], offset: Expr[RegisterOffset], in: Expr[T])(using Quotes): Term

    def fieldOffset(fTpe: TypeRepr): RegisterOffset = {
      val sTpe = dealiasOnDemand(fTpe)
      if (sTpe <:< intTpe) RegisterOffset(ints = 1)
      else if (sTpe <:< floatTpe) RegisterOffset(floats = 1)
      else if (sTpe <:< longTpe) RegisterOffset(longs = 1)
      else if (sTpe <:< doubleTpe) RegisterOffset(doubles = 1)
      else if (sTpe <:< booleanTpe) RegisterOffset(booleans = 1)
      else if (sTpe <:< byteTpe) RegisterOffset(bytes = 1)
      else if (sTpe <:< charTpe) RegisterOffset(chars = 1)
      else if (sTpe <:< shortTpe) RegisterOffset(shorts = 1)
      else if (sTpe <:< unitTpe) RegisterOffset.Zero
      else RegisterOffset(objects = 1)
    }

    def fieldConstructor(in: Expr[Registers], offset: Expr[RegisterOffset], fieldInfo: FieldInfo)(using
      Quotes
    ): Term = {
      val fTpe          = fieldInfo.tpe
      val usedRegisters = Expr(fieldInfo.usedRegisters)
      if (fTpe =:= intTpe) '{ $in.getInt($offset + $usedRegisters) }
      else if (fTpe =:= floatTpe) '{ $in.getFloat($offset + $usedRegisters) }
      else if (fTpe =:= longTpe) '{ $in.getLong($offset + $usedRegisters) }
      else if (fTpe =:= doubleTpe) '{ $in.getDouble($offset + $usedRegisters) }
      else if (fTpe =:= booleanTpe) '{ $in.getBoolean($offset + $usedRegisters) }
      else if (fTpe =:= byteTpe) '{ $in.getByte($offset + $usedRegisters) }
      else if (fTpe =:= charTpe) '{ $in.getChar($offset + $usedRegisters) }
      else if (fTpe =:= shortTpe) '{ $in.getShort($offset + $usedRegisters) }
      else if (fTpe =:= unitTpe) '{ () }
      else {
        fTpe.asType match {
          case '[ft] =>
            val sTpe = dealiasOnDemand(fTpe)
            if (sTpe <:< intTpe) '{ $in.getInt($offset + $usedRegisters).asInstanceOf[ft] }
            else if (sTpe <:< floatTpe) '{ $in.getFloat($offset + $usedRegisters).asInstanceOf[ft] }
            else if (sTpe <:< longTpe) '{ $in.getLong($offset + $usedRegisters).asInstanceOf[ft] }
            else if (sTpe <:< doubleTpe) '{ $in.getDouble($offset + $usedRegisters).asInstanceOf[ft] }
            else if (sTpe <:< booleanTpe) '{ $in.getBoolean($offset + $usedRegisters).asInstanceOf[ft] }
            else if (sTpe <:< byteTpe) '{ $in.getByte($offset + $usedRegisters).asInstanceOf[ft] }
            else if (sTpe <:< charTpe) '{ $in.getChar($offset + $usedRegisters).asInstanceOf[ft] }
            else if (sTpe <:< shortTpe) '{ $in.getShort($offset + $usedRegisters).asInstanceOf[ft] }
            else if (sTpe <:< unitTpe) '{ ().asInstanceOf[ft] }
            else '{ $in.getObject($offset + $usedRegisters).asInstanceOf[ft] }
        }
      }
    }.asTerm

    def toBlock(terms: List[Term]): Term = {
      val size = terms.size
      if (size > 1) Block(terms.init, terms.last)
      else if (size > 0) terms.head
      else Literal(UnitConstant())
    }
  }

  private class ClassInfo[T: Type](tpe: TypeRepr) extends TypeInfo {
    private val tpeClassSymbol     = tpe.classSymbol.get
    private val primaryConstructor = tpeClassSymbol.primaryConstructor
    // caching of expensive calls of field and method member gathering
    private var fieldMembers: List[Symbol]                                       = null
    private var methodMembers: List[Symbol]                                      = null
    private var companionRefAndClass: (Ref, Symbol)                              = null
    val tpeTypeArgs: List[TypeRepr]                                              = typeArgs(tpe)
    val (fieldInfos: List[List[FieldInfo]], usedRegisters: Expr[RegisterOffset]) = {
      val (tpeTypeParams, tpeParams) = primaryConstructor.paramSymss match {
        case tps :: ps if tps.exists(_.isTypeParam) => (tps, ps)
        case ps                                     => (Nil, ps)
      }
      val caseFields    = tpeClassSymbol.caseFields
      var usedRegisters = RegisterOffset.Zero
      var idx           = 0
      (
        tpeParams.map(_.map { symbol =>
          idx += 1
          var fTpe = tpe.memberType(symbol).dealias
          if (tpeTypeArgs ne Nil) fTpe = fTpe.substituteTypes(tpeTypeParams, tpeTypeArgs)
          val name   = symbol.name
          val getter = caseFields
            .find(_.name == name)
            .orElse {
              if (fieldMembers eq null) fieldMembers = tpeClassSymbol.fieldMembers
              fieldMembers.find(_.name == name)
            }
            .orElse {
              if (methodMembers eq null) methodMembers = tpeClassSymbol.methodMembers
              methodMembers.find(member => member.name == name && member.flags.is(Flags.FieldAccessor))
            }
            .getOrElse(Symbol.noSymbol)
          if (!getter.exists || getter.flags.is(Flags.PrivateLocal)) fail {
            s"Field or getter '$name' of '${tpe.show}' should be defined as 'val' or 'var' in the primary constructor."
          }
          var modifiers: List[Term] = Nil
          getter.annotations.foreach { annotation =>
            val aTpe = annotation.tpe
            if (aTpe <:< modifierTermTpe) modifiers = annotation :: modifiers
          }
          val defaultValue = if (symbol.flags.is(Flags.HasDefault)) {
            if (companionRefAndClass eq null) {
              val tpeTypeSymbol = tpe.typeSymbol
              companionRefAndClass = (Ref(tpeTypeSymbol.companionModule), tpeTypeSymbol.companionClass)
            }
            val dvMethod        = companionRefAndClass._2.declaredMethod("$lessinit$greater$default$" + idx).head
            val dvSelectNoTypes = Select(companionRefAndClass._1, dvMethod)
            new Some(dvMethod.paramSymss match {
              case Nil                                          => dvSelectNoTypes
              case List(params) if params.exists(_.isTypeParam) => dvSelectNoTypes.appliedToTypes(tpeTypeArgs)
              case _                                            =>
                fail {
                  s"Default values of non-first parameter lists are not supported for '$symbol' in class '${tpe.show}'."
                }
            })
          } else {
            if (modifiers.exists(_.tpe <:< modifierTransientTpe) && !isOption(fTpe) && !isCollection(fTpe)) {
              fail(s"Missing default value for transient field '$name' in '${tpe.show}'")
            }
            None
          }
          val fieldInfo = new FieldInfo(name, fTpe, defaultValue, getter, usedRegisters, modifiers)
          usedRegisters = RegisterOffset.add(usedRegisters, fieldOffset(fTpe))
          fieldInfo
        }),
        Expr(usedRegisters)
      )
    }

    def fields[S: Type](nameOverrides: Array[String])(using Quotes): Expr[Seq[SchemaTerm[Binding, S, ?]]] = {
      var idx = -1
      Varargs(fieldInfos.flatMap(_.map { fieldInfo =>
        val fTpe = fieldInfo.tpe
        fTpe.asType match {
          case '[ft] =>
            idx += 1
            val schema   = findImplicitOrDeriveSchema[ft](fTpe)
            val isNonRec = isNonRecursive(fTpe)
            val name     = Expr {
              if (idx < nameOverrides.length) nameOverrides(idx)
              else fieldInfo.name
            }
            val modifiers = fieldInfo.modifiers
            lazy val ms   = Varargs(modifiers.map(_.asExpr.asInstanceOf[Expr[Modifier.Term]]))
            fieldInfo.defaultValue match {
              case Some(dvSelect) =>
                val dv = dvSelect.asExpr.asInstanceOf[Expr[ft]]
                if (modifiers eq Nil) {
                  if (isNonRec) '{ $schema.reflect.defaultValue($dv).asTerm[S]($name) }
                  else '{ new Reflect.Deferred(() => $schema.reflect).defaultValue($dv).asTerm[S]($name) }
                } else {
                  if (isNonRec) '{ $schema.reflect.defaultValue($dv).asTerm[S]($name).copy(modifiers = $ms) }
                  else {
                    '{
                      new Reflect.Deferred(() => $schema.reflect)
                        .defaultValue($dv)
                        .asTerm[S]($name)
                        .copy(modifiers = $ms)
                    }
                  }
                }
              case _ =>
                if (modifiers eq Nil) {
                  if (isNonRec) '{ $schema.reflect.asTerm[S]($name) }
                  else '{ new Reflect.Deferred(() => $schema.reflect).asTerm[S]($name) }
                } else {
                  if (isNonRec) '{ $schema.reflect.asTerm[S]($name).copy(modifiers = $ms) }
                  else '{ new Reflect.Deferred(() => $schema.reflect).asTerm[S]($name).copy(modifiers = $ms) }
                }
            }
        }
      }))
    }

    def constructor(in: Expr[Registers], offset: Expr[RegisterOffset])(using Quotes): Expr[T] = {
      val constructor = Select(New(Inferred(tpe)), primaryConstructor).appliedToTypes(tpeTypeArgs)
      val argss       = fieldInfos.map(_.map(fieldConstructor(in, offset, _)))
      argss.tail.foldLeft(Apply(constructor, argss.head))(Apply(_, _)).asExpr.asInstanceOf[Expr[T]]
    }

    def deconstructor(out: Expr[Registers], offset: Expr[RegisterOffset], in: Expr[T])(using Quotes): Term =
      toBlock(fieldInfos.flatMap(_.map { fieldInfo =>
        val fTpe          = fieldInfo.tpe
        val getter        = Select(in.asTerm, fieldInfo.getter).asExpr
        val usedRegisters = Expr(fieldInfo.usedRegisters)
        {
          if (fTpe <:< intTpe) '{ $out.setInt($offset + $usedRegisters, ${ getter.asInstanceOf[Expr[Int]] }) }
          else if (fTpe <:< floatTpe) {
            '{ $out.setFloat($offset + $usedRegisters, ${ getter.asInstanceOf[Expr[Float]] }) }
          } else if (fTpe <:< longTpe) {
            '{ $out.setLong($offset + $usedRegisters, ${ getter.asInstanceOf[Expr[Long]] }) }
          } else if (fTpe <:< doubleTpe) {
            '{ $out.setDouble($offset + $usedRegisters, ${ getter.asInstanceOf[Expr[Double]] }) }
          } else if (fTpe <:< booleanTpe) {
            '{ $out.setBoolean($offset + $usedRegisters, ${ getter.asInstanceOf[Expr[Boolean]] }) }
          } else if (fTpe <:< byteTpe) {
            '{ $out.setByte($offset + $usedRegisters, ${ getter.asInstanceOf[Expr[Byte]] }) }
          } else if (fTpe <:< charTpe) {
            '{ $out.setChar($offset + $usedRegisters, ${ getter.asInstanceOf[Expr[Char]] }) }
          } else if (fTpe <:< shortTpe) {
            '{ $out.setShort($offset + $usedRegisters, ${ getter.asInstanceOf[Expr[Short]] }) }
          } else if (fTpe <:< unitTpe) '{ () }
          else if (fTpe <:< anyRefTpe) {
            '{ $out.setObject($offset + $usedRegisters, ${ getter.asInstanceOf[Expr[AnyRef]] }) }
          } else {
            val sTpe = dealiasOnDemand(fTpe)
            if (sTpe <:< intTpe) '{ $out.setInt($offset + $usedRegisters, $getter.asInstanceOf[Int]) }
            else if (sTpe <:< floatTpe) '{ $out.setFloat($offset + $usedRegisters, $getter.asInstanceOf[Float]) }
            else if (sTpe <:< longTpe) '{ $out.setLong($offset + $usedRegisters, $getter.asInstanceOf[Long]) }
            else if (sTpe <:< doubleTpe) {
              '{ $out.setDouble($offset + $usedRegisters, $getter.asInstanceOf[Double]) }
            } else if (sTpe <:< booleanTpe) {
              '{ $out.setBoolean($offset + $usedRegisters, $getter.asInstanceOf[Boolean]) }
            } else if (sTpe <:< byteTpe) '{ $out.setByte($offset + $usedRegisters, $getter.asInstanceOf[Byte]) }
            else if (sTpe <:< charTpe) '{ $out.setChar($offset + $usedRegisters, $getter.asInstanceOf[Char]) }
            else if (sTpe <:< shortTpe) '{ $out.setShort($offset + $usedRegisters, $getter.asInstanceOf[Short]) }
            else if (sTpe <:< unitTpe) '{ () }
            else '{ $out.setObject($offset + $usedRegisters, $getter.asInstanceOf[AnyRef]) }
          }
        }.asTerm
      }))
  }

  private class GenericTupleInfo[T: Type](tpe: TypeRepr) extends TypeInfo {
    val tpeTypeArgs: List[TypeRepr]                                        = genericTupleTypeArgs(tpe)
    val (fieldInfos: List[FieldInfo], usedRegisters: Expr[RegisterOffset]) = {
      var usedRegisters = RegisterOffset.Zero
      (
        tpeTypeArgs.map {
          var idx = 0
          fTpe =>
            idx += 1
            val fieldInfo = new FieldInfo(s"_$idx", fTpe, None, Symbol.noSymbol, usedRegisters, Nil)
            usedRegisters = RegisterOffset.add(usedRegisters, fieldOffset(fTpe))
            fieldInfo
        },
        Expr(usedRegisters)
      )
    }

    def fields[S: Type](nameOverrides: Array[String])(using Quotes): Expr[Seq[SchemaTerm[Binding, S, ?]]] =
      Varargs(fieldInfos.map {
        var idx = -1
        fieldInfo =>
          idx += 1
          val fTpe = fieldInfo.tpe
          fTpe.asType match {
            case '[ft] =>
              val schema = findImplicitOrDeriveSchema[ft](fTpe)
              val name   = Expr {
                if (idx < nameOverrides.length) nameOverrides(idx)
                else fieldInfo.name
              }
              if (isNonRecursive(fTpe)) '{ $schema.reflect.asTerm[S]($name) }
              else '{ new Reflect.Deferred(() => $schema.reflect).asTerm[S]($name) }
          }
      })

    def constructor(in: Expr[Registers], offset: Expr[RegisterOffset])(using Quotes): Expr[T] =
      (if (fieldInfos eq Nil) Expr(EmptyTuple)
       else {
         val symbol      = Symbol.newVal(Symbol.spliceOwner, "xs", arrayOfAnyTpe, Flags.EmptyFlags, Symbol.noSymbol)
         val ref         = Ref(symbol)
         val update      = Select(ref, defn.Array_update)
         val assignments = fieldInfos.map {
           var idx = -1
           fieldInfo =>
             idx += 1
             Apply(update, List(Literal(IntConstant(idx)), fieldConstructor(in, offset, fieldInfo)))
         }
         val valDef   = ValDef(symbol, new Some(Apply(newArrayOfAny, List(Literal(IntConstant(fieldInfos.size))))))
         val block    = Block(valDef :: assignments, ref)
         val typeCast = Select(block, asInstanceOfMethod).appliedToType(iArrayOfAnyRefTpe)
         Select(Apply(fromIArrayMethod, List(typeCast)), asInstanceOfMethod).appliedToType(tpe).asExpr
       }).asInstanceOf[Expr[T]]

    def deconstructor(out: Expr[Registers], offset: Expr[RegisterOffset], in: Expr[T])(using Quotes): Term =
      toBlock(fieldInfos.map {
        val productElement = Select(in.asTerm, productElementMethod)
        var idx            = -1
        fieldInfo =>
          idx += 1
          val fTpe          = fieldInfo.tpe
          val sTpe          = dealiasOnDemand(fTpe)
          val getter        = productElement.appliedTo(Literal(IntConstant(idx))).asExpr
          val usedRegisters = Expr(fieldInfo.usedRegisters)
          {
            if (sTpe <:< intTpe) '{ $out.setInt($offset + $usedRegisters, $getter.asInstanceOf[Int]) }
            else if (sTpe <:< floatTpe) '{ $out.setFloat($offset + $usedRegisters, $getter.asInstanceOf[Float]) }
            else if (sTpe <:< longTpe) '{ $out.setLong($offset + $usedRegisters, $getter.asInstanceOf[Long]) }
            else if (sTpe <:< doubleTpe) '{ $out.setDouble($offset + $usedRegisters, $getter.asInstanceOf[Double]) }
            else if (sTpe <:< booleanTpe) {
              '{ $out.setBoolean($offset + $usedRegisters, $getter.asInstanceOf[Boolean]) }
            } else if (sTpe <:< byteTpe) '{ $out.setByte($offset + $usedRegisters, $getter.asInstanceOf[Byte]) }
            else if (sTpe <:< charTpe) '{ $out.setChar($offset + $usedRegisters, $getter.asInstanceOf[Char]) }
            else if (sTpe <:< shortTpe) '{ $out.setShort($offset + $usedRegisters, $getter.asInstanceOf[Short]) }
            else if (sTpe <:< unitTpe) '{ () }
            else '{ $out.setObject($offset + $usedRegisters, $getter.asInstanceOf[AnyRef]) }
          }.asTerm
      })
  }

  private def deriveSchema[T: Type](tpe: TypeRepr)(using Quotes): Expr[Schema[T]] = {
    if (isEnumOrModuleValue(tpe)) {
      deriveSchemaForEnumOrModuleValue(tpe)
    } else if (isCollection(tpe)) {
      if (tpe <:< arrayOfWildcardTpe) {
        val eTpe = typeArgs(tpe).head
        eTpe.asType match {
          case '[et] =>
            val schema   = findImplicitOrDeriveSchema[et](eTpe)
            val classTag = summonClassTag[et]
            val tpeName  = toExpr(typeIdOf[Array[et]](tpe))
            '{
              implicit val ct: ClassTag[et] = $classTag
              new Schema(
                reflect = new Reflect.Sequence(
                  element = $schema.reflect,
                  typeId = $tpeName
                    .copy(typeParams = List(zio.blocks.schema.Schema.tparam($schema.reflect.typeId)))
                    .asInstanceOf[TypeId[Array[et]]],
                  seqBinding = new Binding.Seq(
                    constructor = new SeqConstructor.ArrayConstructor {
                      def newObjectBuilder[B](sizeHint: Int): Builder[B] =
                        new Builder(new Array[et](Math.max(sizeHint, 1)).asInstanceOf[Array[B]], 0)

                      def addObject[B](builder: ObjectBuilder[B], a: B): Unit = {
                        var buf = builder.buffer
                        val idx = builder.size
                        if (buf.length == idx) {
                          val xs     = buf.asInstanceOf[Array[et]]
                          val newLen = idx << 1
                          buf = ${ genArraysCopyOf[et](eTpe, 'xs, 'newLen) }.asInstanceOf[Array[B]]
                          builder.buffer = buf
                        }
                        buf(idx) = a
                        builder.size = idx + 1
                      }

                      def resultObject[B](builder: ObjectBuilder[B]): Array[B] = {
                        val buf  = builder.buffer
                        val size = builder.size
                        if (buf.length == size) buf
                        else {
                          val xs = buf.asInstanceOf[Array[et]]
                          ${ genArraysCopyOf[et](eTpe, 'xs, 'size) }.asInstanceOf[Array[B]]
                        }
                      }

                      def emptyObject[B]: Array[B] = Array.empty[et].asInstanceOf[Array[B]]
                    },
                    deconstructor = SeqDeconstructor.arrayDeconstructor
                  )
                )
              )
            }
        }
      } else if (isIArray(tpe)) {
        val eTpe = typeArgs(tpe).head
        eTpe.asType match {
          case '[et] =>
            val schema   = findImplicitOrDeriveSchema[et](eTpe)
            val classTag = summonClassTag[et]
            val tpeName  = toExpr(typeIdOf[IArray[et]](tpe))
            '{
              implicit val ct: ClassTag[et] = $classTag
              new Schema(
                reflect = new Reflect.Sequence(
                  element = $schema.reflect,
                  typeId = $tpeName
                    .copy(typeParams = List(zio.blocks.schema.Schema.tparam($schema.reflect.typeId)))
                    .asInstanceOf[TypeId[IArray[et]]],
                  seqBinding = new Binding.Seq(
                    constructor = new SeqConstructor.IArrayConstructor {
                      def newObjectBuilder[B](sizeHint: Int): Builder[B] =
                        new Builder(new Array[et](Math.max(sizeHint, 1)).asInstanceOf[Array[B]], 0)

                      def addObject[B](builder: ObjectBuilder[B], a: B): Unit = {
                        var buf = builder.buffer
                        val idx = builder.size
                        if (buf.length == idx) {
                          val xs     = buf.asInstanceOf[Array[et]]
                          val newLen = idx << 1
                          buf = ${ genArraysCopyOf[et](eTpe, 'xs, 'newLen) }.asInstanceOf[Array[B]]
                          builder.buffer = buf
                        }
                        buf(idx) = a
                        builder.size = idx + 1
                      }

                      def resultObject[B](builder: ObjectBuilder[B]): IArray[B] = IArray.unsafeFromArray {
                        val buf  = builder.buffer
                        val size = builder.size
                        if (buf.length == size) buf
                        else {
                          val xs = buf.asInstanceOf[Array[et]]
                          ${ genArraysCopyOf[et](eTpe, 'xs, 'size) }.asInstanceOf[Array[B]]
                        }
                      }

                      def emptyObject[B]: IArray[B] = IArray.empty[et].asInstanceOf[IArray[B]]
                    },
                    deconstructor = SeqDeconstructor.iArrayDeconstructor
                  )
                )
              )
            }
        }
      } else if (tpe <:< TypeRepr.of[ArraySeq[?]]) {
        val eTpe = typeArgs(tpe).head
        eTpe.asType match {
          case '[et] =>
            val schema   = findImplicitOrDeriveSchema[et](eTpe)
            val classTag = summonClassTag[et]
            val tpeName  = toExpr(typeIdOf[ArraySeq[et]](tpe))
            '{
              implicit val ct: ClassTag[et] = $classTag
              new Schema(
                reflect = new Reflect.Sequence(
                  element = $schema.reflect,
                  typeId = $tpeName
                    .copy(typeParams = List(zio.blocks.schema.Schema.tparam($schema.reflect.typeId)))
                    .asInstanceOf[TypeId[ArraySeq[et]]],
                  seqBinding = new Binding.Seq(
                    constructor = new SeqConstructor.ArraySeqConstructor {
                      def newObjectBuilder[B](sizeHint: Int): Builder[B] =
                        new Builder(new Array[et](Math.max(sizeHint, 1)).asInstanceOf[Array[B]], 0)

                      def addObject[B](builder: ObjectBuilder[B], a: B): Unit = {
                        var buf = builder.buffer
                        val idx = builder.size
                        if (buf.length == idx) {
                          val xs     = buf.asInstanceOf[Array[et]]
                          val newLen = idx << 1
                          buf = ${ genArraysCopyOf[et](eTpe, 'xs, 'newLen) }.asInstanceOf[Array[B]]
                          builder.buffer = buf
                        }
                        buf(idx) = a
                        builder.size = idx + 1
                      }

                      def resultObject[B](builder: ObjectBuilder[B]): ArraySeq[B] = ArraySeq.unsafeWrapArray {
                        val buf  = builder.buffer
                        val size = builder.size
                        if (buf.length == size) buf
                        else {
                          val xs = buf.asInstanceOf[Array[et]]
                          ${ genArraysCopyOf[et](eTpe, 'xs, 'size) }.asInstanceOf[Array[B]]
                        }
                      }

                      def emptyObject[B]: ArraySeq[B] = ArraySeq.empty[et].asInstanceOf[ArraySeq[B]]
                    },
                    deconstructor = SeqDeconstructor.arraySeqDeconstructor
                  )
                )
              )
            }
        }
      } else if (tpe <:< TypeRepr.of[List[?]]) {
        val eTpe = typeArgs(tpe).head
        eTpe.asType match {
          case '[et] =>
            val schema = findImplicitOrDeriveSchema[et](eTpe)
            '{ Schema.list($schema) }
        }
      } else if (tpe <:< TypeRepr.of[Map[?, ?]]) {
        val tpeTypeArgs = typeArgs(tpe)
        val kTpe        = tpeTypeArgs.head
        val vTpe        = tpeTypeArgs.last
        kTpe.asType match {
          case '[kt] =>
            vTpe.asType match {
              case '[vt] =>
                val kSchema = findImplicitOrDeriveSchema[kt](kTpe)
                val vSchema = findImplicitOrDeriveSchema[vt](vTpe)
                '{ Schema.map($kSchema, $vSchema) }
            }
        }
      } else if (tpe <:< TypeRepr.of[Set[?]]) {
        val eTpe = typeArgs(tpe).head
        eTpe.asType match {
          case '[et] =>
            val schema = findImplicitOrDeriveSchema[et](eTpe)
            '{ Schema.set($schema) }
        }
      } else if (tpe <:< TypeRepr.of[Vector[?]]) {
        val eTpe = typeArgs(tpe).head
        eTpe.asType match {
          case '[et] =>
            val schema = findImplicitOrDeriveSchema[et](eTpe)
            '{ Schema.vector($schema) }
        }
      } else if (tpe <:< TypeRepr.of[IndexedSeq[?]]) {
        val eTpe = typeArgs(tpe).head
        eTpe.asType match {
          case '[et] =>
            val schema = findImplicitOrDeriveSchema[et](eTpe)
            '{ Schema.indexedSeq($schema) }
        }
      } else if (tpe <:< TypeRepr.of[Seq[?]]) {
        val eTpe = typeArgs(tpe).head
        eTpe.asType match {
          case '[et] =>
            val schema = findImplicitOrDeriveSchema[et](eTpe)
            '{ Schema.seq($schema) }
        }
      } else cannotDeriveSchema(tpe)
    } else if (isGenericTuple(tpe)) {
      val tTpe = normalizeGenericTuple(tpe)
      tTpe.asType match {
        case '[tt] =>
          val typeInfo =
            if (isGenericTuple(tTpe)) new GenericTupleInfo[tt](tTpe)
            else new ClassInfo[tt](tTpe)
          val fields  = typeInfo.fields[tt](Array.empty[String])
          val tpeName = toExpr(typeIdOf[tt](tTpe))
          '{
            new Schema(
              reflect = new Reflect.Record[Binding, tt](
                fields = Vector($fields*),
                typeId = ${ tpeName }.asInstanceOf[zio.blocks.typeid.TypeId[tt]],
                recordBinding = new Binding.Record(
                  constructor = new Constructor[tt] {
                    def usedRegisters: RegisterOffset = ${ typeInfo.usedRegisters }

                    def construct(in: Registers, offset: RegisterOffset): tt = ${
                      typeInfo.constructor('in, 'offset)
                    }
                  },
                  deconstructor = new Deconstructor[tt] {
                    def usedRegisters: RegisterOffset = ${ typeInfo.usedRegisters }

                    def deconstruct(out: Registers, offset: RegisterOffset, in: tt): Unit = ${
                      typeInfo.deconstructor('out, 'offset, 'in).asExpr
                    }
                  }
                )
              )
            )
          }
      }
    } else if (isOption(tpe)) {
      if (tpe <:< TypeRepr.of[None.type]) deriveSchemaForEnumOrModuleValue(tpe)
      else if (tpe <:< TypeRepr.of[Some[?]]) deriveSchemaForNonAbstractScalaClass(tpe)
      else {
        tpe.asType match {
          case '[ot] =>
            val vTpe          = tpe.dealias.typeArgs.head
            val dealiasedVTpe = if (isOpaque(vTpe)) opaqueDealias(vTpe) else vTpe.dealias

            def cast(expr: Expr[Schema[?]]): Expr[Schema[ot]] =
              expr.asExprOf[Schema[ot]]

            val isPurePrimitive = vTpe =:= dealiasedVTpe

            if (isPurePrimitive && dealiasedVTpe =:= stringTpe) cast('{ Schema.option(Schema.string) })
            else if (isPurePrimitive && dealiasedVTpe =:= intTpe) cast('{ Schema.optionInt })
            else if (isPurePrimitive && dealiasedVTpe =:= longTpe) cast('{ Schema.optionLong })
            else if (isPurePrimitive && dealiasedVTpe =:= doubleTpe) cast('{ Schema.optionDouble })
            else if (isPurePrimitive && dealiasedVTpe =:= floatTpe) cast('{ Schema.optionFloat })
            else if (isPurePrimitive && dealiasedVTpe =:= booleanTpe) cast('{ Schema.optionBoolean })
            else if (isPurePrimitive && dealiasedVTpe =:= byteTpe) cast('{ Schema.optionByte })
            else if (isPurePrimitive && dealiasedVTpe =:= charTpe) cast('{ Schema.optionChar })
            else if (isPurePrimitive && dealiasedVTpe =:= shortTpe) cast('{ Schema.optionShort })
            else if (isPurePrimitive && dealiasedVTpe =:= unitTpe) cast('{ Schema.optionUnit })
            else {
              vTpe.asType match {
                case '[vt] =>
                  val schema = findImplicitOrDeriveSchema[vt](vTpe)
                  cast('{ Schema.option($schema) })
              }
            }
        }
      }
    } else if (isSealedTraitOrAbstractClass(tpe) || isUnion(tpe)) {
      deriveSchemaForSealedTraitOrAbstractClassOrUnion(tpe)
    } else if (isNamedTuple(tpe)) {
      val tpeTypeArgs = typeArgs(tpe)
      val nTpe        = tpeTypeArgs.head
      var tTpe        = tpeTypeArgs.last
      val nTypeArgs   =
        if (isGenericTuple(nTpe)) genericTupleTypeArgs(nTpe)
        else typeArgs(nTpe)
      val nameOverrides = new Array[String](nTypeArgs.size)
      var idx           = -1
      nTypeArgs.foreach { case ConstantType(StringConstant(str)) =>
        idx += 1
        nameOverrides(idx) = str
      }
      if (isGenericTuple(tTpe)) tTpe = normalizeGenericTuple(tTpe)
      tTpe.asType match {
        case '[tt] =>
          val typeInfo =
            if (isGenericTuple(tTpe)) new GenericTupleInfo[tt](tTpe)
            else new ClassInfo[tt](tTpe)
          val fields  = typeInfo.fields[T](nameOverrides)
          val tpeName = toExpr(typeIdOf[T](tpe))
          '{
            new Schema(
              reflect = new Reflect.Record[Binding, T](
                fields = Vector($fields*),
                typeId = ${ tpeName }.asInstanceOf[zio.blocks.typeid.TypeId[T]],
                recordBinding = new Binding.Record(
                  constructor = new Constructor {
                    def usedRegisters: RegisterOffset = ${ typeInfo.usedRegisters }

                    def construct(in: Registers, offset: RegisterOffset): T = ${
                      typeInfo.constructor('in, 'offset).asInstanceOf[Expr[T]]
                    }
                  },
                  deconstructor = new Deconstructor {
                    def usedRegisters: RegisterOffset = ${ typeInfo.usedRegisters }

                    def deconstruct(out: Registers, offset: RegisterOffset, in: T): Unit = ${
                      val value  = Apply(toTupleMethod.appliedToTypes(tpe.typeArgs), List('in.asTerm))
                      val symbol = Symbol.newVal(Symbol.spliceOwner, "t", tTpe, Flags.EmptyFlags, Symbol.noSymbol)
                      val valDef = ValDef(symbol, new Some(value))
                      val expr   = Ref(symbol).asExpr.asInstanceOf[Expr[tt]]
                      Block(List(valDef), typeInfo.deconstructor('out, 'offset, expr)).asExpr
                    }
                  }
                )
              )
            )
          }
      }
    } else if (isNonAbstractScalaClass(tpe)) {
      deriveSchemaForNonAbstractScalaClass(tpe)
    } else if (isOpaque(tpe)) {
      val sTpe = opaqueDealias(tpe)
      sTpe.asType match {
        case '[s] =>
          val schema = findImplicitOrDeriveSchema[s](sTpe)
          val tpeId  = toExpr(typeIdOf[T](tpe))
          '{
            new Schema($schema.reflect.typeId(${ tpeId }.asInstanceOf[zio.blocks.typeid.TypeId[s]]))
              .asInstanceOf[Schema[T]]
          }
      }
    } else if (isZioPreludeNewtype(tpe)) {
      val sTpe = zioPreludeNewtypeDealias(tpe)
      sTpe.asType match {
        case '[s] =>
          val schema = findImplicitOrDeriveSchema[s](sTpe)
          val tpeId  = toExpr(typeIdOf[T](tpe))
          '{
            new Schema($schema.reflect.typeId(${ tpeId }.asInstanceOf[zio.blocks.typeid.TypeId[s]]))
              .asInstanceOf[Schema[T]]
          }
      }
    } else if (isTypeRef(tpe) || isAppliedTypeAlias(tpe)) {
      val sTpe = if (isTypeRef(tpe)) typeRefDealias(tpe) else tpe.dealias
      sTpe.asType match {
        case '[s] =>
          val schema = findImplicitOrDeriveSchema[s](sTpe)
          val tpeId  = toExpr(typeIdOf[T](tpe))
          '{
            new Schema($schema.reflect.typeId(${ tpeId }.asInstanceOf[zio.blocks.typeid.TypeId[s]]))
              .asInstanceOf[Schema[T]]
          }
      }
    } else cannotDeriveSchema(tpe)
  }.asInstanceOf[Expr[Schema[T]]]

  private def deriveSchemaForEnumOrModuleValue[T: Type](tpe: TypeRepr)(using Quotes): Expr[Schema[T]] = {
    val tpeName = toExpr(typeIdOf(tpe))
    '{
      new Schema(
        reflect = new Reflect.Record[Binding, T](
          fields = Vector.empty,
          typeId = ${ tpeName }.asInstanceOf[zio.blocks.typeid.TypeId[T]],
          recordBinding = new Binding.Record(
            constructor = new ConstantConstructor(${
              Ref(
                if (isEnumValue(tpe)) tpe.termSymbol
                else tpe.typeSymbol.companionModule
              ).asExpr.asInstanceOf[Expr[T]]
            }),
            deconstructor = new ConstantDeconstructor
          ),
          doc = ${ doc(tpe) },
          modifiers = ${ modifiers(tpe) }
        )
      )
    }
  }

  private def deriveSchemaForNonAbstractScalaClass[T: Type](tpe: TypeRepr)(using Quotes): Expr[Schema[T]] = {
    val classInfo = new ClassInfo(tpe)
    val fields    = classInfo.fields(Array.empty[String])
    val tpeName   = toExpr(typeIdOf(tpe))
    '{
      new Schema(
        reflect = new Reflect.Record[Binding, T](
          fields = Vector($fields*),
          typeId = ${ tpeName }.asInstanceOf[zio.blocks.typeid.TypeId[T]],
          recordBinding = new Binding.Record(
            constructor = new Constructor {
              def usedRegisters: RegisterOffset = ${ classInfo.usedRegisters }

              def construct(in: Registers, offset: RegisterOffset): T = ${
                classInfo.constructor('in, 'offset)
              }
            },
            deconstructor = new Deconstructor {
              def usedRegisters: RegisterOffset = ${ classInfo.usedRegisters }

              def deconstruct(out: Registers, offset: RegisterOffset, in: T): Unit = ${
                classInfo.deconstructor('out, 'offset, 'in).asExpr
              }
            }
          ),
          doc = ${ doc(tpe) },
          modifiers = ${ modifiers(tpe) }
        )
      )
    }
  }

  private def deriveSchemaForSealedTraitOrAbstractClassOrUnion[T: Type](
    tpe: TypeRepr
  )(using Quotes): Expr[Schema[T]] = {
    val subtypes =
      if (isUnion(tpe)) allUnionTypes(tpe)
      else directSubTypes(tpe)
    if (subtypes.isEmpty) fail(s"Cannot find sub-types for ADT base '${tpe.show}'.")
    val fullTermNames         = subtypes.map(sTpe => toFullTermName(typeIdOf(sTpe)))
    val maxCommonPrefixLength = {
      val minFullTermName = fullTermNames.min
      val maxFullTermName = fullTermNames.max
      val minLength       = Math.min(minFullTermName.length, maxFullTermName.length) - 1
      var idx             = 0
      while (idx < minLength && minFullTermName(idx).equals(maxFullTermName(idx))) idx += 1
      idx
    }
    val cases = Varargs(subtypes.zip(fullTermNames).map { case (sTpe, fullName) =>
      sTpe.asType match {
        case '[st] =>
          var modifiers: List[Term] = Nil
          sTpe.typeSymbol.annotations.foreach { annotation =>
            val aTpe = annotation.tpe
            if (aTpe <:< modifierTermTpe) modifiers = annotation :: modifiers
          }
          val caseName = Expr(toShortTermName(fullName, maxCommonPrefixLength))
          val schema   = findImplicitOrDeriveSchema[st](sTpe)
          (if (modifiers eq Nil) {
             '{ $schema.reflect.asTerm[T]($caseName) }
           } else {
             val ms = Varargs(modifiers.map(_.asExpr.asInstanceOf[Expr[Modifier.Term]]))
             '{ $schema.reflect.asTerm[T]($caseName).copy(modifiers = $ms) }
           }).asInstanceOf[Expr[SchemaTerm[Binding, T, ? <: T]]]
      }
    })
    val matcherCases = Varargs(subtypes.map { sTpe =>
      sTpe.asType match {
        case '[st] =>
          '{
            new Matcher[st] {
              def downcastOrNull(a: Any): st = (a: @scala.unchecked) match {
                case x: st => x
                case _     => null.asInstanceOf[st]
              }
            }
          }.asInstanceOf[Expr[Matcher[? <: T]]]
      }
    })
    val tpeName = toExpr(typeIdOf(tpe))
    '{
      new Schema(
        reflect = new Reflect.Variant[Binding, T](
          cases = Vector($cases*),
          typeId = ${ tpeName }.asInstanceOf[zio.blocks.typeid.TypeId[T]],
          variantBinding = new Binding.Variant(
            discriminator = new Discriminator {
              def discriminate(a: T): Int = ${
                val v = 'a
                Match(
                  '{ $v: @scala.unchecked }.asTerm,
                  subtypes.map {
                    var idx = -1
                    sTpe =>
                      idx += 1
                      CaseDef(Typed(Wildcard(), Inferred(sTpe)), None, Literal(IntConstant(idx)))
                  }
                ).asExpr.asInstanceOf[Expr[Int]]
              }
            },
            matchers = Matchers($matcherCases*)
          ),
          doc = ${ doc(tpe) },
          modifiers = ${ modifiers(tpe) }
        )
      )
    }
  }

  private def genArraysCopyOf[T: Type](tpe: TypeRepr, x: Expr[Array[T]], newLen: Expr[Int])(using
    Quotes
  ): Expr[Array[T]] = {
    if (tpe =:= booleanTpe) '{ java.util.Arrays.copyOf(${ x.asInstanceOf[Expr[Array[Boolean]]] }, $newLen) }
    else if (tpe =:= byteTpe) '{ java.util.Arrays.copyOf(${ x.asInstanceOf[Expr[Array[Byte]]] }, $newLen) }
    else if (tpe =:= shortTpe) '{ java.util.Arrays.copyOf(${ x.asInstanceOf[Expr[Array[Short]]] }, $newLen) }
    else if (tpe =:= intTpe) '{ java.util.Arrays.copyOf(${ x.asInstanceOf[Expr[Array[Int]]] }, $newLen) }
    else if (tpe =:= longTpe) '{ java.util.Arrays.copyOf(${ x.asInstanceOf[Expr[Array[Long]]] }, $newLen) }
    else if (tpe =:= floatTpe) '{ java.util.Arrays.copyOf(${ x.asInstanceOf[Expr[Array[Float]]] }, $newLen) }
    else if (tpe =:= doubleTpe) '{ java.util.Arrays.copyOf(${ x.asInstanceOf[Expr[Array[Double]]] }, $newLen) }
    else if (tpe =:= charTpe) '{ java.util.Arrays.copyOf(${ x.asInstanceOf[Expr[Array[Char]]] }, $newLen) }
    else if (tpe <:< anyRefTpe) '{ java.util.Arrays.copyOf(${ x.asInstanceOf[Expr[Array[AnyRef & T]]] }, $newLen) }
    else
      '{
        val x1 = ${ genNewArray[T](newLen) }
        java.lang.System.arraycopy($x, 0, x1, 0, $newLen)
        x1
      }
  }.asInstanceOf[Expr[Array[T]]]

  private def genNewArray[T: Type](size: Expr[Int]): Expr[Array[T]] =
    Apply(TypeApply(newArray, List(TypeTree.of[T])), List(size.asTerm)).asExpr.asInstanceOf[Expr[Array[T]]]

  private def toFullTermName(typeId: TypeId[?]): Array[String] = {
    val rawOwnerSegments = typeId.owner.asString.split('.').filter(_.nonEmpty)
    val ownerSegments    =
      if (rawOwnerSegments.length > 0 && rawOwnerSegments(0) == "scala") rawOwnerSegments.drop(1)
      else if (rawOwnerSegments.length > 1 && rawOwnerSegments(0) == "java" && rawOwnerSegments(1) == "lang")
        rawOwnerSegments.drop(2)
      else rawOwnerSegments
    val fullTermName = new Array[String](ownerSegments.length + 1)

    var idx = 0
    var i   = 0
    while (i < ownerSegments.length) {
      fullTermName(idx) = ownerSegments(i)
      idx += 1
      i += 1
    }
    fullTermName(idx) = typeId.name
    fullTermName
  }

  private def toShortTermName(fullName: Array[String], from: Int): String = {
    val str = new java.lang.StringBuilder
    var idx = from
    while (idx < fullName.length) {
      if (idx != from) str.append('.')
      str.append(fullName(idx))
      idx += 1
    }
    str.toString
  }

  private def cannotDeriveSchema(tpe: TypeRepr): Nothing = fail(s"Cannot derive schema for '${tpe.show}'.")

  def derived[A: Type]: Expr[Schema[A]] = {
    val aTpe        = TypeRepr.of[A]
    val schema      = aTpe.asType match { case '[a] => deriveSchema[a](aTpe) }
    val schemaBlock = Block(schemaDefs.toList, schema.asTerm).asExpr.asInstanceOf[Expr[Schema[A]]]
    // report.info(s"Generated schema:\n${schemaBlock.show}", Position.ofMacroExpansion)
    schemaBlock
  }
}
