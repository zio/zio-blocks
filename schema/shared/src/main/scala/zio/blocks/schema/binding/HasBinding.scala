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

import zio.blocks.schema.{DynamicValue, Lazy, ReflectTransformer}

/**
 * Reads and replaces the [[Binding]] inside a `Reflect` node's metadata
 * `F[T, A]`. The `Reflect` methods that need a binding, such as
 * `Reflect.Record#constructor` and `Reflect#fromDynamicValue`, take one
 * implicitly. The library provides instances for `Binding` itself
 * (`Binding.bindingHasBinding`) and for `BindingInstance`, which pairs a
 * binding with a derived type class instance.
 *
 * A `HasBinding` is also a [[zio.blocks.schema.ReflectTransformer]] that turns
 * every node's metadata into its `Binding`.
 *
 * The typed accessors (`record`, `constructor`, `seq` and the others) check the
 * binding's kind at run time and throw a `RuntimeException` naming the expected
 * kind when it does not match.
 *
 * @tparam F
 *   the metadata type of the nodes
 */
trait HasBinding[F[_, _]] extends ReflectTransformer.OnlyMetadata[F, Binding] {
  override def transformMetadata[T, A](f: F[T, A]): Lazy[Binding[T, A]] = Lazy(binding(f))

  /** Returns the binding inside `fa`. */
  def binding[T, A](fa: F[T, A]): Binding[T, A]

  /**
   * Returns `fa` with its binding replaced by the result of `f`. The library's
   * instances call `f` at once.
   */
  def updateBinding[T, A](fa: F[T, A], f: Binding[T, A] => Binding[T, A]): F[T, A]

  /**
   * Returns the binding of `fa`, which must be a `Binding.Primitive`; throws a
   * `RuntimeException` (`Expected Binding.Primitive`) when it is another kind.
   */
  final def primitive[A](fa: F[BindingType.Primitive, A]): Binding.Primitive[A] =
    binding(fa) match {
      case primitive: Binding.Primitive[A] @scala.unchecked => primitive
      case _                                                => sys.error("Expected Binding.Primitive")
    }

  /**
   * Returns `fa` with its primitive binding replaced by the result of `f`. It
   * calls `updateBinding` with a function that throws a `RuntimeException`
   * (`Expected Binding.Primitive`) when the binding is another kind; with the
   * library's instances that function runs during this call.
   */
  final def updatePrimitive[A](
    fa: F[BindingType.Primitive, A],
    f: Binding.Primitive[A] => Binding.Primitive[A]
  ): F[BindingType.Primitive, A] =
    updateBinding(
      fa,
      {
        case primitive: Binding.Primitive[A] @scala.unchecked => f(primitive)
        case _                                                => sys.error("Expected Binding.Primitive")
      }
    )

  /**
   * Returns the binding of `fa`, which must be a `Binding.Record`; throws a
   * `RuntimeException` (`Expected Binding.Record`) when it is another kind.
   */
  final def record[A](fa: F[BindingType.Record, A]): Binding.Record[A] =
    binding(fa) match {
      case record: Binding.Record[A] @scala.unchecked => record
      case _                                          => sys.error("Expected Binding.Record")
    }

  /**
   * Returns the binding of `fa`, which must be a `Binding.Variant`; throws a
   * `RuntimeException` (`Expected Binding.Variant`) when it is another kind.
   */
  final def variant[A](fa: F[BindingType.Variant, A]): Binding.Variant[A] =
    binding(fa) match {
      case variant: Binding.Variant[A] @scala.unchecked => variant
      case _                                            => sys.error("Expected Binding.Variant")
    }

  /**
   * Returns the constructor of the record binding of `fa`; throws like `record`
   * when it is not a `Binding.Record`.
   */
  final def constructor[A](fa: F[BindingType.Record, A]): Constructor[A] = record(fa).constructor

