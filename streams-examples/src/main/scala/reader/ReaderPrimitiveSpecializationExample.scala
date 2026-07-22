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

package reader

import zio.blocks.chunk.Chunk
import zio.blocks.streams.io.Reader

/**
 * Demonstrates primitive specialization in readers. When a Reader is backed by
 * primitive types (Int, Long, Float, Double), specialized factory methods like
 * singleInt, singleLong, etc. avoid boxing entirely. This example also shows
 * readAll for bulk consumption and skip for advancing the reader.
 */
object ReaderPrimitiveSpecializationExample extends App {

  println("=== singleInt (zero-boxed) ===")
  val intReader = Reader.singleInt(42)
  println(s"Read: ${intReader.read(-1)}")

  println("\n=== singleLong (zero-boxed) ===")
  val longReader = Reader.singleLong(9999999999L)
  println(s"Read: ${longReader.read(Long.MaxValue)}")

  println("\n=== singleFloat (zero-boxed) ===")
  val floatReader = Reader.singleFloat(3.14f)
  println(s"Read: ${floatReader.readFloat(Float.MaxValue)}")

  println("\n=== singleDouble (zero-boxed) ===")
  val doubleReader = Reader.singleDouble(2.718)
  println(s"Read: ${doubleReader.read(Double.MaxValue)}")

  println("\n=== readAll: bulk drain to Chunk ===")
  val bulkReader  = Reader.fromChunk(Chunk(1, 2, 3, 4, 5))
  val allElements = bulkReader.readAll()
  println(s"All elements: $allElements")

  println("\n=== skip: discard n elements ===")
  val skipReader = Reader.fromRange(10 to 15)
  skipReader.skip(2)
  // Should now read from 12 onward
  var v         = skipReader.read(-1)
  val remaining = scala.collection.mutable.ArrayBuffer[Int]()
  while (v != -1) {
    remaining += v
    v = skipReader.read(-1)
  }
  println(s"After skipping 2: ${remaining.toList}")

  println("\n=== reset: rewind to beginning ===")
  val resetReader = Reader.fromChunk(Chunk("x", "y", "z"))
  var elem        = resetReader.read(null: String)
  println(s"First read: $elem")
  resetReader.reset()
  elem = resetReader.read(null: String)
  println(s"After reset: $elem")

  println("\n=== readable: check if elements remain ===")
  val checkReader = Reader.fromChunk(Chunk(100, 200))
  println(s"readable before read: ${checkReader.readable()}")
  checkReader.read(-1)
  println(s"readable after one read: ${checkReader.readable()}")
  checkReader.read(-1)
  println(s"readable after exhaustion: ${checkReader.readable()}")
}
