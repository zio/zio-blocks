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

package zio.blocks.schema.binding

import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Reflection helpers for calling the members of a structural type, with a cache
 * of the looked-up methods per class and member name. The `Binding.of` macro
 * does not use them: it generates its own `getMethod` calls. Only tests call
 * them.
 */
private[binding] object StructuralReflection {
  private val methodCache = new ConcurrentHashMap[(Class[?], String), Method]()

  /**
   * Calls the public method `name` without parameters on `obj` and returns its
   * result, caching the method per class and name.
   */
  def get(obj: AnyRef, name: String): AnyRef = {
    val cls = obj.getClass
    val key = (cls, name)
    var m   = methodCache.get(key)
    if (m == null) {
      m = cls.getMethod(name)
      methodCache.put(key, m)
    }
    m.invoke(obj)
  }

  /**
   * Returns `true` when `obj` is not `null` and its class has a public method
   * without parameters for each of `names`.
   */
  def hasAll(obj: AnyRef, names: Array[String]): Boolean = {
    if (obj == null) return false
    val cls = obj.getClass
    var idx = 0
    while (idx < names.length) {
      val name = names(idx)
      try cls.getMethod(name)
      catch {
        case _: NoSuchMethodException => return false
      }
      idx += 1
    }
    true
  }
}
