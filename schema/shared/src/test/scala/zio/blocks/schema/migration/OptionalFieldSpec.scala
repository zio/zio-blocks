package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test._

object OptionalFieldSpec extends ZIOSpecDefault {

  def spec = suite("Optional Field Handling")(
    suite("Mandate Action")(
      test("unwraps Some and extracts value") {
        // Create a DynamicValue representing Some(42)
        val someValue = DynamicValue.Variant(
          "Some",
          DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(42))))
        )

        val action = MigrationAction.Mandate(
          at = DynamicOptic.root,
          default = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        )

        val result = action.execute(someValue)

        assertTrue(
          result == Right(DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )
      },
      test("uses default value for None") {
        // Create a DynamicValue representing None
        val noneValue = DynamicValue.Variant(
          "None",
          DynamicValue.Record(Chunk.empty)
        )

        val action = MigrationAction.Mandate(
          at = DynamicOptic.root,
          default = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(99)))
        )

        val result = action.execute(noneValue)

        assertTrue(
          result == Right(DynamicValue.Primitive(PrimitiveValue.Int(99)))
        )
      },
      test("unwraps Some in a record field") {
        val record = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            "age"  -> DynamicValue.Variant(
              "Some",
              DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(30))))
            )
          )
        )

        val action = MigrationAction.Mandate(
          at = DynamicOptic.root.field("age"),
          default = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        )

        val result = action.execute(record)

        assertTrue(
          result.isRight &&
            result.toOption.get == DynamicValue.Record(
              Chunk(
                "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
                "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
              )
            )
        )
      },
      test("uses default for None in a record field") {
        val record = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Bob")),
            "age"  -> DynamicValue.Variant("None", DynamicValue.Record(Chunk.empty))
          )
        )

        val action = MigrationAction.Mandate(
          at = DynamicOptic.root.field("age"),
          default = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(18)))
        )

        val result = action.execute(record)

        assertTrue(
          result.isRight &&
            result.toOption.get == DynamicValue.Record(
              Chunk(
                "name" -> DynamicValue.Primitive(PrimitiveValue.String("Bob")),
                "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(18))
              )
            )
        )
      },
      test("fails on non-Option value") {
        val plainValue = DynamicValue.Primitive(PrimitiveValue.Int(42))

        val action = MigrationAction.Mandate(
          at = DynamicOptic.root,
          default = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        )

        val result = action.execute(plainValue)

        assertTrue(result.isLeft)
      }
    ),
    suite("Optionalize Action")(
      test("wraps value in Some") {
        val plainValue = DynamicValue.Primitive(PrimitiveValue.Int(42))

        val action = MigrationAction.Optionalize(
          at = DynamicOptic.root,
          defaultForReverse = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        )

        val result = action.execute(plainValue)

        assertTrue(
          result == Right(
            DynamicValue.Variant(
              "Some",
              DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(42))))
            )
          )
        )
      },
      test("wraps value in record field") {
        val record = DynamicValue.Record(
          Chunk(
            "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            "age"  -> DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        )

        val action = MigrationAction.Optionalize(
          at = DynamicOptic.root.field("age"),
          defaultForReverse = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        )

        val result = action.execute(record)

        assertTrue(
          result.isRight &&
            result.toOption.get == DynamicValue.Record(
              Chunk(
                "name" -> DynamicValue.Primitive(PrimitiveValue.String("Alice")),
                "age"  -> DynamicValue.Variant(
                  "Some",
                  DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(30))))
                )
              )
            )
        )
      },
      test("wraps complex value (record) in Some") {
        val complexValue = DynamicValue.Record(
          Chunk(
            "street" -> DynamicValue.Primitive(PrimitiveValue.String("Main St")),
            "number" -> DynamicValue.Primitive(PrimitiveValue.Int(123))
          )
        )

        val action = MigrationAction.Optionalize(
          at = DynamicOptic.root,
          defaultForReverse = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("")))
        )

        val result = action.execute(complexValue)

        assertTrue(
          result == Right(
            DynamicValue.Variant(
              "Some",
              DynamicValue.Record(Chunk("value" -> complexValue))
            )
          )
        )
      }
    ),
    suite("Mandate Reverse (becomes Optionalize)")(
      test("reverse of Mandate is Optionalize") {
        val action = MigrationAction.Mandate(
          at = DynamicOptic.root.field("age"),
          default = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        )

        val reversed = action.reverse

        assertTrue(
          reversed.isInstanceOf[MigrationAction.Optionalize]
        )
      }
    ),
    suite("Optionalize Reverse (becomes Mandate)")(
      test("reverse of Optionalize is Mandate with default None behavior") {
        val action = MigrationAction.Optionalize(
          at = DynamicOptic.root.field("age"),
          defaultForReverse = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
        )

        val reversed = action.reverse

        assertTrue(
          reversed.isInstanceOf[MigrationAction.Mandate]
        )
      }
    ),
    suite("DynamicMigration with Optional Field Operations")(
      test("compose Mandate operations") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction.Mandate(
              DynamicOptic.root.field("age"),
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
            ),
            MigrationAction.Mandate(
              DynamicOptic.root.field("score"),
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(100)))
            )
          )
        )

        val record = DynamicValue.Record(
          Chunk(
            "age" -> DynamicValue
              .Variant("Some", DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(25))))),
            "score" -> DynamicValue.Variant("None", DynamicValue.Record(Chunk.empty))
          )
        )

        val result = migration.apply(record)

        assertTrue(
          result.isRight &&
            result.toOption.get == DynamicValue.Record(
              Chunk(
                "age"   -> DynamicValue.Primitive(PrimitiveValue.Int(25)),
                "score" -> DynamicValue.Primitive(PrimitiveValue.Int(100))
              )
            )
        )
      },
      test("compose Optionalize operations") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction
              .Optionalize(
                DynamicOptic.root.field("age"),
                DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
              ),
            MigrationAction.Optionalize(
              DynamicOptic.root.field("score"),
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
            )
          )
        )

        val record = DynamicValue.Record(
          Chunk(
            "age"   -> DynamicValue.Primitive(PrimitiveValue.Int(25)),
            "score" -> DynamicValue.Primitive(PrimitiveValue.Int(100))
          )
        )

        val result = migration.apply(record)

        assertTrue(
          result.isRight &&
            result.toOption.get == DynamicValue.Record(
              Chunk(
                "age" -> DynamicValue.Variant(
                  "Some",
                  DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(25))))
                ),
                "score" -> DynamicValue.Variant(
                  "Some",
                  DynamicValue.Record(Chunk("value" -> DynamicValue.Primitive(PrimitiveValue.Int(100))))
                )
              )
            )
        )
      },
      test("round-trip: Optionalize then Mandate preserves value") {
        val migration = DynamicMigration(
          Vector(
            MigrationAction
              .Optionalize(
                DynamicOptic.root.field("age"),
                DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
              ),
            MigrationAction.Mandate(
              DynamicOptic.root.field("age"),
              DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))
            )
          )
        )

        val record = DynamicValue.Record(
          Chunk(
            "age" -> DynamicValue.Primitive(PrimitiveValue.Int(30))
          )
        )

        val result = migration.apply(record)

        assertTrue(
          result == Right(record)
        )
      }
    )
  )
}
