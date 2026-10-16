/*
 * Runs every projection example in turn, in the order they are meant to be
 * read:
 *   1. Example1PerEntity
 *   2. Example2CrossEntity
 *   3. Example3GlobalAggregate
 *   4. Example4SchemaEvolution
 *   5. Example5TagRename
 *   6. Example6MigrationShortcut
 *   7. Example7CustomActions
 *   8. Example8FieldUpdates
 *   9. Example9InsertAndDelete
 *   10. Example10EventFieldChanges
 *   11. Example11RebuildOnDemand
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
      _ <- Example3GlobalAggregate.demo
      _ <- Example4SchemaEvolution.demo
      _ <- Example5TagRename.demo
      _ <- Example6MigrationShortcut.demo
      _ <- Example7CustomActions.demo
      _ <- Example8FieldUpdates.demo
      _ <- Example9InsertAndDelete.demo
      _ <- Example10EventFieldChanges.demo
      _ <- Example11RebuildOnDemand.demo
      _ <- Console.printLine("\n=== All demos completed successfully ===").orDie
    } yield ()
}
