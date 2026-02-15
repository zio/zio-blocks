package zio.blocks.scope

/**
 * A handle for registering cleanup actions (finalizers).
 *
 * Finalizers run in LIFO order when the scope closes. This trait exposes only
 * the `defer` capability, preventing user code from accessing scope internals
 * like `allocate` or `close`.
 */
trait Finalizer {

  /**
   * Registers a finalizer to run when the scope closes.
   *
   * @param f
   *   a by-name expression to execute during cleanup
   * @return
   *   a [[DeferHandle]] that can be used to cancel the registered finalizer
   *   before the scope closes
   */
  def defer(f: => Unit): DeferHandle
}
