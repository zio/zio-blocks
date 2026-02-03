package zio.blocks.schema.binding

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

trait BindingCompanionVersionSpecific {

  /**
   * Derives a [[Binding]] for type `A` at compile time.
   *
   * Supports:
   *   - Primitive types (Int, String, Boolean, etc.)
   *   - Case classes (derives [[Binding.Record]])
   *   - Sealed traits/enums (derives [[Binding.Variant]])
   *   - Option, Either, and their subtypes
   *   - [[zio.blocks.schema.DynamicValue]]
   *
   * For sequence types (List, Vector, etc.) and map types, use the overloads
   * that take type constructors: `Binding.of[List]`, `Binding.of[Map]`.
   *
   * @tparam A
   *   the type to derive a binding for
   * @return
   *   the derived binding
   */
  def of[A]: Binding[_, A] = macro BindingCompanionVersionSpecificMacros.ofImpl[A]

  /**
   * Creates a [[Binding.Seq]] for a sequence type constructor. Uses implicit
   * [[SeqConstructor]] and [[SeqDeconstructor]] instances. The resulting
   * binding has element type `Nothing` which upcasts via covariance.
   *
   * @tparam F
   *   the sequence type constructor (e.g., List, Vector)
   */
  def of[F[_]](implicit sc: SeqConstructor[F], sd: SeqDeconstructor[F]): Binding.Seq[F, Nothing] =
    new Binding.Seq(sc, sd)

  /**
   * Creates a [[Binding.Map]] for a map type constructor. Uses implicit
   * [[MapConstructor]] and [[MapDeconstructor]] instances.
   *
   * @tparam M
   *   the map type constructor (e.g., Map)
   */
  def of[M[_, _]](implicit mc: MapConstructor[M], md: MapDeconstructor[M]): Binding.Map[M, Nothing, Nothing] =
    new Binding.Map(mc, md)
}

object BindingCompanionVersionSpecificMacros {
  def ofImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[Binding[_, A]] =
    new BindingMacroImpl[c.type](c).of[A]
}

private class BindingMacroImpl[C <: blackbox.Context](val c: C) {
  import c.universe._

  private val intTpe          = typeOf[Int]
  private val floatTpe        = typeOf[Float]
  private val longTpe         = typeOf[Long]
  private val doubleTpe       = typeOf[Double]
  private val booleanTpe      = typeOf[Boolean]
  private val byteTpe         = typeOf[Byte]
  private val charTpe         = typeOf[Char]
  private val shortTpe        = typeOf[Short]
  private val unitTpe         = typeOf[Unit]
  private val stringTpe       = typeOf[String]
  private val arrayTpe        = typeOf[Array[_]].typeConstructor
  private val optionTpe       = typeOf[Option[_]].typeConstructor
  private val someTpe         = typeOf[Some[_]].typeConstructor
  private val noneTpe         = typeOf[None.type]
  private val eitherTpe       = typeOf[Either[_, _]].typeConstructor
  private val leftTpe         = typeOf[Left[_, _]].typeConstructor
  private val rightTpe        = typeOf[Right[_, _]].typeConstructor
  private val listTpe         = typeOf[List[_]].typeConstructor
  private val vectorTpe       = typeOf[Vector[_]].typeConstructor
  private val setTpe          = typeOf[Set[_]].typeConstructor
  private val indexedSeqTpe   = typeOf[IndexedSeq[_]].typeConstructor
  private val seqTpe          = typeOf[collection.immutable.Seq[_]].typeConstructor
  private val chunkTpe        = typeOf[zio.blocks.chunk.Chunk[_]].typeConstructor
  private val arraySeqTpe     = typeOf[scala.collection.immutable.ArraySeq[_]].typeConstructor
  private val mapTpe          = typeOf[Map[_, _]].typeConstructor
  private val dynamicValueTpe = typeOf[zio.blocks.schema.DynamicValue]

  private def fail(msg: String): Nothing = c.abort(c.enclosingPosition, msg)

  private def isSealedTraitOrAbstractClass(tpe: Type): Boolean = {
    val symbol = tpe.typeSymbol
    symbol.isClass && symbol.asClass.isSealed && (symbol.asClass.isAbstract || symbol.asClass.isTrait)
  }

  private def isNonAbstractScalaClass(tpe: Type): Boolean = {
    val symbol = tpe.typeSymbol
    symbol.isClass && !symbol.asClass.isAbstract && !symbol.asClass.isTrait && !symbol.asClass.isJava
  }

  private def isEnumOrModuleValue(tpe: Type): Boolean = {
    val symbol = tpe.typeSymbol
    symbol.isModuleClass || (symbol.isClass && symbol.asClass.isModuleClass)
  }

  private def typeArgs(tpe: Type): List[Type] = tpe match {
    case TypeRef(_, _, args) => args
    case _                   => Nil
  }

