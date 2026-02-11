package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test._

/**
 * Scala 2 tests for migrations with structural types. Structural types are
 * JVM-only (require reflection). These tests verify:
 *   - Structural type → case class migration
 *   - Structural type → structural type migration
 *   - Migration composition across structural types
 *   - Reverse migrations with structural types
 */
object StructuralMigrationSpec extends SchemaBaseSpec {

  // --- Target case classes (the "current" versions) ---
  case class PersonV2(fullName: String, age: Int)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived
  }

  case class PersonV3(fullName: String, age: Int, country: String)
  object PersonV3 {
    implicit val schema: Schema[PersonV3] = Schema.derived
  }

  // --- Structural types (representing "old" versions) ---
  type PersonV1Structural = { def firstName: String; def lastName: String }

  type PersonV2Structural = { def fullName: String; def age: Int }

  def spec: Spec[Any, Nothing] = suite("StructuralMigrationSpec")(
    suite("structural -> case class migration")(
      test("rename + addField migrates structural type to case class") {
        val sourceSchema = Schema.derived[PersonV1Structural]

        val migration = MigrationBuilder(sourceSchema, PersonV2.schema, Vector.empty)
          .withAction(
            MigrationAction.Rename(
              at = DynamicOptic.root.field("firstName"),
              to = "fullName"
            )
          )
          .withAction(
            MigrationAction.DropField(
              at = DynamicOptic.root.field("lastName"),
              defaultForReverse = ""
            )
          )
          .withAction(
            MigrationAction.AddField(
              at = DynamicOptic.root.field("age"),
              default = 0
            )
          )
          .buildPartial

        val oldPerson: PersonV1Structural = new {
          def firstName: String = "Alice"
          def lastName: String  = "Smith"
        }

        val result = migration.apply(oldPerson)

        assertTrue(
          result == Right(PersonV2("Alice", 0))
        )
      },
      test("join fields migrates structural type to case class") {
        val sourceSchema = Schema.derived[PersonV1Structural]

        val migration = MigrationBuilder(sourceSchema, PersonV2.schema, Vector.empty)
          .withAction(
            MigrationAction.Join(
              at = DynamicOptic.root.field("fullName"),
              sourcePaths = Vector(
                DynamicOptic.root.field("firstName"),
                DynamicOptic.root.field("lastName")
              ),
              combiner = DynamicSchemaExpr.StringConcat(
                DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field0")),
                DynamicSchemaExpr.StringConcat(
                  " ",
                  DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field1"))
                )
              )
            )
          )
          .withAction(
            MigrationAction.AddField(
              at = DynamicOptic.root.field("age"),
              default = 25
            )
          )
          .buildPartial

        val oldPerson: PersonV1Structural = new {
          def firstName: String = "John"
          def lastName: String  = "Doe"
        }

        val result = migration.apply(oldPerson)

        assertTrue(
          result == Right(PersonV2("John Doe", 25))
        )
      }
    ),
    suite("structural -> structural migration")(
      test("rename + addField between structural types via DynamicMigration") {
        val sourceSchema = Schema.derived[PersonV1Structural]

        val dynMigration = DynamicMigration(
          Vector(
            MigrationAction.Rename(
              at = DynamicOptic.root.field("firstName"),
              to = "fullName"
            ),
            MigrationAction.DropField(
              at = DynamicOptic.root.field("lastName"),
              defaultForReverse = ""
            ),
            MigrationAction.AddField(
              at = DynamicOptic.root.field("age"),
              default = 42
            )
          )
        )

        val oldPerson: PersonV1Structural = new {
          def firstName: String = "Bob"
          def lastName: String  = "Jones"
        }

        // Convert structural source to DynamicValue, apply migration, verify result
        val sourceDv = sourceSchema.toDynamicValue(oldPerson)
        val result   = dynMigration.apply(sourceDv)

        assertTrue(
          result == Right(
            DynamicValue.Record(
              Chunk(
                "fullName" -> DynamicValue.Primitive(PrimitiveValue.String("Bob")),
                "age"      -> DynamicValue.Primitive(PrimitiveValue.Int(42))
              )
            )
          )
        )
      }
    ),
    suite("case class -> structural migration (via .structural)")(
      test("case class schema converted to structural preserves DynamicValue") {
        val nominalSchema    = Schema.derived[PersonV2]
        val structuralSchema = nominalSchema.structural

        val person       = PersonV2("Alice", 30)
        val dynamicValue = nominalSchema.toDynamicValue(person)

        // Structural schema should be able to reconstruct from DynamicValue
        val structuralPerson = structuralSchema.fromDynamicValue(dynamicValue)

        // And an identity DynamicMigration preserves the DynamicValue
        val migrated = DynamicMigration.identity.apply(dynamicValue)

        assertTrue(
          structuralPerson.isRight,
          migrated == Right(dynamicValue)
        )
      }
    ),
    suite("composition with structural types")(
      test("structural -> case class -> case class composition") {
        val sourceSchema = Schema.derived[PersonV1Structural]

        // V1 (structural) -> V2 (case class)
        val migration1 = MigrationBuilder(sourceSchema, PersonV2.schema, Vector.empty)
          .withAction(
            MigrationAction.Rename(
              at = DynamicOptic.root.field("firstName"),
              to = "fullName"
            )
          )
          .withAction(
            MigrationAction.DropField(
              at = DynamicOptic.root.field("lastName"),
              defaultForReverse = ""
            )
          )
          .withAction(
            MigrationAction.AddField(
              at = DynamicOptic.root.field("age"),
              default = 30
            )
          )
          .buildPartial

        // V2 -> V3: Add country
        val migration2 = MigrationBuilder(PersonV2.schema, PersonV3.schema, Vector.empty)
          .withAction(
            MigrationAction.AddField(
              at = DynamicOptic.root.field("country"),
              default = "US"
            )
          )
          .buildPartial

        val composed = migration1 ++ migration2

        val oldPerson: PersonV1Structural = new {
          def firstName: String = "Jane"
          def lastName: String  = "Doe"
        }

        val result = composed.apply(oldPerson)

        assertTrue(
          result == Right(PersonV3("Jane", 30, "US"))
        )
      },
      test("structural -> structural -> case class composition") {
        type AddressV1 = { def street: String; def city: String }
        type AddressV2 = { def street: String; def city: String; def zip: String }

        case class Address(street: String, city: String, zip: String, country: String)
        object Address {
          implicit val schema: Schema[Address] = Schema.derived
        }

        val v1Schema = Schema.derived[AddressV1]
        val v2Schema = Schema.derived[AddressV2]

        // V1 -> V2: Add zip
        val migration1 = MigrationBuilder(v1Schema, v2Schema, Vector.empty)
          .withAction(
            MigrationAction.AddField(
              at = DynamicOptic.root.field("zip"),
              default = "00000"
            )
          )
          .buildPartial

        // V2 -> Address: Add country
        val migration2 = MigrationBuilder(v2Schema, Address.schema, Vector.empty)
          .withAction(
            MigrationAction.AddField(
              at = DynamicOptic.root.field("country"),
              default = "US"
            )
          )
          .buildPartial

        val composed = migration1 ++ migration2

        val oldAddress: AddressV1 = new {
          def street: String = "123 Main St"
          def city: String   = "Springfield"
        }

        val result = composed.apply(oldAddress)

        assertTrue(
          result == Right(Address("123 Main St", "Springfield", "00000", "US"))
        )
      }
    ),
    suite("reverse migration with structural types")(
      test("reverse of case class -> structural via DynamicMigration") {
        val dynMigration = DynamicMigration(
          Vector(
            MigrationAction.AddField(
              at = DynamicOptic.root.field("country"),
              default = "US"
            )
          )
        )

        val reversed = dynMigration.reverse

        val personV3Dv = PersonV3.schema.toDynamicValue(PersonV3("Alice", 30, "US"))
        val result     = reversed.apply(personV3Dv)

        assertTrue(
          result == Right(
            DynamicValue.Record(
              Chunk(
                "fullName" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
                "age"      -> DynamicValue.Primitive(PrimitiveValue.Int(30))
              )
            )
          )
        )
      },
      test("reverse.reverse equals original structurally") {
        val sourceSchema = Schema.derived[PersonV1Structural]

        val migration = MigrationBuilder(sourceSchema, PersonV2.schema, Vector.empty)
          .withAction(
            MigrationAction.Rename(
              at = DynamicOptic.root.field("firstName"),
              to = "fullName"
            )
          )
          .withAction(
            MigrationAction.DropField(
              at = DynamicOptic.root.field("lastName"),
              defaultForReverse = ""
            )
          )
          .withAction(
            MigrationAction.AddField(
              at = DynamicOptic.root.field("age"),
              default = 0
            )
          )
          .buildPartial

        val doubleReversed = migration.reverse.reverse

        assertTrue(doubleReversed.dynamicMigration == migration.dynamicMigration)
      }
    ),
    suite("identity migration with structural types")(
      test("identity migration on structural type preserves DynamicValue") {
        val schema = Schema.derived[PersonV1Structural]

        val person: PersonV1Structural = new {
          def firstName: String = "Test"
          def lastName: String  = "User"
        }

        val original = schema.toDynamicValue(person)
        val result   = DynamicMigration.identity.apply(original)

        assertTrue(
          result == Right(original)
        )
      }
    )
  )
}
