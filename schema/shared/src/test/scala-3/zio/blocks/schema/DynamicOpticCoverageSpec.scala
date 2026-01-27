package zio.blocks.schema

import zio.test._

/**
 * Coverage tests for DynamicOptic.Node prism matchers. Each Node type has a
 * downcastOrNull matcher that needs to be exercised with both matching and
 * non-matching inputs.
 */
object DynamicOpticCoverageSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("DynamicOpticCoverageSpec")(
    nodeSchemaTests,
    nodePrismMatchTests,
    nodePrismNonMatchTests,
    dynamicOpticSchemaTests,
    dynamicOpticRenderingTests,
    exhaustivePrismCrossTypeTests,
    fromKVTests,
    selectionOperationTests
  )

  // All Node instances for testing
  val fieldNode: DynamicOptic.Node     = DynamicOptic.Node.Field("testField")
  val caseNode: DynamicOptic.Node      = DynamicOptic.Node.Case("TestCase")
  val atIndexNode: DynamicOptic.Node   = DynamicOptic.Node.AtIndex(5)
  val atMapKeyNode: DynamicOptic.Node  = DynamicOptic.Node.AtMapKey(DynamicValue.Primitive(PrimitiveValue.String("key")))
  val atIndicesNode: DynamicOptic.Node = DynamicOptic.Node.AtIndices(Seq(0, 1, 2))
  val atMapKeysNode: DynamicOptic.Node = DynamicOptic.Node.AtMapKeys(
    Seq(
      DynamicValue.Primitive(PrimitiveValue.String("k1")),
      DynamicValue.Primitive(PrimitiveValue.String("k2"))
    )
  )
  val elementsNode: DynamicOptic.Node  = DynamicOptic.Node.Elements
  val mapKeysNode: DynamicOptic.Node   = DynamicOptic.Node.MapKeys
  val mapValuesNode: DynamicOptic.Node = DynamicOptic.Node.MapValues
  val wrappedNode: DynamicOptic.Node   = DynamicOptic.Node.Wrapped

  val allNodes: Vector[DynamicOptic.Node] = Vector(
    fieldNode,
    caseNode,
    atIndexNode,
    atMapKeyNode,
    atIndicesNode,
    atMapKeysNode,
    elementsNode,
    mapKeysNode,
    mapValuesNode,
    wrappedNode
  )

  // Schema-based round-trip tests for each Node type
  val nodeSchemaTests = suite("Node schema round-trip")(
    test("Field node round-trip") {
      val schema = Schema[DynamicOptic.Node]
      val dv     = schema.reflect.toDynamicValue(fieldNode)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(fieldNode))
    },
    test("Case node round-trip") {
      val schema = Schema[DynamicOptic.Node]
      val dv     = schema.reflect.toDynamicValue(caseNode)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(caseNode))
    },
    test("AtIndex node round-trip") {
      val schema = Schema[DynamicOptic.Node]
      val dv     = schema.reflect.toDynamicValue(atIndexNode)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(atIndexNode))
    },
    test("AtMapKey node round-trip") {
      val schema = Schema[DynamicOptic.Node]
      val dv     = schema.reflect.toDynamicValue(atMapKeyNode)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(atMapKeyNode))
    },
    test("AtIndices node round-trip") {
      val schema = Schema[DynamicOptic.Node]
      val dv     = schema.reflect.toDynamicValue(atIndicesNode)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(atIndicesNode))
    },
    test("AtMapKeys node round-trip") {
      val schema = Schema[DynamicOptic.Node]
      val dv     = schema.reflect.toDynamicValue(atMapKeysNode)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(atMapKeysNode))
    },
    test("Elements node round-trip") {
      val schema = Schema[DynamicOptic.Node]
      val dv     = schema.reflect.toDynamicValue(elementsNode)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(elementsNode))
    },
    test("MapKeys node round-trip") {
      val schema = Schema[DynamicOptic.Node]
      val dv     = schema.reflect.toDynamicValue(mapKeysNode)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(mapKeysNode))
    },
    test("MapValues node round-trip") {
      val schema = Schema[DynamicOptic.Node]
      val dv     = schema.reflect.toDynamicValue(mapValuesNode)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(mapValuesNode))
    },
    test("Wrapped node round-trip") {
      val schema = Schema[DynamicOptic.Node]
      val dv     = schema.reflect.toDynamicValue(wrappedNode)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(wrappedNode))
    }
  )

  // Test each prism matching its correct type
  val nodePrismMatchTests = suite("Node prism matching tests")(
    test("Field prism matches Field") {
      val schema  = Schema[DynamicOptic.Node]
      val variant = schema.reflect.asVariant.get
      val prism   = variant.prismByIndex[DynamicOptic.Node.Field](0).get
      assertTrue(prism.getOption(fieldNode).isDefined)
    },
    test("Case prism matches Case") {
      val schema  = Schema[DynamicOptic.Node]
      val variant = schema.reflect.asVariant.get
      val prism   = variant.prismByIndex[DynamicOptic.Node.Case](1).get
      assertTrue(prism.getOption(caseNode).isDefined)
    },
    test("AtIndex prism matches AtIndex") {
      val schema  = Schema[DynamicOptic.Node]
      val variant = schema.reflect.asVariant.get
      val prism   = variant.prismByIndex[DynamicOptic.Node.AtIndex](2).get
      assertTrue(prism.getOption(atIndexNode).isDefined)
    },
    test("AtMapKey prism matches AtMapKey") {
      val schema  = Schema[DynamicOptic.Node]
      val variant = schema.reflect.asVariant.get
      val prism   = variant.prismByIndex[DynamicOptic.Node.AtMapKey](3).get
      assertTrue(prism.getOption(atMapKeyNode).isDefined)
    },
    test("AtIndices prism matches AtIndices") {
      val schema  = Schema[DynamicOptic.Node]
      val variant = schema.reflect.asVariant.get
      val prism   = variant.prismByIndex[DynamicOptic.Node.AtIndices](4).get
      assertTrue(prism.getOption(atIndicesNode).isDefined)
    },
    test("AtMapKeys prism matches AtMapKeys") {
      val schema  = Schema[DynamicOptic.Node]
      val variant = schema.reflect.asVariant.get
      val prism   = variant.prismByIndex[DynamicOptic.Node.AtMapKeys](5).get
      assertTrue(prism.getOption(atMapKeysNode).isDefined)
    },
    test("Elements prism matches Elements") {
      val schema  = Schema[DynamicOptic.Node]
      val variant = schema.reflect.asVariant.get
      val prism   = variant.prismByIndex[DynamicOptic.Node.Elements.type](6).get
      assertTrue(prism.getOption(elementsNode).isDefined)
    },
    test("MapKeys prism matches MapKeys") {
      val schema  = Schema[DynamicOptic.Node]
      val variant = schema.reflect.asVariant.get
      val prism   = variant.prismByIndex[DynamicOptic.Node.MapKeys.type](7).get
      assertTrue(prism.getOption(mapKeysNode).isDefined)
    },
    test("MapValues prism matches MapValues") {
      val schema  = Schema[DynamicOptic.Node]
      val variant = schema.reflect.asVariant.get
      val prism   = variant.prismByIndex[DynamicOptic.Node.MapValues.type](8).get
      assertTrue(prism.getOption(mapValuesNode).isDefined)
    },
    test("Wrapped prism matches Wrapped") {
      val schema  = Schema[DynamicOptic.Node]
      val variant = schema.reflect.asVariant.get
      val prism   = variant.prismByIndex[DynamicOptic.Node.Wrapped.type](9).get
      assertTrue(prism.getOption(wrappedNode).isDefined)
    }
  )

  // Test each prism NOT matching other types (exercises all downcastOrNull branches)
  // Each prism is tested against ALL non-matching node types to maximize coverage
  val nodePrismNonMatchTests = suite("Node prism non-matching tests")(
    // Field prism should not match other types
    test("Field prism rejects all other types") {
      val schema  = Schema[DynamicOptic.Node]
      val variant = schema.reflect.asVariant.get
      val prism   = variant.prismByIndex[DynamicOptic.Node.Field](0).get
      assertTrue(
        prism.getOption(caseNode).isEmpty,
        prism.getOption(atIndexNode).isEmpty,
        prism.getOption(atMapKeyNode).isEmpty,
        prism.getOption(atIndicesNode).isEmpty,
        prism.getOption(atMapKeysNode).isEmpty,
        prism.getOption(elementsNode).isEmpty,
        prism.getOption(mapKeysNode).isEmpty,
        prism.getOption(mapValuesNode).isEmpty,
        prism.getOption(wrappedNode).isEmpty
      )
    },
    // Case prism should not match other types
    test("Case prism rejects all other types") {
      val schema  = Schema[DynamicOptic.Node]
      val variant = schema.reflect.asVariant.get
      val prism   = variant.prismByIndex[DynamicOptic.Node.Case](1).get
      assertTrue(
        prism.getOption(fieldNode).isEmpty,
        prism.getOption(atIndexNode).isEmpty,
        prism.getOption(atMapKeyNode).isEmpty,
        prism.getOption(atIndicesNode).isEmpty,
        prism.getOption(atMapKeysNode).isEmpty,
        prism.getOption(elementsNode).isEmpty,
        prism.getOption(mapKeysNode).isEmpty,
        prism.getOption(mapValuesNode).isEmpty,
        prism.getOption(wrappedNode).isEmpty
      )
    },
    // AtIndex prism should not match other types
    test("AtIndex prism rejects all other types") {
      val schema  = Schema[DynamicOptic.Node]
      val variant = schema.reflect.asVariant.get
      val prism   = variant.prismByIndex[DynamicOptic.Node.AtIndex](2).get
      assertTrue(
        prism.getOption(fieldNode).isEmpty,
        prism.getOption(caseNode).isEmpty,
        prism.getOption(atMapKeyNode).isEmpty,
        prism.getOption(atIndicesNode).isEmpty,
        prism.getOption(atMapKeysNode).isEmpty,
        prism.getOption(elementsNode).isEmpty,
        prism.getOption(mapKeysNode).isEmpty,
        prism.getOption(mapValuesNode).isEmpty,
        prism.getOption(wrappedNode).isEmpty
      )
    },
    // AtMapKey prism should not match other types
    test("AtMapKey prism rejects all other types") {
      val schema  = Schema[DynamicOptic.Node]
      val variant = schema.reflect.asVariant.get
      val prism   = variant.prismByIndex[DynamicOptic.Node.AtMapKey](3).get
      assertTrue(
        prism.getOption(fieldNode).isEmpty,
        prism.getOption(caseNode).isEmpty,
        prism.getOption(atIndexNode).isEmpty,
        prism.getOption(atIndicesNode).isEmpty,
        prism.getOption(atMapKeysNode).isEmpty,
        prism.getOption(elementsNode).isEmpty,
        prism.getOption(mapKeysNode).isEmpty,
        prism.getOption(mapValuesNode).isEmpty,
        prism.getOption(wrappedNode).isEmpty
      )
    },
    // AtIndices prism should not match other types
    test("AtIndices prism rejects all other types") {
      val schema  = Schema[DynamicOptic.Node]
      val variant = schema.reflect.asVariant.get
      val prism   = variant.prismByIndex[DynamicOptic.Node.AtIndices](4).get
      assertTrue(
        prism.getOption(fieldNode).isEmpty,
        prism.getOption(caseNode).isEmpty,
        prism.getOption(atIndexNode).isEmpty,
        prism.getOption(atMapKeyNode).isEmpty,
        prism.getOption(atMapKeysNode).isEmpty,
        prism.getOption(elementsNode).isEmpty,
        prism.getOption(mapKeysNode).isEmpty,
        prism.getOption(mapValuesNode).isEmpty,
        prism.getOption(wrappedNode).isEmpty
      )
    },
    // AtMapKeys prism should not match other types
    test("AtMapKeys prism rejects all other types") {
      val schema  = Schema[DynamicOptic.Node]
      val variant = schema.reflect.asVariant.get
      val prism   = variant.prismByIndex[DynamicOptic.Node.AtMapKeys](5).get
      assertTrue(
        prism.getOption(fieldNode).isEmpty,
        prism.getOption(caseNode).isEmpty,
        prism.getOption(atIndexNode).isEmpty,
        prism.getOption(atMapKeyNode).isEmpty,
        prism.getOption(atIndicesNode).isEmpty,
        prism.getOption(elementsNode).isEmpty,
        prism.getOption(mapKeysNode).isEmpty,
        prism.getOption(mapValuesNode).isEmpty,
        prism.getOption(wrappedNode).isEmpty
      )
    },
    // Elements prism should not match other types
    test("Elements prism rejects all other types") {
      val schema  = Schema[DynamicOptic.Node]
      val variant = schema.reflect.asVariant.get
      val prism   = variant.prismByIndex[DynamicOptic.Node.Elements.type](6).get
      assertTrue(
        prism.getOption(fieldNode).isEmpty,
        prism.getOption(caseNode).isEmpty,
        prism.getOption(atIndexNode).isEmpty,
        prism.getOption(atMapKeyNode).isEmpty,
        prism.getOption(atIndicesNode).isEmpty,
        prism.getOption(atMapKeysNode).isEmpty,
        prism.getOption(mapKeysNode).isEmpty,
        prism.getOption(mapValuesNode).isEmpty,
        prism.getOption(wrappedNode).isEmpty
      )
    },
    // MapKeys prism should not match other types
    test("MapKeys prism rejects all other types") {
      val schema  = Schema[DynamicOptic.Node]
      val variant = schema.reflect.asVariant.get
      val prism   = variant.prismByIndex[DynamicOptic.Node.MapKeys.type](7).get
      assertTrue(
        prism.getOption(fieldNode).isEmpty,
        prism.getOption(caseNode).isEmpty,
        prism.getOption(atIndexNode).isEmpty,
        prism.getOption(atMapKeyNode).isEmpty,
        prism.getOption(atIndicesNode).isEmpty,
        prism.getOption(atMapKeysNode).isEmpty,
        prism.getOption(elementsNode).isEmpty,
        prism.getOption(mapValuesNode).isEmpty,
        prism.getOption(wrappedNode).isEmpty
      )
    },
    // MapValues prism should not match other types
    test("MapValues prism rejects all other types") {
      val schema  = Schema[DynamicOptic.Node]
      val variant = schema.reflect.asVariant.get
      val prism   = variant.prismByIndex[DynamicOptic.Node.MapValues.type](8).get
      assertTrue(
        prism.getOption(fieldNode).isEmpty,
        prism.getOption(caseNode).isEmpty,
        prism.getOption(atIndexNode).isEmpty,
        prism.getOption(atMapKeyNode).isEmpty,
        prism.getOption(atIndicesNode).isEmpty,
        prism.getOption(atMapKeysNode).isEmpty,
        prism.getOption(elementsNode).isEmpty,
        prism.getOption(mapKeysNode).isEmpty,
        prism.getOption(wrappedNode).isEmpty
      )
    },
    // Wrapped prism should not match other types
    test("Wrapped prism rejects all other types") {
      val schema  = Schema[DynamicOptic.Node]
      val variant = schema.reflect.asVariant.get
      val prism   = variant.prismByIndex[DynamicOptic.Node.Wrapped.type](9).get
      assertTrue(
        prism.getOption(fieldNode).isEmpty,
        prism.getOption(caseNode).isEmpty,
        prism.getOption(atIndexNode).isEmpty,
        prism.getOption(atMapKeyNode).isEmpty,
        prism.getOption(atIndicesNode).isEmpty,
        prism.getOption(atMapKeysNode).isEmpty,
        prism.getOption(elementsNode).isEmpty,
        prism.getOption(mapKeysNode).isEmpty,
        prism.getOption(mapValuesNode).isEmpty
      )
    }
  )

  // DynamicOptic schema tests
  val dynamicOpticSchemaTests = suite("DynamicOptic schema round-trip")(
    test("Empty DynamicOptic round-trip") {
      val optic  = DynamicOptic.root
      val schema = Schema[DynamicOptic]
      val dv     = schema.reflect.toDynamicValue(optic)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(optic))
    },
    test("Single field path round-trip") {
      val optic  = DynamicOptic.root.field("name")
      val schema = Schema[DynamicOptic]
      val dv     = schema.reflect.toDynamicValue(optic)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(optic))
    },
    test("Nested field path round-trip") {
      val optic  = DynamicOptic.root.field("outer").field("inner").field("value")
      val schema = Schema[DynamicOptic]
      val dv     = schema.reflect.toDynamicValue(optic)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(optic))
    },
    test("Case path round-trip") {
      val optic  = DynamicOptic.root.caseOf("SomeCase")
      val schema = Schema[DynamicOptic]
      val dv     = schema.reflect.toDynamicValue(optic)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(optic))
    },
    test("Elements path round-trip") {
      val optic  = DynamicOptic.elements
      val schema = Schema[DynamicOptic]
      val dv     = schema.reflect.toDynamicValue(optic)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(optic))
    },
    test("MapKeys path round-trip") {
      val optic  = DynamicOptic.mapKeys
      val schema = Schema[DynamicOptic]
      val dv     = schema.reflect.toDynamicValue(optic)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(optic))
    },
    test("MapValues path round-trip") {
      val optic  = DynamicOptic.mapValues
      val schema = Schema[DynamicOptic]
      val dv     = schema.reflect.toDynamicValue(optic)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(optic))
    },
    test("Wrapped path round-trip") {
      val optic  = DynamicOptic.wrapped
      val schema = Schema[DynamicOptic]
      val dv     = schema.reflect.toDynamicValue(optic)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(optic))
    },
    test("Complex mixed path round-trip") {
      val optic  = DynamicOptic.root.field("data").caseOf("Some").field("value")
      val schema = Schema[DynamicOptic]
      val dv     = schema.reflect.toDynamicValue(optic)
      val result = schema.reflect.fromDynamicValue(dv, Nil)
      assertTrue(result == Right(optic))
    }
  )

  // DynamicOptic rendering tests
  val dynamicOpticRenderingTests = suite("DynamicOptic rendering")(
    test("Root path renders correctly") {
      val optic = DynamicOptic.root
      val str   = optic.toString
      assertTrue(str == ".")
    },
    test("Field path renders correctly") {
      val optic = DynamicOptic.root.field("name")
      val str   = optic.toString
      assertTrue(str.contains("name"))
    },
    test("Case path renders correctly") {
      val optic = DynamicOptic.root.caseOf("MyCase")
      val str   = optic.toString
      assertTrue(str.contains("MyCase"))
    },
    test("Elements path renders correctly") {
      val optic = DynamicOptic.elements
      val str   = optic.toString
      assertTrue(str.contains("[*]"))
    },
    test("MapKeys path renders correctly") {
      val optic = DynamicOptic.mapKeys
      val str   = optic.toString
      assertTrue(str.contains("{*:}"))
    },
    test("MapValues path renders correctly") {
      val optic = DynamicOptic.mapValues
      val str   = optic.toString
      assertTrue(str.contains("{*}"))
    },
    test("Wrapped path renders correctly") {
      val optic = DynamicOptic.wrapped
      val str   = optic.toString
      assertTrue(str.contains(".~"))
    }
  )

  // Exhaustive cross-type prism tests - each prism tested against all 10 node types
  val variant = Schema[DynamicOptic.Node].reflect.asVariant.get

  val exhaustivePrismCrossTypeTests = suite("Exhaustive prism cross-type coverage")(
    // Field prism (index 0) against all types
    test("Field prism matches Field") {
      val prism = variant.prismByIndex[DynamicOptic.Node.Field](0).get
      assertTrue(prism.getOption(fieldNode).isDefined)
    },
    test("Field prism rejects Case") {
      val prism = variant.prismByIndex[DynamicOptic.Node.Field](0).get
      assertTrue(prism.getOption(caseNode).isEmpty)
    },
    test("Field prism rejects AtIndex") {
      val prism = variant.prismByIndex[DynamicOptic.Node.Field](0).get
      assertTrue(prism.getOption(atIndexNode).isEmpty)
    },
    test("Field prism rejects AtMapKey") {
      val prism = variant.prismByIndex[DynamicOptic.Node.Field](0).get
      assertTrue(prism.getOption(atMapKeyNode).isEmpty)
    },
    test("Field prism rejects AtIndices") {
      val prism = variant.prismByIndex[DynamicOptic.Node.Field](0).get
      assertTrue(prism.getOption(atIndicesNode).isEmpty)
    },
    test("Field prism rejects AtMapKeys") {
      val prism = variant.prismByIndex[DynamicOptic.Node.Field](0).get
      assertTrue(prism.getOption(atMapKeysNode).isEmpty)
    },
    test("Field prism rejects Elements") {
      val prism = variant.prismByIndex[DynamicOptic.Node.Field](0).get
      assertTrue(prism.getOption(elementsNode).isEmpty)
    },
    test("Field prism rejects MapKeys") {
      val prism = variant.prismByIndex[DynamicOptic.Node.Field](0).get
      assertTrue(prism.getOption(mapKeysNode).isEmpty)
    },
    test("Field prism rejects MapValues") {
      val prism = variant.prismByIndex[DynamicOptic.Node.Field](0).get
      assertTrue(prism.getOption(mapValuesNode).isEmpty)
    },
    test("Field prism rejects Wrapped") {
      val prism = variant.prismByIndex[DynamicOptic.Node.Field](0).get
      assertTrue(prism.getOption(wrappedNode).isEmpty)
    },
    // Case prism (index 1) against all types
    test("Case prism matches Case") {
      val prism = variant.prismByIndex[DynamicOptic.Node.Case](1).get
      assertTrue(prism.getOption(caseNode).isDefined)
    },
    test("Case prism rejects Field") {
      val prism = variant.prismByIndex[DynamicOptic.Node.Case](1).get
      assertTrue(prism.getOption(fieldNode).isEmpty)
    },
    test("Case prism rejects AtIndex") {
      val prism = variant.prismByIndex[DynamicOptic.Node.Case](1).get
      assertTrue(prism.getOption(atIndexNode).isEmpty)
    },
    test("Case prism rejects AtMapKey") {
      val prism = variant.prismByIndex[DynamicOptic.Node.Case](1).get
      assertTrue(prism.getOption(atMapKeyNode).isEmpty)
    },
    test("Case prism rejects AtIndices") {
      val prism = variant.prismByIndex[DynamicOptic.Node.Case](1).get
      assertTrue(prism.getOption(atIndicesNode).isEmpty)
    },
    test("Case prism rejects AtMapKeys") {
      val prism = variant.prismByIndex[DynamicOptic.Node.Case](1).get
      assertTrue(prism.getOption(atMapKeysNode).isEmpty)
    },
    test("Case prism rejects Elements") {
      val prism = variant.prismByIndex[DynamicOptic.Node.Case](1).get
      assertTrue(prism.getOption(elementsNode).isEmpty)
    },
    test("Case prism rejects MapKeys") {
      val prism = variant.prismByIndex[DynamicOptic.Node.Case](1).get
      assertTrue(prism.getOption(mapKeysNode).isEmpty)
    },
    test("Case prism rejects MapValues") {
      val prism = variant.prismByIndex[DynamicOptic.Node.Case](1).get
      assertTrue(prism.getOption(mapValuesNode).isEmpty)
    },
    test("Case prism rejects Wrapped") {
      val prism = variant.prismByIndex[DynamicOptic.Node.Case](1).get
      assertTrue(prism.getOption(wrappedNode).isEmpty)
    },
    // AtIndex prism (index 2) against all types
    test("AtIndex prism matches AtIndex") {
      val prism = variant.prismByIndex[DynamicOptic.Node.AtIndex](2).get
      assertTrue(prism.getOption(atIndexNode).isDefined)
    },
    test("AtIndex prism rejects Field") {
      val prism = variant.prismByIndex[DynamicOptic.Node.AtIndex](2).get
      assertTrue(prism.getOption(fieldNode).isEmpty)
    },
    test("AtIndex prism rejects Case") {
      val prism = variant.prismByIndex[DynamicOptic.Node.AtIndex](2).get
      assertTrue(prism.getOption(caseNode).isEmpty)
    },
    test("AtIndex prism rejects AtMapKey") {
      val prism = variant.prismByIndex[DynamicOptic.Node.AtIndex](2).get
      assertTrue(prism.getOption(atMapKeyNode).isEmpty)
    },
    test("AtIndex prism rejects AtIndices") {
      val prism = variant.prismByIndex[DynamicOptic.Node.AtIndex](2).get
      assertTrue(prism.getOption(atIndicesNode).isEmpty)
    },
    test("AtIndex prism rejects AtMapKeys") {
      val prism = variant.prismByIndex[DynamicOptic.Node.AtIndex](2).get
      assertTrue(prism.getOption(atMapKeysNode).isEmpty)
    },
    test("AtIndex prism rejects Elements") {
      val prism = variant.prismByIndex[DynamicOptic.Node.AtIndex](2).get
      assertTrue(prism.getOption(elementsNode).isEmpty)
    },
    test("AtIndex prism rejects MapKeys") {
      val prism = variant.prismByIndex[DynamicOptic.Node.AtIndex](2).get
      assertTrue(prism.getOption(mapKeysNode).isEmpty)
    },
    test("AtIndex prism rejects MapValues") {
      val prism = variant.prismByIndex[DynamicOptic.Node.AtIndex](2).get
      assertTrue(prism.getOption(mapValuesNode).isEmpty)
    },
    test("AtIndex prism rejects Wrapped") {
      val prism = variant.prismByIndex[DynamicOptic.Node.AtIndex](2).get
      assertTrue(prism.getOption(wrappedNode).isEmpty)
    },
    // AtMapKey prism (index 3) against all types
    test("AtMapKey prism matches AtMapKey") {
      val prism = variant.prismByIndex[DynamicOptic.Node.AtMapKey](3).get
      assertTrue(prism.getOption(atMapKeyNode).isDefined)
    },
    test("AtMapKey prism rejects Field") {
      val prism = variant.prismByIndex[DynamicOptic.Node.AtMapKey](3).get
      assertTrue(prism.getOption(fieldNode).isEmpty)
    },
    test("AtMapKey prism rejects Case") {
      val prism = variant.prismByIndex[DynamicOptic.Node.AtMapKey](3).get
      assertTrue(prism.getOption(caseNode).isEmpty)
    },
    test("AtMapKey prism rejects AtIndex") {
      val prism = variant.prismByIndex[DynamicOptic.Node.AtMapKey](3).get
      assertTrue(prism.getOption(atIndexNode).isEmpty)
    },
    test("AtMapKey prism rejects AtIndices") {
      val prism = variant.prismByIndex[DynamicOptic.Node.AtMapKey](3).get
      assertTrue(prism.getOption(atIndicesNode).isEmpty)
    },
    test("AtMapKey prism rejects AtMapKeys") {
      val prism = variant.prismByIndex[DynamicOptic.Node.AtMapKey](3).get
      assertTrue(prism.getOption(atMapKeysNode).isEmpty)
    },
    test("AtMapKey prism rejects Elements") {
      val prism = variant.prismByIndex[DynamicOptic.Node.AtMapKey](3).get
      assertTrue(prism.getOption(elementsNode).isEmpty)
    },
    test("AtMapKey prism rejects MapKeys") {
      val prism = variant.prismByIndex[DynamicOptic.Node.AtMapKey](3).get
      assertTrue(prism.getOption(mapKeysNode).isEmpty)
    },
    test("AtMapKey prism rejects MapValues") {
      val prism = variant.prismByIndex[DynamicOptic.Node.AtMapKey](3).get
      assertTrue(prism.getOption(mapValuesNode).isEmpty)
    },
    test("AtMapKey prism rejects Wrapped") {
      val prism = variant.prismByIndex[DynamicOptic.Node.AtMapKey](3).get
      assertTrue(prism.getOption(wrappedNode).isEmpty)
    },
    // AtIndices prism (index 4) against all types
    test("AtIndices prism matches AtIndices") {
      val prism = variant.prismByIndex[DynamicOptic.Node.AtIndices](4).get
      assertTrue(prism.getOption(atIndicesNode).isDefined)
    },
    test("AtIndices prism rejects others") {
      val prism = variant.prismByIndex[DynamicOptic.Node.AtIndices](4).get
      assertTrue(
        prism.getOption(fieldNode).isEmpty &&
          prism.getOption(caseNode).isEmpty &&
          prism.getOption(atIndexNode).isEmpty &&
          prism.getOption(atMapKeyNode).isEmpty &&
          prism.getOption(atMapKeysNode).isEmpty &&
          prism.getOption(elementsNode).isEmpty &&
          prism.getOption(mapKeysNode).isEmpty &&
          prism.getOption(mapValuesNode).isEmpty &&
          prism.getOption(wrappedNode).isEmpty
      )
    },
    // AtMapKeys prism (index 5) against all types
    test("AtMapKeys prism matches AtMapKeys") {
      val prism = variant.prismByIndex[DynamicOptic.Node.AtMapKeys](5).get
      assertTrue(prism.getOption(atMapKeysNode).isDefined)
    },
    test("AtMapKeys prism rejects others") {
      val prism = variant.prismByIndex[DynamicOptic.Node.AtMapKeys](5).get
      assertTrue(
        prism.getOption(fieldNode).isEmpty &&
          prism.getOption(caseNode).isEmpty &&
          prism.getOption(atIndexNode).isEmpty &&
          prism.getOption(atMapKeyNode).isEmpty &&
          prism.getOption(atIndicesNode).isEmpty &&
          prism.getOption(elementsNode).isEmpty &&
          prism.getOption(mapKeysNode).isEmpty &&
          prism.getOption(mapValuesNode).isEmpty &&
          prism.getOption(wrappedNode).isEmpty
      )
    },
    // Elements prism (index 6) against all types
    test("Elements prism matches Elements") {
      val prism = variant.prismByIndex[DynamicOptic.Node.Elements.type](6).get
      assertTrue(prism.getOption(elementsNode).isDefined)
    },
    test("Elements prism rejects others") {
      val prism = variant.prismByIndex[DynamicOptic.Node.Elements.type](6).get
      assertTrue(
        prism.getOption(fieldNode).isEmpty &&
          prism.getOption(caseNode).isEmpty &&
          prism.getOption(atIndexNode).isEmpty &&
          prism.getOption(atMapKeyNode).isEmpty &&
          prism.getOption(atIndicesNode).isEmpty &&
          prism.getOption(atMapKeysNode).isEmpty &&
          prism.getOption(mapKeysNode).isEmpty &&
          prism.getOption(mapValuesNode).isEmpty &&
          prism.getOption(wrappedNode).isEmpty
      )
    },
    // MapKeys prism (index 7) against all types
    test("MapKeys prism matches MapKeys") {
      val prism = variant.prismByIndex[DynamicOptic.Node.MapKeys.type](7).get
      assertTrue(prism.getOption(mapKeysNode).isDefined)
    },
    test("MapKeys prism rejects others") {
      val prism = variant.prismByIndex[DynamicOptic.Node.MapKeys.type](7).get
      assertTrue(
        prism.getOption(fieldNode).isEmpty &&
          prism.getOption(caseNode).isEmpty &&
          prism.getOption(atIndexNode).isEmpty &&
          prism.getOption(atMapKeyNode).isEmpty &&
          prism.getOption(atIndicesNode).isEmpty &&
          prism.getOption(atMapKeysNode).isEmpty &&
          prism.getOption(elementsNode).isEmpty &&
          prism.getOption(mapValuesNode).isEmpty &&
          prism.getOption(wrappedNode).isEmpty
      )
    },
    // MapValues prism (index 8) against all types
    test("MapValues prism matches MapValues") {
      val prism = variant.prismByIndex[DynamicOptic.Node.MapValues.type](8).get
      assertTrue(prism.getOption(mapValuesNode).isDefined)
    },
    test("MapValues prism rejects others") {
      val prism = variant.prismByIndex[DynamicOptic.Node.MapValues.type](8).get
      assertTrue(
        prism.getOption(fieldNode).isEmpty &&
          prism.getOption(caseNode).isEmpty &&
          prism.getOption(atIndexNode).isEmpty &&
          prism.getOption(atMapKeyNode).isEmpty &&
          prism.getOption(atIndicesNode).isEmpty &&
          prism.getOption(atMapKeysNode).isEmpty &&
          prism.getOption(elementsNode).isEmpty &&
          prism.getOption(mapKeysNode).isEmpty &&
          prism.getOption(wrappedNode).isEmpty
      )
    },
    // Wrapped prism (index 9) against all types
    test("Wrapped prism matches Wrapped") {
      val prism = variant.prismByIndex[DynamicOptic.Node.Wrapped.type](9).get
      assertTrue(prism.getOption(wrappedNode).isDefined)
    },
    test("Wrapped prism rejects others") {
      val prism = variant.prismByIndex[DynamicOptic.Node.Wrapped.type](9).get
      assertTrue(
        prism.getOption(fieldNode).isEmpty &&
          prism.getOption(caseNode).isEmpty &&
          prism.getOption(atIndexNode).isEmpty &&
          prism.getOption(atMapKeyNode).isEmpty &&
          prism.getOption(atIndicesNode).isEmpty &&
          prism.getOption(atMapKeysNode).isEmpty &&
          prism.getOption(elementsNode).isEmpty &&
          prism.getOption(mapKeysNode).isEmpty &&
          prism.getOption(mapValuesNode).isEmpty
      )
    }
  )

  // fromKV tests
  val fromKVTests = suite("DynamicValue.fromKV coverage")(
    test("Empty kvs returns empty record") {
      val result = DynamicValue.fromKV(Seq.empty)
      assertTrue(result.isRight)
    },
    test("Single field KV") {
      val path   = DynamicOptic.root.field("name")
      val value  = DynamicValue.Primitive(PrimitiveValue.String("test"))
      val result = DynamicValue.fromKV(Seq((path, value)))
      assertTrue(result.isRight)
    },
    test("Multiple fields KV") {
      val path1  = DynamicOptic.root.field("name")
      val value1 = DynamicValue.Primitive(PrimitiveValue.String("Alice"))
      val path2  = DynamicOptic.root.field("age")
      val value2 = DynamicValue.Primitive(PrimitiveValue.Int(30))
      val result = DynamicValue.fromKV(Seq((path1, value1), (path2, value2)))
      assertTrue(result.isRight)
    },
    test("Nested field KV") {
      val path   = DynamicOptic.root.field("user").field("name")
      val value  = DynamicValue.Primitive(PrimitiveValue.String("Bob"))
      val result = DynamicValue.fromKV(Seq((path, value)))
      assertTrue(result.isRight)
    },
    test("Array index KV") {
      val path   = DynamicOptic.root.field("items").at(0)
      val value  = DynamicValue.Primitive(PrimitiveValue.Int(42))
      val result = DynamicValue.fromKV(Seq((path, value)))
      assertTrue(result.isRight)
    },
    test("Map key KV") {
      val path   = DynamicOptic.root.field("data").atKey("key1")
      val value  = DynamicValue.Primitive(PrimitiveValue.Int(100))
      val result = DynamicValue.fromKV(Seq((path, value)))
      assertTrue(result.isRight)
    },
    test("Variant case KV") {
      val path   = DynamicOptic.root.caseOf("Some")
      val value  = DynamicValue.Primitive(PrimitiveValue.Int(42))
      val result = DynamicValue.fromKV(Seq((path, value)))
      assertTrue(result.isRight)
    },
    test("fromKVUnsafe with valid input") {
      val path   = DynamicOptic.root.field("x")
      val value  = DynamicValue.Primitive(PrimitiveValue.Int(1))
      val result = DynamicValue.fromKVUnsafe(Seq((path, value)))
      assertTrue(result != null)
    },
    test("fromKVUnsafe with empty input") {
      val result = DynamicValue.fromKVUnsafe(Seq.empty)
      assertTrue(result != null)
    },
    test("Sparse array with gaps") {
      val path   = DynamicOptic.root.field("sparse").at(5)
      val value  = DynamicValue.Primitive(PrimitiveValue.Int(42))
      val result = DynamicValue.fromKV(Seq((path, value)))
      assertTrue(result.isRight)
    }
  )

  // Selection operation tests
  import zio.blocks.chunk.Chunk

  val selectionOperationTests = suite("DynamicValueSelection coverage")(
    test("isSuccess for valid selection") {
      val dv        = DynamicValue.Record("name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")))
      val selection = DynamicValueSelection.succeed(dv)
      assertTrue(selection.isSuccess)
    },
    test("isFailure for failed selection") {
      val selection = DynamicValueSelection.fail(DynamicValueError("test error"))
      assertTrue(selection.isFailure)
    },
    test("nonEmpty for non-empty selection") {
      val dv        = DynamicValue.Primitive(PrimitiveValue.Int(42))
      val selection = DynamicValueSelection.succeed(dv)
      assertTrue(selection.nonEmpty)
    },
    test("isEmpty for empty selection") {
      val selection = DynamicValueSelection.empty
      assertTrue(selection.isEmpty)
    },
    test("error returns Some for failure") {
      val selection = DynamicValueSelection.fail(DynamicValueError("test error"))
      assertTrue(selection.error.isDefined)
    },
    test("error returns None for success") {
      val dv        = DynamicValue.Primitive(PrimitiveValue.Int(42))
      val selection = DynamicValueSelection.succeed(dv)
      assertTrue(selection.error.isEmpty)
    },
    test("toChunk returns values on success") {
      val dv        = DynamicValue.Primitive(PrimitiveValue.Int(42))
      val selection = DynamicValueSelection.succeed(dv)
      assertTrue(selection.toChunk.nonEmpty)
    },
    test("toChunk returns empty on failure") {
      val selection = DynamicValueSelection.fail(DynamicValueError("error"))
      assertTrue(selection.toChunk.isEmpty)
    },
    test("one returns Right for single value") {
      val dv        = DynamicValue.Primitive(PrimitiveValue.Int(42))
      val selection = DynamicValueSelection.succeed(dv)
      assertTrue(selection.one.isRight)
    },
    test("one returns Left for empty") {
      val selection = DynamicValueSelection.empty
      assertTrue(selection.one.isLeft)
    },
    test("filter keeps matching values") {
      val dv        = DynamicValue.Primitive(PrimitiveValue.Int(42))
      val selection = DynamicValueSelection.succeed(dv)
      val filtered  = selection.filter(_ => true)
      assertTrue(filtered.nonEmpty)
    },
    test("filter removes non-matching values") {
      val dv        = DynamicValue.Primitive(PrimitiveValue.Int(42))
      val selection = DynamicValueSelection.succeed(dv)
      val filtered  = selection.filter(_ => false)
      assertTrue(filtered.isEmpty)
    },
    test("orElse returns alternative on failure") {
      val failed      = DynamicValueSelection.fail(DynamicValueError("error"))
      val dv          = DynamicValue.Primitive(PrimitiveValue.Int(42))
      val alternative = DynamicValueSelection.succeed(dv)
      assertTrue(failed.orElse(alternative).isSuccess)
    },
    test("++ concatenates selections") {
      val dv1      = DynamicValue.Primitive(PrimitiveValue.Int(1))
      val dv2      = DynamicValue.Primitive(PrimitiveValue.Int(2))
      val s1       = DynamicValueSelection.succeed(dv1)
      val s2       = DynamicValueSelection.succeed(dv2)
      val combined = s1 ++ s2
      assertTrue(combined.size == 2)
    },
    test("primitives filters primitives") {
      val dv        = DynamicValue.Primitive(PrimitiveValue.Int(42))
      val selection = DynamicValueSelection.succeed(dv)
      assertTrue(selection.primitives.nonEmpty)
    },
    test("records filters records") {
      val dv        = DynamicValue.Record("x" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
      val selection = DynamicValueSelection.succeed(dv)
      assertTrue(selection.records.nonEmpty)
    },
    test("variants filters variants") {
      val dv        = DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.Int(1)))
      val selection = DynamicValueSelection.succeed(dv)
      assertTrue(selection.variants.nonEmpty)
    },
    test("sequences filters sequences") {
      val dv        = DynamicValue.Sequence(Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1))))
      val selection = DynamicValueSelection.succeed(dv)
      assertTrue(selection.sequences.nonEmpty)
    },
    test("maps filters maps") {
      val key: DynamicValue   = DynamicValue.Primitive(PrimitiveValue.String("k"))
      val value: DynamicValue = DynamicValue.Primitive(PrimitiveValue.Int(1))
      val mapValue            = DynamicValue.Map(key -> value)
      val selection           = DynamicValueSelection.succeed(mapValue)
      assertTrue(selection.maps.nonEmpty)
    },
    test("nulls filters nulls") {
      val selection = DynamicValueSelection.succeed(DynamicValue.Null)
      assertTrue(selection.nulls.nonEmpty)
    },
    test("sortFields works") {
      val dv = DynamicValue.Record(
        "b" -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
        "a" -> DynamicValue.Primitive(PrimitiveValue.Int(2))
      )
      val selection = DynamicValueSelection.succeed(dv)
      assertTrue(selection.sortFields.isSuccess)
    },
    test("normalize works") {
      val dv        = DynamicValue.Record("x" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
      val selection = DynamicValueSelection.succeed(dv)
      assertTrue(selection.normalize.isSuccess)
    }
  )
}
