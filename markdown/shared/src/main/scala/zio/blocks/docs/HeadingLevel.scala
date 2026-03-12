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

package zio.blocks.docs

/**
 * Heading level for ATX-style headings.
 *
 * GFM supports six levels of headings, from H1 (# ) to H6 (###### ).
 *
 * @param value
 *   The numeric heading level (1-6)
 */
sealed abstract class HeadingLevel(val value: Int) extends Product with Serializable

object HeadingLevel {

  /** Heading level 1 (# ). */
  case object H1 extends HeadingLevel(1)

  /** Heading level 2 (## ). */
  case object H2 extends HeadingLevel(2)

  /** Heading level 3 (### ). */
  case object H3 extends HeadingLevel(3)

  /** Heading level 4 (#### ). */
  case object H4 extends HeadingLevel(4)

  /** Heading level 5 (##### ). */
  case object H5 extends HeadingLevel(5)

  /** Heading level 6 (###### ). */
  case object H6 extends HeadingLevel(6)

  /**
   * Safely construct a heading level from an integer.
   *
   * @param n
   *   The heading level (must be 1-6)
   * @return
   *   Some(level) if n is 1-6, None otherwise
   */
  def fromInt(n: Int): Option[HeadingLevel] = n match {
    case 1 => Some(H1)
    case 2 => Some(H2)
    case 3 => Some(H3)
    case 4 => Some(H4)
    case 5 => Some(H5)
    case 6 => Some(H6)
    case _ => None
  }

  /**
   * Construct a heading level from an integer, throwing on invalid input.
   *
   * @param n
   *   The heading level (must be 1-6)
   * @return
   *   The heading level
   * @throws java.lang.IllegalArgumentException
   *   if n is not 1-6
   */
  def unsafeFromInt(n: Int): HeadingLevel =
    fromInt(n).getOrElse(throw new IllegalArgumentException(s"Invalid heading level: $n"))
}
