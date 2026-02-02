package zio.blocks.schema

import zio.blocks.schema.binding._
import zio.blocks.typeid.TypeId

/**
 * A [[ReflectTransformer]] that rebinds an unbound reflect structure using
 * bindings from a [[BindingResolver]].
 *
 * This transformer is used internally by [[Schema.rebind]] to convert
 * `Reflect.Unbound[A]` to `Reflect.Bound[A]` by looking up the appropriate
 * bindings for each type in the reflect structure.
 *
 * @param resolver
 *   The BindingResolver providing bindings for all types in the structure
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
 * Exception thrown when rebinding fails due to a missing binding in the
 * registry.
 *
 * @param path
 *   The path in the schema structure where the binding was needed
 * @param typeId
 *   The TypeId of the type that was missing
 * @param expectedKind
 *   The kind of binding that was expected (Record, Variant, etc.)
 */
final class RebindException(
  val path: DynamicOptic,
  val typeId: TypeId[_],
  val expectedKind: String
) extends RuntimeException(
      s"Missing $expectedKind binding for type ${typeId.fullName} at path: $path"
    )
