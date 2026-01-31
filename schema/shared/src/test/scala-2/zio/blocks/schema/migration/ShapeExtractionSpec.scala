package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema.migration.ShapeExtraction._

object ShapeExtractionSpec extends ZIOSpecDefault {

  // Test types
  case class Simple(name: String, age: Int)
  case class Address(street: String, city: String)
  case class Person(name: String, address: Address)
  case class Country(name: String, code: String)
  case class FullAddress(street: String, country: Country)
  case class Contact(address: FullAddress)

  sealed trait Result
  case class Success(value: Int)    extends Result
  case class Failure(error: String) extends Result

  sealed trait Status
  case class Active(since: String) extends Status
  case object Inactive             extends Status

  case class Empty()
  case class SingleField(x: Int)
  case class WithOption(value: Option[String])
  case class WithList(items: List[String])
  case class WithMap(data: Map[String, Int])
  case class WithVector(elements: Vector[Double])

  case class Inner(x: Int, y: Int)
  case class Outer(inner: Inner, z: Int)

  sealed trait Payment
  case class Card(number: String, expiry: String) extends Payment
  case class Cash(amount: Int)                    extends Payment
  case class BankTransfer(bank: BankInfo)         extends Payment
  case class BankInfo(name: String, swift: String)

  case class HolderWithListOfSealed(results: List[Result])
  case class HolderWithOptionSealed(maybe: Option[Status])
  case class ResponseWithSealed(status: Status, result: Result)

  sealed abstract class BaseClass
  case class ConcreteA(x: Int)    extends BaseClass
  case class ConcreteB(y: String) extends BaseClass

  sealed trait SingleCase
  case class OnlyCase(value: Int) extends SingleCase

  case class WrapperWithSealed(inner: Result)

