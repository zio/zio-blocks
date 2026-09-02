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

import java.nio.charset.StandardCharsets
import java.time.Instant

import zio.*
import zio.test.*

import zio.blocks.schema.Schema
import zio.blocks.schema.migration.Migration
import zio.blocks.sql.{DbCon, JdbcTransactor, SqlDialect}

object EventStoreBranchSpec extends ZIOSpecDefault {

  // ---------------------------------------------------------------------------
  // Models
  // ---------------------------------------------------------------------------

  sealed trait BranchEvent
  object BranchEvent {
    case class Created(name: String)           extends BranchEvent
    case class Updated(name: String, age: Int) extends BranchEvent
    case object Deleted                        extends BranchEvent
    implicit val schema: Schema[BranchEvent] = Schema.derived[BranchEvent]
  }

  case class PlainEvent(value: String, count: Int)
  object PlainEvent {
    implicit val schema: Schema[PlainEvent] = Schema.derived[PlainEvent]
  }

  sealed trait MigratableEvent
  object MigratableEvent {
    case class CurrentItem(id: String) extends MigratableEvent
    case class OtherItem(id: String)   extends MigratableEvent
    implicit val schema: Schema[MigratableEvent] = Schema.derived[MigratableEvent]
  }

  // ---------------------------------------------------------------------------
  // Helpers (use Hub.bounded per task, JdbcTransactor as in EventStoreSpec)
  // ---------------------------------------------------------------------------

  private def freshStore[E: Schema]: Task[(SQLiteEventStore[E], zio.blocks.sql.Transactor, java.nio.file.Path)] =
    for {
      tmp   <- ZIO.attempt(java.nio.file.Files.createTempFile("eventstore-branch", ".db"))
      _     <- ZIO.attempt(Class.forName("org.sqlite.JDBC"))
      url    = s"jdbc:sqlite:${tmp.toAbsolutePath.toString}"
      tx     = JdbcTransactor.fromUrl(url, SqlDialect.SQLite)
      hub   <- Hub.bounded[EventEnvelope[E]](64)
      store <- SQLiteEventStore.makeWithHub[E](tx, hub)
    } yield (store, tx, tmp)

  private def freshStoreWithMigration[E: Schema](
    migration: Migration[E, E]
  ): Task[(SQLiteEventStore[E], zio.blocks.sql.Transactor, java.nio.file.Path)] =
    for {
      tmp   <- ZIO.attempt(java.nio.file.Files.createTempFile("eventstore-branch-mig", ".db"))
      _     <- ZIO.attempt(Class.forName("org.sqlite.JDBC"))
      url    = s"jdbc:sqlite:${tmp.toAbsolutePath.toString}"
      tx     = JdbcTransactor.fromUrl(url, SqlDialect.SQLite)
      hub   <- Hub.bounded[EventEnvelope[E]](64)
      info   = TagResolver.resolve[E](migration)
      store <- SQLiteEventStore.makeWithHub[E](tx, hub, info)
    } yield (store, tx, tmp)

