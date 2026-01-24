package zio.blocks.schema

import scala.quoted.*
import scala.language.dynamics

/**
 * A trait that can be extended by companion objects to automatically derive
 * optics (Lens for case class fields, Prism for sealed trait/enum variants).
 *
 * Usage:
 * {{{
 * case class Person(name: String, age: Int) derives Schema
 * object Person extends DerivedOptics[Person]
 *
 * // Access optics via the `optics` member:
 * val nameLens: Lens[Person, String] = Person.optics.name
 * val ageLens: Lens[Person, Int] = Person.optics.age
 * }}}
 *
 * For sealed traits/enums:
 * {{{
 * sealed trait Shape derives Schema
 * case class Circle(radius: Double) extends Shape
 * case class Rectangle(width: Double, height: Double) extends Shape
 * object Shape extends DerivedOptics[Shape]
 *
 * // Access prisms via the `optics` member:
 * val circlePrism: Prism[Shape, Circle] = Shape.optics.circle
 * }}}
 *
 * For opaque types / value classes:
 * {{{
 * opaque type Age = Int
 * object Age extends DerivedOptics[Age] {
 *   // Ensure a Wrapper schema is available (e.g. using wrapTotal)
 *   given schema: Schema[Age] = Schema.int.wrapTotal(Age.apply, _.value)
 *
 *   def apply(i: Int): Age = i
 *   extension (a: Age) def value: Int = a
 * }
 *
 * // Access the wrapper lens via `.value`:
 * val valueLens: Lens[Age, Int] = Age.optics.value
 * }}}
 *
 * The optics object is cached to avoid recreation on every access.
 *
 * @tparam S
 *   The type for which to derive optics
 */
trait DerivedOptics[S] {

