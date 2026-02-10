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

object NonEmptyChunkSpec extends ChunkBaseSpec {

  val genInt: Gen[Any, Int]                                         = Gen.int(-10, 10)
  val genChunkLocal: Gen[Any, Chunk[Int]]                           = genChunk(genInt)
  val genIntFunction: Gen[Any, Any => Int]                          = Gen.function(genInt)
  val genIntFunction2: Gen[Any, (Any, Any) => Int]                  = Gen.function2(genInt)
  val genNonEmptyChunkLocal: Gen[Any, NonEmptyChunk[Int]]           = genNonEmptyChunk(genInt)
  val genNonEmptyChunkFunction: Gen[Any, Any => NonEmptyChunk[Int]] = Gen.function(genNonEmptyChunkLocal)

  def spec = suite("NonEmptyChunkSpec")(
    test("+") {
      check(genNonEmptyChunkLocal, genInt)((as, a) => assert((as :+ a).toChunk)(equalTo(as.toChunk :+ a)))
    },
    suite("++")(
      test("Chunk with NonEmptyChunk") {
        check(genChunkLocal, genNonEmptyChunkLocal)((as, bs) => assert((as ++ bs).toChunk)(equalTo(as ++ bs.toChunk)))
      },
      test("NonEmptyChunk with Chunk") {
        check(genNonEmptyChunkLocal, genChunkLocal)((as, bs) => assert((as ++ bs).toChunk)(equalTo(as.toChunk ++ bs)))
      },
      test("NonEmptyChunk with NonEmptyChunk") {
        check(genNonEmptyChunkLocal, genNonEmptyChunkLocal) { (as, bs) =>
          assert((as ++ bs).toChunk)(equalTo(as.toChunk ++ bs.toChunk))
        }
      }
    ),
    test("flatMap") {
      check(genNonEmptyChunkLocal, genNonEmptyChunkFunction) { (as, f) =>
        assert(as.flatMap(f).toChunk)(equalTo(as.toChunk.flatMap(a => f(a).toChunk)))
      }
    },
    test("groupBy") {
      check(genNonEmptyChunkLocal, genIntFunction)((as, f) =>
        assert(as.groupBy(f).map { case (k, v) => (k, v.toChunk) })(equalTo(as.toChunk.groupBy(f)))
      )
    },
    test("groupMap") {
      check(genNonEmptyChunkLocal, genIntFunction, genIntFunction)((as, f, g) =>
        assert(as.groupMap(f)(g).map { case (k, v) => (k, v.toChunk) })(equalTo(as.toChunk.groupBy(f).map {
          case (k, v) =>
            (k, v.map(g))
        }))
      )
    },
    test("map") {
      check(genNonEmptyChunkLocal, genIntFunction)((as, f) => assert(as.map(f).toChunk)(equalTo(as.toChunk.map(f))))
    },
    test("materialize") {
      check(genNonEmptyChunkLocal)(as => assert(as.materialize)(equalTo(as)))
    },
    test("reduceMapLeft") {
      check(genNonEmptyChunkLocal, genIntFunction, genIntFunction2) { (as, map, reduce) =>
        val actual   = as.reduceMapLeft(map)(reduce)
        val expected = as.tail.foldLeft(map(as.head))(reduce)
        assert(actual)(equalTo(expected))
      }
    },
    test("reduceMapRight") {
      check(genNonEmptyChunkLocal, genIntFunction, genIntFunction2) { (as, map, reduce) =>
        val actual   = as.reduceMapRight(map)(reduce)
        val expected = as.init.foldRight(map(as.last))(reduce)
        assert(actual)(equalTo(expected))
      }
    },
    test("size") {
      check(genNonEmptyChunkLocal)(as => assert(as.size)(equalTo(as.toChunk.size)))
    },
    test("toArray") {
      check(genNonEmptyChunkLocal)(as => assert(as.toArray)(equalTo(as.toChunk.toArray)))
    },
    test("toChunk") {
      check(genNonEmptyChunkLocal)(as => assert(as.toChunk)(equalTo(Chunk.from(as))))
    },
    suite("unapplySeq")(
      test("matches a nonempty chunk") {
        val chunk  = Chunk(1, 2, 3)
        val actual = chunk match {
          case NonEmptyChunk(x, y, z) => Some((x, y, z))
          case _                      => None
        }
        val expected = Some((1, 2, 3))
        assert(actual)(equalTo(expected))
      },
      test("does not match an empty chunk") {
        val chunk  = Chunk.empty
        val actual = chunk match {
          case NonEmptyChunk(x, y, z) => Some((x, y, z))
          case _                      => None
        }
        val expected = None
        assert(actual)(equalTo(expected))
      },
      test("does not match another collection type") {
        val vector = Vector(1, 2, 3)
        val actual = vector match {
          case NonEmptyChunk(x, y, z) => Some((x, y, z))
          case _                      => None
        }
        val expected = None
        assert(actual)(equalTo(expected))
      },
      test("matches a NonEmptyChunk") {
        val nonEmptyChunk = NonEmptyChunk(1, 2, 3)
        val actual        = nonEmptyChunk match {
          case NonEmptyChunk(x, y, z) => Some((x, y, z))
          case _                      => None
        }
        val expected = Some((1, 2, 3))
        assert(actual)(equalTo(expected))
      }
    )
  )
}
