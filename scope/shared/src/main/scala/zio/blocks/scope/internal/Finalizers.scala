/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.scope.internal

import zio.blocks.chunk.{Chunk, ChunkBuilder}
import zio.blocks.scope.{DeferHandle, Finalization}
import java.util.concurrent.atomic.AtomicReference

/**
 * A thread-safe collection of finalizers that run in LIFO order.
 *
 * Uses a lock-free Treiber stack (CAS-based singly-linked list) for O(1)
 * addition, cancellation via volatile flag, and LIFO execution without sorting.
 */
private[scope] final class Finalizers extends AtomicReference[AnyRef](Finalizers.Empty) {
  import Finalizers._

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
  def add(finalizer: => Unit): DeferHandle = addFn(() => finalizer)

  private[scope] def addFn(thunk: () => Unit): DeferHandle = {
    val node = new Node(thunk)
    if (addNode(node)) new DeferHandle.NodeHandle(node, this)
    else DeferHandle.Noop
  }

  /**
   * Adds a pre-built node to the list. Returns true if successful, false if the
   * scope is already closed. Package-private for use by Scope.open() to avoid
   * extra lambda and DeferHandle allocations.
   */
  private[scope] def addNode(node: Node): Boolean = {
    while (true) {
      val cur = get()
      if (cur eq Closed) return false
      node.next = cur
      if (compareAndSet(cur, node)) return true
    }
    throw new AssertionError("unreachable")
  }

  /**
   * Cancels a node and attempts to physically remove it from the head of the
   * list via CAS. If the node is not at the head, it remains in the list with
   * its cancelled flag set — runAll will skip it. This avoids unsafe non-CAS
   * writes to interior node pointers under concurrent modification.
   */
  private[scope] def remove(target: Node): Unit = {
    target.cancelled = true
    // Only attempt physical removal if the target is at the head.
    // For non-head nodes, the cancelled flag is sufficient: runAll skips them.
    val cur = get()
    if (cur eq target) {
      compareAndSet(cur, target.next) // best-effort; if CAS fails, cancelled flag suffices
    }
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
    val snapshot = getAndSet(Closed)
    if ((snapshot eq Closed) || (snapshot eq Empty)) return Finalization.empty
    var cur                             = snapshot.asInstanceOf[Node]
    var errors: ChunkBuilder[Throwable] = null
    while (cur != null) {
      if (!cur.cancelled) {
        try cur.run()
        catch {
          case t: Throwable =>
            if (errors == null) errors = Chunk.newBuilder[Throwable]
            errors += t
        }
      }
      val next = cur.next
      cur.next = null // help GC
      cur = if ((next eq Empty) || (next eq Closed)) null else next.asInstanceOf[Node]
    }
    if (errors == null) Finalization.empty else Finalization(errors.result())
  }

  /**
   * Returns true if this finalizer collection has been closed.
   */
  def isClosed: Boolean = get() eq Closed

  /**
   * Returns the current number of registered (non-cancelled) finalizers.
   *
   * Note: This is mainly useful for testing. The count may change concurrently.
   */
  def size: Int = {
    var count = 0
    var cur   = get()
    while ((cur ne Empty) && (cur ne Closed)) {
      val node = cur.asInstanceOf[Node]
      if (!node.cancelled) count += 1
      cur = node.next
    }
    count
  }
}

private[scope] object Finalizers {
  private val Empty: AnyRef  = new AnyRef
  private val Closed: AnyRef = new AnyRef

  private[scope] class Node(private val thunk: () => Unit) {
    @volatile var cancelled: Boolean = false
    @volatile var next: AnyRef       = Empty
    def run(): Unit                  = thunk()
  }

  /**
   * Creates a Finalizers instance that starts in the closed state.
   *
   * All operations on a closed Finalizers are no-ops: add() returns
   * DeferHandle.Noop, runAll() returns Finalization.empty, isClosed returns
   * true.
   */
  def closed: Finalizers = {
    val f = new Finalizers
    f.runAll()
    f
  }
}
