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

package zio.blocks.schema.derive

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.typeid.TypeId
import zio.blocks.docs.Doc

trait Deriver[TC[_]] { self =>
  type HasInstance[F[_, _]] = zio.blocks.schema.derive.HasInstance[F, TC]

  final def binding[F[_, _], T, A](fa: F[T, A])(implicit F: HasBinding[F]): Binding[T, A] = F.binding(fa)

  final def instance[F[_, _], T, A](fa: F[T, A])(implicit D: HasInstance[F]): Lazy[TC[A]] = D.instance(fa)

  def derivePrimitive[A](
    primitiveType: PrimitiveType[A],
    typeId: TypeId[A],
    binding: Binding.Primitive[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  ): Lazy[TC[A]]

  def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Record[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[A]]

  def deriveVariant[F[_, _], A](
    cases: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Variant[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[A]]

  def deriveSequence[F[_, _], C[_], A](
    element: Reflect[F, A],
    typeId: TypeId[C[A]],
    binding: Binding.Seq[C, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[C[A]],
    examples: Seq[C[A]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[C[A]]]

  def deriveMap[F[_, _], M[_, _], K, V](
    key: Reflect[F, K],
    value: Reflect[F, V],
    typeId: TypeId[M[K, V]],
    binding: Binding.Map[M, K, V],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[M[K, V]],
    examples: Seq[M[K, V]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[M[K, V]]]

  def deriveDynamic[F[_, _]](
    binding: Binding.Dynamic,
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[DynamicValue],
    examples: Seq[DynamicValue]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[DynamicValue]]

  def deriveWrapper[F[_, _], A, B](
    wrapped: Reflect[F, B],
    typeId: TypeId[A],
    binding: Binding.Wrapper[A, B],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[A]]

  def instanceOverrides: IndexedSeq[InstanceOverride] = Chunk.empty

  def modifierOverrides: IndexedSeq[ModifierOverride] = Chunk.empty

  /**
   * Returns a new deriver that pre-registers a type-level instance override.
   * During derivation, every occurrence of type `A` will use the supplied
   * instance instead of deriving one.
   */
  final def withInstance[A](instance: => TC[A])(implicit typeId: TypeId[A]): Deriver[TC] =
    new DeriverWithOverrides[TC](
      self,
      Chunk(new InstanceOverrideByType[TC, A](typeId, Lazy(instance))),
      Chunk.empty
    )

  /**
   * Returns a new deriver that pre-registers a field-level instance override.
   * During derivation, the field named `termName` inside the parent type
   * identified by `typeId` will use the supplied instance.
   */
  final def withInstance[A](typeId: TypeId[A], termName: String, instance: => TC[Any]): Deriver[TC] =
    new DeriverWithOverrides[TC](
      self,
      Chunk(new InstanceOverrideByTypeAndTermName[TC, A, Any](typeId, termName, Lazy(instance))),
      Chunk.empty
    )

  /**
   * Returns a new deriver that pre-registers a reflect-level modifier override.
   * During derivation, every occurrence of the type identified by `typeId` will
   * have the modifier prepended to its modifiers.
   */
  final def withModifier[A](typeId: TypeId[A], modifier: Modifier.Reflect): Deriver[TC] =
    new DeriverWithOverrides[TC](
      self,
      Chunk.empty,
      Chunk(new ModifierReflectOverrideByType[A](typeId, modifier))
    )

  /**
   * Returns a new deriver that pre-registers a term-level modifier override.
   * During derivation, the field named `termName` inside the parent type
   * identified by `typeId` will have the modifier applied.
   */
  final def withModifier[A](typeId: TypeId[A], termName: String, modifier: Modifier.Term): Deriver[TC] =
    new DeriverWithOverrides[TC](
      self,
      Chunk.empty,
      Chunk(new ModifierTermOverrideByType[A](typeId, termName, modifier))
    )
}
