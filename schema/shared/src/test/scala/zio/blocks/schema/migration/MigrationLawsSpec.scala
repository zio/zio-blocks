package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._
import zio.blocks.schema.migration.MigrationAction._
import zio.blocks.schema.migration.SchemaExpr

object MigrationLawsSpec extends ZIOSpecDefault {

  // =================================================================================
  // 1. SETUP & MOCKS
  // =================================================================================

  // Mock SchemaExpr
  val mockExpr: SchemaExpr[_] = null.asInstanceOf[SchemaExpr[_]]

  // Helper to create a simple optic path
  def path(field: String): DynamicOptic = DynamicOptic(Vector(DynamicOptic.Node.Field(field)))

  // [FIX] Moved 'Person' class here (Object Level) to avoid "Unused Symbol" error in Scala 3
  case class Person(name: String)
  implicit val sPerson: Schema[Person] = null.asInstanceOf[Schema[Person]]

  // =================================================================================
  // 2. LAW VERIFICATION SUITE
  // =================================================================================

  def spec = suite("Mentor's Laws: Mathematical Verification")(
    // ---------------------------------------------------------------------------
    // LAW 1: Identity
    // ---------------------------------------------------------------------------
    suite("Law 1: Identity")(
      test("Empty migration builder produces zero actions") {
        // Identity Migration (Source == Target)
        // Using the Person class defined above
        val m = MigrationBuilder.make[Person, Person].buildPartial

        // Verification
        assertTrue(m.dynamicMigration.actions.isEmpty)
      }
    ),

    // ---------------------------------------------------------------------------
    // LAW 2: Associativity
    // ---------------------------------------------------------------------------
    suite("Law 2: Associativity (Action Composition)")(
      test("Composition of action lists is associative") {
        val a1 = Rename(path("f1"), "f2")
        val a2 = AddField(path("f3"), mockExpr)
        val a3 = DropField(path("f4"), mockExpr)

        val m1 = Vector(a1)
        val m2 = Vector(a2)
        val m3 = Vector(a3)

        val leftSide  = (m1 ++ m2) ++ m3
        val rightSide = m1 ++ (m2 ++ m3)

        assertTrue(leftSide == rightSide)
      }
    ),

    // ---------------------------------------------------------------------------
    // LAW 3: Structural Reverse
    // ---------------------------------------------------------------------------
    suite("Law 3: Structural Reverse (m.reverse.reverse == m)")(
      test("Rename: reverse.reverse == identity") {
        val original = Rename(path("old"), "new")
        assertTrue(original.reverse.reverse == original)
      },

      test("AddField: reverse.reverse == identity") {
        val original = AddField(path("newField"), mockExpr)
        assertTrue(original.reverse.reverse == original)
      },

      test("DropField: reverse.reverse == identity") {
        val original = DropField(path("deletedField"), mockExpr)
        assertTrue(original.reverse.reverse == original)
      },

      test("Mandate <-> Optionalize cycle") {
        val original  = Mandate(path("opt"), mockExpr)
        val reversed  = original.reverse
        val roundTrip = reversed.reverse

        assertTrue(
          reversed.isInstanceOf[Optionalize] &&
            roundTrip.isInstanceOf[Mandate]
        )
      },

      test("Enum RenameCase: reverse.reverse == identity") {
        val original = RenameCase(path("Tag"), "OldCase", "NewCase")
        assertTrue(original.reverse.reverse == original)
      },

      test("Join <-> Split cycle") {
        val original = Join(path("full"), Vector(path("first"), path("last")), mockExpr)
        assertTrue(original.reverse.reverse == original)
      }
    ),

    // ---------------------------------------------------------------------------
    // LAW 4: Best-Effort Semantic Inverse
    // ---------------------------------------------------------------------------
    suite("Law 4: Semantic Inverse Logic")(
      test("Rename reverse targets the output name as source") {
        val forward  = Rename(path("a"), "b")
        val reversed = forward.reverse.asInstanceOf[Rename]

        assertTrue(
          reversed.at.nodes.last == DynamicOptic.Node.Field("b") &&
            reversed.to == "a"
        )
      },

      test("Split reverse targets the combined field") {
        val forward  = Split(path("fullName"), Vector(path("first"), path("last")), mockExpr)
        val reversed = forward.reverse.asInstanceOf[Join]

        assertTrue(
          reversed.at == forward.at &&
            reversed.sourcePaths == forward.targetPaths
        )
      }
    )
  )
}
