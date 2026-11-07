package avoidallocation

import java.lang.management.ManagementFactory

/**
 * How to Avoid Allocation on Hot Paths — Measure Allocation Per Tick
 *
 * Counts the bytes one run of a block allocates on the current thread, after
 * running it long enough for the JIT to compile it.
 */
object Allocation {
  private val threads =
    ManagementFactory.getThreadMXBean.asInstanceOf[com.sun.management.ThreadMXBean]

  @volatile private var sink: Int = 0

  // Runs `body` until the JIT has compiled it, then returns the average number
  // of bytes one run allocates on the current thread.
  def bytesPerRun(body: => Any): Long = {
    var i = 0
    while (i < 2000) { sink ^= System.identityHashCode(body); i += 1 }
    val runs   = 200
    val before = threads.getCurrentThreadAllocatedBytes
    i = 0
    while (i < runs) { sink ^= System.identityHashCode(body); i += 1 }
    (threads.getCurrentThreadAllocatedBytes - before) / runs
  }
}
