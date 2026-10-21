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
 * A [[ReflectTransformer]] that turns an unbound reflect into a bound one,
 * looking up the binding of each node by its type ID in `resolver`.
 * `DynamicSchema#rebind` runs it. Each method throws a [[RebindException]] when
 * the resolver has no binding of the right kind for the node's type.
 */
private[schema] final class RebindTransformer(resolver: BindingResolver)
    extends ReflectTransformer[NoBinding, Binding] {

  def transformRecord[A](
    path: DynamicOptic,
    fields: IndexedSeq[Term[Binding, A, ?]],
    typeId: TypeId[A],
    metadata: NoBinding[BindingType.Record, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    storedDefaultValue: Option[DynamicValue],
    storedExamples: collection.immutable.Seq[DynamicValue]
  ): Lazy[Reflect.Record[Binding, A]] = Lazy {
    val binding = resolver
      .resolveRecord[A](typeId)
      .getOrElse(
        throw new RebindException(path, typeId, "Record")
      )
    new Reflect.Record(
      fields,
      typeId,
      binding.asInstanceOf[Binding.Record[A]],
      doc,
      modifiers,
      storedDefaultValue,
      storedExamples
    )
  }

  def transformVariant[A](
    path: DynamicOptic,
    cases: IndexedSeq[Term[Binding, A, ? <: A]],
    typeId: TypeId[A],
    metadata: NoBinding[BindingType.Variant, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    storedDefaultValue: Option[DynamicValue],
    storedExamples: collection.immutable.Seq[DynamicValue]
  ): Lazy[Reflect.Variant[Binding, A]] = Lazy {
    val binding = resolver
      .resolveVariant[A](typeId)
      .getOrElse(
        throw new RebindException(path, typeId, "Variant")
      )
    new Reflect.Variant(
      cases,
      typeId,
      binding.asInstanceOf[Binding.Variant[A]],
      doc,
      modifiers,
      storedDefaultValue,
      storedExamples
    )
  }

  def transformSequence[A, C[_]](
    path: DynamicOptic,
    element: Reflect[Binding, A],
    typeId: TypeId[C[A]],
    metadata: NoBinding[BindingType.Seq[C], C[A]],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    storedDefaultValue: Option[DynamicValue],
    storedExamples: collection.immutable.Seq[DynamicValue]
  ): Lazy[Reflect.Sequence[Binding, A, C]] = Lazy {
    val binding = resolver
      .resolveSeqFor[C, A](typeId)
      .getOrElse(
        throw new RebindException(path, typeId, "Sequence")
      )
    new Reflect.Sequence(
      element,
      typeId,
      binding,
      doc,
      modifiers,
      storedDefaultValue,
      storedExamples
    )
  }

  def transformMap[Key, Value, M[_, _]](
    path: DynamicOptic,
    key: Reflect[Binding, Key],
    value: Reflect[Binding, Value],
    typeId: TypeId[M[Key, Value]],
    metadata: NoBinding[BindingType.Map[M], M[Key, Value]],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    storedDefaultValue: Option[DynamicValue],
    storedExamples: collection.immutable.Seq[DynamicValue]
  ): Lazy[Reflect.Map[Binding, Key, Value, M]] = Lazy {
    val binding = resolver
      .resolveMapFor[M, Key, Value](typeId)
      .getOrElse(
        throw new RebindException(path, typeId, "Map")
      )
    Reflect.Map(
      key,
      value,
      typeId,
      binding,
      doc,
      modifiers,
      storedDefaultValue,
      storedExamples
    )
  }

  def transformDynamic(
    path: DynamicOptic,
    typeId: TypeId[DynamicValue],
    metadata: NoBinding[BindingType.Dynamic, DynamicValue],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    storedDefaultValue: Option[DynamicValue],
    storedExamples: collection.immutable.Seq[DynamicValue]
  ): Lazy[Reflect.Dynamic[Binding]] = Lazy {
    val binding = resolver
      .resolveDynamic(typeId)
      .getOrElse(
        throw new RebindException(path, typeId, "Dynamic")
      )
    new Reflect.Dynamic(
      binding,
      typeId,
      doc,
      modifiers,
      storedDefaultValue,
      storedExamples
    )
  }

  def transformPrimitive[A](
    path: DynamicOptic,
    primitiveType: PrimitiveType[A],
    typeId: TypeId[A],
    metadata: NoBinding[BindingType.Primitive, A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    storedDefaultValue: Option[DynamicValue],
    storedExamples: collection.immutable.Seq[DynamicValue]
  ): Lazy[Reflect.Primitive[Binding, A]] = Lazy {
    val binding = resolver
      .resolvePrimitive[A](typeId)
      .getOrElse(
        throw new RebindException(path, typeId, "Primitive")
      )
    new Reflect.Primitive(
      primitiveType,
      typeId,
      binding.asInstanceOf[Binding.Primitive[A]],
      doc,
      modifiers,
      storedDefaultValue,
      storedExamples
    )
  }

  def transformWrapper[A, B](
    path: DynamicOptic,
    wrapped: Reflect[Binding, B],
    typeId: TypeId[A],
    metadata: NoBinding[BindingType.Wrapper[A, B], A],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    storedDefaultValue: Option[DynamicValue],
    storedExamples: collection.immutable.Seq[DynamicValue]
  ): Lazy[Reflect.Wrapper[Binding, A, B]] = Lazy {
    val binding = resolver
      .resolveWrapper[A](typeId)
      .getOrElse(
        throw new RebindException(path, typeId, "Wrapper")
      )
    new Reflect.Wrapper(
      wrapped,
      typeId,
      binding.asInstanceOf[Binding.Wrapper[A, B]],
      doc,
      modifiers,
      storedDefaultValue,
      storedExamples
    )
  }
}

/**
 * Thrown by `DynamicSchema#rebind` when its `BindingResolver` has no binding
 * for a type in the schema. The message names the kind of binding, the full
 * name of the type and the path.
 *
 * @param path
 *   the location of the node that needs the binding (a wrapped node has the
 *   path of its wrapper)
 * @param typeId
 *   the type ID that has no binding
 * @param expectedKind
 *   the kind of binding that was looked up: `Record`, `Variant`, `Sequence`,
 *   `Map`, `Dynamic`, `Primitive` or `Wrapper`
 */
final class RebindException(
  val path: DynamicOptic,
  val typeId: TypeId[_],
  val expectedKind: String
) extends RuntimeException(
      s"Missing $expectedKind binding for type ${typeId.fullName} at path: $path"
    )
