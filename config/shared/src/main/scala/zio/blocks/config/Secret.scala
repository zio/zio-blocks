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

package zio.blocks.config

final class Secret private (private val value: String) {
  override def toString: String = "<secret>"

  override def hashCode: Int = value.hashCode

  override def equals(obj: Any): Boolean = obj match {
    case other: Secret => value == other.value
    case _             => false
  }
}

object Secret {
  def apply(value: String): Secret = new Secret(value)

  def unwrap(secret: Secret): String = secret.value

  def displayable: Displayable[Secret] = Displayable.instance(_ => "<secret>")
}
