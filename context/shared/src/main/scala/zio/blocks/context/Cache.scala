package zio.blocks.context

import zio.blocks.typeid.TypeId

private[context] trait Cache {
  def get(key: TypeId.Erased): Any
  def put(key: TypeId.Erased, value: Any): Unit
  def putIfAbsent(key: TypeId.Erased, value: Any): Any
}
