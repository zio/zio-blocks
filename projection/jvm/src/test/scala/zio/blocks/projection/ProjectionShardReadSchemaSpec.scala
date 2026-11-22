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

/**
 * Regression coverage for shard schema validation on the read path: reopening a
 * stale-schema shard through `ProjectionEngine.queryShard` must fail and must
 * not cache the store, otherwise a later routed write would hit the cached fast
 * path and bypass validation.
 */
object ProjectionShardReadSchemaSpec extends ZIOSpecDefault {

  case class RepoRow(@Modifier.id id: String, name: String, owner: String)
  object RepoRow {
    implicit val schema: Schema[RepoRow] = Schema.derived[RepoRow]
  }

  case class RepoAdded(ownerId: String, name: String)
  object RepoAdded {
    implicit val schema: Schema[RepoAdded] = Schema.derived[RepoAdded]
  }

  private def deleteRecursively(dir: java.nio.file.Path): UIO[Unit] =
    ZIO.succeed {
      try {
        if (java.nio.file.Files.exists(dir)) {
          val paths = java.nio.file.Files.walk(dir)
          try {
            paths
              .sorted(java.util.Comparator.reverseOrder[java.nio.file.Path]())
              .forEach(p => java.nio.file.Files.deleteIfExists(p))
            ()
          } finally paths.close()
        }
      } catch { case _: Throwable => () }
    }

  private def awaitShardRows[A](engine: ProjectionEngine, spec: Projection[A], routingKey: String): Task[Chunk[A]] =
    Live.live {
      def loop(remaining: Int): Task[Chunk[A]] =
        engine.queryShard(spec, routingKey).flatMap { rows =>
          if (rows.nonEmpty) ZIO.succeed(rows)
          else if (remaining <= 0) ZIO.succeed(rows)
          else ZIO.sleep(20.millis) *> loop(remaining - 1)
        }
      loop(100)
    }

  def spec: Spec[TestEnvironment, Any] = suite("ProjectionShardReadSchemaSpec")(
    test("queryShard rejects a stale-schema shard, does not cache it, and a routed write validates instead") {
      ZIO.scoped {
        for {
          dir <- ZIO.attempt(
                   java.nio.file.Files.createDirectories(
                     java.nio.file.Paths.get(s"target/projection-shard-read-${java.util.UUID.randomUUID()}")
                   )
                 )
          result <- scenario(dir).ensuring(deleteRecursively(dir))
        } yield result
      }
    }
  )

  private def scenario(dir: java.nio.file.Path): Task[TestResult] = ZIO.scoped {
    implicit val entityPath: EntityPath[RepoRow] = EntityPath[RepoRow](dir.toString, "id")

    val spec = Projection[RepoRow]("shard_read_rows", entityPath)
      .from("repos")
      .routedBy[RepoAdded](_.ownerId)
      .on[RepoAdded]
      .insert((e, ctx) => RepoRow(ctx.entityId, e.name, e.ownerId))

    val primaryPath = dir.resolve("primary.db").toString

    for {
      cache   <- TransactorCache.make()
      primary <- SQLiteProjectionStore.make[RepoRow](primaryPath, cache)
      events  <- ProjectionEngineSpec.InMemEventStore.make[Any]
      seeded  <- ZIO.scoped {
                  for {
                    engine <- ProjectionEngine.makeWithStores(
                                List(spec),
                                Map(spec.name -> primary),
                                Map("repos"   -> events),
                                cache
                              )
                    _    <- engine.start
                    _    <- events.append("r1", RepoAdded("alice", "repo-1"))
                    rows <- awaitShardRows(engine, spec, "alice")
                    path  = engine.shardStorePath(spec, "alice")
                  } yield (rows, path)
                }
      (seededRows, shardPath) = seeded
      stale                  <- SQLiteProjectionStore.make[RepoRow](shardPath, cache)
      _                      <- stale.updateSchemaHash("stale-schema-hash")
      outcome                <- ZIO.scoped {
                   for {
                     engine <- ProjectionEngine.makeWithStores(
                                 List(spec),
                                 Map(spec.name -> primary),
                                 Map("repos"   -> events),
                                 cache
                               )
                     // Queued before start so catch-up is deterministic: the stale-schema
                     // write must fail, and the healthy write proves the batch moved on.
                     _         <- events.append("r2", RepoAdded("alice", "repo-2"))
                     _         <- events.append("r3", RepoAdded("bob", "repo-3"))
                     _         <- engine.start
                     bob       <- awaitShardRows(engine, spec, "bob")
                     seq       <- engine.getLastProcessedSeq(spec)
                     warm      <- engine.shardsMap
                     aliceExit <- engine.queryShard(spec, "alice").exit
                     aliceRows <- stale.findAll
                   } yield (bob, seq, warm, aliceExit, aliceRows)
                 }
      (bobRows, seq, warm, aliceExit, aliceRows) = outcome
    } yield assertTrue(
      seededRows.map(_.id) == Chunk("r1"),
      bobRows.map(_.id) == Chunk("r3"),
      // r2 failed on the stale shard, so the watermark only advanced via r3.
      seq == 3L,
      !warm.getOrElse(spec.name, Map.empty).contains("alice"),
      warm.getOrElse(spec.name, Map.empty).contains("bob"),
      aliceExit match {
        case Exit.Failure(cause) => cause.failures.exists(_.getMessage.contains("shard schema hash mismatch"))
        case Exit.Success(_)     => false
      },
      // The stale shard still holds only the original row; r2 was not written.
      aliceRows.map(_.id) == Chunk("r1")
    )
  }
}
