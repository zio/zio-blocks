/*
 * Example 3 of 8: a global aggregate projection.
 *
 * Read Examples 1 and 2 first. Next: Example4SchemaEvolution.
 *
 * A global projection keeps one shared store for the whole projection, which is
 * the place for totals and counters. Here `UserCreated` events from the "users"
 * source and `RepoCreated` events from the "repos" source both feed a single
 * `DailyStats` row.
 *
 * This example runs on real SQLite files: the events are kept in
 * `.projection-examples/example3/events/users.db` and `.projection-examples/example3/events/repos.db`,
 * and the projection in `global/dailyStats.db`. The engine always keeps the store
 * of a global projection in a folder called `global`, so it is not inside
 * `.projection-examples`.
 *
 * It shows:
 *   - `Projection.global`, with `routeToAll` on each source
 *   - `aggregate` with `FieldUpdate.Increment`, which adds to a counter in SQL
 *     without reading the row first
 *   - one projection listening to more than one source, each with its own
 *     event store
 *
 * Run it from the repository root with:
 *   sbt "projection-examples/runMain projection.examples.Example3GlobalAggregate"
 */

package projection.examples

import zio.*
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

import Polling.pollUntil

object Example3GlobalAggregate extends ZIOAppDefault {

  // The key is the date. Its field is called `id` because a global projection has no `EntityPath`, so its
  // SQLite store looks for a column with that name.
  case class DailyStats(@Modifier.id id: String, userCount: Int, repoCount: Int)
  object DailyStats {
    implicit val schema: Schema[DailyStats]         = Schema.derived[DailyStats]
    implicit val entityPath: EntityPath[DailyStats] = EntityPath.derived[DailyStats]
  }

  def demo: ZIO[Any, Throwable, Unit] =
    Console.printLine("\n--- Example 3: Global Aggregate (DailyStats from users + repos) ---").orDie *>
      ZIO.scoped {
        for {
          _ <- Sqlite.globalFolder("dailyStats") *> Sqlite.folders(Sqlite.path("example3/events"))

          dateKey = "2025-08-26"

          projection = Projection
                         .global[DailyStats]("dailyStats")
                         .from("users")
                         .routeToAll
                         .on[UserCreated]
                         .aggregate(FieldUpdate.Increment("user_count", 1L))
                         .from("repos")
                         .routeToAll
                         .on[RepoCreated]
                         .aggregate(FieldUpdate.Increment("repo_count", 1L))

          _ <- Console.printLine(s"  Scope: ${projection.scope}").orDie

          cache <- TransactorCache.make()
          users <- Sqlite.eventStore[UserCreated](cache, Sqlite.path("example3/events/users.db"))
          repos <- Sqlite.eventStore[RepoCreated](cache, Sqlite.path("example3/events/repos.db"))
          engine <- ProjectionEngine.makeWithStoresVarargs(
                      cache,
                      Map[String, EventStore[?]]("users" -> users, "repos" -> repos)
                    )(projection)
          _ <- engine.start

          _ <- ZIO.foreachDiscard(1 to 5)(i => users.append(dateKey, UserCreated(s"user-$i", s"u$i@ex.com")))
          _ <- ZIO.foreachDiscard(1 to 3)(i => repos.append(dateKey, RepoCreated("any", s"repo-$i")))

          stats <- pollUntil(
                     engine.query(projection, dateKey).map(_.filter(s => s.userCount == 5 && s.repoCount == 3)),
                     5.seconds
                   )

          files <- Sqlite.dbFiles("global")
          _     <- Console.printLine(s"  Files in global/: ${files.mkString(", ")}").orDie
          _ <- stats match {
                 case Some(s) =>
                   Console.printLine(s"  Date: $dateKey, Users: ${s.userCount}, Repos: ${s.repoCount}").orDie
                 case None =>
                   Console.printLine("  ⚠ Aggregate not yet ready (timeout)").orDie
               }
          _ <- Console.printLine("  ✓ Global: events from multiple sources converge to single aggregate row").orDie
        } yield ()
      }

  def run: ZIO[Any, Throwable, Unit] = demo
}
