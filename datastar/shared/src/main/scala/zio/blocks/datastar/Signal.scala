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
import zio.blocks.schema.Schema

/**
 * A typed name for a Datastar signal, a piece of state that lives in the
 * browser page. `A` is the Scala type of the signal's value. Use a signal to
 * declare state with `dataSignals`, to refer to it in attributes with
 * [[Signal#ref]], and to send new values from the server with `:=` and
 * [[DatastarEvent.patchSignals]].
 *
 * A name is one or more segments separated by dots. Each segment starts with a
 * character that Java accepts at the start of an identifier (such as a letter,
 * `_` or `\$`; Java also accepts currency signs such as `€`, which JavaScript
 * rejects, a known bug) and continues with characters Java accepts inside one,
 * and the name must not contain `__`, which Datastar uses for modifiers.
 * `Signal("...")` checks a literal name at compile time and any other name at
 * run time; [[Signal.dynamic]] always checks at run time.
 *
 * {{{
 * val count = Signal[Int]("count")
 *
 * count.ref.value          // "\$count"
 * (count := 42).serialized // "42"
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/datastar/signals/ Signal]]
 */
final class Signal[A] private[datastar] (val name: String) {

  /**
   * Returns the reference to this signal in a Datastar expression, `\$name`.
   *
   * {{{
   * Signal[Int]("count").ref.value // "\$count"
   * }}}
   */
  def ref: DatastarRef = new DatastarRef(name)

  /**
   * Pairs this signal with `value`, encoded as JSON by the schema's JSON codec,
   * for [[DatastarEvent.patchSignals]] or `dataSignals`.
   *
   * {{{
   * val name = Signal[String]("name")
   *
   * (name := "Ada").serialized // "\"Ada\""
   * }}}
   */
  def :=(value: A)(implicit schema: Schema[A]): SignalUpdate[A] = {
    val serialized = schema.jsonCodec.encodeToString(value)
    new SignalUpdate[A](name, serialized)
  }
}

/**
 * Creates signals. `Signal[A]("name")` checks a literal name at compile time;
 * [[dynamic]] takes a name known only at run time.
 */
object Signal extends SignalVersionSpecific {

  /**
   * Creates a signal from a name known only at run time, checking it as the
   * [[Signal]] type describes.
   *
   * {{{
   * Signal.dynamic[Int]("user.age").name // "user.age"
   * }}}
   *
   * @throws java.lang.IllegalArgumentException
   *   if `name` is not a valid signal name, such as `user-name` or `a__b`
   */
  def dynamic[A](name: String): Signal[A] = checkedApply(name)

  private[datastar] def unsafeApply[A](name: String): Signal[A] = new Signal[A](name)

  private[datastar] def checkedApply[A](name: String): Signal[A] = {
    requireValidName(name)
    unsafeApply(name)
  }

  private[datastar] def requireValidName(name: String): Unit =
    if (!isValidName(name)) {
      throw new IllegalArgumentException(invalidNameMessage(name))
    }

  private[datastar] def invalidNameMessage(name: String): String =
    s"Invalid Datastar signal name '$name'. Signal names must be dot-separated JavaScript identifiers and must not contain '__'."

  private[datastar] def isValidName(name: String): Boolean = {
    if (name.isEmpty || name.contains("__")) return false

    var segmentStart = 0
    var i            = 0
    while (i <= name.length) {
      if (i == name.length || name.charAt(i) == '.') {
        if (!isValidSegment(name, segmentStart, i)) return false
        segmentStart = i + 1
      }
      i += 1
    }
    true
  }

  private def isValidSegment(name: String, start: Int, end: Int): Boolean = {
    if (start >= end) return false
    if (!Character.isJavaIdentifierStart(name.charAt(start))) return false

    var i = start + 1
    while (i < end) {
      if (!Character.isJavaIdentifierPart(name.charAt(i))) return false
      i += 1
    }
    true
  }

  /**
   * Renders a signal in JavaScript as its reference, `\$name`, so a signal can
   * stand for itself in a `js"..."` interpolation or an attribute value.
   */
  implicit def signalToJs[A]: ToJs[Signal[A]] = new ToJs[Signal[A]] {
    def toJs(s: Signal[A]): String = s.ref.value
  }
}
