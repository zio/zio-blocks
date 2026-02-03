package zio.blocks.schema.binding

import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.quoted.*
import zio.blocks.chunk.Chunk
import zio.blocks.schema.{CommonMacroOps, DynamicValue, Platform, SchemaError}

trait BindingCompanionVersionSpecific {

  /**
   * Derives a [[Binding]] for type `A` at compile time.
   *
   * Supports:
   *   - Primitive types (Int, String, Boolean, etc.)
   *   - Case classes (derives [[Binding.Record]])
   *   - Sealed traits/enums (derives [[Binding.Variant]])
   *   - Option, Either, and their subtypes
   *   - [[DynamicValue]]
   *   - Structural types (JVM only)
   *
   * For sequence types (List, Vector, etc.) and map types, use the overloads
   * that take type constructors: `Binding.of[List]`, `Binding.of[Map]`.
   *
   * @tparam A
   *   the type to derive a binding for
   * @return
   *   the derived binding
   */
  transparent inline def of[A]: Binding[_, A] = ${ BindingCompanionVersionSpecificImpl.of[A] }

  /**
   * Creates a [[Binding.Seq]] for a sequence type constructor. Uses given
   * [[SeqConstructor]] and [[SeqDeconstructor]] instances. The resulting
   * binding has element type `Nothing` which upcasts via covariance.
   *
   * @tparam F
   *   the sequence type constructor (e.g., List, Vector)
   */
  inline def of[F[_]](using sc: SeqConstructor[F], sd: SeqDeconstructor[F]): Binding.Seq[F, Nothing] =
    new Binding.Seq(sc, sd)

  /**
   * Creates a [[Binding.Map]] for a map type constructor. Uses given
   * [[MapConstructor]] and [[MapDeconstructor]] instances.
   *
   * @tparam M
   *   the map type constructor (e.g., Map)
   */
  inline def of[M[_, _]](using mc: MapConstructor[M], md: MapDeconstructor[M]): Binding.Map[M, Nothing, Nothing] =
    new Binding.Map(mc, md)
}

private object BindingCompanionVersionSpecificImpl {
  import scala.quoted.*

  def of[A: Type](using Quotes): Expr[Binding[_, A]] = new BindingCompanionVersionSpecificImpl().of[A]
}

private class BindingCompanionVersionSpecificImpl(using Quotes) {
  import quotes.reflect.*

  private val intTpe             = defn.IntClass.typeRef
  private val floatTpe           = defn.FloatClass.typeRef
  private val longTpe            = defn.LongClass.typeRef
  private val doubleTpe          = defn.DoubleClass.typeRef
  private val booleanTpe         = defn.BooleanClass.typeRef
  private val byteTpe            = defn.ByteClass.typeRef
  private val charTpe            = defn.CharClass.typeRef
  private val shortTpe           = defn.ShortClass.typeRef
  private val unitTpe            = defn.UnitClass.typeRef
  private val stringTpe          = defn.StringClass.typeRef
  private val anyTpe             = defn.AnyClass.typeRef
  private val wildcard           = TypeBounds(defn.NothingClass.typeRef, anyTpe)
  private val arrayClass         = defn.ArrayClass
  private val arrayOfWildcardTpe = arrayClass.typeRef.appliedTo(wildcard)

  private def fail(msg: String): Nothing = throw new Exception(msg)

  private def isEnumValue(tpe: TypeRepr): Boolean = tpe.termSymbol.flags.is(Flags.Enum)

  private def isEnumOrModuleValue(tpe: TypeRepr): Boolean = isEnumValue(tpe) || tpe.typeSymbol.flags.is(Flags.Module)

  private def isSealedTraitOrAbstractClass(tpe: TypeRepr): Boolean = tpe.classSymbol.fold(false) { symbol =>
    val flags = symbol.flags
    flags.is(Flags.Sealed) && (flags.is(Flags.Abstract) || flags.is(Flags.Trait))
  }

  private def isNonAbstractScalaClass(tpe: TypeRepr): Boolean = tpe.classSymbol.fold(false) { symbol =>
    val flags = symbol.flags
    !(flags.is(Flags.Abstract) || flags.is(Flags.JavaDefined) || flags.is(Flags.Trait))
  }

  private def isIArray(tpe: TypeRepr): Boolean = tpe.typeSymbol.fullName == "scala.IArray$package$.IArray"

  private def isUnion(tpe: TypeRepr): Boolean = tpe match {
    case OrType(_, _) => true
    case _            => false
  }

  private def allUnionTypes(tpe: TypeRepr): List[TypeRepr] = CommonMacroOps.allUnionTypes(tpe)

  private def typeArgs(tpe: TypeRepr): List[TypeRepr] = tpe match {
    case AppliedType(_, args) => args
    case _                    => Nil
  }

  private def directSubTypes(tpe: TypeRepr): List[TypeRepr] = CommonMacroOps.directSubTypes(tpe)

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

  private def isZioPreludeNewtype(tpe: TypeRepr): Boolean = tpe match {
    case TypeRef(compTpe, "Type") => compTpe.baseClasses.exists(_.fullName == "zio.prelude.Newtype")
    case _                        => false
  }

  private def zioPreludeNewtypeDealias(tpe: TypeRepr): TypeRepr = tpe match {
    case TypeRef(compTpe, _) =>
      compTpe.baseClasses.find(_.fullName == "zio.prelude.Newtype") match {
        case Some(cls) => compTpe.baseType(cls).typeArgs.head.dealias
        case _         => fail(s"Cannot dealias zio-prelude newtype: ${tpe.show}.")
      }
    case _ => fail(s"Cannot dealias zio-prelude newtype: ${tpe.show}.")
  }

  private def zioPreludeNewtypeCompanion(tpe: TypeRepr): Term = tpe match {
    case TypeRef(compTpe, _) => Ref(compTpe.typeSymbol.companionModule)
    case _                   => fail(s"Cannot get companion for zio-prelude newtype: ${tpe.show}.")
  }

  private def isTypeRef(tpe: TypeRepr): Boolean = tpe match {
    case trTpe: TypeRef =>
      val typeSymbol = trTpe.typeSymbol
      typeSymbol.isTypeDef && typeSymbol.isAliasType
    case _ => false
  }

  private def typeRefDealias(tpe: TypeRepr): TypeRepr = tpe match {
    case trTpe: TypeRef =>
      val sTpe = trTpe.translucentSuperType.dealias
      if (sTpe == trTpe) fail(s"Cannot dealias type reference: ${tpe.show}.")
      sTpe
    case _ => fail(s"Cannot dealias type reference: ${tpe.show}.")
  }

  private def isGenericTuple(tpe: TypeRepr): Boolean = CommonMacroOps.isGenericTuple(tpe)

  private val genericTupleTypeArgsCache = new mutable.HashMap[TypeRepr, List[TypeRepr]]

  private def genericTupleTypeArgs(tpe: TypeRepr): List[TypeRepr] =
    genericTupleTypeArgsCache.getOrElseUpdate(tpe, CommonMacroOps.genericTupleTypeArgs(tpe))

  private def normalizeGenericTuple(tpe: TypeRepr): TypeRepr =
    CommonMacroOps.normalizeGenericTuple(genericTupleTypeArgs(tpe))

  private def isNamedTuple(tpe: TypeRepr): Boolean = CommonMacroOps.isNamedTuple(tpe)

  private def isIterator(tpe: TypeRepr): Boolean = tpe <:< TypeRepr.of[Iterator[?]]

  private case class SmartConstructorInfo(
    companionRef: Term,
    applyMethod: Symbol,
    underlyingType: TypeRepr,
    errorType: TypeRepr,
    unwrapFieldName: String
  )

  private def findSmartConstructor(tpe: TypeRepr): Option[SmartConstructorInfo] = {
    val eitherSymbol = Symbol.requiredClass("scala.util.Either")

    for {
      classSymbol <- tpe.classSymbol
      if !classSymbol.flags.is(Flags.Abstract) && !classSymbol.flags.is(Flags.Trait)
      constructor = classSymbol.primaryConstructor
      paramLists  = constructor.paramSymss.filterNot(_.exists(_.isTypeParam))
      allParams   = paramLists.flatten
      if allParams.size == 1
      fieldSymbol     = allParams.head
      fieldName       = fieldSymbol.name
      underlyingType  = tpe.memberType(fieldSymbol).dealias
      companionModule = classSymbol.companionModule
      if companionModule != Symbol.noSymbol
      applyMethods = companionModule.methodMember("apply")
      method      <- applyMethods.find { method =>
                  val methodType = companionModule.typeRef.memberType(method)
                  methodType match {
                    case MethodType(paramNames, paramTypes, returnType) if paramNames.size == 1 =>
                      val paramType = paramTypes.head.dealias
                      if (!(paramType =:= underlyingType)) false
                      else {
                        returnType.dealias match {
                          case AppliedType(con, List(_, rightType)) if con.typeSymbol == eitherSymbol =>
                            rightType =:= tpe || rightType.dealias =:= tpe
                          case _ => false
                        }
                      }
                    case _ => false
                  }
                }
      returnType <- companionModule.typeRef.memberType(method) match {
                      case MethodType(_, _, rt) => Some(rt.dealias)
                      case _                    => None
                    }
      errorType <- returnType match {
                     case AppliedType(_, List(errTpe, _)) => Some(errTpe.dealias)
                     case _                               => None
                   }
    } yield SmartConstructorInfo(
      Ref(companionModule),
      method,
      underlyingType,
      errorType,
      fieldName
    )
  }

  def of[A: Type]: Expr[Binding[_, A]] = {
    var tpe = TypeRepr.of[A].dealias
    // Handle AndType (e.g., Record8[Option] & Object) by extracting the left (application) type
    // BUT preserve true intersection types of structural types (e.g., HasName & HasAge)
    tpe = tpe match {
      case AndType(left, right) if right =:= anyTpe || right =:= TypeRepr.of[Object] =>
        // This is an artifact like "Record8[Option] & Object", extract the left type
        left
      case AndType(_, _) =>
        // Keep intersection types as-is (could be structural types like HasName & HasAge)
        tpe
      case _ => tpe
    }
    deriveBinding[A](tpe).asExprOf[Binding[_, A]]
  }

