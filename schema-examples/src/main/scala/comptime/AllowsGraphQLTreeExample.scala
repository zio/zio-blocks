package comptime

import zio.blocks.schema._
import zio.blocks.schema.comptime.Allows
import Allows.{Primitive, Record, Sequence, `|`}
import Allows.{Optional => AOptional, Self => ASelf}
import util.ShowExpr.show

// ---------------------------------------------------------------------------
// GraphQL / tree structure example using Self for recursive grammars
//
// Self refers back to the entire enclosing Allows[A, S] grammar, allowing
// the constraint to describe recursive data structures like trees, linked
// lists, and nested menus.
//
// Non-recursive types also satisfy Self-containing grammars — the Self
// position is never reached, so the constraint is vacuously satisfied.
// ---------------------------------------------------------------------------

// Recursive tree: children reference the same type
case class TreeNode(value: Int, children: List[TreeNode])
object TreeNode { implicit val schema: Schema[TreeNode] = Schema.derived }

// Recursive category hierarchy (common in e-commerce, CMS, etc.)
case class NavCategory(name: String, slug: String, subcategories: List[NavCategory])
object NavCategory { implicit val schema: Schema[NavCategory] = Schema.derived }

// Linked list via Optional[Self]
case class Chain(label: String, next: Option[Chain])
object Chain { implicit val schema: Schema[Chain] = Schema.derived }

// Non-recursive type — satisfies Self-containing grammars vacuously
case class FlatNode(id: Int, label: String)
object FlatNode { implicit val schema: Schema[FlatNode] = Schema.derived }

object GraphQL {

  type TreeShape = Primitive | Sequence[ASelf] | AOptional[ASelf]

  /** Generate a simplified GraphQL type definition for a recursive type. */
  def graphqlType[A](implicit schema: Schema[A], ev: Allows[A, Record[TreeShape]]): String = {
    val reflect = schema.reflect.asRecord.get
    val fields  = reflect.fields.map { f =>
      s"  ${f.name}: ${gqlType(resolve(f.value), schema.reflect.typeId.name)}"
    }
    s"type ${schema.reflect.typeId.name} {\n${fields.mkString("\n")}\n}"
  }

  /** Unwrap Deferred to get the actual Reflect node. */
  private def resolve(r: Reflect.Bound[_]): Reflect.Bound[_] = r match {
    case d: Reflect.Deferred[_, _] => resolve(d.value.asInstanceOf[Reflect.Bound[_]])
    case other                     => other
  }

  private def gqlType(r: Reflect.Bound[_], selfName: String): String = r match {
    case _: Reflect.Sequence[_, _, _] => s"[$selfName]"
    case p: Reflect.Primitive[_, _]   =>
      p.primitiveType match {
        case PrimitiveType.Int(_)     => "Int"
        case PrimitiveType.Long(_)    => "Int"
        case PrimitiveType.Float(_)   => "Float"
        case PrimitiveType.Double(_)  => "Float"
        case PrimitiveType.String(_)  => "String"
        case PrimitiveType.Boolean(_) => "Boolean"
        case _                        => "String"
      }
    case _ => selfName
  }
}

// ---------------------------------------------------------------------------
// Demonstration
// ---------------------------------------------------------------------------

object AllowsGraphQLTreeExample extends App {

  // Recursive tree with Sequence[Self]
  show(GraphQL.graphqlType[TreeNode])

  // Recursive categories — same grammar, different domain
  show(GraphQL.graphqlType[NavCategory])

  // Linked list via Optional[Self]
  show(GraphQL.graphqlType[Chain])

  // Non-recursive type also satisfies the grammar (vacuously — Self is never reached)
  show(GraphQL.graphqlType[FlatNode])

  // Show that recursive data actually works at runtime
  val tree = TreeNode(
    1,
    List(
      TreeNode(2, List(TreeNode(4, Nil), TreeNode(5, Nil))),
      TreeNode(3, Nil)
    )
  )
  show(Schema[TreeNode].toDynamicValue(tree).toJson.toString)

  val nav = NavCategory(
    "Electronics",
    "electronics",
    List(
      NavCategory("Phones", "phones", Nil),
      NavCategory(
        "Laptops",
        "laptops",
        List(
          NavCategory("Gaming", "gaming", Nil)
        )
      )
    )
  )
  show(Schema[NavCategory].toDynamicValue(nav).toJson.toString)
}
