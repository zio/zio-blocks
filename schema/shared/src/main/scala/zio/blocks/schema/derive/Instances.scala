package zio.blocks.schema.derive

import zio.blocks.schema._

final case class Instances[TC[_], A](automatic: Lazy[TC[A]], custom: Option[Lazy[TC[A]]]) {
  def derivation: Lazy[TC[A]] = custom.getOrElse(automatic)
}

object Instances {}
