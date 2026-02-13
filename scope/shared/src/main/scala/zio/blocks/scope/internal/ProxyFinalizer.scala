package zio.blocks.scope.internal

import zio.blocks.scope.{Finalizer, Finalization}

/**
 * A Finalizer that collects cleanup actions for later execution.
 *
 * Used internally by shared resources to defer finalization until all
 * references are released. This is thread-safe and lock-free.
 */
private[scope] final class ProxyFinalizer extends Finalizer {
  private val finalizers = new Finalizers

  override def defer(f: => Unit): Unit = finalizers.add(f)

  /**
   * Runs all collected finalizers in LIFO order.
   *
   * @return
   *   a Finalization containing any exceptions thrown during finalization
   */
  def runAll(): Finalization = finalizers.runAll()
}
