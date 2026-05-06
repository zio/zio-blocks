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

package zio.blocks.datastar

import zio.blocks.html.ToJs
import zio.blocks.schema.Schema

/**
 * A named reactive signal for the Datastar frontend framework.
 *
 * Represents a client-side signal identified by `name`. Signal names are
 * validated at construction time. Literal names are checked at compile time,
 * and dynamic names are checked at runtime. Names must be dot-separated
 * JavaScript identifiers and must not contain `__`, which Datastar reserves for
 * modifier syntax. Use `:=` to create a [[SignalUpdate]] that pairs this signal
 * with a JSON-serialized value.
 *
 * {{{
 * val count = Signal[Int]("count")
 * count := 0  // SignalUpdate ready for patchSignals
 * }}}
 */
final class Signal[A] private[datastar] (val name: String) {

  def ref: DatastarRef = new DatastarRef(name)

  def :=(value: A)(implicit schema: Schema[A]): SignalUpdate[A] = {
    val serialized = schema.jsonCodec.encodeToString(value)
    new SignalUpdate[A](name, serialized)
  }
}

object Signal extends SignalVersionSpecific {

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

  implicit def signalToJs[A]: ToJs[Signal[A]] = new ToJs[Signal[A]] {
    def toJs(s: Signal[A]): String = s.ref.value
  }
}
