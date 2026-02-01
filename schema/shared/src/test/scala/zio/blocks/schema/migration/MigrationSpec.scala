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

  // Types with collections for testing .each selector
  case class WithTags(name: String, tags: Chunk[String])
  object WithTags {
    implicit val schema: Schema[WithTags] = Schema.derived
  }

  // Sum types for testing .when[T] selector
  sealed trait Country
  object Country {
    case class UK(postcode: String)    extends Country
    case class US(zipCode: String)     extends Country
    case class DE(plz: String)         extends Country
    implicit val schema: Schema[Country] = Schema.derived
  }

  case class WithCountry(name: String, country: Country)
  object WithCountry {
    implicit val schema: Schema[WithCountry] = Schema.derived
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
      },
      test("addFieldInt convenience method") {
        val builder   = MigrationBuilder[PersonV1, PersonV1].addFieldInt("count", 42)
        val migration = builder.buildDynamic
        assertTrue(migration.actions.length == 1)
        assertTrue(migration.actions.head.isInstanceOf[MigrationAction.AddField])
      },
      test("addFieldBoolean convenience method") {
        val builder   = MigrationBuilder[PersonV1, PersonV1].addFieldBoolean("active", true)
        val migration = builder.buildDynamic
        assertTrue(migration.actions.length == 1)
        assertTrue(migration.actions.head.isInstanceOf[MigrationAction.AddField])
      },
      test("dropField with default for reverse") {
        val builder = MigrationBuilder[PersonV1, PersonV1]
          .dropField("age", ResolvedExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0))))
        val migration = builder.buildDynamic
        assertTrue(migration.actions.length == 1)
        val dropAction = migration.actions.head.asInstanceOf[MigrationAction.DropField]
        assertTrue(dropAction.defaultForReverse.isDefined)
      },
      test("transformField with explicit reverse") {
        val builder = MigrationBuilder[PersonV1, PersonV1]
          .transformField("age", ResolvedExpr.Identity, ResolvedExpr.Identity)
        val migration = builder.buildDynamic
        assertTrue(migration.actions.length == 1)
      },
      test("mandateField builder method") {
        val builder = MigrationBuilder[PersonV1, PersonV1]
          .mandateField("age", ResolvedExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0))))
        val migration = builder.buildDynamic
        assertTrue(migration.actions.head.isInstanceOf[MigrationAction.MandateField])
      },
      test("optionalizeField builder method") {
        val builder   = MigrationBuilder[PersonV1, PersonV1].optionalizeField("age")
        val migration = builder.buildDynamic
        assertTrue(migration.actions.head.isInstanceOf[MigrationAction.OptionalizeField])
      },
      test("changeFieldType builder method") {
        val builder   = MigrationBuilder[PersonV1, PersonV1].changeFieldType("age", ResolvedExpr.Convert("int", "string"))
        val migration = builder.buildDynamic
        assertTrue(migration.actions.head.isInstanceOf[MigrationAction.ChangeFieldType])
      },
      test("keepField builder method") {
        val builder   = MigrationBuilder[PersonV1, PersonV1].keepField("age")
        val migration = builder.buildDynamic
        assertTrue(migration.actions.head.isInstanceOf[MigrationAction.KeepField])
      },
      test("atCase builder method") {
        val builder = MigrationBuilder[PersonV1, PersonV1]
          .atCase("SomeCase")(_.renameField("a", "b"))
        val migration = builder.buildDynamic
        assertTrue(migration.actions.head.isInstanceOf[MigrationAction.AtCase])
      },
      test("atElements builder method") {
        val builder   = MigrationBuilder[PersonV1, PersonV1].atElements(_.renameField("a", "b"))
        val migration = builder.buildDynamic
        assertTrue(migration.actions.head.isInstanceOf[MigrationAction.AtElements])
      },
      test("atMapKeys builder method") {
        val builder   = MigrationBuilder[PersonV1, PersonV1].atMapKeys(_.renameField("a", "b"))
        val migration = builder.buildDynamic
        assertTrue(migration.actions.head.isInstanceOf[MigrationAction.AtMapKeys])
      },
      test("atMapValues builder method") {
        val builder   = MigrationBuilder[PersonV1, PersonV1].atMapValues(_.renameField("a", "b"))
        val migration = builder.buildDynamic
        assertTrue(migration.actions.head.isInstanceOf[MigrationAction.AtMapValues])
      },
      test("renameCase builder method") {
        val builder   = MigrationBuilder[PersonV1, PersonV1].renameCase("OldCase", "NewCase")
        val migration = builder.buildDynamic
        assertTrue(migration.actions.head.isInstanceOf[MigrationAction.RenameCase])
      },
      test("transformCase builder method") {
        val builder   = MigrationBuilder[PersonV1, PersonV1].transformCase("SomeCase")(_.renameField("a", "b"))
        val migration = builder.buildDynamic
        assertTrue(migration.actions.head.isInstanceOf[MigrationAction.TransformCase])
      },
      test("transformElements builder method") {
        val builder   = MigrationBuilder[PersonV1, PersonV1].transformElements(ResolvedExpr.Identity)
        val migration = builder.buildDynamic
        assertTrue(migration.actions.head.isInstanceOf[MigrationAction.TransformElements])
      },
      test("transformKeys builder method") {
        val builder   = MigrationBuilder[PersonV1, PersonV1].transformKeys(ResolvedExpr.Identity)
        val migration = builder.buildDynamic
        assertTrue(migration.actions.head.isInstanceOf[MigrationAction.TransformKeys])
      },
      test("transformValues builder method") {
        val builder   = MigrationBuilder[PersonV1, PersonV1].transformValues(ResolvedExpr.Identity)
        val migration = builder.buildDynamic
        assertTrue(migration.actions.head.isInstanceOf[MigrationAction.TransformValues])
      },
      test("joinFields builder method") {
        val builder = MigrationBuilder[PersonV1, PersonV1]
          .joinFields(Vector("firstName", "lastName"), "fullName", ResolvedExpr.Identity)
        val migration = builder.buildDynamic
        assertTrue(migration.actions.head.isInstanceOf[MigrationAction.JoinFields])
      },
      test("splitField builder method") {
        val builder = MigrationBuilder[PersonV1, PersonV1]
          .splitField("fullName", Vector("first", "last"), ResolvedExpr.Identity)
        val migration = builder.buildDynamic
        assertTrue(migration.actions.head.isInstanceOf[MigrationAction.SplitField])
      },
      test("buildPartial method") {
        val migration = MigrationBuilder[PersonV1, PersonV1].renameField("a", "b").buildPartial
        assertTrue(migration.actions.length == 1)
      },
      test("withFieldTracking smart constructor") {
        val builder = MigrationBuilder.withFieldTracking[PersonV1, PersonV1]
        assertTrue(builder.actions.isEmpty)
      }
    ),
    suite("DynamicMigration Extended")(
      test("empty migration") {
        val migration = DynamicMigration.empty
        assertTrue(migration.isEmpty)
        assertTrue(migration.toString == "DynamicMigration {}")
      },
      test("identity migration") {
        val migration = DynamicMigration.identity
        assertTrue(!migration.isEmpty)
      },
      test("single action constructor") {
        val migration = DynamicMigration(MigrationAction.Identity)
        assertTrue(migration.actions.length == 1)
      },
      test("varargs action constructor") {
        val migration = DynamicMigration(
          MigrationAction.RenameField("a", "b"),
          MigrationAction.RenameField("c", "d")
        )
        assertTrue(migration.actions.length == 2)
      },
      test("andThen composition") {
        val m1     = DynamicMigration(MigrationAction.RenameField("a", "b"))
        val m2     = DynamicMigration(MigrationAction.RenameField("c", "d"))
        val result = m1.andThen(m2)
        assertTrue(result.actions.length == 2)
      },
      test("Sequence action") {
        val dv = DynamicValue.Record(
          Chunk(("a", DynamicValue.Primitive(PrimitiveValue.String("hello"))))
        )
        val migration = DynamicMigration(
          MigrationAction.Sequence(
            Vector(
              MigrationAction.RenameField("a", "b"),
              MigrationAction.AddField("c", ResolvedExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(1))))
            )
          )
        )
        val result = migration(dv)
        assertTrue(result.isRight)
        assertTrue(result.toOption.get.fields.exists(_._1 == "b"))
        assertTrue(result.toOption.get.fields.exists(_._1 == "c"))
      },
      test("MandateField unwraps Some") {
        val dv = DynamicValue.Record(
          Chunk(
            (
              "opt",
              DynamicValue.Variant(
                "Some",
                DynamicValue.Record(Chunk(("value", DynamicValue.Primitive(PrimitiveValue.Int(42)))))
              )
            )
          )
        )
        val migration = DynamicMigration(
          MigrationAction.MandateField("opt", ResolvedExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0))))
        )
        val result = migration(dv)
        assertTrue(result.isRight)
        val record   = result.toOption.get.asInstanceOf[DynamicValue.Record]
        val optValue = record.fields.find(_._1 == "opt").get._2
        assertTrue(optValue == DynamicValue.Primitive(PrimitiveValue.Int(42)))
      },
      test("MandateField uses default for None") {
        val dv = DynamicValue.Record(
          Chunk(("opt", DynamicValue.Variant("None", DynamicValue.Record(Chunk.empty))))
        )
        val migration = DynamicMigration(
          MigrationAction.MandateField("opt", ResolvedExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(99))))
        )
        val result = migration(dv)
        assertTrue(result.isRight)
        val record   = result.toOption.get.asInstanceOf[DynamicValue.Record]
        val optValue = record.fields.find(_._1 == "opt").get._2
        assertTrue(optValue == DynamicValue.Primitive(PrimitiveValue.Int(99)))
      },
      test("MandateField uses default for Null") {
        val dv        = DynamicValue.Record(Chunk(("opt", DynamicValue.Null)))
        val migration = DynamicMigration(
          MigrationAction.MandateField("opt", ResolvedExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(99))))
        )
        val result = migration(dv)
        assertTrue(result.isRight)
      },
      test("MandateField error on missing field") {
        val dv        = DynamicValue.Record(Chunk.empty)
        val migration = DynamicMigration(
          MigrationAction.MandateField("missing", ResolvedExpr.Literal(DynamicValue.Null))
        )
        val result = migration(dv)
        assertTrue(result.isLeft)
      },
      test("MandateField error on non-record") {
        val dv        = DynamicValue.Primitive(PrimitiveValue.Int(1))
        val migration = DynamicMigration(
          MigrationAction.MandateField("field", ResolvedExpr.Literal(DynamicValue.Null))
        )
        val result = migration(dv)
        assertTrue(result.isLeft)
      },
      test("OptionalizeField wraps value in Some") {
        val dv = DynamicValue.Record(
          Chunk(("field", DynamicValue.Primitive(PrimitiveValue.Int(42))))
        )
        val migration = DynamicMigration(MigrationAction.OptionalizeField("field"))
        val result    = migration(dv)
        assertTrue(result.isRight)
        val record  = result.toOption.get.asInstanceOf[DynamicValue.Record]
        val wrapped = record.fields.find(_._1 == "field").get._2
        assertTrue(wrapped.isInstanceOf[DynamicValue.Variant])
        val variant = wrapped.asInstanceOf[DynamicValue.Variant]
        assertTrue(variant.caseName == Some("Some"))
      },
      test("OptionalizeField wraps Null in None") {
        val dv        = DynamicValue.Record(Chunk(("field", DynamicValue.Null)))
        val migration = DynamicMigration(MigrationAction.OptionalizeField("field"))
        val result    = migration(dv)
        assertTrue(result.isRight)
        val record  = result.toOption.get.asInstanceOf[DynamicValue.Record]
        val wrapped = record.fields.find(_._1 == "field").get._2
        val variant = wrapped.asInstanceOf[DynamicValue.Variant]
        assertTrue(variant.caseName == Some("None"))
      },
      test("OptionalizeField error on missing field") {
        val dv        = DynamicValue.Record(Chunk.empty)
        val migration = DynamicMigration(MigrationAction.OptionalizeField("missing"))
        val result    = migration(dv)
        assertTrue(result.isLeft)
      },
      test("OptionalizeField error on non-record") {
        val dv        = DynamicValue.Primitive(PrimitiveValue.Int(1))
        val migration = DynamicMigration(MigrationAction.OptionalizeField("field"))
        val result    = migration(dv)
        assertTrue(result.isLeft)
      },
      test("ChangeFieldType converts field value") {
        val dv = DynamicValue.Record(
          Chunk(("num", DynamicValue.Primitive(PrimitiveValue.Int(42))))
        )
        val migration = DynamicMigration(
          MigrationAction.ChangeFieldType("num", ResolvedExpr.Convert("int", "string"))
        )
        val result = migration(dv)
        assertTrue(result.isRight)
        val record   = result.toOption.get.asInstanceOf[DynamicValue.Record]
        val numValue = record.fields.find(_._1 == "num").get._2
        assertTrue(numValue == DynamicValue.Primitive(PrimitiveValue.String("42")))
      },
      test("ChangeFieldType error on missing field") {
        val dv        = DynamicValue.Record(Chunk.empty)
        val migration = DynamicMigration(MigrationAction.ChangeFieldType("missing", ResolvedExpr.Identity))
        val result    = migration(dv)
        assertTrue(result.isLeft)
      },
      test("ChangeFieldType error on non-record") {
        val dv        = DynamicValue.Primitive(PrimitiveValue.Int(1))
        val migration = DynamicMigration(MigrationAction.ChangeFieldType("field", ResolvedExpr.Identity))
        val result    = migration(dv)
        assertTrue(result.isLeft)
      },
      test("KeepField is no-op") {
        val dv = DynamicValue.Record(
          Chunk(("field", DynamicValue.Primitive(PrimitiveValue.Int(42))))
        )
        val migration = DynamicMigration(MigrationAction.KeepField("field"))
        val result    = migration(dv)
        assertTrue(result == Right(dv))
      },
      test("AtCase applies nested actions to matching case") {
        val dv = DynamicValue.Variant(
          "MyCase",
          DynamicValue.Record(Chunk(("a", DynamicValue.Primitive(PrimitiveValue.String("hello")))))
        )
        val migration = DynamicMigration(
          MigrationAction.AtCase("MyCase", Vector(MigrationAction.RenameField("a", "b")))
        )
        val result = migration(dv)
        assertTrue(result.isRight)
        val variant = result.toOption.get.asInstanceOf[DynamicValue.Variant]
        val inner   = variant.value.asInstanceOf[DynamicValue.Record]
        assertTrue(inner.fields.exists(_._1 == "b"))
        assertTrue(!inner.fields.exists(_._1 == "a"))
      },
      test("AtCase leaves non-matching case unchanged") {
        val dv = DynamicValue.Variant(
          "OtherCase",
          DynamicValue.Record(Chunk(("a", DynamicValue.Primitive(PrimitiveValue.String("hello")))))
        )
        val migration = DynamicMigration(
          MigrationAction.AtCase("MyCase", Vector(MigrationAction.RenameField("a", "b")))
        )
        val result = migration(dv)
        assertTrue(result == Right(dv))
      },
      test("AtCase error on non-variant") {
        val dv        = DynamicValue.Primitive(PrimitiveValue.Int(1))
        val migration = DynamicMigration(MigrationAction.AtCase("Case", Vector.empty))
        val result    = migration(dv)
        assertTrue(result.isLeft)
      },
      test("AtMapKeys transforms map keys") {
        val dv = DynamicValue.Map(
          Chunk(
            (
              DynamicValue.Record(Chunk(("k", DynamicValue.Primitive(PrimitiveValue.String("key"))))),
              DynamicValue.Primitive(PrimitiveValue.Int(1))
            )
          )
        )
        val migration = DynamicMigration(
          MigrationAction.AtMapKeys(Vector(MigrationAction.RenameField("k", "key")))
        )
        val result = migration(dv)
        assertTrue(result.isRight)
      },
      test("AtMapKeys error on non-map") {
        val dv        = DynamicValue.Primitive(PrimitiveValue.Int(1))
        val migration = DynamicMigration(MigrationAction.AtMapKeys(Vector.empty))
        val result    = migration(dv)
        assertTrue(result.isLeft)
      },
      test("AtMapValues transforms map values") {
        val dv = DynamicValue.Map(
          Chunk(
            (
              DynamicValue.Primitive(PrimitiveValue.String("key")),
              DynamicValue.Record(Chunk(("v", DynamicValue.Primitive(PrimitiveValue.Int(1)))))
            )
          )
        )
        val migration = DynamicMigration(
          MigrationAction.AtMapValues(Vector(MigrationAction.RenameField("v", "value")))
        )
        val result = migration(dv)
        assertTrue(result.isRight)
      },
      test("AtMapValues error on non-map") {
        val dv        = DynamicValue.Primitive(PrimitiveValue.Int(1))
        val migration = DynamicMigration(MigrationAction.AtMapValues(Vector.empty))
        val result    = migration(dv)
        assertTrue(result.isLeft)
      },
      test("RenameCase renames matching variant") {
        val dv        = DynamicValue.Variant("OldName", DynamicValue.Record(Chunk.empty))
        val migration = DynamicMigration(MigrationAction.RenameCase("OldName", "NewName"))
        val result    = migration(dv)
        assertTrue(result.isRight)
        val variant = result.toOption.get.asInstanceOf[DynamicValue.Variant]
        assertTrue(variant.caseName == Some("NewName"))
      },
      test("RenameCase leaves non-matching variant unchanged") {
        val dv        = DynamicValue.Variant("OtherCase", DynamicValue.Record(Chunk.empty))
        val migration = DynamicMigration(MigrationAction.RenameCase("OldName", "NewName"))
        val result    = migration(dv)
        assertTrue(result == Right(dv))
      },
      test("RenameCase leaves non-variant unchanged") {
        val dv        = DynamicValue.Primitive(PrimitiveValue.Int(1))
        val migration = DynamicMigration(MigrationAction.RenameCase("OldName", "NewName"))
        val result    = migration(dv)
        assertTrue(result == Right(dv))
      },
      test("TransformCase transforms matching variant") {
        val dv = DynamicValue.Variant(
          "MyCase",
          DynamicValue.Record(Chunk(("a", DynamicValue.Primitive(PrimitiveValue.String("hello")))))
        )
        val migration = DynamicMigration(
          MigrationAction.TransformCase("MyCase", Vector(MigrationAction.RenameField("a", "b")))
        )
        val result = migration(dv)
        assertTrue(result.isRight)
      },
      test("TransformCase leaves non-matching variant unchanged") {
        val dv        = DynamicValue.Variant("OtherCase", DynamicValue.Record(Chunk.empty))
        val migration = DynamicMigration(MigrationAction.TransformCase("MyCase", Vector.empty))
        val result    = migration(dv)
        assertTrue(result == Right(dv))
      },
      test("TransformElements transforms sequence elements") {
        val dv = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(2))
          )
        )
        val migration = DynamicMigration(
          MigrationAction.TransformElements(ResolvedExpr.Convert("int", "string"), None)
        )
        val result = migration(dv)
        assertTrue(result.isRight)
        val seq = result.toOption.get.asInstanceOf[DynamicValue.Sequence]
        assertTrue(seq.elements.forall(_.isInstanceOf[DynamicValue.Primitive]))
      },
      test("TransformElements error on non-sequence") {
        val dv        = DynamicValue.Primitive(PrimitiveValue.Int(1))
        val migration = DynamicMigration(MigrationAction.TransformElements(ResolvedExpr.Identity, None))
        val result    = migration(dv)
        assertTrue(result.isLeft)
      },
      test("TransformKeys transforms map keys") {
        val dv = DynamicValue.Map(
          Chunk(
            (
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
              DynamicValue.Primitive(PrimitiveValue.String("one"))
            )
          )
        )
        val migration = DynamicMigration(
          MigrationAction.TransformKeys(ResolvedExpr.Convert("int", "string"), None)
        )
        val result = migration(dv)
        assertTrue(result.isRight)
      },
      test("TransformKeys error on non-map") {
        val dv        = DynamicValue.Primitive(PrimitiveValue.Int(1))
        val migration = DynamicMigration(MigrationAction.TransformKeys(ResolvedExpr.Identity, None))
        val result    = migration(dv)
        assertTrue(result.isLeft)
      },
      test("TransformValues transforms map values") {
        val dv = DynamicValue.Map(
          Chunk(
            (
              DynamicValue.Primitive(PrimitiveValue.String("key")),
              DynamicValue.Primitive(PrimitiveValue.Int(42))
            )
          )
        )
        val migration = DynamicMigration(
          MigrationAction.TransformValues(ResolvedExpr.Convert("int", "string"), None)
        )
        val result = migration(dv)
        assertTrue(result.isRight)
      },
      test("TransformValues error on non-map") {
        val dv        = DynamicValue.Primitive(PrimitiveValue.Int(1))
        val migration = DynamicMigration(MigrationAction.TransformValues(ResolvedExpr.Identity, None))
        val result    = migration(dv)
        assertTrue(result.isLeft)
      },
      test("JoinFields combines fields") {
        val dv = DynamicValue.Record(
          Chunk(
            ("first", DynamicValue.Primitive(PrimitiveValue.String("Hello"))),
            ("second", DynamicValue.Primitive(PrimitiveValue.String("World")))
          )
        )
        val migration = DynamicMigration(
          MigrationAction.JoinFields(
            Vector("first", "second"),
            "combined",
            ResolvedExpr.Concat(
              Vector(ResolvedExpr.FieldAccess("first"), ResolvedExpr.FieldAccess("second")),
              " "
            ),
            None
          )
        )
        val result = migration(dv)
        assertTrue(result.isRight)
        val record = result.toOption.get.asInstanceOf[DynamicValue.Record]
        assertTrue(record.fields.exists(_._1 == "combined"))
        assertTrue(!record.fields.exists(_._1 == "first"))
        assertTrue(!record.fields.exists(_._1 == "second"))
      },
      test("JoinFields error on missing source field") {
        val dv = DynamicValue.Record(
          Chunk(("first", DynamicValue.Primitive(PrimitiveValue.String("Hello"))))
        )
        val migration = DynamicMigration(
          MigrationAction.JoinFields(Vector("first", "missing"), "combined", ResolvedExpr.Identity, None)
        )
        val result = migration(dv)
        assertTrue(result.isLeft)
      },
      test("JoinFields error on non-record") {
        val dv        = DynamicValue.Primitive(PrimitiveValue.Int(1))
        val migration = DynamicMigration(MigrationAction.JoinFields(Vector("a"), "b", ResolvedExpr.Identity, None))
        val result    = migration(dv)
        assertTrue(result.isLeft)
      },
      test("SplitField splits record result") {
        val dv = DynamicValue.Record(
          Chunk(("fullName", DynamicValue.Primitive(PrimitiveValue.String("John Doe"))))
        )
        val splitter = ResolvedExpr.Literal(
          DynamicValue.Record(
            Chunk(
              ("first", DynamicValue.Primitive(PrimitiveValue.String("John"))),
              ("last", DynamicValue.Primitive(PrimitiveValue.String("Doe")))
            )
          )
        )
        val migration = DynamicMigration(
          MigrationAction.SplitField("fullName", Vector("first", "last"), splitter, None)
        )
        val result = migration(dv)
        assertTrue(result.isRight)
        val record = result.toOption.get.asInstanceOf[DynamicValue.Record]
        assertTrue(record.fields.exists(_._1 == "first"))
        assertTrue(record.fields.exists(_._1 == "last"))
        assertTrue(!record.fields.exists(_._1 == "fullName"))
      },
      test("SplitField splits sequence result") {
        val dv = DynamicValue.Record(
          Chunk(("fullName", DynamicValue.Primitive(PrimitiveValue.String("John Doe"))))
        )
        val splitter = ResolvedExpr.Literal(
          DynamicValue.Sequence(
            Chunk(
              DynamicValue.Primitive(PrimitiveValue.String("John")),
              DynamicValue.Primitive(PrimitiveValue.String("Doe"))
            )
          )
        )
        val migration = DynamicMigration(
          MigrationAction.SplitField("fullName", Vector("first", "last"), splitter, None)
        )
        val result = migration(dv)
        assertTrue(result.isRight)
      },
      test("SplitField error on missing source field") {
        val dv        = DynamicValue.Record(Chunk.empty)
        val migration =
          DynamicMigration(MigrationAction.SplitField("missing", Vector("a", "b"), ResolvedExpr.Identity, None))
        val result = migration(dv)
        assertTrue(result.isLeft)
      },
      test("SplitField error on non-record") {
        val dv        = DynamicValue.Primitive(PrimitiveValue.Int(1))
        val migration =
          DynamicMigration(MigrationAction.SplitField("field", Vector("a", "b"), ResolvedExpr.Identity, None))
        val result = migration(dv)
        assertTrue(result.isLeft)
      },
      test("SplitField error on wrong splitter result type") {
        val dv = DynamicValue.Record(
          Chunk(("field", DynamicValue.Primitive(PrimitiveValue.String("value"))))
        )
        val splitter  = ResolvedExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        val migration = DynamicMigration(MigrationAction.SplitField("field", Vector("a", "b"), splitter, None))
        val result    = migration(dv)
        assertTrue(result.isLeft)
      },
      test("SplitField error on missing target fields in splitter result") {
        val dv = DynamicValue.Record(
          Chunk(("field", DynamicValue.Primitive(PrimitiveValue.String("value"))))
        )
        val splitter = ResolvedExpr.Literal(
          DynamicValue.Record(Chunk(("wrong", DynamicValue.Primitive(PrimitiveValue.String("val")))))
        )
        val migration = DynamicMigration(MigrationAction.SplitField("field", Vector("a", "b"), splitter, None))
        val result    = migration(dv)
        assertTrue(result.isLeft)
      },
      test("error paths for record operations") {
        val nonRecord = DynamicValue.Primitive(PrimitiveValue.Int(1))

        val addResult =
          DynamicMigration(MigrationAction.AddField("f", ResolvedExpr.Literal(DynamicValue.Null)))(nonRecord)
        val dropResult      = DynamicMigration(MigrationAction.DropField("f", None))(nonRecord)
        val renameResult    = DynamicMigration(MigrationAction.RenameField("a", "b"))(nonRecord)
        val transformResult =
          DynamicMigration(MigrationAction.TransformField("f", ResolvedExpr.Identity, None))(nonRecord)
        val atFieldResult = DynamicMigration(MigrationAction.AtField("f", Vector.empty))(nonRecord)

        assertTrue(addResult.isLeft)
        assertTrue(dropResult.isLeft)
        assertTrue(renameResult.isLeft)
        assertTrue(transformResult.isLeft)
        assertTrue(atFieldResult.isLeft)
      },
      test("TransformField error on missing field") {
        val dv        = DynamicValue.Record(Chunk.empty)
        val migration = DynamicMigration(MigrationAction.TransformField("missing", ResolvedExpr.Identity, None))
        val result    = migration(dv)
        assertTrue(result.isLeft)
      },
      test("AtField error on missing field") {
        val dv        = DynamicValue.Record(Chunk.empty)
        val migration = DynamicMigration(MigrationAction.AtField("missing", Vector.empty))
        val result    = migration(dv)
        assertTrue(result.isLeft)
      },
      test("AtElements error propagation") {
        val dv = DynamicValue.Sequence(
          Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        val migration = DynamicMigration(
          MigrationAction.AtElements(
            Vector(MigrationAction.RenameField("missing", "other"))
          )
        )
        val result = migration(dv)
        assertTrue(result.isLeft)
      },
      test("AtMapKeys error propagation") {
        val dv = DynamicValue.Map(
          Chunk(
            (DynamicValue.Primitive(PrimitiveValue.Int(1)), DynamicValue.Primitive(PrimitiveValue.String("one")))
          )
        )
        val migration = DynamicMigration(
          MigrationAction.AtMapKeys(Vector(MigrationAction.RenameField("missing", "other")))
        )
        val result = migration(dv)
        assertTrue(result.isLeft)
      },
      test("AtMapValues error propagation") {
        val dv = DynamicValue.Map(
          Chunk(
            (DynamicValue.Primitive(PrimitiveValue.String("key")), DynamicValue.Primitive(PrimitiveValue.Int(1)))
          )
        )
        val migration = DynamicMigration(
          MigrationAction.AtMapValues(Vector(MigrationAction.RenameField("missing", "other")))
        )
        val result = migration(dv)
        assertTrue(result.isLeft)
      },
      test("TransformElements error propagation") {
        val dv = DynamicValue.Sequence(
          Chunk(DynamicValue.Primitive(PrimitiveValue.String("not a number")))
        )
        val migration = DynamicMigration(
          MigrationAction.TransformElements(ResolvedExpr.Convert("string", "int"), None)
        )
        val result = migration(dv)
        assertTrue(result.isLeft)
      },
      test("TransformKeys error propagation") {
        val dv = DynamicValue.Map(
          Chunk(
            (
              DynamicValue.Primitive(PrimitiveValue.String("not a number")),
              DynamicValue.Primitive(PrimitiveValue.Int(1))
            )
          )
        )
        val migration = DynamicMigration(
          MigrationAction.TransformKeys(ResolvedExpr.Convert("string", "int"), None)
        )
        val result = migration(dv)
        assertTrue(result.isLeft)
      },
      test("TransformValues error propagation") {
        val dv = DynamicValue.Map(
          Chunk(
            (
              DynamicValue.Primitive(PrimitiveValue.String("key")),
              DynamicValue.Primitive(PrimitiveValue.String("not a number"))
            )
          )
        )
        val migration = DynamicMigration(
          MigrationAction.TransformValues(ResolvedExpr.Convert("string", "int"), None)
        )
        val result = migration(dv)
        assertTrue(result.isLeft)
      },
      test("Sequence action error propagation") {
        val dv        = DynamicValue.Record(Chunk.empty)
        val migration = DynamicMigration(
          MigrationAction.Sequence(
            Vector(MigrationAction.TransformField("missing", ResolvedExpr.Identity, None))
          )
        )
        val result = migration(dv)
        assertTrue(result.isLeft)
      },
      test("toString shows actions") {
        val migration = DynamicMigration(MigrationAction.RenameField("a", "b"))
        assertTrue(migration.toString.contains("RenameField"))
      }
    ),
    suite("ResolvedExpr Extended")(
      test("PathAccess extracts nested value") {
        val dv = DynamicValue.Record(
          Chunk(
            (
              "person",
              DynamicValue.Record(
                Chunk(("name", DynamicValue.Primitive(PrimitiveValue.String("John"))))
              )
            )
          )
        )
        val expr   = ResolvedExpr.PathAccess(DynamicOptic.root.field("person").field("name"))
        val result = expr.eval(dv, DynamicOptic.root)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("John"))))
      },
      test("PathAccess error on invalid path") {
        val dv     = DynamicValue.Record(Chunk.empty)
        val expr   = ResolvedExpr.PathAccess(DynamicOptic.root.field("missing"))
        val result = expr.eval(dv, DynamicOptic.root)
        assertTrue(result.isLeft)
      },
      test("PathAccess reverse is None") {
        val expr = ResolvedExpr.PathAccess(DynamicOptic.root)
        assertTrue(expr.reverse.isEmpty)
      },
      test("DefaultValue fails without schema context") {
        val result = ResolvedExpr.DefaultValue.eval(DynamicValue.Null, DynamicOptic.root)
        assertTrue(result.isLeft)
      },
      test("DefaultValue reverse is itself") {
        assertTrue(ResolvedExpr.DefaultValue.reverse == Some(ResolvedExpr.DefaultValue))
      },
      test("IfThenElse evaluates then branch on true") {
        val expr = ResolvedExpr.IfThenElse(
          ResolvedExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))),
          ResolvedExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("yes"))),
          ResolvedExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("no")))
        )
        val result = expr.eval(DynamicValue.Null, DynamicOptic.root)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("yes"))))
      },
      test("IfThenElse evaluates else branch on false") {
        val expr = ResolvedExpr.IfThenElse(
          ResolvedExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(false))),
          ResolvedExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("yes"))),
          ResolvedExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("no")))
        )
        val result = expr.eval(DynamicValue.Null, DynamicOptic.root)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("no"))))
      },
      test("IfThenElse treats Null as false") {
        val expr = ResolvedExpr.IfThenElse(
          ResolvedExpr.Literal(DynamicValue.Null),
          ResolvedExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("yes"))),
          ResolvedExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("no")))
        )
        val result = expr.eval(DynamicValue.Null, DynamicOptic.root)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("no"))))
      },
      test("IfThenElse treats non-boolean as true") {
        val expr = ResolvedExpr.IfThenElse(
          ResolvedExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
          ResolvedExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("yes"))),
          ResolvedExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("no")))
        )
        val result = expr.eval(DynamicValue.Null, DynamicOptic.root)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.String("yes"))))
      },
      test("IfThenElse reverse") {
        val expr = ResolvedExpr.IfThenElse(
          ResolvedExpr.Identity,
          ResolvedExpr.Identity,
          ResolvedExpr.Identity
        )
        assertTrue(expr.reverse.isDefined)
      },
      test("IfThenElse reverse is None when branches not reversible") {
        val expr = ResolvedExpr.IfThenElse(
          ResolvedExpr.Identity,
          ResolvedExpr.FieldAccess("a"),
          ResolvedExpr.Identity
        )
        assertTrue(expr.reverse.isEmpty)
      },
      test("WrapSome wraps value in Some variant") {
        val expr   = ResolvedExpr.WrapSome(ResolvedExpr.Identity)
        val result = expr.eval(DynamicValue.Primitive(PrimitiveValue.Int(42)), DynamicOptic.root)
        assertTrue(result.isRight)
        val variant = result.toOption.get.asInstanceOf[DynamicValue.Variant]
        assertTrue(variant.caseName == Some("Some"))
      },
      test("WrapSome reverse is UnwrapSome") {
        val expr = ResolvedExpr.WrapSome(ResolvedExpr.Identity)
        val rev  = expr.reverse
        assertTrue(rev.isDefined)
        assertTrue(rev.get.isInstanceOf[ResolvedExpr.UnwrapSome])
      },
      test("UnwrapSome unwraps Some variant") {
        val someValue = DynamicValue.Variant(
          "Some",
          DynamicValue.Record(Chunk(("value", DynamicValue.Primitive(PrimitiveValue.Int(42)))))
        )
        val expr   = ResolvedExpr.UnwrapSome(ResolvedExpr.Identity)
        val result = expr.eval(someValue, DynamicOptic.root)
        assertTrue(result == Right(DynamicValue.Primitive(PrimitiveValue.Int(42))))
      },
      test("UnwrapSome error on None/Null") {
        val expr   = ResolvedExpr.UnwrapSome(ResolvedExpr.Identity)
        val result = expr.eval(DynamicValue.Null, DynamicOptic.root)
        assertTrue(result.isLeft)
      },
      test("UnwrapSome error on non-Some variant") {
        val expr   = ResolvedExpr.UnwrapSome(ResolvedExpr.Identity)
        val result = expr.eval(DynamicValue.Primitive(PrimitiveValue.Int(1)), DynamicOptic.root)
        assertTrue(result.isLeft)
      },
      test("UnwrapSome error on Some without value field") {
        val someValue = DynamicValue.Variant("Some", DynamicValue.Record(Chunk.empty))
        val expr      = ResolvedExpr.UnwrapSome(ResolvedExpr.Identity)
        val result    = expr.eval(someValue, DynamicOptic.root)
        assertTrue(result.isLeft)
      },
      test("UnwrapSome reverse is WrapSome") {
        val expr = ResolvedExpr.UnwrapSome(ResolvedExpr.Identity)
        val rev  = expr.reverse
        assertTrue(rev.isDefined)
        assertTrue(rev.get.isInstanceOf[ResolvedExpr.WrapSome])
      },
      test("GetNone returns None variant") {
        val expr   = ResolvedExpr.GetNone
        val result = expr.eval(DynamicValue.Null, DynamicOptic.root)
        assertTrue(result.isRight)
        val variant = result.toOption.get.asInstanceOf[DynamicValue.Variant]
        assertTrue(variant.caseName == Some("None"))
      },
      test("GetNone reverse is None") {
        assertTrue(ResolvedExpr.GetNone.reverse.isEmpty)
      },
      test("Literal reverse is itself") {
        val expr = ResolvedExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        assertTrue(expr.reverse == Some(expr))
      },
      test("Identity reverse is itself") {
        assertTrue(ResolvedExpr.Identity.reverse == Some(ResolvedExpr.Identity))
      },
      test("FieldAccess reverse is None") {
        val expr = ResolvedExpr.FieldAccess("field")
        assertTrue(expr.reverse.isEmpty)
      },
      test("FieldAccess error on non-record") {
        val expr   = ResolvedExpr.FieldAccess("field")
        val result = expr.eval(DynamicValue.Primitive(PrimitiveValue.Int(1)), DynamicOptic.root)
        assertTrue(result.isLeft)
      },
      test("Convert reverse swaps types") {
        val expr = ResolvedExpr.Convert("int", "string")
        val rev  = expr.reverse
        assertTrue(rev == Some(ResolvedExpr.Convert("string", "int")))
      },
      test("Convert error on failed conversion") {
        val expr   = ResolvedExpr.Convert("string", "int")
        val result = expr.eval(DynamicValue.Primitive(PrimitiveValue.String("not a number")), DynamicOptic.root)
        assertTrue(result.isLeft)
      },
      test("Concat error propagation") {
        val expr = ResolvedExpr.Concat(
          Vector(ResolvedExpr.FieldAccess("missing")),
          ""
        )
        val result = expr.eval(DynamicValue.Record(Chunk.empty), DynamicOptic.root)
        assertTrue(result.isLeft)
      },
      test("Concat reverse is None") {
        val expr = ResolvedExpr.Concat(Vector.empty, "")
        assertTrue(expr.reverse.isEmpty)
      },
      test("smart constructors") {
        val lit  = ResolvedExpr.literalDynamic(DynamicValue.Null)
        val id   = ResolvedExpr.identity
        val fld  = ResolvedExpr.field("name")
        val pth  = ResolvedExpr.path(DynamicOptic.root)
        val conv = ResolvedExpr.convert("int", "string")
        val cat  = ResolvedExpr.concat(ResolvedExpr.Identity)
        val catW = ResolvedExpr.concatWith(" ")(ResolvedExpr.Identity)
        val def_ = ResolvedExpr.defaultValue
        val ite  = ResolvedExpr.ifThenElse(ResolvedExpr.Identity, ResolvedExpr.Identity, ResolvedExpr.Identity)
        val wrp  = ResolvedExpr.wrapSome(ResolvedExpr.Identity)
        val unw  = ResolvedExpr.unwrapSome(ResolvedExpr.Identity)
        val non  = ResolvedExpr.none

        assertTrue(lit.isInstanceOf[ResolvedExpr.Literal])
        assertTrue(id == ResolvedExpr.Identity)
        assertTrue(fld.isInstanceOf[ResolvedExpr.FieldAccess])
        assertTrue(pth.isInstanceOf[ResolvedExpr.PathAccess])
        assertTrue(conv.isInstanceOf[ResolvedExpr.Convert])
        assertTrue(cat.isInstanceOf[ResolvedExpr.Concat])
        assertTrue(catW.isInstanceOf[ResolvedExpr.Concat])
        assertTrue(def_ == ResolvedExpr.DefaultValue)
        assertTrue(ite.isInstanceOf[ResolvedExpr.IfThenElse])
        assertTrue(wrp.isInstanceOf[ResolvedExpr.WrapSome])
        assertTrue(unw.isInstanceOf[ResolvedExpr.UnwrapSome])
        assertTrue(non == ResolvedExpr.GetNone)
      }
    ),
    suite("MigrationAction Extended")(
      test("TransformField reverse") {
        val action  = MigrationAction.TransformField("f", ResolvedExpr.Identity, Some(ResolvedExpr.Identity))
        val reverse = action.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.TransformField])
      },
      test("TransformField reverse without explicit reverse transform") {
        val action  = MigrationAction.TransformField("f", ResolvedExpr.Convert("int", "string"), None)
        val reverse = action.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.TransformField])
      },
      test("MandateField reverse is OptionalizeField") {
        val action  = MigrationAction.MandateField("f", ResolvedExpr.Literal(DynamicValue.Null))
        val reverse = action.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.OptionalizeField])
      },
      test("OptionalizeField reverse is MandateField") {
        val action  = MigrationAction.OptionalizeField("f")
        val reverse = action.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.MandateField])
      },
      test("ChangeFieldType reverse") {
        val action  = MigrationAction.ChangeFieldType("f", ResolvedExpr.Convert("int", "string"))
        val reverse = action.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.ChangeFieldType])
      },
      test("KeepField reverse is itself") {
        val action  = MigrationAction.KeepField("f")
        val reverse = action.reverse
        assertTrue(reverse == MigrationAction.KeepField("f"))
      },
      test("AtCase reverse") {
        val action  = MigrationAction.AtCase("Case", Vector(MigrationAction.RenameField("a", "b")))
        val reverse = action.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.AtCase])
        val atCase = reverse.asInstanceOf[MigrationAction.AtCase]
        assertTrue(atCase.actions.head == MigrationAction.RenameField("b", "a"))
      },
      test("AtElements reverse") {
        val action  = MigrationAction.AtElements(Vector(MigrationAction.RenameField("a", "b")))
        val reverse = action.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.AtElements])
      },
      test("AtMapKeys reverse") {
        val action  = MigrationAction.AtMapKeys(Vector(MigrationAction.RenameField("a", "b")))
        val reverse = action.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.AtMapKeys])
      },
      test("AtMapValues reverse") {
        val action  = MigrationAction.AtMapValues(Vector(MigrationAction.RenameField("a", "b")))
        val reverse = action.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.AtMapValues])
      },
      test("RenameCase reverse") {
        val action  = MigrationAction.RenameCase("Old", "New")
        val reverse = action.reverse
        assertTrue(reverse == MigrationAction.RenameCase("New", "Old"))
      },
      test("TransformCase reverse") {
        val action  = MigrationAction.TransformCase("Case", Vector(MigrationAction.RenameField("a", "b")))
        val reverse = action.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.TransformCase])
      },
      test("TransformElements reverse") {
        val action = MigrationAction.TransformElements(
          ResolvedExpr.Convert("int", "string"),
          Some(ResolvedExpr.Convert("string", "int"))
        )
        val reverse = action.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.TransformElements])
      },
      test("TransformKeys reverse") {
        val action  = MigrationAction.TransformKeys(ResolvedExpr.Identity, None)
        val reverse = action.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.TransformKeys])
      },
      test("TransformValues reverse") {
        val action  = MigrationAction.TransformValues(ResolvedExpr.Identity, None)
        val reverse = action.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.TransformValues])
      },
      test("JoinFields reverse is SplitField") {
        val action = MigrationAction.JoinFields(
          Vector("a", "b"),
          "c",
          ResolvedExpr.Identity,
          Some(ResolvedExpr.Identity)
        )
        val reverse = action.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.SplitField])
      },
      test("JoinFields reverse without splitter") {
        val action  = MigrationAction.JoinFields(Vector("a", "b"), "c", ResolvedExpr.Identity, None)
        val reverse = action.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.SplitField])
      },
      test("SplitField reverse is JoinFields") {
        val action = MigrationAction.SplitField(
          "c",
          Vector("a", "b"),
          ResolvedExpr.Identity,
          Some(ResolvedExpr.Identity)
        )
        val reverse = action.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.JoinFields])
      },
      test("SplitField reverse without combiner") {
        val action  = MigrationAction.SplitField("c", Vector("a", "b"), ResolvedExpr.Identity, None)
        val reverse = action.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.JoinFields])
      },
      test("Sequence reverse") {
        val action = MigrationAction.Sequence(
          Vector(MigrationAction.RenameField("a", "b"), MigrationAction.RenameField("c", "d"))
        )
        val reverse = action.reverse
        assertTrue(reverse.isInstanceOf[MigrationAction.Sequence])
        val seq = reverse.asInstanceOf[MigrationAction.Sequence]
        assertTrue(seq.actions(0) == MigrationAction.RenameField("d", "c"))
        assertTrue(seq.actions(1) == MigrationAction.RenameField("b", "a"))
      },
      test("smart constructors") {
        val add       = MigrationAction.addField("f", ResolvedExpr.Literal(DynamicValue.Null))
        val drop1     = MigrationAction.dropField("f")
        val drop2     = MigrationAction.dropField("f", ResolvedExpr.Literal(DynamicValue.Null))
        val rename    = MigrationAction.renameField("a", "b")
        val trans1    = MigrationAction.transformField("f", ResolvedExpr.Identity)
        val trans2    = MigrationAction.transformField("f", ResolvedExpr.Identity, ResolvedExpr.Identity)
        val mandate   = MigrationAction.mandateField("f", ResolvedExpr.Literal(DynamicValue.Null))
        val optional  = MigrationAction.optionalizeField("f")
        val change    = MigrationAction.changeFieldType("f", ResolvedExpr.Identity)
        val keep      = MigrationAction.keepField("f")
        val atF       = MigrationAction.atField("f")(MigrationAction.Identity)
        val atC       = MigrationAction.atCase("c")(MigrationAction.Identity)
        val atE       = MigrationAction.atElements(MigrationAction.Identity)
        val atMK      = MigrationAction.atMapKeys(MigrationAction.Identity)
        val atMV      = MigrationAction.atMapValues(MigrationAction.Identity)
        val renameC   = MigrationAction.renameCase("a", "b")
        val transC    = MigrationAction.transformCase("c")(MigrationAction.Identity)
        val transElem = MigrationAction.transformElements(ResolvedExpr.Identity)
        val transK    = MigrationAction.transformKeys(ResolvedExpr.Identity)
        val transV    = MigrationAction.transformValues(ResolvedExpr.Identity)
        val join      = MigrationAction.joinFields(Vector("a", "b"), "c", ResolvedExpr.Identity)
        val split     = MigrationAction.splitField("c", Vector("a", "b"), ResolvedExpr.Identity)
        val seq       = MigrationAction.sequence(MigrationAction.Identity)
        val id        = MigrationAction.identity

        assertTrue(add.isInstanceOf[MigrationAction.AddField])
        assertTrue(drop1.isInstanceOf[MigrationAction.DropField])
        assertTrue(drop2.isInstanceOf[MigrationAction.DropField])
        assertTrue(rename.isInstanceOf[MigrationAction.RenameField])
        assertTrue(trans1.isInstanceOf[MigrationAction.TransformField])
        assertTrue(trans2.isInstanceOf[MigrationAction.TransformField])
        assertTrue(mandate.isInstanceOf[MigrationAction.MandateField])
        assertTrue(optional.isInstanceOf[MigrationAction.OptionalizeField])
        assertTrue(change.isInstanceOf[MigrationAction.ChangeFieldType])
        assertTrue(keep.isInstanceOf[MigrationAction.KeepField])
        assertTrue(atF.isInstanceOf[MigrationAction.AtField])
        assertTrue(atC.isInstanceOf[MigrationAction.AtCase])
        assertTrue(atE.isInstanceOf[MigrationAction.AtElements])
        assertTrue(atMK.isInstanceOf[MigrationAction.AtMapKeys])
        assertTrue(atMV.isInstanceOf[MigrationAction.AtMapValues])
        assertTrue(renameC.isInstanceOf[MigrationAction.RenameCase])
        assertTrue(transC.isInstanceOf[MigrationAction.TransformCase])
        assertTrue(transElem.isInstanceOf[MigrationAction.TransformElements])
        assertTrue(transK.isInstanceOf[MigrationAction.TransformKeys])
        assertTrue(transV.isInstanceOf[MigrationAction.TransformValues])
        assertTrue(join.isInstanceOf[MigrationAction.JoinFields])
        assertTrue(split.isInstanceOf[MigrationAction.SplitField])
        assertTrue(seq.isInstanceOf[MigrationAction.Sequence])
        assertTrue(id == MigrationAction.Identity)
      }
    ),
    suite("MigrationError")(
      test("MissingField message") {
        val error = MigrationError.missingField(DynamicOptic.root, "fieldName")
        assertTrue(error.getMessage.contains("fieldName"))
        assertTrue(error.message.contains("Missing field"))
      },
      test("ConversionFailed message") {
        val error = MigrationError.conversionFailed(DynamicOptic.root, "int", "string", "reason")
        assertTrue(error.getMessage.contains("int"))
        assertTrue(error.getMessage.contains("string"))
        assertTrue(error.getMessage.contains("reason"))
      },
      test("UnexpectedStructure message") {
        val error = MigrationError.unexpectedStructure(DynamicOptic.root, "Record", "Primitive")
        assertTrue(error.getMessage.contains("Record"))
        assertTrue(error.getMessage.contains("Primitive"))
      },
      test("MissingDefault message") {
        val error = MigrationError.missingDefault(DynamicOptic.root, "field")
        assertTrue(error.getMessage.contains("field"))
        assertTrue(error.message.contains("default"))
      },
      test("UnknownCase message") {
        val error = MigrationError.unknownCase(DynamicOptic.root, "CaseName")
        assertTrue(error.getMessage.contains("CaseName"))
      },
      test("ExpressionFailed message") {
        val error = MigrationError.expressionFailed(DynamicOptic.root, "expr", "reason")
        assertTrue(error.getMessage.contains("expr"))
        assertTrue(error.getMessage.contains("reason"))
      },
      test("IncompatibleSchemas message") {
        val error = MigrationError.incompatibleSchemas(DynamicOptic.root, "TypeA", "TypeB")
        assertTrue(error.getMessage.contains("TypeA"))
        assertTrue(error.getMessage.contains("TypeB"))
      }
    ),
    suite("PrimitiveConversions Extended")(
      test("convert handles Null") {
        val result = PrimitiveConversions.convert(DynamicValue.Null, "any", "string")
        assertTrue(result == Right(DynamicValue.Null))
      },
      test("convert error on non-primitive") {
        val result = PrimitiveConversions.convert(DynamicValue.Record(Chunk.empty), "record", "string")
        assertTrue(result.isLeft)
      },
      test("unknown target type") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Int(1), "unknown_type")
        assertTrue(result.isLeft)
      },
      test("float to string") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Float(3.14f), "string")
        assertTrue(result.isRight)
      },
      test("short to string") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Short(42.toShort), "string")
        assertTrue(result.isRight)
      },
      test("byte to string") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Byte(42.toByte), "string")
        assertTrue(result.isRight)
      },
      test("char to string") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Char('A'), "string")
        assertTrue(result.isRight)
      },
      test("bigint to string") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.BigInt(BigInt(123)), "string")
        assertTrue(result.isRight)
      },
      test("bigdecimal to string") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.BigDecimal(BigDecimal(123.45)), "string")
        assertTrue(result.isRight)
      },
      test("string to long") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.String("123"), "long")
        assertTrue(result == Right(PrimitiveValue.Long(123L)))
      },
      test("string to long fails for non-numeric") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.String("not a number"), "long")
        assertTrue(result.isLeft)
      },
      test("string to double") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.String("3.14"), "double")
        assertTrue(result == Right(PrimitiveValue.Double(3.14)))
      },
      test("string to double fails for non-numeric") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.String("not a number"), "double")
        assertTrue(result.isLeft)
      },
      test("string to float") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.String("3.14"), "float")
        assertTrue(result.isRight)
      },
      test("string to float fails for non-numeric") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.String("not a number"), "float")
        assertTrue(result.isLeft)
      },
      test("string to short") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.String("42"), "short")
        assertTrue(result == Right(PrimitiveValue.Short(42.toShort)))
      },
      test("string to short fails for non-numeric") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.String("not a number"), "short")
        assertTrue(result.isLeft)
      },
      test("string to byte") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.String("42"), "byte")
        assertTrue(result == Right(PrimitiveValue.Byte(42.toByte)))
      },
      test("string to byte fails for non-numeric") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.String("not a number"), "byte")
        assertTrue(result.isLeft)
      },
      test("string to bigint") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.String("123456789"), "bigint")
        assertTrue(result == Right(PrimitiveValue.BigInt(BigInt(123456789))))
      },
      test("string to bigint fails for non-numeric") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.String("not a number"), "bigint")
        assertTrue(result.isLeft)
      },
      test("string to bigdecimal") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.String("123.45"), "bigdecimal")
        assertTrue(result == Right(PrimitiveValue.BigDecimal(BigDecimal("123.45"))))
      },
      test("string to bigdecimal fails for non-numeric") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.String("not a number"), "bigdecimal")
        assertTrue(result.isLeft)
      },
      test("string to char") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.String("A"), "char")
        assertTrue(result == Right(PrimitiveValue.Char('A')))
      },
      test("string to char fails for multi-char string") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.String("AB"), "char")
        assertTrue(result.isLeft)
      },
      test("int to char") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Int(65), "char")
        assertTrue(result == Right(PrimitiveValue.Char('A')))
      },
      test("double to int") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Double(42.7), "int")
        assertTrue(result == Right(PrimitiveValue.Int(42)))
      },
      test("double to float") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Double(3.14), "float")
        assertTrue(result.isRight)
      },
      test("float to int") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Float(42.7f), "int")
        assertTrue(result == Right(PrimitiveValue.Int(42)))
      },
      test("float to long") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Float(42.7f), "long")
        assertTrue(result == Right(PrimitiveValue.Long(42L)))
      },
      test("float to double") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Float(3.14f), "double")
        assertTrue(result.isRight)
      },
      test("short to int") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Short(42.toShort), "int")
        assertTrue(result == Right(PrimitiveValue.Int(42)))
      },
      test("short to long") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Short(42.toShort), "long")
        assertTrue(result == Right(PrimitiveValue.Long(42L)))
      },
      test("short to double") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Short(42.toShort), "double")
        assertTrue(result == Right(PrimitiveValue.Double(42.0)))
      },
      test("short to float") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Short(42.toShort), "float")
        assertTrue(result == Right(PrimitiveValue.Float(42.0f)))
      },
      test("byte to int") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Byte(42.toByte), "int")
        assertTrue(result == Right(PrimitiveValue.Int(42)))
      },
      test("byte to long") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Byte(42.toByte), "long")
        assertTrue(result == Right(PrimitiveValue.Long(42L)))
      },
      test("byte to double") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Byte(42.toByte), "double")
        assertTrue(result == Right(PrimitiveValue.Double(42.0)))
      },
      test("byte to float") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Byte(42.toByte), "float")
        assertTrue(result == Right(PrimitiveValue.Float(42.0f)))
      },
      test("byte to short") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Byte(42.toByte), "short")
        assertTrue(result == Right(PrimitiveValue.Short(42.toShort)))
      },
      test("char to int") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Char('A'), "int")
        assertTrue(result == Right(PrimitiveValue.Int(65)))
      },
      test("char to long") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Char('A'), "long")
        assertTrue(result == Right(PrimitiveValue.Long(65L)))
      },
      test("long to int") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Long(42L), "int")
        assertTrue(result == Right(PrimitiveValue.Int(42)))
      },
      test("long to short") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Long(42L), "short")
        assertTrue(result == Right(PrimitiveValue.Short(42.toShort)))
      },
      test("long to byte") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Long(42L), "byte")
        assertTrue(result == Right(PrimitiveValue.Byte(42.toByte)))
      },
      test("long to float") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Long(42L), "float")
        assertTrue(result == Right(PrimitiveValue.Float(42.0f)))
      },
      test("long to bigint") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Long(42L), "bigint")
        assertTrue(result == Right(PrimitiveValue.BigInt(BigInt(42))))
      },
      test("long to bigdecimal") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Long(42L), "bigdecimal")
        assertTrue(result == Right(PrimitiveValue.BigDecimal(BigDecimal(42))))
      },
      test("int to short") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Int(42), "short")
        assertTrue(result == Right(PrimitiveValue.Short(42.toShort)))
      },
      test("int to byte") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Int(42), "byte")
        assertTrue(result == Right(PrimitiveValue.Byte(42.toByte)))
      },
      test("int to float") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Int(42), "float")
        assertTrue(result == Right(PrimitiveValue.Float(42.0f)))
      },
      test("int to double") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Int(42), "double")
        assertTrue(result == Right(PrimitiveValue.Double(42.0)))
      },
      test("int to long") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Int(42), "long")
        assertTrue(result == Right(PrimitiveValue.Long(42L)))
      },
      test("int to bigdecimal") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Int(42), "bigdecimal")
        assertTrue(result == Right(PrimitiveValue.BigDecimal(BigDecimal(42))))
      },
      test("boolean to string") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Boolean(true), "string")
        assertTrue(result == Right(PrimitiveValue.String("true")))
      },
      test("boolean to long") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Boolean(true), "long")
        assertTrue(result == Right(PrimitiveValue.Long(1L)))
      },
      test("boolean to double") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Boolean(true), "double")
        assertTrue(result == Right(PrimitiveValue.Double(1.0)))
      },
      test("boolean to float") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Boolean(true), "float")
        assertTrue(result == Right(PrimitiveValue.Float(1.0f)))
      },
      test("boolean to short") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Boolean(true), "short")
        assertTrue(result == Right(PrimitiveValue.Short(1.toShort)))
      },
      test("boolean to byte") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Boolean(true), "byte")
        assertTrue(result == Right(PrimitiveValue.Byte(1.toByte)))
      },
      test("string to boolean invalid") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.String("invalid"), "boolean")
        assertTrue(result.isLeft)
      },
      test("bigint to bigdecimal") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.BigInt(BigInt(42)), "bigdecimal")
        assertTrue(result == Right(PrimitiveValue.BigDecimal(BigDecimal(42))))
      },
      test("cannot convert bigdecimal to int") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.BigDecimal(BigDecimal(42.5)), "int")
        assertTrue(result.isLeft)
      },
      test("cannot convert bigint to int") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.BigInt(BigInt(42)), "int")
        assertTrue(result.isLeft)
      },
      test("cannot convert char to boolean") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Char('A'), "boolean")
        assertTrue(result.isLeft)
      },
      test("cannot convert double to char") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Double(65.0), "char")
        assertTrue(result.isLeft)
      },
      test("cannot convert boolean to bigint") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Boolean(true), "bigint")
        assertTrue(result.isLeft)
      },
      test("cannot convert boolean to bigdecimal") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Boolean(true), "bigdecimal")
        assertTrue(result.isLeft)
      },
      test("cannot convert char to bigint") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Char('A'), "bigint")
        assertTrue(result.isLeft)
      },
      test("cannot convert char to bigdecimal") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Char('A'), "bigdecimal")
        assertTrue(result.isLeft)
      },
      test("cannot convert float to bigint") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Float(3.14f), "bigint")
        assertTrue(result.isLeft)
      },
      test("cannot convert double to bigint") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Double(3.14), "bigint")
        assertTrue(result.isLeft)
      },
      test("cannot convert short to bigint") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Short(42.toShort), "bigint")
        assertTrue(result.isLeft)
      },
      test("cannot convert byte to bigint") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Byte(42.toByte), "bigint")
        assertTrue(result.isLeft)
      },
      test("cannot convert short to bigdecimal") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Short(42.toShort), "bigdecimal")
        assertTrue(result.isLeft)
      },
      test("cannot convert byte to bigdecimal") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Byte(42.toByte), "bigdecimal")
        assertTrue(result.isLeft)
      },
      test("cannot convert boolean to char") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Boolean(true), "char")
        assertTrue(result.isLeft)
      },
      test("cannot convert long to char") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Long(65L), "char")
        assertTrue(result.isLeft)
      },
      test("cannot convert short to char") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Short(65.toShort), "char")
        assertTrue(result.isLeft)
      },
      test("cannot convert byte to char") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Byte(65.toByte), "char")
        assertTrue(result.isLeft)
      },
      test("cannot convert float to char") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.Float(65.0f), "char")
        assertTrue(result.isLeft)
      },
      test("cannot convert bigint to char") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.BigInt(BigInt(65)), "char")
        assertTrue(result.isLeft)
      },
      test("cannot convert bigdecimal to char") {
        val result = PrimitiveConversions.convertPrimitive(PrimitiveValue.BigDecimal(BigDecimal(65)), "char")
        assertTrue(result.isLeft)
      }
    ),
    suite("Migration typed wrapper")(
      test("Migration.identity") {
        val migration = Migration.identity[PersonV1]
        val v1        = PersonV1("John", "Doe", 30)
        val result    = migration(v1)
        assertTrue(result == Right(v1))
      },
      test("Migration.apply with varargs actions") {
        val migration = Migration[PersonV1, PersonV1](
          MigrationAction.KeepField("firstName"),
          MigrationAction.KeepField("lastName"),
          MigrationAction.KeepField("age")
        )
        val v1     = PersonV1("John", "Doe", 30)
        val result = migration(v1)
        assertTrue(result == Right(v1))
      },
      test("Migration.fromDynamic") {
        val dynamicMigration = DynamicMigration.identity
        val migration        = Migration.fromDynamic[PersonV1, PersonV1](dynamicMigration)
        val v1               = PersonV1("John", "Doe", 30)
        val result           = migration(v1)
        assertTrue(result == Right(v1))
      },
      test("Migration.builder") {
        val migration = Migration.builder[PersonV1, PersonV1].keepField("firstName").build
        val v1        = PersonV1("John", "Doe", 30)
        val result    = migration(v1)
        assertTrue(result == Right(v1))
      },
      test("Migration.renameField") {
        val migration = Migration.renameField[AddressV1, AddressV1]("street", "streetName")
        assertTrue(migration.actions.length == 1)
      },
      test("Migration.addField") {
        val migration = Migration.addField[AddressV1, AddressV1](
          "zipCode",
          ResolvedExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("00000")))
        )
        assertTrue(migration.actions.length == 1)
      },
      test("Migration.dropField") {
        val migration = Migration.dropField[AddressV1, AddressV1]("street")
        assertTrue(migration.actions.length == 1)
      },
      test("Migration.isEmpty") {
        val empty    = Migration.identity[PersonV1]
        val nonEmpty = Migration.renameField[PersonV1, PersonV1]("firstName", "first")
        assertTrue(!empty.isEmpty)
        assertTrue(!nonEmpty.isEmpty)
      },
      test("Migration.toString") {
        val migration = Migration.identity[PersonV1]
        assertTrue(migration.toString.contains("Migration"))
      },
      test("Migration.andThen alias") {
        val m1     = Migration.identity[PersonV1]
        val m2     = Migration.identity[PersonV1]
        val result = m1.andThen(m2)
        assertTrue(result.actions.length == 2)
      }
    ),
    suite("TypedMigrationBuilder")(
      test("rename field with selectors") {
        val migration = Migration.typed[AddressV1, AddressV2]
          .renameField(_.street, _.streetName)
          .addField(_.zipCode, "00000")
          .keepField(_.city, _.city)
          .build

        val v1     = AddressV1("123 Main St", "Boston")
        val result = migration(v1)
        assertTrue(result == Right(AddressV2("123 Main St", "Boston", "00000")))
      },
      test("keep field tracks source and target") {
        val migration = Migration.typed[PersonV1, PersonV1]
          .keepField(_.firstName, _.firstName)
          .keepField(_.lastName, _.lastName)
          .keepField(_.age, _.age)
          .build

        val v1     = PersonV1("John", "Doe", 30)
        val result = migration(v1)
        assertTrue(result == Right(v1))
      },
      test("drop field with selector") {
        val migration = Migration.typed[PersonV3, PersonV1]
          .dropField(_.email)
          .keepField(_.firstName, _.firstName)
          .keepField(_.lastName, _.lastName)
          .keepField(_.age, _.age)
          .build

        val v3     = PersonV3("John", "Doe", 30, Some("john@example.com"))
        val result = migration(v3)
        assertTrue(result == Right(PersonV1("John", "Doe", 30)))
      },
      test("add field with typed default") {
        val migration = Migration.typed[PersonV1, PersonV3]
          .addFieldExpr(_.email, ResolvedExpr.none)
          .keepField(_.firstName, _.firstName)
          .keepField(_.lastName, _.lastName)
          .keepField(_.age, _.age)
          .build

        val v1     = PersonV1("John", "Doe", 30)
        val result = migration(v1)
        assertTrue(result == Right(PersonV3("John", "Doe", 30, None)))
      },
      test("nested field migration with direct selector") {
        // Direct nested selector support: _.address.street instead of inField wrapper
        val migration = Migration.typed[WithNestedV1, WithNestedV2]
          .renameField(_.address.street, _.address.streetName)
          .addFieldExpr(_.address.zipCode, ResolvedExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("00000"))))
          .keepField(_.name, _.name)
          .keepField(_.address.city, _.address.city)
          .build

        val v1     = WithNestedV1("Person", AddressV1("123 Main St", "Boston"))
        val result = migration(v1)
        assertTrue(result == Right(WithNestedV2("Person", AddressV2("123 Main St", "Boston", "00000"))))
      },
      test("buildPartial skips validation") {
        val migration = Migration.typed[AddressV1, AddressV2]
          .renameField(_.street, _.streetName)
          // Missing: addField for zipCode and keepField for city
          .buildPartial

        // buildPartial doesn't throw, just builds what's there
        assertTrue(migration.actions.nonEmpty)
      },
      test("buildValidated returns error for incomplete migration") {
        val result = Migration.typed[AddressV1, AddressV2]
          .renameField(_.street, _.streetName)
          .buildValidated

        assertTrue(result.isLeft)
      },
      test("buildValidated returns success for complete migration") {
        val result = Migration.typed[AddressV1, AddressV2]
          .renameField(_.street, _.streetName)
          .addField(_.zipCode, "00000")
          .keepField(_.city, _.city)
          .buildValidated

        assertTrue(result.isRight)
      },
      test("transformElements with selector on collection field") {
        // Test that transformElements works with collection field selectors
        val builder = Migration.typed[WithTags, WithTags]
          .keepField(_.name, _.name)
          .transformElements(_.tags, ResolvedExpr.Convert("string", "string"))
          .buildPartial

        assertTrue(builder.actions.nonEmpty)
      },
      test("transformCase for variant types") {
        // Test renameCase on sum type
        val builder = Migration.typed[WithCountry, WithCountry]
          .keepField(_.name, _.name)
          .keepField(_.country, _.country)
          .renameCase("UK", "UnitedKingdom")
          .buildPartial

        val hasCaseAction = builder.actions.exists {
          case MigrationAction.RenameCase(_, from, to) => from == "UK" && to == "UnitedKingdom"
          case _ => false
        }
        assertTrue(hasCaseAction)
      }
    )
  )
}
