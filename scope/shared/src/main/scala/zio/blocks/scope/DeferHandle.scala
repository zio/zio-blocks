package zio.blocks.scope

/**
 * A handle returned by [[Scope.defer]] that allows cancelling a registered
 * finalizer before the scope closes.
 *
 * When `scope.defer(cleanup)` is called, the cleanup action is registered to
 * run when the scope closes, and a `DeferHandle` is returned. The handle can be
 * used to remove that finalizer early, attempting to prevent it from running
 * when the scope closes. If cancellation races with scope closure, the
 * finalizer may already be in the process of running. This is useful when a
 * resource is explicitly released before the scope ends, and running the
 * finalizer again would be unnecessary or harmful.
 *
 * @see
 *   [[Scope.defer]] for registering finalizers
 */
abstract class DeferHandle {

  /**
   * Cancels the registered finalizer so it will not run when the scope closes.
   *
   * This method is thread-safe and idempotent: calling it multiple times has
   * the same effect as calling it once. If the scope has already closed (and
   * the finalizer has already run or been discarded), this method is a no-op.
   */
  def cancel(): Unit
}

object DeferHandle {
  private[scope] object Noop extends DeferHandle {
    def cancel(): Unit = ()
  }

  private[scope] final class Live(
    id: Long,
    entries: java.util.concurrent.ConcurrentHashMap[Long, () => Unit]
  ) extends DeferHandle {
    def cancel(): Unit = { entries.remove(id); () }
  }
}
