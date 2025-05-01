package zio.blocks.schema

import zio.blocks.schema.binding._

trait ReflectTransformer[-F[_, _], G[_, _]] {
  def apply[K, A](f: F[K, A]): Lazy[G[K, A]]

  // This function takes all the fields of a Reflect.Record and transforms them into a new record with the binding changed to G[BindingType.Record, A]:
  def transformRecord[A](
    fields: Seq[Term[G, A, ?]],
    typeName: TypeName[A],
    recordBinding: F[BindingType.Record, A],
    doc: Doc = Doc.Empty,
    modifiers: Seq[Modifier.Record] = Vector()
  ): Lazy[Reflect.Record[G, A]] =
    for {
      binding <- apply(recordBinding)
    } yield Reflect.Record(fields, typeName, binding, doc, modifiers)

  // This function takes all the cases of a Reflect.Variant and transforms them into a new variant with the binding changed to G[BindingType.Variant, A]:
  def transformVariant[A](
    cases: Seq[Term[G, A, ? <: A]],
    typeName: TypeName[A],
    variantBinding: F[BindingType.Variant, A],
    doc: Doc = Doc.Empty,
    modifiers: Seq[Modifier.Variant] = Vector()
  ): Lazy[Reflect.Variant[G, A]] =
    for {
      binding <- apply(variantBinding)
    } yield Reflect.Variant(cases, typeName, binding, doc, modifiers)

  // This function takes a sequence element and transforms it into a new sequence with the binding changed to G[BindingType.Seq[C], C[A]]:
  def transformSequence[A, C[_]](
    element: Reflect[G, A],
    typeName: TypeName[C[A]],
    seqBinding: F[BindingType.Seq[C], C[A]],
    doc: Doc = Doc.Empty,
    modifiers: Seq[Modifier.Seq] = Vector()
  ): Lazy[Reflect.Sequence[G, A, C]] =
    for {
      binding <- apply(seqBinding)
    } yield Reflect.Sequence(element, binding, typeName, doc, modifiers)

  // This function takes map key and value elements and transforms them into a new map with the binding changed to G[BindingType.Map[M], M[Key, Value]]:
  def transformMap[Key, Value, M[_, _]](
    key: Reflect[G, Key],
    value: Reflect[G, Value],
    typeName: TypeName[M[Key, Value]],
    mapBinding: F[BindingType.Map[M], M[Key, Value]],
    doc: Doc = Doc.Empty,
    modifiers: Seq[Modifier.Map] = Vector()
  ): Lazy[Reflect.Map[G, Key, Value, M]] =
    for {
      binding <- apply(mapBinding)
    } yield Reflect.Map(key, value, binding, typeName, doc, modifiers)

  // This function transforms a dynamic value with the binding changed to G[BindingType.Dynamic, DynamicValue]:
  def transformDynamic(
    dynamicBinding: F[BindingType.Dynamic, DynamicValue],
    doc: Doc = Doc.Empty,
    modifiers: Seq[Modifier.Dynamic] = Vector()
  ): Lazy[Reflect.Dynamic[G]] =
    for {
      binding <- apply(dynamicBinding)
    } yield Reflect.Dynamic(binding, doc, modifiers)

  // This function transforms a primitive value with the binding changed to G[BindingType.Primitive, A]:
  def transformPrimitive[A](
    primitiveType: PrimitiveType[A],
    typeName: TypeName[A],
    primitiveBinding: F[BindingType.Primitive, A],
    doc: Doc = Doc.Empty,
    modifiers: Seq[Modifier.Primitive] = Vector()
  ): Lazy[Reflect.Primitive[G, A]] =
    for {
      binding <- apply(primitiveBinding)
    } yield Reflect.Primitive(primitiveType, binding, typeName, doc, modifiers)

  // This function transforms a deferred value with the binding changed to G:
  def transformDeferred[A](
    value: () => Reflect[G, A]
  ): Lazy[Reflect.Deferred[G, A]] =
    Lazy(Reflect.Deferred(value))
}

object ReflectTransformer {
  private type Any2[A, B] = Any

  private[this] val _noBinding: ReflectTransformer[Any2, NoBinding] = new ReflectTransformer[Any2, NoBinding] {
    private val nb                                 = NoBinding[Any, Any]()
    private val result                             = Lazy(nb)
    def apply[K, A](f: Any): Lazy[NoBinding[K, A]] = result.asInstanceOf[Lazy[NoBinding[K, A]]]
  }

  def noBinding[F[_, _]](): ReflectTransformer[F, NoBinding] = _noBinding.asInstanceOf[ReflectTransformer[F, NoBinding]]
}
