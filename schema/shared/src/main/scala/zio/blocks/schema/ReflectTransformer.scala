package zio.blocks.schema

import zio.blocks.schema.binding._

trait ReflectTransformer[-F[_, _], G[_, _]] {
  def transformRecord[A](
    path: DynamicOptic,
    fields: IndexedSeq[Term[G, A, ?]],
    typeName: TypeName[A],
    metadata: F[BindingType.Record, A],
    doc: Doc,
    modifiers: Seq[Modifier.Record]
  ): Lazy[Reflect.Record[G, A]]

  def transformVariant[A](
    path: DynamicOptic,
    cases: IndexedSeq[Term[G, A, ? <: A]],
    typeName: TypeName[A],
    metadata: F[BindingType.Variant, A],
    doc: Doc,
    modifiers: Seq[Modifier.Variant]
  ): Lazy[Reflect.Variant[G, A]]

  def transformSequence[A, C[_]](
    path: DynamicOptic,
    element: Reflect[G, A],
    typeName: TypeName[C[A]],
    metadata: F[BindingType.Seq[C], C[A]],
    doc: Doc,
    modifiers: Seq[Modifier.Seq]
  ): Lazy[Reflect.Sequence[G, A, C]]

  def transformMap[Key, Value, M[_, _]](
    path: DynamicOptic,
    key: Reflect[G, Key],
    value: Reflect[G, Value],
    typeName: TypeName[M[Key, Value]],
    metadata: F[BindingType.Map[M], M[Key, Value]],
    doc: Doc,
    modifiers: Seq[Modifier.Map]
  ): Lazy[Reflect.Map[G, Key, Value, M]]

  def transformDynamic(
    path: DynamicOptic,
    metadata: F[BindingType.Dynamic, DynamicValue],
    doc: Doc,
    modifiers: Seq[Modifier.Dynamic]
  ): Lazy[Reflect.Dynamic[G]]

  def transformPrimitive[A](
    path: DynamicOptic,
    primitiveType: PrimitiveType[A],
    typeName: TypeName[A],
    metadata: F[BindingType.Primitive, A],
    doc: Doc,
    modifiers: Seq[Modifier.Primitive]
  ): Lazy[Reflect.Primitive[G, A]]
}

object ReflectTransformer {
  abstract class OnlyMetadata[F[_, _], G[_, _]] extends ReflectTransformer[F, G] {
    def transformMetadata[K, A](f: F[K, A]): Lazy[G[K, A]]

    def transformRecord[A](
      path: DynamicOptic,
      fields: IndexedSeq[Term[G, A, ?]],
      typeName: TypeName[A],
      metadata: F[BindingType.Record, A],
      doc: Doc,
      modifiers: Seq[Modifier.Record]
    ): Lazy[Reflect.Record[G, A]] =
      for {
        binding <- transformMetadata(metadata)
      } yield Reflect.Record(fields, typeName, binding, doc, modifiers)

    def transformVariant[A](
      path: DynamicOptic,
      cases: IndexedSeq[Term[G, A, ? <: A]],
      typeName: TypeName[A],
      metadata: F[BindingType.Variant, A],
      doc: Doc,
      modifiers: Seq[Modifier.Variant]
    ): Lazy[Reflect.Variant[G, A]] =
      for {
        binding <- transformMetadata(metadata)
      } yield Reflect.Variant(cases, typeName, binding, doc, modifiers)

    def transformSequence[A, C[_]](
      path: DynamicOptic,
      element: Reflect[G, A],
      typeName: TypeName[C[A]],
      metadata: F[BindingType.Seq[C], C[A]],
      doc: Doc,
      modifiers: Seq[Modifier.Seq]
    ): Lazy[Reflect.Sequence[G, A, C]] =
      for {
        binding <- transformMetadata(metadata)
      } yield Reflect.Sequence(element, typeName, binding, doc, modifiers)

    def transformMap[Key, Value, M[_, _]](
      path: DynamicOptic,
      key: Reflect[G, Key],
      value: Reflect[G, Value],
      typeName: TypeName[M[Key, Value]],
      metadata: F[BindingType.Map[M], M[Key, Value]],
      doc: Doc,
      modifiers: Seq[Modifier.Map]
    ): Lazy[Reflect.Map[G, Key, Value, M]] =
      for {
        binding <- transformMetadata(metadata)
      } yield Reflect.Map(key, value, typeName, binding, doc, modifiers)

    def transformDynamic(
      path: DynamicOptic,
      metadata: F[BindingType.Dynamic, DynamicValue],
      doc: Doc,
      modifiers: Seq[Modifier.Dynamic]
    ): Lazy[Reflect.Dynamic[G]] =
      for {
        binding <- transformMetadata(metadata)
      } yield Reflect.Dynamic(binding, doc, modifiers)

    def transformPrimitive[A](
      path: DynamicOptic,
      primitiveType: PrimitiveType[A],
      typeName: TypeName[A],
      metadata: F[BindingType.Primitive, A],
      doc: Doc,
      modifiers: Seq[Modifier.Primitive]
    ): Lazy[Reflect.Primitive[G, A]] =
      for {
        binding <- transformMetadata(metadata)
      } yield Reflect.Primitive(primitiveType, typeName, binding, doc, modifiers)
  }

  private type Any2[_, _] = Any

  private[this] val _noBinding = new OnlyMetadata[Any2, NoBinding] {
    private[this] val nb = Lazy(NoBinding[Any, Any]())

    def transformMetadata[K, A](f: Any): Lazy[NoBinding[K, A]] = nb.asInstanceOf[Lazy[NoBinding[K, A]]]
  }

  def noBinding[F[_, _]](): ReflectTransformer[F, NoBinding] = _noBinding.asInstanceOf[ReflectTransformer[F, NoBinding]]
}
