package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicSchemaExpr, DynamicValue, PrimitiveConverter, PrimitiveValue}
import zio.test._

object MigrationActionSpec extends ZIOSpecDefault {

  def spec = suite("MigrationActionSpec")(
    suite("AddField")(
      test("should add a field to a record with default value") {
        val record = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )
        )
        val action = MigrationAction.AddField(
          at = DynamicOptic.root.field("age"),
          default = 0
        )

        val result = action.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Chunk(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
              "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(0))
            )
          )
        )
      },
      test("should fail if field already exists") {
        val record = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(25))
          )
        )
        val action = MigrationAction.AddField(
          at = DynamicOptic.root.field("age"),
          default = 0
        )

        val result = action.execute(record)

        assertTrue(result.isLeft) &&
        assertTrue(result.swap.exists(_.isInstanceOf[MigrationError.FieldAlreadyExists]))
      }
    ),
    suite("AddField with DefaultValue")(
      test("should add a field using DynamicSchemaExpr.DefaultValue") {
        val record = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )
        )
        val action = MigrationAction.AddField(
          at = DynamicOptic.root.field("age"),
          default = DynamicSchemaExpr.DefaultValue(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        )

        val result = action.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Chunk(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
              "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(0))
            )
          )
        )
      },
      test("DropField reverse with DefaultValue should restore field") {
        val record = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(25))
          )
        )
        val drop = MigrationAction.DropField(
          at = DynamicOptic.root.field("age"),
          defaultForReverse = DynamicSchemaExpr.DefaultValue(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        )

        val dropped  = drop.execute(record)
        val restored = drop.reverse.execute(dropped.toOption.get)

        assertTrue(dropped.isRight) &&
        assertTrue(restored.isRight) &&
        assertTrue(
          restored.toOption.get == DynamicValue.Record(
            Chunk(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
              "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(0))
            )
          )
        )
      }
    ),
    suite("DropField")(
      test("should remove a field from a record") {
        val record = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(25))
          )
        )
        val action = MigrationAction.DropField(
          at = DynamicOptic.root.field("age"),
          defaultForReverse = 0
        )

        val result = action.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Chunk(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
            )
          )
        )
      },
      test("should fail if field does not exist") {
        val record = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )
        )
        val action = MigrationAction.DropField(
          at = DynamicOptic.root.field("age"),
          defaultForReverse = 0
        )

        val result = action.execute(record)

        assertTrue(result.isLeft) &&
        assertTrue(result.swap.exists(_.isInstanceOf[MigrationError.FieldNotFound]))
      }
    ),
    suite("Rename")(
      test("should rename a field in a record") {
        val record = DynamicValue.Record(
          Chunk(
            "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "age"       -> DynamicValue.Primitive(PrimitiveValue.Int(25))
          )
        )
        val action = MigrationAction.Rename(
          at = DynamicOptic.root.field("firstName"),
          to = "name"
        )

        val result = action.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Chunk(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
              "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(25))
            )
          )
        )
      },
      test("should fail if source field does not exist") {
        val record = DynamicValue.Record(
          Chunk(
            "age" -> DynamicValue.Primitive(PrimitiveValue.Int(25))
          )
        )
        val action = MigrationAction.Rename(
          at = DynamicOptic.root.field("firstName"),
          to = "name"
        )

        val result = action.execute(record)

        assertTrue(result.isLeft) &&
        assertTrue(result.swap.exists(_.isInstanceOf[MigrationError.FieldNotFound]))
      },
      test("should fail if target field already exists") {
        val record = DynamicValue.Record(
          Chunk(
            "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "name"      -> DynamicValue.Primitive(PrimitiveValue.String("Jane"))
          )
        )
        val action = MigrationAction.Rename(
          at = DynamicOptic.root.field("firstName"),
          to = "name"
        )

        val result = action.execute(record)

        assertTrue(result.isLeft) &&
        assertTrue(result.swap.exists(_.isInstanceOf[MigrationError.FieldAlreadyExists]))
      }
    ),
    suite("Reverse")(
      test("AddField.reverse should return DropField") {
        val addField = MigrationAction.AddField(
          at = DynamicOptic.root.field("age"),
          default = 0
        )

        val reversed = addField.reverse

        assertTrue(reversed.isInstanceOf[MigrationAction.DropField]) &&
        assertTrue(reversed.asInstanceOf[MigrationAction.DropField].at == addField.at)
      },
      test("DropField.reverse should return AddField") {
        val dropField = MigrationAction.DropField(
          at = DynamicOptic.root.field("age"),
          defaultForReverse = 0
        )

        val reversed = dropField.reverse

        assertTrue(reversed.isInstanceOf[MigrationAction.AddField]) &&
        assertTrue(reversed.asInstanceOf[MigrationAction.AddField].at == dropField.at)
      },
      test("Rename.reverse should flip to/from") {
        val rename = MigrationAction.Rename(
          at = DynamicOptic.root.field("firstName"),
          to = "name"
        )

        val reversed = rename.reverse

        assertTrue(reversed.isInstanceOf[MigrationAction.Rename]) &&
        assertTrue(reversed.asInstanceOf[MigrationAction.Rename].to == "firstName")
      }
    ),
    suite("TransformValue")(
      test("should transform a field value using literal replacement") {
        val record = DynamicValue.Record(
          Chunk(
            "age" -> DynamicValue.Primitive(PrimitiveValue.Int(25))
          )
        )
        val action = MigrationAction.TransformValue(
          at = DynamicOptic.root.field("age"),
          transform = 30
        )

        val result = action.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Chunk(
              "age" -> DynamicValue.Primitive(PrimitiveValue.Int(30))
            )
          )
        )
      },
      test("should transform a field value using type conversion") {
        val record = DynamicValue.Record(
          Chunk(
            "count" -> DynamicValue.Primitive(PrimitiveValue.Int(42))
          )
        )
        val action = MigrationAction.TransformValue(
          at = DynamicOptic.root.field("count"),
          transform = DynamicSchemaExpr.Convert(
            42,
            PrimitiveConverter.IntToLong
          )
        )

        val result = action.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Chunk(
              "count" -> DynamicValue.Primitive(PrimitiveValue.Long(42L))
            )
          )
        )
      },
      test("should fail if field does not exist") {
        val record = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )
        )
        val action = MigrationAction.TransformValue(
          at = DynamicOptic.root.field("age"),
          transform = 0
        )

        val result = action.execute(record)

        assertTrue(result.isLeft)
      }
    ),
    suite("ChangeType")(
      test("should convert string field to int") {
        val record = DynamicValue.Record(
          Chunk(
            "age" -> DynamicValue.Primitive(PrimitiveValue.String("25"))
          )
        )
        val action = MigrationAction.ChangeType(
          at = DynamicOptic.root.field("age"),
          converter = PrimitiveConverter.StringToInt
        )

        val result = action.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Chunk(
              "age" -> DynamicValue.Primitive(PrimitiveValue.Int(25))
            )
          )
        )
      },
      test("should convert int field to long") {
        val record = DynamicValue.Record(
          Chunk(
            "count" -> DynamicValue.Primitive(PrimitiveValue.Int(42))
          )
        )
        val action = MigrationAction.ChangeType(
          at = DynamicOptic.root.field("count"),
          converter = PrimitiveConverter.IntToLong
        )

        val result = action.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Chunk(
              "count" -> DynamicValue.Primitive(PrimitiveValue.Long(42L))
            )
          )
        )
      },
      test("should fail if conversion fails") {
        val record = DynamicValue.Record(
          Chunk(
            "age" -> DynamicValue.Primitive(PrimitiveValue.String("not-a-number"))
          )
        )
        val action = MigrationAction.ChangeType(
          at = DynamicOptic.root.field("age"),
          converter = PrimitiveConverter.StringToInt
        )

        val result = action.execute(record)

        assertTrue(result.isLeft) &&
        assertTrue(result.swap.exists(_.isInstanceOf[MigrationError.EvaluationError]))
      },
      test("should fail if field does not exist") {
        val record = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
          )
        )
        val action = MigrationAction.ChangeType(
          at = DynamicOptic.root.field("age"),
          converter = PrimitiveConverter.StringToInt
        )

        val result = action.execute(record)

        assertTrue(result.isLeft) &&
        assertTrue(result.swap.exists(_.isInstanceOf[MigrationError.FieldNotFound]))
      }
    ),
    suite("Mandate")(
      test("should unwrap Some value") {
        val optionValue = DynamicValue.Variant(
          "Some",
          DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(42))))
        )
        val record = DynamicValue.Record(Chunk("maybeAge" -> optionValue))
        val action = MigrationAction.Mandate(
          at = DynamicOptic.root.field("maybeAge"),
          default = 0
        )

        val result = action.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Chunk("maybeAge" -> DynamicValue.Primitive(PrimitiveValue.Int(42)))
          )
        )
      },
      test("should use default for None value") {
        val noneValue = DynamicValue.Variant("None", DynamicValue.Record(Chunk.empty))
        val record    = DynamicValue.Record(Chunk("maybeAge" -> noneValue))
        val action    = MigrationAction.Mandate(
          at = DynamicOptic.root.field("maybeAge"),
          default = 99
        )

        val result = action.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Chunk("maybeAge" -> DynamicValue.Primitive(PrimitiveValue.Int(99)))
          )
        )
      },
      test("should fail if value is not a Variant") {
        val record = DynamicValue.Record(
          Chunk("maybeAge" -> DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )
        val action = MigrationAction.Mandate(
          at = DynamicOptic.root.field("maybeAge"),
          default = 0
        )

        val result = action.execute(record)

        assertTrue(result.isLeft) &&
        assertTrue(result.swap.exists(_.isInstanceOf[MigrationError.InvalidStructure]))
      }
    ),
    suite("Optionalize")(
      test("should wrap value in Some") {
        val record = DynamicValue.Record(
          Chunk("age" -> DynamicValue.Primitive(PrimitiveValue.Int(25)))
        )
        val action = MigrationAction.Optionalize(
          at = DynamicOptic.root.field("age"),
          defaultForReverse = 0
        )

        val result = action.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Chunk(
              "age" -> DynamicValue.Variant(
                "Some",
                DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(25))))
              )
            )
          )
        )
      },
      test("Optionalize.reverse should return Mandate") {
        val optionalize = MigrationAction.Optionalize(
          at = DynamicOptic.root.field("age"),
          defaultForReverse = 0
        )

        val reversed = optionalize.reverse

        assertTrue(reversed.isInstanceOf[MigrationAction.Mandate]) &&
        assertTrue(reversed.asInstanceOf[MigrationAction.Mandate].at == optionalize.at)
      }
    ),
    suite("Join")(
      test("should combine two fields into one") {
        val record = DynamicValue.Record(
          Chunk(
            "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "lastName"  -> DynamicValue.Primitive(PrimitiveValue.String("Doe"))
          )
        )
        val combiner = DynamicSchemaExpr.StringConcat(
          DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field0")),
          DynamicSchemaExpr.StringConcat(
            " ",
            DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field1"))
          )
        )
        val action = MigrationAction.Join(
          at = DynamicOptic.root.field("fullName"),
          sourcePaths = Vector(
            DynamicOptic.root.field("firstName"),
            DynamicOptic.root.field("lastName")
          ),
          combiner = combiner
        )

        val result = action.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Chunk("fullName" -> DynamicValue.Primitive(PrimitiveValue.String("John Doe")))
          )
        )
      },
      test("should fail if source field does not exist") {
        val record = DynamicValue.Record(
          Chunk("firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John")))
        )
        val action = MigrationAction.Join(
          at = DynamicOptic.root.field("fullName"),
          sourcePaths = Vector(
            DynamicOptic.root.field("firstName"),
            DynamicOptic.root.field("lastName")
          ),
          combiner = ""
        )

        val result = action.execute(record)

        assertTrue(result.isLeft) &&
        assertTrue(result.swap.exists(_.isInstanceOf[MigrationError.FieldNotFound]))
      },
      test("should fail if target field already exists") {
        val record = DynamicValue.Record(
          Chunk(
            "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
            "lastName"  -> DynamicValue.Primitive(PrimitiveValue.String("Doe")),
            "fullName"  -> DynamicValue.Primitive(PrimitiveValue.String("Existing"))
          )
        )
        val action = MigrationAction.Join(
          at = DynamicOptic.root.field("fullName"),
          sourcePaths = Vector(
            DynamicOptic.root.field("firstName"),
            DynamicOptic.root.field("lastName")
          ),
          combiner = ""
        )

        val result = action.execute(record)

        assertTrue(result.isLeft) &&
        assertTrue(result.swap.exists(_.isInstanceOf[MigrationError.FieldAlreadyExists]))
      }
    ),
    suite("Split")(
      test("should split a field into multiple fields") {
        val record = DynamicValue.Record(
          Chunk("fullName" -> DynamicValue.Primitive(PrimitiveValue.String("John Doe")))
        )
        val action = MigrationAction.Split(
          at = DynamicOptic.root.field("fullName"),
          targetPaths = Vector(
            DynamicOptic.root.field("firstName"),
            DynamicOptic.root.field("lastName")
          ),
          splitter = DynamicSchemaExpr.StringSplit(
            DynamicSchemaExpr.Dynamic(DynamicOptic.root),
            " "
          )
        )

        val result = action.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Chunk(
              "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
              "lastName"  -> DynamicValue.Primitive(PrimitiveValue.String("Doe"))
            )
          )
        )
      },
      test("should fail if source field does not exist") {
        val record = DynamicValue.Record(Chunk.empty)
        val action = MigrationAction.Split(
          at = DynamicOptic.root.field("fullName"),
          targetPaths = Vector(
            DynamicOptic.root.field("firstName"),
            DynamicOptic.root.field("lastName")
          ),
          splitter = DynamicSchemaExpr.StringSplit(
            DynamicSchemaExpr.Dynamic(DynamicOptic.root),
            " "
          )
        )

        val result = action.execute(record)

        assertTrue(result.isLeft) &&
        assertTrue(result.swap.exists(_.isInstanceOf[MigrationError.FieldNotFound]))
      },
      test("should fail if split result count doesn't match target count") {
        val record = DynamicValue.Record(
          Chunk("fullName" -> DynamicValue.Primitive(PrimitiveValue.String("John"))) // no space, won't split
        )
        val action = MigrationAction.Split(
          at = DynamicOptic.root.field("fullName"),
          targetPaths = Vector(
            DynamicOptic.root.field("firstName"),
            DynamicOptic.root.field("lastName")
          ),
          splitter = DynamicSchemaExpr.StringSplit(
            DynamicSchemaExpr.Dynamic(DynamicOptic.root),
            " "
          )
        )

        val result = action.execute(record)

        assertTrue(result.isLeft) &&
        assertTrue(result.swap.exists(_.isInstanceOf[MigrationError.EvaluationError]))
      }
    ),
    suite("TransformElements")(
      test("should transform each element in a sequence") {
        val sequence = DynamicValue.Sequence(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.Int(2)),
            DynamicValue.Primitive(PrimitiveValue.Int(3))
          )
        )
        val record = DynamicValue.Record(Chunk("numbers" -> sequence))
        val action = MigrationAction.TransformElements(
          at = DynamicOptic.root.field("numbers"),
          transform = DynamicSchemaExpr.Arithmetic(
            DynamicSchemaExpr.Dynamic(DynamicOptic.root),
            10,
            DynamicSchemaExpr.ArithmeticOperator.Add,
            DynamicSchemaExpr.NumericType.IntType
          )
        )

        val result = action.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Chunk(
              "numbers" -> DynamicValue.Sequence(
                Chunk(
                  DynamicValue.Primitive(PrimitiveValue.Int(11)),
                  DynamicValue.Primitive(PrimitiveValue.Int(12)),
                  DynamicValue.Primitive(PrimitiveValue.Int(13))
                )
              )
            )
          )
        )
      },
      test("should handle empty sequence") {
        val record = DynamicValue.Record(
          Chunk("numbers" -> DynamicValue.Sequence(Chunk.empty))
        )
        val action = MigrationAction.TransformElements(
          at = DynamicOptic.root.field("numbers"),
          transform = 0
        )

        val result = action.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Chunk("numbers" -> DynamicValue.Sequence(Chunk.empty))
          )
        )
      },
      test("should fail if value is not a Sequence") {
        val record = DynamicValue.Record(
          Chunk("numbers" -> DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )
        val action = MigrationAction.TransformElements(
          at = DynamicOptic.root.field("numbers"),
          transform = 0
        )

        val result = action.execute(record)

        assertTrue(result.isLeft) &&
        assertTrue(result.swap.exists(_.isInstanceOf[MigrationError.InvalidStructure]))
      }
    ),
    suite("TransformKeys")(
      test("should transform each key in a map") {
        val map = DynamicValue.Map(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.String("a")) -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.String("b")) -> DynamicValue.Primitive(PrimitiveValue.Int(2))
          )
        )
        val record = DynamicValue.Record(Chunk("data" -> map))
        val action = MigrationAction.TransformKeys(
          at = DynamicOptic.root.field("data"),
          transform = DynamicSchemaExpr.StringUppercase(
            DynamicSchemaExpr.Dynamic(DynamicOptic.root)
          )
        )

        val result = action.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Chunk(
              "data" -> DynamicValue.Map(
                Chunk(
                  DynamicValue.Primitive(PrimitiveValue.String("A")) -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
                  DynamicValue.Primitive(PrimitiveValue.String("B")) -> DynamicValue.Primitive(PrimitiveValue.Int(2))
                )
              )
            )
          )
        )
      },
      test("should fail if value is not a Map") {
        val record = DynamicValue.Record(
          Chunk("data" -> DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )
        val action = MigrationAction.TransformKeys(
          at = DynamicOptic.root.field("data"),
          transform = "key"
        )

        val result = action.execute(record)

        assertTrue(result.isLeft) &&
        assertTrue(result.swap.exists(_.isInstanceOf[MigrationError.InvalidStructure]))
      }
    ),
    suite("TransformValues")(
      test("should transform each value in a map") {
        val map = DynamicValue.Map(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.String("x")) -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
            DynamicValue.Primitive(PrimitiveValue.String("y")) -> DynamicValue.Primitive(PrimitiveValue.Int(2))
          )
        )
        val record = DynamicValue.Record(Chunk("data" -> map))
        val action = MigrationAction.TransformValues(
          at = DynamicOptic.root.field("data"),
          transform = DynamicSchemaExpr.Arithmetic(
            DynamicSchemaExpr.Dynamic(DynamicOptic.root),
            100,
            DynamicSchemaExpr.ArithmeticOperator.Multiply,
            DynamicSchemaExpr.NumericType.IntType
          )
        )

        val result = action.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Chunk(
              "data" -> DynamicValue.Map(
                Chunk(
                  DynamicValue.Primitive(PrimitiveValue.String("x")) -> DynamicValue.Primitive(PrimitiveValue.Int(100)),
                  DynamicValue.Primitive(PrimitiveValue.String("y")) -> DynamicValue.Primitive(PrimitiveValue.Int(200))
                )
              )
            )
          )
        )
      },
      test("should fail if value is not a Map") {
        val record = DynamicValue.Record(
          Chunk("data" -> DynamicValue.Sequence(Chunk.empty))
        )
        val action = MigrationAction.TransformValues(
          at = DynamicOptic.root.field("data"),
          transform = 0
        )

        val result = action.execute(record)

        assertTrue(result.isLeft) &&
        assertTrue(result.swap.exists(_.isInstanceOf[MigrationError.InvalidStructure]))
      }
    ),
    suite("RenameCase")(
      test("should rename a variant case") {
        val variant = DynamicValue.Variant(
          "PayPal",
          DynamicValue.Record(Chunk("email" -> DynamicValue.Primitive(PrimitiveValue.String("test@example.com"))))
        )
        val record = DynamicValue.Record(Chunk("payment" -> variant))
        val action = MigrationAction.RenameCase(
          at = DynamicOptic.root.field("payment"),
          from = "PayPal",
          to = "PaypalPayment"
        )

        val result = action.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Chunk(
              "payment" -> DynamicValue.Variant(
                "PaypalPayment",
                DynamicValue.Record(Chunk("email" -> DynamicValue.Primitive(PrimitiveValue.String("test@example.com"))))
              )
            )
          )
        )
      },
      test("should leave non-matching case unchanged") {
        val variant = DynamicValue.Variant(
          "CreditCard",
          DynamicValue.Record(Chunk("number" -> DynamicValue.Primitive(PrimitiveValue.String("1234"))))
        )
        val record = DynamicValue.Record(Chunk("payment" -> variant))
        val action = MigrationAction.RenameCase(
          at = DynamicOptic.root.field("payment"),
          from = "PayPal",
          to = "PaypalPayment"
        )

        val result = action.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get == record)
      },
      test("should fail if value is not a Variant") {
        val record = DynamicValue.Record(
          Chunk("payment" -> DynamicValue.Primitive(PrimitiveValue.String("invalid")))
        )
        val action = MigrationAction.RenameCase(
          at = DynamicOptic.root.field("payment"),
          from = "PayPal",
          to = "PaypalPayment"
        )

        val result = action.execute(record)

        assertTrue(result.isLeft) &&
        assertTrue(result.swap.exists(_.isInstanceOf[MigrationError.InvalidStructure]))
      },
      test("RenameCase.reverse should flip from/to") {
        val renameCase = MigrationAction.RenameCase(
          at = DynamicOptic.root.field("payment"),
          from = "PayPal",
          to = "PaypalPayment"
        )

        val reversed = renameCase.reverse

        assertTrue(reversed.isInstanceOf[MigrationAction.RenameCase]) &&
        assertTrue(reversed.asInstanceOf[MigrationAction.RenameCase].from == "PaypalPayment") &&
        assertTrue(reversed.asInstanceOf[MigrationAction.RenameCase].to == "PayPal")
      }
    ),
    suite("TransformCase")(
      test("should transform matching case with nested actions") {
        val variant = DynamicValue.Variant(
          "CreditCard",
          DynamicValue.Record(
            Chunk(
              "number" -> DynamicValue.Primitive(PrimitiveValue.String("1234")),
              "expiry" -> DynamicValue.Primitive(PrimitiveValue.String("12/25"))
            )
          )
        )
        val record = DynamicValue.Record(Chunk("payment" -> variant))
        val action = MigrationAction.TransformCase(
          at = DynamicOptic.root.field("payment"),
          caseName = "CreditCard",
          actions = Vector(
            MigrationAction.AddField(
              at = DynamicOptic.root.field("cvv"),
              default = "000"
            )
          )
        )

        val result = action.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(
          result.toOption.get == DynamicValue.Record(
            Chunk(
              "payment" -> DynamicValue.Variant(
                "CreditCard",
                DynamicValue.Record(
                  Chunk(
                    "number" -> DynamicValue.Primitive(PrimitiveValue.String("1234")),
                    "expiry" -> DynamicValue.Primitive(PrimitiveValue.String("12/25")),
                    "cvv"    -> DynamicValue.Primitive(PrimitiveValue.String("000"))
                  )
                )
              )
            )
          )
        )
      },
      test("should leave non-matching case unchanged") {
        val variant = DynamicValue.Variant(
          "PayPal",
          DynamicValue.Record(Chunk("email" -> DynamicValue.Primitive(PrimitiveValue.String("test@example.com"))))
        )
        val record = DynamicValue.Record(Chunk("payment" -> variant))
        val action = MigrationAction.TransformCase(
          at = DynamicOptic.root.field("payment"),
          caseName = "CreditCard",
          actions = Vector(
            MigrationAction.AddField(
              at = DynamicOptic.root.field("cvv"),
              default = "000"
            )
          )
        )

        val result = action.execute(record)

        assertTrue(result.isRight) &&
        assertTrue(result.toOption.get == record)
      },
      test("should fail if value is not a Variant") {
        val record = DynamicValue.Record(
          Chunk("payment" -> DynamicValue.Primitive(PrimitiveValue.String("invalid")))
        )
        val action = MigrationAction.TransformCase(
          at = DynamicOptic.root.field("payment"),
          caseName = "CreditCard",
          actions = Vector.empty
        )

        val result = action.execute(record)

        assertTrue(result.isLeft) &&
        assertTrue(result.swap.exists(_.isInstanceOf[MigrationError.InvalidStructure]))
      }
    ),
    suite("Error paths - AddField")(
      test("should fail when default expression returns empty sequence") {
        val record = DynamicValue.Record(
          Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("John")))
        )
        // Dynamic optic pointing to a field that doesn't exist gives empty result
        val action = MigrationAction.AddField(
          at = DynamicOptic.root.field("age"),
          default = DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("missing"))
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      },
      test("should fail on non-record value") {
        val value  = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val action = MigrationAction.AddField(
          at = DynamicOptic.root.field("x"),
          default = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        )
        val result = action.execute(value)
        assertTrue(result.isLeft)
      }
    ),
    suite("Error paths - TransformValue")(
      test("should fail on empty path") {
        val record = DynamicValue.Record(Chunk("x" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val action = MigrationAction.TransformValue(
          at = DynamicOptic.root,
          transform = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      },
      test("should fail when transform expression returns empty") {
        val record = DynamicValue.Record(
          Chunk("x" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        // Use Dynamic pointing at a missing field to trigger eval error
        val action = MigrationAction.TransformValue(
          at = DynamicOptic.root.field("x"),
          transform = DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("nonexistent"))
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      }
    ),
    suite("Error paths - ChangeType")(
      test("should fail on empty path") {
        val record = DynamicValue.Record(Chunk("x" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val action = MigrationAction.ChangeType(
          at = DynamicOptic.root,
          converter = PrimitiveConverter.IntToString
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      }
    ),
    suite("Error paths - Mandate")(
      test("should fail on non-option value") {
        val record = DynamicValue.Record(
          Chunk("x" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        val action = MigrationAction.Mandate(
          at = DynamicOptic.root.field("x"),
          default = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      },
      test("should fail when Some contains non-record") {
        val record = DynamicValue.Record(
          Chunk("x" -> DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.Int(42))))
        )
        val action = MigrationAction.Mandate(
          at = DynamicOptic.root.field("x"),
          default = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      },
      test("should fail on unknown variant case") {
        val record = DynamicValue.Record(
          Chunk(
            "x" -> DynamicValue.Variant("Unknown", DynamicValue.Primitive(PrimitiveValue.Unit))
          )
        )
        val action = MigrationAction.Mandate(
          at = DynamicOptic.root.field("x"),
          default = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      },
      test("should fail when None default expression fails") {
        val record = DynamicValue.Record(
          Chunk("x" -> DynamicValue.Variant("None", DynamicValue.Primitive(PrimitiveValue.Unit)))
        )
        val action = MigrationAction.Mandate(
          at = DynamicOptic.root.field("x"),
          default = DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("missing"))
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      }
    ),
    suite("Error paths - Join")(
      test("should fail when source path is empty") {
        val record = DynamicValue.Record(
          Chunk(
            "a" -> DynamicValue.Primitive(PrimitiveValue.String("hello")),
            "b" -> DynamicValue.Primitive(PrimitiveValue.String("world"))
          )
        )
        val action = MigrationAction.Join(
          at = DynamicOptic.root.field("c"),
          sourcePaths = Vector(DynamicOptic.root),
          combiner = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("x")))
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      },
      test("should fail when combiner expression fails") {
        val record = DynamicValue.Record(
          Chunk(
            "a" -> DynamicValue.Primitive(PrimitiveValue.String("hello")),
            "b" -> DynamicValue.Primitive(PrimitiveValue.String("world"))
          )
        )
        val action = MigrationAction.Join(
          at = DynamicOptic.root.field("c"),
          sourcePaths = Vector(DynamicOptic.root.field("a"), DynamicOptic.root.field("b")),
          combiner = DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("missing"))
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      },
      test("should fail when target field already exists") {
        val record = DynamicValue.Record(
          Chunk(
            "a"    -> DynamicValue.Primitive(PrimitiveValue.String("hello")),
            "b"    -> DynamicValue.Primitive(PrimitiveValue.String("world")),
            "full" -> DynamicValue.Primitive(PrimitiveValue.String("exists"))
          )
        )
        val action = MigrationAction.Join(
          at = DynamicOptic.root.field("full"),
          sourcePaths = Vector(DynamicOptic.root.field("a"), DynamicOptic.root.field("b")),
          combiner = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("x")))
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      },
      test("reverse should return Irreversible for non-delimiter Join") {
        val action = MigrationAction.Join(
          at = DynamicOptic.root.field("c"),
          sourcePaths = Vector(DynamicOptic.root.field("a")),
          combiner = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.Irreversible])
      },
      test("reverse should return Irreversible when delimiter is not a String literal") {
        val action = MigrationAction.Join(
          at = DynamicOptic.root.field("c"),
          sourcePaths = Vector(DynamicOptic.root.field("a"), DynamicOptic.root.field("b")),
          combiner = DynamicSchemaExpr.StringConcat(
            DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field0")),
            DynamicSchemaExpr.StringConcat(
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))),
              DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field1"))
            )
          )
        )
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.Irreversible])
      }
    ),
    suite("Error paths - Split")(
      test("should fail when target field already exists") {
        val record = DynamicValue.Record(
          Chunk(
            "full"  -> DynamicValue.Primitive(PrimitiveValue.String("John Doe")),
            "first" -> DynamicValue.Primitive(PrimitiveValue.String("exists"))
          )
        )
        val action = MigrationAction.Split(
          at = DynamicOptic.root.field("full"),
          targetPaths = Vector(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
          splitter = DynamicSchemaExpr.StringSplit(DynamicSchemaExpr.Dynamic(DynamicOptic.root), " ")
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      },
      test("should fail when target path is empty") {
        val record = DynamicValue.Record(
          Chunk("full" -> DynamicValue.Primitive(PrimitiveValue.String("John Doe")))
        )
        val action = MigrationAction.Split(
          at = DynamicOptic.root.field("full"),
          targetPaths = Vector(DynamicOptic.root),
          splitter = DynamicSchemaExpr.StringSplit(DynamicSchemaExpr.Dynamic(DynamicOptic.root), " ")
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      }
    ),
    suite("Error paths - TransformElements")(
      test("should fail on non-sequence") {
        val record = DynamicValue.Record(
          Chunk("items" -> DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )
        val action = MigrationAction.TransformElements(
          at = DynamicOptic.root.field("items"),
          transform = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      },
      test("should fail when transform expression fails on element") {
        val record = DynamicValue.Record(
          Chunk(
            "items" -> DynamicValue.Sequence(
              Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1)))
            )
          )
        )
        val action = MigrationAction.TransformElements(
          at = DynamicOptic.root.field("items"),
          transform = DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("nonexistent"))
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      }
    ),
    suite("Error paths - TransformKeys")(
      test("should fail on non-map") {
        val record = DynamicValue.Record(
          Chunk("data" -> DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )
        val action = MigrationAction.TransformKeys(
          at = DynamicOptic.root.field("data"),
          transform = DynamicSchemaExpr.StringUppercase(DynamicSchemaExpr.Dynamic(DynamicOptic.root))
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      },
      test("should fail when transform expression fails on key") {
        val record = DynamicValue.Record(
          Chunk(
            "data" -> DynamicValue.Map(
              Chunk(
                DynamicValue.Primitive(PrimitiveValue.Int(1)) -> DynamicValue.Primitive(PrimitiveValue.String("v"))
              )
            )
          )
        )
        val action = MigrationAction.TransformKeys(
          at = DynamicOptic.root.field("data"),
          transform = DynamicSchemaExpr.StringUppercase(DynamicSchemaExpr.Dynamic(DynamicOptic.root))
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      }
    ),
    suite("Error paths - TransformValues")(
      test("should fail on non-map") {
        val record = DynamicValue.Record(
          Chunk("data" -> DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )
        val action = MigrationAction.TransformValues(
          at = DynamicOptic.root.field("data"),
          transform = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      },
      test("should fail when transform expression fails on value") {
        val record = DynamicValue.Record(
          Chunk(
            "data" -> DynamicValue.Map(
              Chunk(
                DynamicValue.Primitive(PrimitiveValue.String("k")) -> DynamicValue.Primitive(PrimitiveValue.Int(1))
              )
            )
          )
        )
        val action = MigrationAction.TransformValues(
          at = DynamicOptic.root.field("data"),
          transform = DynamicSchemaExpr.StringUppercase(DynamicSchemaExpr.Dynamic(DynamicOptic.root))
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      }
    ),
    suite("NavigationHelpers edge cases")(
      test("modifyRecordAt should fail with non-Field node at path end") {
        val record = DynamicValue.Record(
          Chunk("items" -> DynamicValue.Sequence(Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1)))))
        )
        // Use AddField with a path ending in elements (non-Field node)
        val action = MigrationAction.AddField(
          at = new DynamicOptic(Vector(DynamicOptic.Node.Field("items"), DynamicOptic.Node.Elements)),
          default = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      },
      test("navigateToField should fail on intermediate non-record") {
        val record = DynamicValue.Record(
          Chunk("x" -> DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )
        val action = MigrationAction.TransformValue(
          at = DynamicOptic.root.field("x").field("y"),
          transform = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      },
      test("navigateToField should fail on empty path") {
        val record = DynamicValue.Record(Chunk("x" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        // Use DropField at empty path to exercise navigateToField empty check
        val action = MigrationAction.DropField(
          at = DynamicOptic.root,
          defaultForReverse = 0
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      },
      test("navigateToField should fail with non-Field nodes in path") {
        val record = DynamicValue.Record(
          Chunk("items" -> DynamicValue.Sequence(Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1)))))
        )
        val nonFieldPath = new DynamicOptic(Vector(DynamicOptic.Node.Elements, DynamicOptic.Node.Field("x")))
        val action       = MigrationAction.Rename(at = nonFieldPath, to = "y")
        val result       = action.execute(record)
        assertTrue(result.isLeft)
      },
      test("updateNestedField should fail on empty path") {
        // AddField with empty path exercises modifyRecordAt empty path check
        val record = DynamicValue.Record(Chunk("x" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val action = MigrationAction.AddField(at = DynamicOptic.root, default = 0)
        val result = action.execute(record)
        assertTrue(result.isLeft)
      },
      test("updateNestedField should fail with non-Field nodes in path") {
        val record = DynamicValue.Record(
          Chunk("items" -> DynamicValue.Sequence(Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1)))))
        )
        val nonFieldPath = new DynamicOptic(Vector(DynamicOptic.Node.Field("items"), DynamicOptic.Node.Elements))
        val action       = MigrationAction.Rename(at = nonFieldPath, to = "y")
        val result       = action.execute(record)
        assertTrue(result.isLeft)
      },
      test("navigateToField should fail when intermediate field not found") {
        val record = DynamicValue.Record(
          Chunk("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        // Path a.b.c but 'b' doesn't exist inside 'a' (not a record)
        val action = MigrationAction.TransformValue(
          at = DynamicOptic.root.field("missing").field("child"),
          transform = 0
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      },
      test("navigateToField should fail when parent value is not a Record at target depth") {
        val record = DynamicValue.Record(
          Chunk("x" -> DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )
        // Single-level path that targets a primitive directly via modifyRecordAt
        val action = MigrationAction.DropField(
          at = DynamicOptic.root.field("nonexistent"),
          defaultForReverse = 0
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      },
      test("updateNestedField should fail when target field not found at depth") {
        val nested = DynamicValue.Record(
          Chunk("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
        )
        val record = DynamicValue.Record(Chunk("outer" -> nested))
        val action = MigrationAction.TransformValue(
          at = DynamicOptic.root.field("outer").field("missing"),
          transform = 0
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      },
      test("updateNestedField should fail when value at depth is not a Record") {
        val record = DynamicValue.Record(
          Chunk(
            "outer" -> DynamicValue.Record(
              Chunk("inner" -> DynamicValue.Primitive(PrimitiveValue.Int(42)))
            )
          )
        )
        // Path goes through 'inner' which is a primitive, not a record
        val action = MigrationAction.TransformValue(
          at = DynamicOptic.root.field("outer").field("inner").field("deep"),
          transform = 0
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      },
      test("updateNestedField deep recursive Left propagation") {
        // 3-level deep path where the innermost field doesn't exist
        val deep   = DynamicValue.Record(Chunk("z" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val middle = DynamicValue.Record(Chunk("b" -> deep))
        val record = DynamicValue.Record(Chunk("a" -> middle))
        val action = MigrationAction.TransformValue(
          at = DynamicOptic.root.field("a").field("b").field("missing"),
          transform = 0
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      }
    ),
    suite("Action-specific edge cases")(
      test("Mandate: Some without 'value' field should fail") {
        val record = DynamicValue.Record(
          Chunk(
            "x" -> DynamicValue.Variant(
              "Some",
              DynamicValue.Record(Chunk("notvalue" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
            )
          )
        )
        val action = MigrationAction.Mandate(at = DynamicOptic.root.field("x"), default = 0)
        val result = action.execute(record)
        assertTrue(result.isLeft)
      },
      test("Mandate: None with empty default expression should fail") {
        val record = DynamicValue.Record(
          Chunk("x" -> DynamicValue.Variant("None", DynamicValue.Record(Chunk.empty)))
        )
        // Use Dynamic pointing to missing field to produce empty result
        val action = MigrationAction.Mandate(
          at = DynamicOptic.root.field("x"),
          default = DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("nonexistent"))
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      },
      test("Join: source path with non-Field last node should fail") {
        val record = DynamicValue.Record(
          Chunk("a" -> DynamicValue.Primitive(PrimitiveValue.String("hello")))
        )
        val action = MigrationAction.Join(
          at = DynamicOptic.root.field("c"),
          sourcePaths = Vector(
            new DynamicOptic(Vector(DynamicOptic.Node.Elements))
          ),
          combiner = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("x")))
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      },
      test("Join: combiner returns empty sequence should fail") {
        val record = DynamicValue.Record(
          Chunk(
            "a" -> DynamicValue.Primitive(PrimitiveValue.String("hello")),
            "b" -> DynamicValue.Primitive(PrimitiveValue.String("world"))
          )
        )
        val action = MigrationAction.Join(
          at = DynamicOptic.root.field("c"),
          sourcePaths = Vector(DynamicOptic.root.field("a"), DynamicOptic.root.field("b")),
          combiner = DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("nonexistent"))
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      },
      test("Split: target path with non-Field last node should fail") {
        val record = DynamicValue.Record(
          Chunk("full" -> DynamicValue.Primitive(PrimitiveValue.String("John Doe")))
        )
        val action = MigrationAction.Split(
          at = DynamicOptic.root.field("full"),
          targetPaths = Vector(
            DynamicOptic.root.field("first"),
            new DynamicOptic(Vector(DynamicOptic.Node.Elements))
          ),
          splitter = DynamicSchemaExpr.StringSplit(DynamicSchemaExpr.Dynamic(DynamicOptic.root), " ")
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      },
      test("TransformElements: transform returns empty sequence should fail") {
        val record = DynamicValue.Record(
          Chunk(
            "items" -> DynamicValue.Sequence(
              Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1)))
            )
          )
        )
        // Use StringUppercase on an Int to trigger eval returning Left, not empty
        // Instead, use a Dynamic that returns empty
        val action = MigrationAction.TransformElements(
          at = DynamicOptic.root.field("items"),
          transform = DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("nonexistent"))
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      },
      test("TransformKeys: transform returns empty sequence should fail") {
        val record = DynamicValue.Record(
          Chunk(
            "data" -> DynamicValue.Map(
              Chunk(
                DynamicValue.Primitive(PrimitiveValue.String("k")) -> DynamicValue.Primitive(PrimitiveValue.Int(1))
              )
            )
          )
        )
        val action = MigrationAction.TransformKeys(
          at = DynamicOptic.root.field("data"),
          transform = DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("nonexistent"))
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      },
      test("TransformValues: transform returns empty sequence should fail") {
        val record = DynamicValue.Record(
          Chunk(
            "data" -> DynamicValue.Map(
              Chunk(
                DynamicValue.Primitive(PrimitiveValue.String("k")) -> DynamicValue.Primitive(PrimitiveValue.Int(1))
              )
            )
          )
        )
        val action = MigrationAction.TransformValues(
          at = DynamicOptic.root.field("data"),
          transform = DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("nonexistent"))
        )
        val result = action.execute(record)
        assertTrue(result.isLeft)
      },
      test("Irreversible.reverse returns self") {
        val action   = MigrationAction.Irreversible(DynamicOptic.root.field("x"), "test reason")
        val reversed = action.reverse
        assertTrue(reversed eq action)
      },
      test("TransformValue.reverse with non-invertible expression returns Irreversible") {
        val action = MigrationAction.TransformValue(
          at = DynamicOptic.root.field("x"),
          transform = DynamicSchemaExpr.StringConcat(
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("a"))),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("b")))
          )
        )
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.Irreversible])
      },
      test("TransformElements.reverse with non-invertible expression returns Irreversible") {
        val action = MigrationAction.TransformElements(
          at = DynamicOptic.root.field("items"),
          transform = DynamicSchemaExpr.StringConcat(
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("a"))),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("b")))
          )
        )
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.Irreversible])
      },
      test("TransformKeys.reverse with non-invertible expression returns Irreversible") {
        val action = MigrationAction.TransformKeys(
          at = DynamicOptic.root.field("data"),
          transform = DynamicSchemaExpr.StringConcat(
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("a"))),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("b")))
          )
        )
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.Irreversible])
      },
      test("TransformValues.reverse with non-invertible expression returns Irreversible") {
        val action = MigrationAction.TransformValues(
          at = DynamicOptic.root.field("data"),
          transform = DynamicSchemaExpr.StringConcat(
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("a"))),
            DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("b")))
          )
        )
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.Irreversible])
      },
      test("Split.reverse with non-StringSplit splitter returns Irreversible") {
        val action = MigrationAction.Split(
          at = DynamicOptic.root.field("full"),
          targetPaths = Vector(DynamicOptic.root.field("a"), DynamicOptic.root.field("b")),
          splitter = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("x")))
        )
        val reversed = action.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.Irreversible])
      },
      test("Join: nested path with invalid sibling constraint should fail") {
        val nested = DynamicValue.Record(
          Chunk(
            "inner" -> DynamicValue.Record(
              Chunk(
                "a" -> DynamicValue.Primitive(PrimitiveValue.String("hello")),
                "b" -> DynamicValue.Primitive(PrimitiveValue.String("world"))
              )
            )
          )
        )
        val action = MigrationAction.Join(
          at = DynamicOptic.root.field("inner").field("c"),
          sourcePaths = Vector(
            DynamicOptic.root.field("other").field("a") // different parent
          ),
          combiner = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("x")))
        )
        val result = action.execute(nested)
        assertTrue(result.isLeft)
      },
      test("Split: nested path with invalid sibling constraint should fail") {
        val nested = DynamicValue.Record(
          Chunk(
            "inner" -> DynamicValue.Record(
              Chunk("full" -> DynamicValue.Primitive(PrimitiveValue.String("John Doe")))
            )
          )
        )
        val action = MigrationAction.Split(
          at = DynamicOptic.root.field("inner").field("full"),
          targetPaths = Vector(
            DynamicOptic.root.field("other").field("a") // different parent
          ),
          splitter = DynamicSchemaExpr.StringSplit(DynamicSchemaExpr.Dynamic(DynamicOptic.root), " ")
        )
        val result = action.execute(nested)
        assertTrue(result.isLeft)
      },
      test("Irreversible.execute should return Left") {
        val action = MigrationAction.Irreversible(DynamicOptic.root.field("x"), "Cannot reverse")
        val record = DynamicValue.Record(Chunk("x" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val result = action.execute(record)
        assertTrue(result.isLeft)
      },
      test("Rename.reverse with nested path") {
        val rename = MigrationAction.Rename(
          at = DynamicOptic.root.field("address").field("firstName"),
          to = "name"
        )
        val reversed = rename.reverse
        assertTrue(reversed.isInstanceOf[MigrationAction.Rename])
      }
    )
  )
}
