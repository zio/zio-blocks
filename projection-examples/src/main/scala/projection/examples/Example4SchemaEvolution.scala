/*
 * Example 4 of 6: schema evolution.
 *
 * Builds on Example1PerEntity. Next: Example5TagRename.
 *
 * When the read model's class changes, such as `UserProfile` gaining an `email`
 * field, the data already stored no longer matches. The engine notices this when
 * it starts, by comparing a hash of the schema with the one saved in the store,
 * and rebuilds the store by replaying the events.
 *
 * This example runs on real SQLite files and uses two engines in a row, like an
 * application that is stopped and started again with a newer version. The events
 * stay in `.projection-examples/example4/events/users.db`, and the read model in
 * `.projection-examples/example4/profiles/userProfiles.db`.
 *   1. The first engine runs the projection with `V1.UserProfile` (id, name).
 *   2. The second engine runs it with `V2.UserProfile` (id, name, email). Both
 *      classes are called `UserProfile`, so they use the same table, and the
 *      projection keeps its name.
 *
 * It shows:
 *   - `SchemaHash.compute`, and that two versions of a type hash differently
 *   - the second engine detecting the changed schema in `start` and rebuilding
 *     the table from the stored events, so the old rows gain their `email`
 *   - that the rebuilt projection keeps processing new events
 *
 * Run it from the repository root with:
 *   sbt "projection-examples/runMain projection.examples.Example4SchemaEvolution"
 */

package projection.examples

import zio.*
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

import Polling.pollUntil

object Example4SchemaEvolution extends ZIOAppDefault {

  private val profilesFolder: String = Sqlite.path("example4/profiles")

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
    Console.printLine("\n--- Example 4: Schema Evolution (UserProfile V1 -> V2) ---").orDie *>
      ZIO.scoped {
        for {
          _ <- Sqlite.folders(profilesFolder, Sqlite.path("example4/events"))

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

          // 1. The first version of the application.
          _ <- ZIO.scoped {
                 for {
                   cache  <- TransactorCache.make()
                   users  <- Sqlite.eventStore[UserCreated](cache, Sqlite.path("example4/events/users.db"))
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
                   users  <- Sqlite.eventStore[UserCreated](cache, Sqlite.path("example4/events/users.db"))
                   engine <- ProjectionEngine.makeWithStoresVarargs(
                               cache,
                               Map[String, EventStore[?]]("users" -> users)
                             )(projectionV2)
                   // `start` finds the changed schema hash and rebuilds the table from the stored events.
                   _ <- engine.start

                   u1 <- engine.query(projectionV2, "user-1")
                   u2 <- engine.query(projectionV2, "user-2")
                   _  <- Console.printLine(s"  After rebuild, user-1: ${show(u1)}").orDie
                   _  <- Console.printLine(s"  After rebuild, user-2: ${show(u2)}").orDie

                   _  <- users.append("user-3", UserCreated("Carol", "carol@example.com"))
                   u3 <- pollUntil(engine.query(projectionV2, "user-3"))
                   _  <- Console.printLine(s"  New event, user-3:    ${show(u3)}").orDie
                 } yield ()
               }

          _ <- Console.printLine("  ✓ Schema Evolution: hash check + rebuild from stored events keeps the data").orDie
        } yield ()
      }

  def run: ZIO[Any, Throwable, Unit] = demo
}
