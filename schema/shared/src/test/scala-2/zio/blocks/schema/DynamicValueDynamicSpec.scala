package zio.blocks.schema

import scala.language.dynamics
import zio.test._

object DynamicValueDynamicSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("DynamicValueDynamicSpec")(
    suite("DynamicValue extends Dynamic")(
      suite("Record field access")(
        test("accesses field by name using dot notation") {
          val record = DynamicValue.Record(
            Vector(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
              "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
            )
          )

          val name = record.name
          val age  = record.age

          assertTrue(name == DynamicValue.Primitive(PrimitiveValue.String("Alice"))) &&
          assertTrue(age == DynamicValue.Primitive(PrimitiveValue.Int(30)))
        },
        test("accesses nested record fields") {
          val record = DynamicValue.Record(
            Vector(
              "person" -> DynamicValue.Record(
                Vector(
                  "name"    -> DynamicValue.Primitive(PrimitiveValue.String("Bob")),
                  "address" -> DynamicValue.Record(
                    Vector(
                      "city" -> DynamicValue.Primitive(PrimitiveValue.String("New York"))
                    )
                  )
                )
              )
            )
          )

          val person = record.person
          val name   = record.person.name
          val city   = record.person.address.city

          assertTrue(name == DynamicValue.Primitive(PrimitiveValue.String("Bob"))) &&
          assertTrue(city == DynamicValue.Primitive(PrimitiveValue.String("New York")))
        },
        test("throws NoSuchFieldException for missing field") {
          val record = DynamicValue.Record(
            Vector(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))
            )
          )

          val result = scala.util.Try(record.missingField)

          assertTrue(result.isFailure) &&
          assertTrue(result.failed.get.isInstanceOf[NoSuchFieldException]) &&
          assertTrue(result.failed.get.getMessage.contains("missingField"))
        }
      ),
      suite("Map key access")(
        test("accesses map entry by string key using dot notation") {
          val map = DynamicValue.Map(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.String("key1")) -> DynamicValue.Primitive(PrimitiveValue.Int(100)),
              DynamicValue.Primitive(PrimitiveValue.String("key2")) -> DynamicValue.Primitive(PrimitiveValue.Int(200))
            )
          )

          val value1 = map.key1
          val value2 = map.key2

          assertTrue(value1 == DynamicValue.Primitive(PrimitiveValue.Int(100))) &&
          assertTrue(value2 == DynamicValue.Primitive(PrimitiveValue.Int(200)))
        },
        test("accesses nested map values") {
          val map = DynamicValue.Map(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.String("outer")) -> DynamicValue.Map(
                Vector(
                  DynamicValue.Primitive(PrimitiveValue.String("inner")) -> DynamicValue.Primitive(
                    PrimitiveValue.String("value")
                  )
                )
              )
            )
          )

          val inner = map.outer.inner

          assertTrue(inner == DynamicValue.Primitive(PrimitiveValue.String("value")))
        },
        test("throws NoSuchFieldException for missing key") {
          val map = DynamicValue.Map(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.String("key1")) -> DynamicValue.Primitive(PrimitiveValue.Int(100))
            )
          )

          val result = scala.util.Try(map.missingKey)

          assertTrue(result.isFailure) &&
          assertTrue(result.failed.get.isInstanceOf[NoSuchFieldException]) &&
          assertTrue(result.failed.get.getMessage.contains("missingKey"))
        }
      ),
      suite("Variant delegation")(
        test("delegates to inner record value") {
          val variant = DynamicValue.Variant(
            "Person",
            DynamicValue.Record(
              Vector(
                "name" -> DynamicValue.Primitive(PrimitiveValue.String("Charlie")),
                "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(25))
              )
            )
          )

          val name = variant.name
          val age  = variant.age

          assertTrue(name == DynamicValue.Primitive(PrimitiveValue.String("Charlie"))) &&
          assertTrue(age == DynamicValue.Primitive(PrimitiveValue.Int(25)))
        },
        test("delegates through nested variants") {
          val variant = DynamicValue.Variant(
            "Outer",
            DynamicValue.Variant(
              "Inner",
              DynamicValue.Record(
                Vector(
                  "data" -> DynamicValue.Primitive(PrimitiveValue.String("nested"))
                )
              )
            )
          )

          // Variant delegates to inner value, which is also a Variant, which then delegates to its inner Record
          // Note: We use "data" instead of "value" because "value" is a case class accessor on Variant
          val data = variant.data

          assertTrue(data == DynamicValue.Primitive(PrimitiveValue.String("nested")))
        }
      ),
      suite("Primitive and Sequence errors")(
        test("throws UnsupportedOperationException for Primitive") {
          val primitive = DynamicValue.Primitive(PrimitiveValue.Int(42))

          val result = scala.util.Try(primitive.anyField)

          assertTrue(result.isFailure) &&
          assertTrue(result.failed.get.isInstanceOf[UnsupportedOperationException]) &&
          assertTrue(result.failed.get.getMessage.contains("primitive"))
        },
        test("throws UnsupportedOperationException for Sequence") {
          val sequence = DynamicValue.Sequence(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
              DynamicValue.Primitive(PrimitiveValue.Int(2))
            )
          )

          val result = scala.util.Try(sequence.anyField)

          assertTrue(result.isFailure) &&
          assertTrue(result.failed.get.isInstanceOf[UnsupportedOperationException]) &&
          assertTrue(result.failed.get.getMessage.contains("sequence"))
        }
      ),
      suite("Mixed access patterns")(
        test("accesses record field containing a map") {
          val record = DynamicValue.Record(
            Vector(
              "config" -> DynamicValue.Map(
                Vector(
                  DynamicValue.Primitive(PrimitiveValue.String("setting")) -> DynamicValue.Primitive(
                    PrimitiveValue.Boolean(true)
                  )
                )
              )
            )
          )

          val setting = record.config.setting

          assertTrue(setting == DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
        },
        test("accesses map containing a record") {
          val map = DynamicValue.Map(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.String("user")) -> DynamicValue.Record(
                Vector(
                  "email" -> DynamicValue.Primitive(PrimitiveValue.String("test@example.com"))
                )
              )
            )
          )

          val email = map.user.email

          assertTrue(email == DynamicValue.Primitive(PrimitiveValue.String("test@example.com")))
        }
      )
    )
  )
}
