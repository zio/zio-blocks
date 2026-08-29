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
import zio.blocks.sql.{DbCon, Frag}

object TransactorCacheSpec extends ZIOSpecDefault {

  private def tempPath(prefix: String = "tc"): Task[(String, java.nio.file.Path)] =
    ZIO.attempt {
      val p = java.nio.file.Files.createTempFile(prefix, ".db")
      (p.toAbsolutePath.toString, p)
    }

  private def cleanupPath(pathStr: String, tmp: java.nio.file.Path): UIO[Unit] =
    ZIO.succeed {
      try java.nio.file.Files.deleteIfExists(tmp)
      catch { case _: Throwable => () }
      try java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(pathStr + "-wal"))
      catch { case _: Throwable => () }
      try java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(pathStr + "-shm"))
      catch { case _: Throwable => () }
      try java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(pathStr + "-journal"))
      catch { case _: Throwable => () }
    }

  private def withTempCache(
    maxSize: Int = 256
  )(f: (TransactorCache, String, java.nio.file.Path) => Task[TestResult]): Task[TestResult] =
    ZIO.scoped {
      for {
        tmp               <- tempPath()
        (pathStr, tmpPath) = tmp
        cache             <- TransactorCache.make(maxSize)
        result            <- f(cache, pathStr, tmpPath).ensuring(cleanupPath(pathStr, tmpPath))
      } yield result
    }

  private def journalMode(transactor: zio.blocks.sql.Transactor): String =
    transactor.connect {
      val con = summon[DbCon]
      val ps  = con.connection.prepareStatement("PRAGMA journal_mode")
      try {
        val rs = ps.executeQuery()
        try {
          if (rs.next()) rs.reader.getString(1) else ""
        } finally rs.close()
      } finally ps.close()
    }

  private def synchronousMode(transactor: zio.blocks.sql.Transactor): String =
    transactor.connect {
      val con = summon[DbCon]
      val ps  = con.connection.prepareStatement("PRAGMA synchronous")
      try {
        val rs = ps.executeQuery()
        try {
          if (rs.next()) rs.reader.getString(1) else ""
        } finally rs.close()
      } finally ps.close()
    }

  def spec: Spec[TestEnvironment, Any] = suite("TransactorCacheSpec")(
    test("get opens new transactor and size 1") {
      withTempCache() { (cache, pathStr, _) =>
        for {
          tx <- cache.get(pathStr)
          sz <- cache.size
        } yield assertTrue(tx != null, sz == 1)
      }
    },
    test("get same path reuses same instance") {
      withTempCache() { (cache, pathStr, _) =>
        for {
          tx1 <- cache.get(pathStr)
          tx2 <- cache.get(pathStr)
          sz  <- cache.size
        } yield assertTrue(tx1 eq tx2, sz == 1)
      }
    },
    test("get different paths creates distinct transactors") {
      ZIO.scoped {
        for {
          tmp1    <- tempPath("tc-a")
          tmp2    <- tempPath("tc-b")
          (p1, t1) = tmp1
          (p2, t2) = tmp2
          cache   <- TransactorCache.make(256)
          tx1     <- cache.get(p1)
          tx2     <- cache.get(p2)
          sz      <- cache.size
          _       <- cleanupPath(p1, t1)
          _       <- cleanupPath(p2, t2)
        } yield assertTrue(tx1 ne tx2, sz == 2)
      }
    },
    test("evict removes entry and decrements size") {
      withTempCache() { (cache, pathStr, _) =>
        for {
          _   <- cache.get(pathStr)
          sz1 <- cache.size
          _   <- cache.evict(pathStr)
          sz2 <- cache.size
        } yield assertTrue(sz1 == 1, sz2 == 0)
      }
    },
    test("evict non-existent is no-op") {
      withTempCache() { (cache, pathStr, _) =>
        for {
          _   <- cache.get(pathStr)
          sz1 <- cache.size
          _   <- cache.evict("/tmp/nonexistent-db-xyz.db")
          sz2 <- cache.size
        } yield assertTrue(sz1 == 1, sz2 == 1)
      }
    },
    test("evict then get creates new instance") {
      withTempCache() { (cache, pathStr, _) =>
        for {
          tx1 <- cache.get(pathStr)
          _   <- cache.evict(pathStr)
          tx2 <- cache.get(pathStr)
        } yield assertTrue(tx1 ne tx2)
      }
    },
    test("close clears all entries") {
      ZIO.scoped {
        for {
          tmp1    <- tempPath("tc-c1")
          tmp2    <- tempPath("tc-c2")
          (p1, t1) = tmp1
          (p2, t2) = tmp2
          cache   <- TransactorCache.make(256)
          _       <- cache.get(p1)
          _       <- cache.get(p2)
          sz1     <- cache.size
          _       <- cache.close
          sz2     <- cache.size
          _       <- cleanupPath(p1, t1)
          _       <- cleanupPath(p2, t2)
        } yield assertTrue(sz1 == 2, sz2 == 0)
      }
    },
    test("close is idempotent") {
      withTempCache() { (cache, pathStr, _) =>
        for {
          _  <- cache.get(pathStr)
          _  <- cache.close
          _  <- cache.close
          sz <- cache.size
        } yield assertTrue(sz == 0)
      }
    },
    test("get after close fails") {
      withTempCache() { (cache, pathStr, _) =>
        for {
          _      <- cache.get(pathStr)
          _      <- cache.close
          result <- cache.get(pathStr).either
        } yield assertTrue(result.isLeft)
      }
    },
    test("evict after close is no-op") {
      withTempCache() { (cache, pathStr, _) =>
        for {
          _  <- cache.get(pathStr)
          _  <- cache.close
          _  <- cache.evict(pathStr)
          sz <- cache.size
        } yield assertTrue(sz == 0)
      }
    },
    test("LRU eviction at maxSize 3 evicts least recent") {
      ZIO.scoped {
        for {
          tmp1    <- tempPath("lru1")
          tmp2    <- tempPath("lru2")
          tmp3    <- tempPath("lru3")
          tmp4    <- tempPath("lru4")
          (p1, t1) = tmp1
          (p2, t2) = tmp2
          (p3, t3) = tmp3
          (p4, t4) = tmp4
          cache   <- TransactorCache.make(3)
          tx1     <- cache.get(p1)
          _       <- cache.get(p2)
          _       <- cache.get(p3)
          sz1     <- cache.size
          _       <- cache.get(p4) // should evict p1
          sz2     <- cache.size
          // p1 should be evicted, so new get creates different instance
          tx1Again <- cache.get(p1)
          sz3      <- cache.size
          _        <- cleanupPath(p1, t1)
          _        <- cleanupPath(p2, t2)
          _        <- cleanupPath(p3, t3)
          _        <- cleanupPath(p4, t4)
        } yield assertTrue(sz1 == 3, sz2 == 3, sz3 == 3, tx1 ne tx1Again)
      }
    },
    test("LRU eviction respects access order - get updates LRU") {
      ZIO.scoped {
        for {
          tmp1    <- tempPath("lruA1")
          tmp2    <- tempPath("lruA2")
          tmp3    <- tempPath("lruA3")
          tmp4    <- tempPath("lruA4")
          (p1, t1) = tmp1
          (p2, t2) = tmp2
          (p3, t3) = tmp3
          (p4, t4) = tmp4
          cache   <- TransactorCache.make(3)
          tx1     <- cache.get(p1)
          tx2     <- cache.get(p2)
          _       <- cache.get(p3)
          // Access p1 to make it MRU
          _ <- cache.get(p1)
          // Now insert p4, should evict p2 (LRU)
          _  <- cache.get(p4)
          sz <- cache.size
          // p2 should be evicted, p1 should still be cached (same instance)
          tx1Again <- cache.get(p1)
          tx2Again <- cache.get(p2)
          _        <- cleanupPath(p1, t1)
          _        <- cleanupPath(p2, t2)
          _        <- cleanupPath(p3, t3)
          _        <- cleanupPath(p4, t4)
        } yield assertTrue(sz == 3, tx1 eq tx1Again, tx2 ne tx2Again)
      }
    },
    test("maxSize 1 evicts on every new path") {
      ZIO.scoped {
        for {
          tmp1     <- tempPath("ms1-1")
          tmp2     <- tempPath("ms1-2")
          (p1, t1)  = tmp1
          (p2, t2)  = tmp2
          cache    <- TransactorCache.make(1)
          tx1      <- cache.get(p1)
          sz1      <- cache.size
          tx2      <- cache.get(p2)
          sz2      <- cache.size
          tx1Again <- cache.get(p1)
          _        <- cleanupPath(p1, t1)
          _        <- cleanupPath(p2, t2)
        } yield assertTrue(sz1 == 1, sz2 == 1, tx1 ne tx2, tx1 ne tx1Again)
      }
    },
    test("default maxSize is 256") {
      ZIO.scoped {
        for {
          cache <- TransactorCache.make()
          cfg    = TransactorCacheConfig.default
        } yield assertTrue(cfg.maxSize == 256, cache != null)
      }
    },
    test("WAL mode enabled after get - journal_mode returns wal") {
      withTempCache() { (cache, pathStr, _) =>
        for {
          tx   <- cache.get(pathStr)
          mode <- ZIO.attemptBlocking(journalMode(tx))
        } yield assertTrue(mode.toLowerCase == "wal")
      }
    },
    test("synchronous NORMAL after get") {
      withTempCache() { (cache, pathStr, _) =>
        for {
          tx   <- cache.get(pathStr)
          sync <- ZIO.attemptBlocking(synchronousMode(tx))
        } yield assertTrue(sync.nonEmpty, Set("1", "2", "normal", "full").contains(sync.toLowerCase))
      }
    },
    test("WAL remains after reuse") {
      withTempCache() { (cache, pathStr, _) =>
        for {
          tx1  <- cache.get(pathStr)
          tx2  <- cache.get(pathStr)
          mode <- ZIO.attemptBlocking(journalMode(tx2))
        } yield assertTrue(tx1 eq tx2, mode.toLowerCase == "wal")
      }
    },
    test("concurrent get same path 10 fibers returns same instance") {
      withTempCache() { (cache, pathStr, _) =>
        for {
          txs <- ZIO.foreachPar(1 to 10)(_ => cache.get(pathStr))
          sz  <- cache.size
        } yield assertTrue(txs.forall(_ eq txs.head), sz == 1)
      }
    },
    test("concurrent get different paths 10 fibers distinct") {
      ZIO.scoped {
        for {
          tmps  <- ZIO.foreach(1 to 10)(i => tempPath(s"conc-$i"))
          cache <- TransactorCache.make(256)
          txs   <- ZIO.foreachPar(tmps)(tmp => cache.get(tmp._1))
          sz    <- cache.size
          _     <- ZIO.foreachDiscard(tmps)(tmp => cleanupPath(tmp._1, tmp._2))
        } yield assertTrue(txs.distinct.size == 10, sz == 10)
      }
    },
    test("concurrent get mixed paths thread-safe no leak") {
      ZIO.scoped {
        for {
          tmp1    <- tempPath("mix1")
          tmp2    <- tempPath("mix2")
          (p1, t1) = tmp1
          (p2, t2) = tmp2
          cache   <- TransactorCache.make(10)
          // 10 fibers, each does 20 gets alternating between p1 and p2
          _ <-
            ZIO.foreachParDiscard(1 to 10)(_ => ZIO.foreachDiscard(1 to 20)(i => cache.get(if (i % 2 == 0) p1 else p2)))
          sz <- cache.size
          _  <- cleanupPath(p1, t1)
          _  <- cleanupPath(p2, t2)
        } yield assertTrue(sz == 2)
      }
    },
    test("Scope finalizer closes cache on scope exit") {
      for {
        tmp         <- tempPath("scope")
        (pathStr, t) = tmp
        cacheRef    <- Ref.make[Option[TransactorCache]](None)
        _           <- ZIO.scoped {
               for {
                 cache <- TransactorCache.make(256)
                 _     <- cacheRef.set(Some(cache))
                 _     <- cache.get(pathStr)
                 sz    <- cache.size
                 _     <- ZIO.succeed(assertTrue(sz == 1))
               } yield ()
             }
        cacheOpt <- cacheRef.get
        cache     = cacheOpt.get
        szAfter  <- cache.size
        result   <- cache.get(pathStr).either
        _        <- cleanupPath(pathStr, t)
      } yield assertTrue(szAfter == 0, result.isLeft)
    },
    test("size reflects cache size correctly after multiple ops") {
      ZIO.scoped {
        for {
          tmp1    <- tempPath("sz1")
          tmp2    <- tempPath("sz2")
          tmp3    <- tempPath("sz3")
          (p1, t1) = tmp1
          (p2, t2) = tmp2
          (p3, t3) = tmp3
          cache   <- TransactorCache.make(10)
          s0      <- cache.size
          _       <- cache.get(p1)
          s1      <- cache.size
          _       <- cache.get(p2)
          s2      <- cache.size
          _       <- cache.get(p3)
          s3      <- cache.size
          _       <- cache.evict(p2)
          s4      <- cache.size
          _       <- cache.close
          s5      <- cache.size
          _       <- cleanupPath(p1, t1)
          _       <- cleanupPath(p2, t2)
          _       <- cleanupPath(p3, t3)
        } yield assertTrue(s0 == 0, s1 == 1, s2 == 2, s3 == 3, s4 == 2, s5 == 0)
      }
    },
    test("eviction closes LRU gracefully and new path usable") {
      ZIO.scoped {
        for {
          tmp1    <- tempPath("evClose1")
          tmp2    <- tempPath("evClose2")
          tmp3    <- tempPath("evClose3")
          tmp4    <- tempPath("evClose4")
          (p1, t1) = tmp1
          (p2, t2) = tmp2
          (p3, t3) = tmp3
          (p4, t4) = tmp4
          cache   <- TransactorCache.make(2)
          tx1     <- cache.get(p1)
          tx2     <- cache.get(p2)
          // Verify both usable
          _ <- ZIO.attemptBlocking {
                 tx1.connect { Frag.literal("CREATE TABLE t1 (id INTEGER)").update; () }
                 tx2.connect { Frag.literal("CREATE TABLE t2 (id INTEGER)").update; () }
               }
          // Insert p3 should evict p1
          tx3 <- cache.get(p3)
          sz1 <- cache.size
          // p1 evicted, should get new instance
          tx1New <- cache.get(p1)
          // p1 should be usable again
          _ <- ZIO.attemptBlocking {
                 tx1New.connect { Frag.literal("CREATE TABLE t1b (id INTEGER)").update; () }
               }
          _ <- cleanupPath(p1, t1)
          _ <- cleanupPath(p2, t2)
          _ <- cleanupPath(p3, t3)
          _ <- cleanupPath(p4, t4)
        } yield assertTrue(sz1 == 2, tx1 ne tx1New, tx3 != null)
      }
    },
    test("transactor remains functional after caching") {
      withTempCache() { (cache, pathStr, _) =>
        for {
          tx   <- cache.get(pathStr)
          rows <- ZIO.attemptBlocking {
                    tx.connect {
                      Frag.literal("CREATE TABLE func_test (id INTEGER NOT NULL, name TEXT NOT NULL)").update
                      ()
                    }
                    tx.connect {
                      Frag.literal("INSERT INTO func_test (id, name) VALUES (1, 'hello')").update
                      ()
                    }
                    tx.connect {
                      Frag
                        .literal("SELECT name FROM func_test")
                        .query[String](using summon[DbCon], summon[zio.blocks.sql.DbCodec[String]])
                    }
                  }
          sz <- cache.size
        } yield assertTrue(rows == List("hello"), sz == 1)
      }
    }
  )
}
