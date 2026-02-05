package zio.blocks.scope.internal

import zio.blocks.chunk.Chunk
import zio.blocks.context.{Context, IsNominalType}
import zio.blocks.scope.Scope

/**
 * Base implementation of a closeable scope.
 *
 * Thread-safety is guaranteed by the Finalizers implementation which uses
 * lock-free atomic operations.
 */
private[scope] abstract class ScopeImpl[H, T](
  private val parent: Scope[?],
  private[scope] val context: Context[H],
  private val finalizers: Finalizers
) extends Scope.Closeable[H, T] {

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
    finalizers.add(finalizer)

  def close(): Chunk[Throwable] = finalizers.runAll()

  protected def doClose(): Chunk[Throwable] = close()
}
