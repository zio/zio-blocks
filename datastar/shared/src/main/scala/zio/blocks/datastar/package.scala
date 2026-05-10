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

package zio.http

package object datastar extends DatastarAttributes {

  private[datastar] def toKebabCase(s: String): String = {
    val sb = new java.lang.StringBuilder(s.length + 4)
    var i  = 0
    while (i < s.length) {
      val c = s.charAt(i)
      if (Character.isUpperCase(c)) {
        if (i > 0 && s.charAt(i - 1) != '-') sb.append('-')
        sb.append(Character.toLowerCase(c))
      } else {
        sb.append(c)
      }
      i += 1
    }
    sb.toString
  }
}
