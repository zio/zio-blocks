/*
 * Copyright 2018-2024 John A. De Goes and the ZIO Contributors
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

object ChunkOptimizationSpec extends ChunkBaseSpec {

  def spec = suite("ChunkOptimizationSpec")(
    test("indexOf correctness (optimized)") {
      check(genChunk(Gen.byte), Gen.byte) { (chunk, target) =>
        val actual   = chunk.indexOf(target)
        val expected = chunk.toList.indexOf(target)
        assert(actual)(equalTo(expected))
      }
    },
    test("BitChunk AND correctness (optimized)") {
      check(genChunk(Gen.byte), genChunk(Gen.byte)) { (c1, c2) =>
        val len = c1.length min c2.length
        val b1  = c1.take(len).asBitsByte
        val b2  = c2.take(len).asBitsByte

        val actual   = (b1 & b2).toPackedByte
        val expected = c1.take(len).zip(c2.take(len)).map { case (a, b) => (a & b).toByte }

        assert(actual)(equalTo(expected))
      }
    },
    test("BitChunk OR correctness (optimized)") {
      check(genChunk(Gen.byte), genChunk(Gen.byte)) { (c1, c2) =>
        val len = c1.length min c2.length
        val b1  = c1.take(len).asBitsByte
        val b2  = c2.take(len).asBitsByte

        val actual   = (b1 | b2).toPackedByte
        val expected = c1.take(len).zip(c2.take(len)).map { case (a, b) => (a | b).toByte }

        assert(actual)(equalTo(expected))
      }
    },
    test("BitChunk XOR correctness (optimized)") {
      check(genChunk(Gen.byte), genChunk(Gen.byte)) { (c1, c2) =>
        val len = c1.length min c2.length
        val b1  = c1.take(len).asBitsByte
        val b2  = c2.take(len).asBitsByte

        val actual   = (b1 ^ b2).toPackedByte
        val expected = c1.take(len).zip(c2.take(len)).map { case (a, b) => (a ^ b).toByte }

        assert(actual)(equalTo(expected))
      }
    },
    test("BitChunk Negate correctness (optimized)") {
      check(genChunk(Gen.byte)) { c1 =>
        val b1       = c1.asBitsByte
        val actual   = b1.negate.toPackedByte
        val expected = c1.map(b => (~b).toByte)

        assert(actual)(equalTo(expected))
      }
    }
  )
}
