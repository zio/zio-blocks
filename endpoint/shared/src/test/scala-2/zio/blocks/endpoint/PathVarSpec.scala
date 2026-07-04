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
import zio.test.Assertion.isLeft

object PathVarSpec extends ZIOSpecDefault {

  def spec: Spec[Any, Nothing] = suite("PathVar (Scala 2.13)")(
    test("SegmentCodec.int(\"id\").PathVars =:= Tuple1[PathVar[\"id\", Int]]") {
      val seg = SegmentCodec.int("id")
      implicitly[seg.PathVars =:= Tuple1[PathVar["id", Int]]]
      assertCompletes
    },
    test("SegmentCodec.int(\"id\").PathVars is NOT Tuple1[PathVar[\"wrong\", Int]]") {
      assertZIO(
        typeCheck("""
          import zio.blocks.endpoint._

          val seg = SegmentCodec.int("id")
          implicitly[seg.PathVars =:= Tuple1[PathVar["wrong", Int]]]
        """)
      )(isLeft)
    },
    test("SegmentCodec.int(\"id\").unused.PathVars =:= Tuple1[PathVar.Ignored[\"id\", Int]]") {
      val seg = SegmentCodec.int("id").unused
      implicitly[seg.PathVars =:= Tuple1[PathVar.Ignored["id", Int]]]
      assertCompletes
    },
    test("SegmentCodec.int(\"id\").unused.PathVars is NOT Tuple1[PathVar[\"id\", Int]]") {
      assertZIO(
        typeCheck("""
          import zio.blocks.endpoint._

          val seg = SegmentCodec.int("id").unused
          implicitly[seg.PathVars =:= Tuple1[PathVar["id", Int]]]
        """)
      )(isLeft)
    }
  )
}
