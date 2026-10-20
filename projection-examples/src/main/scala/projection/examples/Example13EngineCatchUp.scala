/*
 * Example 13 of 13: catch-up in `start`.
 *
 * Read Example1QuickStart first.
 *
 * The program of the Usage section of the `ProjectionEngine` page in the
 * documentation.
 *
 * `ProjectionEngine#start` first applies the events that are already stored, a
 * step called catch-up, and returns only when it is done. Only then does the
 * engine follow new events, on a background fiber. So the events appended here
 * before `start` can be read right after it, without polling. After each event
 * it applies, the engine saves the event's sequence number in the projection's
 * store. That number, the watermark, is where it resumes on the next start.
 *
 * It shows:
 *   - opening an event store and a projection store, and giving them to
 *     `ProjectionEngine.makeWithStores`
 *   - events appended before `start`, applied by catch-up and read right after
 *     it
 *   - the watermark, read with `getLastProcessedSeq`
 *
 * This example runs on real SQLite files: the events are kept in
 * `.projection-examples/example13/events/users.db`, and the projection in
 * `.projection-examples/example13/users/user_profiles.db`.
 *
 * Run it from the repository root with:
 *   sbt "projection-examples/runMain projection.examples.Example13EngineCatchUp"
 */

package projection.examples

import zio.*
import zio.blocks.projection.*
import zio.blocks.schema.{Modifier, Schema}

object Example13EngineCatchUp extends ZIOAppDefault {

  // The event. The event store holds this one type.
  case class UserCreated(name: String, email: String)
  object UserCreated {
    implicit val schema: Schema[UserCreated] = Schema.derived[UserCreated]
  }

  // The read model, one row per user. `@Modifier.id` marks the ID field, and `EntityPath.derived` finds it.
  case class UserProfile(@Modifier.id userId: String, name: String, email: String)
  object UserProfile {
    implicit val schema: Schema[UserProfile]         = Schema.derived[UserProfile]
    implicit val entityPath: EntityPath[UserProfile] = EntityPath.derived[UserProfile]
  }

  // The projection: each `UserCreated` inserts the row of the user it was recorded under
  val projection: Projection[UserProfile] = Projection[UserProfile]("user_profiles")
    .from("users")
    .routeToSelf
    .on[UserCreated]
    .insert((e, ctx) => UserProfile(ctx.entityId, e.name, e.email))

  private def show(row: Option[UserProfile]): String =
    row.map(u => s"${u.name} <${u.email}>").getOrElse("not found")

  def demo: ZIO[Any, Throwable, Unit] =
    Console.printLine("\n--- Example 13: Catch-up in start ---").orDie *>
      ZIO.scoped {
        for {
          _        <- Sqlite.folders(Sqlite.path("example13/events"), Sqlite.path("example13/users"))
          cache    <- TransactorCache.make()
          tx       <- cache.get(Sqlite.path("example13/events/users.db"))
          users    <- SQLiteEventStore.make[UserCreated](tx)
          profiles <- SQLiteProjectionStore.make[UserProfile](Sqlite.path("example13/users/user_profiles.db"), cache)
          engine   <- ProjectionEngine.makeWithStores(
                      specs = List(projection),
                      stores = Map("user_profiles" -> profiles), // projection name -> its primary store
                      eventStores = Map("users" -> users), // source name given to `from` -> its event store
                      cache = cache
                    )
          // Appended before `start`, so catch-up applies them
          _ <- users.append("user-1", UserCreated("Alice", "alice@example.com"))
          _ <- users.append("user-2", UserCreated("Bob", "bob@example.com"))
          // `start` returns only after catch-up, so both rows are there right away, with no polling
          _     <- engine.start
          alice <- engine.query(projection, "user-1")
          bob   <- engine.query(projection, "user-2")
          seq   <- engine.getLastProcessedSeq(projection)
          _     <- Console.printLine(s"  user-1: ${show(alice)}").orDie
          _     <- Console.printLine(s"  user-2: ${show(bob)}").orDie
          _     <- Console.printLine(s"  Watermark: $seq").orDie
        } yield ()
      }

  def run: ZIO[Any, Throwable, Unit] = demo
}
