package scope.examples

import zio.blocks.scope._
import java.util.concurrent.{Executors, CountDownLatch}

/**
 * Simulates a stateful resource that tracks which thread owns it.
 *
 * @param name
 *   the resource name
 */
final class ThreadAwareResource(val name: String) extends AutoCloseable {
  private val createdThread = Thread.currentThread()

  def getInfo: String = {
    val currentThread = Thread.currentThread()
    val owner         = createdThread.getName
    val current       = currentThread.getName
    if (createdThread eq currentThread) {
      s"[$name] Safe: owned by '$owner', accessed by '$current' (same thread)"
    } else {
      s"[$name] WARNING: owned by '$owner', accessed by '$current' (different thread!)"
    }
  }

  override def close(): Unit =
    println(s"[$name] Closing resource (was created by ${createdThread.getName})")
}

/**
 * Demonstrates thread ownership enforcement in ZIO Blocks Scope.
 *
 * This example shows:
 *   - Scope.global: `isOwner` always true; any thread can create children from
 *     it
 *   - Scope.Child: captures the creating thread; `isOwner` checks
 *     `Thread.currentThread() eq owner`
 *   - Scope.open(): creates an unowned child scope; `isOwner` always true from
 *     any thread
 *   - Calling `scoped` on a Scope.Child from a different thread throws
 *     IllegalStateException
 *
 * Thread ownership prevents accidentally passing a scope to another thread and
 * using it there, which would violate structured concurrency guarantees.
 */
@main def runThreadOwnershipExample(): Unit = {
  println("=== Thread Ownership Example ===\n")

  // === Part 1: Single-thread usage (CORRECT) ===
  println("--- Part 1: Single-thread usage (correct) ---\n")

  Scope.global.scoped { scope =>
    val currentThread = Thread.currentThread().getName
    println(s"[Main] Entered scope on thread: $currentThread\n")

    // Scope.Child is owned by the current thread (main)
    scope.scoped { child =>
      import child._
      println(s"[Main] Created child scope on thread: $currentThread")
      println(s"[Main] Child scope isOwner: ${child.isOwner} (true only for the creating thread)")

      val res: $[ThreadAwareResource] =
        allocate(Resource(new ThreadAwareResource("SingleThreadResource")))

      $(res) { r =>
        println(s"[Main] ${r.getInfo}\n")
      }
    }

    println(s"[Main] Child scope closed, finalizers ran\n")
  }

  // === Part 2: Demonstrating Scope.open() for cross-thread usage ===
  println("--- Part 2: Unowned scope via open() (for cross-thread) ---\n")

  Scope.global.scoped { scope =>
    import scope._
    val mainThread = Thread.currentThread().getName
    println(s"[Main] On thread: $mainThread\n")

    // open() creates an unowned scope that any thread can use
    $(open()) { handle =>
      val childScope = handle.scope
      println(s"[Main] Created unowned scope via open()")
      println(s"[Main] Unowned scope isOwner: ${childScope.isOwner} (true from any thread)\n")

      // Now we can use this scope from a different thread
      val executor = Executors.newSingleThreadExecutor { r =>
        val t = new Thread(r)
        t.setName("worker-thread")
        t
      }

      try {
        val latch = new CountDownLatch(1)

        executor.execute { () =>
          try {
            val workerThread = Thread.currentThread().getName
            println(s"[Worker] On thread: $workerThread\n")

            // Using the unowned scope from a different thread - this works!
            childScope.scoped { workerChild =>
              import workerChild._
              println(s"[Worker] Created child of unowned scope")

              val res: $[ThreadAwareResource] =
                allocate(Resource(new ThreadAwareResource("CrossThreadResource")))

              $(res) { r =>
                println(s"[Worker] ${r.getInfo}\n")
              }

              println("[Worker] Worker scope closed")
            }
          } finally {
            latch.countDown()
          }
        }

        // Wait for worker thread to finish
        latch.await()
        println()
      } finally {
        executor.shutdown()
      }

      // Clean up the open scope and propagate any finalizer failures
      handle.close().orThrow()
      println("[Main] Unowned scope closed\n")
    }
  }

  // === Part 3: Explanation of ownership violation (what would fail) ===
  println("--- Part 3: Thread ownership violation (explanation) ---\n")
  println("""
If you tried to pass a Scope.Child to another thread and call scoped on it,
you would get an IllegalStateException:

  Scope.global.scoped { scope =>
    import scope._

    // This scope is owned by the main thread
    val executor = Executors.newSingleThreadExecutor()

    executor.execute { () =>
      // This would throw: Cannot create child scope: current thread does not own this scope.
      scope.scoped { child => ... }  // WRONG: scope is owned by main thread
    }
  }

Solution: Use scope.open() instead, which creates an unowned scope that
any thread can use. See Part 2 above for the correct pattern.
""")

  println("=== Example Complete ===")
}
