package zio.blocks.schema

import zio.blocks.schema.binding._
import zio.blocks.typeid.TypeId

trait ReflectTransformer[-F[_, _], G[_, _]] {
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

  def transformDynamic(
    path: DynamicOptic,
    typeId: TypeId[DynamicValue],
    metadata: F[BindingType.Dynamic, DynamicValue],
    doc: Doc,
    modifiers: Seq[Modifier.Reflect],
    storedDefaultValue: Option[DynamicValue],
    storedExamples: collection.immutable.Seq[DynamicValue]
  ): Lazy[Reflect.Dynamic[G]]

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

object ReflectTransformer {
  abstract class OnlyMetadata[F[_, _], G[_, _]] extends ReflectTransformer[F, G] {
    def transformMetadata[K, A](f: F[K, A]): Lazy[G[K, A]]

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

  def noBinding[F[_, _]](): ReflectTransformer[F, NoBinding] = _noBinding.asInstanceOf[ReflectTransformer[F, NoBinding]]
}
