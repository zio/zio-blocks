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

object BlockingMpscRingBufferSpec extends ZIOSpecDefault {

  def spec = suite("BlockingMpscRingBuffer")(
    suite("constructor")(
      test("capacity 0 throws") {
        assertTrue(throws(new BlockingMpscRingBuffer[String](0)))
      },
      test("capacity 3 (non-power-of-2) throws") {
        assertTrue(throws(new BlockingMpscRingBuffer[String](3)))
      },
      test("negative capacity throws") {
        assertTrue(throws(new BlockingMpscRingBuffer[String](-1)))
      },
      test("capacity 1 succeeds") {
        val rb = new BlockingMpscRingBuffer[String](1)
        assertTrue(rb.capacity == 1)
      },
      test("capacity 1024 succeeds") {
        val rb = new BlockingMpscRingBuffer[String](1024)
        assertTrue(rb.capacity == 1024)
      }
    ),
    suite("non-blocking tryOffer/tryTake delegation")(
      test("tryOffer then tryTake returns the same element") {
        val rb      = new BlockingMpscRingBuffer[String](4)
        val offered = rb.tryOffer("hello")
        val taken   = rb.tryTake()
        assertTrue(offered, taken == "hello")
      },
      test("tryOffer returns true on non-full buffer") {
        val rb = new BlockingMpscRingBuffer[String](4)
        assertTrue(rb.tryOffer("a"))
      },
      test("tryOffer returns false on full buffer") {
        val rb = new BlockingMpscRingBuffer[String](2)
        rb.tryOffer("a")
        rb.tryOffer("b")
        assertTrue(!rb.tryOffer("c"))
      },
      test("tryTake returns null on empty buffer") {
        val rb = new BlockingMpscRingBuffer[String](4)
        assertTrue(rb.tryTake() == null)
      }
    ),
    suite("FIFO ordering via tryOffer/tryTake")(
      test("elements are taken in tryOffer order") {
        val rb    = new BlockingMpscRingBuffer[String](8)
        val items = (0 until 8).map(i => s"item-$i")
        items.foreach(rb.tryOffer)
        val polled = (0 until 8).map(_ => rb.tryTake())
        assertTrue(polled == items)
      }
    ),
    suite("size, isEmpty, isFull")(
      test("fresh buffer: size=0, isEmpty=true, isFull=false") {
        val rb = new BlockingMpscRingBuffer[String](4)
        assertTrue(rb.size == 0, rb.isEmpty, !rb.isFull)
      },
      test("after one offer: size=1, isEmpty=false") {
        val rb = new BlockingMpscRingBuffer[String](4)
        rb.tryOffer("a")
        assertTrue(rb.size == 1, !rb.isEmpty)
      },
      test("full buffer: isFull=true") {
        val rb = new BlockingMpscRingBuffer[String](2)
        rb.tryOffer("a")
        rb.tryOffer("b")
        assertTrue(rb.isFull)
      },
      test("after drain: size=0, isEmpty=true") {
        val rb = new BlockingMpscRingBuffer[String](4)
        rb.tryOffer("a")
        rb.tryOffer("b")
        rb.tryTake()
        rb.tryTake()
        assertTrue(rb.size == 0, rb.isEmpty)
      }
    ),
    suite("null rejection")(
      test("tryOffer(null) throws NullPointerException") {
        val rb = new BlockingMpscRingBuffer[String](4)
        assertTrue(throwsNPE(rb.tryOffer(null)))
      }
    ),
    suite("wrap-around")(
      test("fill and drain multiple cycles") {
        val rb         = new BlockingMpscRingBuffer[String](4)
        var allCorrect = true
        (0 until 10).foreach { cycle =>
          val items = (0 until 4).map(i => s"cycle$cycle-item$i")
          items.foreach(rb.tryOffer)
          val polled = (0 until 4).map(_ => rb.tryTake())
          if (polled != items) allCorrect = false
        }
        assertTrue(allCorrect)
      },
      test("partial fill and drain across wrap boundary") {
        val rb         = new BlockingMpscRingBuffer[String](4)
        var allCorrect = true
        (0 until 20).foreach { cycle =>
          val items = (0 until 3).map(i => s"c$cycle-$i")
          items.foreach(rb.tryOffer)
          val polled = (0 until 3).map(_ => rb.tryTake())
          if (polled != items) allCorrect = false
        }
        assertTrue(allCorrect)
      }
    ),
    suite("capacity=1 edge case")(
      test("tryOffer and tryTake with capacity 1") {
        val rb       = new BlockingMpscRingBuffer[String](1)
        val offered  = rb.tryOffer("only")
        val full     = rb.isFull
        val rejected = !rb.tryOffer("overflow")
        val v        = rb.tryTake()
        val empty    = rb.isEmpty
        assertTrue(offered, full, rejected, v == "only", empty)
      },
      test("repeated tryOffer/tryTake cycles with capacity 1") {
        val rb         = new BlockingMpscRingBuffer[String](1)
        var allCorrect = true
        (0 until 100).foreach { i =>
          val item = s"item-$i"
          if (!rb.tryOffer(item)) allCorrect = false
          val polled = rb.tryTake()
          if (polled != item) allCorrect = false
        }
        assertTrue(allCorrect)
      }
    ),
    suite("companion object")(
      test("BlockingMpscRingBuffer.apply creates a buffer") {
        val rb = BlockingMpscRingBuffer[String](8)
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