  private def directSubTypes(tpe: Type): List[Type] =
    zio.blocks.schema.CommonMacroOps.directSubTypes(c)(tpe)

  private def isIterator(tpe: Type): Boolean = tpe <:< typeOf[Iterator[_]]

  // Structural type detection for Scala 2
  // Structural types appear as RefinedType with member declarations
  // Also handles intersection types (`with`) which may have empty decls but structural parents
  private def isStructuralType(tpe: Type): Boolean = {
    val dealiased = tpe.dealias
    dealiased match {
      case RefinedType(parents, decls) =>
        // Case 1: Has own member declarations
        val hasOwnDecls = decls.exists {
          case m: MethodSymbol => m.paramLists.flatten.isEmpty && !m.isConstructor
          case _               => false
        }
        // Case 2: Intersection type with structural parents
        val hasStructuralParent = parents.exists(p =>
          !(p =:= typeOf[AnyRef] || p =:= typeOf[Any] || p =:= typeOf[Object]) && isStructuralType(p)
        )
        hasOwnDecls || hasStructuralParent
      case _ => false
    }
  }

  // Get structural members from a type (supports intersection via `with`)
  private def getStructuralMembers(tpe: Type): List[(String, Type)] = {
    def collectMembers(t: Type): List[(String, Type)] = {
      val dealiased = t.dealias
      dealiased match {
        case RefinedType(parents, decls) =>
          val memberDecls = decls.toList.collect {
            case m: MethodSymbol if m.paramLists.flatten.isEmpty && !m.isConstructor =>
              (m.name.decodedName.toString, m.returnType.asSeenFrom(t, t.typeSymbol))
          }
          val parentMembers = parents.flatMap(collectMembers)
          memberDecls ++ parentMembers
        case _ => Nil
      }
    }

    val members = collectMembers(tpe)
    val grouped = members.groupBy(_._1)
    // Validate that members with the same name have compatible types
    grouped.foreach {
      case (name, occurrences) if occurrences.size > 1 =>
        val types = occurrences.map(_._2).distinct
        if (types.size > 1 && !types.tail.forall(_ =:= types.head)) {
          fail(s"Conflicting types for member '$name' in structural type: ${types.map(_.toString).mkString(", ")}")
        }
      case _ => // single occurrence, no conflict possible
    }
    // Deduplicate by name, keeping first occurrence, then sort alphabetically
    grouped.map(_._2.head).toList.sortBy(_._1)
  }

  private def structuralRegisterKind(tpe: Type): String = {
    val dealiased = dealiasOnDemand(tpe)
    if (dealiased =:= booleanTpe) "boolean"
    else if (dealiased =:= byteTpe) "byte"
    else if (dealiased =:= shortTpe) "short"
    else if (dealiased =:= intTpe) "int"
    else if (dealiased =:= longTpe) "long"
    else if (dealiased =:= floatTpe) "float"
    else if (dealiased =:= doubleTpe) "double"
    else if (dealiased =:= charTpe) "char"
    else "object"
  }

  private def isTypeRef(tpe: Type): Boolean = tpe match {
    case TypeRef(_, sym, Nil) =>
      sym.isType && sym.asType.isAliasType
    case _ => false
  }

  private def typeRefDealias(tpe: Type): Type = tpe.dealias

  private def isZioPreludeNewtypeImpl(tpe: Type): Boolean = tpe match {
    case TypeRef(compTpe, typeSym, Nil) if typeSym.name.toString == "Type" =>
      compTpe.baseClasses.exists { cls =>
        val fullName = cls.fullName
        fullName == "zio.prelude.Newtype" || fullName == "zio.prelude.Subtype"
      }
    case _ => false
  }

  private def isZioPreludeNewtype(tpe: Type): Boolean =
    isZioPreludeNewtypeImpl(tpe) || isZioPreludeNewtypeImpl(tpe.dealias)

  private def zioPreludeNewtypeDealias(tpe: Type): Type = {
    val t = if (isZioPreludeNewtypeImpl(tpe)) tpe else tpe.dealias
    t match {
      case TypeRef(compTpe, _, _) =>
        val newtypeOpt = compTpe.baseClasses.find { cls =>
          val fullName = cls.fullName
          fullName == "zio.prelude.Newtype" || fullName == "zio.prelude.Subtype"
        }
        newtypeOpt match {
          case Some(cls) => compTpe.baseType(cls).typeArgs.head.dealias
          case _         => fail(s"Cannot dealias zio-prelude newtype: $tpe")
        }
      case _ => fail(s"Cannot dealias zio-prelude newtype: $tpe")
    }
  }

  private def zioPreludeNewtypeCompanion(tpe: Type): Symbol = tpe match {
    case TypeRef(compTpe, _, _) => compTpe.typeSymbol.companion
    case _                      => fail(s"Cannot get companion for zio-prelude newtype: $tpe")
  }

