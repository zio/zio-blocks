package zio.blocks.scope

import zio.blocks.context.Context

private[scope] trait InStackVersionSpecific {
  private val singleton: InStack[Any, Any] = new InStack[Any, Any] {}

  implicit def head[T, Tail]: InStack[T, Context[T] :: Tail] =
    singleton.asInstanceOf[InStack[T, Context[T] :: Tail]]

  implicit def tail[T, H, Tail](implicit ev: InStack[T, Tail]): InStack[T, H :: Tail] =
    singleton.asInstanceOf[InStack[T, H :: Tail]]
}
