/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.projection

import java.sql.DriverManager
import java.time.Instant

import zio.*
import zio.stream.ZStream
import zio.test.*

import zio.blocks.schema.Schema
import zio.blocks.sql.*

object EventStoreSpec extends ZIOSpecDefault {

  // ---------------------------------------------------------------------------
  // Test event models
  // ---------------------------------------------------------------------------

  sealed trait TestEvent
  object TestEvent {
    case class Created(name: String)           extends TestEvent
    case class Updated(name: String, age: Int) extends TestEvent
    case object Deleted                        extends TestEvent

    implicit val schema: Schema[TestEvent] = Schema.derived[TestEvent]
  }

  sealed trait NumericEvent
  object NumericEvent {
    @eventTag(1) case class NumericCreated(id: String)             extends NumericEvent
    @eventTag(2) case class NumericUpdated(id: String, value: Int) extends NumericEvent
    case class NumericPlain(id: String)                            extends NumericEvent

    implicit val schema: Schema[NumericEvent] = Schema.derived[NumericEvent]
  }

  case class ComplexPayload(id: String, count: Int, flag: Boolean, maybe: Option[String])
  object ComplexPayload {
    implicit val schema: Schema[ComplexPayload] = Schema.derived[ComplexPayload]
  }

  sealed trait ComplexEvent
  object ComplexEvent {
    case class WithComplex(payload: ComplexPayload) extends ComplexEvent
    case class Simple(value: String)                extends ComplexEvent

    implicit val schema: Schema[ComplexEvent] = Schema.derived[ComplexEvent]
  }

  // ---------------------------------------------------------------------------
  // Helper to create fresh store with temp file
  // ---------------------------------------------------------------------------

  private def freshStore[E: Schema]: Task[(SQLiteEventStore[E], java.nio.file.Path)] =
    for {
      tmp   <- ZIO.attempt(java.nio.file.Files.createTempFile("eventstore", ".db"))
      _     <- ZIO.attempt(Class.forName("org.sqlite.JDBC"))
      url    = s"jdbc:sqlite:${tmp.toAbsolutePath.toString}"
      tx     = JdbcTransactor.fromUrl(url, SqlDialect.SQLite)
      hub   <- Hub.unbounded[EventEnvelope[E]]
      store <- SQLiteEventStore.makeWithHub[E](tx, hub)
    } yield (store, tmp)

  private def withStore[E: Schema](f: SQLiteEventStore[E] => Task[TestResult]): Task[TestResult] =
    freshStore[E].flatMap { case (store, tmp) =>
      f(store).ensuring(
        ZIO.succeed {
          try java.nio.file.Files.deleteIfExists(tmp)
          catch { case _: Throwable => () }
        }
      )
    }

  // ---------------------------------------------------------------------------
  // Spec
  // ---------------------------------------------------------------------------

