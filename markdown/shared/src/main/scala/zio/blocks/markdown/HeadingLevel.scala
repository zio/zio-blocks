/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
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

package zio.blocks.markdown

/**
 * Type-safe heading level for Markdown headings (H1-H6).
 */
sealed abstract class HeadingLevel(val level: Int) extends Product with Serializable {
  def toInt: Int = level
}

object HeadingLevel {
  case object H1 extends HeadingLevel(1)
  case object H2 extends HeadingLevel(2)
  case object H3 extends HeadingLevel(3)
  case object H4 extends HeadingLevel(4)
  case object H5 extends HeadingLevel(5)
  case object H6 extends HeadingLevel(6)

  val values: List[HeadingLevel] = List(H1, H2, H3, H4, H5, H6)

  def fromInt(n: Int): Option[HeadingLevel] = n match {
    case 1 => Some(H1)
    case 2 => Some(H2)
    case 3 => Some(H3)
    case 4 => Some(H4)
    case 5 => Some(H5)
    case 6 => Some(H6)
    case _ => None
  }

  def unsafeFromInt(n: Int): HeadingLevel =
    fromInt(n).getOrElse(throw new IllegalArgumentException(s"Invalid heading level: $n (must be 1-6)"))
}
