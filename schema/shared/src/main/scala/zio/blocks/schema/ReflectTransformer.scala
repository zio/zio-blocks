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
import zio.blocks.schema.binding._
import zio.blocks.typeid.TypeId

/**
 * Rebuilds a [[Reflect]] tree with `G` in place of `F` at every node, through
 * `Reflect#transform`. That method walks the tree and transforms the children
 * of a node first; a transformer says how to rebuild each kind of node from its
 * transformed children and the `F` it held. [[Reflect.Deferred]] nodes have no
 * method of their own: `Reflect.Deferred#transform` handles them.
 *
 * `ReflectTransformer.noBinding` strips the bindings of a reflect,
 * `DynamicSchema#rebind` attaches them back, and type class derivation pairs
 * each binding with a derived instance. Extend
 * [[ReflectTransformer.OnlyMetadata]] when only the metadata changes.
 *
 * @tparam F
 *   what the nodes of the source reflect hold
 * @tparam G
 *   what the nodes of the result hold
 *
 * @see
 *   [[https://zioblocks.com/reference/schema/reflect-transformer/ ReflectTransformer]]
 */
trait ReflectTransformer[-F[_, _], G[_, _]] {

  /**
   * Rebuilds a record node from its transformed `fields`. `path` is the
   * location of the node and `metadata` what it holds in the source reflect.
   * The other parameters are its type ID, documentation, modifiers, default
   * value and examples, which the new node usually keeps.
   */
  def transformRecord[A](
    path: DynamicOptic,
    fields: IndexedSeq[Term[G, A, ?]],
    typeId: TypeId[A],
    metadata: F[BindingType.Record, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    storedDefaultValue: Option[DynamicValue],
    storedExamples: collection.immutable.Seq[DynamicValue]
  ): Lazy[Reflect.Record[G, A]]

  /**
   * Rebuilds a variant node from its transformed `cases`. `path` is the
   * location of the node and `metadata` what it holds in the source reflect.
   * The other parameters are its type ID, documentation, modifiers, default
   * value and examples, which the new node usually keeps.
   */
  def transformVariant[A](
    path: DynamicOptic,
    cases: IndexedSeq[Term[G, A, ? <: A]],
    typeId: TypeId[A],
    metadata: F[BindingType.Variant, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    storedDefaultValue: Option[DynamicValue],
    storedExamples: collection.immutable.Seq[DynamicValue]
  ): Lazy[Reflect.Variant[G, A]]

  /**
   * Rebuilds a sequence node from its transformed `element` node. `path` is the
   * location of the node and `metadata` what it holds in the source reflect.
   * The other parameters are its type ID, documentation, modifiers, default
   * value and examples, which the new node usually keeps.
   */
  def transformSequence[A, C[_]](
    path: DynamicOptic,
    element: Reflect[G, A],
    typeId: TypeId[C[A]],
    metadata: F[BindingType.Seq[C], C[A]],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    storedDefaultValue: Option[DynamicValue],
    storedExamples: collection.immutable.Seq[DynamicValue]
  ): Lazy[Reflect.Sequence[G, A, C]]

  /**
   * Rebuilds a map node from its transformed `key` and `value` nodes. `path` is
   * the location of the node and `metadata` what it holds in the source
   * reflect. The other parameters are its type ID, documentation, modifiers,
   * default value and examples, which the new node usually keeps.
   */
  def transformMap[Key, Value, M[_, _]](
    path: DynamicOptic,
    key: Reflect[G, Key],
    value: Reflect[G, Value],
    typeId: TypeId[M[Key, Value]],
    metadata: F[BindingType.Map[M], M[Key, Value]],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    storedDefaultValue: Option[DynamicValue],
    storedExamples: collection.immutable.Seq[DynamicValue]
  ): Lazy[Reflect.Map[G, Key, Value, M]]

  /**
   * Rebuilds a dynamic node. `path` is the location of the node and `metadata`
   * what it holds in the source reflect. The other parameters are its type ID,
   * documentation, modifiers, default value and examples, which the new node
   * usually keeps.
   */
  def transformDynamic(
    path: DynamicOptic,
    typeId: TypeId[DynamicValue],
    metadata: F[BindingType.Dynamic, DynamicValue],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    storedDefaultValue: Option[DynamicValue],
    storedExamples: collection.immutable.Seq[DynamicValue]
  ): Lazy[Reflect.Dynamic[G]]

  /**
   * Rebuilds a primitive node of `primitiveType`. `path` is the location of the
   * node and `metadata` what it holds in the source reflect. The other
   * parameters are its type ID, documentation, modifiers, default value and
   * examples, which the new node usually keeps.
   */
  def transformPrimitive[A](
    path: DynamicOptic,
    primitiveType: PrimitiveType[A],
    typeId: TypeId[A],
    metadata: F[BindingType.Primitive, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    storedDefaultValue: Option[DynamicValue],
    storedExamples: collection.immutable.Seq[DynamicValue]
  ): Lazy[Reflect.Primitive[G, A]]

  /**
   * Rebuilds a wrapper node from its transformed `wrapped` node, which has the
   * same `path`. `path` is the location of the node and `metadata` what it
   * holds in the source reflect. The other parameters are its type ID,
   * documentation, modifiers, default value and examples, which the new node
   * usually keeps.
   */
  def transformWrapper[A, B](
    path: DynamicOptic,
    wrapped: Reflect[G, B],
    typeId: TypeId[A],
    metadata: F[BindingType.Wrapper[A, B], A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    storedDefaultValue: Option[DynamicValue],
    storedExamples: collection.immutable.Seq[DynamicValue]
  ): Lazy[Reflect.Wrapper[G, A, B]]
}

/**
 * The [[ReflectTransformer]] base class for metadata-only changes, and the
 * transformer that strips bindings.
 */
object ReflectTransformer {

  /**
   * A [[ReflectTransformer]] that changes only what each node holds: every
   * method replaces `metadata` with `transformMetadata` of it and keeps the
   * transformed children and everything else as they are. `HasBinding` is one.
   */
  abstract class OnlyMetadata[F[_, _], G[_, _]] extends ReflectTransformer[F, G] {

    /**
     * Returns what a node holds in the result, given what it held in the source
     * reflect.
     */
    def transformMetadata[K, A](f: F[K, A]): Lazy[G[K, A]]

    /** Rebuilds the record node with `transformMetadata(metadata)`. */
    def transformRecord[A](
      path: DynamicOptic,
      fields: IndexedSeq[Term[G, A, ?]],
      typeId: TypeId[A],
      metadata: F[BindingType.Record, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      storedDefaultValue: Option[DynamicValue],
      storedExamples: collection.immutable.Seq[DynamicValue]
    ): Lazy[Reflect.Record[G, A]] =
      for {
        binding <- transformMetadata(metadata)
      } yield new Reflect.Record(fields, typeId, binding, doc, modifiers, storedDefaultValue, storedExamples)

    /** Rebuilds the variant node with `transformMetadata(metadata)`. */
    def transformVariant[A](
      path: DynamicOptic,
      cases: IndexedSeq[Term[G, A, ? <: A]],
      typeId: TypeId[A],
      metadata: F[BindingType.Variant, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      storedDefaultValue: Option[DynamicValue],
      storedExamples: collection.immutable.Seq[DynamicValue]
    ): Lazy[Reflect.Variant[G, A]] =
      for {
        binding <- transformMetadata(metadata)
      } yield new Reflect.Variant(cases, typeId, binding, doc, modifiers, storedDefaultValue, storedExamples)

    /** Rebuilds the sequence node with `transformMetadata(metadata)`. */
    def transformSequence[A, C[_]](
      path: DynamicOptic,
      element: Reflect[G, A],
      typeId: TypeId[C[A]],
      metadata: F[BindingType.Seq[C], C[A]],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      storedDefaultValue: Option[DynamicValue],
      storedExamples: collection.immutable.Seq[DynamicValue]
    ): Lazy[Reflect.Sequence[G, A, C]] =
      for {
        binding <- transformMetadata(metadata)
      } yield new Reflect.Sequence(element, typeId, binding, doc, modifiers, storedDefaultValue, storedExamples)

    /** Rebuilds the map node with `transformMetadata(metadata)`. */
    def transformMap[Key, Value, M[_, _]](
      path: DynamicOptic,
      key: Reflect[G, Key],
      value: Reflect[G, Value],
      typeId: TypeId[M[Key, Value]],
      metadata: F[BindingType.Map[M], M[Key, Value]],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      storedDefaultValue: Option[DynamicValue],
      storedExamples: collection.immutable.Seq[DynamicValue]
    ): Lazy[Reflect.Map[G, Key, Value, M]] =
      for {
        binding <- transformMetadata(metadata)
      } yield Reflect.Map(key, value, typeId, binding, doc, modifiers, storedDefaultValue, storedExamples)

    /** Rebuilds the dynamic node with `transformMetadata(metadata)`. */
    def transformDynamic(
      path: DynamicOptic,
      typeId: TypeId[DynamicValue],
      metadata: F[BindingType.Dynamic, DynamicValue],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      storedDefaultValue: Option[DynamicValue],
      storedExamples: collection.immutable.Seq[DynamicValue]
    ): Lazy[Reflect.Dynamic[G]] =
      for {
        binding <- transformMetadata(metadata)
      } yield new Reflect.Dynamic(binding, typeId, doc, modifiers, storedDefaultValue, storedExamples)

    /** Rebuilds the primitive node with `transformMetadata(metadata)`. */
    def transformPrimitive[A](
      path: DynamicOptic,
      primitiveType: PrimitiveType[A],
      typeId: TypeId[A],
      metadata: F[BindingType.Primitive, A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      storedDefaultValue: Option[DynamicValue],
      storedExamples: collection.immutable.Seq[DynamicValue]
    ): Lazy[Reflect.Primitive[G, A]] =
      for {
        binding <- transformMetadata(metadata)
      } yield new Reflect.Primitive(
        primitiveType,
        typeId,
        binding,
        doc,
        modifiers,
        storedDefaultValue,
        storedExamples
      )

    /** Rebuilds the wrapper node with `transformMetadata(metadata)`. */
    def transformWrapper[A, B](
      path: DynamicOptic,
      wrapped: Reflect[G, B],
      typeId: TypeId[A],
      metadata: F[BindingType.Wrapper[A, B], A],
      doc: Doc,
      modifiers: Seq[Modifier.Reflect],
      storedDefaultValue: Option[DynamicValue],
      storedExamples: collection.immutable.Seq[DynamicValue]
    ): Lazy[Reflect.Wrapper[G, A, B]] =
      for {
        binding <- transformMetadata(metadata)
      } yield new Reflect.Wrapper(
        wrapped,
        typeId,
        binding,
        doc,
        modifiers,
        storedDefaultValue,
        storedExamples
      )
  }

  private type Any2[_, _] = Any

  private[this] val _noBinding = new OnlyMetadata[Any2, NoBinding] {
    private[this] val nb = Lazy(NoBinding[Any, Any]())

    def transformMetadata[K, A](f: Any): Lazy[NoBinding[K, A]] = nb.asInstanceOf[Lazy[NoBinding[K, A]]]
  }

  /**
   * Returns the transformer that replaces what every node holds with
   * `NoBinding`, keeping everything else. `Reflect#noBinding` uses it. The same
   * instance is returned on every call.
   */
  def noBinding[F[_, _]](): ReflectTransformer[F, NoBinding] = _noBinding.asInstanceOf[ReflectTransformer[F, NoBinding]]
}