  private def dealiasOnDemand(tpe: Type): Type =
    if (isZioPreludeNewtype(tpe)) zioPreludeNewtypeDealias(tpe)
    else if (isTypeRef(tpe)) typeRefDealias(tpe)
    else tpe

  private case class SmartConstructorInfo(
    companionSymbol: Symbol,
    applyMethod: MethodSymbol,
    underlyingType: Type,
    errorType: Type,
    unwrapFieldName: TermName
  )

  private def findSmartConstructor(tpe: Type): Option[SmartConstructorInfo] = {
    val classSymbol = tpe.typeSymbol
    if (!classSymbol.isClass) return None
    val cls = classSymbol.asClass
    if (cls.isAbstract || cls.isTrait) return None

    val constructor = cls.primaryConstructor
    if (!constructor.isMethod) return None
    val paramLists = constructor.asMethod.paramLists
    val allParams  = paramLists.flatten

    if (allParams.size != 1) return None

    val fieldSymbol    = allParams.head
    val fieldName      = fieldSymbol.name.toTermName
    val underlyingType = tpe.decl(fieldSymbol.name).typeSignatureIn(tpe).finalResultType

    val companion = cls.companion
    if (companion == NoSymbol) return None

    val applyMethods = companion.typeSignature.decls.filter(_.name.decodedName.toString == "apply")
    val eitherTpe    = typeOf[Either[_, _]].typeConstructor

    applyMethods.find { method =>
      if (!method.isMethod) false
      else {
        val m          = method.asMethod
        val mParamList = m.paramLists.flatten
        if (mParamList.size != 1) false
        else {
          val paramType  = mParamList.head.typeSignature.dealias
          val returnType = m.returnType.dealias
          if (!(paramType =:= underlyingType)) false
          else {
            returnType match {
              case TypeRef(_, sym, List(_, rightType)) if sym == eitherTpe.typeSymbol =>
                rightType.dealias =:= tpe
              case _ => false
            }
          }
        }
      }
    } match {
      case Some(method) =>
        val m          = method.asMethod
        val returnType = m.returnType.dealias
        val errorType  = returnType match {
          case TypeRef(_, _, List(errTpe, _)) => errTpe.dealias
          case _                              => return None
        }
        Some(
          SmartConstructorInfo(
            companion,
            m,
            underlyingType,
            errorType,
            fieldName
          )
        )
      case None => None
    }
  }

  def of[A: c.WeakTypeTag]: c.Expr[Binding[_, A]] = {
    val tpe = weakTypeOf[A].dealias
    deriveBinding(tpe).asInstanceOf[c.Expr[Binding[_, A]]]
  }

