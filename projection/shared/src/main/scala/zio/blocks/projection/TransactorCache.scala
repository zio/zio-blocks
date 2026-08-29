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
 * Each distinct file path maps to a single [[Transactor]] instance. When the
 * cache reaches `maxSize` the least-recently-used entry is evicted.
 */
trait TransactorCache {

  /** Returns a cached [[Transactor]] for `path` or opens a new one. */
  def get(path: String): Task[Transactor]

  /** Evicts and closes the [[Transactor]] for `path` if present. */
  def evict(path: String): Task[Unit]

  /** Closes all cached transactors and clears the cache. */
  def close: Task[Unit]

  /** Number of cached entries. */
  def size: Task[Int]
}

/** Configuration for [[TransactorCache]]. */
final case class TransactorCacheConfig(maxSize: Int = 256)

object TransactorCacheConfig {
  val default: TransactorCacheConfig = TransactorCacheConfig()
}

object TransactorCache {

  /**
   * Creates a [[TransactorCache]] with the given `maxSize` and registers a
   * finalizer on the enclosing [[Scope]] to close the cache when the scope
   * closes.
   */
  def make(maxSize: Int = 256): ZIO[Scope, Nothing, TransactorCache] =
    ZIO.acquireRelease(TransactorCachePlatform.makeInternal(maxSize))(_.close.orDie)

  /** Creates a [[TransactorCache]] without scope management. */
  def makeUnscoped(maxSize: Int = 256): UIO[TransactorCache] =
    TransactorCachePlatform.makeInternal(maxSize)

  // $COVERAGE-OFF$
  /** ZLayer that provides a [[TransactorCache]] with default size 256. */
  def live(maxSize: Int = 256): ZLayer[Scope, Nothing, TransactorCache] =
    ZLayer.fromZIO(make(maxSize))

  /** ZLayer with custom config. */
  def liveConfig(config: TransactorCacheConfig): ZLayer[Scope, Nothing, TransactorCache] =
    live(config.maxSize)
  // $COVERAGE-ON$
}
