package zio.blocks.scope.internal

import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.Scope

private[scope] abstract class ScopeImpl[H, T](
  private val parent: Scope[?],
  private[scope] val context: Context[H],
  private val finalizers: Finalizers
) extends Scope.Closeable[H, T] {

  @volatile private var closed: Boolean = false

  private[scope] def getImpl[A](nom: IsNominalType[A]): A =
    context.getOption[A](nom) match {
      case Some(value) => value
      case None        => getFromParent(parent, nom)
    }

  private def getFromParent[A](scope: Scope[?], nom: IsNominalType[A]): A =
    scope match {
      case p: ScopeImpl[?, ?] =>
        p.context.getOption[A](nom) match {
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

  protected def doClose(): Unit = close()
}
