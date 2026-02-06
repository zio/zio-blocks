package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.blocks.chunk.Chunk
import zio.test._

object DynamicMigrationCoverageSpec extends SchemaBaseSpec {

  private val root  = DynamicOptic.root
  private val intV  = DynamicValue.Primitive(PrimitiveValue.Int(42))
  private val strV  = DynamicValue.Primitive(PrimitiveValue.String("hello"))
  private val litI  = DynamicSchemaExpr.Literal(intV)
  private val litS  = DynamicSchemaExpr.Literal(strV)
  private val litI0 = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0)))

  private val simpleRecord = DynamicValue.Record(Chunk("name" -> strV, "age" -> intV))
  private val variant      = DynamicValue.Variant("Dog", DynamicValue.Record(Chunk("breed" -> strV)))
  private val seq3         = DynamicValue.Sequence(
    Chunk(intV, DynamicValue.Primitive(PrimitiveValue.Int(10)), DynamicValue.Primitive(PrimitiveValue.Int(20)))
  )
  private val map2 = DynamicValue.Map(
    Chunk(
      (DynamicValue.Primitive(PrimitiveValue.String("a")), intV),
      (DynamicValue.Primitive(PrimitiveValue.String("b")), DynamicValue.Primitive(PrimitiveValue.Int(99)))
    )
  )

  def spec: Spec[TestEnvironment, Any] = suite("DynamicMigrationCoverageSpec")(
    suite("DynamicMigration basics")(
      test("empty migration returns value unchanged") {
        val result = DynamicMigration.empty(simpleRecord)
        assertTrue(result == Right(simpleRecord))
      },
      test("isEmpty on empty is true") {
        assertTrue(DynamicMigration.empty.isEmpty)
      },
      test("nonEmpty on empty is false") {
        assertTrue(!DynamicMigration.empty.nonEmpty)
      },
      test("nonEmpty on migration with action") {
        val m = DynamicMigration(MigrationAction.Identity)
        assertTrue(m.nonEmpty)
      },
      test("++ composes migrations") {
        val m1       = DynamicMigration(MigrationAction.RenameField(root, "name", "fullName"))
        val m2       = DynamicMigration(MigrationAction.AddField(root, "email", litS))
        val combined = m1 ++ m2
        assertTrue(combined.actions.length == 2)
      },
      test("andThen is alias for ++") {
        val m1 = DynamicMigration(MigrationAction.Identity)
        val m2 = DynamicMigration(MigrationAction.Identity)
        assertTrue(m1.andThen(m2).actions.length == 2)
      },
      test("reverse reverses actions") {
        val m = DynamicMigration(
          Vector(
            MigrationAction.AddField(root, "x", litI),
            MigrationAction.RenameField(root, "a", "b")
          )
        )
        val rev = m.reverse
        assertTrue(
          rev.actions.length == 2 &&
            rev.actions(0).isInstanceOf[MigrationAction.RenameField] &&
            rev.actions(1).isInstanceOf[MigrationAction.DropField]
        )
      }
    ),
    suite("Identity action")(
      test("Identity returns value unchanged") {
        val result = DynamicMigration(MigrationAction.Identity)(simpleRecord)
        assertTrue(result == Right(simpleRecord))
      }
    ),
    suite("AddField action")(
      test("adds field to record") {
        val m      = DynamicMigration(MigrationAction.AddField(root, "email", litS))
        val result = m(simpleRecord)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.length == 3 && fields.exists(_._1 == "email"))
          case _ => assertTrue(false)
        }
      },
      test("add field already exists fails") {
        val m = DynamicMigration(MigrationAction.AddField(root, "name", litS))
        assertTrue(m(simpleRecord).isLeft)
      },
      test("add field on non-record fails") {
        val m = DynamicMigration(MigrationAction.AddField(root, "x", litI))
        assertTrue(m(intV).isLeft)
      }
    ),
    suite("DropField action")(
      test("drops field from record") {
        val m      = DynamicMigration(MigrationAction.DropField(root, "age", litI))
        val result = m(simpleRecord)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.length == 1 && !fields.exists(_._1 == "age"))
          case _ => assertTrue(false)
        }
      },
      test("drop non-existent field fails") {
        val m = DynamicMigration(MigrationAction.DropField(root, "missing", litI))
        assertTrue(m(simpleRecord).isLeft)
      },
      test("drop field from non-record fails") {
        val m = DynamicMigration(MigrationAction.DropField(root, "x", litI))
        assertTrue(m(intV).isLeft)
      }
    ),
    suite("RenameField action")(
      test("renames field in record") {
        val m      = DynamicMigration(MigrationAction.RenameField(root, "name", "fullName"))
        val result = m(simpleRecord)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.exists(_._1 == "fullName") && !fields.exists(_._1 == "name"))
          case _ => assertTrue(false)
        }
      },
      test("rename non-existent field fails") {
        val m = DynamicMigration(MigrationAction.RenameField(root, "missing", "new"))
        assertTrue(m(simpleRecord).isLeft)
      },
      test("rename to existing field fails") {
        val m = DynamicMigration(MigrationAction.RenameField(root, "name", "age"))
        assertTrue(m(simpleRecord).isLeft)
      },
      test("rename on non-record fails") {
        val m = DynamicMigration(MigrationAction.RenameField(root, "a", "b"))
        assertTrue(m(intV).isLeft)
      }
    ),
    suite("TransformValue action")(
      test("transforms a field value") {
        val mul = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2))),
          DynamicSchemaExpr.ArithmeticOperator.Multiply
        )
        val m      = DynamicMigration(MigrationAction.TransformValue(root.field("age"), mul, litI))
        val result = m(simpleRecord)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            val ageField = fields.find(_._1 == "age").get._2
            assertTrue(ageField == DynamicValue.Primitive(PrimitiveValue.Int(4)))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Mandate action")(
      test("mandate unwraps Some variant") {
        val rec    = DynamicValue.Record(Chunk("nick" -> DynamicValue.Variant("Some", strV)))
        val m      = DynamicMigration(MigrationAction.Mandate(root.field("nick"), litS))
        val result = m(rec)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.find(_._1 == "nick").get._2 == strV)
          case _ => assertTrue(false)
        }
      },
      test("mandate replaces None variant with default") {
        val rec    = DynamicValue.Record(Chunk("nick" -> DynamicValue.Variant("None", DynamicValue.Null)))
        val m      = DynamicMigration(MigrationAction.Mandate(root.field("nick"), litS))
        val result = m(rec)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.find(_._1 == "nick").get._2 == strV)
          case _ => assertTrue(false)
        }
      },
      test("mandate on non-optional value passes through") {
        val rec    = DynamicValue.Record(Chunk("nick" -> strV))
        val m      = DynamicMigration(MigrationAction.Mandate(root.field("nick"), litS))
        val result = m(rec)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.find(_._1 == "nick").get._2 == strV)
          case _ => assertTrue(false)
        }
      }
    ),
    suite("Optionalize action")(
      test("wraps value in Some variant") {
        val rec    = DynamicValue.Record(Chunk("nick" -> strV))
        val m      = DynamicMigration(MigrationAction.Optionalize(root.field("nick")))
        val result = m(rec)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.find(_._1 == "nick").get._2 == DynamicValue.Variant("Some", strV))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("ChangeType action")(
      test("converts field using converter expression") {
        val coerce = DynamicSchemaExpr.CoercePrimitive(
          DynamicSchemaExpr.Path(DynamicOptic.root.field("age")),
          "Long"
        )
        val m      = DynamicMigration(MigrationAction.ChangeType(root.field("age"), coerce, litI))
        val result = m(simpleRecord)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.find(_._1 == "age").get._2 == DynamicValue.Primitive(PrimitiveValue.Long(42L)))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("RenameCase action")(
      test("renames matching case") {
        val m      = DynamicMigration(MigrationAction.RenameCase(root, "Dog", "Hound"))
        val result = m(variant)
        result match {
          case Right(DynamicValue.Variant(name, _)) =>
            assertTrue(name == "Hound")
          case _ => assertTrue(false)
        }
      },
      test("non-matching case passes through") {
        val m      = DynamicMigration(MigrationAction.RenameCase(root, "Cat", "Kitty"))
        val result = m(variant)
        result match {
          case Right(DynamicValue.Variant(name, _)) =>
            assertTrue(name == "Dog")
          case _ => assertTrue(false)
        }
      },
      test("rename case on non-variant fails") {
        val m = DynamicMigration(MigrationAction.RenameCase(root, "A", "B"))
        assertTrue(m(simpleRecord).isLeft)
      }
    ),
    suite("TransformCase action")(
      test("transforms matching case") {
        val innerActions = Vector(MigrationAction.RenameField(root, "breed", "type"))
        val m            = DynamicMigration(MigrationAction.TransformCase(root, "Dog", innerActions))
        val result       = m(variant)
        result match {
          case Right(DynamicValue.Variant("Dog", DynamicValue.Record(fields))) =>
            assertTrue(fields.exists(_._1 == "type") && !fields.exists(_._1 == "breed"))
          case _ => assertTrue(false)
        }
      },
      test("non-matching case passes through") {
        val innerActions = Vector(MigrationAction.RenameField(root, "breed", "type"))
        val m            = DynamicMigration(MigrationAction.TransformCase(root, "Cat", innerActions))
        val result       = m(variant)
        result match {
          case Right(DynamicValue.Variant("Dog", _)) => assertTrue(true)
          case _                                     => assertTrue(false)
        }
      },
      test("transform case on non-variant fails") {
        val m = DynamicMigration(MigrationAction.TransformCase(root, "A", Vector.empty))
        assertTrue(m(simpleRecord).isLeft)
      }
    ),
    suite("TransformElements action")(
      test("transforms all elements in sequence") {
        val doubleExpr = DynamicSchemaExpr.Arithmetic(
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2))),
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(2))),
          DynamicSchemaExpr.ArithmeticOperator.Multiply
        )
        val m      = DynamicMigration(MigrationAction.TransformElements(root, doubleExpr, litI))
        val result = m(DynamicValue.Sequence(Chunk(intV)))
        result match {
          case Right(DynamicValue.Sequence(elems)) =>
            assertTrue(elems.head == DynamicValue.Primitive(PrimitiveValue.Int(4)))
          case _ => assertTrue(false)
        }
      },
      test("transform elements on non-sequence fails") {
        val m = DynamicMigration(MigrationAction.TransformElements(root, litI, litI))
        assertTrue(m(simpleRecord).isLeft)
      }
    ),
    suite("TransformKeys action")(
      test("transforms all keys in map") {
        val upper  = DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("X")))
        val m      = DynamicMigration(MigrationAction.TransformKeys(root, upper, litS))
        val result = m(map2)
        result match {
          case Right(DynamicValue.Map(entries)) =>
            assertTrue(entries.forall(_._1 == DynamicValue.Primitive(PrimitiveValue.String("X"))))
          case _ => assertTrue(false)
        }
      },
      test("transform keys on non-map fails") {
        val m = DynamicMigration(MigrationAction.TransformKeys(root, litS, litS))
        assertTrue(m(simpleRecord).isLeft)
      }
    ),
    suite("TransformValues action")(
      test("transforms all values in map") {
        val m      = DynamicMigration(MigrationAction.TransformValues(root, litI0, litI))
        val result = m(map2)
        result match {
          case Right(DynamicValue.Map(entries)) =>
            assertTrue(entries.forall(_._2 == DynamicValue.Primitive(PrimitiveValue.Int(0))))
          case _ => assertTrue(false)
        }
      },
      test("transform values on non-map fails") {
        val m = DynamicMigration(MigrationAction.TransformValues(root, litI, litI))
        assertTrue(m(simpleRecord).isLeft)
      }
    ),
    suite("modifyAtPathRec with various node types")(
      test("Field node navigates into nested record") {
        val nested = DynamicValue.Record(Chunk("person" -> simpleRecord))
        val m      = DynamicMigration(MigrationAction.RenameField(root.field("person"), "name", "fullName"))
        val result = m(nested)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            fields.find(_._1 == "person") match {
              case Some((_, DynamicValue.Record(inner))) =>
                assertTrue(inner.exists(_._1 == "fullName"))
              case _ => assertTrue(false)
            }
          case _ => assertTrue(false)
        }
      },
      test("Case node navigates into matching variant") {
        val caseOptic = DynamicOptic(Vector(DynamicOptic.Node.Case("Dog")))
        val m         = DynamicMigration(MigrationAction.RenameField(caseOptic, "breed", "type"))
        val result    = m(variant)
        result match {
          case Right(DynamicValue.Variant("Dog", DynamicValue.Record(fields))) =>
            assertTrue(fields.exists(_._1 == "type"))
          case _ => assertTrue(false)
        }
      },
      test("Case node with non-matching variant passes through") {
        val caseOptic = DynamicOptic(Vector(DynamicOptic.Node.Case("Cat")))
        val m         = DynamicMigration(MigrationAction.RenameField(caseOptic, "color", "hue"))
        val result    = m(variant)
        result match {
          case Right(DynamicValue.Variant("Dog", _)) => assertTrue(true)
          case _                                     => assertTrue(false)
        }
      },
      test("Case node on non-variant fails") {
        val caseOptic = DynamicOptic(Vector(DynamicOptic.Node.Case("X")))
        val m         = DynamicMigration(MigrationAction.AddField(caseOptic, "y", litI))
        assertTrue(m(simpleRecord).isLeft)
      },
      test("AtIndex node modifies specific element") {
        val atIdx  = DynamicOptic(Vector(DynamicOptic.Node.AtIndex(1)))
        val m      = DynamicMigration(MigrationAction.TransformValue(atIdx, litI0, litI))
        val result = m(seq3)
        result match {
          case Right(DynamicValue.Sequence(elems)) =>
            assertTrue(elems(1) == DynamicValue.Primitive(PrimitiveValue.Int(0)))
          case _ => assertTrue(false)
        }
      },
      test("AtIndex out of bounds fails") {
        val atIdx = DynamicOptic(Vector(DynamicOptic.Node.AtIndex(99)))
        val m     = DynamicMigration(MigrationAction.TransformValue(atIdx, litI0, litI))
        assertTrue(m(seq3).isLeft)
      },
      test("AtIndex on non-sequence fails") {
        val atIdx = DynamicOptic(Vector(DynamicOptic.Node.AtIndex(0)))
        val m     = DynamicMigration(MigrationAction.TransformValue(atIdx, litI0, litI))
        assertTrue(m(simpleRecord).isLeft)
      },
      test("AtMapKey node modifies specific entry") {
        val keyDV  = DynamicValue.Primitive(PrimitiveValue.String("a"))
        val atKey  = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(keyDV)))
        val m      = DynamicMigration(MigrationAction.TransformValue(atKey, litI0, litI))
        val result = m(map2)
        result match {
          case Right(DynamicValue.Map(entries)) =>
            assertTrue(entries.find(_._1 == keyDV).get._2 == DynamicValue.Primitive(PrimitiveValue.Int(0)))
          case _ => assertTrue(false)
        }
      },
      test("AtMapKey not found fails") {
        val keyDV = DynamicValue.Primitive(PrimitiveValue.String("missing"))
        val atKey = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(keyDV)))
        val m     = DynamicMigration(MigrationAction.TransformValue(atKey, litI0, litI))
        assertTrue(m(map2).isLeft)
      },
      test("AtMapKey on non-map fails") {
        val keyDV = DynamicValue.Primitive(PrimitiveValue.String("a"))
        val atKey = DynamicOptic(Vector(DynamicOptic.Node.AtMapKey(keyDV)))
        val m     = DynamicMigration(MigrationAction.TransformValue(atKey, litI0, litI))
        assertTrue(m(simpleRecord).isLeft)
      },
      test("Elements node modifies all sequence elements via path") {
        val elementsOptic = DynamicOptic(Vector(DynamicOptic.Node.Elements))
        val m             = DynamicMigration(MigrationAction.TransformValue(elementsOptic, litI0, litI))
        val result        = m(seq3)
        result match {
          case Right(DynamicValue.Sequence(elems)) =>
            assertTrue(elems.forall(_ == DynamicValue.Primitive(PrimitiveValue.Int(0))))
          case _ => assertTrue(false)
        }
      },
      test("Elements on non-sequence fails") {
        val elementsOptic = DynamicOptic(Vector(DynamicOptic.Node.Elements))
        val m             = DynamicMigration(MigrationAction.TransformValue(elementsOptic, litI0, litI))
        assertTrue(m(simpleRecord).isLeft)
      },
      test("MapKeys node modifies all map keys via path") {
        val keysOptic = DynamicOptic(Vector(DynamicOptic.Node.MapKeys))
        val m         = DynamicMigration(MigrationAction.TransformValue(keysOptic, litS, litS))
        val result    = m(map2)
        result match {
          case Right(DynamicValue.Map(entries)) =>
            assertTrue(entries.forall(_._1 == strV))
          case _ => assertTrue(false)
        }
      },
      test("MapKeys on non-map fails") {
        val keysOptic = DynamicOptic(Vector(DynamicOptic.Node.MapKeys))
        val m         = DynamicMigration(MigrationAction.TransformValue(keysOptic, litS, litS))
        assertTrue(m(simpleRecord).isLeft)
      },
      test("MapValues node modifies all map values via path") {
        val valsOptic = DynamicOptic(Vector(DynamicOptic.Node.MapValues))
        val m         = DynamicMigration(MigrationAction.TransformValue(valsOptic, litI0, litI))
        val result    = m(map2)
        result match {
          case Right(DynamicValue.Map(entries)) =>
            assertTrue(entries.forall(_._2 == DynamicValue.Primitive(PrimitiveValue.Int(0))))
          case _ => assertTrue(false)
        }
      },
      test("MapValues on non-map fails") {
        val valsOptic = DynamicOptic(Vector(DynamicOptic.Node.MapValues))
        val m         = DynamicMigration(MigrationAction.TransformValue(valsOptic, litI0, litI))
        assertTrue(m(simpleRecord).isLeft)
      },
      test("Wrapped node unwraps single-field record") {
        val wrappedOptic  = DynamicOptic(Vector(DynamicOptic.Node.Wrapped))
        val wrappedRecord = DynamicValue.Record(Chunk("value" -> intV))
        val m             = DynamicMigration(MigrationAction.TransformValue(wrappedOptic, litI0, litI))
        val result        = m(wrappedRecord)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.head._2 == DynamicValue.Primitive(PrimitiveValue.Int(0)))
          case _ => assertTrue(false)
        }
      },
      test("Wrapped on non-single-field value fails") {
        val wrappedOptic = DynamicOptic(Vector(DynamicOptic.Node.Wrapped))
        val m            = DynamicMigration(MigrationAction.TransformValue(wrappedOptic, litI0, litI))
        assertTrue(m(simpleRecord).isLeft)
      },
      test("AtIndices node modifies multiple elements") {
        val atIndices = DynamicOptic(Vector(DynamicOptic.Node.AtIndices(Vector(0, 2))))
        val m         = DynamicMigration(MigrationAction.TransformValue(atIndices, litI0, litI))
        val result    = m(seq3)
        result match {
          case Right(DynamicValue.Sequence(elems)) =>
            assertTrue(
              elems(0) == DynamicValue.Primitive(PrimitiveValue.Int(0)) &&
                elems(1) == DynamicValue.Primitive(PrimitiveValue.Int(10)) &&
                elems(2) == DynamicValue.Primitive(PrimitiveValue.Int(0))
            )
          case _ => assertTrue(false)
        }
      },
      test("AtIndices out of bounds fails") {
        val atIndices = DynamicOptic(Vector(DynamicOptic.Node.AtIndices(Vector(0, 99))))
        val m         = DynamicMigration(MigrationAction.TransformValue(atIndices, litI0, litI))
        assertTrue(m(seq3).isLeft)
      },
      test("AtIndices on non-sequence fails") {
        val atIndices = DynamicOptic(Vector(DynamicOptic.Node.AtIndices(Vector(0))))
        val m         = DynamicMigration(MigrationAction.TransformValue(atIndices, litI0, litI))
        assertTrue(m(simpleRecord).isLeft)
      },
      test("AtMapKeys node modifies multiple map entries") {
        val keyA   = DynamicValue.Primitive(PrimitiveValue.String("a"))
        val keyB   = DynamicValue.Primitive(PrimitiveValue.String("b"))
        val atKeys = DynamicOptic(Vector(DynamicOptic.Node.AtMapKeys(Vector(keyA, keyB))))
        val m      = DynamicMigration(MigrationAction.TransformValue(atKeys, litI0, litI))
        val result = m(map2)
        result match {
          case Right(DynamicValue.Map(entries)) =>
            assertTrue(entries.forall(_._2 == DynamicValue.Primitive(PrimitiveValue.Int(0))))
          case _ => assertTrue(false)
        }
      },
      test("AtMapKeys missing key fails") {
        val keyMissing = DynamicValue.Primitive(PrimitiveValue.String("missing"))
        val atKeys     = DynamicOptic(Vector(DynamicOptic.Node.AtMapKeys(Vector(keyMissing))))
        val m          = DynamicMigration(MigrationAction.TransformValue(atKeys, litI0, litI))
        assertTrue(m(map2).isLeft)
      },
      test("AtMapKeys on non-map fails") {
        val keyA   = DynamicValue.Primitive(PrimitiveValue.String("a"))
        val atKeys = DynamicOptic(Vector(DynamicOptic.Node.AtMapKeys(Vector(keyA))))
        val m      = DynamicMigration(MigrationAction.TransformValue(atKeys, litI0, litI))
        assertTrue(m(simpleRecord).isLeft)
      }
    ),
    suite("Join action")(
      test("joins multiple fields into one") {
        val rec      = DynamicValue.Record(Chunk("a" -> strV, "b" -> DynamicValue.Primitive(PrimitiveValue.String("world"))))
        val combiner = DynamicSchemaExpr.StringConcat(
          DynamicSchemaExpr.Path(DynamicOptic.root.field("_0")),
          DynamicSchemaExpr.Path(DynamicOptic.root.field("_1"))
        )
        val m = DynamicMigration(
          MigrationAction.Join(
            root.field("combined"),
            Vector(root.field("a"), root.field("b")),
            combiner,
            litS
          )
        )
        val result = m(rec)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.exists(_._1 == "combined"))
          case _ => assertTrue(false)
        }
      },
      test("join with missing source path fails") {
        val rec = DynamicValue.Record(Chunk("a" -> strV))
        val m   = DynamicMigration(
          MigrationAction.Join(
            root.field("combined"),
            Vector(root.field("a"), root.field("missing")),
            litS,
            litS
          )
        )
        assertTrue(m(rec).isLeft)
      },
      test("join on non-record fails") {
        val m = DynamicMigration(
          MigrationAction.Join(
            root.field("combined"),
            Vector(root.field("a")),
            litS,
            litS
          )
        )
        assertTrue(m(intV).isLeft)
      }
    ),
    suite("Split action")(
      test("splits field into multiple fields") {
        val rec      = DynamicValue.Record(Chunk("combined" -> strV))
        val splitter = DynamicSchemaExpr.Literal(DynamicValue.Sequence(Chunk(strV, strV)))
        val m        = DynamicMigration(
          MigrationAction.Split(
            root.field("combined"),
            Vector(root.field("a"), root.field("b")),
            splitter,
            litS
          )
        )
        val result = m(rec)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.exists(_._1 == "a") && fields.exists(_._1 == "b"))
          case _ => assertTrue(false)
        }
      },
      test("split wrong count fails") {
        val rec      = DynamicValue.Record(Chunk("combined" -> strV))
        val splitter = DynamicSchemaExpr.Literal(DynamicValue.Sequence(Chunk(strV)))
        val m        = DynamicMigration(
          MigrationAction.Split(
            root.field("combined"),
            Vector(root.field("a"), root.field("b")),
            splitter,
            litS
          )
        )
        assertTrue(m(rec).isLeft)
      },
      test("split non-sequence result fails") {
        val rec = DynamicValue.Record(Chunk("combined" -> strV))
        val m   = DynamicMigration(
          MigrationAction.Split(
            root.field("combined"),
            Vector(root.field("a")),
            litS,
            litS
          )
        )
        assertTrue(m(rec).isLeft)
      },
      test("split missing source field fails") {
        val rec = DynamicValue.Record(Chunk("other" -> strV))
        val m   = DynamicMigration(
          MigrationAction.Split(
            root.field("missing"),
            Vector(root.field("a")),
            litS,
            litS
          )
        )
        assertTrue(m(rec).isLeft)
      },
      test("split on non-record fails") {
        val m = DynamicMigration(
          MigrationAction.Split(
            root.field("x"),
            Vector(root.field("a")),
            litS,
            litS
          )
        )
        assertTrue(m(intV).isLeft)
      },
      test("split with invalid path fails") {
        val rec = DynamicValue.Record(Chunk("x" -> strV))
        val m   = DynamicMigration(
          MigrationAction.Split(
            DynamicOptic(Vector(DynamicOptic.Node.Elements)),
            Vector(root.field("a")),
            litS,
            litS
          )
        )
        assertTrue(m(rec).isLeft)
      }
    ),
    suite("multiple actions sequentially")(
      test("first action failure stops pipeline") {
        val m = DynamicMigration(
          Vector(
            MigrationAction.DropField(root, "missing", litI),
            MigrationAction.AddField(root, "x", litI)
          )
        )
        assertTrue(m(simpleRecord).isLeft)
      },
      test("all actions succeed sequentially") {
        val m = DynamicMigration(
          Vector(
            MigrationAction.RenameField(root, "name", "fullName"),
            MigrationAction.AddField(root, "email", litS)
          )
        )
        val result = m(simpleRecord)
        result match {
          case Right(DynamicValue.Record(fields)) =>
            assertTrue(fields.exists(_._1 == "fullName") && fields.exists(_._1 == "email"))
          case _ => assertTrue(false)
        }
      }
    ),
    suite("getDynamicValueTypeName coverage")(
      test("Primitive type name") {
        val m      = DynamicMigration(MigrationAction.AddField(root, "x", litI))
        val result = m(intV)
        assertTrue(result.isLeft && result.left.exists(_.message.contains("Primitive")))
      },
      test("Sequence type name") {
        val m      = DynamicMigration(MigrationAction.AddField(root, "x", litI))
        val result = m(DynamicValue.Sequence(Chunk.empty))
        assertTrue(result.isLeft && result.left.exists(_.message.contains("Sequence")))
      },
      test("Map type name") {
        val m      = DynamicMigration(MigrationAction.AddField(root, "x", litI))
        val result = m(DynamicValue.Map(Chunk.empty))
        assertTrue(result.isLeft && result.left.exists(_.message.contains("Map")))
      },
      test("Null type name") {
        val m      = DynamicMigration(MigrationAction.AddField(root, "x", litI))
        val result = m(DynamicValue.Null)
        assertTrue(result.isLeft && result.left.exists(_.message.contains("Null")))
      },
      test("Variant type name") {
        val m      = DynamicMigration(MigrationAction.AddField(root, "x", litI))
        val result = m(variant)
        assertTrue(result.isLeft && result.left.exists(_.message.contains("Variant")))
      }
    )
  )
}