  private def deriveBinding(tpe: Type): c.Expr[Any] =
    if (tpe =:= intTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.int")
    else if (tpe =:= floatTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.float")
    else if (tpe =:= longTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.long")
    else if (tpe =:= doubleTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.double")
    else if (tpe =:= booleanTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.boolean")
    else if (tpe =:= byteTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.byte")
    else if (tpe =:= charTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.char")
    else if (tpe =:= shortTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.short")
    else if (tpe =:= unitTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.unit")
    else if (tpe =:= stringTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.string")
    else if (tpe <:< typeOf[BigInt]) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.bigInt")
    else if (tpe <:< typeOf[BigDecimal]) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.bigDecimal")
    else if (tpe <:< typeOf[java.time.DayOfWeek])
      c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.dayOfWeek")
    else if (tpe <:< typeOf[java.time.Duration])
      c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.duration")
    else if (tpe <:< typeOf[java.time.Instant])
      c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.instant")
    else if (tpe <:< typeOf[java.time.LocalDate])
      c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.localDate")
    else if (tpe <:< typeOf[java.time.LocalDateTime])
      c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.localDateTime")
    else if (tpe <:< typeOf[java.time.LocalTime])
      c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.localTime")
    else if (tpe <:< typeOf[java.time.Month]) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.month")
    else if (tpe <:< typeOf[java.time.MonthDay])
      c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.monthDay")
    else if (tpe <:< typeOf[java.time.OffsetDateTime])
      c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.offsetDateTime")
    else if (tpe <:< typeOf[java.time.OffsetTime])
      c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.offsetTime")
    else if (tpe <:< typeOf[java.time.Period]) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.period")
    else if (tpe <:< typeOf[java.time.Year]) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.year")
    else if (tpe <:< typeOf[java.time.YearMonth])
      c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.yearMonth")
    else if (tpe <:< typeOf[java.time.ZoneOffset])
      c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.zoneOffset")
    else if (tpe <:< typeOf[java.time.ZoneId]) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.zoneId")
    else if (tpe <:< typeOf[java.time.ZonedDateTime])
      c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.zonedDateTime")
    else if (tpe <:< typeOf[java.util.Currency])
      c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.currency")
    else if (tpe <:< typeOf[java.util.UUID]) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.uuid")
    else if (tpe <:< dynamicValueTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Dynamic()")
    else if (tpe =:= noneTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.none")
    else if (tpe.typeConstructor =:= someTpe) deriveSomeBinding(tpe)
    else if (tpe.typeConstructor =:= optionTpe) deriveOptionBinding(tpe)
    else if (tpe.typeConstructor =:= leftTpe) deriveLeftBinding(tpe)
    else if (tpe.typeConstructor =:= rightTpe) deriveRightBinding(tpe)
    else if (tpe.typeConstructor =:= eitherTpe) deriveEitherBinding(tpe)
    else if (tpe.typeConstructor =:= mapTpe)
      fail(s"Use Binding.of[Map] for map types, not Binding.of[$tpe]")
    else if (tpe.typeConstructor =:= chunkTpe)
      fail(s"Use Binding.of[Chunk] for Chunk types, not Binding.of[$tpe]")
    else if (tpe.typeConstructor =:= arraySeqTpe)
      fail(s"Use Binding.of[ArraySeq] for ArraySeq types, not Binding.of[$tpe]")
    else if (tpe.typeConstructor =:= listTpe)
      fail(s"Use Binding.of[List] for List types, not Binding.of[$tpe]")
    else if (tpe.typeConstructor =:= vectorTpe)
      fail(s"Use Binding.of[Vector] for Vector types, not Binding.of[$tpe]")
    else if (tpe.typeConstructor =:= setTpe)
      fail(s"Use Binding.of[Set] for Set types, not Binding.of[$tpe]")
    else if (tpe.typeConstructor =:= indexedSeqTpe)
      fail(s"Use Binding.of[IndexedSeq] for IndexedSeq types, not Binding.of[$tpe]")
    else if (tpe.typeConstructor =:= seqTpe)
      fail(s"Use Binding.of[Seq] for Seq types, not Binding.of[$tpe]")
    else if (tpe.typeConstructor =:= arrayTpe)
      fail(s"Use Binding.of[Array] for Array types, not Binding.of[$tpe]")
    else if (isIterator(tpe))
      fail(s"Cannot derive Binding for Iterator types: $tpe. Iterators are not round-trip serializable.")
    else if (isEnumOrModuleValue(tpe)) deriveEnumOrModuleValueBinding(tpe)
    else if (isSealedTraitOrAbstractClass(tpe)) deriveSealedTraitBinding(tpe)
    else if (isStructuralType(tpe)) deriveStructuralRecordBinding(tpe)
    else if (isNonAbstractScalaClass(tpe)) {
      findSmartConstructor(tpe) match {
        case Some(info) => deriveSmartConstructorBinding(tpe, info)
        case None       => deriveRecordBinding(tpe)
      }
    } else if (isZioPreludeNewtype(tpe)) deriveZioPreludeNewtypeBinding(tpe)
    else if (isTypeRef(tpe)) deriveBinding(typeRefDealias(tpe))
    else fail(s"Cannot derive Binding for type: ${tpe}")

  private def deriveSomeBinding(tpe: Type): c.Expr[Any] = {
    val elemTpe       = typeArgs(tpe).head
    val dealiasedElem = dealiasOnDemand(elemTpe)
    if (dealiasedElem <:< intTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.someInt")
    else if (dealiasedElem <:< longTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.someLong")
    else if (dealiasedElem <:< floatTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.someFloat")
    else if (dealiasedElem <:< doubleTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.someDouble")
    else if (dealiasedElem <:< booleanTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.someBoolean")
    else if (dealiasedElem <:< byteTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.someByte")
    else if (dealiasedElem <:< shortTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.someShort")
    else if (dealiasedElem <:< charTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.someChar")
    else if (dealiasedElem <:< unitTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.someUnit")
    else c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.some[$elemTpe]")
  }

  private def deriveOptionBinding(tpe: Type): c.Expr[Any] = {
    val elemTpe = typeArgs(tpe).head
    c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Variant.option[$elemTpe]")
  }

  private def deriveLeftBinding(tpe: Type): c.Expr[Any] = {
    val args       = typeArgs(tpe)
    val aTpe       = args(0)
    val bTpe       = args(1)
    val dealiasedA = dealiasOnDemand(aTpe)
    if (dealiasedA <:< intTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.leftInt[$bTpe]")
    else if (dealiasedA <:< longTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.leftLong[$bTpe]")
    else if (dealiasedA <:< floatTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.leftFloat[$bTpe]")
    else if (dealiasedA <:< doubleTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.leftDouble[$bTpe]")
    else if (dealiasedA <:< booleanTpe)
      c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.leftBoolean[$bTpe]")
    else if (dealiasedA <:< byteTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.leftByte[$bTpe]")
    else if (dealiasedA <:< shortTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.leftShort[$bTpe]")
    else if (dealiasedA <:< charTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.leftChar[$bTpe]")
    else if (dealiasedA <:< unitTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.leftUnit[$bTpe]")
    else c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.left[$aTpe, $bTpe]")
  }

  private def deriveRightBinding(tpe: Type): c.Expr[Any] = {
    val args       = typeArgs(tpe)
    val aTpe       = args(0)
    val bTpe       = args(1)
    val dealiasedB = dealiasOnDemand(bTpe)
    if (dealiasedB <:< intTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.rightInt[$aTpe]")
    else if (dealiasedB <:< longTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.rightLong[$aTpe]")
    else if (dealiasedB <:< floatTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.rightFloat[$aTpe]")
    else if (dealiasedB <:< doubleTpe)
      c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.rightDouble[$aTpe]")
    else if (dealiasedB <:< booleanTpe)
      c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.rightBoolean[$aTpe]")
    else if (dealiasedB <:< byteTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.rightByte[$aTpe]")
    else if (dealiasedB <:< shortTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.rightShort[$aTpe]")
    else if (dealiasedB <:< charTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.rightChar[$aTpe]")
    else if (dealiasedB <:< unitTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.rightUnit[$aTpe]")
    else c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.right[$aTpe, $bTpe]")
  }

  private def deriveEitherBinding(tpe: Type): c.Expr[Any] = {
    val args = typeArgs(tpe)
    c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Variant.either[${args(0)}, ${args(1)}]")
  }

  private def deriveEnumOrModuleValueBinding(tpe: Type): c.Expr[Any] = {
    val moduleSymbol = tpe.typeSymbol.asClass.module
    c.Expr[Any](
      q"""
      new _root_.zio.blocks.schema.binding.Binding.Record[$tpe](
        constructor = new _root_.zio.blocks.schema.binding.ConstantConstructor($moduleSymbol),
        deconstructor = new _root_.zio.blocks.schema.binding.ConstantDeconstructor
      )
      """
    )
  }

  private def deriveSealedTraitBinding(tpe: Type): c.Expr[Any] = {
    val subtypes = directSubTypes(tpe)
    if (subtypes.isEmpty) fail(s"Cannot find sub-types for ADT base '${tpe}'.")

    val matcherCases = subtypes.map { sTpe =>
      q"""
      new _root_.zio.blocks.schema.binding.Matcher[$sTpe] {
        def downcastOrNull(a: Any): $sTpe = a match {
          case x: $sTpe @_root_.scala.unchecked => x
          case _                                => null.asInstanceOf[$sTpe]
        }
      }
      """
    }

    val discriminateCases = subtypes.zipWithIndex.map { case (sTpe, idx) =>
      cq"_: $sTpe @_root_.scala.unchecked => $idx"
    }

    c.Expr[Any](
      q"""
      new _root_.zio.blocks.schema.binding.Binding.Variant[$tpe](
        discriminator = new _root_.zio.blocks.schema.binding.Discriminator[$tpe] {
          def discriminate(a: $tpe): Int = (a: @_root_.scala.unchecked) match { case ..$discriminateCases }
        },
        matchers = _root_.zio.blocks.schema.binding.Matchers(..$matcherCases)
      )
      """
    )
  }

  private def deriveSmartConstructorBinding(tpe: Type, info: SmartConstructorInfo): c.Expr[Any] = {
    val underlyingType = info.underlyingType
    val fieldName      = info.unwrapFieldName
    val companion      = info.companionSymbol
    val applyMethod    = info.applyMethod
    val errorType      = info.errorType

    val schemaErrorTpe = typeOf[_root_.zio.blocks.schema.SchemaError]
    val isSchemaError  = errorType <:< schemaErrorTpe

    val wrapTree =
      if (isSchemaError) {
        q"""
        (underlying: $underlyingType) => $companion.$applyMethod(underlying) match {
          case _root_.scala.Right(a)  => a
          case _root_.scala.Left(err) => throw err
        }
        """
      } else {
        q"""
        (underlying: $underlyingType) => $companion.$applyMethod(underlying) match {
          case _root_.scala.Right(a)  => a
          case _root_.scala.Left(err) => throw _root_.zio.blocks.schema.SchemaError.validationFailed(err.toString)
        }
        """
      }

    val unwrapTree = q"""(a: $tpe) => a.$fieldName"""

    c.Expr[Any](
      q"""
      new _root_.zio.blocks.schema.binding.Binding.Wrapper[$tpe, $underlyingType](
        wrap = $wrapTree,
        unwrap = $unwrapTree
      )
      """
    )
  }

  private def deriveZioPreludeNewtypeBinding(tpe: Type): c.Expr[Any] = {
    val underlyingType = zioPreludeNewtypeDealias(tpe)
    val companion      = zioPreludeNewtypeCompanion(tpe)

    val wrapMethod   = companion.typeSignature.decl(TermName("wrap"))
    val unwrapMethod = companion.typeSignature.decl(TermName("unwrap"))

    val wrapTree =
      if (wrapMethod != NoSymbol) {
        q"""
        (underlying: $underlyingType) => $companion.wrap(underlying)
        """
      } else {
        q"""
        (underlying: $underlyingType) => underlying.asInstanceOf[$tpe]
        """
      }

    val unwrapTree =
      if (unwrapMethod != NoSymbol) {
        q"""(a: $tpe) => $companion.unwrap(a)"""
      } else {
        q"""(a: $tpe) => a.asInstanceOf[$underlyingType]"""
      }

    c.Expr[Any](
      q"""
      new _root_.zio.blocks.schema.binding.Binding.Wrapper[$tpe, $underlyingType](
        wrap = $wrapTree,
        unwrap = $unwrapTree
      )
      """
    )
  }

  private def deriveRecordBinding(tpe: Type): c.Expr[Any] = {
    val classSymbol = tpe.typeSymbol.asClass
    val constructor = classSymbol.primaryConstructor.asMethod
    val paramLists  = constructor.paramLists

    if (paramLists.isEmpty || paramLists.forall(_.isEmpty)) {
      deriveEmptyRecordBinding(tpe)
    } else {
      // Compute RegisterOffset deltas at macro time using the same formula as RegisterOffset.apply
      def offsetDelta(registerType: String): Long = registerType match {
        case "Boolean" => 0x100000000L // booleans = 1
        case "Byte"    => 0x100000000L // bytes = 1
        case "Short"   => 0x200000000L // shorts = 1 (shifted by 1)
        case "Int"     => 0x400000000L // ints = 1 (shifted by 2)
        case "Long"    => 0x800000000L // longs = 1 (shifted by 3)
        case "Float"   => 0x400000000L // floats = 1 (shifted by 2)
        case "Double"  => 0x800000000L // doubles = 1 (shifted by 3)
        case "Char"    => 0x200000000L // chars = 1 (shifted by 1)
        case "Object"  => 1L           // objects = 1
        case "Unit"    => 0L
        case _         => 1L
      }

      case class FieldInfo(name: TermName, tpe: Type, registerType: String, fieldOffset: Long)

      var currentOffset: Long = 0L
      val fieldLists          = paramLists.map(_.map { param =>
        val fieldTpe     = tpe.decl(param.name).typeSignatureIn(tpe).finalResultType
        val dealiasedTpe = dealiasOnDemand(fieldTpe)
        val registerType =
          if (dealiasedTpe <:< booleanTpe) "Boolean"
          else if (dealiasedTpe <:< byteTpe) "Byte"
          else if (dealiasedTpe <:< shortTpe) "Short"
          else if (dealiasedTpe <:< intTpe) "Int"
          else if (dealiasedTpe <:< longTpe) "Long"
          else if (dealiasedTpe <:< floatTpe) "Float"
          else if (dealiasedTpe <:< doubleTpe) "Double"
          else if (dealiasedTpe <:< charTpe) "Char"
          else if (dealiasedTpe <:< unitTpe) "Unit"
          else "Object"
        val fieldOffset = currentOffset
        currentOffset += offsetDelta(registerType)
        FieldInfo(param.name.toTermName, fieldTpe, registerType, fieldOffset)
      })
      val fields            = fieldLists.flatten
      val usedRegistersLong = currentOffset

      def fieldToArg(f: FieldInfo): Tree = {
        val fieldTpe                 = f.tpe
        val dealiasedTpe             = dealiasOnDemand(fieldTpe)
        val needsCast                = !(fieldTpe =:= dealiasedTpe)
        def maybeCast(t: Tree): Tree = if (needsCast) q"$t.asInstanceOf[$fieldTpe]" else t
        val offsetLit                = Literal(Constant(f.fieldOffset))
        f.registerType match {
          case "Boolean" => maybeCast(q"in.getBoolean(offset + $offsetLit)")
          case "Byte"    => maybeCast(q"in.getByte(offset + $offsetLit)")
          case "Short"   => maybeCast(q"in.getShort(offset + $offsetLit)")
          case "Int"     => maybeCast(q"in.getInt(offset + $offsetLit)")
          case "Long"    => maybeCast(q"in.getLong(offset + $offsetLit)")
          case "Float"   => maybeCast(q"in.getFloat(offset + $offsetLit)")
          case "Double"  => maybeCast(q"in.getDouble(offset + $offsetLit)")
          case "Char"    => maybeCast(q"in.getChar(offset + $offsetLit)")
          case "Unit"    => maybeCast(q"()")
          case _         => q"in.getObject(offset + $offsetLit).asInstanceOf[${f.tpe}]"
        }
      }

      val constructArgss = fieldLists.map(_.map(fieldToArg))
      val constructCall  = q"new $tpe(...$constructArgss)"

      val deconstructStatements = fields.map { f =>
        val fieldName        = f.name
        val fieldTpe         = f.tpe
        val dealiasedTpe     = dealiasOnDemand(fieldTpe)
        val needsCast        = !(fieldTpe =:= dealiasedTpe)
        val fieldValue: Tree =
          if (needsCast) q"in.$fieldName.asInstanceOf[$dealiasedTpe]"
          else q"in.$fieldName"
        val offsetLit = Literal(Constant(f.fieldOffset))
        f.registerType match {
          case "Boolean" => q"out.setBoolean(offset + $offsetLit, $fieldValue)"
          case "Byte"    => q"out.setByte(offset + $offsetLit, $fieldValue)"
          case "Short"   => q"out.setShort(offset + $offsetLit, $fieldValue)"
          case "Int"     => q"out.setInt(offset + $offsetLit, $fieldValue)"
          case "Long"    => q"out.setLong(offset + $offsetLit, $fieldValue)"
          case "Float"   => q"out.setFloat(offset + $offsetLit, $fieldValue)"
          case "Double"  => q"out.setDouble(offset + $offsetLit, $fieldValue)"
          case "Char"    => q"out.setChar(offset + $offsetLit, $fieldValue)"
          case "Unit"    => q"()"
          case _         => q"out.setObject(offset + $offsetLit, in.$fieldName.asInstanceOf[AnyRef])"
        }
      }

      val usedRegistersLit = Literal(Constant(usedRegistersLong))

      c.Expr[Any](
        q"""
        new _root_.zio.blocks.schema.binding.Binding.Record[$tpe](
          constructor = new _root_.zio.blocks.schema.binding.Constructor[$tpe] {
            def usedRegisters: _root_.zio.blocks.schema.binding.RegisterOffset.RegisterOffset = $usedRegistersLit

            def construct(
              in: _root_.zio.blocks.schema.binding.Registers,
              offset: _root_.zio.blocks.schema.binding.RegisterOffset.RegisterOffset
            ): $tpe = $constructCall
          },
          deconstructor = new _root_.zio.blocks.schema.binding.Deconstructor[$tpe] {
            def usedRegisters: _root_.zio.blocks.schema.binding.RegisterOffset.RegisterOffset = $usedRegistersLit

            def deconstruct(
              out: _root_.zio.blocks.schema.binding.Registers,
              offset: _root_.zio.blocks.schema.binding.RegisterOffset.RegisterOffset,
              in: $tpe
            ): Unit = { ..$deconstructStatements }
          }
        )
        """
      )
    }
  }

  private def deriveEmptyRecordBinding(tpe: Type): c.Expr[Any] =
    c.Expr[Any](
      q"""
      new _root_.zio.blocks.schema.binding.Binding.Record[$tpe](
        constructor = new _root_.zio.blocks.schema.binding.Constructor[$tpe] {
          def usedRegisters: _root_.zio.blocks.schema.binding.RegisterOffset.RegisterOffset = 0L

          def construct(
            in: _root_.zio.blocks.schema.binding.Registers,
            offset: _root_.zio.blocks.schema.binding.RegisterOffset.RegisterOffset
          ): $tpe = new $tpe()
        },
        deconstructor = new _root_.zio.blocks.schema.binding.ConstantDeconstructor
      )
      """
    )

  private def deriveStructuralRecordBinding(tpe: Type): c.Expr[Any] = {
    // Check at compile-time if Platform supports reflection
    // This is checked at macro expansion time, which runs on JVM, but we need to ensure
    // the generated code will also run on JVM only
    if (!zio.blocks.schema.Platform.supportsReflection) {
      fail(
        s"""Cannot derive Binding for structural type '$tpe' on ${zio.blocks.schema.Platform.name}.
           |
           |Structural types require JVM runtime reflection.
           |Use case classes for cross-platform compatibility.""".stripMargin
      )
    }

    val members = getStructuralMembers(tpe)
    if (members.isEmpty) {
      fail(s"Structural type $tpe has no members. Cannot derive Binding.")
    }

    case class FieldInfo(name: String, tpe: Type, kind: String, fieldOffset: Long)

    // Compute field offsets using the same formula as RegisterOffset.apply
    def offsetDelta(registerType: String): Long = registerType match {
      case "boolean" => 0x100000000L
      case "byte"    => 0x100000000L
      case "short"   => 0x200000000L
      case "int"     => 0x400000000L
      case "long"    => 0x800000000L
      case "float"   => 0x400000000L
      case "double"  => 0x800000000L
      case "char"    => 0x200000000L
      case "object"  => 1L
    }

    var currentOffset: Long = 0L
    val fields              = members.map { case (name, memberTpe) =>
      val kind        = structuralRegisterKind(memberTpe)
      val fieldOffset = currentOffset
      currentOffset += offsetDelta(kind)
      FieldInfo(name, memberTpe, kind, fieldOffset)
    }
    val usedRegistersLong = currentOffset

    // Generate anonymous class method definitions for the constructor
    val methodDefs = fields.zipWithIndex.map { case (f, idx) =>
      val fieldName  = TermName(f.name)
      val fieldTpe   = f.tpe
      val idxLiteral = Literal(Constant(idx))
      q"def $fieldName: $fieldTpe = values($idxLiteral).asInstanceOf[$fieldTpe]"
    }

    // Generate the values array construction from registers
    val valueReads = fields.map { f =>
      val offsetLit = Literal(Constant(f.fieldOffset))
      f.kind match {
        case "boolean" => q"in.getBoolean(offset + $offsetLit).asInstanceOf[AnyRef]"
        case "byte"    => q"in.getByte(offset + $offsetLit).asInstanceOf[AnyRef]"
        case "short"   => q"in.getShort(offset + $offsetLit).asInstanceOf[AnyRef]"
        case "int"     => q"in.getInt(offset + $offsetLit).asInstanceOf[AnyRef]"
        case "long"    => q"in.getLong(offset + $offsetLit).asInstanceOf[AnyRef]"
        case "float"   => q"in.getFloat(offset + $offsetLit).asInstanceOf[AnyRef]"
        case "double"  => q"in.getDouble(offset + $offsetLit).asInstanceOf[AnyRef]"
        case "char"    => q"in.getChar(offset + $offsetLit).asInstanceOf[AnyRef]"
        case _         => q"in.getObject(offset + $offsetLit)"
      }
    }

    // Generate deconstructor statements using reflection
    val deconstructStatements = fields.map { f =>
      val fieldNameStr = f.name
      val offsetLit    = Literal(Constant(f.fieldOffset))
      f.kind match {
        case "boolean" =>
          q"""
          val method = in.getClass.getMethod($fieldNameStr)
          out.setBoolean(offset + $offsetLit, method.invoke(in).asInstanceOf[java.lang.Boolean].booleanValue)
          """
        case "byte" =>
          q"""
          val method = in.getClass.getMethod($fieldNameStr)
          out.setByte(offset + $offsetLit, method.invoke(in).asInstanceOf[java.lang.Byte].byteValue)
          """
        case "short" =>
          q"""
          val method = in.getClass.getMethod($fieldNameStr)
          out.setShort(offset + $offsetLit, method.invoke(in).asInstanceOf[java.lang.Short].shortValue)
          """
        case "int" =>
          q"""
          val method = in.getClass.getMethod($fieldNameStr)
          out.setInt(offset + $offsetLit, method.invoke(in).asInstanceOf[java.lang.Integer].intValue)
          """
        case "long" =>
          q"""
          val method = in.getClass.getMethod($fieldNameStr)
          out.setLong(offset + $offsetLit, method.invoke(in).asInstanceOf[java.lang.Long].longValue)
          """
        case "float" =>
          q"""
          val method = in.getClass.getMethod($fieldNameStr)
          out.setFloat(offset + $offsetLit, method.invoke(in).asInstanceOf[java.lang.Float].floatValue)
          """
        case "double" =>
          q"""
          val method = in.getClass.getMethod($fieldNameStr)
          out.setDouble(offset + $offsetLit, method.invoke(in).asInstanceOf[java.lang.Double].doubleValue)
          """
        case "char" =>
          q"""
          val method = in.getClass.getMethod($fieldNameStr)
          out.setChar(offset + $offsetLit, method.invoke(in).asInstanceOf[java.lang.Character].charValue)
          """
        case _ =>
          q"""
          val method = in.getClass.getMethod($fieldNameStr)
          out.setObject(offset + $offsetLit, method.invoke(in))
          """
      }
    }

    val usedRegistersLit = Literal(Constant(usedRegistersLong))

    c.Expr[Any](
      q"""
      new _root_.zio.blocks.schema.binding.Binding.Record[$tpe](
        constructor = new _root_.zio.blocks.schema.binding.Constructor[$tpe] {
          def usedRegisters: _root_.zio.blocks.schema.binding.RegisterOffset.RegisterOffset = $usedRegistersLit

          def construct(
            in: _root_.zio.blocks.schema.binding.Registers,
            offset: _root_.zio.blocks.schema.binding.RegisterOffset.RegisterOffset
          ): $tpe = {
            val values: Array[AnyRef] = Array(..$valueReads)
            new { ..$methodDefs }.asInstanceOf[$tpe]
          }
        },
        deconstructor = new _root_.zio.blocks.schema.binding.Deconstructor[$tpe] {
          def usedRegisters: _root_.zio.blocks.schema.binding.RegisterOffset.RegisterOffset = $usedRegistersLit

          def deconstruct(
            out: _root_.zio.blocks.schema.binding.Registers,
            offset: _root_.zio.blocks.schema.binding.RegisterOffset.RegisterOffset,
            in: $tpe
          ): Unit = {
            ..$deconstructStatements
          }
        }
      )
      """
    )
  }
}
