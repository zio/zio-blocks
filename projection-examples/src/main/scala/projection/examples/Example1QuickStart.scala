/*
 * Example 1 of 12: the quick start.
 *
 * Start here. Next: Example2PerEntity.
 *
 * The program of the Quick Start in the documentation.
 *
 * It defines the events and the read model, declares a projection, and runs it
 * on SQLite files, the way an application does. Two kinds of event are recorded
 * for each user: `UserCreated` inserts the user's row, and `EmailChanged`
 * updates one column of it, so the read model follows the events as they come.
 *
 * The events go to `events/users.db` and the read model to
 * `users/user_profiles.db`, both relative to the working directory. The stores do
 * not create folders, so the program creates the two folders first. The files
 * stay on disk after the program ends, so running it again continues after the
 * last event it processed.
 *
 * Run it from the repository root with:
 *   sbt "projection-examples/runMain projection.examples.Example1QuickStart"
 */

package projection.examples

import zio.*
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

import java.nio.file.{Files, Paths}

object Example1QuickStart extends ZIOAppDefault {

  // The events. They share a sealed trait, so that one event store holds both kinds.
  sealed trait UserEvent
  object UserEvent {
    case class UserCreated(name: String, email: String) extends UserEvent
    object UserCreated {
      implicit val schema: Schema[UserCreated] = Schema.derived[UserCreated]
    }
    case class EmailChanged(newEmail: String) extends UserEvent
    object EmailChanged {
      implicit val schema: Schema[EmailChanged] = Schema.derived[EmailChanged]
    }
    implicit val schema: Schema[UserEvent] = Schema.derived[UserEvent]
  }
  import UserEvent.*

  // The read model. The ID field is `userId`, so the derived folder is `users`, the same folder the store is opened in below.
  case class UserProfile(@Modifier.id userId: String, name: String, email: String)
  object UserProfile {
    implicit val schema: Schema[UserProfile]         = Schema.derived[UserProfile]
    implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived[UserProfile]
  }

  // The projection: what each event does to the row of the user it was recorded under
  val projection: Projection[UserProfile] = Projection[UserProfile]("user_profiles")
    .from("users")
    .routeToSelf
    .on[UserCreated]
    .insert((e, ctx) => UserProfile(ctx.entityId, e.name, e.email))
    .on[EmailChanged]
    .updateWithField("email", (e, _) => e.newEmail)

  // Wire the projection to SQLite files
  val program: ZIO[Scope, Throwable, List[(String, Option[UserProfile])]] = for {
    _        <- ZIO.attemptBlocking(List("events", "users").foreach(d => Files.createDirectories(Paths.get(d))))
    cache    <- TransactorCache.make()
    tx       <- cache.get("events/users.db")
    users    <- SQLiteEventStore.make[UserEvent](tx)
    profiles <- SQLiteProjectionStore.make[UserProfile]("users/user_profiles.db", cache)
    engine   <- ProjectionEngine.makeWithStores(
                specs = List(projection),
                stores = Map("user_profiles" -> profiles), // projection name -> its store
                eventStores = Map("users" -> users), // source name given to `from` -> its event store
                cache = cache
              )
    _ <- engine.start
    _ <- users.append("user-1", UserCreated("Alice", "alice@example.com"))
    _ <- users.append("user-2", UserCreated("Bob", "bob@example.com"))
    _ <- users.append("user-3", UserCreated("Charlie", "charlie@example.com"))
    _ <- users.append("user-1", EmailChanged("alice@zio.dev"))
    // The engine applies the events in the background, in order, so once the last one shows all of them have been applied
    _    <- engine.query(projection, "user-1").repeatUntil(_.exists(_.email == "alice@zio.dev"))
    rows <- ZIO.foreach(List("user-1", "user-2", "user-3"))(id => engine.query(projection, id).map(id -> _))
  } yield rows

  def run: ZIO[Any, Throwable, Unit] =
    ZIO.scoped(program).flatMap { rows =>
      ZIO.foreachDiscard(rows) { case (id, row) =>
        Console.printLine(s"$id: ${row.map(u => s"${u.name} <${u.email}>").getOrElse("not found")}")
      }
    }
}
