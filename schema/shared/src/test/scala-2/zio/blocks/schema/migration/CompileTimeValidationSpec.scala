package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._
import TypeLevel._

/**
 * Scala 2-specific CompileTimeValidationSpec tests.
 *
 * Tests the `syntax()` wrapper pattern which is specific to Scala 2's
 * `MigrationBuilderSyntax` class (wrapping a builder for lambda type
 * inference).
 *
 * All shared tests (direct method chaining, typeCheck, ShapeTree, etc.) are in
 * `src/test/scala/zio/blocks/schema/migration/CompileTimeValidationSpec.scala`.
 */
object CompileTimeValidationSpecScala2 extends ZIOSpecDefault {

  // Re-use case classes for syntax() wrapper tests
  case class DropSource(name: String, age: Int, extra: Boolean)
  object DropSource { implicit val schema: Schema[DropSource] = Schema.derived }

  case class DropTarget(name: String, age: Int)
  object DropTarget { implicit val schema: Schema[DropTarget] = Schema.derived }

  case class AddSource(name: String, age: Int)
  object AddSource { implicit val schema: Schema[AddSource] = Schema.derived }

  case class AddTarget(name: String, age: Int, extra: Boolean)
  object AddTarget { implicit val schema: Schema[AddTarget] = Schema.derived }

  case class RenameSource(oldName: String, age: Int)
  object RenameSource { implicit val schema: Schema[RenameSource] = Schema.derived }

  case class RenameTarget(newName: String, age: Int)
  object RenameTarget { implicit val schema: Schema[RenameTarget] = Schema.derived }

  case class PersonA(name: String, age: Int)
  object PersonA { implicit val schema: Schema[PersonA] = Schema.derived }

  case class PersonB(name: String, age: Int)
  object PersonB { implicit val schema: Schema[PersonB] = Schema.derived }

  case class ComplexSource(a: String, b: Int, c: Boolean, d: Double)
  object ComplexSource { implicit val schema: Schema[ComplexSource] = Schema.derived }

  case class ComplexTarget(x: String, b: Int, y: Boolean, e: Long)
  object ComplexTarget { implicit val schema: Schema[ComplexTarget] = Schema.derived }

  case class MultiDropSource(keep: String, drop1: Int, drop2: Boolean, drop3: Double)
  object MultiDropSource { implicit val schema: Schema[MultiDropSource] = Schema.derived }

  case class MultiDropTarget(keep: String)
  object MultiDropTarget { implicit val schema: Schema[MultiDropTarget] = Schema.derived }

  case class MultiAddSource(keep: String)
  object MultiAddSource { implicit val schema: Schema[MultiAddSource] = Schema.derived }

  case class MultiAddTarget(keep: String, add1: Int, add2: Boolean, add3: Double)
  object MultiAddTarget { implicit val schema: Schema[MultiAddTarget] = Schema.derived }

  case class DeepInnerV1(value: Int)
  object DeepInnerV1 { implicit val schema: Schema[DeepInnerV1] = Schema.derived }

  case class DeepMiddleV1(inner: DeepInnerV1)
  object DeepMiddleV1 { implicit val schema: Schema[DeepMiddleV1] = Schema.derived }

  case class DeepOuterV1(middle: DeepMiddleV1)
  object DeepOuterV1 { implicit val schema: Schema[DeepOuterV1] = Schema.derived }

  case class DeepInnerV2(value: Long)
  object DeepInnerV2 { implicit val schema: Schema[DeepInnerV2] = Schema.derived }

  case class DeepMiddleV2(inner: DeepInnerV2)
  object DeepMiddleV2 { implicit val schema: Schema[DeepMiddleV2] = Schema.derived }

  case class DeepOuterV2(middle: DeepMiddleV2)
  object DeepOuterV2 { implicit val schema: Schema[DeepOuterV2] = Schema.derived }

  case class AddressV1(street: String, city: String)
  object AddressV1 { implicit val schema: Schema[AddressV1] = Schema.derived }

  case class NestedPersonV1(name: String, address: AddressV1)
  object NestedPersonV1 { implicit val schema: Schema[NestedPersonV1] = Schema.derived }

  case class AddressV2(street: String, zip: String)
  object AddressV2 { implicit val schema: Schema[AddressV2] = Schema.derived }

  case class NestedPersonV2(name: String, address: AddressV2)
  object NestedPersonV2 { implicit val schema: Schema[NestedPersonV2] = Schema.derived }

  // Helpers
  def syntax[A, B, H <: TList, P <: TList](
    b: MigrationBuilder[A, B, H, P]
  ): MigrationBuilderSyntax[A, B, H, P] = new MigrationBuilderSyntax(b)

  override def spec = suite("CompileTimeValidationSpec - Scala 2 syntax() wrapper tests")(
    syntaxWrapperSuite
  )