  private def deriveBinding[A: Type](tpe: TypeRepr)(using Quotes): Expr[Any] =
    if (tpe =:= intTpe) '{ Binding.Primitive.int }
    else if (tpe =:= floatTpe) '{ Binding.Primitive.float }
    else if (tpe =:= longTpe) '{ Binding.Primitive.long }
    else if (tpe =:= doubleTpe) '{ Binding.Primitive.double }
    else if (tpe =:= booleanTpe) '{ Binding.Primitive.boolean }
    else if (tpe =:= byteTpe) '{ Binding.Primitive.byte }
    else if (tpe =:= charTpe) '{ Binding.Primitive.char }
    else if (tpe =:= shortTpe) '{ Binding.Primitive.short }
    else if (tpe =:= unitTpe) '{ Binding.Primitive.unit }
    else if (tpe =:= stringTpe) '{ Binding.Primitive.string }
    else if (tpe <:< TypeRepr.of[BigInt]) '{ Binding.Primitive.bigInt }
    else if (tpe <:< TypeRepr.of[BigDecimal]) '{ Binding.Primitive.bigDecimal }
    else if (tpe <:< TypeRepr.of[java.time.DayOfWeek]) '{ Binding.Primitive.dayOfWeek }
    else if (tpe <:< TypeRepr.of[java.time.Duration]) '{ Binding.Primitive.duration }
    else if (tpe <:< TypeRepr.of[java.time.Instant]) '{ Binding.Primitive.instant }
    else if (tpe <:< TypeRepr.of[java.time.LocalDate]) '{ Binding.Primitive.localDate }
    else if (tpe <:< TypeRepr.of[java.time.LocalDateTime]) '{ Binding.Primitive.localDateTime }
    else if (tpe <:< TypeRepr.of[java.time.LocalTime]) '{ Binding.Primitive.localTime }
    else if (tpe <:< TypeRepr.of[java.time.Month]) '{ Binding.Primitive.month }
    else if (tpe <:< TypeRepr.of[java.time.MonthDay]) '{ Binding.Primitive.monthDay }
    else if (tpe <:< TypeRepr.of[java.time.OffsetDateTime]) '{ Binding.Primitive.offsetDateTime }
    else if (tpe <:< TypeRepr.of[java.time.OffsetTime]) '{ Binding.Primitive.offsetTime }
    else if (tpe <:< TypeRepr.of[java.time.Period]) '{ Binding.Primitive.period }
    else if (tpe <:< TypeRepr.of[java.time.Year]) '{ Binding.Primitive.year }
    else if (tpe <:< TypeRepr.of[java.time.YearMonth]) '{ Binding.Primitive.yearMonth }
    else if (tpe <:< TypeRepr.of[java.time.ZoneOffset]) '{ Binding.Primitive.zoneOffset }
    else if (tpe <:< TypeRepr.of[java.time.ZoneId]) '{ Binding.Primitive.zoneId }
    else if (tpe <:< TypeRepr.of[java.time.ZonedDateTime]) '{ Binding.Primitive.zonedDateTime }
    else if (tpe <:< TypeRepr.of[java.util.Currency]) '{ Binding.Primitive.currency }
    else if (tpe <:< TypeRepr.of[java.util.UUID]) '{ Binding.Primitive.uuid }
    else if (tpe <:< TypeRepr.of[DynamicValue]) '{ Binding.Dynamic() }
    else if (tpe =:= TypeRepr.of[None.type]) '{ Binding.Record.none }
    else if (tpe <:< TypeRepr.of[Some[?]]) deriveSomeBinding(tpe)
    else if (tpe <:< TypeRepr.of[Option[?]]) deriveOptionBinding(tpe)
    else if (tpe <:< TypeRepr.of[Left[?, ?]]) deriveLeftBinding(tpe)
    else if (tpe <:< TypeRepr.of[Right[?, ?]]) deriveRightBinding(tpe)
    else if (tpe <:< TypeRepr.of[Either[?, ?]]) deriveEitherBinding(tpe)
    else if (tpe <:< TypeRepr.of[Map[?, ?]])
      fail(s"Use Binding.of[Map] for map types, not Binding.of[${tpe.show}]")
    else if (tpe <:< TypeRepr.of[Chunk[?]])
      fail(s"Use Binding.of[Chunk] for Chunk types, not Binding.of[${tpe.show}]")
    else if (tpe <:< TypeRepr.of[ArraySeq[?]])
      fail(s"Use Binding.of[ArraySeq] for ArraySeq types, not Binding.of[${tpe.show}]")
    else if (tpe <:< TypeRepr.of[List[?]])
      fail(s"Use Binding.of[List] for List types, not Binding.of[${tpe.show}]")
    else if (tpe <:< TypeRepr.of[Vector[?]])
      fail(s"Use Binding.of[Vector] for Vector types, not Binding.of[${tpe.show}]")
    else if (tpe <:< TypeRepr.of[Set[?]])
      fail(s"Use Binding.of[Set] for Set types, not Binding.of[${tpe.show}]")
    else if (tpe <:< TypeRepr.of[IndexedSeq[?]])
      fail(s"Use Binding.of[IndexedSeq] for IndexedSeq types, not Binding.of[${tpe.show}]")
    else if (tpe <:< TypeRepr.of[collection.immutable.Seq[?]])
      fail(s"Use Binding.of[Seq] for Seq types, not Binding.of[${tpe.show}]")
    else if (tpe <:< arrayOfWildcardTpe)
      fail(s"Use Binding.of[Array] for Array types, not Binding.of[${tpe.show}]")
    else if (isIArray(tpe))
      fail(s"Use Binding.of[IArray] for IArray types, not Binding.of[${tpe.show}]")
    else if (isIterator(tpe))
      fail(s"Cannot derive Binding for Iterator types: ${tpe.show}. Iterators are not round-trip serializable.")
    else if (isGenericTuple(tpe)) deriveGenericTupleBinding[A](tpe)
    else if (isNamedTuple(tpe)) deriveNamedTupleBinding[A](tpe)
    else if (isEnumOrModuleValue(tpe)) deriveEnumOrModuleValueBinding[A]
    else if (isSealedTraitOrAbstractClass(tpe)) deriveSealedTraitBinding[A](tpe)
    else if (isUnionOfStructuralTypes(tpe)) deriveStructuralVariantBindingSimple[A](tpe)
    else if (isUnion(tpe)) deriveUnionBinding[A](tpe)
    else if (isNonAbstractScalaClass(tpe)) {
      findSmartConstructor(tpe) match {
        case Some(info) => deriveSmartConstructorBinding[A](tpe, info)
        case None       => deriveRecordBinding[A](tpe)
      }
    } else if (isOpaque(tpe)) deriveOpaqueBinding[A](tpe)
    else if (isZioPreludeNewtype(tpe)) deriveZioPreludeNewtypeBinding[A](tpe)
    else if (isTypeRef(tpe)) {
      val sTpe = typeRefDealias(tpe)
      sTpe.asType match { case '[s] => deriveBinding[s](sTpe) }
    } else if (isStructuralType(tpe)) deriveStructuralRecordBindingSimple[A](tpe)
    else fail(s"Cannot derive Binding for type: ${tpe.show}")

  private def deriveSomeBinding(tpe: TypeRepr)(using Quotes): Expr[Any] = {
    val elemTpe       = typeArgs(tpe).head
    val dealiasedElem = dealiasOnDemand(elemTpe)
    if (dealiasedElem <:< intTpe) '{ Binding.Record.someInt }
    else if (dealiasedElem <:< longTpe) '{ Binding.Record.someLong }
    else if (dealiasedElem <:< floatTpe) '{ Binding.Record.someFloat }
    else if (dealiasedElem <:< doubleTpe) '{ Binding.Record.someDouble }
    else if (dealiasedElem <:< booleanTpe) '{ Binding.Record.someBoolean }
    else if (dealiasedElem <:< byteTpe) '{ Binding.Record.someByte }
    else if (dealiasedElem <:< shortTpe) '{ Binding.Record.someShort }
    else if (dealiasedElem <:< charTpe) '{ Binding.Record.someChar }
    else if (dealiasedElem <:< unitTpe) '{ Binding.Record.someUnit }
    else
      elemTpe.asType match {
        case '[a] => '{ Binding.Record.some[a & AnyRef] }
      }
  }

  private def deriveOptionBinding(tpe: TypeRepr)(using Quotes): Expr[Any] =
    typeArgs(tpe).head.asType match {
      case '[a] => '{ Binding.Variant.option[a] }
    }

  private def deriveLeftBinding(tpe: TypeRepr)(using Quotes): Expr[Any] = {
    val args       = typeArgs(tpe)
    val aTpe       = args(0)
    val dealiasedA = dealiasOnDemand(aTpe)
    val bTpe       = args(1)
    (aTpe.asType, bTpe.asType) match {
      case ('[a], '[b]) =>
        if (dealiasedA <:< intTpe) '{ Binding.Record.leftInt[b] }
        else if (dealiasedA <:< longTpe) '{ Binding.Record.leftLong[b] }
        else if (dealiasedA <:< floatTpe) '{ Binding.Record.leftFloat[b] }
        else if (dealiasedA <:< doubleTpe) '{ Binding.Record.leftDouble[b] }
        else if (dealiasedA <:< booleanTpe) '{ Binding.Record.leftBoolean[b] }
        else if (dealiasedA <:< byteTpe) '{ Binding.Record.leftByte[b] }
        else if (dealiasedA <:< shortTpe) '{ Binding.Record.leftShort[b] }
        else if (dealiasedA <:< charTpe) '{ Binding.Record.leftChar[b] }
        else if (dealiasedA <:< unitTpe) '{ Binding.Record.leftUnit[b] }
        else '{ Binding.Record.left[a, b] }
    }
  }

  private def deriveRightBinding(tpe: TypeRepr)(using Quotes): Expr[Any] = {
    val args       = typeArgs(tpe)
    val aTpe       = args(0)
    val bTpe       = args(1)
    val dealiasedB = dealiasOnDemand(bTpe)
    (aTpe.asType, bTpe.asType) match {
      case ('[a], '[b]) =>
        if (dealiasedB <:< intTpe) '{ Binding.Record.rightInt[a] }
        else if (dealiasedB <:< longTpe) '{ Binding.Record.rightLong[a] }
        else if (dealiasedB <:< floatTpe) '{ Binding.Record.rightFloat[a] }
        else if (dealiasedB <:< doubleTpe) '{ Binding.Record.rightDouble[a] }
        else if (dealiasedB <:< booleanTpe) '{ Binding.Record.rightBoolean[a] }
        else if (dealiasedB <:< byteTpe) '{ Binding.Record.rightByte[a] }
        else if (dealiasedB <:< shortTpe) '{ Binding.Record.rightShort[a] }
        else if (dealiasedB <:< charTpe) '{ Binding.Record.rightChar[a] }
        else if (dealiasedB <:< unitTpe) '{ Binding.Record.rightUnit[a] }
        else '{ Binding.Record.right[a, b] }
    }
  }

  private def deriveEitherBinding(tpe: TypeRepr)(using Quotes): Expr[Any] = {
    val args = typeArgs(tpe)
    (args(0).asType, args(1).asType) match {
      case ('[a], '[b]) => '{ Binding.Variant.either[a, b] }
    }
  }

  private def deriveEnumOrModuleValueBinding[A: Type](using Quotes): Expr[Any] = {
    val tpe   = TypeRepr.of[A]
    val value = Ref(
      if (isEnumValue(tpe)) tpe.termSymbol
      else tpe.typeSymbol.companionModule
    ).asExpr.asInstanceOf[Expr[A]]
    '{
      new Binding.Record[A](
        constructor = new ConstantConstructor($value),
        deconstructor = new ConstantDeconstructor
      )
    }
  }

