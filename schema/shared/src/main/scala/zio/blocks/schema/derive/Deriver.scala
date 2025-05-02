package zio.blocks.schema.derive

import zio.blocks.schema._
import zio.blocks.schema.binding.{Binding, HasBinding}

trait Deriver[TC[_]] { self =>
  type HasInstance[F[_, _]] = zio.blocks.schema.derive.HasInstance[F, TC]

  final def binding[F[_, _], T, A](fa: F[T, A])(implicit F: HasBinding[F]): Binding[T, A] = F.binding(fa)

  final def instance[F[_, _], T, A](fa: F[T, A])(implicit D: HasInstance[F]): Lazy[TC[A]] = D.instance(fa)

  def derivePrimitive[F[_, _], A](
    primitiveType: PrimitiveType[A],
    typeName: TypeName[A],
    doc: Doc,
    modifiers: Seq[Modifier.Primitive]
  ): Lazy[TC[A]]

  def deriveRecord[F[_, _], A](
    fields: Seq[Term[F, A, ?]],
    typeName: TypeName[A],
    doc: Doc,
    modifiers: Seq[Modifier.Record]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[A]]

  def deriveVariant[F[_, _], A](
    cases: Seq[Term[F, A, ?]],
    typeName: TypeName[A],
    doc: Doc,
    modifiers: Seq[Modifier.Variant]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[A]]

  def deriveSequence[F[_, _], C[_], A](
    element: Reflect[F, A],
    typeName: TypeName[C[A]],
    doc: Doc,
    modifiers: Seq[Modifier.Seq]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[C[A]]]

  def deriveMap[F[_, _], M[_, _], K, V](
    key: Reflect[F, K],
    value: Reflect[F, V],
    typeName: TypeName[M[K, V]],
    doc: Doc,
    modifiers: Seq[Modifier.Map]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[M[K, V]]]

  def deriveDynamic[F[_, _]](
    doc: Doc,
    modifiers: Seq[Modifier.Dynamic]
  )(implicit F: HasBinding[F], D: HasInstance[F]): Lazy[TC[DynamicValue]]
}
