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

/**
 * Cross-store parity: the dual `ProjectionStore` implementations
 * (`InMemoryProjectionStore` for tests, `SQLiteProjectionStore` on the JVM)
 * must agree on observable semantics. Runs one op script against both stores
 * and asserts identical outcomes, so a change to either store's semantics
 * breaks this spec instead of silently drifting.
 */
object ProjectionStoreParitySpec extends ZIOSpecDefault {

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

  private def tempPath(): Task[(String, java.nio.file.Path)] =
    ZIO.attempt {
      val p = java.nio.file.Files.createTempFile("parity", ".db")
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

  /**
   * The shared op script. Returns the observable outcome of each step so both
   * stores can be compared for equality.
   */
  private def script(store: ProjectionStore[User]): Task[List[String]] = {
    val u1 = User("u1", "Alice", "a@b.com", 30L, 100L, active = true)
    for {
      freshSeq  <- store.getLastProcessedSeq
      freshHash <- store.getSchemaHash
      freshMiss <- store.findById("missing")
      _         <- store.insert(u1)
      found     <- store.findById("u1")
      dupFailed <- store.insert(u1).either.map(_.isLeft)
      _         <- store.upsert(u1.copy(name = "Alicia"))
      updated   <- store.findById("u1")
      _         <- store.updateFields("u1", Chunk(FieldUpdate("name", "Ally")))
      patched   <- store.findById("u1")
      _         <- store.updateLastProcessedSeq(7L)
      seq       <- store.getLastProcessedSeq
      _         <- store.updateSchemaHash("hash-1")
      hash      <- store.getSchemaHash
      _         <- store.delete("u1")
      deleted   <- store.findById("u1")
      _         <- store.delete("u1")
      _         <- store.insert(u1)
      _         <- store.truncate
      truncated <- store.findById("u1")
    } yield List(
      s"freshSeq=$freshSeq",
      s"freshHash=$freshHash",
      s"freshMiss=$freshMiss",
      s"found=$found",
      s"dupFailed=$dupFailed",
      s"updated=${updated.map(_.name)}",
      s"patched=${patched.map(_.name)}",
      s"seq=$seq",
      s"hash=$hash",
      s"deleted=$deleted",
      s"truncated=$truncated"
    )
  }

  def spec: Spec[TestEnvironment, Any] = suite("ProjectionStoreParity")(
    test("in-memory and SQLite stores agree on the shared op script") {
      ZIO.scoped {
        for {
          tmp            <- tempPath()
          (path, tmpPath) = tmp
          cache          <- TransactorCache.make()
          sqlite         <- SQLiteProjectionStore.make[User](path, cache)
          mem            <- InMemoryProjectionStore.make[User]
          memOut         <- script(mem)
          sqliteOut      <- script(sqlite).ensuring(cleanup(path, tmpPath))
        } yield assertTrue(sqliteOut == memOut) &&
          assertTrue(
            memOut.contains("freshSeq=0"),
            memOut.contains("freshHash=None"),
            memOut.contains("freshMiss=None"),
            memOut.exists(_.startsWith("found=Some(User(u1,Alice")),
            memOut.contains("dupFailed=true"),
            memOut.contains("updated=Some(Alicia)"),
            memOut.contains("patched=Some(Ally)"),
            memOut.contains("seq=7"),
            memOut.contains("hash=Some(hash-1)"),
            memOut.contains("deleted=None"),
            memOut.contains("truncated=None")
          )
      }
    }
  )
}
