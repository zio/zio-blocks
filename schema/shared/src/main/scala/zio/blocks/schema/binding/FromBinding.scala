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

package zio.blocks.schema.binding

/**
 * Wraps a [[Binding]] as the metadata `F[T, A]` of a `Reflect` node. The
 * `Reflect` constructors for standard types, such as `Reflect.int` and
 * `Reflect.option`, take one to store the bindings they create. The library's
 * instance is `Binding.bindingFromBinding`, for `F` = `Binding`.
 *
 * @tparam F
 *   the metadata type of the nodes
 */
trait FromBinding[F[_, _]] {

  /** Returns `binding` as metadata of type `F`. */
  def fromBinding[T, A](binding: Binding[T, A]): F[T, A]
}
