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

/** Tests for Into integration with structural types. */
object IntoIntegrationSpec extends SchemaBaseSpec {

  case class Person(name: String, age: Int)
  type PersonStructure = { def name: String; def age: Int }

  def spec: Spec[Any, Nothing] = suite("IntoIntegrationSpec")(
    test("nominal to structural via Into") {
      val person = Person("Alice", 30)
      val into   = Into.derived[Person, PersonStructure]
      val result = into.into(person)
      assertTrue(result.isRight)
    },
    test("nominal to structural preserves data") {
      val person = Person("Bob", 25)
      val into   = Into.derived[Person, PersonStructure]
      val result = into.into(person)

      result match {
        case Right(r) =>
          val nameMethod = r.getClass.getMethod("name")
          val ageMethod  = r.getClass.getMethod("age")
          assertTrue(nameMethod.invoke(r) == "Bob", ageMethod.invoke(r) == 25)
        case Left(err) =>
          assertTrue(false) ?? s"Conversion failed: $err"
      }
    }
  )
}
