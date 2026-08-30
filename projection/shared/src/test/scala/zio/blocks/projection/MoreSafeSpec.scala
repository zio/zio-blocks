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
import zio.blocks.projection.testing.InMemoryProjectionStore
import zio.blocks.schema.{Modifier, Schema}

object MoreSafeSpec extends ZIOSpecDefault {

  case class User(
    @Modifier.id id: String,
    name: String,
    age: Long
  )
  object User {
    implicit val schema: Schema[User]         = Schema.derived[User]
    implicit val entityPath: EntityPath[User] = EntityPath.derived[User]
  }
  case class UserCreated(name: String)
  object UserCreated { implicit val schema: Schema[UserCreated] = Schema.derived[UserCreated] }

  case class WithInt(
    @Modifier.id id: String,
    ival: Int
  )
  object WithInt {
    implicit val schema: Schema[WithInt]         = Schema.derived[WithInt]
    implicit val entityPath: EntityPath[WithInt] = EntityPath.derived[WithInt]
  }
  case class WithLong(
    @Modifier.id id: String,
    lval: Long
  )
  object WithLong {
    implicit val schema: Schema[WithLong]         = Schema.derived[WithLong]
    implicit val entityPath: EntityPath[WithLong] = EntityPath.derived[WithLong]
  }
  case class WithDouble(
    @Modifier.id id: String,
    dval: Double
  )
  object WithDouble {
    implicit val schema: Schema[WithDouble]         = Schema.derived[WithDouble]
    implicit val entityPath: EntityPath[WithDouble] = EntityPath.derived[WithDouble]
  }

