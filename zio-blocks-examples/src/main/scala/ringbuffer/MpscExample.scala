package ringbuffer

import zio.blocks.ringbuffer.MpscRingBuffer
import java.util.concurrent.{CountDownLatch, Thread}

object MpscExample extends App {
  val buffer = MpscRingBuffer[java.lang.Integer](16)
  val latch = new CountDownLatch(1)

  val producers = (0 until 3).map { id =>
    new Thread(() => {
      for (i <- 1 to 4) {
        buffer.offer(java.lang.Integer.valueOf(id * 100 + i))
      }
    })
  }

  val consumer = new Thread(() => {
    var received = 0
    while (received < 12) {
      val item = buffer.take()
      if (item ne null) {
        println(s"Processed: $item")
        received += 1
      }
    }
    latch.countDown()
  })

  producers.foreach(_.start())
  consumer.start()
  producers.foreach(_.join())
  latch.await()
  println("All items processed")
}
