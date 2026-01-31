/*
 * Copyright 2024 John A. De Goes and the ZIO Contributors
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

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test._

object MigrationSpec extends SchemaBaseSpec {

  // Test data types
  case class PersonV1(firstName: String, lastName: String, age: Int)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived
  }

  case class PersonV2(fullName: String, age: Int)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived
  }

  case class PersonV3(firstName: String, lastName: String, age: Int, email: Option[String])
  object PersonV3 {
    implicit val schema: Schema[PersonV3] = Schema.derived
  }

  case class AddressV1(street: String, city: String)
  object AddressV1 {
    implicit val schema: Schema[AddressV1] = Schema.derived
  }

  case class AddressV2(streetName: String, city: String, zipCode: String)
  object AddressV2 {
    implicit val schema: Schema[AddressV2] = Schema.derived
  }

  case class WithNestedV1(name: String, address: AddressV1)
  object WithNestedV1 {
    implicit val schema: Schema[WithNestedV1] = Schema.derived
  }

  case class WithNestedV2(name: String, address: AddressV2)
  object WithNestedV2 {
    implicit val schema: Schema[WithNestedV2] = Schema.derived
  }

  def spec: Spec[TestEnvironment, Any] = suite("MigrationSpec")(
    suite("Basic Field Operations")(
      test("rename field") {
        val v1 = AddressV1("123 Main St", "Boston")

        // Build the full migration with add for zipCode
        val fullMigration = MigrationBuilder[AddressV1, AddressV2]
          .renameField("street", "streetName")
          .addFieldString("zipCode", "00000")
          .build

        val result = fullMigration(v1)
        assertTrue(result == Right(AddressV2("123 Main St", "Boston", "00000")))
      },
      test("add field with default") {
        val migration = MigrationBuilder[PersonV1, PersonV3]
          .addField("email", ResolvedExpr.none)
          .build

        val v1     = PersonV1("John", "Doe", 30)
        val result = migration(v1)
        assertTrue(result == Right(PersonV3("John", "Doe", 30, None)))
      },
      test("drop field") {
        val migration = MigrationBuilder[PersonV3, PersonV1]
          .dropField("email")
          .build

        val v3     = PersonV3("John", "Doe", 30, Some("john@example.com"))
        val result = migration(v3)
        assertTrue(result == Right(PersonV1("John", "Doe", 30)))
      },
      test("transform field") {
        val migration = MigrationBuilder[PersonV1, PersonV1]
          .transformField("age", ResolvedExpr.Convert("int", "int"))
          .build

        val v1     = PersonV1("John", "Doe", 30)
        val result = migration(v1)
        assertTrue(result == Right(PersonV1("John", "Doe", 30)))
      }
    ),
    suite("Nested Migrations")(
      test("nested field rename using atField") {
        val migration = MigrationBuilder[WithNestedV1, WithNestedV2]
          .atField("address")(
            _.renameField("street", "streetName")
              .addFieldString("zipCode", "00000")
          )
          .build

        val v1     = WithNestedV1("Person", AddressV1("123 Main St", "Boston"))
        val result = migration(v1)
        assertTrue(result == Right(WithNestedV2("Person", AddressV2("123 Main St", "Boston", "00000"))))
      }
    ),
    suite("Migration Composition")(
      test("compose two migrations") {
        val migration1 = MigrationBuilder[AddressV1, AddressV2]
          .renameField("street", "streetName")
          .addFieldString("zipCode", "00000")
          .build

        val migration2 = Migration.identity[AddressV2]

        val combined = migration1 ++ migration2
        val v1       = AddressV1("123 Main St", "Boston")
        val result   = combined(v1)
        assertTrue(result == Right(AddressV2("123 Main St", "Boston", "00000")))
      }
    ),
    suite("Reverse Migration")(
      test("reverse of rename is rename back") {
        val migration = MigrationBuilder[AddressV1, AddressV2]
          .renameField("street", "streetName")
          .addFieldString("zipCode", "00000")
          .build

        val reversed = migration.reverse

        val v2     = AddressV2("123 Main St", "Boston", "00000")
        val result = reversed(v2)
        // Reverse drops the added field
        assertTrue(result.isRight)
      }
    ),
    suite("DynamicMigration")(
      test("apply to DynamicValue") {
        val dynamicMigration = DynamicMigration(
          MigrationAction.RenameField("street", "streetName")
        )

        val dv = DynamicValue.Record(
          Chunk(
            ("street", DynamicValue.Primitive(PrimitiveValue.String("123 Main St"))),
            ("city", DynamicValue.Primitive(PrimitiveValue.String("Boston")))
          )
        )

        val result   = dynamicMigration(dv)
        val expected = DynamicValue.Record(
          Chunk(
            ("streetName", DynamicValue.Primitive(PrimitiveValue.String("123 Main St"))),
            ("city", DynamicValue.Primitive(PrimitiveValue.String("Boston")))
          )
        )

        assertTrue(result == Right(expected))
      },
      test("add field to DynamicValue") {
        val dynamicMigration = DynamicMigration(
          MigrationAction.AddField(
            "zipCode",
            ResolvedExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("00000")))
          )
        )

        val dv = DynamicValue.Record(
          Chunk(
            ("street", DynamicValue.Primitive(PrimitiveValue.String("123 Main St"))),
            ("city", DynamicValue.Primitive(PrimitiveValue.String("Boston")))
          )
        )

        val result = dynamicMigration(dv)
        assertTrue(result.isRight)
        assertTrue(result.toOption.get.fields.exists(_._1 == "zipCode"))
      },
      test("nested migration with AtField") {
        val dynamicMigration = DynamicMigration(
          MigrationAction.AtField(
            "address",
            Vector(
              MigrationAction.RenameField("street", "streetName")
            )
          )
        )

        val addressDv = DynamicValue.Record(
          Chunk(
            ("street", DynamicValue.Primitive(PrimitiveValue.String("123 Main St"))),
            ("city", DynamicValue.Primitive(PrimitiveValue.String("Boston")))
          )
        )

        val dv = DynamicValue.Record(
          Chunk(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("Person"))),
            ("address", addressDv)
          )
        )

        val result = dynamicMigration(dv)
        assertTrue(result.isRight)

        val resultAddress = result.toOption.get.fields.find(_._1 == "address").get._2
        assertTrue(resultAddress.fields.exists(_._1 == "streetName"))
        assertTrue(!resultAddress.fields.exists(_._1 == "street"))
      }
    ),
    suite("MigrationAction")(
      test("Identity is its own reverse") {
        val action  = MigrationAction.Identity
        val reverse = action.reverse
        assertTrue(reverse == MigrationAction.Identity)
      },
      test("RenameField reverse swaps from and to") {
        val action  = MigrationAction.RenameField("a", "b")
        val reverse = action.reverse
        assertTrue(reverse == MigrationAction.RenameField("b", "a"))
      },
      test("AddField reverse is DropField") {
        val default = ResolvedExpr.Literal(DynamicValue.Null)
        val action  = MigrationAction.AddField("field", default)
        val reverse = action.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.DropField])
      },
      test("DropField reverse is AddField") {
        val default = Some(ResolvedExpr.Literal(DynamicValue.Null))
        val action  = MigrationAction.DropField("field", default)
        val reverse = action.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.AddField])
      },
      test("AtField reverse reverses nested actions") {
        val action = MigrationAction.AtField(
          "address",
          Vector(
            MigrationAction.RenameField("a", "b"),
            MigrationAction.RenameField("c", "d")
          )
        )
        val reverse = action.reverse

        assertTrue(reverse.isInstanceOf[MigrationAction.AtField])
        val atField = reverse.asInstanceOf[MigrationAction.AtField]
        assertTrue(atField.fieldName == "address")
        assertTrue(atField.actions.length == 2)
        // Reversed order and reversed actions
        assertTrue(atField.actions(0) == MigrationAction.RenameField("d", "c"))
        assertTrue(atField.actions(1) == MigrationAction.RenameField("b", "a"))
      }
    ),
    suite("ResolvedExpr")(
      test("Literal evaluates to constant") {
        val expr   = ResolvedExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("hello")))
        val result = expr.eval(DynamicValue.Null, DynamicOptic.root)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("hello"))))
      },
      test("Identity returns input") {
        val input  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val expr   = ResolvedExpr.Identity
        val result = expr.eval(input, DynamicOptic.root)
        assertTrue(result == Right(input))
      },
      test("FieldAccess extracts field value") {
        val dv = DynamicValue.Record(
          Chunk(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("John"))),
            ("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
          )
        )
        val expr   = ResolvedExpr.FieldAccess("name")
        val result = expr.eval(dv, DynamicOptic.root)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("John"))))
      },
      test("Concat concatenates string values") {
        val dv = DynamicValue.Record(
          Chunk(
            ("first", DynamicValue.Primitive(PrimitiveValue.String("Hello"))),
            ("second", DynamicValue.Primitive(PrimitiveValue.String("World")))
          )
        )
        val expr = ResolvedExpr.Concat(
          Vector(
            ResolvedExpr.FieldAccess("first"),
            ResolvedExpr.FieldAccess("second")
          ),
          " "
        )
        val result = expr.eval(dv, DynamicOptic.root)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("Hello World"))))
      },
      test("Convert converts between primitive types") {
        val dv     = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val expr   = ResolvedExpr.Convert("int", "string")
        val result = expr.eval(dv, DynamicOptic.root)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("42"))))
      }
    ),
    suite("PrimitiveConversions")(
      test("int to string") {
        val pv     = PrimitiveValue.Int(42)
        val result = PrimitiveConversions.convertPrimitive(pv, "string")
        assertTrue(result == Right(PrimitiveValue.String("42")))
      },
      test("string to int") {
        val pv     = PrimitiveValue.String("42")
        val result = PrimitiveConversions.convertPrimitive(pv, "int")
        assertTrue(result == Right(PrimitiveValue.Int(42)))
      },
      test("string to int fails for non-numeric") {
        val pv     = PrimitiveValue.String("not a number")
        val result = PrimitiveConversions.convertPrimitive(pv, "int")
        assertTrue(result.isLeft)
      },
      test("boolean to int") {
        val pvTrue  = PrimitiveValue.Boolean(true)
        val pvFalse = PrimitiveValue.Boolean(false)
        assertTrue(
          PrimitiveConversions.convertPrimitive(pvTrue, "int") == Right(PrimitiveValue.Int(1)) &&
            PrimitiveConversions.convertPrimitive(pvFalse, "int") == Right(PrimitiveValue.Int(0))
        )
      },
      test("int to boolean") {
        val pvZero    = PrimitiveValue.Int(0)
        val pvNonZero = PrimitiveValue.Int(42)
        assertTrue(
          PrimitiveConversions.convertPrimitive(pvZero, "boolean") == Right(PrimitiveValue.Boolean(false)) &&
            PrimitiveConversions.convertPrimitive(pvNonZero, "boolean") == Right(PrimitiveValue.Boolean(true))
        )
      },
      test("long to double") {
        val pv     = PrimitiveValue.Long(42L)
        val result = PrimitiveConversions.convertPrimitive(pv, "double")
        assertTrue(result == Right(PrimitiveValue.Double(42.0)))
      },
      test("double to long") {
        val pv     = PrimitiveValue.Double(42.5)
        val result = PrimitiveConversions.convertPrimitive(pv, "long")
        assertTrue(result == Right(PrimitiveValue.Long(42L)))
      },
      test("string to boolean") {
        assertTrue(
          PrimitiveConversions.convertPrimitive(PrimitiveValue.String("true"), "boolean") == Right(
            PrimitiveValue.Boolean(true)
          ) &&
            PrimitiveConversions.convertPrimitive(PrimitiveValue.String("false"), "boolean") == Right(
              PrimitiveValue.Boolean(false)
            ) &&
            PrimitiveConversions.convertPrimitive(PrimitiveValue.String("yes"), "boolean") == Right(
              PrimitiveValue.Boolean(true)
            ) &&
            PrimitiveConversions.convertPrimitive(PrimitiveValue.String("no"), "boolean") == Right(
              PrimitiveValue.Boolean(false)
            )
        )
      },
      test("int to bigint") {
        val pv     = PrimitiveValue.Int(42)
        val result = PrimitiveConversions.convertPrimitive(pv, "bigint")
        assertTrue(result == Right(PrimitiveValue.BigInt(BigInt(42))))
      },
      test("double to bigdecimal") {
        val pv     = PrimitiveValue.Double(42.5)
        val result = PrimitiveConversions.convertPrimitive(pv, "bigdecimal")
        assertTrue(result == Right(PrimitiveValue.BigDecimal(BigDecimal(42.5))))
      }
    ),
    suite("Collection Migrations")(
      test("transform sequence elements") {
        val dynamicMigration = DynamicMigration(
          MigrationAction.AtElements(
            Vector(
              MigrationAction.RenameField("name", "fullName")
            )
          )
        )

        val elem1 = DynamicValue.Record(Chunk(("name", DynamicValue.Primitive(PrimitiveValue.String("John")))))
        val elem2 = DynamicValue.Record(Chunk(("name", DynamicValue.Primitive(PrimitiveValue.String("Jane")))))
        val dv    = DynamicValue.Sequence(Chunk(elem1, elem2))

        val result = dynamicMigration(dv)
        assertTrue(result.isRight)

        val seq = result.toOption.get.elements
        assertTrue(seq.forall(_.fields.exists(_._1 == "fullName")))
        assertTrue(seq.forall(!_.fields.exists(_._1 == "name")))
      }
    ),
    suite("MigrationBuilder")(
      test("builder accumulates actions") {
        val builder = MigrationBuilder[PersonV1, PersonV1]
          .renameField("firstName", "first")
          .renameField("lastName", "last")

        val migration = builder.buildDynamic
        assertTrue(migration.actions.length == 2)
      },
      test("nested builder with atField") {
        val builder = MigrationBuilder[WithNestedV1, WithNestedV2]
          .atField("address")(
            _.renameField("street", "streetName")
              .addFieldString("zipCode", "00000")
          )

        val migration = builder.buildDynamic
        assertTrue(migration.actions.length == 1)
        assertTrue(migration.actions.head.isInstanceOf[MigrationAction.AtField])

        val atField = migration.actions.head.asInstanceOf[MigrationAction.AtField]
        assertTrue(atField.actions.length == 2)
      }
    )
  )
}
