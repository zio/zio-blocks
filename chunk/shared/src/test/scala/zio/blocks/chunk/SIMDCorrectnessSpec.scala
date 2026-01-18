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
