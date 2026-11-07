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
import zio.blocks.sql.Transactor

/**
 * Cache of SQLite Transactors keyed by file path with LRU eviction.
 *
 * Each distinct file path maps to a single `Transactor` instance. When a new
 * path is opened while the cache holds `maxSize` entries, the least recently
 * used entry is evicted; a `maxSize` of `0` or less never evicts.
 *
 * Opening a path loads the `org.sqlite.JDBC` driver, so `sqlite-jdbc` must be
 * on the runtime classpath, and connects once to turn on WAL mode, which
 * creates the file. A `Transactor` keeps no connection open: it opens one for
 * each operation, so evicting or closing an entry releases nothing.
 */
trait TransactorCache {

  /**
   * Returns the cached `Transactor` for `path`, or opens a new one. Fails if
   * the file cannot be opened, for example because its folder does not exist,
   * and with an `IllegalStateException` once the cache is closed.
   */
  def get(path: String): Task[Transactor]

  /** Removes the `Transactor` for `path` from the cache, if it is there. */
  def evict(path: String): Task[Unit]

  /**
   * Clears the cache and marks it closed, so that later calls to `get` fail.
   */
  def close: Task[Unit]

  /** The number of cached entries, `0` once the cache is closed. */
  def size: Task[Int]
}

/**
 * Configuration for [[TransactorCache]].
 *
 * @param maxSize
 *   the most entries the cache holds before it evicts one
 */
final case class TransactorCacheConfig(maxSize: Int = 256)

/** Holds the default [[TransactorCacheConfig]]. */
object TransactorCacheConfig {

  /** The default configuration, with a `maxSize` of 256. */
  val default: TransactorCacheConfig = TransactorCacheConfig()
}

/** Creates [[TransactorCache]]s. */
object TransactorCache {

  /**
   * Creates a [[TransactorCache]] with the given `maxSize` and registers a
   * finalizer on the enclosing `Scope` to close the cache when the scope
   * closes.
   */
  def make(maxSize: Int = 256): ZIO[Scope, Nothing, TransactorCache] =
    ZIO.acquireRelease(TransactorCachePlatform.makeInternal(maxSize))(_.close.orDie)

  /**
   * Creates a [[TransactorCache]] that is never closed for you; call `close`
   * when done.
   */
  def makeUnscoped(maxSize: Int = 256): UIO[TransactorCache] =
    TransactorCachePlatform.makeInternal(maxSize)

  // $COVERAGE-OFF$
  /**
   * A `ZLayer` that provides a [[TransactorCache]] made with `make`, closed
   * when the `Scope` it needs closes. `maxSize` defaults to 256.
   */
  def live(maxSize: Int = 256): ZLayer[Scope, Nothing, TransactorCache] =
    ZLayer.fromZIO(make(maxSize))

  /** Same as `live(config.maxSize)`. */
  def liveConfig(config: TransactorCacheConfig): ZLayer[Scope, Nothing, TransactorCache] =
    live(config.maxSize)
  // $COVERAGE-ON$
}
