package zio.blocks.schema.migration

import scala.annotation.tailrec
import scala.quoted.*

/**
 * Compile-time shape extraction for migration validation.
 *
 * Extracts the complete structure of a type at compile time, including:
 *   - All field paths (including nested paths like "address.street")
 *   - Sealed trait/enum case names
 *   - Field paths within each case
 *
 * This enables validation that migrations handle all structural changes between
 * source and target types.
 */
object ShapeExtraction {

  /**
   * Represents the complete shape of a type.
   *
   * @param fieldPaths
   *   All dot-separated field paths in sorted order (e.g., List("address",
   *   "address.city", "address.street", "name"))
   * @param caseNames
   *   Case names for sealed traits/enums in sorted order (e.g., List("Failure",
   *   "Success"))
   * @param caseFieldPaths
   *   Field paths for each case, keyed by case name (e.g., Map("Success" ->
   *   List("value"), "Failure" -> List("error")))
   */
  case class Shape(
    fieldPaths: List[String],
    caseNames: List[String],
    caseFieldPaths: Map[String, List[String]]
  )

  /**
   * Extract all field paths from a type at compile time.
   *
   * For nested case classes, returns all dot-separated paths:
   * {{{
   * case class Address(street: String, city: String)
   * case class Person(name: String, address: Address)
   *
   * extractFieldPaths[Person]
   * // Returns: List("address", "address.city", "address.street", "name")
   * }}}
   *
   * For recursive types, produces a compile-time error.
   */
  inline def extractFieldPaths[A]: List[String] = ${ extractFieldPathsMacro[A] }

  /**
   * Extract case names from a sealed trait or enum at compile time.
   *
   * {{{
   * sealed trait Result
   * case class Success(value: Int) extends Result
   * case class Failure(error: String) extends Result
   *
   * extractCaseNames[Result]
   * // Returns: List("Failure", "Success")
   * }}}
   *
   * For non-sealed types, returns an empty list.
   */
  inline def extractCaseNames[A]: List[String] = ${ extractCaseNamesMacro[A] }

  /**
   * Extract the complete shape of a type at compile time.
   *
   * Combines field paths, case names, and case field paths into a single Shape
   * object. For sealed traits, extracts both the case names and the field paths
   * within each case.
   *
   * {{{
   * sealed trait Payment
   * case class Card(number: String, expiry: String) extends Payment
   * case class Cash(amount: Int) extends Payment
   *
   * extractShape[Payment]
   * // Returns: Shape(
   * //   fieldPaths = List(),
   * //   caseNames = List("Card", "Cash"),
   * //   caseFieldPaths = Map(
   * //     "Card" -> List("expiry", "number"),
   * //     "Cash" -> List("amount")
   * //   )
   * // )
   * }}}
   */
  inline def extractShape[A]: Shape = ${ extractShapeMacro[A] }

  private def extractFieldPathsMacro[A: Type](using q: Quotes): Expr[List[String]] = {
    import q.reflect.*

    val tpe    = TypeRepr.of[A].dealias
    val paths  = MacroHelpers.extractFieldPathsFromType(tpe, "", Set.empty, "Migration shape extraction")
    val sorted = paths.sorted
    Expr(sorted)
  }

  private def extractCaseNamesMacro[A: Type](using q: Quotes): Expr[List[String]] = {
    import q.reflect.*

    val tpe   = TypeRepr.of[A].dealias
    val names = MacroHelpers.extractCaseNamesFromType(tpe)
    Expr(names.sorted)
  }

  private def extractShapeMacro[A: Type](using q: Quotes): Expr[Shape] = {
    import q.reflect.*

    val tpe = TypeRepr.of[A].dealias

    // Extract field paths (for product types)
    val fieldPaths = MacroHelpers.extractFieldPathsFromType(tpe, "", Set.empty, "Migration shape extraction").sorted

    // Extract case names (for sum types)
    val caseNames = MacroHelpers.extractCaseNamesFromType(tpe).sorted

    // Extract field paths for each case
    val caseFieldPaths: Map[String, List[String]] =
      if (MacroHelpers.isSealedTraitOrEnum(tpe)) {
        val subTypes = MacroHelpers.directSubTypes(tpe)
        subTypes.map { subTpe =>
          val caseName  = MacroHelpers.getCaseName(subTpe)
          val casePaths =
            MacroHelpers.extractFieldPathsFromType(subTpe, "", Set.empty, "Migration shape extraction").sorted
          caseName -> casePaths
        }.toMap
      } else {
        Map.empty
      }

    val fieldPathsExpr     = Expr(fieldPaths)
    val caseNamesExpr      = Expr(caseNames)
    val caseFieldPathsExpr = Expr(caseFieldPaths)

    '{ Shape($fieldPathsExpr, $caseNamesExpr, $caseFieldPathsExpr) }
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
     * Uses MacroHelpers to get nested paths and converts them to a Tuple type.
     */
    transparent inline given derived[A]: FieldPaths[A] = ${ derivedImpl[A] }

    /**
     * Derive FieldPaths for a specific case of a sealed trait/enum. This is
     * used internally for validating case field changes in transformCase.
     */
    transparent inline def forCase[A]: FieldPaths[A] = ${ derivedImpl[A] }

    private def derivedImpl[A: Type](using q: Quotes): Expr[FieldPaths[A]] = {
      import q.reflect.*

      // Use MacroHelpers to get paths
      val tpe   = TypeRepr.of[A].dealias
      val paths = MacroHelpers.extractFieldPathsFromType(tpe, "", Set.empty).sorted

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
    transparent inline given derived[A]: CasePaths[A] = ${ casesDerivdImpl[A] }

    private def casesDerivdImpl[A: Type](using q: Quotes): Expr[CasePaths[A]] = {
      import q.reflect.*

      val tpe       = TypeRepr.of[A].dealias
      val caseNames = MacroHelpers.extractCaseNamesFromType(tpe).sorted.map(name => s"case:$name")

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

  // Implementation macros for field extraction

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
