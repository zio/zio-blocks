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

package zio.blocks.streams

import scala.compiletime.testing.typeCheckErrors
import zio.blocks.chunk.Chunk
import zio.blocks.combinators.Unions
import zio.test._

object StreamChoiceSpec extends StreamsBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("Stream.choice")(
    test("concatenates disjoint element types using direct unions") {
      val result = Stream.succeed("left").choice(Stream.succeed(1)).runCollect

      assertTrue(result == Right(Chunk[String | Int]("left", 1)))
    },
    test("preserves widened error channel") {
      val left: Stream[String, String] = Stream.fail("boom")
      val right                        = Stream.succeed(true)

      val result = left.choice(right).runCollect

      assertTrue(result == Left("boom"))
    },
    test("works with explicit Unions evidence for three-way left nesting") {
      val builder                                      = new StringBuilder("two")
      val inner: Stream[String, String | CharSequence] =
        Stream.succeed("one").choice(Stream.succeed(builder))
      val result = inner.choice(Stream.succeed(true)).runCollect

      assertTrue(result == Right(Chunk[String | CharSequence | Boolean]("one", builder, true)))
    },
    test("rejects duplicate choice element types at compile time") {
      val errors = typeCheckErrors("Stream.succeed(1).choice(Stream.succeed(2))")

      assertTrue(
        errors.length == 1,
        errors.head.message.contains("Union types must contain unique types")
      )
    }
  )
}
