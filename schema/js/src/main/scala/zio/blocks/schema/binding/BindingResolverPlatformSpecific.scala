package zio.blocks.schema.binding

import zio.blocks.schema.DynamicValue
import zio.blocks.typeid.TypeId

private[binding] trait BindingResolverPlatformSpecific {

  val reflection: BindingResolver = NoOpReflection

  private object NoOpReflection extends BindingResolver {

    def resolveRecord[A](implicit typeId: TypeId[A]): Option[Binding.Record[A]] = None

    def resolveVariant[A](implicit typeId: TypeId[A]): Option[Binding.Variant[A]] = None

    def resolvePrimitive[A](implicit typeId: TypeId[A]): Option[Binding.Primitive[A]] = None

    def resolveWrapper[A](implicit typeId: TypeId[A]): Option[Binding.Wrapper[A, _]] = None

    def resolveDynamic(implicit typeId: TypeId[DynamicValue]): Option[Binding.Dynamic] = None

    def resolveSeq[X](implicit typeId: TypeId[X], u: UnapplySeq[X]): Option[Binding.Seq[u.C, u.A]] = None

    def resolveSeqFor[C[_], A](typeId: TypeId[C[A]]): Option[Binding.Seq[C, A]] = None

    def resolveMap[X](implicit typeId: TypeId[X], u: UnapplyMap[X]): Option[Binding.Map[u.M, u.K, u.V]] = None

    def resolveMapFor[M[_, _], K, V](typeId: TypeId[M[K, V]]): Option[Binding.Map[M, K, V]] = None

    override def toString: String = "BindingResolver.Reflection (no-op on JS)"
  }
}
