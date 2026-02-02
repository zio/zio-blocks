package zio.blocks.schema.migration

import zio.test._
import zio.test.Assertion
import zio.blocks.schema.migration.ShapeExtraction._
import zio.blocks.schema.migration.ShapeNode._
import zio.blocks.schema.migration.Segment
import zio.blocks.schema.migration.TreeDiff

object ShapeExtractionSpec extends ZIOSpecDefault {

  // Enum for ShapeTree tests
  enum Color {
    case Red, Green, Blue
  }

  // Test types for ShapeNode extraction
  case class PersonForTree(name: String, age: Int)
  case class AddressForTree(street: String, city: String)
  case class PersonWithAddressForTree(name: String, address: AddressForTree)

  // Container types for ShapeNode
  case class WithListOfPerson(items: List[PersonForTree])
  case class WithOptionAddress(address: Option[AddressForTree])
  case class WithMapStringPerson(data: Map[String, PersonForTree])

  // Sealed trait for ShapeNode
  sealed trait StatusForTree
  case class ActiveStatus(since: String) extends StatusForTree
  case object InactiveStatus             extends StatusForTree

  // Error types for Either test
  case class ErrorInfo(code: Int, message: String)
  case class SuccessData(value: String, count: Int)

  // Value class test types for wrapped type extraction
  case class UserId(value: String)     extends AnyVal
  case class Amount(value: BigDecimal) extends AnyVal
  case class PersonWithUserId(name: String, id: UserId)

  // For testing nested wrapping, use a wrapper case class instead of nested value class
  // (Value classes can't wrap other value classes)
  case class WrappedUserId(inner: UserId)
  case class OrderWithIds(wrappedId: WrappedUserId, amount: Amount)

  // Wrapped types in containers
  case class WithListOfUserIds(ids: List[UserId])
  case class WithOptionAmount(amount: Option[Amount])
  case class WithMapUserIdAmount(data: Map[UserId, Amount])

  // Wrapped type containing record
  case class PersonRecord(name: String, age: Int)
  case class BoxedPerson(person: PersonRecord) extends AnyVal

