package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test.Assertion._
import zio.test.{Spec, TestEnvironment, assert}

object MigrationSpec extends SchemaBaseSpec {

  case class PersonV1(name: String, age: Int)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived
  }

  case class PersonV2(fullName: String, age: Int, country: String)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("MigrationSpec")(
    suite("Identity")(
      test("identity migration returns the input value unchanged") {
        val person = PersonV1("Alice", 30)
        val m      = Migration.identity[PersonV1]
        assert(m(person))(isRight(equalTo(person)))
      }
    ),
    suite("Typed Migration")(
      test("migrates PersonV1 to PersonV2 with rename and add field") {
        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .renameField(DynamicOptic.root, "name", "fullName")
          .addField(DynamicOptic.root, "country", DynamicValue.Primitive(PrimitiveValue.String("US")))
          .build

        val old = PersonV1("Alice", 30)
        assert(migration(old))(isRight(equalTo(PersonV2("Alice", 30, "US"))))
      },
      test("reverse migration from PersonV2 back to PersonV1") {
        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .renameField(DynamicOptic.root, "name", "fullName")
          .addField(DynamicOptic.root, "country", DynamicValue.Primitive(PrimitiveValue.String("US")))
          .build

        val v2       = PersonV2("Alice", 30, "US")
        val reversed = migration.reverse
        assert(reversed(v2))(isRight(equalTo(PersonV1("Alice", 30))))
      },
      test("full round-trip: apply then reverse") {
        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .renameField(DynamicOptic.root, "name", "fullName")
          .addField(DynamicOptic.root, "country", DynamicValue.Primitive(PrimitiveValue.String("US")))
          .build

        val original = PersonV1("Bob", 25)
        val result   = for {
          migrated <- migration(original)
          restored <- migration.reverse(migrated)
        } yield restored
        assert(result)(isRight(equalTo(original)))
      }
    ),
    suite("Composition")(
      test("composing two typed migrations works end-to-end") {
        val m1 = Migration
          .newBuilder[PersonV1, PersonV2]
          .renameField(DynamicOptic.root, "name", "fullName")
          .addField(DynamicOptic.root, "country", DynamicValue.Primitive(PrimitiveValue.String("US")))
          .build

        val m2 = m1.reverse

        val composed = m1 ++ m2
        val original = PersonV1("Bob", 25)

        assert(composed(original))(isRight(equalTo(original)))
      },
      test("andThen is alias for ++") {
        val m1 = Migration
          .newBuilder[PersonV1, PersonV2]
          .renameField(DynamicOptic.root, "name", "fullName")
          .addField(DynamicOptic.root, "country", DynamicValue.Primitive(PrimitiveValue.String("US")))
          .build
        val m2       = m1.reverse
        val original = PersonV1("Charlie", 35)

        val result1 = (m1 ++ m2)(original)
        val result2 = m1.andThen(m2)(original)

        assert(result1)(equalTo(result2))
      }
    ),
    suite("Error Handling")(
      test("migration error includes readable message") {
        val err = MigrationError.ActionFailed("AddField", DynamicOptic.root.field("address"), "Expected Record")
        assert(err.message.contains("AddField"))(equalTo(true)) &&
        assert(err.message.contains("address"))(equalTo(true))
      },
      test("multiple errors can be combined") {
        val e1       = MigrationError.PathNotFound(DynamicOptic.root.field("x"))
        val e2       = MigrationError.PathNotFound(DynamicOptic.root.field("y"))
        val combined = e1 ++ e2
        combined match {
          case MigrationError.Multiple(errors) =>
            assert(errors.length)(equalTo(2))
          case _ =>
            assert(false)(equalTo(true))
        }
      },
      test("TypeConversionFailed includes from/to types") {
        val err = MigrationError.TypeConversionFailed(DynamicOptic.root.field("x"), "Int", "String")
        assert(err.message.contains("Int"))(equalTo(true)) &&
        assert(err.message.contains("String"))(equalTo(true))
      }
    ),
    suite("MigrationBuilder")(
      test("builder accumulates actions in order") {
        val builder = Migration
          .newBuilder[PersonV1, PersonV2]
          .addField(DynamicOptic.root, "country", DynamicValue.Primitive(PrimitiveValue.String("US")))
          .renameField(DynamicOptic.root, "name", "fullName")

        assert(builder.actions.length)(equalTo(2)) &&
        assert(builder.actions.head.isInstanceOf[MigrationAction.AddField])(equalTo(true)) &&
        assert(builder.actions(1).isInstanceOf[MigrationAction.Rename])(equalTo(true))
      },
      test("buildPartial creates migration without validation") {
        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .renameField(DynamicOptic.root, "name", "fullName")
          .addField(DynamicOptic.root, "country", DynamicValue.Primitive(PrimitiveValue.String("US")))
          .buildPartial

        val old = PersonV1("Charlie", 40)
        assert(migration(old))(isRight(equalTo(PersonV2("Charlie", 40, "US"))))
      },
      test("builder supports all record operations") {
        val builder = Migration
          .newBuilder[PersonV1, PersonV2]
          .addField(DynamicOptic.root, "f1", DynamicValue.Null)
          .dropField(DynamicOptic.root, "f2")
          .renameField(DynamicOptic.root, "f3", "f4")
          .transformValue(DynamicOptic.root.field("f5"), DynamicValue.Null, DynamicValue.Null)
          .mandateField(DynamicOptic.root.field("f6"), DynamicValue.Null)
          .optionalizeField(DynamicOptic.root.field("f7"))
          .changeFieldType(DynamicOptic.root.field("f8"), DynamicValue.Null, DynamicValue.Null)

        assert(builder.actions.length)(equalTo(7))
      },
      test("builder supports enum operations") {
        val builder = Migration
          .newBuilder[PersonV1, PersonV2]
          .renameCase(DynamicOptic.root, "Old", "New")
          .transformCase(DynamicOptic.root, "Case1", Vector.empty)

        assert(builder.actions.length)(equalTo(2))
      },
      test("builder supports collection operations") {
        val builder = Migration
          .newBuilder[PersonV1, PersonV2]
          .transformElements(DynamicOptic.root.field("items"), Vector.empty)
          .transformKeys(DynamicOptic.root.field("map"), Vector.empty)
          .transformValues(DynamicOptic.root.field("map"), Vector.empty)

        assert(builder.actions.length)(equalTo(3))
      },
      test("builder supports join and split") {
        val builder = Migration
          .newBuilder[PersonV1, PersonV2]
          .joinFields(
            DynamicOptic.root.field("fullName"),
            Vector(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
            DynamicValue.Null
          )
          .splitField(
            DynamicOptic.root.field("fullName"),
            Vector(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
            DynamicValue.Null
          )

        assert(builder.actions.length)(equalTo(2)) &&
        assert(builder.actions.head.isInstanceOf[MigrationAction.Join])(equalTo(true)) &&
        assert(builder.actions(1).isInstanceOf[MigrationAction.Split])(equalTo(true))
      }
    )
  )
}
