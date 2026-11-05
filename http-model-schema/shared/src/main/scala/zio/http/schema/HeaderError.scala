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

package zio.http.schema

/**
 * Why `header`, `headerAll` or `headerOrElse` could not read a header: it is
 * [[HeaderError.Missing]], or its value is [[HeaderError.Malformed]]. Both
 * carry the name as the caller spelled it. Match on the case to tell a client's
 * omission from a bad value, or use `message` for a ready-made sentence.
 */
sealed trait HeaderError extends Product with Serializable {

  /** A one-line, human-readable description of the error. */
  def message: String
}

/** The two kinds of [[HeaderError]]. */
object HeaderError {

  /**
   * No header is named `name`. Its `message` reads
   * `Missing header: X-Request-Id`.
   */
  final case class Missing(name: String) extends HeaderError {

    /** `Missing header: ` followed by the name. */
    def message: String = s"Missing header: $name"
  }

  /**
   * The header `name` is present but its `value` does not decode; `cause` says
   * why, for example `Cannot parse 'two' as Int`, or gives the message of the
   * `Header.Codec` that failed. Its `message` reads
   * `Malformed header 'X-Api-Version' value 'two': Cannot parse 'two' as Int`.
   * A derived [[HeaderCodec]] reports a bad value with this same message inside
   * its [[zio.blocks.schema.SchemaError]].
   */
  final case class Malformed(name: String, value: String, cause: String) extends HeaderError {

    /** Names the header, the raw value and the cause, in that order. */
    def message: String = s"Malformed header '$name' value '$value': $cause"
  }
}
