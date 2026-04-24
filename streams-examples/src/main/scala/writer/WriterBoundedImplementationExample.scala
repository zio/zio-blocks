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

package writer

import zio.blocks.streams.io.Writer
import scala.collection.mutable

/**
 * Demonstrates implementing a bounded Writer that auto-closes when capacity is
 * reached. Shows how write() returns false only on closure, not on buffer
 * fullness, and how writeable() reflects the closure state.
 */
object WriterBoundedImplementationExample extends App {

  class BoundedWriter[A](maxCapacity: Int) extends Writer[A] {
    private val buffer = mutable.Buffer[A]()
    private var closed = false

    def isClosed: Boolean = closed

    def write(a: A): Boolean =
      if (closed) false
      else if (buffer.size < maxCapacity) {
        buffer += a
        true
      } else {
        // Buffer full: auto-close and reject
        closed = true
        false
      }

    def close(): Unit = closed = true

    override def fail(error: Throwable): Unit = close()

    def contents: mutable.Buffer[A] = buffer
  }

  println("=== Bounded Writer with auto-close ===")
  val bounded = new BoundedWriter[Int](3)

  println(s"Write 10: ${bounded.write(10)}")
  println(s"Write 20: ${bounded.write(20)}")
  println(s"Write 30: ${bounded.write(30)}")
  println(s"Write 40 (buffer full, auto-closes): ${bounded.write(40)}")
  println(s"Write 50 (closed): ${bounded.write(50)}")

  println(s"\nBuffer contents: ${bounded.contents}")
  println(s"Writer closed: ${bounded.isClosed}")
  println(s"Writeable: ${bounded.writeable()}")

  println("\n=== Behavior summary ===")
  println("• write() returns true while space exists")
  println("• When buffer fills, write() auto-closes and returns false")
  println("• All subsequent write() calls return false (closure is permanent)")
  println("• writeable() reflects closure state, not buffer capacity")
}
