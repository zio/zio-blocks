package ringbuffer

import zio.blocks.ringbuffer.SpscRingBuffer
import java.util.concurrent.{CountDownLatch, Thread}

object BatchExample extends App {
  val buffer = SpscRingBuffer[java.lang.Integer](64)
  val latch = new CountDownLatch(1)

  val producer = new Thread(() => {
    var batch = 1
    while (batch <= 3) {
      val currentBatch = batch
      val count = buffer.fill(() => java.lang.Integer.valueOf(currentBatch * 100), 10)
      println(s"Filled $count items in batch $currentBatch")
      batch += 1
    }
  })

  val consumer = new Thread(() => {
    val items = scala.collection.mutable.Buffer[java.lang.Integer]()
    while (items.size < 30) {
      val drained = buffer.drain(items += _, 10)
      if (drained > 0) println(s"Drained $drained items")
    }
    println(s"Total items consumed: ${items.size}")
    latch.countDown()
  })

  producer.start()
  consumer.start()
  latch.await()
  println("Done")
}
