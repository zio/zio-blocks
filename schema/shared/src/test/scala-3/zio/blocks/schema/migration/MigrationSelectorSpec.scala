package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.blocks.schema.migration.MigrationMacros._
import zio.test._
import zio.test.Assertion._

object MigrationSelectorSpec extends SchemaBaseSpec {

  // ─── Test domain types ──────────────────────────────────────────────────

  final case class PersonV1(name: String, age: Int)
  object PersonV1 {
    implicit val schema: Schema[PersonV1] = Schema.derived[PersonV1]
  }

  final case class PersonV2(fullName: String, age: Int, country: String)
  object PersonV2 {
    implicit val schema: Schema[PersonV2] = Schema.derived[PersonV2]
  }

  final case class SimpleRecord(x: Int, y: String)
  object SimpleRecord {
    implicit val schema: Schema[SimpleRecord] = Schema.derived[SimpleRecord]
  }

  final case class RenamedRecord(z: Int, y: String)
  object RenamedRecord {
    implicit val schema: Schema[RenamedRecord] = Schema.derived[RenamedRecord]
  }

  final case class ExtendedRecord(x: Int, y: String, extra: Boolean)
  object ExtendedRecord {
    implicit val schema: Schema[ExtendedRecord] = Schema.derived[ExtendedRecord]
  }

  final case class ShrunkRecord(y: String)
  object ShrunkRecord {
    implicit val schema: Schema[ShrunkRecord] = Schema.derived[ShrunkRecord]
  }

  final case class TypeChangedRecord(x: String, y: String)
  object TypeChangedRecord {
    implicit val schema: Schema[TypeChangedRecord] = Schema.derived[TypeChangedRecord]
  }

  // ─── Tests ──────────────────────────────────────────────────────────────

  def spec: Spec[TestEnvironment, Any] = suite("MigrationSelectorSpec")(
    renameViaSelector,
    addFieldViaSelector,
    dropFieldViaSelector,
    changeFieldTypeViaSelector,
    transformFieldViaSelector,
    mandateFieldViaSelector,
    optionalizeFieldViaSelector,
    combinedSelectorOperations,
    nestedSelectors
  )

  val renameViaSelector: Spec[Any, Any] = suite("RenameViaSelector")(
    test("rename field using _.field selectors") {
      val m = Migration
        .newBuilder[SimpleRecord, RenamedRecord]
        .renameField(_.x, _.z)
        .buildPartial
      val result = m(SimpleRecord(42, "hello"))
      assert(result)(isRight(equalTo(RenamedRecord(42, "hello"))))
    },
    test("rename with build validation succeeds") {
      val m = Migration
        .newBuilder[SimpleRecord, RenamedRecord]
        .renameField(_.x, _.z)
        .build
      assertTrue(!m.isEmpty)
    }
  )

  val addFieldViaSelector: Spec[Any, Any] = suite("AddFieldViaSelector")(
    test("add field using _.field selector") {
      val m = Migration
        .newBuilder[SimpleRecord, ExtendedRecord]
        .addField(_.extra, false)
        .buildPartial
      val result = m(SimpleRecord(42, "hello"))
      assert(result)(isRight(equalTo(ExtendedRecord(42, "hello", false))))
    }
  )

  val dropFieldViaSelector: Spec[Any, Any] = suite("DropFieldViaSelector")(
    test("drop field using _.field selector") {
      val m = Migration
        .newBuilder[SimpleRecord, ShrunkRecord]
        .dropField(_.x)
        .buildPartial
      val result = m(SimpleRecord(42, "hello"))
      assert(result)(isRight(equalTo(ShrunkRecord("hello"))))
    }
  )

  val changeFieldTypeViaSelector: Spec[Any, Any] = suite("ChangeFieldTypeViaSelector")(
    test("change field type using selectors") {
      val m = Migration
        .newBuilder[SimpleRecord, TypeChangedRecord]
        .changeFieldType(_.x, _.x, DynamicSchemaExpr.IntToString)
        .buildPartial
      val result = m(SimpleRecord(42, "hello"))
      assert(result)(isRight(equalTo(TypeChangedRecord("42", "hello"))))
    }
  )

  val transformFieldViaSelector: Spec[Any, Any] = suite("TransformFieldViaSelector")(
    test("transform field value using selectors") {
      val m = Migration
        .newBuilder[SimpleRecord, TypeChangedRecord]
        .transformField(_.x, _.x, DynamicSchemaExpr.IntToString)
        .buildPartial
      val result = m(SimpleRecord(42, "hello"))
      assert(result)(isRight(equalTo(TypeChangedRecord("42", "hello"))))
    }
  )

  val mandateFieldViaSelector: Spec[Any, Any] = suite("MandateFieldViaSelector")(
    test("mandate field using selectors") {
      val dm = Migration
        .newBuilder[SimpleRecord, SimpleRecord]
        .mandateField(_.x, _.x, 0)
        .buildPartial
        .dynamicMigration
      val input = DynamicValue.Record("x" -> DynamicValue.int(42), "y" -> DynamicValue.string("hello"))
      assertTrue(dm(input).isRight)
    }
  )

  val optionalizeFieldViaSelector: Spec[Any, Any] = suite("OptionalizeFieldViaSelector")(
    test("optionalize field using selectors") {
      val dm = Migration
        .newBuilder[SimpleRecord, SimpleRecord]
        .optionalizeField(_.x, _.x)
        .buildPartial
        .dynamicMigration
      val input = DynamicValue.Record("x" -> DynamicValue.int(42), "y" -> DynamicValue.string("hello"))
      assertTrue(dm(input).isRight)
    }
  )

  val combinedSelectorOperations: Spec[Any, Any] = suite("CombinedSelectorOperations")(
    test("combined rename + add using selectors") {
      val m = Migration
        .newBuilder[PersonV1, PersonV2]
        .renameField(_.name, _.fullName)
        .addField(_.country, "US")
        .build
      val result = m(PersonV1("Alice", 30))
      assert(result)(isRight(equalTo(PersonV2("Alice", 30, "US"))))
    },
    test("selector rename + string add produce the same migration actions") {
      val m1 = Migration
        .newBuilder[PersonV1, PersonV2]
        .renameField(_.name, _.fullName)
        .addField(_.country, "US")
        .buildPartial
        .dynamicMigration
      val m2 = Migration
        .newBuilder[PersonV1, PersonV2]
        .renameField("name", "fullName")
        .addField("country", "US")
        .buildPartial
        .dynamicMigration
      assertTrue(m1.actions == m2.actions)
    }
  )

  val nestedSelectors: Spec[Any, Any] = suite("NestedSelectors")(
    test("selectorToDynamicOptic extracts single field path") {
      val optic = MigrationMacros.selectorToDynamicOptic[PersonV2](_.fullName)
      assertTrue(optic.nodes.length == 1)
    }
  )
}
