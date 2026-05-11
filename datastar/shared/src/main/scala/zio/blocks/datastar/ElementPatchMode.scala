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
 * Describes how Datastar should patch rendered content into the target element.
 *
 * `Outer` is the default patch mode and is omitted from SSE rendering.
 */
sealed trait ElementPatchMode extends Product with Serializable {

  /** Renders the lowercase SSE value for this patch mode. */
  def render: String = productPrefix.toLowerCase
}

object ElementPatchMode {

  /** Replaces the target element itself. */
  case object Outer extends ElementPatchMode

  /** Replaces the target element's children. */
  case object Inner extends ElementPatchMode

  /** Replaces the target element using replacement semantics. */
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
