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
import zio.blocks.sql.{DbCon, JdbcTransactor, SqlDialect, Transactor}

private final case class CacheState(
  cache: Map[String, Transactor],
  access: Map[String, Long],
  counter: Long,
  closed: Boolean
)

private final class LiveTransactorCache(
  maxSize: Int,
  ref: Ref[CacheState],
  sem: Semaphore
) extends TransactorCache {

  private def createTransactor(path: String): Task[Transactor] =
    ZIO.attemptBlocking {
      Class.forName("org.sqlite.JDBC")
      val url = s"jdbc:sqlite:$path"
      val tx  = JdbcTransactor.fromUrl(url, SqlDialect.SQLite)
      // Enable WAL mode and NORMAL synchronous using raw statements (PRAGMA journal_mode returns a row, so use query)
      tx.connect {
        val dbCon = summon[DbCon]
        val ps1   = dbCon.connection.prepareStatement("PRAGMA journal_mode=WAL")
        try {
          val rs = ps1.executeQuery()
          try rs.next()
          finally rs.close()
        } finally ps1.close()
        val ps2 = dbCon.connection.prepareStatement("PRAGMA synchronous=NORMAL")
        try ps2.executeUpdate()
        finally ps2.close()
        val ps3 = dbCon.connection.prepareStatement("PRAGMA busy_timeout=5000")
        try {
          // busy_timeout may return a row on some drivers, handle both
          try {
            val rs = ps3.executeQuery()
            try rs.next()
            finally rs.close()
          } catch {
            case _: java.sql.SQLException => ps3.executeUpdate()
          }
        } finally ps3.close()
        ()
      }
      tx
    }

  private def closeTransactor(tx: Transactor): Task[Unit] =
    // JdbcTransactor holds no persistent resources; attempt to close if AutoCloseable
    ZIO.attemptBlocking {
      tx match {
        case ac: AutoCloseable =>
          try ac.close()
          catch { case _: Throwable => () }
        case _ => ()
      }
    }.orDie

  def get(path: String): Task[Transactor] =
    sem.withPermit {
      for {
        state  <- ref.get
        _      <- ZIO.fail(new IllegalStateException("TransactorCache is closed")).when(state.closed)
        result <- state.cache.get(path) match {
                    case Some(cached) =>
                      ref
                        .update(s => s.copy(access = s.access.updated(path, s.counter + 1), counter = s.counter + 1))
                        .as(cached)
                    case None =>
                      for {
                        tx <- createTransactor(path)
                        // Re-read state after creation (still under semaphore, so consistent)
                        curState <- ref.get
                        // Double-check if another fiber added same path (should not happen due to semaphore, but handle)
                        txToReturn <-
                          if (curState.cache.contains(path)) {
                            // Another entry appeared - use existing and close newly created
                            val existing = curState.cache(path)
                            // Update access for existing
                            ref
                              .update(s =>
                                s.copy(access = s.access.updated(path, s.counter + 1), counter = s.counter + 1)
                              )
                              .as(existing) <*
                              closeTransactor(tx).orDie
                          } else {
                            val needEvict = curState.cache.size >= maxSize && maxSize > 0
                            if (needEvict) {
                              val lruKey    = curState.access.minBy(_._2)._1
                              val evicted   = curState.cache(lruKey)
                              val newCache  = curState.cache - lruKey + (path  -> tx)
                              val newAccess = curState.access - lruKey + (path -> (curState.counter + 1))
                              val newState  = CacheState(newCache, newAccess, curState.counter + 1, curState.closed)
                              ref.set(newState) *> closeTransactor(evicted).orDie.as(tx)
                            } else {
                              val newCache  = curState.cache + (path  -> tx)
                              val newAccess = curState.access + (path -> (curState.counter + 1))
                              val newState  = CacheState(newCache, newAccess, curState.counter + 1, curState.closed)
                              ref.set(newState).as(tx)
                            }
                          }
                      } yield txToReturn
                  }
      } yield result
    }

  def evict(path: String): Task[Unit] =
    sem.withPermit {
      for {
        state <- ref.get
        _     <-
          if (state.closed) ZIO.unit
          else
            state.cache.get(path) match {
              case Some(tx) =>
                val newCache  = state.cache - path
                val newAccess = state.access - path
                ref.set(state.copy(cache = newCache, access = newAccess)) *> closeTransactor(tx).orDie
              case None => ZIO.unit
            }
      } yield ()
    }

  def close: Task[Unit] =
    sem.withPermit {
      for {
        state <- ref.get
        _     <-
          if (state.closed) ZIO.unit
          else {
            val txs = state.cache.values.toList
            ref.set(CacheState(Map.empty, Map.empty, state.counter, closed = true)) *>
              ZIO.foreachDiscard(txs)(closeTransactor(_).orDie)
          }
      } yield ()
    }

  def size: Task[Int] =
    sem.withPermit {
      ref.get.map(s => if (s.closed) 0 else s.cache.size)
    }
}

private[projection] object TransactorCachePlatform {

  def makeInternal(maxSize: Int): UIO[TransactorCache] =
    for {
      ref <- Ref.make(CacheState(Map.empty, Map.empty, 0L, closed = false))
      sem <- Semaphore.make(1)
    } yield new LiveTransactorCache(maxSize, ref, sem)
}
