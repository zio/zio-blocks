package zio.schema.migration

import zio.test._
import zio.test.Assertion._
import zio.blocks.schema._

object DynamicMigrationLawsSpec extends ZIOSpecDefault {

  // Generators
  val genNode: Gen[Any, DynamicOptic.Node] = Gen.oneOf(
    Gen.alphaNumericString.map(DynamicOptic.Node.Field(_)),
    Gen.alphaNumericString.map(DynamicOptic.Node.Case(_)),
    Gen.int.map(DynamicOptic.Node.AtIndex(_))
    // Add others if needed
  )

  val genPath: Gen[Any, DynamicOptic] = Gen.listOfBounded(0, 3)(genNode).map(nodes => DynamicOptic(nodes.toVector))

  val genAction: Gen[Any, MigrationAction] = Gen.oneOf(
    // Simple actions for laws
    for {
      path    <- genPath
      newName <- Gen.alphaNumericString
    } yield MigrationAction.Rename(path, newName),
    for {
      path <- genPath
    } yield MigrationAction.Optionalize(path)
  )

  val genMigration: Gen[Any, DynamicMigration] =
    Gen.listOfBounded(0, 5)(genAction).map(actions => DynamicMigration(actions.toVector))

  override def spec = suite("DynamicMigration Laws")(
    test("Associativity: (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)") {
      check(genMigration, genMigration, genMigration) { (m1, m2, m3) =>
        val left  = (m1 ++ m2) ++ m3
        val right = m1 ++ (m2 ++ m3)
        assert(left)(equalTo(right))
      }
    },
    test("Identity: m ++ empty == m") {
      check(genMigration) { m =>
        assert(m ++ DynamicMigration(Vector.empty))(equalTo(m))
      }
    },
    test("Identity: empty ++ m == m") {
      check(genMigration) { m =>
        assert(DynamicMigration(Vector.empty) ++ m)(equalTo(m))
      }
    }
  )
}
