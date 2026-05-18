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

package zio.blocks.streams

import zio.blocks.chunk.Chunk
import zio.test._

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import java.util.concurrent.CountDownLatch

object ProducerStreamsSpec extends StreamsBaseSpec {

  /**
   * Helper: create a producer stream that emits elements on a separate thread.
   */
  private def producerStream[A](elements: A*): Stream[Nothing, A] =
    ProducerStreams.fromProducer[Nothing, A] { sink =>
      val thread = new Thread(() => {
        elements.foreach(sink.emit(_))
        sink.end()
      })
      thread.start()
      () => thread.interrupt()
    }

  def spec: Spec[TestEnvironment, Any] = suite("ProducerStreams")(
    happyPathSuite,
    errorHandlingSuite,
    cancellationSuite,
    metadataSuite,
    equivalenceSuite,
    resourceSafetySuite,
    robustnessSuite,
    fromProducerSimpleSuite,
    chunkEmissionSuite,
    byteStreamsSuite,
    stressSuite
  )

  private val happyPathSuite = suite("happy path")(
    test("empty producer returns empty chunk") {
      val stream = ProducerStreams.fromProducer[Nothing, Int] { sink =>
        val thread = new Thread(() => sink.end())
        thread.start()
        () => thread.interrupt()
      }
      assertTrue(stream.runCollect == Right(Chunk.empty[Int]))
    },
    test("single element") {
      assertTrue(producerStream(42).runCollect == Right(Chunk(42)))
    },
    test("multiple elements preserve order") {
      assertTrue(producerStream(1, 2, 3, 4, 5).runCollect == Right(Chunk(1, 2, 3, 4, 5)))
    },
    test("large number of elements (1000)") {
      val elems    = (1 to 1000).toList
      val expected = Chunk.fromIterable(elems)
      assertTrue(producerStream(elems: _*).runCollect == Right(expected))
    }
  )

  private val errorHandlingSuite = suite("error handling")(
    test("fail before any data") {
      val stream = ProducerStreams.fromProducer[String, Int] { sink =>
        val thread = new Thread(() => sink.fail("boom"))
        thread.start()
        () => thread.interrupt()
      }
      assertTrue(stream.runCollect == Left("boom"))
    },
    test("fail after partial data") {
      val stream = ProducerStreams.fromProducer[String, Int] { sink =>
        val thread = new Thread(() => {
          sink.emit(1)
          sink.emit(2)
          sink.fail("mid-error")
        })
        thread.start()
        () => thread.interrupt()
      }
      assertTrue(stream.runCollect == Left("mid-error"))
    },
    test("emit after fail returns false") {
      val emitResult = new AtomicBoolean(true)
      val latch      = new java.util.concurrent.CountDownLatch(1)
      val stream     = ProducerStreams.fromProducer[String, Int] { sink =>
        val thread = new Thread(() => {
          sink.fail("err")
          emitResult.set(sink.emit(999))
          latch.countDown()
        })
        thread.start()
        () => thread.interrupt()
      }
      val result = stream.runCollect
      latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
      assertTrue(result == Left("err")) &&
      assertTrue(!emitResult.get)
    },
    test("end after fail is no-op") {
      val stream = ProducerStreams.fromProducer[String, Int] { sink =>
        val thread = new Thread(() => {
          sink.fail("err")
          sink.end()
        })
        thread.start()
        () => thread.interrupt()
      }
      assertTrue(stream.runCollect == Left("err"))
    }
  )

  private val cancellationSuite = suite("cancellation")(
    test("take(n) invokes cancel callback") {
      val cancelled = new AtomicBoolean(false)
      val stream    = ProducerStreams.fromProducer[Nothing, Int] { sink =>
        val thread = new Thread(() => {
          var i = 0
          while (sink.emit(i)) { i += 1 }
        })
        thread.start()
        () => { cancelled.set(true); thread.interrupt() }
      }
      val result = stream.take(3).runCollect
      assertTrue(result == Right(Chunk(0, 1, 2))) &&
      assertTrue(cancelled.get)
    },
    test("cancel callback invoked exactly once on close") {
      val cancelCount = new AtomicInteger(0)
      val stream      = ProducerStreams.fromProducer[Nothing, Int] { sink =>
        val thread = new Thread(() => {
          var i = 0
          while (sink.emit(i)) { i += 1 }
        })
        thread.start()
        () => { cancelCount.incrementAndGet(); thread.interrupt() }
      }
      val result = stream.take(5).runCollect
      assertTrue(result == Right(Chunk(0, 1, 2, 3, 4))) &&
      assertTrue(cancelCount.get == 1)
    },
    test("cancel not called on normal completion (reader already closed)") {
      val cancelled = new AtomicBoolean(false)
      val stream    = ProducerStreams.fromProducer[Nothing, Int] { sink =>
        val thread = new Thread(() => {
          sink.emit(1)
          sink.emit(2)
          sink.end()
        })
        thread.start()
        () => { cancelled.set(true); thread.interrupt() }
      }
      val result = stream.runCollect
      assertTrue(result == Right(Chunk(1, 2))) &&
      assertTrue(!cancelled.get)
    }
  )

