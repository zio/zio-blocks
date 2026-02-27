package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._

/**
 * Tests for `DynamicMigration` — the fully serializable untyped migration core.
 *
 * Validates algebraic laws (identity, associativity, composition), error
 * propagation, and structural reverse semantics.
 */
object DynamicMigrationSpec extends ZIOSpecDefault {

  private val sampleRecord = DynamicValue.Record(Chunk(
    ("name", DynamicValue.Primitive(PrimitiveValue.String("Alice"))),
    ("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
  ))

  def spec: Spec[TestEnvironment, Any] = suite("DynamicMigrationSpec")(
    identityLaws,
    associativityLaws,
    compositionLaws,
    reverseLaws,
    errorPropagation
  )

  private val identityLaws = suite("Identity Laws")(
    test("identity migration returns value unchanged") {
      val result = DynamicMigration.identity.migrate(sampleRecord)
      assertTrue(result == Right(sampleRecord))
    },
    test("identity migration has zero actions") {
      assertTrue(DynamicMigration.identity.isEmpty)
      assertTrue(DynamicMigration.identity.size == 0)
    },
    test("identity composed with migration equals that migration") {
      val m = DynamicMigration(MigrationAction.AddField(
        DynamicOptic.root, "email",
        DynamicValue.Primitive(PrimitiveValue.String("none"))
      ))
      val composed = DynamicMigration.identity ++ m
      val direct   = m.migrate(sampleRecord)
      val indirect = composed.migrate(sampleRecord)
      assertTrue(direct == indirect)
    },
    test("migration composed with identity equals that migration") {
      val m = DynamicMigration(MigrationAction.RenameField(DynamicOptic.root, "name", "fullName"))
      val composed = m ++ DynamicMigration.identity
      val direct   = m.migrate(sampleRecord)
      val indirect = composed.migrate(sampleRecord)
      assertTrue(direct == indirect)
    }
  )

  private val associativityLaws = suite("Associativity Laws")(
    test("(a ++ b) ++ c == a ++ (b ++ c)") {
      val a = DynamicMigration(MigrationAction.AddField(
        DynamicOptic.root, "email",
        DynamicValue.Primitive(PrimitiveValue.String("unknown"))
      ))
      val b = DynamicMigration(MigrationAction.RenameField(DynamicOptic.root, "name", "fullName"))
      val c = DynamicMigration(MigrationAction.DropField(DynamicOptic.root, "age", None))

      val leftAssoc  = (a ++ b) ++ c
      val rightAssoc = a ++ (b ++ c)

      val leftResult  = leftAssoc.migrate(sampleRecord)
      val rightResult = rightAssoc.migrate(sampleRecord)

      assertTrue(leftResult == rightResult)
    }
  )

  private val compositionLaws = suite("Composition Laws")(
    test("(a ++ b).migrate(v) == a.migrate(v).flatMap(b.migrate)") {
      val a = DynamicMigration(MigrationAction.AddField(
        DynamicOptic.root, "email",
        DynamicValue.Primitive(PrimitiveValue.String("test@test.com"))
      ))
      val b = DynamicMigration(MigrationAction.RenameField(DynamicOptic.root, "name", "fullName"))

      val composed   = (a ++ b).migrate(sampleRecord)
      val sequential = a.migrate(sampleRecord).flatMap(b.migrate)

      assertTrue(composed == sequential)
    }
  )

  private val reverseLaws = suite("Reverse Laws")(
    test("reversible migration roundtrips correctly") {
      val m = DynamicMigration(
        MigrationAction.AddField(
          DynamicOptic.root, "email",
          DynamicValue.Primitive(PrimitiveValue.String("default"))
        ),
        MigrationAction.RenameField(DynamicOptic.root, "name", "fullName")
      )

      val rev = m.reverse
      assertTrue(rev.isDefined)

      val migrated  = m.migrate(sampleRecord)
      assertTrue(migrated.isRight)

      val recovered = rev.get.migrate(migrated.toOption.get)
      assertTrue(recovered == Right(sampleRecord))
    },
    test("irreversible migration returns None for reverse") {
      val m = DynamicMigration(MigrationAction.DropField(DynamicOptic.root, "age", None))
      assertTrue(m.reverse.isEmpty)
    },
    test("identity reverse is identity") {
      val rev = DynamicMigration.identity.reverse
      assertTrue(rev.isDefined)
      assertTrue(rev.get.isEmpty)
    }
  )

  private val errorPropagation = suite("Error Propagation")(
    test("errors include path context") {
      val action = MigrationAction.RenameField(DynamicOptic.root, "nonexistent", "something")
      val result = action(sampleRecord)
      assertTrue(result.isLeft)
      assertTrue(result.swap.toOption.get.message.contains("nonexistent"))
    },
    test("first error stops migration") {
      val m = DynamicMigration(
        MigrationAction.RenameField(DynamicOptic.root, "nonexistent", "a"), // fails
        MigrationAction.RenameField(DynamicOptic.root, "name", "fullName") // would succeed
      )
      val result = m.migrate(sampleRecord)
      assertTrue(result.isLeft)
      // name should still be "name" since migration stopped at first error
    }
  )
}
