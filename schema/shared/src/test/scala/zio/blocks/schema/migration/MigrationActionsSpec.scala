package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._
import zio.blocks.schema.migration.MigrationAction._
import scala.annotation.unused
import zio.blocks.schema.migration.SchemaExpr

object MigrationActionsSpec extends ZIOSpecDefault {

  // =================================================================================
  // 1. TEST MODELS
  // =================================================================================

  sealed trait ColorV1; case class RedV1()     extends ColorV1; case class GreenV1(intensity: Int) extends ColorV1
  sealed trait ColorV2; case class ScarletV2() extends ColorV2; case class GreenV2(intensity: Int) extends ColorV2

  case class Source(
    renamedField: String,
    deletedField: Int,
    transformVal: Int,
    typeChanged: Int,
    optionalToMandate: Option[String],
    mandateToOptional: String,
    tags: Vector[String],
    metadata: Map[String, Int],
    config: Map[Int, String],
    status: ColorV1
  )

  case class Target(
    newName: String,
    addedField: Boolean,
    transformVal: Int,
    typeChanged: String,
    optionalToMandate: String,
    mandateToOptional: Option[String],
    tags: Vector[String],
    metadata: Map[String, String],
    config: Map[String, String],
    status: ColorV2
  )

  @unused implicit val sSrc: Schema[Source]    = null.asInstanceOf[Schema[Source]]
  @unused implicit val sTgt: Schema[Target]    = null.asInstanceOf[Schema[Target]]
  @unused implicit val sCv1: Schema[ColorV1]   = null.asInstanceOf[Schema[ColorV1]]
  @unused implicit val sCv2: Schema[ColorV2]   = null.asInstanceOf[Schema[ColorV2]]
  @unused implicit val sRed: Schema[RedV1]     = null.asInstanceOf[Schema[RedV1]]
  @unused implicit val sSca: Schema[ScarletV2] = null.asInstanceOf[Schema[ScarletV2]]
  @unused implicit val sGr1: Schema[GreenV1]   = null.asInstanceOf[Schema[GreenV1]]
  @unused implicit val sGr2: Schema[GreenV2]   = null.asInstanceOf[Schema[GreenV2]]

  def expr: SchemaExpr[_] = null.asInstanceOf[SchemaExpr[_]]

  // =================================================================================
  // 2. MANUAL BYPASS HELPER (Cross-Compatible: Scala 2 & 3)
  // =================================================================================

  // [FIX] Using 'implicit class' instead of 'extension'.
  // This works in BOTH Scala 2.13 and Scala 3.
  implicit class MigrationBuilderOps[A, B, S <: MigrationState.State](val mb: MigrationBuilder[A, B, S])
      extends AnyVal {
    def transformCaseManual[CaseA, CaseB](
      selectorName: String,
      f: MigrationBuilder[CaseA, CaseB, MigrationState.Empty] => MigrationBuilder[CaseA, CaseB, ?]
    )(implicit
      sA: Schema[CaseA],
      sB: Schema[CaseB]
    ): MigrationBuilder[A, B, MigrationState.TransformCase[String, MigrationState.Empty, S]] = {

      val optic         = DynamicOptic(Vector(DynamicOptic.Node.Field(selectorName)))
      val nestedBuilder = f(MigrationBuilder.make[CaseA, CaseB])

      // Access dynamicMigration via buildPartial
      val nestedActions = nestedBuilder.buildPartial.dynamicMigration.actions

      mb.withState[MigrationState.TransformCase[String, MigrationState.Empty, S]](
        MigrationAction.TransformCase(optic, nestedActions)
      )
    }
  }

  // =================================================================================
  // 3. VERIFICATION SUITE
  // =================================================================================

