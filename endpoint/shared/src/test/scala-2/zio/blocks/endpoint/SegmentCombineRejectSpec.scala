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

package zio.blocks.endpoint

import zio.test._
import zio.test.Assertion.{isLeft, isRight}

object SegmentCombineRejectSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Nothing] = suite("SegmentCodec ~ compile-time ambiguity rejection (Scala 2.13)")(
    test("rejects combining two string segments") {
      assertZIO(
        typeCheck("""
          import zio.blocks.endpoint._

          val invalid = SegmentCodec.string("a") ~ SegmentCodec.string("b")
        """)
      )(isLeft)
    },
    test("rejects combining two int segments") {
      assertZIO(
        typeCheck("""
          import zio.blocks.endpoint._

          val invalid = SegmentCodec.int("a") ~ SegmentCodec.int("b")
        """)
      )(isLeft)
    },
    test("rejects combining int after long") {
      assertZIO(
        typeCheck("""
          import zio.blocks.endpoint._

          val invalid = SegmentCodec.long("a") ~ SegmentCodec.int("b")
        """)
      )(isLeft)
    },
    test("rejects combining long after int") {
      assertZIO(
        typeCheck("""
          import zio.blocks.endpoint._

          val invalid = SegmentCodec.int("a") ~ SegmentCodec.long("b")
        """)
      )(isLeft)
    },
    test("rejects combining two long segments") {
      assertZIO(
        typeCheck("""
          import zio.blocks.endpoint._

          val invalid = SegmentCodec.long("a") ~ SegmentCodec.long("b")
        """)
      )(isLeft)
    },
    test("rejects flattened numeric tails") {
      assertZIO(
        typeCheck("""
          import zio.blocks.endpoint._

          val invalid = SegmentCodec.uuid("id") ~ SegmentCodec.int("a") ~ SegmentCodec.int("b")
        """)
      )(isLeft)
    },
    test("rejects flattened string tails") {
      assertZIO(
        typeCheck("""
          import zio.blocks.endpoint._

          val invalid = SegmentCodec.int("a") ~ SegmentCodec.string("s") ~ SegmentCodec.string("t")
        """)
      )(isLeft)
    },
    test("allows unambiguous combinations") {
      assertZIO(
        typeCheck("""
          import zio.blocks.endpoint._

          val valid = SegmentCodec.literal("v") ~ SegmentCodec.int("major") ~ SegmentCodec.string("suffix")
        """)
      )(isRight)
    }
  )
}
