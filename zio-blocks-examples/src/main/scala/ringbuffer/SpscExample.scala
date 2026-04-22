package ringbuffer

import zio.blocks.ringbuffer.SpscRingBuffer
import java.util.concurrent.{CountDownLatch, Thread}

object SpscExample extends App {
  val buffer = SpscRingBuffer[String](8)
  val latch = new CountDownLatch(1)

  val producer = new Thread(() => {
    for (i <- 1 to 5) {
      buffer.offer(s"message-$i")
    }
  })

  val consumer = new Thread(() => {
    for (_ <- 1 to 5) {
      var msg: String = null
      while ({ msg = buffer.take(); msg.eq(null) }) {}
      println(s"Received: $msg")
    }
    latch.countDown()
  })

  producer.start()
  consumer.start()
  latch.await()
  println("Done")
}