  private val metadataSuite = suite("metadata")(
    test("knownLength=Some(42) is reported") {
      val stream = ProducerStreams.fromProducer[Nothing, Int](
        register = { sink =>
          val t = new Thread(() => sink.end())
          t.start()
          () => t.interrupt()
        },
        knownLength = Some(42L)
      )
      assertTrue(stream.knownLength == Some(42L))
    },
    test("knownLength=None by default") {
      val stream = producerStream(1, 2, 3)
      assertTrue(stream.knownLength == None)
    },
    test("knownChunk is always None") {
      val stream = producerStream(1, 2, 3)
      assertTrue(stream.knownChunk == None)
    },
    test("knownLength propagates through map") {
      val stream = ProducerStreams.fromProducer[Nothing, Int](
        register = { sink =>
          val t = new Thread(() => sink.end())
          t.start()
          () => t.interrupt()
        },
        knownLength = Some(10L)
      )
      assertTrue(stream.map(_ + 1).knownLength == Some(10L))
    },
    test("knownLength invalidated by filter") {
      val stream = ProducerStreams.fromProducer[Nothing, Int](
        register = { sink =>
          val t = new Thread(() => sink.end())
          t.start()
          () => t.interrupt()
        },
        knownLength = Some(10L)
      )
      assertTrue(stream.filter(_ > 5).knownLength == None)
    }
  )

  private val equivalenceSuite = suite("equivalence")(
    test("empty producer == Stream.empty") {
      val producer = ProducerStreams.fromProducer[Nothing, Int] { sink =>
        val t = new Thread(() => sink.end())
        t.start()
        () => t.interrupt()
      }
      assertTrue(producer.runCollect == Stream.empty.runCollect)
    },
    test("producer [1,2,3] == Stream(1,2,3)") {
      assertTrue(producerStream(1, 2, 3).runCollect == Stream(1, 2, 3).runCollect)
    },
    test("works with map") {
      assertTrue(producerStream(1, 2, 3).map(_ * 10).runCollect == Stream(1, 2, 3).map(_ * 10).runCollect)
    },
    test("works with filter") {
      assertTrue(
        producerStream(1, 2, 3, 4, 5)
          .filter(_ % 2 == 0)
          .runCollect == Stream(1, 2, 3, 4, 5).filter(_ % 2 == 0).runCollect
      )
    },
    test("works with take") {
      assertTrue(producerStream(1, 2, 3, 4, 5).take(3).runCollect == Stream(1, 2, 3, 4, 5).take(3).runCollect)
    },
    test("works with drop") {
      assertTrue(producerStream(1, 2, 3, 4, 5).drop(2).runCollect == Stream(1, 2, 3, 4, 5).drop(2).runCollect)
    },
    test("works with concat") {
      val p1 = producerStream(1, 2)
      val p2 = producerStream(3, 4)
      assertTrue((p1 ++ p2).runCollect == (Stream(1, 2) ++ Stream(3, 4)).runCollect)
    }
  )

