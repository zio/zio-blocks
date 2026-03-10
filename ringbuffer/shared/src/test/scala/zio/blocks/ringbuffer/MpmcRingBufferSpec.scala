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

object MpmcRingBufferSpec extends ZIOSpecDefault {

  def spec = suite("MpmcRingBuffer")(
    suite("constructor")(
      test("capacity 0 throws") {
        assertTrue(throws(new MpmcRingBuffer[String](0)))
      },
      test("capacity 3 (non-power-of-2) throws") {
        assertTrue(throws(new MpmcRingBuffer[String](3)))
      },
      test("negative capacity throws") {
        assertTrue(throws(new MpmcRingBuffer[String](-1)))
      },
      test("capacity 1 throws (requires >= 2 for sequence buffer algorithm)") {
        assertTrue(throws(new MpmcRingBuffer[String](1)))
      },
      test("capacity 2 succeeds") {
        val rb = new MpmcRingBuffer[String](2)
        assertTrue(rb.capacity == 2)
      },
      test("capacity 1024 succeeds") {
        val rb = new MpmcRingBuffer[String](1024)
        assertTrue(rb.capacity == 1024)
      }
    ),
    suite("single-element offer/take")(
      test("offer then take returns the same element") {
        val rb      = new MpmcRingBuffer[String](4)
        val offered = rb.offer("hello")
        val taken   = rb.take()
        assertTrue(offered, taken == "hello")
      },
      test("offer returns true on non-full buffer") {
        val rb = new MpmcRingBuffer[String](4)
        assertTrue(rb.offer("a"))
      },
      test("take returns null on empty buffer") {
        val rb = new MpmcRingBuffer[String](4)
        assertTrue(rb.take() == null)
      }
    ),
    suite("null rejection")(
      test("offer(null) throws NullPointerException") {
        val rb = new MpmcRingBuffer[String](4)
        assertTrue(throwsNPE(rb.offer(null)))
      }
    ),
    suite("FIFO ordering")(
      test("elements are taken in offer order") {
        val rb    = new MpmcRingBuffer[String](8)
        val items = (0 until 8).map(i => s"item-$i")
        items.foreach(rb.offer)
        val polled = (0 until 8).map(_ => rb.take())
        assertTrue(polled == items)
      },
      test("partial fill and drain preserves order") {
        val rb    = new MpmcRingBuffer[String](16)
        val items = (0 until 5).map(i => s"elem-$i")
        items.foreach(rb.offer)
        val polled = (0 until 5).map(_ => rb.take())
        assertTrue(polled == items)
      }
    ),
    suite("full buffer")(
      test("offer returns false when buffer is full") {
        val rb = new MpmcRingBuffer[String](4)
        (0 until 4).foreach(i => rb.offer(s"v$i"))
        assertTrue(!rb.offer("overflow"))
      },
      test("no element is lost when offer fails on full buffer") {
        val rb = new MpmcRingBuffer[String](2)
        rb.offer("a")
        rb.offer("b")
        rb.offer("c") // should fail
        assertTrue(rb.take() == "a", rb.take() == "b", rb.take() == null)
      }
    ),
    suite("empty buffer")(
      test("take returns null on fresh buffer") {
        val rb = new MpmcRingBuffer[String](4)
        assertTrue(rb.take() == null)
      },
      test("take returns null after draining all elements") {
        val rb = new MpmcRingBuffer[String](4)
        rb.offer("x")
        rb.take()
        assertTrue(rb.take() == null)
      }
    ),
    suite("wrap-around")(
      test("fill and drain multiple cycles") {
        val rb         = new MpmcRingBuffer[String](4)
        var allCorrect = true
        (0 until 10).foreach { cycle =>
          val items = (0 until 4).map(i => s"cycle$cycle-item$i")
          items.foreach(rb.offer)
          val polled = (0 until 4).map(_ => rb.take())
          if (polled != items) allCorrect = false
        }
        assertTrue(allCorrect)
      },
      test("partial fill and drain across wrap boundary") {
        val rb = new MpmcRingBuffer[String](4)
        // Fill 3, drain 3, repeat — forces wrap-around at different offsets
        var allCorrect = true
        (0 until 20).foreach { cycle =>
          val items = (0 until 3).map(i => s"c$cycle-$i")
          items.foreach(rb.offer)
          val polled = (0 until 3).map(_ => rb.take())
          if (polled != items) allCorrect = false
        }
        assertTrue(allCorrect)
      }
    ),
    suite("size, isEmpty, isFull")(
      test("fresh buffer: size=0, isEmpty=true, isFull=false") {
        val rb = new MpmcRingBuffer[String](4)
        assertTrue(rb.size == 0, rb.isEmpty, !rb.isFull)
      },
      test("after one offer: size=1, isEmpty=false, isFull=false") {
        val rb = new MpmcRingBuffer[String](4)
        rb.offer("a")
        assertTrue(rb.size == 1, !rb.isEmpty, !rb.isFull)
      },
      test("full buffer: size=capacity, isEmpty=false, isFull=true") {
        val rb = new MpmcRingBuffer[String](4)
        (0 until 4).foreach(i => rb.offer(s"v$i"))
        assertTrue(rb.size == 4, !rb.isEmpty, rb.isFull)
      },
      test("after drain: size=0, isEmpty=true, isFull=false") {
        val rb = new MpmcRingBuffer[String](4)
        (0 until 4).foreach(i => rb.offer(s"v$i"))
        (0 until 4).foreach(_ => rb.take())
        assertTrue(rb.size == 0, rb.isEmpty, !rb.isFull)
      },
      test("size tracks each offer and take") {
        val rb = new MpmcRingBuffer[String](8)
        rb.offer("a")
        rb.offer("b")
        rb.offer("c")
        val s1 = rb.size
        rb.take()
        val s2 = rb.size
        rb.take()
        val s3 = rb.size
        assertTrue(s1 == 3, s2 == 2, s3 == 1)
      }
    ),
    suite("capacity=2 edge case")(
      test("offer and take with capacity 2") {
        val rb       = new MpmcRingBuffer[String](2)
        val offered  = rb.offer("a")
        val offered2 = rb.offer("b")
        val full     = rb.isFull
        val rejected = !rb.offer("overflow")
        val v1       = rb.take()
        val v2       = rb.take()
        val empty    = rb.isEmpty
        assertTrue(offered, offered2, full, rejected, v1 == "a", v2 == "b", empty)
      },
      test("repeated offer/take cycles with capacity 2") {
        val rb         = new MpmcRingBuffer[String](2)
        var allCorrect = true
        (0 until 100).foreach { i =>
          val item = s"item-$i"
          if (!rb.offer(item)) allCorrect = false
          val polled = rb.take()
          if (polled != item) allCorrect = false
        }
        assertTrue(allCorrect)
      }
    ),
    suite("companion object")(
      test("MpmcRingBuffer.apply creates a buffer") {
        val rb = MpmcRingBuffer[String](8)
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
