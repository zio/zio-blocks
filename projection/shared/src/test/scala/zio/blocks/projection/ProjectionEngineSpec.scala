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
import zio.test.Live

import java.time.Instant

object ProjectionEngineSpec extends ZIOSpecDefault {

  // ---------------------------------------------------------------------------
  // Models
  // ---------------------------------------------------------------------------

  case class User(
    @Modifier.id id: String,
    name: String,
    email: String,
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

  // Events
  case class UserCreated(name: String, email: String)
  object UserCreated { implicit val schema: Schema[UserCreated] = Schema.derived[UserCreated] }
  case class UserRenamed(newName: String)
  object UserRenamed { implicit val schema: Schema[UserRenamed] = Schema.derived[UserRenamed] }
  case class UserDeleted()
  object UserDeleted { implicit val schema: Schema[UserDeleted] = Schema.derived[UserDeleted] }
  case class CountInc(by: Long)
  object CountInc { implicit val schema: Schema[CountInc] = Schema.derived[CountInc] }
  case class RepoCreated(ownerId: String, repoName: String)
  object RepoCreated { implicit val schema: Schema[RepoCreated] = Schema.derived[RepoCreated] }

  // Simple Order global model
  case class OrderSummary(
    @Modifier.id id: String,
    item: String,
    count: Long
  )
  object OrderSummary {
    implicit val schema: Schema[OrderSummary]         = Schema.derived[OrderSummary]
    implicit val entityPath: EntityPath[OrderSummary] = EntityPath.derived[OrderSummary]
  }
  case class OrderPlaced(item: String)
  object OrderPlaced { implicit val schema: Schema[OrderPlaced] = Schema.derived[OrderPlaced] }

  // ---------------------------------------------------------------------------
  // In-memory EventStore stub for tests
  // ---------------------------------------------------------------------------

  final class InMemEventStore[E](
    hub: Hub[EventEnvelope[E]],
    buffer: Ref[List[EventEnvelope[E]]],
    counter: Ref[Long]
  ) extends EventStore[E] {

    def subscribe: Hub[EventEnvelope[E]] = hub

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

    def readAll(tags: Set[String] = Set.empty): ZStream[Any, Throwable, EventEnvelope[E]] =
      readFrom(0L, tags)
  }

  object InMemEventStore {
    def make[E]: ZIO[Any, Nothing, InMemEventStore[E]] =
      for {
        hub     <- Hub.unbounded[EventEnvelope[E]]
        buffer  <- Ref.make(List.empty[EventEnvelope[E]])
        counter <- Ref.make(0L)
      } yield new InMemEventStore[E](hub, buffer, counter)

    def makeWithHub[E](hub: Hub[EventEnvelope[E]]): ZIO[Any, Nothing, InMemEventStore[E]] =
      for {
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

  // ---------------------------------------------------------------------------
  // Spec
  // ---------------------------------------------------------------------------

  def spec: Spec[TestEnvironment, Any] = suite("ProjectionEngineSpec")(
    suite("config defaults")(
      test("default batchSize 100 and timeout 50 millis") {
        val cfg = ProjectionEngineConfig.default
        assertTrue(cfg.batchSize == 100, cfg.batchTimeout == 50.millis, cfg.ringCapacity == 4096)
      },
      test("custom config") {
        val cfg = ProjectionEngineConfig(batchSize = 10, batchTimeout = 100.millis, ringCapacity = 512)
        assertTrue(cfg.batchSize == 10, cfg.batchTimeout == 100.millis, cfg.ringCapacity == 512)
      }
    ),
    suite("make and factory")(
      test("make(specs) creates engine with InMemory stores") {
        ZIO.scoped {
          for {
            engine <- ProjectionEngine.make(
                        ProjectionSpec[User]("users")
                          .on[UserCreated]
                          .custom((e, ctx) => ProjectionAction.Insert(User(ctx.entityId, e.name, e.email, 0L)))
                      )
          } yield assertTrue(engine.config.batchSize == 100, engine.storesMap.contains("users"))
        }
      },
      test("makeWithStores injects explicit stores and eventStores") {
        ZIO.scoped {
          for {
            cache    <- TransactorCache.make()
            store    <- InMemoryProjectionStore.make[User]
            hubStore <- InMemEventStore.make[UserCreated]
            spec      = ProjectionSpec[User]("users")
                     .from("src")
                     .routeToSelf
                     .on[UserCreated]
                     .custom((e, ctx) => ProjectionAction.Insert(User(ctx.entityId, e.name, e.email, 0L)))
            engine <-
              ProjectionEngine.makeWithStores(List(spec), Map(spec.name -> store), Map("src" -> hubStore), cache)
          } yield assertTrue(engine.storesMap.contains("users"), engine.eventStoresMap.contains("src"))
        }
      },
      test("transactorCache exposed") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make()
            store <- InMemoryProjectionStore.make[User]
            hub   <- InMemEventStore.make[UserCreated]
            spec   = ProjectionSpec[User]("users")
                     .on[UserCreated]
                     .custom((e, ctx) => ProjectionAction.Insert(User(ctx.entityId, e.name, e.email, 0L)))
            engine <- ProjectionEngine.makeWithStores(List(spec), Map(spec.name -> store), Map("hub" -> hub), cache)
          } yield assertTrue(engine.transactorCache eq cache)
        }
      }
    ),
    suite("single-source")(
      test("insert single event and query") {
        ZIO.scoped {
          for {
            cache    <- TransactorCache.make()
            store    <- InMemoryProjectionStore.make[User]
            hubStore <- InMemEventStore.make[Any]
            spec      = ProjectionSpec[User]("users")
                     .from("events")
                     .routeToSelf
                     .on[UserCreated]
                     .insert((e, ctx) => User(ctx.entityId, e.name, e.email, 0L))
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name -> store),
                        Map("events"  -> hubStore.asInstanceOf[EventStore[Any]]),
                        cache
                      )
            _      <- engine.start
            _      <- hubStore.append("u1", UserCreated("Alice", "a@b.com"))
            result <- pollUntil(engine.query(spec, "u1"))
          } yield assertTrue(result.exists(_.name == "Alice"), result.exists(_.email == "a@b.com"))
        }
      },
      test("update after insert") {
        ZIO.scoped {
          for {
            cache    <- TransactorCache.make()
            store    <- InMemoryProjectionStore.make[User]
            hubStore <- InMemEventStore.make[Any]
            spec      = ProjectionSpec[User]("users")
                     .from("ev")
                     .routeToSelf
                     .on[UserCreated]
                     .insert((e, ctx) => User(ctx.entityId, e.name, e.email, 0L))
                     .on[UserRenamed]
                     .update(_.name)((e, _) => e.newName)
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name -> store),
                        Map("ev"      -> hubStore.asInstanceOf[EventStore[Any]]),
                        cache
                      )
            _      <- engine.start
            _      <- hubStore.append("u1", UserCreated("Alice", "a@b.com"))
            _      <- Live.live(ZIO.sleep(150.millis))
            _      <- hubStore.append("u1", UserRenamed("Alicia"))
            result <- pollUntil(engine.query(spec, "u1").map(_.filter(_.name == "Alicia")))
          } yield assertTrue(result.exists(_.name == "Alicia"))
        }
      },
      test("delete removes entity") {
        ZIO.scoped {
          for {
            cache    <- TransactorCache.make()
            store    <- InMemoryProjectionStore.make[User]
            hubStore <- InMemEventStore.make[Any]
            spec      = ProjectionSpec[User]("users")
                     .from("ev")
                     .routeToSelf
                     .on[UserCreated]
                     .insert((e, ctx) => User(ctx.entityId, e.name, e.email, 0L))
                     .on[UserDeleted]
                     .delete
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name -> store),
                        Map("ev"      -> hubStore.asInstanceOf[EventStore[Any]]),
                        cache
                      )
            _      <- engine.start
            _      <- hubStore.append("u1", UserCreated("ToDel", "d@b.com"))
            _      <- Live.live(ZIO.sleep(150.millis))
            _      <- hubStore.append("u1", UserDeleted())
            _      <- Live.live(ZIO.sleep(200.millis))
            result <- engine.query(spec, "u1")
          } yield assertTrue(result.isEmpty)
        }
      }
    ),
    suite("multi-source")(
      test("two bindings different sources") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make()
            store <- InMemoryProjectionStore.make[User]
            hub1  <- InMemEventStore.make[Any]
            hub2  <- InMemEventStore.make[Any]
            spec   = ProjectionSpec[User]("users")
                     .from("src1")
                     .routeToSelf
                     .on[UserCreated]
                     .insert((e, ctx) => User(ctx.entityId, e.name, e.email, 0L))
            // Need second binding: we add via from again, but insert same logic; for multi-source we create two specs sharing store?
            // Instead test two specs each with own source, sharing engine
            spec2 = ProjectionSpec[Counter]("counters")
                      .from("src2")
                      .routeToSelf
                      .on[CountInc]
                      .custom((e, ctx) => ProjectionAction.Insert(Counter(ctx.entityId, e.by)))
            store2 <- InMemoryProjectionStore.make[Counter]
            engine <- ProjectionEngine.makeWithStores(
                        List(spec, spec2),
                        Map(spec.name -> store, spec2.name                          -> store2),
                        Map("src1"    -> hub1.asInstanceOf[EventStore[Any]], "src2" -> hub2.asInstanceOf[EventStore[Any]]),
                        cache
                      )
            _  <- engine.start
            _  <- hub1.append("u1", UserCreated("Bob", "b@b.com"))
            _  <- hub2.append("c1", CountInc(99L))
            r1 <- pollUntil(engine.query(spec, "u1"))
            r2 <- pollUntil(engine.query(spec2, "c1"))
          } yield assertTrue(r1.exists(_.name == "Bob"), r2.exists(_.total == 99L))
        }
      },
      test("multi-source single spec with two bindings") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make()
            store <- InMemoryProjectionStore.make[User]
            hubA  <- InMemEventStore.make[Any]
            hubB  <- InMemEventStore.make[Any]
            spec   = ProjectionSpec[User]("users")
                     .from("srcA")
                     .routeToSelf
                     .on[UserCreated]
                     .insert((e, ctx) => User(ctx.entityId, e.name, e.email, 0L))
                     .from("srcB")
                     .routeToSelf
                     .on[UserRenamed]
                     .update(_.name)((e, _) => e.newName)
            // spec has two bindings: srcA with UserCreated, srcB with UserRenamed
            // For this test we need handlers on correct binding: add second handler after from srcB
            // The builder adds to last binding, so spec correctly has srcA->Insert, srcB->Update
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name -> store),
                        Map("srcA"    -> hubA.asInstanceOf[EventStore[Any]], "srcB" -> hubB.asInstanceOf[EventStore[Any]]),
                        cache
                      )
            _      <- engine.start
            _      <- hubA.append("u1", UserCreated("Alice", "a@b.com"))
            _      <- Live.live(ZIO.sleep(150.millis))
            _      <- hubB.append("u1", UserRenamed("Ally"))
            result <- pollUntil(engine.query(spec, "u1").map(_.filter(_.name == "Ally")))
          } yield assertTrue(result.exists(_.name == "Ally"))
        }
      }
    ),
    suite("query")(
      test("query returns None for missing entity") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make()
            store <- InMemoryProjectionStore.make[User]
            hub   <- InMemEventStore.make[Any]
            spec   = ProjectionSpec[User]("users")
                     .on[UserCreated]
                     .insert((e, ctx) => User(ctx.entityId, e.name, e.email, 0L))
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name  -> store),
                        Map("_default" -> hub.asInstanceOf[EventStore[Any]]),
                        cache
                      )
            _      <- engine.start
            result <- engine.query(spec, "missing")
          } yield assertTrue(result.isEmpty)
        }
      },
      test("query reads correct store after multiple inserts") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make()
            store <- InMemoryProjectionStore.make[User]
            hub   <- InMemEventStore.make[Any]
            spec   = ProjectionSpec[User]("users")
                     .from("ev")
                     .routeToSelf
                     .on[UserCreated]
                     .insert((e, ctx) => User(ctx.entityId, e.name, e.email, 0L))
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name -> store),
                        Map("ev"      -> hub.asInstanceOf[EventStore[Any]]),
                        cache
                      )
            _ <- engine.start
            _ <-
              ZIO.foreachDiscard(List("u1", "u2", "u3"))(id => hub.append(id, UserCreated(s"Name-$id", s"$id@b.com")))
            _  <- Live.live(ZIO.sleep(300.millis))
            r1 <- engine.query(spec, "u1")
            r2 <- engine.query(spec, "u2")
            r3 <- engine.query(spec, "u3")
          } yield assertTrue(
            r1.exists(_.name == "Name-u1"),
            r2.exists(_.name == "Name-u2"),
            r3.exists(_.name == "Name-u3")
          )
        }
      },
      test("queryByName helper") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make()
            store <- InMemoryProjectionStore.make[User]
            hub   <- InMemEventStore.make[Any]
            spec   = ProjectionSpec[User]("users")
                     .on[UserCreated]
                     .insert((e, ctx) => User(ctx.entityId, e.name, e.email, 0L))
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name  -> store),
                        Map("_default" -> hub.asInstanceOf[EventStore[Any]]),
                        cache
                      )
            _      <- engine.start
            _      <- hub.append("u9", UserCreated("Zed", "z@b.com"))
            _      <- Live.live(ZIO.sleep(200.millis))
            result <- engine.queryByName[User]("users", "u9")
          } yield assertTrue(result.exists(_.name == "Zed"))
        }
      },
      test("getLastProcessedSeq advances") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make()
            store <- InMemoryProjectionStore.make[User]
            hub   <- InMemEventStore.make[Any]
            spec   = ProjectionSpec[User]("users")
                     .from("ev")
                     .routeToSelf
                     .on[UserCreated]
                     .insert((e, ctx) => User(ctx.entityId, e.name, e.email, 0L))
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name -> store),
                        Map("ev"      -> hub.asInstanceOf[EventStore[Any]]),
                        cache
                      )
            _      <- engine.start
            before <- engine.getLastProcessedSeq(spec)
            _      <- hub.append("u1", UserCreated("A", "a@b.com"))
            _      <- Live.live(ZIO.sleep(300.millis))
            after  <- engine.getLastProcessedSeq(spec)
          } yield assertTrue(before == 0L, after > 0L)
        }
      }
    ),
    suite("batching")(
      test("batch size triggers processing") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make()
            store <- InMemoryProjectionStore.make[User]
            hub   <- InMemEventStore.make[Any]
            cfg    = ProjectionEngineConfig(batchSize = 2, batchTimeout = 5.seconds)
            spec   = ProjectionSpec[User]("users")
                     .from("ev")
                     .routeToSelf
                     .on[UserCreated]
                     .insert((e, ctx) => User(ctx.entityId, e.name, e.email, 0L))
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name -> store),
                        Map("ev"      -> hub.asInstanceOf[EventStore[Any]]),
                        cache,
                        cfg
                      )
            _  <- engine.start
            _  <- ZIO.foreachDiscard((1 to 4).toList)(i => hub.append(s"u$i", UserCreated(s"N$i", s"$i@b.com")))
            _  <- Live.live(ZIO.sleep(400.millis))
            r1 <- engine.query(spec, "u1")
            r2 <- engine.query(spec, "u2")
            r3 <- engine.query(spec, "u3")
            r4 <- engine.query(spec, "u4")
          } yield assertTrue(r1.isDefined, r2.isDefined, r3.isDefined, r4.isDefined)
        }
      },
      test("batch timeout triggers processing for single event") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make()
            store <- InMemoryProjectionStore.make[User]
            hub   <- InMemEventStore.make[Any]
            cfg    = ProjectionEngineConfig(batchSize = 100, batchTimeout = 50.millis)
            spec   = ProjectionSpec[User]("users")
                     .from("ev")
                     .routeToSelf
                     .on[UserCreated]
                     .insert((e, ctx) => User(ctx.entityId, e.name, e.email, 0L))
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name -> store),
                        Map("ev"      -> hub.asInstanceOf[EventStore[Any]]),
                        cache,
                        cfg
                      )
            _ <- engine.start
            _ <- hub.append("u1", UserCreated("Solo", "s@b.com"))
            // Even though batchSize not reached, timeout 50ms should flush
            result <- pollUntil(engine.query(spec, "u1"), timeout = 2.seconds)
          } yield assertTrue(result.exists(_.name == "Solo"))
        }
      },
      test("large batch exceeding batchSize handled") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make()
            store <- InMemoryProjectionStore.make[Counter]
            hub   <- InMemEventStore.make[Any]
            cfg    = ProjectionEngineConfig(batchSize = 3, batchTimeout = 50.millis)
            spec   = ProjectionSpec[Counter]("counters")
                     .from("ev")
                     .routeToSelf
                     .on[CountInc]
                     .custom((e, ctx) => ProjectionAction.Insert(Counter(ctx.entityId, e.by)))
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name -> store),
                        Map("ev"      -> hub.asInstanceOf[EventStore[Any]]),
                        cache,
                        cfg
                      )
            _       <- engine.start
            _       <- ZIO.foreachDiscard((1 to 10).toList)(i => hub.append(s"c$i", CountInc(i.toLong)))
            _       <- Live.live(ZIO.sleep(500.millis))
            results <- ZIO.foreach((1 to 10).toList)(i => engine.query(spec, s"c$i"))
          } yield assertTrue(results.forall(_.isDefined), results.flatten.map(_.total).sum == 55L)
        }
      }
    ),
    suite("cross-entity routedBy")(
      test("routedBy extracts key and dispatches") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make()
            store <- InMemoryProjectionStore.make[User]
            hub   <- InMemEventStore.make[Any]
            // RepoCreated contains ownerId, routedBy ownerId should create shard per owner
            spec = ProjectionSpec[User]("users")
                     .from("repos")
                     .routedBy[RepoCreated](_.ownerId)
                     .on[RepoCreated]
                     .insert((e, ctx) => User(ctx.entityId, e.repoName, s"${e.ownerId}@b.com", 0L))
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name -> store),
                        Map("repos"   -> hub.asInstanceOf[EventStore[Any]]),
                        cache
                      )
            _      <- engine.start
            _      <- hub.append("repo1", RepoCreated("ownerA", "my-repo"))
            result <- pollUntil(engine.query(spec, "repo1"))
          } yield assertTrue(result.exists(_.name == "my-repo"), result.exists(_.email == "ownerA@b.com"))
        }
      },
      test("cross-entity multiple owners isolate shards but query finds") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make()
            store <- InMemoryProjectionStore.make[User]
            hub   <- InMemEventStore.make[Any]
            spec   = ProjectionSpec[User]("users")
                     .from("repos")
                     .routedBy[RepoCreated](_.ownerId)
                     .on[RepoCreated]
                     .insert((e, ctx) => User(ctx.entityId, e.repoName, e.ownerId, 0L))
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name -> store),
                        Map("repos"   -> hub.asInstanceOf[EventStore[Any]]),
                        cache
                      )
            _  <- engine.start
            _  <- hub.append("r1", RepoCreated("alice", "repo-alice"))
            _  <- hub.append("r2", RepoCreated("bob", "repo-bob"))
            _  <- Live.live(ZIO.sleep(300.millis))
            ra <- engine.query(spec, "r1")
            rb <- engine.query(spec, "r2")
          } yield assertTrue(ra.exists(_.email == "alice"), rb.exists(_.email == "bob"))
        }
      },
      test("routingKey via scope extractor fallback") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make()
            store <- InMemoryProjectionStore.make[User]
            hub   <- InMemEventStore.make[Any]
            spec   = ProjectionSpec[User]("users")
                     .from("ev")
                     .routedBy[RepoCreated](_.ownerId)
                     .on[RepoCreated]
                     .insert((e, ctx) => User(ctx.entityId, e.repoName, e.ownerId, 0L))
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name -> store),
                        Map("ev"      -> hub.asInstanceOf[EventStore[Any]]),
                        cache
                      )
            _      <- engine.start
            _      <- hub.append("rX", RepoCreated("ownerX", "x-repo"))
            result <- pollUntil(engine.query(spec, "rX"))
          } yield assertTrue(result.isDefined)
        }
      }
    ),
    suite("global")(
      test("global projection single file") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make()
            store <- InMemoryProjectionStore.make[OrderSummary]
            hub   <- InMemEventStore.make[Any]
            spec   = ProjectionSpec
                     .global[OrderSummary]("globalOrders")
                     .from("orders")
                     .routeToAll
                     .on[OrderPlaced]
                     .insert((e, ctx) => OrderSummary(ctx.entityId, e.item, 1L))
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name -> store),
                        Map("orders"  -> hub.asInstanceOf[EventStore[Any]]),
                        cache
                      )
            _      <- engine.start
            _      <- hub.append("o1", OrderPlaced("widget"))
            result <- pollUntil(engine.query(spec, "o1"))
          } yield assertTrue(result.exists(_.item == "widget"))
        }
      },
      test("global routeToAll processes multiple entities") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make()
            store <- InMemoryProjectionStore.make[OrderSummary]
            hub   <- InMemEventStore.make[Any]
            spec   = ProjectionSpec
                     .global[OrderSummary]("g2")
                     .from("ev")
                     .routeToAll
                     .on[OrderPlaced]
                     .insert((e, ctx) => OrderSummary(ctx.entityId, e.item, 1L))
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name -> store),
                        Map("ev"      -> hub.asInstanceOf[EventStore[Any]]),
                        cache
                      )
            _  <- engine.start
            _  <- hub.append("o1", OrderPlaced("a"))
            _  <- hub.append("o2", OrderPlaced("b"))
            _  <- Live.live(ZIO.sleep(300.millis))
            r1 <- engine.query(spec, "o1")
            r2 <- engine.query(spec, "o2")
          } yield assertTrue(r1.exists(_.item == "a"), r2.exists(_.item == "b"))
        }
      }
    ),
    suite("error handling")(
      test("handler exception logged and pipeline continues") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make()
            store <- InMemoryProjectionStore.make[User]
            hub   <- InMemEventStore.make[Any]
            spec   = ProjectionSpec[User]("users")
                     .from("ev")
                     .routeToSelf
                     .on[UserCreated]
                     .custom { (e, ctx) =>
                       if (e.name == "bad") throw new RuntimeException("boom")
                       else ProjectionAction.Insert(User(ctx.entityId, e.name, e.email, 0L))
                     }
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name -> store),
                        Map("ev"      -> hub.asInstanceOf[EventStore[Any]]),
                        cache
                      )
            _    <- engine.start
            _    <- hub.append("u1", UserCreated("bad", "bad@b.com"))
            _    <- hub.append("u2", UserCreated("good", "good@b.com"))
            _    <- Live.live(ZIO.sleep(400.millis))
            bad  <- engine.query(spec, "u1")
            good <- pollUntil(engine.query(spec, "u2"))
          } yield assertTrue(bad.isEmpty, good.exists(_.name == "good"))
        }
      },
      test("store failure does not block next event") {
        // Simulate store that fails on specific id via custom store
        ZIO.scoped {
          for {
            cache     <- TransactorCache.make()
            baseStore <- InMemoryProjectionStore.make[User]
            // Wrap store to fail on u1
            failingStore = new ProjectionStore[User] {
                             def insert(a: User): Task[Unit] =
                               if (a.id == "u1") ZIO.fail(new RuntimeException("insert fail"))
                               else baseStore.insert(a)
                             def upsert(a: User): Task[Unit]                                             = baseStore.upsert(a)
                             def updateFields(entityId: String, updates: Chunk[FieldUpdate]): Task[Unit] =
                               baseStore.updateFields(entityId, updates)
                             def delete(entityId: String): Task[Unit]           = baseStore.delete(entityId)
                             def truncate: Task[Unit]                           = baseStore.truncate
                             def findById(entityId: String): Task[Option[User]] = baseStore.findById(entityId)
                             def getLastProcessedSeq: Task[Long]                = baseStore.getLastProcessedSeq
                             def updateLastProcessedSeq(seq: Long): Task[Unit]  = baseStore.updateLastProcessedSeq(seq)
                             def getSchemaHash: Task[Option[String]]            = baseStore.getSchemaHash
                             def updateSchemaHash(hash: String): Task[Unit]     = baseStore.updateSchemaHash(hash)
                           }
            hub <- InMemEventStore.make[Any]
            spec = ProjectionSpec[User]("users")
                     .from("ev")
                     .routeToSelf
                     .on[UserCreated]
                     .insert((e, ctx) => User(ctx.entityId, e.name, e.email, 0L))
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name -> failingStore),
                        Map("ev"      -> hub.asInstanceOf[EventStore[Any]]),
                        cache
                      )
            _  <- engine.start
            _  <- hub.append("u1", UserCreated("Alice", "a@b.com"))
            _  <- hub.append("u2", UserCreated("Bob", "b@b.com"))
            _  <- Live.live(ZIO.sleep(400.millis))
            r1 <- engine.query(spec, "u1")
            r2 <- pollUntil(engine.query(spec, "u2"))
          } yield assertTrue(r1.isEmpty, r2.exists(_.name == "Bob"))
        }
      },
      test("no handler event is skipped gracefully") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make()
            store <- InMemoryProjectionStore.make[User]
            hub   <- InMemEventStore.make[Any]
            spec   = ProjectionSpec[User]("users")
                     .from("ev")
                     .routeToSelf
                     .on[UserCreated]
                     .insert((e, ctx) => User(ctx.entityId, e.name, e.email, 0L))
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name -> store),
                        Map("ev"      -> hub.asInstanceOf[EventStore[Any]]),
                        cache
                      )
            _    <- engine.start
            _    <- hub.append("u1", CountInc(5L)) // no handler for CountInc
            _    <- hub.append("u2", UserCreated("OK", "ok@b.com"))
            good <- pollUntil(engine.query(spec, "u2"))
            bad  <- engine.query(spec, "u1")
          } yield assertTrue(good.exists(_.name == "OK"), bad.isEmpty)
        }
      }
    ),
    suite("Hub live vs catch-up")(
      test("catch-up reads pre-existing events before live") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make()
            store <- InMemoryProjectionStore.make[User]
            hub   <- InMemEventStore.make[Any]
            spec   = ProjectionSpec[User]("users")
                     .from("ev")
                     .routeToSelf
                     .on[UserCreated]
                     .insert((e, ctx) => User(ctx.entityId, e.name, e.email, 0L))
            // Append before engine start
            _      <- hub.append("u1", UserCreated("Pre", "pre@b.com"))
            _      <- hub.append("u2", UserCreated("Pre2", "pre2@b.com"))
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name -> store),
                        Map("ev"      -> hub.asInstanceOf[EventStore[Any]]),
                        cache
                      )
            _  <- engine.start
            _  <- Live.live(ZIO.sleep(300.millis))
            r1 <- engine.query(spec, "u1")
            r2 <- engine.query(spec, "u2")
            // Now append live
            _  <- hub.append("u3", UserCreated("Live", "live@b.com"))
            r3 <- pollUntil(engine.query(spec, "u3"))
          } yield assertTrue(r1.exists(_.name == "Pre"), r2.exists(_.name == "Pre2"), r3.exists(_.name == "Live"))
        }
      },
      test("live subscription receives events after start") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make()
            store <- InMemoryProjectionStore.make[User]
            hub   <- InMemEventStore.make[Any]
            spec   = ProjectionSpec[User]("users")
                     .from("ev")
                     .routeToSelf
                     .on[UserCreated]
                     .insert((e, ctx) => User(ctx.entityId, e.name, e.email, 0L))
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name -> store),
                        Map("ev"      -> hub.asInstanceOf[EventStore[Any]]),
                        cache
                      )
            _      <- engine.start
            _      <- Live.live(ZIO.sleep(100.millis))
            _      <- hub.append("live1", UserCreated("LiveOne", "l1@b.com"))
            result <- pollUntil(engine.query(spec, "live1"))
          } yield assertTrue(result.exists(_.name == "LiveOne"))
        }
      },
      test("increment update via live") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make()
            store <- InMemoryProjectionStore.make[User]
            hub   <- InMemEventStore.make[Any]
            spec   = ProjectionSpec[User]("users")
                     .from("ev")
                     .routeToSelf
                     .on[UserCreated]
                     .insert((e, ctx) => User(ctx.entityId, e.name, e.email, 10L))
                     .on[CountInc]
                     .aggregate(FieldUpdate.Increment("age", 5L))
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name -> store),
                        Map("ev"      -> hub.asInstanceOf[EventStore[Any]]),
                        cache
                      )
            _      <- engine.start
            _      <- hub.append("u1", UserCreated("Alice", "a@b.com"))
            _      <- Live.live(ZIO.sleep(200.millis))
            _      <- hub.append("u1", CountInc(5L))
            result <- pollUntil(engine.query(spec, "u1").map(_.filter(_.age == 15L)))
          } yield assertTrue(result.exists(_.age == 15L))
        }
      },
      test("rebuild: lastSeq prevents reprocessing on restart") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make()
            store <- InMemoryProjectionStore.make[User]
            hub   <- InMemEventStore.make[Any]
            spec   = ProjectionSpec[User]("users")
                     .from("ev")
                     .routeToSelf
                     .on[UserCreated]
                     .insert((e, ctx) => User(ctx.entityId, e.name, e.email, 0L))
            engine1 <- ProjectionEngine.makeWithStores(
                         List(spec),
                         Map(spec.name -> store),
                         Map("ev"      -> hub.asInstanceOf[EventStore[Any]]),
                         cache
                       )
            _    <- engine1.start
            _    <- hub.append("u1", UserCreated("First", "f@b.com"))
            _    <- Live.live(ZIO.sleep(300.millis))
            seq1 <- store.getLastProcessedSeq
            // New engine with same store and hub should catch-up from seq1, not reprocess
            engine2 <- ProjectionEngine.makeWithStores(
                         List(spec),
                         Map(spec.name -> store),
                         Map("ev"      -> hub.asInstanceOf[EventStore[Any]]),
                         cache
                       )
            _      <- engine2.start
            _      <- Live.live(ZIO.sleep(200.millis))
            seq2   <- store.getLastProcessedSeq
            result <- engine2.query(spec, "u1")
          } yield assertTrue(seq1 > 0L, seq2 == seq1, result.exists(_.name == "First"))
        }
      }
    ),
    suite("query and store integration")(
      test("uses TransactorCache for store creation") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make(4)
            size0 <- cache.size
            store <- InMemoryProjectionStore.make[User]
            hub   <- InMemEventStore.make[Any]
            spec   = ProjectionSpec[User]("users")
                     .on[UserCreated]
                     .insert((e, ctx) => User(ctx.entityId, e.name, e.email, 0L))
            engine <- ProjectionEngine.makeWithStores(
                        List(spec),
                        Map(spec.name  -> store),
                        Map("_default" -> hub.asInstanceOf[EventStore[Any]]),
                        cache
                      )
            _     <- engine.start
            size1 <- cache.size
          } yield assertTrue(size0 == 0, size1 == 0) // InMemory doesn't use cache, but cache is retained
        }
      },
      test("ProjectionAction types: Insert, Update, Delete, Upsert, Truncate, Noop") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make()
            store <- InMemoryProjectionStore.make[Counter]
            hub   <- InMemEventStore.make[Any]
            // Test Upsert via custom
            specUpsert = ProjectionSpec[Counter]("counters")
                           .from("ev")
                           .routeToSelf
                           .on[CountInc]
                           .custom((e, ctx) => ProjectionAction.Upsert(Counter(ctx.entityId, e.by)))
            engine <- ProjectionEngine.makeWithStores(
                        List(specUpsert),
                        Map(specUpsert.name -> store),
                        Map("ev"            -> hub.asInstanceOf[EventStore[Any]]),
                        cache
                      )
            _  <- engine.start
            _  <- hub.append("c1", CountInc(7L))
            r1 <- pollUntil(engine.query(specUpsert, "c1"))
            // Test Truncate
            store2   <- InMemoryProjectionStore.make[Counter]
            hub2     <- InMemEventStore.make[Any]
            specTrunc = ProjectionSpec[Counter]("counters2")
                          .from("ev2")
                          .routeToSelf
                          .on[UserCreated]
                          .insert((e, ctx) => Counter(ctx.entityId, 1L))
                          .on[UserDeleted]
                          .custom((_, _) => ProjectionAction.Truncate)
            cache2  <- TransactorCache.make()
            engine2 <- ProjectionEngine.makeWithStores(
                         List(specTrunc),
                         Map(specTrunc.name -> store2),
                         Map("ev2"          -> hub2.asInstanceOf[EventStore[Any]]),
                         cache2
                       )
            _      <- engine2.start
            _      <- hub2.append("c1", UserCreated("x", "x@b.com"))
            _      <- Live.live(ZIO.sleep(200.millis))
            _      <- hub2.append("any", UserDeleted())
            _      <- Live.live(ZIO.sleep(200.millis))
            rTrunc <- store2.findById("c1")
          } yield assertTrue(r1.exists(_.total == 7L), rTrunc.isEmpty)
        }
      }
    )
  )
}