  /**
   * Provides access to the derived optics for type S. For case classes, returns
   * an object with Lens accessors for each field. For sealed traits/enums,
   * returns an object with Prism accessors for each variant.
   *
   * The returned object uses structural typing, so you get compile-time type
   * checking and IDE completion for the accessor names.
   */
  transparent inline def optics(using schema: Schema[S]): Any = ${ DerivedOpticsMacros.opticsImpl[S]('schema, false) }
}

object DerivedOptics {
  // Adapter to treat a Wrapper as a Record with one field named "value".
  // This allows reusing the optimized LensImpl logic without modifying Optic.scala.
  private[schema] def wrapperAsRecord[A, B](wrapper: Reflect.Wrapper.Bound[A, B]): Reflect.Record.Bound[A] = {
    import zio.blocks.schema.binding._
    import zio.blocks.schema.binding.RegisterOffset.RegisterOffset

    val fieldTerm = new Term[Binding, A, B]("value", wrapper.wrapped)

    // Determine primitive type components
    val (usedRegs, reader, writer)
      : (RegisterOffset, (Registers, RegisterOffset) => Any, (Registers, RegisterOffset, Any) => Unit) =
      Reflect.unwrapToPrimitiveTypeOption(wrapper.wrapped) match {
        case Some(pt) =>
          pt match {
            case _: PrimitiveType.Boolean =>
              (
                RegisterOffset(booleans = 1),
                (r: Registers, o: RegisterOffset) => r.getBoolean(o),
                (r: Registers, o: RegisterOffset, v: Any) => r.setBoolean(o, v.asInstanceOf[Boolean])
              )
            case _: PrimitiveType.Byte =>
              (
                RegisterOffset(bytes = 1),
                (r: Registers, o: RegisterOffset) => r.getByte(o),
                (r: Registers, o: RegisterOffset, v: Any) => r.setByte(o, v.asInstanceOf[Byte])
              )
            case _: PrimitiveType.Short =>
              (
                RegisterOffset(shorts = 1),
                (r: Registers, o: RegisterOffset) => r.getShort(o),
                (r: Registers, o: RegisterOffset, v: Any) => r.setShort(o, v.asInstanceOf[Short])
              )
            case _: PrimitiveType.Int =>
              (
                RegisterOffset(ints = 1),
                (r: Registers, o: RegisterOffset) => r.getInt(o),
                (r: Registers, o: RegisterOffset, v: Any) => r.setInt(o, v.asInstanceOf[Int])
              )
            case _: PrimitiveType.Long =>
              (
                RegisterOffset(longs = 1),
                (r: Registers, o: RegisterOffset) => r.getLong(o),
                (r: Registers, o: RegisterOffset, v: Any) => r.setLong(o, v.asInstanceOf[Long])
              )
            case _: PrimitiveType.Float =>
              (
                RegisterOffset(floats = 1),
                (r: Registers, o: RegisterOffset) => r.getFloat(o),
                (r: Registers, o: RegisterOffset, v: Any) => r.setFloat(o, v.asInstanceOf[Float])
              )
            case _: PrimitiveType.Double =>
              (
                RegisterOffset(doubles = 1),
                (r: Registers, o: RegisterOffset) => r.getDouble(o),
                (r: Registers, o: RegisterOffset, v: Any) => r.setDouble(o, v.asInstanceOf[Double])
              )
            case _: PrimitiveType.Char =>
              (
                RegisterOffset(chars = 1),
                (r: Registers, o: RegisterOffset) => r.getChar(o),
                (r: Registers, o: RegisterOffset, v: Any) => r.setChar(o, v.asInstanceOf[Char])
              )
            case PrimitiveType.Unit =>
              (
                RegisterOffset(0),
                (_: Registers, _: RegisterOffset) => (),
                (_: Registers, _: RegisterOffset, _: Any) => ()
              )
            case _ =>
              (
                RegisterOffset(objects = 1),
                (r: Registers, o: RegisterOffset) => r.getObject(o).asInstanceOf[B],
                (r: Registers, o: RegisterOffset, v: Any) => r.setObject(o, v.asInstanceOf[AnyRef])
              )
          }
        case None =>
          (
            RegisterOffset(objects = 1),
            (r: Registers, o: RegisterOffset) => r.getObject(o).asInstanceOf[B],
            (r: Registers, o: RegisterOffset, v: Any) => r.setObject(o, v.asInstanceOf[AnyRef])
          )
      }

    val syntheticBinding = new Binding.Record[A](
      constructor = new Constructor[A] {
        override def usedRegisters: RegisterOffset = usedRegs

        override def construct(registers: Registers, offset: RegisterOffset): A = {
          val b = reader(registers, offset).asInstanceOf[B]
          wrapper.binding.wrap(b) match {
            case Right(a)  => a
            case Left(err) => throw new RuntimeException(s"Wrapper validation failed: ${err.message}")
          }
        }
      },
      deconstructor = new Deconstructor[A] {
        override def usedRegisters: RegisterOffset = usedRegs

        override def deconstruct(registers: Registers, offset: RegisterOffset, value: A): Unit = {
          val b = wrapper.binding.unwrap(value)
          writer(registers, offset, b)
        }
      },
      defaultValue = wrapper.wrapped.binding.defaultValue.map(b =>
        () => wrapper.binding.wrap(b()).getOrElse(throw new RuntimeException("Default value invalid"))
      ),
      examples = Nil
    )

    Reflect.Record(
      fields = IndexedSeq(fieldTerm.asInstanceOf[Term[Binding, A, ?]]),
      typeName = wrapper.typeName,
      recordBinding = syntheticBinding
    )
  }
}

/**
 * An optics holder that stores lenses/prisms in a map and provides dynamic
 * access. This is an implementation detail and should not be used directly.
 */
final class OpticsHolder(members: Map[String, Any]) extends scala.Dynamic with Selectable {
  def selectDynamic(name: String): Any =
    members.getOrElse(name, throw new RuntimeException(s"No optic found for: $name"))
}

private[schema] object DerivedOpticsMacros {
  import java.util.concurrent.ConcurrentHashMap

  // Helper to lower-case the first letter of a name (per issue #514 requirement)
  private def lowerFirst(s: String): String =
    if (s.isEmpty) s else s.head.toLower.toString + s.tail

  // Global cache to avoid recreating optics objects at runtime
  // Key is the type's full name as a string
  private val cache = new ConcurrentHashMap[String, OpticsHolder]()

  private[schema] def getOrCreate(key: String, create: => OpticsHolder): OpticsHolder = {
    var result = cache.get(key)
    if (result == null) {
      result = create
      val existing = cache.putIfAbsent(key, result)
      if (existing != null) result = existing
    }
    result
  }

  def opticsImpl[S: Type](schema: Expr[Schema[S]], prefixUnderscore: Boolean)(using q: Quotes): Expr[Any] = {
    import q.reflect.*

    val tpe           = TypeRepr.of[S]
    val caseClassType = tpe.dealias
    val caseClassSym  = caseClassType.typeSymbol
    val isPrelude     = tpe.baseClasses.exists { sym =>
      val name     = sym.name
      val isTarget = name == "Newtype" || name == "Subtype"
      isTarget && sym.owner.fullName == "zio.prelude"
    }
    val isCaseClass = caseClassSym.flags.is(Flags.Case)
    val isSealed    = caseClassSym.flags.is(Flags.Sealed)
    val isEnum      = caseClassSym.flags.is(Flags.Enum)
    if (isCaseClass) {
      buildCaseClassOptics[S](schema, caseClassSym, caseClassType, prefixUnderscore)
    } else if (isSealed || isEnum) {
      buildSealedTraitOptics[S](schema, caseClassSym, caseClassType, prefixUnderscore)
    } else if (isPrelude) {
      buildWrapperOptics[S](schema, tpe, caseClassType, prefixUnderscore)
    } else {
      buildWrapperOptics[S](schema, tpe, caseClassType, prefixUnderscore)
    }
  }

  private def buildWrapperOptics[S: Type](
    schema: Expr[Schema[S]],
    originalType: Quotes#reflectModule#TypeRepr,
    underlyingType: Quotes#reflectModule#TypeRepr,
    prefixUnderscore: Boolean
  )(using q: Quotes): Expr[Any] = {
    import q.reflect.*

    val tpe           = originalType.asInstanceOf[TypeRepr]
    val underlyingTpe = underlyingType.asInstanceOf[TypeRepr]
    underlyingTpe.asType match {
      case '[u] =>
        val fieldName   = if (prefixUnderscore) "_value" else "value"
        val lensType    = TypeRepr.of[Lens[S, u]]
        val refinedType = Refinement(TypeRepr.of[OpticsHolder], fieldName, lensType)
        refinedType.asType match {
          case '[rt] =>
            val cacheKey = Expr(tpe.show + (if (prefixUnderscore) "_" else ""))
            '{
              getOrCreate(
                $cacheKey, {
                  // Runtime check: try to treat the schema as a wrapper
                  val reflectData = $schema.reflect
                  val opticsMap   = if (reflectData.isWrapper) {
                    val w = reflectData.asInstanceOf[Reflect.Wrapper.Bound[S, u]]
                    // Convert Wrapper to Fake Record
                    val record = DerivedOptics.wrapperAsRecord(w)
                    // Create Lens from Fake Record (field index 0)
                    val lens = Lens(record, record.fields(0).asInstanceOf[zio.blocks.schema.Term.Bound[S, u]])
                    Map(${ Expr(fieldName) } -> lens)
                  } else {
                    Map.empty
                  }
                  new OpticsHolder(opticsMap)
                }
              ).asInstanceOf[rt]
            }
        }
    }
  }

  private def buildCaseClassOptics[S: Type](
    schema: Expr[Schema[S]],
    sym: Quotes#reflectModule#Symbol,
    tpe: Quotes#reflectModule#TypeRepr,
    prefixUnderscore: Boolean
  )(using q: Quotes): Expr[Any] = {
    import q.reflect.*

    val symCast               = sym.asInstanceOf[Symbol]
    val tpeCast               = tpe.asInstanceOf[TypeRepr]
    val fields                = symCast.caseFields
    var refinedType: TypeRepr = TypeRepr.of[OpticsHolder]
    for (field <- fields) {
      val fieldType    = tpeCast.memberType(field).dealias
      val lensType     = TypeRepr.of[Lens].appliedTo(List(tpeCast, fieldType))
      val accessorName = if (prefixUnderscore) "_" + field.name else field.name
      refinedType = Refinement(refinedType, accessorName, lensType)
    }
    val cacheKey: Expr[String]              = Expr(tpeCast.show + (if (prefixUnderscore) "_" else ""))
    val prefixUnderscoreExpr: Expr[Boolean] = Expr(prefixUnderscore)
    refinedType.asType match {
      case '[t] =>
        '{
          getOrCreate(
            $cacheKey, {
              val reflectData = $schema.reflect
              val record      = reflectData.asRecord.orElse {
                reflectData.asWrapperUnknown.map { _ =>
                  val w = reflectData.asInstanceOf[Reflect.Wrapper.Bound[S, Any]]
                  DerivedOptics.wrapperAsRecord(w)
                }
              }.getOrElse(
                throw new RuntimeException(s"Expected a record schema for ${$cacheKey}")
              )
              val members = record.fields.zipWithIndex.map { case (term, idx) =>
                val lens = record
                  .lensByIndex(idx)
                  .getOrElse(
                    throw new RuntimeException(s"Cannot find lens for field ${term.name}")
                  )
                val name = NameTransformer.encode(if ($prefixUnderscoreExpr) "_" + term.name else term.name)
                name -> lens
              }.toMap
              new OpticsHolder(members)
            }
          ).asInstanceOf[t]
        }
    }
  }

