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

sealed abstract class Version(val major: Int, val minor: Int) {
  val text: String              = s"HTTP/$major.$minor"
  override def toString: String = text
}

object Version {
  case object `HTTP/1.0` extends Version(1, 0)
  case object `HTTP/1.1` extends Version(1, 1)
  case object `HTTP/2.0` extends Version(2, 0)
  case object `HTTP/3.0` extends Version(3, 0)

  val values: Chunk[Version] = Chunk(`HTTP/1.0`, `HTTP/1.1`, `HTTP/2.0`, `HTTP/3.0`)

  def fromString(s: String): Option[Version] = s match {
    case "HTTP/1.0"            => Some(`HTTP/1.0`)
    case "HTTP/1.1"            => Some(`HTTP/1.1`)
    case "HTTP/2.0" | "HTTP/2" => Some(`HTTP/2.0`)
    case "HTTP/3.0" | "HTTP/3" => Some(`HTTP/3.0`)
    case _                     => None
  }

  def render(version: Version): String = version.text
}