  private val resourceSafetySuite = suite("resource safety")(
    test("cancel not called on normal completion") {
      val cancelCount = new AtomicInteger(0)
      val stream      = ProducerStreams.fromProducer[Nothing, Int] { sink =>
        val thread = new Thread(() => {
          sink.emit(1)
          sink.end()
        })
        thread.start()
        () => { cancelCount.incrementAndGet(); thread.interrupt() }
      }
      val result = stream.runCollect
      assertTrue(result == Right(Chunk(1))) &&
      assertTrue(cancelCount.get == 0)
    },
    test("cancel not called on error") {
      val cancelCount = new AtomicInteger(0)
      val stream      = ProducerStreams.fromProducer[String, Int] { sink =>
        val thread = new Thread(() => sink.fail("err"))
        thread.start()
        () => { cancelCount.incrementAndGet(); thread.interrupt() }
      }
      val result = stream.runCollect
      assertTrue(result == Left("err")) &&
      assertTrue(cancelCount.get == 0)
    },
    test("producer thread completes after close") {
      val threadDone = new CountDownLatch(1)
      val stream     = ProducerStreams.fromProducer[Nothing, Int] { sink =>
        val thread = new Thread(() => {
          try {
            var i = 0
            while (sink.emit(i)) { i += 1 }
          } finally {
            threadDone.countDown()
          }
        })
        thread.start()
        () => thread.interrupt()
      }
      stream.take(5).runCollect
      val completed = threadDone.await(5, java.util.concurrent.TimeUnit.SECONDS)
      assertTrue(completed)
    },
    test("ensuring composes with producer stream") {
      val ensuringCalled = new AtomicBoolean(false)
      val stream         = ProducerStreams
        .fromProducer[Nothing, Int] { sink =>
          val thread = new Thread(() => {
            sink.emit(1)
            sink.emit(2)
            sink.end()
          })
          thread.start()
          () => thread.interrupt()
        }
        .ensuring(ensuringCalled.set(true))
      val result = stream.runCollect
      assertTrue(result == Right(Chunk(1, 2))) &&
      assertTrue(ensuringCalled.get)
    }
  )

  private val robustnessSuite = suite("robustness")(
    test("emit(null) throws NullPointerException") {
      val npeCaught = new AtomicBoolean(false)
      val stream    = ProducerStreams.fromProducer[Nothing, String] { sink =>
        val thread = new Thread(() => {
          try sink.emit(null.asInstanceOf[String])
          catch { case _: NullPointerException => npeCaught.set(true) }
          sink.end()
        })
        thread.start()
        () => thread.interrupt()
      }
      assertTrue(stream.runCollect == Right(Chunk.empty[String])) &&
      assertTrue(npeCaught.get)
    },
    test("register throws synchronously delivers error to consumer") {
      val stream = ProducerStreams.fromProducer[Throwable, Int] { _ =>
        throw new RuntimeException("register-boom")
      }
      val result = stream.runCollect
      assertTrue(result.isLeft) && {
        val error = result.swap.getOrElse(null).asInstanceOf[RuntimeException]
        assertTrue(error.getMessage == "register-boom")
      }
    },
    test("cancel callback exception does not block stream") {
      val stream = ProducerStreams.fromProducer[Nothing, Int] { sink =>
        val thread = new Thread(() => {
          var i = 0
          while (sink.emit(i)) { i += 1 }
        })
        thread.start()
        () => { thread.interrupt(); throw new RuntimeException("cancel-boom") }
      }
      assertTrue(stream.take(3).runCollect == Right(Chunk(0, 1, 2)))
    },
    test("bufferSize=1 works with backpressure") {
      val stream = ProducerStreams.fromProducer[Nothing, Int](
        register = { sink =>
          val thread = new Thread(() => {
            (0 until 10).foreach(sink.emit(_))
            sink.end()
          })
          thread.start()
          () => thread.interrupt()
        },
        bufferSize = 1
      )
      assertTrue(stream.runCollect == Right(Chunk.fromIterable(0 until 10)))
    },
    test("bufferSize=0 throws IllegalArgumentException") {
      val threw = try {
        ProducerStreams.fromProducer[Nothing, Int](
          register = { sink => sink.end(); () => () },
          bufferSize = 0
        )
        false
      } catch {
        case _: IllegalArgumentException => true
      }
      assertTrue(threw)
    },
    test("producer that ignores emit=false") {
      val stream = ProducerStreams.fromProducer[Nothing, Int] { sink =>
        val thread = new Thread(() => {
          var i = 0
          while (i < 100) { sink.emit(i); i += 1 }
          sink.end()
        })
        thread.start()
        () => thread.interrupt()
      }
      assertTrue(stream.take(5).runCollect == Right(Chunk(0, 1, 2, 3, 4)))
    },
    test("concurrent emit and close") {
      val stream = ProducerStreams.fromProducer[Nothing, Int](
        register = { sink =>
          val thread = new Thread(() => {
            var i = 0
            while (sink.emit(i)) { i += 1 }
          })
          thread.start()
          () => thread.interrupt()
        },
        bufferSize = 1
      )
      assertTrue(stream.take(1).runCollect == Right(Chunk(0)))
    }
  )

