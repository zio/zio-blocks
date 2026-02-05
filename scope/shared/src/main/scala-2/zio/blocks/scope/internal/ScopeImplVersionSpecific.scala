package zio.blocks.scope.internal

import zio.blocks.chunk.Chunk
import zio.blocks.context.Context
import zio.blocks.scope.Scope
import java.util.concurrent.atomic.AtomicBoolean

private[scope] final class ScopeImplScala2[H, T](
  parent: Scope[_],
  context: Context[H],
  finalizers: Finalizers,
  private[internal] val errorReporter: Chunk[Throwable] => Unit = ScopeImplScala2.defaultErrorReporter
) extends ScopeImpl[H, T](parent, context, finalizers) {

  private val runCalled = new AtomicBoolean(false)

  private def ensureFirstRun(): Unit =
    if (!runCalled.compareAndSet(false, true)) {
      throw new IllegalStateException(
        "Scope.run can only be called once. " +
          "If you need to reuse the scope, create a new one with injected[T]."
      )
    }

  def run[B](f: Scope.Has[H] => B): B = {
    ensureFirstRun()
    try {
      f(this)
    } finally {
      val errors = doClose()
      if (errors.nonEmpty) {
        errorReporter(errors)
      }
    }
  }

  def runWithErrors[B](f: Scope.Has[H] => B): (B, Chunk[Throwable]) = {
    ensureFirstRun()
    var result: B = null.asInstanceOf[B]
    val errors    = try {
      result = f(this)
      doClose()
    } catch {
      case t: Throwable =>
        doClose()
        throw t
    }
    (result, errors)
  }
}

private[scope] object ScopeImplScala2 {
  val defaultErrorReporter: Chunk[Throwable] => Unit = { errors =>
    System.err.println(s"[Scope] ${errors.size} uncaught finalizer error(s):")
    errors.foreach { t =>
      System.err.println(s"  - ${t.getClass.getName}: ${t.getMessage}")
    }
  }
}
