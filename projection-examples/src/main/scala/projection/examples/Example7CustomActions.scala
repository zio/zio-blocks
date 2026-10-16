/*
 * Example 7 of 11: custom actions.
 *
 * Builds on Example1PerEntity. Read it first.
 *
 * `insert`, `delete` and the field updates each produce one fixed action. When
 * what should happen depends on the event, `custom` lets the handler look at the
 * event and return the action itself. This example uses four of the actions a
 * handler can return: `Upsert`, `Noop`, `Delete` and `Update`.
 *
 *   - `ProfileImported` has a `draft` flag. A draft is ignored with `Noop`, so no
 *     row is written. Anything else is written with `Upsert`, which creates the
 *     row or replaces it if it exists, so importing the same user twice is safe.
 *   - `AccountClosed` has an `eraseData` flag. When it is set, the row is removed
 *     with `Delete`. Otherwise `Update` changes only the `status` column and keeps
 *     the rest of the row.
 *
 * It shows:
 *   - `custom`, which returns a `ProjectionAction` chosen from the event
 *   - `Upsert` replacing a row, `Noop` writing nothing, `Delete` removing a row
 *   - `Update` with a `FieldUpdate.Set`, changing one column of an existing row
 *   - running it in memory with `TestEngine`
 *
 * Run it from the repository root with:
 *   sbt "projection-examples/runMain projection.examples.Example7CustomActions"
 */

package projection.examples

import zio.*
import zio.blocks.chunk.Chunk
import zio.blocks.projection.*
import zio.blocks.projection.testing.TestEngine
import zio.blocks.schema.{Modifier, Schema}

import Polling.pollUntil

object Example7CustomActions extends ZIOAppDefault {

  case class ProfileImported(name: String, email: String, draft: Boolean)
  object ProfileImported {
    implicit val schema: Schema[ProfileImported] = Schema.derived[ProfileImported]
  }

  case class AccountClosed(eraseData: Boolean)
  object AccountClosed {
    implicit val schema: Schema[AccountClosed] = Schema.derived[AccountClosed]
  }

  case class UserProfile(@Modifier.id id: String, name: String, email: String, status: String)
  object UserProfile {
    implicit val schema: Schema[UserProfile]         = Schema.derived[UserProfile]
    implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived[UserProfile]
  }

  private def show(profile: Option[UserProfile]): String =
    profile.map(p => s"${p.name} <${p.email}>, ${p.status}").getOrElse("no row")

  def demo: ZIO[Any, Throwable, Unit] =
    Console.printLine("\n--- Example 7: Custom Actions (Upsert, Noop, Delete, Update) ---").orDie *>
      ZIO.scoped {
        for {
          projection = Projection[UserProfile]("user_profiles")
                         .from("users")
                         .routeToSelf
                         .on[ProfileImported]
                         .custom { (e, ctx) =>
                           if (e.draft) ProjectionAction.Noop
                           else ProjectionAction.Upsert(UserProfile(ctx.entityId, e.name, e.email, "active"))
                         }
                         .on[AccountClosed]
                         .custom { (e, _) =>
                           if (e.eraseData) ProjectionAction.Delete
                           else ProjectionAction.Update(Chunk(FieldUpdate.Set("status", "closed")))
                         }

          engine <- TestEngine.make(projection)

          _ <- engine.append("user-1", ProfileImported("Alice", "alice@example.com", draft = false))
          _ <- engine.append("user-1", ProfileImported("Alice B.", "alice@example.com", draft = false))
          _ <- engine.append("user-2", ProfileImported("Bob", "bob@example.com", draft = true))
          _ <- engine.append("user-3", ProfileImported("Carol", "carol@example.com", draft = false))
          _ <- engine.append("user-3", AccountClosed(eraseData = false))
          _ <- engine.append("user-4", ProfileImported("Dave", "dave@example.com", draft = false))
          _ <- engine.append("user-4", AccountClosed(eraseData = true))
          // Two of the users end up without a row, so there is nothing to wait for on them. Events are applied in
          // order, so once this last user exists every earlier event has been applied.
          _ <- engine.append("user-5", ProfileImported("Erin", "erin@example.com", draft = false))

          _  <- pollUntil(engine.query(projection, "user-5"))
          u1 <- engine.query(projection, "user-1")
          u2 <- engine.query(projection, "user-2")
          u3 <- engine.query(projection, "user-3")
          u4 <- engine.query(projection, "user-4")

          _ <- Console.printLine(s"  user-1: ${show(u1)}").orDie
          _ <- Console.printLine(s"  user-2: ${show(u2)}").orDie
          _ <- Console.printLine(s"  user-3: ${show(u3)}").orDie
          _ <- Console.printLine(s"  user-4: ${show(u4)}").orDie
          _ <- Console.printLine("  ✓ Custom: the handler chose Upsert, Noop, Update or Delete for each event").orDie
        } yield ()
      }

  def run: ZIO[Any, Throwable, Unit] = demo
}
