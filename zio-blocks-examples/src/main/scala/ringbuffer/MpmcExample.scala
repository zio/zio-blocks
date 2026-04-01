package ringbuffer

import zio.blocks.ringbuffer.MpmcRingBuffer
import java.util.concurrent.{CountDownLatch, Thread, atomic}

object MpmcExample extends App {
  val buffer = MpmcRingBuffer[String](32)
  val processed = new atomic.AtomicInteger(0)
  val latch = new CountDownLatch(1)

  val producers = (0 until 2).map { id =>
    new Thread(() => {
      for (i <- 1 to 5) {
        buffer.offer(s"task-$id-$i")
      }
    })
  }

  val consumers = (0 until 2).map { _ =>
    new Thread(() => {
      while (processed.get() < 10) {
        val task = buffer.take()
        if (!task.eq(null)) {
          println(s"Worker processing: $task")
          processed.incrementAndGet()
        }
      }
    })
  }

  (producers ++ consumers).foreach(_.start())
  producers.foreach(_.join())
  while (processed.get() < 10) Thread.sleep(1)
  latch.countDown()
  println("All tasks completed")
}
