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

private[projection] object TransactorCachePlatform {

  def makeInternal(maxSize: Int): UIO[TransactorCache] =
    ZIO.succeed(new TransactorCache {
      def get(path: String): Task[zio.blocks.sql.Transactor] =
        ZIO.fail(new UnsupportedOperationException("TransactorCache is JVM-only for SQLite"))
      def evict(path: String): Task[Unit] = ZIO.unit
      def close: Task[Unit]               = ZIO.unit
      def size: Task[Int]                 = ZIO.succeed(0)
    })
}
