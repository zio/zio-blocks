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
 * The binding kinds: the phantom type `T` of `Binding[T, A]` and of a `Reflect`
 * node's metadata `F[T, A]`. `BindingType` has no values; its members only name
 * which kind of [[Binding]] a node carries.
 */
final abstract class BindingType

/**
 * One binding kind per `Reflect` node kind (a `Reflect.Deferred` node uses the
 * binding of the node it defers to).
 */
object BindingType {

  /** The kind of [[Binding.Record]], for `Reflect.Record` nodes. */
  type Record <: BindingType

  /** The kind of [[Binding.Variant]], for `Reflect.Variant` nodes. */
  type Variant <: BindingType

  /**
   * The kind of [[Binding.Seq]] for a collection type constructor, for
   * `Reflect.Sequence` nodes.
   */
  type Seq[_[_]] <: BindingType

  /**
   * The kind of [[Binding.Map]] for a map type constructor, for `Reflect.Map`
   * nodes.
   */
  type Map[_[_, _]] <: BindingType

  /** The kind of [[Binding.Primitive]], for `Reflect.Primitive` nodes. */
  type Primitive <: BindingType

  /**
   * The kind of [[Binding.Wrapper]] for a wrapper type and the type it wraps,
   * for `Reflect.Wrapper` nodes.
   */
  type Wrapper[_, _] <: BindingType

  /** The kind of [[Binding.Dynamic]], for `Reflect.Dynamic` nodes. */
  type Dynamic <: BindingType
}
