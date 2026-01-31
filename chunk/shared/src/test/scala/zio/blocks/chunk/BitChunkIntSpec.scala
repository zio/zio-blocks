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

object BitChunkIntSpec extends ChunkBaseSpec {

  val genIntChunk: Gen[Any, Chunk[Int]] =
    for {
      ints <- Gen.listOf(Gen.int)
    } yield Chunk.fromIterable(ints)

  val genInt: Gen[Any, Int] =
    Gen.small(Gen.const(_))

  val genEndianness: Gen[Any, Chunk.BitChunk.Endianness] =
    Gen.elements(Chunk.BitChunk.Endianness.BigEndian, Chunk.BitChunk.Endianness.LittleEndian)

  def toBinaryString(endianness: Chunk.BitChunk.Endianness)(int: Int): String = {
    val endiannessLong =
      if (endianness == Chunk.BitChunk.Endianness.BigEndian) int else java.lang.Integer.reverse(int)
    String.format("%32s", endiannessLong.toBinaryString).replace(' ', '0')
  }

  def spec = suite("BitChunkIntSpec")(
    test("foreach") {
      check(genIntChunk, genEndianness) { (ints, endianness) =>
        var sum = 0
        ints.asBitsInt(endianness).foreach(x => if (x) sum += 1)
        assert(sum)(equalTo(ints.foldLeft(0)((acc, i) => acc + java.lang.Integer.bitCount(i))))
      }
    },
    test("drop") {
      check(genIntChunk, genInt, genEndianness) { (ints, n, endianness) =>
        val actual   = ints.asBitsInt(endianness).drop(n).toBinaryString
        val expected = ints.map(toBinaryString(endianness)).mkString.drop(n)
        assert(actual)(equalTo(expected))
      }
    },
    test("drop and then drop") {
      check(genIntChunk, genInt, genInt, genEndianness) { (ints, n, m, endianness) =>
        val actual   = ints.asBitsInt(endianness).drop(n).drop(m).toBinaryString
        val expected = ints.map(toBinaryString(endianness)).mkString.drop(n).drop(m)
        assert(actual)(equalTo(expected))
      }
    },
    test("drop and then take") {
      check(genIntChunk, genInt, genInt, genEndianness) { (ints, n, m, endianness) =>
        val actual   = ints.asBitsInt(endianness).drop(n).take(m).toBinaryString
        val expected = ints.map(toBinaryString(endianness)).mkString.drop(n).take(m)
        assert(actual)(equalTo(expected))
      }
    },
    test("take") {
      check(genIntChunk, genInt, genEndianness) { (ints, n, endianness) =>
        val actual   = ints.asBitsInt(endianness).take(n).toBinaryString
        val expected = ints.map(toBinaryString(endianness)).mkString.take(n)
        assert(actual)(equalTo(expected))
      }
    },
    test("take and then drop") {
      check(genIntChunk, genInt, genInt, genEndianness) { (ints, n, m, endianness) =>
        val actual   = ints.asBitsInt(endianness).take(n).drop(m).toBinaryString
        val expected = ints.map(toBinaryString(endianness)).mkString.take(n).drop(m)
        assert(actual)(equalTo(expected))
      }
    },
    test("take and then take") {
      check(genIntChunk, genInt, genInt, genEndianness) { (ints, n, m, endianness) =>
        val actual   = ints.asBitsInt(endianness).take(n).take(m).toBinaryString
        val expected = ints.map(toBinaryString(endianness)).mkString.take(n).take(m)
        assert(actual)(equalTo(expected))
      }
    },
    test("slice") {
      check(genIntChunk, genInt, genInt, genEndianness) { (bytes, n, m, endianness) =>
        val actual   = bytes.asBitsInt(endianness).slice(n, m).toBinaryString
        val expected = bytes.map(toBinaryString(endianness)).mkString.slice(n, m)
        assert(actual)(equalTo(expected))
      }
    },
    test("toBinaryString") {
      check(genIntChunk, genEndianness) { (ints, endianness) =>
        val actual   = ints.asBitsInt(endianness).toBinaryString
        val expected = ints.map(toBinaryString(endianness)).mkString
        assert(actual)(equalTo(expected))
      }
    }
  )

}
