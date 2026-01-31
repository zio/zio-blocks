package zio.blocks.context

import zio.blocks.typeid.TypeId

trait IsNominalType[A] {
  def typeId: TypeId[A]
  def typeIdErased: TypeId.Erased
}

object IsNominalType extends IsNominalTypeVersionSpecific {
  def apply[A](implicit ev: IsNominalType[A]): IsNominalType[A] = ev
}
