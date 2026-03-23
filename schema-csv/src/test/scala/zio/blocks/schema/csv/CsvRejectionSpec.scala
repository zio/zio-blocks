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

package zio.blocks.schema.csv

import zio.blocks.schema._
import zio.test._

object CsvRejectionSpec extends SchemaBaseSpec {

  sealed trait Shape
  case class Circle(radius: Double)                   extends Shape
  case class Rectangle(width: Double, height: Double) extends Shape

  object Shape {
    implicit val schema: Schema[Shape] = Schema.derived
  }

  def spec = suite("CsvRejectionSpec")(
    test("variant types throw UnsupportedOperationException") {
      val result =
        try {
          Schema[Shape].derive(CsvFormat)
          false
        } catch {
          case e: UnsupportedOperationException =>
            e.getMessage.contains("variant/sum types")
        }
      assertTrue(result)
    },
    test("sequence fields throw UnsupportedOperationException") {
      val result =
        try {
          Schema[List[Int]].derive(CsvFormat)
          false
        } catch {
          case e: UnsupportedOperationException =>
            e.getMessage.contains("sequence")
        }
      assertTrue(result)
    },
    test("map fields throw UnsupportedOperationException") {
      val result =
        try {
          Schema[Map[String, Int]].derive(CsvFormat)
          false
        } catch {
          case e: UnsupportedOperationException =>
            e.getMessage.contains("map")
        }
      assertTrue(result)
    }
  )
}
