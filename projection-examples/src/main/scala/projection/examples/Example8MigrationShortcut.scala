/*
 * Example 8 of 11: the migration shortcut.
 *
 * Read Example7SchemaEvolution first. It is the same story, with one change: the
 * second engine is told how the read model changed. Next: Example9RebuildOnDemand.
 *
 * When the read model gains a field, such as `UserProfile` gaining an `email`,
 * the engine normally rebuilds the whole table by replaying every event. If the
 * change is only added fields, a `Migration` registered with
 * `engine.registerMigration` lets the engine add the columns with
 * `ALTER TABLE ADD COLUMN` instead, and no event is replayed.
 *
 * This example runs on real SQLite files and uses two engines in a row. The
 * events stay in `.projection-examples/example8/events/users.db`, and the read
 * model in `.projection-examples/example8/profiles/userProfiles.db`.
 *   1. The first engine runs the projection with `V1.UserProfile` (id, name).
 *   2. The second engine runs it with `V2.UserProfile` (id, name, email), after
 *      registering a migration that adds `email` with the default `""`.
 *
 * It shows:
 *   - registering the migration before `start`, so the engine can use it
 *   - the old rows being kept without a rebuild. Their new `email` column is
 *     empty (`null`), not `""` and not the real address: the shortcut only adds
 *     the column and does not replay the events. A rebuild, as in
 *     Example7SchemaEvolution, would fill it from the stored events.
 *   - that the migrated projection keeps processing new events
 *
 * Run it from the repository root with:
 *   sbt "projection-examples/runMain projection.examples.Example8MigrationShortcut"
 */

package projection.examples

import zio.*
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema, SchemaExpr}
import zio.blocks.schema.migration.Migration

import Polling.pollUntil

object Example8MigrationShortcut extends ZIOAppDefault {

  private val profilesFolder: String = Sqlite.path("example8/profiles")

  object V1 {
    case class UserProfile(@Modifier.id id: String, name: String)
    object UserProfile {
      implicit val schema: Schema[UserProfile]         = Schema.derived[UserProfile]
      implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived[UserProfile](profilesFolder)
    }
  }

  object V2 {
    case class UserProfile(@Modifier.id id: String, name: String, email: String)
    object UserProfile {
      implicit val schema: Schema[UserProfile]         = Schema.derived[UserProfile]
      implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived[UserProfile](profilesFolder)
    }
  }

  private def show(profile: Option[V2.UserProfile]): String =
    profile.map(u => s"${u.name} <${u.email}>").getOrElse("not found")

  def demo: ZIO[Any, Throwable, Unit] =
    Console.printLine("\n--- Example 8: Migration Shortcut (UserProfile V1 -> V2) ---").orDie *>
      ZIO.scoped {
        for {
          _ <- Sqlite.folders(profilesFolder, Sqlite.path("example8/events"))

          hashV1 = SchemaHash.compute[V1.UserProfile]
          hashV2 = SchemaHash.compute[V2.UserProfile]
          _     <- Console.printLine(s"  SchemaHash V1: ${hashV1.take(16)}...").orDie
          _     <- Console.printLine(s"  SchemaHash V2: ${hashV2.take(16)}...").orDie
          _     <- Console.printLine(s"  Hashes differ: ${hashV1 != hashV2}").orDie

          projectionV1 = Projection[V1.UserProfile]("userProfiles")
                           .from("users")
                           .routeToSelf
                           .on[UserCreated]
                           .insert((e, ctx) => V1.UserProfile(ctx.entityId, e.name))

          projectionV2 = Projection[V2.UserProfile]("userProfiles")
                           .from("users")
                           .routeToSelf
                           .on[UserCreated]
                           .insert((e, ctx) => V2.UserProfile(ctx.entityId, e.name, e.email))

          // Describes the change from V1 to V2: the new field `email` starts as "".
          migration = Migration
                        .newBuilder[V1.UserProfile, V2.UserProfile]
                        .addField(_.email, SchemaExpr.literal(""))
                        .build

          // 1. The first version of the application.
          _ <- ZIO.scoped {
                 for {
                   cache  <- TransactorCache.make()
                   users  <- Sqlite.eventStore[UserCreated](cache, Sqlite.path("example8/events/users.db"))
                   engine <- ProjectionEngine.makeWithStoresVarargs(
                               cache,
                               Map[String, EventStore[?]]("users" -> users)
                             )(projectionV1)
                   _      <- engine.start

                   _ <- users.append("user-1", UserCreated("Alice", "alice@example.com"))
                   _ <- users.append("user-2", UserCreated("Bob", "bob@example.com"))

                   u2 <- pollUntil(engine.query(projectionV1, "user-2"))
                   u1 <- engine.query(projectionV1, "user-1")
                   _  <- Console.printLine(s"  V1 user-1: ${u1.map(_.name).getOrElse("not found")}").orDie
                   _  <- Console.printLine(s"  V1 user-2: ${u2.map(_.name).getOrElse("not found")}").orDie
                 } yield ()
               }

          _ <- Console.printLine("  --- restart with V2 ---").orDie

          // 2. The second version of the application, on the same files.
          _ <- ZIO.scoped {
                 for {
                   cache  <- TransactorCache.make()
                   users  <- Sqlite.eventStore[UserCreated](cache, Sqlite.path("example8/events/users.db"))
                   engine <- ProjectionEngine.makeWithStoresVarargs(
                               cache,
                               Map[String, EventStore[?]]("users" -> users)
                             )(projectionV2)
                   // The migration is registered before `start`, which is where the changed schema hash is found.
                   // Because it only adds a field, the engine adds the column and does not replay the events.
                   _ <- engine.registerMigration(projectionV2, migration)
                   _ <- engine.start

                   u1 <- engine.query(projectionV2, "user-1")
                   u2 <- engine.query(projectionV2, "user-2")
                   _  <- Console.printLine(s"  After migration, user-1: ${show(u1)}").orDie
                   _  <- Console.printLine(s"  After migration, user-2: ${show(u2)}").orDie

                   _  <- users.append("user-3", UserCreated("Carol", "carol@example.com"))
                   u3 <- pollUntil(engine.query(projectionV2, "user-3"))
                   _  <- Console.printLine(s"  New event, user-3:    ${show(u3)}").orDie
                 } yield ()
               }

          _ <- Console.printLine("  ✓ Migration shortcut: the column was added, old rows kept, their new field is empty").orDie
        } yield ()
      }

  def run: ZIO[Any, Throwable, Unit] = demo
}
