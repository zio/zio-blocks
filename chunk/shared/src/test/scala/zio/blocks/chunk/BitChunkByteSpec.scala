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

object BitChunkByteSpec extends ChunkBaseSpec {

  val genByteChunk: Gen[Any, Chunk[Byte]] =
    for {
      bytes <- Gen.listOf(Gen.byte)
    } yield Chunk.fromIterable(bytes)

  val genInt: Gen[Any, Int] =
    Gen.small(Gen.const(_))

  val genBitChunk: Gen[Any, Chunk.BitChunkByte] =
    for {
      chunk <- genByteChunk
      i     <- Gen.int(0, chunk.length * 8)
      j     <- Gen.int(0, chunk.length * 8)
    } yield Chunk.BitChunkByte(chunk, i min j, i max j)

  val genBoolChunk: Gen[Any, Chunk[Boolean]] =
    Gen.listOf(Gen.boolean).map(Chunk.fromIterable(_))

  def toBinaryString(byte: Byte): String =
    String.format("%8s", (byte.toInt & 0xff).toBinaryString).replace(' ', '0')

  def spec: Spec[Any, Any] = suite("BitChunkByteSpec")(
    test("drop") {
      check(genByteChunk, genInt) { (bytes, n) =>
        val actual   = bytes.asBitsByte.drop(n).toBinaryString
        val expected = bytes.map(toBinaryString).mkString.drop(n)
        assert(actual)(equalTo(expected))
      }
    },
    test("drop and then drop") {
      check(genByteChunk, genInt, genInt) { (bytes, n, m) =>
        val actual   = bytes.asBitsByte.drop(n).drop(m).toBinaryString
        val expected = bytes.map(toBinaryString).mkString.drop(n).drop(m)
        assert(actual)(equalTo(expected))
      }
    },
    test("drop and then take") {
      check(genByteChunk, genInt, genInt) { (bytes, n, m) =>
        val actual   = bytes.asBitsByte.drop(n).take(m).toBinaryString
        val expected = bytes.map(toBinaryString).mkString.drop(n).take(m)
        assert(actual)(equalTo(expected))
      }
    },
    test("take") {
      check(genByteChunk, genInt) { (bytes, n) =>
        val actual   = bytes.asBitsByte.take(n).toBinaryString
        val expected = bytes.map(toBinaryString).mkString.take(n)
        assert(actual)(equalTo(expected))
      }
    },
    test("take and then drop") {
      check(genByteChunk, genInt, genInt) { (bytes, n, m) =>
        val actual   = bytes.asBitsByte.take(n).drop(m).toBinaryString
        val expected = bytes.map(toBinaryString).mkString.take(n).drop(m)
        assert(actual)(equalTo(expected))
      }
    },
    test("take and then take") {
      check(genByteChunk, genInt, genInt) { (bytes, n, m) =>
        val actual   = bytes.asBitsByte.take(n).take(m).toBinaryString
        val expected = bytes.map(toBinaryString).mkString.take(n).take(m)
        assert(actual)(equalTo(expected))
      }
    },
    test("slice") {
      check(genByteChunk, genInt, genInt) { (bytes, n, m) =>
        val actual   = bytes.asBitsByte.slice(n, m).toBinaryString
        val expected = bytes.map(toBinaryString).mkString.slice(n, m)
        assert(actual)(equalTo(expected))
      }
    },
    test("toBinaryString") {
      check(genByteChunk) { bytes =>
        val actual   = bytes.asBitsByte.toBinaryString
        val expected = bytes.map(toBinaryString).mkString
        assert(actual)(equalTo(expected))
      }
    },
    test("and") {
      check(genBitChunk, genBitChunk) { (l, r) =>
        val anded    = l & r
        val actual   = anded.toBinaryString.take(anded.length)
        val expected =
          l.bytes
            .map(toBinaryString)
            .mkString
            .slice(l.minBitIndex, l.maxBitIndex)
            .zip(
              r.bytes.map(toBinaryString).mkString.slice(r.minBitIndex, r.maxBitIndex)
            )
            .map {
              case ('0', '0') => '0'
              case ('0', '1') => '0'
              case ('1', '0') => '0'
              case ('1', '1') => '1'
              case _          => ""
            }
            .mkString

        assert(actual)(equalTo(expected))
      }
    },
    test("or") {
      check(genBitChunk, genBitChunk) { (l, r) =>
        val ored     = l | r
        val actual   = ored.toBinaryString.take(ored.length)
        val expected =
          l.bytes
            .map(toBinaryString)
            .mkString
            .slice(l.minBitIndex, l.maxBitIndex)
            .zip(
              r.bytes.map(toBinaryString).mkString.slice(r.minBitIndex, r.maxBitIndex)
            )
            .map {
              case ('0', '0') => '0'
              case ('0', '1') => '1'
              case ('1', '0') => '1'
              case ('1', '1') => '1'
              case _          => ""
            }
            .mkString

        assert(actual)(equalTo(expected))
      }
    },
    test("xor") {
      check(genBitChunk, genBitChunk) { (l, r) =>
        val xored    = l ^ r
        val actual   = xored.toBinaryString.take(xored.length)
        val expected =
          l.bytes
            .map(toBinaryString)
            .mkString
            .slice(l.minBitIndex, l.maxBitIndex)
            .zip(
              r.bytes.map(toBinaryString).mkString.slice(r.minBitIndex, r.maxBitIndex)
            )
            .map {
              case ('0', '0') => '0'
              case ('0', '1') => '1'
              case ('1', '0') => '1'
              case ('1', '1') => '0'
              case _          => ""
            }
            .mkString

        assert(actual)(equalTo(expected))
      }
    },
    test("not") {
      check(genBitChunk) { bits =>
        val not      = bits.negate
        val actual   = not.toBinaryString.take(not.length)
        val expected =
          bits.bytes
            .map(toBinaryString)
            .mkString
            .slice(bits.minBitIndex, bits.maxBitIndex)
            .map {
              case '0' => '1'
              case '1' => '0'
              case _   => ""
            }
            .mkString

        assert(actual)(equalTo(expected))
      }
    },
    test("boolean and") {
      check(genBoolChunk, genBoolChunk) { (l, r) =>
        val anded    = l & r
        val actual   = anded.toBinaryString.take(anded.length)
        val expected = l
          .zipWith(r)(_ & _)
          .map {
            case true  => '1'
            case false => '0'
          }
          .mkString
        assert(actual)(equalTo(expected))
      }
    },
    test("boolean or") {
      check(genBoolChunk, genBoolChunk) { (l, r) =>
        val ored     = l | r
        val actual   = ored.toBinaryString.take(ored.length)
        val expected = l
          .zipWith(r)(_ | _)
          .map {
            case true  => '1'
            case false => '0'
          }
          .mkString
        assert(actual)(equalTo(expected))
      }
    },
    test("boolean xor") {
      check(genBoolChunk, genBoolChunk) { (l, r) =>
        val xored    = l ^ r
        val actual   = xored.toBinaryString.take(xored.length)
        val expected = l
          .zipWith(r)(_ ^ _)
          .map {
            case true  => '1'
            case false => '0'
          }
          .mkString
        assert(actual)(equalTo(expected))
      }
    },
    test("boolean not") {
      check(genBoolChunk) { bits =>
        val not      = bits.negate
        val actual   = not.toBinaryString.take(not.length)
        val expected = bits.map {
          case true  => '0'
          case false => '1'
        }.mkString
        assert(actual)(equalTo(expected))
      }
    }
  )
}
