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

package zio.blocks.schema.structural
import zio.blocks.schema.SchemaBaseSpec

import zio.blocks.schema._
import zio.test._

/**
 * Tests for Scala 3 enum structural conversion (JVM only, requires
 * ToStructural).
 */
object EnumStructuralSpec extends SchemaBaseSpec {

  enum Color {
    case Red, Green, Blue
  }

  enum Status {
    case Active, Inactive, Suspended
  }

  enum Shape {
    case Circle(radius: Double)
    case Rectangle(width: Double, height: Double)
    case Triangle(base: Double, height: Double)
  }

  def spec = suite("EnumStructuralSpec")(
    suite("Structural Conversion")(
      test("simple enum converts to structural union type") {
        typeCheck("""
          import zio.blocks.schema._
          enum Color { case Red, Green, Blue }
          val schema: Schema[Color] = Schema.derived[Color]
          val structural: Schema[{def Blue: {}} | {def Green: {}} | {def Red: {}}] = schema.structural
        """).map(result => assertTrue(result.isRight))
      },
      test("parameterized enum converts to structural union type") {
        typeCheck("""
          import zio.blocks.schema._
          enum Shape {
            case Circle(radius: Double)
            case Rectangle(width: Double, height: Double)
            case Triangle(base: Double, height: Double)
          }
          val schema: Schema[Shape] = Schema.derived[Shape]
          val structural: Schema[
            {def Circle: {def radius: Double}} |
            {def Rectangle: {def height: Double; def width: Double}} |
            {def Triangle: {def base: Double; def height: Double}}
          ] = schema.structural
        """).map(result => assertTrue(result.isRight))
      },
      test("structural enum schema is still a Variant") {
        val schema     = Schema.derived[Status]
        val structural = schema.structural
        val isVariant  = (structural.reflect: @unchecked) match {
          case _: Reflect.Variant[_, _] => true
        }
        assertTrue(isVariant)
      },
      test("structural enum preserves case count") {
        val schema     = Schema.derived[Status]
        val structural = schema.structural
        val caseCount  = (structural.reflect: @unchecked) match {
          case v: Reflect.Variant[_, _] => v.cases.size
        }
        assertTrue(caseCount == 3)
      }
    )
  )
}