  def spec = suite("Professional Verification: Migration Actions vs Builder")(
    suite("1. Record Actions (Core)")(
      test("addField -> Generates AddField") {
        val m = MigrationBuilder
          .make[Source, Target]
          .addField((t: Target) => t.addedField, expr)
          .buildPartial
        assertTrue(m.dynamicMigration.actions.exists {
          case AddField(at, _) => at.nodes.last == DynamicOptic.Node.Field("addedField")
          case _               => false
        })
      },
      test("dropField -> Generates DropField") {
        val m = MigrationBuilder
          .make[Source, Target]
          .dropField((s: Source) => s.deletedField, expr)
          .buildPartial
        assertTrue(m.dynamicMigration.actions.exists {
          case DropField(at, _) => at.nodes.last == DynamicOptic.Node.Field("deletedField")
          case _                => false
        })
      },
      test("renameField -> Generates Rename") {
        val m = MigrationBuilder
          .make[Source, Target]
          .renameField((s: Source) => s.renamedField, (t: Target) => t.newName)
          .buildPartial
        assertTrue(m.dynamicMigration.actions.exists {
          case Rename(at, to) => at.nodes.last == DynamicOptic.Node.Field("renamedField") && to == "newName"
          case _              => false
        })
      },
      test("transformField -> Generates TransformValue") {
        val m = MigrationBuilder
          .make[Source, Target]
          .transformField((s: Source) => s.transformVal, (t: Target) => t.transformVal, expr)
          .buildPartial
        assertTrue(m.dynamicMigration.actions.exists {
          case TransformValue(at, _) => at.nodes.last == DynamicOptic.Node.Field("transformVal")
          case _                     => false
        })
      },
      test("changeFieldType -> Generates ChangeType") {
        val m = MigrationBuilder
          .make[Source, Target]
          .changeFieldType((s: Source) => s.typeChanged, (t: Target) => t.typeChanged, expr)
          .buildPartial
        assertTrue(m.dynamicMigration.actions.exists {
          case ChangeType(at, _) => at.nodes.last == DynamicOptic.Node.Field("typeChanged")
          case _                 => false
        })
      }
    ),

    suite("2. Structural Constraint Actions")(
      test("mandateField -> Generates Mandate") {
        val m = MigrationBuilder
          .make[Source, Target]
          .mandateField((s: Source) => s.optionalToMandate, (t: Target) => t.optionalToMandate, expr)
          .buildPartial
        assertTrue(m.dynamicMigration.actions.exists {
          case Mandate(at, _) => at.nodes.last == DynamicOptic.Node.Field("optionalToMandate")
          case _              => false
        })
      },
      test("optionalizeField -> Generates Optionalize") {
        val m = MigrationBuilder
          .make[Source, Target]
          .optionalizeField((s: Source) => s.mandateToOptional, (t: Target) => t.mandateToOptional)
          .buildPartial
        assertTrue(m.dynamicMigration.actions.exists {
          case Optionalize(at) => at.nodes.last == DynamicOptic.Node.Field("mandateToOptional")
          case _               => false
        })
      }
    ),

    suite("3. Collection & Map Actions")(
      test("transformElements -> Generates TransformElements") {
        val m = MigrationBuilder
          .make[Source, Target]
          .transformElements((s: Source) => s.tags, expr)
          .buildPartial
        assertTrue(m.dynamicMigration.actions.exists {
          case TransformElements(at, _) => at.nodes.last == DynamicOptic.Node.Field("tags")
          case _                        => false
        })
      },
      test("transformValues -> Generates TransformValues") {
        val m = MigrationBuilder
          .make[Source, Target]
          .transformValues((s: Source) => s.metadata, expr)
          .buildPartial
        assertTrue(m.dynamicMigration.actions.exists {
          case TransformValues(at, _) => at.nodes.last == DynamicOptic.Node.Field("metadata")
          case _                      => false
        })
      },
      test("transformKeys -> Generates TransformKeys") {
        val m = MigrationBuilder
          .make[Source, Target]
          .transformKeys((s: Source) => s.config, expr)
          .buildPartial
        assertTrue(m.dynamicMigration.actions.exists {
          case TransformKeys(at, _) => at.nodes.last == DynamicOptic.Node.Field("config")
          case _                    => false
        })
      }
    ),

    suite("4. Enum Actions (Advanced)")(
      test("renameCase -> Generates RenameCase") {
        val m = MigrationBuilder
          .make[ColorV1, ColorV2]
          .renameCase("RedV1", "ScarletV2")
          .buildPartial
        assertTrue(m.dynamicMigration.actions.exists {
          case RenameCase(_, from, to) => from == "RedV1" && to == "ScarletV2"
          case _                       => false
        })
      },

      test("transformCase -> Generates TransformCase") {
        // Now works for both Scala 2 and 3 via implicit class
        val m = MigrationBuilder
          .make[ColorV1, ColorV2]
          .transformCaseManual[GreenV1, GreenV2](
            "GreenV1",
            (b: MigrationBuilder[GreenV1, GreenV2, MigrationState.Empty]) =>
              b.transformField((s: GreenV1) => s.intensity, (t: GreenV2) => t.intensity, expr)
          )
          .buildPartial

        assertTrue(m.dynamicMigration.actions.exists {
          case TransformCase(_, nestedActions) =>
            nestedActions.exists {
              case TransformValue(nestedAt, _) => nestedAt.nodes.last == DynamicOptic.Node.Field("intensity")
              case _                           => false
            }
          case _ => false
        })
      }
    )
  )
}
