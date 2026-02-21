package zio.blocks.scope.internal

import zio.blocks.chunk.Chunk
import zio.blocks.scope.{DeferHandle, Finalization}
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

/**
 * A thread-safe collection of finalizers that run in LIFO order.
 *
 * This implementation uses a ConcurrentHashMap with monotonic IDs for O(1)
 * addition, O(1) cancellation (true removal), and LIFO execution via descending
 * ID sort during runAll.
 */
private[scope] final class Finalizers {
  private val counter = new AtomicLong(0L)
  private val entries = new ConcurrentHashMap[Long, () => Unit]()
  private val closed  = new AtomicBoolean(false)

  /**
   * Adds a finalizer to be run when the scope closes.
   *
   * If the scope is already closed, the finalizer is silently ignored and a
   * no-op DeferHandle is returned.
   *
   * @param finalizer
   *   Code to execute on scope close (evaluated by-name)
   * @return
   *   a DeferHandle that can be used to cancel the finalizer
   */
  def add(finalizer: => Unit): DeferHandle = {
    if (closed.get()) return DeferHandle.Noop
    val id    = counter.getAndIncrement()
    val thunk = () => finalizer
    entries.put(id, thunk)
    // Double-check: if closed between get() and put(), remove and don't run
    if (closed.get()) { entries.remove(id); return DeferHandle.Noop }
    new DeferHandle.Live(id, entries)
  }

  /**
   * Runs all registered finalizers in LIFO order and returns any errors.
   *
   * This method is idempotent - calling it multiple times will only run the
   * finalizers once. Subsequent calls return an empty Finalization.
   *
   * @return
   *   A Finalization containing any exceptions thrown by finalizers
   */
  def runAll(): Finalization = {
    if (!closed.compareAndSet(false, true)) return Finalization.empty
    // Snapshot keys into a list — safe even if concurrent adds sneak in
    // between compareAndSet and iteration (the double-check in add() will
    // clean those up, but we may still see them in the iterator).
    val buf  = new java.util.ArrayList[Long]()
    val iter = entries.keySet().iterator()
    while (iter.hasNext) buf.add(iter.next())
    val ids = new Array[Long](buf.size())
    var k   = 0
    while (k < ids.length) { ids(k) = buf.get(k); k += 1 }
    java.util.Arrays.sort(ids)
    // Reverse for LIFO order (highest ID = most recently added = runs first)
    val errorsBuilder = Chunk.newBuilder[Throwable]
    var j             = ids.length - 1
    while (j >= 0) {
      val id    = ids(j)
      val thunk = entries.remove(id)
      if (thunk != null) {
        try thunk()
        catch { case t: Throwable => errorsBuilder += t }
      }
      j -= 1
    }
    Finalization(errorsBuilder.result())
  }

  /**
   * Returns true if this finalizer collection has been closed.
   */
  def isClosed: Boolean = closed.get()

  /**
   * Returns the current number of registered finalizers.
   *
   * Note: This is mainly useful for testing. The count may change concurrently.
   */
  def size: Int = entries.size()
}

private[scope] object Finalizers {

  /**
   * Creates a Finalizers instance that starts in the closed state.
   *
   * All operations on a closed Finalizers are no-ops: add() returns
   * DeferHandle.Noop, runAll() returns Finalization.empty, isClosed returns
   * true.
   */
  def closed: Finalizers = {
    val f = new Finalizers
    f.runAll() // Transitions to closed
    f
  }
}
