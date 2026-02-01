package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

object MigrationSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("Migration")(
    suite("FieldAction")(
      test("Add field adds a new field to a record") {
        val original  = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
        val migration = DynamicMigration.record(_.addField("age", DynamicValue.int(30)))
        val result    = migration(original)

        assertTrue(
          result == Right(
            DynamicValue.Record(
              "name" -> DynamicValue.string("Alice"),
              "age"  -> DynamicValue.int(30)
            )
          )
        )
      },
      test("Remove field removes a field from a record") {
        val original = DynamicValue.Record(
          "name" -> DynamicValue.string("Alice"),
          "age"  -> DynamicValue.int(30)
        )
        val migration = DynamicMigration.record(_.removeField("age", DynamicValue.int(0)))
        val result    = migration(original)

        assertTrue(result == Right(DynamicValue.Record("name" -> DynamicValue.string("Alice"))))
      },
      test("Rename field renames a field in a record") {
        val original  = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
        val migration = DynamicMigration.record(_.renameField("name", "fullName"))
        val result    = migration(original)

        assertTrue(result == Right(DynamicValue.Record("fullName" -> DynamicValue.string("Alice"))))
      },
      test("Transform field applies a transformation to a field value") {
        val original  = DynamicValue.Record("count" -> DynamicValue.int(5))
        val migration = DynamicMigration.record(
          _.transformField(
            "count",
            DynamicValueTransform.numericAdd(10),
            DynamicValueTransform.numericAdd(-10)
          )
        )
        val result = migration(original)

        assertTrue(result == Right(DynamicValue.Record("count" -> DynamicValue.int(15))))
      },
      test("MakeOptional wraps a field value in Some") {
        val original  = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
        val migration = DynamicMigration.record(_.makeFieldOptional("name", DynamicValue.string("")))
        val result    = migration(original)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            val nameField = fields.find(_._1 == "name").map(_._2)
            nameField match {
              case Some(DynamicValue.Variant("Some", _)) => assertTrue(true)
              case _                                     => assertTrue(false)
            }
          case _ => assertTrue(false)
        }
      },
      test("MakeRequired unwraps Some to extract the inner value") {
        val original = DynamicValue.Record(
          "name" -> DynamicValue.Variant("Some", DynamicValue.Record("value" -> DynamicValue.string("Alice")))
        )
        val migration = DynamicMigration.record(_.makeFieldRequired("name", DynamicValue.string("")))
        val result    = migration(original)

        assertTrue(result == Right(DynamicValue.Record("name" -> DynamicValue.string("Alice"))))
      },
      test("MakeRequired uses default for None") {
        val original = DynamicValue.Record(
          "name" -> DynamicValue.Variant("None", DynamicValue.Record())
        )
        val migration = DynamicMigration.record(_.makeFieldRequired("name", DynamicValue.string("default")))
        val result    = migration(original)

        assertTrue(result == Right(DynamicValue.Record("name" -> DynamicValue.string("default"))))
      },
      test("ChangeType converts field value type") {
        val original  = DynamicValue.Record("count" -> DynamicValue.int(42))
        val migration = DynamicMigration.record(
          _.changeFieldType(
            "count",
            PrimitiveConversion.IntToLong,
            PrimitiveConversion.LongToInt
          )
        )
        val result = migration(original)

        assertTrue(result == Right(DynamicValue.Record("count" -> DynamicValue.long(42L))))
      }
    ),
    suite("Chained field actions")(
      test("Multiple field actions are applied in order") {
        val original  = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
        val migration = DynamicMigration.record(
          _.addField("age", DynamicValue.int(30))
            .renameField("name", "fullName")
            .addField("active", DynamicValue.boolean(true))
        )
        val result = migration(original)

        assertTrue(
          result == Right(
            DynamicValue.Record(
              "fullName" -> DynamicValue.string("Alice"),
              "age"      -> DynamicValue.int(30),
              "active"   -> DynamicValue.boolean(true)
            )
          )
        )
      }
    ),
    suite("Nested migrations")(
      test("Apply migration to nested record field") {
        val original = DynamicValue.Record(
          "name"    -> DynamicValue.string("Alice"),
          "address" -> DynamicValue.Record(
            "street" -> DynamicValue.string("123 Main St"),
            "city"   -> DynamicValue.string("NYC")
          )
        )
        val migration = DynamicMigration.record(
          _.nested("address")(
            _.addField("zip", DynamicValue.string("10001"))
          )
        )
        val result = migration(original)

        assertTrue(
          result == Right(
            DynamicValue.Record(
              "name"    -> DynamicValue.string("Alice"),
              "address" -> DynamicValue.Record(
                "street" -> DynamicValue.string("123 Main St"),
                "city"   -> DynamicValue.string("NYC"),
                "zip"    -> DynamicValue.string("10001")
              )
            )
          )
        )
      },
      test("Deeply nested migrations") {
        val original = DynamicValue.Record(
          "person" -> DynamicValue.Record(
            "name"    -> DynamicValue.string("Alice"),
            "contact" -> DynamicValue.Record(
              "email" -> DynamicValue.string("alice@example.com")
            )
          )
        )
        val migration = DynamicMigration.record(
          _.nested("person")(
            _.nested("contact")(
              _.addField("phone", DynamicValue.string("555-1234"))
            )
          )
        )
        val result = migration(original)

        assertTrue(
          result == Right(
            DynamicValue.Record(
              "person" -> DynamicValue.Record(
                "name"    -> DynamicValue.string("Alice"),
                "contact" -> DynamicValue.Record(
                  "email" -> DynamicValue.string("alice@example.com"),
                  "phone" -> DynamicValue.string("555-1234")
                )
              )
            )
          )
        )
      }
    ),
    suite("Sequence migrations")(
      test("Apply migration to each element in a sequence") {
        val original = DynamicValue.Sequence(
          DynamicValue.Record("name" -> DynamicValue.string("Alice")),
          DynamicValue.Record("name" -> DynamicValue.string("Bob"))
        )
        val elementMigration = DynamicMigration.record(_.addField("active", DynamicValue.boolean(true)))
        val migration        = DynamicMigration.sequence(elementMigration)
        val result           = migration(original)

        assertTrue(
          result == Right(
            DynamicValue.Sequence(
              DynamicValue.Record(
                "name"   -> DynamicValue.string("Alice"),
                "active" -> DynamicValue.boolean(true)
              ),
              DynamicValue.Record(
                "name"   -> DynamicValue.string("Bob"),
                "active" -> DynamicValue.boolean(true)
              )
            )
          )
        )
      }
    ),
    suite("Variant migrations")(
      test("Rename a case in a variant") {
        val original = DynamicValue.Variant(
          "OldName",
          DynamicValue.Record("value" -> DynamicValue.int(42))
        )
        val migration = DynamicMigration.variant(_.renameCase("OldName", "NewName"))
        val result    = migration(original)

        assertTrue(
          result == Right(
            DynamicValue.Variant(
              "NewName",
              DynamicValue.Record("value" -> DynamicValue.int(42))
            )
          )
        )
      },
      test("Apply nested migration to variant case") {
        val original = DynamicValue.Variant(
          "User",
          DynamicValue.Record("name" -> DynamicValue.string("Alice"))
        )
        val migration = DynamicMigration.variant(
          _.nested("User")(
            _.addField("role", DynamicValue.string("admin"))
          )
        )
        val result = migration(original)

        assertTrue(
          result == Right(
            DynamicValue.Variant(
              "User",
              DynamicValue.Record(
                "name" -> DynamicValue.string("Alice"),
                "role" -> DynamicValue.string("admin")
              )
            )
          )
        )
      }
    ),
    suite("Reversibility")(
      test("Adding and removing a field are inverses") {
        val original  = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
        val migration = DynamicMigration.record(_.addField("age", DynamicValue.int(30)))
        val migrated  = migration(original)
        val reversed  = migration.reverse(migrated.toOption.get)

        assertTrue(reversed == Right(original))
      },
      test("Renaming a field is reversible") {
        val original  = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
        val migration = DynamicMigration.record(_.renameField("name", "fullName"))
        val migrated  = migration(original)
        val reversed  = migration.reverse(migrated.toOption.get)

        assertTrue(reversed == Right(original))
      },
      test("Complex migration is reversible") {
        val original = DynamicValue.Record(
          "name" -> DynamicValue.string("Alice"),
          "age"  -> DynamicValue.int(30)
        )
        val migration = DynamicMigration.record(
          _.renameField("name", "fullName")
            .addField("active", DynamicValue.boolean(true))
        )
        val migrated = migration(original)
        val reversed = migration.reverse(migrated.toOption.get)

        assertTrue(reversed == Right(original))
      }
    ),
    suite("DynamicValueTransform")(
      test("Identity transform returns the same value") {
        val value  = DynamicValue.int(42)
        val result = DynamicValueTransform.identity(value)
        assertTrue(result == Right(value))
      },
      test("Constant transform returns the constant value") {
        val value    = DynamicValue.int(42)
        val constant = DynamicValue.string("hello")
        val result   = DynamicValueTransform.constant(constant)(value)
        assertTrue(result == Right(constant))
      },
      test("StringAppend appends a suffix") {
        val value  = DynamicValue.string("hello")
        val result = DynamicValueTransform.stringAppend(" world")(value)
        assertTrue(result == Right(DynamicValue.string("hello world")))
      },
      test("StringPrepend prepends a prefix") {
        val value  = DynamicValue.string("world")
        val result = DynamicValueTransform.stringPrepend("hello ")(value)
        assertTrue(result == Right(DynamicValue.string("hello world")))
      },
      test("StringReplace replaces occurrences") {
        val value  = DynamicValue.string("hello world")
        val result = DynamicValueTransform.stringReplace("world", "universe")(value)
        assertTrue(result == Right(DynamicValue.string("hello universe")))
      },
      test("NumericAdd adds to integer") {
        val value  = DynamicValue.int(10)
        val result = DynamicValueTransform.numericAdd(5)(value)
        assertTrue(result == Right(DynamicValue.int(15)))
      },
      test("NumericMultiply multiplies integer") {
        val value  = DynamicValue.int(10)
        val result = DynamicValueTransform.numericMultiply(3)(value)
        assertTrue(result == Right(DynamicValue.int(30)))
      },
      test("WrapInSome wraps value in Some variant") {
        val value  = DynamicValue.int(42)
        val result = DynamicValueTransform.wrapInSome(value)
        result match {
          case Right(DynamicValue.Variant("Some", _)) => assertTrue(true)
          case _                                      => assertTrue(false)
        }
      },
      test("Sequence applies transforms in order") {
        val value     = DynamicValue.int(10)
        val transform = DynamicValueTransform.sequence(
          DynamicValueTransform.numericAdd(5),
          DynamicValueTransform.numericMultiply(2)
        )
        val result = transform(value)
        assertTrue(result == Right(DynamicValue.int(30)))
      }
    ),
    suite("PrimitiveConversion")(
      test("IntToLong converts Int to Long") {
        val value  = DynamicValue.int(42)
        val result = PrimitiveConversion.IntToLong(value)
        assertTrue(result == Right(DynamicValue.long(42L)))
      },
      test("LongToInt converts Long to Int") {
        val value  = DynamicValue.long(42L)
        val result = PrimitiveConversion.LongToInt(value)
        assertTrue(result == Right(DynamicValue.int(42)))
      },
      test("IntToString converts Int to String") {
        val value  = DynamicValue.int(42)
        val result = PrimitiveConversion.IntToString(value)
        assertTrue(result == Right(DynamicValue.string("42")))
      },
      test("StringToInt converts String to Int") {
        val value  = DynamicValue.string("42")
        val result = PrimitiveConversion.StringToInt(value)
        assertTrue(result == Right(DynamicValue.int(42)))
      },
      test("StringToInt fails for non-numeric string") {
        val value  = DynamicValue.string("hello")
        val result = PrimitiveConversion.StringToInt(value)
        assertTrue(result.isLeft)
      },
      test("DoubleToInt truncates decimal part") {
        val value  = DynamicValue.double(42.9)
        val result = PrimitiveConversion.DoubleToInt(value)
        assertTrue(result == Right(DynamicValue.int(42)))
      }
    ),
    suite("MigrationExpr")(
      test("Literal returns constant DynamicValue") {
        val expr   = MigrationExpr.literal(42)
        val result = expr.evalDynamic(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.int(42)))
      },
      test("DefaultValue extracts schema default when available") {
        val schemaWithDefault = Schema[Int].defaultValue(99)
        val expr              = MigrationExpr.DefaultValue[Any](schemaWithDefault)
        val result            = expr.evalDynamic(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.int(99)))
      },
      test("DefaultValue fails when schema has no default") {
        val schemaWithoutDefault = Schema[Int]
        val expr                 = MigrationExpr.DefaultValue[Any](schemaWithoutDefault)
        val result               = expr.evalDynamic(DynamicValue.Null)
        assertTrue(result.isLeft)
      },
      test("FieldAccess retrieves value from input") {
        val input  = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
        val expr   = MigrationExpr.field[Any, String](DynamicOptic.root.field("name"))
        val result = expr.evalDynamic(input)
        assertTrue(result == Right(DynamicValue.string("Alice")))
      },
      test("Transform applies DynamicValueTransform to result") {
        val expr = MigrationExpr.transform[Any, Int, Int](
          MigrationExpr.literal(10),
          DynamicValueTransform.numericMultiply(2)
        )
        val result = expr.evalDynamic(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.int(20)))
      },
      test("Concat joins two string expressions") {
        val expr = MigrationExpr.concat(
          MigrationExpr.literal("Hello"),
          MigrationExpr.literal(" World")
        )
        val result = expr.evalDynamic(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.string("Hello World")))
      },
      test("PrimitiveConvert applies conversion") {
        val expr = MigrationExpr.convert[Any, Int, Long](
          MigrationExpr.literal(42),
          PrimitiveConversion.IntToLong
        )
        val result = expr.evalDynamic(DynamicValue.Null)
        assertTrue(result == Right(DynamicValue.long(42L)))
      }
    ),
    suite("Error handling")(
      test("Returns FieldNotFound when field does not exist") {
        val original  = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
        val migration = DynamicMigration.record(_.removeField("age", DynamicValue.int(0)))
        val result    = migration(original)

        result match {
          case Left(MigrationError.FieldNotFound(_, _)) => assertTrue(true)
          case _                                        => assertTrue(false)
        }
      },
      test("Returns FieldAlreadyExists when adding duplicate field") {
        val original  = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
        val migration = DynamicMigration.record(_.addField("name", DynamicValue.string("Bob")))
        val result    = migration(original)

        result match {
          case Left(MigrationError.FieldAlreadyExists(_, _)) => assertTrue(true)
          case _                                             => assertTrue(false)
        }
      },
      test("Returns TypeMismatch when applying record migration to non-record") {
        val original  = DynamicValue.int(42)
        val migration = DynamicMigration.record(_.addField("name", DynamicValue.string("Alice")))
        val result    = migration(original)

        result match {
          case Left(MigrationError.TypeMismatch(_, _, _)) => assertTrue(true)
          case _                                          => assertTrue(false)
        }
      }
    ),
    suite("Composition")(
      test("andThen composes two migrations") {
        val original   = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
        val migration1 = DynamicMigration.record(_.addField("age", DynamicValue.int(30)))
        val migration2 = DynamicMigration.record(_.addField("active", DynamicValue.boolean(true)))
        val composed   = migration1.andThen(migration2)
        val result     = composed(original)

        assertTrue(
          result == Right(
            DynamicValue.Record(
              "name"   -> DynamicValue.string("Alice"),
              "age"    -> DynamicValue.int(30),
              "active" -> DynamicValue.boolean(true)
            )
          )
        )
      },
      test("Identity migration is neutral for andThen") {
        val original     = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
        val migration    = DynamicMigration.record(_.addField("age", DynamicValue.int(30)))
        val withIdentity = DynamicMigration.identity.andThen(migration)
        val result       = withIdentity(original)

        assertTrue(
          result == Right(
            DynamicValue.Record(
              "name" -> DynamicValue.string("Alice"),
              "age"  -> DynamicValue.int(30)
            )
          )
        )
      },
      test("andThen is associative: (m1 andThen m2) andThen m3 == m1 andThen (m2 andThen m3)") {
        val original = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
        val m1       = DynamicMigration.record(_.addField("age", DynamicValue.int(30)))
        val m2       = DynamicMigration.record(_.addField("active", DynamicValue.boolean(true)))
        val m3       = DynamicMigration.record(_.addField("score", DynamicValue.int(100)))

        val leftAssoc  = (m1.andThen(m2)).andThen(m3)
        val rightAssoc = m1.andThen(m2.andThen(m3))

        val resultLeft  = leftAssoc(original)
        val resultRight = rightAssoc(original)

        assertTrue(resultLeft == resultRight)
      },
      test("Identity migration is right-neutral: m andThen identity == m") {
        val original          = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
        val migration         = DynamicMigration.record(_.addField("age", DynamicValue.int(30)))
        val withIdentityRight = migration.andThen(DynamicMigration.identity)

        val resultMigration    = migration(original)
        val resultWithIdentity = withIdentityRight(original)

        assertTrue(resultMigration == resultWithIdentity)
      }
    ),
    suite("MigrationStep")(
      test("NoOp step does nothing") {
        val original  = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
        val migration = DynamicMigration(MigrationStep.NoOp)
        val result    = migration(original)

        assertTrue(result == Right(original))
      },
      test("Empty record step is equivalent to NoOp") {
        val original  = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
        val migration = DynamicMigration(MigrationStep.Record.empty)
        val result    = migration(original)

        assertTrue(result == Right(original))
      },
      test("isEmpty returns true for empty steps") {
        assertTrue(MigrationStep.NoOp.isEmpty) &&
        assertTrue(MigrationStep.Record.empty.isEmpty) &&
        assertTrue(MigrationStep.Variant.empty.isEmpty)
      },
      test("isEmpty returns false for non-empty steps") {
        val recordStep  = MigrationStep.Record.empty.addField("x", DynamicValue.int(1))
        val variantStep = MigrationStep.Variant.empty.renameCase("A", "B")
        assertTrue(!recordStep.isEmpty) &&
        assertTrue(!variantStep.isEmpty)
      }
    ),
    suite("Schema serialization")(
      test("PrimitiveConversion round-trips through DynamicValue") {
        val conversions: Vector[PrimitiveConversion] = Vector(
          PrimitiveConversion.IntToLong,
          PrimitiveConversion.LongToInt,
          PrimitiveConversion.IntToString,
          PrimitiveConversion.StringToInt
        )
        val schema  = Schema[PrimitiveConversion]
        val results = conversions.map { conv =>
          val dv = schema.toDynamicValue(conv)
          schema.fromDynamicValue(dv)
        }
        assertTrue(results.forall(_.isRight)) &&
        assertTrue(results.map(_.toOption.get) == conversions)
      },
      test("MigrationError round-trips through DynamicValue") {
        val errors: Vector[MigrationError] = Vector(
          MigrationError.FieldNotFound("name", DynamicOptic.root),
          MigrationError.TypeMismatch("Record", "Int", DynamicOptic.root.field("x")),
          MigrationError.TransformFailed("reason", DynamicOptic.root)
        )
        val schema  = Schema[MigrationError]
        val results = errors.map { e =>
          val dv = schema.toDynamicValue(e)
          schema.fromDynamicValue(dv)
        }
        assertTrue(results.forall(_.isRight)) &&
        assertTrue(results.map(_.toOption.get) == errors)
      }
    ),
    suite("MigrationBuilder")(
      suite("String-based API")(
        test("addField adds a field using path string") {
          val builder = MigrationBuilder[PersonV1, PersonV2]
            .addField("age", DynamicValue.int(0))
          val migration = builder.build
          val original  = PersonV1("Alice")
          val result    = migration(original)

          assertTrue(result == Right(PersonV2("Alice", 0)))
        },
        test("dropField removes a field using path string") {
          val builder = MigrationBuilder[PersonV2, PersonV1]
            .dropField("age", DynamicValue.int(0))
          val migration = builder.build
          val original  = PersonV2("Alice", 30)
          val result    = migration(original)

          assertTrue(result == Right(PersonV1("Alice")))
        },
        test("renameField renames a field using path strings") {
          val builder = MigrationBuilder[PersonV1, PersonRenamed]
            .renameField("name", "fullName")
          val migration = builder.build
          val original  = PersonV1("Alice")
          val result    = migration(original)

          assertTrue(result == Right(PersonRenamed("Alice")))
        },
        test("changeFieldType converts field type") {
          val builder = MigrationBuilder[PersonWithIntId, PersonWithLongId]
            .changeFieldType("id", PrimitiveConversion.IntToLong, PrimitiveConversion.LongToInt)
          val migration = builder.build
          val original  = PersonWithIntId("Alice", 42)
          val result    = migration(original)

          assertTrue(result == Right(PersonWithLongId("Alice", 42L)))
        },
        test("nested path string works for deeply nested fields") {
          val builder = MigrationBuilder[NestedV1, NestedV2]
            .addField("person.contact.phone", DynamicValue.string("555-0000"))
          val migration = builder.build
          val original  = NestedV1(PersonWithContact("Alice", Contact("alice@example.com")))
          val result    = migration(original)

          assertTrue(
            result == Right(NestedV2(PersonWithContactV2("Alice", ContactV2("alice@example.com", "555-0000"))))
          )
        },
        test("multiple field operations chain correctly") {
          val builder = MigrationBuilder[PersonV1, PersonV3]
            .addField("age", DynamicValue.int(0))
            .addField("active", DynamicValue.boolean(true))
          val migration = builder.build
          val original  = PersonV1("Alice")
          val result    = migration(original)

          assertTrue(result == Right(PersonV3("Alice", 0, true)))
        }
      ),
      suite("Builder chaining")(
        test("builder is immutable and chainable") {
          val base      = MigrationBuilder[PersonV1, PersonV2]
          val withAge   = base.addField("age", DynamicValue.int(25))
          val migration = withAge.build
          val original  = PersonV1("Alice")
          val result    = migration(original)

          assertTrue(result == Right(PersonV2("Alice", 25)))
        },
        test("fromBuilder provides fluent API") {
          val migration = MigrationBuilder
            .from[PersonV1]
            .to[PersonV2]
            .addField("age", DynamicValue.int(18))
            .build
          val result = migration(PersonV1("Bob"))

          assertTrue(result == Right(PersonV2("Bob", 18)))
        }
      ),
      suite("Variant operations")(
        test("renameCase renames enum case") {
          val builder = MigrationBuilder[StatusV1, StatusV2]
            .renameCase("Active", "Enabled")
          val migration = builder.build
          val original  = StatusV1.Active
          val result    = migration(original)

          assertTrue(result == Right(StatusV2.Enabled))
        },
        test("addCase adds new case with default") {
          val builder = MigrationBuilder[StatusV1, StatusV3]
            .addCase("Pending", DynamicValue.Variant("Pending", DynamicValue.Record()))
          val migration = builder.build
          val original  = StatusV1.Active
          val result    = migration(original)

          assertTrue(result == Right(StatusV3.Active))
        }
      ),
      suite("Dynamic migration extraction")(
        test("toDynamicMigration returns serializable migration") {
          val builder = MigrationBuilder[PersonV1, PersonV2]
            .addField("age", DynamicValue.int(0))
          val dynamic = builder.toDynamicMigration

          val original = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
          val result   = dynamic(original)

          assertTrue(
            result == Right(
              DynamicValue.Record(
                "name" -> DynamicValue.string("Alice"),
                "age"  -> DynamicValue.int(0)
              )
            )
          )
        }
      )
    ),
    suite("JoinFields and SplitField")(
      test("JoinFields combines multiple fields into one using StringAppend") {
        val original = DynamicValue.Record(
          "prefix" -> DynamicValue.string("Hello"),
          "suffix" -> DynamicValue.string("World"),
          "age"    -> DynamicValue.int(30)
        )

        val migration = DynamicMigration.record(
          _.joinFields(
            "combined",
            Vector("prefix", "suffix"),
            DynamicValueTransform.identity,
            DynamicValueTransform.identity
          )
        )
        val result = migration(original)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            val fieldMap = fields.toVector.toMap
            assertTrue(
              fieldMap.contains("combined"),
              fieldMap.contains("age"),
              !fieldMap.contains("prefix"),
              !fieldMap.contains("suffix")
            )
          case _ => assertTrue(false)
        }
      },
      test("SplitField splits one field into multiple") {
        val original = DynamicValue.Record(
          "source" -> DynamicValue.Record(
            "first"  -> DynamicValue.string("Hello"),
            "second" -> DynamicValue.string("World")
          ),
          "age" -> DynamicValue.int(30)
        )

        val migration = DynamicMigration.record(
          _.splitField(
            "source",
            Vector("first", "second"),
            DynamicValueTransform.identity,
            DynamicValueTransform.identity
          )
        )
        val result = migration(original)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            val fieldMap = fields.toVector.toMap
            assertTrue(
              fieldMap.contains("first"),
              fieldMap.contains("second"),
              fieldMap.contains("age"),
              !fieldMap.contains("source")
            )
          case _ => assertTrue(false)
        }
      },
      test("JoinFields fails when source field not found") {
        val original = DynamicValue.Record(
          "firstName" -> DynamicValue.string("John"),
          "age"       -> DynamicValue.int(30)
        )

        val migration = DynamicMigration.record(
          _.joinFields(
            "fullName",
            Vector("firstName", "lastName"),
            DynamicValueTransform.identity,
            DynamicValueTransform.identity
          )
        )
        val result = migration(original)

        result match {
          case Left(MigrationError.FieldNotFound("lastName", _)) => assertTrue(true)
          case _                                                 => assertTrue(false)
        }
      },
      test("JoinFields fails when target field already exists") {
        val original = DynamicValue.Record(
          "firstName" -> DynamicValue.string("John"),
          "lastName"  -> DynamicValue.string("Doe"),
          "fullName"  -> DynamicValue.string("Existing")
        )

        val migration = DynamicMigration.record(
          _.joinFields(
            "fullName",
            Vector("firstName", "lastName"),
            DynamicValueTransform.identity,
            DynamicValueTransform.identity
          )
        )
        val result = migration(original)

        result match {
          case Left(MigrationError.FieldAlreadyExists("fullName", _)) => assertTrue(true)
          case _                                                      => assertTrue(false)
        }
      },
      test("SplitField fails when source field not found") {
        val original = DynamicValue.Record(
          "age" -> DynamicValue.int(30)
        )

        val migration = DynamicMigration.record(
          _.splitField("fullName", Vector("a", "b"), DynamicValueTransform.identity, DynamicValueTransform.identity)
        )
        val result = migration(original)

        result match {
          case Left(MigrationError.FieldNotFound("fullName", _)) => assertTrue(true)
          case _                                                 => assertTrue(false)
        }
      },
      test("SplitField fails when target field already exists") {
        val original = DynamicValue.Record(
          "fullName"  -> DynamicValue.string("John Doe"),
          "firstName" -> DynamicValue.string("Existing")
        )

        val splitter = DynamicValueTransform.stringSplitToFields(Vector("firstName", "lastName"), " ", 2)

        val migration = DynamicMigration.record(
          _.splitField("fullName", Vector("firstName", "lastName"), splitter, DynamicValueTransform.identity)
        )
        val result = migration(original)

        result match {
          case Left(MigrationError.FieldAlreadyExists("firstName", _)) => assertTrue(true)
          case _                                                       => assertTrue(false)
        }
      },
      test("JoinFields and SplitField are reversible") {
        val original = DynamicValue.Record(
          "firstName" -> DynamicValue.string("Jane"),
          "lastName"  -> DynamicValue.string("Smith"),
          "age"       -> DynamicValue.int(25)
        )

        val combiner = DynamicValueTransform.stringJoinFields(Vector("firstName", "lastName"), " ")
        val splitter = DynamicValueTransform.stringSplitToFields(Vector("firstName", "lastName"), " ", 2)

        val migration = DynamicMigration.record(
          _.joinFields("fullName", Vector("firstName", "lastName"), combiner, splitter)
        )
        val migrated = migration(original)
        val reversed = migration.reverse(migrated.toOption.get)

        reversed match {
          case Right(DynamicValue.Record(fields)) =>
            val fieldMap    = fields.toVector.toMap
            val originalMap = original.fields.toVector.toMap
            assertTrue(
              fieldMap == originalMap
            )
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Four-level nested migrations")(
      test("Apply migration at depth 4") {
        val original = DynamicValue.Record(
          "level1" -> DynamicValue.Record(
            "level2" -> DynamicValue.Record(
              "level3" -> DynamicValue.Record(
                "level4" -> DynamicValue.Record(
                  "value" -> DynamicValue.string("original")
                )
              )
            )
          )
        )

        val migration = DynamicMigration.record(
          _.nested("level1")(
            _.nested("level2")(
              _.nested("level3")(
                _.nested("level4")(
                  _.addField("added", DynamicValue.int(42))
                    .renameField("value", "renamed")
                )
              )
            )
          )
        )
        val result = migration(original)

        assertTrue(
          result == Right(
            DynamicValue.Record(
              "level1" -> DynamicValue.Record(
                "level2" -> DynamicValue.Record(
                  "level3" -> DynamicValue.Record(
                    "level4" -> DynamicValue.Record(
                      "renamed" -> DynamicValue.string("original"),
                      "added"   -> DynamicValue.int(42)
                    )
                  )
                )
              )
            )
          )
        )
      },
      test("Deeply nested migration is reversible") {
        val original = DynamicValue.Record(
          "a" -> DynamicValue.Record(
            "b" -> DynamicValue.Record(
              "c" -> DynamicValue.Record(
                "d" -> DynamicValue.string("deep")
              )
            )
          )
        )

        val migration = DynamicMigration.record(
          _.nested("a")(
            _.nested("b")(
              _.nested("c")(
                _.addField("e", DynamicValue.int(1))
              )
            )
          )
        )
        val migrated = migration(original)
        val reversed = migration.reverse(migrated.toOption.get)

        assertTrue(reversed == Right(original))
      }
    ),
    suite("MigrationBuilder with JoinFields/SplitField")(
      test("joinFields via builder works correctly") {
        val combiner = DynamicValueTransform.stringJoinFields(Vector("firstName", "lastName"), " ")
        val splitter = DynamicValueTransform.stringSplitToFields(Vector("firstName", "lastName"), " ", 2)

        val builder = MigrationBuilder[PersonWithSplitName, PersonWithFullName]
          .joinFields("fullName", Vector("firstName", "lastName"), combiner, splitter)
        val dynamic = builder.toDynamicMigration

        val original = DynamicValue.Record(
          "firstName" -> DynamicValue.string("Alice"),
          "lastName"  -> DynamicValue.string("Wonder"),
          "age"       -> DynamicValue.int(28)
        )
        val result = dynamic(original)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            val fieldMap = fields.toVector.toMap
            assertTrue(
              fieldMap.get("fullName") == Some(DynamicValue.string("Alice Wonder")),
              fieldMap.get("age") == Some(DynamicValue.int(28)),
              !fieldMap.contains("firstName"),
              !fieldMap.contains("lastName")
            )
          case _ => assertTrue(false)
        }
      },
      test("splitField via builder works correctly") {
        val splitter = DynamicValueTransform.stringSplitToFields(Vector("firstName", "lastName"), " ", 2)
        val combiner = DynamicValueTransform.stringJoinFields(Vector("firstName", "lastName"))

        val builder = MigrationBuilder[PersonWithFullName, PersonWithSplitName]
          .splitField("fullName", Vector("firstName", "lastName"), splitter, combiner)
        val dynamic = builder.toDynamicMigration

        val original = DynamicValue.Record(
          "fullName" -> DynamicValue.string("Bob Builder"),
          "age"      -> DynamicValue.int(35)
        )
        val result = dynamic(original)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            val fieldMap = fields.toVector.toMap
            assertTrue(
              fieldMap.get("firstName") == Some(DynamicValue.string("Bob")),
              fieldMap.get("lastName") == Some(DynamicValue.string("Builder")),
              fieldMap.get("age") == Some(DynamicValue.int(35)),
              !fieldMap.contains("fullName")
            )
          case _ => assertTrue(false)
        }
      }
    )
  )
}

