package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test._

/**
 * Additional tests targeting untested branches across the migration module to
 * ensure branch coverage meets the 80% minimum.
 */
object MigrationCoverageSpec extends SchemaBaseSpec {

  def spec: Spec[TestEnvironment, Any] = suite("MigrationCoverageSpec")(
    changeTypeSuite,
    transformCaseSuite,
    transformKeysSuite,
    transformValuesSuite,
    joinSplitSuite,
    errorPathsSuite,
    validateEdgeCasesSuite,
    schemaShapeActionsSuite,
    migrationSchemasSuite,
    actionReverseSuite,
    explainEdgeCasesSuite,
    migrationErrorSuite,
    schemaShapeCompareSuite,
    schemaShapeResolveSuite,
    compositionSuite
  )

  // ── ChangeType ──────────────────────────────────────────────────────

  private val changeTypeSuite = suite("ChangeType execution")(
    test("changes type at a field using literal expression") {
      val record = DynamicValue.Record(
        Chunk(
          ("count", DynamicValue.Primitive(PrimitiveValue.Int(42)))
        )
      )
      val converter = SchemaExpr.Literal[Any, Any]("forty-two", Schema[String].asInstanceOf[Schema[Any]])
      val action    = MigrationAction.ChangeType(DynamicOptic.root.field("count"), converter, inverseConverter = None)
      val migration = DynamicMigration(Vector(action))
      val result    = migration.apply(record)
      assertTrue(
        result.isRight,
        result.toOption.get match {
          case DynamicValue.Record(fields) =>
            fields.exists(f => f._1 == "count" && f._2 == DynamicValue.Primitive(PrimitiveValue.String("forty-two")))
          case _ => false
        }
      )
    },
    test("ChangeType without inverse is lossy") {
      val expr   = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.ChangeType(DynamicOptic.root.field("a"), expr, inverseConverter = None)
      assertTrue(action.lossy, action.reverse.isEmpty)
    },
    test("ChangeType with inverse is lossless") {
      val expr   = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val inv    = SchemaExpr.Literal[Any, Any]("0", Schema[String].asInstanceOf[Schema[Any]])
      val action =
        MigrationAction.ChangeType(DynamicOptic.root.field("a"), expr, inverseConverter = Some(inv))
      assertTrue(!action.lossy, action.reverse.isDefined)
    }
  )

  // ── TransformCase ───────────────────────────────────────────────────

  private val transformCaseSuite = suite("TransformCase execution")(
    test("transforms matching case with sub-actions") {
      val variant = DynamicValue.Variant(
        "Active",
        DynamicValue.Record(
          Chunk(
            ("status", DynamicValue.Primitive(PrimitiveValue.String("on")))
          )
        )
      )
      val subAction = MigrationAction.Rename(DynamicOptic.root.field("status"), "state")
      val action    = MigrationAction.TransformCase(DynamicOptic.root, "Active", Vector(subAction))
      val migration = DynamicMigration(Vector(action))
      val result    = migration.apply(variant)
      assertTrue(
        result.isRight,
        result.toOption.get match {
          case DynamicValue.Variant("Active", DynamicValue.Record(fields)) =>
            fields.exists(_._1 == "state") && !fields.exists(_._1 == "status")
          case _ => false
        }
      )
    },
    test("passes through non-matching case unchanged") {
      val variant   = DynamicValue.Variant("Inactive", DynamicValue.Null)
      val subAction = MigrationAction.Rename(DynamicOptic.root.field("status"), "state")
      val action    = MigrationAction.TransformCase(DynamicOptic.root, "Active", Vector(subAction))
      val migration = DynamicMigration(Vector(action))
      val result    = migration.apply(variant)
      assertTrue(
        result.isRight,
        result.toOption.get == variant
      )
    },
    test("TransformCase with all lossless sub-actions is lossless") {
      val sub    = MigrationAction.Rename(DynamicOptic.root.field("a"), "b")
      val action = MigrationAction.TransformCase(DynamicOptic.root, "X", Vector(sub))
      assertTrue(!action.lossy, action.reverse.isDefined)
    },
    test("TransformCase with lossy sub-action is lossy") {
      val sub    = MigrationAction.DropField(DynamicOptic.root.field("a"), reverseDefault = None)
      val action = MigrationAction.TransformCase(DynamicOptic.root, "X", Vector(sub))
      assertTrue(action.lossy, action.reverse.isEmpty)
    },
    test("fails on non-variant value") {
      val record    = DynamicValue.Record(Chunk(("a", DynamicValue.Null)))
      val action    = MigrationAction.TransformCase(DynamicOptic.root, "X", Vector.empty)
      val migration = DynamicMigration(Vector(action))
      val result    = migration.apply(record)
      assertTrue(result.isLeft, result.left.toOption.get.message.contains("Expected variant"))
    }
  )

  // ── TransformKeys ───────────────────────────────────────────────────

