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
 * The alignment of a GFM table column, set by colons in the delimiter row:
 * `:---` for [[Alignment.Left]], `---:` for [[Alignment.Right]], `:---:` for
 * [[Alignment.Center]] and `---` for [[Alignment.None]]. [[HtmlRenderer]]
 * writes a `text-align` style for the first three, and [[TerminalRenderer]]
 * ignores alignment.
 */
sealed trait Alignment extends Product with Serializable

/** The four column alignments. */
object Alignment {

  /** A left-aligned column, written `:---`. */
  case object Left extends Alignment

  /** A right-aligned column, written `---:`. */
  case object Right extends Alignment

  /** A centered column, written `:---:`. */
  case object Center extends Alignment

  /** A column with no stated alignment, written `---`. */
  case object None extends Alignment
}