  private def deriveSealedTraitBinding[A: Type](tpe: TypeRepr)(using Quotes): Expr[Any] = {
    val subtypes = directSubTypes(tpe)
    if (subtypes.isEmpty) fail(s"Cannot find sub-types for ADT base '${tpe.show}'.")

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
          }.asInstanceOf[Expr[Matcher[? <: A]]]
      }
    })

    '{
      new Binding.Variant[A](
        discriminator = new Discriminator[A] {
          def discriminate(a: A): Int = ${
            val v = 'a
            Match(
              '{ $v: @scala.unchecked }.asTerm,
              subtypes.zipWithIndex.map { case (sTpe, idx) =>
                CaseDef(Typed(Wildcard(), Inferred(sTpe)), None, Literal(IntConstant(idx)))
              }
            ).asExpr.asInstanceOf[Expr[Int]]
          }
        },
        matchers = Matchers($matcherCases*)
      )
    }
  }

  private def deriveUnionBinding[A: Type](tpe: TypeRepr)(using Quotes): Expr[Any] = {
    val subtypes = allUnionTypes(tpe)
    if (subtypes.isEmpty) fail(s"Cannot find sub-types for union type '${tpe.show}'.")

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
          }.asInstanceOf[Expr[Matcher[? <: A]]]
      }
    })

    '{
      new Binding.Variant[A](
        discriminator = new Discriminator[A] {
          def discriminate(a: A): Int = ${
            val v = 'a
            Match(
              '{ $v: @scala.unchecked }.asTerm,
              subtypes.zipWithIndex.map { case (sTpe, idx) =>
                CaseDef(Typed(Wildcard(), Inferred(sTpe)), None, Literal(IntConstant(idx)))
              }
            ).asExpr.asInstanceOf[Expr[Int]]
          }
        },
        matchers = Matchers($matcherCases*)
      )
    }
  }

  private def deriveOpaqueBinding[A: Type](tpe: TypeRepr)(using Quotes): Expr[Any] = {
    val underlyingTpe = opaqueDealias(tpe)
    val owner         = tpe.typeSymbol.owner
    val companion     =
      if (owner.isClassDef && owner.flags.is(Flags.Module)) owner.companionModule
      else tpe.typeSymbol.companionModule

    if (companion == Symbol.noSymbol) {
      underlyingTpe.asType match {
        case '[b] =>
          '{
            Binding.Wrapper[A, b](
              wrap = (b: b) => b.asInstanceOf[A],
              unwrap = (a: A) => a.asInstanceOf[b]
            )
          }
      }
    } else {
      val applyMethods = companion.methodMember("apply")
      val eitherSymbol = Symbol.requiredClass("scala.util.Either")

      val smartConstructorOpt = applyMethods.find { method =>
        val methodType = companion.typeRef.memberType(method)
        methodType match {
          case MethodType(paramNames, paramTypes, returnType) if paramNames.size == 1 =>
            val paramType = paramTypes.head.dealias
            if (!(paramType =:= underlyingTpe)) false
            else {
              returnType.dealias match {
                case AppliedType(con, List(_, rightType)) if con.typeSymbol == eitherSymbol =>
                  rightType =:= tpe || rightType.dealias =:= tpe
                case _ => false
              }
            }
          case _ => false
        }
      }

      smartConstructorOpt match {
        case Some(method) =>
          val returnType = companion.typeRef.memberType(method) match {
            case MethodType(_, _, rt) => rt.dealias
            case _                    => fail(s"Unexpected method type for opaque smart constructor: ${tpe.show}")
          }
          val errorType = returnType match {
            case AppliedType(_, List(errTpe, _)) => errTpe.dealias
            case _                               => fail(s"Unexpected return type for opaque smart constructor: ${tpe.show}")
          }
          val isSchemaError = errorType <:< TypeRepr.of[SchemaError]

          val unwrapMethods = companion.methodMember("unwrap") ++ companion.methodMember("value")
          val unwrapMethod  = unwrapMethods.find { m =>
            val mTpe = companion.typeRef.memberType(m)
            mTpe match {
              case MethodType(_, List(paramTpe), retTpe) =>
                (paramTpe =:= tpe || paramTpe.dealias =:= tpe) &&
                (retTpe =:= underlyingTpe || retTpe.dealias =:= underlyingTpe)
              case _ => false
            }
          }

          (underlyingTpe.asType, errorType.asType) match {
            case ('[b], '[e]) =>
              val wrapExpr: Expr[b => A] =
                if (isSchemaError) {
                  '{ (underlying: b) =>
                    ${
                      Apply(Select(Ref(companion), method), List('underlying.asTerm)).asExpr
                        .asInstanceOf[Expr[Either[SchemaError, A]]]
                    } match {
                      case Right(a)  => a
                      case Left(err) => throw err
                    }
                  }
                } else {
                  '{ (underlying: b) =>
                    ${
                      Apply(Select(Ref(companion), method), List('underlying.asTerm)).asExpr
                        .asInstanceOf[Expr[Either[e, A]]]
                    } match {
                      case Right(a)  => a
                      case Left(err) => throw SchemaError.validationFailed(err.toString)
                    }
                  }
                }

              val unwrapExpr: Expr[A => b] = unwrapMethod match {
                case Some(m) =>
                  '{ (a: A) =>
                    ${ Apply(Select(Ref(companion), m), List('a.asTerm)).asExpr.asInstanceOf[Expr[b]] }
                  }
                case None =>
                  '{ (a: A) => a.asInstanceOf[b] }
              }

              '{ Binding.Wrapper[A, b](wrap = $wrapExpr, unwrap = $unwrapExpr) }
          }

        case None =>
          underlyingTpe.asType match {
            case '[b] =>
              '{
                Binding.Wrapper[A, b](
                  wrap = (b: b) => b.asInstanceOf[A],
                  unwrap = (a: A) => a.asInstanceOf[b]
                )
              }
          }
      }
    }
  }

  private def deriveZioPreludeNewtypeBinding[A: Type](tpe: TypeRepr)(using Quotes): Expr[Any] = {
    val underlyingTpe = zioPreludeNewtypeDealias(tpe)
    val companion     = zioPreludeNewtypeCompanion(tpe)

    val wrapMethod = companion.symbol
      .methodMember("wrap")
      .headOption
      .orElse(companion.symbol.methodMember("make").headOption)
    val unwrapMethod = companion.symbol.methodMember("unwrap").headOption
    val eitherSymbol = Symbol.requiredClass("scala.util.Either")

    underlyingTpe.asType match {
      case '[b] =>
        val wrapExpr: Expr[b => A] = wrapMethod match {
          case Some(m) =>
            val mTpe = companion.symbol.typeRef.memberType(m)
            mTpe match {
              case MethodType(_, _, AppliedType(con, List(errTpe, _))) if con.typeSymbol == eitherSymbol =>
                val isSchemaError = errTpe.dealias <:< TypeRepr.of[SchemaError]
                errTpe.asType match {
                  case '[e] =>
                    if (isSchemaError) {
                      '{ (underlying: b) =>
                        ${
                          Apply(Select(companion, m), List('underlying.asTerm)).asExpr
                            .asInstanceOf[Expr[Either[SchemaError, A]]]
                        } match {
                          case Right(a)  => a
                          case Left(err) => throw err
                        }
                      }
                    } else {
                      '{ (underlying: b) =>
                        ${
                          Apply(Select(companion, m), List('underlying.asTerm)).asExpr
                            .asInstanceOf[Expr[Either[e, A]]]
                        } match {
                          case Right(a)  => a
                          case Left(err) => throw SchemaError.validationFailed(err.toString)
                        }
                      }
                    }
                }
              case _ =>
                '{ (underlying: b) =>
                  ${ Apply(Select(companion, m), List('underlying.asTerm)).asExpr.asInstanceOf[Expr[A]] }
                }
            }
          case None =>
            '{ (underlying: b) => underlying.asInstanceOf[A] }
        }

        val unwrapExpr: Expr[A => b] = unwrapMethod match {
          case Some(m) =>
            '{ (a: A) => ${ Apply(Select(companion, m), List('a.asTerm)).asExpr.asInstanceOf[Expr[b]] } }
          case None =>
            '{ (a: A) => a.asInstanceOf[b] }
        }

        '{ Binding.Wrapper[A, b](wrap = $wrapExpr, unwrap = $unwrapExpr) }
    }
  }

  private def deriveSmartConstructorBinding[A: Type](
    tpe: TypeRepr,
    info: SmartConstructorInfo
  )(using Quotes): Expr[Any] = {
    val isSchemaError = info.errorType <:< TypeRepr.of[SchemaError]

    (info.underlyingType.asType, info.errorType.asType) match {
      case ('[b], '[e]) =>
        val wrapExpr: Expr[b => A] =
          if (isSchemaError) {
            '{ (underlying: b) =>
              ${
                Apply(Select(info.companionRef, info.applyMethod), List('underlying.asTerm)).asExpr
                  .asInstanceOf[Expr[Either[SchemaError, A]]]
              } match {
                case Right(a)  => a
                case Left(err) => throw err
              }
            }
          } else {
            '{ (underlying: b) =>
              ${
                Apply(Select(info.companionRef, info.applyMethod), List('underlying.asTerm)).asExpr
                  .asInstanceOf[Expr[Either[e, A]]]
              } match {
                case Right(a)  => a
                case Left(err) => throw SchemaError.validationFailed(err.toString)
              }
            }
          }

        val fieldName                = info.unwrapFieldName
        val fieldSymbol              = tpe.typeSymbol.fieldMember(fieldName)
        val unwrapExpr: Expr[A => b] = '{ (a: A) =>
          ${ Select('a.asTerm, fieldSymbol).asExpr.asInstanceOf[Expr[b]] }
        }

        '{ Binding.Wrapper[A, b](wrap = $wrapExpr, unwrap = $unwrapExpr) }
    }
  }

  private def deriveRecordBinding[A: Type](tpe: TypeRepr)(using Quotes): Expr[Any] = {
    import zio.blocks.schema.binding.RegisterOffset.*

    val classSymbol = tpe.classSymbol.getOrElse(fail(s"Cannot get class symbol for ${tpe.show}"))
    val constructor = classSymbol.primaryConstructor

    val (tpeTypeArgs, tpeTypeParams, paramLists) = constructor.paramSymss match {
      case tps :: ps if tps.exists(_.isTypeParam) => (typeArgs(tpe), tps, ps.map(_.filterNot(_.isTypeParam)))
      case ps                                     => (Nil, Nil, ps.map(_.filterNot(_.isTypeParam)))
    }

    case class FieldInfo(name: String, tpe: TypeRepr, registerType: RegisterType[?])

    val fieldLists = paramLists.map(_.map { sym =>
      var fieldTpe = tpe.memberType(sym).dealias
      if (tpeTypeArgs.nonEmpty) fieldTpe = fieldTpe.substituteTypes(tpeTypeParams, tpeTypeArgs)

      // Dealias newtypes/opaque types to get the underlying type for register type determination
      val dealiasedTpe = dealiasOnDemand(fieldTpe)
      val registerType =
        if (dealiasedTpe <:< booleanTpe) RegisterType.Boolean
        else if (dealiasedTpe <:< byteTpe) RegisterType.Byte
        else if (dealiasedTpe <:< shortTpe) RegisterType.Short
        else if (dealiasedTpe <:< intTpe) RegisterType.Int
        else if (dealiasedTpe <:< longTpe) RegisterType.Long
        else if (dealiasedTpe <:< floatTpe) RegisterType.Float
        else if (dealiasedTpe <:< doubleTpe) RegisterType.Double
        else if (dealiasedTpe <:< charTpe) RegisterType.Char
        else if (dealiasedTpe <:< unitTpe) RegisterType.Unit
        else RegisterType.Object()

      FieldInfo(sym.name, fieldTpe, registerType)
    })
    val fields = fieldLists.flatten

    val usedRegisters = fields.foldLeft(RegisterOffset.Zero) { (acc, f) =>
      f.registerType match {
        case RegisterType.Boolean      => RegisterOffset.add(acc, RegisterOffset(booleans = 1))
        case RegisterType.Byte         => RegisterOffset.add(acc, RegisterOffset(bytes = 1))
        case RegisterType.Short        => RegisterOffset.add(acc, RegisterOffset(shorts = 1))
        case RegisterType.Int          => RegisterOffset.add(acc, RegisterOffset(ints = 1))
        case RegisterType.Long         => RegisterOffset.add(acc, RegisterOffset(longs = 1))
        case RegisterType.Float        => RegisterOffset.add(acc, RegisterOffset(floats = 1))
        case RegisterType.Double       => RegisterOffset.add(acc, RegisterOffset(doubles = 1))
        case RegisterType.Char         => RegisterOffset.add(acc, RegisterOffset(chars = 1))
        case RegisterType.Unit         => acc
        case _: RegisterType.Object[?] => RegisterOffset.add(acc, RegisterOffset(objects = 1))
      }
    }
    val usedRegistersExpr = Expr(usedRegisters)

    val constructorExpr: Expr[(Registers, RegisterOffset) => A] = '{ (in: Registers, offset: RegisterOffset) =>
      ${
        var currentOffset = RegisterOffset.Zero

        def fieldToArg(f: FieldInfo): Term = {
          val offsetExpr    = Expr(currentOffset)
          val dealiasedType = dealiasOnDemand(f.tpe)
          val needsCast     = !(f.tpe =:= dealiasedType)

          val argExpr = f.registerType match {
            case RegisterType.Boolean =>
              currentOffset = RegisterOffset.add(currentOffset, RegisterOffset(booleans = 1))
              if (needsCast) f.tpe.asType match {
                case '[ft] => '{ in.getBoolean(RegisterOffset.add(offset, $offsetExpr)).asInstanceOf[ft] }
              }
              else '{ in.getBoolean(RegisterOffset.add(offset, $offsetExpr)) }
            case RegisterType.Byte =>
              currentOffset = RegisterOffset.add(currentOffset, RegisterOffset(bytes = 1))
              if (needsCast) f.tpe.asType match {
                case '[ft] => '{ in.getByte(RegisterOffset.add(offset, $offsetExpr)).asInstanceOf[ft] }
              }
              else '{ in.getByte(RegisterOffset.add(offset, $offsetExpr)) }
            case RegisterType.Short =>
              currentOffset = RegisterOffset.add(currentOffset, RegisterOffset(shorts = 1))
              if (needsCast) f.tpe.asType match {
                case '[ft] => '{ in.getShort(RegisterOffset.add(offset, $offsetExpr)).asInstanceOf[ft] }
              }
              else '{ in.getShort(RegisterOffset.add(offset, $offsetExpr)) }
            case RegisterType.Int =>
              currentOffset = RegisterOffset.add(currentOffset, RegisterOffset(ints = 1))
              if (needsCast) f.tpe.asType match {
                case '[ft] => '{ in.getInt(RegisterOffset.add(offset, $offsetExpr)).asInstanceOf[ft] }
              }
              else '{ in.getInt(RegisterOffset.add(offset, $offsetExpr)) }
            case RegisterType.Long =>
              currentOffset = RegisterOffset.add(currentOffset, RegisterOffset(longs = 1))
              if (needsCast) f.tpe.asType match {
                case '[ft] => '{ in.getLong(RegisterOffset.add(offset, $offsetExpr)).asInstanceOf[ft] }
              }
              else '{ in.getLong(RegisterOffset.add(offset, $offsetExpr)) }
            case RegisterType.Float =>
              currentOffset = RegisterOffset.add(currentOffset, RegisterOffset(floats = 1))
              if (needsCast) f.tpe.asType match {
                case '[ft] => '{ in.getFloat(RegisterOffset.add(offset, $offsetExpr)).asInstanceOf[ft] }
              }
              else '{ in.getFloat(RegisterOffset.add(offset, $offsetExpr)) }
            case RegisterType.Double =>
              currentOffset = RegisterOffset.add(currentOffset, RegisterOffset(doubles = 1))
              if (needsCast) f.tpe.asType match {
                case '[ft] => '{ in.getDouble(RegisterOffset.add(offset, $offsetExpr)).asInstanceOf[ft] }
              }
              else '{ in.getDouble(RegisterOffset.add(offset, $offsetExpr)) }
            case RegisterType.Char =>
              currentOffset = RegisterOffset.add(currentOffset, RegisterOffset(chars = 1))
              if (needsCast) f.tpe.asType match {
                case '[ft] => '{ in.getChar(RegisterOffset.add(offset, $offsetExpr)).asInstanceOf[ft] }
              }
              else '{ in.getChar(RegisterOffset.add(offset, $offsetExpr)) }
            case RegisterType.Unit =>
              if (needsCast) f.tpe.asType match {
                case '[ft] => '{ ().asInstanceOf[ft] }
              }
              else '{ () }
            case _: RegisterType.Object[?] =>
              currentOffset = RegisterOffset.add(currentOffset, RegisterOffset(objects = 1))
              f.tpe.asType match {
                case '[ft] => '{ in.getObject(RegisterOffset.add(offset, $offsetExpr)).asInstanceOf[ft] }
              }
          }
          argExpr.asTerm
        }

        val argss      = fieldLists.map(_.map(fieldToArg))
        val newExpr    = New(Inferred(tpe))
        val selectCtor = Select(newExpr, constructor).appliedToTypes(tpeTypeArgs)
        val applied    =
          if (argss.isEmpty) Apply(selectCtor, Nil)
          else argss.tail.foldLeft(Apply(selectCtor, argss.head): Term)((acc, args) => Apply(acc, args))
        applied.asExpr.asInstanceOf[Expr[A]]
      }
    }

    val deconstructorExpr: Expr[(Registers, RegisterOffset, A) => Unit] =
      '{ (out: Registers, offset: RegisterOffset, in: A) =>
        ${
          var currentOffset = RegisterOffset.Zero
          val statements    = fields.map { f =>
            val offsetExpr  = Expr(currentOffset)
            val fieldSymbol = {
              val fs = tpe.typeSymbol.fieldMember(f.name)
              if (fs != Symbol.noSymbol) fs
              else
                tpe.typeSymbol
                  .methodMember(f.name)
                  .headOption
                  .getOrElse(fail(s"Cannot find field ${f.name} in ${tpe.show}"))
            }
            val fieldSelectTerm = Select('in.asTerm, fieldSymbol)

            // Check if field needs cast from newtype to underlying primitive
            val dealiasedType = dealiasOnDemand(f.tpe)
            val needsCast     = !(f.tpe =:= dealiasedType)

            val stmt = f.registerType match {
              case RegisterType.Boolean =>
                currentOffset = RegisterOffset.add(currentOffset, RegisterOffset(booleans = 1))
                if (needsCast) {
                  val castedField = '{ ${ fieldSelectTerm.asExpr }.asInstanceOf[Boolean] }
                  '{ out.setBoolean(RegisterOffset.add(offset, $offsetExpr), $castedField) }
                } else {
                  '{
                    out.setBoolean(
                      RegisterOffset.add(offset, $offsetExpr),
                      ${ fieldSelectTerm.asExpr.asInstanceOf[Expr[Boolean]] }
                    )
                  }
                }
              case RegisterType.Byte =>
                currentOffset = RegisterOffset.add(currentOffset, RegisterOffset(bytes = 1))
                if (needsCast) {
                  val castedField = '{ ${ fieldSelectTerm.asExpr }.asInstanceOf[Byte] }
                  '{ out.setByte(RegisterOffset.add(offset, $offsetExpr), $castedField) }
                } else {
                  '{
                    out.setByte(
                      RegisterOffset.add(offset, $offsetExpr),
                      ${ fieldSelectTerm.asExpr.asInstanceOf[Expr[Byte]] }
                    )
                  }
                }
              case RegisterType.Short =>
                currentOffset = RegisterOffset.add(currentOffset, RegisterOffset(shorts = 1))
                if (needsCast) {
                  val castedField = '{ ${ fieldSelectTerm.asExpr }.asInstanceOf[Short] }
                  '{ out.setShort(RegisterOffset.add(offset, $offsetExpr), $castedField) }
                } else {
                  '{
                    out.setShort(
                      RegisterOffset.add(offset, $offsetExpr),
                      ${ fieldSelectTerm.asExpr.asInstanceOf[Expr[Short]] }
                    )
                  }
                }
              case RegisterType.Int =>
                currentOffset = RegisterOffset.add(currentOffset, RegisterOffset(ints = 1))
                if (needsCast) {
                  val castedField = '{ ${ fieldSelectTerm.asExpr }.asInstanceOf[Int] }
                  '{ out.setInt(RegisterOffset.add(offset, $offsetExpr), $castedField) }
                } else {
                  '{
                    out.setInt(
                      RegisterOffset.add(offset, $offsetExpr),
                      ${ fieldSelectTerm.asExpr.asInstanceOf[Expr[Int]] }
                    )
                  }
                }
              case RegisterType.Long =>
                currentOffset = RegisterOffset.add(currentOffset, RegisterOffset(longs = 1))
                if (needsCast) {
                  val castedField = '{ ${ fieldSelectTerm.asExpr }.asInstanceOf[Long] }
                  '{ out.setLong(RegisterOffset.add(offset, $offsetExpr), $castedField) }
                } else {
                  '{
                    out.setLong(
                      RegisterOffset.add(offset, $offsetExpr),
                      ${ fieldSelectTerm.asExpr.asInstanceOf[Expr[Long]] }
                    )
                  }
                }
              case RegisterType.Float =>
                currentOffset = RegisterOffset.add(currentOffset, RegisterOffset(floats = 1))
                if (needsCast) {
                  val castedField = '{ ${ fieldSelectTerm.asExpr }.asInstanceOf[Float] }
                  '{ out.setFloat(RegisterOffset.add(offset, $offsetExpr), $castedField) }
                } else {
                  '{
                    out.setFloat(
                      RegisterOffset.add(offset, $offsetExpr),
                      ${ fieldSelectTerm.asExpr.asInstanceOf[Expr[Float]] }
                    )
                  }
                }
              case RegisterType.Double =>
                currentOffset = RegisterOffset.add(currentOffset, RegisterOffset(doubles = 1))
                if (needsCast) {
                  val castedField = '{ ${ fieldSelectTerm.asExpr }.asInstanceOf[Double] }
                  '{ out.setDouble(RegisterOffset.add(offset, $offsetExpr), $castedField) }
                } else {
                  '{
                    out.setDouble(
                      RegisterOffset.add(offset, $offsetExpr),
                      ${ fieldSelectTerm.asExpr.asInstanceOf[Expr[Double]] }
                    )
                  }
                }
              case RegisterType.Char =>
                currentOffset = RegisterOffset.add(currentOffset, RegisterOffset(chars = 1))
                if (needsCast) {
                  val castedField = '{ ${ fieldSelectTerm.asExpr }.asInstanceOf[Char] }
                  '{ out.setChar(RegisterOffset.add(offset, $offsetExpr), $castedField) }
                } else {
                  '{
                    out.setChar(
                      RegisterOffset.add(offset, $offsetExpr),
                      ${ fieldSelectTerm.asExpr.asInstanceOf[Expr[Char]] }
                    )
                  }
                }
              case RegisterType.Unit =>
                '{ () }
              case _: RegisterType.Object[?] =>
                currentOffset = RegisterOffset.add(currentOffset, RegisterOffset(objects = 1))
                if (f.tpe <:< TypeRepr.of[AnyRef]) {
                  '{
                    out.setObject(
                      RegisterOffset.add(offset, $offsetExpr),
                      ${ fieldSelectTerm.asExpr.asInstanceOf[Expr[AnyRef]] }
                    )
                  }
                } else {
                  '{
                    out.setObject(
                      RegisterOffset.add(offset, $offsetExpr),
                      ${ fieldSelectTerm.asExpr }.asInstanceOf[AnyRef]
                    )
                  }
                }
            }
            stmt.asTerm
          }
          if (statements.isEmpty) '{ () }
          else Block(statements.init.toList, statements.last).asExpr.asInstanceOf[Expr[Unit]]
        }
      }

    '{
      new Binding.Record[A](
        constructor = new Constructor[A] {
          def usedRegisters: RegisterOffset = $usedRegistersExpr

          def construct(in: Registers, offset: RegisterOffset): A = $constructorExpr(in, offset)
        },
        deconstructor = new Deconstructor[A] {
          def usedRegisters: RegisterOffset = $usedRegistersExpr

          def deconstruct(out: Registers, offset: RegisterOffset, in: A): Unit = $deconstructorExpr(out, offset, in)
        }
      )
    }
  }

  private val tupleTpe          = Symbol.requiredClass("scala.Tuple").typeRef
  private val arrayOfAnyTpe     = defn.ArrayClass.typeRef.appliedTo(anyTpe)
  private val iArrayOfAnyRefTpe = TypeRepr.of[IArray[AnyRef]]
  private val newArrayOfAny     =
    Select(New(TypeIdent(defn.ArrayClass)), defn.ArrayClass.primaryConstructor).appliedToType(anyTpe)
  private val fromIArrayMethod     = Select.unique(Ref(Symbol.requiredModule("scala.runtime.TupleXXL")), "fromIArray")
  private val asInstanceOfMethod   = anyTpe.typeSymbol.declaredMethod("asInstanceOf").head
  private val productElementMethod = tupleTpe.typeSymbol.methodMember("productElement").head
  private lazy val toTupleMethod   = Select.unique(Ref(Symbol.requiredModule("scala.NamedTuple")), "toTuple")

  private def dealiasOnDemand(tpe: TypeRepr): TypeRepr =
    if (isOpaque(tpe)) opaqueDealias(tpe)
    else if (isZioPreludeNewtype(tpe)) zioPreludeNewtypeDealias(tpe)
    else if (isTypeRef(tpe)) typeRefDealias(tpe)
    else tpe

  private def fieldOffset(fTpe: TypeRepr): RegisterOffset.RegisterOffset = {
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

  private case class TupleFieldInfo(index: Int, tpe: TypeRepr, usedRegisters: RegisterOffset.RegisterOffset)

  private def tupleFieldConstructor(
    in: Expr[Registers],
    offset: Expr[RegisterOffset.RegisterOffset],
    fieldInfo: TupleFieldInfo
  )(using Quotes): Term = {
    val fTpe          = fieldInfo.tpe
    val usedRegisters = Expr(fieldInfo.usedRegisters)
    val sTpe          = dealiasOnDemand(fTpe)
    (if (sTpe =:= intTpe) '{ $in.getInt(RegisterOffset.add($offset, $usedRegisters)) }
     else if (sTpe =:= floatTpe) '{ $in.getFloat(RegisterOffset.add($offset, $usedRegisters)) }
     else if (sTpe =:= longTpe) '{ $in.getLong(RegisterOffset.add($offset, $usedRegisters)) }
     else if (sTpe =:= doubleTpe) '{ $in.getDouble(RegisterOffset.add($offset, $usedRegisters)) }
     else if (sTpe =:= booleanTpe) '{ $in.getBoolean(RegisterOffset.add($offset, $usedRegisters)) }
     else if (sTpe =:= byteTpe) '{ $in.getByte(RegisterOffset.add($offset, $usedRegisters)) }
     else if (sTpe =:= charTpe) '{ $in.getChar(RegisterOffset.add($offset, $usedRegisters)) }
     else if (sTpe =:= shortTpe) '{ $in.getShort(RegisterOffset.add($offset, $usedRegisters)) }
     else if (sTpe =:= unitTpe) '{ () }
     else
       fTpe.asType match {
         case '[ft] =>
           if (sTpe <:< intTpe) '{ $in.getInt(RegisterOffset.add($offset, $usedRegisters)).asInstanceOf[ft] }
           else if (sTpe <:< floatTpe) '{ $in.getFloat(RegisterOffset.add($offset, $usedRegisters)).asInstanceOf[ft] }
           else if (sTpe <:< longTpe) '{ $in.getLong(RegisterOffset.add($offset, $usedRegisters)).asInstanceOf[ft] }
           else if (sTpe <:< doubleTpe) '{ $in.getDouble(RegisterOffset.add($offset, $usedRegisters)).asInstanceOf[ft] }
           else if (sTpe <:< booleanTpe) '{
             $in.getBoolean(RegisterOffset.add($offset, $usedRegisters)).asInstanceOf[ft]
           }
           else if (sTpe <:< byteTpe) '{ $in.getByte(RegisterOffset.add($offset, $usedRegisters)).asInstanceOf[ft] }
           else if (sTpe <:< charTpe) '{ $in.getChar(RegisterOffset.add($offset, $usedRegisters)).asInstanceOf[ft] }
           else if (sTpe <:< shortTpe) '{ $in.getShort(RegisterOffset.add($offset, $usedRegisters)).asInstanceOf[ft] }
           else if (sTpe <:< unitTpe) '{ ().asInstanceOf[ft] }
           else '{ $in.getObject(RegisterOffset.add($offset, $usedRegisters)).asInstanceOf[ft] }
       }).asTerm
  }

  private def toBlock(terms: List[Term]): Term = {
    val size = terms.size
    if (size > 1) Block(terms.init, terms.last)
    else if (size == 1) terms.head
    else '{ () }.asTerm
  }

  private def deriveGenericTupleBinding[A: Type](tpe: TypeRepr)(using Quotes): Expr[Any] = {
    import zio.blocks.schema.binding.RegisterOffset.*

    val tTpe        = normalizeGenericTuple(tpe)
    val tpeTypeArgs = genericTupleTypeArgs(tpe)

    val fieldInfos = {
      var usedRegisters = RegisterOffset.Zero
      var idx           = 0
      tpeTypeArgs.map { fTpe =>
        val fieldInfo = TupleFieldInfo(idx, fTpe, usedRegisters)
        usedRegisters = RegisterOffset.add(usedRegisters, fieldOffset(fTpe))
        idx += 1
        fieldInfo
      }
    }
    val totalUsedRegisters = fieldInfos.lastOption
      .map(f => RegisterOffset.add(f.usedRegisters, fieldOffset(f.tpe)))
      .getOrElse(RegisterOffset.Zero)
    val usedRegistersExpr = Expr(totalUsedRegisters)

    tTpe.asType match {
      case '[tt] =>
        val constructorExpr: Expr[(Registers, RegisterOffset) => tt] = '{ (in: Registers, offset: RegisterOffset) =>
          ${
            if (fieldInfos.isEmpty) Expr(EmptyTuple).asInstanceOf[Expr[tt]]
            else {
              val symbol      = Symbol.newVal(Symbol.spliceOwner, "xs", arrayOfAnyTpe, Flags.EmptyFlags, Symbol.noSymbol)
              val ref         = Ref(symbol)
              val update      = Select(ref, defn.Array_update)
              val assignments = fieldInfos.map { fieldInfo =>
                Apply(
                  update,
                  List(Literal(IntConstant(fieldInfo.index)), tupleFieldConstructor('in, 'offset, fieldInfo))
                )
              }
              val valDef   = ValDef(symbol, new Some(Apply(newArrayOfAny, List(Literal(IntConstant(fieldInfos.size))))))
              val block    = Block(valDef :: assignments, ref)
              val typeCast = Select(block, asInstanceOfMethod).appliedToType(iArrayOfAnyRefTpe)
              Select(Apply(fromIArrayMethod, List(typeCast)), asInstanceOfMethod)
                .appliedToType(tTpe)
                .asExpr
                .asInstanceOf[Expr[tt]]
            }
          }
        }

        val deconstructorExpr: Expr[(Registers, RegisterOffset, tt) => Unit] = '{
          (out: Registers, offset: RegisterOffset, in: tt) =>
            ${
              val productElement = Select('in.asTerm, productElementMethod)
              val statements     = fieldInfos.map { fieldInfo =>
                val fTpe          = fieldInfo.tpe
                val sTpe          = dealiasOnDemand(fTpe)
                val getter        = productElement.appliedTo(Literal(IntConstant(fieldInfo.index))).asExpr
                val usedRegisters = Expr(fieldInfo.usedRegisters)
                (if (sTpe <:< intTpe) '{
                   out.setInt(RegisterOffset.add(offset, $usedRegisters), $getter.asInstanceOf[Int])
                 }
                 else if (sTpe <:< floatTpe) '{
                   out.setFloat(RegisterOffset.add(offset, $usedRegisters), $getter.asInstanceOf[Float])
                 }
                 else if (sTpe <:< longTpe) '{
                   out.setLong(RegisterOffset.add(offset, $usedRegisters), $getter.asInstanceOf[Long])
                 }
                 else if (sTpe <:< doubleTpe) '{
                   out.setDouble(RegisterOffset.add(offset, $usedRegisters), $getter.asInstanceOf[Double])
                 }
                 else if (sTpe <:< booleanTpe) '{
                   out.setBoolean(RegisterOffset.add(offset, $usedRegisters), $getter.asInstanceOf[Boolean])
                 }
                 else if (sTpe <:< byteTpe) '{
                   out.setByte(RegisterOffset.add(offset, $usedRegisters), $getter.asInstanceOf[Byte])
                 }
                 else if (sTpe <:< charTpe) '{
                   out.setChar(RegisterOffset.add(offset, $usedRegisters), $getter.asInstanceOf[Char])
                 }
                 else if (sTpe <:< shortTpe) '{
                   out.setShort(RegisterOffset.add(offset, $usedRegisters), $getter.asInstanceOf[Short])
                 }
                 else if (sTpe <:< unitTpe) '{ () }
                 else
                   '{ out.setObject(RegisterOffset.add(offset, $usedRegisters), $getter.asInstanceOf[AnyRef]) }).asTerm
              }
              toBlock(statements).asExpr.asInstanceOf[Expr[Unit]]
            }
        }

        '{
          new Binding.Record[A](
            constructor = new Constructor[A] {
              def usedRegisters: RegisterOffset = $usedRegistersExpr

              def construct(in: Registers, offset: RegisterOffset): A = $constructorExpr(in, offset).asInstanceOf[A]
            },
            deconstructor = new Deconstructor[A] {
              def usedRegisters: RegisterOffset = $usedRegistersExpr

              def deconstruct(out: Registers, offset: RegisterOffset, in: A): Unit =
                $deconstructorExpr(out, offset, in.asInstanceOf[tt])
            }
          )
        }
    }
  }

  private def deriveNamedTupleBinding[A: Type](originalTpe: TypeRepr)(using Quotes): Expr[Any] = {
    import zio.blocks.schema.binding.RegisterOffset.*

    val tpeTypeArgs = typeArgs(originalTpe)
    var tTpe        = tpeTypeArgs.last
    if (isGenericTuple(tTpe)) tTpe = normalizeGenericTuple(tTpe)

    val valueTupleTypeArgs =
      if (isGenericTuple(tpeTypeArgs.last)) genericTupleTypeArgs(tpeTypeArgs.last)
      else typeArgs(tpeTypeArgs.last)

    val fieldInfos = {
      var usedRegisters = RegisterOffset.Zero
      var idx           = 0
      valueTupleTypeArgs.map { fTpe =>
        val fieldInfo = TupleFieldInfo(idx, fTpe, usedRegisters)
        usedRegisters = RegisterOffset.add(usedRegisters, fieldOffset(fTpe))
        idx += 1
        fieldInfo
      }
    }
    val totalUsedRegisters = fieldInfos.lastOption
      .map(f => RegisterOffset.add(f.usedRegisters, fieldOffset(f.tpe)))
      .getOrElse(RegisterOffset.Zero)
    val usedRegistersExpr = Expr(totalUsedRegisters)
    val isLargeTuple      = valueTupleTypeArgs.size > 22

    tTpe.asType match {
      case '[tt] =>
        val constructorExpr: Expr[(Registers, RegisterOffset) => tt] = '{ (in: Registers, offset: RegisterOffset) =>
          ${
            if (fieldInfos.isEmpty) Expr(EmptyTuple).asInstanceOf[Expr[tt]]
            else if (isLargeTuple) {
              // For tuples > 22 elements, use TupleXXL.fromIArray
              val symbol      = Symbol.newVal(Symbol.spliceOwner, "xs", arrayOfAnyTpe, Flags.EmptyFlags, Symbol.noSymbol)
              val ref         = Ref(symbol)
              val update      = Select(ref, defn.Array_update)
              val assignments = fieldInfos.map { fieldInfo =>
                Apply(
                  update,
                  List(Literal(IntConstant(fieldInfo.index)), tupleFieldConstructor('in, 'offset, fieldInfo))
                )
              }
              val valDef   = ValDef(symbol, new Some(Apply(newArrayOfAny, List(Literal(IntConstant(fieldInfos.size))))))
              val block    = Block(valDef :: assignments, ref)
              val typeCast = Select(block, asInstanceOfMethod).appliedToType(iArrayOfAnyRefTpe)
              Select(Apply(fromIArrayMethod, List(typeCast)), asInstanceOfMethod)
                .appliedToType(tTpe)
                .asExpr
                .asInstanceOf[Expr[tt]]
            } else {
              // For tuples <= 22 elements, use direct constructor (new TupleN(...))
              val tupleClassSymbol = tTpe.classSymbol.getOrElse(fail(s"Cannot get class symbol for tuple ${tTpe.show}"))
              val tupleConstructor = tupleClassSymbol.primaryConstructor
              val tupleTypeArgs    = typeArgs(tTpe)
              val args             = fieldInfos.map { fieldInfo =>
                tupleFieldConstructor('in, 'offset, fieldInfo)
              }
              val newExpr    = New(Inferred(tTpe))
              val selectCtor = Select(newExpr, tupleConstructor).appliedToTypes(tupleTypeArgs)
              Apply(selectCtor, args).asExpr.asInstanceOf[Expr[tt]]
            }
          }
        }

        val deconstructorExpr: Expr[(Registers, RegisterOffset, A) => Unit] = '{
          (out: Registers, offset: RegisterOffset, in: A) =>
            ${
              val toTupleCall    = Apply(toTupleMethod.appliedToTypes(originalTpe.typeArgs), List('in.asTerm))
              val tupleSymbol    = Symbol.newVal(Symbol.spliceOwner, "t", tTpe, Flags.EmptyFlags, Symbol.noSymbol)
              val tupleValDef    = ValDef(tupleSymbol, new Some(toTupleCall))
              val tupleRef       = Ref(tupleSymbol)
              val productElement = Select(tupleRef, productElementMethod)

              val statements = fieldInfos.map { fieldInfo =>
                val fTpe          = fieldInfo.tpe
                val sTpe          = dealiasOnDemand(fTpe)
                val getter        = productElement.appliedTo(Literal(IntConstant(fieldInfo.index))).asExpr
                val usedRegisters = Expr(fieldInfo.usedRegisters)
                (if (sTpe <:< intTpe) '{
                   out.setInt(RegisterOffset.add(offset, $usedRegisters), $getter.asInstanceOf[Int])
                 }
                 else if (sTpe <:< floatTpe) '{
                   out.setFloat(RegisterOffset.add(offset, $usedRegisters), $getter.asInstanceOf[Float])
                 }
                 else if (sTpe <:< longTpe) '{
                   out.setLong(RegisterOffset.add(offset, $usedRegisters), $getter.asInstanceOf[Long])
                 }
                 else if (sTpe <:< doubleTpe) '{
                   out.setDouble(RegisterOffset.add(offset, $usedRegisters), $getter.asInstanceOf[Double])
                 }
                 else if (sTpe <:< booleanTpe) '{
                   out.setBoolean(RegisterOffset.add(offset, $usedRegisters), $getter.asInstanceOf[Boolean])
                 }
                 else if (sTpe <:< byteTpe) '{
                   out.setByte(RegisterOffset.add(offset, $usedRegisters), $getter.asInstanceOf[Byte])
                 }
                 else if (sTpe <:< charTpe) '{
                   out.setChar(RegisterOffset.add(offset, $usedRegisters), $getter.asInstanceOf[Char])
                 }
                 else if (sTpe <:< shortTpe) '{
                   out.setShort(RegisterOffset.add(offset, $usedRegisters), $getter.asInstanceOf[Short])
                 }
                 else if (sTpe <:< unitTpe) '{ () }
                 else
                   '{ out.setObject(RegisterOffset.add(offset, $usedRegisters), $getter.asInstanceOf[AnyRef]) }).asTerm
              }

              Block(List(tupleValDef), toBlock(statements)).asExpr.asInstanceOf[Expr[Unit]]
            }
        }

        '{
          new Binding.Record[A](
            constructor = new Constructor[A] {
              def usedRegisters: RegisterOffset = $usedRegistersExpr

              def construct(in: Registers, offset: RegisterOffset): A = $constructorExpr(in, offset).asInstanceOf[A]
            },
            deconstructor = new Deconstructor[A] {
              def usedRegisters: RegisterOffset = $usedRegistersExpr

              def deconstruct(out: Registers, offset: RegisterOffset, in: A): Unit = $deconstructorExpr(out, offset, in)
            }
          )
        }
    }
  }

  // ============ Structural Type Support (JVM only) ============

  private def isStructuralType(tpe: TypeRepr): Boolean = {
    val dealiased = tpe.dealias
    dealiased match {
      case Refinement(_, _, _)     => true
      case AndType(left, right)    => isStructuralType(left) || isStructuralType(right)
      case AnnotatedType(inner, _) => isStructuralType(inner)
      case _                       => false
    }
  }

  private def isUnionOfStructuralTypes(tpe: TypeRepr): Boolean =
    tpe.dealias match {
      case OrType(left, right) =>
        (isStructuralType(left) || isUnionOfStructuralTypes(left)) &&
        (isStructuralType(right) || isUnionOfStructuralTypes(right))
      case _ => false
    }

  private def getStructuralMembers(tpe: TypeRepr): List[(String, TypeRepr)] = {
    def collectMembers(t: TypeRepr): List[(String, TypeRepr)] = {
      val dealiased = t.dealias
      dealiased match {
        case Refinement(parent, name, info) =>
          val memberType = info match {
            case MethodType(Nil, Nil, returnType)            => returnType
            case MethodType(_, params, _) if params.nonEmpty =>
              fail(s"Structural type member '$name' has parameters, which is not supported.")
            case ByNameType(underlying) => underlying
            case other                  => other
          }
          (name, memberType) :: collectMembers(parent)
        case AndType(left, right) =>
          collectMembers(left) ++ collectMembers(right)
        case AnnotatedType(inner, _) =>
          collectMembers(inner)
        case _ => Nil
      }
    }

    val members = collectMembers(tpe)
    val grouped = members.groupBy(_._1)
    grouped.foreach { case (name, occurrences) =>
      if (occurrences.size > 1) {
        val types = occurrences.map(_._2.show).distinct
        if (types.size > 1) {
          fail(s"Conflicting types for member '$name' in structural type: ${types.mkString(", ")}")
        }
      }
    }
    members.distinctBy(_._1).sortBy(_._1)
  }

  private def getAllUnionTypesForStructural(tpe: TypeRepr): List[TypeRepr] =
    tpe.dealias match {
      case OrType(left, right) =>
        getAllUnionTypesForStructural(left) ++ getAllUnionTypesForStructural(right)
      case other => List(other)
    }

  private def structuralRegisterKind(tpe: TypeRepr): String =
    tpe.dealias match {
      case t if t =:= booleanTpe => "boolean"
      case t if t =:= byteTpe    => "byte"
      case t if t =:= shortTpe   => "short"
      case t if t =:= intTpe     => "int"
      case t if t =:= longTpe    => "long"
      case t if t =:= floatTpe   => "float"
      case t if t =:= doubleTpe  => "double"
      case t if t =:= charTpe    => "char"
      case _                     => "object"
    }

  private def structuralRegisterOffset(kind: String): Expr[RegisterOffset.RegisterOffset] =
    kind match {
      case "boolean" => '{ RegisterOffset(booleans = 1) }
      case "byte"    => '{ RegisterOffset(bytes = 1) }
      case "short"   => '{ RegisterOffset(shorts = 1) }
      case "int"     => '{ RegisterOffset(ints = 1) }
      case "long"    => '{ RegisterOffset(longs = 1) }
      case "float"   => '{ RegisterOffset(floats = 1) }
      case "double"  => '{ RegisterOffset(doubles = 1) }
      case "char"    => '{ RegisterOffset(chars = 1) }
      case "object"  => '{ RegisterOffset(objects = 1) }
    }

  private case class StructuralFieldForGen(name: String, memberTpe: TypeRepr, kind: String, index: Int)

  private def deriveStructuralRecordBindingSimple[A: Type](tpe: TypeRepr): Expr[Any] = {
    if (!Platform.supportsReflection) {
      fail(
        s"""Cannot derive Binding for structural type '${tpe.show}' on ${Platform.name}.
           |
           |Structural types require JVM runtime reflection.
           |Use case classes for cross-platform compatibility.""".stripMargin
      )
    }

    val members = getStructuralMembers(tpe)
    if (members.isEmpty) {
      fail(s"Structural type ${tpe.show} has no members. Cannot derive Binding.")
    }

    val fields = members.zipWithIndex.map { case ((name, memberTpe), idx) =>
      StructuralFieldForGen(name, memberTpe, structuralRegisterKind(memberTpe), idx)
    }

    val totalUsedRegistersExpr: Expr[RegisterOffset.RegisterOffset] = {
      var acc: Expr[RegisterOffset.RegisterOffset] = '{ 0L }
      fields.foreach { f =>
        acc = '{ RegisterOffset.add($acc, ${ structuralRegisterOffset(f.kind) }) }
      }
      acc
    }

    val constructorExpr: Expr[Constructor[A]] =
      generateStructuralConstructorWithRealMethods[A](fields, totalUsedRegistersExpr)

    val deconstructorExpr: Expr[Deconstructor[A]] = generateStructuralDeconstructor[A](fields, totalUsedRegistersExpr)

    '{ new Binding.Record[A]($constructorExpr, $deconstructorExpr) }
  }

  private def generateStructuralConstructorWithRealMethods[A: Type](
    fields: Seq[StructuralFieldForGen],
    totalUsedRegistersExpr: Expr[RegisterOffset.RegisterOffset]
  ): Expr[Constructor[A]] = {
    val fieldKindsExpr = Expr(fields.map(_.kind).toArray)

    // Generate anonymous class factory using Symbol.newClass (Scala 3.5+ only)
    val instanceCreatorExpr: Expr[Array[Any] => A] = generateAnonymousClassFactory[A](fields)

    '{
      new Constructor[A] {
        private val _fieldKinds      = $fieldKindsExpr
        private val _instanceCreator = $instanceCreatorExpr

        def usedRegisters: RegisterOffset.RegisterOffset = $totalUsedRegistersExpr

        def construct(in: Registers, baseOffset: RegisterOffset.RegisterOffset): A = {
          val values = new scala.Array[Any](_fieldKinds.length)
          var idx    = 0
          var offset = baseOffset
          val len    = _fieldKinds.length
          while (idx < len) {
            val value: Any = _fieldKinds(idx) match {
              case "boolean" =>
                val v = in.getBoolean(offset)
                offset = RegisterOffset.add(offset, RegisterOffset(booleans = 1))
                v
              case "byte" =>
                val v = in.getByte(offset)
                offset = RegisterOffset.add(offset, RegisterOffset(bytes = 1))
                v
              case "short" =>
                val v = in.getShort(offset)
                offset = RegisterOffset.add(offset, RegisterOffset(shorts = 1))
                v
              case "int" =>
                val v = in.getInt(offset)
                offset = RegisterOffset.add(offset, RegisterOffset(ints = 1))
                v
              case "long" =>
                val v = in.getLong(offset)
                offset = RegisterOffset.add(offset, RegisterOffset(longs = 1))
                v
              case "float" =>
                val v = in.getFloat(offset)
                offset = RegisterOffset.add(offset, RegisterOffset(floats = 1))
                v
              case "double" =>
                val v = in.getDouble(offset)
                offset = RegisterOffset.add(offset, RegisterOffset(doubles = 1))
                v
              case "char" =>
                val v = in.getChar(offset)
                offset = RegisterOffset.add(offset, RegisterOffset(chars = 1))
                v
              case _ =>
                val v = in.getObject(offset)
                offset = RegisterOffset.add(offset, RegisterOffset(objects = 1))
                v
            }
            values(idx) = value
            idx += 1
          }
          _instanceCreator(values)
        }
      }
    }
  }

  /**
   * Generates a factory function that creates anonymous class instances for
   * structural types. Delegates to StructuralBindingMacros which is only
   * available in Scala 3.5+ (via scala-3.5+/ directory).
   */
  private def generateAnonymousClassFactory[A: Type](fields: Seq[StructuralFieldForGen]): Expr[Array[Any] => A] = {
    val fieldInfos = fields.map(f => StructuralBindingMacros.StructuralFieldInfo(f.name, f.memberTpe, f.kind, f.index))
    StructuralBindingMacros.generateAnonymousClassFactory[A](fieldInfos, fail)
  }

  private def generateStructuralDeconstructor[A: Type](
    fields: Seq[StructuralFieldForGen],
    totalUsedRegistersExpr: Expr[RegisterOffset.RegisterOffset]
  ): Expr[Deconstructor[A]] =
    '{
      new Deconstructor[A] {
        def usedRegisters: RegisterOffset.RegisterOffset = $totalUsedRegistersExpr

        def deconstruct(out: Registers, baseOffset: RegisterOffset.RegisterOffset, in: A): Unit = {
          var offset = baseOffset
          ${
            val deconstructorStatements = fields.map { f =>
              val fieldNameExpr = Expr(f.name)
              f.kind match {
                case "boolean" =>
                  '{
                    val method = in.getClass.getMethod($fieldNameExpr)
                    val v      = method.invoke(in).asInstanceOf[java.lang.Boolean].booleanValue
                    out.setBoolean(offset, v)
                    offset = RegisterOffset.add(offset, RegisterOffset(booleans = 1))
                  }
                case "byte" =>
                  '{
                    val method = in.getClass.getMethod($fieldNameExpr)
                    val v      = method.invoke(in).asInstanceOf[java.lang.Byte].byteValue
                    out.setByte(offset, v)
                    offset = RegisterOffset.add(offset, RegisterOffset(bytes = 1))
                  }
                case "short" =>
                  '{
                    val method = in.getClass.getMethod($fieldNameExpr)
                    val v      = method.invoke(in).asInstanceOf[java.lang.Short].shortValue
                    out.setShort(offset, v)
                    offset = RegisterOffset.add(offset, RegisterOffset(shorts = 1))
                  }
                case "int" =>
                  '{
                    val method = in.getClass.getMethod($fieldNameExpr)
                    val v      = method.invoke(in).asInstanceOf[java.lang.Integer].intValue
                    out.setInt(offset, v)
                    offset = RegisterOffset.add(offset, RegisterOffset(ints = 1))
                  }
                case "long" =>
                  '{
                    val method = in.getClass.getMethod($fieldNameExpr)
                    out.setLong(offset, method.invoke(in).asInstanceOf[java.lang.Long].longValue)
                    offset = RegisterOffset.add(offset, RegisterOffset(longs = 1))
                  }
                case "float" =>
                  '{
                    val method = in.getClass.getMethod($fieldNameExpr)
                    out.setFloat(offset, method.invoke(in).asInstanceOf[java.lang.Float].floatValue)
                    offset = RegisterOffset.add(offset, RegisterOffset(floats = 1))
                  }
                case "double" =>
                  '{
                    val method = in.getClass.getMethod($fieldNameExpr)
                    out.setDouble(offset, method.invoke(in).asInstanceOf[java.lang.Double].doubleValue)
                    offset = RegisterOffset.add(offset, RegisterOffset(doubles = 1))
                  }
                case "char" =>
                  '{
                    val method = in.getClass.getMethod($fieldNameExpr)
                    out.setChar(offset, method.invoke(in).asInstanceOf[java.lang.Character].charValue)
                    offset = RegisterOffset.add(offset, RegisterOffset(chars = 1))
                  }
                case _ =>
                  '{
                    val method = in.getClass.getMethod($fieldNameExpr)
                    out.setObject(offset, method.invoke(in))
                    offset = RegisterOffset.add(offset, RegisterOffset(objects = 1))
                  }
              }
            }
            Expr.block(deconstructorStatements.init.toList, deconstructorStatements.last)
          }
        }
      }
    }

  private def deriveStructuralVariantBindingSimple[A: Type](tpe: TypeRepr): Expr[Any] = {
    if (!Platform.supportsReflection) {
      fail(
        s"""Cannot derive Binding for union of structural types '${tpe.show}' on ${Platform.name}.
           |
           |Structural types require JVM runtime reflection.
           |Use sealed traits for cross-platform compatibility.""".stripMargin
      )
    }

    val unionTypes = getAllUnionTypesForStructural(tpe)
    if (unionTypes.size < 2) {
      fail(s"Union type ${tpe.show} needs at least 2 alternatives.")
    }

    case class VariantCase(tpe: TypeRepr, members: List[(String, TypeRepr)], discriminatorNames: Array[String])

    val cases = unionTypes.map { t =>
      val members = getStructuralMembers(t)
      if (members.isEmpty) {
        fail(s"Union alternative ${t.show} has no members.")
      }
      VariantCase(t, members, members.map(_._1).toArray)
    }

    val otherCasesMembers = cases.map(_.members.map(_._1).toSet)
    cases.zipWithIndex.foreach { case (c, i) =>
      val myMembers     = c.members.map(_._1).toSet
      val otherMembers  = otherCasesMembers.zipWithIndex.filter(_._2 != i).flatMap(_._1).toSet
      val uniqueMembers = myMembers.diff(otherMembers)
      if (uniqueMembers.isEmpty && cases.size > 1) {
        val otherCasesStr = otherCasesMembers.zipWithIndex
          .filter(_._2 != i)
          .map { case (members, idx) => s"Case $idx: {${members.mkString(", ")}}" }
          .mkString("; ")
        fail(
          s"""Cannot discriminate union alternative ${c.tpe.show}.
             |Each union alternative must have at least one unique member name.
             |Members: {${myMembers.mkString(", ")}}
             |Other cases: $otherCasesStr""".stripMargin
        )
      }
    }

    // Helper to check if object has all named methods - inlined into generated code
    def hasAllMethodsExpr(objExpr: Expr[AnyRef], names: Array[String]): Expr[Boolean] = {
      // Use Varargs to construct a proper Seq expression, then convert to Array
      val nameExprs: Seq[Expr[String]]    = names.toSeq.map(n => Expr(n))
      val namesSeqExpr: Expr[Seq[String]] = Varargs(nameExprs)
      '{
        val obj   = $objExpr
        val names = $namesSeqExpr.toArray
        if (obj == null) false
        else {
          val cls = obj.getClass
          var i   = 0
          var ok  = true
          while (ok && i < names.length) {
            try { cls.getMethod(names(i)) }
            catch { case _: NoSuchMethodException => ok = false }
            i += 1
          }
          ok
        }
      }
    }

    val discriminatorExpr = '{
      new Discriminator[A] {
        def discriminate(a: A): Int = ${
          val casesWithIndex = cases.zipWithIndex.map { case (c, idx) =>
            (c.discriminatorNames, Expr(idx))
          }
          casesWithIndex.foldRight('{ -1 }: Expr[Int]) { case ((names, idxExpr), elseExpr) =>
            val checkExpr = hasAllMethodsExpr('{ a.asInstanceOf[AnyRef] }, names)
            '{ if ($checkExpr) $idxExpr else $elseExpr }
          }
        }
      }
    }

    val matcherExprs: List[Expr[Matcher[?]]] = cases.map { c =>
      c.tpe.asType match {
        case '[t] =>
          val names = c.discriminatorNames
          '{
            new Matcher[t] {
              def downcastOrNull(any: Any): t =
                if (any == null) null.asInstanceOf[t]
                else if (${ hasAllMethodsExpr('{ any.asInstanceOf[AnyRef] }, names) })
                  any.asInstanceOf[t]
                else null.asInstanceOf[t]
            }
          }
      }
    }

    val matchersExpr = matcherExprs match {
      case Nil             => fail("No matchers generated")
      case m :: Nil        => '{ Matchers($m.asInstanceOf[Matcher[? <: A]]) }
      case m1 :: m2 :: Nil => '{ Matchers($m1.asInstanceOf[Matcher[? <: A]], $m2.asInstanceOf[Matcher[? <: A]]) }
      case all             =>
        val varargs = Varargs(all.map(m => '{ $m.asInstanceOf[Matcher[? <: A]] }))
        '{ Matchers($varargs*) }
    }

    '{ new Binding.Variant[A]($discriminatorExpr, $matchersExpr) }
  }
}
