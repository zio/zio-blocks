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
import zio.test._

object StreamChoiceSpec extends StreamsBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("Stream.choice (compile-time)")(
    test("rejects duplicate choice element types at compile time") {
      val errors = typeCheckErrors("Stream.succeed(1).choice(Stream.succeed(2))")

      assertTrue(
        errors.nonEmpty,
        errors.exists(_.message.contains("Union types must contain unique types"))
      )
    },
    test("rejects union-typed right-hand side at compile time") {
      val errors = typeCheckErrors(
        "(Stream.succeed(1): Stream[Nothing, Int]).choice(Stream.succeed(\"a\"): Stream[Nothing, String | Boolean])"
      )

      assertTrue(
        errors.exists(_.message.contains("must not be a union type"))
      )
    }
  )
}
