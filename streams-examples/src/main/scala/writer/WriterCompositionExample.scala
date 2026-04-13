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
 * Demonstrates writer composition with ++ (concat), transformation with
 * contramap, and error handling via fail(). Shows how multiple writers can be
 * chained and how transformations are applied before writing.
 */
object WriterCompositionExample extends App {

  println("=== concat: ++ operator ===")
  val results = mutable.ArrayBuffer[Int]()

  val w1 = new Writer[Int] {
    def isClosed      = false
    def write(a: Int) = {
      results += a * 10
      a < 50 // reject values >= 50
    }
    def close(): Unit = ()
  }

  val w2 = new Writer[Int] {
    def isClosed      = false
    def write(a: Int) = {
      results += a * 100
      true
    }
    def close(): Unit = ()
  }

  val combined = w1 ++ w2

  combined.write(10) // accepted by w1 (10 < 50)
  combined.write(60) // rejected by w1, switches to w2
  println(s"Results: ${results.toList}")

  println("\n=== contramap: transform elements ===")
  val stringResults = mutable.ArrayBuffer[String]()
  val stringWriter  = new Writer[String] {
    def isClosed         = false
    def write(a: String) = {
      stringResults += a
      true
    }
    def close(): Unit = ()
  }

  val intWriter = stringWriter.contramap[Int](_.toString)
  intWriter.write(42)
  intWriter.write(99)
  println(s"String results: ${stringResults.toList}")

  println("\n=== Multiple contramap: chained transformations ===")
  val doubleResults      = mutable.ArrayBuffer[String]()
  val doubleStringWriter = new Writer[String] {
    def isClosed         = false
    def write(a: String) = {
      doubleResults += a
      true
    }
    def close(): Unit = ()
  }

  val intDoubleWriter = doubleStringWriter
    .contramap[Double](d => s"${d * 2}")
    .contramap[Int](i => i.toDouble)

  intDoubleWriter.write(5)  // 5 -> 5.0 -> "10.0"
  intDoubleWriter.write(10) // 10 -> 10.0 -> "20.0"
  println(s"Double results: ${doubleResults.toList}")

  println("\n=== fail: error closure ===")
  val failWriter = new Writer[Int] {
    private var closed = false
    def isClosed       = closed
    def write(a: Int)  =
      if (closed) false else { println(s"Write: $a"); true }
    def close(): Unit                         = closed = true
    override def fail(error: Throwable): Unit = {
      closed = true
      println(s"Failed with: ${error.getMessage}")
    }
  }

  failWriter.write(1)
  failWriter.fail(new RuntimeException("Oops"))
  println(s"isClosed after fail: ${failWriter.isClosed}")

  println("\n=== writeAll: bulk operations ===")
  val bulkResults = mutable.ArrayBuffer[Int]()
  val bulkWriter  = Writer.limited(
    new Writer[Int] {
      def isClosed      = false
      def write(a: Int) = { bulkResults += a; true }
      def close(): Unit = ()
    },
    2
  )

  val chunk     = Chunk(1, 2, 3, 4)
  val unwritten = bulkWriter.writeAll(chunk)
  println(s"Bulk results: ${bulkResults.toList}")
  println(s"Unwritten: $unwritten")
}
