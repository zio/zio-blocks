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

/** Tests for simple product type to structural conversion. */
object SimpleProductSpec extends SchemaBaseSpec {

  case class Person(name: String, age: Int)
  type PersonLike = { def name: String; def age: Int }

  def spec: Spec[Any, Nothing] = suite("SimpleProductSpec")(
    test("structural schema has correct field names") {
      val schema     = Schema.derived[Person]
      val structural = schema.structural
      val fieldNames = (structural.reflect: @unchecked) match {
        case record: Reflect.Record[_, _] => record.fields.map(_.name).toSet
      }
      assertTrue(fieldNames == Set("name", "age"))
    },
    test("case class converts to expected structural type") {
      typeCheck("""
        import zio.blocks.schema._
        case class Person(name: String, age: Int)
        val schema: Schema[Person] = Schema.derived[Person]
        val structural: Schema[{def age: Int; def name: String}] = schema.structural
      """).map(result => assertTrue(result.isRight))
    }
  )
}
