package zio.blocks.schema

import zio.test._
import zio.test.Assertion._

object DynamicOpticNodeSchemaCoverageSpec extends SchemaBaseSpec {
  import DynamicOptic.Node

  def spec: Spec[TestEnvironment, Any] = suite("DynamicOpticNodeSchemaCoverageSpec")(
    test("roundtrips all Node variants via DynamicValue") {
      val nodes: List[Node] = List(
        Node.Field("foo"),
        Node.Case("Bar"),
        Node.AtIndex(0),
        Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("k"))),
        Node.AtIndices(Seq(0, 2, 4)),
        Node.AtMapKeys(
          Seq(DynamicValue.Primitive(PrimitiveValue.Int(1)), DynamicValue.Primitive(PrimitiveValue.Int(2)))
        ),
        Node.Elements,
        Node.MapKeys,
        Node.MapValues,
        Node.Wrapped
      )

      nodes.foldLeft(assertTrue(true)) { (acc, node) =>
        val schema = Schema[Node]
        acc && assert(schema.fromDynamicValue(schema.toDynamicValue(node)))(isRight(equalTo(node)))
      }
    },
    test("fails on unknown variant discriminator") {
      val schema = Schema[Node]
      val dv     = DynamicValue.Variant("NotARealCase", DynamicValue.Primitive(PrimitiveValue.Unit))

      assert(schema.fromDynamicValue(dv))(isLeft(anything))
    },
    test("fails when expecting a variant") {
      val schema = Schema[Node]

      assert(schema.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(1))))(isLeft(anything))
    },
    test("exercises variant matchers downcastOrNull branches") {
      val variant  = Schema[Node].reflect.asVariant.get
      val matchers = variant.matchers

      val cases: List[(Int, Node, Node)] = List(
        (0, Node.Field("foo"), Node.Case("Bar")),
        (1, Node.Case("Bar"), Node.Field("foo")),
        (2, Node.AtIndex(0), Node.Field("foo")),
        (3, Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("k"))), Node.Field("foo")),
        (4, Node.AtIndices(Seq(0, 2, 4)), Node.Field("foo")),
        (5, Node.AtMapKeys(Seq(DynamicValue.Primitive(PrimitiveValue.Int(1)))), Node.Field("foo")),
        (6, Node.Elements, Node.MapKeys),
        (7, Node.MapKeys, Node.Elements),
        (8, Node.MapValues, Node.Elements),
        (9, Node.Wrapped, Node.Elements)
      )

      cases.foldLeft(assertTrue(true)) { case (acc, (idx, okValue, wrongValue)) =>
        val matcher = matchers(idx)
        acc && assertTrue(matcher.downcastOrNull(okValue) != null) && assertTrue(
          matcher.downcastOrNull(wrongValue) == null
        )
      }
    }
  )
}
