package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._
import zio.blocks.schema.migration.MigrationAction._

// =================================================================================
// 1. GLOBAL DOMAIN MODELS (Scala 3 Style)
// =================================================================================

object Scala3Models {

  // --- Scenario 1: Standard Trait ---
  trait LegacyUserTrait {
    def username: String
  }

  case class ModernUser(username: String)

  // --- Scenario 2: Intersection Types (Scala 3 Exclusive Feature) ---
  // We want to migrate from an object that is BOTH PartA AND PartB
  trait PartA { def id: Int             }
  trait PartB { def description: String }

  // Scala 3 Intersection Type (equivalent to 'PartA with PartB' in Scala 2, but cleaner)
  type CombinedEntity = PartA & PartB

  case class TargetEntity(id: Int, description: String)
}

// =================================================================================
// 2. TEST SUITE
// =================================================================================

object StructuralAndMacroSpecScala3 extends ZIOSpecDefault {

  import Scala3Models._

  // ==========================================
  // Global Givens (Scala 3 replacements for Implicits)
  // ==========================================

  // We use 'given' to provide schemas.
  // We mock them because we are testing the Builder's compilation logic, not ZIO Schema derivation.

  given Schema[LegacyUserTrait] = null.asInstanceOf[Schema[LegacyUserTrait]]
  given Schema[ModernUser]      = null.asInstanceOf[Schema[ModernUser]]

  given Schema[CombinedEntity] = null.asInstanceOf[Schema[CombinedEntity]]
  given Schema[TargetEntity]   = null.asInstanceOf[Schema[TargetEntity]]

  // ==========================================
  // Test Suite
  // ==========================================

  def spec = suite("Scala 3 Exclusive: Intersection Types & Modern Syntax")(
    suite("Migration Builder with Scala 3 Features")(
      // -----------------------------------------------------------------------
      // TEST 1: Trait to Class Migration using 'given'
      // -----------------------------------------------------------------------
      test("1. [Scala 3] Trait Migration works with 'given' instances") {

        // This proves that the Builder picks up 'given' schemas automatically
        val builder = MigrationBuilder
          .make[LegacyUserTrait, ModernUser]
          .renameField(src => src.username, dst => dst.username)

        val migration = builder.buildPartial

        assertTrue(
          migration.dynamicMigration.actions.nonEmpty,
          migration.dynamicMigration.actions.head.isInstanceOf[Rename]
        )
      },

      // -----------------------------------------------------------------------
      // TEST 2: Intersection Type Migration (The "Killer Feature")
      // -----------------------------------------------------------------------
      test("2. [Scala 3] Migration from Intersection Type (PartA & PartB)") {

        // Scenario: Source is (PartA & PartB).
        // We act on it as if it has fields from BOTH traits.
        // If the macro works correctly, it should handle the Intersection Type seamlessly.

        val builder = MigrationBuilder
          .make[CombinedEntity, TargetEntity]
          .renameField(src => src.id, dst => dst.id)                   // From PartA
          .renameField(src => src.description, dst => dst.description) // From PartB

        val migration = builder.buildPartial
        val actions   = migration.dynamicMigration.actions

        assertTrue(
          actions.size == 2,
          // Verify First Action (id)
          actions(0).asInstanceOf[Rename].at.nodes.head == DynamicOptic.Node.Field("id"),
          // Verify Second Action (description)
          actions(1).asInstanceOf[Rename].at.nodes.head == DynamicOptic.Node.Field("description")
        )
      }
    )
  )
}