  def spec = suite("ShapeExtractionSpec")(
    suite("extractShapeTree")(
      test("flat case class returns RecordNode") {
        val tree = extractShapeTree[Simple]
        assertTrue(
          tree == ShapeNode.RecordNode(
            Map(
              "name" -> ShapeNode.PrimitiveNode,
              "age"  -> ShapeNode.PrimitiveNode
            )
          )
        )
      },
      test("nested case class returns nested RecordNode") {
        val tree = extractShapeTree[Person]
        assertTrue(
          tree == ShapeNode.RecordNode(
            Map(
              "name"    -> ShapeNode.PrimitiveNode,
              "address" -> ShapeNode.RecordNode(
                Map(
                  "street" -> ShapeNode.PrimitiveNode,
                  "city"   -> ShapeNode.PrimitiveNode
                )
              )
            )
          )
        )
      },
      test("deeply nested case class (3 levels)") {
        val tree = extractShapeTree[Contact]
        assertTrue(
          tree == ShapeNode.RecordNode(
            Map(
              "address" -> ShapeNode.RecordNode(
                Map(
                  "street"  -> ShapeNode.PrimitiveNode,
                  "country" -> ShapeNode.RecordNode(
                    Map(
                      "name" -> ShapeNode.PrimitiveNode,
                      "code" -> ShapeNode.PrimitiveNode
                    )
                  )
                )
              )
            )
          )
        )
      },
      test("empty case class returns empty RecordNode") {
        val tree = extractShapeTree[Empty]
        assertTrue(tree == ShapeNode.RecordNode(Map.empty))
      },
      test("primitive type returns PrimitiveNode") {
        val tree = extractShapeTree[String]
        assertTrue(tree == ShapeNode.PrimitiveNode)
      },
      test("Option field returns OptionNode with element shape") {
        val tree = extractShapeTree[WithOption]
        assertTrue(
          tree == ShapeNode.RecordNode(
            Map(
              "value" -> ShapeNode.OptionNode(ShapeNode.PrimitiveNode)
            )
          )
        )
      },
      test("List field returns SeqNode with element shape") {
        val tree = extractShapeTree[WithList]
        assertTrue(
          tree == ShapeNode.RecordNode(
            Map(
              "items" -> ShapeNode.SeqNode(ShapeNode.PrimitiveNode)
            )
          )
        )
      },
      test("Map field returns MapNode with key and value shapes") {
        val tree = extractShapeTree[WithMap]
        assertTrue(
          tree == ShapeNode.RecordNode(
            Map(
              "data" -> ShapeNode.MapNode(ShapeNode.PrimitiveNode, ShapeNode.PrimitiveNode)
            )
          )
        )
      },
      test("Vector field returns SeqNode") {
        val tree = extractShapeTree[WithVector]
        assertTrue(
          tree == ShapeNode.RecordNode(
            Map(
              "elements" -> ShapeNode.SeqNode(ShapeNode.PrimitiveNode)
            )
          )
        )
      },
      test("sealed trait returns SealedNode with case shapes") {
        val tree = extractShapeTree[Result]
        assertTrue(
          tree == ShapeNode.SealedNode(
            Map(
              "Success" -> ShapeNode.RecordNode(Map("value" -> ShapeNode.PrimitiveNode)),
              "Failure" -> ShapeNode.RecordNode(Map("error" -> ShapeNode.PrimitiveNode))
            )
          )
        )
      },
      test("sealed trait with case object returns SealedNode with empty RecordNode") {
        val tree = extractShapeTree[Status]
        assertTrue(
          tree == ShapeNode.SealedNode(
            Map(
              "Active"   -> ShapeNode.RecordNode(Map("since" -> ShapeNode.PrimitiveNode)),
              "Inactive" -> ShapeNode.RecordNode(Map.empty)
            )
          )
        )
      },
      test("Either returns SealedNode with Left and Right") {
        val tree = extractShapeTree[Either[String, Int]]
        assertTrue(
          tree == ShapeNode.SealedNode(
            Map(
              "Left"  -> ShapeNode.PrimitiveNode,
              "Right" -> ShapeNode.PrimitiveNode
            )
          )
        )
      },
      test("Either with complex types") {
        val tree = extractShapeTree[Either[Simple, Address]]
        assertTrue(
          tree == ShapeNode.SealedNode(
            Map(
              "Left" -> ShapeNode.RecordNode(
                Map(
                  "name" -> ShapeNode.PrimitiveNode,
                  "age"  -> ShapeNode.PrimitiveNode
                )
              ),
              "Right" -> ShapeNode.RecordNode(
                Map(
                  "street" -> ShapeNode.PrimitiveNode,
                  "city"   -> ShapeNode.PrimitiveNode
                )
              )
            )
          )
        )
      },
      test("List of case class returns SeqNode with RecordNode") {
        val tree = extractShapeTree[List[Simple]]
        assertTrue(
          tree == ShapeNode.SeqNode(
            ShapeNode.RecordNode(
              Map(
                "name" -> ShapeNode.PrimitiveNode,
                "age"  -> ShapeNode.PrimitiveNode
              )
            )
          )
        )
      },
      test("Option of case class returns OptionNode with RecordNode") {
        val tree = extractShapeTree[Option[Address]]
        assertTrue(
          tree == ShapeNode.OptionNode(
            ShapeNode.RecordNode(
              Map(
                "street" -> ShapeNode.PrimitiveNode,
                "city"   -> ShapeNode.PrimitiveNode
              )
            )
          )
        )
      },
      test("Map with case class value returns MapNode with RecordNode value") {
        val tree = extractShapeTree[Map[String, Simple]]
        assertTrue(
          tree == ShapeNode.MapNode(
            ShapeNode.PrimitiveNode,
            ShapeNode.RecordNode(
              Map(
                "name" -> ShapeNode.PrimitiveNode,
                "age"  -> ShapeNode.PrimitiveNode
              )
            )
          )
        )
      },
      test("sealed trait inside List returns SeqNode with SealedNode") {
        val tree = extractShapeTree[HolderWithListOfSealed]
        assertTrue(
          tree == ShapeNode.RecordNode(
            Map(
              "results" -> ShapeNode.SeqNode(
                ShapeNode.SealedNode(
                  Map(
                    "Success" -> ShapeNode.RecordNode(Map("value" -> ShapeNode.PrimitiveNode)),
                    "Failure" -> ShapeNode.RecordNode(Map("error" -> ShapeNode.PrimitiveNode))
                  )
                )
              )
            )
          )
        )
      },
      test("sealed trait inside Option returns OptionNode with SealedNode") {
        val tree = extractShapeTree[HolderWithOptionSealed]
        assertTrue(
          tree == ShapeNode.RecordNode(
            Map(
              "maybe" -> ShapeNode.OptionNode(
                ShapeNode.SealedNode(
                  Map(
                    "Active"   -> ShapeNode.RecordNode(Map("since" -> ShapeNode.PrimitiveNode)),
                    "Inactive" -> ShapeNode.RecordNode(Map.empty)
                  )
                )
              )
            )
          )
        )
      },
      test("case class with multiple sealed fields") {
        val tree = extractShapeTree[ResponseWithSealed]
        assertTrue(
          tree == ShapeNode.RecordNode(
            Map(
              "status" -> ShapeNode.SealedNode(
                Map(
                  "Active"   -> ShapeNode.RecordNode(Map("since" -> ShapeNode.PrimitiveNode)),
                  "Inactive" -> ShapeNode.RecordNode(Map.empty)
                )
              ),
              "result" -> ShapeNode.SealedNode(
                Map(
                  "Success" -> ShapeNode.RecordNode(Map("value" -> ShapeNode.PrimitiveNode)),
                  "Failure" -> ShapeNode.RecordNode(Map("error" -> ShapeNode.PrimitiveNode))
                )
              )
            )
          )
        )
      },
      test("sealed abstract class returns SealedNode") {
        val tree = extractShapeTree[BaseClass]
        assertTrue(
          tree == ShapeNode.SealedNode(
            Map(
              "ConcreteA" -> ShapeNode.RecordNode(Map("x" -> ShapeNode.PrimitiveNode)),
              "ConcreteB" -> ShapeNode.RecordNode(Map("y" -> ShapeNode.PrimitiveNode))
            )
          )
        )
      },
      test("single-case sealed trait returns SealedNode with one case") {
        val tree = extractShapeTree[SingleCase]
        assertTrue(
          tree == ShapeNode.SealedNode(
            Map(
              "OnlyCase" -> ShapeNode.RecordNode(Map("value" -> ShapeNode.PrimitiveNode))
            )
          )
        )
      },
      test("case class with sealed trait field returns RecordNode with SealedNode") {
        val tree = extractShapeTree[WrapperWithSealed]
        assertTrue(
          tree == ShapeNode.RecordNode(
            Map(
              "inner" -> ShapeNode.SealedNode(
                Map(
                  "Success" -> ShapeNode.RecordNode(Map("value" -> ShapeNode.PrimitiveNode)),
                  "Failure" -> ShapeNode.RecordNode(Map("error" -> ShapeNode.PrimitiveNode))
                )
              )
            )
          )
        )
      }
    ),
    suite("extractShapeTree recursion detection")(
      test("direct recursion produces compile error") {
        assertZIO(typeCheck("""
          import zio.blocks.schema.migration.ShapeExtraction._
          case class DirectRecursion(self: DirectRecursion)
          extractShapeTree[DirectRecursion]
        """))(Assertion.isLeft)
      },
      test("mutual recursion produces compile error") {
        assertZIO(typeCheck("""
          import zio.blocks.schema.migration.ShapeExtraction._
          case class MutualA(b: MutualB)
          case class MutualB(a: MutualA)
          extractShapeTree[MutualA]
        """))(Assertion.isLeft)
      },
      test("recursion through List produces compile error") {
        assertZIO(typeCheck("""
          import zio.blocks.schema.migration.ShapeExtraction._
          case class ListRecursionTree(children: List[ListRecursionTree])
          extractShapeTree[ListRecursionTree]
        """))(Assertion.isLeft)
      },
      test("recursion through Option produces compile error") {
        assertZIO(typeCheck("""
          import zio.blocks.schema.migration.ShapeExtraction._
          case class OptionRecursionTree(next: Option[OptionRecursionTree])
          extractShapeTree[OptionRecursionTree]
        """))(Assertion.isLeft)
      }
    ),
    suite("ShapeTree typeclass")(
      test("derives for flat case class") {
        val st = implicitly[ShapeTree[Simple]]
        assertTrue(
          st.tree == ShapeNode.RecordNode(
            Map(
              "name" -> ShapeNode.PrimitiveNode,
              "age"  -> ShapeNode.PrimitiveNode
            )
          )
        )
      },
      test("derives for nested case class") {
        val st = implicitly[ShapeTree[Person]]
        assertTrue(
          st.tree == ShapeNode.RecordNode(
            Map(
              "name"    -> ShapeNode.PrimitiveNode,
              "address" -> ShapeNode.RecordNode(
                Map(
                  "street" -> ShapeNode.PrimitiveNode,
                  "city"   -> ShapeNode.PrimitiveNode
                )
              )
            )
          )
        )
      },
      test("derives for sealed trait") {
        val st = implicitly[ShapeTree[Result]]
        assertTrue(
          st.tree == ShapeNode.SealedNode(
            Map(
              "Success" -> ShapeNode.RecordNode(Map("value" -> ShapeNode.PrimitiveNode)),
              "Failure" -> ShapeNode.RecordNode(Map("error" -> ShapeNode.PrimitiveNode))
            )
          )
        )
      },
      test("derives for primitive") {
        val st = implicitly[ShapeTree[String]]
        assertTrue(st.tree == ShapeNode.PrimitiveNode)
      },
      test("derives for Option") {
        val st = implicitly[ShapeTree[Option[Simple]]]
        assertTrue(
          st.tree == ShapeNode.OptionNode(
            ShapeNode.RecordNode(
              Map(
                "name" -> ShapeNode.PrimitiveNode,
                "age"  -> ShapeNode.PrimitiveNode
              )
            )
          )
        )
      },
      test("derives for List") {
        val st = implicitly[ShapeTree[List[Address]]]
        assertTrue(
          st.tree == ShapeNode.SeqNode(
            ShapeNode.RecordNode(
              Map(
                "street" -> ShapeNode.PrimitiveNode,
                "city"   -> ShapeNode.PrimitiveNode
              )
            )
          )
        )
      },
      test("derives for Map") {
        val st = implicitly[ShapeTree[Map[String, Int]]]
        assertTrue(
          st.tree == ShapeNode.MapNode(ShapeNode.PrimitiveNode, ShapeNode.PrimitiveNode)
        )
      },
      test("derives for Either") {
        val st = implicitly[ShapeTree[Either[String, Int]]]
        assertTrue(
          st.tree == ShapeNode.SealedNode(
            Map(
              "Left"  -> ShapeNode.PrimitiveNode,
              "Right" -> ShapeNode.PrimitiveNode
            )
          )
        )
      }
    ),
    suite("MigrationPaths typeclass")(
      test("derives for identical types - empty removed and added") {
        val _ = implicitly[MigrationPaths[Simple, Simple]]
        assertCompletes
      },
      test("derives for types with added field") {
        val _ = implicitly[MigrationPaths[SingleField, Simple]]
        assertCompletes
      },
      test("derives for types with removed field") {
        val _ = implicitly[MigrationPaths[Simple, SingleField]]
        assertCompletes
      },
      test("derives for nested case classes") {
        val _ = implicitly[MigrationPaths[Person, Contact]]
        assertCompletes
      },
      test("derives for sealed traits") {
        val _ = implicitly[MigrationPaths[Result, Status]]
        assertCompletes
      },
      test("pathToFlatString converts field paths") {
        val path = List(Segment.Field("address"), Segment.Field("city"))
        assertTrue(MigrationPaths.pathToFlatString(path) == "address.city")
      },
      test("pathToFlatString converts case paths") {
        val path = List(Segment.Case("Success"))
        assertTrue(MigrationPaths.pathToFlatString(path) == "case:Success")
      },
      test("pathToFlatString converts container paths") {
        val path = List(Segment.Field("items"), Segment.Element, Segment.Field("name"))
        assertTrue(MigrationPaths.pathToFlatString(path) == "items.element.name")
      },
      test("pathToFlatString handles empty path") {
        assertTrue(MigrationPaths.pathToFlatString(Nil) == "<root>")
      }
    ),
    suite("TreeDiff")(
      test("identical trees have empty diff") {
        val tree = ShapeNode.RecordNode(
          Map(
            "name" -> ShapeNode.PrimitiveNode,
            "age"  -> ShapeNode.PrimitiveNode
          )
        )
        val (removed, added) = TreeDiff.diff(tree, tree)
        assertTrue(removed.isEmpty, added.isEmpty)
      },
      test("added field appears in added list") {
        val source = ShapeNode.RecordNode(Map("name" -> ShapeNode.PrimitiveNode))
        val target = ShapeNode.RecordNode(
          Map(
            "name" -> ShapeNode.PrimitiveNode,
            "age"  -> ShapeNode.PrimitiveNode
          )
        )
        val (removed, added) = TreeDiff.diff(source, target)
        assertTrue(
          removed.isEmpty,
          added == List(List(Segment.Field("age")))
        )
      },
      test("removed field appears in removed list") {
        val source = ShapeNode.RecordNode(
          Map(
            "name" -> ShapeNode.PrimitiveNode,
            "age"  -> ShapeNode.PrimitiveNode
          )
        )
        val target           = ShapeNode.RecordNode(Map("name" -> ShapeNode.PrimitiveNode))
        val (removed, added) = TreeDiff.diff(source, target)
        assertTrue(
          removed == List(List(Segment.Field("age"))),
          added.isEmpty
        )
      },
      test("type change appears in both lists") {
        val source = ShapeNode.RecordNode(Map("data" -> ShapeNode.PrimitiveNode))
        val target = ShapeNode.RecordNode(
          Map(
            "data" -> ShapeNode.RecordNode(Map("value" -> ShapeNode.PrimitiveNode))
          )
        )
        val (removed, added) = TreeDiff.diff(source, target)
        val expectedPath     = List(Segment.Field("data"))
        assertTrue(
          removed == List(expectedPath),
          added == List(expectedPath)
        )
      },
      test("nested field changes have correct paths") {
        val source = ShapeNode.RecordNode(
          Map(
            "address" -> ShapeNode.RecordNode(
              Map(
                "city"   -> ShapeNode.PrimitiveNode,
                "street" -> ShapeNode.PrimitiveNode
              )
            )
          )
        )
        val target = ShapeNode.RecordNode(
          Map(
            "address" -> ShapeNode.RecordNode(
              Map(
                "city" -> ShapeNode.PrimitiveNode,
                "zip"  -> ShapeNode.PrimitiveNode
              )
            )
          )
        )
        val (removed, added) = TreeDiff.diff(source, target)
        assertTrue(
          removed == List(List(Segment.Field("address"), Segment.Field("street"))),
          added == List(List(Segment.Field("address"), Segment.Field("zip")))
        )
      },
      test("sealed trait case changes") {
        val source = ShapeNode.SealedNode(
          Map(
            "A" -> ShapeNode.RecordNode(Map.empty),
            "B" -> ShapeNode.RecordNode(Map.empty)
          )
        )
        val target = ShapeNode.SealedNode(
          Map(
            "A" -> ShapeNode.RecordNode(Map.empty),
            "C" -> ShapeNode.RecordNode(Map.empty)
          )
        )
        val (removed, added) = TreeDiff.diff(source, target)
        assertTrue(
          removed == List(List(Segment.Case("B"))),
          added == List(List(Segment.Case("C")))
        )
      },
      test("container element changes") {
        val source           = ShapeNode.SeqNode(ShapeNode.PrimitiveNode)
        val target           = ShapeNode.SeqNode(ShapeNode.RecordNode(Map("x" -> ShapeNode.PrimitiveNode)))
        val (removed, added) = TreeDiff.diff(source, target)
        val expectedPath     = List(Segment.Element)
        assertTrue(
          removed == List(expectedPath),
          added == List(expectedPath)
        )
      },
      test("map key and value changes") {
        val source = ShapeNode.MapNode(ShapeNode.PrimitiveNode, ShapeNode.PrimitiveNode)
        val target = ShapeNode.MapNode(
          ShapeNode.RecordNode(Map("id" -> ShapeNode.PrimitiveNode)),
          ShapeNode.PrimitiveNode
        )
        val (removed, added) = TreeDiff.diff(source, target)
        val keyPath          = List(Segment.Key)
        assertTrue(
          removed == List(keyPath),
          added == List(keyPath)
        )
      },
      test("Path.render formats paths correctly") {
        val path1 = List(Segment.Field("address"), Segment.Field("city"))
        val path2 = List(Segment.Case("Success"))
        val path3 = List(Segment.Field("items"), Segment.Element, Segment.Field("name"))
        assertTrue(
          Path.render(path1) == ".address.city",
          Path.render(path2) == "[case:Success]",
          Path.render(path3) == ".items[element].name",
          Path.render(Nil) == "<root>"
        )
      }
    )
  )
}
