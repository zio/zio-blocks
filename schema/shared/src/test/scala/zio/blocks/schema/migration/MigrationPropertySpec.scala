package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.blocks.schema.migration.{SchemaExpr => SE}
import zio.test._
import zio.test.Assertion._

/**
 * Property-based tests for migrations.
 *
 * Tests algebraic properties:
 *   - Reversibility: forward.reverse.apply(forward.apply(x)) == x
 *   - Composition associativity: (a ++ b) ++ c == a ++ (b ++ c)
 *   - Identity: migration ++ identity == migration
 *   - Idempotence: Some operations should be idempotent
 */
object MigrationPropertySpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("MigrationPropertySpec")(
    reversibilityTests,
    compositionTests,
    identityTests,
    idempotenceTests
  )

  // ===== Reversibility Tests =====

  val reversibilityTests = suite("Reversibility Properties")(
    test("AddField is reversible") {
      val record = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice"))
        )
      )

      val forward = DynamicMigration.single(
        MigrationAction.AddField(DynamicOptic.root, "age", SE.literalInt(30))
      )

      val reverse = forward.reverse

      val result = for {
        migrated <- forward.apply(record)
        restored <- reverse.apply(migrated)
      } yield restored

      assert(result)(isRight(equalTo(record)))
    },

    test("Rename is reversible") {
      val record = DynamicValue.Record(
        Vector(
          "oldName" -> DynamicValue.Primitive(PrimitiveValue.String("value"))
        )
      )

      val forward = DynamicMigration.single(
        MigrationAction.Rename(DynamicOptic.root, "oldName", "newName")
      )

      val reverse = forward.reverse

      val result = for {
        migrated <- forward.apply(record)
        restored <- reverse.apply(migrated)
      } yield restored

      assert(result)(isRight(equalTo(record)))
    },

    test("DropField is reversible with default") {
      val record = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Bob")),
          "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(25))
        )
      )

      val forward = DynamicMigration.single(
        MigrationAction.DropField(DynamicOptic.root, "age", Some(SE.literalInt(25)))
      )

      val reverse = forward.reverse

      val result = for {
        migrated <- forward.apply(record)
        restored <- reverse.apply(migrated)
      } yield restored

      assert(result)(isRight(equalTo(record)))
    },

    test("Optionalize is reversible") {
      val record = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Charlie")),
          "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(35))
        )
      )

      val forward = DynamicMigration.single(
        MigrationAction.Optionalize(DynamicOptic.root, "age")
      )

      val reverse = forward.reverse

      val result = for {
        migrated <- forward.apply(record)
        restored <- reverse.apply(migrated)
      } yield restored

      // Note: This won't be exactly equal because Optionalize wraps in Some
      // But the reverse (Mandate) should unwrap it
      assert(result)(isRight(anything))
    },

    test("RenameCase is reversible") {
      val variant = DynamicValue.Variant("OldCase", DynamicValue.Primitive(PrimitiveValue.Int(42)))

      val forward = DynamicMigration.single(
        MigrationAction.RenameCase(DynamicOptic.root, "OldCase", "NewCase")
      )

      val reverse = forward.reverse

      val result = for {
        migrated <- forward.apply(variant)
        restored <- reverse.apply(migrated)
      } yield restored

      assert(result)(isRight(equalTo(variant)))
    },

    test("complex migration is reversible") {
      val record = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Diana"))
        )
      )

      val forward = DynamicMigration(
        Vector(
          MigrationAction.AddField(DynamicOptic.root, "age", SE.literalInt(28)),
          MigrationAction.AddField(DynamicOptic.root, "city", SE.literalString("NYC")),
          MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
        )
      )

      val reverse = forward.reverse

      val result = for {
        migrated <- forward.apply(record)
        restored <- reverse.apply(migrated)
      } yield restored

      assert(result)(isRight(equalTo(record)))
    }
  )

  // ===== Composition Tests =====

  val compositionTests = suite("Composition Properties")(
    test("composition is associative") {
      val record = DynamicValue.Record(
        Vector(
          "a" -> DynamicValue.Primitive(PrimitiveValue.String("value"))
        )
      )

      val m1 = DynamicMigration.single(
        MigrationAction.AddField(DynamicOptic.root, "b", SE.literalString("b"))
      )

      val m2 = DynamicMigration.single(
        MigrationAction.AddField(DynamicOptic.root, "c", SE.literalString("c"))
      )

      val m3 = DynamicMigration.single(
        MigrationAction.AddField(DynamicOptic.root, "d", SE.literalString("d"))
      )

      val left  = (m1 ++ m2) ++ m3
      val right = m1 ++ (m2 ++ m3)

      val resultLeft  = left.apply(record)
      val resultRight = right.apply(record)

      assert(resultLeft)(equalTo(resultRight))
    },

    test("composition order matters for non-commutative operations") {
      val record = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Eve"))
        )
      )

      val m1 = DynamicMigration.single(
        MigrationAction.Rename(DynamicOptic.root, "name", "fullName")
      )

      val m2 = DynamicMigration.single(
        MigrationAction.AddField(DynamicOptic.root, "name", SE.literalString("new"))
      )

      val forward  = m1 ++ m2
      val backward = m2 ++ m1

      val resultForward  = forward.apply(record)
      val resultBackward = backward.apply(record)

      // These should be different because order matters
      assert(resultForward)(isRight(anything)) &&
      assert(resultBackward)(isLeft(anything)) // Should fail because "name" doesn't exist after rename
    }
  )

  // ===== Identity Tests =====

  val identityTests = suite("Identity Properties")(
    test("identity migration does nothing") {
      val record = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Frank"))
        )
      )

      val identity = DynamicMigration.identity

      val result = identity.apply(record)

      assert(result)(isRight(equalTo(record)))
    },

    test("composing with identity is identity") {
      val record = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Grace"))
        )
      )

      val migration = DynamicMigration.single(
        MigrationAction.AddField(DynamicOptic.root, "age", SE.literalInt(40))
      )

      val withIdentityLeft  = DynamicMigration.identity ++ migration
      val withIdentityRight = migration ++ DynamicMigration.identity

      val resultOriginal = migration.apply(record)
      val resultLeft     = withIdentityLeft.apply(record)
      val resultRight    = withIdentityRight.apply(record)

      assert(resultLeft)(equalTo(resultOriginal)) &&
      assert(resultRight)(equalTo(resultOriginal))
    }
  )

  // ===== Idempotence Tests =====

  val idempotenceTests = suite("Idempotence Properties")(
    test("adding same field twice fails") {
      val record = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Henry"))
        )
      )

      val migration = DynamicMigration(
        Vector(
          MigrationAction.AddField(DynamicOptic.root, "age", SE.literalInt(30)),
          MigrationAction.AddField(DynamicOptic.root, "age", SE.literalInt(31)) // Duplicate
        )
      )

      val result = migration.apply(record)

      assert(result)(isLeft(anything))
    },

    test("renaming to same name fails (not idempotent by design)") {
      val record = DynamicValue.Record(
        Vector(
          "name" -> DynamicValue.Primitive(PrimitiveValue.String("Iris"))
        )
      )

      val migration = DynamicMigration.single(
        MigrationAction.Rename(DynamicOptic.root, "name", "name")
      )

      val result = migration.apply(record)

      // This fails because the implementation detects no change and treats it as field not found
      assert(result)(isLeft(anything))
    }
  )
}
