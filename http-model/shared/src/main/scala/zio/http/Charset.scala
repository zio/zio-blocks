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

sealed abstract class Charset(val name: String) extends CharsetPlatformSpecific {
  override def toString: String = name
}

object Charset {
  case object UTF8       extends Charset("UTF-8")
  case object ASCII      extends Charset("US-ASCII")
  case object ISO_8859_1 extends Charset("ISO-8859-1")
  case object UTF16      extends Charset("UTF-16")
  case object UTF16BE    extends Charset("UTF-16BE")
  case object UTF16LE    extends Charset("UTF-16LE")

  val values: Chunk[Charset] = Chunk(UTF8, ASCII, ISO_8859_1, UTF16, UTF16BE, UTF16LE)

  def fromString(s: String): Option[Charset] = s.toUpperCase match {
    case "UTF-8" | "UTF8"                    => Some(UTF8)
    case "US-ASCII" | "ASCII"                => Some(ASCII)
    case "ISO-8859-1" | "LATIN1" | "LATIN-1" => Some(ISO_8859_1)
    case "UTF-16"                            => Some(UTF16)
    case "UTF-16BE"                          => Some(UTF16BE)
    case "UTF-16LE"                          => Some(UTF16LE)
    case _                                   => None
  }

  def render(charset: Charset): String = charset.name
}
