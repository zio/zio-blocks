package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test._

object DynamicMigrationSpec extends SchemaBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("DynamicMigrationSpec")(
    addFieldSuite,
    dropFieldSuite,
    renameSuite,
    transformValueSuite,
    mandateSuite,
    optionalizeSuite,
    renameCaseSuite,
    transformElementsSuite,
    compositionSuite,
    reverseSuite,
    lossySuite,
    validateSuite,
    explainSuite
  )

  private val addFieldSuite = suite("AddField")(
    test("adds a field to a record with default value") {
      val record = DynamicValue.Record(
        Chunk(
          ("name", DynamicValue.Primitive(PrimitiveValue.String("Alice")))
        )
      )
      val action = MigrationAction.AddField(
        DynamicOptic.root.field("age"),
        SchemaExpr.Literal[Any, Any](42, Schema[Int].asInstanceOf[Schema[Any]])
      )
      val migration = DynamicMigration(Vector(action))
      val result    = migration.apply(record)
      assertTrue(
        result.isRight,
        result.toOption.get match {
          case DynamicValue.Record(fields) =>
            fields.length == 2 &&
            fields.exists(f => f._1 == "name") &&
            fields.exists(f => f._1 == "age" && f._2 == DynamicValue.Primitive(PrimitiveValue.Int(42)))
          case _ => false
        }
      )
    },
    test("fails when field already exists") {
      val record = DynamicValue.Record(
        Chunk(
          ("name", DynamicValue.Primitive(PrimitiveValue.String("Alice"))),
          ("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
        )
      )
      val action = MigrationAction.AddField(
        DynamicOptic.root.field("age"),
        SchemaExpr.Literal[Any, Any](42, Schema[Int].asInstanceOf[Schema[Any]])
      )
      val migration = DynamicMigration(Vector(action))
      val result    = migration.apply(record)
      assertTrue(
        result.isLeft,
        result.left.toOption.get.message.contains("already exists")
      )
    }
  )

  private val dropFieldSuite = suite("DropField")(
    test("drops a field from a record") {
      val record = DynamicValue.Record(
        Chunk(
          ("name", DynamicValue.Primitive(PrimitiveValue.String("Alice"))),
          ("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
        )
      )
      val action    = MigrationAction.DropField(DynamicOptic.root.field("age"), reverseDefault = None)
      val migration = DynamicMigration(Vector(action))
      val result    = migration.apply(record)
      assertTrue(
        result.isRight,
        result.toOption.get match {
          case DynamicValue.Record(fields) =>
            fields.length == 1 && fields.head._1 == "name"
          case _ => false
        }
      )
    },
    test("fails when field doesn't exist") {
      val record = DynamicValue.Record(
        Chunk(
          ("name", DynamicValue.Primitive(PrimitiveValue.String("Alice")))
        )
      )
      val action    = MigrationAction.DropField(DynamicOptic.root.field("age"), reverseDefault = None)
      val migration = DynamicMigration(Vector(action))
      val result    = migration.apply(record)
      assertTrue(
        result.isLeft,
        result.left.toOption.get.message.contains("not found")
      )
    }
  )

  private val renameSuite = suite("Rename")(
    test("renames a field in a record") {
      val record = DynamicValue.Record(
        Chunk(
          ("firstName", DynamicValue.Primitive(PrimitiveValue.String("Alice"))),
          ("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
        )
      )
      val action    = MigrationAction.Rename(DynamicOptic.root.field("firstName"), "fullName")
      val migration = DynamicMigration(Vector(action))
      val result    = migration.apply(record)
      assertTrue(
        result.isRight,
        result.toOption.get match {
          case DynamicValue.Record(fields) =>
            fields.exists(_._1 == "fullName") && !fields.exists(_._1 == "firstName")
          case _ => false
        }
      )
    },
    test("fails when target name already exists") {
      val record = DynamicValue.Record(
        Chunk(
          ("firstName", DynamicValue.Primitive(PrimitiveValue.String("Alice"))),
          ("fullName", DynamicValue.Primitive(PrimitiveValue.String("Bob")))
        )
      )
      val action    = MigrationAction.Rename(DynamicOptic.root.field("firstName"), "fullName")
      val migration = DynamicMigration(Vector(action))
      val result    = migration.apply(record)
      assertTrue(
        result.isLeft,
        result.left.toOption.get.message.contains("already exists")
      )
    }
  )

  private val transformValueSuite = suite("TransformValue")(
    test("transforms value at path using expression") {
      val record = DynamicValue.Record(
        Chunk(
          ("count", DynamicValue.Primitive(PrimitiveValue.Int(5)))
        )
      )
      // Use a literal expression as a simple transform (replaces value)
      val transform = SchemaExpr.Literal[Any, Any](10, Schema[Int].asInstanceOf[Schema[Any]])
      val action    = MigrationAction.TransformValue(DynamicOptic.root.field("count"), transform, inverse = None)
      val migration = DynamicMigration(Vector(action))
      val result    = migration.apply(record)
      assertTrue(
        result.isRight,
        result.toOption.get match {
          case DynamicValue.Record(fields) =>
            fields.exists(f => f._1 == "count" && f._2 == DynamicValue.Primitive(PrimitiveValue.Int(10)))
          case _ => false
        }
      )
    }
  )

  private val mandateSuite = suite("Mandate")(
    test("unwraps Some variant to value") {
      val record = DynamicValue.Record(
        Chunk(
          ("nickname", DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.String("Ali"))))
        )
      )
      val defaultExpr = SchemaExpr.Literal[Any, Any]("unknown", Schema[String].asInstanceOf[Schema[Any]])
      val action      = MigrationAction.Mandate(DynamicOptic.root.field("nickname"), defaultExpr)
      val migration   = DynamicMigration(Vector(action))
      val result      = migration.apply(record)
      assertTrue(
        result.isRight,
        result.toOption.get match {
          case DynamicValue.Record(fields) =>
            fields.exists(f => f._1 == "nickname" && f._2 == DynamicValue.Primitive(PrimitiveValue.String("Ali")))
          case _ => false
        }
      )
    },
    test("uses default for None variant") {
      val record = DynamicValue.Record(
        Chunk(
          ("nickname", DynamicValue.Variant("None", DynamicValue.Null))
        )
      )
      val defaultExpr = SchemaExpr.Literal[Any, Any]("unknown", Schema[String].asInstanceOf[Schema[Any]])
      val action      = MigrationAction.Mandate(DynamicOptic.root.field("nickname"), defaultExpr)
      val migration   = DynamicMigration(Vector(action))
      val result      = migration.apply(record)
      assertTrue(
        result.isRight,
        result.toOption.get match {
          case DynamicValue.Record(fields) =>
            fields.exists(f => f._1 == "nickname" && f._2 == DynamicValue.Primitive(PrimitiveValue.String("unknown")))
          case _ => false
        }
      )
    }
  )

  private val optionalizeSuite = suite("Optionalize")(
    test("wraps a value in Some variant") {
      val record = DynamicValue.Record(
        Chunk(
          ("name", DynamicValue.Primitive(PrimitiveValue.String("Alice")))
        )
      )
      val action    = MigrationAction.Optionalize(DynamicOptic.root.field("name"))
      val migration = DynamicMigration(Vector(action))
      val result    = migration.apply(record)
      assertTrue(
        result.isRight,
        result.toOption.get match {
          case DynamicValue.Record(fields) =>
            fields.exists(f =>
              f._1 == "name" &&
                f._2 == DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.String("Alice")))
            )
          case _ => false
        }
      )
    }
  )

  private val renameCaseSuite = suite("RenameCase")(
    test("renames a matching variant case") {
      val variant   = DynamicValue.Variant("Active", DynamicValue.Null)
      val action    = MigrationAction.RenameCase(DynamicOptic.root, "Active", "Enabled")
      val migration = DynamicMigration(Vector(action))
      val result    = migration.apply(variant)
      assertTrue(
        result.isRight,
        result.toOption.get == DynamicValue.Variant("Enabled", DynamicValue.Null)
      )
    },
    test("passes through non-matching case unchanged") {
      val variant   = DynamicValue.Variant("Inactive", DynamicValue.Null)
      val action    = MigrationAction.RenameCase(DynamicOptic.root, "Active", "Enabled")
      val migration = DynamicMigration(Vector(action))
      val result    = migration.apply(variant)
      assertTrue(
        result.isRight,
        result.toOption.get == DynamicValue.Variant("Inactive", DynamicValue.Null)
      )
    }
  )

  private val transformElementsSuite = suite("TransformElements")(
    test("transforms each element of a sequence") {
      val seq = DynamicValue.Sequence(
        Chunk(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2)),
          DynamicValue.Primitive(PrimitiveValue.Int(3))
        )
      )
      // Use a literal to replace all elements with the same value
      val transform = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val action    = MigrationAction.TransformElements(DynamicOptic.root, transform, inverse = None)
      val migration = DynamicMigration(Vector(action))
      val result    = migration.apply(seq)
      assertTrue(
        result.isRight,
        result.toOption.get match {
          case DynamicValue.Sequence(elems) =>
            elems.length == 3 && elems.forall(_ == DynamicValue.Primitive(PrimitiveValue.Int(0)))
          case _ => false
        }
      )
    }
  )

  private val compositionSuite = suite("Composition")(
    test("concatenates two migrations") {
      val record = DynamicValue.Record(
        Chunk(
          ("firstName", DynamicValue.Primitive(PrimitiveValue.String("Alice")))
        )
      )
      val m1 = DynamicMigration(
        Vector(
          MigrationAction.Rename(DynamicOptic.root.field("firstName"), "fullName")
        )
      )
      val m2 = DynamicMigration(
        Vector(
          MigrationAction.AddField(
            DynamicOptic.root.field("age"),
            SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
          )
        )
      )
      val composed = m1 ++ m2
      val result   = composed.apply(record)
      assertTrue(
        result.isRight,
        composed.actions.size == 2,
        result.toOption.get match {
          case DynamicValue.Record(fields) =>
            fields.exists(_._1 == "fullName") && fields.exists(_._1 == "age")
          case _ => false
        }
      )
    }
  )

  private val reverseSuite = suite("Reverse")(
    test("reverses a lossless migration") {
      val rename    = MigrationAction.Rename(DynamicOptic.root.field("firstName"), "fullName")
      val migration = DynamicMigration(Vector(rename))
      val reversed  = migration.reverse
      assertTrue(
        reversed.isDefined,
        reversed.get.actions.size == 1,
        reversed.get.actions.head match {
          case MigrationAction.Rename(_, newName) => newName == "firstName"
          case _                                  => false
        }
      )
    },
    test("reverse of lossless migration round-trips") {
      val record = DynamicValue.Record(
        Chunk(
          ("firstName", DynamicValue.Primitive(PrimitiveValue.String("Alice"))),
          ("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
        )
      )
      val migration = DynamicMigration(
        Vector(
          MigrationAction.Rename(DynamicOptic.root.field("firstName"), "fullName")
        )
      )
      val forward   = migration.apply(record)
      val reversed  = migration.reverse.get
      val roundTrip = forward.flatMap(reversed.apply(_))
      assertTrue(
        roundTrip.isRight,
        roundTrip.toOption.get == record
      )
    },
    test("returns None for lossy migration") {
      val drop      = MigrationAction.DropField(DynamicOptic.root.field("age"), reverseDefault = None)
      val migration = DynamicMigration(Vector(drop))
      assertTrue(migration.reverse.isEmpty)
    },
    test("unsafeReverse throws for lossy migration") {
      val drop      = MigrationAction.DropField(DynamicOptic.root.field("age"), reverseDefault = None)
      val migration = DynamicMigration(Vector(drop))
      val caught    = try {
        migration.unsafeReverse
        false
      } catch {
        case _: IllegalStateException => true
        case _: Throwable             => false
      }
      assertTrue(caught)
    }
  )

  private val lossySuite = suite("Lossy markers")(
    test("Rename is lossless") {
      val action = MigrationAction.Rename(DynamicOptic.root.field("a"), "b")
      assertTrue(!action.lossy, action.reverse.isDefined)
    },
    test("DropField without reverse default is lossy") {
      val action = MigrationAction.DropField(DynamicOptic.root.field("a"), reverseDefault = None)
      assertTrue(action.lossy, action.reverse.isEmpty)
    },
    test("DropField with reverse default is lossless") {
      val expr   = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.DropField(DynamicOptic.root.field("a"), reverseDefault = Some(expr))
      assertTrue(!action.lossy, action.reverse.isDefined)
    },
    test("AddField is lossless") {
      val expr   = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.AddField(DynamicOptic.root.field("a"), expr)
      assertTrue(!action.lossy, action.reverse.isDefined)
    },
    test("TransformValue without inverse is lossy") {
      val expr   = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.TransformValue(DynamicOptic.root.field("a"), expr, inverse = None)
      assertTrue(action.lossy, action.reverse.isEmpty)
    },
    test("TransformValue with inverse is lossless") {
      val expr   = SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
      val inv    = SchemaExpr.Literal[Any, Any](1, Schema[Int].asInstanceOf[Schema[Any]])
      val action = MigrationAction.TransformValue(DynamicOptic.root.field("a"), expr, inverse = Some(inv))
      assertTrue(!action.lossy, action.reverse.isDefined)
    },
    test("RenameCase is lossless") {
      val action = MigrationAction.RenameCase(DynamicOptic.root, "A", "B")
      assertTrue(!action.lossy, action.reverse.isDefined)
    },
    test("DynamicMigration.isLossy reflects action lossiness") {
      val lossless = DynamicMigration(
        Vector(
          MigrationAction.Rename(DynamicOptic.root.field("a"), "b")
        )
      )
      val lossy = DynamicMigration(
        Vector(
          MigrationAction.DropField(DynamicOptic.root.field("a"), reverseDefault = None)
        )
      )
      val mixed = DynamicMigration(
        Vector(
          MigrationAction.Rename(DynamicOptic.root.field("a"), "b"),
          MigrationAction.DropField(DynamicOptic.root.field("c"), reverseDefault = None)
        )
      )
      assertTrue(
        !lossless.isLossy,
        lossy.isLossy,
        mixed.isLossy,
        mixed.lossyActionIndices == Vector(1)
      )
    }
  )

  private val validateSuite = suite("Validate (security)")(
    test("accepts valid migration within limits") {
      val migration = DynamicMigration(
        Vector(
          MigrationAction.Rename(DynamicOptic.root.field("a"), "b")
        )
      )
      val result = DynamicMigration.validate(migration)
      assertTrue(result.isRight)
    },
    test("rejects migration with too many actions") {
      val actions   = (0 until 200).map(i => MigrationAction.Rename(DynamicOptic.root.field(s"f$i"), s"g$i")).toVector
      val migration = DynamicMigration(actions)
      val result    = DynamicMigration.validate(migration, DynamicMigration.Limits(maxActions = 100))
      assertTrue(
        result.isLeft,
        result.left.toOption.get.exists(_.contains("Too many actions"))
      )
    },
    test("rejects migration with deep optic path") {
      var path = DynamicOptic.root
      for (i <- 0 until 60) path = path.field(s"f$i")
      val action    = MigrationAction.Rename(path, "target")
      val migration = DynamicMigration(Vector(action))
      val result    = DynamicMigration.validate(migration, DynamicMigration.Limits(maxOpticDepth = 50))
      assertTrue(
        result.isLeft,
        result.left.toOption.get.exists(_.contains("Optic depth"))
      )
    }
  )

  private val explainSuite = suite("Explain")(
    test("produces human-readable summary") {
      val migration = DynamicMigration(
        Vector(
          MigrationAction.Rename(DynamicOptic.root.field("firstName"), "fullName"),
          MigrationAction.AddField(
            DynamicOptic.root.field("age"),
            SchemaExpr.Literal[Any, Any](0, Schema[Int].asInstanceOf[Schema[Any]])
          ),
          MigrationAction.DropField(DynamicOptic.root.field("obsolete"), reverseDefault = None)
        ),
        MigrationMetadata(id = Some("v1-to-v2"))
      )
      val explanation = migration.explain
      assertTrue(
        explanation.contains("v1-to-v2"),
        explanation.contains("3 actions"),
        explanation.contains("lossy"),
        explanation.contains("Rename"),
        explanation.contains("AddField"),
        explanation.contains("DropField")
      )
    }
  )
}
