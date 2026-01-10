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

  def spec = suite("ChunkBuilderSpec")(
    suite("Boolean")(
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
        val builder = new ChunkBuilder.Boolean
        assert(builder.toString)(equalTo("ChunkBuilder.Boolean"))
      }
    ),
    suite("Byte")(
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
        val builder = new ChunkBuilder.Byte
        assert(builder.toString)(equalTo("ChunkBuilder.Byte"))
      }
    ),
    suite("Char")(
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
        val builder = new ChunkBuilder.Char
        assert(builder.toString)(equalTo("ChunkBuilder.Char"))
      }
    ),
    suite("Double")(
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
        val builder = new ChunkBuilder.Double
        assert(builder.toString)(equalTo("ChunkBuilder.Double"))
      }
    ),
    suite("Float")(
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
        val builder = new ChunkBuilder.Float
        assert(builder.toString)(equalTo("ChunkBuilder.Float"))
      }
    ),
    suite("Int")(
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
        val builder = new ChunkBuilder.Int
        assert(builder.toString)(equalTo("ChunkBuilder.Int"))
      }
    ),
    suite("Long")(
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
        val builder = new ChunkBuilder.Long
        assert(builder.toString)(equalTo("ChunkBuilder.Long"))
      }
    ),
    suite("Short")(
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
        val builder = new ChunkBuilder.Short
        assert(builder.toString)(equalTo("ChunkBuilder.Short"))
      }
    )
  )
}
