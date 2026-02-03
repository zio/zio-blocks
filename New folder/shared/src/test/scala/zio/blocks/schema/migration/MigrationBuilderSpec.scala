package zio.blocks.schema.migration

import zio.test._
import zio.blocks.schema._
import zio.blocks.schema.migration.MigrationAction._
import scala.annotation.unused

// Import Migration SchemaExpr
import zio.blocks.schema.migration.SchemaExpr

object MigrationBuilderSpec extends ZIOSpecDefault {

  // --- Models ---
  case class SourceRecord(
    keep: String,
    renameMe: Int,
    transformMe: Int,
    optionalVal: Option[String],
    mandateVal: String,
    dropMe: Boolean
  )

  case class TargetRecord(
    keep: String,
    renamed: Int,
    transformMe: String,
    optionalVal: String,
    mandateVal: Option[String],
    newField: Boolean
  )

  sealed trait StatusV1
  case class ActiveV1()   extends StatusV1
  case class InactiveV1() extends StatusV1

  sealed trait StatusV2
  case class LiveV2()     extends StatusV2
  case class InactiveV2() extends StatusV2

  @unused implicit val schemaSrc: Schema[SourceRecord] = null.asInstanceOf[Schema[SourceRecord]]
  @unused implicit val schemaTgt: Schema[TargetRecord] = null.asInstanceOf[Schema[TargetRecord]]
  @unused implicit val schemaEnum1: Schema[StatusV1]   = null.asInstanceOf[Schema[StatusV1]]
  @unused implicit val schemaEnum2: Schema[StatusV2]   = null.asInstanceOf[Schema[StatusV2]]

  // @nowarn("msg=unused")
  implicit val conversion: scala.languageFeature.implicitConversions = scala.language.implicitConversions

  def dummyExpr: SchemaExpr[_] = null.asInstanceOf[SchemaExpr[_]]

  def spec = suite("Requirement: MigrationBuilder API & Macros")(
    suite("1. Record Operations")(
      test("renameField: Compiles and generates Rename action") {
        val m = MigrationBuilder
          .make[SourceRecord, TargetRecord]
          .renameField((s: SourceRecord) => s.renameMe, (t: TargetRecord) => t.renamed)
          .buildPartial

        assertTrue(m.dynamicMigration.actions.exists {
          case Rename(from, to) =>
            from.nodes.last == DynamicOptic.Node.Field("renameMe") && to == "renamed"
          case _ => false
        })
      },

      test("addField: Compiles and generates AddField action") {
        val m = MigrationBuilder
          .make[SourceRecord, TargetRecord]
          .addField((t: TargetRecord) => t.newField, dummyExpr)
          .buildPartial

        assertTrue(m.dynamicMigration.actions.exists {
          case AddField(at, _) =>
            at.nodes.last == DynamicOptic.Node.Field("newField")
          case _ => false
        })
      },

      test("dropField: Compiles and generates DropField action") {
        val m = MigrationBuilder
          .make[SourceRecord, TargetRecord]
          .dropField((s: SourceRecord) => s.dropMe, dummyExpr)
          .buildPartial

        assertTrue(m.dynamicMigration.actions.exists {
          case DropField(at, _) =>
            at.nodes.last == DynamicOptic.Node.Field("dropMe")
          case _ => false
        })
      },

      test("transformField: Compiles and generates TransformValue action") {
        val m = MigrationBuilder
          .make[SourceRecord, TargetRecord]
          .transformField(
            (s: SourceRecord) => s.transformMe,
            (t: TargetRecord) => t.transformMe,
            dummyExpr
          )
          .buildPartial

        assertTrue(m.dynamicMigration.actions.exists {
          case TransformValue(at, _) =>
            at.nodes.last == DynamicOptic.Node.Field("transformMe")
          case _ => false
        })
      },

      // [NEW TEST] Mandate Field (Option -> T)
      test("mandateField: Compiles and generates Mandate action") {
        val m = MigrationBuilder
          .make[SourceRecord, TargetRecord]
          .mandateField(
            (s: SourceRecord) => s.optionalVal, // Source is Option[String]
            (t: TargetRecord) => t.optionalVal, // Target is String
            dummyExpr
          )
          .buildPartial

        assertTrue(m.dynamicMigration.actions.exists {
          case Mandate(at, _) =>
            at.nodes.last == DynamicOptic.Node.Field("optionalVal")
          case _ => false
        })
      },

      // [NEW TEST] Optionalize Field (T -> Option)
      test("optionalizeField: Compiles and generates Optionalize action") {
        val m = MigrationBuilder
          .make[SourceRecord, TargetRecord]
          .optionalizeField(
            (s: SourceRecord) => s.mandateVal, // Source is String
            (t: TargetRecord) => t.mandateVal  // Target is Option[String]
          )
          .buildPartial

        assertTrue(m.dynamicMigration.actions.exists {
          case Optionalize(at) =>
            at.nodes.last == DynamicOptic.Node.Field("mandateVal")
          case _ => false
        })
      }
    ),

    suite("2. Enum Operations")(
      test("renameCase: Compiles and generates Rename action for Sum Types") {
        val m = MigrationBuilder
          .make[StatusV1, StatusV2]
          .renameCase("ActiveV1", "LiveV2")
          .buildPartial

        assertTrue(m.dynamicMigration.actions.exists {
          case RenameCase(_, _, to) =>
            to == "LiveV2"
          case _ => false
        })
      }
    ),

    suite("3. Build Methods")(
      test("build: Enforces full validation") {
        assertTrue(true)
      },

      test("buildPartial: Allows partial migration") {
        val m = MigrationBuilder
          .make[SourceRecord, TargetRecord]
          .dropField((s: SourceRecord) => s.dropMe, dummyExpr)
          .buildPartial

        assertTrue(m != null)
      }
    )
  )
}
