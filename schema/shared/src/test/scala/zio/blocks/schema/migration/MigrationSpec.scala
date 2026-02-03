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
          MigrationError.FieldNotFound(DynamicOptic.root, "name"),
          MigrationError.TypeMismatch(DynamicOptic.root.field("x"), "Record", "Int"),
          MigrationError.TransformFailed(DynamicOptic.root, "reason")
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
          val migration = builder.buildPartial
          val original  = PersonV1("Alice")
          val result    = migration(original)

          assertTrue(result == Right(PersonV2("Alice", 0)))
        },
        test("dropField removes a field using path string") {
          val builder = MigrationBuilder[PersonV2, PersonV1]
            .dropField("age", DynamicValue.int(0))
          val migration = builder.buildPartial
          val original  = PersonV2("Alice", 30)
          val result    = migration(original)

          assertTrue(result == Right(PersonV1("Alice")))
        },
        test("renameField renames a field using path strings") {
          val builder = MigrationBuilder[PersonV1, PersonRenamed]
            .renameField("name", "fullName")
          val migration = builder.buildPartial
          val original  = PersonV1("Alice")
          val result    = migration(original)

          assertTrue(result == Right(PersonRenamed("Alice")))
        },
        test("changeFieldType converts field type") {
          val builder = MigrationBuilder[PersonWithIntId, PersonWithLongId]
            .changeFieldType("id", PrimitiveConversion.IntToLong, PrimitiveConversion.LongToInt)
          val migration = builder.buildPartial
          val original  = PersonWithIntId("Alice", 42)
          val result    = migration(original)

          assertTrue(result == Right(PersonWithLongId("Alice", 42L)))
        },
        test("nested path string works for deeply nested fields") {
          val builder = MigrationBuilder[NestedV1, NestedV2]
            .addField("person.contact.phone", DynamicValue.string("555-0000"))
          val migration = builder.buildPartial
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
          val migration = builder.buildPartial
          val original  = PersonV1("Alice")
          val result    = migration(original)

          assertTrue(result == Right(PersonV3("Alice", 0, true)))
        }
      ),
      suite("Builder chaining")(
        test("builder is immutable and chainable") {
          val base      = MigrationBuilder[PersonV1, PersonV2]
          val withAge   = base.addField("age", DynamicValue.int(25))
          val migration = withAge.buildPartial
          val original  = PersonV1("Alice")
          val result    = migration(original)

          assertTrue(result == Right(PersonV2("Alice", 25)))
        },
        test("fromBuilder provides fluent API") {
          val migration = MigrationBuilder
            .from[PersonV1]
            .to[PersonV2]
            .addField("age", DynamicValue.int(18))
            .buildPartial
          val result = migration(PersonV1("Bob"))

          assertTrue(result == Right(PersonV2("Bob", 18)))
        },
        test("exprToDynamicValue throws for non-Literal expressions") {
          val fieldExpr = MigrationExpr.field[PersonV1, String](DynamicOptic.root.field("name"))

          val caught = try {
            val builder = MigrationBuilder[PersonV1, PersonV2]
            builder.addFieldExpr(DynamicOptic.root.field("age"), fieldExpr)
            None
          } catch {
            case e: UnsupportedOperationException => Some(e.getMessage)
            case _: Throwable                     => None
          }

          assertTrue(
            caught.isDefined,
            caught.exists(_.contains("Only MigrationExpr.literal()"))
          )
        }
      ),
      suite("Variant operations")(
        test("renameCase renames enum case") {
          val builder = MigrationBuilder[StatusV1, StatusV2]
            .renameCase("Active", "Enabled")
          val migration = builder.buildPartial
          val original  = StatusV1.Active
          val result    = migration(original)

          assertTrue(result == Right(StatusV2.Enabled))
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
          case Left(MigrationError.FieldNotFound(_, "lastName")) => assertTrue(true)
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
          case Left(MigrationError.FieldAlreadyExists(_, "fullName")) => assertTrue(true)
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
          case Left(MigrationError.FieldNotFound(_, "fullName")) => assertTrue(true)
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
          case Left(MigrationError.FieldAlreadyExists(_, "firstName")) => assertTrue(true)
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
    ),
    suite("PrimitiveConversion overflow and edge cases")(
      test("LongToInt fails when value exceeds Int.MaxValue") {
        val value  = DynamicValue.long(Int.MaxValue.toLong + 1)
        val result = PrimitiveConversion.LongToInt(value)
        assertTrue(result.isLeft)
      },
      test("LongToInt fails when value is below Int.MinValue") {
        val value  = DynamicValue.long(Int.MinValue.toLong - 1)
        val result = PrimitiveConversion.LongToInt(value)
        assertTrue(result.isLeft)
      },
      test("LongToInt succeeds at Int boundaries") {
        val maxResult = PrimitiveConversion.LongToInt(DynamicValue.long(Int.MaxValue.toLong))
        val minResult = PrimitiveConversion.LongToInt(DynamicValue.long(Int.MinValue.toLong))
        assertTrue(
          maxResult == Right(DynamicValue.int(Int.MaxValue)),
          minResult == Right(DynamicValue.int(Int.MinValue))
        )
      },
      test("DoubleToInt fails for NaN") {
        val value  = DynamicValue.double(Double.NaN)
        val result = PrimitiveConversion.DoubleToInt(value)
        assertTrue(result.isLeft)
      },
      test("DoubleToInt fails for positive Infinity") {
        val value  = DynamicValue.double(Double.PositiveInfinity)
        val result = PrimitiveConversion.DoubleToInt(value)
        assertTrue(result.isLeft)
      },
      test("DoubleToInt fails for negative Infinity") {
        val value  = DynamicValue.double(Double.NegativeInfinity)
        val result = PrimitiveConversion.DoubleToInt(value)
        assertTrue(result.isLeft)
      },
      test("DoubleToInt fails when value exceeds Int range") {
        val aboveMax = DynamicValue.double(Int.MaxValue.toDouble + 1000)
        val belowMin = DynamicValue.double(Int.MinValue.toDouble - 1000)
        assertTrue(
          PrimitiveConversion.DoubleToInt(aboveMax).isLeft,
          PrimitiveConversion.DoubleToInt(belowMin).isLeft
        )
      },
      test("DoubleToFloat fails when value exceeds Float range") {
        val aboveMax = DynamicValue.double(Float.MaxValue.toDouble * 2)
        val belowMin = DynamicValue.double(-Float.MaxValue.toDouble * 2)
        assertTrue(
          PrimitiveConversion.DoubleToFloat(aboveMax).isLeft,
          PrimitiveConversion.DoubleToFloat(belowMin).isLeft
        )
      },
      test("DoubleToFloat preserves NaN and Infinity") {
        val nan    = PrimitiveConversion.DoubleToFloat(DynamicValue.double(Double.NaN))
        val posInf = PrimitiveConversion.DoubleToFloat(DynamicValue.double(Double.PositiveInfinity))
        val negInf = PrimitiveConversion.DoubleToFloat(DynamicValue.double(Double.NegativeInfinity))
        assertTrue(
          nan.isRight,
          posInf.isRight,
          negInf.isRight
        )
      }
    ),
    suite("PrimitiveConversion untested conversions")(
      test("LongToString converts Long to String") {
        val value  = DynamicValue.long(9876543210L)
        val result = PrimitiveConversion.LongToString(value)
        assertTrue(result == Right(DynamicValue.string("9876543210")))
      },
      test("StringToLong converts String to Long") {
        val value  = DynamicValue.string("9876543210")
        val result = PrimitiveConversion.StringToLong(value)
        assertTrue(result == Right(DynamicValue.long(9876543210L)))
      },
      test("StringToLong fails for non-numeric string") {
        val value  = DynamicValue.string("not-a-number")
        val result = PrimitiveConversion.StringToLong(value)
        assertTrue(result.isLeft)
      },
      test("DoubleToString converts Double to String") {
        val value  = DynamicValue.double(3.14159)
        val result = PrimitiveConversion.DoubleToString(value)
        assertTrue(result == Right(DynamicValue.string("3.14159")))
      },
      test("StringToDouble converts String to Double") {
        val value  = DynamicValue.string("3.14159")
        val result = PrimitiveConversion.StringToDouble(value)
        assertTrue(result == Right(DynamicValue.double(3.14159)))
      },
      test("StringToDouble fails for non-numeric string") {
        val value  = DynamicValue.string("not-a-double")
        val result = PrimitiveConversion.StringToDouble(value)
        assertTrue(result.isLeft)
      },
      test("FloatToDouble converts Float to Double") {
        val value  = DynamicValue.float(3.14f)
        val result = PrimitiveConversion.FloatToDouble(value)
        assertTrue(result.isRight)
      },
      test("DoubleToFloat converts Double to Float within range") {
        val value  = DynamicValue.double(3.14)
        val result = PrimitiveConversion.DoubleToFloat(value)
        assertTrue(result.isRight)
      },
      test("BooleanToString converts true to 'true'") {
        val value  = DynamicValue.boolean(true)
        val result = PrimitiveConversion.BooleanToString(value)
        assertTrue(result == Right(DynamicValue.string("true")))
      },
      test("BooleanToString converts false to 'false'") {
        val value  = DynamicValue.boolean(false)
        val result = PrimitiveConversion.BooleanToString(value)
        assertTrue(result == Right(DynamicValue.string("false")))
      },
      test("StringToBoolean converts 'true' to true (case-insensitive)") {
        assertTrue(
          PrimitiveConversion.StringToBoolean(DynamicValue.string("true")) == Right(DynamicValue.boolean(true)),
          PrimitiveConversion.StringToBoolean(DynamicValue.string("TRUE")) == Right(DynamicValue.boolean(true)),
          PrimitiveConversion.StringToBoolean(DynamicValue.string("True")) == Right(DynamicValue.boolean(true))
        )
      },
      test("StringToBoolean converts 'false' to false (case-insensitive)") {
        assertTrue(
          PrimitiveConversion.StringToBoolean(DynamicValue.string("false")) == Right(DynamicValue.boolean(false)),
          PrimitiveConversion.StringToBoolean(DynamicValue.string("FALSE")) == Right(DynamicValue.boolean(false)),
          PrimitiveConversion.StringToBoolean(DynamicValue.string("False")) == Right(DynamicValue.boolean(false))
        )
      },
      test("StringToBoolean fails for invalid string") {
        val result = PrimitiveConversion.StringToBoolean(DynamicValue.string("yes"))
        assertTrue(result.isLeft)
      },
      test("IntToDouble converts Int to Double") {
        val value  = DynamicValue.int(42)
        val result = PrimitiveConversion.IntToDouble(value)
        assertTrue(result == Right(DynamicValue.double(42.0)))
      }
    ),
    suite("NumericAdd/NumericMultiply overflow")(
      test("NumericAdd fails when Int result exceeds Int.MaxValue") {
        val value  = DynamicValue.int(Int.MaxValue)
        val result = DynamicValueTransform.numericAdd(1)(value)
        assertTrue(result.isLeft)
      },
      test("NumericAdd fails when Int result is below Int.MinValue") {
        val value  = DynamicValue.int(Int.MinValue)
        val result = DynamicValueTransform.numericAdd(-1)(value)
        assertTrue(result.isLeft)
      },
      test("NumericAdd fails when Long result exceeds Long.MaxValue") {
        val value  = DynamicValue.long(Long.MaxValue)
        val result = DynamicValueTransform.numericAdd(1)(value)
        assertTrue(result.isLeft)
      },
      test("NumericAdd fails when Long result is below Long.MinValue") {
        val value  = DynamicValue.long(Long.MinValue)
        val result = DynamicValueTransform.numericAdd(-1)(value)
        assertTrue(result.isLeft)
      },
      test("NumericMultiply fails when Int result exceeds Int.MaxValue") {
        val value  = DynamicValue.int(Int.MaxValue / 2 + 1)
        val result = DynamicValueTransform.numericMultiply(3)(value)
        assertTrue(result.isLeft)
      },
      test("NumericMultiply fails when Int result is below Int.MinValue") {
        val value  = DynamicValue.int(Int.MinValue / 2 - 1)
        val result = DynamicValueTransform.numericMultiply(3)(value)
        assertTrue(result.isLeft)
      },
      test("NumericMultiply fails when Long result exceeds Long.MaxValue") {
        val value  = DynamicValue.long(Long.MaxValue / 2 + 1)
        val result = DynamicValueTransform.numericMultiply(3)(value)
        assertTrue(result.isLeft)
      },
      test("NumericMultiply fails when Long result is below Long.MinValue") {
        val value  = DynamicValue.long(Long.MinValue / 2 - 1)
        val result = DynamicValueTransform.numericMultiply(3)(value)
        assertTrue(result.isLeft)
      },
      test("NumericAdd works with Double without overflow") {
        val value  = DynamicValue.double(Double.MaxValue / 2)
        val result = DynamicValueTransform.numericAdd(1000)(value)
        assertTrue(result.isRight)
      },
      test("NumericMultiply works with Double without overflow") {
        val value  = DynamicValue.double(1000.0)
        val result = DynamicValueTransform.numericMultiply(1000)(value)
        assertTrue(result.isRight)
      },
      test("NumericAdd fails for non-numeric type") {
        val value  = DynamicValue.string("not a number")
        val result = DynamicValueTransform.numericAdd(1)(value)
        assertTrue(result.isLeft)
      },
      test("NumericMultiply fails for non-numeric type") {
        val value  = DynamicValue.string("not a number")
        val result = DynamicValueTransform.numericMultiply(2)(value)
        assertTrue(result.isLeft)
      }
    ),
    suite("UnwrapSome transform")(
      test("UnwrapSome extracts value from Some variant") {
        val value = DynamicValue.Variant(
          "Some",
          DynamicValue.Record("value" -> DynamicValue.string("inner"))
        )
        val result = DynamicValueTransform.unwrapSome(DynamicValue.string("default"))(value)
        assertTrue(result == Right(DynamicValue.string("inner")))
      },
      test("UnwrapSome returns default for None variant") {
        val value   = DynamicValue.Variant("None", DynamicValue.Record())
        val default = DynamicValue.string("default")
        val result  = DynamicValueTransform.unwrapSome(default)(value)
        assertTrue(result == Right(default))
      },
      test("UnwrapSome returns default for Null") {
        val default = DynamicValue.string("default")
        val result  = DynamicValueTransform.unwrapSome(default)(DynamicValue.Null)
        assertTrue(result == Right(default))
      },
      test("UnwrapSome fails for non-Option value") {
        val value  = DynamicValue.string("plain string")
        val result = DynamicValueTransform.unwrapSome(DynamicValue.string("default"))(value)
        assertTrue(result.isLeft)
      },
      test("UnwrapSome fails for Some variant without 'value' field") {
        val value  = DynamicValue.Variant("Some", DynamicValue.Record("wrong" -> DynamicValue.int(42)))
        val result = DynamicValueTransform.unwrapSome(DynamicValue.string("default"))(value)
        assertTrue(result.isLeft)
      }
    ),
    suite("String transform type errors")(
      test("StringAppend fails for non-String input") {
        val value  = DynamicValue.int(42)
        val result = DynamicValueTransform.stringAppend(" suffix")(value)
        assertTrue(result.isLeft)
      },
      test("StringPrepend fails for non-String input") {
        val value  = DynamicValue.int(42)
        val result = DynamicValueTransform.stringPrepend("prefix ")(value)
        assertTrue(result.isLeft)
      },
      test("StringReplace fails for non-String input") {
        val value  = DynamicValue.int(42)
        val result = DynamicValueTransform.stringReplace("old", "new")(value)
        assertTrue(result.isLeft)
      },
      test("StringJoinFields fails for non-Record input") {
        val value  = DynamicValue.string("not a record")
        val result = DynamicValueTransform.stringJoinFields(Vector("a", "b"), "-")(value)
        assertTrue(result.isLeft)
      },
      test("StringJoinFields fails when field is not String") {
        val value = DynamicValue.Record(
          "a" -> DynamicValue.string("hello"),
          "b" -> DynamicValue.int(42)
        )
        val result = DynamicValueTransform.stringJoinFields(Vector("a", "b"), "-")(value)
        assertTrue(result.isLeft)
      },
      test("StringJoinFields reports all missing fields") {
        val value = DynamicValue.Record(
          "x" -> DynamicValue.string("only x")
        )
        val result = DynamicValueTransform.stringJoinFields(Vector("a", "b", "c"), "-")(value)
        result match {
          case Left(err) =>
            assertTrue(
              err.contains("'a'"),
              err.contains("'b'"),
              err.contains("'c'")
            )
          case _ => assertTrue(false)
        }
      },
      test("StringSplitToFields fails for non-String input") {
        val value  = DynamicValue.int(42)
        val result = DynamicValueTransform.stringSplitToFields(Vector("a", "b"), "-")(value)
        assertTrue(result.isLeft)
      }
    ),
    suite("MakeRequired error cases")(
      test("MakeRequired fails for non-Option value") {
        val original = DynamicValue.Record(
          "name" -> DynamicValue.string("plain string, not an Option")
        )
        val migration = DynamicMigration.record(_.makeFieldRequired("name", DynamicValue.string("")))
        val result    = migration(original)

        result match {
          case Left(MigrationError.TransformFailed(_, _)) => assertTrue(true)
          case _                                          => assertTrue(false)
        }
      },
      test("MakeRequired fails when field does not exist") {
        val original  = DynamicValue.Record("other" -> DynamicValue.int(1))
        val migration = DynamicMigration.record(_.makeFieldRequired("nonexistent", DynamicValue.string("")))
        val result    = migration(original)

        result match {
          case Left(MigrationError.FieldNotFound(_, _)) => assertTrue(true)
          case _                                        => assertTrue(false)
        }
      }
    ),
    suite("MakeOptional error cases")(
      test("MakeOptional fails when field does not exist") {
        val original  = DynamicValue.Record("other" -> DynamicValue.int(1))
        val migration = DynamicMigration.record(_.makeFieldOptional("nonexistent", DynamicValue.Null))
        val result    = migration(original)

        result match {
          case Left(MigrationError.FieldNotFound(_, _)) => assertTrue(true)
          case _                                        => assertTrue(false)
        }
      }
    ),
    suite("ChangeType error cases")(
      test("ChangeType fails when field does not exist") {
        val original  = DynamicValue.Record("other" -> DynamicValue.int(1))
        val migration = DynamicMigration.record(
          _.changeFieldType("nonexistent", PrimitiveConversion.IntToLong, PrimitiveConversion.LongToInt)
        )
        val result = migration(original)

        result match {
          case Left(MigrationError.FieldNotFound(_, _)) => assertTrue(true)
          case _                                        => assertTrue(false)
        }
      },
      test("ChangeType fails when conversion fails") {
        val original  = DynamicValue.Record("field" -> DynamicValue.string("not an int"))
        val migration = DynamicMigration.record(
          _.changeFieldType("field", PrimitiveConversion.IntToLong, PrimitiveConversion.LongToInt)
        )
        val result = migration(original)

        result match {
          case Left(MigrationError.TransformFailed(_, _)) => assertTrue(true)
          case _                                          => assertTrue(false)
        }
      }
    ),
    suite("Transform error cases")(
      test("Transform fails when field does not exist") {
        val original  = DynamicValue.Record("other" -> DynamicValue.int(1))
        val migration = DynamicMigration.record(
          _.transformField("nonexistent", DynamicValueTransform.identity, DynamicValueTransform.identity)
        )
        val result = migration(original)

        result match {
          case Left(MigrationError.FieldNotFound(_, _)) => assertTrue(true)
          case _                                        => assertTrue(false)
        }
      }
    ),
    suite("Additional MigrationError types")(
      test("CaseNotFound error can be serialized") {
        val error  = MigrationError.CaseNotFound(DynamicOptic.root, "MissingCase")
        val schema = Schema[MigrationError]
        val dv     = schema.toDynamicValue(error)
        val result = schema.fromDynamicValue(dv)
        assertTrue(result == Right(error))
      },
      test("InvalidIndex error can be serialized") {
        val error  = MigrationError.InvalidIndex(DynamicOptic.root, 10, 5)
        val schema = Schema[MigrationError]
        val dv     = schema.toDynamicValue(error)
        val result = schema.fromDynamicValue(dv)
        assertTrue(result == Right(error))
      },
      test("ExpressionEvalFailed error can be serialized") {
        val error  = MigrationError.ExpressionEvalFailed(DynamicOptic.root, "eval failed")
        val schema = Schema[MigrationError]
        val dv     = schema.toDynamicValue(error)
        val result = schema.fromDynamicValue(dv)
        assertTrue(result == Right(error))
      },
      test("IncompatibleValue error can be serialized") {
        val error  = MigrationError.IncompatibleValue(DynamicOptic.root, "incompatible")
        val schema = Schema[MigrationError]
        val dv     = schema.toDynamicValue(error)
        val result = schema.fromDynamicValue(dv)
        assertTrue(result == Right(error))
      }
    ),
    suite("Sequence element migration failure")(
      test("Sequence migration fails if any element fails") {
        val original = DynamicValue.Sequence(
          DynamicValue.Record("name" -> DynamicValue.string("Alice")),
          DynamicValue.int(42),
          DynamicValue.Record("name" -> DynamicValue.string("Bob"))
        )
        val elementMigration = DynamicMigration.record(_.addField("age", DynamicValue.int(0)))
        val migration        = DynamicMigration.sequence(elementMigration)
        val result           = migration(original)

        assertTrue(result.isLeft)
      },
      test("Sequence migration fails when applied to non-Sequence value") {
        val original  = DynamicValue.Record("field" -> DynamicValue.int(1))
        val migration =
          DynamicMigration(MigrationStep.Sequence(MigrationStep.Record.empty.addField("x", DynamicValue.int(0))))
        val result = migration(original)

        result match {
          case Left(MigrationError.TypeMismatch(_, expected, _)) => assertTrue(expected == "Sequence")
          case _                                                 => assertTrue(false)
        }
      }
    ),
    suite("PrimitiveConversion type errors")(
      test("IntToLong fails for non-Int input") {
        val value  = DynamicValue.string("not an int")
        val result = PrimitiveConversion.IntToLong(value)
        assertTrue(result.isLeft)
      },
      test("LongToInt fails for non-Long input") {
        val value  = DynamicValue.string("not a long")
        val result = PrimitiveConversion.LongToInt(value)
        assertTrue(result.isLeft)
      },
      test("DoubleToInt fails for non-Double input") {
        val value  = DynamicValue.string("not a double")
        val result = PrimitiveConversion.DoubleToInt(value)
        assertTrue(result.isLeft)
      },
      test("DoubleToFloat fails for non-Double input") {
        val value  = DynamicValue.string("not a double")
        val result = PrimitiveConversion.DoubleToFloat(value)
        assertTrue(result.isLeft)
      },
      test("FloatToDouble fails for non-Float input") {
        val value  = DynamicValue.string("not a float")
        val result = PrimitiveConversion.FloatToDouble(value)
        assertTrue(result.isLeft)
      },
      test("StringToInt fails for non-String input") {
        val value  = DynamicValue.int(42)
        val result = PrimitiveConversion.StringToInt(value)
        assertTrue(result.isLeft)
      },
      test("IntToString fails for non-Int input") {
        val value  = DynamicValue.string("not an int")
        val result = PrimitiveConversion.IntToString(value)
        assertTrue(result.isLeft)
      },
      test("BooleanToString fails for non-Boolean input") {
        val value  = DynamicValue.string("not a boolean")
        val result = PrimitiveConversion.BooleanToString(value)
        assertTrue(result.isLeft)
      },
      test("StringToBoolean fails for non-String input") {
        val value  = DynamicValue.boolean(true)
        val result = PrimitiveConversion.StringToBoolean(value)
        assertTrue(result.isLeft)
      }
    ),
    suite("Map migrations")(
      test("MapEntries applies key and value transformations") {
        val original = DynamicValue.Map(
          DynamicValue.string("key1") -> DynamicValue.Record("count" -> DynamicValue.int(10)),
          DynamicValue.string("key2") -> DynamicValue.Record("count" -> DynamicValue.int(20))
        )
        val keyMigration   = DynamicMigration.identity
        val valueMigration = DynamicMigration.record(_.addField("active", DynamicValue.boolean(true)))
        val migration      = DynamicMigration(
          MigrationStep.MapEntries(keyMigration.step, valueMigration.step)
        )
        val result = migration(original)

        assertTrue(
          result == Right(
            DynamicValue.Map(
              DynamicValue.string("key1") -> DynamicValue.Record(
                "count"  -> DynamicValue.int(10),
                "active" -> DynamicValue.boolean(true)
              ),
              DynamicValue.string("key2") -> DynamicValue.Record(
                "count"  -> DynamicValue.int(20),
                "active" -> DynamicValue.boolean(true)
              )
            )
          )
        )
      },
      test("MapEntries key transformation works correctly") {
        val original = DynamicValue.Map(
          DynamicValue.Record("id" -> DynamicValue.string("a")) -> DynamicValue.int(1),
          DynamicValue.Record("id" -> DynamicValue.string("b")) -> DynamicValue.int(2)
        )
        val keyMigration = DynamicMigration.record(_.renameField("id", "key"))
        val migration    = DynamicMigration(
          MigrationStep.MapEntries(keyMigration.step, MigrationStep.NoOp)
        )
        val result = migration(original)

        assertTrue(
          result == Right(
            DynamicValue.Map(
              DynamicValue.Record("key" -> DynamicValue.string("a")) -> DynamicValue.int(1),
              DynamicValue.Record("key" -> DynamicValue.string("b")) -> DynamicValue.int(2)
            )
          )
        )
      },
      test("MapEntries fails when key migration produces duplicate keys") {
        val original = DynamicValue.Map(
          DynamicValue.Record("x" -> DynamicValue.int(1), "y" -> DynamicValue.int(2)) -> DynamicValue.string("a"),
          DynamicValue.Record("x" -> DynamicValue.int(1), "y" -> DynamicValue.int(3)) -> DynamicValue.string("b")
        )
        val keyMigration = DynamicMigration.record(_.removeField("y", DynamicValue.int(0)))
        val migration    = DynamicMigration(
          MigrationStep.MapEntries(keyMigration.step, MigrationStep.NoOp)
        )
        val result = migration(original)

        result match {
          case Left(MigrationError.DuplicateMapKey(_)) => assertTrue(true)
          case _                                       => assertTrue(false)
        }
      },
      test("MapEntries is reversible") {
        val original = DynamicValue.Map(
          DynamicValue.string("a") -> DynamicValue.Record("v" -> DynamicValue.int(1)),
          DynamicValue.string("b") -> DynamicValue.Record("v" -> DynamicValue.int(2))
        )
        val valueMigration = DynamicMigration.record(_.addField("extra", DynamicValue.boolean(true)))
        val migration      = DynamicMigration(
          MigrationStep.MapEntries(MigrationStep.NoOp, valueMigration.step)
        )
        val migrated = migration(original)
        val reversed = migration.reverse(migrated.toOption.get)

        assertTrue(reversed == Right(original))
      },
      test("MapEntries with empty map returns empty map") {
        val original  = DynamicValue.Map()
        val migration = DynamicMigration(MigrationStep.MapEntries(MigrationStep.NoOp, MigrationStep.NoOp))
        val result    = migration(original)

        assertTrue(result == Right(DynamicValue.Map()))
      },
      test("MapEntries fails when applied to non-Map value") {
        val original  = DynamicValue.int(42)
        val migration = DynamicMigration(MigrationStep.MapEntries(MigrationStep.NoOp, MigrationStep.NoOp))
        val result    = migration(original)

        result match {
          case Left(MigrationError.TypeMismatch(_, _, _)) => assertTrue(true)
          case _                                          => assertTrue(false)
        }
      },
      test("DuplicateMapKey error can be serialized") {
        val error  = MigrationError.DuplicateMapKey(DynamicOptic.root)
        val schema = Schema[MigrationError]
        val dv     = schema.toDynamicValue(error)
        val result = schema.fromDynamicValue(dv)
        assertTrue(result == Right(error))
      }
    ),
    suite("Reverse with rename nested keys")(
      test("Record.reverse updates nested keys after field rename") {
        val original = DynamicValue.Record(
          "oldName" -> DynamicValue.Record("inner" -> DynamicValue.string("value"))
        )
        val migration = DynamicMigration.record(
          _.renameField("oldName", "newName")
            .nested("newName")(_.addField("extra", DynamicValue.int(42)))
        )
        val migrated = migration(original)
        val reversed = migration.reverse(migrated.toOption.get)

        assertTrue(reversed == Right(original))
      },
      test("Variant.reverse updates nested keys after case rename") {
        val original = DynamicValue.Variant(
          "OldCase",
          DynamicValue.Record("field" -> DynamicValue.string("data"))
        )
        val migration = DynamicMigration.variant(
          _.renameCase("OldCase", "NewCase")
            .nested("NewCase")(_.addField("extra", DynamicValue.int(100)))
        )
        val migrated = migration(original)
        val reversed = migration.reverse(migrated.toOption.get)

        assertTrue(reversed == Right(original))
      }
    ),
    suite("Incompatible step composition")(
      test("andThen throws for incompatible step types Record+Variant") {
        val recordMigration  = DynamicMigration.record(_.addField("x", DynamicValue.int(1)))
        val variantMigration = DynamicMigration.variant(_.renameCase("A", "B"))

        val caught = try {
          recordMigration.andThen(variantMigration)
          None
        } catch {
          case e: IllegalArgumentException => Some(e.getMessage)
          case _: Throwable                => None
        }

        assertTrue(
          caught.isDefined,
          caught.exists(_.contains("Cannot compose incompatible"))
        )
      },
      test("andThen throws for incompatible step types Sequence+Record") {
        val seqMigration    = DynamicMigration.sequence(DynamicMigration.identity)
        val recordMigration = DynamicMigration.record(_.addField("x", DynamicValue.int(1)))

        val caught = try {
          seqMigration.andThen(recordMigration)
          None
        } catch {
          case e: IllegalArgumentException => Some(e.getMessage)
          case _: Throwable                => None
        }

        assertTrue(
          caught.isDefined,
          caught.exists(_.contains("Cannot compose incompatible"))
        )
      }
    ),
    suite("transformCase")(
      test("transformCase applies nested migration to variant case") {
        val original = DynamicValue.Variant(
          "User",
          DynamicValue.Record("name" -> DynamicValue.string("Alice"))
        )
        val migration = DynamicMigration.variant(
          _.transformCase("User")(_.addField("role", DynamicValue.string("admin")))
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
      },
      test("transformCase with rename applies to renamed case") {
        val original = DynamicValue.Variant(
          "OldCase",
          DynamicValue.Record("data" -> DynamicValue.int(42))
        )
        val migration = DynamicMigration.variant(
          _.renameCase("OldCase", "NewCase")
            .transformCase("NewCase")(_.addField("extra", DynamicValue.boolean(true)))
        )
        val result = migration(original)

        assertTrue(
          result == Right(
            DynamicValue.Variant(
              "NewCase",
              DynamicValue.Record(
                "data"  -> DynamicValue.int(42),
                "extra" -> DynamicValue.boolean(true)
              )
            )
          )
        )
      },
      test("transformCase is reversible") {
        val original = DynamicValue.Variant(
          "Case1",
          DynamicValue.Record("value" -> DynamicValue.string("test"))
        )
        val migration = DynamicMigration.variant(
          _.transformCase("Case1")(_.addField("added", DynamicValue.int(0)))
        )
        val migrated = migration(original)
        val reversed = migration.reverse(migrated.toOption.get)

        assertTrue(reversed == Right(original))
      }
    ),
    suite("Builder transformElements")(
      test("transformElements applies nested migration to sequence elements") {
        val original = DynamicValue.Record(
          "items" -> DynamicValue.Sequence(
            DynamicValue.Record("name" -> DynamicValue.string("item1")),
            DynamicValue.Record("name" -> DynamicValue.string("item2"))
          )
        )
        val step = MigrationStep.Record.empty
          .copy(nestedFields =
            Map(
              "items" -> MigrationStep.Sequence(
                MigrationStep.Record.empty.addField("count", DynamicValue.int(0))
              )
            )
          )
        val migration = DynamicMigration(step)
        val result    = migration(original)

        assertTrue(
          result == Right(
            DynamicValue.Record(
              "items" -> DynamicValue.Sequence(
                DynamicValue.Record("name" -> DynamicValue.string("item1"), "count" -> DynamicValue.int(0)),
                DynamicValue.Record("name" -> DynamicValue.string("item2"), "count" -> DynamicValue.int(0))
              )
            )
          )
        )
      },
      test("transformElements is reversible") {
        val original = DynamicValue.Record(
          "items" -> DynamicValue.Sequence(
            DynamicValue.Record("name" -> DynamicValue.string("a")),
            DynamicValue.Record("name" -> DynamicValue.string("b"))
          )
        )
        val step = MigrationStep.Record.empty
          .copy(nestedFields =
            Map(
              "items" -> MigrationStep.Sequence(
                MigrationStep.Record.empty.addField("extra", DynamicValue.boolean(true))
              )
            )
          )
        val migration = DynamicMigration(step)
        val migrated  = migration(original)
        val reversed  = migration.reverse(migrated.toOption.get)

        assertTrue(reversed == Right(original))
      },
      test("transformElements with nested path") {
        val original = DynamicValue.Record(
          "container" -> DynamicValue.Record(
            "items" -> DynamicValue.Sequence(
              DynamicValue.Record("value" -> DynamicValue.int(1))
            )
          )
        )
        val step = MigrationStep.Record.empty
          .nested("container") { nested =>
            nested.copy(nestedFields =
              nested.nestedFields + ("items" -> MigrationStep.Sequence(
                MigrationStep.Record.empty.addField("flag", DynamicValue.boolean(false))
              ))
            )
          }
        val migration = DynamicMigration(step)
        val result    = migration(original)

        assertTrue(
          result == Right(
            DynamicValue.Record(
              "container" -> DynamicValue.Record(
                "items" -> DynamicValue.Sequence(
                  DynamicValue.Record("value" -> DynamicValue.int(1), "flag" -> DynamicValue.boolean(false))
                )
              )
            )
          )
        )
      }
    ),
    suite("Builder transformValues")(
      test("transformValues applies nested migration to map values") {
        val original = DynamicValue.Record(
          "data" -> DynamicValue.Map(
            DynamicValue.string("key1") -> DynamicValue.Record("value" -> DynamicValue.string("a")),
            DynamicValue.string("key2") -> DynamicValue.Record("value" -> DynamicValue.string("b"))
          )
        )
        val step = MigrationStep.Record.empty
          .copy(nestedFields =
            Map(
              "data" -> MigrationStep.MapEntries(
                MigrationStep.NoOp,
                MigrationStep.Record.empty.addField("version", DynamicValue.int(1))
              )
            )
          )
        val migration = DynamicMigration(step)
        val result    = migration(original)

        assertTrue(
          result == Right(
            DynamicValue.Record(
              "data" -> DynamicValue.Map(
                DynamicValue.string("key1") -> DynamicValue.Record(
                  "value"   -> DynamicValue.string("a"),
                  "version" -> DynamicValue.int(1)
                ),
                DynamicValue.string("key2") -> DynamicValue.Record(
                  "value"   -> DynamicValue.string("b"),
                  "version" -> DynamicValue.int(1)
                )
              )
            )
          )
        )
      },
      test("transformValues is reversible") {
        val original = DynamicValue.Record(
          "data" -> DynamicValue.Map(
            DynamicValue.string("x") -> DynamicValue.Record("v" -> DynamicValue.int(10))
          )
        )
        val step = MigrationStep.Record.empty
          .copy(nestedFields =
            Map(
              "data" -> MigrationStep.MapEntries(
                MigrationStep.NoOp,
                MigrationStep.Record.empty.addField("added", DynamicValue.string("new"))
              )
            )
          )
        val migration = DynamicMigration(step)
        val migrated  = migration(original)
        val reversed  = migration.reverse(migrated.toOption.get)

        assertTrue(reversed == Right(original))
      }
    ),
    suite("Builder transformKeys")(
      test("transformKeys applies nested migration to map keys") {
        val original = DynamicValue.Record(
          "data" -> DynamicValue.Map(
            DynamicValue.Record("id" -> DynamicValue.string("k1")) -> DynamicValue.int(100),
            DynamicValue.Record("id" -> DynamicValue.string("k2")) -> DynamicValue.int(200)
          )
        )
        val step = MigrationStep.Record.empty
          .copy(nestedFields =
            Map(
              "data" -> MigrationStep.MapEntries(
                MigrationStep.Record.empty.renameField("id", "key"),
                MigrationStep.NoOp
              )
            )
          )
        val migration = DynamicMigration(step)
        val result    = migration(original)

        assertTrue(
          result == Right(
            DynamicValue.Record(
              "data" -> DynamicValue.Map(
                DynamicValue.Record("key" -> DynamicValue.string("k1")) -> DynamicValue.int(100),
                DynamicValue.Record("key" -> DynamicValue.string("k2")) -> DynamicValue.int(200)
              )
            )
          )
        )
      },
      test("transformKeys is reversible") {
        val original = DynamicValue.Record(
          "data" -> DynamicValue.Map(
            DynamicValue.Record("name" -> DynamicValue.string("a")) -> DynamicValue.int(1)
          )
        )
        val step = MigrationStep.Record.empty
          .copy(nestedFields =
            Map(
              "data" -> MigrationStep.MapEntries(
                MigrationStep.Record.empty.renameField("name", "label"),
                MigrationStep.NoOp
              )
            )
          )
        val migration = DynamicMigration(step)
        val migrated  = migration(original)
        val reversed  = migration.reverse(migrated.toOption.get)

        assertTrue(reversed == Right(original))
      }
    ),
    suite("MigrationBuilder collection methods")(
      test("transformElements via MigrationBuilder internal method") {
        val builder = MigrationBuilder[ContainerWithItemsV1, ContainerWithItemsV2, Nothing, Nothing](
          ContainerWithItemsV1.schema,
          ContainerWithItemsV2.schema,
          MigrationStep.Record.empty,
          MigrationStep.Variant.empty
        )
        val updatedBuilder = builder.transformElements("items")(_.addField("count", DynamicValue.int(0)))
        val migration      = updatedBuilder.toDynamicMigration

        val original = DynamicValue.Record(
          "items" -> DynamicValue.Sequence(
            DynamicValue.Record("name" -> DynamicValue.string("item1"))
          )
        )
        val result = migration(original)

        assertTrue(
          result == Right(
            DynamicValue.Record(
              "items" -> DynamicValue.Sequence(
                DynamicValue.Record("name" -> DynamicValue.string("item1"), "count" -> DynamicValue.int(0))
              )
            )
          )
        )
      },
      test("transformValues via MigrationBuilder internal method") {
        val builder = MigrationBuilder[ContainerWithMapV1, ContainerWithMapV2, Nothing, Nothing](
          ContainerWithMapV1.schema,
          ContainerWithMapV2.schema,
          MigrationStep.Record.empty,
          MigrationStep.Variant.empty
        )
        val updatedBuilder = builder.transformValues("data")(_.addField("version", DynamicValue.int(1)))
        val migration      = updatedBuilder.toDynamicMigration

        val original = DynamicValue.Record(
          "data" -> DynamicValue.Map(
            DynamicValue.string("key1") -> DynamicValue.Record("value" -> DynamicValue.string("a"))
          )
        )
        val result = migration(original)

        assertTrue(
          result == Right(
            DynamicValue.Record(
              "data" -> DynamicValue.Map(
                DynamicValue.string("key1") -> DynamicValue.Record(
                  "value"   -> DynamicValue.string("a"),
                  "version" -> DynamicValue.int(1)
                )
              )
            )
          )
        )
      },
      test("transformKeys via MigrationBuilder internal method") {
        case class KeyV1(id: String)
        case class KeyV2(key: String)
        case class ContainerK1(data: scala.collection.immutable.Map[KeyV1, Int])
        case class ContainerK2(data: scala.collection.immutable.Map[KeyV2, Int])

        val builder = MigrationBuilder[ContainerK1, ContainerK2, Nothing, Nothing](
          Schema.derived[ContainerK1],
          Schema.derived[ContainerK2],
          MigrationStep.Record.empty,
          MigrationStep.Variant.empty
        )
        val updatedBuilder = builder.transformKeys("data")(_.renameField("id", "key"))
        val migration      = updatedBuilder.toDynamicMigration

        val original = DynamicValue.Record(
          "data" -> DynamicValue.Map(
            DynamicValue.Record("id" -> DynamicValue.string("k1")) -> DynamicValue.int(42)
          )
        )
        val result = migration(original)

        assertTrue(
          result == Right(
            DynamicValue.Record(
              "data" -> DynamicValue.Map(
                DynamicValue.Record("key" -> DynamicValue.string("k1")) -> DynamicValue.int(42)
              )
            )
          )
        )
      }
    ),
    suite("Migration laws")(
      test("Identity law: identity migration returns original value unchanged") {
        val values = Vector(
          DynamicValue.Record("name" -> DynamicValue.string("Alice"), "age" -> DynamicValue.int(30)),
          DynamicValue.Variant("Active", DynamicValue.Record("since" -> DynamicValue.string("2024"))),
          DynamicValue.Sequence(DynamicValue.int(1), DynamicValue.int(2), DynamicValue.int(3)),
          DynamicValue.Map(DynamicValue.string("key") -> DynamicValue.int(42)),
          DynamicValue.int(42),
          DynamicValue.string("hello"),
          DynamicValue.Null
        )
        val identity = DynamicMigration.identity
        val results  = values.map(v => identity(v) == Right(v))
        assertTrue(results.forall(_ == true))
      },
      test("Structural reverse law: m.reverse.reverse equals m structurally") {
        val migrations = Vector(
          DynamicMigration.record(_.addField("newField", DynamicValue.int(0))),
          DynamicMigration.record(_.removeField("oldField", DynamicValue.string(""))),
          DynamicMigration.record(_.renameField("old", "new")),
          DynamicMigration.record(
            _.addField("a", DynamicValue.int(1))
              .renameField("b", "c")
              .removeField("d", DynamicValue.string("default"))
          ),
          DynamicMigration.variant(_.renameCase("OldCase", "NewCase")),
          DynamicMigration.record(
            _.nested("inner")(_.addField("deep", DynamicValue.boolean(true)))
          )
        )

        val results = migrations.map { m =>
          val doubleReversed = m.reverse.reverse
          doubleReversed.step == m.step
        }
        assertTrue(results.forall(_ == true))
      },
      test("Round-trip law: apply then reverse returns original value") {
        val original = DynamicValue.Record(
          "name"    -> DynamicValue.string("Alice"),
          "age"     -> DynamicValue.int(30),
          "address" -> DynamicValue.Record(
            "city"   -> DynamicValue.string("NYC"),
            "street" -> DynamicValue.string("123 Main")
          )
        )

        val migration = DynamicMigration.record(
          _.addField("active", DynamicValue.boolean(true))
            .renameField("age", "years")
            .nested("address")(_.addField("zip", DynamicValue.string("10001")))
        )

        val migrated = migration(original)
        val reversed = migrated.flatMap(migration.reverse.apply)

        assertTrue(reversed == Right(original))
      },
      test("Associativity law: (m1 ++ m2) ++ m3 produces same result as m1 ++ (m2 ++ m3)") {
        val original = DynamicValue.Record("x" -> DynamicValue.int(1))
        val m1       = DynamicMigration.record(_.addField("a", DynamicValue.int(10)))
        val m2       = DynamicMigration.record(_.addField("b", DynamicValue.int(20)))
        val m3       = DynamicMigration.record(_.addField("c", DynamicValue.int(30)))

        val leftAssoc  = (m1.andThen(m2)).andThen(m3)
        val rightAssoc = m1.andThen(m2.andThen(m3))

        val resultLeft  = leftAssoc(original)
        val resultRight = rightAssoc(original)

        assertTrue(
          resultLeft == resultRight,
          resultLeft.isRight
        )
      }
    ),
    suite("DynamicMigration serialization")(
      test("Complex record migration round-trips through DynamicValue") {
        val migration = DynamicMigration.record(
          _.addField("newField", DynamicValue.int(42))
            .removeField("oldField", DynamicValue.string("default"))
            .renameField("from", "to")
            .transformField("value", DynamicValueTransform.numericAdd(10), DynamicValueTransform.numericAdd(-10))
        )

        val schema   = Schema[DynamicMigration]
        val dv       = schema.toDynamicValue(migration)
        val restored = schema.fromDynamicValue(dv)

        assertTrue(restored.isRight)

        val original = DynamicValue.Record(
          "oldField" -> DynamicValue.string("removed"),
          "from"     -> DynamicValue.string("renamed"),
          "value"    -> DynamicValue.int(5)
        )

        val resultOriginal = migration(original)
        val resultRestored = restored.toOption.get.apply(original)

        assertTrue(resultOriginal == resultRestored)
      },
      test("Nested migration with multiple levels serializes correctly") {
        val migration = DynamicMigration.record(
          _.nested("level1")(
            _.nested("level2")(
              _.addField("deep", DynamicValue.boolean(true))
                .renameField("old", "new")
            )
          )
        )

        val schema   = Schema[DynamicMigration]
        val dv       = schema.toDynamicValue(migration)
        val restored = schema.fromDynamicValue(dv)

        assertTrue(restored.isRight)

        val original = DynamicValue.Record(
          "level1" -> DynamicValue.Record(
            "level2" -> DynamicValue.Record(
              "old" -> DynamicValue.string("value")
            )
          )
        )

        val resultOriginal = migration(original)
        val resultRestored = restored.toOption.get.apply(original)

        assertTrue(resultOriginal == resultRestored)
      },
      test("Variant migration with renames and nested transforms serializes correctly") {
        val migration = DynamicMigration.variant(
          _.renameCase("OldCase", "NewCase")
            .transformCase("NewCase")(_.addField("extra", DynamicValue.int(100)))
        )

        val schema   = Schema[DynamicMigration]
        val dv       = schema.toDynamicValue(migration)
        val restored = schema.fromDynamicValue(dv)

        assertTrue(restored.isRight)

        val original = DynamicValue.Variant("OldCase", DynamicValue.Record("data" -> DynamicValue.string("test")))

        val resultOriginal = migration(original)
        val resultRestored = restored.toOption.get.apply(original)

        assertTrue(resultOriginal == resultRestored)
      },
      test("Sequence and Map migrations serialize correctly") {
        val seqMigration = DynamicMigration(
          MigrationStep.Sequence(MigrationStep.Record.empty.addField("index", DynamicValue.int(0)))
        )
        val mapMigration = DynamicMigration(
          MigrationStep.MapEntries(
            MigrationStep.Record.empty.renameField("id", "key"),
            MigrationStep.Record.empty.addField("version", DynamicValue.int(1))
          )
        )

        val schema = Schema[DynamicMigration]

        val seqDv       = schema.toDynamicValue(seqMigration)
        val seqRestored = schema.fromDynamicValue(seqDv)

        val mapDv       = schema.toDynamicValue(mapMigration)
        val mapRestored = schema.fromDynamicValue(mapDv)

        assertTrue(
          seqRestored.isRight,
          mapRestored.isRight,
          seqRestored.toOption.get.step == seqMigration.step,
          mapRestored.toOption.get.step == mapMigration.step
        )
      }
    ),
    suite("Schema evolution scenarios")(
      test("Multi-version migration chain V1 -> V2 -> V3") {
        val v1ToV2 = DynamicMigration.record(_.addField("age", DynamicValue.int(0)))
        val v2ToV3 = DynamicMigration.record(_.addField("active", DynamicValue.boolean(true)))
        val v1ToV3 = v1ToV2.andThen(v2ToV3)

        val personV1 = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
        val result   = v1ToV3(personV1)

        assertTrue(
          result == Right(
            DynamicValue.Record(
              "name"   -> DynamicValue.string("Alice"),
              "age"    -> DynamicValue.int(0),
              "active" -> DynamicValue.boolean(true)
            )
          )
        )

        val reversed  = v1ToV3.reverse
        val roundTrip = result.flatMap(reversed.apply)
        assertTrue(roundTrip == Right(personV1))
      },
      test("Complex record evolution with rename, add, remove, and transform") {
        val migration = DynamicMigration.record(
          _.renameField("firstName", "name")
            .removeField("middleName", DynamicValue.string(""))
            .addField("fullName", DynamicValue.string(""))
            .transformField("age", DynamicValueTransform.numericAdd(1), DynamicValueTransform.numericAdd(-1))
        )

        val original = DynamicValue.Record(
          "firstName"  -> DynamicValue.string("John"),
          "middleName" -> DynamicValue.string("Q"),
          "age"        -> DynamicValue.int(29)
        )

        val result = migration(original)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            val fieldMap = fields.toVector.toMap
            assertTrue(
              fieldMap.get("name") == Some(DynamicValue.string("John")),
              fieldMap.get("age") == Some(DynamicValue.int(30)),
              fieldMap.get("fullName") == Some(DynamicValue.string("")),
              !fieldMap.contains("firstName"),
              !fieldMap.contains("middleName")
            )
          case _ => assertTrue(false)
        }
      },
      test("Nested object evolution with deep field changes") {
        val migration = DynamicMigration.record(
          _.renameField("user", "account")
            .nested("account")(
              _.renameField("email", "primaryEmail")
                .addField("verified", DynamicValue.boolean(false))
                .nested("profile")(
                  _.addField("avatar", DynamicValue.string("default.png"))
                )
            )
        )

        val original = DynamicValue.Record(
          "user" -> DynamicValue.Record(
            "email"   -> DynamicValue.string("user@example.com"),
            "profile" -> DynamicValue.Record(
              "displayName" -> DynamicValue.string("User")
            )
          )
        )

        val result   = migration(original)
        val reversed = result.flatMap(migration.reverse.apply)

        assertTrue(
          result.isRight,
          reversed == Right(original)
        )
      },
      test("Enum evolution with case rename and nested field migration") {
        val migration = DynamicMigration.variant(
          _.renameCase("Premium", "Pro")
            .transformCase("Pro")(
              _.addField("tier", DynamicValue.int(1))
                .renameField("expiresAt", "validUntil")
            )
            .transformCase("Free")(
              _.addField("adsEnabled", DynamicValue.boolean(true))
            )
        )

        val premiumUser = DynamicValue.Variant(
          "Premium",
          DynamicValue.Record("expiresAt" -> DynamicValue.string("2025-12-31"))
        )

        val freeUser = DynamicValue.Variant(
          "Free",
          DynamicValue.Record("signupDate" -> DynamicValue.string("2024-01-01"))
        )

        val premiumResult = migration(premiumUser)
        val freeResult    = migration(freeUser)

        premiumResult match {
          case Right(DynamicValue.Variant("Pro", DynamicValue.Record(fields))) =>
            val fieldMap = fields.toVector.toMap
            assertTrue(
              fieldMap.get("validUntil") == Some(DynamicValue.string("2025-12-31")),
              fieldMap.get("tier") == Some(DynamicValue.int(1)),
              !fieldMap.contains("expiresAt")
            )
          case _ => assertTrue(false)
        }

        freeResult match {
          case Right(DynamicValue.Variant("Free", DynamicValue.Record(fields))) =>
            val fieldMap = fields.toVector.toMap
            assertTrue(
              fieldMap.get("adsEnabled") == Some(DynamicValue.boolean(true)),
              fieldMap.get("signupDate") == Some(DynamicValue.string("2024-01-01"))
            )
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Edge cases")(
      test("Variant with unmatched case passes through unchanged") {
        val migration = DynamicMigration.variant(
          _.renameCase("OldCase", "NewCase")
            .transformCase("NewCase")(_.addField("x", DynamicValue.int(1)))
        )

        val unmatchedVariant = DynamicValue.Variant(
          "OtherCase",
          DynamicValue.Record("data" -> DynamicValue.string("untouched"))
        )

        val result = migration(unmatchedVariant)

        assertTrue(result == Right(unmatchedVariant))
      },
      test("Empty record with no fields migrates correctly") {
        val migration = DynamicMigration.record(_.addField("first", DynamicValue.int(1)))
        val empty     = DynamicValue.Record()
        val result    = migration(empty)

        assertTrue(result == Right(DynamicValue.Record("first" -> DynamicValue.int(1))))
      },
      test("Empty sequence migration returns empty sequence") {
        val migration = DynamicMigration.sequence(
          DynamicMigration.record(_.addField("x", DynamicValue.int(0)))
        )
        val empty  = DynamicValue.Sequence()
        val result = migration(empty)

        assertTrue(result == Right(DynamicValue.Sequence()))
      },
      test("Empty map migration returns empty map") {
        val migration = DynamicMigration(
          MigrationStep.MapEntries(
            MigrationStep.NoOp,
            MigrationStep.Record.empty.addField("version", DynamicValue.int(1))
          )
        )
        val empty  = DynamicValue.Map()
        val result = migration(empty)

        assertTrue(result == Right(DynamicValue.Map()))
      },
      test("Deeply nested empty migration step acts as identity") {
        val migration = DynamicMigration.record(
          _.nested("a")(
            _.nested("b")(
              _.nested("c")(identity)
            )
          )
        )

        val original = DynamicValue.Record(
          "a" -> DynamicValue.Record(
            "b" -> DynamicValue.Record(
              "c" -> DynamicValue.Record("value" -> DynamicValue.int(42))
            )
          )
        )

        val result = migration(original)
        assertTrue(result == Right(original))
      },
      test("Migration on nested field that doesn't exist in some records") {
        val migration = DynamicMigration.record(
          _.nested("optional")(_.addField("added", DynamicValue.int(1)))
        )

        val withField = DynamicValue.Record(
          "optional" -> DynamicValue.Record("existing" -> DynamicValue.string("yes"))
        )

        val withoutField = DynamicValue.Record(
          "other" -> DynamicValue.string("no optional field")
        )

        val resultWith    = migration(withField)
        val resultWithout = migration(withoutField)

        assertTrue(resultWith.isRight)
        assertTrue(resultWithout.isLeft)
        assertTrue(resultWithout.left.exists(_.isInstanceOf[MigrationError.FieldNotFound]))
      },
      test("Sequence with mixed element types handles migration correctly") {
        val original = DynamicValue.Sequence(
          DynamicValue.Record("type" -> DynamicValue.string("a"), "value" -> DynamicValue.int(1)),
          DynamicValue.Record("type" -> DynamicValue.string("b"), "value" -> DynamicValue.int(2))
        )

        val migration = DynamicMigration.sequence(
          DynamicMigration.record(_.addField("processed", DynamicValue.boolean(true)))
        )

        val result = migration(original)

        assertTrue(
          result == Right(
            DynamicValue.Sequence(
              DynamicValue.Record(
                "type"      -> DynamicValue.string("a"),
                "value"     -> DynamicValue.int(1),
                "processed" -> DynamicValue.boolean(true)
              ),
              DynamicValue.Record(
                "type"      -> DynamicValue.string("b"),
                "value"     -> DynamicValue.int(2),
                "processed" -> DynamicValue.boolean(true)
              )
            )
          )
        )
      }
    ),
    suite("Error paths include location")(
      test("Nested field not found error includes full path") {
        val migration = DynamicMigration.record(
          _.nested("level1")(
            _.nested("level2")(
              _.removeField("nonexistent", DynamicValue.Null)
            )
          )
        )

        val original = DynamicValue.Record(
          "level1" -> DynamicValue.Record(
            "level2" -> DynamicValue.Record(
              "existing" -> DynamicValue.string("value")
            )
          )
        )

        val result = migration(original)

        result match {
          case Left(MigrationError.FieldNotFound(source, fieldName)) =>
            assertTrue(
              fieldName == "nonexistent",
              source.toScalaString.contains("level1"),
              source.toScalaString.contains("level2")
            )
          case _ => assertTrue(false)
        }
      },
      test("Transform failure at nested level includes path in error") {
        val failingTransform = DynamicValueTransform.stringAppend(" suffix")

        val migration = DynamicMigration.record(
          _.nested("container")(
            _.transformField("value", failingTransform, DynamicValueTransform.identity)
          )
        )

        val original = DynamicValue.Record(
          "container" -> DynamicValue.Record(
            "value" -> DynamicValue.int(42)
          )
        )

        val result = migration(original)

        result match {
          case Left(MigrationError.TransformFailed(source, reason)) =>
            assertTrue(
              reason.contains("StringAppend"),
              source.toScalaString.contains("container"),
              source.toScalaString.contains("value")
            )
          case _ => assertTrue(false)
        }
      },
      test("Sequence element failure includes index in path") {
        val migration = DynamicMigration.sequence(
          DynamicMigration.record(_.removeField("required", DynamicValue.Null))
        )

        val original = DynamicValue.Sequence(
          DynamicValue.Record("required" -> DynamicValue.int(1)),
          DynamicValue.Record("required" -> DynamicValue.int(2)),
          DynamicValue.Record("missing"  -> DynamicValue.int(3))
        )

        val result = migration(original)

        result match {
          case Left(MigrationError.FieldNotFound(source, _)) =>
            assertTrue(source.toScalaString.contains(".at(2)"))
          case _ => assertTrue(false)
        }
      },
      test("Map value migration failure includes path context") {
        val migration = DynamicMigration(
          MigrationStep.MapEntries(
            MigrationStep.NoOp,
            MigrationStep.Record.empty
              .withFieldAction(FieldAction.Remove("required", DynamicValue.Null))
          )
        )

        val original = DynamicValue.Map(
          DynamicValue.string("key1") -> DynamicValue.Record("required" -> DynamicValue.int(1)),
          DynamicValue.string("key2") -> DynamicValue.Record("other" -> DynamicValue.int(2))
        )

        val result = migration(original)

        result match {
          case Left(MigrationError.FieldNotFound(source, _)) =>
            assertTrue(source.toScalaString.contains(".eachValue"))
          case _ => assertTrue(false)
        }
      },
      test("Type mismatch error includes expected and actual types") {
        val migration = DynamicMigration.record(_.addField("x", DynamicValue.int(1)))
        val original  = DynamicValue.Sequence(DynamicValue.int(1), DynamicValue.int(2))

        val result = migration(original)

        result match {
          case Left(MigrationError.TypeMismatch(_, expected, _)) =>
            assertTrue(expected == "Record")
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Composition edge cases")(
      test("Composing add then remove of same field results in no net change") {
        val m1       = DynamicMigration.record(_.addField("temp", DynamicValue.int(42)))
        val m2       = DynamicMigration.record(_.removeField("temp", DynamicValue.int(0)))
        val composed = m1.andThen(m2)

        val original = DynamicValue.Record("existing" -> DynamicValue.string("value"))
        val result   = composed(original)

        assertTrue(result == Right(original))
      },
      test("Composing rename then add to old name works correctly") {
        val m1       = DynamicMigration.record(_.renameField("old", "new"))
        val m2       = DynamicMigration.record(_.addField("old", DynamicValue.string("reused")))
        val composed = m1.andThen(m2)

        val original = DynamicValue.Record("old" -> DynamicValue.int(1))
        val result   = composed(original)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            val fieldMap = fields.toVector.toMap
            assertTrue(
              fieldMap.get("new") == Some(DynamicValue.int(1)),
              fieldMap.get("old") == Some(DynamicValue.string("reused"))
            )
          case _ => assertTrue(false)
        }
      },
      test("Nested migration composition merges correctly") {
        val m1 = DynamicMigration.record(
          _.nested("inner")(_.addField("a", DynamicValue.int(1)))
        )
        val m2 = DynamicMigration.record(
          _.nested("inner")(_.addField("b", DynamicValue.int(2)))
        )
        val composed = m1.andThen(m2)

        val original = DynamicValue.Record(
          "inner" -> DynamicValue.Record("existing" -> DynamicValue.string("x"))
        )
        val result = composed(original)

        assertTrue(
          result == Right(
            DynamicValue.Record(
              "inner" -> DynamicValue.Record(
                "existing" -> DynamicValue.string("x"),
                "a"        -> DynamicValue.int(1),
                "b"        -> DynamicValue.int(2)
              )
            )
          )
        )
      },
      test("Composing variant and nested case migrations") {
        val m1       = DynamicMigration.variant(_.renameCase("A", "B"))
        val m2       = DynamicMigration.variant(_.transformCase("B")(_.addField("x", DynamicValue.int(1))))
        val composed = m1.andThen(m2)

        val original = DynamicValue.Variant("A", DynamicValue.Record("data" -> DynamicValue.string("test")))
        val result   = composed(original)

        assertTrue(
          result == Right(
            DynamicValue.Variant(
              "B",
              DynamicValue.Record(
                "data" -> DynamicValue.string("test"),
                "x"    -> DynamicValue.int(1)
              )
            )
          )
        )
      }
    ),
    suite("FieldAction serialization")(
      test("All FieldAction types round-trip through DynamicValue") {
        val actions: Vector[FieldAction] = Vector(
          FieldAction.Add("field", DynamicValue.int(42)),
          FieldAction.Remove("field", DynamicValue.string("default")),
          FieldAction.Rename("old", "new"),
          FieldAction.Transform("field", DynamicValueTransform.identity, DynamicValueTransform.identity),
          FieldAction.MakeOptional("field", DynamicValue.Null),
          FieldAction.MakeRequired("field", DynamicValue.int(0)),
          FieldAction.ChangeType("field", PrimitiveConversion.IntToLong, PrimitiveConversion.LongToInt),
          FieldAction
            .JoinFields("target", Vector("a", "b"), DynamicValueTransform.identity, DynamicValueTransform.identity),
          FieldAction.SplitField(
            "source",
            Vector("x", "y"),
            DynamicValueTransform.identity,
            DynamicValueTransform.identity
          )
        )

        val schema  = Schema[FieldAction]
        val results = actions.map { action =>
          val dv       = schema.toDynamicValue(action)
          val restored = schema.fromDynamicValue(dv)
          restored.isRight && restored.toOption.get == action
        }

        assertTrue(results.forall(_ == true))
      }
    ),
    suite("MigrationStep serialization")(
      test("All MigrationStep types round-trip through DynamicValue") {
        val steps: Vector[MigrationStep] = Vector(
          MigrationStep.NoOp,
          MigrationStep.Record.empty.addField("x", DynamicValue.int(1)),
          MigrationStep.Variant.empty.renameCase("A", "B"),
          MigrationStep.Sequence(MigrationStep.Record.empty.addField("i", DynamicValue.int(0))),
          MigrationStep.MapEntries(
            MigrationStep.Record.empty.renameField("id", "key"),
            MigrationStep.Record.empty.addField("version", DynamicValue.int(1))
          )
        )

        val schema  = Schema[MigrationStep]
        val results = steps.map { step =>
          val dv       = schema.toDynamicValue(step)
          val restored = schema.fromDynamicValue(dv)
          restored.isRight && restored.toOption.get == step
        }

        assertTrue(results.forall(_ == true))
      }
    ),
    suite("DynamicValueTransform serialization")(
      test("All DynamicValueTransform types round-trip through DynamicValue") {
        val transforms: Vector[DynamicValueTransform] = Vector(
          DynamicValueTransform.identity,
          DynamicValueTransform.constant(DynamicValue.string("const")),
          DynamicValueTransform.stringAppend(" suffix"),
          DynamicValueTransform.stringPrepend("prefix "),
          DynamicValueTransform.stringReplace("old", "new"),
          DynamicValueTransform.numericAdd(10),
          DynamicValueTransform.numericMultiply(2),
          DynamicValueTransform.wrapInSome,
          DynamicValueTransform.unwrapSome(DynamicValue.Null),
          DynamicValueTransform.stringJoinFields(Vector("a", "b"), "-"),
          DynamicValueTransform.stringSplitToFields(Vector("x", "y"), "-")
        )

        val schema  = Schema[DynamicValueTransform]
        val results = transforms.map { transform =>
          val dv       = schema.toDynamicValue(transform)
          val restored = schema.fromDynamicValue(dv)
          restored.isRight
        }

        assertTrue(results.forall(_ == true))
      }
    ),
    suite("Error propagation in fold operations")(
      test("Multiple field action errors stop at first failure") {
        val original  = DynamicValue.Record("name" -> DynamicValue.string("Alice"))
        val migration = DynamicMigration.record(
          _.removeField("nonexistent1", DynamicValue.Null)
            .removeField("nonexistent2", DynamicValue.Null)
        )
        val result = migration(original)

        result match {
          case Left(MigrationError.FieldNotFound(_, "nonexistent1")) => assertTrue(true)
          case _                                                     => assertTrue(false)
        }
      },
      test("Nested field error propagates correctly when earlier nested step fails") {
        val original = DynamicValue.Record(
          "field1" -> DynamicValue.Record("inner" -> DynamicValue.int(1)),
          "field2" -> DynamicValue.Record("inner" -> DynamicValue.int(2))
        )
        val migration = DynamicMigration.record(
          _.nested("field1")(_.removeField("missing", DynamicValue.Null))
            .nested("field2")(_.addField("added", DynamicValue.int(0)))
        )
        val result = migration(original)

        result match {
          case Left(MigrationError.FieldNotFound(_, "missing")) => assertTrue(true)
          case _                                                => assertTrue(false)
        }
      },
      test("Map entry error propagates on second entry failure") {
        val original = DynamicValue.Map(
          DynamicValue.string("key1") -> DynamicValue.Record("required" -> DynamicValue.int(1)),
          DynamicValue.string("key2") -> DynamicValue.Record("other" -> DynamicValue.int(2))
        )
        val migration = DynamicMigration(
          MigrationStep.MapEntries(
            MigrationStep.NoOp,
            MigrationStep.Record.empty.withFieldAction(FieldAction.Remove("required", DynamicValue.Null))
          )
        )
        val result = migration(original)

        assertTrue(result.isLeft)
      },
      test("Sequence element error propagates on later element failure") {
        val original = DynamicValue.Sequence(
          DynamicValue.Record("field" -> DynamicValue.int(1)),
          DynamicValue.Record("field" -> DynamicValue.int(2)),
          DynamicValue.Record("wrong" -> DynamicValue.int(3))
        )
        val migration = DynamicMigration.sequence(
          DynamicMigration.record(_.removeField("field", DynamicValue.Null))
        )
        val result = migration(original)

        result match {
          case Left(MigrationError.FieldNotFound(path, "field")) =>
            assertTrue(path.toScalaString.contains(".at(2)"))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Variant type mismatch")(
      test("Variant migration fails when applied to non-Variant value") {
        val original  = DynamicValue.Record("field" -> DynamicValue.int(1))
        val migration = DynamicMigration(MigrationStep.Variant.empty.renameCase("A", "B"))
        val result    = migration(original)

        result match {
          case Left(MigrationError.TypeMismatch(_, expected, _)) => assertTrue(expected == "Variant")
          case _                                                 => assertTrue(false)
        }
      }
    ),
    suite("Map type mismatch")(
      test("Map migration fails when applied to non-Map value") {
        val original  = DynamicValue.Record("field" -> DynamicValue.int(1))
        val migration = DynamicMigration(MigrationStep.MapEntries(MigrationStep.NoOp, MigrationStep.NoOp))
        val result    = migration(original)

        result match {
          case Left(MigrationError.TypeMismatch(_, expected, _)) => assertTrue(expected == "Map")
          case _                                                 => assertTrue(false)
        }
      }
    ),
    suite("IntToDouble conversion errors")(
      test("IntToDouble fails for non-Int input") {
        val value  = DynamicValue.string("not an int")
        val result = PrimitiveConversion.IntToDouble(value)
        assertTrue(result.isLeft)
      }
    ),
    suite("Rename field edge cases")(
      test("Rename fails when target field already exists") {
        val original = DynamicValue.Record(
          "oldName" -> DynamicValue.string("value"),
          "newName" -> DynamicValue.string("existing")
        )
        val migration = DynamicMigration.record(_.renameField("oldName", "newName"))
        val result    = migration(original)

        result match {
          case Left(MigrationError.FieldAlreadyExists(_, "newName")) => assertTrue(true)
          case _                                                     => assertTrue(false)
        }
      },
      test("Rename fails when source field does not exist") {
        val original  = DynamicValue.Record("other" -> DynamicValue.string("value"))
        val migration = DynamicMigration.record(_.renameField("nonexistent", "newName"))
        val result    = migration(original)

        result match {
          case Left(MigrationError.FieldNotFound(_, "nonexistent")) => assertTrue(true)
          case _                                                    => assertTrue(false)
        }
      }
    ),
    suite("Compose Sequence and MapEntries steps")(
      test("andThen composes two Sequence migrations") {
        val m1       = DynamicMigration(MigrationStep.Sequence(MigrationStep.Record.empty.addField("a", DynamicValue.int(1))))
        val m2       = DynamicMigration(MigrationStep.Sequence(MigrationStep.Record.empty.addField("b", DynamicValue.int(2))))
        val composed = m1.andThen(m2)

        val original = DynamicValue.Sequence(DynamicValue.Record("x" -> DynamicValue.string("test")))
        val result   = composed(original)

        result match {
          case Right(DynamicValue.Sequence(elems)) =>
            val fields = elems.head.asInstanceOf[DynamicValue.Record].fields.toVector.toMap
            assertTrue(
              fields.contains("a"),
              fields.contains("b"),
              fields.contains("x")
            )
          case _ => assertTrue(false)
        }
      },
      test("andThen composes two MapEntries migrations") {
        val m1 = DynamicMigration(
          MigrationStep.MapEntries(MigrationStep.NoOp, MigrationStep.Record.empty.addField("v1", DynamicValue.int(1)))
        )
        val m2 = DynamicMigration(
          MigrationStep.MapEntries(MigrationStep.NoOp, MigrationStep.Record.empty.addField("v2", DynamicValue.int(2)))
        )
        val composed = m1.andThen(m2)

        val original = DynamicValue.Map(
          DynamicValue.string("k") -> DynamicValue.Record("existing" -> DynamicValue.string("x"))
        )
        val result = composed(original)

        result match {
          case Right(DynamicValue.Map(entries)) =>
            val (_, value) = entries.head
            val fields     = value.asInstanceOf[DynamicValue.Record].fields.toVector.toMap
            assertTrue(
              fields.contains("v1"),
              fields.contains("v2"),
              fields.contains("existing")
            )
          case _ => assertTrue(false)
        }
      }
    ),
    suite("SplitField error cases")(
      test("SplitField fails when splitter returns non-Record") {
        val nonRecordSplitter = DynamicValueTransform.constant(DynamicValue.string("not a record"))

        val original = DynamicValue.Record(
          "source" -> DynamicValue.string("value")
        )
        val migration = DynamicMigration.record(
          _.splitField("source", Vector("a", "b"), nonRecordSplitter, DynamicValueTransform.identity)
        )
        val result = migration(original)

        result match {
          case Left(MigrationError.TransformFailed(_, msg)) =>
            assertTrue(msg.contains("Record"))
          case _ => assertTrue(false)
        }
      },
      test("SplitField fails when splitter returns Record without all target fields") {
        val incompleteSplitter = DynamicValueTransform.constant(
          DynamicValue.Record("a" -> DynamicValue.string("only a"))
        )

        val original = DynamicValue.Record(
          "source" -> DynamicValue.string("value")
        )
        val migration = DynamicMigration.record(
          _.splitField("source", Vector("a", "b", "c"), incompleteSplitter, DynamicValueTransform.identity)
        )
        val result = migration(original)

        result match {
          case Left(MigrationError.TransformFailed(_, msg)) =>
            assertTrue(msg.contains("all target fields"))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Nested step edge cases")(
      test("Empty nested step on missing field returns error") {
        val migration = DynamicMigration.record(
          _.nested("missing")(_.addField("x", DynamicValue.int(1)))
        )
        val original = DynamicValue.Record("other" -> DynamicValue.int(42))
        val result   = migration(original)

        result match {
          case Left(MigrationError.FieldNotFound(_, "missing")) => assertTrue(true)
          case _                                                => assertTrue(false)
        }
      }
    ),
    suite("MergeNestedMaps coverage")(
      test("andThen with nested fields only in first migration") {
        val m1 = DynamicMigration.record(
          _.nested("inner")(_.addField("a", DynamicValue.int(1)))
        )
        val m2 = DynamicMigration.record(
          _.addField("outer", DynamicValue.string("x"))
        )
        val composed = m1.andThen(m2)

        val original = DynamicValue.Record(
          "inner" -> DynamicValue.Record("existing" -> DynamicValue.int(0))
        )
        val result = composed(original)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            val fieldMap    = fields.toVector.toMap
            val innerFields = fieldMap("inner").asInstanceOf[DynamicValue.Record].fields.toVector.toMap
            assertTrue(
              innerFields.contains("a"),
              fieldMap.contains("outer")
            )
          case _ => assertTrue(false)
        }
      },
      test("andThen with nested fields only in second migration") {
        val m1 = DynamicMigration.record(
          _.addField("outer", DynamicValue.string("x"))
        )
        val m2 = DynamicMigration.record(
          _.nested("inner")(_.addField("b", DynamicValue.int(2)))
        )
        val composed = m1.andThen(m2)

        val original = DynamicValue.Record(
          "inner" -> DynamicValue.Record("existing" -> DynamicValue.int(0))
        )
        val result = composed(original)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            val fieldMap    = fields.toVector.toMap
            val innerFields = fieldMap("inner").asInstanceOf[DynamicValue.Record].fields.toVector.toMap
            assertTrue(
              innerFields.contains("b"),
              fieldMap.contains("outer")
            )
          case _ => assertTrue(false)
        }
      },
      test("Empty nested step is allowed when field doesn't exist") {
        val emptyNestedStep = MigrationStep.Record(Vector.empty, Map("missing" -> MigrationStep.NoOp))
        val migration       = DynamicMigration(emptyNestedStep)
        val original        = DynamicValue.Record("other" -> DynamicValue.int(42))
        val result          = migration(original)

        assertTrue(result.isRight)
      }
    ),
    suite("Sequence transform error propagation")(
      test("Sequence transform stops at first failure and propagates error") {
        val failingFirst = DynamicValueTransform.sequence(
          DynamicValueTransform.stringAppend(" suffix"),
          DynamicValueTransform.identity,
          DynamicValueTransform.stringPrepend("prefix ")
        )
        val result = failingFirst(DynamicValue.int(10))
        assertTrue(result.isLeft)
      }
    ),
    suite("MakeRequired with Null value")(
      test("MakeRequired handles DynamicValue.Null") {
        val original = DynamicValue.Record(
          "field" -> DynamicValue.Null
        )
        val migration = DynamicMigration.record(
          _.makeFieldRequired("field", DynamicValue.string("default"))
        )
        val result = migration(original)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            val fieldMap = fields.toVector.toMap
            assertTrue(fieldMap("field") == DynamicValue.string("default"))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("JoinFields combiner error")(
      test("JoinFields fails when combiner returns Left") {
        val original = DynamicValue.Record(
          "a" -> DynamicValue.int(1),
          "b" -> DynamicValue.int(2)
        )
        val failingCombiner = DynamicValueTransform.stringJoinFields(Vector("a", "b"), "-")
        val migration       = DynamicMigration.record(
          _.joinFields("combined", Vector("a", "b"), failingCombiner, DynamicValueTransform.identity)
        )
        val result = migration(original)

        result match {
          case Left(MigrationError.TransformFailed(_, _)) => assertTrue(true)
          case _                                          => assertTrue(false)
        }
      }
    ),
    suite("Map key migration error propagation")(
      test("Map migration fails on second entry and propagates to third") {
        val original = DynamicValue.Map(
          DynamicValue.Record("id" -> DynamicValue.string("a"))    -> DynamicValue.int(1),
          DynamicValue.Record("wrong" -> DynamicValue.string("b")) -> DynamicValue.int(2),
          DynamicValue.Record("id" -> DynamicValue.string("c"))    -> DynamicValue.int(3)
        )
        val keyMigration = DynamicMigration.record(_.removeField("id", DynamicValue.Null))
        val migration    = DynamicMigration(MigrationStep.MapEntries(keyMigration.step, MigrationStep.NoOp))
        val result       = migration(original)

        assertTrue(result.isLeft)
      }
    ),
    suite("SplitField splitter error")(
      test("SplitField fails when splitter returns Left") {
        val failingSplitter = DynamicValueTransform.numericAdd(1)
        val original        = DynamicValue.Record(
          "source" -> DynamicValue.string("value")
        )
        val migration = DynamicMigration.record(
          _.splitField("source", Vector("a", "b"), failingSplitter, DynamicValueTransform.identity)
        )
        val result = migration(original)

        result match {
          case Left(MigrationError.TransformFailed(_, _)) => assertTrue(true)
          case _                                          => assertTrue(false)
        }
      }
    ),
    suite("MakeOptional field not found")(
      test("MakeOptional fails when field does not exist in record") {
        val original  = DynamicValue.Record("other" -> DynamicValue.int(1))
        val migration = DynamicMigration.record(_.makeFieldOptional("missing", DynamicValue.Null))
        val result    = migration(original)

        result match {
          case Left(MigrationError.FieldNotFound(_, "missing")) => assertTrue(true)
          case _                                                => assertTrue(false)
        }
      }
    ),
    suite("NumericMultiply coverage")(
      test("NumericMultiply on Long values") {
        val transform = DynamicValueTransform.numericMultiply(2)
        val result    = transform(DynamicValue.long(5L))
        assertTrue(result == Right(DynamicValue.long(10L)))
      },
      test("NumericMultiply on Long overflow") {
        val transform = DynamicValueTransform.numericMultiply(BigDecimal(Long.MaxValue))
        val result    = transform(DynamicValue.long(2L))
        assertTrue(result.isLeft)
      },
      test("NumericMultiply on Float values") {
        val transform = DynamicValueTransform.numericMultiply(2)
        val result    = transform(DynamicValue.float(2.5f))
        result match {
          case Right(DynamicValue.Primitive(PrimitiveValue.Float(f))) =>
            assertTrue(f == 5.0f)
          case _ => assertTrue(false)
        }
      },
      test("NumericMultiply on non-numeric fails") {
        val transform = DynamicValueTransform.numericMultiply(2)
        val result    = transform(DynamicValue.string("not a number"))
        assertTrue(result.isLeft)
      },
      test("NumericMultiply on BigDecimal values") {
        val transform = DynamicValueTransform.numericMultiply(2)
        val result    = transform(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal("3.14"))))
        result match {
          case Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(bd))) =>
            assertTrue(bd == BigDecimal("6.28"))
          case _ => assertTrue(false)
        }
      },
      test("NumericMultiply on BigInt values") {
        val transform = DynamicValueTransform.numericMultiply(3)
        val result    = transform(DynamicValue.bigInt(BigInt(100)))
        result match {
          case Right(DynamicValue.Primitive(PrimitiveValue.BigInt(bi))) =>
            assertTrue(bi == BigInt(300))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("NumericAdd coverage")(
      test("NumericAdd on Long values") {
        val transform = DynamicValueTransform.numericAdd(5)
        val result    = transform(DynamicValue.long(10L))
        assertTrue(result == Right(DynamicValue.long(15L)))
      },
      test("NumericAdd on Long overflow") {
        val transform = DynamicValueTransform.numericAdd(BigDecimal(Long.MaxValue))
        val result    = transform(DynamicValue.long(1L))
        assertTrue(result.isLeft)
      },
      test("NumericAdd on Float values") {
        val transform = DynamicValueTransform.numericAdd(1)
        val result    = transform(DynamicValue.float(2.5f))
        result match {
          case Right(DynamicValue.Primitive(PrimitiveValue.Float(f))) =>
            assertTrue(f == 3.5f)
          case _ => assertTrue(false)
        }
      },
      test("NumericAdd on BigDecimal values") {
        val transform = DynamicValueTransform.numericAdd(BigDecimal("0.5"))
        val result    = transform(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal("1.5"))))
        result match {
          case Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(bd))) =>
            assertTrue(bd == BigDecimal("2.0"))
          case _ => assertTrue(false)
        }
      },
      test("NumericAdd on BigInt values") {
        val transform = DynamicValueTransform.numericAdd(50)
        val result    = transform(DynamicValue.bigInt(BigInt(100)))
        result match {
          case Right(DynamicValue.Primitive(PrimitiveValue.BigInt(bi))) =>
            assertTrue(bi == BigInt(150))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("StringSplitToFields coverage")(
      test("StringSplitToFields splits correctly") {
        val transform = DynamicValueTransform.stringSplitToFields(Vector("first", "last"), " ")
        val result    = transform(DynamicValue.string("John Doe"))
        result match {
          case Right(DynamicValue.Record(fields)) =>
            val fieldMap = fields.toVector.toMap
            assertTrue(
              fieldMap.get("first") == Some(DynamicValue.string("John")),
              fieldMap.get("last") == Some(DynamicValue.string("Doe"))
            )
          case _ => assertTrue(false)
        }
      },
      test("StringSplitToFields fails on non-string") {
        val transform = DynamicValueTransform.stringSplitToFields(Vector("a", "b"), " ")
        val result    = transform(DynamicValue.int(123))
        assertTrue(result.isLeft)
      },
      test("StringSplitToFields pads with empty strings when fewer parts") {
        val transform = DynamicValueTransform.stringSplitToFields(Vector("a", "b", "c"), " ")
        val result    = transform(DynamicValue.string("only two"))
        result match {
          case Right(DynamicValue.Record(fields)) =>
            val fieldMap = fields.toVector.toMap
            assertTrue(
              fieldMap.get("a") == Some(DynamicValue.string("only")),
              fieldMap.get("b") == Some(DynamicValue.string("two")),
              fieldMap.get("c") == Some(DynamicValue.string(""))
            )
          case _ => assertTrue(false)
        }
      }
    ),
    suite("PrimitiveConversion type errors")(
      test("LongToString fails on non-Long") {
        val result = PrimitiveConversion.LongToString(DynamicValue.string("not a long"))
        assertTrue(result.isLeft)
      },
      test("StringToLong fails on non-String") {
        val result = PrimitiveConversion.StringToLong(DynamicValue.int(123))
        assertTrue(result.isLeft)
      },
      test("DoubleToString fails on non-Double") {
        val result = PrimitiveConversion.DoubleToString(DynamicValue.string("not a double"))
        assertTrue(result.isLeft)
      },
      test("StringToDouble fails on non-String") {
        val result = PrimitiveConversion.StringToDouble(DynamicValue.int(123))
        assertTrue(result.isLeft)
      },
      test("StringToLong fails on non-parseable string") {
        val result = PrimitiveConversion.StringToLong(DynamicValue.string("not-a-number"))
        assertTrue(result.isLeft)
      },
      test("StringToDouble fails on non-parseable string") {
        val result = PrimitiveConversion.StringToDouble(DynamicValue.string("not-a-number"))
        assertTrue(result.isLeft)
      },
      test("IntToString success") {
        val result = PrimitiveConversion.IntToString(DynamicValue.int(42))
        assertTrue(result == Right(DynamicValue.string("42")))
      },
      test("StringToInt success") {
        val result = PrimitiveConversion.StringToInt(DynamicValue.string("42"))
        assertTrue(result == Right(DynamicValue.int(42)))
      },
      test("StringToInt fails on non-parseable") {
        val result = PrimitiveConversion.StringToInt(DynamicValue.string("not-a-number"))
        assertTrue(result.isLeft)
      },
      test("FloatToDouble success") {
        val result = PrimitiveConversion.FloatToDouble(DynamicValue.float(1.5f))
        result match {
          case Right(DynamicValue.Primitive(PrimitiveValue.Double(d))) =>
            assertTrue(d == 1.5)
          case _ => assertTrue(false)
        }
      },
      test("DoubleToFloat success") {
        val result = PrimitiveConversion.DoubleToFloat(DynamicValue.double(1.5))
        result match {
          case Right(DynamicValue.Primitive(PrimitiveValue.Float(f))) =>
            assertTrue(f == 1.5f)
          case _ => assertTrue(false)
        }
      },
      test("BooleanToString success") {
        val result = PrimitiveConversion.BooleanToString(DynamicValue.boolean(true))
        assertTrue(result == Right(DynamicValue.string("true")))
      },
      test("StringToBoolean success") {
        val result = PrimitiveConversion.StringToBoolean(DynamicValue.string("true"))
        assertTrue(result == Right(DynamicValue.boolean(true)))
      },
      test("StringToBoolean fails on invalid") {
        val result = PrimitiveConversion.StringToBoolean(DynamicValue.string("not-a-bool"))
        assertTrue(result.isLeft)
      },
      test("IntToDouble success") {
        val result = PrimitiveConversion.IntToDouble(DynamicValue.int(42))
        result match {
          case Right(DynamicValue.Primitive(PrimitiveValue.Double(d))) =>
            assertTrue(d == 42.0)
          case _ => assertTrue(false)
        }
      },
      test("DoubleToInt success") {
        val result = PrimitiveConversion.DoubleToInt(DynamicValue.double(42.0))
        assertTrue(result == Right(DynamicValue.int(42)))
      },
      test("DoubleToInt fails on NaN") {
        val result = PrimitiveConversion.DoubleToInt(DynamicValue.double(Double.NaN))
        assertTrue(result.isLeft)
      },
      test("DoubleToInt fails on Infinity") {
        val result = PrimitiveConversion.DoubleToInt(DynamicValue.double(Double.PositiveInfinity))
        assertTrue(result.isLeft)
      },
      test("DoubleToInt fails on overflow") {
        val result = PrimitiveConversion.DoubleToInt(DynamicValue.double(Double.MaxValue))
        assertTrue(result.isLeft)
      }
    ),
    suite("String transform error cases")(
      test("StringAppend fails on non-String") {
        val result = DynamicValueTransform.stringAppend("suffix")(DynamicValue.int(123))
        assertTrue(result.isLeft)
      },
      test("StringPrepend fails on non-String") {
        val result = DynamicValueTransform.stringPrepend("prefix")(DynamicValue.int(123))
        assertTrue(result.isLeft)
      },
      test("StringReplace fails on non-String") {
        val result = DynamicValueTransform.stringReplace("a", "b")(DynamicValue.int(123))
        assertTrue(result.isLeft)
      }
    ),
    suite("More PrimitiveConversion error cases")(
      test("FloatToDouble fails on non-Float") {
        val result = PrimitiveConversion.FloatToDouble(DynamicValue.int(123))
        assertTrue(result.isLeft)
      },
      test("DoubleToFloat fails on non-Double") {
        val result = PrimitiveConversion.DoubleToFloat(DynamicValue.int(123))
        assertTrue(result.isLeft)
      },
      test("DoubleToFloat fails on overflow") {
        val result = PrimitiveConversion.DoubleToFloat(DynamicValue.double(Double.MaxValue))
        assertTrue(result.isLeft)
      },
      test("DoubleToFloat handles NaN") {
        val result = PrimitiveConversion.DoubleToFloat(DynamicValue.double(Double.NaN))
        result match {
          case Right(DynamicValue.Primitive(PrimitiveValue.Float(f))) =>
            assertTrue(f.isNaN)
          case _ => assertTrue(false)
        }
      },
      test("DoubleToFloat handles Infinity") {
        val result = PrimitiveConversion.DoubleToFloat(DynamicValue.double(Double.PositiveInfinity))
        result match {
          case Right(DynamicValue.Primitive(PrimitiveValue.Float(f))) =>
            assertTrue(f.isInfinite)
          case _ => assertTrue(false)
        }
      },
      test("BooleanToString fails on non-Boolean") {
        val result = PrimitiveConversion.BooleanToString(DynamicValue.int(123))
        assertTrue(result.isLeft)
      },
      test("StringToBoolean fails on non-String") {
        val result = PrimitiveConversion.StringToBoolean(DynamicValue.int(123))
        assertTrue(result.isLeft)
      },
      test("IntToDouble fails on non-Int") {
        val result = PrimitiveConversion.IntToDouble(DynamicValue.string("not int"))
        assertTrue(result.isLeft)
      },
      test("DoubleToInt fails on non-Double") {
        val result = PrimitiveConversion.DoubleToInt(DynamicValue.string("not double"))
        assertTrue(result.isLeft)
      },
      test("IntToString fails on non-Int") {
        val result = PrimitiveConversion.IntToString(DynamicValue.string("not int"))
        assertTrue(result.isLeft)
      },
      test("StringToInt fails on non-String") {
        val result = PrimitiveConversion.StringToInt(DynamicValue.int(123))
        assertTrue(result.isLeft)
      },
      test("IntToLong fails on non-Int") {
        val result = PrimitiveConversion.IntToLong(DynamicValue.string("not int"))
        assertTrue(result.isLeft)
      },
      test("LongToInt fails on non-Long") {
        val result = PrimitiveConversion.LongToInt(DynamicValue.string("not long"))
        assertTrue(result.isLeft)
      },
      test("LongToInt fails on overflow") {
        val result = PrimitiveConversion.LongToInt(DynamicValue.long(Long.MaxValue))
        assertTrue(result.isLeft)
      }
    ),
    suite("SchemaError message coverage")(
      test("ConversionFailed with no cause") {
        val err = SchemaError.ConversionFailed(DynamicOptic.root, "Test error")
        assertTrue(err.message == "Test error")
      },
      test("ConversionFailed with single cause error") {
        val innerErr = SchemaError.ExpectationMismatch(DynamicOptic.root, "inner error")
        val cause    = new SchemaError(::(innerErr, Nil))
        val err      = SchemaError.ConversionFailed(DynamicOptic.root, "Outer error", Some(cause))
        assertTrue(
          err.message.contains("Outer error"),
          err.message.contains("Caused by"),
          err.message.contains("inner error")
        )
      },
      test("ConversionFailed with multiple cause errors") {
        val err1  = SchemaError.ExpectationMismatch(DynamicOptic.root, "error 1")
        val err2  = SchemaError.ExpectationMismatch(DynamicOptic.root, "error 2")
        val cause = new SchemaError(::(err1, List(err2)))
        val err   = SchemaError.ConversionFailed(DynamicOptic.root, "Outer error", Some(cause))
        assertTrue(
          err.message.contains("Outer error"),
          err.message.contains("Caused by"),
          err.message.contains("error 1"),
          err.message.contains("error 2")
        )
      }
    ),
    suite("FieldAction.reverse methods")(
      test("Add.reverse returns Remove") {
        val add      = FieldAction.Add("field", DynamicValue.int(42))
        val reversed = add.reverse
        assertTrue(reversed == FieldAction.Remove("field", DynamicValue.int(42)))
      },
      test("Remove.reverse returns Add") {
        val remove   = FieldAction.Remove("field", DynamicValue.string("default"))
        val reversed = remove.reverse
        assertTrue(reversed == FieldAction.Add("field", DynamicValue.string("default")))
      },
      test("Rename.reverse swaps from and to") {
        val rename   = FieldAction.Rename("old", "new")
        val reversed = rename.reverse
        assertTrue(reversed == FieldAction.Rename("new", "old"))
      },
      test("Transform.reverse swaps forward and backward") {
        val transform = FieldAction.Transform(
          "field",
          DynamicValueTransform.stringAppend(" suffix"),
          DynamicValueTransform.stringPrepend("prefix ")
        )
        val reversed = transform.reverse
        reversed match {
          case FieldAction.Transform(name, fwd, bwd) =>
            assertTrue(
              name == "field",
              fwd == DynamicValueTransform.stringPrepend("prefix "),
              bwd == DynamicValueTransform.stringAppend(" suffix")
            )
          case _ => assertTrue(false)
        }
      },
      test("MakeOptional.reverse returns MakeRequired") {
        val makeOpt  = FieldAction.MakeOptional("field", DynamicValue.int(0))
        val reversed = makeOpt.reverse
        assertTrue(reversed == FieldAction.MakeRequired("field", DynamicValue.int(0)))
      },
      test("MakeRequired.reverse returns MakeOptional") {
        val makeReq  = FieldAction.MakeRequired("field", DynamicValue.string("default"))
        val reversed = makeReq.reverse
        assertTrue(reversed == FieldAction.MakeOptional("field", DynamicValue.string("default")))
      },
      test("ChangeType.reverse swaps forward and backward conversions") {
        val changeType = FieldAction.ChangeType(
          "field",
          PrimitiveConversion.IntToLong,
          PrimitiveConversion.LongToInt
        )
        val reversed = changeType.reverse
        assertTrue(
          reversed == FieldAction.ChangeType("field", PrimitiveConversion.LongToInt, PrimitiveConversion.IntToLong)
        )
      },
      test("JoinFields.reverse returns SplitField") {
        val join = FieldAction.JoinFields(
          "combined",
          Vector("a", "b"),
          DynamicValueTransform.identity,
          DynamicValueTransform.constant(DynamicValue.string("split"))
        )
        val reversed = join.reverse
        reversed match {
          case FieldAction.SplitField(sourceName, targetNames, splitter, combiner) =>
            assertTrue(
              sourceName == "combined",
              targetNames == Vector("a", "b"),
              splitter == DynamicValueTransform.constant(DynamicValue.string("split")),
              combiner == DynamicValueTransform.identity
            )
          case _ => assertTrue(false)
        }
      },
      test("SplitField.reverse returns JoinFields") {
        val split = FieldAction.SplitField(
          "source",
          Vector("x", "y"),
          DynamicValueTransform.identity,
          DynamicValueTransform.constant(DynamicValue.string("joined"))
        )
        val reversed = split.reverse
        reversed match {
          case FieldAction.JoinFields(targetName, sourceNames, combiner, splitter) =>
            assertTrue(
              targetName == "source",
              sourceNames == Vector("x", "y"),
              combiner == DynamicValueTransform.constant(DynamicValue.string("joined")),
              splitter == DynamicValueTransform.identity
            )
          case _ => assertTrue(false)
        }
      }
    ),
    suite("MigrationStep.isEmpty edge cases")(
      test("Sequence.isEmpty returns true when elementStep is NoOp") {
        val seq = MigrationStep.Sequence(MigrationStep.NoOp)
        assertTrue(seq.isEmpty)
      },
      test("Sequence.isEmpty returns false when elementStep has actions") {
        val seq = MigrationStep.Sequence(MigrationStep.Record.empty.addField("x", DynamicValue.int(1)))
        assertTrue(!seq.isEmpty)
      },
      test("MapEntries.isEmpty returns true when both key and value steps are NoOp") {
        val mapEntries = MigrationStep.MapEntries(MigrationStep.NoOp, MigrationStep.NoOp)
        assertTrue(mapEntries.isEmpty)
      },
      test("MapEntries.isEmpty returns false when keyStep has actions") {
        val mapEntries = MigrationStep.MapEntries(
          MigrationStep.Record.empty.addField("k", DynamicValue.int(1)),
          MigrationStep.NoOp
        )
        assertTrue(!mapEntries.isEmpty)
      },
      test("MapEntries.isEmpty returns false when valueStep has actions") {
        val mapEntries = MigrationStep.MapEntries(
          MigrationStep.NoOp,
          MigrationStep.Record.empty.addField("v", DynamicValue.int(1))
        )
        assertTrue(!mapEntries.isEmpty)
      },
      test("Record.isEmpty returns true with empty fieldActions and empty nestedFields") {
        val record = MigrationStep.Record.empty
        assertTrue(record.isEmpty)
      },
      test("Record.isEmpty returns true with empty fieldActions and all-empty nestedFields") {
        val record = MigrationStep.Record(Vector.empty, Map("nested" -> MigrationStep.NoOp))
        assertTrue(record.isEmpty)
      },
      test("Variant.isEmpty returns true with empty renames and all-empty nestedCases") {
        val variant = MigrationStep.Variant(Map.empty, Map("case" -> MigrationStep.NoOp))
        assertTrue(variant.isEmpty)
      }
    ),
    suite("MigrationError.message methods")(
      test("FieldNotFound.message includes field name and path") {
        val err = MigrationError.FieldNotFound(DynamicOptic.root.field("parent"), "missingField")
        val msg = err.message
        assertTrue(
          msg.contains("missingField"),
          msg.contains("parent")
        )
      },
      test("CaseNotFound.message includes case name and path") {
        val err = MigrationError.CaseNotFound(DynamicOptic.root, "UnknownCase")
        val msg = err.message
        assertTrue(msg.contains("UnknownCase"))
      },
      test("TypeMismatch.message includes expected and actual types") {
        val err = MigrationError.TypeMismatch(DynamicOptic.root, "Record", "Variant")
        val msg = err.message
        assertTrue(
          msg.contains("Record"),
          msg.contains("Variant"),
          msg.contains("expected")
        )
      },
      test("InvalidIndex.message includes index and size") {
        val err = MigrationError.InvalidIndex(DynamicOptic.root, 5, 3)
        val msg = err.message
        assertTrue(
          msg.contains("5"),
          msg.contains("3"),
          msg.contains("out of bounds")
        )
      },
      test("TransformFailed.message includes reason") {
        val err = MigrationError.TransformFailed(DynamicOptic.root, "conversion failed")
        val msg = err.message
        assertTrue(msg.contains("conversion failed"))
      },
      test("ExpressionEvalFailed.message includes reason") {
        val err = MigrationError.ExpressionEvalFailed(DynamicOptic.root, "eval error")
        val msg = err.message
        assertTrue(msg.contains("eval error"))
      },
      test("FieldAlreadyExists.message includes field name") {
        val err = MigrationError.FieldAlreadyExists(DynamicOptic.root, "duplicateField")
        val msg = err.message
        assertTrue(msg.contains("duplicateField"))
      },
      test("IncompatibleValue.message includes reason") {
        val err = MigrationError.IncompatibleValue(DynamicOptic.root, "wrong type")
        val msg = err.message
        assertTrue(msg.contains("wrong type"))
      },
      test("DuplicateMapKey.message includes path") {
        val err = MigrationError.DuplicateMapKey(DynamicOptic.root.field("mapField"))
        val msg = err.message
        assertTrue(msg.contains("duplicate"))
      }
    ),
    suite("Migration class methods")(
      test("Migration.applyDynamic applies migration to DynamicValue") {
        val migration = MigrationBuilder[PersonV1, PersonV2]
          .addField("age", DynamicValue.int(25))
          .buildPartial

        val dynamicInput = Schema[PersonV1].toDynamicValue(PersonV1("Alice"))
        val result       = migration.applyDynamic(dynamicInput)

        result match {
          case Right(dv) =>
            val fields = dv.asInstanceOf[DynamicValue.Record].fields.toVector.toMap
            assertTrue(
              fields.get("name") == Some(DynamicValue.string("Alice")),
              fields.get("age") == Some(DynamicValue.int(25))
            )
          case Left(_) => assertTrue(false)
        }
      },
      test("Migration.reverse returns reversed migration") {
        val migration = MigrationBuilder[PersonV1, PersonV2]
          .addField("age", DynamicValue.int(30))
          .buildPartial

        val reversed = migration.reverse

        assertTrue(
          reversed.sourceSchema == Schema[PersonV2],
          reversed.targetSchema == Schema[PersonV1]
        )

        val person2        = PersonV2("Bob", 30)
        val dynamicInput   = Schema[PersonV2].toDynamicValue(person2)
        val reversedDynMig = reversed.dynamicMigration
        val result         = reversedDynMig(dynamicInput)

        result match {
          case Right(dv) =>
            val fields = dv.asInstanceOf[DynamicValue.Record].fields.toVector.toMap
            assertTrue(
              fields.get("name") == Some(DynamicValue.string("Bob")),
              !fields.contains("age")
            )
          case Left(_) => assertTrue(false)
        }
      },
      test("Migration.andThen composes two migrations") {
        val m1 = MigrationBuilder[PersonV1, PersonV2]
          .addField("age", DynamicValue.int(25))
          .buildPartial

        val m2 = MigrationBuilder[PersonV2, PersonV3]
          .addField("active", DynamicValue.boolean(true))
          .buildPartial

        val composed = m1.andThen(m2)
        val result   = composed(PersonV1("Charlie"))

        assertTrue(result == Right(PersonV3("Charlie", 25, true)))
      }
    ),
    suite("MigrationBuilder nested path operations")(
      test("optionalizeField on nested path wraps field in Some") {
        val original = DynamicValue.Record(
          "outer" -> DynamicValue.Record(
            "inner" -> DynamicValue.int(42)
          )
        )
        val step = MigrationStep.Record.empty
          .nested("outer")(_.makeFieldOptional("inner", DynamicValue.Null))
        val migration = DynamicMigration(step)
        val result    = migration(original)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            val fieldMap   = fields.toVector.toMap
            val outer      = fieldMap("outer").asInstanceOf[DynamicValue.Record]
            val innerMap   = outer.fields.toVector.toMap
            val innerField = innerMap.get("inner")
            innerField match {
              case Some(DynamicValue.Variant("Some", _)) => assertTrue(true)
              case _                                     => assertTrue(false)
            }
          case _ => assertTrue(false)
        }
      },
      test("mandateField on nested path unwraps Some") {
        val original = DynamicValue.Record(
          "outer" -> DynamicValue.Record(
            "inner" -> DynamicValue.Variant("Some", DynamicValue.Record("value" -> DynamicValue.string("test")))
          )
        )
        val step = MigrationStep.Record.empty
          .nested("outer")(_.makeFieldRequired("inner", DynamicValue.string("default")))
        val migration = DynamicMigration(step)
        val result    = migration(original)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            val fieldMap   = fields.toVector.toMap
            val outer      = fieldMap("outer").asInstanceOf[DynamicValue.Record]
            val innerMap   = outer.fields.toVector.toMap
            val innerField = innerMap.get("inner")
            assertTrue(innerField == Some(DynamicValue.string("test")))
          case _ => assertTrue(false)
        }
      },
      test("transformCase applies nested record step to variant case") {
        val original = DynamicValue.Variant(
          "MyCase",
          DynamicValue.Record("value" -> DynamicValue.int(1))
        )
        val step = MigrationStep.Variant.empty
          .transformCase("MyCase")(_.addField("extra", DynamicValue.string("added")))
        val migration = DynamicMigration(step)
        val result    = migration(original)

        result match {
          case Right(DynamicValue.Variant(caseName, payload)) =>
            val fieldMap = payload.asInstanceOf[DynamicValue.Record].fields.toVector.toMap
            assertTrue(
              caseName == "MyCase",
              fieldMap.get("value") == Some(DynamicValue.int(1)),
              fieldMap.get("extra") == Some(DynamicValue.string("added"))
            )
          case _ => assertTrue(false)
        }
      },
      test("deeply nested transformElements") {
        val original = DynamicValue.Record(
          "level1" -> DynamicValue.Record(
            "level2" -> DynamicValue.Record(
              "items" -> DynamicValue.Sequence(
                DynamicValue.Record("name" -> DynamicValue.string("item1"))
              )
            )
          )
        )
        val step = MigrationStep.Record.empty
          .nested("level1") { l1 =>
            l1.nested("level2") { l2 =>
              l2.copy(nestedFields =
                l2.nestedFields + ("items" -> MigrationStep.Sequence(
                  MigrationStep.Record.empty.addField("count", DynamicValue.int(0))
                ))
              )
            }
          }
        val migration = DynamicMigration(step)
        val result    = migration(original)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            val fieldMap = fields.toVector.toMap
            val l1       = fieldMap("level1").asInstanceOf[DynamicValue.Record]
            val l1Map    = l1.fields.toVector.toMap
            val l2       = l1Map("level2").asInstanceOf[DynamicValue.Record]
            val l2Map    = l2.fields.toVector.toMap
            val items    = l2Map("items").asInstanceOf[DynamicValue.Sequence]
            val itemMap  = items.elements.head.asInstanceOf[DynamicValue.Record].fields.toVector.toMap
            assertTrue(
              itemMap.get("name") == Some(DynamicValue.string("item1")),
              itemMap.get("count") == Some(DynamicValue.int(0))
            )
          case _ => assertTrue(false)
        }
      },
      test("transformKeys on map field") {
        val original = DynamicValue.Record(
          "data" -> DynamicValue.Map(
            DynamicValue.Record("id" -> DynamicValue.string("k1")) -> DynamicValue.int(1)
          )
        )
        val step = MigrationStep.Record.empty
          .copy(nestedFields =
            Map(
              "data" -> MigrationStep.MapEntries(
                MigrationStep.Record.empty.renameField("id", "key"),
                MigrationStep.NoOp
              )
            )
          )
        val migration = DynamicMigration(step)
        val result    = migration(original)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            val fieldMap  = fields.toVector.toMap
            val dataMap   = fieldMap("data").asInstanceOf[DynamicValue.Map]
            val (key, _)  = dataMap.entries.head
            val keyFields = key.asInstanceOf[DynamicValue.Record].fields.toVector.toMap
            assertTrue(keyFields.contains("key") && !keyFields.contains("id"))
          case _ => assertTrue(false)
        }
      },
      test("transformValues on map field") {
        val original = DynamicValue.Record(
          "data" -> DynamicValue.Map(
            DynamicValue.string("k1") -> DynamicValue.Record("value" -> DynamicValue.int(1))
          )
        )
        val step = MigrationStep.Record.empty
          .copy(nestedFields =
            Map(
              "data" -> MigrationStep.MapEntries(
                MigrationStep.NoOp,
                MigrationStep.Record.empty.addField("extra", DynamicValue.string("added"))
              )
            )
          )
        val migration = DynamicMigration(step)
        val result    = migration(original)

        result match {
          case Right(DynamicValue.Record(fields)) =>
            val fieldMap    = fields.toVector.toMap
            val dataMap     = fieldMap("data").asInstanceOf[DynamicValue.Map]
            val (_, value)  = dataMap.entries.head
            val valueFields = value.asInstanceOf[DynamicValue.Record].fields.toVector.toMap
            assertTrue(
              valueFields.get("value") == Some(DynamicValue.int(1)),
              valueFields.get("extra") == Some(DynamicValue.string("added"))
            )
          case _ => assertTrue(false)
        }
      }
    ),
    suite("MigrationStep.reverse methods")(
      test("Record.reverse reverses fieldActions and updates nestedFields keys") {
        val record = MigrationStep.Record.empty
          .addField("a", DynamicValue.int(1))
          .renameField("x", "y")
          .nested("y")(_.addField("inner", DynamicValue.string("test")))

        val reversed = record.reverse.asInstanceOf[MigrationStep.Record]

        val renamedBack = reversed.fieldActions.exists {
          case FieldAction.Rename("y", "x") => true
          case _                            => false
        }
        assertTrue(
          renamedBack,
          reversed.nestedFields.contains("x")
        )
      },
      test("Variant.reverse reverses renames and updates nestedCases keys") {
        val variant = MigrationStep.Variant.empty
          .renameCase("CaseA", "CaseB")
          .nested("CaseB")(_.addField("field", DynamicValue.int(1)))

        val reversed = variant.reverse.asInstanceOf[MigrationStep.Variant]

        assertTrue(
          reversed.renames.get("CaseB") == Some("CaseA"),
          reversed.nestedCases.contains("CaseA")
        )
      },
      test("Sequence.reverse reverses the elementStep") {
        val seq = MigrationStep.Sequence(
          MigrationStep.Record.empty.addField("x", DynamicValue.int(1))
        )
        val reversed = seq.reverse.asInstanceOf[MigrationStep.Sequence]

        val innerReversed = reversed.elementStep.asInstanceOf[MigrationStep.Record]
        assertTrue(
          innerReversed.fieldActions.exists {
            case FieldAction.Remove("x", _) => true
            case _                          => false
          }
        )
      },
      test("MapEntries.reverse reverses both key and value steps") {
        val mapEntries = MigrationStep.MapEntries(
          MigrationStep.Record.empty.addField("k", DynamicValue.int(1)),
          MigrationStep.Record.empty.addField("v", DynamicValue.int(2))
        )
        val reversed = mapEntries.reverse.asInstanceOf[MigrationStep.MapEntries]

        val keyReversed   = reversed.keyStep.asInstanceOf[MigrationStep.Record]
        val valueReversed = reversed.valueStep.asInstanceOf[MigrationStep.Record]

        assertTrue(
          keyReversed.fieldActions.exists {
            case FieldAction.Remove("k", _) => true
            case _                          => false
          },
          valueReversed.fieldActions.exists {
            case FieldAction.Remove("v", _) => true
            case _                          => false
          }
        )
      },
      test("NoOp.reverse returns NoOp") {
        val noop     = MigrationStep.NoOp
        val reversed = noop.reverse
        assertTrue(reversed == MigrationStep.NoOp)
      }
    ),
    suite("MigrationBuilder field name extraction")(
      test("addedFieldNames extracts direct added fields") {
        val step = MigrationStep.Record.empty
          .addField("topLevel", DynamicValue.int(1))
          .joinFields("combined", Vector("a", "b"), DynamicValueTransform.identity, DynamicValueTransform.identity)
          .splitField("source", Vector("x", "y"), DynamicValueTransform.identity, DynamicValueTransform.identity)
        val builder = MigrationBuilder(Schema[Unit], Schema[Unit], step, MigrationStep.Variant.empty)

        val added = builder.addedFieldNames
        assertTrue(
          added.contains("topLevel"),
          added.contains("combined"),
          added.contains("x"),
          added.contains("y")
        )
      },
      test("addedFieldNames extracts nested added fields") {
        val step = MigrationStep.Record.empty
          .nested("outer")(_.addField("inner", DynamicValue.int(2)))
        val builder = MigrationBuilder(Schema[Unit], Schema[Unit], step, MigrationStep.Variant.empty)

        val added = builder.addedFieldNames
        assertTrue(added.contains("outer.inner"))
      },
      test("removedFieldNames extracts direct removed fields") {
        val step = MigrationStep.Record.empty
          .removeField("removed", DynamicValue.Null)
          .joinFields("combined", Vector("a", "b"), DynamicValueTransform.identity, DynamicValueTransform.identity)
          .splitField("source", Vector("x", "y"), DynamicValueTransform.identity, DynamicValueTransform.identity)
        val builder = MigrationBuilder(Schema[Unit], Schema[Unit], step, MigrationStep.Variant.empty)

        val removed = builder.removedFieldNames
        assertTrue(
          removed.contains("removed"),
          removed.contains("a"),
          removed.contains("b"),
          removed.contains("source")
        )
      },
      test("renamedFromNames and renamedToNames track renames") {
        val step = MigrationStep.Record.empty
          .renameField("oldName", "newName")
          .nested("parent")(_.renameField("oldInner", "newInner"))
        val builder = MigrationBuilder(Schema[Unit], Schema[Unit], step, MigrationStep.Variant.empty)

        assertTrue(
          builder.renamedFromNames.contains("oldName"),
          builder.renamedFromNames.contains("parent.oldInner"),
          builder.renamedToNames.contains("newName"),
          builder.renamedToNames.contains("parent.newInner")
        )
      }
    ),
    suite("MigrationBuilder error cases")(
      test("renameField with different parent paths via DynamicMigration") {
        val migration = DynamicMigration.record(_.renameField("field1", "field2"))
        val original  = DynamicValue.Record("field1" -> DynamicValue.int(1))
        val result    = migration(original)
        assertTrue(result == Right(DynamicValue.Record("field2" -> DynamicValue.int(1))))
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

case class ItemV1(name: String)
object ItemV1 {
  implicit val schema: Schema[ItemV1] = Schema.derived
}

case class ItemV2(name: String, count: Int)
object ItemV2 {
  implicit val schema: Schema[ItemV2] = Schema.derived
}

case class ContainerWithItemsV1(items: Seq[ItemV1])
object ContainerWithItemsV1 {
  implicit val schema: Schema[ContainerWithItemsV1] = Schema.derived
}

case class ContainerWithItemsV2(items: Seq[ItemV2])
object ContainerWithItemsV2 {
  implicit val schema: Schema[ContainerWithItemsV2] = Schema.derived
}

case class MapValueV1(value: String)
object MapValueV1 {
  implicit val schema: Schema[MapValueV1] = Schema.derived
}

case class MapValueV2(value: String, version: Int)
object MapValueV2 {
  implicit val schema: Schema[MapValueV2] = Schema.derived
}

case class ContainerWithMapV1(data: scala.collection.immutable.Map[String, MapValueV1])
object ContainerWithMapV1 {
  implicit val schema: Schema[ContainerWithMapV1] = Schema.derived
}

case class ContainerWithMapV2(data: scala.collection.immutable.Map[String, MapValueV2])
object ContainerWithMapV2 {
  implicit val schema: Schema[ContainerWithMapV2] = Schema.derived
}
