package zio.blocks.scope.internal

import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.{::, Scope}

private[scope] final class ScopeImpl[S, C](
  private val parent: Scope[?],
  private val context: Context[C],
  private val finalizers: Finalizers
) extends Scope.Closeable[Context[C] :: S] {
  override type CurrentLayer = C

  @volatile private var closed: Boolean = false

  private[scope] def getImpl[T](nom: IsNominalType[T]): T =
    context.getOption[T](nom) match {
      case Some(value) => value
      case None        => getFromParent(parent, nom)
    }

  private def getFromParent[T](scope: Scope[?], nom: IsNominalType[T]): T =
    scope match {
      case p: ScopeImpl[?, ?] =>
        p.context.getOption[T](nom) match {
          case Some(value) => value
          case None        => getFromParent(p.parent, nom)
        }
      case _ =>
        throw new NoSuchElementException(s"Service ${nom.typeId.fullName} not found in scope")
    }

  def defer(finalizer: => Unit): Unit =
    if (!closed) finalizers.add(finalizer)

  def close(): Unit = synchronized {
    if (!closed) {
      closed = true
      val errors = finalizers.runAll()
      errors.headOption.foreach(throw _)
    }
  }

  def run[B](f: Context[C] => B): B =
    try f(context)
    finally close()
}