  private val transformKeysSuite = suite("TransformKeys execution")(
    test("transforms each key of a map") {
      val map = DynamicValue.Map(
        Chunk(
          (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1))),
          (DynamicValue.Primitive(PrimitiveValue.String("b")), DynamicValue.Primitive(PrimitiveValue.Int(2)))
        )
      )
      val transform = SchemaExpr.Literal[Any, Any]("key", Schema[String].asInstanceOf[Schema[Any]])
      val action    = MigrationAction.TransformKeys(DynamicOptic.root, transform, inverse = None)
      val migration = DynamicMigration(Vector(action))
      val result    = migration.apply(map)
      assertTrue(
        result.isRight,
        result.toOption.get match {
          case DynamicValue.Map(entries) =>
            entries.length == 2 &&
            entries.forall(_._1 == DynamicValue.Primitive(PrimitiveValue.String("key")))
          case _ => false
        }
      )
    },
    test("fails on non-map value") {
      val seq    = DynamicValue.Sequence(Chunk(DynamicValue.Null))
      val expr   = SchemaExpr.Literal[Any, Any]("x", Schema[String].asInstanceOf[Schema[Any]])
      val action = MigrationAction.TransformKeys(DynamicOptic.root, expr, inverse = None)
      val result = DynamicMigration(Vector(action)).apply(seq)
      assertTrue(result.isLeft, result.left.toOption.get.message.contains("Expected map"))
    }
  )

  // ── TransformValues ─────────────────────────────────────────────────

  private val transformValuesSuite = suite("TransformValues execution")(
    test("transforms each value of a map") {
      val map = DynamicValue.Map(
        Chunk(
          (DynamicValue.Primitive(PrimitiveValue.String("x")), DynamicValue.Primitive(PrimitiveValue.Int(1))),
          (DynamicValue.Primitive(PrimitiveValue.String("y")), DynamicValue.Primitive(PrimitiveValue.Int(2)))
        )
      )
      val transform = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val action    = MigrationAction.TransformValues(DynamicOptic.root, transform, inverse = None)
      val migration = DynamicMigration(Vector(action))
      val result    = migration.apply(map)
      assertTrue(
        result.isRight,
        result.toOption.get match {
          case DynamicValue.Map(entries) =>
            entries.length == 2 &&
            entries.forall(_._2 == DynamicValue.Primitive(PrimitiveValue.Int(0)))
          case _ => false
        }
      )
    },
    test("fails on non-map value") {
      val record = DynamicValue.Record(Chunk(("a", DynamicValue.Null)))
      val expr   = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.TransformValues(DynamicOptic.root, expr, inverse = None)
      val result = DynamicMigration(Vector(action)).apply(record)
      assertTrue(result.isLeft, result.left.toOption.get.message.contains("Expected map"))
    }
  )

  // ── Join / Split ────────────────────────────────────────────────────

  private val joinSplitSuite = suite("Join and Split execution")(
    test("Join combines source fields into target") {
      val record = DynamicValue.Record(
        Chunk(
          ("first", DynamicValue.Primitive(PrimitiveValue.String("Alice"))),
          ("last", DynamicValue.Primitive(PrimitiveValue.String("Smith"))),
          ("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
        )
      )
      val combiner = SchemaExpr.Literal[Any, Any]("combined", Schema[String].asInstanceOf[Schema[Any]])
      val action   = MigrationAction.Join(
        DynamicOptic.root.field("fullName"),
        Vector(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
        combiner,
        inverseSplitter = None,
        targetShape = SchemaShape.Dyn
      )
      val migration = DynamicMigration(Vector(action))
      val result    = migration.apply(record)
      assertTrue(
        result.isRight,
        result.toOption.get match {
          case DynamicValue.Record(fields) =>
            fields.exists(_._1 == "fullName") &&
            fields.exists(_._1 == "age") &&
            !fields.exists(_._1 == "first") &&
            !fields.exists(_._1 == "last")
          case _ => false
        }
      )
    },
    test("Join fails when source path not found") {
      val record = DynamicValue.Record(
        Chunk(("name", DynamicValue.Primitive(PrimitiveValue.String("Alice"))))
      )
      val combiner = SchemaExpr.Literal[Any, Any]("x", Schema[String].asInstanceOf[Schema[Any]])
      val action   = MigrationAction.Join(
        DynamicOptic.root.field("result"),
        Vector(DynamicOptic.root.field("missing")),
        combiner,
        inverseSplitter = None,
        targetShape = SchemaShape.Dyn
      )
      val result = DynamicMigration(Vector(action)).apply(record)
      assertTrue(result.isLeft, result.left.toOption.get.message.contains("source path not found"))
    },
    test("Split divides source into multiple targets") {
      val record = DynamicValue.Record(
        Chunk(
          ("fullName", DynamicValue.Primitive(PrimitiveValue.String("Alice Smith"))),
          ("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
        )
      )
      val splitter = SchemaExpr.Literal[Any, Any](
        DynamicValue.Sequence(
          Chunk(
            DynamicValue.Primitive(PrimitiveValue.String("Alice")),
            DynamicValue.Primitive(PrimitiveValue.String("Smith"))
          )
        ),
        Schema[DynamicValue].asInstanceOf[Schema[Any]]
      )
      val action = MigrationAction.Split(
        DynamicOptic.root.field("fullName"),
        Vector(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
        splitter,
        inverseJoiner = None,
        targetShapes = Vector.empty
      )
      val migration = DynamicMigration(Vector(action))
      val result    = migration.apply(record)
      assertTrue(
        result.isRight,
        result.toOption.get match {
          case DynamicValue.Record(fields) =>
            fields.exists(_._1 == "first") &&
            fields.exists(_._1 == "last") &&
            fields.exists(_._1 == "age") &&
            !fields.exists(_._1 == "fullName")
          case _ => false
        }
      )
    },
    test("Split fails when source not found") {
      val record   = DynamicValue.Record(Chunk(("a", DynamicValue.Null)))
      val splitter = SchemaExpr.Literal[Any, Any]("x", Schema[String].asInstanceOf[Schema[Any]])
      val action   = MigrationAction.Split(
        DynamicOptic.root.field("missing"),
        Vector(DynamicOptic.root.field("b")),
        splitter,
        inverseJoiner = None,
        targetShapes = Vector.empty
      )
      val result = DynamicMigration(Vector(action)).apply(record)
      assertTrue(result.isLeft, result.left.toOption.get.message.contains("source path not found"))
    },
    test("Join without inverse is lossy") {
      val expr   = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.Join(
        DynamicOptic.root.field("t"),
        Vector(DynamicOptic.root.field("a")),
        expr,
        inverseSplitter = None,
        targetShape = SchemaShape.Dyn
      )
      assertTrue(action.lossy, action.reverse.isEmpty)
    },
    test("Join with inverse is lossless") {
      val expr   = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val inv    = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.Join(
        DynamicOptic.root.field("t"),
        Vector(DynamicOptic.root.field("a")),
        expr,
        inverseSplitter = Some(inv),
        targetShape = SchemaShape.Dyn
      )
      assertTrue(!action.lossy, action.reverse.isDefined)
    },
    test("Split without inverse is lossy") {
      val expr   = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.Split(
        DynamicOptic.root.field("a"),
        Vector(DynamicOptic.root.field("b")),
        expr,
        inverseJoiner = None,
        targetShapes = Vector.empty
      )
      assertTrue(action.lossy, action.reverse.isEmpty)
    },
    test("Split with inverse is lossless") {
      val expr   = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val inv    = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.Split(
        DynamicOptic.root.field("a"),
        Vector(DynamicOptic.root.field("b")),
        expr,
        inverseJoiner = Some(inv),
        targetShapes = Vector.empty
      )
      assertTrue(!action.lossy, action.reverse.isDefined)
    }
  )

  // ── Error Paths ─────────────────────────────────────────────────────

  private val errorPathsSuite = suite("Error paths")(
    test("AddField with root path fails (no Field node)") {
      val expr   = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.AddField(DynamicOptic.root, expr)
      val result = DynamicMigration(Vector(action)).apply(DynamicValue.Null)
      assertTrue(result.isLeft, result.left.toOption.get.message.contains("must end with a Field node"))
    },
    test("DropField with root path fails") {
      val action = MigrationAction.DropField(DynamicOptic.root, reverseDefault = None)
      val result = DynamicMigration(Vector(action)).apply(DynamicValue.Null)
      assertTrue(result.isLeft, result.left.toOption.get.message.contains("must end with a Field node"))
    },
    test("Rename with root path fails") {
      val action = MigrationAction.Rename(DynamicOptic.root, "newName")
      val result = DynamicMigration(Vector(action)).apply(DynamicValue.Null)
      assertTrue(result.isLeft, result.left.toOption.get.message.contains("must end with a Field node"))
    },
    test("Rename fails when source field not found") {
      val record = DynamicValue.Record(
        Chunk(("name", DynamicValue.Primitive(PrimitiveValue.String("Alice"))))
      )
      val action = MigrationAction.Rename(DynamicOptic.root.field("missing"), "newName")
      val result = DynamicMigration(Vector(action)).apply(record)
      assertTrue(result.isLeft, result.left.toOption.get.message.contains("not found"))
    },
    test("Mandate fails on non-Option variant") {
      val record = DynamicValue.Record(
        Chunk(("field", DynamicValue.Primitive(PrimitiveValue.Int(42))))
      )
      val expr   = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.Mandate(DynamicOptic.root.field("field"), expr)
      val result = DynamicMigration(Vector(action)).apply(record)
      assertTrue(result.isLeft, result.left.toOption.get.message.contains("Expected Option-like"))
    },
    test("RenameCase fails on non-variant") {
      val record = DynamicValue.Record(Chunk(("a", DynamicValue.Null)))
      val action = MigrationAction.RenameCase(DynamicOptic.root, "A", "B")
      val result = DynamicMigration(Vector(action)).apply(record)
      assertTrue(result.isLeft, result.left.toOption.get.message.contains("Expected variant"))
    },
    test("TransformElements fails on non-sequence") {
      val record = DynamicValue.Record(Chunk(("a", DynamicValue.Null)))
      val expr   = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.TransformElements(DynamicOptic.root, expr, inverse = None)
      val result = DynamicMigration(Vector(action)).apply(record)
      assertTrue(result.isLeft, result.left.toOption.get.message.contains("Expected sequence"))
    },
    test("includeInputSlice adds slice to error") {
      val record = DynamicValue.Record(
        Chunk(("name", DynamicValue.Primitive(PrimitiveValue.String("Alice"))))
      )
      val action = MigrationAction.DropField(DynamicOptic.root.field("missing"), reverseDefault = None)
      val result = DynamicMigration(Vector(action)).apply(record, includeInputSlice = true)
      assertTrue(
        result.isLeft,
        result.left.toOption.get.inputSlice.isDefined
      )
    },
    test("max execution depth exceeded returns error") {
      // Simulate deeply nested TransformCase by calling applyWithDepth directly
      val migration = DynamicMigration(Vector.empty)
      val result    = migration.applyWithDepth(DynamicValue.Null, false, 51)
      assertTrue(
        result.isLeft,
        result.left.toOption.get.message.contains("nesting depth")
      )
    },
    test("Optionalize is lossy") {
      val action = MigrationAction.Optionalize(DynamicOptic.root.field("x"))
      assertTrue(action.lossy, action.reverse.isEmpty)
    },
    test("Mandate reverse is Optionalize") {
      val expr   = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.Mandate(DynamicOptic.root.field("x"), expr)
      assertTrue(
        !action.lossy,
        action.reverse.isDefined,
        action.reverse.get.isInstanceOf[MigrationAction.Optionalize]
      )
    }
  )

  // ── Validate Edge Cases ─────────────────────────────────────────────

  private val validateEdgeCasesSuite = suite("Validate edge cases")(
    test("validates TransformCase nesting depth") {
      // Create deeply nested TransformCase
      def nested(depth: Int): MigrationAction =
        if (depth <= 0) MigrationAction.Rename(DynamicOptic.root.field("a"), "b")
        else MigrationAction.TransformCase(DynamicOptic.root, "X", Vector(nested(depth - 1)))
      val migration = DynamicMigration(Vector(nested(15)))
      val result    = DynamicMigration.validate(migration, DynamicMigration.Limits(maxNestingDepth = 10))
      assertTrue(
        result.isLeft,
        result.left.toOption.get.exists(_.contains("nesting depth"))
      )
    },
    test("validates total node count") {
      // Create many actions with optic paths to exceed total nodes
      val actions = (0 until 50).map { i =>
        val path = DynamicOptic.root.field(s"a$i").field(s"b$i").field(s"c$i")
        MigrationAction.Rename(path, s"d$i")
      }.toVector
      val migration = DynamicMigration(actions)
      val result    = DynamicMigration.validate(migration, DynamicMigration.Limits(maxTotalNodes = 50))
      assertTrue(
        result.isLeft,
        result.left.toOption.get.exists(_.contains("Total node count"))
      )
    },
    test("validates Join source optic depth") {
      var deepPath = DynamicOptic.root
      for (i <- 0 until 55) deepPath = deepPath.field(s"f$i")
      val expr   = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.Join(
        DynamicOptic.root.field("target"),
        Vector(deepPath),
        expr,
        inverseSplitter = None,
        targetShape = SchemaShape.Dyn
      )
      val migration = DynamicMigration(Vector(action))
      val result    = DynamicMigration.validate(migration, DynamicMigration.Limits(maxOpticDepth = 50))
      assertTrue(
        result.isLeft,
        result.left.toOption.get.exists(_.contains("Join source optic depth"))
      )
    },
    test("validates Split target optic depth") {
      var deepPath = DynamicOptic.root
      for (i <- 0 until 55) deepPath = deepPath.field(s"f$i")
      val expr   = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.Split(
        DynamicOptic.root.field("source"),
        Vector(deepPath),
        expr,
        inverseJoiner = None,
        targetShapes = Vector.empty
      )
      val migration = DynamicMigration(Vector(action))
      val result    = DynamicMigration.validate(migration, DynamicMigration.Limits(maxOpticDepth = 50))
      assertTrue(
        result.isLeft,
        result.left.toOption.get.exists(_.contains("Split target optic depth"))
      )
    }
  )

  // ── SchemaShape applyAction ─────────────────────────────────────────

  private val stringShape = SchemaShape.fromReflect(Schema[String].reflect)
  private val intShape    = SchemaShape.fromReflect(Schema[Int].reflect)

  private val schemaShapeActionsSuite = suite("SchemaShape applyAction additional")(
    test("ChangeType produces Dyn shape") {
      val shape  = SchemaShape.Record(Vector(("count", intShape)))
      val expr   = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.ChangeType(DynamicOptic.root.field("count"), expr, inverseConverter = None)
      val result = SchemaShape.applyAction(shape, action)
      assertTrue(result match {
        case Right(SchemaShape.Record(fields)) =>
          fields.exists(f => f._1 == "count" && f._2 == SchemaShape.Dyn)
        case _ => false
      })
    },
    test("TransformElements on sequence produces Sequence(Dyn)") {
      val shape  = SchemaShape.Sequence(intShape)
      val expr   = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.TransformElements(DynamicOptic.root, expr, inverse = None)
      val result = SchemaShape.applyAction(shape, action)
      assertTrue(result == Right(SchemaShape.Sequence(SchemaShape.Dyn)))
    },
    test("TransformElements on non-sequence fails") {
      val shape  = SchemaShape.Record(Vector(("a", intShape)))
      val expr   = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.TransformElements(DynamicOptic.root, expr, inverse = None)
      val result = SchemaShape.applyAction(shape, action)
      assertTrue(result.isLeft)
    },
    test("TransformKeys on map produces MapShape(Dyn, v)") {
      val shape  = SchemaShape.MapShape(stringShape, intShape)
      val expr   = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.TransformKeys(DynamicOptic.root, expr, inverse = None)
      val result = SchemaShape.applyAction(shape, action)
      assertTrue(result == Right(SchemaShape.MapShape(SchemaShape.Dyn, intShape)))
    },
    test("TransformKeys on non-map fails") {
      val shape  = SchemaShape.Sequence(intShape)
      val expr   = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.TransformKeys(DynamicOptic.root, expr, inverse = None)
      val result = SchemaShape.applyAction(shape, action)
      assertTrue(result.isLeft)
    },
    test("TransformValues on map produces MapShape(k, Dyn)") {
      val shape  = SchemaShape.MapShape(stringShape, intShape)
      val expr   = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.TransformValues(DynamicOptic.root, expr, inverse = None)
      val result = SchemaShape.applyAction(shape, action)
      assertTrue(result == Right(SchemaShape.MapShape(stringShape, SchemaShape.Dyn)))
    },
    test("TransformValues on non-map fails") {
      val shape  = SchemaShape.Record(Vector(("a", intShape)))
      val expr   = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.TransformValues(DynamicOptic.root, expr, inverse = None)
      val result = SchemaShape.applyAction(shape, action)
      assertTrue(result.isLeft)
    },
    test("TransformCase on variant transforms matching case") {
      val shape = SchemaShape.Variant(
        Vector(
          ("A", SchemaShape.Record(Vector(("x", intShape)))),
          ("B", SchemaShape.Record(Vector.empty))
        )
      )
      val subAction = MigrationAction.Rename(DynamicOptic.root.field("x"), "y")
      val action    = MigrationAction.TransformCase(DynamicOptic.root, "A", Vector(subAction))
      val result    = SchemaShape.applyAction(shape, action)
      assertTrue(result match {
        case Right(SchemaShape.Variant(cases)) =>
          cases.exists { case (n, s) =>
            n == "A" && (s match {
              case SchemaShape.Record(fields) => fields.exists(_._1 == "y")
              case _                          => false
            })
          }
        case _ => false
      })
    },
    test("TransformCase fails when case not found") {
      val shape  = SchemaShape.Variant(Vector(("A", SchemaShape.Record(Vector.empty))))
      val action = MigrationAction.TransformCase(DynamicOptic.root, "Missing", Vector.empty)
      val result = SchemaShape.applyAction(shape, action)
      assertTrue(result.isLeft)
    },
    test("Join on shape removes sources and adds target") {
      val shape = SchemaShape.Record(
        Vector(
          ("first", stringShape),
          ("last", stringShape),
          ("age", intShape)
        )
      )
      val expr   = SchemaExpr.Literal[Any, Any]("x", Schema[String].asInstanceOf[Schema[Any]])
      val action = MigrationAction.Join(
        DynamicOptic.root.field("fullName"),
        Vector(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
        expr,
        inverseSplitter = None,
        targetShape = SchemaShape.Dyn
      )
      val result = SchemaShape.applyAction(shape, action)
      assertTrue(result match {
        case Right(SchemaShape.Record(fields)) =>
          fields.exists(_._1 == "fullName") &&
          fields.exists(_._1 == "age") &&
          !fields.exists(_._1 == "first") &&
          !fields.exists(_._1 == "last")
        case _ => false
      })
    },
    test("Split on shape removes source and adds targets") {
      val shape = SchemaShape.Record(
        Vector(
          ("fullName", stringShape),
          ("age", intShape)
        )
      )
      val expr   = SchemaExpr.Literal[Any, Any]("x", Schema[String].asInstanceOf[Schema[Any]])
      val action = MigrationAction.Split(
        DynamicOptic.root.field("fullName"),
        Vector(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
        expr,
        inverseJoiner = None,
        targetShapes = Vector(SchemaShape.Dyn, SchemaShape.Dyn)
      )
      val result = SchemaShape.applyAction(shape, action)
      assertTrue(result match {
        case Right(SchemaShape.Record(fields)) =>
          fields.exists(_._1 == "first") &&
          fields.exists(_._1 == "last") &&
          fields.exists(_._1 == "age") &&
          !fields.exists(_._1 == "fullName")
        case _ => false
      })
    },
    test("TransformValue produces Dyn shape") {
      val shape  = SchemaShape.Record(Vector(("x", intShape)))
      val expr   = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.TransformValue(DynamicOptic.root.field("x"), expr, inverse = None)
      val result = SchemaShape.applyAction(shape, action)
      assertTrue(result match {
        case Right(SchemaShape.Record(fields)) =>
          fields.exists(f => f._1 == "x" && f._2 == SchemaShape.Dyn)
        case _ => false
      })
    },
    test("Mandate on non-optional shape fails") {
      val shape  = SchemaShape.Record(Vector(("x", intShape)))
      val expr   = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.Mandate(DynamicOptic.root.field("x"), expr)
      val result = SchemaShape.applyAction(shape, action)
      assertTrue(result.isLeft)
    }
  )

  // ── MigrationSchemas round-trip ─────────────────────────────────────

  private val migrationSchemasSuite = suite("MigrationSchemas serialization")(
    test("Rename action serializes and deserializes") {
      val action = MigrationAction.Rename(DynamicOptic.root.field("firstName"), "fullName")
      val schema = MigrationSchemas.migrationActionSchema
      val dv     = schema.toDynamicValue(action)
      val back   = schema.fromDynamicValue(dv)
      assertTrue(
        back.isRight,
        back.toOption.get match {
          case MigrationAction.Rename(at, name) =>
            at == DynamicOptic.root.field("firstName") && name == "fullName"
          case _ => false
        }
      )
    },
    test("DropField action serializes and deserializes") {
      val action = MigrationAction.DropField(DynamicOptic.root.field("age"), reverseDefault = None)
      val schema = MigrationSchemas.migrationActionSchema
      val dv     = schema.toDynamicValue(action)
      val back   = schema.fromDynamicValue(dv)
      assertTrue(
        back.isRight,
        back.toOption.get match {
          case MigrationAction.DropField(at, _) => at == DynamicOptic.root.field("age")
          case _                                => false
        }
      )
    },
    test("AddField action serializes and deserializes") {
      val expr   = SchemaExpr.Literal[Any, Any](42, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.AddField(DynamicOptic.root.field("count"), expr)
      val schema = MigrationSchemas.migrationActionSchema
      val dv     = schema.toDynamicValue(action)
      val back   = schema.fromDynamicValue(dv)
      assertTrue(
        back.isRight,
        back.toOption.get match {
          case MigrationAction.AddField(at, _) => at == DynamicOptic.root.field("count")
          case _                               => false
        }
      )
    },
    test("Optionalize action serializes and deserializes") {
      val action = MigrationAction.Optionalize(DynamicOptic.root.field("name"))
      val schema = MigrationSchemas.migrationActionSchema
      val dv     = schema.toDynamicValue(action)
      val back   = schema.fromDynamicValue(dv)
      assertTrue(
        back.isRight,
        back.toOption.get match {
          case MigrationAction.Optionalize(at) => at == DynamicOptic.root.field("name")
          case _                               => false
        }
      )
    },
    test("RenameCase action serializes and deserializes") {
      val action = MigrationAction.RenameCase(DynamicOptic.root, "Active", "Enabled")
      val schema = MigrationSchemas.migrationActionSchema
      val dv     = schema.toDynamicValue(action)
      val back   = schema.fromDynamicValue(dv)
      assertTrue(
        back.isRight,
        back.toOption.get match {
          case MigrationAction.RenameCase(_, from, to) => from == "Active" && to == "Enabled"
          case _                                       => false
        }
      )
    },
    test("MigrationMetadata serializes and deserializes") {
      val meta = MigrationMetadata(
        id = Some("v1-to-v2"),
        description = Some("test migration"),
        timestamp = Some(123456L),
        createdBy = Some("test"),
        fingerprint = Some("abc123")
      )
      val schema = MigrationSchemas.migrationMetadataSchema
      val dv     = schema.toDynamicValue(meta)
      val back   = schema.fromDynamicValue(dv)
      assertTrue(
        back.isRight,
        back.toOption.get == meta
      )
    },
    test("DynamicMigration serializes and deserializes") {
      val actions = Vector(
        MigrationAction.Rename(DynamicOptic.root.field("a"), "b"),
        MigrationAction.Optionalize(DynamicOptic.root.field("c"))
      )
      val dm     = DynamicMigration(actions, MigrationMetadata(id = Some("test")))
      val schema = MigrationSchemas.dynamicMigrationSchema
      val dv     = schema.toDynamicValue(dm)
      val back   = schema.fromDynamicValue(dv)
      assertTrue(
        back.isRight,
        back.toOption.get.actions.size == 2,
        back.toOption.get.metadata.id == Some("test")
      )
    },
    test("MigrationError serializes and deserializes") {
      val error = MigrationError(
        "test error",
        DynamicOptic.root.field("x"),
        actionIndex = Some(0),
        actualShape = Some("Record(...)"),
        expectedShape = Some("Prim(...)"),
        inputSlice = Some("some value")
      )
      val schema = MigrationSchemas.migrationErrorSchema
      val dv     = schema.toDynamicValue(error)
      val back   = schema.fromDynamicValue(dv)
      assertTrue(
        back.isRight,
        back.toOption.get.message == "test error"
      )
    }
  )

  // ── Action Reverse Coverage ─────────────────────────────────────────

  private val actionReverseSuite = suite("Action reverse coverage")(
    test("TransformElements with inverse is lossless and reversible") {
      val t      = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val inv    = SchemaExpr.Literal[Any, Any](1, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.TransformElements(DynamicOptic.root, t, inverse = Some(inv))
      assertTrue(!action.lossy, action.reverse.isDefined)
    },
    test("TransformElements without inverse is lossy") {
      val t      = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.TransformElements(DynamicOptic.root, t, inverse = None)
      assertTrue(action.lossy, action.reverse.isEmpty)
    },
    test("TransformKeys with inverse is lossless") {
      val t      = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val inv    = SchemaExpr.Literal[Any, Any](1, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.TransformKeys(DynamicOptic.root, t, inverse = Some(inv))
      assertTrue(!action.lossy, action.reverse.isDefined)
    },
    test("TransformKeys without inverse is lossy") {
      val t      = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.TransformKeys(DynamicOptic.root, t, inverse = None)
      assertTrue(action.lossy, action.reverse.isEmpty)
    },
    test("TransformValues with inverse is lossless") {
      val t      = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val inv    = SchemaExpr.Literal[Any, Any](1, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.TransformValues(DynamicOptic.root, t, inverse = Some(inv))
      assertTrue(!action.lossy, action.reverse.isDefined)
    },
    test("TransformValues without inverse is lossy") {
      val t      = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.TransformValues(DynamicOptic.root, t, inverse = None)
      assertTrue(action.lossy, action.reverse.isEmpty)
    }
  )

  // ── Explain Edge Cases ──────────────────────────────────────────────

  private val explainEdgeCasesSuite = suite("Explain edge cases")(
    test("explain with no actions") {
      val dm     = DynamicMigration(Vector.empty, MigrationMetadata(id = Some("empty")))
      val result = dm.explain
      assertTrue(
        result.contains("empty"),
        result.contains("0 actions"),
        result.contains("lossless")
      )
    },
    test("explain with single action uses singular") {
      val dm = DynamicMigration(
        Vector(MigrationAction.Rename(DynamicOptic.root.field("a"), "b"))
      )
      val result = dm.explain
      assertTrue(result.contains("1 action,"))
    },
    test("explain covers all action types") {
      val expr = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val dm   = DynamicMigration(
        Vector(
          MigrationAction.ChangeType(DynamicOptic.root.field("a"), expr, inverseConverter = None),
          MigrationAction.TransformCase(DynamicOptic.root, "X", Vector.empty),
          MigrationAction.TransformElements(DynamicOptic.root, expr, inverse = None),
          MigrationAction.TransformKeys(DynamicOptic.root, expr, inverse = None),
          MigrationAction.TransformValues(DynamicOptic.root, expr, inverse = None),
          MigrationAction.Join(
            DynamicOptic.root.field("t"),
            Vector(DynamicOptic.root.field("a"), DynamicOptic.root.field("b")),
            expr,
            inverseSplitter = None,
            targetShape = SchemaShape.Dyn
          ),
          MigrationAction.Split(
            DynamicOptic.root.field("s"),
            Vector(DynamicOptic.root.field("x"), DynamicOptic.root.field("y")),
            expr,
            inverseJoiner = None,
            targetShapes = Vector.empty
          )
        )
      )
      val result = dm.explain
      assertTrue(
        result.contains("ChangeType"),
        result.contains("TransformCase"),
        result.contains("TransformElements"),
        result.contains("TransformKeys"),
        result.contains("TransformValues"),
        result.contains("Join"),
        result.contains("Split")
      )
    }
  )

  // ── MigrationError ──────────────────────────────────────────────────

  private val migrationErrorSuite = suite("MigrationError formatting")(
    test("formatMessage without actionIndex") {
      val msg = MigrationError.formatMessage("test error", DynamicOptic.root, None)
      assertTrue(msg.contains("test error"))
    },
    test("formatMessage with actionIndex") {
      val msg = MigrationError.formatMessage("test error", DynamicOptic.root.field("x"), Some(3))
      assertTrue(msg.contains("[action 3]"), msg.contains("test error"))
    },
    test("MigrationValidationException message includes error count") {
      val errors = List(
        MigrationError("error1", DynamicOptic.root),
        MigrationError("error2", DynamicOptic.root)
      )
      val ex = MigrationValidationException(errors)
      assertTrue(
        ex.getMessage.contains("2 errors"),
        ex.getMessage.contains("error1"),
        ex.getMessage.contains("error2")
      )
    },
    test("MigrationValidationException singular error") {
      val ex = MigrationValidationException(List(MigrationError("only error", DynamicOptic.root)))
      assertTrue(ex.getMessage.contains("1 error)"))
    }
  )

  // ── SchemaShape compareShapes ───────────────────────────────────────

  private val schemaShapeCompareSuite = suite("SchemaShape compareShapes additional")(
    test("Variant comparison detects missing cases") {
      val actual   = SchemaShape.Variant(Vector(("A", SchemaShape.Record(Vector.empty))))
      val expected = SchemaShape.Variant(
        Vector(
          ("A", SchemaShape.Record(Vector.empty)),
          ("B", SchemaShape.Record(Vector.empty))
        )
      )
      val errors = SchemaShape.compareShapes(actual, expected)
      assertTrue(errors.nonEmpty, errors.exists(_.message.contains("missing target case")))
    },
    test("Variant comparison detects extra cases") {
      val actual = SchemaShape.Variant(
        Vector(
          ("A", SchemaShape.Record(Vector.empty)),
          ("B", SchemaShape.Record(Vector.empty))
        )
      )
      val expected = SchemaShape.Variant(Vector(("A", SchemaShape.Record(Vector.empty))))
      val errors   = SchemaShape.compareShapes(actual, expected)
      assertTrue(errors.nonEmpty, errors.exists(_.message.contains("unaccounted source case")))
    },
    test("Sequence comparison compares elements") {
      val actual   = SchemaShape.Sequence(stringShape)
      val expected = SchemaShape.Sequence(intShape)
      val errors   = SchemaShape.compareShapes(actual, expected)
      assertTrue(errors.nonEmpty, errors.exists(_.message.contains("shape mismatch")))
    },
    test("MapShape comparison compares keys and values") {
      val actual   = SchemaShape.MapShape(stringShape, intShape)
      val expected = SchemaShape.MapShape(intShape, intShape)
      val errors   = SchemaShape.compareShapes(actual, expected)
      assertTrue(errors.nonEmpty)
    },
    test("Opt comparison compares inner shapes") {
      val actual   = SchemaShape.Opt(stringShape)
      val expected = SchemaShape.Opt(intShape)
      val errors   = SchemaShape.compareShapes(actual, expected)
      assertTrue(errors.nonEmpty)
    },
    test("Wrap comparison compares inner shapes") {
      val actual   = SchemaShape.Wrap(stringShape)
      val expected = SchemaShape.Wrap(intShape)
      val errors   = SchemaShape.compareShapes(actual, expected)
      assertTrue(errors.nonEmpty)
    }
  )

  // ── SchemaShape resolveShapeAt ──────────────────────────────────────

  private val schemaShapeResolveSuite = suite("SchemaShape resolveShapeAt")(
    test("resolves field in record") {
      val shape  = SchemaShape.Record(Vector(("name", stringShape), ("age", intShape)))
      val result = SchemaShape.resolveShapeAt(shape, DynamicOptic.root.field("name"))
      assertTrue(result == Some(stringShape))
    },
    test("returns None for missing field") {
      val shape  = SchemaShape.Record(Vector(("name", stringShape)))
      val result = SchemaShape.resolveShapeAt(shape, DynamicOptic.root.field("missing"))
      assertTrue(result.isEmpty)
    },
    test("resolves empty path to root") {
      val shape  = SchemaShape.Record(Vector(("x", intShape)))
      val result = SchemaShape.resolveShapeAt(shape, DynamicOptic.root)
      assertTrue(result == Some(shape))
    },
    test("resolves nested field") {
      val inner  = SchemaShape.Record(Vector(("street", stringShape)))
      val shape  = SchemaShape.Record(Vector(("address", inner)))
      val result =
        SchemaShape.resolveShapeAt(shape, DynamicOptic.root.field("address").field("street"))
      assertTrue(result == Some(stringShape))
    },
    test("resolves elements of sequence") {
      val shape  = SchemaShape.Sequence(intShape)
      val result = SchemaShape.resolveShapeAt(shape, DynamicOptic.root.elements)
      assertTrue(result == Some(intShape))
    },
    test("returns None for elements on non-sequence") {
      val result = SchemaShape.resolveShapeAt(intShape, DynamicOptic.root.elements)
      assertTrue(result.isEmpty)
    }
  )

  // ── Composition (mixed action types) ──────────────────────────────

  private def lit[A](value: A, schema: Schema[A]): SchemaExpr[Any, Any] =
    SchemaExpr.Literal[Any, Any](value, schema.asInstanceOf[Schema[Any]])

  private val compositionSuite = suite("Composition with mixed action types")(
    test("record operations chain: Rename + AddField + DropField + Optionalize") {
      val record = DynamicValue.Record(
        Chunk(
          ("firstName", DynamicValue.Primitive(PrimitiveValue.String("Alice"))),
          ("age", DynamicValue.Primitive(PrimitiveValue.Int(30))),
          ("obsolete", DynamicValue.Primitive(PrimitiveValue.String("remove me")))
        )
      )
      val migration = DynamicMigration(
        Vector(
          MigrationAction.Rename(DynamicOptic.root.field("firstName"), "fullName"),
          MigrationAction.AddField(DynamicOptic.root.field("nickname"), lit("none", Schema[String])),
          MigrationAction.DropField(DynamicOptic.root.field("obsolete"), reverseDefault = None),
          MigrationAction.Optionalize(DynamicOptic.root.field("age"))
        )
      )
      val result = migration.apply(record)
      assertTrue(
        result.isRight,
        result.toOption.get match {
          case DynamicValue.Record(fields) =>
            fields.exists(f => f._1 == "fullName" && f._2 == DynamicValue.Primitive(PrimitiveValue.String("Alice"))) &&
            fields.exists(f => f._1 == "nickname" && f._2 == DynamicValue.Primitive(PrimitiveValue.String("none"))) &&
            fields.exists(f =>
              f._1 == "age" && f._2 == DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.Int(30)))
            ) &&
            !fields.exists(_._1 == "obsolete") &&
            !fields.exists(_._1 == "firstName")
          case _ => false
        }
      )
    },
    test("two migrations composed via ++ with different action types") {
      val record = DynamicValue.Record(
        Chunk(
          ("firstName", DynamicValue.Primitive(PrimitiveValue.String("Bob"))),
          ("obsolete", DynamicValue.Primitive(PrimitiveValue.String("old")))
        )
      )
      val m1 = DynamicMigration(
        Vector(
          MigrationAction.Rename(DynamicOptic.root.field("firstName"), "fullName"),
          MigrationAction.AddField(DynamicOptic.root.field("age"), lit(0, Schema[Int]))
        )
      )
      val m2 = DynamicMigration(
        Vector(
          MigrationAction.DropField(DynamicOptic.root.field("obsolete"), reverseDefault = None),
          MigrationAction.Optionalize(DynamicOptic.root.field("fullName"))
        )
      )
      val composed = m1 ++ m2
      val result   = composed.apply(record)
      assertTrue(
        result.isRight,
        composed.actions.size == 4,
        result.toOption.get match {
          case DynamicValue.Record(fields) =>
            fields.exists(f =>
              f._1 == "fullName" &&
                f._2 == DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.String("Bob")))
            ) &&
            fields.exists(f => f._1 == "age" && f._2 == DynamicValue.Primitive(PrimitiveValue.Int(0))) &&
            !fields.exists(_._1 == "obsolete") &&
            !fields.exists(_._1 == "firstName")
          case _ => false
        }
      )
    },
    test("enum operations composed: RenameCase then TransformCase") {
      val variant = DynamicValue.Variant(
        "Active",
        DynamicValue.Record(
          Chunk(("status", DynamicValue.Primitive(PrimitiveValue.String("on"))))
        )
      )
      val migration = DynamicMigration(
        Vector(
          MigrationAction.RenameCase(DynamicOptic.root, "Active", "Enabled"),
          MigrationAction.TransformCase(
            DynamicOptic.root,
            "Enabled",
            Vector(MigrationAction.Rename(DynamicOptic.root.field("status"), "state"))
          )
        )
      )
      val result = migration.apply(variant)
      assertTrue(
        result.isRight,
        result.toOption.get match {
          case DynamicValue.Variant("Enabled", DynamicValue.Record(fields)) =>
            fields.exists(_._1 == "state") && !fields.exists(_._1 == "status")
          case _ => false
        }
      )
    },
    test("record ops composed with ChangeType and TransformValue") {
      val record = DynamicValue.Record(
        Chunk(
          ("count", DynamicValue.Primitive(PrimitiveValue.Int(42))),
          ("label", DynamicValue.Primitive(PrimitiveValue.String("original")))
        )
      )
      val migration = DynamicMigration(
        Vector(
          MigrationAction.ChangeType(
            DynamicOptic.root.field("count"),
            lit("converted", Schema[String]),
            inverseConverter = None
          ),
          MigrationAction.TransformValue(
            DynamicOptic.root.field("label"),
            lit("transformed", Schema[String]),
            inverse = None
          ),
          MigrationAction.Rename(DynamicOptic.root.field("label"), "description")
        )
      )
      val result = migration.apply(record)
      assertTrue(
        result.isRight,
        result.toOption.get match {
          case DynamicValue.Record(fields) =>
            fields.exists(f => f._1 == "count" && f._2 == DynamicValue.Primitive(PrimitiveValue.String("converted"))) &&
            fields.exists(f =>
              f._1 == "description" && f._2 == DynamicValue.Primitive(PrimitiveValue.String("transformed"))
            ) &&
            !fields.exists(_._1 == "label")
          case _ => false
        }
      )
    },
    test("collection + record ops: TransformElements on nested sequence") {
      val record = DynamicValue.Record(
        Chunk(
          ("name", DynamicValue.Primitive(PrimitiveValue.String("Alice"))),
          (
            "scores",
            DynamicValue.Sequence(
              Chunk(
                DynamicValue.Primitive(PrimitiveValue.Int(1)),
                DynamicValue.Primitive(PrimitiveValue.Int(2))
              )
            )
          )
        )
      )
      val migration = DynamicMigration(
        Vector(
          MigrationAction.Rename(DynamicOptic.root.field("name"), "fullName"),
          MigrationAction.TransformElements(
            DynamicOptic.root.field("scores"),
            lit(0, Schema[Int]),
            inverse = None
          )
        )
      )
      val result = migration.apply(record)
      assertTrue(
        result.isRight,
        result.toOption.get match {
          case DynamicValue.Record(fields) =>
            fields.exists(_._1 == "fullName") &&
            fields.exists {
              case ("scores", DynamicValue.Sequence(elems)) =>
                elems.length == 2 && elems.forall(_ == DynamicValue.Primitive(PrimitiveValue.Int(0)))
              case _ => false
            }
          case _ => false
        }
      )
    },
    test("map ops composed: TransformKeys then TransformValues") {
      val map = DynamicValue.Map(
        Chunk(
          (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1))),
          (DynamicValue.Primitive(PrimitiveValue.String("b")), DynamicValue.Primitive(PrimitiveValue.Int(2)))
        )
      )
      val migration = DynamicMigration(
        Vector(
          MigrationAction.TransformKeys(DynamicOptic.root, lit("key", Schema[String]), inverse = None),
          MigrationAction.TransformValues(DynamicOptic.root, lit(0, Schema[Int]), inverse = None)
        )
      )
      val result = migration.apply(map)
      assertTrue(
        result.isRight,
        result.toOption.get match {
          case DynamicValue.Map(entries) =>
            entries.length == 2 &&
            entries.forall(e =>
              e._1 == DynamicValue.Primitive(PrimitiveValue.String("key")) &&
                e._2 == DynamicValue.Primitive(PrimitiveValue.Int(0))
            )
          case _ => false
        }
      )
    },
    test("Mandate + Rename + AddField chain") {
      val record = DynamicValue.Record(
        Chunk(
          ("nickname", DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.String("Ali")))),
          ("firstName", DynamicValue.Primitive(PrimitiveValue.String("Alice")))
        )
      )
      val migration = DynamicMigration(
        Vector(
          MigrationAction.Mandate(DynamicOptic.root.field("nickname"), lit("unknown", Schema[String])),
          MigrationAction.Rename(DynamicOptic.root.field("firstName"), "fullName"),
          MigrationAction.AddField(DynamicOptic.root.field("age"), lit(25, Schema[Int]))
        )
      )
      val result = migration.apply(record)
      assertTrue(
        result.isRight,
        result.toOption.get match {
          case DynamicValue.Record(fields) =>
            fields.exists(f => f._1 == "nickname" && f._2 == DynamicValue.Primitive(PrimitiveValue.String("Ali"))) &&
            fields.exists(_._1 == "fullName") &&
            fields.exists(f => f._1 == "age" && f._2 == DynamicValue.Primitive(PrimitiveValue.Int(25))) &&
            !fields.exists(_._1 == "firstName")
          case _ => false
        }
      )
    },
    test("Join + Rename + AddField chain") {
      val record = DynamicValue.Record(
        Chunk(
          ("first", DynamicValue.Primitive(PrimitiveValue.String("Alice"))),
          ("last", DynamicValue.Primitive(PrimitiveValue.String("Smith"))),
          ("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
        )
      )
      val migration = DynamicMigration(
        Vector(
          MigrationAction.Join(
            DynamicOptic.root.field("fullName"),
            Vector(DynamicOptic.root.field("first"), DynamicOptic.root.field("last")),
            lit("combined", Schema[String]),
            inverseSplitter = None,
            targetShape = SchemaShape.Dyn
          ),
          MigrationAction.AddField(DynamicOptic.root.field("email"), lit("none@example.com", Schema[String]))
        )
      )
      val result = migration.apply(record)
      assertTrue(
        result.isRight,
        result.toOption.get match {
          case DynamicValue.Record(fields) =>
            fields.exists(_._1 == "fullName") &&
            fields.exists(_._1 == "age") &&
            fields.exists(f =>
              f._1 == "email" && f._2 == DynamicValue.Primitive(PrimitiveValue.String("none@example.com"))
            ) &&
            !fields.exists(_._1 == "first") &&
            !fields.exists(_._1 == "last")
          case _ => false
        }
      )
    },
    test("error in second migration of composed chain reports correctly") {
      val record = DynamicValue.Record(
        Chunk(("name", DynamicValue.Primitive(PrimitiveValue.String("Alice"))))
      )
      val m1 = DynamicMigration(
        Vector(MigrationAction.Rename(DynamicOptic.root.field("name"), "fullName"))
      )
      val m2 = DynamicMigration(
        Vector(MigrationAction.DropField(DynamicOptic.root.field("nonexistent"), reverseDefault = None))
      )
      val composed = m1 ++ m2
      val result   = composed.apply(record)
      assertTrue(
        result.isLeft,
        result.left.toOption.get.message.contains("not found"),
        composed.actions.size == 2
      )
    },
    test("lossless composed migration is reversible") {
      val record = DynamicValue.Record(
        Chunk(
          ("firstName", DynamicValue.Primitive(PrimitiveValue.String("Alice"))),
          ("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
        )
      )
      val m1 = DynamicMigration(
        Vector(MigrationAction.Rename(DynamicOptic.root.field("firstName"), "fullName"))
      )
      val m2 = DynamicMigration(
        Vector(
          MigrationAction.AddField(DynamicOptic.root.field("nickname"), lit("none", Schema[String]))
        )
      )
      val composed = m1 ++ m2
      assertTrue(!composed.isLossy, composed.reverse.isDefined)
      val forward   = composed.apply(record)
      val reversed  = composed.reverse.get
      val roundTrip = forward.flatMap(reversed.apply(_))
      assertTrue(roundTrip.isRight, roundTrip.toOption.get == record)
    },
    test("mixed lossy/lossless composed migration tracks lossiness") {
      val m1 = DynamicMigration(
        Vector(MigrationAction.Rename(DynamicOptic.root.field("a"), "b"))
      )
      val m2 = DynamicMigration(
        Vector(
          MigrationAction.DropField(DynamicOptic.root.field("c"), reverseDefault = None),
          MigrationAction.TransformValue(DynamicOptic.root.field("d"), lit(0, Schema[Int]), inverse = None)
        )
      )
      val composed = m1 ++ m2
      assertTrue(
        composed.isLossy,
        composed.reverse.isEmpty,
        composed.lossyActionIndices == Vector(1, 2),
        composed.actions.size == 3
      )
    },
    test("three-way composition with different action domains") {
      val record = DynamicValue.Record(
        Chunk(
          ("firstName", DynamicValue.Primitive(PrimitiveValue.String("Alice"))),
          ("age", DynamicValue.Primitive(PrimitiveValue.Int(30))),
          ("temp", DynamicValue.Primitive(PrimitiveValue.String("remove")))
        )
      )
      val m1 = DynamicMigration(
        Vector(MigrationAction.Rename(DynamicOptic.root.field("firstName"), "fullName"))
      )
      val m2 = DynamicMigration(
        Vector(
          MigrationAction.DropField(DynamicOptic.root.field("temp"), reverseDefault = None),
          MigrationAction.AddField(DynamicOptic.root.field("email"), lit("a@b.com", Schema[String]))
        )
      )
      val m3 = DynamicMigration(
        Vector(MigrationAction.Optionalize(DynamicOptic.root.field("age")))
      )
      val leftAssoc  = (m1 ++ m2) ++ m3
      val rightAssoc = m1 ++ (m2 ++ m3)
      val resultL    = leftAssoc.apply(record)
      val resultR    = rightAssoc.apply(record)
      assertTrue(
        resultL.isRight,
        resultR.isRight,
        resultL == resultR,
        leftAssoc.actions.size == 4,
        rightAssoc.actions.size == 4
      )
    },
    test("full lifecycle: compose, apply, reverse, round-trip with mixed lossless ops") {
      val record = DynamicValue.Record(
        Chunk(
          ("firstName", DynamicValue.Primitive(PrimitiveValue.String("Alice"))),
          ("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
        )
      )
      val migration = DynamicMigration(
        Vector(
          MigrationAction.Rename(DynamicOptic.root.field("firstName"), "fullName"),
          MigrationAction.AddField(DynamicOptic.root.field("nickname"), lit("none", Schema[String])),
          MigrationAction.Rename(DynamicOptic.root.field("fullName"), "name")
        )
      )
      val forward  = migration.apply(record)
      val reversed = migration.reverse
      assertTrue(forward.isRight, reversed.isDefined)
      val roundTrip = forward.flatMap(reversed.get.apply(_))
      assertTrue(roundTrip.isRight, roundTrip.toOption.get == record)
    }
  )
}
