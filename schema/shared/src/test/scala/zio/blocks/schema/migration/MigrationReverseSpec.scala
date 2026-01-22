package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Comprehensive test suite for migration reverse operations.
 *   - PERFECT: Lossless bidirectional transformation
 *   - LOSSY: Reverses but may lose information (still useful!)
 *   - IMPOSSIBLE: Cannot reverse, returns identity
 */
object MigrationReverseSpec extends ZIOSpecDefault {

  def spec = suite("MigrationReverseSpec")(
    suite("PERFECT - Lossless Bidirectional Transformations")(
      suite("AddField / DropField")(
        test("AddField → DropField → round-trip preserves original") {
          val original = DynamicValue.Record(
            Vector(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("John"))
            )
          )

          val addAction = MigrationAction.AddField(
            at = DynamicOptic.root.field("age"),
            default = SchemaExpr.Literal(30, Schema.int)
          )
          val dropAction = addAction.reverse

          val forward      = addAction.execute(original)
          val backward     = forward.flatMap(dropAction.execute)
          val roundTripped = backward.toOption.get

          assertTrue(roundTripped == original)
        },
        test("DropField → AddField → round-trip preserves original") {
          val original = DynamicValue.Record(
            Vector(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("John")),
              "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(25))
            )
          )

          val dropAction = MigrationAction.DropField(
            at = DynamicOptic.root.field("age"),
            defaultForReverse = SchemaExpr.Literal(25, Schema.int)
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
            Vector(
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
            Vector(
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
            Vector(
              "age" -> DynamicValue.Variant(
                "Some",
                DynamicValue.Record(Vector("value" -> DynamicValue.Primitive(PrimitiveValue.Int(25))))
              )
            )
          )

          val mandateAction = MigrationAction.Mandate(
            at = DynamicOptic.root.field("age"),
            default = SchemaExpr.Literal(0, Schema.int)
          )
          val optionalizeAction = mandateAction.reverse

          val forward      = mandateAction.execute(original)
          val backward     = forward.flatMap(optionalizeAction.execute)
          val roundTripped = backward.toOption.get

          assertTrue(roundTripped == original)
        },
        test("Optionalize → Mandate → round-trip preserves original") {
          val original = DynamicValue.Record(
            Vector(
              "age" -> DynamicValue.Primitive(PrimitiveValue.Int(25))
            )
          )

          val optionalizeAction = MigrationAction.Optionalize(
            at = DynamicOptic.root.field("age"),
            defaultForReverse = SchemaExpr.Literal(0, Schema.int)
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
            DynamicValue.Record(Vector("email" -> DynamicValue.Primitive(PrimitiveValue.String("test@example.com"))))
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
              Vector(
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
                default = SchemaExpr.Literal("000", Schema.string)
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
    suite("PERFECT - Arithmetic Reversals (After Implementation)")(
      suite("TransformValue - Add/Subtract")(
        test("Add → Subtract → round-trip preserves original") {
          val original = DynamicValue.Record(
            Vector(
              "price" -> DynamicValue.Primitive(PrimitiveValue.Int(100))
            )
          )

          val addAction = MigrationAction.TransformValue(
            at = DynamicOptic.root.field("price"),
            transform = SchemaExpr.Arithmetic(
              SchemaExpr.Dynamic[DynamicValue, Int](DynamicOptic.root),
              SchemaExpr.Literal(50, Schema.int),
              SchemaExpr.ArithmeticOperator.Add,
              IsNumeric.IsInt
            )
          )
          val subtractAction = addAction.reverse

          val forward      = addAction.execute(original)
          val backward     = forward.flatMap(subtractAction.execute)
          val roundTripped = backward.toOption.get

          assertTrue(
            forward.toOption.get == DynamicValue.Record(
              Vector("price" -> DynamicValue.Primitive(PrimitiveValue.Int(150)))
            )
          ) &&
          assertTrue(roundTripped == original)
        },
        test("Subtract → Add → round-trip preserves original") {
          val original = DynamicValue.Record(
            Vector(
              "balance" -> DynamicValue.Primitive(PrimitiveValue.Int(1000))
            )
          )

          val subtractAction = MigrationAction.TransformValue(
            at = DynamicOptic.root.field("balance"),
            transform = SchemaExpr.Arithmetic(
              SchemaExpr.Dynamic[DynamicValue, Int](DynamicOptic.root),
              SchemaExpr.Literal(200, Schema.int),
              SchemaExpr.ArithmeticOperator.Subtract,
              IsNumeric.IsInt
            )
          )
          val addAction = subtractAction.reverse

          val forward      = subtractAction.execute(original)
          val backward     = forward.flatMap(addAction.execute)
          val roundTripped = backward.toOption.get

          assertTrue(
            forward.toOption.get == DynamicValue.Record(
              Vector("balance" -> DynamicValue.Primitive(PrimitiveValue.Int(800)))
            )
          ) &&
          assertTrue(roundTripped == original)
        }
      ),
      suite("TransformValue - Multiply/Divide (requires Divide operator)")(
        test("Multiply → Divide → round-trip preserves original") {
          val original = DynamicValue.Record(
            Vector(
              "quantity" -> DynamicValue.Primitive(PrimitiveValue.Int(10))
            )
          )

          val multiplyAction = MigrationAction.TransformValue(
            at = DynamicOptic.root.field("quantity"),
            transform = SchemaExpr.Arithmetic(
              SchemaExpr.Dynamic[DynamicValue, Int](DynamicOptic.root),
              SchemaExpr.Literal(5, Schema.int),
              SchemaExpr.ArithmeticOperator.Multiply,
              IsNumeric.IsInt
            )
          )
          val divideAction = multiplyAction.reverse

          val forward      = multiplyAction.execute(original)
          val backward     = forward.flatMap(divideAction.execute)
          val roundTripped = backward.toOption.get

          assertTrue(
            forward.toOption.get == DynamicValue.Record(
              Vector("quantity" -> DynamicValue.Primitive(PrimitiveValue.Int(50)))
            )
          ) &&
          assertTrue(roundTripped == original)
        }
      ),
      suite("TransformValue - Boolean Not (self-inverse)")(
        test("Not → Not → round-trip preserves original") {
          val original = DynamicValue.Record(
            Vector(
              "active" -> DynamicValue.Primitive(PrimitiveValue.Boolean(true))
            )
          )

          val notAction = MigrationAction.TransformValue(
            at = DynamicOptic.root.field("active"),
            transform = SchemaExpr.Not(
              SchemaExpr.Dynamic[DynamicValue, Boolean](DynamicOptic.root)
            )
          )
          val reverseNotAction = notAction.reverse

          val forward      = notAction.execute(original)
          val backward     = forward.flatMap(reverseNotAction.execute)
          val roundTripped = backward.toOption.get

          assertTrue(
            forward.toOption.get == DynamicValue.Record(
              Vector("active" -> DynamicValue.Primitive(PrimitiveValue.Boolean(false)))
            )
          ) &&
          assertTrue(roundTripped == original)
        }
      )
    ),
    suite("LOSSY - String Case Conversions")(
      suite("TransformValue - Uppercase/Lowercase")(
        test("Uppercase → Lowercase → reverses but loses original casing") {
          val original = DynamicValue.Record(
            Vector(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("john"))
            )
          )

          val uppercaseAction = MigrationAction.TransformValue(
            at = DynamicOptic.root.field("name"),
            transform = SchemaExpr.StringUppercase(
              SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root)
            )
          )
          val lowercaseAction = uppercaseAction.reverse

          val forward  = uppercaseAction.execute(original)
          val backward = forward.flatMap(lowercaseAction.execute)

          assertTrue(
            forward.toOption.get == DynamicValue.Record(
              Vector("name" -> DynamicValue.Primitive(PrimitiveValue.String("JOHN")))
            )
          ) &&
          assertTrue(backward.toOption.get == original)
        },
        test("Lowercase → Uppercase → reverses but loses original casing") {
          val original = DynamicValue.Record(
            Vector(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("JOHN"))
            )
          )

          val lowercaseAction = MigrationAction.TransformValue(
            at = DynamicOptic.root.field("name"),
            transform = SchemaExpr.StringLowercase(
              SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root)
            )
          )
          val uppercaseAction = lowercaseAction.reverse

          val forward  = lowercaseAction.execute(original)
          val backward = forward.flatMap(uppercaseAction.execute)

          assertTrue(
            forward.toOption.get == DynamicValue.Record(
              Vector("name" -> DynamicValue.Primitive(PrimitiveValue.String("john")))
            )
          ) &&
          assertTrue(backward.toOption.get == original)
        },
        test("LOSSY: Mixed case gets lost in round-trip") {
          val original = DynamicValue.Record(
            Vector(
              "name" -> DynamicValue.Primitive(PrimitiveValue.String("JoHn"))
            )
          )

          val uppercaseAction = MigrationAction.TransformValue(
            at = DynamicOptic.root.field("name"),
            transform = SchemaExpr.StringUppercase(
              SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root)
            )
          )
          val lowercaseAction = uppercaseAction.reverse

          val forward  = uppercaseAction.execute(original)
          val backward = forward.flatMap(lowercaseAction.execute)

          // After round-trip: "JoHn" → "JOHN" → "john" (LOST original casing!)
          assertTrue(
            backward.toOption.get == DynamicValue.Record(
              Vector("name" -> DynamicValue.Primitive(PrimitiveValue.String("john")))
            )
          ) &&
          assertTrue(backward.toOption.get != original) // Demonstrates loss
        }
      )
    ),
    // TODO AJAY FIX ME, figure out how you want to handle join and split
    suite("LOSSY - Join/Split (Leave as-is per user request)")(
      suite("Join - String Concatenation")(
        test("Join with delimiter → Split → recovers fields") {
          val original = DynamicValue.Record(
            Vector(
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
            combiner = SchemaExpr.StringConcat(
              SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root.field("field0")),
              SchemaExpr.StringConcat(
                SchemaExpr.Literal(" ", Schema.string),
                SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root.field("field1"))
              )
            )
          )
          val splitAction = joinAction.reverse

          val forward  = joinAction.execute(original)
          val backward = forward.flatMap(splitAction.execute)

          assertTrue(
            forward.toOption.get == DynamicValue.Record(
              Vector("fullName" -> DynamicValue.Primitive(PrimitiveValue.String("John Doe")))
            )
          ) &&
          assertTrue(backward.toOption.get == original)
        }
      ),
      suite("Split - String Delimiter")(
        test("Split.reverse returns a Join (but cannot execute correctly)") {
          val splitAction = MigrationAction.Split(
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
          val joinAction = splitAction.reverse

          // Split.reverse structurally creates a Join, but using splitter as combiner
          // doesn't work semantically (splitter expects string, combiner gets Record)
          // This is acknowledged as a limitation - Split reverse is best-effort only
          assertTrue(joinAction.isInstanceOf[MigrationAction.Join])
        }
      )
    ),
    suite("IMPOSSIBLE - Cannot Reverse (Returns Identity)")(
      suite("TransformValue - Irreversible Operations")(
        test("StringLength cannot reverse (info lost)") {
          val lengthAction = MigrationAction.TransformValue(
            at = DynamicOptic.root.field("name"),
            transform = SchemaExpr.StringLength(
              SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root)
            )
          )
          val reverseAction = lengthAction.reverse

          // Reverse should return identity
          assertTrue(reverseAction == lengthAction)
        },
        test("Relational operations cannot reverse (boolean result)") {
          val relationalAction = MigrationAction.TransformValue(
            at = DynamicOptic.root.field("age"),
            transform = SchemaExpr.Relational(
              SchemaExpr.Dynamic[DynamicValue, Int](DynamicOptic.root),
              SchemaExpr.Literal(18, Schema.int),
              SchemaExpr.RelationalOperator.GreaterThan
            )
          )
          val reverseAction = relationalAction.reverse

          // Reverse should return identity
          assertTrue(reverseAction == relationalAction)
        }
      )
    ),
    suite("Collection Operations - Follow TransformValue Patterns")(
      suite("TransformElements")(
        test("Add to all elements → Subtract from all elements") {
          val original = DynamicValue.Record(
            Vector(
              "prices" -> DynamicValue.Sequence(
                Vector(
                  DynamicValue.Primitive(PrimitiveValue.Int(100)),
                  DynamicValue.Primitive(PrimitiveValue.Int(200)),
                  DynamicValue.Primitive(PrimitiveValue.Int(300))
                )
              )
            )
          )

          val addAction = MigrationAction.TransformElements(
            at = DynamicOptic.root.field("prices"),
            transform = SchemaExpr.Arithmetic(
              SchemaExpr.Dynamic[DynamicValue, Int](DynamicOptic.root),
              SchemaExpr.Literal(10, Schema.int),
              SchemaExpr.ArithmeticOperator.Add,
              IsNumeric.IsInt
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
            Vector(
              "metadata" -> DynamicValue.Map(
                Vector(
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
            transform = SchemaExpr.StringUppercase(
              SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root)
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
            Vector(
              "scores" -> DynamicValue.Map(
                Vector(
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
            transform = SchemaExpr.Arithmetic(
              SchemaExpr.Dynamic[DynamicValue, Int](DynamicOptic.root),
              SchemaExpr.Literal(5, Schema.int),
              SchemaExpr.ArithmeticOperator.Add,
              IsNumeric.IsInt
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
          Vector(
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
            transform = SchemaExpr.StringUppercase(
              SchemaExpr.Dynamic[DynamicValue, String](DynamicOptic.root)
            )
          ),
          MigrationAction.TransformValue(
            at = DynamicOptic.root.field("salary"),
            transform = SchemaExpr.Arithmetic(
              SchemaExpr.Dynamic[DynamicValue, Int](DynamicOptic.root),
              SchemaExpr.Literal(10000, Schema.int),
              SchemaExpr.ArithmeticOperator.Add,
              IsNumeric.IsInt
            )
          ),
          MigrationAction.Optionalize(
            at = DynamicOptic.root.field("age"),
            defaultForReverse = SchemaExpr.Literal(0, Schema.int)
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
