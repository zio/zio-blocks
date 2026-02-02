package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicValue, DynamicOptic, IsNumeric, PrimitiveConverter, PrimitiveValue, Schema, SchemaExpr}
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
          default = SchemaExpr.Literal(0, Schema.int)
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
          default = SchemaExpr.Literal(0, Schema.int)
        )

        val result = action.execute(record)

        assertTrue(result.isLeft) &&
        assertTrue(result.swap.exists(_.isInstanceOf[MigrationError.FieldAlreadyExists]))
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
          defaultForReverse = SchemaExpr.Literal(0, Schema.int)
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
          defaultForReverse = SchemaExpr.Literal(0, Schema.int)
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
          default = SchemaExpr.Literal(0, Schema.int)
        )

        val reversed = addField.reverse

        assertTrue(reversed.isInstanceOf[MigrationAction.DropField]) &&
        assertTrue(reversed.asInstanceOf[MigrationAction.DropField].at == addField.at)
      },
      test("DropField.reverse should return AddField") {
        val dropField = MigrationAction.DropField(
          at = DynamicOptic.root.field("age"),
          defaultForReverse = SchemaExpr.Literal(0, Schema.int)
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
          transform = SchemaExpr.Literal[DynamicValue, Int](30, Schema.int)
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
          transform = SchemaExpr.Convert[DynamicValue, Long](
            SchemaExpr.Literal[DynamicValue, Int](42, Schema.int),
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
          transform = SchemaExpr.Literal[DynamicValue, Int](0, Schema.int)
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
          default = SchemaExpr.Literal(0, Schema.int)
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
          default = SchemaExpr.Literal(99, Schema.int)
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
          default = SchemaExpr.Literal(0, Schema.int)
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
          defaultForReverse = SchemaExpr.Literal(0, Schema.int)
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
          defaultForReverse = SchemaExpr.Literal(0, Schema.int)
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
        val combiner = SchemaExpr.StringConcat(
          SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root.field("field0")),
          SchemaExpr.StringConcat(
            SchemaExpr.Literal(" ", Schema.string),
            SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root.field("field1"))
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
          combiner = SchemaExpr.Literal("", Schema.string)
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
          combiner = SchemaExpr.Literal("", Schema.string)
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
          splitter = SchemaExpr.StringSplit(
            SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root),
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
          splitter = SchemaExpr.StringSplit(
            SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root),
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
          splitter = SchemaExpr.StringSplit(
            SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root),
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
          transform = SchemaExpr.Arithmetic(
            SchemaExpr.Dynamic[DynamicValue, Int](DynamicOptic.root),
            SchemaExpr.Literal(10, Schema.int),
            SchemaExpr.ArithmeticOperator.Add,
            IsNumeric.IsInt
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
          transform = SchemaExpr.Literal(0, Schema.int)
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
          transform = SchemaExpr.Literal(0, Schema.int)
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
          transform = SchemaExpr.StringUppercase(
            SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root)
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
          transform = SchemaExpr.Literal("key", Schema.string)
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
          transform = SchemaExpr.Arithmetic(
            SchemaExpr.Dynamic[DynamicValue, Int](DynamicOptic.root),
            SchemaExpr.Literal(100, Schema.int),
            SchemaExpr.ArithmeticOperator.Multiply,
            IsNumeric.IsInt
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
          transform = SchemaExpr.Literal(0, Schema.int)
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
              default = SchemaExpr.Literal("000", Schema.string)
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
              default = SchemaExpr.Literal("000", Schema.string)
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
    )
  )
}
