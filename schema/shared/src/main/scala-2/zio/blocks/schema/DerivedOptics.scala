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
 * object Person extends DerivedOptics[Person]
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
 * object Shape extends DerivedOptics[Shape]
 *
 * // Access prisms via the `optics` member:
 * val circlePrism: Prism[Shape, Circle] = Shape.optics.circle
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
   * an object with Lens accessors for each field. For sealed traits, returns an
   * object with Prism accessors for each variant.
   *
   * The returned object uses structural typing, so you get compile-time type
   * checking and IDE completion for the accessor names.
   */
  def optics(implicit schema: Schema[S]): Any = macro DerivedOpticsMacros.opticsImpl[S]
}

/**
 * A variant of DerivedOptics that prefixes all accessor names with underscore.
 * This is useful when you want to avoid name collisions with existing methods
 * in the companion object.
 *
 * Usage:
 * {{{
 * case class Person(name: String, age: Int)
 * object Person extends DerivedOptics_[Person]
 *
 * // Access optics with underscore prefix:
 * val nameLens: Lens[Person, String] = Person.optics._name
 * val ageLens: Lens[Person, Int] = Person.optics._age
 * }}}
 */
trait DerivedOptics_[S] {

  /**
   * Provides access to the derived optics for type S with underscore-prefixed
   * accessor names.
   */
  def optics(implicit schema: Schema[S]): Any = macro DerivedOpticsMacros.opticsImplUnderscore[S]
}

object DerivedOptics {
  private[schema] final class OpticsHolder(members: Map[String, Any]) extends scala.Dynamic {
    def selectDynamic(name: String): Any =
      members.getOrElse(name, throw new RuntimeException(s"No optic found for: $name"))
  }