  private val fromProducerSimpleSuite = suite("fromProducerSimple")(
    test("basic usage") {
      val stream = ProducerStreams.fromProducerSimple[Nothing, Int] { sink =>
        sink.emit(1)
        sink.emit(2)
        sink.emit(3)
      }
      assertTrue(stream.runCollect == Right(Chunk(1, 2, 3)))
    },
    test("auto-ends on normal completion") {
      val stream = ProducerStreams.fromProducerSimple[Nothing, Int] { sink =>
        sink.emit(10)
        sink.emit(20)
      }
      assertTrue(stream.runCollect == Right(Chunk(10, 20)))
    },
    test("catches producer exception") {
      val stream = ProducerStreams.fromProducerSimple[Throwable, Int] { sink =>
        sink.emit(1)
        throw new RuntimeException("boom")
      }
      val result = stream.runCollect
      assertTrue(result.isLeft) && {
        val error = result.swap.getOrElse(null).asInstanceOf[RuntimeException]
        assertTrue(error.getMessage == "boom")
      }
    },
    test("cancellation interrupts thread") {
      val done   = new CountDownLatch(1)
      val stream = ProducerStreams.fromProducerSimple[Nothing, Int] { sink =>
        try {
          var i = 0
          while (sink.emit(i)) {
            i += 1
            Thread.sleep(100)
          }
        } finally {
          done.countDown()
        }
      }
      val result        = stream.take(2).runCollect
      val threadStopped = done.await(5, java.util.concurrent.TimeUnit.SECONDS)
      assertTrue(result == Right(Chunk(0, 1))) &&
      assertTrue(threadStopped)
    }
  )

  private val chunkEmissionSuite = suite("chunk emission")(
    test("fromProducer with chunk emission") {
      val stream = ProducerStreams.fromProducerSimple[Nothing, Int](produce = { sink =>
        sink.emit(Chunk(1, 2, 3))
        sink.emit(Chunk(4, 5, 6))
      })
      val result = stream.runFold(Vector.empty[Int])((acc, i) => acc :+ i)
      assertTrue(result == Right(Vector(1, 2, 3, 4, 5, 6)))
    },
    test("mixed emit and chunk emit") {
      val stream = ProducerStreams.fromProducerSimple[Nothing, Int](produce = { sink =>
        sink.emit(1)
        sink.emit(Chunk(2, 3))
        sink.emit(4)
        sink.emit(Chunk(5, 6))
      })
      val result = stream.runFold(Vector.empty[Int])((acc, i) => acc :+ i)
      assertTrue(result == Right(Vector(1, 2, 3, 4, 5, 6)))
    },
    test("empty chunk emission") {
      val stream = ProducerStreams.fromProducerSimple[Nothing, Int](produce = { sink =>
        sink.emit(Chunk.empty[Int])
        sink.emit(Chunk(1, 2))
        sink.emit(Chunk.empty[Int])
        sink.emit(Chunk(3, 4))
      })
      val result = stream.runFold(Vector.empty[Int])((acc, i) => acc :+ i)
      assertTrue(result == Right(Vector(1, 2, 3, 4)))
    },
    test("large chunk emission") {
      val bigChunk = Chunk.fromArray((0 until 10000).toArray)
      val stream   = ProducerStreams.fromProducerSimple[Nothing, Int](produce = { sink =>
        sink.emit(bigChunk)
      })
      val result = stream.runFold(0L)((acc, i) => acc + i)
      assertTrue(result == Right((0 until 10000).map(_.toLong).sum))
    }
  )

