package zio.blocks.schema.migration

import scala.quoted.*

/**
 * Compile-time shape extraction for migration validation.
 *
 * Extracts the complete structure of a type at compile time as a hierarchical
 * ShapeNode tree. This enables validation that migrations handle all structural
 * changes between source and target types.
 */
object ShapeExtraction {

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
   * ShapeTree provides a unified view:
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
  private[migration] sealed trait ShapeTree[A] {

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
}
