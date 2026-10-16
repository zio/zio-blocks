/*
 * Runs every projection example in turn, in the order they are meant to be
 * read:
 *   1. Example1PerEntity
 *   2. Example2CrossEntity
 *   3. Example3InsertAndDelete
 *   4. Example4FieldUpdates
 *   5. Example5GlobalAggregate
 *   6. Example6CustomActions
 *   7. Example7SchemaEvolution
 *   8. Example8MigrationShortcut
 *   9. Example9RebuildOnDemand
 *   10. Example10TagRename
 *   11. Example11EventFieldChanges
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
      _ <- Example1PerEntity.demo
      _ <- Example2CrossEntity.demo
      _ <- Example3InsertAndDelete.demo
      _ <- Example4FieldUpdates.demo
      _ <- Example5GlobalAggregate.demo
      _ <- Example6CustomActions.demo
      _ <- Example7SchemaEvolution.demo
      _ <- Example8MigrationShortcut.demo
      _ <- Example9RebuildOnDemand.demo
      _ <- Example10TagRename.demo
      _ <- Example11EventFieldChanges.demo
      _ <- Console.printLine("\n=== All demos completed successfully ===").orDie
    } yield ()
}