case class PersonV1(name: String)
object PersonV1 {
  implicit val schema: Schema[PersonV1] = Schema.derived
}

case class PersonV2(name: String, age: Int)
object PersonV2 {
  implicit val schema: Schema[PersonV2] = Schema.derived
}

case class PersonV3(name: String, age: Int, active: Boolean)
object PersonV3 {
  implicit val schema: Schema[PersonV3] = Schema.derived
}

case class PersonRenamed(fullName: String)
object PersonRenamed {
  implicit val schema: Schema[PersonRenamed] = Schema.derived
}

case class PersonWithIntId(name: String, id: Int)
object PersonWithIntId {
  implicit val schema: Schema[PersonWithIntId] = Schema.derived
}

case class PersonWithLongId(name: String, id: Long)
object PersonWithLongId {
  implicit val schema: Schema[PersonWithLongId] = Schema.derived
}

case class Contact(email: String)
object Contact {
  implicit val schema: Schema[Contact] = Schema.derived
}

case class ContactV2(email: String, phone: String)
object ContactV2 {
  implicit val schema: Schema[ContactV2] = Schema.derived
}

case class PersonWithContact(name: String, contact: Contact)
object PersonWithContact {
  implicit val schema: Schema[PersonWithContact] = Schema.derived
}

case class PersonWithContactV2(name: String, contact: ContactV2)
object PersonWithContactV2 {
  implicit val schema: Schema[PersonWithContactV2] = Schema.derived
}

case class NestedV1(person: PersonWithContact)
object NestedV1 {
  implicit val schema: Schema[NestedV1] = Schema.derived
}

case class NestedV2(person: PersonWithContactV2)
object NestedV2 {
  implicit val schema: Schema[NestedV2] = Schema.derived
}

sealed trait StatusV1
object StatusV1 {
  case object Active   extends StatusV1
  case object Inactive extends StatusV1
  implicit val schema: Schema[StatusV1] = Schema.derived
}

sealed trait StatusV2
object StatusV2 {
  case object Enabled  extends StatusV2
  case object Inactive extends StatusV2
  implicit val schema: Schema[StatusV2] = Schema.derived
}

sealed trait StatusV3
object StatusV3 {
  case object Active   extends StatusV3
  case object Inactive extends StatusV3
  case object Pending  extends StatusV3
  implicit val schema: Schema[StatusV3] = Schema.derived
}

case class PersonWithFullName(fullName: String, age: Int)
object PersonWithFullName {
  implicit val schema: Schema[PersonWithFullName] = Schema.derived
}

case class PersonWithSplitName(firstName: String, lastName: String, age: Int)
object PersonWithSplitName {
  implicit val schema: Schema[PersonWithSplitName] = Schema.derived
}
