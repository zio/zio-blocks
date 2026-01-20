/*
 * Copyright 2023 ZIO Blocks Maintainers
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

import zio.blocks.schema.{Lazy, ReflectTransformer}

trait HasBinding[F[_, _]] extends ReflectTransformer.OnlyMetadata[F, Binding] {
  override def transformMetadata[T, A](f: F[T, A]): Lazy[Binding[T, A]] = Lazy(binding(f))

  def binding[T, A](fa: F[T, A]): Binding[T, A]

  def updateBinding[T, A](fa: F[T, A], f: Binding[T, A] => Binding[T, A]): F[T, A]

  final def primitive[A](fa: F[BindingType.Primitive, A]): Binding.Primitive[A] =
    binding(fa) match {
      case primitive: Binding.Primitive[A] => primitive
      case _                               => sys.error("Expected Binding.Primitive")
    }

  final def updatePrimitive[A](
    fa: F[BindingType.Primitive, A],
    f: Binding.Primitive[A] => Binding.Primitive[A]
  ): F[BindingType.Primitive, A] =
    updateBinding(
      fa,
      {
        case primitive: Binding.Primitive[A] => f(primitive)
        case _                               => sys.error("Expected Binding.Primitive")
      }
    )

  final def record[A](fa: F[BindingType.Record, A]): Binding.Record[A] =
    binding(fa) match {
      case record: Binding.Record[A] => record
      case _                         => sys.error("Expected Binding.Record")
    }

  final def variant[A](fa: F[BindingType.Variant, A]): Binding.Variant[A] =
    binding(fa) match {
      case variant: Binding.Variant[A] => variant
      case _                           => sys.error("Expected Binding.Variant")
    }

  final def constructor[A](fa: F[BindingType.Record, A]): Constructor[A] = record(fa).constructor

  final def updateConstructor[A](
    fa: F[BindingType.Record, A],
    f: Constructor[A] => Constructor[A]
  ): F[BindingType.Record, A] =
    updateBinding(
      fa,
      {
        case record: Binding.Record[A] => record.copy(constructor = f(record.constructor))
        case _                         => sys.error("Expected Binding.Record")
      }
    )

  final def deconstructor[A](fa: F[BindingType.Record, A]): Deconstructor[A] = record(fa).deconstructor

  final def updateDeconstructor[A](
    fa: F[BindingType.Record, A],
    f: Deconstructor[A] => Deconstructor[A]
  ): F[BindingType.Record, A] =
    updateBinding(
      fa,
      {
        case record: Binding.Record[A] => record.copy(deconstructor = f(record.deconstructor))
        case _                         => sys.error("Expected Binding.Record")
      }
    )

  final def discriminator[A](fa: F[BindingType.Variant, A]): Discriminator[A] = variant(fa).discriminator

  final def updateDiscriminator[A](
    fa: F[BindingType.Variant, A],
    f: Discriminator[A] => Discriminator[A]
  ): F[BindingType.Variant, A] =
    updateBinding(
      fa,
      {
        case variant: Binding.Variant[A] => variant.copy(discriminator = f(variant.discriminator))
        case _                           => sys.error("Expected Binding.Variant")
      }
    )

  final def matchers[A](fa: F[BindingType.Variant, A]): Matchers[A] = variant(fa).matchers

  final def updateMatchers[A](fa: F[BindingType.Variant, A], f: Matchers[A] => Matchers[A]): F[BindingType.Variant, A] =
    updateBinding(
      fa,
      {
        case variant: Binding.Variant[A] => variant.copy(matchers = f(variant.matchers))
        case _                           => sys.error("Expected Binding.Variant")
      }
    )

  final def mapConstructor[M[_, _], K, V](fa: F[BindingType.Map[M], M[K, V]]): MapConstructor[M] =
    map(fa).constructor

  final def mapDeconstructor[M[_, _], K, V](fa: F[BindingType.Map[M], M[K, V]]): MapDeconstructor[M] =
    map(fa).deconstructor

  final def seqConstructor[C[_], A](fa: F[BindingType.Seq[C], C[A]]): SeqConstructor[C] =
    seq(fa).constructor

  final def seqDeconstructor[C[_], A](fa: F[BindingType.Seq[C], C[A]]): SeqDeconstructor[C] =
    seq(fa).deconstructor

  final def map[M[_, _], K, V](fa: F[BindingType.Map[M], M[K, V]]): Binding.Map[M, K, V] =
    binding(fa) match {
      case map: Binding.Map[M, K, V] @scala.unchecked => map
      case _                                          => sys.error("Expected Binding.Map")
    }

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

  final def seq[C[_], A](fa: F[BindingType.Seq[C], C[A]]): Binding.Seq[C, A] =
    binding(fa) match {
      case seq: Binding.Seq[C, A] @scala.unchecked => seq
      case _                                       => sys.error("Expected Binding.Seq")
    }

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

  final def wrapper[A, B](fa: F[BindingType.Wrapper[A, B], A]): Binding.Wrapper[A, B] =
    binding(fa) match {
      case wrapper: Binding.Wrapper[A, B] => wrapper
      case _                              => sys.error("Expected Binding.Wrapper")
    }
}