  def spec: Spec[TestEnvironment, Any] = suite("MoreSafeSpec")(
    test("InMemory insert/find for User") {
      for {
        store <- InMemoryProjectionStore.make[User]
        _     <- store.insert(User("u1", "Alice", 10L))
        f     <- store.findById("u1")
      } yield assertTrue(f.exists(_.name == "Alice"))
    },
    test("InMemory upsert") {
      for {
        store <- InMemoryProjectionStore.make[User]
        _     <- store.insert(User("u1", "Alice", 10L))
        _     <- store.upsert(User("u1", "Bob", 20L))
        f     <- store.findById("u1")
      } yield assertTrue(f.exists(_.name == "Bob"))
    },
    test("InMemory delete") {
      for {
        store <- InMemoryProjectionStore.make[User]
        _     <- store.insert(User("u1", "Alice", 10L))
        _     <- store.delete("u1")
        f     <- store.findById("u1")
      } yield assertTrue(f.isEmpty)
    },
    test("InMemory truncate") {
      for {
        store <- InMemoryProjectionStore.make[User]
        _     <- store.insert(User("u1", "Alice", 10L))
        _     <- store.truncate
        f     <- store.findById("u1")
      } yield assertTrue(f.isEmpty)
    },
    test("InMemory updateFields Set") {
      for {
        store <- InMemoryProjectionStore.make[User]
        _     <- store.insert(User("u1", "Alice", 10L))
        _     <- store.updateFields("u1", Chunk(FieldUpdate.Set("name", "Bob")))
        f     <- store.findById("u1")
      } yield assertTrue(f.exists(_.name == "Bob"))
    },
    test("InMemory Increment Long") {
      for {
        store <- InMemoryProjectionStore.make[User]
        _     <- store.insert(User("u1", "Alice", 10L))
        _     <- store.updateFields("u1", Chunk(FieldUpdate.Increment("age", 5L)))
        f     <- store.findById("u1")
      } yield assertTrue(f.exists(_.age == 15L))
    },
    test("InMemory Decrement Long") {
      for {
        store <- InMemoryProjectionStore.make[User]
        _     <- store.insert(User("u1", "Alice", 10L))
        _     <- store.updateFields("u1", Chunk(FieldUpdate.Decrement("age", 3L)))
        f     <- store.findById("u1")
      } yield assertTrue(f.exists(_.age == 7L))
    },
    test("InMemory Max") {
      for {
        store <- InMemoryProjectionStore.make[User]
        _     <- store.insert(User("u1", "Alice", 10L))
        _     <- store.updateFields("u1", Chunk(FieldUpdate.Max("age", 20L)))
        f     <- store.findById("u1")
      } yield assertTrue(f.exists(_.age == 20L))
    },
    test("InMemory Min") {
      for {
        store <- InMemoryProjectionStore.make[User]
        _     <- store.insert(User("u1", "Alice", 20L))
        _     <- store.updateFields("u1", Chunk(FieldUpdate.Min("age", 5L)))
        f     <- store.findById("u1")
      } yield assertTrue(f.exists(_.age == 5L))
    },
    test("InMemory Increment Int") {
      for {
        store <- InMemoryProjectionStore.make[WithInt]
        _     <- store.insert(WithInt("i1", 10))
        _     <- store.updateFields("i1", Chunk(FieldUpdate.Increment("ival", 5L)))
        f     <- store.findById("i1")
      } yield assertTrue(f.exists(_.ival == 15))
    },
    test("InMemory Increment Long field") {
      for {
        store <- InMemoryProjectionStore.make[WithLong]
        _     <- store.insert(WithLong("l1", 10L))
        _     <- store.updateFields("l1", Chunk(FieldUpdate.Increment("lval", 5L)))
        f     <- store.findById("l1")
      } yield assertTrue(f.exists(_.lval == 15L))
    },
    test("InMemory Increment Double") {
      for {
        store <- InMemoryProjectionStore.make[WithDouble]
        _     <- store.insert(WithDouble("d1", 10.0))
        _     <- store.updateFields("d1", Chunk(FieldUpdate.Increment("dval", 5L)))
        f     <- store.findById("d1")
      } yield assertTrue(f.exists(_.dval == 15.0))
    },
    test("InMemory missing entity via update creates default") {
      for {
        store <- InMemoryProjectionStore.make[User]
        _     <- store.updateFields("missing", Chunk(FieldUpdate.Set("name", "Created")))
        f     <- store.findById("missing")
      } yield assertTrue(f.exists(_.name == "Created"))
    },
    test("Projection validate empty") {
      val p = Projection[User]("empty")
      assertTrue(p.validate().nonEmpty)
    },
    test("Projection dispatch") {
      val p   = Projection[User]("p").on[UserCreated].insert((_, ctx) => User(ctx.entityId, "x", 1L))
      val ctx = ProjectionContext("e1", java.time.Instant.now(), 1L)
      assertTrue(p.dispatch(UserCreated("A"), ctx).isDefined)
    },
    test("TransactorCache LRU") {
      for {
        c  <- TransactorCache.makeUnscoped(2)
        _  <- c.get("/tmp/more-lru1.db")
        _  <- c.get("/tmp/more-lru2.db")
        _  <- c.get("/tmp/more-lru3.db")
        sz <- c.size
        _  <- c.close
      } yield assertTrue(sz == 2)
    },
    test("SchemaHash compute") {
      val h1 = SchemaHash.compute[User]
      val h2 = SchemaHash.compute[WithInt]
      assertTrue(h1.nonEmpty, h2.nonEmpty, h1 != h2)
    },
    test("TagResolver currentTags") {
      val tags = TagResolver.currentTags[UserCreated]
      assertTrue(tags.contains("UserCreated"))
    },
    test("FieldUpdate helpers") {
      assertTrue(FieldUpdate.increment("cnt", 1L) == FieldUpdate.Increment("cnt", 1L))
      assertTrue(AggregateProjection.inc("cnt") == FieldUpdate.Increment("cnt", 1L))
    },
    test("Chunk single") {
      val c = Chunk.single(42)
      assertTrue(c.size == 1)
    },
    test("ProjectionContext") {
      val c = ProjectionContext("e1", java.time.Instant.now(), 1L)
      assertTrue(c.entityId == "e1")
    },
    test("EntityPath derived") {
      val ep = EntityPath.derived[User]
      assertTrue(ep.entityIdField == "id")
    },
    test("ProjectionEngine boundedHub") {
      for {
        hub <- ProjectionEngine.boundedHub(8)
        sz  <- hub.size
      } yield assertTrue(sz == 0)
    },
    test("InMemory getLastSeq") {
      for {
        store <- InMemoryProjectionStore.make[User]
        s0    <- store.getLastProcessedSeq
        _     <- store.updateLastProcessedSeq(5L)
        s1    <- store.getLastProcessedSeq
      } yield assertTrue(s0 == 0L, s1 == 5L)
    },
    test("InMemory getSchemaHash") {
      for {
        store <- InMemoryProjectionStore.make[User]
        h0    <- store.getSchemaHash
        _     <- store.updateSchemaHash("h1")
        h1    <- store.getSchemaHash
      } yield assertTrue(h0.isEmpty, h1.contains("h1"))
    },
    test("InMemory addColumn no-op") {
      for {
        store <- InMemoryProjectionStore.make[User]
        res1  <- store.addColumn("col", "TEXT").either
        res2  <- store.addColumn("", "").either
        _     <- store.insert(User("u1", "Alice", 10L))
        f     <- store.findById("u1")
        seq   <- store.getLastProcessedSeq
      } yield assertTrue(res1.isRight, res2.isRight, f.exists(_.name == "Alice"), seq == 0L)
    },
    test("Projection routingKey") {
      val p =
        Projection[User]("rk").from("src").routeToSelf.on[UserCreated].insert((_, ctx) => User(ctx.entityId, "x", 1L))
      val k = p.routingKey(UserCreated("A"), ProjectionContext("e1", java.time.Instant.now(), 1L), "src")
      assertTrue(k.contains("e1"))
    },
    test("AggregateProjection") {
      val p = AggregateProjection.globalWithCounters[User]("agg")
      assertTrue(p.isGlobal)
    },
    test("InMemory Max/Min on Int") {
      for {
        store <- InMemoryProjectionStore.make[WithInt]
        _     <- store.insert(WithInt("i1", 5))
        _     <- store.updateFields("i1", Chunk(FieldUpdate.Max("ival", 10L)))
        _     <- store.updateFields("i1", Chunk(FieldUpdate.Min("ival", 2L)))
        f     <- store.findById("i1")
      } yield assertTrue(f.exists(_.ival == 2))
    },
    test("InMemory updateFields empty") {
      for {
        store <- InMemoryProjectionStore.make[User]
        res   <- store.updateFields("u1", Chunk.empty[FieldUpdate]).either
        f1    <- store.findById("u1")
        _     <- store.insert(User("u2", "Bob", 20L))
        _     <- store.updateFields("u2", Chunk.empty[FieldUpdate]).either
        f2    <- store.findById("u2")
        seq   <- store.getLastProcessedSeq
        hash  <- store.getSchemaHash
      } yield assertTrue(res.isRight, f1.isEmpty, f2.exists(_.name == "Bob"), seq == 0L, hash.isEmpty)
    }
  )
}
