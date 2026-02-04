package zio.blocks.scope.internal

import zio.blocks.context.Context
import zio.blocks.scope.Scope

private[scope] final class ScopeImplScala2[H, T](
  parent: Scope[_],
  context: Context[H],
  finalizers: Finalizers
) extends ScopeImpl[H, T](parent, context, finalizers) {

  def run[B](f: Scope.Has[H] => B): B =
    try f(this)
    finally doClose()
}
