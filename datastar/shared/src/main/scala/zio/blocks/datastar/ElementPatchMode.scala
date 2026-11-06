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

/**
 * Where an element patch puts its content relative to the target element, for
 * [[DatastarEvent.PatchElementsBuilder#mode]]. `Outer` is the default, and an
 * event in `Outer` mode has no `mode` line.
 *
 * {{{
 * ElementPatchMode.Append.render // "append"
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/datastar/sse/ Server-Sent Events]]
 */
sealed trait ElementPatchMode extends Product with Serializable {

  /** Returns the mode's name in lower case, the value of the `mode` line. */
  def render: String = productPrefix.toLowerCase
}

/** The eight patch modes of the Datastar protocol. */
object ElementPatchMode {

  /** Morphs the target element into the new content; the default mode. */
  case object Outer extends ElementPatchMode

  /** Morphs the target element's children into the new content. */
  case object Inner extends ElementPatchMode

  /** Replaces the target element with the new content, without morphing. */
  case object Replace extends ElementPatchMode

  /** Inserts content at the beginning of the target element's children. */
  case object Prepend extends ElementPatchMode

  /** Inserts content at the end of the target element's children. */
  case object Append extends ElementPatchMode

  /** Inserts content immediately before the target element. */
  case object Before extends ElementPatchMode

  /** Inserts content immediately after the target element. */
  case object After extends ElementPatchMode

  /** Removes the target element. */
  case object Remove extends ElementPatchMode
}
