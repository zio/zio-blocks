package zio.blocks.scope.internal

import zio.blocks.chunk.Chunk
import zio.blocks.context.Context
import zio.blocks.scope.Scope
import java.util.concurrent.atomic.AtomicBoolean

private[scope] final class ScopeImplScala2[H, T, ParentTag](
  parent: Scope[_],
  context: Context[H],
  finalizers: Finalizers,
  private[internal] val errorReporter: Chunk[Throwable] => Unit = ScopeImplScala2.defaultErrorReporter
) extends ScopeImpl[H, T](parent, context, finalizers) {

  /**
   * In Scala 3, the Tag type is defined as:
   *
   * type Tag = ParentTag | this.type
   *
   * This union type precisely represents that the child scope's tag encompasses
   * both the parent's tag and its own identity.
   *
   * Since Scala 2 does not support union types, we approximate this with a
   * lower type bound:
   *
   * type Tag >: ParentTag
   *
   * This is intentionally less precise: it allows any supertype of `ParentTag`,
   * not specifically the union. However, the runtime semantics are equivalent,
   * and the practical safety guarantees are maintained because scoped values
   * can only be obtained from their originating scope.
   */
  type Tag >: ParentTag

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
