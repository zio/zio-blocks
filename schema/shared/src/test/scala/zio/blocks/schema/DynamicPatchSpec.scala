package zio.blocks.schema

import zio.blocks.chunk.Chunk
import zio.blocks.schema.patch.{DynamicPatch, PatchMode}
import zio.test._

/**
 * Tests for DynamicPatch operations to increase coverage of the $anon classes
 * in DynamicPatch.scala. Tests various operations, modes, and edge cases.
 */
object DynamicPatchSpec extends SchemaBaseSpec {

  case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }

  case class Address(street: String, city: String, zip: Int)
  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }

  case class Company(name: String, address: Address, employees: List[Person])
  object Company {
    implicit val schema: Schema[Company] = Schema.derived
  }

  def spec: Spec[Any, Any] = suite("DynamicPatchSpec")(
    suite("DynamicPatch.empty")(
      test("empty patch returns original value") {
        val person = Person("John", 30)
        val dv     = Schema[Person].toDynamicValue(person)
        val result = DynamicPatch.empty.apply(dv)
        assertTrue(result == Right(dv))
      },
      test("empty patch toString") {
        val patch = DynamicPatch.empty
        assertTrue(patch.toString == "DynamicPatch {}")
      },
      test("empty patch isEmpty") {
        assertTrue(DynamicPatch.empty.isEmpty)
      }
    ),
    suite("DynamicPatch.root with Operation.Set")(
      test("replace primitive at root") {
        val dv    = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val patch = DynamicPatch.root(
          DynamicPatch.Operation.Set(
            DynamicValue.Primitive(PrimitiveValue.Int(100))
          )
        )
        val result = patch.apply(dv)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(100))))
      },
      test("replace string at root") {
        val dv    = DynamicValue.Primitive(PrimitiveValue.String("old"))
        val patch = DynamicPatch.root(
          DynamicPatch.Operation.Set(
            DynamicValue.Primitive(PrimitiveValue.String("new"))
          )
        )
        val result = patch.apply(dv)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("new"))))
      }
    ),
    suite("DynamicPatch with paths")(
      test("set field in record") {
        val dv = DynamicValue.Record(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
          "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(25))
        )
        val optic = DynamicOptic.root.field("name")
        val patch = DynamicPatch(
          optic,
          DynamicPatch.Operation.Set(
            DynamicValue.Primitive(PrimitiveValue.String("Bob"))
          )
        )
        val result = patch.apply(dv)
        assertTrue(result.isRight)
        val record    = result.toOption.get.asInstanceOf[DynamicValue.Record]
        val nameValue = record.fields.find(_._1 == "name").map(_._2)
        assertTrue(nameValue == Some(DynamicValue.Primitive(PrimitiveValue.String("Bob"))))
      },
      test("missing field in strict mode") {
        val dv    = DynamicValue.Record("name" -> DynamicValue.Primitive(PrimitiveValue.String("Test")))
        val optic = DynamicOptic.root.field("missing")
        val patch = DynamicPatch(
          optic,
          DynamicPatch.Operation.Set(
            DynamicValue.Primitive(PrimitiveValue.String("New"))
          )
        )
        val result = patch.apply(dv, PatchMode.Strict)
        assertTrue(result.isLeft)
      },
      test("missing field in lenient mode returns original") {
        val dv    = DynamicValue.Record("name" -> DynamicValue.Primitive(PrimitiveValue.String("Test")))
        val optic = DynamicOptic.root.field("missing")
        val patch = DynamicPatch(
          optic,
          DynamicPatch.Operation.Set(
            DynamicValue.Primitive(PrimitiveValue.String("New"))
          )
        )
        val result = patch.apply(dv, PatchMode.Lenient)
        assertTrue(result == Right(dv))
      }
    ),
    suite("DynamicPatch.apply modes")(
      test("strict mode fails on type mismatch") {
        val dv    = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val optic = DynamicOptic.root.field("name")
        val patch = DynamicPatch(
          optic,
          DynamicPatch.Operation.Set(
            DynamicValue.Primitive(PrimitiveValue.String("Test"))
          )
        )
        val result = patch.apply(dv, PatchMode.Strict)
        assertTrue(result.isLeft)
      }
    ),
    suite("nested navigation")(
      test("navigate to nested record field") {
        val address = Address("123 Main", "NYC", 10001)
        val company = Company("Acme", address, Nil)
        val dv      = Schema[Company].toDynamicValue(company)
        val optic   = DynamicOptic.root.field("address").field("city")
        val patch   = DynamicPatch(
          optic,
          DynamicPatch.Operation.Set(
            DynamicValue.Primitive(PrimitiveValue.String("LA"))
          )
        )
        val result = patch.apply(dv)
        assertTrue(result.isRight)
        val updated = Schema[Company].fromDynamicValue(result.toOption.get)
        assertTrue(updated.map(_.address.city) == Right("LA"))
      }
    ),
    suite("sequence operations")(
      test("set element at index") {
        val dv = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(2)),
            DynamicValue.Primitive(PrimitiveValue.Int(3))
          )
        )
        val optic = DynamicOptic.root.at(1)
        val patch = DynamicPatch(
          optic,
          DynamicPatch.Operation.Set(
            DynamicValue.Primitive(PrimitiveValue.Int(20))
          )
        )
        val result = patch.apply(dv)
        assertTrue(result.isRight)
      },
      test("index out of bounds fails in strict mode") {
        val dv = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.Int(1))
          )
        )
        val optic = DynamicOptic.root.at(10)
        val patch = DynamicPatch(
          optic,
          DynamicPatch.Operation.Set(
            DynamicValue.Primitive(PrimitiveValue.Int(100))
          )
        )
        val result = patch.apply(dv, PatchMode.Strict)
        assertTrue(result.isLeft)
      }
    ),
    suite("patch composition")(
      test("compose multiple patches") {
        val dv = DynamicValue.Record(
          "a" -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
          "b" -> DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
        val patch1 = DynamicPatch(
          DynamicOptic.root.field("a"),
          DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(10)))
        )
        val patch2 = DynamicPatch(
          DynamicOptic.root.field("b"),
          DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(20)))
        )
        val combined = patch1 ++ patch2
        val result   = combined.apply(dv)
        assertTrue(result.isRight)
      },
      test("combined patch is not empty") {
        val patch1   = DynamicPatch.root(DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val patch2   = DynamicPatch.root(DynamicPatch.Operation.Set(DynamicValue.Primitive(PrimitiveValue.Int(2))))
        val combined = patch1 ++ patch2
        assertTrue(!combined.isEmpty)
      }
    ),
    suite("DynamicPatch toString")(
      test("non-empty patch toString contains DynamicPatch") {
        val patch = DynamicPatch.root(
          DynamicPatch.Operation.Set(
            DynamicValue.Primitive(PrimitiveValue.String("Test"))
          )
        )
        assertTrue(patch.toString.contains("DynamicPatch"))
      }
    ),
    suite("variant navigation")(
      test("navigate into matching variant case") {
        val dv    = DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.Int(42)))
        val optic = DynamicOptic.root.caseOf("Some")
        val patch = DynamicPatch(
          optic,
          DynamicPatch.Operation.Set(
            DynamicValue.Primitive(PrimitiveValue.Int(100))
          )
        )
        val result = patch.apply(dv)
        assertTrue(result.isRight)
      },
      test("non-matching variant case fails in strict mode") {
        val dv    = DynamicValue.Variant("None", DynamicValue.Record())
        val optic = DynamicOptic.root.caseOf("Some")
        val patch = DynamicPatch(
          optic,
          DynamicPatch.Operation.Set(
            DynamicValue.Primitive(PrimitiveValue.Int(100))
          )
        )
        val result = patch.apply(dv, PatchMode.Strict)
        assertTrue(result.isLeft)
      }
    ),
    suite("map operations")(
      test("set value at map key") {
        val dv = DynamicValue.Map(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.String("key1")) -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.String("key2")) -> DynamicValue.Primitive(PrimitiveValue.Int(2))
          )
        )
        val optic = DynamicOptic.root.atKey("key1")
        val patch = DynamicPatch(
          optic,
          DynamicPatch.Operation.Set(
            DynamicValue.Primitive(PrimitiveValue.Int(100))
          )
        )
        val result = patch.apply(dv)
        assertTrue(result.isRight)
      },
      test("missing key fails") {
        val dv = DynamicValue.Map(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1))
          )
        )
        val optic = DynamicOptic.root.atKey("missing")
        val patch = DynamicPatch(
          optic,
          DynamicPatch.Operation.Set(
            DynamicValue.Primitive(PrimitiveValue.Int(100))
          )
        )
        val result = patch.apply(dv, PatchMode.Strict)
        assertTrue(result.isLeft)
      }
    )
  )
}
