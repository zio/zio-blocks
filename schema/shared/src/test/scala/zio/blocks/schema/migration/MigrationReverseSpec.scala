package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test._

/**
 * Comprehensive test suite for migration reverse operations.
 *   - Lossless: Lossless bidirectional transformation
 *   - Lossy: Reverses but may lose information (still useful!)
 *   - Impossible: Cannot reverse, returns identity
 */
object MigrationReverseSpec extends ZIOSpecDefault {

  def spec = suite("MigrationReverseSpec")(
    suite("Lossless Bidirectional Transformations")(
      suite("AddField / DropField")(
        test("AddField → DropField → round-trip preserves original") {
          val original = DynamicValue.Record(
            Chunk(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
            )
          )

          val addAction = MigrationAction.AddField(
            at = DynamicOptic.root.field("age"),
            default = 30
          )
          val dropAction = addAction.reverse

          val forward      = addAction.execute(original)
          val backward     = forward.flatMap(dropAction.execute)
          val roundTripped = backward.toOption.get

          assertTrue(roundTripped == original)
        },
        test("DropField → AddField → round-trip preserves original") {
          val original = DynamicValue.Record(
            Chunk(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
              "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(25))
            )
          )

          val dropAction = MigrationAction.DropField(
            at = DynamicOptic.root.field("age"),
            defaultForReverse = 25
          )
          val addAction = dropAction.reverse

          val forward      = dropAction.execute(original)
          val backward     = forward.flatMap(addAction.execute)
          val roundTripped = backward.toOption.get

          assertTrue(roundTripped == original)
        }
      ),
      suite("Rename")(
        test("Rename → Rename → round-trip preserves original") {
          val original = DynamicValue.Record(
            Chunk(
              "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
            )
          )

          val renameAction = MigrationAction.Rename(
            at = DynamicOptic.root.field("firstName"),
            to = "name"
          )
          val reverseAction = renameAction.reverse

          val forward      = renameAction.execute(original)
          val backward     = forward.flatMap(reverseAction.execute)
          val roundTripped = backward.toOption.get

          assertTrue(roundTripped == original)
        }
      ),
      suite("ChangeType")(
        test("Int → String → Int round-trip preserves value") {
          val original = DynamicValue.Record(
            Chunk(
              "age" -> DynamicValue.Primitive(PrimitiveValue.Int(25))
            )
          )

          val changeAction = MigrationAction.ChangeType(
            at = DynamicOptic.root.field("age"),
            converter = PrimitiveConverter.IntToString
          )
          val reverseAction = changeAction.reverse

          val forward      = changeAction.execute(original)
          val backward     = forward.flatMap(reverseAction.execute)
          val roundTripped = backward.toOption.get

          assertTrue(roundTripped == original)
        }
      ),
      suite("Mandate / Optionalize")(
        test("Mandate(Some) → Optionalize → round-trip preserves original") {
          val original = DynamicValue.Record(
            Chunk(
              "age" -> DynamicValue.Variant(
                "Some",
                DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(25))))
              )
            )
          )

          val mandateAction = MigrationAction.Mandate(
            at = DynamicOptic.root.field("age"),
            default = 0
          )
          val optionalizeAction = mandateAction.reverse

          val forward      = mandateAction.execute(original)
          val backward     = forward.flatMap(optionalizeAction.execute)
          val roundTripped = backward.toOption.get

          assertTrue(roundTripped == original)
        },
        test("Optionalize → Mandate → round-trip preserves original") {
          val original = DynamicValue.Record(
            Chunk(
              "age" -> DynamicValue.Primitive(PrimitiveValue.Int(25))
            )
          )

          val optionalizeAction = MigrationAction.Optionalize(
            at = DynamicOptic.root.field("age"),
            defaultForReverse = 0
          )
          val mandateAction = optionalizeAction.reverse

          val forward      = optionalizeAction.execute(original)
          val backward     = forward.flatMap(mandateAction.execute)
          val roundTripped = backward.toOption.get

          assertTrue(roundTripped == original)
        }
      ),
      suite("RenameCase")(
        test("RenameCase → RenameCase → round-trip preserves original") {
          val original = DynamicValue.Variant(
            "PayPal",
            DynamicValue.Record(Chunk("email" -> DynamicValue.Primitive(PrimitiveValue.String("test@example.com"))))
          )

          val renameAction = MigrationAction.RenameCase(
            at = DynamicOptic.root,
            from = "PayPal",
            to = "PaypalPayment"
          )
          val reverseAction = renameAction.reverse

          val forward      = renameAction.execute(original)
          val backward     = forward.flatMap(reverseAction.execute)
          val roundTripped = backward.toOption.get

          assertTrue(roundTripped == original)
        }
      ),
      suite("TransformCase")(
        test("TransformCase → reverse → round-trip preserves original") {
          val original = DynamicValue.Variant(
            "CreditCard",
            DynamicValue.Record(
              Chunk(
                "number" -> DynamicValue.Primitive(PrimitiveValue.String("1234"))
              )
            )
          )

          val transformAction = MigrationAction.TransformCase(
            at = DynamicOptic.root,
            caseName = "CreditCard",
            actions = Vector(
              MigrationAction.AddField(
                at = DynamicOptic.root.field("cvv"),
                default = "000"
              )
            )
          )
          val reverseAction = transformAction.reverse

          val forward      = transformAction.execute(original)
          val backward     = forward.flatMap(reverseAction.execute)
          val roundTripped = backward.toOption.get

          assertTrue(roundTripped == original)
        }
      )
    ),
    suite("Lossy Reversals")(
      suite("TransformValue - Add/Subtract")(
        test("Add → Subtract → round-trip preserves original") {
          val original = DynamicValue.Record(
            Chunk(
              "price" -> DynamicValue.Primitive(PrimitiveValue.Int(100))
            )
          )

          val addAction = MigrationAction.TransformValue(
            at = DynamicOptic.root.field("price"),
            transform = DynamicSchemaExpr.Arithmetic(
              DynamicSchemaExpr.Dynamic(DynamicOptic.root),
              50,
              DynamicSchemaExpr.ArithmeticOperator.Add,
              DynamicSchemaExpr.NumericType.IntType
            )
          )
          val subtractAction = addAction.reverse

          val forward      = addAction.execute(original)
          val backward     = forward.flatMap(subtractAction.execute)
          val roundTripped = backward.toOption.get

          assertTrue(
            forward.toOption.get == DynamicValue.Record(
              Chunk("price" -> DynamicValue.Primitive(PrimitiveValue.Int(150)))
            )
          ) &&
          assertTrue(roundTripped == original)
        },
        test("Subtract → Add → round-trip preserves original") {
          val original = DynamicValue.Record(
            Chunk(
              "balance" -> DynamicValue.Primitive(PrimitiveValue.Int(1000))
            )
          )

          val subtractAction = MigrationAction.TransformValue(
            at = DynamicOptic.root.field("balance"),
            transform = DynamicSchemaExpr.Arithmetic(
              DynamicSchemaExpr.Dynamic(DynamicOptic.root),
              200,
              DynamicSchemaExpr.ArithmeticOperator.Subtract,
              DynamicSchemaExpr.NumericType.IntType
            )
          )
          val addAction = subtractAction.reverse

          val forward      = subtractAction.execute(original)
          val backward     = forward.flatMap(addAction.execute)
          val roundTripped = backward.toOption.get

          assertTrue(
            forward.toOption.get == DynamicValue.Record(
              Chunk("balance" -> DynamicValue.Primitive(PrimitiveValue.Int(800)))
            )
          ) &&
          assertTrue(roundTripped == original)
        }
      ),
      suite("TransformValue - Multiply/Divide (requires Divide operator)")(
        test("Multiply → Divide → round-trip preserves original") {
          val original = DynamicValue.Record(
            Chunk(
              "quantity" -> DynamicValue.Primitive(PrimitiveValue.Int(10))
            )
          )

          val multiplyAction = MigrationAction.TransformValue(
            at = DynamicOptic.root.field("quantity"),
            transform = DynamicSchemaExpr.Arithmetic(
              DynamicSchemaExpr.Dynamic(DynamicOptic.root),
              5,
              DynamicSchemaExpr.ArithmeticOperator.Multiply,
              DynamicSchemaExpr.NumericType.IntType
            )
          )
          val divideAction = multiplyAction.reverse

          val forward      = multiplyAction.execute(original)
          val backward     = forward.flatMap(divideAction.execute)
          val roundTripped = backward.toOption.get

          assertTrue(
            forward.toOption.get == DynamicValue.Record(
              Chunk("quantity" -> DynamicValue.Primitive(PrimitiveValue.Int(50)))
            )
          ) &&
          assertTrue(roundTripped == original)
        }
      ),
      suite("TransformValue - Boolean Not (self-inverse)")(
        test("Not → Not → round-trip preserves original") {
          val original = DynamicValue.Record(
            Chunk(
              "active" -> DynamicValue.Primitive(PrimitiveValue.Boolean(true))
            )
          )

          val notAction = MigrationAction.TransformValue(
            at = DynamicOptic.root.field("active"),
            transform = DynamicSchemaExpr.Not(
              DynamicSchemaExpr.Dynamic(DynamicOptic.root)
            )
          )
          val reverseNotAction = notAction.reverse

          val forward      = notAction.execute(original)
          val backward     = forward.flatMap(reverseNotAction.execute)
          val roundTripped = backward.toOption.get

          assertTrue(
            forward.toOption.get == DynamicValue.Record(
              Chunk("active" -> DynamicValue.Primitive(PrimitiveValue.Boolean(false)))
            )
          ) &&
          assertTrue(roundTripped == original)
        }
      )
    ),
    suite("Lossy Bidirectional Transformations")(
      suite("TransformValue - Uppercase/Lowercase")(
        test("Uppercase → Lowercase → reverses but loses original casing") {
          val original = DynamicValue.Record(
            Chunk(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("john"))
            )
          )

          val uppercaseAction = MigrationAction.TransformValue(
            at = DynamicOptic.root.field("name"),
            transform = DynamicSchemaExpr.StringUppercase(
              DynamicSchemaExpr.Dynamic(DynamicOptic.root)
            )
          )
          val lowercaseAction = uppercaseAction.reverse

          val forward  = uppercaseAction.execute(original)
          val backward = forward.flatMap(lowercaseAction.execute)

          assertTrue(
            forward.toOption.get == DynamicValue.Record(
              Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("JOHN")))
            )
          ) &&
          assertTrue(backward.toOption.get == original)
        },
        test("Lowercase → Uppercase → reverses but loses original casing") {
          val original = DynamicValue.Record(
            Chunk(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("JOHN"))
            )
          )

          val lowercaseAction = MigrationAction.TransformValue(
            at = DynamicOptic.root.field("name"),
            transform = DynamicSchemaExpr.StringLowercase(
              DynamicSchemaExpr.Dynamic(DynamicOptic.root)
            )
          )
          val uppercaseAction = lowercaseAction.reverse

          val forward  = lowercaseAction.execute(original)
          val backward = forward.flatMap(uppercaseAction.execute)

          assertTrue(
            forward.toOption.get == DynamicValue.Record(
              Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("john")))
            )
          ) &&
          assertTrue(backward.toOption.get == original)
        },
        test("Lossy: Mixed case gets lost in round-trip") {
          val original = DynamicValue.Record(
            Chunk(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("JoHn"))
            )
          )

          val uppercaseAction = MigrationAction.TransformValue(
            at = DynamicOptic.root.field("name"),
            transform = DynamicSchemaExpr.StringUppercase(
              DynamicSchemaExpr.Dynamic(DynamicOptic.root)
            )
          )
          val lowercaseAction = uppercaseAction.reverse

          val forward  = uppercaseAction.execute(original)
          val backward = forward.flatMap(lowercaseAction.execute)

          // After round-trip: "JoHn" → "JOHN" → "john" (LOST original casing!)
          assertTrue(
            backward.toOption.get == DynamicValue.Record(
              Chunk("name" -> DynamicValue.Primitive(PrimitiveValue.String("john")))
            )
          ) &&
          assertTrue(backward.toOption.get != original) // Demonstrates loss
        }
      )
    ),
    suite("Lossy - Join/Split")(
      suite("Join - String Concatenation")(
        test("Join with delimiter → Split → recovers fields") {
          val original = DynamicValue.Record(
            Chunk(
              "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
              "lastName"  -> DynamicValue.Primitive(PrimitiveValue.String("Doe"))
            )
          )

          val joinAction = MigrationAction.Join(
            at = DynamicOptic.root.field("fullName"),
            sourcePaths = Vector(
              DynamicOptic.root.field("firstName"),
              DynamicOptic.root.field("lastName")
            ),
            combiner = DynamicSchemaExpr.StringConcat(
              DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field0")),
              DynamicSchemaExpr.StringConcat(
                " ",
                DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field1"))
              )
            )
          )
          val splitAction = joinAction.reverse

          val forward  = joinAction.execute(original)
          val backward = forward.flatMap(splitAction.execute)

          assertTrue(
            forward.toOption.get == DynamicValue.Record(
              Chunk("fullName" -> DynamicValue.Primitive(PrimitiveValue.String("John Doe")))
            )
          ) &&
          assertTrue(backward.toOption.get == original)
        }
      ),
      suite("Split - String Delimiter")(
        test("Split with delimiter → Join → recovers original") {
          val original = DynamicValue.Record(
            Chunk(
              "fullName" -> DynamicValue.Primitive(PrimitiveValue.String("John Doe"))
            )
          )

          val splitAction = MigrationAction.Split(
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
          val joinAction = splitAction.reverse

          val forward  = splitAction.execute(original)
          val backward = forward.flatMap(joinAction.execute)

          assertTrue(joinAction.isInstanceOf[MigrationAction.Join]) &&
          assertTrue(
            forward.toOption.get == DynamicValue.Record(
              Chunk(
                "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
                "lastName"  -> DynamicValue.Primitive(PrimitiveValue.String("Doe"))
              )
            )
          ) &&
          assertTrue(backward.toOption.get == original)
        },
        test("Split with 3 target paths → Join → recovers original") {
          val original = DynamicValue.Record(
            Chunk(
              "date" -> DynamicValue.Primitive(PrimitiveValue.String("2024-01-15"))
            )
          )

          val splitAction = MigrationAction.Split(
            at = DynamicOptic.root.field("date"),
            targetPaths = Vector(
              DynamicOptic.root.field("year"),
              DynamicOptic.root.field("month"),
              DynamicOptic.root.field("day")
            ),
            splitter = DynamicSchemaExpr.StringSplit(
              DynamicSchemaExpr.Dynamic(DynamicOptic.root),
              "-"
            )
          )
          val joinAction = splitAction.reverse

          val forward  = splitAction.execute(original)
          val backward = forward.flatMap(joinAction.execute)

          assertTrue(joinAction.isInstanceOf[MigrationAction.Join]) &&
          assertTrue(backward.toOption.get == original)
        }
      )
    ),
    suite("Impossible - Cannot Reverse (Returns Irreversible)")(
      suite("TransformValue - Irreversible Operations")(
        test("StringLength cannot reverse (info lost)") {
          val lengthAction = MigrationAction.TransformValue(
            at = DynamicOptic.root.field("name"),
            transform = DynamicSchemaExpr.StringLength(
              DynamicSchemaExpr.Dynamic(DynamicOptic.root)
            )
          )
          val reverseAction = lengthAction.reverse

          // Reverse should return Irreversible
          assertTrue(reverseAction.isInstanceOf[MigrationAction.Irreversible])
        },
        test("Relational operations cannot reverse (boolean result)") {
          val relationalAction = MigrationAction.TransformValue(
            at = DynamicOptic.root.field("age"),
            transform = DynamicSchemaExpr.Relational(
              DynamicSchemaExpr.Dynamic(DynamicOptic.root),
              18,
              DynamicSchemaExpr.RelationalOperator.GreaterThan
            )
          )
          val reverseAction = relationalAction.reverse

          // Reverse should return Irreversible
          assertTrue(reverseAction.isInstanceOf[MigrationAction.Irreversible])
        }
      ),
      suite("Join/Split - Unsupported Expression Types")(
        test("Join with non-StringConcat combiner returns Irreversible") {
          val original = DynamicValue.Record(
            Chunk(
              "x" -> DynamicValue.Primitive(PrimitiveValue.Int(10)),
              "y" -> DynamicValue.Primitive(PrimitiveValue.Int(20))
            )
          )

          val joinAction = MigrationAction.Join(
            at = DynamicOptic.root.field("sum"),
            sourcePaths = Vector(
              DynamicOptic.root.field("x"),
              DynamicOptic.root.field("y")
            ),
            combiner = DynamicSchemaExpr.Arithmetic(
              DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field0")),
              DynamicSchemaExpr.Dynamic(DynamicOptic.root.field("field1")),
              DynamicSchemaExpr.ArithmeticOperator.Add,
              DynamicSchemaExpr.NumericType.IntType
            )
          )
          val reverseAction = joinAction.reverse

          assertTrue(reverseAction.isInstanceOf[MigrationAction.Irreversible]) &&
          assertTrue(reverseAction.execute(original).isLeft) &&
          assertTrue(
            reverseAction.execute(original).left.toOption.get.isInstanceOf[MigrationError.IrreversibleOperation]
          )
        },
        test("Split with non-StringSplit splitter returns Irreversible") {
          val original = DynamicValue.Record(
            Chunk(
              "data" -> DynamicValue.Primitive(PrimitiveValue.String("hello"))
            )
          )

          val splitAction = MigrationAction.Split(
            at = DynamicOptic.root.field("data"),
            targetPaths = Vector(
              DynamicOptic.root.field("part1"),
              DynamicOptic.root.field("part2")
            ),
            splitter = DynamicSchemaExpr.Dynamic(DynamicOptic.root)
          )
          val reverseAction = splitAction.reverse

          assertTrue(reverseAction.isInstanceOf[MigrationAction.Irreversible]) &&
          assertTrue(reverseAction.execute(original).isLeft) &&
          assertTrue(
            reverseAction.execute(original).left.toOption.get.isInstanceOf[MigrationError.IrreversibleOperation]
          )
        }
      )
    ),
    suite("Collection Operations - Follow TransformValue Patterns")(
      suite("TransformElements")(
        test("Add to all elements → Subtract from all elements") {
          val original = DynamicValue.Record(
            Chunk(
              "prices" -> DynamicValue.Sequence(
                Chunk(
                  DynamicValue.Primitive(PrimitiveValue.Int(100)),
                  DynamicValue.Primitive(PrimitiveValue.Int(200)),
                  DynamicValue.Primitive(PrimitiveValue.Int(300))
                )
              )
            )
          )

          val addAction = MigrationAction.TransformElements(
            at = DynamicOptic.root.field("prices"),
            transform = DynamicSchemaExpr.Arithmetic(
              DynamicSchemaExpr.Dynamic(DynamicOptic.root),
              10,
              DynamicSchemaExpr.ArithmeticOperator.Add,
              DynamicSchemaExpr.NumericType.IntType
            )
          )
          val subtractAction = addAction.reverse

          val forward  = addAction.execute(original)
          val backward = forward.flatMap(subtractAction.execute)

          assertTrue(backward.toOption.get == original)
        }
      ),
      suite("TransformKeys")(
        test("Uppercase keys → Lowercase keys (LOSSY)") {
          val original = DynamicValue.Record(
            Chunk(
              "metadata" -> DynamicValue.Map(
                Chunk(
                  DynamicValue.Primitive(PrimitiveValue.String("name")) ->
                    DynamicValue.Primitive(PrimitiveValue.String("John")),
                  DynamicValue.Primitive(PrimitiveValue.String("age")) ->
                    DynamicValue.Primitive(PrimitiveValue.Int(25))
                )
              )
            )
          )

          val uppercaseAction = MigrationAction.TransformKeys(
            at = DynamicOptic.root.field("metadata"),
            transform = DynamicSchemaExpr.StringUppercase(
              DynamicSchemaExpr.Dynamic(DynamicOptic.root)
            )
          )
          val lowercaseAction = uppercaseAction.reverse

          val forward  = uppercaseAction.execute(original)
          val backward = forward.flatMap(lowercaseAction.execute)

          assertTrue(backward.toOption.get == original)
        }
      ),
      suite("TransformValues")(
        test("Add to all values → Subtract from all values") {
          val original = DynamicValue.Record(
            Chunk(
              "scores" -> DynamicValue.Map(
                Chunk(
                  DynamicValue.Primitive(PrimitiveValue.String("math")) ->
                    DynamicValue.Primitive(PrimitiveValue.Int(90)),
                  DynamicValue.Primitive(PrimitiveValue.String("english")) ->
                    DynamicValue.Primitive(PrimitiveValue.Int(85))
                )
              )
            )
          )

          val addAction = MigrationAction.TransformValues(
            at = DynamicOptic.root.field("scores"),
            transform = DynamicSchemaExpr.Arithmetic(
              DynamicSchemaExpr.Dynamic(DynamicOptic.root),
              5,
              DynamicSchemaExpr.ArithmeticOperator.Add,
              DynamicSchemaExpr.NumericType.IntType
            )
          )
          val subtractAction = addAction.reverse

          val forward  = addAction.execute(original)
          val backward = forward.flatMap(subtractAction.execute)

          assertTrue(backward.toOption.get == original)
        }
      )
    ),
    suite("Complex Round-Trip Scenarios")(
      test("Multiple reversible operations compose correctly") {
        val original = DynamicValue.Record(
          Chunk(
            "firstName" -> DynamicValue.Primitive(PrimitiveValue.String("john")),
            "age"       -> DynamicValue.Primitive(PrimitiveValue.Int(25)),
            "salary"    -> DynamicValue.Primitive(PrimitiveValue.Int(50000))
          )
        )

        val actions = Vector(
          MigrationAction.Rename(
            at = DynamicOptic.root.field("firstName"),
            to = "name"
          ),
          MigrationAction.TransformValue(
            at = DynamicOptic.root.field("name"),
            transform = DynamicSchemaExpr.StringUppercase(
              DynamicSchemaExpr.Dynamic(DynamicOptic.root)
            )
          ),
          MigrationAction.TransformValue(
            at = DynamicOptic.root.field("salary"),
            transform = DynamicSchemaExpr.Arithmetic(
              DynamicSchemaExpr.Dynamic(DynamicOptic.root),
              10000,
              DynamicSchemaExpr.ArithmeticOperator.Add,
              DynamicSchemaExpr.NumericType.IntType
            )
          ),
          MigrationAction.Optionalize(
            at = DynamicOptic.root.field("age"),
            defaultForReverse = 0
          )
        )

        // Forward migration
        val forward = actions.foldLeft[Either[MigrationError, DynamicValue]](Right(original)) {
          case (Right(value), action) => action.execute(value)
          case (left, _)              => left
        }

        // Reverse migration
        val reverseActions = actions.reverse.map(_.reverse)
        val backward       = reverseActions.foldLeft(forward) {
          case (Right(value), action) => action.execute(value)
          case (left, _)              => left
        }

        // This will be LOSSY due to uppercase/lowercase
        // "john" → "JOHN" → "john" (happens to work for all-lowercase)
        assertTrue(forward.isRight) &&
        assertTrue(backward.isRight) &&
        assertTrue(backward.toOption.get == original)
      }
    )
  )
}
