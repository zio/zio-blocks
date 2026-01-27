package zio.blocks.schema

import zio.test._
import DynamicOptic.Node

/**
 * Coverage tests targeting DynamicOptic.Node manual schemas. Each Node type
 * exercises:
 *   - Constructor.construct (during fromDynamicValue)
 *   - Deconstructor.deconstruct (during toDynamicValue)
 *   - Discriminator.discriminate (during toDynamicValue for variant)
 *   - Matcher.downcastOrNull (during fromDynamicValue for variant)
 */
object NodeCoverageSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("NodeCoverageSpec")(
    nodeSchemaTests,
    dynamicOpticSchemaTests,
    toStringTests,
    toScalaStringTests
  )

  // ===========================================================================
  // Node Schema Tests - 10 variants
  // ===========================================================================
  val nodeSchemaTests = suite("Node schema coverage")(
    suite("Node.Field")(
      (1 to 10).map { i =>
        test(s"Field roundtrip $i") {
          val node: Node = Node.Field(s"field$i")
          val dv         = Schema[Node].toDynamicValue(node)
          val restored   = Schema[Node].fromDynamicValue(dv)
          assertTrue(restored == Right(node))
        }
      }: _*
    ),
    suite("Node.Case")(
      (1 to 10).map { i =>
        test(s"Case roundtrip $i") {
          val node: Node = Node.Case(s"case$i")
          val dv         = Schema[Node].toDynamicValue(node)
          val restored   = Schema[Node].fromDynamicValue(dv)
          assertTrue(restored == Right(node))
        }
      }: _*
    ),
    suite("Node.AtIndex")(
      (1 to 10).map { i =>
        test(s"AtIndex roundtrip $i") {
          val node: Node = Node.AtIndex(i * 10)
          val dv         = Schema[Node].toDynamicValue(node)
          val restored   = Schema[Node].fromDynamicValue(dv)
          assertTrue(restored == Right(node))
        }
      }: _*
    ),
    suite("Node.AtMapKey")(
      (1 to 10).map { i =>
        test(s"AtMapKey roundtrip $i") {
          val key        = DynamicValue.Primitive(PrimitiveValue.String(s"key$i"))
          val node: Node = Node.AtMapKey(key)
          val dv         = Schema[Node].toDynamicValue(node)
          val restored   = Schema[Node].fromDynamicValue(dv)
          assertTrue(restored == Right(node))
        }
      }: _*
    ),
    suite("Node.AtIndices")(
      (1 to 10).map { i =>
        test(s"AtIndices roundtrip $i") {
          val indices    = (0 until i).toSeq
          val node: Node = Node.AtIndices(indices)
          val dv         = Schema[Node].toDynamicValue(node)
          val restored   = Schema[Node].fromDynamicValue(dv)
          assertTrue(restored == Right(node))
        }
      }: _*
    ),
    suite("Node.AtMapKeys")(
      (1 to 10).map { i =>
        test(s"AtMapKeys roundtrip $i") {
          val keys       = (0 until i).map(j => DynamicValue.Primitive(PrimitiveValue.Int(j))).toSeq
          val node: Node = Node.AtMapKeys(keys)
          val dv         = Schema[Node].toDynamicValue(node)
          val restored   = Schema[Node].fromDynamicValue(dv)
          assertTrue(restored == Right(node))
        }
      }: _*
    ),
    suite("Node.Elements")(
      (1 to 10).map { i =>
        test(s"Elements roundtrip $i") {
          val node: Node = Node.Elements
          val dv         = Schema[Node].toDynamicValue(node)
          val restored   = Schema[Node].fromDynamicValue(dv)
          assertTrue(restored == Right(node))
        }
      }: _*
    ),
    suite("Node.MapKeys")(
      (1 to 10).map { i =>
        test(s"MapKeys roundtrip $i") {
          val node: Node = Node.MapKeys
          val dv         = Schema[Node].toDynamicValue(node)
          val restored   = Schema[Node].fromDynamicValue(dv)
          assertTrue(restored == Right(node))
        }
      }: _*
    ),
    suite("Node.MapValues")(
      (1 to 10).map { i =>
        test(s"MapValues roundtrip $i") {
          val node: Node = Node.MapValues
          val dv         = Schema[Node].toDynamicValue(node)
          val restored   = Schema[Node].fromDynamicValue(dv)
          assertTrue(restored == Right(node))
        }
      }: _*
    ),
    suite("Node.Wrapped")(
      (1 to 10).map { i =>
        test(s"Wrapped roundtrip $i") {
          val node: Node = Node.Wrapped
          val dv         = Schema[Node].toDynamicValue(node)
          val restored   = Schema[Node].fromDynamicValue(dv)
          assertTrue(restored == Right(node))
        }
      }: _*
    )
  )

  // ===========================================================================
  // DynamicOptic Schema Tests
  // ===========================================================================
  val dynamicOpticSchemaTests = suite("DynamicOptic schema coverage")(
    test("Empty optic") {
      val optic    = DynamicOptic.root
      val dv       = Schema[DynamicOptic].toDynamicValue(optic)
      val restored = Schema[DynamicOptic].fromDynamicValue(dv)
      assertTrue(restored == Right(optic))
    },
    test("Single field") {
      val optic    = DynamicOptic.root.field("foo")
      val dv       = Schema[DynamicOptic].toDynamicValue(optic)
      val restored = Schema[DynamicOptic].fromDynamicValue(dv)
      assertTrue(restored == Right(optic))
    },
    test("Complex path") {
      val optic    = DynamicOptic.root.field("users").at(0).field("name")
      val dv       = Schema[DynamicOptic].toDynamicValue(optic)
      val restored = Schema[DynamicOptic].fromDynamicValue(dv)
      assertTrue(restored == Right(optic))
    },
    test("With case") {
      val optic    = DynamicOptic.root.caseOf("Success").field("value")
      val dv       = Schema[DynamicOptic].toDynamicValue(optic)
      val restored = Schema[DynamicOptic].fromDynamicValue(dv)
      assertTrue(restored == Right(optic))
    },
    test("With atIndices") {
      val optic    = DynamicOptic.root.atIndices(1, 2, 3)
      val dv       = Schema[DynamicOptic].toDynamicValue(optic)
      val restored = Schema[DynamicOptic].fromDynamicValue(dv)
      assertTrue(restored == Right(optic))
    },
    test("With atKeys via DynamicValue") {
      val keys     = Seq(DynamicValue.Primitive(PrimitiveValue.Int(1)), DynamicValue.Primitive(PrimitiveValue.Int(2)))
      val optic    = DynamicOptic(IndexedSeq(Node.AtMapKeys(keys)))
      val dv       = Schema[DynamicOptic].toDynamicValue(optic)
      val restored = Schema[DynamicOptic].fromDynamicValue(dv)
      assertTrue(restored == Right(optic))
    },
    test("With atKey via DynamicValue") {
      val key      = DynamicValue.Primitive(PrimitiveValue.String("foo"))
      val optic    = DynamicOptic(IndexedSeq(Node.AtMapKey(key)))
      val dv       = Schema[DynamicOptic].toDynamicValue(optic)
      val restored = Schema[DynamicOptic].fromDynamicValue(dv)
      assertTrue(restored == Right(optic))
    },
    test("With elements") {
      val optic    = DynamicOptic.root.elements
      val dv       = Schema[DynamicOptic].toDynamicValue(optic)
      val restored = Schema[DynamicOptic].fromDynamicValue(dv)
      assertTrue(restored == Right(optic))
    },
    test("With mapKeys") {
      val optic    = DynamicOptic.root.mapKeys
      val dv       = Schema[DynamicOptic].toDynamicValue(optic)
      val restored = Schema[DynamicOptic].fromDynamicValue(dv)
      assertTrue(restored == Right(optic))
    },
    test("With mapValues") {
      val optic    = DynamicOptic.root.mapValues
      val dv       = Schema[DynamicOptic].toDynamicValue(optic)
      val restored = Schema[DynamicOptic].fromDynamicValue(dv)
      assertTrue(restored == Right(optic))
    },
    test("With wrapped") {
      val optic    = DynamicOptic.root.wrapped
      val dv       = Schema[DynamicOptic].toDynamicValue(optic)
      val restored = Schema[DynamicOptic].fromDynamicValue(dv)
      assertTrue(restored == Right(optic))
    },
    test("All node types combined") {
      val key   = DynamicValue.Primitive(PrimitiveValue.String("inner"))
      val optic = DynamicOptic(
        IndexedSeq(
          Node.Field("data"),
          Node.Case("List"),
          Node.AtIndex(0),
          Node.AtMapKey(key),
          Node.Elements,
          Node.MapValues,
          Node.Wrapped
        )
      )
      val dv       = Schema[DynamicOptic].toDynamicValue(optic)
      val restored = Schema[DynamicOptic].fromDynamicValue(dv)
      assertTrue(restored == Right(optic))
    }
  )

  // ===========================================================================
  // toString Tests - cover rendering paths
  // ===========================================================================
  val toStringTests = suite("DynamicOptic.toString coverage")(
    test("Empty renders as .") {
      assertTrue(DynamicOptic.root.toString == ".")
    },
    test("Field renders with dot") {
      assertTrue(DynamicOptic.root.field("foo").toString.contains(".foo"))
    },
    test("Case renders with angle brackets") {
      val str = DynamicOptic.root.caseOf("Success").toString
      assertTrue(str.contains("<Success>"))
    },
    test("AtIndex renders with brackets") {
      val str = DynamicOptic.root.at(5).toString
      assertTrue(str.contains("[5]"))
    },
    test("AtIndices renders comma-separated") {
      val str = DynamicOptic.root.atIndices(1, 2, 3).toString
      assertTrue(str.contains("[1,2,3]"))
    },
    test("AtMapKey renders with braces - string key") {
      val key   = DynamicValue.Primitive(PrimitiveValue.String("foo"))
      val optic = DynamicOptic(IndexedSeq(Node.AtMapKey(key)))
      val str   = optic.toString
      assertTrue(str.contains("{") && str.contains("}") && str.contains("foo"))
    },
    test("AtMapKey renders with braces - int key") {
      val key   = DynamicValue.Primitive(PrimitiveValue.Int(42))
      val optic = DynamicOptic(IndexedSeq(Node.AtMapKey(key)))
      val str   = optic.toString
      assertTrue(str.contains("{42}"))
    },
    test("AtMapKeys renders comma-separated") {
      val keys = Seq(
        DynamicValue.Primitive(PrimitiveValue.Int(1)),
        DynamicValue.Primitive(PrimitiveValue.Int(2)),
        DynamicValue.Primitive(PrimitiveValue.Int(3))
      )
      val optic = DynamicOptic(IndexedSeq(Node.AtMapKeys(keys)))
      val str   = optic.toString
      assertTrue(str.contains("{") && str.contains("1") && str.contains("2") && str.contains("3"))
    },
    test("Elements renders as [*]") {
      assertTrue(DynamicOptic.root.elements.toString.contains("[*]"))
    },
    test("MapKeys renders as {*:}") {
      assertTrue(DynamicOptic.root.mapKeys.toString.contains("{*:}"))
    },
    test("MapValues renders as {*}") {
      assertTrue(DynamicOptic.root.mapValues.toString.contains("{*}"))
    },
    test("Wrapped renders as .~") {
      assertTrue(DynamicOptic.root.wrapped.toString.contains(".~"))
    },
    test("Render special chars in string - newline") {
      val key   = DynamicValue.Primitive(PrimitiveValue.String("line\n1"))
      val optic = DynamicOptic(IndexedSeq(Node.AtMapKey(key)))
      val str   = optic.toString
      assertTrue(str.contains("\\n"))
    },
    test("Render special chars in string - tab") {
      val key   = DynamicValue.Primitive(PrimitiveValue.String("col\t1"))
      val optic = DynamicOptic(IndexedSeq(Node.AtMapKey(key)))
      val str   = optic.toString
      assertTrue(str.contains("\\t"))
    },
    test("Render special chars in string - return") {
      val key   = DynamicValue.Primitive(PrimitiveValue.String("line\r1"))
      val optic = DynamicOptic(IndexedSeq(Node.AtMapKey(key)))
      val str   = optic.toString
      assertTrue(str.contains("\\r"))
    },
    test("Render special chars in string - quote") {
      val key   = DynamicValue.Primitive(PrimitiveValue.String("say\"hello\""))
      val optic = DynamicOptic(IndexedSeq(Node.AtMapKey(key)))
      val str   = optic.toString
      assertTrue(str.contains("\\\""))
    },
    test("Render special chars in string - backslash") {
      val key   = DynamicValue.Primitive(PrimitiveValue.String("path\\to"))
      val optic = DynamicOptic(IndexedSeq(Node.AtMapKey(key)))
      val str   = optic.toString
      assertTrue(str.contains("\\\\"))
    },
    test("Render boolean key") {
      val key   = DynamicValue.Primitive(PrimitiveValue.Boolean(true))
      val optic = DynamicOptic(IndexedSeq(Node.AtMapKey(key)))
      val str   = optic.toString
      assertTrue(str.contains("true"))
    },
    test("Render char key") {
      val key   = DynamicValue.Primitive(PrimitiveValue.Char('x'))
      val optic = DynamicOptic(IndexedSeq(Node.AtMapKey(key)))
      val str   = optic.toString
      assertTrue(str.contains("'x'"))
    },
    test("Render char key with special chars - newline") {
      val key   = DynamicValue.Primitive(PrimitiveValue.Char('\n'))
      val optic = DynamicOptic(IndexedSeq(Node.AtMapKey(key)))
      val str   = optic.toString
      assertTrue(str.contains("\\"))
    },
    test("Render char key with special chars - tab") {
      val key   = DynamicValue.Primitive(PrimitiveValue.Char('\t'))
      val optic = DynamicOptic(IndexedSeq(Node.AtMapKey(key)))
      val str   = optic.toString
      assertTrue(str.contains("\\"))
    },
    test("Render byte key") {
      val key   = DynamicValue.Primitive(PrimitiveValue.Byte(42.toByte))
      val optic = DynamicOptic(IndexedSeq(Node.AtMapKey(key)))
      val str   = optic.toString
      assertTrue(str.contains("42"))
    },
    test("Render short key") {
      val key   = DynamicValue.Primitive(PrimitiveValue.Short(1234.toShort))
      val optic = DynamicOptic(IndexedSeq(Node.AtMapKey(key)))
      val str   = optic.toString
      assertTrue(str.contains("1234"))
    },
    test("Render long key") {
      val key   = DynamicValue.Primitive(PrimitiveValue.Long(999999999999L))
      val optic = DynamicOptic(IndexedSeq(Node.AtMapKey(key)))
      val str   = optic.toString
      assertTrue(str.contains("999999999999"))
    },
    test("Render float key") {
      val key   = DynamicValue.Primitive(PrimitiveValue.Float(3.14f))
      val optic = DynamicOptic(IndexedSeq(Node.AtMapKey(key)))
      val str   = optic.toString
      assertTrue(str.contains("3.14"))
    },
    test("Render double key") {
      val key   = DynamicValue.Primitive(PrimitiveValue.Double(2.71828))
      val optic = DynamicOptic(IndexedSeq(Node.AtMapKey(key)))
      val str   = optic.toString
      assertTrue(str.contains("2.71828"))
    }
  )

  // ===========================================================================
  // toScalaString Tests - cover Scala-style rendering paths
  // ===========================================================================
  val toScalaStringTests = suite("DynamicOptic.toScalaString coverage")(
    test("Empty renders as .") {
      assertTrue(DynamicOptic.root.toScalaString == ".")
    },
    test("Field renders with dot") {
      assertTrue(DynamicOptic.root.field("foo").toScalaString.contains(".foo"))
    },
    test("Case renders with when[]") {
      val str = DynamicOptic.root.caseOf("Success").toScalaString
      assertTrue(str.contains(".when[Success]"))
    },
    test("AtIndex renders with at()") {
      val str = DynamicOptic.root.at(5).toScalaString
      assertTrue(str.contains(".at(5)"))
    },
    test("AtIndices renders with atIndices()") {
      val str = DynamicOptic.root.atIndices(1, 2, 3).toScalaString
      assertTrue(str.contains(".atIndices(1, 2, 3)"))
    },
    test("AtMapKey renders with atKey()") {
      val key   = DynamicValue.Primitive(PrimitiveValue.String("foo"))
      val optic = DynamicOptic(IndexedSeq(Node.AtMapKey(key)))
      val str   = optic.toScalaString
      assertTrue(str.contains(".atKey("))
    },
    test("AtMapKeys renders with atKeys()") {
      val keys = Seq(
        DynamicValue.Primitive(PrimitiveValue.Int(1)),
        DynamicValue.Primitive(PrimitiveValue.Int(2))
      )
      val optic = DynamicOptic(IndexedSeq(Node.AtMapKeys(keys)))
      val str   = optic.toScalaString
      assertTrue(str.contains(".atKeys("))
    },
    test("Elements renders as .each") {
      assertTrue(DynamicOptic.root.elements.toScalaString.contains(".each"))
    },
    test("MapKeys renders as .eachKey") {
      assertTrue(DynamicOptic.root.mapKeys.toScalaString.contains(".eachKey"))
    },
    test("MapValues renders as .eachValue") {
      assertTrue(DynamicOptic.root.mapValues.toScalaString.contains(".eachValue"))
    },
    test("Wrapped renders as .wrapped") {
      assertTrue(DynamicOptic.root.wrapped.toScalaString.contains(".wrapped"))
    }
  )
}