  /**
   * Returns `fa` with the constructor of its record binding replaced by the
   * result of `f`. It calls `updateBinding` with a function that throws a
   * `RuntimeException` (`Expected Binding.Record`) when the binding is another
   * kind; with the library's instances that function runs during this call.
   */
  final def updateConstructor[A](
    fa: F[BindingType.Record, A],
    f: Constructor[A] => Constructor[A]
  ): F[BindingType.Record, A] =
    updateBinding(
      fa,
      {
        case record: Binding.Record[A] @scala.unchecked => record.copy(constructor = f(record.constructor))
        case _                                          => sys.error("Expected Binding.Record")
      }
    )

  /**
   * Returns the deconstructor of the record binding of `fa`; throws like
   * `record` when it is not a `Binding.Record`.
   */
  final def deconstructor[A](fa: F[BindingType.Record, A]): Deconstructor[A] = record(fa).deconstructor

  /**
   * Returns `fa` with the deconstructor of its record binding replaced by the
   * result of `f`. It calls `updateBinding` with a function that throws a
   * `RuntimeException` (`Expected Binding.Record`) when the binding is another
   * kind; with the library's instances that function runs during this call.
   */
  final def updateDeconstructor[A](
    fa: F[BindingType.Record, A],
    f: Deconstructor[A] => Deconstructor[A]
  ): F[BindingType.Record, A] =
    updateBinding(
      fa,
      {
        case record: Binding.Record[A] @scala.unchecked => record.copy(deconstructor = f(record.deconstructor))
        case _                                          => sys.error("Expected Binding.Record")
      }
    )

  /**
   * Returns the discriminator of the variant binding of `fa`; throws like
   * `variant` when it is not a `Binding.Variant`.
   */
  final def discriminator[A](fa: F[BindingType.Variant, A]): Discriminator[A] = variant(fa).discriminator

  /**
   * Returns `fa` with the discriminator of its variant binding replaced by the
   * result of `f`. It calls `updateBinding` with a function that throws a
   * `RuntimeException` (`Expected Binding.Variant`) when the binding is another
   * kind; with the library's instances that function runs during this call.
   */
  final def updateDiscriminator[A](
    fa: F[BindingType.Variant, A],
    f: Discriminator[A] => Discriminator[A]
  ): F[BindingType.Variant, A] =
    updateBinding(
      fa,
      {
        case variant: Binding.Variant[A] @scala.unchecked => variant.copy(discriminator = f(variant.discriminator))
        case _                                            => sys.error("Expected Binding.Variant")
      }
    )

  /**
   * Returns the matchers of the variant binding of `fa`; throws like `variant`
   * when it is not a `Binding.Variant`.
   */
  final def matchers[A](fa: F[BindingType.Variant, A]): Matchers[A] = variant(fa).matchers

  /**
   * Returns `fa` with the matchers of its variant binding replaced by the
   * result of `f`. It calls `updateBinding` with a function that throws a
   * `RuntimeException` (`Expected Binding.Variant`) when the binding is another
   * kind; with the library's instances that function runs during this call.
   */
  final def updateMatchers[A](fa: F[BindingType.Variant, A], f: Matchers[A] => Matchers[A]): F[BindingType.Variant, A] =
    updateBinding(
      fa,
      {
        case variant: Binding.Variant[A] @scala.unchecked => variant.copy(matchers = f(variant.matchers))
        case _                                            => sys.error("Expected Binding.Variant")
      }
    )

  /**
   * Returns the constructor of the map binding of `fa`; throws like `map` when
   * it is not a `Binding.Map`.
   */
  final def mapConstructor[M[_, _], K, V](fa: F[BindingType.Map[M], M[K, V]]): MapConstructor[M] =
    map(fa).constructor

  /**
   * Returns the deconstructor of the map binding of `fa`; throws like `map`
   * when it is not a `Binding.Map`.
   */
  final def mapDeconstructor[M[_, _], K, V](fa: F[BindingType.Map[M], M[K, V]]): MapDeconstructor[M] =
    map(fa).deconstructor

