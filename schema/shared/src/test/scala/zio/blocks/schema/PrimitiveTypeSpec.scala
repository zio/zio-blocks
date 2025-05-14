package zio.blocks.schema

import zio.Scope
import zio.blocks.schema.Validation.None
import zio.test.Assertion.{equalTo, isLeft, isRight}
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assert, assertTrue}

import java.time.DayOfWeek

object PrimitiveTypeSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment with Scope, Any] = suite("PrimitiveTypeSpec")(
    suite("PrimitiveType.Unit")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Unit
        assertTrue(tpe.toDynamicValue(()) == DynamicValue.Primitive(PrimitiveValue.Unit)) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Unit)))(isRight(equalTo(()))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(1))))(
          isLeft(equalTo(SchemaError.invalidType(DynamicOptic.root, "Expected Unit")))
        )
      }
    ),
    suite("PrimitiveType.Byte")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Byte(None)
        assertTrue(tpe.toDynamicValue(1) == DynamicValue.Primitive(PrimitiveValue.Byte(1: Byte))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Byte(1: Byte))))(isRight(equalTo(1: Byte))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(1))))(
          isLeft(equalTo(SchemaError.invalidType(DynamicOptic.root, "Expected Byte")))
        )
      }
    ),
    suite("PrimitiveType.Boolean")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Boolean(None)
        assertTrue(tpe.toDynamicValue(true) == DynamicValue.Primitive(PrimitiveValue.Boolean(true))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))(isRight(equalTo(true))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(1))))(
          isLeft(equalTo(SchemaError.invalidType(DynamicOptic.root, "Expected Boolean")))
        )
      }
    ),
    suite("PrimitiveType.Short")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Short(None)
        assertTrue(tpe.toDynamicValue(1) == DynamicValue.Primitive(PrimitiveValue.Short(1))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Short(1))))(isRight(equalTo(1: Short))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(1))))(
          isLeft(equalTo(SchemaError.invalidType(DynamicOptic.root, "Expected Short")))
        )
      }
    ),
    suite("PrimitiveType.Char")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Char(None)
        assertTrue(tpe.toDynamicValue('1') == DynamicValue.Primitive(PrimitiveValue.Char('1'))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Char('1'))))(isRight(equalTo('1'))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(1))))(
          isLeft(equalTo(SchemaError.invalidType(DynamicOptic.root, "Expected Char")))
        )
      }
    ),
    suite("PrimitiveType.Int")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Int(None)
        assertTrue(tpe.toDynamicValue(1) == DynamicValue.Primitive(PrimitiveValue.Int(1))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(1))))(isRight(equalTo(1))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(DynamicOptic.root, "Expected Int")))
        )
      }
    ),
    suite("PrimitiveType.Float")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Float(None)
        assertTrue(tpe.toDynamicValue(1) == DynamicValue.Primitive(PrimitiveValue.Float(1.0f))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Float(1.0f))))(isRight(equalTo(1.0f))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(DynamicOptic.root, "Expected Float")))
        )
      }
    ),
    suite("PrimitiveType.Long")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Long(None)
        assertTrue(tpe.toDynamicValue(1) == DynamicValue.Primitive(PrimitiveValue.Long(1L))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(isRight(equalTo(1L))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(1))))(
          isLeft(equalTo(SchemaError.invalidType(DynamicOptic.root, "Expected Long")))
        )
      }
    ),
    suite("PrimitiveType.Double")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Double(None)
        assertTrue(tpe.toDynamicValue(1) == DynamicValue.Primitive(PrimitiveValue.Double(1.0))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Double(1.0))))(isRight(equalTo(1.0))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(DynamicOptic.root, "Expected Double")))
        )
      }
    ),
    suite("PrimitiveType.String")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.String(None)
        assertTrue(tpe.toDynamicValue("WWW") == DynamicValue.Primitive(PrimitiveValue.String("WWW"))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.String("WWW"))))(isRight(equalTo("WWW"))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(DynamicOptic.root, "Expected String")))
        )
      }
    ),
    suite("PrimitiveType.BigInt")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.BigInt(None)
        assertTrue(tpe.toDynamicValue(BigInt(1)) == DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(1)))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(1)))))(
          isRight(equalTo(BigInt(1)))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(DynamicOptic.root, "Expected BigInt")))
        )
      }
    ),
    suite("PrimitiveType.BigDecimal")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.BigDecimal(None)
        assertTrue(
          tpe.toDynamicValue(BigDecimal(1.0)) == DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(1.0)))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(1.0)))))(
          isRight(equalTo(BigDecimal(1.0)))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(DynamicOptic.root, "Expected BigDecimal")))
        )
      }
    ),
    suite("PrimitiveType.DayOfWeek")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.DayOfWeek(None)
        assert(tpe.toDynamicValue(DayOfWeek.MONDAY))(
          equalTo(DynamicValue.Primitive(PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY)))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.DayOfWeek(DayOfWeek.MONDAY))))(
          isRight(equalTo(DayOfWeek.MONDAY))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(DynamicOptic.root, "Expected DayOfWeek")))
        )
      }
    )
  )
}
