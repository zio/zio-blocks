package zio.blocks.scope

private[scope] trait InStackVersionSpecific {
  private val singleton: InStack[Any, Any] = new InStack[Any, Any] {}

  // Head match: T is at the head of the scope
  implicit def head[T, R <: T, Tail <: Scope]: InStack[T, Scope.::[R, Tail]] =
    singleton.asInstanceOf[InStack[T, Scope.::[R, Tail]]]

  // Tail match: T is somewhere in the tail
  implicit def tail[T, H, Tail <: Scope](implicit ev: InStack[T, Tail]): InStack[T, Scope.::[H, Tail]] =
    singleton.asInstanceOf[InStack[T, Scope.::[H, Tail]]]
}
