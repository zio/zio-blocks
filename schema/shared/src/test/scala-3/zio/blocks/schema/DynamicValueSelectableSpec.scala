package zio.blocks.schema

import zio.test._

object DynamicValueSelectableSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("DynamicValueSelectableSpec")(
    suite("DynamicValue extends Selectable")(
      suite("Record field access")(
        test("accesses field by name using dot notation") {
          val record = DynamicValue.Record(
            Vector(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
              "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
            )
          )

          val name = record.selectDynamic("name")
          val age  = record.selectDynamic("age")

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

          val person  = record.selectDynamic("person")
          val name    = person.selectDynamic("name")
          val address = person.selectDynamic("address")
          val city    = address.selectDynamic("city")

          assertTrue(name == DynamicValue.Primitive(PrimitiveValue.String("Bob"))) &&
          assertTrue(city == DynamicValue.Primitive(PrimitiveValue.String("New York")))
        },
        test("throws NoSuchFieldException for missing field") {
          val record = DynamicValue.Record(
            Vector(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))
            )
          )

          val result = scala.util.Try(record.selectDynamic("missingField"))

          assertTrue(result.isFailure) &&
          assertTrue(result.failed.get.isInstanceOf[NoSuchFieldException]) &&
          assertTrue(result.failed.get.getMessage.contains("missingField"))
        }
      ),
      suite("Map key access")(
        test("accesses map entry by string key") {
          val map = DynamicValue.Map(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.String("key1")) -> DynamicValue.Primitive(PrimitiveValue.Int(100)),
              DynamicValue.Primitive(PrimitiveValue.String("key2")) -> DynamicValue.Primitive(PrimitiveValue.Int(200))
            )
          )

          val value1 = map.selectDynamic("key1")
          val value2 = map.selectDynamic("key2")

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

          val outer = map.selectDynamic("outer")
          val inner = outer.selectDynamic("inner")

          assertTrue(inner == DynamicValue.Primitive(PrimitiveValue.String("value")))
        },
        test("throws NoSuchFieldException for missing key") {
          val map = DynamicValue.Map(
            Vector(
              DynamicValue.Primitive(PrimitiveValue.String("key1")) -> DynamicValue.Primitive(PrimitiveValue.Int(100))
            )
          )

          val result = scala.util.Try(map.selectDynamic("missingKey"))

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

          val name = variant.selectDynamic("name")
          val age  = variant.selectDynamic("age")

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

          // Note: We use "data" instead of "value" because "value" is a case class accessor on Variant
          val data = variant.selectDynamic("data")

          assertTrue(data == DynamicValue.Primitive(PrimitiveValue.String("nested")))
        }
      ),
      suite("Primitive and Sequence errors")(
        test("throws UnsupportedOperationException for Primitive") {
          val primitive = DynamicValue.Primitive(PrimitiveValue.Int(42))

          val result = scala.util.Try(primitive.selectDynamic("anyField"))

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

          val result = scala.util.Try(sequence.selectDynamic("anyField"))

          assertTrue(result.isFailure) &&
          assertTrue(result.failed.get.isInstanceOf[UnsupportedOperationException]) &&
          assertTrue(result.failed.get.getMessage.contains("sequence"))
        }
      ),
      suite("Structural refinement types")(
        test("can be used with structural type refinement") {
          // In Scala 3, Selectable enables structural type refinement
          // The DynamicValue can be treated as having any field
          val record = DynamicValue.Record(
            Vector(
              "x" -> DynamicValue.Primitive(PrimitiveValue.Int(10)),
              "y" -> DynamicValue.Primitive(PrimitiveValue.Int(20))
            )
          )

          // Access via selectDynamic
          val x = record.selectDynamic("x")
          val y = record.selectDynamic("y")

          assertTrue(x == DynamicValue.Primitive(PrimitiveValue.Int(10))) &&
          assertTrue(y == DynamicValue.Primitive(PrimitiveValue.Int(20)))
        },
        test("works with complex nested structures") {
          val value = DynamicValue.Record(
            Vector(
              "user" -> DynamicValue.Record(
                Vector(
                  "profile" -> DynamicValue.Record(
                    Vector(
                      "settings" -> DynamicValue.Map(
                        Vector(
                          DynamicValue.Primitive(PrimitiveValue.String("theme")) ->
                            DynamicValue.Primitive(PrimitiveValue.String("dark"))
                        )
                      )
                    )
                  )
                )
              )
            )
          )

          val user     = value.selectDynamic("user")
          val profile  = user.selectDynamic("profile")
          val settings = profile.selectDynamic("settings")
          val theme    = settings.selectDynamic("theme")

          assertTrue(theme == DynamicValue.Primitive(PrimitiveValue.String("dark")))
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

          val config  = record.selectDynamic("config")
          val setting = config.selectDynamic("setting")

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

          val user  = map.selectDynamic("user")
          val email = user.selectDynamic("email")

          assertTrue(email == DynamicValue.Primitive(PrimitiveValue.String("test@example.com")))
        }
      )
    )
  )
}
