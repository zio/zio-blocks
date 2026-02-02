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

/**
 * A path is a sequence of segments representing a location within a ShapeNode
 * tree. (Scala 3 top-level type alias for convenience)
 */
type Path = List[Segment]

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

  /**
   * Extract the hierarchical shape tree of a type at compile time.
   *
   * Returns a ShapeNode tree representing the complete structure of the type:
   * {{{
   * case class Address(street: String, city: String)
   * case class Person(name: String, address: Address)
   *
   * extractShapeTree[Person]
   * // Returns: RecordNode(Map(
   * //   "name" -> PrimitiveNode,
   * //   "address" -> RecordNode(Map(
   * //     "street" -> PrimitiveNode,
   * //     "city" -> PrimitiveNode
   * //   ))
   * // ))
   * }}}
   *
   * For recursive types, produces a compile-time error.
   */
  inline def extractShapeTree[A]: ShapeNode = ${ extractShapeTreeMacro[A] }

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

  private def extractShapeTreeMacro[A: Type](using q: Quotes): Expr[ShapeNode] = {
    import q.reflect.*

    val tpe  = TypeRepr.of[A].dealias
    val tree = MacroHelpers.extractShapeTree(tpe, Set.empty, "Shape tree extraction")
    shapeNodeToExpr(tree)
  }

  /** Convert a ShapeNode to an Expr at compile time. */
  private def shapeNodeToExpr(using q: Quotes)(node: ShapeNode): Expr[ShapeNode] =
    node match {
      case ShapeNode.PrimitiveNode =>
        '{ ShapeNode.PrimitiveNode }

      case ShapeNode.RecordNode(fields) =>
        val fieldExprs = fields.toList.map { case (name, child) =>
          val nameExpr  = Expr(name)
          val childExpr = shapeNodeToExpr(child)
          '{ ($nameExpr, $childExpr) }
        }
        val fieldsExpr = Expr.ofList(fieldExprs)
        '{ ShapeNode.RecordNode($fieldsExpr.toMap) }

      case ShapeNode.SealedNode(cases) =>
        val caseExprs = cases.toList.map { case (name, child) =>
          val nameExpr  = Expr(name)
          val childExpr = shapeNodeToExpr(child)
          '{ ($nameExpr, $childExpr) }
        }
        val casesExpr = Expr.ofList(caseExprs)
        '{ ShapeNode.SealedNode($casesExpr.toMap) }

      case ShapeNode.SeqNode(element) =>
        val elementExpr = shapeNodeToExpr(element)
        '{ ShapeNode.SeqNode($elementExpr) }

      case ShapeNode.OptionNode(element) =>
        val elementExpr = shapeNodeToExpr(element)
        '{ ShapeNode.OptionNode($elementExpr) }

      case ShapeNode.MapNode(key, value) =>
        val keyExpr   = shapeNodeToExpr(key)
        val valueExpr = shapeNodeToExpr(value)
        '{ ShapeNode.MapNode($keyExpr, $valueExpr) }

      case ShapeNode.WrappedNode(inner) =>
        val innerExpr = shapeNodeToExpr(inner)
        '{ ShapeNode.WrappedNode($innerExpr) }
    }

  /**
   * Typeclass for extracting the hierarchical shape tree of a type at compile
   * time.
   *
   * ShapeTree provides a unified view that replaces both FieldPaths and
   * CasePaths:
   *   - Fields are represented as RecordNode entries
   *   - Cases are represented as SealedNode entries
   *   - Container types (List, Option, Map) are represented as their respective
   *     nodes
   *
   * Use TreeDiff.diff to compare two ShapeTree instances and identify
   * structural changes.
   *
   * {{{
   * case class Address(street: String, city: String)
   * case class Person(name: String, address: Address)
   *
   * val tree = summon[ShapeTree[Person]].tree
   * // tree = RecordNode(Map(
   * //   "name" -> PrimitiveNode,
   * //   "address" -> RecordNode(Map(
   * //     "street" -> PrimitiveNode,
   * //     "city" -> PrimitiveNode
   * //   ))
   * // ))
   * }}}
   */
  sealed trait ShapeTree[A] {

    /** The hierarchical shape tree for type A. */
    def tree: ShapeNode
  }

  object ShapeTree {

    /** Concrete implementation of ShapeTree. */
    final class Impl[A](val tree: ShapeNode) extends ShapeTree[A]

    /**
     * Given instance that extracts the shape tree from a type at compile time.
     */
    inline given derived[A]: ShapeTree[A] = ${ derivedImpl[A] }

    private def derivedImpl[A: Type](using q: Quotes): Expr[ShapeTree[A]] = {
      import q.reflect.*

      val tpe  = TypeRepr.of[A].dealias
      val tree = MacroHelpers.extractShapeTree(tpe, Set.empty, "ShapeTree derivation")

      // Convert ShapeNode to Expr - uses the shapeNodeToExpr from enclosing ShapeExtraction object
      val treeExpr = ShapeExtraction.shapeNodeToExpr(tree)
      '{ new ShapeTree.Impl[A]($treeExpr) }
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
