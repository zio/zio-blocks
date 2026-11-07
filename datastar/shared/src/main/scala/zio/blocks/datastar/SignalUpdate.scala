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

package zio.http.datastar

import zio.blocks.html._

/**
 * A signal name paired with its value encoded as JSON, made by [[Signal#:=]].
 * [[DatastarEvent.patchSignals]] sends updates to the browser, and
 * `dataSignals(update, ...)` declares them as initial values in a page.
 *
 * `name` is the signal's name and `serialized` is the JSON that the signal's
 * schema produced; it is inserted as given into the objects that the module
 * builds.
 */
final class SignalUpdate[A] private[datastar] (val name: String, val serialized: String)

/** Renders signal updates as JavaScript object expressions. */
object SignalUpdate {

  /**
   * Renders one or more updates as a JavaScript object expression, with quoted
   * keys, `": "` after each key and `", "` between entries, in argument order.
   *
   * {{{
   * val count = Signal[Int]("count")
   * val name  = Signal[String]("name")
   *
   * SignalUpdate.objectExpression(count := 1, name := "Ada") // {"count": 1, "name": "Ada"}
   * }}}
   */
  def objectExpression(update: SignalUpdate[_], updates: SignalUpdate[_]*): String = {
    val all = update +: updates
    val sb  = new java.lang.StringBuilder(32)
    sb.append('{')
    var i = 0
    while (i < all.length) {
      if (i > 0) sb.append(", ")
      val current = all(i)
      sb.append(escapeJsKey(current.name)).append(": ").append(current.serialized)
      i += 1
    }
    sb.append('}')
    sb.toString
  }

  /**
   * Renders an update in JavaScript as a one-entry object, as
   * [[objectExpression]] does.
   */
  implicit def signalUpdateToJs[A]: ToJs[SignalUpdate[A]] = new ToJs[SignalUpdate[A]] {
    def toJs(u: SignalUpdate[A]): String = objectExpression(u)
  }

  private def escapeJsKey(s: String): String =
    DatastarStringEscape.quotedString(s)
}
