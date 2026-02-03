package zio.blocks.schema.migration

import zio._
import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue}
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
    coverageBoosterSuite // কভারেজ ৮০% করার জন্য নতুন যুক্ত করা হয়েছে
  )

  // ১. Interpreter
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

  // ২. JSON Codec
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

  // ৩. Error Render
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

  // ৪. Coverage Booster: এই অংশটি আপনার ব্রাঞ্চ কভারেজ ৮০% এর উপরে নিয়ে যাবে।
  val coverageBoosterSuite = suite("Coverage Booster - Negative Paths")(
    test("Should handle error branches in Interpreter") {
      val recordData    = DynamicValue.Record(Chunk("f1" -> DynamicValue.Primitive(PrimitiveValue.String("val"))))
      val primitiveData = DynamicValue.Primitive(PrimitiveValue.Int(10))

      // TypeMismatch: রেকর্ড অ্যাকশন প্রিমিটিভ ডাটার ওপর চালানো
      val mismatchAction = MigrationAction.Rename(fieldOptic("f1"), "f2")
      val res1           = MigrationInterpreter.run(primitiveData, mismatchAction)

      // FieldNotFound: এমন ফিল্ড রিনেম করা যা রেকর্ডে নেই
      val notFoundAction = MigrationAction.Rename(fieldOptic("missing_field"), "new_name")
      val res2           = MigrationInterpreter.run(recordData, notFoundAction)

      // Mandate with 'None' variant: ডিফল্ট ভ্যালু ব্যবহারের ব্রাঞ্চ চেক করবে
      val noneData      = DynamicValue.Variant("None", DynamicValue.Primitive(PrimitiveValue.Unit))
      val mandateAction = MigrationAction.Mandate(
        DynamicOptic.root,
        MigExpr.Constant(DynamicValue.Primitive(PrimitiveValue.String("default")))
      )
      val res3 = MigrationInterpreter.run(noneData, mandateAction)

      // Identity branches: যখন রিনেম কেস বা ট্রান্সফর্ম কেস ম্যাচ করে না
      val renameCaseAction = MigrationAction.RenameCase(DynamicOptic.root, "NonExistent", "New")
      val res4             = MigrationInterpreter.run(DynamicValue.Variant("Existing", primitiveData), renameCaseAction)

      val transformCaseAction = MigrationAction.TransformCase(
        DynamicOptic(Vector(DynamicOptic.Node.Case("NonExistent"))),
        Vector.empty
      )
      val res5 = MigrationInterpreter.run(recordData, transformCaseAction)

      assert(res1.isLeft)(isTrue) &&
      assert(res2.isLeft)(isTrue) &&
      assert(res3.isRight)(isTrue) &&
      assert(res4.isRight)(isTrue) &&
      assert(res5.isRight)(isTrue)
    }
  )
}
