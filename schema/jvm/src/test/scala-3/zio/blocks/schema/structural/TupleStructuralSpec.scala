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
 * Tests for tuple to structural type conversion (JVM only).
 */
object TupleStructuralSpec extends SchemaBaseSpec {

  def spec = suite("TupleStructuralSpec")(
    suite("Tuple2 structural conversion")(
      test("Tuple2 round-trips correctly") {
        val schema   = Schema.derived[(String, Int)]
        val original = ("hello", 42)

        val dynamic   = schema.toDynamicValue(original)
        val roundTrip = schema.fromDynamicValue(dynamic)

        assertTrue(roundTrip == Right(original))
      }
    ),
    suite("Tuple structural schema is a Record")(
      test("Tuple2 structural schema is a Record") {
        val schema     = Schema.derived[(String, Int)]
        val structural = schema.structural
        val isRecord   = (structural.reflect: @unchecked) match {
          case _: Reflect.Record[_, _] => true
        }
        assertTrue(isRecord)
      },
      test("Tuple3 structural schema has correct field count") {
        val schema     = Schema.derived[(String, Int, Boolean)]
        val structural = schema.structural
        val fieldCount = (structural.reflect: @unchecked) match {
          case r: Reflect.Record[_, _] => r.fields.size
        }
        assertTrue(fieldCount == 3)
      }
    ),
    suite("Type-level structural conversion")(
      test("Tuple2 converts to expected structural type") {
        val schema     = Schema.derived[(String, Int)]
        val structural = schema.structural
        val fieldNames = (structural.reflect: @unchecked) match {
          case record: Reflect.Record[_, _] => record.fields.map(_.name).toList
        }
        assertTrue(fieldNames == List("_1", "_2"))
      },
      test("Tuple3 converts to expected structural type") {
        val schema     = Schema.derived[(String, Int, Boolean)]
        val structural = schema.structural
        val fieldNames = (structural.reflect: @unchecked) match {
          case record: Reflect.Record[_, _] => record.fields.map(_.name).toList
        }
        assertTrue(fieldNames == List("_1", "_2", "_3"))
      }
    )
  )
}
