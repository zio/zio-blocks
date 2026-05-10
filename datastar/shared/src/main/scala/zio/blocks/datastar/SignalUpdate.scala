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

import zio.http.html.ToJs

/**
 * A signal name paired with its JSON-serialized value.
 *
 * Created via `Signal.:=`. The `name` is the signal identifier and `serialized`
 * is the JSON string produced by the signal's schema codec. Used by
 * [[DatastarEvent.patchSignals]] to build SSE payloads and by the `ToJs`
 * instance to render inline JavaScript object literals.
 *
 * `serialized` is inserted verbatim into the final object expression, so it
 * must already be valid JSON for the value being assigned.
 */
final class SignalUpdate[A] private[datastar] (val name: String, val serialized: String)

object SignalUpdate {

  /** Renders one or more updates as a single JavaScript object expression. */
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

  implicit def signalUpdateToJs[A]: ToJs[SignalUpdate[A]] = new ToJs[SignalUpdate[A]] {
    def toJs(u: SignalUpdate[A]): String = objectExpression(u)
  }

  private def escapeJsKey(s: String): String =
    DatastarStringEscape.quotedString(s)
}
