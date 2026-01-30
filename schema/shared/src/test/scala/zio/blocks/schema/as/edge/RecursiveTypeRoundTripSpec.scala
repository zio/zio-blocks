package zio.blocks.schema.as.edge

import zio.blocks.schema._
import zio.test._

/**
 * Tests for round-trip conversions with recursive types using As.
 *
 * Since As requires bidirectional conversion and the macro doesn't yet support
 * finding implicit As instances for container element types (Option, List),
 * these tests use same-type conversions for recursive structures.
 */
object RecursiveTypeRoundTripSpec extends ZIOSpecDefault {

  // Simple recursive tree - same type round-trip
  case class TreeNode(value: Int, children: List[TreeNode])

  // Linked list - same type round-trip
  case class LinkedNode(value: String, next: Option[LinkedNode])

  // Binary tree - same type round-trip
  case class BinaryNode(value: Int, left: Option[BinaryNode], right: Option[BinaryNode])

  // Recursive with additional fields - same type round-trip
  case class Category(name: String, description: String, subcategories: List[Category])

  // Non-recursive products for bidirectional conversion
  case class SimpleA(value: Int, name: String)
  case class SimpleB(value: Int, name: String)

  // Products with different field names but unique types
  case class PersonA(id: Long, username: String)
  case class PersonB(id: Long, username: String)

  // Products with collections of same primitives
  case class CollectionA(items: List[Int], tags: Set[String])
  case class CollectionB(items: List[Int], tags: Set[String])

