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
import zio.blocks.schema._
import zio.test._

object StructuralMigrationSpec extends ZIOSpecDefault {

  private def literal[A: Schema](value: A): SchemaExpr[Any, A] =
    SchemaExpr.literal(value)

  private def identityExpr[A: Schema]: SchemaExpr[A, A] =
    SchemaExpr.optic(DynamicOptic.root, Schema[A])

  def spec: Spec[TestEnvironment, Any] = suite("StructuralMigrationSpec")(
    suite("Structural → Case Class")(
      test("addField works with structural target selectors") {
        case class PersonV1(name: String)
        implicit val personV1Schema: Schema[PersonV1] = Schema.derived[PersonV1]

        type PersonV2 = { def name: String; def age: Int }
        implicit val personV2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .transformField(_.name, _.name, identityExpr[String])
          .addField(_.age, literal(0))
          .build

        assertTrue(migration(PersonV1("Alice")).isRight)
      },
      test("renameField works with structural source selectors") {
        type PersonV0 = { def firstName: String }
        def makePersonV0(name: String): PersonV0      = new { def firstName: String = name }
        implicit val personV0Schema: Schema[PersonV0] = Schema.derived[PersonV0]

        case class PersonV2(fullName: String)
        implicit val personV2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV0, PersonV2]
          .renameField(_.firstName, _.fullName)
          .build

        assertTrue(migration(makePersonV0("Charlie")) == Right(PersonV2("Charlie")))
      },
      test("transformField works with structural source selectors") {
        type PersonV0 = { def age: Int }
        def makePersonV0(value: Int): PersonV0        = new { def age: Int = value }
        implicit val personV0Schema: Schema[PersonV0] = Schema.derived[PersonV0]

        case class PersonV2(age: Long)
        implicit val personV2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV0, PersonV2]
          .transformField(_.age, _.age, literal(30L))
          .build

        assertTrue(migration(makePersonV0(30)) == Right(PersonV2(30L)))
      },
      test("dropField works with structural source selectors") {
        type PersonV1 = { def name: String; def age: Int }
        def makePersonV1(n: String, a: Int): PersonV1 = new {
          def name: String = n
          def age: Int     = a
        }
        case class PersonV2(name: String)

        implicit val personV1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val personV2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .dropField(_.age, literal(0))
          .buildPartial

        assertTrue(migration.dynamicMigration(Schema[PersonV1].toDynamicValue(makePersonV1("Alice", 30))).isRight)
      },
      test("changeFieldType works with structural source selectors") {
        type PersonV1 = { def score: Int }
        def makePersonV1(s: Int): PersonV1 = new { def score: Int = s }
        case class PersonV2(score: String)

        implicit val personV1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val personV2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .changeFieldType(_.score, literal("0"))
          .build

        assertTrue(migration(makePersonV1(42)) == Right(PersonV2("0")))
      }
    ),
    suite("Case Class → Structural")(
      test("addField works with structural target selectors") {
        case class PersonV1(name: String)
        implicit val personV1Schema: Schema[PersonV1] = Schema.derived[PersonV1]

        type PersonV2 = { def name: String; def age: Int }
        implicit val personV2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .transformField(_.name, _.name, identityExpr[String])
          .addField(_.age, literal(0))
          .build

        assertTrue(migration(PersonV1("Emma")).isRight)
      },
      test("renameField works with structural target selectors") {
        case class PersonV1(firstName: String)
        implicit val personV1Schema: Schema[PersonV1] = Schema.derived[PersonV1]

        type PersonV2 = { def fullName: String }
        implicit val personV2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .renameField(_.firstName, _.fullName)
          .build

        assertTrue(migration(PersonV1("Grace")).isRight)
      },
      test("dropField works with structural target selectors") {
        case class PersonV1(name: String, age: Int)
        type PersonV2 = { def name: String }

        implicit val personV1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val personV2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .dropField(_.age, literal(0))
          .buildPartial

        assertTrue(migration.dynamicMigration(Schema[PersonV1].toDynamicValue(PersonV1("Alice", 30))).isRight)
      },
      test("changeFieldType works with structural target selectors") {
        case class PersonV1(score: Int)
        type PersonV2 = { def score: String }

        implicit val personV1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val personV2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .changeFieldType(_.score, literal("0"))
          .build

        assertTrue(migration(PersonV1(42)).isRight)
      }
    ),
    suite("Structural → Structural")(
      test("renameField works with structural selectors on both sides") {
        type PersonV1 = { def firstName: String }
        type PersonV2 = { def fullName: String }

        def makePersonV1(name: String): PersonV1 = new { def firstName: String = name }

        implicit val personV1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val personV2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

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
        implicit val personV0Schema: Schema[PersonV0] = Schema.derived[PersonV0]

        val migration = Migration.newBuilder[PersonV0, PersonV0].build

        assertTrue(migration(makePersonV0("Mia", 32)).isRight && migration.isEmpty)
      },
      test("dropField works with structural selectors on both sides") {
        type PersonV1 = { def name: String; def age: Int }
        type PersonV2 = { def name: String }
        def makePersonV1(n: String, a: Int): PersonV1 = new {
          def name: String = n
          def age: Int     = a
        }

        implicit val personV1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val personV2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .dropField(_.age, literal(0))
          .buildPartial

        assertTrue(migration.dynamicMigration(Schema[PersonV1].toDynamicValue(makePersonV1("Alice", 30))).isRight)
      },
      test("changeFieldType works with structural selectors on both sides") {
        type PersonV1 = { def score: Int }
        type PersonV2 = { def score: String }
        def makePersonV1(s: Int): PersonV1 = new { def score: Int = s }

        implicit val personV1Schema: Schema[PersonV1] = Schema.derived[PersonV1]
        implicit val personV2Schema: Schema[PersonV2] = Schema.derived[PersonV2]

        val migration = Migration
          .newBuilder[PersonV1, PersonV2]
          .changeFieldType(_.score, literal("0"))
          .build

        assertTrue(migration(makePersonV1(42)).isRight)
      }
    ),
    suite("Structural selector validation")(
      test("build succeeds for nested structural fields without structural selectors") {
        case class Address(street: String, city: String)
        case class PersonV1(name: String, address: Address)
        implicit val addressSchema: Schema[Address]   = Schema.derived[Address]
        implicit val personV1Schema: Schema[PersonV1] = Schema.derived[PersonV1]

        type AddressV2 = { def street: String; def city: String }
        type PersonV2  = { def name: String; def address: AddressV2; def age: Int }
        implicit val addressV2Schema: Schema[AddressV2] = Schema.derived[AddressV2]
        implicit val personV2Schema: Schema[PersonV2]   = Schema.derived[PersonV2]

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
      test("deep nested structural renameField via migrateField") {
        type Inner  = { def firstName: String }
        type Outer  = { def data: Inner }
        type Inner2 = { def fullName: String }
        type Outer2 = { def data: Inner2 }

        def makeInner(n: String): Inner = new { def firstName: String = n }
        def makeOuter(d: Inner): Outer  = new { def data: Inner = d }

        implicit val innerSchema: Schema[Inner]   = Schema.derived[Inner]
        implicit val outerSchema: Schema[Outer]   = Schema.derived[Outer]
        implicit val inner2Schema: Schema[Inner2] = Schema.derived[Inner2]
        implicit val outer2Schema: Schema[Outer2] = Schema.derived[Outer2]

        val innerMigration = Migration
          .newBuilder[Inner, Inner2]
          .renameField(_.firstName, _.fullName)
          .build

        val migration = Migration
          .newBuilder[Outer, Outer2]
          .migrateField(_.data, innerMigration)
          .build

        assertTrue(migration(makeOuter(makeInner("Alice"))).isRight)
      },
      test("deep nested structural dropField via migrateField") {
        type Inner  = { def name: String; def age: Int }
        type Outer  = { def data: Inner }
        type Inner2 = { def name: String }
        type Outer2 = { def data: Inner2 }

        def makeInner(n: String, a: Int): Inner = new {
          def name: String = n
          def age: Int     = a
        }
        def makeOuter(d: Inner): Outer = new { def data: Inner = d }

        implicit val innerSchema: Schema[Inner]   = Schema.derived[Inner]
        implicit val outerSchema: Schema[Outer]   = Schema.derived[Outer]
        implicit val inner2Schema: Schema[Inner2] = Schema.derived[Inner2]
        implicit val outer2Schema: Schema[Outer2] = Schema.derived[Outer2]

        val innerMigration = Migration
          .newBuilder[Inner, Inner2]
          .dropField(_.age, literal(0))
          .buildPartial

        val outerMigration = Migration
          .newBuilder[Outer, Outer2]
          .migrateField(_.data, innerMigration)
          .buildPartial

        assertTrue(
          outerMigration.dynamicMigration(Schema[Outer].toDynamicValue(makeOuter(makeInner("Bob", 25)))).isRight
        )
      },
      test("deep nested structural changeFieldType via migrateField") {
        type Inner  = { def score: Int }
        type Outer  = { def data: Inner }
        type Inner2 = { def score: String }
        type Outer2 = { def data: Inner2 }

        def makeInner(s: Int): Inner   = new { def score: Int = s }
        def makeOuter(d: Inner): Outer = new { def data: Inner = d }

        implicit val innerSchema: Schema[Inner]   = Schema.derived[Inner]
        implicit val outerSchema: Schema[Outer]   = Schema.derived[Outer]
        implicit val inner2Schema: Schema[Inner2] = Schema.derived[Inner2]
        implicit val outer2Schema: Schema[Outer2] = Schema.derived[Outer2]

        val innerMigration = Migration
          .newBuilder[Inner, Inner2]
          .changeFieldType(_.score, literal("0"))
          .build

        val migration = Migration
          .newBuilder[Outer, Outer2]
          .migrateField(_.data, innerMigration)
          .build

        assertTrue(migration(makeOuter(makeInner(42))).isRight)
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

        implicit val streetV1Schema: Schema[StreetV1]   = Schema.derived[StreetV1]
        implicit val addressV1Schema: Schema[AddressV1] = Schema.derived[AddressV1]
        implicit val personV1Schema: Schema[PersonV1]   = Schema.derived[PersonV1]
        implicit val streetV2Schema: Schema[StreetV2]   = Schema.derived[StreetV2]
        implicit val addressV2Schema: Schema[AddressV2] = Schema.derived[AddressV2]
        implicit val personV2Schema: Schema[PersonV2]   = Schema.derived[PersonV2]

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
