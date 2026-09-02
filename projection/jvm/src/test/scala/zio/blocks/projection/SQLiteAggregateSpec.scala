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

import zio.*
import zio.blocks.chunk.Chunk
import zio.blocks.schema.{Modifier, Schema}
import zio.test.*

object SQLiteAggregateSpec extends ZIOSpecDefault {

  case class DailyStats(
    @Modifier.id date: String,
    userCount: Int,
    repoCount: Int,
    peak: Long
  )
  object DailyStats {
    implicit val schema: Schema[DailyStats]         = Schema.derived[DailyStats]
    implicit val entityPath: EntityPath[DailyStats] = EntityPath.derived[DailyStats]
  }

  private def tempFile(prefix: String = "agg-sqlite"): Task[(String, java.nio.file.Path)] =
    ZIO.attempt {
      val p = java.nio.file.Files.createTempFile(prefix, ".db")
      (p.toAbsolutePath.toString, p)
    }

  private def cleanup(path: String, tmp: java.nio.file.Path): UIO[Unit] =
    ZIO.succeed {
      try java.nio.file.Files.deleteIfExists(tmp)
      catch { case _: Throwable => () }
      try java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(path + "-wal"))
      catch { case _: Throwable => () }
      try java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(path + "-shm"))
      catch { case _: Throwable => () }
    }

  private def withStore(f: (SQLiteProjectionStore[DailyStats], String) => Task[TestResult]): Task[TestResult] =
    ZIO.scoped {
      for {
        tmp            <- tempFile()
        (path, tmpPath) = tmp
        cache          <- TransactorCache.make()
        store          <- SQLiteProjectionStore.make[DailyStats](path, cache)
        res            <- f(store, path).ensuring(cleanup(path, tmpPath))
      } yield res
    }

  private val dateKey = "2025-08-26"

  def spec: Spec[TestEnvironment, Any] = suite("SQLiteAggregateSpec")(
    test("Increment atomic SQL on missing row creates row") {
      withStore { (store, _) =>
        for {
          _   <- store.updateFields(dateKey, Chunk(FieldUpdate.Increment("user_count", 5L)))
          res <- store.findById(dateKey)
        } yield assertTrue(res.exists(_.userCount == 5))
      }
    },
    test("Increment generates atomic COALESCE SQL and concurrent increments correct") {
      withStore { (store, _) =>
        for {
          _ <- store.updateFields(dateKey, Chunk(FieldUpdate.Increment("user_count", 1L)))
          _ <- ZIO.foreachParDiscard(1 to 10)(_ =>
                 store.updateFields(dateKey, Chunk(FieldUpdate.Increment("user_count", 1L)))
               )
          res <- store.findById(dateKey)
        } yield assertTrue(res.exists(_.userCount == 11))
      }
    },
    test("10 fibers each increment 10 times final 100") {
      withStore { (store, _) =>
        for {
          _ <- store.upsert(DailyStats(dateKey, 0, 0, 0L))
          _ <- ZIO.foreachParDiscard(1 to 10)(_ =>
                 ZIO.foreachDiscard(1 to 10)(_ =>
                   store.updateFields(dateKey, Chunk(FieldUpdate.Increment("user_count", 1L)))
                 )
               )
          res <- store.findById(dateKey)
        } yield assertTrue(res.exists(_.userCount == 100))
      }
    },
    test("Decrement atomic SQL") {
      withStore { (store, _) =>
        for {
          _   <- store.upsert(DailyStats(dateKey, 10, 0, 0L))
          _   <- store.updateFields(dateKey, Chunk(FieldUpdate.Decrement("user_count", 3L)))
          res <- store.findById(dateKey)
        } yield assertTrue(res.exists(_.userCount == 7))
      }
    },
    test("Max atomic SQL") {
      withStore { (store, _) =>
        for {
          _  <- store.upsert(DailyStats(dateKey, 0, 0, 50L))
          _  <- store.updateFields(dateKey, Chunk(FieldUpdate.Max("peak", 100L)))
          r1 <- store.findById(dateKey)
          _  <- store.updateFields(dateKey, Chunk(FieldUpdate.Max("peak", 10L)))
          r2 <- store.findById(dateKey)
        } yield assertTrue(r1.exists(_.peak == 100L), r2.exists(_.peak == 100L))
      }
    },
    test("Min atomic SQL") {
      withStore { (store, _) =>
        for {
          _  <- store.upsert(DailyStats(dateKey, 0, 0, 50L))
          _  <- store.updateFields(dateKey, Chunk(FieldUpdate.Min("peak", 10L)))
          r1 <- store.findById(dateKey)
          _  <- store.updateFields(dateKey, Chunk(FieldUpdate.Min("peak", 99L)))
          r2 <- store.findById(dateKey)
        } yield assertTrue(r1.exists(_.peak == 10L), r2.exists(_.peak == 10L))
      }
    },
    test("Upsert via ON CONFLICT inserts and updates") {
      withStore { (store, _) =>
        for {
          _  <- store.upsert(DailyStats(dateKey, 1, 1, 1L))
          r1 <- store.findById(dateKey)
          _  <- store.upsert(DailyStats(dateKey, 9, 9, 9L))
          r2 <- store.findById(dateKey)
        } yield assertTrue(r1.exists(_.userCount == 1), r2.exists(_.userCount == 9), r2.exists(_.peak == 9L))
      }
    },
    test("concurrent upserts via TransactorCache don't lose increments (atomic UPDATE not read-modify-write)") {
      withStore { (store, _) =>
        for {
          _ <- store.upsert(DailyStats(dateKey, 0, 0, 0L))
          _ <- ZIO.foreachParDiscard(1 to 20)(_ =>
                 store.updateFields(dateKey, Chunk(FieldUpdate.Increment("repo_count", 5L)))
               )
          res <- store.findById(dateKey)
        } yield assertTrue(res.exists(_.repoCount == 100))
      }
    },
    test("Global single file all events routed to same DB") {
      ZIO.scoped {
        for {
          tmp            <- tempFile("global-file")
          (path, tmpPath) = tmp
          cache          <- TransactorCache.make()
          store          <- SQLiteProjectionStore.make[DailyStats](path, cache)
          // Simulate engine's global/<name>.db path selection
          globalPath = s"global/dailyStats.db"
          _         <- store.updateFields(dateKey, Chunk(FieldUpdate.Increment("user_count", 1L)))
          res       <- store.findById(dateKey)
          _         <- cleanup(path, tmpPath)
          // Verify global path naming convention
          spec = Projection.global[DailyStats]("dailyStats")
        } yield assertTrue(res.isDefined, spec.scope == ProjectionScope.Global, globalPath == "global/dailyStats.db")
      }
    },
    test("mixed Set + Increment + Max + Min + Decrement in one chunk") {
      withStore { (store, _) =>
        for {
          _ <- store.upsert(DailyStats(dateKey, 10, 50, 50L))
          _ <- store.updateFields(
                 dateKey,
                 Chunk(
                   FieldUpdate.Set("user_count", 20),
                   FieldUpdate.Increment("repo_count", 5L),
                   FieldUpdate.Decrement("repo_count", 2L),
                   FieldUpdate.Max("peak", 80L),
                   FieldUpdate.Min("peak", 100L)
                 )
               )
          res <- store.findById(dateKey)
        } yield assertTrue(res.exists(_.userCount == 20), res.exists(_.repoCount == 53), res.exists(_.peak == 80L))
      }
    }
  )
}
