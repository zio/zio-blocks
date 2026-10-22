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

import scala.language.experimental.macros
import scala.language.dynamics
import scala.reflect.macros.whitebox

/**
 * Mix this trait into the companion object of a case class or a sealed trait to
 * get the class's optics without writing them: a [[Lens]] for each field of a
 * case class, named after the field, and a [[Prism]] for each case of a sealed
 * trait (or, on Scala 3, an enum), named after the case with its first letter
 * in lower case (`Paperback` gives `paperback`). They are the members of
 * `optics`. On Scala 3 they are also members of the companion itself
 * (`Book.title`), through a given conversion. A member that the companion
 * defines itself takes precedence there, and the derived optic stays available
 * on `optics`.
 *
 * An implicit `Schema` of the class must be in scope where `optics` is used; a
 * schema defined in the companion is. For a type that is not the class of a
 * companion, such as `Box[Int]` or a type alias, use [[DerivedOptics.Of]]. On
 * Scala 3, a value class whose schema is a wrapper (built with `transform`)
 * gets a working lens only when its field is named `value`. Opaque types are
 * not supported yet.
 *
 * The optics are built the first time `optics` is evaluated and kept in a
 * global cache keyed by the type's name, so every companion for the same type
 * gets the optics built from the first schema used.
 *
 * Known problems, described in `docs/dev/issues.md`: on Scala 2, `optics`
 * compiles only inside the `zio.blocks.schema` package, so the examples below
 * are compiled on Scala 3 only; on Scala 3.9, direct access such as
 * `Book.title` warns that it will stop compiling on Scala 3.10.
 *
 * {{{
 * // scala-3-only
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
  def optics: Any = macro DerivedOpticsMacros.opticsFromCompanionImpl
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
    def optics(implicit schema: Schema[S]): Any = macro DerivedOpticsMacros.opticsImpl[S]
  }

  /**
   * The runtime object behind `optics`: it holds the derived optics by name,
   * and the type of `optics` gives each member its type. It is an
   * implementation detail; use the members of `optics`. On Scala 2 it is
   * `DerivedOptics.OpticsHolder` and backs only the optics of types that are
   * neither case classes nor sealed traits (the others are anonymous classes);
   * on Scala 3 it is `zio.blocks.schema.OpticsHolder`, backs every `optics`,
   * and is also a `Selectable`.
   */
  final class OpticsHolder(members: Map[String, Any]) extends scala.Dynamic {

    /**
     * Returns the optic named `name`.
     *
     * @throws java.lang.RuntimeException
     *   (`No optic found for: <name>`) if there is no optic with that name
     */
    def selectDynamic(name: String): Any =
      members.getOrElse(name, throw new RuntimeException(s"No optic found for: $name"))
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
        case None =>
          (
            RegisterOffset(objects = 1),
            (r: Registers, o: RegisterOffset) => r.getObject(o).asInstanceOf[B],
            (r: Registers, o: RegisterOffset, v: Any) => r.setObject(o, v.asInstanceOf[AnyRef])
          )
      }

    val syntheticBinding = new Binding.Record(
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

private[schema] object DerivedOpticsMacros {
  import java.util.concurrent.ConcurrentHashMap

  // Helper to lower-case the first letter of a name (per issue #514 requirement)
  private def lowerFirst(s: String): String =
    if (s.isEmpty) s else s.head.toLower.toString + s.tail

  // Global cache to avoid recreating optics objects at runtime
  // Key is the type string to handle generics correctly (e.g. Box[Int] vs Box[String])
  private val cache = new ConcurrentHashMap[String, Any]()

  private[schema] def getOrCreate[T](key: String, create: => T): T = {
    var result = cache.get(key)
    if (result == null) {
      result = create
      val existing = cache.putIfAbsent(key, result)
      if (existing != null) result = existing
    }
    result.asInstanceOf[T]
  }

  def opticsFromCompanionImpl(c: whitebox.Context): c.Tree = {
    import c.universe._

    // Get the companion object type from c.prefix (e.g., Person.type)
    val companionType = c.prefix.tree.tpe

    // Find the companion class (e.g., Person from Person.type)
    val companionClassType = companionType.typeSymbol.companion match {
      case NoSymbol =>
        c.abort(c.enclosingPosition, s"Cannot find companion class for ${companionType.typeSymbol.name}")
      case sym => sym.asType.toType
    }

    // Look for an implicit Schema[CompanionClass] in scope
    val schemaType     = appliedType(typeOf[Schema[_]].typeConstructor, List(companionClassType))
    val schemaImplicit = c.inferImplicitValue(schemaType)
    if (schemaImplicit.isEmpty) {
      c.abort(
        c.enclosingPosition,
        s"Cannot find implicit Schema[${companionClassType}]. " +
          s"Make sure you have defined: implicit val schema: Schema[${companionClassType.typeSymbol.name}] = Schema.derived"
      )
    }

    // Now call opticsImplWithSchema with the inferred types
    opticsImplWithSchema(c)(companionClassType, schemaImplicit, prefixUnderscore = false)
  }

  def opticsImpl[S: c.WeakTypeTag](c: whitebox.Context)(schema: c.Expr[Schema[S]]): c.Tree =
    opticsImplWithSchema(c)(c.universe.weakTypeOf[S], schema.tree, prefixUnderscore = false)

  private def opticsImplWithSchema(c: whitebox.Context)(
    tpe: c.universe.Type,
    schemaTree: c.universe.Tree,
    prefixUnderscore: Boolean
  ): c.Tree = {
    val originalType = tpe
    val dealiased    = originalType.dealias
    val typeSym      = dealiased.typeSymbol.asClass
    val isCaseClass  = typeSym.isCaseClass
    val isSealed     = typeSym.isSealed
    val baseClasses  = originalType.baseClasses
    val isPrelude    = baseClasses.exists { sym =>
      val fullName = sym.fullName
      fullName == "zio.prelude.Newtype" || fullName == "zio.prelude.Subtype"
    }
    if (isCaseClass) {
      buildCaseClassOpticsWithTree(c)(schemaTree, dealiased, prefixUnderscore)
    } else if (isSealed) {
      buildSealedTraitOpticsWithTree(c)(schemaTree, dealiased, prefixUnderscore)
    } else if (isPrelude) {
      buildWrapperOpticsWithTree(c)(schemaTree, originalType, dealiased, prefixUnderscore)
    } else {
      buildWrapperOpticsWithTree(c)(schemaTree, originalType, dealiased, prefixUnderscore)
    }
  }

  private def buildCaseClassOpticsWithTree(c: whitebox.Context)(
    schemaTree: c.universe.Tree,
    tpe: c.universe.Type,
    prefixUnderscore: Boolean
  ): c.Tree = {
    import c.universe._

    val primaryConstructor = tpe.decls.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m
    }.getOrElse(c.abort(c.enclosingPosition, s"Could not find primary constructor for $tpe"))
    val fields        = primaryConstructor.paramLists.flatten
    val lensAccessors = fields.zipWithIndex.map { case (field, idx) =>
      val baseName     = field.name.toString
      val accessorName = TermName(if (prefixUnderscore) "_" + baseName else baseName)
      val fieldType    = field.typeSignatureIn(tpe).dealias
      val lensType     = appliedType(typeOf[Lens[_, _]].typeConstructor, List(tpe, fieldType))
      q"""
        lazy val $accessorName: $lensType = {
          val ref = _schema.reflect
          val record = if (ref.isRecord) ref.asRecord.get
            else if (ref.isWrapper) {
               val w = ref.asInstanceOf[_root_.zio.blocks.schema.Reflect.Wrapper.Bound[$tpe, Any]]
               _root_.zio.blocks.schema.DerivedOptics.wrapperAsRecord(w)
            } else throw new RuntimeException("Expected a record or wrapper schema for " + ${tpe.toString})

          record.lensByIndex[$fieldType]($idx).getOrElse(
            throw new RuntimeException("Cannot find lens for field " + ${baseName})
          )
        }
      """
    }
    val cacheKey = tpe.toString + (if (prefixUnderscore) "_" else "")
    q"""
      _root_.zio.blocks.schema.DerivedOpticsMacros.getOrCreate(
        $cacheKey,
        new {
          private val _schema: _root_.zio.blocks.schema.Schema[$tpe] = $schemaTree
          ..$lensAccessors
        }
      )
    """
  }

  private def buildSealedTraitOpticsWithTree(c: whitebox.Context)(
    schemaTree: c.universe.Tree,
    tpe: c.universe.Type,
    prefixUnderscore: Boolean
  ): c.Tree = {
    import c.universe._

    val subtypes       = CommonMacroOps.directSubTypes(c)(tpe)
    val prismAccessors = subtypes.zipWithIndex.map { case (subtype, idx) =>
      val baseName     = lowerFirst(subtype.typeSymbol.name.toString)
      val accessorName = TermName(if (prefixUnderscore) "_" + baseName else baseName)
      val prismType    = appliedType(typeOf[Prism[_, _]].typeConstructor, List(tpe, subtype))
      q"""
        lazy val $accessorName: $prismType = {
          val variant = _schema.reflect.asVariant.getOrElse(
            throw new RuntimeException("Expected a variant schema for " + ${tpe.toString})
          )
          variant.prismByIndex[$subtype]($idx).getOrElse(
            throw new RuntimeException("Cannot find prism for case " + ${baseName})
          )
        }
      """
    }
    val cacheKey = tpe.toString + (if (prefixUnderscore) "_" else "")
    q"""
      _root_.zio.blocks.schema.DerivedOpticsMacros.getOrCreate(
        $cacheKey,
        new {
          private val _schema: _root_.zio.blocks.schema.Schema[$tpe] = $schemaTree
          ..$prismAccessors
        }
      )
    """
  }

  private def buildWrapperOpticsWithTree(c: whitebox.Context)(
    schemaTree: c.universe.Tree,
    originalType: c.Type,
    underlyingType: c.Type,
    prefixUnderscore: Boolean
  ): c.Tree = {
    import c.universe._

    val fieldNameStr = if (prefixUnderscore) "_value" else "value"
    val fieldName    = TermName(fieldNameStr)
    val sType        = originalType
    val cacheKey     = originalType.toString + (if (prefixUnderscore) "_" else "")
    q"""
      _root_.zio.blocks.schema.DerivedOpticsMacros.getOrCreate(
        $cacheKey, {
           val mapOpt = $schemaTree.reflect match {
             case w: _root_.zio.blocks.schema.Reflect.Wrapper => 
               // Convert Wrapper to Fake Record
               val record = _root_.zio.blocks.schema.DerivedOptics.wrapperAsRecord(w.asInstanceOf[_root_.zio.blocks.schema.Reflect.Wrapper.Bound[$sType, $underlyingType]])
               // Create Lens from Fake Record (field index 0)
               val lens = _root_.zio.blocks.schema.Lens(record, record.fields(0).asInstanceOf[_root_.zio.blocks.schema.Term.Bound[$sType, $underlyingType]])
               Some(Map($fieldNameStr -> lens))
             case _ => 
               None
           }
           val map = mapOpt.getOrElse(Map.empty)
           new _root_.zio.blocks.schema.DerivedOptics.OpticsHolder(map) {
             lazy val $fieldName: _root_.zio.blocks.schema.Lens[$sType, $underlyingType] = 
               map.getOrElse($fieldNameStr, throw new NoSuchElementException("Optic " + $fieldNameStr + " not found (schema is not a wrapper)")).asInstanceOf[_root_.zio.blocks.schema.Lens[$sType, $underlyingType]]
           }
        }
      )
    """
  }
}
