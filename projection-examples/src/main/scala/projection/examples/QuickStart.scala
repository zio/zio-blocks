/*
 * The program of the Quick Start in the documentation.
 *
 * It defines the events and the read model, declares a projection, and runs it
 * on SQLite files, the way an application does. The events go to
 * `events/users.db` and the read model to `users/userProfiles.db`, both relative
 * to the working directory. The stores do not create folders, so the program
 * creates the two folders first.
 *
 * The files stay on disk after the program ends, so running it again continues
 * after the last event it processed.
 *
 * Run it from the repository root with:
 *   sbt "projection-examples/runMain projection.examples.QuickStart"
 */

package projection.examples

import zio.*
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

import java.nio.file.{Files, Paths}

object QuickStart extends ZIOAppDefault {

  // The events, the read model and the projection
  case class UserCreated(name: String, email: String)
  object UserCreated {
    implicit val schema: Schema[UserCreated] = Schema.derived[UserCreated]
  }

  // The ID field is `userId`, so the derived folder is `users`. A field named `id` would give `ids`.
  case class UserProfile(@Modifier.id userId: String, name: String, email: String)
  object UserProfile {
    implicit val schema: Schema[UserProfile]         = Schema.derived[UserProfile]
    implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived[UserProfile]
  }

  val projection: Projection[UserProfile] = Projection[UserProfile]("userProfiles")
    .from("users")
    .routeToSelf
    .on[UserCreated]
    .insert((e, ctx) => UserProfile(ctx.entityId, e.name, e.email))

  // Wire the projection to SQLite files
  val program: ZIO[Scope, Throwable, Option[UserProfile]] = for {
    _      <- ZIO.attemptBlocking(List("events", "users").foreach(d => Files.createDirectories(Paths.get(d))))
    cache  <- TransactorCache.make()
    tx     <- cache.get("events/users.db")
    users  <- SQLiteEventStore.make[UserCreated](tx)
    // "users" is the source name given to `from`; the projection store becomes users/userProfiles.db
    engine <- ProjectionEngine.makeWithStoresVarargs(cache, Map("users" -> users))(projection)
    _      <- engine.start
    _      <- users.append("user-1", UserCreated("Alice", "alice@example.com"))
    // The engine applies the event in the background, so ask until the row is there
    alice  <- engine.query(projection, "user-1").repeatUntil(_.isDefined)
  } yield alice

  def run: ZIO[Any, Throwable, Unit] =
    ZIO.scoped(program).flatMap { alice =>
      Console.printLine(s"user-1: ${alice.map(u => s"${u.name} <${u.email}>").getOrElse("not found")}")
    }
}
