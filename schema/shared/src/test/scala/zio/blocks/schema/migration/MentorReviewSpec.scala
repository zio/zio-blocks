package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._
import zio.blocks.schema.migration.MigrationAction._
//import zio.blocks.schema.migration.SchemaExpr

object MentorReviewSpec extends ZIOSpecDefault {

  // Models
  case class V0(name: String)
  case class V1(fullName: String)

  implicit val sV0: Schema[V0] = null.asInstanceOf[Schema[V0]]
  implicit val sV1: Schema[V1] = null.asInstanceOf[Schema[V1]]

  def spec = suite("Mentor Review: Context Tree vs Type State")(
    test("Should allow factoring migration builder into a variable (The 'val' Test)") {

      // MENTOR'S CHALLENGE:
      // Can we split the definition and the .build call?

      // Step 1: Define the builder and assign it to a variable 'partial'
      // If our implementation relied on AST inspection, it would lose context here.
      // But since we use Type State (S), 'partial' carries the type info:
      // MigrationBuilder[V0, V1, RenameField["name", "fullName", Empty]]
      val partial = MigrationBuilder
        .make[V0, V1]
        .renameField((s: V0) => s.name, (t: V1) => t.fullName)

      // Step 2: Call build (or buildPartial) on the variable
      // The macro should look at the TYPE of 'partial', not the variable name.
      val result = partial.buildPartial

      // Verification
      assertTrue(
        result.dynamicMigration.actions.size == 1,
        result.dynamicMigration.actions.head match {
          case Rename(at, to) =>
            at.nodes.last == DynamicOptic.Node.Field("name") && to == "fullName"
          case _ => false
        }
      )
    },

    test("Should allow chaining variables multiple times") {
      // Even deeper separation
      val step1 = MigrationBuilder.make[V0, V1]

      val step2 = step1.renameField((s: V0) => s.name, (t: V1) => t.fullName)

      // The Type of step2 should preserve the history from step1
      val result = step2.buildPartial

      assertTrue(result.dynamicMigration.actions.nonEmpty)
    }
  )
}