  def spec: Spec[TestEnvironment, Any] = suite("EventStoreSpec")(
    test("append writes string tag derived from variant name") {
      withStore[TestEvent] { store =>
        for {
          seq <- store.append("e1", TestEvent.Created("Alice"))
          all <- store.readAll().runCollect
        } yield assertTrue(seq == 1L, all.head.tag == "Created", all.head.entityId == "e1")
      }
    },
    test("append returns monotonically increasing seq") {
      withStore[TestEvent] { store =>
        for {
          s1 <- store.append("e1", TestEvent.Created("A"))
          s2 <- store.append("e1", TestEvent.Updated("A", 1))
          s3 <- store.append("e2", TestEvent.Deleted)
        } yield assertTrue(s1 == 1L, s2 == 2L, s3 == 3L, s2 == s1 + 1, s3 == s2 + 1)
      }
    },
    test("readFrom afterSeq returns envelopes after seq ordered") {
      withStore[TestEvent] { store =>
        for {
          _   <- store.append("e1", TestEvent.Created("A"))
          _   <- store.append("e1", TestEvent.Updated("A", 10))
          _   <- store.append("e1", TestEvent.Deleted)
          all <- store.readFrom(1L).runCollect
        } yield assertTrue(all.size == 2, all.head.seq == 2L, all.last.seq == 3L, all.head.tag == "Updated")
      }
    },
    test("readAll returns all ordered by seq") {
      withStore[TestEvent] { store =>
        for {
          _   <- store.append("e1", TestEvent.Created("A"))
          _   <- store.append("e2", TestEvent.Created("B"))
          _   <- store.append("e1", TestEvent.Deleted)
          all <- store.readAll().runCollect
        } yield assertTrue(all.size == 3, all.map(_.seq) == Chunk(1L, 2L, 3L))
      }
    },
    test("selective read with single tag returns only matching") {
      withStore[TestEvent] { store =>
        for {
          _        <- store.append("e1", TestEvent.Created("A"))
          _        <- store.append("e1", TestEvent.Updated("A", 1))
          _        <- store.append("e1", TestEvent.Created("B"))
          filtered <- store.readAll(Set("Created")).runCollect
        } yield assertTrue(filtered.size == 2, filtered.forall(_.tag == "Created"))
      }
    },
    test("selective read with multiple tags returns union") {
      withStore[TestEvent] { store =>
        for {
          _        <- store.append("e1", TestEvent.Created("A"))
          _        <- store.append("e1", TestEvent.Updated("A", 1))
          _        <- store.append("e1", TestEvent.Deleted)
          filtered <- store.readAll(Set("Created", "Deleted")).runCollect
        } yield assertTrue(filtered.size == 2, filtered.map(_.tag).toSet == Set("Created", "Deleted"))
      }
    },
    test("empty tags set returns all") {
      withStore[TestEvent] { store =>
        for {
          _   <- store.append("e1", TestEvent.Created("A"))
          _   <- store.append("e1", TestEvent.Updated("A", 2))
          all <- store.readAll(Set.empty).runCollect
        } yield assertTrue(all.size == 2)
      }
    },
    test("empty store read returns empty") {
      withStore[TestEvent] { store =>
        for {
          all <- store.readAll().runCollect
        } yield assertTrue(all.isEmpty)
      }
    },
    test("readFrom beyond max returns empty") {
      withStore[TestEvent] { store =>
        for {
          _   <- store.append("e1", TestEvent.Created("A"))
          res <- store.readFrom(100L).runCollect
        } yield assertTrue(res.isEmpty)
      }
    },
    test("numeric tag via @eventTag stores numeric string") {
      withStore[NumericEvent] { store =>
        for {
          seq <- store.append("e1", NumericEvent.NumericCreated("x"))
          all <- store.readAll().runCollect
        } yield assertTrue(seq == 1L, all.head.tag == "1")
      }
    },
    test("numeric tag second value") {
      withStore[NumericEvent] { store =>
        for {
          _   <- store.append("e1", NumericEvent.NumericUpdated("x", 42))
          all <- store.readAll().runCollect
        } yield assertTrue(all.head.tag == "2")
      }
    },
    test("numeric selective read works with numeric string tags") {
      withStore[NumericEvent] { store =>
        for {
          _      <- store.append("e1", NumericEvent.NumericCreated("a"))
          _      <- store.append("e1", NumericEvent.NumericUpdated("a", 1))
          _      <- store.append("e1", NumericEvent.NumericPlain("a"))
          ones   <- store.readAll(Set("1")).runCollect
          twos   <- store.readAll(Set("2")).runCollect
          plains <- store.readAll(Set("NumericPlain")).runCollect
        } yield assertTrue(ones.size == 1, ones.head.tag == "1", twos.size == 1, twos.head.tag == "2", plains.size == 1)
      }
    },
    test("plain variant without annotation keeps string tag") {
      withStore[NumericEvent] { store =>
        for {
          _   <- store.append("e1", NumericEvent.NumericPlain("p"))
          all <- store.readAll().runCollect
        } yield assertTrue(all.head.tag == "NumericPlain")
      }
    },
    test("payload round-trip for Created") {
      withStore[TestEvent] { store =>
        val ev = TestEvent.Created("RoundTrip")
        for {
          _   <- store.append("e1", ev)
          all <- store.readAll().runCollect
        } yield assertTrue(all.head.event == ev)
      }
    },
    test("payload round-trip for Updated with int") {
      withStore[TestEvent] { store =>
        val ev = TestEvent.Updated("Bob", 99)
        for {
          _   <- store.append("e1", ev)
          all <- store.readAll().runCollect
        } yield assertTrue(all.head.event == ev)
      }
    },
    test("payload round-trip for Deleted case object") {
      withStore[TestEvent] { store =>
        val ev: TestEvent = TestEvent.Deleted
        for {
          _   <- store.append("e1", ev)
          all <- store.readAll().runCollect
        } yield assertTrue(all.head.event == ev)
      }
    },
    test("payload round-trip for complex payload") {
      withStore[ComplexEvent] { store =>
        val ev = ComplexEvent.WithComplex(ComplexPayload("id1", 42, flag = true, Some("hello")))
        for {
          _   <- store.append("e1", ev)
          all <- store.readAll().runCollect
        } yield assertTrue(all.head.event == ev)
      }
    },
    test("payload round-trip with None option") {
      withStore[ComplexEvent] { store =>
        val ev = ComplexEvent.WithComplex(ComplexPayload("id2", 0, flag = false, None))
        for {
          _   <- store.append("e1", ev)
          all <- store.readAll().runCollect
        } yield assertTrue(all.head.event == ev)
      }
    },
    test("Hub subscription receives appended events") {
      withStore[TestEvent] { store =>
        ZIO.scoped {
          for {
            hub      <- ZIO.succeed(store.subscribe)
            dequeue  <- hub.subscribe
            _        <- store.append("e1", TestEvent.Created("hubTest"))
            envelope <- dequeue.take
          } yield assertTrue(
            envelope.tag == "Created",
            envelope.entityId == "e1",
            envelope.event == TestEvent.Created("hubTest")
          )
        }
      }
    },
    test("Hub subscription receives multiple events in order") {
      withStore[TestEvent] { store =>
        ZIO.scoped {
          for {
            hub     <- ZIO.succeed(store.subscribe)
            dequeue <- hub.subscribe
            _       <- store.append("e1", TestEvent.Created("A"))
            _       <- store.append("e1", TestEvent.Updated("A", 1))
            e1      <- dequeue.take
            e2      <- dequeue.take
          } yield assertTrue(e1.seq == 1L, e2.seq == 2L, e1.tag == "Created", e2.tag == "Updated")
        }
      }
    },
    test("append stores entityId correctly for different entities") {
      withStore[TestEvent] { store =>
        for {
          _   <- store.append("entity-1", TestEvent.Created("A"))
          _   <- store.append("entity-2", TestEvent.Created("B"))
          all <- store.readAll().runCollect
        } yield assertTrue(
          all.find(_.seq == 1L).exists(_.entityId == "entity-1"),
          all.find(_.seq == 2L).exists(_.entityId == "entity-2")
        )
      }
    },
    test("timestamp is recent") {
      withStore[TestEvent] { store =>
        val before = Instant.now().minusSeconds(2)
        for {
          _   <- store.append("e1", TestEvent.Created("time"))
          all <- store.readAll().runCollect
        } yield assertTrue(
          all.head.timestamp.isAfter(before),
          all.head.timestamp.isBefore(Instant.now().plusSeconds(2))
        )
      }
    },
    test("ordering preserved after many appends") {
      withStore[TestEvent] { store =>
        for {
          _   <- ZIO.foreachDiscard(1 to 20)(i => store.append(s"e$i", TestEvent.Created(s"name$i")))
          all <- store.readAll().runCollect
        } yield assertTrue(
          all.size == 20,
          all.map(_.seq) == Chunk.fromIterable(1L to 20L),
          all.map(_.seq).sorted == all.map(_.seq)
        )
      }
    },
    test("selective read uses index via EXPLAIN QUERY PLAN") {
      withStore[TestEvent] { store =>
        for {
          _    <- store.append("e1", TestEvent.Created("A"))
          _    <- store.append("e1", TestEvent.Updated("A", 1))
          plan <- ZIO.attemptBlocking(store.explainQueryPlan(0L, Set("Created")))
        } yield assertTrue(plan.exists(_.contains("idx_events_tag_seq")))
      }
    },
    test("readAll with selective tags uses index") {
      withStore[TestEvent] { store =>
        for {
          _    <- store.append("e1", TestEvent.Created("A"))
          plan <- ZIO.attemptBlocking(store.explainQueryPlan(0L, Set("Created", "Updated")))
        } yield assertTrue(plan.exists(_.contains("idx_events_tag_seq")))
      }
    },
    test("readFrom with afterSeq and tags filters correctly") {
      withStore[TestEvent] { store =>
        for {
          _   <- store.append("e1", TestEvent.Created("A"))    // seq 1
          _   <- store.append("e1", TestEvent.Updated("A", 1)) // seq 2
          _   <- store.append("e1", TestEvent.Created("B"))    // seq 3
          res <- store.readFrom(1L, Set("Created")).runCollect
        } yield assertTrue(res.size == 1, res.head.seq == 3L, res.head.tag == "Created")
      }
    },
    test("append same entityId multiple events seq increments independently") {
      withStore[TestEvent] { store =>
        for {
          s1  <- store.append("same", TestEvent.Created("A"))
          s2  <- store.append("same", TestEvent.Updated("A", 1))
          s3  <- store.append("same", TestEvent.Updated("A", 2))
          all <- store.readAll().runCollect
        } yield assertTrue(s1 == 1L, s2 == 2L, s3 == 3L, all.forall(_.entityId == "same"))
      }
    },
    test("string tags default not numeric") {
      withStore[TestEvent] { store =>
        for {
          _   <- store.append("e1", TestEvent.Created("x"))
          _   <- store.append("e1", TestEvent.Updated("x", 5))
          all <- store.readAll().runCollect
        } yield assertTrue(
          all.find(_.seq == 1L).exists(_.tag == "Created"),
          all.find(_.seq == 2L).exists(_.tag == "Updated")
        )
      }
    },
    test("readFrom with empty tags after seq returns all after") {
      withStore[TestEvent] { store =>
        for {
          _   <- store.append("e1", TestEvent.Created("A"))
          _   <- store.append("e1", TestEvent.Created("B"))
          _   <- store.append("e1", TestEvent.Created("C"))
          res <- store.readFrom(1L, Set.empty).runCollect
        } yield assertTrue(res.size == 2, res.head.seq == 2L, res.last.seq == 3L)
      }
    },
    test("selective read empty result when tag not present") {
      withStore[TestEvent] { store =>
        for {
          _   <- store.append("e1", TestEvent.Created("A"))
          res <- store.readAll(Set("NonExistent")).runCollect
        } yield assertTrue(res.isEmpty)
      }
    }
  )
}
