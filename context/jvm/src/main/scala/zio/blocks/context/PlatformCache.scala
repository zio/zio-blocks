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

package zio.blocks.context

import java.util.concurrent.ConcurrentHashMap
import zio.blocks.typeid.TypeId

private[context] object PlatformCache {
  def empty: Cache = new JvmCache(new ConcurrentHashMap[TypeId.Erased, Any]())

  private final class JvmCache(underlying: ConcurrentHashMap[TypeId.Erased, Any]) extends Cache {
    def get(key: TypeId.Erased): Any                     = underlying.get(key)
    def put(key: TypeId.Erased, value: Any): Unit        = { underlying.put(key, value); () }
    def putIfAbsent(key: TypeId.Erased, value: Any): Any = underlying.putIfAbsent(key, value)
  }
}
