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

/** Tests for empty product types to structural conversion. */
object EmptyProductSpec extends SchemaBaseSpec {

  case class Empty()
  case object Singleton

  def spec: Spec[Any, Nothing] = suite("EmptyProductSpec")(
    test("empty structural has zero fields") {
      val schema     = Schema.derived[Empty]
      val structural = schema.structural
      val numFields  = structural.reflect match {
        case record: Reflect.Record[_, _] => record.fields.size
        case _                            => -1
      }
      assertTrue(numFields == 0)
    },
    test("empty case class converts to expected structural type") {
      typeCheck("""
        import zio.blocks.schema._
        case class Empty()
        val schema: Schema[Empty] = Schema.derived[Empty]
        val structural: Schema[{}] = schema.structural
      """).map(result => assertTrue(result.isRight))
    },
    test("case object converts to expected structural type") {
      typeCheck("""
        import zio.blocks.schema._
        case object Singleton
        val schema: Schema[Singleton.type] = Schema.derived[Singleton.type]
        val structural: Schema[{}] = schema.structural
      """).map(result => assertTrue(result.isRight))
    }
  )
}
