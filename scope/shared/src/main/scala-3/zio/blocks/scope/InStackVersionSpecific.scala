package zio.blocks.scope

import zio.blocks.context.Context

private[scope] trait InStackVersionSpecific {
  private val singleton: InStack[Any, Any] = new InStack[Any, Any] {}

  given head[T, R <: T, Tail]: InStack[T, Context[R] :: Tail] =
    singleton.asInstanceOf[InStack[T, Context[R] :: Tail]]

  given tail[T, H, Tail](using ev: InStack[T, Tail]): InStack[T, H :: Tail] =
    singleton.asInstanceOf[InStack[T, H :: Tail]]
}
