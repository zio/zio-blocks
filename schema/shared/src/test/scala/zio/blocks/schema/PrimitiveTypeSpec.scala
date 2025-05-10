package zio.blocks.schema

import zio.Scope
import zio.blocks.schema.Validation.None
import zio.test.Assertion.{equalTo, isLeft, isRight}
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assert}

object PrimitiveTypeSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment with Scope, Any] = suite("PrimitiveTypeSpec")(
    suite("PrimitiveType.Unit")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Unit
        assert(tpe.toDynamicValue(()))(equalTo(DynamicValue.Primitive(PrimitiveValue.Unit))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Unit)))(isRight(equalTo(()))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(1))))(
          isLeft(equalTo(SchemaError.invalidType(DynamicOptic.root, "Expected Unit")))
        )
      }
    ),
    suite("PrimitiveType.Byte")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Byte(None)
        assert(tpe.toDynamicValue(1))(equalTo(DynamicValue.Primitive(PrimitiveValue.Byte(1: Byte)))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Byte(1: Byte))))(isRight(equalTo(1: Byte))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(1))))(
          isLeft(equalTo(SchemaError.invalidType(DynamicOptic.root, "Expected Byte")))
        )
      }
    ),
    suite("PrimitiveType.Boolean")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Boolean(None)
        assert(tpe.toDynamicValue(true))(equalTo(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))(isRight(equalTo(true))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(1))))(
          isLeft(equalTo(SchemaError.invalidType(DynamicOptic.root, "Expected Boolean")))
        )
      }
    ),
    suite("PrimitiveType.Short")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Short(None)
        assert(tpe.toDynamicValue(1))(equalTo(DynamicValue.Primitive(PrimitiveValue.Short(1)))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Short(1))))(isRight(equalTo(1: Short))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(1))))(
          isLeft(equalTo(SchemaError.invalidType(DynamicOptic.root, "Expected Short")))
        )
      }
    ),
    suite("PrimitiveType.Char")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Char(None)
        assert(tpe.toDynamicValue('1'))(equalTo(DynamicValue.Primitive(PrimitiveValue.Char('1')))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Char('1'))))(isRight(equalTo('1'))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(1))))(
          isLeft(equalTo(SchemaError.invalidType(DynamicOptic.root, "Expected Char")))
        )
      }
    ),
    suite("PrimitiveType.Int")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Int(None)
        assert(tpe.toDynamicValue(1))(equalTo(DynamicValue.Primitive(PrimitiveValue.Int(1)))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(1))))(isRight(equalTo(1))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(DynamicOptic.root, "Expected Int")))
        )
      }
    ),
    suite("PrimitiveType.Float")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Float(None)
        assert(tpe.toDynamicValue(1))(equalTo(DynamicValue.Primitive(PrimitiveValue.Float(1.0f)))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Float(1.0f))))(isRight(equalTo(1.0f))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(DynamicOptic.root, "Expected Float")))
        )
      }
    ),
    suite("PrimitiveType.Long")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Long(None)
        assert(tpe.toDynamicValue(1))(equalTo(DynamicValue.Primitive(PrimitiveValue.Long(1L)))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(isRight(equalTo(1L))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Int(1))))(
          isLeft(equalTo(SchemaError.invalidType(DynamicOptic.root, "Expected Long")))
        )
      }
    ),
    suite("PrimitiveType.Double")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.Double(None)
        assert(tpe.toDynamicValue(1))(equalTo(DynamicValue.Primitive(PrimitiveValue.Double(1.0)))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Double(1.0))))(isRight(equalTo(1.0))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(DynamicOptic.root, "Expected Double")))
        )
      }
    ),
    suite("PrimitiveType.String")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.String(None)
        assert(tpe.toDynamicValue("WWW"))(equalTo(DynamicValue.Primitive(PrimitiveValue.String("WWW")))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.String("WWW"))))(isRight(equalTo("WWW"))) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(DynamicOptic.root, "Expected String")))
        )
      }
    ),
    suite("PrimitiveType.BigInt")(
      test("has consistent toDynamicValue and fromDynamicValue") {
        val tpe = PrimitiveType.BigInt(None)
        assert(tpe.toDynamicValue(BigInt(1)))(equalTo(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(1))))) &&
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
        assert(tpe.toDynamicValue(BigDecimal(1.0)))(
          equalTo(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(1.0))))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(1.0)))))(
          isRight(equalTo(BigDecimal(1.0)))
        ) &&
        assert(tpe.fromDynamicValue(DynamicValue.Primitive(PrimitiveValue.Long(1L))))(
          isLeft(equalTo(SchemaError.invalidType(DynamicOptic.root, "Expected BigDecimal")))
        )
      }
    )
  )
}