  private def withStore[E: Schema](
    f: (SQLiteEventStore[E], zio.blocks.sql.Transactor) => Task[TestResult]
  ): Task[TestResult] =
    freshStore[E].flatMap { case (store, tx, tmp) =>
      f(store, tx).ensuring(
        ZIO.succeed {
          try java.nio.file.Files.deleteIfExists(tmp)
          catch { case _: Throwable => () }
          try java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(tmp.toString + "-wal"))
          catch { case _: Throwable => () }
          try java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(tmp.toString + "-shm"))
          catch { case _: Throwable => () }
        }
      )
    }

  private def withMigratedStore[E: Schema](migration: Migration[E, E])(
    f: (SQLiteEventStore[E], zio.blocks.sql.Transactor) => Task[TestResult]
  ): Task[TestResult] =
    freshStoreWithMigration[E](migration).flatMap { case (store, tx, tmp) =>
      f(store, tx).ensuring(
        ZIO.succeed {
          try java.nio.file.Files.deleteIfExists(tmp)
          catch { case _: Throwable => () }
          try java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(tmp.toString + "-wal"))
          catch { case _: Throwable => () }
          try java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(tmp.toString + "-shm"))
          catch { case _: Throwable => () }
        }
      )
    }

  private def rawInsert(
    tx: zio.blocks.sql.Transactor,
    tag: String,
    payloadJson: String,
    entityId: String = "e1"
  ): Task[Unit] =
    ZIO.attemptBlocking {
      tx.connect {
        val con  = summon[DbCon]
        val conn = con.connection
        val ps   = conn.prepareStatement("INSERT INTO events (tag, payload, ts, entityId) VALUES (?, ?, ?, ?)")
        try {
          val pw = ps.paramWriter
          pw.setString(1, tag)
          pw.setBytes(2, payloadJson.getBytes(StandardCharsets.UTF_8))
          pw.setLong(3, Instant.now().toEpochMilli)
          pw.setString(4, entityId)
          ps.executeUpdate()
          ()
        } finally ps.close()
      }
    }

  // ---------------------------------------------------------------------------
  // Spec
  // ---------------------------------------------------------------------------

  def spec: Spec[TestEnvironment, Any] = suite("EventStoreBranchSpec")(
    test("buildSelect tagged read via readFrom with non-empty Set(Created) filters correctly") {
      withStore[BranchEvent] { (store, _) =>
        for {
          _        <- store.append("e1", BranchEvent.Created("A"))
          _        <- store.append("e1", BranchEvent.Updated("A", 10))
          _        <- store.append("e1", BranchEvent.Created("B"))
          _        <- store.append("e1", BranchEvent.Deleted)
          filtered <- store.readFrom(0L, Set("Created")).runCollect
        } yield assertTrue(
          filtered.size == 2,
          filtered.forall(_.tag == "Created"),
          filtered.map(_.seq) == zio.Chunk(1L, 3L),
          filtered.forall(_.event.isInstanceOf[BranchEvent.Created])
        )
      }
    },
    test("deriveTag fallback for non-variant plain case class uses typeId name") {
      withStore[PlainEvent] { (store, _) =>
        val ev = PlainEvent("hello", 42)
        for {
          seq <- store.append("plain-1", ev)
          all <- store.readAll().runCollect
        } yield assertTrue(
          seq == 1L,
          all.size == 1,
          all.head.tag == "PlainEvent",
          all.head.event == ev,
          all.head.entityId == "plain-1"
        )
      }
    },
    test("decodePayload oldTag migration via RenameCase reads old row as new tag and event") {
      val migration =
        Migration.newBuilder[MigratableEvent, MigratableEvent].renameCase("LegacyItem", "CurrentItem").build
      withMigratedStore[MigratableEvent](migration) { (store, tx) =>
        for {
          _   <- rawInsert(tx, "LegacyItem", """{"LegacyItem":{"id":"m1"}}""", "e1")
          _   <- rawInsert(tx, "CurrentItem", """{"CurrentItem":{"id":"m2"}}""", "e2")
          all <- store.readAll().runCollect
        } yield assertTrue(
          all.size == 2,
          all.forall(_.tag == "CurrentItem"),
          all.map(_.event).toSet == Set(MigratableEvent.CurrentItem("m1"), MigratableEvent.CurrentItem("m2")),
          all.forall(_.seq >= 1L)
        )
      }
    },
    test("empty stream termination via readFrom Long.MaxValue returns empty") {
      withStore[BranchEvent] { (store, _) =>
        for {
          _         <- store.append("e1", BranchEvent.Created("A"))
          _         <- store.append("e1", BranchEvent.Updated("A", 1))
          empty     <- store.readFrom(Long.MaxValue).runCollect
          alsoEmpty <- store.readFrom(999999L, Set("Created")).runCollect
        } yield assertTrue(empty.isEmpty, alsoEmpty.isEmpty)
      }
    },
    test("corrupt payload error channel fails with decode error") {
      withStore[BranchEvent] { (store, tx) =>
        for {
          _      <- rawInsert(tx, "Created", "not-json-at-all", "e1")
          result <- store.readAll().runCollect.either
        } yield assertTrue(
          result.isLeft,
          result.left.exists(_.getMessage.contains("Failed to decode"))
        )
      }
    }
  )
}
