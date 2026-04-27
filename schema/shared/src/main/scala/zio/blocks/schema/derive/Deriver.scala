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
   *
   * When the returned deriver is passed to `DerivationBuilder`, overrides
   * registered here take precedence over builder-level overrides for the same
   * target because `DerivationBuilder.derive` merges them as
   * `builderOverrides ++ deriver.instanceOverrides` and later entries win in
   * the resulting `.toMap`.
   *
   * @tparam A
   *   the type whose derived instance should be replaced
   * @param instance
   *   the custom type-class instance (evaluated lazily)
   * @param typeId
   *   implicit identifier for `A`, used to match occurrences during derivation
   * @return
   *   a new `Deriver` that wraps this deriver with the additional override
   */
  final def withInstance[A](instance: => TC[A])(implicit typeId: TypeId[A]): Deriver[TC] =
    new DeriverWithOverrides[TC](
      self,
      Chunk(new InstanceOverrideByType[TC, A](typeId, Lazy(instance))),
      Chunk.empty
    )

  /**
   * Returns a new deriver that pre-registers a field-level instance override.
   * During derivation, the field named `termName` inside the parent
   * record/variant identified by `typeId` will use the supplied instance
   * instead of deriving one.
   *
   * This is a medium-precision override between optic-based (exact path) and
   * type-based (all occurrences).
   *
   * @tparam A
   *   the parent record or variant type that owns the field
   * @tparam B
   *   the type of the field being overridden. Note that `B` is not statically
   *   checked against the actual field type; a mismatch will surface as a
   *   runtime error during codec use, not at derivation time.
   * @param typeId
   *   identifier for the parent type `A`
   * @param termName
   *   the name of the field (or variant case) to override. If no field with
   *   this name exists in the target type, the override is silently ignored
   *   during derivation (matching `DerivationBuilder.instance` behaviour).
   * @param instance
   *   the custom type-class instance for the field (evaluated lazily)
   * @return
   *   a new `Deriver` that wraps this deriver with the additional override
   */
  final def withInstance[A, B](typeId: TypeId[A], termName: String, instance: => TC[B]): Deriver[TC] =
    new DeriverWithOverrides[TC](
      self,
      Chunk(new InstanceOverrideByTypeAndTermName[TC, A, B](typeId, termName, Lazy(instance))),
      Chunk.empty
    )

  /**
   * Returns a new deriver that pre-registers a reflect-level modifier override.
   * During derivation, every occurrence of the type identified by `typeId` will
   * have the modifier prepended to its modifiers.
   *
   * @tparam A
   *   the type to attach the modifier to
   * @param typeId
   *   identifier for `A`
   * @param modifier
   *   the reflect-level modifier to prepend
   * @return
   *   a new `Deriver` that wraps this deriver with the additional override
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
   *
   * @tparam A
   *   the parent record or variant type that owns the field
   * @param typeId
   *   identifier for `A`
   * @param termName
   *   the name of the field (or variant case) to modify. If no field with this
   *   name exists in the target type, the override is silently ignored.
   * @param modifier
   *   the term-level modifier to apply
   * @return
   *   a new `Deriver` that wraps this deriver with the additional override
   */
  final def withModifier[A](typeId: TypeId[A], termName: String, modifier: Modifier.Term): Deriver[TC] =
    new DeriverWithOverrides[TC](
      self,
      Chunk.empty,
      Chunk(new ModifierTermOverrideByType[A](typeId, termName, modifier))
    )
}
