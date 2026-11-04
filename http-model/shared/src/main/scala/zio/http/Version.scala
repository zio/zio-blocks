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

import zio.blocks.chunk.Chunk

/**
 * An HTTP protocol version: one of `HTTP/1.0`, `HTTP/1.1`, `HTTP/2.0` and
 * `HTTP/3.0`. `toString` returns `text`.
 */
sealed abstract class Version(val major: Int, val minor: Int) {

  /** The version as written on the wire, `HTTP/<major>.<minor>`. */
  val text: String              = s"HTTP/$major.$minor"
  override def toString: String = text
}

/** The [[Version]] values and their text conversions. */
object Version {

  /** HTTP/1.0. */
  case object `HTTP/1.0` extends Version(1, 0)

  /** HTTP/1.1. */
  case object `HTTP/1.1` extends Version(1, 1)

  /** HTTP/2, rendered as `HTTP/2.0`. */
  case object `HTTP/2.0` extends Version(2, 0)

  /** HTTP/3, rendered as `HTTP/3.0`. */
  case object `HTTP/3.0` extends Version(3, 0)

  /** The four versions, from `HTTP/1.0` to `HTTP/3.0`. */
  val values: Chunk[Version] = Chunk(`HTTP/1.0`, `HTTP/1.1`, `HTTP/2.0`, `HTTP/3.0`)

  /**
   * Returns the version whose text is exactly `s`. Besides `HTTP/1.0`,
   * `HTTP/1.1`, `HTTP/2.0` and `HTTP/3.0`, the short forms `HTTP/2` and
   * `HTTP/3` are accepted. Matching is case-sensitive and does not trim
   * whitespace: `http/1.1`, `"HTTP/1.1 "`, `HTTP/2.1` and the empty string give
   * `None`.
   */
  def fromString(s: String): Option[Version] = s match {
    case "HTTP/1.0"            => Some(`HTTP/1.0`)
    case "HTTP/1.1"            => Some(`HTTP/1.1`)
    case "HTTP/2.0" | "HTTP/2" => Some(`HTTP/2.0`)
    case "HTTP/3.0" | "HTTP/3" => Some(`HTTP/3.0`)
    case _                     => None
  }

  /** Returns the `text` of `version`. */
  def render(version: Version): String = version.text
}
