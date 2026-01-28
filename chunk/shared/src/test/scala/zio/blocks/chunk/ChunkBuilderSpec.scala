/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
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

import zio.test.Assertion._
import zio.test._

object ChunkBuilderSpec extends ChunkBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("ChunkBuilderSpec")(
    suite("AnyRef")(
      test("sizeHint") {
        val builder = ChunkBuilder.make[String]()
        builder.sizeHint(1)
        builder += "a"
        builder.sizeHint(5)
        assertTrue(builder.result() == Chunk("a"))
      },
      test("knownSize") {
        val builder    = ChunkBuilder.make[String]()
        val knownSize1 = builder.knownSize
        builder += "a"
        val knownSize2 = builder.knownSize
        assertTrue(
          knownSize1 == 0,
          knownSize2 == 1
        )
      },
      test("clear") {
        val builder    = ChunkBuilder.make[String]()
        val knownSize1 = builder.knownSize
        builder += "a"
        builder.clear()
        val knownSize2 = builder.knownSize
        assertTrue(
          knownSize1 == 0,
          knownSize2 == 0,
          builder.result() == Chunk.empty[String]
        )
      },
      test("addOne")(
        check(genChunk(Gen.string)) { as =>
          val builder = ChunkBuilder.make[String]()
          as.foreach(builder += _)
          assertTrue(builder.result() == as)
        }
      ),
      test("addAll") {
        check(genChunk(Gen.string)) { as =>
          val builder = ChunkBuilder.make[String]()
          builder ++= as
          assertTrue(builder.result() == as)
        }
      },
      test("toString") {
        assert(ChunkBuilder.make[String]().toString)(equalTo("ChunkBuilder"))
      }
    ),
    suite("Boolean")(
      test("sizeHint") {
        val builder = new ChunkBuilder.Boolean
        builder.sizeHint(1)
        builder += true
        builder.sizeHint(5)
        assertTrue(builder.result() == Chunk(true))
      },
      test("knownSize") {
        val builder    = new ChunkBuilder.Boolean
        val knownSize1 = builder.knownSize
        builder += true
        val knownSize2 = builder.knownSize
        assertTrue(
          knownSize1 == 0,
          knownSize2 == 1
        )
      },
      test("clear") {
        val builder    = new ChunkBuilder.Boolean
        val knownSize1 = builder.knownSize
        builder += true
        builder.clear()
        val knownSize2 = builder.knownSize
        assertTrue(
          knownSize1 == 0,
          knownSize2 == 0,
          builder.result() == Chunk.empty[Boolean]
        )
      },
      test("addOne")(
        check(genChunk(Gen.boolean)) { as =>
          val builder = new ChunkBuilder.Boolean
          as.foreach(builder += _)
          assertTrue(builder.result() == as)
        }
      ),
      test("addAll") {
        check(genChunk(Gen.boolean)) { as =>
          val builder = new ChunkBuilder.Boolean
          builder ++= as
          assertTrue(builder.result() == as)
        }
      },
      test("toString") {
        assert((new ChunkBuilder.Boolean).toString)(equalTo("ChunkBuilder.Boolean"))
      }
    ),
    suite("Byte")(
      test("sizeHint") {
        val builder = new ChunkBuilder.Byte
        builder.sizeHint(1)
        builder += 1.toByte
        builder.sizeHint(5)
        assertTrue(builder.result() == Chunk(1.toByte))
      },
      test("knownSize") {
        val builder    = new ChunkBuilder.Byte
        val knownSize1 = builder.knownSize
        builder += 1.toByte
        val knownSize2 = builder.knownSize
        assertTrue(
          knownSize1 == 0,
          knownSize2 == 1
        )
      },
      test("clear") {
        val builder    = new ChunkBuilder.Byte
        val knownSize1 = builder.knownSize
        builder += 1.toByte
        builder.clear()
        val knownSize2 = builder.knownSize
        assertTrue(
          knownSize1 == 0,
          knownSize2 == 0,
          builder.result() == Chunk.empty[Byte]
        )
      },
      test("addOne")(
        check(genChunk(Gen.byte)) { as =>
          val builder = new ChunkBuilder.Byte
          as.foreach(builder += _)
          assertTrue(builder.result() == as)
        }
      ),
      test("addAll") {
        check(genChunk(Gen.byte)) { as =>
          val builder = new ChunkBuilder.Byte
          builder ++= as
          assertTrue(builder.result() == as)
        }
      },
      test("toString") {
        assert((new ChunkBuilder.Byte).toString)(equalTo("ChunkBuilder.Byte"))
      }
    ),
    suite("Char")(
      test("sizeHint") {
        val builder = new ChunkBuilder.Char
        builder.sizeHint(1)
        builder += 1.toChar
        builder.sizeHint(5)
        assertTrue(builder.result() == Chunk(1.toChar))
      },
      test("knownSize") {
        val builder    = new ChunkBuilder.Char
        val knownSize1 = builder.knownSize
        builder += 1.toChar
        val knownSize2 = builder.knownSize
        assertTrue(
          knownSize1 == 0,
          knownSize2 == 1
        )
      },
      test("clear") {
        val builder    = new ChunkBuilder.Char
        val knownSize1 = builder.knownSize
        builder += 1.toChar
        builder.clear()
        val knownSize2 = builder.knownSize
        assertTrue(
          knownSize1 == 0,
          knownSize2 == 0,
          builder.result() == Chunk.empty[Char]
        )
      },
      test("addOne")(
        check(genChunk(Gen.char)) { as =>
          val builder = new ChunkBuilder.Char
          as.foreach(builder += _)
          assertTrue(builder.result() == as)
        }
      ),
      test("addAll") {
        check(genChunk(Gen.char)) { as =>
          val builder = new ChunkBuilder.Char
          builder ++= as
          assertTrue(builder.result() == as)
        }
      },
      test("toString") {
        assert((new ChunkBuilder.Char).toString)(equalTo("ChunkBuilder.Char"))
      }
    ),
    suite("Double")(
      test("sizeHint") {
        val builder = new ChunkBuilder.Double
        builder.sizeHint(1)
        builder += 1.toDouble
        builder.sizeHint(5)
        assertTrue(builder.result() == Chunk(1.toDouble))
      },
      test("knownSize") {
        val builder    = new ChunkBuilder.Double
        val knownSize1 = builder.knownSize
        builder += 1.toDouble
        val knownSize2 = builder.knownSize
        assertTrue(
          knownSize1 == 0,
          knownSize2 == 1
        )
      },
      test("clear") {
        val builder    = new ChunkBuilder.Double
        val knownSize1 = builder.knownSize
        builder += 1.toDouble
        builder.clear()
        val knownSize2 = builder.knownSize
        assertTrue(
          knownSize1 == 0,
          knownSize2 == 0,
          builder.result() == Chunk.empty[Double]
        )
      },
      test("addOne")(
        check(genChunk(Gen.double)) { as =>
          val builder = new ChunkBuilder.Double
          as.foreach(builder += _)
          assertTrue(builder.result() == as)
        }
      ),
      test("addAll") {
        check(genChunk(Gen.double)) { as =>
          val builder = new ChunkBuilder.Double
          builder ++= as
          assertTrue(builder.result() == as)
        }
      },
      test("toString") {
        assert((new ChunkBuilder.Double).toString)(equalTo("ChunkBuilder.Double"))
      }
    ),
    suite("Float")(
      test("sizeHint") {
        val builder = new ChunkBuilder.Float
        builder.sizeHint(1)
        builder += 1.toFloat
        builder.sizeHint(5)
        assertTrue(builder.result() == Chunk(1.toFloat))
      },
      test("knownSize") {
        val builder    = new ChunkBuilder.Float
        val knownSize1 = builder.knownSize
        builder += 1.toFloat
        val knownSize2 = builder.knownSize
        assertTrue(
          knownSize1 == 0,
          knownSize2 == 1
        )
      },
      test("clear") {
        val builder    = new ChunkBuilder.Float
        val knownSize1 = builder.knownSize
        builder += 1.toFloat
        builder.clear()
        val knownSize2 = builder.knownSize
        assertTrue(
          knownSize1 == 0,
          knownSize2 == 0,
          builder.result() == Chunk.empty[Float]
        )
      },
      test("addOne")(
        check(genChunk(Gen.float)) { as =>
          val builder = new ChunkBuilder.Float
          as.foreach(builder += _)
          assertTrue(builder.result() == as)
        }
      ),
      test("addAll") {
        check(genChunk(Gen.float)) { as =>
          val builder = new ChunkBuilder.Float
          builder ++= as
          assertTrue(builder.result() == as)
        }
      },
      test("toString") {
        assert((new ChunkBuilder.Float).toString)(equalTo("ChunkBuilder.Float"))
      }
    ),
    suite("Int")(
      test("sizeHint") {
        val builder = new ChunkBuilder.Int
        builder.sizeHint(1)
        builder += 1
        builder.sizeHint(5)
        assertTrue(builder.result() == Chunk(1))
      },
      test("knownSize") {
        val builder    = new ChunkBuilder.Int
        val knownSize1 = builder.knownSize
        builder += 1
        val knownSize2 = builder.knownSize
        assertTrue(
          knownSize1 == 0,
          knownSize2 == 1
        )
      },
      test("clear") {
        val builder    = new ChunkBuilder.Int
        val knownSize1 = builder.knownSize
        builder += 1
        builder.clear()
        val knownSize2 = builder.knownSize
        assertTrue(
          knownSize1 == 0,
          knownSize2 == 0,
          builder.result() == Chunk.empty[Int]
        )
      },
      test("addOne")(
        check(genChunk(Gen.int)) { as =>
          val builder = new ChunkBuilder.Int
          as.foreach(builder += _)
          assertTrue(builder.result() == as)
        }
      ),
      test("addAll") {
        check(genChunk(Gen.int)) { as =>
          val builder = new ChunkBuilder.Int
          builder ++= as
          assertTrue(builder.result() == as)
        }
      },
      test("toString") {
        assert((new ChunkBuilder.Int).toString)(equalTo("ChunkBuilder.Int"))
      }
    ),
    suite("Long")(
      test("sizeHint") {
        val builder = new ChunkBuilder.Long
        builder.sizeHint(1)
        builder += 1.toLong
        builder.sizeHint(5)
        assertTrue(builder.result() == Chunk(1.toLong))
      },
      test("knownSize") {
        val builder    = new ChunkBuilder.Long
        val knownSize1 = builder.knownSize
        builder += 1.toLong
        val knownSize2 = builder.knownSize
        assertTrue(
          knownSize1 == 0,
          knownSize2 == 1
        )
      },
      test("clear") {
        val builder    = new ChunkBuilder.Long
        val knownSize1 = builder.knownSize
        builder += 1.toLong
        builder.clear()
        val knownSize2 = builder.knownSize
        assertTrue(
          knownSize1 == 0,
          knownSize2 == 0,
          builder.result() == Chunk.empty[Long]
        )
      },
      test("addOne")(
        check(genChunk(Gen.long)) { as =>
          val builder = new ChunkBuilder.Long
          as.foreach(builder += _)
          assertTrue(builder.result() == as)
        }
      ),
      test("addAll") {
        check(genChunk(Gen.long)) { as =>
          val builder = new ChunkBuilder.Long
          builder ++= as
          assertTrue(builder.result() == as)
        }
      },
      test("toString") {
        assert((new ChunkBuilder.Long).toString)(equalTo("ChunkBuilder.Long"))
      }
    ),
    suite("Short")(
      test("sizeHint") {
        val builder = new ChunkBuilder.Short
        builder.sizeHint(1)
        builder += 1.toShort
        builder.sizeHint(5)
        assertTrue(builder.result() == Chunk(1.toShort))
      },
      test("knownSize") {
        val builder    = new ChunkBuilder.Short
        val knownSize1 = builder.knownSize
        builder += 1.toShort
        val knownSize2 = builder.knownSize
        assertTrue(
          knownSize1 == 0,
          knownSize2 == 1
        )
      },
      test("clear") {
        val builder    = new ChunkBuilder.Short
        val knownSize1 = builder.knownSize
        builder += 1.toShort
        builder.clear()
        val knownSize2 = builder.knownSize
        assertTrue(
          knownSize1 == 0,
          knownSize2 == 0,
          builder.result() == Chunk.empty[Short]
        )
      },
      test("addOne")(
        check(genChunk(Gen.short)) { as =>
          val builder = new ChunkBuilder.Short
          as.foreach(builder += _)
          assertTrue(builder.result() == as)
        }
      ),
      test("addAll") {
        check(genChunk(Gen.short)) { as =>
          val builder = new ChunkBuilder.Short
          builder ++= as
          assertTrue(builder.result() == as)
        }
      },
      test("toString") {
        assert((new ChunkBuilder.Short).toString)(equalTo("ChunkBuilder.Short"))
      }
    )
  )
}
