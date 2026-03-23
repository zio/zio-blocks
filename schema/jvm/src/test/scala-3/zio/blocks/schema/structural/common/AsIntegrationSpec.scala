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

/** Tests for As integration with structural types. */
object AsIntegrationSpec extends SchemaBaseSpec {

  case class Person(name: String, age: Int)
  type PersonStructure = { def name: String; def age: Int }

  def spec: Spec[Any, Nothing] = suite("AsIntegrationSpec")(
    test("Into from Person to structural works") {
      val person = Person("Alice", 30)
      val into   = Into.derived[Person, PersonStructure]
      val result = into.into(person)
      assertTrue(result.isRight)
    },
    test("structural schema round-trip via DynamicValue") {
      val person    = Person("Test", 42)
      val schema    = Schema.derived[Person]
      val dynamic   = schema.toDynamicValue(person)
      val roundTrip = schema.fromDynamicValue(dynamic)
      assertTrue(roundTrip == Right(person))
    }
  )
}
