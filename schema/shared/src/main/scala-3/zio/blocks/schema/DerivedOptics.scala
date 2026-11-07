/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema

import scala.quoted.*
import scala.language.dynamics
import scala.language.implicitConversions

/**
 * Mix this trait into the companion object of a case class or a sealed trait to
 * get the class's optics without writing them: a [[Lens]] for each field of a
 * case class, named after the field, and a [[Prism]] for each case of a sealed
 * trait (or, on Scala 3, an enum), named after the case with its first letter
 * in lower case (`Paperback` gives `paperback`). They are the members of
 * `optics`. On Scala 3 they are also members of the companion itself
 * (`Book.title`), through a given conversion. A member that the companion
 * defines itself takes precedence there, and the derived optic stays available
 * on `optics`. Direct access goes through an implicit conversion, so Scala 3
 * gives a feature warning at each use unless
 * `scala.language.implicitConversions` is imported, as the first example does.
 *
 * An implicit `Schema` of the class must be in scope where `optics` is used; a
 * schema defined in the companion is. For a type that is not the class of a
 * companion, such as `Box[Int]` or a type alias, use [[DerivedOptics.Of]]. On
 * Scala 3, a value class whose schema is a wrapper (built with `transform`)
 * gets a working lens only when its field is named `value`. Opaque types are
 * not supported yet.
 *
 * The optics are built the first time `optics` is evaluated and kept in a
 * global cache keyed by the type's printed name. Every companion for the same
 * type shares the optics built from the first schema used, and so do different
 * types that print the same, such as two local classes named `Book` in
 * different methods: the second then fails with a `ClassCastException` or
 * `No optic found` (a bug, see `docs/dev/issues.md`).
 *
 * A known problem, described in `docs/dev/issues.md`: on Scala 2, `optics`
 * compiles only inside the `zio.blocks.schema` package, so the examples below
 * are compiled on Scala 3 only.
 *
 * {{{
 * // scala-3-only
 * import scala.language.implicitConversions
 *
 * case class Book(title: String, pages: Int)
 * object Book extends DerivedOptics {
 *   implicit val schema: Schema[Book] = Schema.derived
 * }
 *
 * val title: Lens[Book, String] = Book.optics.title
 * val pages: Lens[Book, Int]    = Book.pages // direct access, Scala 3 only
 * }}}
 *
 * {{{
 * // scala-3-only
 * sealed trait Format
 * case class Paperback(pages: Int) extends Format
 * case class Ebook(sizeMb: Double) extends Format
 * object Format extends DerivedOptics {
 *   implicit val schema: Schema[Format] = Schema.derived
 * }
 *
 * val paperback: Prism[Format, Paperback] = Format.optics.paperback
 * }}}
 *
 * {{{
 * // scala-3-only
 * case class PageCount(value: Int) extends AnyVal
 * object PageCount extends DerivedOptics {
 *   // a wrapper schema, built with `transform`
 *   implicit val schema: Schema[PageCount] = Schema[Int].transform(PageCount(_), _.value)
 * }
 *
 * val value: Lens[PageCount, Int] = PageCount.optics.value
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/optics/ Optics]]
 */
trait DerivedOptics {

  /**
   * Returns the derived optics of the companion's class: an object with one
   * [[Lens]] per field or one [[Prism]] per case, whose type gives each member
   * its exact optic type. It fails to compile when the object is not the
   * companion of a class, or when no implicit `Schema` of the class is in
   * scope. When the schema does not have the shape of the class (a record or
   * wrapper for a case class, a variant for a sealed trait), using the optics
   * throws a `RuntimeException`.
   */
  transparent inline def optics: Any = ${ DerivedOpticsMacros.opticsFromThisTypeImpl('this) }

  /**
   * Scala 3 only: the conversion that makes the derived optics members of the
   * companion itself, so that `Book.title` works like `Book.optics.title`. It
   * takes the optics from an [[OpticsFor]] instance, which the `optics` macro
   * derives. Like `optics`, it needs an implicit `Schema` of the class where it
   * is used.
   *
   * Credit: Kit Langton discovered this companion-object conversion trick.
   */
  given toDerivedOptics(using
    cc: CompanionClass[this.type],
    opticsFor: OpticsFor[cc.Type]
  ): Conversion[this.type, opticsFor.Out] =
    _ => opticsFor.optics

}

/**
 * Scala 3 only: a type class that gives the class whose companion object has
 * the type `A`. For `Book.type`, the type of the companion object, `Type` is
 * `Book`.
 */
trait CompanionClass[A] {

  /**
   * The class whose companion object has the type `A`.
   */
  type Type
}

/**
 * Scala 3 only: derives a [[CompanionClass]] for a companion object type with a
 * macro. It fails to compile for a type that has no companion class.
 */
object CompanionClass {
  transparent inline given [A]: CompanionClass[A] = ${ companionClassImpl[A] }

  private def companionClassImpl[A: Type](using Quotes): Expr[CompanionClass[A]] = {
    import quotes.reflect.*
    val typeReprA   = TypeRepr.of[A]
    val classSymOpt = typeReprA.classSymbol
    classSymOpt match {
      case Some(classSym) =>
        val companionClass = classSym.companionClass
        if companionClass.isNoSymbol then report.errorAndAbort(s"Cannot find companion class for ${Type.show[A]}")
        else
          companionClass.typeRef.asType match {
            case '[t] =>
              '{
                new CompanionClass[A] {
                  type Type = t
                }
              }
          }
      case _ => report.errorAndAbort(s"Cannot find class symbol for ${Type.show[A]}")
    }
  }
}

/**
 * Holds [[DerivedOptics.Of]] and the runtime helpers that the `optics` macros
 * call.
 */
object DerivedOptics {

