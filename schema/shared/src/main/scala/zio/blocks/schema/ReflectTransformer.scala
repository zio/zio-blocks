package zio.blocks.schema

import zio.blocks.schema.binding._

// TODO: Pass DynamicOptic into each transform method
trait ReflectTransformer[-F[_, _], G[_, _]] {
  def transformRecord[A](
    fields: Seq[Term[G, A, ?]],
    typeName: TypeName[A],
    metadata: F[BindingType.Record, A],
    doc: Doc,
    modifiers: Seq[Modifier.Record]
  ): Lazy[Reflect.Record[G, A]]

  def transformVariant[A](
    cases: Seq[Term[G, A, ? <: A]],
    typeName: TypeName[A],
    metadata: F[BindingType.Variant, A],
    doc: Doc,
    modifiers: Seq[Modifier.Variant]
  ): Lazy[Reflect.Variant[G, A]]

  def transformSequence[A, C[_]](
    element: Reflect[G, A],
    typeName: TypeName[C[A]],
    metadata: F[BindingType.Seq[C], C[A]],
    doc: Doc,
    modifiers: Seq[Modifier.Seq]
  ): Lazy[Reflect.Sequence[G, A, C]]

  def transformMap[Key, Value, M[_, _]](
    key: Reflect[G, Key],
    value: Reflect[G, Value],
    typeName: TypeName[M[Key, Value]],
    metadata: F[BindingType.Map[M], M[Key, Value]],
    doc: Doc,
    modifiers: Seq[Modifier.Map]
  ): Lazy[Reflect.Map[G, Key, Value, M]]

  def transformDynamic(
    metadata: F[BindingType.Dynamic, DynamicValue],
    doc: Doc,
    modifiers: Seq[Modifier.Dynamic]
  ): Lazy[Reflect.Dynamic[G]]

  def transformPrimitive[A](
    primitiveType: PrimitiveType[A],
    typeName: TypeName[A],
    metadata: F[BindingType.Primitive, A],
    doc: Doc,
    modifiers: Seq[Modifier.Primitive]
  ): Lazy[Reflect.Primitive[G, A]]

  def transformDeferred[A](
    value: () => Reflect[G, A]
  ): Lazy[Reflect.Deferred[G, A]]
}

object ReflectTransformer {
  abstract class OnlyMetadata[F[_, _], G[_, _]] extends ReflectTransformer[F, G] {
    def transformMetadata[K, A](f: F[K, A]): Lazy[G[K, A]]

    def transformRecord[A](
      fields: Seq[Term[G, A, ?]],
      typeName: TypeName[A],
      metadata: F[BindingType.Record, A],
      doc: Doc,
      modifiers: Seq[Modifier.Record]
    ): Lazy[Reflect.Record[G, A]] =
      for {
        binding <- transformMetadata(metadata)
      } yield Reflect.Record(fields, typeName, binding, doc, modifiers)

    def transformVariant[A](
      cases: Seq[Term[G, A, ? <: A]],
      typeName: TypeName[A],
      metadata: F[BindingType.Variant, A],
      doc: Doc,
      modifiers: Seq[Modifier.Variant]
    ): Lazy[Reflect.Variant[G, A]] =
      for {
        binding <- transformMetadata(metadata)
      } yield Reflect.Variant(cases, typeName, binding, doc, modifiers)

    def transformSequence[A, C[_]](
      element: Reflect[G, A],
      typeName: TypeName[C[A]],
      metadata: F[BindingType.Seq[C], C[A]],
      doc: Doc,
      modifiers: Seq[Modifier.Seq]
    ): Lazy[Reflect.Sequence[G, A, C]] =
      for {
        binding <- transformMetadata(metadata)
      } yield Reflect.Sequence(element, binding, typeName, doc, modifiers)

    def transformMap[Key, Value, M[_, _]](
      key: Reflect[G, Key],
      value: Reflect[G, Value],
      typeName: TypeName[M[Key, Value]],
      metadata: F[BindingType.Map[M], M[Key, Value]],
      doc: Doc,
      modifiers: Seq[Modifier.Map]
    ): Lazy[Reflect.Map[G, Key, Value, M]] =
      for {
        binding <- transformMetadata(metadata)
      } yield Reflect.Map(key, value, binding, typeName, doc, modifiers)

    def transformDynamic(
      metadata: F[BindingType.Dynamic, DynamicValue],
      doc: Doc,
      modifiers: Seq[Modifier.Dynamic]
    ): Lazy[Reflect.Dynamic[G]] =
      for {
        binding <- transformMetadata(metadata)
      } yield Reflect.Dynamic(binding, doc, modifiers)

    def transformPrimitive[A](
      primitiveType: PrimitiveType[A],
      typeName: TypeName[A],
      metadata: F[BindingType.Primitive, A],
      doc: Doc,
      modifiers: Seq[Modifier.Primitive]
    ): Lazy[Reflect.Primitive[G, A]] =
      for {
        binding <- transformMetadata(metadata)
      } yield Reflect.Primitive(primitiveType, binding, typeName, doc, modifiers)

    def transformDeferred[A](
      value: () => Reflect[G, A]
    ): Lazy[Reflect.Deferred[G, A]] =
      Lazy(Reflect.Deferred(value))
  }

  private type Any2[A, B] = Any

  private[this] val _noBinding: ReflectTransformer[Any2, NoBinding] = new OnlyMetadata[Any2, NoBinding] {
    private val nb                                             = NoBinding[Any, Any]()
    private val result                                         = Lazy(nb)
    def transformMetadata[K, A](f: Any): Lazy[NoBinding[K, A]] = result.asInstanceOf[Lazy[NoBinding[K, A]]]
  }

  def noBinding[F[_, _]](): ReflectTransformer[F, NoBinding] = _noBinding.asInstanceOf[ReflectTransformer[F, NoBinding]]
}