  // Adapter to treat a Wrapper as a Record with one field named "value".
  // This allows reusing the optimized LensImpl logic without modifying Optic.scala.
  private[schema] def wrapperAsRecord[A, B](wrapper: Reflect.Wrapper.Bound[A, B]): Reflect.Record.Bound[A] = {
    import zio.blocks.schema.binding._

    val fieldTerm = new Term[Binding, A, B]("value", wrapper.wrapped)

    // Determine primitive type components
    val (usedRegs, reader, writer) = Reflect.unwrapToPrimitiveTypeOption(wrapper.wrapped) match {
      case Some(pt) =>
        pt match {
          case _: PrimitiveType.Boolean =>
            (
              RegisterOffset(booleans = 1),
              (r: Registers, o: Int) => r.getBoolean(o, 0),
              (r: Registers, o: Int, v: Any) => r.setBoolean(o, 0, v.asInstanceOf[Boolean])
            )
          case _: PrimitiveType.Byte =>
            (
              RegisterOffset(bytes = 1),
              (r: Registers, o: Int) => r.getByte(o, 0),
              (r: Registers, o: Int, v: Any) => r.setByte(o, 0, v.asInstanceOf[Byte])
            )
          case _: PrimitiveType.Short =>
            (
              RegisterOffset(shorts = 1),
              (r: Registers, o: Int) => r.getShort(o, 0),
              (r: Registers, o: Int, v: Any) => r.setShort(o, 0, v.asInstanceOf[Short])
            )
          case _: PrimitiveType.Int =>
            (
              RegisterOffset(ints = 1),
              (r: Registers, o: Int) => r.getInt(o, 0),
              (r: Registers, o: Int, v: Any) => r.setInt(o, 0, v.asInstanceOf[Int])
            )
          case _: PrimitiveType.Long =>
            (
              RegisterOffset(longs = 1),
              (r: Registers, o: Int) => r.getLong(o, 0),
              (r: Registers, o: Int, v: Any) => r.setLong(o, 0, v.asInstanceOf[Long])
            )
          case _: PrimitiveType.Float =>
            (
              RegisterOffset(floats = 1),
              (r: Registers, o: Int) => r.getFloat(o, 0),
              (r: Registers, o: Int, v: Any) => r.setFloat(o, 0, v.asInstanceOf[Float])
            )
          case _: PrimitiveType.Double =>
            (
              RegisterOffset(doubles = 1),
              (r: Registers, o: Int) => r.getDouble(o, 0),
              (r: Registers, o: Int, v: Any) => r.setDouble(o, 0, v.asInstanceOf[Double])
            )
          case _: PrimitiveType.Char =>
            (
              RegisterOffset(chars = 1),
              (r: Registers, o: Int) => r.getChar(o, 0),
              (r: Registers, o: Int, v: Any) => r.setChar(o, 0, v.asInstanceOf[Char])
            )
          case PrimitiveType.Unit =>
            (RegisterOffset(0), (_: Registers, _: Int) => (), (_: Registers, _: Int, _: Any) => ())
          case _ =>
            (
              RegisterOffset(objects = 1),
              (r: Registers, o: Int) => r.getObject(o, 0).asInstanceOf[B],
              (r: Registers, o: Int, v: Any) => r.setObject(o, 0, v.asInstanceOf[AnyRef])
            )
        }
      case None =>
        (
          RegisterOffset(objects = 1),
          (r: Registers, o: Int) => r.getObject(o, 0).asInstanceOf[B],
          (r: Registers, o: Int, v: Any) => r.setObject(o, 0, v.asInstanceOf[AnyRef])
        )
    }

    val syntheticBinding = new Binding.Record(
      constructor = new Constructor[A] {
        override def usedRegisters: Int = usedRegs

        override def construct(registers: Registers, offset: Int): A = {
          val b = reader(registers, offset).asInstanceOf[B]
          wrapper.binding.wrap(b) match {
            case Right(a)  => a
            case Left(err) => throw new RuntimeException(s"Wrapper validation failed: $err")
          }
        }
      },
      deconstructor = new Deconstructor[A] {
        override def usedRegisters: Int = usedRegs

        override def deconstruct(registers: Registers, offset: Int, value: A): Unit = {
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

  def opticsImpl[S: c.WeakTypeTag](c: whitebox.Context)(schema: c.Expr[Schema[S]]): c.Tree =
    opticsImplWithPrefix[S](c)(schema, prefixUnderscore = false)

  def opticsImplUnderscore[S: c.WeakTypeTag](c: whitebox.Context)(schema: c.Expr[Schema[S]]): c.Tree =
    opticsImplWithPrefix[S](c)(schema, prefixUnderscore = true)

  private def opticsImplWithPrefix[S: c.WeakTypeTag](c: whitebox.Context)(
    schema: c.Expr[Schema[S]],
    prefixUnderscore: Boolean
  ): c.Tree = {
    import c.universe._

    val originalType = weakTypeOf[S]
    val tpe          = originalType.dealias
    val typeSym      = tpe.typeSymbol.asClass

    val isCaseClass = typeSym.isCaseClass
    val isSealed    = typeSym.isSealed

    // Check for ZIO Prelude Newtype/Subtype by name to avoid dependency
    val baseClasses = originalType.baseClasses
    val isPrelude   = baseClasses.exists { sym =>
      val fullName = sym.fullName
      fullName == "zio.prelude.Newtype" || fullName == "zio.prelude.Subtype"
    }

    if (isCaseClass) {
      buildCaseClassOptics(c)(schema, tpe, prefixUnderscore)
    } else if (isSealed) {
      buildSealedTraitOptics(c)(schema, tpe, prefixUnderscore)
    } else if (isPrelude) {
      // Treat ZIO Prelude newtypes as wrappers
      // We need to find the underlying type. Usually it's the first type argument of the Newtype/Subtype trait
      // But extracting that reflectively in the macro might be hard without TypeTag for the newtype instance.
      // Fortunately, buildWrapperOptics mostly relies on the Schema structure at runtime.
      buildWrapperOptics(c)(schema, originalType, tpe, prefixUnderscore)
    } else {
      buildWrapperOptics(c)(schema, originalType, tpe, prefixUnderscore)
    }
  }

  private def buildCaseClassOptics[S: c.WeakTypeTag](c: whitebox.Context)(
    schema: c.Expr[Schema[S]],
    tpe: c.universe.Type,
    prefixUnderscore: Boolean
  ): c.Tree = {
    import c.universe._

    // Get case class fields from primary constructor
    val primaryConstructor = tpe.decls.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m
    }.getOrElse(c.abort(c.enclosingPosition, s"Could not find primary constructor for $tpe"))

    val fields = primaryConstructor.paramLists.flatten

    // Build method definitions for the anonymous class
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
          private val _schema: _root_.zio.blocks.schema.Schema[$tpe] = $schema
          ..$lensAccessors
        }
      )
    """
  }

  private def buildSealedTraitOptics[S: c.WeakTypeTag](c: whitebox.Context)(
    schema: c.Expr[Schema[S]],
    tpe: c.universe.Type,
    prefixUnderscore: Boolean
  ): c.Tree = {
    import c.universe._

    // Get direct subtypes
    val subtypes = CommonMacroOps.directSubTypes(c)(tpe)

    // Build method definitions for the anonymous class
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
          private val _schema: _root_.zio.blocks.schema.Schema[$tpe] = $schema
          ..$prismAccessors
        }
      )
    """
  }

  private def buildWrapperOptics[S: c.WeakTypeTag](c: whitebox.Context)(
    schema: c.Expr[Schema[S]],
    originalType: c.Type,
    underlyingType: c.Type,
    prefixUnderscore: Boolean
  ): c.Tree = {
    import c.universe._

    val fieldNameStr = if (prefixUnderscore) "_value" else "value"
    val fieldName    = TermName(fieldNameStr)
    val sType        = weakTypeOf[S]
    val cacheKey     = originalType.toString + (if (prefixUnderscore) "_" else "")

    q"""
      _root_.zio.blocks.schema.DerivedOpticsMacros.getOrCreate(
        $cacheKey, {
           val mapOpt = $schema.reflect match {
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
