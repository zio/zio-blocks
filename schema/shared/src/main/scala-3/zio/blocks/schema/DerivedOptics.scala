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
  transparent inline def optics(using schema: Schema[S]): Any = ${ DerivedOpticsMacros.opticsImpl[S]('schema, true) }
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

  // Helper to lower-case the first letter of a name (per issue #514 requirement)
  private def lowerFirst(s: String): String =
    if (s.isEmpty) s else s.head.toLower.toString + s.tail

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

  def opticsImpl[S: Type](schema: Expr[Schema[S]], prefixUnderscore: Boolean)(using q: Quotes): Expr[Any] = {
    import q.reflect.*

    val tpe = TypeRepr.of[S]

    val caseClassType = tpe.dealias
    val caseClassSym  = caseClassType.typeSymbol

    // Check if it's a case class or sealed trait (including Scala 3 enums)
    val isCaseClass = caseClassSym.flags.is(Flags.Case)
    val isSealed    = caseClassSym.flags.is(Flags.Sealed)
    val isEnum      = caseClassSym.flags.is(Flags.Enum)

    if (isCaseClass) {
      buildCaseClassOptics[S](schema, caseClassSym, caseClassType, prefixUnderscore)
    } else if (isSealed || isEnum) {
      buildSealedTraitOptics[S](schema, caseClassSym, caseClassType, prefixUnderscore)
    } else if (tpe != caseClassType) {
      buildWrapperOptics[S](schema, tpe, caseClassType, prefixUnderscore)
    } else {
      // For primitives or other types that are not wrappers, return an empty OpticsHolder
      val cacheKey: Expr[String] = Expr(caseClassType.show + (if (prefixUnderscore) "_" else ""))
      '{ getOrCreate($cacheKey, new OpticsHolder(Map.empty)) }
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
        val fieldName = if (prefixUnderscore) "_value" else "value"

        // Create the refinement type: OpticsHolder { val value: Lens[S, u] }
        val lensType    = TypeRepr.of[Lens[S, u]]
        val refinedType = Refinement(TypeRepr.of[OpticsHolder], fieldName, lensType)

        refinedType.asType match {
          case '[rt] =>
            val cacheKey = Expr(tpe.show + (if (prefixUnderscore) "_" else ""))

            '{
              getOrCreate(
                $cacheKey, {
                  // Runtime check: try to treat the schema as a wrapper
                  val reflect   = $schema.reflect
                  val opticsMap = if (reflect.isWrapper) {
                    val w    = reflect.asInstanceOf[_root_.zio.blocks.schema.Reflect.Wrapper.Bound[S, u]]
                    val lens = Lens.wrapped(w)
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

    val symCast = sym.asInstanceOf[Symbol]
    val tpeCast = tpe.asInstanceOf[TypeRepr]

    val fields = symCast.caseFields

    // Build the structural refinement type
    // Start with OpticsHolder as the base type
    var refinedType: TypeRepr = TypeRepr.of[OpticsHolder]

    // Add a refinement for each field: def fieldName: Lens[S, FieldType]
    for (field <- fields) {
      val fieldType    = tpeCast.memberType(field).dealias
      val lensType     = TypeRepr.of[Lens].appliedTo(List(tpeCast, fieldType))
      val accessorName = if (prefixUnderscore) "_" + field.name else field.name
      refinedType = Refinement(refinedType, accessorName, lensType)
    }

    // Get unique type string at compile time for the cache key
    // We use show to ensure generic types like Box[Int] and Box[String] have different keys
    val cacheKey: Expr[String]              = Expr(tpeCast.show + (if (prefixUnderscore) "_" else ""))
    val prefixUnderscoreExpr: Expr[Boolean] = Expr(prefixUnderscore)

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
                val name = if ($prefixUnderscoreExpr) "_" + term.name else term.name
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

    val symCast = sym.asInstanceOf[Symbol]
    val tpeCast = tpe.asInstanceOf[TypeRepr]

    val children = symCast.children

    // Build the structural refinement type
    var refinedType: TypeRepr = TypeRepr.of[OpticsHolder]

    // Add a refinement for each child: def childName: Prism[S, ChildType]
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

    // Get unique type string at compile time for the cache key
    val cacheKey: Expr[String]              = Expr(tpeCast.show + (if (prefixUnderscore) "_" else ""))
    val prefixUnderscoreExpr: Expr[Boolean] = Expr(prefixUnderscore)

    // Match the refined type and create the implementation
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
                val name     = if (prefixUs) "_" + baseName else baseName
                name -> prism
              }.toMap
              new OpticsHolder(members)
            }
          ).asInstanceOf[t]
        }
    }
  }
}
