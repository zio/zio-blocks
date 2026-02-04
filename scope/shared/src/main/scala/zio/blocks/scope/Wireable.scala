package zio.blocks.scope

trait Wireable[T] {
  def wire: Wire[?, T]
}

object Wireable extends WireableVersionSpecific
