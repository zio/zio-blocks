package zio.blocks.schema

import zio.test._
import zio.blocks.chunk.Chunk

/**
 * Coverage tests for DynamicValue operations.
 */
object DynamicValueCoverageSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("DynamicValueCoverageSpec")(
    primitiveValueTests,
    recordValueTests,
    variantValueTests,
    sequenceValueTests,
    mapValueTests,
    toStringTests
  )

  // Primitive value tests
  val primitiveValueTests = suite("Primitive values")(
    test("Boolean primitive") {
      val dv = DynamicValue.Primitive(PrimitiveValue.Boolean(true))
      assertTrue(dv.toString.nonEmpty)
    },
    test("Char primitive") {
      val dv = DynamicValue.Primitive(PrimitiveValue.Char('x'))
      assertTrue(dv.toString.nonEmpty)
    },
    test("Byte primitive") {
      val dv = DynamicValue.Primitive(PrimitiveValue.Byte(42.toByte))
      assertTrue(dv.toString.nonEmpty)
    },
    test("Short primitive") {
      val dv = DynamicValue.Primitive(PrimitiveValue.Short(123.toShort))
      assertTrue(dv.toString.nonEmpty)
    },
    test("Int primitive") {
      val dv = DynamicValue.Primitive(PrimitiveValue.Int(42))
      assertTrue(dv.toString.nonEmpty)
    },
    test("Long primitive") {
      val dv = DynamicValue.Primitive(PrimitiveValue.Long(42L))
      assertTrue(dv.toString.nonEmpty)
    },
    test("Float primitive") {
      val dv = DynamicValue.Primitive(PrimitiveValue.Float(3.14f))
      assertTrue(dv.toString.nonEmpty)
    },
    test("Double primitive") {
      val dv = DynamicValue.Primitive(PrimitiveValue.Double(3.14159))
      assertTrue(dv.toString.nonEmpty)
    },
    test("String primitive") {
      val dv = DynamicValue.Primitive(PrimitiveValue.String("hello"))
      assertTrue(dv.toString.nonEmpty)
    },
    test("BigInt primitive") {
      val dv = DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(123456789)))
      assertTrue(dv.toString.nonEmpty)
    },
    test("BigDecimal primitive") {
      val dv = DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal("3.14159")))
      assertTrue(dv.toString.nonEmpty)
    }
  )

  // Record value tests
  val recordValueTests = suite("Record values")(
    test("Empty record") {
      val dv = DynamicValue.Record(Chunk.empty)
      assertTrue(dv.fields.isEmpty)
    },
    test("Single field record") {
      val dv = DynamicValue.Record(
        "name" -> DynamicValue.Primitive(PrimitiveValue.String("test"))
      )
      assertTrue(dv.get("name").either.isRight)
    },
    test("Multi field record") {
      val dv = DynamicValue.Record(
        "name" -> DynamicValue.Primitive(PrimitiveValue.String("test")),
        "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(42))
      )
      assertTrue(
        dv.get("name").either.isRight,
        dv.get("age").either.isRight,
        dv.get("unknown").either.isLeft
      )
    },
    test("Nested record") {
      val inner = DynamicValue.Record(
        "value" -> DynamicValue.Primitive(PrimitiveValue.Int(1))
      )
      val outer = DynamicValue.Record(
        "nested" -> inner
      )
      assertTrue(outer.get("nested").either.isRight)
    }
  )

  // Variant value tests
  val variantValueTests = suite("Variant values")(
    test("Variant with matching case") {
      val dv = DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.Int(42)))
      assertTrue(dv.getCase("Some").nonEmpty)
    },
    test("Variant with non-matching case") {
      val dv = DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.Int(42)))
      assertTrue(dv.getCase("None").isEmpty)
    },
    test("Variant toString") {
      val dv = DynamicValue.Variant("Left", DynamicValue.Primitive(PrimitiveValue.String("error")))
      assertTrue(dv.toString.contains("Left"))
    }
  )

  // Sequence value tests
  val sequenceValueTests = suite("Sequence values")(
    test("Empty sequence") {
      val dv = DynamicValue.Sequence(Chunk.empty)
      assertTrue(dv.elements.isEmpty)
    },
    test("Non-empty sequence get valid index") {
      val dv = DynamicValue.Sequence(
        Chunk(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2)),
          DynamicValue.Primitive(PrimitiveValue.Int(3))
        )
      )
      assertTrue(dv.get(1).either.isRight)
    },
    test("Non-empty sequence get invalid index") {
      val dv = DynamicValue.Sequence(
        Chunk(
          DynamicValue.Primitive(PrimitiveValue.Int(1))
        )
      )
      assertTrue(dv.get(100).either.isLeft)
    },
    test("Sequence toString") {
      val dv = DynamicValue.Sequence(
        Chunk(
          DynamicValue.Primitive(PrimitiveValue.Int(1))
        )
      )
      assertTrue(dv.toString.nonEmpty)
    }
  )

  // Map value tests using the right method signature
  val mapValueTests = suite("Map values")(
    test("Empty map") {
      val dv = DynamicValue.Map(Chunk.empty)
      assertTrue(dv.entries.isEmpty)
    },
    test("Non-empty map") {
      val key: DynamicValue   = DynamicValue.Primitive(PrimitiveValue.String("key"))
      val value: DynamicValue = DynamicValue.Primitive(PrimitiveValue.Int(42))
      val dv                  = DynamicValue.Map(key -> value)
      assertTrue(dv.entries.nonEmpty)
    },
    test("Non-empty map get valid key") {
      val key: DynamicValue   = DynamicValue.Primitive(PrimitiveValue.String("key"))
      val value: DynamicValue = DynamicValue.Primitive(PrimitiveValue.Int(42))
      val dv                  = DynamicValue.Map(key -> value)
      assertTrue(dv.get(key).either.isRight)
    },
    test("Non-empty map get invalid key") {
      val key: DynamicValue        = DynamicValue.Primitive(PrimitiveValue.String("key"))
      val value: DynamicValue      = DynamicValue.Primitive(PrimitiveValue.Int(42))
      val dv                       = DynamicValue.Map(key -> value)
      val unknownKey: DynamicValue = DynamicValue.Primitive(PrimitiveValue.String("unknown"))
      assertTrue(dv.get(unknownKey).either.isLeft)
    }
  )

  // toString tests for all types
  val toStringTests = suite("toString coverage")(
    test("Record toString") {
      val dv = DynamicValue.Record(
        "field" -> DynamicValue.Primitive(PrimitiveValue.String("test"))
      )
      assertTrue(dv.toString.nonEmpty)
    },
    test("Map toString") {
      val key: DynamicValue   = DynamicValue.Primitive(PrimitiveValue.String("key"))
      val value: DynamicValue = DynamicValue.Primitive(PrimitiveValue.Int(42))
      val dv                  = DynamicValue.Map(key -> value)
      assertTrue(dv.toString.nonEmpty)
    },
    test("Null toString") {
      assertTrue(DynamicValue.Null.toString.nonEmpty)
    }
  )

}
