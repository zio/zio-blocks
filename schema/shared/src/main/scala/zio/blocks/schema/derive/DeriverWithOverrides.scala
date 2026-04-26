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

import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.typeid.TypeId
import zio.blocks.docs.Doc

/**
 * A deriver wrapper that accumulates instance and modifier overrides on top of
 * a wrapped deriver. All abstract derivation methods delegate to the wrapped
 * deriver; `instanceOverrides` and `modifierOverrides` return the union of the
 * wrapped deriver's overrides and the additional ones registered via
 * `withInstance` / `withModifier`.
 *
 * Chaining is naturally supported: each call to `withInstance` or
 * `withModifier` wraps the current deriver in a new layer, and overrides
 * accumulate.
 */
private[derive] final class DeriverWithOverrides[TC[_]](
  wrapped: Deriver[TC],
  additionalInstances: IndexedSeq[InstanceOverride],
  additionalModifiers: IndexedSeq[ModifierOverride]
) extends Deriver[TC] {

  override def derivePrimitive[A](
    primitiveType: PrimitiveType[A],
    typeId: TypeId[A],
    binding: Binding.Primitive[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  ): Lazy[TC[A]] =
    wrapped.derivePrimitive(primitiveType, typeId, binding, doc, modifiers, defaultValue, examples)

  override def deriveRecord[F[_, _], A](
    fields: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Record[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[A]] =
    wrapped.deriveRecord(fields, typeId, binding, doc, modifiers, defaultValue, examples)

  override def deriveVariant[F[_, _], A](
    cases: IndexedSeq[Term[F, A, ?]],
    typeId: TypeId[A],
    binding: Binding.Variant[A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[A]] =
    wrapped.deriveVariant(cases, typeId, binding, doc, modifiers, defaultValue, examples)

  override def deriveSequence[F[_, _], C[_], A](
    element: Reflect[F, A],
    typeId: TypeId[C[A]],
    binding: Binding.Seq[C, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[C[A]],
    examples: Seq[C[A]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[C[A]]] =
    wrapped.deriveSequence(element, typeId, binding, doc, modifiers, defaultValue, examples)

  override def deriveMap[F[_, _], M[_, _], K, V](
    key: Reflect[F, K],
    value: Reflect[F, V],
    typeId: TypeId[M[K, V]],
    binding: Binding.Map[M, K, V],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[M[K, V]],
    examples: Seq[M[K, V]]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[M[K, V]]] =
    wrapped.deriveMap(key, value, typeId, binding, doc, modifiers, defaultValue, examples)

  override def deriveDynamic[F[_, _]](
    binding: Binding.Dynamic,
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[DynamicValue],
    examples: Seq[DynamicValue]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[DynamicValue]] =
    wrapped.deriveDynamic(binding, doc, modifiers, defaultValue, examples)

  override def deriveWrapper[F[_, _], A, B](
    wrapped0: Reflect[F, B],
    typeId: TypeId[A],
    binding: Binding.Wrapper[A, B],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    defaultValue: Option[A],
    examples: Seq[A]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[A]] =
    wrapped.deriveWrapper(wrapped0, typeId, binding, doc, modifiers, defaultValue, examples)

  override def instanceOverrides: IndexedSeq[InstanceOverride] =
    wrapped.instanceOverrides ++ additionalInstances

  override def modifierOverrides: IndexedSeq[ModifierOverride] =
    wrapped.modifierOverrides ++ additionalModifiers
}
