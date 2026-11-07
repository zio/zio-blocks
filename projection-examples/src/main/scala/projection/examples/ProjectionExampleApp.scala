/*
 * Runs every projection example in turn, in the order they are meant to be
 * read:
 *   1. Example1QuickStart
 *   2. Example2PerEntity
 *   3. Example3CrossEntity
 *   4. Example4InsertAndDelete
 *   5. Example5FieldUpdates
 *   6. Example6GlobalAggregate
 *   7. Example7CustomActions
 *   8. Example8SchemaEvolution
 *   9. Example9MigrationShortcut
 *   10. Example10RebuildOnDemand
 *   11. Example11TagRename
 *   12. Example12EventFieldChanges
 *   13. Example13EngineCatchUp
 *
 * Each example can also be run on its own, see the comment at the top of its
 * file.
 *
 * Run it from the repository root with:
 *   sbt projection-examples/run
 */

package projection.examples

import zio.*

object ProjectionExampleApp extends ZIOAppDefault {

  def run: ZIO[Any, Throwable, Unit] =
    for {
      _ <- Console.printLine("=== ZIO Blocks Projection Examples ===").orDie
      _ <- Console.printLine("\n--- Example 1: Quick Start ---").orDie *> Example1QuickStart.run
      _ <- Example2PerEntity.demo
      _ <- Example3CrossEntity.demo
      _ <- Example4InsertAndDelete.demo
      _ <- Example5FieldUpdates.demo
      _ <- Example6GlobalAggregate.demo
      _ <- Example7CustomActions.demo
      _ <- Example8SchemaEvolution.demo
      _ <- Example9MigrationShortcut.demo
      _ <- Example10RebuildOnDemand.demo
      _ <- Example11TagRename.demo
      _ <- Example12EventFieldChanges.demo
      _ <- Example13EngineCatchUp.demo
      _ <- Console.printLine("\n=== All demos completed successfully ===").orDie
    } yield ()
}