  /**
   * Mix `DerivedOptics.Of[S]` into any object to get the optics of `S` when `S`
   * is not the class of that object's companion: a generic instantiation such
   * as `Box[Int]`, a type alias, or a type whose companion you cannot change.
   * It works like [[DerivedOptics]], with `S` given explicitly. `optics` needs
   * an implicit `Schema[S]` where it is called, so a schema defined in the
   * object has to be imported there.
   *
   * {{{
   * // scala-3-only
   * case class Box[A](value: A)
   * object BoxInt extends DerivedOptics.Of[Box[Int]] {
   *   implicit val schema: Schema[Box[Int]] = Schema.derived
   * }
   *
   * import BoxInt.schema
   * val value: Lens[Box[Int], Int] = BoxInt.optics.value
   * }}}
   */
  trait Of[S] {

    /**
     * Returns the derived optics of `S`, like `DerivedOptics#optics`. It needs
     * an implicit `Schema[S]` where it is called.
     */
    transparent inline def optics(using schema: Schema[S]): Any =
      ${ DerivedOpticsMacros.opticsImpl[S]('schema, false) }

    /**
     * Scala 3 only: the conversion that makes the derived optics of `S` members
     * of the object itself, so that `BoxInt.value` works like
     * `BoxInt.optics.value`. It takes the optics from an [[OpticsFor]]
     * instance, which the `optics` macro derives. Like `optics`, it needs an
     * implicit `Schema[S]` where it is used.
     */
    given toDerivedOptics(using opticsFor: OpticsFor[S]): Conversion[this.type, opticsFor.Out] =
      _ => opticsFor.optics
  }

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
        case _ =>
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
          wrapper.binding.wrap(b)
        }
      },
      deconstructor = new Deconstructor[A] {
        override def usedRegisters: RegisterOffset = usedRegs

        override def deconstruct(registers: Registers, offset: RegisterOffset, value: A): Unit =
          writer(registers, offset, wrapper.binding.unwrap(value))
      }
    )

    Reflect.Record(
      fields = IndexedSeq(fieldTerm.asInstanceOf[Term[Binding, A, ?]]),
      typeId = wrapper.typeId,
      recordBinding = syntheticBinding
    )
  }
}

/**
 * The runtime object behind `optics`: it holds the derived optics by name, and
 * the type of `optics` gives each member its type. It is an implementation
 * detail; use the members of `optics`. On Scala 2 it is
 * `DerivedOptics.OpticsHolder` and backs only the optics of types that are
 * neither case classes nor sealed traits (the others are anonymous classes); on
 * Scala 3 it is `zio.blocks.schema.OpticsHolder`, backs every `optics`, and is
 * also a `Selectable`.
 */
final class OpticsHolder(members: Map[String, Any]) extends scala.Dynamic with Selectable {

  /**
   * Returns the optic named `name`.
   *
   * @throws java.lang.RuntimeException
   *   (`No optic found for: <name>`) if there is no optic with that name
   */
  def selectDynamic(name: String): Any =
    members.getOrElse(name, throw new RuntimeException(s"No optic found for: $name"))
}

