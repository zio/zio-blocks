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

package zio.blocks.ringbuffer

import zio.test._

object BlockingSpmcRingBufferSpec extends ZIOSpecDefault {

  def spec = suite("BlockingSpmcRingBuffer")(
    suite("constructor")(
      test("capacity 0 throws") {
        assertTrue(throws(new BlockingSpmcRingBuffer[String](0)))
      },
      test("capacity 3 (non-power-of-2) throws") {
        assertTrue(throws(new BlockingSpmcRingBuffer[String](3)))
      },
      test("negative capacity throws") {
        assertTrue(throws(new BlockingSpmcRingBuffer[String](-1)))
      },
      test("capacity 1 succeeds") {
        val rb = new BlockingSpmcRingBuffer[String](1)
        assertTrue(rb.capacity == 1)
      },
      test("capacity 2 succeeds") {
        val rb = new BlockingSpmcRingBuffer[String](2)
        assertTrue(rb.capacity == 2)
      },
      test("capacity 1024 succeeds") {
        val rb = new BlockingSpmcRingBuffer[String](1024)
        assertTrue(rb.capacity == 1024)
      }
    ),
    suite("tryOffer/tryTake (non-blocking)")(
      test("tryOffer then tryTake returns the same element") {
        val rb      = new BlockingSpmcRingBuffer[String](4)
        val offered = rb.tryOffer("hello")
        val taken   = rb.tryTake()
        assertTrue(offered, taken == "hello")
      },
      test("tryOffer returns true on non-full buffer") {
        val rb = new BlockingSpmcRingBuffer[String](4)
        assertTrue(rb.tryOffer("a"))
      },
      test("tryTake returns null on empty buffer") {
        val rb = new BlockingSpmcRingBuffer[String](4)
        assertTrue(rb.tryTake() == null)
      },
      test("tryOffer returns false when buffer is full") {
        val rb = new BlockingSpmcRingBuffer[String](4)
        (0 until 4).foreach(i => rb.tryOffer(s"v$i"))
        assertTrue(!rb.tryOffer("overflow"))
      },
      test("FIFO ordering preserved") {
        val rb    = new BlockingSpmcRingBuffer[String](8)
        val items = (0 until 8).map(i => s"item-$i")
        items.foreach(rb.tryOffer)
        val polled = (0 until 8).map(_ => rb.tryTake())
        assertTrue(polled == items)
      },
      test("tryOffer(null) throws NullPointerException") {
        val rb = new BlockingSpmcRingBuffer[String](4)
        assertTrue(throwsNPE(rb.tryOffer(null)))
      }
    ),
    suite("size, isEmpty, isFull")(
      test("fresh buffer: size=0, isEmpty=true, isFull=false") {
        val rb = new BlockingSpmcRingBuffer[String](4)
        assertTrue(rb.size == 0, rb.isEmpty, !rb.isFull)
      },
      test("after one offer: size=1, isEmpty=false, isFull=false") {
        val rb = new BlockingSpmcRingBuffer[String](4)
        rb.tryOffer("a")
        assertTrue(rb.size == 1, !rb.isEmpty, !rb.isFull)
      },
      test("full buffer: size=capacity, isEmpty=false, isFull=true") {
        val rb = new BlockingSpmcRingBuffer[String](4)
        (0 until 4).foreach(i => rb.tryOffer(s"v$i"))
        assertTrue(rb.size == 4, !rb.isEmpty, rb.isFull)
      },
      test("after drain: size=0, isEmpty=true, isFull=false") {
        val rb = new BlockingSpmcRingBuffer[String](4)
        (0 until 4).foreach(i => rb.tryOffer(s"v$i"))
        (0 until 4).foreach(_ => rb.tryTake())
        assertTrue(rb.size == 0, rb.isEmpty, !rb.isFull)
      }
    ),
    suite("wrap-around")(
      test("fill and drain multiple cycles") {
        val rb         = new BlockingSpmcRingBuffer[String](4)
        var allCorrect = true
        (0 until 10).foreach { cycle =>
          val items = (0 until 4).map(i => s"cycle$cycle-item$i")
          items.foreach(rb.tryOffer)
          val polled = (0 until 4).map(_ => rb.tryTake())
          if (polled != items) allCorrect = false
        }
        assertTrue(allCorrect)
      }
    ),
    suite("companion object")(
      test("BlockingSpmcRingBuffer.apply creates a buffer") {
        val rb = BlockingSpmcRingBuffer[String](8)
        assertTrue(rb.capacity == 8, rb.isEmpty)
      }
    )
  )

  private def throws(thunk: => Any): Boolean =
    try {
      thunk
      false
    } catch {
      case _: IllegalArgumentException => true
      case _: Throwable                => false
    }

  private def throwsNPE(thunk: => Any): Boolean =
    try {
      thunk
      false
    } catch {
      case _: NullPointerException => true
      case _: Throwable            => false
    }
}
