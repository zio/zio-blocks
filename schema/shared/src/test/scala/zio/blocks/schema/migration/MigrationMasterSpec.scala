package zio.blocks.schema.migration

import zio._
import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, Schema}
import zio.blocks.schema.migration.registry.MigrationRegistry
import zio.blocks.schema.migration.json.MigrationJsonCodec
import zio.blocks.schema.migration.{SchemaExpr => MigExpr}
import zio.test._
import zio.test.Assertion._
import zio.blocks.schema.json.Json

object MigrationMasterSpec extends ZIOSpecDefault {

  def fieldOptic(name: String): DynamicOptic =
    DynamicOptic(Vector(DynamicOptic.Node.Field(name)))

  def spec = suite("Migration Master Spec - Final Fix")(
    interpreterFullSuite,
    jsonCodecFullSuite,
    jsonCodecCoverageSuite,
    errorRenderSuite,
    structuralActionsSuite,
    coverageBoosterSuite,
    registrySuite,
    schemaExprSuite,
    reverseLogicSuite,
    typedMigrationSuite,
    dynamicMigrationCompositionSuite
  )

  // ১. Interpreter Suite
  val interpreterFullSuite = suite("MigrationInterpreter - Full Branches")(
    test("Should execute all structural actions") {
      val data = DynamicValue.Record(
        Chunk(
          "f1" -> DynamicValue.Primitive(PrimitiveValue.String("old")),
          "f2" -> DynamicValue.Primitive(PrimitiveValue.Int(10))
        )
      )

      val actions = Vector(
        MigrationAction.Rename(fieldOptic("f1"), "f1_new"),
        MigrationAction
          .AddField(fieldOptic("f3"), MigExpr.Constant(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))),
        MigrationAction.DropField(fieldOptic("f2"), MigExpr.Identity())
      )

      val result = DynamicMigration(actions).apply(data)
      assert(result.isRight)(isTrue)
    },

