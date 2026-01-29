package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic

import scala.language.experimental.macros

/**
 * A type class that converts a selector function (A => B) to a DynamicOptic.
 *
 * This is the core mechanism that enables the user-facing API to accept
 * selector expressions like `_.fieldName` instead of raw DynamicOptic values.
 *
 * The implementation uses macros to analyze the selector expression at compile
 * time and extract the path information.
 *
 * @tparam A the source type
 * @tparam B the target type of the selector
 */
trait ToDynamicOptic[A, B] {

  /**
   * The extracted DynamicOptic path.
   */
  def optic: DynamicOptic
}

object ToDynamicOptic {

  /**
   * Creates a ToDynamicOptic instance with the given optic.
   */
  def apply[A, B](o: DynamicOptic): ToDynamicOptic[A, B] = new ToDynamicOptic[A, B] {
    def optic: DynamicOptic = o
  }

  /**
   * Macro-based derivation of ToDynamicOptic.
   *
   * This macro analyzes the selector function at compile time and extracts
   * the field access path.
   *
   * Example:
   * {{`
   *   case class Person(name: String, age: Int)
   *   val toOptic = ToDynamicOptic.derive[Person, String](_.name)
   *   // toOptic.optic == DynamicOptic.root.field("name")
   * `}}
   */
  def derive[A, B](selector: A => B): ToDynamicOptic[A, B] = macro ToDynamicOpticMacro.deriveImpl[A, B]

  /**
   * Implicit derivation for field access.
   *
   * This provides automatic conversion of selector functions to DynamicOptic
   * in the migration builder API.
   */
  implicit def materialize[A, B]: ToDynamicOptic[A, B] = macro ToDynamicOpticMacro.materializeImpl[A, B]
}

/**
 * Macro implementation for ToDynamicOptic.
 *
 * This macro analyzes Scala code at compile time to extract the path
 * from selector expressions like `_.fieldName` or `_.address.street`.
 */
object ToDynamicOpticMacro {

  import scala.reflect.macros.whitebox.Context

  def deriveImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: Context)(selector: c.Expr[A => B]): c.Expr[ToDynamicOptic[A, B]] = {
    import c.universe._

    val path = extractPath(c)(selector.tree)
    val opticExpr = buildOptic(c)(path)

    c.Expr[ToDynamicOptic[A, B]](q"""
      new _root_.zio.blocks.schema.migration.ToDynamicOptic[${weakTypeTag[A].tpe}, ${weakTypeTag[B].tpe}] {
        def optic: _root_.zio.blocks.schema.DynamicOptic = $opticExpr
      }
    """)
  }

  def materializeImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: Context): c.Expr[ToDynamicOptic[A, B]] = {
    import c.universe._

    c.abort(c.enclosingPosition, "ToDynamicOptic.materialize should not be called directly. Use ToDynamicOptic.derive instead.")
  }

  /**
   * Extracts the path from a selector expression tree.
   *
   * Supports:
   * - Field access: _.fieldName
   * - Nested access: _.field1.field2
   * - Collection traversal: _.items.each (as _.items[*])
   * - Index access: _.items(0) (as _.items[0])
   */
  private def extractPath(c: Context)(tree: c.Tree): List[PathNode] = {
    import c.universe._

    tree match {
      // Function literal: (x => x.field)
      case Function(List(ValDef(_, paramName, _, _)), body) =>
        extractPathFromBody(c)(body, paramName)

      // Eta expansion or method reference
      case _ =>
        List.empty
    }
  }

  private def extractPathFromBody(c: Context)(tree: c.Tree, paramName: c.TermName): List[PathNode] = {
    import c.universe._

    tree match {
      // Field access: x.field
      case Select(Ident(id), fieldName) if id == paramName =>
        List(FieldNode(fieldName.toString))

      // Nested field access: x.field1.field2
      case Select(qualifier, fieldName) =>
        extractPathFromBody(c)(qualifier, paramName) :+ FieldNode(fieldName.toString)

      // Method call for collection traversal: x.items.each
      case Apply(Select(qualifier, TermName("each")), Nil) =>
        extractPathFromBody(c)(qualifier, paramName) :+ ElementsNode

      // Method call for index access: x.items(0)
      case Apply(Select(qualifier, TermName("apply")), List(Literal(Constant(index: Int)))) =>
        extractPathFromBody(c)(qualifier, paramName) :+ AtIndexNode(index)

      // Method call for index access: x.items.at(0)
      case Apply(Select(qualifier, TermName("at")), List(Literal(Constant(index: Int)))) =>
        extractPathFromBody(c)(qualifier, paramName) :+ AtIndexNode(index)

      // Identity: just x
      case Ident(id) if id == paramName =>
        List.empty

      // Unknown pattern
      case _ =>
        List.empty
    }
  }

  /**
   * Builds a DynamicOptic expression from the extracted path nodes.
   */
  private def buildOptic(c: Context)(path: List[PathNode]): c.Tree = {
    import c.universe._

    val rootExpr = q"_root_.zio.blocks.schema.DynamicOptic.root"

    path.foldLeft(rootExpr) { (acc, node) =>
      node match {
        case FieldNode(name)    => q"$acc.field($name)"
        case AtIndexNode(index) => q"$acc.at($index)"
        case ElementsNode       => q"$acc.elements"
        case _                  => acc
      }
    }
  }

  // ==========================================================================
  // Path Node Types
  // ==========================================================================

  private sealed trait PathNode
  private case class FieldNode(name: String) extends PathNode
  private case class AtIndexNode(index: Int) extends PathNode
  private case object ElementsNode extends PathNode
}
