package zio.blocks.scope.internal

import zio.blocks.chunk.Chunk
import zio.blocks.scope.Finalizer

/**
 * A Finalizer that collects cleanup actions for later execution.
 *
 * Used internally by shared resources to defer finalization until all
 * references are released. This is thread-safe and lock-free.
 */
private[scope] final class ProxyFinalizer extends Finalizer {
  private val finalizers = new Finalizers

  def defer(f: => Unit): Unit = finalizers.add(f)

  /**
   * Runs all collected finalizers in LIFO order.
   *
   * @return
   *   any exceptions thrown during finalization
   */
  def runAll(): Chunk[Throwable] = finalizers.runAll()
}
