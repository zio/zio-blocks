package zio.blocks.schema

import scala.language.experimental.macros
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

object DerivedOpticsMacros {
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

  def opticsImpl[S: c.WeakTypeTag](c: whitebox.Context)(schema: c.Expr[Schema[S]]): c.Tree = {
    import c.universe._

    val tpe     = weakTypeOf[S].dealias
    val typeSym = tpe.typeSymbol.asClass

    val isCaseClass = typeSym.isCaseClass
    val isSealed    = typeSym.isSealed

    if (isCaseClass) {
      buildCaseClassOptics(c)(schema, tpe)
    } else if (isSealed) {
      buildSealedTraitOptics(c)(schema, tpe)
    } else {
      val cacheKey = tpe.toString
      q"""
        _root_.zio.blocks.schema.DerivedOpticsMacros.getOrCreate(
          $cacheKey,
          new {}
        )
      """
    }
  }

  private def buildCaseClassOptics[S: c.WeakTypeTag](c: whitebox.Context)(
    schema: c.Expr[Schema[S]],
    tpe: c.universe.Type
  ): c.Tree = {
    import c.universe._

    // Get case class fields from primary constructor
    val primaryConstructor = tpe.decls.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m
    }.getOrElse(c.abort(c.enclosingPosition, s"Could not find primary constructor for $tpe"))

    val fields = primaryConstructor.paramLists.flatten

    // Build method definitions for the anonymous class
    val lensAccessors = fields.zipWithIndex.map { case (field, idx) =>
      val fieldName = field.name.toTermName
      val fieldType = field.typeSignatureIn(tpe).dealias
      val lensType  = appliedType(typeOf[Lens[_, _]].typeConstructor, List(tpe, fieldType))

      q"""
        lazy val $fieldName: $lensType = {
          val record = _schema.reflect.asRecord.getOrElse(
            throw new RuntimeException("Expected a record schema for " + ${tpe.toString})
          )
          record.lensByIndex[$fieldType]($idx).getOrElse(
            throw new RuntimeException("Cannot find lens for field " + ${fieldName.toString})
          )
        }
      """
    }

    val cacheKey = tpe.toString

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
    tpe: c.universe.Type
  ): c.Tree = {
    import c.universe._

    // Get direct subtypes
    val subtypes = CommonMacroOps.directSubTypes(c)(tpe)

    // Build method definitions for the anonymous class
    val prismAccessors = subtypes.zipWithIndex.map { case (subtype, idx) =>
      val caseName  = TermName(lowerFirst(subtype.typeSymbol.name.toString))
      val prismType = appliedType(typeOf[Prism[_, _]].typeConstructor, List(tpe, subtype))

      q"""
        lazy val $caseName: $prismType = {
          val variant = _schema.reflect.asVariant.getOrElse(
            throw new RuntimeException("Expected a variant schema for " + ${tpe.toString})
          )
          variant.prismByIndex[$subtype]($idx).getOrElse(
            throw new RuntimeException("Cannot find prism for case " + ${caseName.toString})
          )
        }
      """
    }

    val cacheKey = tpe.toString

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
}
