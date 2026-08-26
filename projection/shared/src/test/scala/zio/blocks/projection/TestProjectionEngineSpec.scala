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
import zio.test.*
import zio.blocks.chunk.Chunk
import zio.blocks.projection.testing.{InMemoryProjectionStore, TestContext, TestProjectionEngine}
import zio.blocks.schema.{Modifier, Schema}

import java.time.Instant

object TestProjectionEngineSpec extends ZIOSpecDefault {

  case class User(
    @Modifier.id id: String,
    name: String,
    email: String,
    age: Long,
    score: Long,
    active: Boolean
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

  case class UserCreated(name: String, email: String)
  object UserCreated {
    implicit val schema: Schema[UserCreated] = Schema.derived[UserCreated]
  }

  case class UserRenamed(newName: String)
  object UserRenamed {
    implicit val schema: Schema[UserRenamed] = Schema.derived[UserRenamed]
  }

  case class UserDeleted()
  object UserDeleted {
    implicit val schema: Schema[UserDeleted] = Schema.derived[UserDeleted]
  }

  case class CountInc(by: Long)
  object CountInc {
    implicit val schema: Schema[CountInc] = Schema.derived[CountInc]
  }

  def spec: Spec[TestEnvironment, Any] = suite("TestProjectionEngineSpec")(
    suite("TestContext factory")(
      test("default values") {
        val ctx = TestContext.make()
        assertTrue(ctx.entityId == "test-1", ctx.seq == 0L, ctx.sourceEntityId.isEmpty)
      },
      test("custom entityId") {
        val ctx = TestContext.make(entityId = "custom-1")
        assertTrue(ctx.entityId == "custom-1")
      },
      test("custom seq") {
        val ctx = TestContext.make(seq = 99L)
        assertTrue(ctx.seq == 99L)
      },
      test("custom timestamp") {
        val ts  = Instant.parse("2025-06-01T12:00:00Z")
        val ctx = TestContext.make(timestamp = ts)
        assertTrue(ctx.timestamp == ts)
      },
      test("sourceEntityId Some") {
        val ctx = TestContext.make(sourceEntityId = Some("src-1"))
        assertTrue(ctx.sourceEntityId.contains("src-1"))
      },
      test("sourceEntityId None default") {
        val ctx = TestContext.make()
        assertTrue(ctx.sourceEntityId.isEmpty)
      },
      test("withSource helper") {
        val ctx = TestContext.makeWithSource("e1", "src-99")
        assertTrue(ctx.entityId == "e1", ctx.sourceEntityId.contains("src-99"))
      },
      test("withSeq helper") {
        val ctx = TestContext.withSeq("e1", 55L)
        assertTrue(ctx.seq == 55L, ctx.entityId == "e1")
      },
      test("withTimestamp helper") {
        val ts  = Instant.parse("2024-12-01T00:00:00Z")
        val ctx = TestContext.withTimestamp("e1", ts)
        assertTrue(ctx.timestamp == ts)
      },
      test("all fields combined") {
        val ts  = Instant.parse("2023-01-01T00:00:00Z")
        val ctx = TestContext.make("id-42", 123L, ts, Some("src-42"))
        assertTrue(ctx.entityId == "id-42", ctx.seq == 123L, ctx.timestamp == ts, ctx.sourceEntityId.contains("src-42"))
      }
    ),
    suite("processEvent single")(
      test("insert dispatch returns Insert") {
        val engine = TestProjectionEngine.make
        val spec   = ProjectionSpec[User]("users")
          .on[UserCreated]
          .insert((e, ctx) => User(ctx.entityId, e.name, e.email, 0L, 0L, active = true))
        val ctx = TestContext.make(entityId = "u1")
        for {
          action <- engine.processEvent(spec, UserCreated("Alice", "a@b.com"), ctx)
        } yield assertTrue(action match {
          case ProjectionAction.Insert(u: User @unchecked) => u.name == "Alice"
          case _                                           => false
        })
      },
      test("delete dispatch returns Delete") {
        val engine = TestProjectionEngine.make
        val spec   = ProjectionSpec[User]("users").on[UserDeleted].delete
        val ctx    = TestContext.make()
        for {
          action <- engine.processEvent(spec, UserDeleted(), ctx)
        } yield assertTrue(action == ProjectionAction.Delete)
      },
      test("update dispatch returns Update with correct field") {
        val engine = TestProjectionEngine.make
        val spec   = ProjectionSpec[User]("users")
          .on[UserRenamed]
          .update(_.name)((e, _) => e.newName)
        val ctx = TestContext.make()
        for {
          action <- engine.processEvent(spec, UserRenamed("Bob"), ctx)
        } yield assertTrue(action match {
          case ProjectionAction.Update(mods) => mods.head.asInstanceOf[FieldUpdate.Set].field == "name"
          case _                             => false
        })
      },
      test("no handler returns Noop") {
        val engine = TestProjectionEngine.make
        val spec   = ProjectionSpec[User]("users")
          .on[UserCreated]
          .insert((e, ctx) => User(ctx.entityId, e.name, e.email, 0L, 0L, active = true))
        val ctx = TestContext.make()
        for {
          action <- engine.processEvent(spec, UserDeleted(), ctx)
        } yield assertTrue(action == ProjectionAction.Noop)
      },
      test("custom handler returns Upsert") {
        val engine = TestProjectionEngine.make
        val spec   = ProjectionSpec[User]("users")
          .on[UserCreated]
          .custom((e, ctx) => ProjectionAction.Upsert(User(ctx.entityId, e.name, e.email, 0L, 0L, active = true)))
        val ctx = TestContext.make(entityId = "x")
        for {
          action <- engine.processEvent(spec, UserCreated("Frank", "f@b.com"), ctx)
        } yield assertTrue(action match {
          case ProjectionAction.Upsert(u: User @unchecked) => u.name == "Frank"
          case _                                           => false
        })
      },
      test("aggregate handler returns Update Increment") {
        val engine = TestProjectionEngine.make
        val spec   = ProjectionSpec[Counter]("counters").on[CountInc].aggregate(FieldUpdate.Increment("total", 5L))
        val ctx    = TestContext.make()
        for {
          action <- engine.processEvent(spec, CountInc(5L), ctx)
        } yield assertTrue(action match {
          case ProjectionAction.Update(mods) => mods.head == FieldUpdate.Increment("total", 5L)
          case _                             => false
        })
      },
      test("processEvent uses ctx entityId for insert") {
        val engine = TestProjectionEngine.make
        val spec   = ProjectionSpec[User]("users")
          .on[UserCreated]
          .insert((e, ctx) => User(ctx.entityId, e.name, e.email, 0L, 0L, active = true))
        val ctx = TestContext.make(entityId = "my-entity-99")
        for {
          action <- engine.processEvent(spec, UserCreated("Zed", "z@b.com"), ctx)
        } yield assertTrue(action match {
          case ProjectionAction.Insert(u: User @unchecked) => u.id == "my-entity-99"
          case _                                           => false
        })
      }
    ),
    suite("processEvents batch")(
      test("batch insert applies to store") {
        val engine = TestProjectionEngine.make
        val spec   = ProjectionSpec[User]("users")
          .on[UserCreated]
          .insert((e, ctx) => User(ctx.entityId, e.name, e.email, 0L, 0L, active = true))
        val events = List(
          (UserCreated("A", "a@b.com"), TestContext.make(entityId = "1")),
          (UserCreated("B", "b@b.com"), TestContext.make(entityId = "2")),
          (UserCreated("C", "c@b.com"), TestContext.make(entityId = "3"))
        )
        for {
          store <- InMemoryProjectionStore.make[User]
          _     <- engine.processEvents(spec, events, store)
          r1    <- store.findById("1")
          r2    <- store.findById("2")
          r3    <- store.findById("3")
        } yield assertTrue(r1.exists(_.name == "A"), r2.exists(_.name == "B"), r3.exists(_.name == "C"))
      },
      test("batch without store completes") {
        val engine = TestProjectionEngine.make
        val spec   = ProjectionSpec[User]("users")
          .on[UserCreated]
          .insert((e, ctx) => User(ctx.entityId, e.name, e.email, 0L, 0L, active = true))
        val events = List(
          (UserCreated("X", "x@b.com"), TestContext.make(entityId = "x1"))
        )
        for {
          _ <- engine.processEvents(spec, events)
        } yield assertTrue(true)
      },
      test("batch update via FieldUpdate applies increment") {
        val engine     = TestProjectionEngine.make
        val specInsert = ProjectionSpec[User]("users")
          .on[UserCreated]
          .insert((e, ctx) => User(ctx.entityId, e.name, e.email, 10L, 0L, active = true))
        val specUpdate = ProjectionSpec[User]("users")
          .on[CountInc]
          .aggregate(FieldUpdate.Increment("age", 5L))
        for {
          store <- InMemoryProjectionStore.make[User]
          _     <- engine.processAndApply(
                 specInsert,
                 UserCreated("Alice", "a@b.com"),
                 TestContext.make(entityId = "u1"),
                 store
               )
          _   <- engine.processEvents(specUpdate, List((CountInc(5L), TestContext.make(entityId = "u1"))), store)
          res <- store.findById("u1")
        } yield assertTrue(res.exists(_.age == 15L))
      },
      test("batch delete removes entity") {
        val engine     = TestProjectionEngine.make
        val insertSpec = ProjectionSpec[User]("users")
          .on[UserCreated]
          .insert((e, ctx) => User(ctx.entityId, e.name, e.email, 0L, 0L, active = true))
        val deleteSpec = ProjectionSpec[User]("users").on[UserDeleted].delete
        for {
          store <- InMemoryProjectionStore.make[User]
          _     <- engine.processAndApply(
                 insertSpec,
                 UserCreated("ToDelete", "d@b.com"),
                 TestContext.make(entityId = "del"),
                 store
               )
          _   <- engine.processEvents(deleteSpec, List((UserDeleted(), TestContext.make(entityId = "del"))), store)
          res <- store.findById("del")
        } yield assertTrue(res.isEmpty)
      },
      test("batch truncate clears all") {
        val engine     = TestProjectionEngine.make
        val insertSpec = ProjectionSpec[User]("users")
          .on[UserCreated]
          .insert((e, ctx) => User(ctx.entityId, e.name, e.email, 0L, 0L, active = true))
        val truncateSpec = ProjectionSpec[User]("users").on[UserDeleted].custom((_, _) => ProjectionAction.Truncate)
        for {
          store <- InMemoryProjectionStore.make[User]
          _     <- ZIO.foreachDiscard(List("1", "2", "3"))(id =>
                 engine.processAndApply(
                   insertSpec,
                   UserCreated(s"N$id", "a@b.com"),
                   TestContext.make(entityId = id),
                   store
                 )
               )
          _  <- engine.processEvents(truncateSpec, List((UserDeleted(), TestContext.make(entityId = "any"))), store)
          r1 <- store.findById("1")
          r2 <- store.findById("2")
          r3 <- store.findById("3")
        } yield assertTrue(r1.isEmpty, r2.isEmpty, r3.isEmpty)
      },
      test("batch mixed insert and update sequence") {
        val engine     = TestProjectionEngine.make
        val insertSpec = ProjectionSpec[User]("users")
          .on[UserCreated]
          .insert((e, ctx) => User(ctx.entityId, e.name, e.email, 0L, 0L, active = true))
        val renameSpec = ProjectionSpec[User]("users")
          .on[UserRenamed]
          .update(_.name)((e, _) => e.newName)
        for {
          store <- InMemoryProjectionStore.make[User]
          _     <- engine.processAndApply(
                 insertSpec,
                 UserCreated("Alice", "a@b.com"),
                 TestContext.make(entityId = "u1"),
                 store
               )
          _   <- engine.processEvents(renameSpec, List((UserRenamed("Alicia"), TestContext.make(entityId = "u1"))), store)
          res <- store.findById("u1")
        } yield assertTrue(res.exists(_.name == "Alicia"))
      },
      test("processEventsWithActions returns list of actions") {
        val engine = TestProjectionEngine.make
        val spec   = ProjectionSpec[User]("users")
          .on[UserCreated]
          .insert((e, ctx) => User(ctx.entityId, e.name, e.email, 0L, 0L, active = true))
          .on[UserDeleted]
          .delete
        val events: List[(Any, ProjectionContext)] = List(
          (UserCreated("A", "a@b.com"): Any, TestContext.make(entityId = "1")),
          (UserDeleted(): Any, TestContext.make(entityId = "1"))
        )
        // Use genericAny spec dispatch via Any
        val anySpec = spec.asInstanceOf[ProjectionSpec[User]]
        for {
          actions <- engine.processEventsWithActions[Any, User](anySpec, events)
        } yield assertTrue(
          actions.size == 2,
          actions.head.isInstanceOf[ProjectionAction.Insert[?]],
          actions(1) == ProjectionAction.Delete
        )
      },
      test("processEvents empty list no-op") {
        val engine = TestProjectionEngine.make
        val spec   = ProjectionSpec[User]("users").on[UserDeleted].delete
        for {
          store <- InMemoryProjectionStore.make[User]
          _     <- engine.processEvents(spec, List.empty[(UserDeleted, ProjectionContext)], store)
          res   <- store.findById("any")
        } yield assertTrue(res.isEmpty)
      },
      test("processAndApply returns action and mutates store") {
        val engine = TestProjectionEngine.make
        val spec   = ProjectionSpec[Counter]("counters")
          .on[CountInc]
          .custom((e, ctx) => ProjectionAction.Insert(Counter(ctx.entityId, e.by)))
        for {
          store  <- InMemoryProjectionStore.make[Counter]
          action <- engine.processAndApply(spec, CountInc(42L), TestContext.make(entityId = "c1"), store)
          res    <- store.findById("c1")
        } yield assertTrue(action == ProjectionAction.Insert(Counter("c1", 42L)), res.exists(_.total == 42L))
      }
    ),
    suite("engine synchronous no Hub")(
      test("sequential batch preserves order") {
        val engine = TestProjectionEngine.make
        val spec   = ProjectionSpec[Counter]("counters")
          .on[CountInc]
          .custom((e, ctx) => ProjectionAction.Insert(Counter(ctx.entityId + e.by.toString, e.by)))
        val events = (1 to 5).toList.map(i => (CountInc(i.toLong), TestContext.make(entityId = s"id-$i")))
        for {
          store <- InMemoryProjectionStore.make[Counter]
          _     <- engine.processEvents(spec, events, store)
          r     <- store.findById("id-3" + "3")
        } yield assertTrue(r.isDefined)
      },
      test("noop event does not modify store") {
        val engine = TestProjectionEngine.make
        val spec   = ProjectionSpec[User]("users").on[UserCreated].custom((_, _) => ProjectionAction.Noop)
        for {
          store <- InMemoryProjectionStore.make[User]
          _     <- store.insert(User("keep", "Keep", "k@b.com", 1L, 1L, active = true))
          _     <-
            engine.processEvents(spec, List((UserCreated("X", "x@b.com"), TestContext.make(entityId = "keep"))), store)
          res <- store.findById("keep")
        } yield assertTrue(res.exists(_.name == "Keep"))
      }
    )
  )
}