  /**
   * Returns the constructor of the sequence binding of `fa`; throws like `seq`
   * when it is not a `Binding.Seq`.
   */
  final def seqConstructor[C[_], A](fa: F[BindingType.Seq[C], C[A]]): SeqConstructor[C] =
    seq(fa).constructor

  /**
   * Returns the deconstructor of the sequence binding of `fa`; throws like
   * `seq` when it is not a `Binding.Seq`.
   */
  final def seqDeconstructor[C[_], A](fa: F[BindingType.Seq[C], C[A]]): SeqDeconstructor[C] =
    seq(fa).deconstructor

  /**
   * Returns the binding of `fa`, which must be a `Binding.Map`; throws a
   * `RuntimeException` (`Expected Binding.Map`) when it is another kind.
   */
  final def map[M[_, _], K, V](fa: F[BindingType.Map[M], M[K, V]]): Binding.Map[M, K, V] =
    binding(fa) match {
      case map: Binding.Map[M, K, V] @scala.unchecked => map
      case _                                          => sys.error("Expected Binding.Map")
    }

  /**
   * Returns `fa` with its map binding replaced by the result of `f`. It calls
   * `updateBinding` with a function that throws a `RuntimeException` (`Expected
   * Binding.Map`) when the binding is another kind; with the library's
   * instances that function runs during this call.
   */
  final def updateMap[M[_, _], K, V](
    fa: F[BindingType.Map[M], M[K, V]],
    f: Binding.Map[M, K, V] => Binding.Map[M, K, V]
  ): F[BindingType.Map[M], M[K, V]] =
    updateBinding(
      fa,
      {
        case map: Binding.Map[M, K, V] @scala.unchecked => f(map)
        case _                                          => sys.error("Expected Binding.Map")
      }
    )

  /**
   * Returns the binding of `fa`, which must be a `Binding.Seq`; throws a
   * `RuntimeException` (`Expected Binding.Seq`) when it is another kind.
   */
  final def seq[C[_], A](fa: F[BindingType.Seq[C], C[A]]): Binding.Seq[C, A] =
    binding(fa) match {
      case seq: Binding.Seq[C, A] @scala.unchecked => seq
      case _                                       => sys.error("Expected Binding.Seq")
    }

  /**
   * Returns `fa` with its sequence binding replaced by the result of `f`. It
   * calls `updateBinding` with a function that throws a `RuntimeException`
   * (`Expected Binding.Seq`) when the binding is another kind; with the
   * library's instances that function runs during this call.
   */
  final def updateSeq[C[_], A](
    fa: F[BindingType.Seq[C], C[A]],
    f: Binding.Seq[C, A] => Binding.Seq[C, A]
  ): F[BindingType.Seq[C], C[A]] =
    updateBinding(
      fa,
      {
        case seq: Binding.Seq[C, A] @scala.unchecked => f(seq)
        case _                                       => sys.error("Expected Binding.Seq")
      }
    )

  /**
   * Returns the binding of `fa`, which must be a `Binding.Wrapper`; throws a
   * `RuntimeException` (`Expected Binding.Wrapper`) when it is another kind.
   */
  final def wrapper[A, B](fa: F[BindingType.Wrapper[A, B], A]): Binding.Wrapper[A, B] =
    binding(fa) match {
      case wrapper: Binding.Wrapper[A, B] @scala.unchecked => wrapper
      case _                                               => sys.error("Expected Binding.Wrapper")
    }

  /**
   * Returns the binding of `fa`, which must be a `Binding.Dynamic`; throws a
   * `RuntimeException` (`Expected Binding.Dynamic`) when it is another kind.
   */
  final def dynamic(fa: F[BindingType.Dynamic, DynamicValue]): Binding.Dynamic =
    binding(fa) match {
      case dynamic: Binding.Dynamic => dynamic
      case _                        => sys.error("Expected Binding.Dynamic")
    }
}
