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
 * A reference to a signal in a Datastar expression, written `\$name`. Get one
 * from [[Signal#ref]] and assign it to an attribute, as in
 * `dataText := count.ref`.
 */
final class DatastarRef private[datastar] (val signalName: String) {

  /** The expression text, `\$` followed by the signal name. */
  val value: String = "$" + signalName

  /** Returns [[value]]. */
  override def toString: String = value
}

/** Holds the `ToJs` instance for [[DatastarRef]]. */
object DatastarRef {

  /** Renders a reference in JavaScript as its [[DatastarRef#value]]. */
  implicit val datastarRefToJs: ToJs[DatastarRef] = new ToJs[DatastarRef] {
    def toJs(ref: DatastarRef): String = ref.value
  }
}
