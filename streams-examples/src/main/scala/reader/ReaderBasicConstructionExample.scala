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
 * Demonstrates the most common Reader factories: fromChunk, fromIterable,
 * fromRange, single, and unfold. Each reader is drained manually with read() to
 * show how to consume elements.
 */
object ReaderBasicConstructionExample extends App {

  println("=== Reader.fromChunk ===")
  val chunkReader = Reader.fromChunk(Chunk(10, 20, 30))
  var v           = chunkReader.read(-1)
  while (v != -1) {
    println(s"Read: $v")
    v = chunkReader.read(-1)
  }

  println("\n=== Reader.fromRange ===")
  val rangeReader = Reader.fromRange(1 to 3)
  v = rangeReader.read(-1)
  while (v != -1) {
    println(s"Read: $v")
    v = rangeReader.read(-1)
  }

  println("\n=== Reader.fromIterable ===")
  val listReader = Reader.fromIterable(List("a", "b", "c"))
  var sv         = listReader.read(null: String)
  while (sv != null) {
    println(s"Read: $sv")
    sv = listReader.read(null: String)
  }

  println("\n=== Reader.single ===")
  val singleReader = Reader.single(42)
  println(s"Read: ${singleReader.read(-1)}")
  println(s"Read again (closed): ${singleReader.read(-1)}")

  println("\n=== Reader.unfold ===")
  val unfoldReader = Reader.unfold(1) { s =>
    if (s > 3) None else Some((s * 10, s + 1))
  }
  v = unfoldReader.read(-1)
  while (v != -1) {
    println(s"Read: $v")
    v = unfoldReader.read(-1)
  }

  println("\n=== Reader state ===")
  val stateReader = Reader.fromChunk(Chunk(5, 6))
  println(s"readable before: ${stateReader.readable()}")
  stateReader.read(-1)
  println(s"readable after one read: ${stateReader.readable()}")
  stateReader.read(-1)
  println(s"readable after exhaustion: ${stateReader.readable()}")
  println(s"isClosed: ${stateReader.isClosed}")
}
