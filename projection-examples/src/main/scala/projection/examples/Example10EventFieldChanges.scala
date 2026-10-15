/*
 * Example 10 of 10: changing the fields of an event.
 *
 * Builds on Example5TagRename. Read it first.
 *
 * Stored events are never rewritten. When a case of the event type gains, loses
 * or changes a field, the events already stored keep their old JSON, and the
 * event store decodes that JSON with the current case class. No migration is
 * applied: the JSON codec decides what an old event becomes.
 *   - a field that was removed is ignored
 *   - a field that was added takes its default value, `None` for an `Option`, or
 *     an empty collection
 *   - a field that was added with none of these makes the event fail to decode
 *
 * This example stores two `AccountOpened` events with the first version of the
 * event type, in `.projection-examples/example10/events/accounts.db`, then reads
 * them back with two newer versions:
 *   1. V1 has `owner` and `plan`.
 *   2. V2 drops `plan` and adds `currency` with a default, `referrer` as an
 *      `Option` and `tags` as a `List`. The old events read back with `EUR`,
 *      `None` and an empty list.
 *   3. V3 adds `country` with no default. Reading fails with
 *      `missing required field "country"`.
 *
 * It shows:
 *   - that old events are decoded with the current case class, without a
 *     migration
 *   - which added fields are filled in, and which make an old event unreadable
 *
 * Run it from the repository root with:
 *   sbt "projection-examples/runMain projection.examples.Example10EventFieldChanges"
 */

package projection.examples

import zio.*
import zio.blocks.projection.*
import zio.blocks.schema.Schema

object Example10EventFieldChanges extends ZIOAppDefault {

  object V1 {
    sealed trait AccountEvent
    object AccountEvent {
      case class AccountOpened(owner: String, plan: String) extends AccountEvent
      implicit val schema: Schema[AccountEvent] = Schema.derived[AccountEvent]
    }
  }

  object V2 {
    sealed trait AccountEvent
    object AccountEvent {
      case class AccountOpened(owner: String, currency: String = "EUR", referrer: Option[String], tags: List[String])
          extends AccountEvent
      implicit val schema: Schema[AccountEvent] = Schema.derived[AccountEvent]
    }
  }

  object V3 {
    sealed trait AccountEvent
    object AccountEvent {
      case class AccountOpened(owner: String, country: String) extends AccountEvent
      implicit val schema: Schema[AccountEvent] = Schema.derived[AccountEvent]
    }
  }

  private val eventsFile: String = Sqlite.path("example10/events/accounts.db")

  // Opens the event store with the given version of the event type and reads every event.
  private def readAll[E: Schema]: Task[Chunk[EventEnvelope[E]]] =
    ZIO.scoped {
      for {
        cache  <- TransactorCache.make()
        events <- Sqlite.eventStore[E](cache, eventsFile)
        all    <- events.readAll().runCollect
      } yield all
    }

  def demo: ZIO[Any, Throwable, Unit] =
    Console.printLine("\n--- Example 10: Changing the Fields of an Event (AccountOpened V1 -> V2 -> V3) ---").orDie *>
      ZIO.scoped {
        for {
          _ <- Sqlite.folders(Sqlite.path("example10/events"))

          // 1. The first version stores two events.
          _ <- ZIO.scoped {
                 for {
                   cache <- TransactorCache.make()
                   v1    <- Sqlite.eventStore[V1.AccountEvent](cache, eventsFile)
                   _     <- v1.append("acc-1", V1.AccountEvent.AccountOpened("alice", "basic"))
                   _     <- v1.append("acc-2", V1.AccountEvent.AccountOpened("bob", "pro"))
                 } yield ()
               }

          // 2. A newer version reads them: `plan` is ignored, and the new fields are filled in.
          v2 <- readAll[V2.AccountEvent]
          _  <- ZIO.foreachDiscard(v2)(e => Console.printLine(s"  V2 reads seq ${e.seq}: ${e.event}").orDie)

          // 3. A version with a new required field cannot read them.
          v3 <- readAll[V3.AccountEvent].either
          _  <- v3 match {
                 case Right(events) => Console.printLine(s"  V3 reads ${events.size} events").orDie
                 case Left(error)   => Console.printLine(s"  V3 fails: ${error.getMessage}").orDie
               }

          _ <- Console
                 .printLine("  ✓ Event fields: old events are read with the current case class, without a migration")
                 .orDie
        } yield ()
      }

  def run: ZIO[Any, Throwable, Unit] = demo
}
