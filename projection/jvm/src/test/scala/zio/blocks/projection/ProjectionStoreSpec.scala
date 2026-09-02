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
import zio.blocks.schema.{Modifier, Schema}
import zio.blocks.sql.*

object ProjectionStoreSpec extends ZIOSpecDefault {

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

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private def tempFile(prefix: String = "proj"): Task[(String, java.nio.file.Path)] =
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
      try java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(path + "-journal"))
      catch { case _: Throwable => () }
    }

  private def withStore[A: Schema: EntityPath](
    f: (SQLiteProjectionStore[A], String, java.nio.file.Path, TransactorCache) => Task[TestResult]
  ): Task[TestResult] =
    ZIO.scoped {
      for {
        tmp            <- tempFile()
        (path, tmpPath) = tmp
        cache          <- TransactorCache.make()
        store          <- SQLiteProjectionStore.make[A](path, cache)
        result         <- f(store, path, tmpPath, cache).ensuring(cleanup(path, tmpPath))
      } yield result
    }

  private def withStoreSimple[A: Schema: EntityPath](
    f: SQLiteProjectionStore[A] => Task[TestResult]
  ): Task[TestResult] =
    withStore[A]((store, _, _, _) => f(store))

  private def tableExists(tx: Transactor, name: String): Boolean =
    tx.connect {
      val con = summon[DbCon]
      val ps  = con.connection.prepareStatement(
        "SELECT count(*) FROM sqlite_master WHERE type='table' AND name=?"
      )
      try {
        ps.paramWriter.setString(1, name)
        val rs = ps.executeQuery()
        try {
          if (rs.next()) rs.reader.getInt(1) > 0 else false
        } finally rs.close()
      } finally ps.close()
    }

  private def columnType(tx: Transactor, table: String, column: String): Option[String] =
    tx.connect {
      val con = summon[DbCon]
      val ps  = con.connection.prepareStatement(s"PRAGMA table_info($table)")
      try {
        val rs = ps.executeQuery()
        try {
          var result: Option[String] = None
          while (rs.next()) {
            val colName = rs.reader.getString(2)
            val colType = rs.reader.getString(3)
            if (colName == column) result = Some(colType)
          }
          result
        } finally rs.close()
      } finally ps.close()
    }

  // ---------------------------------------------------------------------------
  // Spec
  // ---------------------------------------------------------------------------

  def spec: Spec[TestEnvironment, Any] = suite("ProjectionStoreSpec")(
    suite("auto-create")(
      test("creates main table on first insert") {
        withStore[User] { (store, path, _, cache) =>
          for {
            _     <- store.insert(User("1", "Alice", "a@b.com", 30L, 100L, active = true))
            tx    <- cache.get(path)
            exists = tableExists(tx, "user")
          } yield assertTrue(exists)
        }
      },
      test("creates _projection_meta table on first operation") {
        withStore[User] { (store, path, _, cache) =>
          for {
            _     <- store.getLastProcessedSeq
            tx    <- cache.get(path)
            exists = tableExists(tx, "_projection_meta")
          } yield assertTrue(exists)
        }
      },
      test("auto-creates idempotent - second insert still works") {
        withStore[User] { (store, _, _, _) =>
          for {
            _ <- store.insert(User("1", "A", "a@b.com", 1L, 10L, active = true))
            _ <- store.insert(User("2", "B", "b@b.com", 2L, 20L, active = false))
            a <- store.findById("1")
            b <- store.findById("2")
          } yield assertTrue(a.isDefined, b.isDefined, a.get.name == "A", b.get.name == "B")
        }
      },
      test("findById before any insert creates tables and returns None") {
        withStore[User] { (store, path, _, cache) =>
          for {
            res <- store.findById("nonexistent")
            tx  <- cache.get(path)
            e1   = tableExists(tx, "user")
            e2   = tableExists(tx, "_projection_meta")
          } yield assertTrue(res.isEmpty, e1, e2)
        }
      }
    ),
    suite("insert and findById")(
      test("insert and findById round-trip") {
        withStoreSimple[User] { store =>
          val u = User("u1", "Alice", "alice@example.com", 25L, 50L, active = true)
          for {
            _     <- store.insert(u)
            found <- store.findById("u1")
          } yield assertTrue(found.contains(u))
        }
      },
      test("insert multiple and find each") {
        withStoreSimple[User] { store =>
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
        withStoreSimple[User] { store =>
          for {
            _   <- store.insert(User("exists", "X", "x@b.com", 1L, 1L, active = true))
            res <- store.findById("missing")
          } yield assertTrue(res.isEmpty)
        }
      },
      test("findById after delete returns None") {
        withStoreSimple[User] { store =>
          val u = User("del", "Del", "d@b.com", 5L, 5L, active = true)
          for {
            _   <- store.insert(u)
            _   <- store.delete("del")
            res <- store.findById("del")
          } yield assertTrue(res.isEmpty)
        }
      }
    ),
    suite("upsert")(
      test("upsert inserts when not exists") {
        withStoreSimple[User] { store =>
          val u = User("new", "New", "n@b.com", 10L, 10L, active = true)
          for {
            _   <- store.upsert(u)
            res <- store.findById("new")
          } yield assertTrue(res.contains(u))
        }
      },
      test("upsert updates when exists") {
        withStoreSimple[User] { store =>
          val u1 = User("u1", "Old", "old@b.com", 10L, 10L, active = true)
          val u2 = User("u1", "New", "new@b.com", 99L, 99L, active = false)
          for {
            _   <- store.insert(u1)
            _   <- store.upsert(u2)
            res <- store.findById("u1")
          } yield assertTrue(res.contains(u2), res.get.name == "New", res.get.age == 99L)
        }
      },
      test("upsert with multiple ids") {
        withStoreSimple[User] { store =>
          val u1 = User("a", "A", "a@b.com", 1L, 1L, active = true)
          val u2 = User("b", "B", "b@b.com", 2L, 2L, active = false)
          for {
            _  <- store.upsert(u1)
            _  <- store.upsert(u2)
            r1 <- store.findById("a")
            r2 <- store.findById("b")
          } yield assertTrue(r1.contains(u1), r2.contains(u2))
        }
      }
    ),
    suite("delete and truncate")(
      test("delete removes existing") {
        withStoreSimple[User] { store =>
          val u = User("todelete", "T", "t@b.com", 1L, 1L, active = true)
          for {
            _   <- store.insert(u)
            _   <- store.delete("todelete")
            res <- store.findById("todelete")
          } yield assertTrue(res.isEmpty)
        }
      },
      test("delete non-existent is no-op") {
        withStoreSimple[User] { store =>
          val u = User("keep", "Keep", "k@b.com", 1L, 1L, active = true)
          for {
            _   <- store.insert(u)
            _   <- store.delete("nonexistent")
            res <- store.findById("keep")
          } yield assertTrue(res.contains(u))
        }
      },
      test("truncate removes all rows") {
        withStoreSimple[User] { store =>
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
      test("truncate on empty table is no-op") {
        withStoreSimple[User] { store =>
          for {
            _   <- store.truncate
            res <- store.findById("any")
          } yield assertTrue(res.isEmpty)
        }
      },
      test("insert after truncate works") {
        withStoreSimple[User] { store =>
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
        withStoreSimple[User] { store =>
          val u = User("1", "Alice", "a@b.com", 10L, 100L, active = true)
          for {
            _   <- store.insert(u)
            _   <- store.updateFields("1", Chunk(FieldUpdate.Set("name", "Bob")))
            res <- store.findById("1")
          } yield assertTrue(res.exists(_.name == "Bob"), res.exists(_.email == "a@b.com"))
        }
      },
      test("Set multiple fields in one call") {
        withStoreSimple[User] { store =>
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
        withStoreSimple[User] { store =>
          val u = User("1", "A", "a@b.com", 1L, 1L, active = true)
          for {
            _   <- store.insert(u)
            _   <- store.updateFields("1", Chunk(FieldUpdate.Set("active", false)))
            res <- store.findById("1")
          } yield assertTrue(res.exists(_.active == false))
        }
      },
      test("Increment field by positive") {
        withStoreSimple[User] { store =>
          val u = User("1", "A", "a@b.com", 10L, 50L, active = true)
          for {
            _   <- store.insert(u)
            _   <- store.updateFields("1", Chunk(FieldUpdate.Increment("age", 5L)))
            res <- store.findById("1")
          } yield assertTrue(res.exists(_.age == 15L))
        }
      },
      test("Increment multiple times") {
        withStoreSimple[User] { store =>
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
        withStoreSimple[User] { store =>
          val u = User("1", "A", "a@b.com", 20L, 100L, active = true)
          for {
            _   <- store.insert(u)
            _   <- store.updateFields("1", Chunk(FieldUpdate.Decrement("age", 7L)))
            res <- store.findById("1")
          } yield assertTrue(res.exists(_.age == 13L))
        }
      },
      test("Decrement then Increment") {
        withStoreSimple[User] { store =>
          val u = User("1", "A", "a@b.com", 50L, 50L, active = true)
          for {
            _ <- store.insert(u)
            _ <- store.updateFields(
                   "1",
                   Chunk(FieldUpdate.Decrement("score", 20L), FieldUpdate.Increment("score", 10L))
                 )
            res <- store.findById("1")
          } yield assertTrue(res.exists(_.score == 40L))
        }
      },
      test("Max updates when greater") {
        withStoreSimple[User] { store =>
          val u = User("1", "A", "a@b.com", 10L, 50L, active = true)
          for {
            _   <- store.insert(u)
            _   <- store.updateFields("1", Chunk(FieldUpdate.Max("score", 100L)))
            res <- store.findById("1")
          } yield assertTrue(res.exists(_.score == 100L))
        }
      },
      test("Max does not update when smaller") {
        withStoreSimple[User] { store =>
          val u = User("1", "A", "a@b.com", 10L, 50L, active = true)
          for {
            _   <- store.insert(u)
            _   <- store.updateFields("1", Chunk(FieldUpdate.Max("score", 10L)))
            res <- store.findById("1")
          } yield assertTrue(res.exists(_.score == 50L))
        }
      },
      test("Max equal value keeps existing") {
        withStoreSimple[User] { store =>
          val u = User("1", "A", "a@b.com", 10L, 50L, active = true)
          for {
            _   <- store.insert(u)
            _   <- store.updateFields("1", Chunk(FieldUpdate.Max("score", 50L)))
            res <- store.findById("1")
          } yield assertTrue(res.exists(_.score == 50L))
        }
      },
      test("Min updates when smaller") {
        withStoreSimple[User] { store =>
          val u = User("1", "A", "a@b.com", 10L, 50L, active = true)
          for {
            _   <- store.insert(u)
            _   <- store.updateFields("1", Chunk(FieldUpdate.Min("score", 10L)))
            res <- store.findById("1")
          } yield assertTrue(res.exists(_.score == 10L))
        }
      },
      test("Min does not update when greater") {
        withStoreSimple[User] { store =>
          val u = User("1", "A", "a@b.com", 10L, 50L, active = true)
          for {
            _   <- store.insert(u)
            _   <- store.updateFields("1", Chunk(FieldUpdate.Min("score", 99L)))
            res <- store.findById("1")
          } yield assertTrue(res.exists(_.score == 50L))
        }
      },
      test("Min equal value keeps existing") {
        withStoreSimple[User] { store =>
          val u = User("1", "A", "a@b.com", 10L, 50L, active = true)
          for {
            _   <- store.insert(u)
            _   <- store.updateFields("1", Chunk(FieldUpdate.Min("score", 50L)))
            res <- store.findById("1")
          } yield assertTrue(res.exists(_.score == 50L))
        }
      },
      test("empty Chunk is no-op") {
        withStoreSimple[User] { store =>
          val u = User("1", "A", "a@b.com", 10L, 10L, active = true)
          for {
            _   <- store.insert(u)
            _   <- store.updateFields("1", Chunk.empty)
            res <- store.findById("1")
          } yield assertTrue(res.contains(u))
        }
      },
      test("updateFields non-existent entity is no-op no error") {
        withStoreSimple[User] { store =>
          val u = User("1", "A", "a@b.com", 10L, 10L, active = true)
          for {
            _   <- store.insert(u)
            _   <- store.updateFields("missing", Chunk(FieldUpdate.Set("name", "X")))
            res <- store.findById("1")
          } yield assertTrue(res.contains(u))
        }
      },
      test("mixed variants Set + Increment + Max + Min + Decrement in one chunk") {
        withStoreSimple[User] { store =>
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
            res.exists(_.age == 20L),  // 10+5=15 then MAX(15,20)=20
            res.exists(_.score == 40L) // 50-10=40 then MIN(40,100)=40
          )
        }
      },
      test("Set with numeric string value type conversion") {
        withStoreSimple[User] { store =>
          val u = User("1", "A", "a@b.com", 5L, 5L, active = true)
          for {
            _   <- store.insert(u)
            _   <- store.updateFields("1", Chunk(FieldUpdate.Set("age", 999L)))
            res <- store.findById("1")
          } yield assertTrue(res.exists(_.age == 999L))
        }
      },
      test("selector-based macro snake_case mapping increment") {
        withStoreSimple[User] { store =>
          val u  = User("1", "A", "a@b.com", 0L, 10L, active = true)
          val fu = FieldUpdate.increment[User](_.score, 5L)
          for {
            _   <- store.insert(u)
            _   <- store.updateFields("1", Chunk(fu))
            res <- store.findById("1")
          } yield assertTrue(fu.field == "score", res.exists(_.score == 15L))
        }
      }
    ),
    suite("meta operations")(
      test("getLastProcessedSeq initially 0") {
        withStoreSimple[User] { store =>
          for {
            seq <- store.getLastProcessedSeq
          } yield assertTrue(seq == 0L)
        }
      },
      test("updateLastProcessedSeq and get round-trip") {
        withStoreSimple[User] { store =>
          for {
            _   <- store.updateLastProcessedSeq(42L)
            seq <- store.getLastProcessedSeq
          } yield assertTrue(seq == 42L)
        }
      },
      test("updateLastProcessedSeq overwrite") {
        withStoreSimple[User] { store =>
          for {
            _   <- store.updateLastProcessedSeq(10L)
            _   <- store.updateLastProcessedSeq(99L)
            seq <- store.getLastProcessedSeq
          } yield assertTrue(seq == 99L)
        }
      },
      test("updateLastProcessedSeq multiple increments") {
        withStoreSimple[User] { store =>
          for {
            _ <- ZIO.foreachDiscard(1 to 5)(i => store.updateLastProcessedSeq(i.toLong))
            s <- store.getLastProcessedSeq
          } yield assertTrue(s == 5L)
        }
      },
      test("getSchemaHash initially None") {
        withStoreSimple[User] { store =>
          for {
            h <- store.getSchemaHash
          } yield assertTrue(h.isEmpty)
        }
      },
      test("updateSchemaHash and get round-trip") {
        withStoreSimple[User] { store =>
          for {
            _ <- store.updateSchemaHash("hash-abc")
            h <- store.getSchemaHash
          } yield assertTrue(h.contains("hash-abc"))
        }
      },
      test("updateSchemaHash overwrite") {
        withStoreSimple[User] { store =>
          for {
            _ <- store.updateSchemaHash("first")
            _ <- store.updateSchemaHash("second")
            h <- store.getSchemaHash
          } yield assertTrue(h.contains("second"))
        }
      },
      test("schema hash and seq independent") {
        withStoreSimple[User] { store =>
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
        withStoreSimple[User] { store =>
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
      }
    ),
    suite("DDL and EntityPath mapping")(
      test("table name derived snake_case singular") {
        withStore[User] { (store, path, _, cache) =>
          for {
            _         <- store.insert(User("1", "A", "a@b.com", 1L, 1L, active = true))
            tx        <- cache.get(path)
            existsUser = tableExists(tx, "user")
            notPlural  = !tableExists(tx, "users")
          } yield assertTrue(existsUser, notPlural)
        }
      },
      test("snake_case entity id column user_id for SnakeEntity") {
        withStore[SnakeEntity] { (store, path, _, cache) =>
          for {
            _           <- store.insert(SnakeEntity("1", "John", 10L))
            tx          <- cache.get(path)
            col          = columnType(tx, "snake_entity", "user_id")
            noId         = columnType(tx, "snake_entity", "userId")
            hasFirstName = columnType(tx, "snake_entity", "first_name")
          } yield assertTrue(col.isDefined, noId.isEmpty, hasFirstName.isDefined)
        }
      },
      test("type-aware DDL column types for TypesEntity") {
        withStore[TypesEntity] { (store, path, _, cache) =>
          for {
            _      <- store.insert(TypesEntity("1", 123, 999L, 3.14, boolVal = true, "hello"))
            tx     <- cache.get(path)
            tInt    = columnType(tx, "types_entity", "int_val")
            tLong   = columnType(tx, "types_entity", "long_val")
            tDouble = columnType(tx, "types_entity", "double_val")
            tBool   = columnType(tx, "types_entity", "bool_val")
            tText   = columnType(tx, "types_entity", "text_val")
            tId     = columnType(tx, "types_entity", "id")
          } yield assertTrue(
            tInt.exists(_.toUpperCase == "INTEGER"),
            tLong.exists(_.toUpperCase == "INTEGER"),
            tDouble.exists(_.toUpperCase == "REAL"),
            tBool.exists(_.toUpperCase == "INTEGER"),
            tText.exists(_.toUpperCase == "TEXT"),
            tId.exists(_.toUpperCase == "TEXT")
          )
        }
      },
      test("optional fields nullable via DDL") {
        withStore[OptionalEntity] { (store, path, _, cache) =>
          for {
            _  <- store.insert(OptionalEntity("1", "A", Some("present"), Some(42)))
            _  <- store.insert(OptionalEntity("2", "B", None, None))
            r1 <- store.findById("1")
            r2 <- store.findById("2")
            tx <- cache.get(path)
            // verify nullable columns allow null: pragma table_info notnull should be 0 for maybe fields
          } yield assertTrue(
            r1.exists(_.maybe.contains("present")),
            r2.exists(_.maybe.isEmpty),
            r2.exists(_.optInt.isEmpty)
          )
        }
      },
      test("Table.derived uses SqlDialect.SQLite type mapping") {
        val table = Table.derived[TypesEntity]
        val frag  = table.createTable(SqlDialect.SQLite)
        val sql   = frag.sql(SqlDialect.SQLite)
        assertTrue(
          sql.contains("INTEGER"),
          sql.contains("TEXT"),
          sql.contains("REAL"),
          sql.contains("types_entity")
        )
      }
    ),
    suite("concurrency and isolation")(
      test("parallel inserts 10 distinct ids") {
        withStoreSimple[User] { store =>
          for {
            _ <- ZIO.foreachParDiscard(1 to 10)(i =>
                   store.insert(User(s"id-$i", s"Name-$i", s"a$i@b.com", i.toLong, i.toLong, active = true))
                 )
            results <- ZIO.foreach(1 to 10)(i => store.findById(s"id-$i"))
          } yield assertTrue(results.forall(_.isDefined), results.size == 10)
        }
      },
      test("parallel increments on same entity via updateFields") {
        withStoreSimple[User] { store =>
          val u = User("counter", "C", "c@b.com", 0L, 0L, active = true)
          for {
            _ <- store.insert(u)
            _ <- ZIO.foreachParDiscard(1 to 10)(_ =>
                   store.updateFields("counter", Chunk(FieldUpdate.Increment("age", 1L)))
                 )
            res <- store.findById("counter")
          } yield assertTrue(res.exists(_.age == 10L))
        }
      },
      test("different stores on same DB file isolate meta by table name") {
        ZIO.scoped {
          for {
            tmp            <- tempFile("shared-meta")
            (path, tmpPath) = tmp
            cache          <- TransactorCache.make()
            userStore      <- SQLiteProjectionStore.make[User](path, cache)
            snakeStore     <- SQLiteProjectionStore.make[SnakeEntity](path, cache)
            _              <- userStore.updateLastProcessedSeq(111L)
            _              <- snakeStore.updateLastProcessedSeq(222L)
            uSeq           <- userStore.getLastProcessedSeq
            sSeq           <- snakeStore.getLastProcessedSeq
            _              <- cleanup(path, tmpPath)
          } yield assertTrue(uSeq == 111L, sSeq == 222L)
        }
      },
      test("TransactorCache reused across stores for same path") {
        ZIO.scoped {
          for {
            tmp            <- tempFile("cache-reuse")
            (path, tmpPath) = tmp
            cache          <- TransactorCache.make()
            s1             <- SQLiteProjectionStore.make[User](path, cache)
            s2             <- SQLiteProjectionStore.make[User](path, cache)
            _              <- s1.insert(User("1", "A", "a@b.com", 1L, 1L, active = true))
            res            <- s2.findById("1")
            sz             <- cache.size
            _              <- cleanup(path, tmpPath)
          } yield assertTrue(res.isDefined, sz == 1)
        }
      }
    ),
    suite("edge cases")(
      test("insert with optional None and findById preserves None") {
        withStoreSimple[OptionalEntity] { store =>
          val e = OptionalEntity("opt1", "Opt", None, None)
          for {
            _   <- store.insert(e)
            res <- store.findById("opt1")
          } yield assertTrue(res.contains(e))
        }
      },
      test("updateFields Set string with special characters") {
        withStoreSimple[User] { store =>
          val u = User("1", "A", "a@b.com", 1L, 1L, active = true)
          for {
            _   <- store.insert(u)
            _   <- store.updateFields("1", Chunk(FieldUpdate.Set("name", "O'Reilly")))
            res <- store.findById("1")
          } yield assertTrue(res.exists(_.name == "O'Reilly"))
        }
      },
      test("large seq value round-trip") {
        withStoreSimple[User] { store =>
          for {
            _   <- store.updateLastProcessedSeq(Long.MaxValue)
            seq <- store.getLastProcessedSeq
          } yield assertTrue(seq == Long.MaxValue)
        }
      },
      test("schema hash empty string") {
        withStoreSimple[User] { store =>
          for {
            _ <- store.updateSchemaHash("")
            h <- store.getSchemaHash
          } yield assertTrue(h.contains(""))
        }
      }
    )
  )
}
