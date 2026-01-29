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
   * Typeclass for extracting field names from a type at compile time. The
   * Labels type member contains the field names as a tuple of string literal
   * types.
   *
   * Note: This extracts only top-level field names. For full nested path
   * extraction, use FieldPaths instead.
   */
  sealed trait FieldNames[A] {
    type Labels <: Tuple
  }

  object FieldNames {

    /**
     * Concrete implementation of FieldNames with the Labels type member. Public
     * because it's used in transparent inline given.
     */
    class Impl[A, L <: Tuple] extends FieldNames[A] {
      type Labels = L
    }

    /**
     * Given instance that extracts field names from any type with a ProductOf
     * Mirror. This includes case classes, tuples, and other product types.
     */
    transparent inline given derived[A](using m: Mirror.ProductOf[A]): FieldNames[A] =
      new Impl[A, m.MirroredElemLabels]
  }

  /**
   * Typeclass for extracting all field paths (including nested paths) from a
   * type at compile time. The Paths type member contains the paths as a tuple
   * of string literal types.
   *
   * For nested case classes, returns all dot-separated paths:
   * {{{
   * case class Address(street: String, city: String)
   * case class Person(name: String, address: Address)
   *
   * FieldPaths[Person].Paths =:= ("address", "address.city", "address.street", "name")
   * }}}
   */
  sealed trait FieldPaths[A] {
    type Paths <: Tuple
  }

  object FieldPaths {

    /**
     * Concrete implementation of FieldPaths with the Paths type member. Public
     * because it's used in transparent inline given.
     */
    class Impl[A, P <: Tuple] extends FieldPaths[A] {
      type Paths = P
    }

    /**
     * Given instance that extracts all field paths from a type at compile time.
     * Uses ShapeExtraction to get nested paths and converts them to a Tuple
     * type.
     */
    transparent inline given derived[A]: FieldPaths[A] = ${ derivedImpl[A] }

    /**
     * Derive FieldPaths for a specific case of a sealed trait/enum. This is
     * used internally for validating case field changes in transformCase.
     */
    transparent inline def forCase[A]: FieldPaths[A] = ${ derivedImpl[A] }

    private def derivedImpl[A: Type](using q: Quotes): Expr[FieldPaths[A]] = {
      import q.reflect.*

      // Use ShapeExtraction's internal logic to get paths
      val tpe   = TypeRepr.of[A].dealias
      val paths = extractFieldPathsFromType(tpe, "", Set.empty).sorted

      // Create a tuple type from the paths
      val tupleType = pathsToTupleType(paths)

      tupleType.asType match {
        case '[t] =>
          '{ new FieldPaths.Impl[A, t & Tuple] }
      }
    }

    /**
     * Convert a list of path strings to a Tuple type.
     */
    private def pathsToTupleType(using q: Quotes)(paths: List[String]): q.reflect.TypeRepr = {
      import q.reflect.*

      paths.foldRight(TypeRepr.of[EmptyTuple]) { (path, acc) =>
        val pathType = ConstantType(StringConstant(path))
        TypeRepr.of[*:].appliedTo(List(pathType, acc))
      }
    }

    /**
     * Extract all field paths from a type, recursively descending into nested
     * case classes. This duplicates ShapeExtraction logic to avoid cyclic
     * dependencies.
     */
    private def extractFieldPathsFromType(using
      q: Quotes
    )(tpe: q.reflect.TypeRepr, prefix: String, visiting: Set[String]): List[String] = {
      import q.reflect.*

      val dealiased = tpe.dealias
      val typeKey   = dealiased.typeSymbol.fullName

      // Check for recursion
      if (visiting.contains(typeKey)) {
        report.errorAndAbort(
          s"Recursive type detected: ${dealiased.show}. " +
            s"Migration validation does not support recursive types. " +
            s"Recursion path: ${visiting.mkString(" -> ")} -> $typeKey"
        )
      }

      // Skip extraction for container types and primitives
      if (isContainerType(dealiased) || isPrimitiveType(dealiased)) {
        return Nil
      }

      // Only extract fields from product types (case classes)
      if (!isProductType(dealiased.typeSymbol)) {
        return Nil
      }

      val newVisiting = visiting + typeKey
      val fields      = getProductFields(dealiased)

      fields.flatMap { case (fieldName, fieldType) =>
        val fullPath    = if (prefix.isEmpty) fieldName else s"$prefix$fieldName"
        val nestedPaths = extractFieldPathsFromType(fieldType, s"$fullPath.", newVisiting)
        fullPath :: nestedPaths
      }
    }

    /**
     * Check if a type is a product type (case class, but not abstract).
     */
    private def isProductType(using q: Quotes)(symbol: q.reflect.Symbol): Boolean = {
      import q.reflect.*
      symbol.flags.is(Flags.Case) && !symbol.flags.is(Flags.Abstract)
    }

    /**
     * Check if a type is a container type (Option, List, Vector, Set, Map,
     * etc.) that should not be recursed into.
     */
    private def isContainerType(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
      import q.reflect.*

      val containerTypes = List(
        TypeRepr.of[Option[?]],
        TypeRepr.of[List[?]],
        TypeRepr.of[Vector[?]],
        TypeRepr.of[Set[?]],
        TypeRepr.of[Seq[?]],
        TypeRepr.of[IndexedSeq[?]],
        TypeRepr.of[Iterable[?]],
        TypeRepr.of[Map[?, ?]],
        TypeRepr.of[Array[?]]
      )

      containerTypes.exists(ct => tpe <:< ct)
    }

    /**
     * Check if a type is a primitive type that should not be recursed into.
     */
    private def isPrimitiveType(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
      import q.reflect.*

      val primitiveTypes = List(
        TypeRepr.of[Boolean],
        TypeRepr.of[Byte],
        TypeRepr.of[Short],
        TypeRepr.of[Int],
        TypeRepr.of[Long],
        TypeRepr.of[Float],
        TypeRepr.of[Double],
        TypeRepr.of[Char],
        TypeRepr.of[String],
        TypeRepr.of[java.math.BigInteger],
        TypeRepr.of[java.math.BigDecimal],
        TypeRepr.of[BigInt],
        TypeRepr.of[BigDecimal],
        TypeRepr.of[java.util.UUID],
        TypeRepr.of[java.time.Instant],
        TypeRepr.of[java.time.LocalDate],
        TypeRepr.of[java.time.LocalTime],
        TypeRepr.of[java.time.LocalDateTime],
        TypeRepr.of[java.time.OffsetDateTime],
        TypeRepr.of[java.time.ZonedDateTime],
        TypeRepr.of[java.time.Duration],
        TypeRepr.of[java.time.Period],
        TypeRepr.of[java.time.Year],
        TypeRepr.of[java.time.YearMonth],
        TypeRepr.of[java.time.MonthDay],
        TypeRepr.of[java.time.ZoneId],
        TypeRepr.of[java.time.ZoneOffset],
        TypeRepr.of[Unit],
        TypeRepr.of[Nothing]
      )

      primitiveTypes.exists(pt => tpe =:= pt)
    }

    /**
     * Get the fields of a product type as (name, type) pairs.
     */
    private def getProductFields(using q: Quotes)(tpe: q.reflect.TypeRepr): List[(String, q.reflect.TypeRepr)] = {
      val symbol = tpe.typeSymbol

      // Get primary constructor
      val constructor = symbol.primaryConstructor
      if (constructor.isNoSymbol) return Nil

      // Get constructor parameter lists
      val paramLists = constructor.paramSymss

      // Filter to term parameters (not type parameters)
      val termParams = paramLists.flatten.filter(_.isTerm)

      termParams.map { param =>
        val paramName = param.name
        val paramType = tpe.memberType(param)
        (paramName, paramType.dealias)
      }
    }
  }

  /**
   * Typeclass for extracting case names from a sealed trait/enum at compile
   * time. The Cases type member contains the case names as a tuple of string
   * literal types with "case:" prefix.
   *
   * For sealed traits/enums:
   * {{{
   * sealed trait Result
   * case class Success(value: Int) extends Result
   * case class Failure(error: String) extends Result
   *
   * CasePaths[Result].Cases =:= ("case:Failure", "case:Success")
   * }}}
   *
   * For non-sealed types, Cases is EmptyTuple.
   */
  sealed trait CasePaths[A] {
    type Cases <: Tuple
  }

  object CasePaths {

    /**
     * Concrete implementation of CasePaths with the Cases type member. Public
     * because it's used in transparent inline given.
     */
    class Impl[A, C <: Tuple] extends CasePaths[A] {
      type Cases = C
    }

    /**
     * Given instance that extracts all case names from a type at compile time.
     * Returns case names with "case:" prefix for sealed traits/enums, or
     * EmptyTuple for non-sealed types.
     */
    transparent inline given derived[A]: CasePaths[A] = ${ derivedImpl[A] }

    private def derivedImpl[A: Type](using q: Quotes): Expr[CasePaths[A]] = {
      import q.reflect.*

      val tpe       = TypeRepr.of[A].dealias
      val caseNames = extractCaseNamesFromType(tpe).sorted.map(name => s"case:$name")

      // Create a tuple type from the case names
      val tupleType = caseNamesToTupleType(caseNames)

      tupleType.asType match {
        case '[t] =>
          '{ new CasePaths.Impl[A, t & Tuple] }
      }
    }

    /**
     * Convert a list of case name strings to a Tuple type.
     */
    private def caseNamesToTupleType(using q: Quotes)(names: List[String]): q.reflect.TypeRepr = {
      import q.reflect.*

      names.foldRight(TypeRepr.of[EmptyTuple]) { (name, acc) =>
        val nameType = ConstantType(StringConstant(name))
        TypeRepr.of[*:].appliedTo(List(nameType, acc))
      }
    }

    /**
     * Extract case names from a sealed trait or enum.
     */
    private def extractCaseNamesFromType(using q: Quotes)(tpe: q.reflect.TypeRepr): List[String] = {
      val dealiased = tpe.dealias

      if (isSealedTraitOrEnum(dealiased)) {
        val subTypes = directSubTypes(dealiased)
        subTypes.map(getCaseName)
      } else {
        Nil
      }
    }

    /**
     * Check if a type is a sealed trait, abstract class, or enum.
     */
    private def isSealedTraitOrEnum(using q: Quotes)(tpe: q.reflect.TypeRepr): Boolean = {
      import q.reflect.*

      tpe.classSymbol.fold(false) { symbol =>
        val flags = symbol.flags
        (flags.is(Flags.Sealed) && (flags.is(Flags.Abstract) || flags.is(Flags.Trait))) ||
        flags.is(Flags.Enum)
      }
    }

    /**
     * Get the name of a case from its type.
     */
    private def getCaseName(using q: Quotes)(tpe: q.reflect.TypeRepr): String = {
      import q.reflect.*

      if (tpe.termSymbol.flags.is(Flags.Enum)) {
        tpe.termSymbol.name
      } else {
        tpe.typeSymbol.name
      }
    }

    /**
     * Get the direct subtypes of a sealed trait or enum.
     */
    private def directSubTypes(using q: Quotes)(tpe: q.reflect.TypeRepr): List[q.reflect.TypeRepr] = {
      import q.reflect.*

      val symbol   = tpe.typeSymbol
      val children = symbol.children

      children.map { child =>
        if (child.isType) {
          child.typeRef
        } else {
          Ref(child).tpe
        }
      }
    }
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
   * Gets the field names of a type at runtime as a tuple of strings. This is
   * useful when you need the actual values rather than just the types.
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
      case _                                               =>
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
