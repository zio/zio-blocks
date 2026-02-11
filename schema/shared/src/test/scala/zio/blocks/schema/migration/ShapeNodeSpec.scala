package zio.blocks.schema.migration

import zio.test._

object ShapeNodeSpec extends ZIOSpecDefault {

  def spec = suite("ShapeNodeSpec")(
    suite("Segment.render")(
      test("Field renders as name") {
        assertTrue(Segment.Field("age").render == "age")
      },
      test("Case renders with prefix") {
        assertTrue(Segment.Case("Admin").render == "case:Admin")
      },
      test("Element renders as 'element'") {
        assertTrue(Segment.Element.render == "element")
      },
      test("Key renders as 'key'") {
        assertTrue(Segment.Key.render == "key")
      },
      test("Value renders as 'value'") {
        assertTrue(Segment.Value.render == "value")
      },
      test("Wrapped renders as 'wrapped'") {
        assertTrue(Segment.Wrapped.render == "wrapped")
      }
    ),
    suite("Path.render")(
      test("empty path renders as <root>") {
        assertTrue(Path.render(Nil) == "<root>")
      },
      test("single segment path renders correctly") {
        assertTrue(Path.render(List(Segment.Field("name"))) == "name")
      },
      test("multi-segment path renders with dots") {
        val path = List(Segment.Field("address"), Segment.Field("city"))
        assertTrue(Path.render(path) == "address.city")
      },
      test("mixed segment path renders correctly") {
        val path = List(Segment.Field("items"), Segment.Element, Segment.Field("name"))
        assertTrue(Path.render(path) == "items.element.name")
      }
    ),
    suite("TreeDiff")(
      test("identical primitives produce empty diff") {
        val (removed, added) = TreeDiff.diff(ShapeNode.PrimitiveNode, ShapeNode.PrimitiveNode)
        assertTrue(removed.isEmpty && added.isEmpty)
      },
      test("different node types produce path in both removed and added") {
        val (removed, added) = TreeDiff.diff(ShapeNode.PrimitiveNode, ShapeNode.RecordNode(Map.empty))
        assertTrue(removed.nonEmpty && added.nonEmpty)
      },
      test("added field appears in added list") {
        val source           = ShapeNode.RecordNode(Map("a" -> ShapeNode.PrimitiveNode))
        val target           = ShapeNode.RecordNode(Map("a" -> ShapeNode.PrimitiveNode, "b" -> ShapeNode.PrimitiveNode))
        val (removed, added) = TreeDiff.diff(source, target)
        assertTrue(removed.isEmpty && added.nonEmpty)
      },
      test("removed field appears in removed list") {
        val source           = ShapeNode.RecordNode(Map("a" -> ShapeNode.PrimitiveNode, "b" -> ShapeNode.PrimitiveNode))
        val target           = ShapeNode.RecordNode(Map("a" -> ShapeNode.PrimitiveNode))
        val (removed, added) = TreeDiff.diff(source, target)
        assertTrue(removed.nonEmpty && added.isEmpty)
      },
      test("sealed node case diff") {
        val source           = ShapeNode.SealedNode(Map("A" -> ShapeNode.PrimitiveNode))
        val target           = ShapeNode.SealedNode(Map("B" -> ShapeNode.PrimitiveNode))
        val (removed, added) = TreeDiff.diff(source, target)
        assertTrue(removed.nonEmpty && added.nonEmpty)
      },
      test("sequence element diff") {
        val source           = ShapeNode.SeqNode(ShapeNode.PrimitiveNode)
        val target           = ShapeNode.SeqNode(ShapeNode.RecordNode(Map("x" -> ShapeNode.PrimitiveNode)))
        val (removed, added) = TreeDiff.diff(source, target)
        assertTrue(removed.nonEmpty && added.nonEmpty)
      },
      test("option element diff") {
        val source           = ShapeNode.OptionNode(ShapeNode.PrimitiveNode)
        val target           = ShapeNode.OptionNode(ShapeNode.RecordNode(Map("x" -> ShapeNode.PrimitiveNode)))
        val (removed, added) = TreeDiff.diff(source, target)
        assertTrue(removed.nonEmpty && added.nonEmpty)
      },
      test("map key and value diff") {
        val source = ShapeNode.MapNode(ShapeNode.PrimitiveNode, ShapeNode.PrimitiveNode)
        val target = ShapeNode.MapNode(
          ShapeNode.RecordNode(Map("x" -> ShapeNode.PrimitiveNode)),
          ShapeNode.RecordNode(Map("y" -> ShapeNode.PrimitiveNode))
        )
        val (removed, added) = TreeDiff.diff(source, target)
        assertTrue(removed.nonEmpty && added.nonEmpty)
      },
      test("wrapped node diff") {
        val source           = ShapeNode.WrappedNode(ShapeNode.PrimitiveNode)
        val target           = ShapeNode.WrappedNode(ShapeNode.RecordNode(Map("x" -> ShapeNode.PrimitiveNode)))
        val (removed, added) = TreeDiff.diff(source, target)
        assertTrue(removed.nonEmpty && added.nonEmpty)
      },
      test("identical records produce empty diff") {
        val source           = ShapeNode.RecordNode(Map("a" -> ShapeNode.PrimitiveNode))
        val target           = ShapeNode.RecordNode(Map("a" -> ShapeNode.PrimitiveNode))
        val (removed, added) = TreeDiff.diff(source, target)
        assertTrue(removed.isEmpty && added.isEmpty)
      },
      test("type change at root produces path in both lists") {
        val (removed, added) = TreeDiff.diff(
          ShapeNode.SeqNode(ShapeNode.PrimitiveNode),
          ShapeNode.PrimitiveNode
        )
        assertTrue(removed.nonEmpty && added.nonEmpty)
      }
    )
  )
}
