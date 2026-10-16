/*
 * Example 8 of 11: field updates.
 *
 * Builds on Example1PerEntity. Read it first.
 *
 * `insert` writes a whole row. When an event changes only one column, a field
 * update sets that column and leaves the rest of the row as it is. There are
 * three ways to name the column:
 *   - `update(_.firstName)`, which picks the field with a selector. The name is
 *     checked by the compiler, and the macro turns it into the column name
 *     `first_name`, so a typo does not compile
 *   - `updateWithField("email", ...)`, which takes the column name as a string.
 *     It must already be the column name, so `snake_case`, and it is not checked
 *     until the event is applied
 *   - `updateField("status")(...)`, which is the same as `updateWithField` with
 *     the function in a second parameter list
 *
 * Each handler computes the new value from the event, and may also use the
 * `ProjectionContext`.
 *
 * It shows:
 *   - the three ways to update one column
 *   - an update changing one column and keeping the others
 *   - running it in memory with `TestEngine`
 *
 * Run it from the repository root with:
 *   sbt "projection-examples/runMain projection.examples.Example8FieldUpdates"
 */

package projection.examples

import zio.*
import zio.blocks.projection.*
import zio.blocks.projection.testing.TestEngine
import zio.blocks.schema.{Modifier, Schema}

import Polling.pollUntil

object Example8FieldUpdates extends ZIOAppDefault {

  case class UserRegistered(firstName: String, email: String)
  object UserRegistered {
    implicit val schema: Schema[UserRegistered] = Schema.derived[UserRegistered]
  }

  case class UserRenamed(newName: String)
  object UserRenamed {
    implicit val schema: Schema[UserRenamed] = Schema.derived[UserRenamed]
  }

  case class EmailChanged(newEmail: String)
  object EmailChanged {
    implicit val schema: Schema[EmailChanged] = Schema.derived[EmailChanged]
  }

  case class UserVerified()
  object UserVerified {
    implicit val schema: Schema[UserVerified] = Schema.derived[UserVerified]
  }

  case class UserProfile(@Modifier.id id: String, firstName: String, email: String, status: String)
  object UserProfile {
    implicit val schema: Schema[UserProfile]         = Schema.derived[UserProfile]
    implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived[UserProfile]
  }

  private def show(profile: Option[UserProfile]): String =
    profile.map(p => s"${p.firstName} <${p.email}>, ${p.status}").getOrElse("not found")

  def demo: ZIO[Any, Throwable, Unit] =
    Console.printLine("\n--- Example 8: Field Updates (update, updateWithField, updateField) ---").orDie *>
      ZIO.scoped {
        for {
          projection = Projection[UserProfile]("user_profiles")
                         .from("users")
                         .routeToSelf
                         .on[UserRegistered]
                         .insert((e, ctx) => UserProfile(ctx.entityId, e.firstName, e.email, "pending"))
                         .on[UserRenamed]
                         .update(_.firstName)((e, _) => e.newName)
                         .on[EmailChanged]
                         .updateWithField("email", (e, _) => e.newEmail)
                         .on[UserVerified]
                         .updateField("status")((_, _) => "verified")

          engine <- TestEngine.make(projection)

          _ <- engine.append("user-1", UserRegistered("Alice", "alice@example.com"))
          _ <- engine.append("user-2", UserRegistered("Bob", "bob@example.com"))
          _ <- engine.append("user-1", UserRenamed("Alicia"))
          _ <- engine.append("user-1", EmailChanged("alicia@example.com"))
          _ <- engine.append("user-1", UserVerified())
          _ <- engine.append("user-2", UserRenamed("Robert"))

          // Events are applied in order, so once the last one is visible every earlier event has been applied.
          u2 <- pollUntil(engine.query(projection, "user-2").map(_.filter(_.firstName == "Robert")))
          u1 <- engine.query(projection, "user-1")

          _ <- Console.printLine(s"  user-1: ${show(u1)}").orDie
          _ <- Console.printLine(s"  user-2: ${show(u2)}").orDie
          _ <- Console.printLine("  ✓ Field updates: each event changed one column and kept the others").orDie
        } yield ()
      }

  def run: ZIO[Any, Throwable, Unit] = demo
}
