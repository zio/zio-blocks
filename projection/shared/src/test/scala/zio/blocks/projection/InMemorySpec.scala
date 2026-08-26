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

object InMemorySpec extends ZIOSpecDefault {

  // ---------------------------------------------------------------------------
  // Test models
  // ---------------------------------------------------------------------------

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

  case class SnakeEntity(
    @Modifier.id userId: String,
    firstName: String,
    count: Long
  )
  object SnakeEntity {
    implicit val schema: Schema[SnakeEntity]         = Schema.derived[SnakeEntity]
    implicit val entityPath: EntityPath[SnakeEntity] = EntityPath.derived[SnakeEntity]
  }

  case class TypesEntity(
    @Modifier.id id: String,
    intVal: Int,
    longVal: Long,
    doubleVal: Double,
    boolVal: Boolean,
    textVal: String
  )
  object TypesEntity {
    implicit val schema: Schema[TypesEntity]         = Schema.derived[TypesEntity]
    implicit val entityPath: EntityPath[TypesEntity] = EntityPath.derived[TypesEntity]
  }

  case class OptionalEntity(
    @Modifier.id id: String,
    name: String,
    maybe: Option[String],
    optInt: Option[Int]
  )
  object OptionalEntity {
    implicit val schema: Schema[OptionalEntity]         = Schema.derived[OptionalEntity]
    implicit val entityPath: EntityPath[OptionalEntity] = EntityPath.derived[OptionalEntity]
  }

  case class UserCreated(name: String, email: String)
  object UserCreated {
    implicit val schema: Schema[UserCreated] = Schema.derived[UserCreated]
  }

  case class UserRenamed(newName: String)
  object UserRenamed {
    implicit val schema: Schema[UserRenamed] = Schema.derived[UserRenamed]
  }

  case class CountInc(by: Long)
  object CountInc {
    implicit val schema: Schema[CountInc] = Schema.derived[CountInc]
  }

  private def withStore[A: Schema: EntityPath](
    f: InMemoryProjectionStore[A] => Task[TestResult]
  ): Task[TestResult] =
    InMemoryProjectionStore.make[A].flatMap(f)