  private val byteStreamsSuite = suite("byte streams")(
    test("fromProducerBytesSimple basic") {
      val stream = ProducerStreams.fromProducerBytesSimple[Nothing](produce = { sink =>
        sink.emit(Chunk.fromArray(Array[Byte](1, 2, 3)))
      })
      val result = stream.runFold(Vector.empty[Byte])((acc, b) => acc :+ b)
      assertTrue(result == Right(Vector[Byte](1, 2, 3)))
    },
    test("fromProducerBytes with multiple chunks") {
      val stream = ProducerStreams.fromProducerBytesSimple[Nothing](produce = { sink =>
        sink.emit(Chunk.fromArray(Array[Byte](10, 20)))
        sink.emit(Chunk.fromArray(Array[Byte](30, 40, 50)))
      })
      val result = stream.runFold(0L)((acc, b) => acc + (b & 0xff))
      assertTrue(result == Right(150L))
    },
    test("fromProducerBytes with runCollect") {
      val stream = ProducerStreams.fromProducerBytesSimple[Nothing](produce = { sink =>
        sink.emit(Chunk.fromArray(Array[Byte](1, 2, 3, 4, 5, 6)))
      })
      val result = stream.runCollect
      assertTrue(result == Right(Chunk[Byte](1, 2, 3, 4, 5, 6)))
    },
    test("fromProducerBytes large") {
      val data   = Array.tabulate[Byte](10000)(i => (i % 256).toByte)
      val stream = ProducerStreams.fromProducerBytesSimple[Nothing](produce = { sink =>
        sink.emit(Chunk.fromArray(data))
      })
      val result = stream.runFold(0L)((acc, b) => acc + (b & 0xff))
      assertTrue(result == Right(data.map(b => (b & 0xff).toLong).sum))
    }
  )

  private val stressSuite = suite("stress")(
    test("1M elements no deadlock") {
      val n      = 1000000
      val stream = ProducerStreams.fromProducerSimple[Nothing, Int](produce = { sink =>
        var i = 0
        while (i < n) { sink.emit(i); i += 1 }
      })
      val result = stream.runFold(0L)((acc, i) => acc + i)
      assertTrue(result == Right(n.toLong * (n - 1) / 2))
    },
    test("1M bytes no deadlock") {
      val n         = 1000000
      val chunkSize = 1024
      val stream    = ProducerStreams.fromProducerBytesSimple[Nothing](produce = { sink =>
        val chunk   = Chunk.fromArray(Array.fill[Byte](chunkSize)(1))
        var emitted = 0
        while (emitted < n) {
          val thisChunk = math.min(chunkSize, n - emitted)
          if (thisChunk == chunkSize) sink.emit(chunk)
          else sink.emit(Chunk.fromArray(Array.fill[Byte](thisChunk)(1)))
          emitted += thisChunk
        }
      })
      val result = stream.runFold(0L)((acc, _) => acc + 1)
      assertTrue(result == Right(1000000L))
    },
    test("FailMarker mid-stream") {
      val stream = ProducerStreams.fromProducerSimple[String, Int](produce = { sink =>
        var i = 0
        while (i < 1000) { sink.emit(i); i += 1 }
        sink.fail("intentional error")
      })
      val result = stream.runFold(0L)((acc, i) => acc + i)
      assertTrue(result.isLeft)
    },
    test("consumer cancels mid-stream") {
      val stream = ProducerStreams.fromProducerSimple[Nothing, Int](produce = { sink =>
        var i = 0
        while (i < 1000000 && sink.emit(i)) { i += 1 }
      })
      val result = stream.take(100).runFold(0L)((acc, i) => acc + i)
      assertTrue(result == Right((0 until 100).map(_.toLong).sum))
    },
    test("zero-length stream") {
      val stream = ProducerStreams.fromProducerSimple[Nothing, Int](produce = { _ => () })
      val result = stream.runFold(0L)((acc, i) => acc + i)
      assertTrue(result == Right(0L))
    },
    test("capacity 2 minimum") {
      val stream = ProducerStreams.fromProducerSimple[Nothing, Int](
        produce = { sink =>
          var i = 0
          while (i < 100) { sink.emit(i); i += 1 }
        },
        bufferSize = 2
      )
      val result = stream.runFold(0L)((acc, i) => acc + i)
      assertTrue(result == Right((0 until 100).map(_.toLong).sum))
    },
    test("producer thread in fromProducerSimple is not the caller thread") {
      @volatile var producerThreadName = ""
      val callerThreadName             = Thread.currentThread().getName
      val stream                       = ProducerStreams.fromProducerSimple[Nothing, Int](produce = { sink =>
        producerThreadName = Thread.currentThread().getName
        sink.emit(42)
      })
      stream.runFold(0)((_, i) => i)
      assertTrue(producerThreadName != callerThreadName)
    }
  )
}
