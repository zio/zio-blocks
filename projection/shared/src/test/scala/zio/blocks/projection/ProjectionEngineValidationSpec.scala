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
import zio.blocks.projection.testing.InMemoryProjectionStore
import zio.blocks.schema.{Modifier, Schema}
import zio.stream.ZStream
import zio.test.*

import java.time.Instant

object ProjectionEngineValidationSpec extends ZIOSpecDefault {

  case class User(
    @Modifier.id id: String,
    name: String,
    age: Long
  )
  object User {
    implicit val schema: Schema[User]         = Schema.derived[User]
    implicit val entityPath: EntityPath[User] = EntityPath.derived[User]
  }

  case class Counter(
    @Modifier.id id: String,
    total: Long
  )
  object Counter {
    implicit val schema: Schema[Counter]         = Schema.derived[Counter]
    implicit val entityPath: EntityPath[Counter] = EntityPath.derived[Counter]
  }

  case class UserCreated(name: String, age: Long)
  object UserCreated { implicit val schema: Schema[UserCreated] = Schema.derived[UserCreated] }
  case class UserDeleted()
  object UserDeleted { implicit val schema: Schema[UserDeleted] = Schema.derived[UserDeleted] }
  case class CountInc(by: Long)
  object CountInc { implicit val schema: Schema[CountInc] = Schema.derived[CountInc] }
  case class RepoCreated(ownerId: String, repoName: String)
  object RepoCreated { implicit val schema: Schema[RepoCreated] = Schema.derived[RepoCreated] }
  case class BadEvent(value: String)
  object BadEvent { implicit val schema: Schema[BadEvent] = Schema.derived[BadEvent] }
  case class TruncateEvent()
  object TruncateEvent { implicit val schema: Schema[TruncateEvent] = Schema.derived[TruncateEvent] }
  case class NoopEvent()
  object NoopEvent { implicit val schema: Schema[NoopEvent] = Schema.derived[NoopEvent] }

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
      ZStream.unwrap(buffer.get.map { list =>
        val filtered = list.filter(_.seq > afterSeq).filter(e => tags.isEmpty || tags.contains(e.tag))
        ZStream.fromIterable(filtered)
      })
    def readAll(tags: Set[String] = Set.empty): ZStream[Any, Throwable, EventEnvelope[E]] = readFrom(0L, tags)
  }
  object InMemEventStore {
    def make[E]: ZIO[Any, Nothing, InMemEventStore[E]] =
      for {
        hub     <- Hub.bounded[EventEnvelope[E]](4096)
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
      Clock.currentTime(java.util.concurrent.TimeUnit.MILLISECONDS).flatMap(s => loop(s + timeout.toMillis))
    }

  def spec: Spec[TestEnvironment, Any] = suite("ProjectionEngineValidationSpec")(
    suite("validateName negative")(
      test("rejects empty") {
        val r = scala.util.Try(ProjectionEngine.validateName(""))
        assertTrue(r.isFailure, r.failed.get.isInstanceOf[IllegalArgumentException])
      },
      test("rejects slash") {
        val r = scala.util.Try(ProjectionEngine.validateName("bad/name"))
        assertTrue(r.isFailure)
      },
      test("rejects backslash") {
        val r = scala.util.Try(ProjectionEngine.validateName("bad\\path"))
        assertTrue(r.isFailure)
      },
      test("rejects dotdot") {
        val r = scala.util.Try(ProjectionEngine.validateName(".."))
        assertTrue(r.isFailure)
      },
      test("validateSpec via make fails on bad name") {
        val res = scala.util.Try {
          zio.Unsafe.unsafe { implicit unsafe =>
            zio.Runtime.default.unsafe
              .run(
                ZIO.scoped {
                  ProjectionEngine.make(
                    Projection[User]("bad/name").on[UserCreated].insert((e, ctx) => User(ctx.entityId, e.name, e.age))
                  )
                }
              )
              .getOrThrow()
          }
        }
        assertTrue(res.isFailure)
      }
    ),
    suite("validateBasePath negative")(
      test("rejects empty") {
        val r = scala.util.Try(ProjectionEngine.validateBasePath(""))
        assertTrue(r.isFailure)
      },
      test("rejects backslash") {
        val r = scala.util.Try(ProjectionEngine.validateBasePath("bad\\path"))
        assertTrue(r.isFailure)
      },
      test("rejects dotdot") {
        val r = scala.util.Try(ProjectionEngine.validateBasePath("a/../b"))
        assertTrue(r.isFailure)
      },
      test("rejects double slash") {
        val r = scala.util.Try(ProjectionEngine.validateBasePath("a//b"))
        assertTrue(r.isFailure)
      },
      test("rejects absolute") {
        val r = scala.util.Try(ProjectionEngine.validateBasePath("/abs"))
        assertTrue(r.isFailure)
      },
      test("validateSpec rejects invalid basePath via EntityPath") {
        val res = scala.util.Try {
          zio.Unsafe.unsafe { implicit unsafe =>
            zio.Runtime.default.unsafe
              .run(
                ZIO.scoped {
                  val ep   = EntityPath[User]("bad/../path", "id")
                  val spec = Projection[User]("valid-name", ep)
                    .on[UserCreated]
                    .insert((e, ctx) => User(ctx.entityId, e.name, e.age))
                  ProjectionEngine.make(spec)
                }
              )
              .getOrThrow()
          }
        }
        // EntityPath.derived validates, but explicit path with traversal should fail via validateSpec
        // If EntityPath construction itself throws, that also counts as rejection
        assertTrue(res.isFailure)
      }
    ),
    suite("empty eventStores start logs warning")(
      test("makeWithStores with empty eventStores start succeeds without crash") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.makeUnscoped(5)
            store <- InMemoryProjectionStore.make[User]
            spec   = Projection[User]("users-empty")
                     .from("src")
                     .routeToSelf
                     .on[UserCreated]
                     .insert((e, ctx) => User(ctx.entityId, e.name, e.age))
            engine <- ProjectionEngine.makeWithStores(List(spec), Map(spec.name -> store), Map.empty, cache)
            _      <- engine.start
            // engine should still have store but no event store
            hasStore = engine.storesMap.contains(spec.name)
            noEvents = engine.eventStoresMap.isEmpty
            // query missing should be None, not failure
            q <- engine.query(spec, "missing")
          } yield assertTrue(hasStore, noEvents, q.isEmpty)
        }
      }
    ),
    suite("scope variants Global vs CrossEntity vs PerEntity")(
      test("PerEntity default scope") {
        val p = Projection[User]("per-entity").on[UserCreated].insert((e, ctx) => User(ctx.entityId, e.name, e.age))
        assertTrue(p.scope == ProjectionScope.PerEntity)
      },
      test("Global scope via global + routeToAll") {
        val p = Projection
          .global[Counter]("global-counter")
          .from("src")
          .routeToAll
          .on[CountInc]
          .aggregate(FieldUpdate.Increment("total", 1L))
        assertTrue(p.scope == ProjectionScope.Global)
      },
      test("CrossEntity scope via routedBy") {
        val p = Projection[User]("cross-entity")
          .from("repos")
          .routedBy[RepoCreated](_.ownerId)
          .on[RepoCreated]
          .insert((e, ctx) => User(ctx.entityId, e.repoName, 0L))
        assertTrue(p.scope.isInstanceOf[ProjectionScope.CrossEntity])
      }
    ),
    suite("RoutedBy extractor throwing fallback to ctx.entityId")(
      test("extractor throwing falls back to entityId and still inserts") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make()
            store <- InMemoryProjectionStore.make[User]
            hub   <- InMemEventStore.make[Any]
            spec   = Projection[User]("throw-fallback")
                     .from("ev")
                     .routedBy[BadEvent] { e =>
                       if (e.value == "throw") throw new RuntimeException("extractor boom")
                       else e.value
                     }
                     .on[BadEvent]
                     .insert((e, ctx) => User(ctx.entityId, e.value, 0L))
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name -> store),
                        Map("ev"      -> hub.asInstanceOf[EventStore[Any]]),
                        cache
                      )
            _ <- engine.start
            // sanitize line needs slash handling: test with "a/b.." to hit replaceAll branch via shard path
            _  <- hub.append("entity-1", BadEvent("throw"))
            _  <- hub.append("entity-2", BadEvent("a/b.."))
            r1 <- pollUntil(engine.query(spec, "entity-1"))
            r2 <- pollUntil(engine.query(spec, "entity-2"))
          } yield assertTrue(r1.exists(_.name == "throw"), r2.exists(_.name == "a/b.."))
        }
      }
    ),
    suite("query shard fallback cross-entity")(
      test("primary miss but shard has entity") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make()
            store <- InMemoryProjectionStore.make[User]
            hub   <- InMemEventStore.make[Any]
            spec   = Projection[User]("shard-fallback")
                     .from("repos")
                     .routedBy[RepoCreated](_.ownerId)
                     .on[RepoCreated]
                     .insert((e, ctx) => User(ctx.entityId, e.repoName, 0L))
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name -> store),
                        Map("repos"   -> hub.asInstanceOf[EventStore[Any]]),
                        cache
                      )
            _ <- engine.start
            _ <- hub.append("r1", RepoCreated("ownerA", "repoA"))
            _ <- hub.append("r2", RepoCreated("ownerB", "repoB"))
            // primary store should not contain r1 directly; query should search shards
            primaryMiss <- store.findById("r1")
            q1          <- pollUntil(engine.query(spec, "r1"))
            q2          <- pollUntil(engine.query(spec, "r2"))
            qMiss       <- engine.query(spec, "no-such")
          } yield assertTrue(
            primaryMiss.isEmpty,
            q1.exists(_.name == "repoA"),
            q2.exists(_.name == "repoB"),
            qMiss.isEmpty
          )
        }
      }
    ),
    suite("applyAction variants Delete/Truncate/Noop")(
      test("Delete removes entity") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make()
            store <- InMemoryProjectionStore.make[User]
            hub   <- InMemEventStore.make[Any]
            spec   = Projection[User]("action-delete")
                     .from("ev")
                     .routeToSelf
                     .on[UserCreated]
                     .insert((e, ctx) => User(ctx.entityId, e.name, e.age))
                     .on[UserDeleted]
                     .delete
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name -> store),
                        Map("ev"      -> hub.asInstanceOf[EventStore[Any]]),
                        cache
                      )
            _      <- engine.start
            _      <- hub.append("u1", UserCreated("Alice", 10L))
            _      <- Live.live(ZIO.sleep(200.millis))
            before <- engine.query(spec, "u1")
            _      <- hub.append("u1", UserDeleted())
            _      <- Live.live(ZIO.sleep(200.millis))
            after  <- engine.query(spec, "u1")
          } yield assertTrue(before.exists(_.name == "Alice"), after.isEmpty)
        }
      },
      test("Truncate clears all") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make()
            store <- InMemoryProjectionStore.make[User]
            hub   <- InMemEventStore.make[Any]
            spec   = Projection[User]("action-truncate")
                     .from("ev")
                     .routeToSelf
                     .on[UserCreated]
                     .insert((e, ctx) => User(ctx.entityId, e.name, e.age))
                     .on[TruncateEvent]
                     .custom((_, _) => ProjectionAction.Truncate)
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name -> store),
                        Map("ev"      -> hub.asInstanceOf[EventStore[Any]]),
                        cache
                      )
            _  <- engine.start
            _  <- hub.append("u1", UserCreated("A", 1L))
            _  <- hub.append("u2", UserCreated("B", 2L))
            _  <- Live.live(ZIO.sleep(250.millis))
            _  <- hub.append("any", TruncateEvent())
            _  <- Live.live(ZIO.sleep(250.millis))
            r1 <- engine.query(spec, "u1")
            r2 <- engine.query(spec, "u2")
          } yield assertTrue(r1.isEmpty, r2.isEmpty)
        }
      },
      test("Noop leaves entity unchanged and Upsert/Update via custom") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make()
            store <- InMemoryProjectionStore.make[User]
            hub   <- InMemEventStore.make[Any]
            spec   = Projection[User]("action-noop")
                     .from("ev")
                     .routeToSelf
                     .on[UserCreated]
                     .insert((e, ctx) => User(ctx.entityId, e.name, e.age))
                     .on[NoopEvent]
                     .custom((_, _) => ProjectionAction.Noop)
                     .on[CountInc]
                     .custom((e, ctx) => ProjectionAction.Upsert(User(ctx.entityId, "upserted", e.by)))
                     .on[BadEvent]
                     .custom((e, ctx) => ProjectionAction.Update(Chunk(FieldUpdate.Set("name", e.value))))
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name -> store),
                        Map("ev"      -> hub.asInstanceOf[EventStore[Any]]),
                        cache
                      )
            _           <- engine.start
            _           <- hub.append("u1", UserCreated("Original", 5L))
            _           <- Live.live(ZIO.sleep(200.millis))
            _           <- hub.append("u1", NoopEvent())
            _           <- Live.live(ZIO.sleep(200.millis))
            afterNoop   <- engine.query(spec, "u1")
            _           <- hub.append("u1", CountInc(99L))
            _           <- Live.live(ZIO.sleep(200.millis))
            afterUpsert <- engine.query(spec, "u1")
            _           <- hub.append("u1", BadEvent("updated"))
            _           <- Live.live(ZIO.sleep(200.millis))
            afterUpdate <- engine.query(spec, "u1")
          } yield assertTrue(
            afterNoop.exists(_.name == "Original"),
            afterUpsert.exists(_.name == "upserted"),
            afterUpdate.exists(_.name == "updated")
          )
        }
      }
    )
  )
}