/**
 * Type class that provides derived optics for type S. Used internally to enable
 * the `given Conversion` pattern for direct field access.
 *
 * It is public because Scala 3.10 only finds givens in a type's companion that
 * are accessible where the search happens: with `private[schema]`,
 * `Person.name` stops compiling outside the `zio.blocks.schema` package.
 */
trait OpticsFor[S] {

  /** The type of the derived optics of `S`, the type of `optics`. */
  type Out

  /** Returns the derived optics of `S`. */
  def optics: Out
}

/** Derives [[OpticsFor]] instances. */
object OpticsFor {

  /**
   * Derives the optics of `S` from its schema, as `DerivedOptics#optics` does,
   * and refines `Out` to their exact type.
   */
  transparent inline given derived[S](using schema: Schema[S]): OpticsFor[S] = ${ opticsForImpl[S]('schema) }

  private def opticsForImpl[S: Type](schema: Expr[Schema[S]])(using Quotes): Expr[OpticsFor[S]] = {
    import quotes.reflect.*
    val opticsExpr = DerivedOpticsMacros.opticsImpl[S](schema, false)
    opticsExpr.asTerm.tpe.asType match {
      case '[t] =>
        '{
          new OpticsFor[S] {
            type Out = t
            def optics: t = $opticsExpr.asInstanceOf[t]
          }
        }
    }
  }
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

  def opticsFromThisTypeImpl(self: Expr[Any])(using q: Quotes): Expr[Any] = {
    import q.reflect.*

    // Get the type of `this` (e.g., Person.type)
    val selfType = self.asTerm.tpe.widen
    // Find the companion class
    val classSymOpt    = selfType.classSymbol
    val companionClass = classSymOpt
      .map(_.companionClass)
      .filter(!_.isNoSymbol)
      .getOrElse(report.errorAndAbort(s"Cannot find companion class for ${selfType.show}"))
    val companionType = companionClass.typeRef
    companionType.asType match {
      case '[s] =>
        // Look for implicit Schema[s] in scope
        Expr.summon[Schema[s]] match {
          case Some(schema) => opticsImpl[s](schema, false)
          case _            =>
            report.errorAndAbort(
              s"Cannot find implicit Schema[${Type.show[s]}]. " +
                s"Make sure you have defined: given schema: Schema[${companionClass.name}] = Schema.derived"
            )
        }
    }
  }

  def opticsFromCompanionImpl[S: Type](schema: Expr[Schema[S]], prefixUnderscore: Boolean)(using q: Quotes): Expr[Any] =
    opticsImpl[S](schema, prefixUnderscore)

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
    if (isCaseClass) buildCaseClassOptics[S](schema, caseClassSym, caseClassType, prefixUnderscore)
    else if (isSealed || isEnum) buildSealedTraitOptics[S](schema, caseClassSym, caseClassType, prefixUnderscore)
    else if (isPrelude) buildWrapperOptics[S](schema, tpe, caseClassType, prefixUnderscore)
    else buildWrapperOptics[S](schema, tpe, caseClassType, prefixUnderscore)
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
                  } else Map.empty
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
              }.getOrElse(throw new RuntimeException(s"Expected a record schema for ${$cacheKey}"))
              val members = record.fields.zipWithIndex.map { case (term, idx) =>
                val lens = record
                  .lensByIndex(idx)
                  .getOrElse(throw new RuntimeException(s"Cannot find lens for field ${term.name}"))
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
        if (hasTypeParams && tpeCast.typeArgs.nonEmpty) child.typeRef.appliedTo(tpeCast.typeArgs)
        else child.typeRef
      } else {
        // For case objects / enum singletons, use termRef without widening
        // to preserve the singleton type (e.g., Status.Active.type instead of Status)
        child.termRef
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
                  .getOrElse(throw new RuntimeException(s"Cannot find prism for case ${term.name}"))
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
    val len                          = name.length
    var idx                          = 0
    while (idx < len) {
      val c = name.charAt(idx)
      if (c < nops && (op2code(c.toInt) ne null)) {
        if (buf eq null) {
          buf = new java.lang.StringBuilder
          buf.append(name.substring(0, idx))
        }
        buf.append(op2code(c.toInt))
      } else if (!Character.isJavaIdentifierPart(c)) {
        if (buf eq null) {
          buf = new java.lang.StringBuilder
          buf.append(name.substring(0, idx))
        }
        buf.append("$u%04X".format(c.toInt))
      } else if (buf ne null) buf.append(c)
      idx += 1
    }
    if (buf eq null) name else buf.toString
  }
}
