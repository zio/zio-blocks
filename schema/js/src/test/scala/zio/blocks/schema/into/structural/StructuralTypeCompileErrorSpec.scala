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

package zio.blocks.schema.into.structural

import zio.blocks.schema._
import zio.test._

/**
 * Tests for structural type conversions on Scala.js.
 *
 *   - Structural → Product: Requires reflection, fails at compile time on JS
 */
object StructuralTypeCompileErrorSpec extends SchemaBaseSpec {

  def spec = suite("StructuralTypeCompileErrorSpec")(
    suite("Structural to Product - Compile Error on JS")(
      test("structural type to case class fails to compile") {
        typeCheck {
          """
          import zio.blocks.schema.Into

          type PointLike = { def x: Int; def y: Int }
          case class Point(x: Int, y: Int)

          Into.derived[PointLike, Point]
          """
        }.map { result =>
          assertTrue(
            result.isLeft,
            // Structural type conversions require reflection, not supported on JS
            result.swap.exists(_.toLowerCase.contains("structural type conversions are not supported on js"))
          )
        }
      },
      test("structural type with multiple fields fails to compile") {
        typeCheck {
          """
          import zio.blocks.schema.Into

          type PersonLike = { def name: String; def age: Int; def active: Boolean }
          case class Person(name: String, age: Int, active: Boolean)

          Into.derived[PersonLike, Person]
          """
        }.map { result =>
          assertTrue(
            result.isLeft,
            result.swap.exists(_.toLowerCase.contains("structural type conversions are not supported on js"))
          )
        }
      }
    )
  )
}
