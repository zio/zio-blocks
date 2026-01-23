package zio.blocks.typeid

sealed trait Kind {
  def arity: Int
  def isProperType: Boolean   = this == Kind.Type
  def isHigherKinded: Boolean = !isProperType
}

object Kind {
  case object Type extends Kind {
    def arity: Int = 0
  }

  final case class Arrow(params: List[Kind], result: Kind) extends Kind {
    def arity: Int = params.size
  }

  val `* -> *`: Kind        = Arrow(List(Type), Type)
  val `* -> * -> *`: Kind   = Arrow(List(Type, Type), Type)
  val `(* -> *) -> *`: Kind = Arrow(List(`* -> *`), Type)

  def arity(n: Int): Kind =
    if (n == 0) Type
    else Arrow(List.fill(n)(Type), Type)
}
