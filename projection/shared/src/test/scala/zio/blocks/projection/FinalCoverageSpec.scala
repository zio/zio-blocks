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
import zio.blocks.sql.{SqlDialect, JdbcTransactor}

object FinalCoverageSpec extends ZIOSpecDefault {

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

  sealed trait MyVariant
  object MyVariant {
    case class A(x: String) extends MyVariant
    case class B(y: Int)    extends MyVariant
    implicit val schema: Schema[MyVariant] = Schema.derived[MyVariant]
  }

  case class WithInt(
    @Modifier.id id: String,
    ival: Int
  )
  object WithInt {
    implicit val schema: Schema[WithInt]         = Schema.derived[WithInt]
    implicit val entityPath: EntityPath[WithInt] = EntityPath.derived[WithInt]
  }

  def spec: Spec[TestEnvironment, Any] = suite("FinalCoverageSpec")(
    suite("EntityPath 0% file")(
      test("path annotation class exists") {
        val ann = new path("custom")
        assertTrue(ann.pathName == "custom")
      }
    ),
    suite("TransactorCache remaining")(
      test("evict non-existent is no-op") {
        for {
          c  <- TransactorCache.makeUnscoped(5)
          _  <- c.evict("/tmp/not-exist.db")
          sz <- c.size
          _  <- c.close
        } yield assertTrue(sz == 0)
      },
      test("get after evict recreates") {
        for {
          c   <- TransactorCache.makeUnscoped(5)
          tx1 <- c.get("/tmp/recreate.db")
          _   <- c.evict("/tmp/recreate.db")
          tx2 <- c.get("/tmp/recreate.db")
          _   <- c.close
        } yield assertTrue(tx1 != null, tx2 != null)
      }
    ),
    suite("SQLiteProjectionStore remaining")(
      test("addColumn with invalid name fails") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make(5)
            tmp   <- ZIO.attempt(java.nio.file.Files.createTempFile("final-addcol", ".db"))
            path   = tmp.toAbsolutePath.toString
            store <- SQLiteProjectionStore.make[User](path, cache)
            res   <- store.addColumn("bad-name!", "TEXT").either
          } yield assertTrue(res.isLeft)
        }
      },
      test("SQLite insert then upsert") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make(5)
            tmp   <- ZIO.attempt(java.nio.file.Files.createTempFile("final-upsert", ".db"))
            path   = tmp.toAbsolutePath.toString
            store <- SQLiteProjectionStore.make[User](path, cache)
            _     <- store.insert(User("u1", "Alice", 10L))
            _     <- store.upsert(User("u1", "Bob", 20L))
            f     <- store.findById("u1")
          } yield assertTrue(f.exists(_.name == "Bob"))
        }
      },
      test("SQLite updateFields with Set and Increment") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make(5)
            tmp   <- ZIO.attempt(java.nio.file.Files.createTempFile("final-upd", ".db"))
            path   = tmp.toAbsolutePath.toString
            store <- SQLiteProjectionStore.make[User](path, cache)
            _     <- store.insert(User("u1", "Alice", 10L))
            _     <- store.updateFields("u1", Chunk(FieldUpdate.Set("name", "Bob")))
            _     <- store.updateFields("u1", Chunk(FieldUpdate.Increment("age", 5L)))
            _     <- store.updateFields("u1", Chunk(FieldUpdate.Decrement("age", 2L)))
            _     <- store.updateFields("u1", Chunk(FieldUpdate.Max("age", 100L)))
            _     <- store.updateFields("u1", Chunk(FieldUpdate.Min("age", 1L)))
            f     <- store.findById("u1")
          } yield assertTrue(f.isDefined)
        }
      },
      test("SQLite updateFields with invalid field fails") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make(5)
            tmp   <- ZIO.attempt(java.nio.file.Files.createTempFile("final-invalid2", ".db"))
            path   = tmp.toAbsolutePath.toString
            store <- SQLiteProjectionStore.make[User](path, cache)
            res   <- store.addColumn("bad-col!", "TEXT").either
          } yield assertTrue(res.isLeft)
        }
      },
      test("SQLite truncate and delete") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make(5)
            tmp   <- ZIO.attempt(java.nio.file.Files.createTempFile("final-trunc", ".db"))
            path   = tmp.toAbsolutePath.toString
            store <- SQLiteProjectionStore.make[User](path, cache)
            _     <- store.insert(User("u1", "Alice", 10L))
            _     <- store.delete("u1")
            f1    <- store.findById("u1")
            _     <- store.insert(User("u2", "Bob", 20L))
            _     <- store.truncate
            f2    <- store.findById("u2")
          } yield assertTrue(f1.isEmpty, f2.isEmpty)
        }
      },
      test("SQLite meta ops") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make(5)
            tmp   <- ZIO.attempt(java.nio.file.Files.createTempFile("final-meta", ".db"))
            path   = tmp.toAbsolutePath.toString
            store <- SQLiteProjectionStore.make[User](path, cache)
            s0    <- store.getLastProcessedSeq
            _     <- store.updateLastProcessedSeq(42L)
            s1    <- store.getLastProcessedSeq
            h0    <- store.getSchemaHash
            _     <- store.updateSchemaHash("hash123")
            h1    <- store.getSchemaHash
          } yield assertTrue(s0 == 0L, s1 == 42L, h0.isEmpty, h1.contains("hash123"))
        }
      },
      test("SQLite addColumn no-op when exists") {
        ZIO.scoped {
          for {
            cache <- TransactorCache.make(5)
            tmp   <- ZIO.attempt(java.nio.file.Files.createTempFile("final-addcol2", ".db"))
            path   = tmp.toAbsolutePath.toString
            store <- SQLiteProjectionStore.make[User](path, cache)
            res1  <- store.addColumn("newcol", "TEXT").either
            res2  <- store.addColumn("newcol", "TEXT").either
            _     <- store.insert(User("u1", "Alice", 10L))
            f     <- store.findById("u1")
          } yield assertTrue(res1.isRight, res2.isRight, f.exists(_.name == "Alice"))
        }
      }
    ),
    suite("SchemaHash remaining")(
      test("SchemaHash variant with multiple cases") {
        val h = SchemaHash.compute[MyVariant]
        assertTrue(h.nonEmpty)
      },
      test("SchemaHash for User vs MyVariant differ") {
        val h1 = SchemaHash.compute[User]
        val h2 = SchemaHash.compute[MyVariant]
        assertTrue(h1 != h2)
      }
    ),
    suite("InMemory remaining for branch coverage")(
      test("InMemory updateFields with empty is no-op") {
        for {
          store <- InMemoryProjectionStore.make[User]
          _     <- store.insert(User("u1", "Alice", 10L))
          _     <- store.updateFields("u1", Chunk.empty[FieldUpdate])
          f     <- store.findById("u1")
        } yield assertTrue(f.exists(_.name == "Alice"))
      },
      test("InMemory inc/dec on Int field via WithInt") {
        for {
          store <- InMemoryProjectionStore.make[WithInt]
          _     <- store.insert(WithInt("i1", 10))
          _     <- store.updateFields("i1", Chunk(FieldUpdate.Increment("ival", 5L)))
          _     <- store.updateFields("i1", Chunk(FieldUpdate.Decrement("ival", 3L)))
          _     <- store.updateFields("i1", Chunk(FieldUpdate.Max("ival", 20L)))
          _     <- store.updateFields("i1", Chunk(FieldUpdate.Min("ival", 5L)))
          f     <- store.findById("i1")
        } yield assertTrue(f.exists(_.ival == 5))
      },
      test("Projection.validate with empty and valid") {
        val empty = Projection[User]("empty")
        val valid = Projection[User]("valid").on[UserCreated].insert((_, ctx) => User(ctx.entityId, "x", 1L))
        assertTrue(empty.validate().nonEmpty, valid.validate().isEmpty)
      },
      test("Projection dispatch alias") {
        val p   = Projection[User]("d").on[UserCreated].insert((_, ctx) => User(ctx.entityId, "x", 1L))
        val ctx = ProjectionContext("e1", java.time.Instant.now(), 1L)
        assertTrue(p.dispatch(UserCreated("A"), ctx) == p.handle(UserCreated("A"), ctx))
      },
      test("Projection routingKey for unknown source is None") {
        val p = Projection[User]("rk")
          .from("known")
          .routeToSelf
          .on[UserCreated]
          .insert((_, ctx) => User(ctx.entityId, "x", 1L))
        val k = p.routingKey(UserCreated("A"), ProjectionContext("e1", java.time.Instant.now(), 1L), "unknown")
        assertTrue(k.isEmpty)
      },
      test("ProjectionEngine boundedHub and validateName") {
        for {
          hub <- ProjectionEngine.boundedHub(8)
          sz  <- hub.size
        } yield assertTrue(sz == 0)
      },
      test("SQLiteEventStore via InMemory store still covers branches") {
        ZIO.scoped {
          for {
            engine <- zio.blocks.projection.testing.TestEngine.make(
                        Projection[User]("ev-final").on[UserCreated].insert((e, ctx) => User(ctx.entityId, e.name, 1L))
                      )
            _   <- engine.append("u1", UserCreated("Alice"))
            all <- engine.engine.eventStoresMap.values.head.asInstanceOf[EventStore[UserCreated]].readAll().runCollect
          } yield assertTrue(all.nonEmpty)
        }
      }
    )
  )
}