  def spec = suite("RecursiveTypeRoundTripSpec")(
    suite("Same Type Recursive Tree Round-Trip")(
      test("leaf node round-trips correctly") {
        val original                                     = TreeNode(1, List.empty)
        implicit lazy val treeAs: As[TreeNode, TreeNode] = As.derived[TreeNode, TreeNode]

        val forward = treeAs.into(original)
        assertTrue(forward == Right(original)) &&
        assertTrue(forward.flatMap(treeAs.from) == Right(original))
      },
      test("tree with one level of children round-trips") {
        val original                                     = TreeNode(1, List(TreeNode(2, List.empty), TreeNode(3, List.empty)))
        implicit lazy val treeAs: As[TreeNode, TreeNode] = As.derived[TreeNode, TreeNode]

        val forward = treeAs.into(original)
        assertTrue(forward == Right(original)) &&
        assertTrue(forward.flatMap(treeAs.from) == Right(original))
      },
      test("deeply nested tree round-trips") {
        val original = TreeNode(
          1,
          List(
            TreeNode(
              2,
              List(
                TreeNode(4, List.empty),
                TreeNode(5, List.empty)
              )
            ),
            TreeNode(
              3,
              List(
                TreeNode(6, List.empty)
              )
            )
          )
        )
        implicit lazy val treeAs: As[TreeNode, TreeNode] = As.derived[TreeNode, TreeNode]

        val forward = treeAs.into(original)
        assertTrue(forward == Right(original)) &&
        assertTrue(forward.flatMap(treeAs.from) == Right(original))
      }
    ),
    suite("Same Type Linked List Round-Trip")(
      test("single node linked list round-trips") {
        val original                                         = LinkedNode("only", None)
        implicit lazy val nodeAs: As[LinkedNode, LinkedNode] = As.derived[LinkedNode, LinkedNode]

        val forward = nodeAs.into(original)
        assertTrue(forward == Right(original)) &&
        assertTrue(forward.flatMap(nodeAs.from) == Right(original))
      },
      test("multi-node linked list round-trips") {
        val original                                         = LinkedNode("first", Some(LinkedNode("second", Some(LinkedNode("third", None)))))
        implicit lazy val nodeAs: As[LinkedNode, LinkedNode] = As.derived[LinkedNode, LinkedNode]

        val forward = nodeAs.into(original)
        assertTrue(forward == Right(original)) &&
        assertTrue(forward.flatMap(nodeAs.from) == Right(original))
      }
    ),
    suite("Same Type Binary Tree Round-Trip")(
      test("single node binary tree round-trips") {
        val original                                          = BinaryNode(10, None, None)
        implicit lazy val btreeAs: As[BinaryNode, BinaryNode] = As.derived[BinaryNode, BinaryNode]

        val forward = btreeAs.into(original)
        assertTrue(forward == Right(original)) &&
        assertTrue(forward.flatMap(btreeAs.from) == Right(original))
      },
      test("binary tree with children round-trips") {
        val original = BinaryNode(
          10,
          Some(BinaryNode(5, None, None)),
          Some(BinaryNode(15, None, None))
        )
        implicit lazy val btreeAs: As[BinaryNode, BinaryNode] = As.derived[BinaryNode, BinaryNode]

        val forward = btreeAs.into(original)
        assertTrue(forward == Right(original)) &&
        assertTrue(forward.flatMap(btreeAs.from) == Right(original))
      },
      test("unbalanced binary tree round-trips") {
        val original = BinaryNode(
          1,
          Some(
            BinaryNode(
              2,
              Some(BinaryNode(3, None, None)),
              None
            )
          ),
          None
        )
        implicit lazy val btreeAs: As[BinaryNode, BinaryNode] = As.derived[BinaryNode, BinaryNode]

        val forward = btreeAs.into(original)
        assertTrue(forward == Right(original)) &&
        assertTrue(forward.flatMap(btreeAs.from) == Right(original))
      }
    ),
    suite("Same Type Category Tree Round-Trip")(
      test("category tree round-trips") {
        val original = Category(
          "Electronics",
          "Electronic devices",
          List(
            Category("Phones", "Mobile phones", List.empty),
            Category(
              "Computers",
              "Desktop and laptop",
              List(
                Category("Laptops", "Portable computers", List.empty),
                Category("Desktops", "Stationary computers", List.empty)
              )
            )
          )
        )
        implicit lazy val catAs: As[Category, Category] = As.derived[Category, Category]

        val forward = catAs.into(original)
        assertTrue(forward == Right(original)) &&
        assertTrue(forward.flatMap(catAs.from) == Right(original))
      }
    ),
    suite("Non-Recursive Bidirectional Conversion")(
      test("simple products round-trip") {
        val original                                = SimpleA(42, "test")
        implicit val simpleAs: As[SimpleA, SimpleB] = As.derived[SimpleA, SimpleB]

        val forward = simpleAs.into(original)
        assertTrue(forward == Right(SimpleB(42, "test"))) &&
        assertTrue(forward.flatMap(simpleAs.from) == Right(original))
      },
      test("products with unique types round-trip") {
        val original                                = PersonA(123L, "john_doe")
        implicit val personAs: As[PersonA, PersonB] = As.derived[PersonA, PersonB]

        val forward = personAs.into(original)
        assertTrue(forward == Right(PersonB(123L, "john_doe"))) &&
        assertTrue(forward.flatMap(personAs.from) == Right(original))
      },
      test("products with collections round-trip") {
        val original                                      = CollectionA(List(1, 2, 3), Set("a", "b"))
        implicit val collAs: As[CollectionA, CollectionB] = As.derived[CollectionA, CollectionB]

        val forward = collAs.into(original)
        assertTrue(forward == Right(CollectionB(List(1, 2, 3), Set("a", "b")))) &&
        assertTrue(forward.flatMap(collAs.from) == Right(original))
      }
    ),
    suite("Swap Operation")(
      test("swapped As works for simple products") {
        val original                                = SimpleB(100, "swap")
        implicit val simpleAs: As[SimpleA, SimpleB] = As.derived[SimpleA, SimpleB]
        val swapped                                 = simpleAs.reverse

        val forward = swapped.into(original)
        assertTrue(forward == Right(SimpleA(100, "swap"))) &&
        assertTrue(forward.flatMap(swapped.from) == Right(original))
      },
      test("double swap returns original behavior") {
        val original                                = SimpleA(999, "double")
        implicit val simpleAs: As[SimpleA, SimpleB] = As.derived[SimpleA, SimpleB]
        val doubleSwapped                           = simpleAs.reverse.reverse

        val forward = doubleSwapped.into(original)
        assertTrue(forward == Right(SimpleB(999, "double"))) &&
        assertTrue(forward.flatMap(doubleSwapped.from) == Right(original))
      }
    )
  )
}