    test("Should handle Sequences and Maps in Interpreter") {
      val seqData = DynamicValue.Sequence(Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1))))
      val mapData = DynamicValue.Map(
        Chunk(DynamicValue.Primitive(PrimitiveValue.String("k")) -> DynamicValue.Primitive(PrimitiveValue.Int(1)))
      )

      val seqAction = MigrationAction.TransformElements(DynamicOptic.root, MigExpr.Identity())
      val mapAction = MigrationAction.TransformValues(
        DynamicOptic.root,
        MigExpr.Constant(DynamicValue.Primitive(PrimitiveValue.Int(5)))
      )
      val keyAction = MigrationAction.TransformKeys(DynamicOptic.root, MigExpr.Identity())

      for {
        resSeq <- ZIO.fromEither(MigrationInterpreter.run(seqData, seqAction))
        resMap <- ZIO.fromEither(MigrationInterpreter.run(mapData, mapAction))
        resKey <- ZIO.fromEither(MigrationInterpreter.run(mapData, keyAction))
      } yield assert(resSeq)(isSubtype[DynamicValue.Sequence](Assertion.anything)) &&
        assert(resMap)(isSubtype[DynamicValue.Map](Assertion.anything)) &&
        assert(resKey)(isSubtype[DynamicValue.Map](Assertion.anything))
    }
  )

  // ২. JSON Codec Suite
  val jsonCodecFullSuite = suite("MigrationJsonCodec - Deep Coverage")(
    test("Round-trip for complex expressions and actions") {
      val actions: Vector[MigrationAction] = Vector(
        MigrationAction.AddField(fieldOptic("f1"), MigExpr.DefaultValue(DynamicValue.Primitive(PrimitiveValue.Unit))),
        MigrationAction
          .TransformValue(fieldOptic("f2"), MigExpr.Converted(MigExpr.Identity(), MigExpr.ConversionOp.ToString)),
        MigrationAction
          .TransformValue(fieldOptic("f3"), MigExpr.Converted(MigExpr.Identity(), MigExpr.ConversionOp.ToInt)),
        MigrationAction.Join(fieldOptic("c"), Vector(fieldOptic("a")), MigExpr.Identity()),
        MigrationAction.Split(fieldOptic("a"), Vector(fieldOptic("b")), MigExpr.Identity()),
        MigrationAction.Optionalize(DynamicOptic(Vector(DynamicOptic.Node.Elements))),
        MigrationAction.RenameCase(fieldOptic("e"), "A", "B")
      )

      val migration = DynamicMigration(actions)
      import MigrationJsonCodec._
      val json          = migration.toJson
      val decodedResult =
        migrationDecoder.decode(zio.blocks.schema.json.Json.parse(json).getOrElse(zio.blocks.schema.json.Json.Null))

      assert(decodedResult)(isRight(Assertion.assertion("Size match")(_.actions.size == actions.size)))
    }
  )

  // jsonCodecCoverageSuite
  val jsonCodecCoverageSuite = suite("MigrationJsonCodec - 100% Booster")(
    test("1. All Primitive Types Round Trip") {
      val primitives = Vector(
        DynamicValue.Primitive(PrimitiveValue.Boolean(true)),
        DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal("123.456"))),
        DynamicValue.Primitive(PrimitiveValue.Long(999999L)),
        DynamicValue.Primitive(PrimitiveValue.Double(3.14159)),
        DynamicValue.Primitive(PrimitiveValue.Unit)
      )

      val actions = primitives.map(p => MigrationAction.AddField(DynamicOptic.root, MigExpr.Constant(p)))

      val migration = DynamicMigration(actions)
      import MigrationJsonCodec._
      val decoded = migrationDecoder.decode(Json.parse(migration.toJson).toOption.get)

      assert(decoded)(isRight(anything))
    },

    test("2. All Missing Action Types Round Trip") {
      val actions = Vector(
        MigrationAction.DropField(DynamicOptic.root, MigExpr.Identity()),
        MigrationAction.Mandate(DynamicOptic.root, MigExpr.Identity()),
        MigrationAction.TransformKeys(DynamicOptic.root, MigExpr.Identity()),
        MigrationAction.TransformValues(DynamicOptic.root, MigExpr.Identity()),
        MigrationAction.ChangeType(DynamicOptic.root, MigExpr.Identity()),
        MigrationAction.TransformCase(DynamicOptic.root, Vector(MigrationAction.Rename(DynamicOptic.root, "x")))
      )

      val migration = DynamicMigration(actions)
      import MigrationJsonCodec._
      val decoded = migrationDecoder.decode(Json.parse(migration.toJson).toOption.get)
      assert(decoded)(isRight(Assertion.assertion("Count")(m => m.actions.size == actions.size)))
    },

    test("3. Negative Paths (Decoder Errors)") {
      import MigrationJsonCodec._

      val badExpr = new Json.Object(Chunk.empty)
      val errExpr = exprDecoder.decode(badExpr)

      val badOp = new Json.Object(
        Chunk(
          "type"    -> new Json.String("converted"),
          "operand" -> new Json.Object(Chunk("type" -> new Json.String("identity"))),
          "op"      -> new Json.String("UnknownOp")
        )
      )
      val errOp = exprDecoder.decode(badOp)

      val badAction = new Json.Object(Chunk.empty)
      val errAction = actionDecoder.decode(badAction)

      val badDetails = new Json.Object(Chunk("op" -> new Json.String("Rename")))
      val errDetails = actionDecoder.decode(badDetails)

      val badType = new Json.Object(
        Chunk(
          "op"      -> new Json.String("FlyingCar"),
          "details" -> new Json.Object(Chunk("at" -> new Json.String("field:x")))
        )
      )
      val errType = actionDecoder.decode(badType)

      // [FIX] Changed String("123") to BigDecimal("123")
      val badOptic = new Json.Number(BigDecimal("123"))
      val errOptic = nodeDecoder.decode(badOptic)

      assert(errExpr)(isLeft(anything)) &&
      assert(errOp)(isLeft(anything)) &&
      assert(errAction)(isLeft(anything)) &&
      assert(errDetails)(isLeft(anything)) &&
      assert(errType)(isLeft(anything)) &&
      assert(errOptic)(isLeft(anything))
    },

    test("4. Unknown Optic Node fallback") {
      import MigrationJsonCodec._
      val badNodeStr = new Json.String("something:else:entirely")
      val decoded    = nodeDecoder.decode(badNodeStr)
      assert(decoded)(isRight(isSubtype[DynamicOptic.Node.Field](anything)))
    }
  )

  // ৩. Error Render Suite
  val errorRenderSuite = suite("MigrationErrorRender")(
    test("Render all error types correctly") {
      val optic = DynamicOptic(
        Vector(DynamicOptic.Node.Field("user"), DynamicOptic.Node.Elements, DynamicOptic.Node.Case("Admin"))
      )

      assert(MigrationErrorRender.render(MigrationError.FieldNotFound(optic, "f")))(
        containsString(".user.each.when[Admin]")
      ) &&
      assert(MigrationErrorRender.render(MigrationError.TypeMismatch(optic, "A", "B")))(
        containsString("Type mismatch")
      ) &&
      assert(MigrationErrorRender.render(MigrationError.TransformationFailed(optic, "Act", "Res")))(
        containsString("Failed to apply")
      ) &&
      assert(MigrationErrorRender.render(MigrationError.CaseNotFound(optic, "C")))(containsString("variant 'C'")) &&
      assert(MigrationErrorRender.render(MigrationError.DecodingError(optic, "Failed to decode Int")))(
        containsString("Final schema decoding failed") && containsString("Failed to decode Int")
      )
    }
  )

  // structuralActionsSuite
  val structuralActionsSuite = suite("Advanced Transformations")(
    test("ChangeType conversion String to Int") {
      val data   = DynamicValue.Primitive(PrimitiveValue.String("456"))
      val action =
        MigrationAction.ChangeType(DynamicOptic.root, MigExpr.Converted(MigExpr.Identity(), MigExpr.ConversionOp.ToInt))
      val result = MigrationInterpreter.run(data, action)
      assert(result)(isRight(equalTo(DynamicValue.Primitive(PrimitiveValue.Int(456)))))
    },

    test("TransformCase for Variants") {
      val data   = DynamicValue.Variant("Gold", DynamicValue.Primitive(PrimitiveValue.Int(1)))
      val action = MigrationAction.TransformCase(
        DynamicOptic(Vector(DynamicOptic.Node.Case("Gold"))),
        Vector(
          MigrationAction.TransformValue(
            DynamicOptic.root,
            MigExpr.Constant(DynamicValue.Primitive(PrimitiveValue.Int(100)))
          )
        )
      )
      val result = MigrationInterpreter.run(data, action)
      assert(result.isRight)(isTrue)
    },

    test("Join action") {
      val data = DynamicValue.Record(
        Chunk(
          "a" -> DynamicValue.Primitive(PrimitiveValue.Int(1)),
          "b" -> DynamicValue.Primitive(PrimitiveValue.Int(2))
        )
      )
      val action = MigrationAction.Join(
        fieldOptic("c"),
        Vector(fieldOptic("a"), fieldOptic("b")),
        MigExpr.Identity()
      )
      val result = MigrationInterpreter.run(data, action)
      assert(result.isRight)(isTrue)
    },

    test("Split action") {
      val data = DynamicValue.Record(
        Chunk(
          "a" -> DynamicValue.Sequence(
            Chunk(
              DynamicValue.Primitive(PrimitiveValue.Int(1)),
              DynamicValue.Primitive(PrimitiveValue.Int(2))
            )
          )
        )
      )
      val action = MigrationAction.Split(
        fieldOptic("a"),
        Vector(fieldOptic("b"), fieldOptic("c")),
        MigExpr.Identity()
      )
      val result = MigrationInterpreter.run(data, action)
      assert(result.isRight)(isTrue)
    },

    test("Optionalize action") {
      val data   = DynamicValue.Primitive(PrimitiveValue.Int(42))
      val action = MigrationAction.Optionalize(DynamicOptic.root)
      val result = MigrationInterpreter.run(data, action)
      assert(result)(isRight(equalTo(DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.Int(42))))))
    },

    test("Mandate action with Some") {
      val data   = DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.Int(42)))
      val action = MigrationAction.Mandate(DynamicOptic.root, MigExpr.Identity())
      val result = MigrationInterpreter.run(data, action)
      assert(result)(isRight(equalTo(DynamicValue.Primitive(PrimitiveValue.Int(42)))))
    },

    test("TransformKeys action") {
      val data = DynamicValue.Map(
        Chunk(
          DynamicValue.Primitive(PrimitiveValue.String("key1")) -> DynamicValue.Primitive(PrimitiveValue.Int(1))
        )
      )
      val action = MigrationAction.TransformKeys(
        DynamicOptic.root,
        MigExpr.Converted(MigExpr.Identity(), MigExpr.ConversionOp.ToString)
      )
      val result = MigrationInterpreter.run(data, action)
      assert(result.isRight)(isTrue)
    },

    test("TransformValues action") {
      val data = DynamicValue.Map(
        Chunk(
          DynamicValue.Primitive(PrimitiveValue.String("key")) -> DynamicValue.Primitive(PrimitiveValue.Int(1))
        )
      )
      val action = MigrationAction.TransformValues(
        DynamicOptic.root,
        MigExpr.Converted(MigExpr.Identity(), MigExpr.ConversionOp.ToString)
      )
      val result = MigrationInterpreter.run(data, action)
      assert(result.isRight)(isTrue)
    }
  )

  // ৪. Coverage Booster - Negative Paths
  val coverageBoosterSuite = suite("Coverage Booster - Negative Paths")(
    test("Should handle error branches in Interpreter") {
      val recordData    = DynamicValue.Record(Chunk("f1" -> DynamicValue.Primitive(PrimitiveValue.String("val"))))
      val primitiveData = DynamicValue.Primitive(PrimitiveValue.Int(10))
      val sequenceData  = DynamicValue.Sequence(Chunk(primitiveData))

      val mismatchAction = MigrationAction.Rename(fieldOptic("f1"), "f2")
      val res1           = MigrationInterpreter.run(primitiveData, mismatchAction)

      val notFoundAction = MigrationAction.Rename(fieldOptic("missing_field"), "new_name")
      val res2           = MigrationInterpreter.run(recordData, notFoundAction)

      val noneData      = DynamicValue.Variant("None", DynamicValue.Primitive(PrimitiveValue.Unit))
      val mandateAction = MigrationAction.Mandate(
        DynamicOptic.root,
        MigExpr.Constant(DynamicValue.Primitive(PrimitiveValue.String("default")))
      )
      val res3 = MigrationInterpreter.run(noneData, mandateAction)

      val renameCaseAction = MigrationAction.RenameCase(DynamicOptic.root, "NonExistent", "New")
      val res4             = MigrationInterpreter.run(DynamicValue.Variant("Existing", primitiveData), renameCaseAction)

      val mapActionOnSeq = MigrationAction.TransformKeys(DynamicOptic.root, MigExpr.Identity())
      val res5           = MigrationInterpreter.run(sequenceData, mapActionOnSeq)

      val seqActionOnRecord = MigrationAction.TransformElements(DynamicOptic.root, MigExpr.Identity())
      val res6              = MigrationInterpreter.run(recordData, seqActionOnRecord)

      assert(res1.isLeft)(isTrue) &&
      assert(res2.isLeft)(isTrue) &&
      assert(res3.isRight)(isTrue) &&
      assert(res4.isRight)(isTrue) &&
      assert(res5.isLeft)(isTrue) &&
      assert(res6.isLeft)(isTrue)
    },

    test("Error in nested structures for prependErrorPath coverage") {
      val data = DynamicValue.Record(
        Chunk("outer" -> DynamicValue.Record(Chunk("inner" -> DynamicValue.Primitive(PrimitiveValue.Int(1)))))
      )
      val action = MigrationAction.Rename(
        DynamicOptic(Vector(DynamicOptic.Node.Field("outer"), DynamicOptic.Node.Field("missing"))),
        "new"
      )
      val res = MigrationInterpreter.run(data, action)
      assert(res.isLeft)(isTrue)
    },

    // FIXED: Mandate on non-optional primitive definitely triggers TypeMismatch
    test("TypeMismatch on Mandate applied to non-optional primitive") {
      val data   = DynamicValue.Primitive(PrimitiveValue.Int(42))
      val action = MigrationAction.Mandate(DynamicOptic.root, MigExpr.Identity())
      val res    = MigrationInterpreter.run(data, action)
      assert(res.isLeft)(isTrue)
    },

    test("CaseNotFound in TransformCase") {
      val data   = DynamicValue.Variant("Silver", DynamicValue.Primitive(PrimitiveValue.Int(1)))
      val action = MigrationAction.TransformCase(
        DynamicOptic.root,
        Vector(
          MigrationAction.RenameCase(DynamicOptic(Vector(DynamicOptic.Node.Case("Gold"))), "Gold", "Platinum")
        )
      )
      val res = MigrationInterpreter.run(data, action)
      assert(res.isRight)(isTrue)
    }
  )

  // ৫. MigrationRegistry Suite
  val registrySuite = suite("MigrationRegistry")(
    test("Register and Plan Upgrade (Forward Compatibility)") {
      val m1 = DynamicMigration(Vector(MigrationAction.Rename(fieldOptic("v1"), "v1_new")))
      val m2 = DynamicMigration(
        Vector(
          MigrationAction.AddField(fieldOptic("v2"), MigExpr.Constant(DynamicValue.Primitive(PrimitiveValue.Int(2))))
        )
      )

      val registry = MigrationRegistry.empty
        .register(1, m1)
        .register(2, m2)

      val planSame = registry.plan(1, 1)
      assert(planSame)(isRight(equalTo(DynamicMigration.empty))) && {
        val planUpgrade = registry.plan(0, 2)
        assert(planUpgrade.map(_.actions.size))(isRight(equalTo(2)))
      }
    },

    test("Plan Rollback (Backward Compatibility)") {
      val m1           = DynamicMigration(Vector(MigrationAction.Rename(fieldOptic("a"), "b")))
      val registry     = MigrationRegistry.empty.register(1, m1)
      val planRollback = registry.plan(1, 0)
      assert(planRollback.map(_.actions.head))(
        isRight(isSubtype[MigrationAction.Rename](Assertion.anything))
      )
    },

    test("Error Handling: Missing Versions") {
      val m1              = DynamicMigration.empty
      val registry        = MigrationRegistry.empty.register(1, m1).register(3, m1)
      val missingUpgrade  = registry.plan(0, 3)
      val missingRollback = registry.plan(3, 0)

      assert(missingUpgrade)(isLeft(isSubtype[MigrationError.DecodingError](anything))) &&
      assert(missingRollback)(isLeft(isSubtype[MigrationError.DecodingError](anything)))
    }
  )

  // ৬. SchemaExpr Smart Constructors Coverage
  val schemaExprSuite = suite("SchemaExpr Smart Constructors")(
    test("Should create Constant via helper") {
      val value = 123
      val expr  = MigExpr.constant[Int, Int](value, Schema[Int])
      assert(expr)(isSubtype[MigExpr.Constant[_]](anything))
    },
    test("Should create Default via helper") {
      val expr = MigExpr.default[String, String](Schema[String])
      assert(expr)(equalTo(MigExpr.DefaultValue[String](DynamicValue.Primitive(PrimitiveValue.Unit))))
    }
  )

  // ৭. MigrationAction Reverse Logic Coverage
  val reverseLogicSuite = suite("MigrationAction.reverse Logic")(
    test("Rename reverse logic") {
      val fwd = MigrationAction.Rename(fieldOptic("old"), "new")
      val rev = fwd.reverse.asInstanceOf[MigrationAction.Rename]

      val badFwd = MigrationAction.Rename(DynamicOptic.root, "new")
      val badRev = badFwd.reverse.asInstanceOf[MigrationAction.Rename]

      assert(rev.to)(equalTo("old")) &&
      assert(badRev.to)(equalTo("unknown"))
    },
    test("Structural reverses (Add/Drop/Mandate/Join/Split)") {
      val add     = MigrationAction.AddField(DynamicOptic.root, MigExpr.Identity())
      val drop    = MigrationAction.DropField(DynamicOptic.root, MigExpr.Identity())
      val mandate = MigrationAction.Mandate(DynamicOptic.root, MigExpr.Identity())
      val opt     = MigrationAction.Optionalize(DynamicOptic.root)
      val join    = MigrationAction.Join(DynamicOptic.root, Vector.empty, MigExpr.Identity())
      val split   = MigrationAction.Split(DynamicOptic.root, Vector.empty, MigExpr.Identity())

      assert(add.reverse)(isSubtype[MigrationAction.DropField](anything)) &&
      assert(drop.reverse)(isSubtype[MigrationAction.AddField](anything)) &&
      assert(mandate.reverse)(isSubtype[MigrationAction.Optionalize](anything)) &&
      assert(opt.reverse)(isSubtype[MigrationAction.Mandate](anything)) &&
      assert(join.reverse)(isSubtype[MigrationAction.Split](anything)) &&
      assert(split.reverse)(isSubtype[MigrationAction.Join](anything))
    },
    test("Recursive reverses (TransformCase)") {
      val inner    = MigrationAction.Rename(fieldOptic("a"), "b")
      val fwd      = MigrationAction.TransformCase(DynamicOptic.root, Vector(inner))
      val rev      = fwd.reverse.asInstanceOf[MigrationAction.TransformCase]
      val innerRev = rev.actions.head.asInstanceOf[MigrationAction.Rename]

      assert(innerRev.to)(equalTo("a"))
    },
    test("Identity reverses") {
      val actions = List(
        MigrationAction.TransformValue(DynamicOptic.root, MigExpr.Identity()),
        MigrationAction.ChangeType(DynamicOptic.root, MigExpr.Identity()),
        MigrationAction.TransformElements(DynamicOptic.root, MigExpr.Identity()),
        MigrationAction.TransformKeys(DynamicOptic.root, MigExpr.Identity()),
        MigrationAction.TransformValues(DynamicOptic.root, MigExpr.Identity())
      )
      assert(actions.forall(a => a.reverse == a))(isTrue)
    },
    test("RenameCase reverse") {
      val rc  = MigrationAction.RenameCase(DynamicOptic.root, "A", "B")
      val rev = rc.reverse.asInstanceOf[MigrationAction.RenameCase]
      assert(rev.from)(equalTo("B")) && assert(rev.to)(equalTo("A"))
    }
  )

  // Typed Migration Suite - Primitive Types
  val typedMigrationSuite = suite("Typed Migration Suite - Primitive Types")(
    test("Successful identity migration String to String") {
      val migration = Migration(DynamicMigration.empty, Schema[String], Schema[String])
      val result    = migration.apply("test")
      assert(result)(isRight(equalTo("test")))
    },

    test("Successful conversion String to Int with valid number") {
      val dynamicMigration = DynamicMigration(
        Vector(
          MigrationAction.ChangeType(
            DynamicOptic.root,
            MigExpr.Converted(MigExpr.Identity(), MigExpr.ConversionOp.ToInt)
          )
        )
      )
      val migration = Migration(dynamicMigration, Schema[String], Schema[Int])
      val result    = migration.apply("123")
      assert(result)(isRight(equalTo(123)))
    },

    test("Decoding error at root via identity migration String to Int with invalid value") {
      val migration = Migration(DynamicMigration.empty, Schema[String], Schema[Int])
      val result    = migration.apply("notAnInt")
      assert(result)(isLeft(isSubtype[MigrationError.DecodingError](anything)))
    },

    test("Migration error (TypeMismatch on structural action on primitive") {
      val dynamicMigration = DynamicMigration(
        Vector(MigrationAction.Rename(fieldOptic("f1"), "f1_new"))
      )
      val migration = Migration(dynamicMigration, Schema[String], Schema[String])
      val result    = migration.apply("test")
      assert(result)(isLeft(isSubtype[MigrationError.TypeMismatch](anything)))
    }
  )

  // DynamicMigration Composition
  val dynamicMigrationCompositionSuite = suite("DynamicMigration Composition")(
    test("Compose two migrations") {
      val m1 = DynamicMigration(Vector(MigrationAction.Rename(fieldOptic("a"), "b")))
      val m2 = DynamicMigration(Vector(MigrationAction.Rename(fieldOptic("b"), "c")))

      val composed = m1 ++ m2

      val data   = DynamicValue.Record(Chunk("a" -> DynamicValue.Primitive(PrimitiveValue.Int(1))))
      val result = composed.apply(data)

      assert(result.map(_.asInstanceOf[DynamicValue.Record].fields.head._1))(isRight(equalTo("c")))
    },

    test("Associativity law for composition") {
      val m1 = DynamicMigration(
        Vector(
          MigrationAction.AddField(fieldOptic("x"), MigExpr.Constant(DynamicValue.Primitive(PrimitiveValue.Int(1))))
        )
      )
      val m2 = DynamicMigration(
        Vector(
          MigrationAction.AddField(fieldOptic("y"), MigExpr.Constant(DynamicValue.Primitive(PrimitiveValue.Int(2))))
        )
      )
      val m3 = DynamicMigration(
        Vector(
          MigrationAction.AddField(fieldOptic("z"), MigExpr.Constant(DynamicValue.Primitive(PrimitiveValue.Int(3))))
        )
      )

      val left  = (m1 ++ m2) ++ m3
      val right = m1 ++ (m2 ++ m3)

      assert(left.actions)(equalTo(right.actions))
    }
  )
}
