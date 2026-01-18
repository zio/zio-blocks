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

object SIMDCorrectnessSpec extends ChunkBaseSpec {

  def spec = suite("SIMDCorrectnessSpec")(
    test("byteChecksum correctness") {
      check(genChunk(Gen.byte)) { chunk =>
        val actual   = chunk.byteChecksum
        val expected = chunk.toList.map(_ & 0xffL).sum
        assert(actual)(equalTo(expected))
      }
    },
    test("indexOf (ByteArray) correctness") {
      check(genChunk(Gen.byte), Gen.byte) { (chunk, target) =>
        val actual   = chunk.indexOf(target)
        val expected = chunk.toList.indexOf(target)
        assert(actual)(equalTo(expected))
      }
    },
    test("findFirstNot correctness") {
      check(genChunk(Gen.byte), Gen.byte) { (chunk, target) =>
        val byteArray = chunk match {
          case b: Chunk.ByteArray => b
          case _                  => Chunk.ByteArray(chunk.toArray, 0, chunk.length)
        }
        val actual   = byteArray.findFirstNot(target, 0)
        val expected = chunk.toList.indexWhere(_ != target)
        assert(actual)(equalTo(expected))
      }
    },
    test("matchAny correctness") {
      check(genChunk(Gen.byte), Gen.listOf(Gen.byte)) { (chunk, candidates) =>
        val byteArray = chunk match {
          case b: Chunk.ByteArray => b
          case _                  => Chunk.ByteArray(chunk.toArray, 0, chunk.length)
        }
        val candidatesArray = candidates.toArray
        val actual          = byteArray.matchAny(candidatesArray)
        val expected        = chunk.toList.exists(b => candidates.contains(b))
        assert(actual)(equalTo(expected))
      }
    }
  )
}
