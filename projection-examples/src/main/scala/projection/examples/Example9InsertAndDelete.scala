/*
 * Example 9 of 10: inserting and deleting rows.
 *
 * Builds on Example1PerEntity. Read it first.
 *
 * Two handlers add and remove whole rows:
 *   - `insert` builds a complete row from the event and the `ProjectionContext`
 *     and adds it to the store
 *   - `delete` removes the row of the entity the event was recorded under. It
 *     takes no function, because the row is always the one with `ctx.entityId`
 *
 * Here `UserCreated` inserts a `UserProfile` and `UserDeleted` removes it, so a
 * user's row exists from the moment the user is created until the user is
 * deleted.
 *
 * It shows:
 *   - `insert`, which builds the row from the event and `ctx.entityId`
 *   - `delete`, which removes the row for the entity of the event
 *   - that a deleted row is gone: querying it returns `None`
 *   - running it in memory with `TestEngine`
 *
 * Run it from the repository root with:
 *   sbt "projection-examples/runMain projection.examples.Example9InsertAndDelete"
 */

package projection.examples

import zio.*
import zio.blocks.projection.*
import zio.blocks.projection.testing.TestEngine
import zio.blocks.schema.{Modifier, Schema}

import Polling.pollUntil

object Example9InsertAndDelete extends ZIOAppDefault {

  case class UserDeleted()
  object UserDeleted {
    implicit val schema: Schema[UserDeleted] = Schema.derived[UserDeleted]
  }

  case class UserProfile(@Modifier.id id: String, name: String, email: String)
  object UserProfile {
    implicit val schema: Schema[UserProfile]         = Schema.derived[UserProfile]
    implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived[UserProfile]
  }

  private def show(profile: Option[UserProfile]): String =
    profile.map(p => s"${p.name} <${p.email}>").getOrElse("no row")

  def demo: ZIO[Any, Throwable, Unit] =
    Console.printLine("\n--- Example 9: Insert and Delete (UserProfile rows) ---").orDie *>
      ZIO.scoped {
        for {
          projection = Projection[UserProfile]("user_profiles")
                         .from("users")
                         .routeToSelf
                         .on[UserCreated]
                         .insert((e, ctx) => UserProfile(ctx.entityId, e.name, e.email))
                         .on[UserDeleted]
                         .delete

          engine <- TestEngine.make(projection)

          _ <- engine.append("user-1", UserCreated("Alice", "alice@example.com"))
          _ <- engine.append("user-2", UserCreated("Bob", "bob@example.com"))
          _ <- engine.append("user-3", UserCreated("Charlie", "charlie@example.com"))
          _ <- engine.append("user-2", UserDeleted())

          // A deleted row cannot be waited for, so the last event is a new user. Events are applied in order, so
          // once user-4 exists the deletion has been applied.
          _ <- engine.append("user-4", UserCreated("Dave", "dave@example.com"))
          _ <- pollUntil(engine.query(projection, "user-4"))

          u1 <- engine.query(projection, "user-1")
          u2 <- engine.query(projection, "user-2")
          u3 <- engine.query(projection, "user-3")

          _ <- Console.printLine(s"  user-1: ${show(u1)}").orDie
          _ <- Console.printLine(s"  user-2: ${show(u2)}").orDie
          _ <- Console.printLine(s"  user-3: ${show(u3)}").orDie
          _ <- Console.printLine("  ✓ Insert and delete: user-2 was inserted, then its row was removed").orDie
        } yield ()
      }

  def run: ZIO[Any, Throwable, Unit] = demo
}
