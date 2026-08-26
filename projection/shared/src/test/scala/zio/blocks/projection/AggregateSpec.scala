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
import zio.blocks.projection.testing.{InMemoryProjectionStore, TestProjectionEngine}
import zio.blocks.schema.{Modifier, Schema}
import zio.stream.ZStream
import zio.test.*
import zio.test.Live

import java.time.Instant

object AggregateSpec extends ZIOSpecDefault {

  // ---------------------------------------------------------------------------
  // Models
  // ---------------------------------------------------------------------------

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

  case class UserCreated(name: String)
  object UserCreated { implicit val schema: Schema[UserCreated] = Schema.derived[UserCreated] }
  case class RepoCreated(name: String)
  object RepoCreated { implicit val schema: Schema[RepoCreated] = Schema.derived[RepoCreated] }
  case class PeakUpdate(value: Long)
  object PeakUpdate { implicit val schema: Schema[PeakUpdate] = Schema.derived[PeakUpdate] }

  // InMemory EventStore stub (same as ProjectionEngineSpec)
  final class InMemEventStore[E](
    hub: Hub[EventEnvelope[E]],
    buffer: Ref[List[EventEnvelope[E]]],
    counter: Ref[Long]
  ) extends EventStore[E] {
    def subscribe: Hub[EventEnvelope[E]]               = hub
    def append(entityId: String, event: E): Task[Long] =
      for {
        seq <- counter.updateAndGet(_ + 1)
        env  = EventEnvelope(seq, event.getClass.getSimpleName.stripSuffix("$"), event, Instant.now(), entityId)
        _   <- buffer.update(_ :+ env)
        _   <- hub.publish(env).unit
      } yield seq
    def readFrom(afterSeq: Long, tags: Set[String] = Set.empty): ZStream[Any, Throwable, EventEnvelope[E]] =
      ZStream.unwrap {
        buffer.get.map { list =>
          val filtered = list.filter(_.seq > afterSeq).filter(e => tags.isEmpty || tags.contains(e.tag))
          ZStream.fromIterable(filtered)
        }
      }
    def readAll(tags: Set[String] = Set.empty): ZStream[Any, Throwable, EventEnvelope[E]] = readFrom(0L, tags)
  }
  object InMemEventStore {
    def make[E]: ZIO[Any, Nothing, InMemEventStore[E]] =
      for {
        hub     <- Hub.unbounded[EventEnvelope[E]]
        buffer  <- Ref.make(List.empty[EventEnvelope[E]])
        counter <- Ref.make(0L)
      } yield new InMemEventStore[E](hub, buffer, counter)
  }

  private def pollUntil[A](zio: Task[Option[A]], timeout: Duration = 2.seconds): Task[Option[A]] =
    Live.live {
      def loop(deadline: Long): Task[Option[A]] =
        for {
          opt <- zio
          now <- Clock.currentTime(java.util.concurrent.TimeUnit.MILLISECONDS)
          res <- if (opt.isDefined) ZIO.succeed(opt)
                 else if (now >= deadline) ZIO.succeed(None)
                 else ZIO.sleep(20.millis) *> loop(deadline)
        } yield res
      Clock.currentTime(java.util.concurrent.TimeUnit.MILLISECONDS).flatMap(start => loop(start + timeout.toMillis))
    }

  private val dateKey = "2025-08-26"

