/*
 * Example 1 of 9: a per-entity projection.
 *
 * Start here. Next: Example2CrossEntity.
 *
 * A per-entity projection keeps one row for each entity ID. Here every
 * `UserCreated` event, appended under an ID such as "user-1", becomes a
 * `UserProfile` row stored under that same ID.
 *
 * It shows:
 *   - defining the read model (`UserProfile`) with an `EntityPath`
 *   - declaring the projection with `from`, `routeToSelf`, `on` and `insert`
 *   - running it in memory with `TestEngine`, appending events and querying rows
 *
 * Run it from the repository root with:
 *   sbt "projection-examples/runMain projection.examples.Example1PerEntity"
 */

package projection.examples

import zio.*
import zio.blocks.projection.*
import zio.blocks.projection.testing.TestEngine
import zio.blocks.schema.{Modifier, Schema}

import Polling.pollUntil

object Example1PerEntity extends ZIOAppDefault {

  case class UserProfile(@Modifier.id id: String, name: String, email: String)
  object UserProfile {
    implicit val schema: Schema[UserProfile]         = Schema.derived[UserProfile]
    implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived[UserProfile]
  }

  def demo: ZIO[Any, Throwable, Unit] =
    Console.printLine("\n--- Example 1: PerEntity Projection (UserProfile) ---").orDie *>
      ZIO.scoped {
        for {
          projection = Projection[UserProfile]("userProfiles")
                         .from("users")
                         .routeToSelf
                         .on[UserCreated]
                         .insert((e, ctx) => UserProfile(ctx.entityId, e.name, e.email))

          engine <- TestEngine.make(projection)

          _ <- engine.append("user-1", UserCreated("Alice", "alice@example.com"))
          _ <- engine.append("user-2", UserCreated("Bob", "bob@example.com"))
          _ <- engine.append("user-3", UserCreated("Charlie", "charlie@example.com"))

          u1 <- pollUntil(engine.query(projection, "user-1"))
          u2 <- pollUntil(engine.query(projection, "user-2"))
          u3 <- pollUntil(engine.query(projection, "user-3"))

          _ <- Console.printLine(s"  user-1: ${u1.map(u => s"${u.name} <${u.email}>").getOrElse("not found")}").orDie
          _ <- Console.printLine(s"  user-2: ${u2.map(u => s"${u.name} <${u.email}>").getOrElse("not found")}").orDie
          _ <- Console.printLine(s"  user-3: ${u3.map(u => s"${u.name} <${u.email}>").getOrElse("not found")}").orDie
          _ <- Console.printLine("  ✓ PerEntity: each user stored in its own entity row").orDie
        } yield ()
      }

  def run: ZIO[Any, Throwable, Unit] = demo
}
