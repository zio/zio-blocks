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

package zio.blocks.chunk

import zio.test._

object ChunkBuilderVersionSpecific extends ChunkBaseSpec {

  def spec = suite("ChunkBuilderVersionSpecific")(
    suite("Boolean")(
      test("knownSize")(
        check(Gen.chunkOf(Gen.boolean)) { zioChunk =>
          val as      = Chunk.fromIterable(zioChunk)
          val builder = new ChunkBuilder.Boolean
          as.foreach(builder += _)
          assertTrue(builder.knownSize == as.size)
        }
      )
    ),
    suite("Byte")(
      test("knownSize")(
        check(Gen.chunkOf(Gen.byte)) { zioChunk =>
          val as      = Chunk.fromIterable(zioChunk)
          val builder = new ChunkBuilder.Byte
          as.foreach(builder += _)
          assertTrue(builder.knownSize == as.size)
        }
      )
    ),
    suite("Char")(
      test("knownSize")(
        check(Gen.chunkOf(Gen.char)) { zioChunk =>
          val as      = Chunk.fromIterable(zioChunk)
          val builder = new ChunkBuilder.Char
          as.foreach(builder += _)
          assertTrue(builder.knownSize == as.size)
        }
      )
    ),
    suite("Double")(
      test("knownSize")(
        check(Gen.chunkOf(Gen.double)) { zioChunk =>
          val as      = Chunk.fromIterable(zioChunk)
          val builder = new ChunkBuilder.Double
          as.foreach(builder += _)
          assertTrue(builder.knownSize == as.size)
        }
      )
    ),
    suite("Float")(
      test("knownSize")(
        check(Gen.chunkOf(Gen.float)) { zioChunk =>
          val as      = Chunk.fromIterable(zioChunk)
          val builder = new ChunkBuilder.Float
          as.foreach(builder += _)
          assertTrue(builder.knownSize == as.size)
        }
      )
    ),
    suite("Int")(
      test("knownSize")(
        check(Gen.chunkOf(Gen.int)) { zioChunk =>
          val as      = Chunk.fromIterable(zioChunk)
          val builder = new ChunkBuilder.Int
          as.foreach(builder += _)
          assertTrue(builder.knownSize == as.size)
        }
      )
    ),
    suite("Long")(
      test("knownSize")(
        check(Gen.chunkOf(Gen.long)) { zioChunk =>
          val as      = Chunk.fromIterable(zioChunk)
          val builder = new ChunkBuilder.Long
          as.foreach(builder += _)
          assertTrue(builder.knownSize == as.size)
        }
      )
    ),
    suite("Short")(
      test("knownSize")(
        check(Gen.chunkOf(Gen.short)) { zioChunk =>
          val as      = Chunk.fromIterable(zioChunk)
          val builder = new ChunkBuilder.Short
          as.foreach(builder += _)
          assertTrue(builder.knownSize == as.size)
        }
      )
    )
  )
}
