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

package zio.blocks.schema.structural.common
import zio.blocks.schema.SchemaBaseSpec

import zio.blocks.schema._
import zio.test._

/** Tests for single-field product types to structural conversion. */
object SingleFieldSpec extends SchemaBaseSpec {

  case class Id(value: String)
  case class Count(n: Int)

  def spec: Spec[Any, Nothing] = suite("SingleFieldSpec")(
    test("single field structural has one field") {
      val schema     = Schema.derived[Id]
      val structural = schema.structural
      val numFields  = (structural.reflect: @unchecked) match {
        case record: Reflect.Record[_, _] => record.fields.size
      }
      assertTrue(numFields == 1)
    },
    test("single field case class converts to expected structural type") {
      typeCheck("""
        import zio.blocks.schema._
        case class Id(value: String)
        val schema: Schema[Id] = Schema.derived[Id]
        val structural: Schema[{def value: String}] = schema.structural
      """).map(result => assertTrue(result.isRight))
    },
    test("single Int field case class converts to expected structural type") {
      typeCheck("""
        import zio.blocks.schema._
        case class Count(n: Int)
        val schema: Schema[Count] = Schema.derived[Count]
        val structural: Schema[{def n: Int}] = schema.structural
      """).map(result => assertTrue(result.isRight))
    }
  )
}
