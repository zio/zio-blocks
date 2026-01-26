package zio.blocks.schema.migration

import scala.annotation.tailrec
import scala.compiletime.*
import scala.deriving.Mirror
import scala.quoted.*

/**
 * Type-level field name extraction for compile-time migration validation.
 *
 * Provides utilities to extract field names from case classes at compile time
 * as tuple types, enabling type-level tracking of which fields have been
 * handled or provided in a migration.
 */
object FieldExtraction {

  /**
   * Typeclass for extracting field names from a type at compile time.
   * The Labels type member contains the field names as a tuple of string literal types.
   */
  sealed trait FieldNames[A] {
    type Labels <: Tuple
  }

  object FieldNames {

    /**
     * Concrete implementation of FieldNames with the Labels type member.
     * Public because it's used in transparent inline given.
     */
    class Impl[A, L <: Tuple] extends FieldNames[A] {
      type Labels = L
    }

    /**
     * Given instance that extracts field names from any type with a ProductOf Mirror.
     * This includes case classes, tuples, and other product types.
     */
    transparent inline given derived[A](using m: Mirror.ProductOf[A]): FieldNames[A] =
      new Impl[A, m.MirroredElemLabels]
  }

  /**
   * Extracts the field name from a selector function at compile time.
   *
   * {{{
   * case class Person(name: String, age: Int)
   *
   * val fieldName = extractFieldName[Person, String](_.name) // "name"
   * }}}
   *
   * For nested selectors, returns only the top-level field name:
   * {{{
   * case class Address(street: String)
   * case class Person(address: Address)
   *
   * val fieldName = extractFieldName[Person, String](_.address.street) // "address"
   * }}}
   */
  inline def extractFieldName[A, B](inline selector: A => B): String =
    ${ extractFieldNameImpl[A, B]('selector) }

  /**
   * Extracts the full field path from a selector function at compile time.
   *
   * For nested selectors, returns all field names in the path:
   * {{{
   * case class Address(street: String)
   * case class Person(address: Address)
   *
   * val path: List[String] = extractFieldPath[Person, String](_.address.street)
   * // path == List("address", "street")
   * }}}
   */
  inline def extractFieldPath[A, B](inline selector: A => B): List[String] =
    ${ extractFieldPathImpl[A, B]('selector) }

  /**
   * Gets the field names of a type at runtime as a tuple of strings.
   * This is useful when you need the actual values rather than just the types.
   *
   * {{{
   * case class Person(name: String, age: Int)
   *
   * val names = fieldNames[Person] // ("name", "age")
   * }}}
   */
  inline def fieldNames[A](using m: Mirror.ProductOf[A]): m.MirroredElemLabels =
    constValueTuple[m.MirroredElemLabels]

  // Implementation macros

  private def extractFieldNameImpl[A: Type, B: Type](
    selector: Expr[A => B]
  )(using q: Quotes): Expr[String] = {
    import q.reflect.*

    val fieldPath = extractFieldPathFromTerm(selector.asTerm)
    if (fieldPath.isEmpty) {
      report.errorAndAbort("Selector must access at least one field", selector.asTerm.pos)
    }
    // Return the first (top-level) field name
    Expr(fieldPath.head)
  }

  private def extractFieldPathImpl[A: Type, B: Type](
    selector: Expr[A => B]
  )(using q: Quotes): Expr[List[String]] = {
    import q.reflect.*

    val fieldPath = extractFieldPathFromTerm(selector.asTerm)
    if (fieldPath.isEmpty) {
      report.errorAndAbort("Selector must access at least one field", selector.asTerm.pos)
    }
    Expr(fieldPath)
  }

  private def extractFieldPathFromTerm(using q: Quotes)(term: q.reflect.Term): List[String] = {
    import q.reflect.*

    @tailrec
    def toPathBody(t: Term): Term = t match {
      case Inlined(_, _, inlinedBlock)                     => toPathBody(inlinedBlock)
      case Block(List(DefDef(_, _, _, Some(pathBody))), _) => pathBody
      case _ =>
        report.errorAndAbort(s"Expected a lambda expression, got '${t.show}'", t.pos)
    }

    def extractPath(t: Term, acc: List[String]): List[String] = t match {
      case Select(parent, fieldName) =>
        extractPath(parent, fieldName :: acc)
      case _: Ident =>
        acc
      case Typed(expr, _) =>
        extractPath(expr, acc)
      case _ =>
        report.errorAndAbort(
          s"Unsupported selector pattern: '${t.show}'. Only simple field access is supported (e.g., _.field or _.field.nested)",
          t.pos
        )
    }

    val pathBody = toPathBody(term)
    extractPath(pathBody, Nil)
  }
}
