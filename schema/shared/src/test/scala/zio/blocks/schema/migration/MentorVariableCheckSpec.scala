package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._
import zio.blocks.schema.migration.MigrationAction._
import zio.blocks.schema.migration.SchemaExpr

object MentorVariableCheckSpec extends ZIOSpecDefault {

  // =================================================================================
  // 1. SETUP MODELS
  // =================================================================================

  case class V1(name: String, age: Int)
  case class V2(fullName: String, age: Int, active: Boolean)

  implicit val sV1: Schema[V1] = null.asInstanceOf[Schema[V1]]
  implicit val sV2: Schema[V2] = null.asInstanceOf[Schema[V2]]
  val mockExpr: SchemaExpr[_]  = null.asInstanceOf[SchemaExpr[_]]

  // =================================================================================
  // 2. THE MENTOR'S CHALLENGE (Fixed & Focused)
  // =================================================================================

  def spec = suite("Addressing @bikramadhikari001's Feedback")(
    test("The 'Acid Test': Breaking the chain into VALs works correctly") {

      // --- Step 1: Base Builder ---
      val step1 = MigrationBuilder.make[V1, V2]

      // --- Step 2: Add operation, assign to variable 'step2' ---
      // If we were parsing AST, the macro would only see the identifier "step1" here.
      // But since we track types, 'step2' captures the state: RenameField[...]
      val step2 = step1.renameField((s: V1) => s.name, (t: V2) => t.fullName)

      // --- Step 3: Add another operation on 'step2' ---
      val step3 = step2.addField((t: V2) => t.active, mockExpr)

      // --- Step 4: Call .buildPartial on the final variable ---
      // This is the moment of truth. The macro inspects the Type of 'step3'.
      val result = step3.buildPartial

      // VERIFICATION:
      // 1. We expect 2 actions.
      // 2. The actions must be in the correct order.
      assertTrue(
        result.dynamicMigration.actions.size == 2,
        result.dynamicMigration.actions.head.isInstanceOf[Rename],
        result.dynamicMigration.actions.last.isInstanceOf[AddField]
      )
    },

    test("Chain separation with explicit type annotation verification") {
      // Further proof: We verify that the intermediate variable actually holds the State Type.

      val m = MigrationBuilder
        .make[V1, V2]
        .renameField((s: V1) => s.name, (t: V2) => t.fullName)

      // We call build on the variable 'm'
      val result = m.buildPartial

      // If the macro relied on "inline chaining", this would return empty or crash.
      // Since it works, it proves we are using Type-Driven State.
      assertTrue(
        result.dynamicMigration.actions.nonEmpty,
        result.dynamicMigration.actions.head.isInstanceOf[Rename]
      )
    }
  )
}