  // Tests specific to Scala 2's syntax() wrapper pattern
  val syntaxWrapperSuite = suite("syntax stored in variable - build without re-wrap")(
    test("syntax stored in variable - dropField then build without re-wrap") {
      val ops      = syntax(MigrationBuilder.newBuilder[DropSource, DropTarget])
      val withDrop =
        ops.dropField(_.extra, DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(false))))
      val migration = withDrop.build

      val source = DropSource("test", 42, true)
      val result = migration(source)

      assertTrue(result.isRight) &&
      assertTrue(result.map(_.name) == Right("test")) &&
      assertTrue(result.map(_.age) == Right(42))
    },
    test("syntax stored in variable - addField then build without re-wrap") {
      val ops     = syntax(MigrationBuilder.newBuilder[AddSource, AddTarget])
      val withAdd =
        ops.addField(_.extra, DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      val migration = withAdd.build

      val source = AddSource("test", 42)
      val result = migration(source)

      assertTrue(result == Right(AddTarget("test", 42, true)))
    },
    test("syntax stored in variable - renameField then build without re-wrap") {
      val ops        = syntax(MigrationBuilder.newBuilder[RenameSource, RenameTarget])
      val withRename = ops.renameField(_.oldName, _.newName)
      val migration  = withRename.build

      val source = RenameSource("John", 30)
      val result = migration(source)

      assertTrue(result.isRight) &&
      assertTrue(result.map(_.newName) == Right("John"))
    },
    test("syntax stored in variable - transformField then build without re-wrap") {
      val ops           = syntax(MigrationBuilder.newBuilder[PersonA, PersonB])
      val withTransform =
        ops.transformField(
          _.name,
          DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("transformed")))
        )
      val migration = withTransform.build

      val source = PersonA("John", 30)
      val result = migration(source)

      assertTrue(result == Right(PersonB("transformed", 30)))
    },
    test("syntax stored in variable - changeFieldType then build without re-wrap") {
      val ops        = syntax(MigrationBuilder.newBuilder[DeepOuterV1, DeepOuterV2])
      val withChange = ops.changeFieldType(_.middle.inner.value, PrimitiveConverter.IntToLong)
      val migration  = withChange.build

      val source = DeepOuterV1(DeepMiddleV1(DeepInnerV1(42)))
      val result = migration(source)

      assertTrue(result == Right(DeepOuterV2(DeepMiddleV2(DeepInnerV2(42L)))))
    },
    test("chained operations with syntax wrappers - build without final re-wrap") {
      val step1 = syntax(MigrationBuilder.newBuilder[ComplexSource, ComplexTarget])
        .renameField(_.a, _.x)
      val step2 = syntax(step1).renameField(_.c, _.y)
      val step3 =
        syntax(step2).dropField(_.d, DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(0.0))))
      val step4 =
        syntax(step3).addField(_.e, DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Long(0L))))
      val migration = step4.build

      val source = ComplexSource("hello", 42, true, 3.14)
      val result = migration(source)

      assertTrue(result.isRight) &&
      assertTrue(result.map(_.x) == Right("hello")) &&
      assertTrue(result.map(_.b) == Right(42))
    },
    test("chained drops with syntax wrappers - build without final re-wrap") {
      val step1 = syntax(MigrationBuilder.newBuilder[MultiDropSource, MultiDropTarget])
        .dropField(_.drop1, DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(0))))
      val step2 = syntax(step1)
        .dropField(_.drop2, DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(false))))
      val step3 =
        syntax(step2).dropField(_.drop3, DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(0.0))))
      val migration = step3.build

      val source = MultiDropSource("keep", 1, true, 2.5)
      val result = migration(source)

      assertTrue(result.isRight) &&
      assertTrue(result.map(_.keep) == Right("keep"))
    },
    test("chained adds with syntax wrappers - build without final re-wrap") {
      val step1 = syntax(MigrationBuilder.newBuilder[MultiAddSource, MultiAddTarget])
        .addField(_.add1, DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Int(42))))
      val step2 =
        syntax(step1).addField(_.add2, DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(true))))
      val step3 =
        syntax(step2).addField(_.add3, DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.Double(3.14))))
      val migration = step3.build

      val source = MultiAddSource("keep")
      val result = migration(source)

      assertTrue(result == Right(MultiAddTarget("keep", 42, true, 3.14)))
    },
    test("nested path with syntax wrappers - build without final re-wrap") {
      val step1 = syntax(MigrationBuilder.newBuilder[NestedPersonV1, NestedPersonV2])
        .dropField(_.address.city, DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String(""))))
      val step2 =
        syntax(step1)
          .addField(_.address.zip, DynamicSchemaExpr.Literal(DynamicValue.Primitive(PrimitiveValue.String("00000"))))
      val migration = step2.build

      val source = NestedPersonV1("John", AddressV1("Main St", "NYC"))
      val result = migration(source)

      assertTrue(result == Right(NestedPersonV2("John", AddressV2("Main St", "00000"))))
    }
  )
}
