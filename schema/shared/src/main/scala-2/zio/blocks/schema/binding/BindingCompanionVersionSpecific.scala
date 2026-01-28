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
   *   - Standard collections (List, Vector, Set, Map, etc.)
   *   - [[zio.blocks.schema.DynamicValue]]
   *
   * @tparam A
   *   the type to derive a binding for
   * @return
   *   the derived binding with precise type
   */
  def of[A]: Any = macro BindingCompanionVersionSpecificMacros.ofImpl[A]

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
  def ofImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[Any] =
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

  private def directSubTypes(tpe: Type): List[Type] = {
    val symbol = tpe.typeSymbol
    if (symbol.isClass && symbol.asClass.isSealed) {
      symbol.asClass.knownDirectSubclasses.toList.map { subSymbol =>
        if (subSymbol.isModuleClass) subSymbol.asClass.module.typeSignature
        else if (subSymbol.isClass) {
          val subClass = subSymbol.asClass
          if (subClass.typeParams.isEmpty) subClass.toType
          else {
            val tArgs = typeArgs(tpe)
            if (tArgs.nonEmpty && subClass.typeParams.size == tArgs.size)
              appliedType(subClass.toType.typeConstructor, tArgs)
            else subClass.toType
          }
        } else subSymbol.typeSignature
      }
    } else Nil
  }

  private def isIterator(tpe: Type): Boolean = tpe <:< typeOf[Iterator[_]]

  private def isTypeRef(tpe: Type): Boolean = tpe match {
    case TypeRef(_, sym, Nil) =>
      sym.isType && sym.asType.isAliasType
    case _ => false
  }

  private def typeRefDealias(tpe: Type): Type = tpe.dealias

  private def isZioPreludeNewtype(tpe: Type): Boolean = tpe match {
    case TypeRef(compTpe, typeSym, Nil) if typeSym.name.toString == "Type" =>
      compTpe.baseClasses.exists(_.fullName == "zio.prelude.Newtype")
    case _ => false
  }

  private def zioPreludeNewtypeDealias(tpe: Type): Type = tpe match {
    case TypeRef(compTpe, _, _) =>
      compTpe.baseClasses.find(_.fullName == "zio.prelude.Newtype") match {
        case Some(cls) => compTpe.baseType(cls).typeArgs.head.dealias
        case _         => fail(s"Cannot dealias zio-prelude newtype: $tpe")
      }
    case _ => fail(s"Cannot dealias zio-prelude newtype: $tpe")
  }

  private def zioPreludeNewtypeCompanion(tpe: Type): Symbol = tpe match {
    case TypeRef(compTpe, _, _) => compTpe.typeSymbol.companion
    case _                      => fail(s"Cannot get companion for zio-prelude newtype: $tpe")
  }

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
        val errorType = returnType match {
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

  def of[A: c.WeakTypeTag]: c.Expr[Any] = {
    val tpe = weakTypeOf[A].dealias
    deriveBinding(tpe)
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
    else if (tpe <:< typeOf[java.time.ZoneId]) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.zoneId")
    else if (tpe <:< typeOf[java.time.ZoneOffset])
      c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Primitive.zoneOffset")
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
    else if (tpe.typeConstructor =:= mapTpe) deriveMapBinding(tpe)
    else if (tpe.typeConstructor =:= chunkTpe)
      deriveSeqBinding(
        tpe,
        q"_root_.zio.blocks.schema.binding.SeqConstructor.chunkConstructor",
        q"_root_.zio.blocks.schema.binding.SeqDeconstructor.chunkDeconstructor"
      )
    else if (tpe.typeConstructor =:= arraySeqTpe)
      deriveSeqBinding(
        tpe,
        q"_root_.zio.blocks.schema.binding.SeqConstructor.arraySeqConstructor",
        q"_root_.zio.blocks.schema.binding.SeqDeconstructor.arraySeqDeconstructor"
      )
    else if (tpe.typeConstructor =:= listTpe)
      deriveSeqBinding(
        tpe,
        q"_root_.zio.blocks.schema.binding.SeqConstructor.listConstructor",
        q"_root_.zio.blocks.schema.binding.SeqDeconstructor.listDeconstructor"
      )
    else if (tpe.typeConstructor =:= vectorTpe)
      deriveSeqBinding(
        tpe,
        q"_root_.zio.blocks.schema.binding.SeqConstructor.vectorConstructor",
        q"_root_.zio.blocks.schema.binding.SeqDeconstructor.vectorDeconstructor"
      )
    else if (tpe.typeConstructor =:= setTpe)
      deriveSeqBinding(
        tpe,
        q"_root_.zio.blocks.schema.binding.SeqConstructor.setConstructor",
        q"_root_.zio.blocks.schema.binding.SeqDeconstructor.setDeconstructor"
      )
    else if (tpe.typeConstructor =:= indexedSeqTpe)
      deriveSeqBinding(
        tpe,
        q"_root_.zio.blocks.schema.binding.SeqConstructor.indexedSeqConstructor",
        q"_root_.zio.blocks.schema.binding.SeqDeconstructor.indexedSeqDeconstructor"
      )
    else if (tpe.typeConstructor =:= seqTpe)
      deriveSeqBinding(
        tpe,
        q"_root_.zio.blocks.schema.binding.SeqConstructor.seqConstructor",
        q"_root_.zio.blocks.schema.binding.SeqDeconstructor.seqDeconstructor"
      )
    else if (tpe.typeConstructor =:= arrayTpe) deriveArrayBinding(tpe)
    else if (isIterator(tpe)) fail(s"Cannot derive Binding for Iterator types: $tpe. Iterators are not round-trip serializable.")
    else if (isEnumOrModuleValue(tpe)) deriveEnumOrModuleValueBinding(tpe)
    else if (isSealedTraitOrAbstractClass(tpe)) deriveSealedTraitBinding(tpe)
    else if (isNonAbstractScalaClass(tpe)) {
      findSmartConstructor(tpe) match {
        case Some(info) => deriveSmartConstructorBinding(tpe, info)
        case None       => deriveRecordBinding(tpe)
      }
    } else if (isZioPreludeNewtype(tpe)) deriveZioPreludeNewtypeBinding(tpe)
    else if (isTypeRef(tpe)) deriveBinding(typeRefDealias(tpe))
    else fail(s"Cannot derive Binding for type: ${tpe}")

  private def deriveSomeBinding(tpe: Type): c.Expr[Any] = {
    val elemTpe = typeArgs(tpe).head
    if (elemTpe =:= intTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.someInt")
    else if (elemTpe =:= longTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.someLong")
    else if (elemTpe =:= floatTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.someFloat")
    else if (elemTpe =:= doubleTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.someDouble")
    else if (elemTpe =:= booleanTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.someBoolean")
    else if (elemTpe =:= byteTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.someByte")
    else if (elemTpe =:= shortTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.someShort")
    else if (elemTpe =:= charTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.someChar")
    else if (elemTpe =:= unitTpe) c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.someUnit")
    else c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.some[$elemTpe]")
  }

  private def deriveOptionBinding(tpe: Type): c.Expr[Any] = {
    val elemTpe = typeArgs(tpe).head
    c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Variant.option[$elemTpe]")
  }

  private def deriveLeftBinding(tpe: Type): c.Expr[Any] = {
    val args = typeArgs(tpe)
    c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.left[${args(0)}, ${args(1)}]")
  }

  private def deriveRightBinding(tpe: Type): c.Expr[Any] = {
    val args = typeArgs(tpe)
    c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Record.right[${args(0)}, ${args(1)}]")
  }

  private def deriveEitherBinding(tpe: Type): c.Expr[Any] = {
    val args = typeArgs(tpe)
    c.Expr[Any](q"_root_.zio.blocks.schema.binding.Binding.Variant.either[${args(0)}, ${args(1)}]")
  }

  private def deriveMapBinding(tpe: Type): c.Expr[Any] = {
    val args = typeArgs(tpe)
    c.Expr[Any](
      q"""new _root_.zio.blocks.schema.binding.Binding.Map[Map, ${args(0)}, ${args(1)}](
        _root_.zio.blocks.schema.binding.MapConstructor.map,
        _root_.zio.blocks.schema.binding.MapDeconstructor.map
      )"""
    )
  }

  private def deriveSeqBinding(tpe: Type, constructor: Tree, deconstructor: Tree): c.Expr[Any] = {
    val elemTpe = typeArgs(tpe).headOption.getOrElse(typeOf[Nothing])
    val collTpe = tpe.typeConstructor
    c.Expr[Any](q"new _root_.zio.blocks.schema.binding.Binding.Seq[$collTpe, $elemTpe]($constructor, $deconstructor)")
  }

  private def deriveArrayBinding(tpe: Type): c.Expr[Any] = {
    val elemTpe = typeArgs(tpe).head
    c.Expr[Any](
      q"""
      {
        val ct = implicitly[_root_.scala.reflect.ClassTag[$elemTpe]]
        new _root_.zio.blocks.schema.binding.Binding.Seq[Array, $elemTpe](
          new _root_.zio.blocks.schema.binding.SeqConstructor.ArrayConstructor {
            def newObjectBuilder[B](sizeHint: Int): Builder[B] =
              new Builder(new Array[Any](_root_.java.lang.Math.max(sizeHint, 1)).asInstanceOf[Array[B]], 0)

            def addObject[B](builder: Builder[B], a: B): Unit = {
              var buf = builder.buffer
              val idx = builder.size
              if (buf.length == idx) {
                buf = _root_.java.util.Arrays.copyOf(buf.asInstanceOf[Array[AnyRef]], idx << 1).asInstanceOf[Array[B]]
                builder.buffer = buf
              }
              buf(idx) = a
              builder.size = idx + 1
            }

            def resultObject[B](builder: Builder[B]): Array[B] = {
              val buf  = builder.buffer
              val size = builder.size
              if (buf.length == size) buf
              else _root_.java.util.Arrays.copyOf(buf.asInstanceOf[Array[AnyRef]], size).asInstanceOf[Array[B]]
            }

            def emptyObject[B]: Array[B] = ct.newArray(0).asInstanceOf[Array[B]]
          },
          _root_.zio.blocks.schema.binding.SeqDeconstructor.arrayDeconstructor
        )
      }
      """
    )
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
          case x: $sTpe => x
          case _        => null.asInstanceOf[$sTpe]
        }
      }
      """
    }

    val discriminateCases = subtypes.zipWithIndex.map { case (sTpe, idx) =>
      cq"_: $sTpe => $idx"
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
        (underlying: $underlyingType) => $companion.$applyMethod(underlying)
        """
      } else {
        q"""
        (underlying: $underlyingType) => $companion.$applyMethod(underlying) match {
          case _root_.scala.Right(a)  => _root_.scala.Right(a)
          case _root_.scala.Left(err) => _root_.scala.Left(
            _root_.zio.blocks.schema.SchemaError.validationFailed(err.toString)
          )
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
        (underlying: $underlyingType) => _root_.scala.Right($companion.wrap(underlying))
        """
      } else {
        q"""
        (underlying: $underlyingType) => _root_.scala.Right(underlying.asInstanceOf[$tpe])
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
      case class FieldInfo(name: TermName, tpe: Type, isPrimitive: Boolean, registerType: String)

      val fieldLists = paramLists.map(_.map { param =>
        val fieldTpe                    = tpe.decl(param.name).typeSignatureIn(tpe).finalResultType
        val (isPrimitive, registerType) =
          if (fieldTpe =:= booleanTpe) (true, "Boolean")
          else if (fieldTpe =:= byteTpe) (true, "Byte")
          else if (fieldTpe =:= shortTpe) (true, "Short")
          else if (fieldTpe =:= intTpe) (true, "Int")
          else if (fieldTpe =:= longTpe) (true, "Long")
          else if (fieldTpe =:= floatTpe) (true, "Float")
          else if (fieldTpe =:= doubleTpe) (true, "Double")
          else if (fieldTpe =:= charTpe) (true, "Char")
          else (false, "Object")
        FieldInfo(param.name.toTermName, fieldTpe, isPrimitive, registerType)
      })
      val fields = fieldLists.flatten

      val usedRegistersTree = fields.foldLeft[Tree](q"_root_.zio.blocks.schema.binding.RegisterOffset.Zero") {
        (acc, f) =>
          val delta = f.registerType match {
            case "Boolean" => q"_root_.zio.blocks.schema.binding.RegisterOffset(booleans = 1)"
            case "Byte"    => q"_root_.zio.blocks.schema.binding.RegisterOffset(bytes = 1)"
            case "Short"   => q"_root_.zio.blocks.schema.binding.RegisterOffset(shorts = 1)"
            case "Int"     => q"_root_.zio.blocks.schema.binding.RegisterOffset(ints = 1)"
            case "Long"    => q"_root_.zio.blocks.schema.binding.RegisterOffset(longs = 1)"
            case "Float"   => q"_root_.zio.blocks.schema.binding.RegisterOffset(floats = 1)"
            case "Double"  => q"_root_.zio.blocks.schema.binding.RegisterOffset(doubles = 1)"
            case "Char"    => q"_root_.zio.blocks.schema.binding.RegisterOffset(chars = 1)"
            case _         => q"_root_.zio.blocks.schema.binding.RegisterOffset(objects = 1)"
          }
          q"_root_.zio.blocks.schema.binding.RegisterOffset.add($acc, $delta)"
      }

      var currentOffsetTree: Tree        = q"_root_.zio.blocks.schema.binding.RegisterOffset.Zero"
      def fieldToArg(f: FieldInfo): Tree = {
        val offsetTree = currentOffsetTree
        f.registerType match {
          case "Boolean" =>
            currentOffsetTree =
              q"_root_.zio.blocks.schema.binding.RegisterOffset.add($currentOffsetTree, _root_.zio.blocks.schema.binding.RegisterOffset(booleans = 1))"
            q"in.getBoolean(_root_.zio.blocks.schema.binding.RegisterOffset.add(offset, $offsetTree))"
          case "Byte" =>
            currentOffsetTree =
              q"_root_.zio.blocks.schema.binding.RegisterOffset.add($currentOffsetTree, _root_.zio.blocks.schema.binding.RegisterOffset(bytes = 1))"
            q"in.getByte(_root_.zio.blocks.schema.binding.RegisterOffset.add(offset, $offsetTree))"
          case "Short" =>
            currentOffsetTree =
              q"_root_.zio.blocks.schema.binding.RegisterOffset.add($currentOffsetTree, _root_.zio.blocks.schema.binding.RegisterOffset(shorts = 1))"
            q"in.getShort(_root_.zio.blocks.schema.binding.RegisterOffset.add(offset, $offsetTree))"
          case "Int" =>
            currentOffsetTree =
              q"_root_.zio.blocks.schema.binding.RegisterOffset.add($currentOffsetTree, _root_.zio.blocks.schema.binding.RegisterOffset(ints = 1))"
            q"in.getInt(_root_.zio.blocks.schema.binding.RegisterOffset.add(offset, $offsetTree))"
          case "Long" =>
            currentOffsetTree =
              q"_root_.zio.blocks.schema.binding.RegisterOffset.add($currentOffsetTree, _root_.zio.blocks.schema.binding.RegisterOffset(longs = 1))"
            q"in.getLong(_root_.zio.blocks.schema.binding.RegisterOffset.add(offset, $offsetTree))"
          case "Float" =>
            currentOffsetTree =
              q"_root_.zio.blocks.schema.binding.RegisterOffset.add($currentOffsetTree, _root_.zio.blocks.schema.binding.RegisterOffset(floats = 1))"
            q"in.getFloat(_root_.zio.blocks.schema.binding.RegisterOffset.add(offset, $offsetTree))"
          case "Double" =>
            currentOffsetTree =
              q"_root_.zio.blocks.schema.binding.RegisterOffset.add($currentOffsetTree, _root_.zio.blocks.schema.binding.RegisterOffset(doubles = 1))"
            q"in.getDouble(_root_.zio.blocks.schema.binding.RegisterOffset.add(offset, $offsetTree))"
          case "Char" =>
            currentOffsetTree =
              q"_root_.zio.blocks.schema.binding.RegisterOffset.add($currentOffsetTree, _root_.zio.blocks.schema.binding.RegisterOffset(chars = 1))"
            q"in.getChar(_root_.zio.blocks.schema.binding.RegisterOffset.add(offset, $offsetTree))"
          case _ =>
            currentOffsetTree =
              q"_root_.zio.blocks.schema.binding.RegisterOffset.add($currentOffsetTree, _root_.zio.blocks.schema.binding.RegisterOffset(objects = 1))"
            q"in.getObject(_root_.zio.blocks.schema.binding.RegisterOffset.add(offset, $offsetTree)).asInstanceOf[${f.tpe}]"
        }
      }

      val constructArgss = fieldLists.map(_.map(fieldToArg))
      val constructCall  = q"new $tpe(...$constructArgss)"

      currentOffsetTree = q"_root_.zio.blocks.schema.binding.RegisterOffset.Zero"
      val deconstructStatements = fields.map { f =>
        val offsetTree = currentOffsetTree
        val fieldName  = f.name
        val setter     = f.registerType match {
          case "Boolean" =>
            currentOffsetTree =
              q"_root_.zio.blocks.schema.binding.RegisterOffset.add($currentOffsetTree, _root_.zio.blocks.schema.binding.RegisterOffset(booleans = 1))"
            q"out.setBoolean(_root_.zio.blocks.schema.binding.RegisterOffset.add(offset, $offsetTree), in.$fieldName)"
          case "Byte" =>
            currentOffsetTree =
              q"_root_.zio.blocks.schema.binding.RegisterOffset.add($currentOffsetTree, _root_.zio.blocks.schema.binding.RegisterOffset(bytes = 1))"
            q"out.setByte(_root_.zio.blocks.schema.binding.RegisterOffset.add(offset, $offsetTree), in.$fieldName)"
          case "Short" =>
            currentOffsetTree =
              q"_root_.zio.blocks.schema.binding.RegisterOffset.add($currentOffsetTree, _root_.zio.blocks.schema.binding.RegisterOffset(shorts = 1))"
            q"out.setShort(_root_.zio.blocks.schema.binding.RegisterOffset.add(offset, $offsetTree), in.$fieldName)"
          case "Int" =>
            currentOffsetTree =
              q"_root_.zio.blocks.schema.binding.RegisterOffset.add($currentOffsetTree, _root_.zio.blocks.schema.binding.RegisterOffset(ints = 1))"
            q"out.setInt(_root_.zio.blocks.schema.binding.RegisterOffset.add(offset, $offsetTree), in.$fieldName)"
          case "Long" =>
            currentOffsetTree =
              q"_root_.zio.blocks.schema.binding.RegisterOffset.add($currentOffsetTree, _root_.zio.blocks.schema.binding.RegisterOffset(longs = 1))"
            q"out.setLong(_root_.zio.blocks.schema.binding.RegisterOffset.add(offset, $offsetTree), in.$fieldName)"
          case "Float" =>
            currentOffsetTree =
              q"_root_.zio.blocks.schema.binding.RegisterOffset.add($currentOffsetTree, _root_.zio.blocks.schema.binding.RegisterOffset(floats = 1))"
            q"out.setFloat(_root_.zio.blocks.schema.binding.RegisterOffset.add(offset, $offsetTree), in.$fieldName)"
          case "Double" =>
            currentOffsetTree =
              q"_root_.zio.blocks.schema.binding.RegisterOffset.add($currentOffsetTree, _root_.zio.blocks.schema.binding.RegisterOffset(doubles = 1))"
            q"out.setDouble(_root_.zio.blocks.schema.binding.RegisterOffset.add(offset, $offsetTree), in.$fieldName)"
          case "Char" =>
            currentOffsetTree =
              q"_root_.zio.blocks.schema.binding.RegisterOffset.add($currentOffsetTree, _root_.zio.blocks.schema.binding.RegisterOffset(chars = 1))"
            q"out.setChar(_root_.zio.blocks.schema.binding.RegisterOffset.add(offset, $offsetTree), in.$fieldName)"
          case _ =>
            currentOffsetTree =
              q"_root_.zio.blocks.schema.binding.RegisterOffset.add($currentOffsetTree, _root_.zio.blocks.schema.binding.RegisterOffset(objects = 1))"
            q"out.setObject(_root_.zio.blocks.schema.binding.RegisterOffset.add(offset, $offsetTree), in.$fieldName.asInstanceOf[AnyRef])"
        }
        setter
      }

      c.Expr[Any](
        q"""
        new _root_.zio.blocks.schema.binding.Binding.Record[$tpe](
          constructor = new _root_.zio.blocks.schema.binding.Constructor[$tpe] {
            def usedRegisters: _root_.zio.blocks.schema.binding.RegisterOffset.RegisterOffset = $usedRegistersTree

            def construct(
              in: _root_.zio.blocks.schema.binding.Registers,
              offset: _root_.zio.blocks.schema.binding.RegisterOffset.RegisterOffset
            ): $tpe = $constructCall
          },
          deconstructor = new _root_.zio.blocks.schema.binding.Deconstructor[$tpe] {
            def usedRegisters: _root_.zio.blocks.schema.binding.RegisterOffset.RegisterOffset = $usedRegistersTree

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
}