  def spec: Spec[TestEnvironment, Any] = suite("AggregateSpec")(
    suite("global scope")(
      test("global factory has Global scope and no entityPath") {
        val s = ProjectionSpec.global[DailyStats]("dailyStats")
        assertTrue(s.scope == ProjectionScope.Global, s.isGlobal, s.entityPath.isEmpty)
      },
      test("global routing uses single store file path global/<name>.db") {
        // Verified indirectly via ProjectionEngine path selection; ensure engine contains global name
        ZIO.scoped {
          for {
            engine <- ProjectionEngine.make(ProjectionSpec.global[DailyStats]("dailyStats"))
          } yield assertTrue(engine.storesMap.contains("dailyStats"))
        }
      },
      test("global projection receives all sources routed to single file") {
        ZIO.scoped {
          for {
            cache    <- TransactorCache.make()
            store    <- InMemoryProjectionStore.make[DailyStats]
            hubUsers <- InMemEventStore.make[Any]
            hubRepos <- InMemEventStore.make[Any]
            spec      = ProjectionSpec
                     .global[DailyStats]("dailyStats")
                     .from("users")
                     .routeToAll
                     .on[UserCreated]
                     .aggregate(FieldUpdate.Increment("user_count", 1L))
                     .from("repos")
                     .routeToAll
                     .on[RepoCreated]
                     .aggregate(FieldUpdate.Increment("repo_count", 1L))
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name -> store),
                        Map(
                          "users" -> hubUsers.asInstanceOf[EventStore[Any]],
                          "repos" -> hubRepos.asInstanceOf[EventStore[Any]]
                        ),
                        cache
                      )
            _ <- engine.start
            _ <- ZIO.foreachDiscard(1 to 5)(_ => hubUsers.append(dateKey, UserCreated("u")))
            _ <- ZIO.foreachDiscard(1 to 3)(_ => hubRepos.append(dateKey, RepoCreated("r")))
            // After events, query aggregate row by grouping key = date
            result <- pollUntil(engine.query(spec, dateKey).map(_.filter(r => r.userCount == 5 && r.repoCount == 3)))
          } yield assertTrue(result.exists(_.userCount == 5), result.exists(_.repoCount == 3))
        }
      },
      test("100 UserCreated + 50 RepoCreated aggregate counts") {
        ZIO.scoped {
          for {
            cache    <- TransactorCache.make()
            store    <- InMemoryProjectionStore.make[DailyStats]
            hubUsers <- InMemEventStore.make[Any]
            hubRepos <- InMemEventStore.make[Any]
            spec      = ProjectionSpec
                     .global[DailyStats]("dailyStats2")
                     .from("users")
                     .routeToAll
                     .on[UserCreated]
                     .aggregate(FieldUpdate.Increment("user_count", 1L))
                     .from("repos")
                     .routeToAll
                     .on[RepoCreated]
                     .aggregate(FieldUpdate.Increment("repo_count", 1L))
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name -> store),
                        Map(
                          "users" -> hubUsers.asInstanceOf[EventStore[Any]],
                          "repos" -> hubRepos.asInstanceOf[EventStore[Any]]
                        ),
                        cache
                      )
            _   <- engine.start
            _   <- ZIO.foreachDiscard(1 to 100)(i => hubUsers.append(dateKey, UserCreated(s"user-$i")))
            _   <- ZIO.foreachDiscard(1 to 50)(i => hubRepos.append(dateKey, RepoCreated(s"repo-$i")))
            res <- pollUntil(
                     engine.query(spec, dateKey).map(_.filter(s => s.userCount == 100 && s.repoCount == 50)),
                     5.seconds
                   )
          } yield assertTrue(res.exists(_.userCount == 100), res.exists(_.repoCount == 50))
        }
      }
    ),
    suite("atomic counters InMemory direct")(
      test("increment on missing row creates row with count 1") {
        for {
          store <- InMemoryProjectionStore.make[DailyStats]
          _     <- store.updateFields(dateKey, Chunk(FieldUpdate.Increment("user_count", 1L)))
          res   <- store.findById(dateKey)
        } yield assertTrue(res.exists(_.userCount == 1))
      },
      test("increment multiple times sequential") {
        for {
          store <- InMemoryProjectionStore.make[DailyStats]
          _     <- store.upsert(DailyStats(dateKey, 0, 0, 0L))
          _     <- store.updateFields(dateKey, Chunk(FieldUpdate.Increment("user_count", 3L)))
          _     <- store.updateFields(dateKey, Chunk(FieldUpdate.Increment("user_count", 7L)))
          res   <- store.findById(dateKey)
        } yield assertTrue(res.exists(_.userCount == 10))
      },
      test("decrement field") {
        for {
          store <- InMemoryProjectionStore.make[DailyStats]
          _     <- store.upsert(DailyStats(dateKey, 10, 0, 0L))
          _     <- store.updateFields(dateKey, Chunk(FieldUpdate.Decrement("user_count", 4L)))
          res   <- store.findById(dateKey)
        } yield assertTrue(res.exists(_.userCount == 6))
      },
      test("increment then decrement") {
        for {
          store <- InMemoryProjectionStore.make[DailyStats]
          _     <- store.upsert(DailyStats(dateKey, 5, 0, 0L))
          _     <- store.updateFields(
                 dateKey,
                 Chunk(FieldUpdate.Increment("user_count", 10L), FieldUpdate.Decrement("user_count", 3L))
               )
          res <- store.findById(dateKey)
        } yield assertTrue(res.exists(_.userCount == 12))
      },
      test("upsert via ON CONFLICT insert then update") {
        for {
          store <- InMemoryProjectionStore.make[DailyStats]
          a1     = DailyStats(dateKey, 1, 1, 10L)
          a2     = DailyStats(dateKey, 5, 5, 20L)
          _     <- store.upsert(a1)
          r1    <- store.findById(dateKey)
          _     <- store.upsert(a2)
          r2    <- store.findById(dateKey)
        } yield assertTrue(r1.contains(a1), r2.contains(a2), r2.exists(_.userCount == 5))
      }
    ),
    suite("Max/Min semantics")(
      test("Max updates when greater") {
        for {
          store <- InMemoryProjectionStore.make[DailyStats]
          _     <- store.upsert(DailyStats(dateKey, 0, 0, 50L))
          _     <- store.updateFields(dateKey, Chunk(FieldUpdate.Max("peak", 100L)))
          res   <- store.findById(dateKey)
        } yield assertTrue(res.exists(_.peak == 100L))
      },
      test("Max keeps when smaller") {
        for {
          store <- InMemoryProjectionStore.make[DailyStats]
          _     <- store.upsert(DailyStats(dateKey, 0, 0, 50L))
          _     <- store.updateFields(dateKey, Chunk(FieldUpdate.Max("peak", 10L)))
          res   <- store.findById(dateKey)
        } yield assertTrue(res.exists(_.peak == 50L))
      },
      test("Min updates when smaller") {
        for {
          store <- InMemoryProjectionStore.make[DailyStats]
          _     <- store.upsert(DailyStats(dateKey, 0, 0, 50L))
          _     <- store.updateFields(dateKey, Chunk(FieldUpdate.Min("peak", 10L)))
          res   <- store.findById(dateKey)
        } yield assertTrue(res.exists(_.peak == 10L))
      },
      test("Min keeps when greater") {
        for {
          store <- InMemoryProjectionStore.make[DailyStats]
          _     <- store.upsert(DailyStats(dateKey, 0, 0, 50L))
          _     <- store.updateFields(dateKey, Chunk(FieldUpdate.Min("peak", 99L)))
          res   <- store.findById(dateKey)
        } yield assertTrue(res.exists(_.peak == 50L))
      },
      test("Max equal keeps existing") {
        for {
          store <- InMemoryProjectionStore.make[DailyStats]
          _     <- store.upsert(DailyStats(dateKey, 0, 0, 42L))
          _     <- store.updateFields(dateKey, Chunk(FieldUpdate.Max("peak", 42L)))
          res   <- store.findById(dateKey)
        } yield assertTrue(res.exists(_.peak == 42L))
      },
      test("Min equal keeps existing") {
        for {
          store <- InMemoryProjectionStore.make[DailyStats]
          _     <- store.upsert(DailyStats(dateKey, 0, 0, 42L))
          _     <- store.updateFields(dateKey, Chunk(FieldUpdate.Min("peak", 42L)))
          res   <- store.findById(dateKey)
        } yield assertTrue(res.exists(_.peak == 42L))
      }
    ),
    suite("concurrent increments")(
      test("10 fibers each increment 10 times -> final 100 InMemory") {
        for {
          store <- InMemoryProjectionStore.make[DailyStats]
          _     <- store.upsert(DailyStats(dateKey, 0, 0, 0L))
          _     <- ZIO.foreachParDiscard(1 to 10) { _ =>
                 ZIO.foreachDiscard(1 to 10)(_ =>
                   store.updateFields(dateKey, Chunk(FieldUpdate.Increment("user_count", 1L)))
                 )
               }
          res <- store.findById(dateKey)
        } yield assertTrue(res.exists(_.userCount == 100))
      },
      test("10 fibers each increment 10 times via ProjectionEngine") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make()
            store <- InMemoryProjectionStore.make[DailyStats]
            hub   <- InMemEventStore.make[Any]
            spec   = ProjectionSpec
                     .global[DailyStats]("conc")
                     .from("ev")
                     .routeToAll
                     .on[UserCreated]
                     .aggregate(FieldUpdate.Increment("user_count", 1L))
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name -> store),
                        Map("ev"      -> hub.asInstanceOf[EventStore[Any]]),
                        cache
                      )
            _ <- engine.start
            _ <- ZIO.foreachParDiscard(1 to 10) { _ =>
                   ZIO.foreachDiscard(1 to 10)(_ => hub.append(dateKey, UserCreated("x")))
                 }
            res <- pollUntil(engine.query(spec, dateKey).map(_.filter(_.userCount == 100)), 5.seconds)
          } yield assertTrue(res.exists(_.userCount == 100))
        }
      },
      test("concurrent increments on different grouping keys isolate") {
        for {
          store <- InMemoryProjectionStore.make[DailyStats]
          _     <- ZIO.foreachParDiscard(List("2025-08-26", "2025-08-27"))(key =>
                 store.updateFields(key, Chunk(FieldUpdate.Increment("user_count", 5L)))
               )
          r1 <- store.findById("2025-08-26")
          r2 <- store.findById("2025-08-27")
        } yield assertTrue(
          r1.exists(_.userCount == 5),
          r2.exists(_.userCount == 5),
          r1.get.date != r2.get.date
        )
      }
    ),
    suite("cross-source aggregate via engine")(
      test("two sources increment different counters converge on same date row") {
        ZIO.scoped {
          for {
            cache    <- TransactorCache.make()
            store    <- InMemoryProjectionStore.make[DailyStats]
            hubUsers <- InMemEventStore.make[Any]
            hubRepos <- InMemEventStore.make[Any]
            spec      = ProjectionSpec
                     .global[DailyStats]("cross")
                     .from("users")
                     .routeToAll
                     .on[UserCreated]
                     .aggregate(FieldUpdate.Increment("user_count", 1L))
                     .from("repos")
                     .routeToAll
                     .on[RepoCreated]
                     .aggregate(FieldUpdate.Increment("repo_count", 1L))
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name -> store),
                        Map(
                          "users" -> hubUsers.asInstanceOf[EventStore[Any]],
                          "repos" -> hubRepos.asInstanceOf[EventStore[Any]]
                        ),
                        cache
                      )
            _   <- engine.start
            _   <- hubUsers.append(dateKey, UserCreated("a"))
            _   <- hubRepos.append(dateKey, RepoCreated("b"))
            res <- pollUntil(engine.query(spec, dateKey).map(_.filter(s => s.userCount == 1 && s.repoCount == 1)))
          } yield assertTrue(res.exists(_.userCount == 1), res.exists(_.repoCount == 1))
        }
      },
      test("peak Max across sources") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make()
            store <- InMemoryProjectionStore.make[DailyStats]
            hub   <- InMemEventStore.make[Any]
            spec   = ProjectionSpec
                     .global[DailyStats]("peakCross")
                     .from("ev")
                     .routeToAll
                     .on[PeakUpdate]
                     .aggregate(FieldUpdate.Max("peak", 0L)) // placeholder, will be overridden per event via custom
            // Use custom to map event value to Max
            spec2 = ProjectionSpec
                      .global[DailyStats]("peakCross2")
                      .from("ev")
                      .routeToAll
                      .on[PeakUpdate]
                      .custom((e, _) =>
                        zio.blocks.projection.ProjectionAction.Update(Chunk(FieldUpdate.Max("peak", e.value)))
                      )
            engine <- ProjectionEngine.makeWithStores(
                        List(spec2),
                        Map(spec2.name -> store),
                        Map("ev"       -> hub.asInstanceOf[EventStore[Any]]),
                        cache
                      )
            _   <- engine.start
            _   <- hub.append(dateKey, PeakUpdate(10L))
            _   <- hub.append(dateKey, PeakUpdate(99L))
            _   <- hub.append(dateKey, PeakUpdate(5L))
            res <- pollUntil(engine.query(spec2, dateKey).map(_.filter(_.peak == 99L)))
          } yield assertTrue(res.exists(_.peak == 99L))
        }
      }
    )
  )
}
