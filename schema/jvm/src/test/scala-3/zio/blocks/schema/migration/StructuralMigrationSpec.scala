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

import scala.language.reflectiveCalls
import scala.reflect.Selectable.reflectiveSelectable
import zio.blocks.schema.*
import zio.test.*

object StructuralMigrationSpec extends ZIOSpecDefault {

  private def literal[A: Schema](value: A): SchemaExpr[Any, A] =
    SchemaExpr.literal(value)

  private def identityExpr[A: Schema]: SchemaExpr[A, A] =
    SchemaExpr.optic(DynamicOptic.root, Schema[A])

  def spec: Spec[TestEnvironment, Any] = suite("StructuralMigrationSpec")(
    suite("Structural → Case Class")(
      test("addField works with structural target selectors") {
        case class PersonV1(name: String)
        given Schema[PersonV1] = Schema.derived[PersonV1]

        type PersonV2 = { def name: String; def age: Int }
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .transformField(_.name, _.name, identityExpr[String])
          .addField(_.age, literal(0))
          .build

        assertTrue(migration(PersonV1("Alice")).isRight)
      },
      test("renameField works with structural source selectors") {
        type PersonV0 = { def firstName: String }
        def makePersonV0(name: String): PersonV0 = new { def firstName: String = name }
        given Schema[PersonV0]                   = Schema.derived[PersonV0]

        case class PersonV2(fullName: String)
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV0, PersonV2]
          .renameField(_.firstName, _.fullName)
          .build

        assertTrue(migration(makePersonV0("Charlie")) == Right(PersonV2("Charlie")))
      },
      test("transformField works with structural source selectors") {
        type PersonV0 = { def age: Int }
        def makePersonV0(value: Int): PersonV0 = new { def age: Int = value }
        given Schema[PersonV0]                 = Schema.derived[PersonV0]

        case class PersonV2(age: Long)
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV0, PersonV2]
          .transformField(_.age, _.age, literal(30L))
          .build

        assertTrue(migration(makePersonV0(30)) == Right(PersonV2(30L)))
      }
    ),
    suite("Case Class → Structural")(
      test("addField works with structural target selectors") {
        case class PersonV1(name: String)
        given Schema[PersonV1] = Schema.derived[PersonV1]

        type PersonV2 = { def name: String; def age: Int }
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .transformField(_.name, _.name, identityExpr[String])
          .addField(_.age, literal(0))
          .build

        assertTrue(migration(PersonV1("Emma")).isRight)
      },
      test("renameField works with structural target selectors") {
        case class PersonV1(firstName: String)
        given Schema[PersonV1] = Schema.derived[PersonV1]

        type PersonV2 = { def fullName: String }
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .renameField(_.firstName, _.fullName)
          .build

        assertTrue(migration(PersonV1("Grace")).isRight)
      }
    ),
    suite("Structural → Structural")(
      test("renameField works with structural selectors on both sides") {
        type PersonV1 = { def firstName: String }
        type PersonV2 = { def fullName: String }

        def makePersonV1(name: String): PersonV1 = new { def firstName: String = name }

        given Schema[PersonV1] = Schema.derived[PersonV1]
        given Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .renameField(_.firstName, _.fullName)
          .build

        assertTrue(migration(makePersonV1("Julia")).isRight)
      },
      test("identity migration works for structural types") {
        type PersonV0 = { def name: String; def age: Int }
        def makePersonV0(n: String, a: Int): PersonV0 = new {
          def name: String = n
          def age: Int     = a
        }
        given Schema[PersonV0] = Schema.derived[PersonV0]

        val migration = Migration.newBuilder[PersonV0, PersonV0].build

        assertTrue(migration(makePersonV0("Mia", 32)).isRight && migration.isEmpty)
      }
    ),
    suite("Structural selector validation")(
      test("build fails to compile when structural target field is missing") {
        typeCheck {
          """
          import scala.language.reflectiveCalls
          import scala.reflect.Selectable.reflectiveSelectable
          import zio.blocks.schema.Schema
          import zio.blocks.schema.migration.Migration

          type PersonV1 = { def name: String }
          type PersonV2 = { def name: String; def age: Int }

          given Schema[PersonV1] = Schema.derived[PersonV1]
          given Schema[PersonV2] = Schema.derived[PersonV2]

          Migration.newBuilder[PersonV1, PersonV2].build
          """
        }.map(result => assertTrue(result.isLeft))
      },
      test("build succeeds for nested structural fields without structural selectors") {
        case class Address(street: String, city: String)
        case class PersonV1(name: String, address: Address)
        given Schema[Address]  = Schema.derived[Address]
        given Schema[PersonV1] = Schema.derived[PersonV1]

        type AddressV2 = { def street: String; def city: String }
        type PersonV2  = { def name: String; def address: AddressV2; def age: Int }
        given Schema[AddressV2] = Schema.derived[AddressV2]
        given Schema[PersonV2]  = Schema.derived[PersonV2]

        val addressMigration = Migration
          .newBuilder[Address, AddressV2]
          .transformField(_.street, _.street, identityExpr[String])
          .transformField(_.city, _.city, identityExpr[String])
          .build

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .transformField(_.name, _.name, identityExpr[String])
          .migrateField(_.address, addressMigration)
          .addField(_.age, literal(0))
          .build

        assertTrue(migration(PersonV1("Leo", Address("123 Main St", "NYC"))).isRight)
      },
      test("nested structural migrateField tracks deep structural fields") {
        type StreetV1  = { def name: String }
        type AddressV1 = { def street: StreetV1; def city: String }
        type PersonV1  = { def name: String; def address: AddressV1 }

        type StreetV2  = { def name: String; def number: Int }
        type AddressV2 = { def street: StreetV2; def city: String }
        type PersonV2  = { def name: String; def address: AddressV2 }

        def makeStreetV1(streetName: String): StreetV1                         = new { def name: String = streetName }
        def makeAddressV1(streetValue: StreetV1, cityValue: String): AddressV1 = new {
          def street: StreetV1 = streetValue
          def city: String     = cityValue
        }
        def makePersonV1(personName: String, addressValue: AddressV1): PersonV1 = new {
          def name: String       = personName
          def address: AddressV1 = addressValue
        }

        given Schema[StreetV1]  = Schema.derived[StreetV1]
        given Schema[AddressV1] = Schema.derived[AddressV1]
        given Schema[PersonV1]  = Schema.derived[PersonV1]
        given Schema[StreetV2]  = Schema.derived[StreetV2]
        given Schema[AddressV2] = Schema.derived[AddressV2]
        given Schema[PersonV2]  = Schema.derived[PersonV2]

        val streetMigration = Migration
          .newBuilder[StreetV1, StreetV2]
          .addField(_.number, literal(0))
          .build

        val addressMigration = Migration
          .newBuilder[AddressV1, AddressV2]
          .migrateField(_.street, streetMigration)
          .build

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .migrateField(_.address, addressMigration)
          .build

        assertTrue(
          migration(makePersonV1("Nina", makeAddressV1(makeStreetV1("Main"), "Berlin"))).isRight
        )
      }
    )
  )
}
