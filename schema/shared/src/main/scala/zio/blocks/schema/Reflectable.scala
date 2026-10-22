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

package zio.blocks.schema

import zio.blocks.docs.Doc

/**
 * Something that carries documentation and modifiers: a [[Reflect]] node or a
 * [[Term]].
 */
trait Reflectable[A] {

  /** Returns the modifiers attached to this node or term. */
  def modifiers: Seq[Modifier]

  /**
   * Returns the documentation of this node or term, or `Doc.empty` when it has
   * none.
   */
  def doc: Doc
}
