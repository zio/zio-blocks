package zio.blocks.scope.internal

import zio.blocks.chunk.Chunk
import java.util.concurrent.atomic.AtomicReference

/**
 * A thread-safe collection of finalizers that run in LIFO order.
 *
 * This implementation is lock-free, using an atomic reference to a linked list
 * of finalizers. Finalizers added after close() is called are silently ignored.
 */
private[scope] final class Finalizers {

  private sealed trait State
  private case class Open(head: Finalizers.Node) extends State
  private case object Closed                     extends State

  private val state: AtomicReference[State] = new AtomicReference(Open(null))

  /**
   * Adds a finalizer to be run when the scope closes.
   *
   * If the scope is already closed, the finalizer is silently ignored.
   *
   * @param finalizer
   *   Code to execute on scope close (evaluated by-name)
   */
  def add(finalizer: => Unit): Unit = {
    val thunk = () => finalizer
    var done  = false
    while (!done) {
      state.get() match {
        case Closed             => done = true
        case o: Open @unchecked =>
          val newHead = Finalizers.Node(thunk, o.head)
          done = state.compareAndSet(o, Open(newHead))
      }
    }
  }

  /**
   * Runs all registered finalizers in LIFO order and returns any errors.
   *
   * This method is idempotent - calling it multiple times will only run the
   * finalizers once. Subsequent calls return an empty Chunk.
   *
   * @return
   *   A Chunk containing any exceptions thrown by finalizers
   */
  def runAll(): Chunk[Throwable] = {
    val prev = state.getAndSet(Closed)
    prev match {
      case Closed             => Chunk.empty
      case o: Open @unchecked =>
        val errorsBuilder = Chunk.newBuilder[Throwable]
        var current       = o.head
        while (current != null) {
          try current.run()
          catch { case t: Throwable => errorsBuilder += t }
          current = current.next
        }
        errorsBuilder.result()
    }
  }

  /**
   * Runs all registered finalizers in LIFO order, throwing if any failed.
   *
   * If multiple finalizers throw, the first exception (LIFO order) is thrown
   * with all subsequent exceptions added as suppressed. This ensures no
   * exceptions are lost.
   *
   * @throws Throwable
   *   the first finalizer exception, with others suppressed
   */
  def runAllOrThrow(): Unit = {
    val errors = runAll()
    if (errors.nonEmpty) {
      val first = errors.head
      errors.tail.foreach(first.addSuppressed)
      throw first
    }
  }

  /**
   * Returns true if this finalizer collection has been closed.
   */
  def isClosed: Boolean = state.get() eq Closed

  /**
   * Returns the current number of registered finalizers.
   *
   * Note: This is mainly useful for testing. The count may change concurrently.
   */
  def size: Int =
    state.get() match {
      case Closed             => 0
      case o: Open @unchecked =>
        var count   = 0
        var current = o.head
        while (current != null) {
          count += 1
          current = current.next
        }
        count
    }
}

private[scope] object Finalizers {
  private[internal] final case class Node(run: () => Unit, next: Node)

  /**
   * Creates a Finalizers instance that starts in the closed state.
   *
   * All operations on a closed Finalizers are no-ops: add() is ignored,
   * runAll() returns empty, isClosed returns true.
   */
  def closed: Finalizers = {
    val f = new Finalizers
    f.runAll() // Transitions from Open(null) to Closed
    f
  }
}
