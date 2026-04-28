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
 * Represents a client-side signal identified by `name`. The name must be a
 * valid JavaScript identifier (Datastar uses it as an object key). Use `:=` to
 * create a [[SignalUpdate]] that pairs this signal with a JSON-serialized
 * value.
 *
 * {{{
 * val count = Signal[Int]("count")
 * count := 0  // SignalUpdate ready for patchSignals
 * }}}
 */
final class Signal[A](val name: String) {

  def ref: String = "$" + name

  def :=(value: A)(implicit schema: Schema[A]): SignalUpdate[A] = {
    val serialized = schema.jsonCodec.encodeToString(value)
    new SignalUpdate[A](name, serialized)
  }
}

object Signal {

  def apply[A](name: String): Signal[A] = new Signal[A](name)

  implicit def signalToJs[A]: ToJs[Signal[A]] = new ToJs[Signal[A]] {
    def toJs(s: Signal[A]): String = "$" + s.name
  }
}
