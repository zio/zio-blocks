package zio.blocks.schema

import scala.annotation.nowarn
import zio.test._
import zio.test.Assertion._

object MigrationSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("MigrationSpec")(
    suite("Identity Law")(
      test("identity migration returns the same value") {
        val record = DynamicValue.Record(
          Vector(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("John"))),
            ("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
          )
        )
        val migration = Migration.identity[Any]
        assert(migration(record))(isRight(equalTo(record)))
      },
      test("identity composed with any migration equals that migration") {
        val record = DynamicValue.Record(
          Vector(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("John")))
          )
        )
        val addAge   = Migration.addField("age", DynamicValue.Primitive(PrimitiveValue.Int(0)))
        val composed = Migration.identity[Any] ++ addAge

        val expected = DynamicValue.Record(
          Vector(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("John"))),
            ("age", DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )

        assert(composed(record))(isRight(equalTo(expected)))
      }
    ),
    suite("Associativity Law")(
      test("(m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)") {
        val record = DynamicValue.Record(
          Vector(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("John")))
          )
        )

        val m1 = Migration.addField("age", DynamicValue.Primitive(PrimitiveValue.Int(25)))
        val m2 = Migration.addField("city", DynamicValue.Primitive(PrimitiveValue.String("NYC")))
        val m3 = Migration.renameField("name", "fullName")

        val leftAssoc  = (m1 ++ m2) ++ m3
        val rightAssoc = m1 ++ (m2 ++ m3)

        val left  = leftAssoc(record)
        val right = rightAssoc(record)

        assert(left)(isRight) && assert(right)(isRight) && assert(left)(equalTo(right))
      }
    ),
    suite("Structural Reverse")(
      test("m.reverse.reverse has equivalent behavior to m for rename") {
        val rename        = Migration.renameField("name", "fullName")
        val doubleReverse = rename.reverse.reverse

        val record = DynamicValue.Record(
          Vector(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("John")))
          )
        )

        assert(rename(record))(equalTo(doubleReverse(record)))
      },
      test("rename and its reverse are inverse operations") {
        val record = DynamicValue.Record(
          Vector(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("John")))
          )
        )

        val rename   = Migration.renameField("name", "fullName")
        val composed = rename ++ rename.reverse

        // Should get back the original structure
        assert(composed(record))(isRight(equalTo(record)))
      }
    ),
    suite("AddField")(
      test("adds a field to a record") {
        val record = DynamicValue.Record(
          Vector(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("John")))
          )
        )
        val migration = Migration.addField("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))

        val expected = DynamicValue.Record(
          Vector(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("John"))),
            ("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
          )
        )

        assert(migration(record))(isRight(equalTo(expected)))
      },
      test("fails if field already exists") {
        val record = DynamicValue.Record(
          Vector(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("John")))
          )
        )
        val migration = Migration.addField("name", DynamicValue.Primitive(PrimitiveValue.String("Jane")))

        assert(migration(record))(isLeft)
      },
      test("fails if value is not a record") {
        val value     = DynamicValue.Primitive(PrimitiveValue.String("test"))
        val migration = Migration.addField("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))

        assert(migration(value))(isLeft)
      }
    ),
    suite("RemoveField")(
      test("removes a field from a record") {
        val record = DynamicValue.Record(
          Vector(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("John"))),
            ("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
          )
        )
        val migration = Migration.removeField("age")

        val expected = DynamicValue.Record(
          Vector(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("John")))
          )
        )

        assert(migration(record))(isRight(equalTo(expected)))
      },
      test("fails if field doesn't exist") {
        val record = DynamicValue.Record(
          Vector(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("John")))
          )
        )
        val migration = Migration.removeField("age")

        assert(migration(record))(isLeft)
      }
    ),
    suite("RenameField")(
      test("renames a field in a record") {
        val record = DynamicValue.Record(
          Vector(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("John")))
          )
        )
        val migration = Migration.renameField("name", "fullName")

        val expected = DynamicValue.Record(
          Vector(
            ("fullName", DynamicValue.Primitive(PrimitiveValue.String("John")))
          )
        )

        assert(migration(record))(isRight(equalTo(expected)))
      },
      test("fails if source field doesn't exist") {
        val record = DynamicValue.Record(
          Vector(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("John")))
          )
        )
        val migration = Migration.renameField("age", "years")

        assert(migration(record))(isLeft)
      },
      test("fails if target field already exists") {
        val record = DynamicValue.Record(
          Vector(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("John"))),
            ("fullName", DynamicValue.Primitive(PrimitiveValue.String("Jane")))
          )
        )
        val migration = Migration.renameField("name", "fullName")

        assert(migration(record))(isLeft)
      }
    ),
    suite("Composition")(
      test("composes multiple migrations") {
        val record = DynamicValue.Record(
          Vector(
            ("firstName", DynamicValue.Primitive(PrimitiveValue.String("John")))
          )
        )

        val migration =
          Migration.renameField("firstName", "name") ++
            Migration.addField("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))

        val expected = DynamicValue.Record(
          Vector(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("John"))),
            ("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
          )
        )

        assert(migration(record))(isRight(equalTo(expected)))
      }
    ),
    suite("Enum/Variant Migrations")(
      test("rename case works on matching variant") {
        val variant = DynamicValue.Variant(
          "PayPal",
          DynamicValue.Record(
            Vector(
              ("email", DynamicValue.Primitive(PrimitiveValue.String("test@example.com")))
            )
          )
        )
        val migration = Migration.RenameCase("PayPal", "PayPalPayment")

        val expected = DynamicValue.Variant(
          "PayPalPayment",
          DynamicValue.Record(
            Vector(
              ("email", DynamicValue.Primitive(PrimitiveValue.String("test@example.com")))
            )
          )
        )

        assert(migration(variant))(isRight(equalTo(expected)))
      },
      test("rename case leaves non-matching variants unchanged") {
        val variant = DynamicValue.Variant(
          "CreditCard",
          DynamicValue.Record(
            Vector(
              ("number", DynamicValue.Primitive(PrimitiveValue.Long(1234567890123456L)))
            )
          )
        )
        val migration = Migration.RenameCase("PayPal", "PayPalPayment")

        assert(migration(variant))(isRight(equalTo(variant)))
      }
    ),
    suite("MigrationBuilder")(
      test("builds a migration from the builder DSL") {
        val record = DynamicValue.Record(
          Vector(
            ("firstName", DynamicValue.Primitive(PrimitiveValue.String("John")))
          )
        )

        val migration = Migration
          .newBuilder[Any, Any]
          .renameField("firstName", "name")
          .addField("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
          .addField("city", DynamicValue.Primitive(PrimitiveValue.String("NYC")))
          .build

        val expected = DynamicValue.Record(
          Vector(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("John"))),
            ("age", DynamicValue.Primitive(PrimitiveValue.Int(30))),
            ("city", DynamicValue.Primitive(PrimitiveValue.String("NYC")))
          )
        )

        assert(migration(record))(isRight(equalTo(expected)))
      },
      test("empty builder returns identity") {
        val record = DynamicValue.Record(
          Vector(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("John")))
          )
        )

        val migration = Migration.newBuilder[Any, Any].build

        assert(migration(record))(isRight(equalTo(record)))
      }
    ),
    suite("Typed DSL (Macros)")(
      test("extracts field names from selectors") {
        @nowarn("unused")
        case class PersonV0(firstName: String)
        @nowarn("unused")
        case class PersonV1(name: String, age: Int)

        @nowarn("unused")
        implicit val schemaV0: Schema[PersonV0] = Schema.derived
        @nowarn("unused")
        implicit val schemaV1: Schema[PersonV1] = Schema.derived

        val migration = Migration
          .newBuilder[PersonV0, PersonV1]
          .renameField(_.firstName, _.name)
          .addField(_.age, 30)
          .build

        val record = DynamicValue.Record(
          Vector(
            ("firstName", DynamicValue.Primitive(PrimitiveValue.String("John")))
          )
        )

        val expected = DynamicValue.Record(
          Vector(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("John"))),
            ("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
          )
        )

        assert(migration(record))(isRight(equalTo(expected)))
      },
      test("automatic derivation between versions") {
        @nowarn("unused")
        case class PersonV0(name: String)
        @nowarn("unused")
        case class PersonV1(name: String, age: Int = 18)

        @nowarn("unused")
        implicit val schemaV0: Schema[PersonV0] = Schema.derived
        @nowarn("unused")
        implicit val schemaV1: Schema[PersonV1] = Schema.derived

        val migration = Migration.derived[PersonV0, PersonV1]

        val record = DynamicValue.Record(
          Vector(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("John")))
          )
        )

        val expected = DynamicValue.Record(
          Vector(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("John"))),
            ("age", DynamicValue.Primitive(PrimitiveValue.Int(18)))
          )
        )

        assert(migration(record))(isRight(equalTo(expected)))
      }
    )
  )
}
