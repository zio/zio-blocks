package zio.blocks.schema

import scala.language.experimental.macros
import scala.language.dynamics
import scala.reflect.macros.whitebox

/**
 * A trait that can be extended by companion objects to automatically derive
 * optics (Lens for case class fields, Prism for sealed trait/enum variants).
 *
 * Usage:
 * {{{
 * case class Person(name: String, age: Int)
 * object Person extends DerivedOptics {
 *   implicit val schema: Schema[Person] = Schema.derived
 * }
 *
 * // Access optics via the `optics` member:
 * val nameLens: Lens[Person, String] = Person.optics.name
 * val ageLens: Lens[Person, Int] = Person.optics.age
 * }}}
 *
 * For sealed traits:
 * {{{
 * sealed trait Shape
 * case class Circle(radius: Double) extends Shape
 * case class Rectangle(width: Double, height: Double) extends Shape
 * object Shape extends DerivedOptics {
 *   implicit val schema: Schema[Shape] = Schema.derived
 * }
 *
 * // Access prisms via the `optics` member:
 * val circlePrism: Prism[Shape, Circle] = Shape.optics.circle
 * }}}
 *
 * Note: In Scala 3, direct field access is also supported (e.g., `Person.name`
 * instead of `Person.optics.name`). This is not available in Scala 2.
 *
 * The optics object is cached to avoid recreation on every access.
 */
trait DerivedOptics {

  /**
   * Provides access to the derived optics for the companion class type. For
   * case classes, returns an object with Lens accessors for each field. For
   * sealed traits, returns an object with Prism accessors for each variant.
   *
   * The returned object uses structural typing, so you get compile-time type
   * checking and IDE completion for the accessor names.
   *
   * This macro infers the type from the companion object.
   */
  def optics: Any = macro DerivedOpticsMacros.opticsFromCompanionImpl
}

/**
 * A parameterized version of DerivedOptics for cases where the type cannot be
 * automatically inferred from the companion object, such as:
 *   - Generic instantiations:
 *     `object BoxInt extends DerivedOptics.Of[Box[Int]]`
 *   - Type aliases: `object AliasedPerson extends DerivedOptics.Of[AP]`
 *   - Non-companion objects providing optics for a type
 */
object DerivedOptics {

  /**
   * Use `DerivedOptics.Of[T]` when you need to explicitly specify the type, for
   * example with generic types or type aliases.
   *
   * Example:
   * {{{
   * case class Box[A](value: A)
   * object BoxInt extends DerivedOptics.Of[Box[Int]] {
   *   implicit val schema: Schema[Box[Int]] = Schema.derived
   * }
   * }}}
   */
  trait Of[S] {
    def optics(implicit schema: Schema[S]): Any = macro DerivedOpticsMacros.opticsImpl[S]
  }

  final class OpticsHolder(members: Map[String, Any]) extends scala.Dynamic {
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
      }
    )

    Reflect.Record(
      fields = IndexedSeq(fieldTerm.asInstanceOf[Term[Binding, A, ?]]),
      typeName = wrapper.typeName,
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