  private def buildSealedTraitOptics[S: Type](
    schema: Expr[Schema[S]],
    sym: Quotes#reflectModule#Symbol,
    tpe: Quotes#reflectModule#TypeRepr,
    prefixUnderscore: Boolean
  )(using q: Quotes): Expr[Any] = {
    import q.reflect.*

    val symCast               = sym.asInstanceOf[Symbol]
    val tpeCast               = tpe.asInstanceOf[TypeRepr]
    val children              = symCast.children
    var refinedType: TypeRepr = TypeRepr.of[OpticsHolder]
    for (child <- children) {
      val childType = if (child.isType) {
        // If the child has type parameters, try to apply the parent's type arguments
        // This handles the common pattern: sealed trait T[A]; case class C[A](...) extends T[A]
        // We check primaryConstructor paramSymss to detect type parameters
        val hasTypeParams = child.primaryConstructor.paramSymss.headOption.exists(_.exists(_.isType))
        if (hasTypeParams && tpeCast.typeArgs.nonEmpty) {
          child.typeRef.appliedTo(tpeCast.typeArgs)
        } else {
          child.typeRef
        }
      } else {
        // For case objects, get the type
        child.termRef.widen
      }
      val prismType    = TypeRepr.of[Prism].appliedTo(List(tpeCast, childType))
      val baseName     = lowerFirst(child.name)
      val accessorName = if (prefixUnderscore) "_" + baseName else baseName
      refinedType = Refinement(refinedType, accessorName, prismType)
    }
    val cacheKey: Expr[String]              = Expr(tpeCast.show + (if (prefixUnderscore) "_" else ""))
    val prefixUnderscoreExpr: Expr[Boolean] = Expr(prefixUnderscore)
    refinedType.asType match {
      case '[t] =>
        '{
          getOrCreate(
            $cacheKey, {
              val variant = $schema.reflect.asVariant.getOrElse(
                throw new RuntimeException(s"Expected a variant schema for ${$cacheKey}")
              )
              val prefixUs = $prefixUnderscoreExpr
              val members  = variant.cases.zipWithIndex.map { case (term, idx) =>
                val prism = variant
                  .prismByIndex(idx)
                  .getOrElse(
                    throw new RuntimeException(s"Cannot find prism for case ${term.name}")
                  )
                val baseName = lowerFirst(term.name)
                val name     = NameTransformer.encode(if (prefixUs) "_" + baseName else baseName)
                name -> prism
              }.toMap
              new OpticsHolder(members)
            }
          ).asInstanceOf[t]
        }
    }
  }
}

