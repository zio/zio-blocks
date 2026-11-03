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

import zio.blocks.schema.Schema

/**
 * A string that must not be printed, such as a password or an API token. Its
 * `toString` is `<secret>`; [[Secret.unwrap]] returns the value. Two secrets
 * are equal when their values are equal. A field of type `Secret` decodes from
 * a plain string, and a `StaticFlag[Secret]` shows as `<secret>` in
 * `Flag.dump`.
 *
 * Only the value's own printing is hidden: [[Provenance.Resolved]] still holds
 * the raw value, a known bug.
 *
 * {{{
 * import zio.blocks.schema.Schema
 *
 * final case class Smtp(user: String, password: Secret)
 * object Smtp { implicit val schema: Schema[Smtp] = Schema.derived }
 *
 * val smtp     = Config.load[Smtp](ConfigSource.fromMap(Map("user" -> "mailer", "password" -> "s3cr3t")))
 * // Right(Smtp(mailer,<secret>))
 * val password = smtp.map(s => Secret.unwrap(s.password))
 * // Right(s3cr3t)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/config/config-source/ ConfigSource]]
 */
final class Secret private (private val value: String) {
  override def toString: String = "<secret>"

  override def hashCode: Int = value.hashCode

  override def equals(obj: Any): Boolean = obj match {
    case other: Secret => value == other.value
    case _             => false
  }
}

/** Constructors and instances for [[Secret]]. */
object Secret {

  /** Wraps `value` in a [[Secret]]. */
  def apply(value: String): Secret = new Secret(value)

  /** Returns the value that `secret` wraps. */
  def unwrap(secret: Secret): String = secret.value

  /**
   * A [[Displayable]] that shows every secret as `<secret>`. The
   * `zio.blocks.config` package object provides it as an implicit.
   */
  def displayable: Displayable[Secret] = Displayable.instance(_ => "<secret>")

  /** Reads and writes a [[Secret]] as a plain string. */
  implicit val schema: Schema[Secret] = Schema[String].transform(Secret.apply, Secret.unwrap)
}