  def spec: Spec[TestEnvironment, Any] = suite("InMemorySpec")(
    suite("insert and findById")(
      test("insert and findById round-trip") {
        withStore[User] { store =>
          val u = User("u1", "Alice", "alice@example.com", 25L, 50L, active = true)
          for {
            _     <- store.insert(u)
            found <- store.findById("u1")
          } yield assertTrue(found.contains(u))
        }
      },
      test("insert multiple and find each") {
        withStore[User] { store =>
          val u1 = User("u1", "A", "a@b.com", 10L, 1L, active = true)
          val u2 = User("u2", "B", "b@b.com", 20L, 2L, active = false)
          val u3 = User("u3", "C", "c@b.com", 30L, 3L, active = true)
          for {
            _  <- store.insert(u1)
            _  <- store.insert(u2)
            _  <- store.insert(u3)
            r1 <- store.findById("u1")
            r2 <- store.findById("u2")
            r3 <- store.findById("u3")
          } yield assertTrue(r1.contains(u1), r2.contains(u2), r3.contains(u3))
        }
      },
      test("findById returns None when not exists") {
        withStore[User] { store =>
          for {
            _   <- store.insert(User("exists", "X", "x@b.com", 1L, 1L, active = true))
            res <- store.findById("missing")
          } yield assertTrue(res.isEmpty)
        }
      },
      test("findById after delete returns None") {
        withStore[User] { store =>
          val u = User("del", "Del", "d@b.com", 5L, 5L, active = true)
          for {
            _   <- store.insert(u)
            _   <- store.delete("del")
            res <- store.findById("del")
          } yield assertTrue(res.isEmpty)
        }
      },
      test("insert duplicate fails") {
        withStore[User] { store =>
          val u = User("dup", "Dup", "d@b.com", 1L, 1L, active = true)
          for {
            _   <- store.insert(u)
            res <- store.insert(u).either
          } yield assertTrue(res.isLeft)
        }
      },
      test("upsert inserts when not exists") {
        withStore[User] { store =>
          val u = User("new", "New", "n@b.com", 10L, 10L, active = true)
          for {
            _   <- store.upsert(u)
            res <- store.findById("new")
          } yield assertTrue(res.contains(u))
        }
      },
      test("upsert updates when exists") {
        withStore[User] { store =>
          val u1 = User("u1", "Old", "old@b.com", 10L, 10L, active = true)
          val u2 = User("u1", "New", "new@b.com", 99L, 99L, active = false)
          for {
            _   <- store.insert(u1)
            _   <- store.upsert(u2)
            res <- store.findById("u1")
          } yield assertTrue(res.contains(u2), res.get.name == "New", res.get.age == 99L)
        }
      }
    ),
    suite("delete and truncate")(
      test("delete removes existing") {
        withStore[User] { store =>
          val u = User("todelete", "T", "t@b.com", 1L, 1L, active = true)
          for {
            _   <- store.insert(u)
            _   <- store.delete("todelete")
            res <- store.findById("todelete")
          } yield assertTrue(res.isEmpty)
        }
      },
      test("delete non-existent is no-op") {
        withStore[User] { store =>
          val u = User("keep", "Keep", "k@b.com", 1L, 1L, active = true)
          for {
            _   <- store.insert(u)
            _   <- store.delete("nonexistent")
            res <- store.findById("keep")
          } yield assertTrue(res.contains(u))
        }
      },
      test("truncate removes all rows") {
        withStore[User] { store =>
          for {
            _  <- store.insert(User("1", "A", "a@b.com", 1L, 1L, active = true))
            _  <- store.insert(User("2", "B", "b@b.com", 2L, 2L, active = true))
            _  <- store.insert(User("3", "C", "c@b.com", 3L, 3L, active = false))
            _  <- store.truncate
            r1 <- store.findById("1")
            r2 <- store.findById("2")
            r3 <- store.findById("3")
          } yield assertTrue(r1.isEmpty, r2.isEmpty, r3.isEmpty)
        }
      },
      test("truncate on empty is no-op") {
        withStore[User] { store =>
          for {
            _   <- store.truncate
            res <- store.findById("any")
          } yield assertTrue(res.isEmpty)
        }
      },
      test("insert after truncate works") {
        withStore[User] { store =>
          val u = User("after", "After", "a@b.com", 1L, 1L, active = true)
          for {
            _   <- store.insert(User("before", "Before", "b@b.com", 1L, 1L, active = true))
            _   <- store.truncate
            _   <- store.insert(u)
            res <- store.findById("after")
          } yield assertTrue(res.contains(u))
        }
      }
    ),
    suite("updateFields - FieldUpdate variants")(
      test("Set single field string") {
        withStore[User] { store =>
          val u = User("1", "Alice", "a@b.com", 10L, 100L, active = true)
          for {
            _   <- store.insert(u)
            _   <- store.updateFields("1", Chunk(FieldUpdate.Set("name", "Bob")))
            res <- store.findById("1")
          } yield assertTrue(res.exists(_.name == "Bob"), res.exists(_.email == "a@b.com"))
        }
      },
      test("Set multiple fields in one call") {
        withStore[User] { store =>
          val u = User("1", "Alice", "a@b.com", 10L, 100L, active = true)
          for {
            _ <- store.insert(u)
            _ <- store.updateFields(
                   "1",
                   Chunk(FieldUpdate.Set("name", "Carol"), FieldUpdate.Set("email", "carol@b.com"))
                 )
            res <- store.findById("1")
          } yield assertTrue(res.exists(_.name == "Carol"), res.exists(_.email == "carol@b.com"))
        }
      },
      test("Set boolean field") {
        withStore[User] { store =>
          val u = User("1", "A", "a@b.com", 1L, 1L, active = true)
          for {
            _   <- store.insert(u)
            _   <- store.updateFields("1", Chunk(FieldUpdate.Set("active", false)))
            res <- store.findById("1")
          } yield assertTrue(res.exists(_.active == false))
        }
      },
      test("Increment field by positive") {
        withStore[User] { store =>
          val u = User("1", "A", "a@b.com", 10L, 50L, active = true)
          for {
            _   <- store.insert(u)
            _   <- store.updateFields("1", Chunk(FieldUpdate.Increment("age", 5L)))
            res <- store.findById("1")
          } yield assertTrue(res.exists(_.age == 15L))
        }
      },
      test("Increment multiple times") {
        withStore[User] { store =>
          val u = User("1", "A", "a@b.com", 0L, 0L, active = true)
          for {
            _   <- store.insert(u)
            _   <- store.updateFields("1", Chunk(FieldUpdate.Increment("score", 3L)))
            _   <- store.updateFields("1", Chunk(FieldUpdate.Increment("score", 7L)))
            res <- store.findById("1")
          } yield assertTrue(res.exists(_.score == 10L))
        }
      },
      test("Decrement field") {
        withStore[User] { store =>
          val u = User("1", "A", "a@b.com", 20L, 100L, active = true)
          for {
            _   <- store.insert(u)
            _   <- store.updateFields("1", Chunk(FieldUpdate.Decrement("age", 7L)))
            res <- store.findById("1")
          } yield assertTrue(res.exists(_.age == 13L))
        }
      },
      test("Max updates when greater") {
        withStore[User] { store =>
          val u = User("1", "A", "a@b.com", 10L, 50L, active = true)
          for {
            _   <- store.insert(u)
            _   <- store.updateFields("1", Chunk(FieldUpdate.Max("score", 100L)))
            res <- store.findById("1")
          } yield assertTrue(res.exists(_.score == 100L))
        }
      },
      test("Max does not update when smaller") {
        withStore[User] { store =>
          val u = User("1", "A", "a@b.com", 10L, 50L, active = true)
          for {
            _   <- store.insert(u)
            _   <- store.updateFields("1", Chunk(FieldUpdate.Max("score", 10L)))
            res <- store.findById("1")
          } yield assertTrue(res.exists(_.score == 50L))
        }
      },
      test("Min updates when smaller") {
        withStore[User] { store =>
          val u = User("1", "A", "a@b.com", 10L, 50L, active = true)
          for {
            _   <- store.insert(u)
            _   <- store.updateFields("1", Chunk(FieldUpdate.Min("score", 10L)))
            res <- store.findById("1")
          } yield assertTrue(res.exists(_.score == 10L))
        }
      },
      test("Min does not update when greater") {
        withStore[User] { store =>
          val u = User("1", "A", "a@b.com", 10L, 50L, active = true)
          for {
            _   <- store.insert(u)
            _   <- store.updateFields("1", Chunk(FieldUpdate.Min("score", 99L)))
            res <- store.findById("1")
          } yield assertTrue(res.exists(_.score == 50L))
        }
      },
      test("empty Chunk is no-op") {
        withStore[User] { store =>
          val u = User("1", "A", "a@b.com", 10L, 10L, active = true)
          for {
            _   <- store.insert(u)
            _   <- store.updateFields("1", Chunk.empty)
            res <- store.findById("1")
          } yield assertTrue(res.contains(u))
        }
      },
      test("updateFields non-existent entity is no-op") {
        withStore[User] { store =>
          val u = User("1", "A", "a@b.com", 10L, 10L, active = true)
          for {
            _   <- store.insert(u)
            _   <- store.updateFields("missing", Chunk(FieldUpdate.Set("name", "X")))
            res <- store.findById("1")
          } yield assertTrue(res.contains(u))
        }
      },
      test("mixed variants Set + Increment + Max + Min + Decrement") {
        withStore[User] { store =>
          val u = User("1", "Orig", "o@b.com", 10L, 50L, active = true)
          for {
            _ <- store.insert(u)
            _ <- store.updateFields(
                   "1",
                   Chunk(
                     FieldUpdate.Set("name", "Mixed"),
                     FieldUpdate.Increment("age", 5L),
                     FieldUpdate.Decrement("score", 10L),
                     FieldUpdate.Max("age", 20L),
                     FieldUpdate.Min("score", 100L)
                   )
                 )
            res <- store.findById("1")
          } yield assertTrue(
            res.exists(_.name == "Mixed"),
            res.exists(_.age == 20L),
            res.exists(_.score == 40L)
          )
        }
      },
      test("selector macro snake_case mapping increment") {
        withStore[User] { store =>
          val u  = User("1", "A", "a@b.com", 0L, 10L, active = true)
          val fu = FieldUpdate.increment[User](_.score, 5L)
          for {
            _   <- store.insert(u)
            _   <- store.updateFields("1", Chunk(fu))
            res <- store.findById("1")
          } yield assertTrue(fu.field == "score", res.exists(_.score == 15L))
        }
      },
      test("snake_case entity id mapping for SnakeEntity") {
        withStore[SnakeEntity] { store =>
          val e = SnakeEntity("s1", "John", 10L)
          for {
            _   <- store.insert(e)
            _   <- store.updateFields("s1", Chunk(FieldUpdate.Set("first_name", "Jane")))
            res <- store.findById("s1")
          } yield assertTrue(res.exists(_.firstName == "Jane"))
        }
      },
      test("Increment with snake_case field first_name via mapper") {
        withStore[SnakeEntity] { store =>
          val e  = SnakeEntity("s1", "John", 5L)
          val fu = FieldUpdate.increment[SnakeEntity](_.count, 3L)
          for {
            _   <- store.insert(e)
            _   <- store.updateFields("s1", Chunk(fu))
            res <- store.findById("s1")
          } yield assertTrue(res.exists(_.count == 8L))
        }
      }
    ),
    suite("meta operations")(
      test("getLastProcessedSeq initially 0") {
        withStore[User] { store =>
          for {
            seq <- store.getLastProcessedSeq
          } yield assertTrue(seq == 0L)
        }
      },
      test("updateLastProcessedSeq and get round-trip") {
        withStore[User] { store =>
          for {
            _   <- store.updateLastProcessedSeq(42L)
            seq <- store.getLastProcessedSeq
          } yield assertTrue(seq == 42L)
        }
      },
      test("updateLastProcessedSeq overwrite") {
        withStore[User] { store =>
          for {
            _   <- store.updateLastProcessedSeq(10L)
            _   <- store.updateLastProcessedSeq(99L)
            seq <- store.getLastProcessedSeq
          } yield assertTrue(seq == 99L)
        }
      },
      test("getSchemaHash initially None") {
        withStore[User] { store =>
          for {
            h <- store.getSchemaHash
          } yield assertTrue(h.isEmpty)
        }
      },
      test("updateSchemaHash and get round-trip") {
        withStore[User] { store =>
          for {
            _ <- store.updateSchemaHash("hash-abc")
            h <- store.getSchemaHash
          } yield assertTrue(h.contains("hash-abc"))
        }
      },
      test("updateSchemaHash overwrite") {
        withStore[User] { store =>
          for {
            _ <- store.updateSchemaHash("first")
            _ <- store.updateSchemaHash("second")
            h <- store.getSchemaHash
          } yield assertTrue(h.contains("second"))
        }
      },
      test("schema hash and seq independent") {
        withStore[User] { store =>
          for {
            _    <- store.updateLastProcessedSeq(123L)
            _    <- store.updateSchemaHash("myhash")
            seq  <- store.getLastProcessedSeq
            h    <- store.getSchemaHash
            _    <- store.updateLastProcessedSeq(999L)
            seq2 <- store.getLastProcessedSeq
            h2   <- store.getSchemaHash
          } yield assertTrue(seq == 123L, h.contains("myhash"), seq2 == 999L, h2.contains("myhash"))
        }
      },
      test("meta survives truncate") {
        withStore[User] { store =>
          for {
            _   <- store.insert(User("1", "A", "a@b.com", 1L, 1L, active = true))
            _   <- store.updateLastProcessedSeq(55L)
            _   <- store.updateSchemaHash("keepme")
            _   <- store.truncate
            seq <- store.getLastProcessedSeq
            h   <- store.getSchemaHash
            res <- store.findById("1")
          } yield assertTrue(seq == 55L, h.contains("keepme"), res.isEmpty)
        }
      },
      test("TestContext factory defaults") {
        val ctx = TestContext.make()
        assertTrue(ctx.entityId == "test-1", ctx.seq == 0L, ctx.sourceEntityId.isEmpty)
      },
      test("TestContext factory custom") {
        val ts  = Instant.parse("2024-01-01T00:00:00Z")
        val ctx = TestContext.make(entityId = "my-id", seq = 42L, timestamp = ts, sourceEntityId = Some("src"))
        assertTrue(ctx.entityId == "my-id", ctx.seq == 42L, ctx.timestamp == ts, ctx.sourceEntityId.contains("src"))
      }
    ),
    suite("InMemory via TestProjectionEngine")(
      test("engine processes insert and applies to store") {
        val engine = TestProjectionEngine.make
        val spec   = ProjectionSpec[User]("users")
          .on[UserCreated]
          .insert((e, ctx) => User(ctx.entityId, e.name, e.email, 0L, 0L, active = true))
        val ctx = TestContext.make(entityId = "e1")
        for {
          store <- InMemoryProjectionStore.make[User]
          _     <- engine.processAndApply(spec, UserCreated("Alice", "a@b.com"), ctx, store)
          res   <- store.findById("e1")
        } yield assertTrue(res.exists(_.name == "Alice"))
      },
      test("engine batch processes multiple events") {
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
      }
    )
  )
}
