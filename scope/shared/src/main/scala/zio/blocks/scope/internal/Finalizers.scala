package zio.blocks.scope.internal

import scala.collection.mutable.ArrayBuffer

private[scope] final class Finalizers {
  private val stack: ArrayBuffer[() => Unit] = ArrayBuffer.empty
  @volatile private var closed: Boolean      = false

  def add(finalizer: => Unit): Unit =
    synchronized {
      if (!closed) {
        stack += (() => finalizer)
      }
    }

  def runAll(): List[Throwable] = synchronized {
    if (closed) return Nil
    closed = true

    val errors = ArrayBuffer.empty[Throwable]
    var i      = stack.length - 1
    while (i >= 0) {
      try stack(i)()
      catch { case t: Throwable => errors += t }
      i -= 1
    }
    stack.clear()
    errors.toList
  }

  def isClosed: Boolean = closed

  def size: Int = synchronized(stack.length)
}
