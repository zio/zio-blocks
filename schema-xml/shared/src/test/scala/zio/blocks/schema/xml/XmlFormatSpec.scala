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

package zio.blocks.schema.xml

import zio.blocks.schema.{Schema, SchemaBaseSpec}
import zio.test._

object XmlFormatSpec extends SchemaBaseSpec {

  case class Person(name: String, age: Int)
  object Person {
    implicit val schema: Schema[Person] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("XmlFormatSpec")(
    test("mimeType is application/xml") {
      assertTrue(XmlFormat.mimeType == "application/xml")
    },
    test("derive codec for case class") {
      val codec  = Schema[Person].derive(XmlFormat)
      val person = Person("Alice", 30)
      val bytes  = codec.encode(person)
      val result = codec.decode(bytes)
      assertTrue(result == Right(person))
    },
    test("round-trip simple case class") {
      val codec  = Schema[Person].derive(XmlFormat)
      val person = Person("Bob", 25)
      val result = codec.decode(codec.encode(person))
      assertTrue(result == Right(person))
    }
  )
}
