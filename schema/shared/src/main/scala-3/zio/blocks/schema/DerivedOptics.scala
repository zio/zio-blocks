package zio.blocks.schema

import scala.quoted.*
import scala.language.dynamics

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
 * You can also use Scala 3's `export` to make optics top-level in the
 * companion:
 * {{{
 * case class Person(name: String, age: Int)
 * object Person extends DerivedOptics[Person] {
 *   export optics.*
 * }
 *
 * // Now access optics directly:
 * val nameLens: Lens[Person, String] = Person.name
 * val ageLens: Lens[Person, Int] = Person.age
 * }}}
 *
 * For sealed traits/enums:
 * {{{
 * sealed trait Shape
 * case class Circle(radius: Double) extends Shape
 * case class Rectangle(width: Double, height: Double) extends Shape
 * object Shape extends DerivedOptics[Shape]
 *
 * // Access prisms via the `optics` member:
 * val circlePrism: Prism[Shape, Circle] = Shape.optics.Circle
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
  transparent inline def optics(using schema: Schema[S]): Any = ${ DerivedOpticsMacros.opticsImpl[S]('schema) }
}

/**
 * An optics holder that stores lenses/prisms in a map and provides dynamic
 * access. This is an implementation detail and should not be used directly.
 */
private[schema] final class OpticsHolder(members: Map[String, Any]) extends scala.Dynamic with Selectable {
  def selectDynamic(name: String): Any =
    members.getOrElse(name, throw new RuntimeException(s"No optic found for: $name"))
}

private object DerivedOpticsMacros {
  import java.util.concurrent.ConcurrentHashMap

  // Global cache to avoid recreating optics objects at runtime
  // Key is the type's full name as a string
  private val cache = new ConcurrentHashMap[String, OpticsHolder]()

  def getOrCreate(key: String, create: => OpticsHolder): OpticsHolder = {
    var result = cache.get(key)
    if (result == null) {
      result = create
      val existing = cache.putIfAbsent(key, result)
      if (existing != null) result = existing
    }
    result
  }

  def opticsImpl[S: Type](schema: Expr[Schema[S]])(using q: Quotes): Expr[Any] = {
    import q.reflect.*

    val tpe = TypeRepr.of[S]

    val caseClassType = tpe.dealias
    val caseClassSym  = caseClassType.typeSymbol

    // Check if it's a case class or sealed trait (including Scala 3 enums)
    val isCaseClass = caseClassSym.flags.is(Flags.Case)
    val isSealed    = caseClassSym.flags.is(Flags.Sealed)
    val isEnum      = caseClassSym.flags.is(Flags.Enum)

    if (isCaseClass) {
      buildCaseClassOptics[S](schema, caseClassSym, caseClassType)
    } else if (isSealed || isEnum) {
      buildSealedTraitOptics[S](schema, caseClassSym, caseClassType)
    } else {
      // For opaque types, primitives, or other types, return an empty OpticsHolder
      val cacheKey: Expr[String] = Expr(caseClassType.show)
      '{ getOrCreate($cacheKey, new OpticsHolder(Map.empty)) }
    }
  }

  private def buildCaseClassOptics[S: Type](
    schema: Expr[Schema[S]],
    sym: Quotes#reflectModule#Symbol,
    tpe: Quotes#reflectModule#TypeRepr
  )(using q: Quotes): Expr[Any] = {
    import q.reflect.*

    val symCast = sym.asInstanceOf[Symbol]
    val tpeCast = tpe.asInstanceOf[TypeRepr]

    val fields = symCast.caseFields

    // Build the structural refinement type
    // Start with OpticsHolder as the base type
    var refinedType: TypeRepr = TypeRepr.of[OpticsHolder]

    // Add a refinement for each field: def fieldName: Lens[S, FieldType]
    for (field <- fields) {
      val fieldType = tpeCast.memberType(field).dealias
      val lensType  = TypeRepr.of[Lens].appliedTo(List(tpeCast, fieldType))
      refinedType = Refinement(refinedType, field.name, lensType)
    }

    // Get unique type string at compile time for the cache key
    // We use show to ensure generic types like Box[Int] and Box[String] have different keys
    val cacheKey: Expr[String] = Expr(tpeCast.show)

    // Match the refined type and create the implementation
    refinedType.asType match {
      case '[t] =>
        '{
          getOrCreate(
            $cacheKey, {
              val record = $schema.reflect.asRecord.getOrElse(
                throw new RuntimeException(s"Expected a record schema for ${$cacheKey}")
              )
              val members = record.fields.zipWithIndex.map { case (term, idx) =>
                val lens = record
                  .lensByIndex(idx)
                  .getOrElse(
                    throw new RuntimeException(s"Cannot find lens for field ${term.name}")
                  )
                term.name -> lens
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
    tpe: Quotes#reflectModule#TypeRepr
  )(using q: Quotes): Expr[Any] = {
    import q.reflect.*

    val symCast = sym.asInstanceOf[Symbol]
    val tpeCast = tpe.asInstanceOf[TypeRepr]

    val children = symCast.children

    // Build the structural refinement type
    var refinedType: TypeRepr = TypeRepr.of[OpticsHolder]

    // Add a refinement for each child: def ChildName: Prism[S, ChildType]
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
      val prismType = TypeRepr.of[Prism].appliedTo(List(tpeCast, childType))
      refinedType = Refinement(refinedType, child.name, prismType)
    }

    // Get unique type string at compile time for the cache key
    val cacheKey: Expr[String] = Expr(tpeCast.show)

    // Match the refined type and create the implementation
    refinedType.asType match {
      case '[t] =>
        '{
          getOrCreate(
            $cacheKey, {
              val variant = $schema.reflect.asVariant.getOrElse(
                throw new RuntimeException(s"Expected a variant schema for ${$cacheKey}")
              )
              val members = variant.cases.zipWithIndex.map { case (term, idx) =>
                val prism = variant
                  .prismByIndex(idx)
                  .getOrElse(
                    throw new RuntimeException(s"Cannot find prism for case ${term.name}")
                  )
                term.name -> prism
              }.toMap
              new OpticsHolder(members)
            }
          ).asInstanceOf[t]
        }
    }
  }
}
