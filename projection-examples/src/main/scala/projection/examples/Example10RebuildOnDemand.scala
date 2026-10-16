/*
 * Example 10 of 12: rebuilding on demand.
 *
 * Builds on Example8SchemaEvolution. Read it first. Next: Example11TagRename.
 *
 * The engine rebuilds a projection by itself only when the shape of its row type
 * changes. A handler that now computes something else does not change that
 * shape, so the rows already stored keep the old results. To recompute them,
 * rebuild the projection yourself: `SchemaEvolution.rebuildWithHash` empties the
 * projection's store and applies every stored event again through the current
 * handlers.
 *
 * This example runs on real SQLite files: the events are kept in
 * `.projection-examples/example10/events/users.db`, and the read model in
 * `.projection-examples/example10/profiles/user_profiles.db`. It runs three
 * versions of an application in a row, on the same files:
 *   1. The first version stores the users' names as they are.
 *   2. The second changes the handler to store the names in upper case. The old
 *      rows keep their names, because nothing triggers a rebuild.
 *   3. The third has the same handler as the second, and calls
 *      `rebuildWithHash` before the engine starts. Now the old rows are in upper
 *      case too.
 *
 * It shows:
 *   - that changing a handler does not rebuild the stored rows
 *   - rebuilding on demand with `SchemaEvolution.rebuildWithHash`, before
 *     `start`, with the stores created for `makeWithStores`
 *
 * Run it from the repository root with:
 *   sbt "projection-examples/runMain projection.examples.Example10RebuildOnDemand"
 */

package projection.examples

import zio.*
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

import Polling.pollUntil

object Example10RebuildOnDemand extends ZIOAppDefault {

  private val profilesFolder: String = Sqlite.path("example10/profiles")
  private val profilesFile: String   = s"$profilesFolder/user_profiles.db"
  private val eventsFile: String     = Sqlite.path("example10/events/users.db")

  case class UserProfile(@Modifier.id userId: String, name: String)
  object UserProfile {
    implicit val schema: Schema[UserProfile]         = Schema.derived[UserProfile]
    implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived[UserProfile](profilesFolder)
  }

  // The first version stores the name as it is.
  val asIs: Projection[UserProfile] = Projection[UserProfile]("user_profiles")
    .from("users")
    .routeToSelf
    .on[UserCreated]
    .insert((e, ctx) => UserProfile(ctx.entityId, e.name))

  // A later version stores the name in upper case. The row type is the same, so its fingerprint is the same too.
  val upperCase: Projection[UserProfile] = Projection[UserProfile]("user_profiles")
    .from("users")
    .routeToSelf
    .on[UserCreated]
    .insert((e, ctx) => UserProfile(ctx.entityId, e.name.toUpperCase))

  private def name(profile: Option[UserProfile]): String = profile.map(_.name).getOrElse("not found")

  def demo: ZIO[Any, Throwable, Unit] =
    Console.printLine("\n--- Example 10: Rebuilding on Demand (a changed handler) ---").orDie *>
      ZIO.scoped {
        for {
          _ <- Sqlite.folders(profilesFolder, Sqlite.path("example10/events"))

          // 1. The first version of the application.
          _ <- ZIO.scoped {
                 for {
                   cache  <- TransactorCache.make()
                   users  <- Sqlite.eventStore[UserCreated](cache, eventsFile)
                   engine <-
                     ProjectionEngine.makeWithStoresVarargs(cache, Map[String, EventStore[?]]("users" -> users))(asIs)
                   _  <- engine.start
                   _  <- users.append("user-1", UserCreated("Alice", "alice@example.com"))
                   _  <- users.append("user-2", UserCreated("Bob", "bob@example.com"))
                   u2 <- pollUntil(engine.query(asIs, "user-2"))
                   u1 <- engine.query(asIs, "user-1")
                   _  <- Console.printLine(s"  V1 user-1: ${name(u1)}").orDie
                   _  <- Console.printLine(s"  V1 user-2: ${name(u2)}").orDie
                 } yield ()
               }

          _ <- Console.printLine("  --- restart with the upper-case handler ---").orDie

          // 2. The handler changed but the row type did not, so `start` finds the same fingerprint and does not rebuild.
          _ <- ZIO.scoped {
                 for {
                   cache  <- TransactorCache.make()
                   users  <- Sqlite.eventStore[UserCreated](cache, eventsFile)
                   engine <- ProjectionEngine.makeWithStoresVarargs(
                               cache,
                               Map[String, EventStore[?]]("users" -> users)
                             )(upperCase)
                   _  <- engine.start
                   u1 <- engine.query(upperCase, "user-1")
                   _  <- Console.printLine(s"  V2 user-1: ${name(u1)} (not rebuilt)").orDie
                   _  <- users.append("user-3", UserCreated("Carol", "carol@example.com"))
                   u3 <- pollUntil(engine.query(upperCase, "user-3"))
                   _  <- Console.printLine(s"  V2 new event, user-3: ${name(u3)}").orDie
                 } yield ()
               }

          _ <- Console.printLine("  --- restart and rebuild on demand ---").orDie

          // 3. The same handler, with the rows rebuilt before the engine starts.
          _ <- ZIO.scoped {
                 for {
                   cache    <- TransactorCache.make()
                   tx       <- cache.get(eventsFile)
                   users    <- SQLiteEventStore.make[UserCreated](tx)
                   profiles <- SQLiteProjectionStore.make[UserProfile](profilesFile, cache)
                   // Empties the store and applies every stored event again, with the current handler.
                   _      <- SchemaEvolution.rebuildWithHash(profiles, users, upperCase)
                   engine <- ProjectionEngine.makeWithStores(
                               specs = List(upperCase),
                               stores = Map("user_profiles" -> profiles),
                               eventStores = Map("users" -> users),
                               cache = cache
                             )
                   _    <- engine.start
                   rows <-
                     ZIO.foreach(List("user-1", "user-2", "user-3"))(id => engine.query(upperCase, id).map(id -> _))
                   _ <- ZIO.foreachDiscard(rows) { case (id, row) =>
                          Console.printLine(s"  After rebuildWithHash, $id: ${name(row)}").orDie
                        }
                 } yield ()
               }

          _ <-
            Console
              .printLine("  ✓ Rebuild on demand: rebuildWithHash applied every stored event again with the new handler")
              .orDie
        } yield ()
      }

  def run: ZIO[Any, Throwable, Unit] = demo
}