  def spec = suite("ShapeExtractionSpec")(
    suite("extractShapeTree")(
      test("flat case class returns RecordNode with primitive fields") {
        val tree = extractShapeTree[PersonForTree]
        assertTrue(
          tree == RecordNode(
            Map(
              "name" -> PrimitiveNode,
              "age"  -> PrimitiveNode
            )
          )
        )
      },
      test("nested case class returns nested RecordNodes") {
        val tree = extractShapeTree[PersonWithAddressForTree]
        assertTrue(
          tree == RecordNode(
            Map(
              "name"    -> PrimitiveNode,
              "address" -> RecordNode(
                Map(
                  "street" -> PrimitiveNode,
                  "city"   -> PrimitiveNode
                )
              )
            )
          )
        )
      },
      test("List[Person] returns SeqNode with RecordNode element") {
        val tree = extractShapeTree[WithListOfPerson]
        assertTrue(
          tree == RecordNode(
            Map(
              "items" -> SeqNode(
                RecordNode(
                  Map(
                    "name" -> PrimitiveNode,
                    "age"  -> PrimitiveNode
                  )
                )
              )
            )
          )
        )
      },
      test("Option[Address] returns OptionNode with RecordNode element") {
        val tree = extractShapeTree[WithOptionAddress]
        assertTrue(
          tree == RecordNode(
            Map(
              "address" -> OptionNode(
                RecordNode(
                  Map(
                    "street" -> PrimitiveNode,
                    "city"   -> PrimitiveNode
                  )
                )
              )
            )
          )
        )
      },
      test("Map[String, Person] returns MapNode with key and value shapes") {
        val tree = extractShapeTree[WithMapStringPerson]
        assertTrue(
          tree == RecordNode(
            Map(
              "data" -> MapNode(
                PrimitiveNode,
                RecordNode(
                  Map(
                    "name" -> PrimitiveNode,
                    "age"  -> PrimitiveNode
                  )
                )
              )
            )
          )
        )
      },
      test("Either[ErrorInfo, SuccessData] returns SealedNode with Left and Right") {
        val tree = extractShapeTree[Either[ErrorInfo, SuccessData]]
        assertTrue(
          tree == SealedNode(
            Map(
              "Left" -> RecordNode(
                Map(
                  "code"    -> PrimitiveNode,
                  "message" -> PrimitiveNode
                )
              ),
              "Right" -> RecordNode(
                Map(
                  "value" -> PrimitiveNode,
                  "count" -> PrimitiveNode
                )
              )
            )
          )
        )
      },
      test("sealed trait with case object returns SealedNode with empty RecordNode for case object") {
        val tree = extractShapeTree[StatusForTree]
        assertTrue(
          tree == SealedNode(
            Map(
              "ActiveStatus"   -> RecordNode(Map("since" -> PrimitiveNode)),
              "InactiveStatus" -> RecordNode(Map.empty)
            )
          )
        )
      },
      test("primitive type returns PrimitiveNode") {
        val tree = extractShapeTree[String]
        assertTrue(tree == PrimitiveNode)
      },
      test("Int primitive type returns PrimitiveNode") {
        val tree = extractShapeTree[Int]
        assertTrue(tree == PrimitiveNode)
      },
      test("Long primitive type returns PrimitiveNode") {
        val tree = extractShapeTree[Long]
        assertTrue(tree == PrimitiveNode)
      },
      test("Boolean primitive type returns PrimitiveNode") {
        val tree = extractShapeTree[Boolean]
        assertTrue(tree == PrimitiveNode)
      },
      test("Double primitive type returns PrimitiveNode") {
        val tree = extractShapeTree[Double]
        assertTrue(tree == PrimitiveNode)
      },
      test("Float primitive type returns PrimitiveNode") {
        val tree = extractShapeTree[Float]
        assertTrue(tree == PrimitiveNode)
      },
      test("BigDecimal primitive type returns PrimitiveNode") {
        val tree = extractShapeTree[BigDecimal]
        assertTrue(tree == PrimitiveNode)
      },
      test("BigInt primitive type returns PrimitiveNode") {
        val tree = extractShapeTree[BigInt]
        assertTrue(tree == PrimitiveNode)
      },
      test("Short primitive type returns PrimitiveNode") {
        val tree = extractShapeTree[Short]
        assertTrue(tree == PrimitiveNode)
      },
      test("Byte primitive type returns PrimitiveNode") {
        val tree = extractShapeTree[Byte]
        assertTrue(tree == PrimitiveNode)
      },
      test("Char primitive type returns PrimitiveNode") {
        val tree = extractShapeTree[Char]
        assertTrue(tree == PrimitiveNode)
      },
      test("simple enum returns SealedNode with empty RecordNodes") {
        val tree = extractShapeTree[Color]
        assertTrue(
          tree == SealedNode(
            Map(
              "Red"   -> RecordNode(Map.empty),
              "Green" -> RecordNode(Map.empty),
              "Blue"  -> RecordNode(Map.empty)
            )
          )
        )
      },
      test("List[Int] returns SeqNode with PrimitiveNode element") {
        val tree = extractShapeTree[List[Int]]
        assertTrue(tree == SeqNode(PrimitiveNode))
      },
      test("Option[String] returns OptionNode with PrimitiveNode element") {
        val tree = extractShapeTree[Option[String]]
        assertTrue(tree == OptionNode(PrimitiveNode))
      },
      test("Map[String, Int] returns MapNode with primitive key and value") {
        val tree = extractShapeTree[Map[String, Int]]
        assertTrue(tree == MapNode(PrimitiveNode, PrimitiveNode))
      }
    ),
    suite("extractShapeTree recursion detection")(
      test("direct recursion produces compile error with helpful message") {
        assertZIO(typeCheck("""
          import zio.blocks.schema.migration.ShapeExtraction._
          case class DirectRecursionTree(self: DirectRecursionTree)
          extractShapeTree[DirectRecursionTree]
        """))(Assertion.isLeft(Assertion.containsString("Recursive")))
      },
      test("mutual recursion produces compile error with helpful message") {
        assertZIO(typeCheck("""
          import zio.blocks.schema.migration.ShapeExtraction._
          case class MutualTreeA(b: MutualTreeB)
          case class MutualTreeB(a: MutualTreeA)
          extractShapeTree[MutualTreeA]
        """))(Assertion.isLeft(Assertion.containsString("Recursive")))
      },
      test("recursion through List produces compile error with helpful message") {
        assertZIO(typeCheck("""
          import zio.blocks.schema.migration.ShapeExtraction._
          case class ListRecursionTree(children: List[ListRecursionTree])
          extractShapeTree[ListRecursionTree]
        """))(Assertion.isLeft(Assertion.containsString("Recursive")))
      },
      test("recursion through Option produces compile error with helpful message") {
        assertZIO(typeCheck("""
          import zio.blocks.schema.migration.ShapeExtraction._
          case class OptionRecursionTree(next: Option[OptionRecursionTree])
          extractShapeTree[OptionRecursionTree]
        """))(Assertion.isLeft(Assertion.containsString("Recursive")))
      }
    ),
    suite("ShapeTree typeclass")(
      test("derives ShapeTree for flat case class") {
        val st = summon[ShapeTree[PersonForTree]]
        assertTrue(
          st.tree == RecordNode(
            Map(
              "name" -> PrimitiveNode,
              "age"  -> PrimitiveNode
            )
          )
        )
      },
      test("derives ShapeTree for nested case class") {
        val st = summon[ShapeTree[PersonWithAddressForTree]]
        assertTrue(
          st.tree == RecordNode(
            Map(
              "name"    -> PrimitiveNode,
              "address" -> RecordNode(
                Map(
                  "street" -> PrimitiveNode,
                  "city"   -> PrimitiveNode
                )
              )
            )
          )
        )
      },
      test("derives ShapeTree for sealed trait") {
        val st = summon[ShapeTree[StatusForTree]]
        assertTrue(
          st.tree == SealedNode(
            Map(
              "ActiveStatus"   -> RecordNode(Map("since" -> PrimitiveNode)),
              "InactiveStatus" -> RecordNode(Map.empty)
            )
          )
        )
      },
      test("derives ShapeTree for container types") {
        val st = summon[ShapeTree[WithListOfPerson]]
        assertTrue(
          st.tree == RecordNode(
            Map(
              "items" -> SeqNode(
                RecordNode(
                  Map(
                    "name" -> PrimitiveNode,
                    "age"  -> PrimitiveNode
                  )
                )
              )
            )
          )
        )
      },
      test("derives ShapeTree for Either") {
        val st = summon[ShapeTree[Either[ErrorInfo, SuccessData]]]
        assertTrue(
          st.tree == SealedNode(
            Map(
              "Left" -> RecordNode(
                Map(
                  "code"    -> PrimitiveNode,
                  "message" -> PrimitiveNode
                )
              ),
              "Right" -> RecordNode(
                Map(
                  "value" -> PrimitiveNode,
                  "count" -> PrimitiveNode
                )
              )
            )
          )
        )
      }
    ),
    suite("TreeDiff")(
      test("identical trees have empty diff") {
        val tree             = RecordNode(Map("name" -> PrimitiveNode, "age" -> PrimitiveNode))
        val (removed, added) = TreeDiff.diff(tree, tree)
        assertTrue(removed.isEmpty, added.isEmpty)
      },
      test("added field appears in added list") {
        val source           = RecordNode(Map("name" -> PrimitiveNode))
        val target           = RecordNode(Map("name" -> PrimitiveNode, "age" -> PrimitiveNode))
        val (removed, added) = TreeDiff.diff(source, target)
        assertTrue(
          removed.isEmpty,
          added == List(List(Segment.Field("age")))
        )
      },
      test("removed field appears in removed list") {
        val source           = RecordNode(Map("name" -> PrimitiveNode, "age" -> PrimitiveNode))
        val target           = RecordNode(Map("name" -> PrimitiveNode))
        val (removed, added) = TreeDiff.diff(source, target)
        assertTrue(
          removed == List(List(Segment.Field("age"))),
          added.isEmpty
        )
      },
      test("changed field type appears in BOTH lists") {
        // name changed from Primitive to Record
        val source           = RecordNode(Map("name" -> PrimitiveNode))
        val target           = RecordNode(Map("name" -> RecordNode(Map("first" -> PrimitiveNode))))
        val (removed, added) = TreeDiff.diff(source, target)
        val expectedPath     = List(Segment.Field("name"))
        assertTrue(
          removed == List(expectedPath),
          added == List(expectedPath)
        )
      },
      test("nested changes have correct prefixed paths") {
        val source = RecordNode(
          Map(
            "address" -> RecordNode(Map("city" -> PrimitiveNode))
          )
        )
        val target = RecordNode(
          Map(
            "address" -> RecordNode(Map("zip" -> PrimitiveNode))
          )
        )
        val (removed, added) = TreeDiff.diff(source, target)
        assertTrue(
          removed == List(List(Segment.Field("address"), Segment.Field("city"))),
          added == List(List(Segment.Field("address"), Segment.Field("zip")))
        )
      },
      test("multiple changes at different levels") {
        val source = RecordNode(
          Map(
            "name"    -> PrimitiveNode,
            "address" -> RecordNode(
              Map(
                "street" -> PrimitiveNode,
                "city"   -> PrimitiveNode
              )
            )
          )
        )
        val target = RecordNode(
          Map(
            "age"     -> PrimitiveNode,
            "address" -> RecordNode(
              Map(
                "street" -> PrimitiveNode,
                "zip"    -> PrimitiveNode
              )
            )
          )
        )
        val (removed, added) = TreeDiff.diff(source, target)
        assertTrue(
          removed.toSet == Set(
            List(Segment.Field("name")),
            List(Segment.Field("address"), Segment.Field("city"))
          ),
          added.toSet == Set(
            List(Segment.Field("age")),
            List(Segment.Field("address"), Segment.Field("zip"))
          )
        )
      },
      test("sealed trait case changes") {
        val source = SealedNode(
          Map(
            "Success" -> RecordNode(Map("value" -> PrimitiveNode)),
            "Failure" -> RecordNode(Map("error" -> PrimitiveNode))
          )
        )
        val target = SealedNode(
          Map(
            "Success" -> RecordNode(Map("value" -> PrimitiveNode)),
            "Error"   -> RecordNode(Map("message" -> PrimitiveNode))
          )
        )
        val (removed, added) = TreeDiff.diff(source, target)
        assertTrue(
          removed == List(List(Segment.Case("Failure"))),
          added == List(List(Segment.Case("Error")))
        )
      },
      test("case field changes within sealed trait") {
        val source = SealedNode(
          Map(
            "Success" -> RecordNode(Map("value" -> PrimitiveNode))
          )
        )
        val target = SealedNode(
          Map(
            "Success" -> RecordNode(Map("result" -> PrimitiveNode))
          )
        )
        val (removed, added) = TreeDiff.diff(source, target)
        assertTrue(
          removed == List(List(Segment.Case("Success"), Segment.Field("value"))),
          added == List(List(Segment.Case("Success"), Segment.Field("result")))
        )
      },
      test("sequence element changes have element segment") {
        val source           = SeqNode(RecordNode(Map("name" -> PrimitiveNode)))
        val target           = SeqNode(RecordNode(Map("title" -> PrimitiveNode)))
        val (removed, added) = TreeDiff.diff(source, target)
        assertTrue(
          removed == List(List(Segment.Element, Segment.Field("name"))),
          added == List(List(Segment.Element, Segment.Field("title")))
        )
      },
      test("option element changes have element segment") {
        val source           = OptionNode(RecordNode(Map("x" -> PrimitiveNode)))
        val target           = OptionNode(RecordNode(Map("y" -> PrimitiveNode)))
        val (removed, added) = TreeDiff.diff(source, target)
        assertTrue(
          removed == List(List(Segment.Element, Segment.Field("x"))),
          added == List(List(Segment.Element, Segment.Field("y")))
        )
      },
      test("map key changes have key segment") {
        val source           = MapNode(RecordNode(Map("id" -> PrimitiveNode)), PrimitiveNode)
        val target           = MapNode(RecordNode(Map("key" -> PrimitiveNode)), PrimitiveNode)
        val (removed, added) = TreeDiff.diff(source, target)
        assertTrue(
          removed == List(List(Segment.Key, Segment.Field("id"))),
          added == List(List(Segment.Key, Segment.Field("key")))
        )
      },
      test("map value changes have value segment") {
        val source           = MapNode(PrimitiveNode, RecordNode(Map("x" -> PrimitiveNode)))
        val target           = MapNode(PrimitiveNode, RecordNode(Map("y" -> PrimitiveNode)))
        val (removed, added) = TreeDiff.diff(source, target)
        assertTrue(
          removed == List(List(Segment.Value, Segment.Field("x"))),
          added == List(List(Segment.Value, Segment.Field("y")))
        )
      },
      test("map with both key and value changes") {
        val source = MapNode(
          RecordNode(Map("keyField" -> PrimitiveNode)),
          RecordNode(Map("valField" -> PrimitiveNode))
        )
        val target = MapNode(
          RecordNode(Map("newKey" -> PrimitiveNode)),
          RecordNode(Map("newVal" -> PrimitiveNode))
        )
        val (removed, added) = TreeDiff.diff(source, target)
        assertTrue(
          removed.toSet == Set(
            List(Segment.Key, Segment.Field("keyField")),
            List(Segment.Value, Segment.Field("valField"))
          ),
          added.toSet == Set(
            List(Segment.Key, Segment.Field("newKey")),
            List(Segment.Value, Segment.Field("newVal"))
          )
        )
      },
      test("node type change at root") {
        val source: ShapeNode = RecordNode(Map("x" -> PrimitiveNode))
        val target: ShapeNode = SeqNode(PrimitiveNode)
        val (removed, added)  = TreeDiff.diff(source, target)
        // Root type change - empty path in both lists
        assertTrue(
          removed == List(Nil),
          added == List(Nil)
        )
      },
      test("deeply nested container changes") {
        val source = RecordNode(
          Map(
            "data" -> SeqNode(OptionNode(RecordNode(Map("old" -> PrimitiveNode))))
          )
        )
        val target = RecordNode(
          Map(
            "data" -> SeqNode(OptionNode(RecordNode(Map("new" -> PrimitiveNode))))
          )
        )
        val (removed, added) = TreeDiff.diff(source, target)
        assertTrue(
          removed == List(List(Segment.Field("data"), Segment.Element, Segment.Element, Segment.Field("old"))),
          added == List(List(Segment.Field("data"), Segment.Element, Segment.Element, Segment.Field("new")))
        )
      }
    ),
    suite("Path rendering")(
      test("empty path renders as <root>") {
        assertTrue(Path.render(Nil) == "<root>")
      },
      test("field path renders without prefix") {
        assertTrue(Path.render(List(Segment.Field("name"))) == "name")
      },
      test("nested field path") {
        assertTrue(Path.render(List(Segment.Field("address"), Segment.Field("city"))) == "address.city")
      },
      test("case segment renders with case: prefix") {
        assertTrue(Path.render(List(Segment.Case("Success"))) == "case:Success")
      },
      test("element segment renders as element") {
        assertTrue(Path.render(List(Segment.Element)) == "element")
      },
      test("complex path with multiple segment types") {
        val path = List(Segment.Field("items"), Segment.Element, Segment.Field("name"))
        assertTrue(Path.render(path) == "items.element.name")
      },
      test("map key/value segments") {
        assertTrue(Path.render(List(Segment.Field("data"), Segment.Key)) == "data.key")
        assertTrue(Path.render(List(Segment.Field("data"), Segment.Value)) == "data.value")
      }
    ),
    suite("extractShapeTree with wrapped types")(
      test("value class returns WrappedNode with PrimitiveNode inner") {
        val tree = extractShapeTree[UserId]
        assertTrue(tree == WrappedNode(PrimitiveNode))
      },
      test("value class with BigDecimal returns WrappedNode with PrimitiveNode inner") {
        val tree = extractShapeTree[Amount]
        assertTrue(tree == WrappedNode(PrimitiveNode))
      },
      test("case class with value class field has WrappedNode") {
        val tree = extractShapeTree[PersonWithUserId]
        assertTrue(
          tree == RecordNode(
            Map(
              "name" -> PrimitiveNode,
              "id"   -> WrappedNode(PrimitiveNode)
            )
          )
        )
      },
      test("case class wrapping value class (nested wrapping)") {
        // WrappedUserId is a regular case class wrapping UserId value class
        val tree = extractShapeTree[WrappedUserId]
        assertTrue(
          tree == RecordNode(
            Map(
              "inner" -> WrappedNode(PrimitiveNode)
            )
          )
        )
      },
      test("record with wrapped and value class fields") {
        val tree = extractShapeTree[OrderWithIds]
        assertTrue(
          tree == RecordNode(
            Map(
              "wrappedId" -> RecordNode(Map("inner" -> WrappedNode(PrimitiveNode))),
              "amount"    -> WrappedNode(PrimitiveNode)
            )
          )
        )
      },
      test("List of value classes has SeqNode with WrappedNode element") {
        val tree = extractShapeTree[WithListOfUserIds]
        assertTrue(
          tree == RecordNode(
            Map(
              "ids" -> SeqNode(WrappedNode(PrimitiveNode))
            )
          )
        )
      },
      test("Option of value class has OptionNode with WrappedNode element") {
        val tree = extractShapeTree[WithOptionAmount]
        assertTrue(
          tree == RecordNode(
            Map(
              "amount" -> OptionNode(WrappedNode(PrimitiveNode))
            )
          )
        )
      },
      test("Map with value class key and value has MapNode with WrappedNode") {
        val tree = extractShapeTree[WithMapUserIdAmount]
        assertTrue(
          tree == RecordNode(
            Map(
              "data" -> MapNode(WrappedNode(PrimitiveNode), WrappedNode(PrimitiveNode))
            )
          )
        )
      },
      test("value class containing record has WrappedNode with RecordNode inner") {
        val tree = extractShapeTree[BoxedPerson]
        assertTrue(
          tree == WrappedNode(
            RecordNode(
              Map(
                "name" -> PrimitiveNode,
                "age"  -> PrimitiveNode
              )
            )
          )
        )
      }
    ),
    suite("TreeDiff with wrapped types")(
      test("identical wrapped types have empty diff") {
        val tree             = WrappedNode(PrimitiveNode)
        val (removed, added) = TreeDiff.diff(tree, tree)
        assertTrue(removed.isEmpty, added.isEmpty)
      },
      test("wrapped inner type change appears in both lists") {
        val source           = WrappedNode(PrimitiveNode)
        val target           = WrappedNode(RecordNode(Map("x" -> PrimitiveNode)))
        val (removed, added) = TreeDiff.diff(source, target)
        val expectedPath     = List(Segment.Wrapped)
        assertTrue(
          removed == List(expectedPath),
          added == List(expectedPath)
        )
      },
      test("field change inside wrapped type has correct path") {
        val source           = RecordNode(Map("id" -> WrappedNode(RecordNode(Map("x" -> PrimitiveNode)))))
        val target           = RecordNode(Map("id" -> WrappedNode(RecordNode(Map("y" -> PrimitiveNode)))))
        val (removed, added) = TreeDiff.diff(source, target)
        assertTrue(
          removed == List(List(Segment.Field("id"), Segment.Wrapped, Segment.Field("x"))),
          added == List(List(Segment.Field("id"), Segment.Wrapped, Segment.Field("y")))
        )
      },
      test("wrapped containing record diff correctly") {
        val source           = WrappedNode(RecordNode(Map("x" -> PrimitiveNode)))
        val target           = WrappedNode(RecordNode(Map("y" -> PrimitiveNode)))
        val (removed, added) = TreeDiff.diff(source, target)
        assertTrue(
          removed == List(List(Segment.Wrapped, Segment.Field("x"))),
          added == List(List(Segment.Wrapped, Segment.Field("y")))
        )
      },
      test("wrapped to non-wrapped type change") {
        val source           = WrappedNode(PrimitiveNode)
        val target           = PrimitiveNode
        val (removed, added) = TreeDiff.diff(source, target)
        // Type mismatch at root
        assertTrue(
          removed == List(Nil),
          added == List(Nil)
        )
      }
    ),
    suite("extractShapeTree recursion through wrapped types")(
      test("direct recursion through value class produces compile error with helpful message") {
        assertZIO(typeCheck("""
          import zio.blocks.schema.migration.ShapeExtraction._
          case class RecursiveWrapper(self: RecursiveWrapper) extends AnyVal
          extractShapeTree[RecursiveWrapper]
        """))(Assertion.isLeft(Assertion.containsString("Recursive")))
      }
    ),
    suite("Path rendering with wrapped segment")(
      test("wrapped segment renders correctly") {
        assertTrue(Path.render(List(Segment.Wrapped)) == "wrapped")
      },
      test("field then wrapped renders correctly") {
        assertTrue(Path.render(List(Segment.Field("id"), Segment.Wrapped)) == "id.wrapped")
      },
      test("wrapped then field renders correctly") {
        assertTrue(Path.render(List(Segment.Wrapped, Segment.Field("x"))) == "wrapped.x")
      }
    )
  )
}
