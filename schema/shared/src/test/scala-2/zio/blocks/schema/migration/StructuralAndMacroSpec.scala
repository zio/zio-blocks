package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._
import zio.blocks.schema.migration.MigrationAction._

// =================================================================================
// 1. DOMAIN MODELS WRAPPER (Global Scope for Macro Safety)
// =================================================================================

object StructuralModels {

  // --- Scenario 1: Traits (Abstract Data Structures) ---
  trait OldStruct { def name: String }
  case class NewClass(fullName: String)

  // --- Scenario 2: Structural Refinements ---
  trait PaymentMethodBase
  // This 'type' alias simulates a structural refinement usually found in legacy code
  type OldCreditCard = PaymentMethodBase { type Tag = "CreditCard"; def number: String }
}

// =================================================================================
// 2. TEST SUITE
// =================================================================================

object StructuralAndMacroSpec extends ZIOSpecDefault {

  // Import models globally
  import StructuralModels._

  // ==========================================
  // Implicit Schemas (Mocked for Testing)
  // ==========================================

  // Implicit resolution is key for the Builder to work
  implicit val newClassSchema: Schema[NewClass]   = null.asInstanceOf[Schema[NewClass]]
  implicit val oldStructSchema: Schema[OldStruct] = null.asInstanceOf[Schema[OldStruct]]

  // ==========================================
  // Test Suite
  // ==========================================

  def spec = suite("Scala 2 Exclusive: Structural & Macro Compliance")(
    // ---------------------------------------------------------------------------
    // TEST GROUP: MIGRATION BUILDER INTEGRITY
    // We verify Trait/Structural support via the Builder API (Best Practice)
    // ---------------------------------------------------------------------------

    suite("Migration Builder on Traits & Structures")(
      test("1. Cross-Platform Structural Migration (Trait -> Case Class)") {
        val v0Schema: Schema[OldStruct] = oldStructSchema
        val v1Schema: Schema[NewClass]  = newClassSchema

        // PROOF OF EXCLUSIVE FEATURE:
        // We are renaming a field from a 'Trait' (OldStruct).
        // If the Macro works on Traits, this line will compile successfully.
        // We don't need to test the macro in isolation; the Builder is the proof.

        val builder = MigrationBuilder
          .make(v0Schema, v1Schema)
          .renameField((x: OldStruct) => x.name, (x: NewClass) => x.fullName)

        val migration = builder.buildPartial

        // Runtime Logic Verification
        assertTrue(
          migration.dynamicMigration.actions.nonEmpty,
          migration.dynamicMigration.actions.head.isInstanceOf[Rename],

          // Verify deep logic: The 'from' field should be "name"
          migration.dynamicMigration.actions.head.asInstanceOf[Rename].at.nodes.head
            == DynamicOptic.Node.Field("name")
        )
      },

      test("2. Identity Migration on Concrete Classes") {
        // Simple sanity check to ensure mixed usage works
        val builder   = MigrationBuilder.make[NewClass, NewClass]
        val migration = builder.build

        assertTrue(migration.dynamicMigration.actions.isEmpty)
      }
    )
  )
}
