package zio.blocks.scope.internal

import zio.blocks.context.Context
import zio.blocks.scope.Scope

private[scope] final class ScopeImplScala3[H, T](
  parent: Scope[?],
  context: Context[H],
  finalizers: Finalizers
) extends ScopeImpl[H, T](parent, context, finalizers) {

  def run[B](f: Scope.Has[H] ?=> B): B =
    try f(using this)
    finally doClose()
}
