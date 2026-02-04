package zio.blocks.scope

trait Wireable[+Out] {
  type In

  def wire: Wire[In, Out]
}

object Wireable extends WireableVersionSpecific {

  type Typed[-In0, +Out] = Wireable[Out] { type In >: In0 }
}
