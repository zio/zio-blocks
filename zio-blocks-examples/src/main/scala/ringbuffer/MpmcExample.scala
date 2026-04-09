package ringbuffer

import zio.blocks.ringbuffer.MpmcRingBuffer
import java.util.concurrent.{CountDownLatch, Thread}
import java.util.concurrent.atomic.AtomicInteger

object MpmcExample extends App {
  val buffer = MpmcRingBuffer[String](32)
  val processed = new AtomicInteger(0)
  val latch = new CountDownLatch(2)

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
        if (task ne null) {
          println(s"Worker processing: $task")
          processed.incrementAndGet()
        }
      }
      latch.countDown()
    })
  }

  (producers ++ consumers).foreach(_.start())
  producers.foreach(_.join())
  latch.await()
  println("All tasks completed")
}
