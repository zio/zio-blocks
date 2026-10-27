package processbinarydata

import java.lang.management.ManagementFactory

/**
 * How to Process Binary Data Without Boxing — Measure Bytes per Element
 *
 * Counts the bytes one run of a block allocates on the current thread, after
 * running it long enough for the JIT compiler to compile it, and divides by the
 * number of elements the block processes.
 */
object Allocation {
  private val threads =
    ManagementFactory.getThreadMXBean.asInstanceOf[com.sun.management.ThreadMXBean]

  @volatile private var blackhole: Int = 0

  def bytesPerElement(elements: Int)(body: => Any): Double = {
    var i = 0
    while (i < 1000) { blackhole ^= body.hashCode; i += 1 } // warm up the JIT
    val runs   = 100
    val before = threads.getCurrentThreadAllocatedBytes
    i = 0
    while (i < runs) { blackhole ^= body.hashCode; i += 1 }
    (threads.getCurrentThreadAllocatedBytes - before).toDouble / runs / elements
  }
}
