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

package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object MigrationSpec extends SchemaBaseSpec {

  // ---------------------------------------------------------------------------
  // Versioned domain types
  // ---------------------------------------------------------------------------

  case class PersonV1(name: String, age: Int)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived
  }

  case class PersonV2(fullName: String, age: Int, email: String)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived
  }

  case class PersonV3(fullName: String, email: String)
  object PersonV3 {
    implicit val schema: Schema[PersonV3] = Schema.derived
  }

  case class Address(city: String, zip: String)
  object Address {
    implicit val schema: Schema[Address] = Schema.derived
  }

  case class PersonWithAddressV1(name: String, address: Address)
  object PersonWithAddressV1 {
    implicit val schema: Schema[PersonWithAddressV1] = Schema.derived
  }

  case class AddressV2(city: String, zip: String, country: String)
  object AddressV2 {
    implicit val schema: Schema[AddressV2] = Schema.derived
  }

  case class PersonWithAddressV2(name: String, address: AddressV2)
  object PersonWithAddressV2 {
    implicit val schema: Schema[PersonWithAddressV2] = Schema.derived
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  def stringDefault(s: String): DynamicValue = DynamicValue.Primitive(PrimitiveValue.String(s))
  def intDefault(n: Int): DynamicValue       = DynamicValue.Primitive(PrimitiveValue.Int(n))

  // ---------------------------------------------------------------------------
  // Specs
  // ---------------------------------------------------------------------------

  def spec: Spec[Any, Any] = suite("MigrationSpec")(
    suite("typed migration: PersonV1 -> PersonV2")(
      test("rename + add field") {
        val m = Migration(PersonV1.schema, PersonV2.schema)
          .renameField("name", "fullName")
          .addField("email", stringDefault("unknown@example.com"))

        val result = m(PersonV1("Alice", 30))
        assertTrue(result == Right(PersonV2("Alice", 30, "unknown@example.com")))
      }
    ),
    suite("typed migration: PersonV2 -> PersonV3")(
      test("drop field") {
        val m = Migration(PersonV2.schema, PersonV3.schema)
          .dropField("age")

        val result = m(PersonV2("Alice", 30, "alice@example.com"))
        assertTrue(result == Right(PersonV3("Alice", "alice@example.com")))
      }
    ),
    suite("chained migration: PersonV1 -> PersonV2 -> PersonV3")(
      test("andThen composes typed migrations") {
        val m1 = Migration(PersonV1.schema, PersonV2.schema)
          .renameField("name", "fullName")
          .addField("email", stringDefault(""))

        val m2 = Migration(PersonV2.schema, PersonV3.schema)
          .dropField("age")

        val composed = m1.andThen(m2)
        val result   = composed(PersonV1("Alice", 30))
        assertTrue(result == Right(PersonV3("Alice", "")))
      }
    ),
    suite("nested record migration")(
      test("add field to nested record via path") {
        val m = Migration(PersonWithAddressV1.schema, PersonWithAddressV2.schema)
          .transformValue(
            DynamicOptic.root.field("address"),
            DynamicMigration.single(MigrationAction.AddField(DynamicOptic.root, "country", stringDefault("US")))
          )

        val result = m(PersonWithAddressV1("Alice", Address("NYC", "10001")))
        assertTrue(result == Right(PersonWithAddressV2("Alice", AddressV2("NYC", "10001", "US"))))
      }
    ),
    suite("identity migration")(
      test("identity preserves value") {
        val m      = Migration.identity[PersonV1]
        val person = PersonV1("Alice", 30)
        assertTrue(m(person) == Right(person))
      }
    ),
    suite("error propagation")(
      test("migration fails when target schema doesn't match") {
        // Deliberately wrong: don't add the required "email" field
        val m = Migration(PersonV1.schema, PersonV2.schema)
          .renameField("name", "fullName")
        // Missing: .addField("email", ...)

        val result = m(PersonV1("Alice", 30))
        assertTrue(result.isLeft)
      }
    )
  )
}
