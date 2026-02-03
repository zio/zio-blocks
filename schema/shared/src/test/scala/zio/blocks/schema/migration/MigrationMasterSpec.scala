package zio.blocks.schema.migration

import zio._
import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, Schema}
import zio.blocks.schema.migration.registry.MigrationRegistry
import zio.blocks.schema.migration.json.MigrationJsonCodec
import zio.blocks.schema.migration.{SchemaExpr => MigExpr}
import zio.test._
import zio.test.Assertion._

object MigrationMasterSpec extends ZIOSpecDefault {

  def fieldOptic(name: String): DynamicOptic =
    DynamicOptic(Vector(DynamicOptic.Node.Field(name)))

  def spec = suite("Migration Master Spec - Final Fix")(
    interpreterFullSuite,
    jsonCodecFullSuite,
    errorRenderSuite,
    structuralActionsSuite,
    coverageBoosterSuite,
    registrySuite,
    schemaExprSuite,   
    reverseLogicSuite  
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
        MigrationAction.AddField(fieldOptic("f3"), MigExpr.Constant(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))),
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
        MigrationAction.TransformValue(fieldOptic("f2"), MigExpr.Converted(MigExpr.Identity(), MigExpr.ConversionOp.ToString)),
        MigrationAction.TransformValue(fieldOptic("f3"), MigExpr.Converted(MigExpr.Identity(), MigExpr.ConversionOp.ToInt)),
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
      assert(MigrationErrorRender.render(MigrationError.CaseNotFound(optic, "C")))(containsString("variant 'C'"))
    }
  )

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
    }
  )

  // ৪. Coverage Booster
  val coverageBoosterSuite = suite("Coverage Booster - Negative Paths")(
    test("Should handle error branches in Interpreter") {
      val recordData    = DynamicValue.Record(Chunk("f1" -> DynamicValue.Primitive(PrimitiveValue.String("val"))))
      val primitiveData = DynamicValue.Primitive(PrimitiveValue.Int(10))
      val sequenceData  = DynamicValue.Sequence(Chunk(primitiveData))

      // TypeMismatch
      val mismatchAction = MigrationAction.Rename(fieldOptic("f1"), "f2")
      val res1           = MigrationInterpreter.run(primitiveData, mismatchAction)

      // FieldNotFound
      val notFoundAction = MigrationAction.Rename(fieldOptic("missing_field"), "new_name")
      val res2           = MigrationInterpreter.run(recordData, notFoundAction)

      // Mandate with 'None'
      val noneData      = DynamicValue.Variant("None", DynamicValue.Primitive(PrimitiveValue.Unit))
      val mandateAction = MigrationAction.Mandate(
        DynamicOptic.root,
        MigExpr.Constant(DynamicValue.Primitive(PrimitiveValue.String("default")))
      )
      val res3 = MigrationInterpreter.run(noneData, mandateAction)

      // Identity branches
      val renameCaseAction = MigrationAction.RenameCase(DynamicOptic.root, "NonExistent", "New")
      val res4             = MigrationInterpreter.run(DynamicValue.Variant("Existing", primitiveData), renameCaseAction)

      // Collection Mismatch 1: Map action on Sequence
      val mapActionOnSeq = MigrationAction.TransformKeys(DynamicOptic.root, MigExpr.Identity())
      val res5           = MigrationInterpreter.run(sequenceData, mapActionOnSeq)

      // Collection Mismatch 2: Sequence action on Record
      val seqActionOnRecord = MigrationAction.TransformElements(DynamicOptic.root, MigExpr.Identity())
      val res6              = MigrationInterpreter.run(recordData, seqActionOnRecord)

      assert(res1.isLeft)(isTrue) &&
      assert(res2.isLeft)(isTrue) &&
      assert(res3.isRight)(isTrue) &&
      assert(res4.isRight)(isTrue) &&
      assert(res5.isLeft)(isTrue) &&
      assert(res6.isLeft)(isTrue)
    }
  )

  // ৫. MigrationRegistry Suite
  val registrySuite = suite("MigrationRegistry")(
    test("Register and Plan Upgrade (Forward Compatibility)") {
      val m1 = DynamicMigration(Vector(MigrationAction.Rename(fieldOptic("v1"), "v1_new")))
      val m2 = DynamicMigration(Vector(MigrationAction.AddField(fieldOptic("v2"), MigExpr.Constant(DynamicValue.Primitive(PrimitiveValue.Int(2))))))
      
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
      val m1 = DynamicMigration(Vector(MigrationAction.Rename(fieldOptic("a"), "b")))
      val registry = MigrationRegistry.empty.register(1, m1)
      val planRollback = registry.plan(1, 0)
      assert(planRollback.map(_.actions.head))(
        isRight(isSubtype[MigrationAction.Rename](Assertion.anything))
      )
    },

    test("Error Handling: Missing Versions") {
      val m1 = DynamicMigration.empty
      val registry = MigrationRegistry.empty.register(1, m1).register(3, m1)
      val missingUpgrade = registry.plan(0, 3)
      val missingRollback = registry.plan(3, 0)

      assert(missingUpgrade)(isLeft(isSubtype[MigrationError.DecodingError](Assertion.anything))) &&
      assert(missingRollback)(isLeft(isSubtype[MigrationError.DecodingError](Assertion.anything)))
    }
  )

  // ৬. SchemaExpr Smart Constructors Coverage (FIXED: টাইপ প্যারামিটার যোগ করা হয়েছে)
  val schemaExprSuite = suite("SchemaExpr Smart Constructors")(
    test("Should create Constant via helper") {
      val value = 123
      // এখানে [Int, Int] টাইপ প্যারামিটার দেওয়া হয়েছে
      val expr = MigExpr.constant[Int, Int](value, Schema[Int])
      assert(expr)(isSubtype[MigExpr.Constant[_]](Assertion.anything))
    },
    test("Should create Default via helper") {
      // এখানে [String, String] টাইপ প্যারামিটার দেওয়া হয়েছে যেন Nothing ইনফার না হয়
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
      val add = MigrationAction.AddField(DynamicOptic.root, MigExpr.Identity())
      val drop = MigrationAction.DropField(DynamicOptic.root, MigExpr.Identity())
      val mandate = MigrationAction.Mandate(DynamicOptic.root, MigExpr.Identity())
      val opt = MigrationAction.Optionalize(DynamicOptic.root)
      val join = MigrationAction.Join(DynamicOptic.root, Vector.empty, MigExpr.Identity())
      val split = MigrationAction.Split(DynamicOptic.root, Vector.empty, MigExpr.Identity())

      assert(add.reverse)(isSubtype[MigrationAction.DropField](anything)) &&
      assert(drop.reverse)(isSubtype[MigrationAction.AddField](anything)) &&
      assert(mandate.reverse)(isSubtype[MigrationAction.Optionalize](anything)) &&
      assert(opt.reverse)(isSubtype[MigrationAction.Mandate](anything)) &&
      assert(join.reverse)(isSubtype[MigrationAction.Split](anything)) &&
      assert(split.reverse)(isSubtype[MigrationAction.Join](anything))
    },
    test("Recursive reverses (TransformCase)") {
      val inner = MigrationAction.Rename(fieldOptic("a"), "b")
      val fwd = MigrationAction.TransformCase(DynamicOptic.root, Vector(inner))
      val rev = fwd.reverse.asInstanceOf[MigrationAction.TransformCase]
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
       val rc = MigrationAction.RenameCase(DynamicOptic.root, "A", "B")
       val rev = rc.reverse.asInstanceOf[MigrationAction.RenameCase]
       assert(rev.from)(equalTo("B")) && assert(rev.to)(equalTo("A"))
    }
  )
}