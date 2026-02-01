package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue}
import zio.blocks.schema.SchemaBaseSpec
import zio.test._

/**
 * Tests for Join and Split migration operations.
 *
 * Verifies that these operations correctly manipulate record fields using
 * DynamicOptic paths for precise field addressing.
 */
object JoinSplitOperationsSpec extends SchemaBaseSpec {

  private def str(s: String): DynamicValue = DynamicValue.Primitive(PrimitiveValue.String(s))

  def spec: Spec[TestEnvironment, Any] = suite("JoinSplitOperationsSpec")(
    suite("Join Operation")(
      test("removes source paths and adds combined target field") {
        val combiner = Resolved.Concat(
          Vector(
            Resolved.FieldAccess("firstName", Resolved.Identity),
            Resolved.FieldAccess("lastName", Resolved.Identity)
          ),
          " "
        )
        val splitter = Resolved.SplitString(Resolved.Identity, " ", 0)

        val action = MigrationAction.Join(
          at = DynamicOptic.root,
          targetFieldName = "fullName",
          sourcePaths = Chunk(
            DynamicOptic.root.field("firstName"),
            DynamicOptic.root.field("lastName")
          ),
          combiner = combiner,
          splitter = splitter
        )

        val migration = DynamicMigration.single(action)
        val input     = DynamicValue.Record(
          ("firstName", str("John")),
          ("lastName", str("Doe"))
        )

        val result = DynamicMigrationInterpreter(migration, input)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            val hasFullName = fields.exists { case (k, v) =>
              k == "fullName" && v == str("John Doe")
            }
            val hasFirstName = fields.exists(_._1 == "firstName")
            val hasLastName  = fields.exists(_._1 == "lastName")
            assertTrue(hasFullName) &&
            assertTrue(!hasFirstName) &&
            assertTrue(!hasLastName)
          case Left(err) =>
            println(s"Error: $err")
            assertTrue(false)
          case Right(other) =>
            println(s"Unexpected: $other")
            assertTrue(false)
        }
      },
      test("stores DynamicOptic sourcePaths correctly") {
        val action = MigrationAction.Join(
          at = DynamicOptic.root,
          targetFieldName = "combined",
          sourcePaths = Chunk(
            DynamicOptic.root.field("a"),
            DynamicOptic.root.field("b")
          ),
          combiner = Resolved.Identity,
          splitter = Resolved.Identity
        )

        assertTrue(action.sourcePaths.size == 2) &&
        assertTrue(action.sourcePaths.head == DynamicOptic.root.field("a")) &&
        assertTrue(action.sourcePaths(1) == DynamicOptic.root.field("b"))
      }
    ),
    suite("Split Operation")(
      test("stores DynamicOptic targetPaths correctly") {
        val splitter = Resolved.SplitString(Resolved.Identity, " ", 0)
        val combiner = Resolved.Concat(
          Vector(Resolved.FieldAccess("firstName", Resolved.Identity)),
          ""
        )

        val action = MigrationAction.Split(
          at = DynamicOptic.root,
          sourceFieldName = "fullName",
          targetPaths = Chunk(
            DynamicOptic.root.field("firstName"),
            DynamicOptic.root.field("lastName")
          ),
          splitter = splitter,
          combiner = combiner
        )

        assertTrue(action.sourceFieldName == "fullName") &&
        assertTrue(action.targetPaths.size == 2) &&
        assertTrue(action.targetPaths.head == DynamicOptic.root.field("firstName")) &&
        assertTrue(action.targetPaths(1) == DynamicOptic.root.field("lastName")) &&
        assertTrue(action.at == DynamicOptic.root)
      },
      test("creates target fields with correct paths") {
        val splitter = Resolved.SplitString(Resolved.Identity, " ", 0)
        val combiner = Resolved.Concat(
          Vector(Resolved.FieldAccess("a", Resolved.Identity)),
          ""
        )

        val action = MigrationAction.Split(
          at = DynamicOptic.root,
          sourceFieldName = "combined",
          targetPaths = Chunk(
            DynamicOptic.root.field("a"),
            DynamicOptic.root.field("b")
          ),
          splitter = splitter,
          combiner = combiner
        )

        assertTrue(action.sourceFieldName == "combined") &&
        assertTrue(action.targetPaths.size == 2) &&
        assertTrue(action.targetPaths.head == DynamicOptic.root.field("a")) &&
        assertTrue(action.targetPaths(1) == DynamicOptic.root.field("b"))
      }
    )
  )
}
