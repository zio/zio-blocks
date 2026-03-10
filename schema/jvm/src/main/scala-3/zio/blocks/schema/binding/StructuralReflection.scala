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

private[binding] object StructuralReflection {
  private val methodCache = new ConcurrentHashMap[(Class[?], String), Method]()

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

  def hasAll(obj: AnyRef, names: Array[String]): Boolean = {
    if (obj == null) return false
    val cls = obj.getClass
    var i   = 0
    while (i < names.length) {
      val name = names(i)
      try {
        cls.getMethod(name)
      } catch {
        case _: NoSuchMethodException => return false
      }
      i += 1
    }
    true
  }
}