// Borrowed from `dotty.tools.dotc.util.NameTransformer`
private object NameTransformer {
  private[this] val nops   = 128
  private[this] val ncodes = 26 * 26

  private class OpCodes(val op: Char, val code: String, val next: OpCodes)

  private[this] val op2code = new Array[String](nops)
  private[this] val code2op = new Array[OpCodes](ncodes)

  private def enterOp(op: Char, code: String): Unit = {
    op2code(op.toInt) = code
    val c = (code.charAt(1) - 'a') * 26 + code.charAt(2) - 'a'
    code2op(c) = new OpCodes(op, code, code2op(c))
  }

  enterOp('~', "$tilde")
  enterOp('=', "$eq")
  enterOp('<', "$less")
  enterOp('>', "$greater")
  enterOp('!', "$bang")
  enterOp('#', "$hash")
  enterOp('%', "$percent")
  enterOp('^', "$up")
  enterOp('&', "$amp")
  enterOp('|', "$bar")
  enterOp('*', "$times")
  enterOp('/', "$div")
  enterOp('+', "$plus")
  enterOp('-', "$minus")
  enterOp(':', "$colon")
  enterOp('\\', "$bslash")
  enterOp('?', "$qmark")
  enterOp('@', "$at")

  def encode(name: String): String = {
    var buf: java.lang.StringBuilder = null
    val len                          = name.length()
    var i                            = 0
    while (i < len) {
      val c = name.charAt(i)
      if (c < nops && (op2code(c.toInt) ne null)) {
        if (buf eq null) {
          buf = new java.lang.StringBuilder
          buf.append(name.substring(0, i))
        }
        buf.append(op2code(c.toInt))
      } else if (!Character.isJavaIdentifierPart(c)) {
        if (buf eq null) {
          buf = new java.lang.StringBuilder
          buf.append(name.substring(0, i))
        }
        buf.append("$u%04X".format(c.toInt))
      } else if (buf ne null) buf.append(c)
      i += 1
    }
    if (buf eq null) name else buf.toString
  }
}
