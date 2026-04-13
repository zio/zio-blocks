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
import zio.blocks.chunk.Chunk
import scala.collection.mutable

/**
 * Demonstrates the most common Writer factories: single, limited, closed, and
 * custom writers via subclassing. Each writer is fed manually with write() to
 * show how to produce elements.
 */
object WriterBasicConstructionExample extends App {

  println("=== Writer.single ===")
  val singleWriter = Writer.single[Int]
  println(s"Write 42: ${singleWriter.write(42)}")
  println(s"Write 99 (closed): ${singleWriter.write(99)}")
  println(s"isClosed: ${singleWriter.isClosed}")

  println("\n=== Writer.closed ===")
  val closedWriter = Writer.closed
  println(s"Write to closed: ${closedWriter.write(1)}")
  println(s"isClosed: ${closedWriter.isClosed}")

  println("\n=== Writer.limited ===")
  val limitedWriter = Writer.limited(Writer.single[String], 2)
  println(s"Write 'a': ${limitedWriter.write("a")}")
  println(s"Write 'b': ${limitedWriter.write("b")}")
  println(s"Write 'c': ${limitedWriter.write("c")}")
  println(s"isClosed: ${limitedWriter.isClosed}")

  println("\n=== writeAll: bulk write ===")
  val collected     = mutable.ArrayBuffer[Int]()
  val collectWriter = new Writer[Int] {
    def isClosed      = false
    def write(a: Int) = { collected += a; true }
    def close(): Unit = ()
  }

  val chunk     = Chunk(10, 20, 30)
  val remaining = collectWriter.writeAll(chunk)
  println(s"Collected: ${collected.toList}")
  println(s"Remaining: $remaining")

  println("\n=== writeable: check capacity ===")
  val capWriter = Writer.single[Int]
  println(s"writeable before: ${capWriter.writeable()}")
  capWriter.write(42)
  println(s"writeable after: ${capWriter.writeable()}")

  println("\n=== Custom Writer ===")
  val upperWriter = new Writer[String] {
    private val buffer   = mutable.ArrayBuffer[String]()
    def isClosed         = false
    def write(a: String) = {
      buffer += a.toUpperCase()
      true
    }
    def close(): Unit = println(s"Final buffer: $buffer")
  }

  upperWriter.write("hello")
  upperWriter.write("world")
  upperWriter.close()
}
