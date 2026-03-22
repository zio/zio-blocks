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

package zio.blocks.codegen.ir

/**
 * Represents a Scala annotation in the IR.
 *
 * @param name
 *   The name of the annotation (e.g., "deprecated", "required")
 * @param args
 *   List of annotation arguments as (name, value) pairs (defaults to empty
 *   list)
 *
 * @example
 *   {{{
 * // Annotation without arguments
 * val required = Annotation("required")
 *
 * // Annotation with arguments
 * val deprecated = Annotation("deprecated", List(("message", "\"use v2\"")))
 *   }}}
 */
final case class Annotation(
  name: String,
  args: List[(String, String)] = Nil
)
