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

/** Tests for tuple to structural type conversion. */
object TuplesSpec extends SchemaBaseSpec {

  def spec: Spec[Any, Nothing] = suite("TuplesSpec")(
    test("tuple3 has correct field names") {
      val schema     = Schema.derived[(String, Int, Boolean)]
      val structural = schema.structural
      val fieldNames = (structural.reflect: @unchecked) match {
        case record: Reflect.Record[_, _] => record.fields.map(_.name).toList
      }
      assertTrue(fieldNames == List("_1", "_2", "_3"))
    },
    test("tuple round-trip preserves data") {
      val tuple     = ("hello", 42)
      val schema    = Schema.derived[(String, Int)]
      val dynamic   = schema.toDynamicValue(tuple)
      val roundTrip = schema.fromDynamicValue(dynamic)
      assertTrue(roundTrip == Right(tuple))
    },
    test("tuple2 converts to expected structural type") {
      val schema     = Schema.derived[(String, Int)]
      val structural = schema.structural
      val fieldNames = (structural.reflect: @unchecked) match {
        case record: Reflect.Record[_, _] => record.fields.map(_.name).toList
      }
      assertTrue(fieldNames == List("_1", "_2"))
    },
    test("tuple3 converts to expected structural type") {
      val schema     = Schema.derived[(String, Int, Boolean)]
      val structural = schema.structural
      val fieldNames = (structural.reflect: @unchecked) match {
        case record: Reflect.Record[_, _] => record.fields.map(_.name).toList
      }
      assertTrue(fieldNames == List("_1", "_2", "_3"))
    }
  )
}
