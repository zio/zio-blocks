package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object TypedMigrationBuilderScala2Spec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("TypedMigrationBuilder Scala 2")(
    suite("TypedMigrationBuilder compile-time validation")(
      test("addTyped tracks field at type level and buildTyped validates") {
        import TypedMigrationBuilderMacro._
        import TypeLevel._

        val migration = MigrationBuilder[PersonV1, PersonV2, Empty, Empty](
          implicitly[Schema[PersonV1]],
          implicitly[Schema[PersonV2]],
          MigrationStep.Record.empty,
          MigrationStep.Variant.empty
        )
          .addTyped(_.age, DynamicValue.int(25))
          .buildTyped

        val original = PersonV1("Alice")
        val result   = migration(original)

        assertTrue(result == Right(PersonV2("Alice", 25)))
      },
      test("dropTyped tracks field at type level") {
        import TypedMigrationBuilderMacro._
        import TypeLevel._

        val migration = MigrationBuilder[PersonV2, PersonV1, Empty, Empty](
          implicitly[Schema[PersonV2]],
          implicitly[Schema[PersonV1]],
          MigrationStep.Record.empty,
          MigrationStep.Variant.empty
        )
          .dropTyped(_.age, DynamicValue.int(0))
          .buildTyped

        val original = PersonV2("Alice", 30)
        val result   = migration(original)

        assertTrue(result == Right(PersonV1("Alice")))
      },
      test("renameTyped tracks both old and new fields at type level") {
        import TypedMigrationBuilderMacro._
        import TypeLevel._

        val migration = MigrationBuilder[PersonV1, PersonRenamed, Empty, Empty](
          implicitly[Schema[PersonV1]],
          implicitly[Schema[PersonRenamed]],
          MigrationStep.Record.empty,
          MigrationStep.Variant.empty
        )
          .renameTyped(_.name, _.fullName)
          .buildTyped

        val original = PersonV1("Alice")
        val result   = migration(original)

        assertTrue(result == Right(PersonRenamed("Alice")))
      },
      test("multiple typed operations chain correctly") {
        import TypedMigrationBuilderMacro._
        import TypeLevel._

        val migration = MigrationBuilder[PersonV1, PersonV3, Empty, Empty](
          implicitly[Schema[PersonV1]],
          implicitly[Schema[PersonV3]],
          MigrationStep.Record.empty,
          MigrationStep.Variant.empty
        )
          .addTyped(_.age, DynamicValue.int(30))
          .addTyped(_.active, DynamicValue.boolean(true))
          .buildTyped

        val original = PersonV1("Alice")
        val result   = migration(original)

        assertTrue(result == Right(PersonV3("Alice", 30, true)))
      },
      test("TypedMigrationBuilder.from fluent API works") {
        import TypedMigrationBuilderMacro._

        val migration = TypedMigrationBuilderMacro
          .from[PersonV1]
          .to[PersonV2]
          .addTyped(_.age, DynamicValue.int(42))
          .buildTyped

        val original = PersonV1("Bob")
        val result   = migration(original)

        assertTrue(result == Right(PersonV2("Bob", 42)))
      },
      test("complex migration with add, drop, and rename") {
        import TypedMigrationBuilderMacro._
        import TypeLevel._

        val migration = MigrationBuilder[PersonV2, PersonV4S2, Empty, Empty](
          implicitly[Schema[PersonV2]],
          implicitly[Schema[PersonV4S2]],
          MigrationStep.Record.empty,
          MigrationStep.Variant.empty
        )
          .dropTyped(_.age, DynamicValue.int(0))
          .addTyped(_.email, DynamicValue.string("default@example.com"))
          .buildTyped

        val original = PersonV2("Alice", 30)
        val result   = migration(original)

        assertTrue(result == Right(PersonV4S2("Alice", "default@example.com")))
      },
      test("nested field paths work with addTyped") {
        import TypedMigrationBuilderMacro._
        import TypeLevel._

        val migration = MigrationBuilder[PersonWithAddressS2, PersonWithAddressV2S2, Empty, Empty](
          implicitly[Schema[PersonWithAddressS2]],
          implicitly[Schema[PersonWithAddressV2S2]],
          MigrationStep.Record.empty,
          MigrationStep.Variant.empty
        )
          .addTyped(_.address.zip, DynamicValue.string("12345"))
          .buildTyped

        val original = PersonWithAddressS2("Alice", AddressSimpleS2("123 Main St", "NYC"))
        val result   = migration(original)

        assertTrue(result == Right(PersonWithAddressV2S2("Alice", AddressWithZipS2("123 Main St", "NYC", "12345"))))
      }
    )
  )
}

case class PersonV4S2(name: String, email: String)
object PersonV4S2 {
  implicit val schema: Schema[PersonV4S2] = Schema.derived
}

case class PersonWithAddressS2(name: String, address: AddressSimpleS2)
object PersonWithAddressS2 {
  implicit val schema: Schema[PersonWithAddressS2] = Schema.derived
}

case class AddressSimpleS2(street: String, city: String)
object AddressSimpleS2 {
  implicit val schema: Schema[AddressSimpleS2] = Schema.derived
}

case class PersonWithAddressV2S2(name: String, address: AddressWithZipS2)
object PersonWithAddressV2S2 {
  implicit val schema: Schema[PersonWithAddressV2S2] = Schema.derived
}

case class AddressWithZipS2(street: String, city: String, zip: String)
object AddressWithZipS2 {
  implicit val schema: Schema[AddressWithZipS2] = Schema.derived
}
