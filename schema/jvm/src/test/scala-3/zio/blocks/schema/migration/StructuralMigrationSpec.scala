package zio.blocks.schema.migration

import scala.language.reflectiveCalls
import zio.test.*
import zio.blocks.schema.*

/**
 * Tests for migrating from PURE STRUCTURAL TYPES to case classes.
 *
 * This demonstrates the killer feature: migrating from old data formats
 * (represented as structural types) to current case classes WITHOUT keeping the
 * old case class definitions around.
 *
 * Use case: You have serialized data from version 1 of your app, but the case
 * class no longer exists. Represent the old schema as a structural type and
 * migrate to your current case class.
 *
 * NOTE: We use Migration.fromActions with string-based field paths because the
 * MigrationBuilder's selector syntax (_.fieldName) doesn't work with structural
 * types (it uses macros that can't introspect selectDynamic calls).
 */
object StructuralMigrationSpec extends ZIOSpecDefault {

  private def literal(dv: DynamicValue): SchemaExpr[Any, Any] =
    SchemaExpr.Literal[Any, Any](dv)

  def spec: Spec[TestEnvironment, Any] = suite("StructuralMigrationSpec")(
    suite("Structural → Case Class (PRIMARY USE CASE: migrating from old versions)")(
      test("pure structural to case class with addField") {
        type PersonV0 = { def name: String }
        def makePersonV0(n: String): PersonV0 = new { def name: String = n }
        given Schema[PersonV0]                = Schema.derived[PersonV0]

        case class PersonV2(name: String, age: Int)
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration.fromActions[PersonV0, PersonV2](
          MigrationAction.AddField(
            DynamicOptic.root.field("age"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )

        val input: PersonV0 = makePersonV0("Alice")
        val result          = migration(input)

        assertTrue(result == Right(PersonV2("Alice", 0)))
      },
      test("pure structural to case class with dropField") {
        type PersonV0 = { def name: String; def age: Int }
        def makePersonV0(n: String, a: Int): PersonV0 = new {
          def name: String = n
          def age: Int     = a
        }
        given Schema[PersonV0] = Schema.derived[PersonV0]

        case class PersonV2(name: String)
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration.fromActions[PersonV0, PersonV2](
          MigrationAction.DropField(
            DynamicOptic.root.field("age"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )

        val input: PersonV0 = makePersonV0("Bob", 30)
        val result          = migration(input)

        assertTrue(result == Right(PersonV2("Bob")))
      },
      test("pure structural to case class with renameField") {
        type PersonV0 = { def firstName: String }
        def makePersonV0(fn: String): PersonV0 = new { def firstName: String = fn }
        given Schema[PersonV0]                 = Schema.derived[PersonV0]

        case class PersonV2(fullName: String)
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration.fromActions[PersonV0, PersonV2](
          MigrationAction.Rename(
            DynamicOptic.root.field("firstName"),
            "fullName"
          )
        )

        val input: PersonV0 = makePersonV0("Charlie")
        val result          = migration(input)

        assertTrue(result == Right(PersonV2("Charlie")))
      },
      test("pure structural to case class with transformField") {
        type PersonV0 = { def age: Int }
        def makePersonV0(a: Int): PersonV0 = new { def age: Int = a }
        given Schema[PersonV0]             = Schema.derived[PersonV0]

        case class PersonV2(age: Long)
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration.fromActions[PersonV0, PersonV2](
          MigrationAction.TransformValue(
            DynamicOptic.root.field("age"),
            literal(DynamicValue.Primitive(PrimitiveValue.Long(30L)))
          )
        )

        val input: PersonV0 = makePersonV0(30)
        val result          = migration(input)

        assertTrue(result == Right(PersonV2(30L)))
      },
      test("complex pure structural to case class with multiple operations") {
        type PersonV0 = { def firstName: String; def age: Int; def city: String }
        def makePersonV0(fn: String, a: Int, c: String): PersonV0 = new {
          def firstName: String = fn
          def age: Int          = a
          def city: String      = c
        }
        given Schema[PersonV0] = Schema.derived[PersonV0]

        case class PersonV2(fullName: String, age: Long, country: String)
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration.fromActions[PersonV0, PersonV2](
          MigrationAction.Rename(DynamicOptic.root.field("firstName"), "fullName"),
          MigrationAction.TransformValue(
            DynamicOptic.root.field("age"),
            literal(DynamicValue.Primitive(PrimitiveValue.Long(25L)))
          ),
          MigrationAction.DropField(
            DynamicOptic.root.field("city"),
            literal(DynamicValue.Primitive(PrimitiveValue.String("Unknown")))
          ),
          MigrationAction.AddField(
            DynamicOptic.root.field("country"),
            literal(DynamicValue.Primitive(PrimitiveValue.String("USA")))
          )
        )

        val input: PersonV0 = makePersonV0("David", 25, "NYC")
        val result          = migration(input)

        assertTrue(result == Right(PersonV2("David", 25L, "USA")))
      }
    ),
    suite("Case Class → Structural (reverse migration for validation)")(
      test("case class to structural with addField") {
        case class PersonV1(name: String)
        given Schema[PersonV1] = Schema.derived[PersonV1]

        type PersonV2 = { def name: String; def age: Int }
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration.fromActions[PersonV1, PersonV2](
          MigrationAction.AddField(
            DynamicOptic.root.field("age"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )

        val input  = PersonV1("Emma")
        val result = migration(input)

        assertTrue(result.isRight)
      },
      test("case class to structural with dropField") {
        case class PersonV1(name: String, age: Int)
        given Schema[PersonV1] = Schema.derived[PersonV1]

        type PersonV2 = { def name: String }
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration.fromActions[PersonV1, PersonV2](
          MigrationAction.DropField(
            DynamicOptic.root.field("age"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )

        val input  = PersonV1("Frank", 40)
        val result = migration(input)

        assertTrue(result.isRight)
      },
      test("case class to structural with renameField") {
        case class PersonV1(firstName: String)
        given Schema[PersonV1] = Schema.derived[PersonV1]

        type PersonV2 = { def fullName: String }
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration.fromActions[PersonV1, PersonV2](
          MigrationAction.Rename(
            DynamicOptic.root.field("firstName"),
            "fullName"
          )
        )

        val input  = PersonV1("Grace")
        val result = migration(input)

        assertTrue(result.isRight)
      },
      test("case class to structural with transformField") {
        case class PersonV1(age: Int)
        given Schema[PersonV1] = Schema.derived[PersonV1]

        type PersonV2 = { def age: Long }
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration.fromActions[PersonV1, PersonV2](
          MigrationAction.TransformValue(
            DynamicOptic.root.field("age"),
            literal(DynamicValue.Primitive(PrimitiveValue.Long(35L)))
          )
        )

        val input  = PersonV1(35)
        val result = migration(input)

        assertTrue(result.isRight)
      }
    ),
    suite("Structural → Structural (version-to-version migration without runtime types)")(
      test("pure structural to pure structural with addField") {
        type PersonV0 = { def name: String }
        def makePersonV0(n: String): PersonV0 = new { def name: String = n }
        given Schema[PersonV0]                = Schema.derived[PersonV0]

        type PersonV2 = { def name: String; def age: Int }
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration.fromActions[PersonV0, PersonV2](
          MigrationAction.AddField(
            DynamicOptic.root.field("age"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )

        val input: PersonV0 = makePersonV0("Helen")
        val result          = migration(input)

        assertTrue(result.isRight)
      },
      test("pure structural to pure structural with dropField") {
        type PersonV0 = { def name: String; def age: Int }
        def makePersonV0(n: String, a: Int): PersonV0 = new {
          def name: String = n
          def age: Int     = a
        }
        given Schema[PersonV0] = Schema.derived[PersonV0]

        type PersonV2 = { def name: String }
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration.fromActions[PersonV0, PersonV2](
          MigrationAction.DropField(
            DynamicOptic.root.field("age"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )

        val input: PersonV0 = makePersonV0("Ivan", 45)
        val result          = migration(input)

        assertTrue(result.isRight)
      },
      test("pure structural to pure structural with renameField") {
        type PersonV0 = { def firstName: String }
        def makePersonV0(fn: String): PersonV0 = new { def firstName: String = fn }
        given Schema[PersonV0]                 = Schema.derived[PersonV0]

        type PersonV2 = { def fullName: String }
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration.fromActions[PersonV0, PersonV2](
          MigrationAction.Rename(
            DynamicOptic.root.field("firstName"),
            "fullName"
          )
        )

        val input: PersonV0 = makePersonV0("Julia")
        val result          = migration(input)

        assertTrue(result.isRight)
      },
      test("pure structural to pure structural with transformField") {
        type PersonV0 = { def age: Int }
        def makePersonV0(a: Int): PersonV0 = new { def age: Int = a }
        given Schema[PersonV0]             = Schema.derived[PersonV0]

        type PersonV2 = { def age: Long }
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration.fromActions[PersonV0, PersonV2](
          MigrationAction.TransformValue(
            DynamicOptic.root.field("age"),
            literal(DynamicValue.Primitive(PrimitiveValue.Long(50L)))
          )
        )

        val input: PersonV0 = makePersonV0(50)
        val result          = migration(input)

        assertTrue(result.isRight)
      },
      test("complex pure structural to pure structural migration") {
        type PersonV0 = { def firstName: String; def lastName: String; def age: Int; def active: Boolean }
        def makePersonV0(fn: String, ln: String, a: Int, ac: Boolean): PersonV0 = new {
          def firstName: String = fn
          def lastName: String  = ln
          def age: Int          = a
          def active: Boolean   = ac
        }
        given Schema[PersonV0] = Schema.derived[PersonV0]

        type PersonV2 = { def fullName: String; def age: Long; def status: String }
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration.fromActions[PersonV0, PersonV2](
          MigrationAction.DropField(
            DynamicOptic.root.field("firstName"),
            literal(DynamicValue.Primitive(PrimitiveValue.String("")))
          ),
          MigrationAction.DropField(
            DynamicOptic.root.field("lastName"),
            literal(DynamicValue.Primitive(PrimitiveValue.String("")))
          ),
          MigrationAction.DropField(
            DynamicOptic.root.field("active"),
            literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
          ),
          MigrationAction.AddField(
            DynamicOptic.root.field("fullName"),
            literal(DynamicValue.Primitive(PrimitiveValue.String("Unknown")))
          ),
          MigrationAction.TransformValue(
            DynamicOptic.root.field("age"),
            literal(DynamicValue.Primitive(PrimitiveValue.Long(0L)))
          ),
          MigrationAction.AddField(
            DynamicOptic.root.field("status"),
            literal(DynamicValue.Primitive(PrimitiveValue.String("active")))
          )
        )

        val input: PersonV0 = makePersonV0("Kate", "Smith", 28, true)
        val result          = migration(input)

        assertTrue(result.isRight)
      }
    ),
    suite("Nested structural types")(
      test("pure structural with nested structural field") {
        type AddressV0 = { def street: String; def city: String }
        def makeAddressV0(s: String, c: String): AddressV0 = new {
          def street: String = s
          def city: String   = c
        }
        given Schema[AddressV0] = Schema.derived[AddressV0]

        type PersonV0 = { def name: String; def address: AddressV0 }
        def makePersonV0(n: String, addr: AddressV0): PersonV0 = new {
          def name: String       = n
          def address: AddressV0 = addr
        }
        given Schema[PersonV0] = Schema.derived[PersonV0]

        case class Address(street: String, city: String)
        case class PersonV2(name: String, address: Address, age: Int)
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration.fromActions[PersonV0, PersonV2](
          MigrationAction.AddField(
            DynamicOptic.root.field("age"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )

        val input: PersonV0 = makePersonV0("Leo", makeAddressV0("123 Main St", "NYC"))
        val result          = migration(input)

        assertTrue(result.isRight && migration.actions.size == 1)
      }
    ),
    suite("Edge cases")(
      test("empty structural type migration") {
        type EmptyV0 = {}
        def makeEmptyV0(): EmptyV0 = new {}
        given Schema[EmptyV0]      = Schema.derived[EmptyV0]

        case class PersonV2(name: String, age: Int)
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration.fromActions[EmptyV0, PersonV2](
          MigrationAction.AddField(
            DynamicOptic.root.field("name"),
            literal(DynamicValue.Primitive(PrimitiveValue.String("Unknown")))
          ),
          MigrationAction.AddField(
            DynamicOptic.root.field("age"),
            literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
          )
        )

        val input: EmptyV0 = makeEmptyV0()
        val result         = migration(input)

        assertTrue(result == Right(PersonV2("Unknown", 0)))
      },
      test("identity migration with pure structural type") {
        type PersonV0 = { def name: String; def age: Int }
        def makePersonV0(n: String, a: Int): PersonV0 = new {
          def name: String = n
          def age: Int     = a
        }
        given Schema[PersonV0] = Schema.derived[PersonV0]

        val migration = Migration.fromActions[PersonV0, PersonV0]()

        val input: PersonV0 = makePersonV0("Mia", 32)
        val result          = migration(input)

        assertTrue(result.isRight && migration.isEmpty)
      },
      test("pure structural migration preserves DynamicValue structure") {
        type PersonV0 = { def name: String }
        def makePersonV0(n: String): PersonV0 = new { def name: String = n }
        given schemaV0: Schema[PersonV0]      = Schema.derived[PersonV0]

        val input: PersonV0 = makePersonV0("Nora")
        val dynamicValue    = schemaV0.toDynamicValue(input)

        assertTrue(
          dynamicValue match {
            case DynamicValue.Record(_) => true
            case _                      => false
          }
        )
      }
    )
  )
}
