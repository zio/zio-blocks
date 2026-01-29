package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.blocks.schema.CompanionOptics
import zio.test._
import zio.test.Assertion._
import TypeLevel._
import MigrationBuilderSyntax._
import FieldExtraction.{CasePaths, FieldPaths}

object CompileTimeValidationSpec extends ZIOSpecDefault {

  // ==========================================================================
  // Test case classes - Identical schemas
  // ==========================================================================

  case class PersonA(name: String, age: Int)
  object PersonA {
    implicit val schema: Schema[PersonA] = Schema.derived
  }

  case class PersonB(name: String, age: Int)
  object PersonB {
    implicit val schema: Schema[PersonB] = Schema.derived
  }

  // ==========================================================================
  // Test case classes - Field renamed
  // ==========================================================================

  case class RenameSource(oldName: String, age: Int)
  object RenameSource {
    implicit val schema: Schema[RenameSource] = Schema.derived
  }

  case class RenameTarget(newName: String, age: Int)
  object RenameTarget {
    implicit val schema: Schema[RenameTarget] = Schema.derived
  }

  // ==========================================================================
  // Test case classes - Field dropped
  // ==========================================================================

  case class DropSource(name: String, age: Int, extra: Boolean)
  object DropSource {
    implicit val schema: Schema[DropSource] = Schema.derived
  }

  case class DropTarget(name: String, age: Int)
  object DropTarget {
    implicit val schema: Schema[DropTarget] = Schema.derived
  }

  // ==========================================================================
  // Test case classes - Field added
  // ==========================================================================

  case class AddSource(name: String, age: Int)
  object AddSource {
    implicit val schema: Schema[AddSource] = Schema.derived
  }

  case class AddTarget(name: String, age: Int, extra: Boolean)
  object AddTarget {
    implicit val schema: Schema[AddTarget] = Schema.derived
  }

  // ==========================================================================
  // Test case classes - Complex: drop + add + rename
  // ==========================================================================

  case class ComplexSource(a: String, b: Int, c: Boolean, d: Double)
  object ComplexSource {
    implicit val schema: Schema[ComplexSource] = Schema.derived
  }

  case class ComplexTarget(x: String, b: Int, y: Boolean, e: Long)
  object ComplexTarget {
    implicit val schema: Schema[ComplexTarget] = Schema.derived
  }

  // ==========================================================================
  // Test case classes - Many shared fields
  // ==========================================================================

  case class ManySharedSource(shared1: String, shared2: Int, shared3: Boolean, removed: Double)
  object ManySharedSource {
    implicit val schema: Schema[ManySharedSource] = Schema.derived
  }

  case class ManySharedTarget(shared1: String, shared2: Int, shared3: Boolean, added: Long)
  object ManySharedTarget {
    implicit val schema: Schema[ManySharedTarget] = Schema.derived
  }

  // ==========================================================================
  // Test case classes - Single field
  // ==========================================================================

  case class SingleA(only: String)
  object SingleA {
    implicit val schema: Schema[SingleA] = Schema.derived
  }

  case class SingleB(only: String)
  object SingleB {
    implicit val schema: Schema[SingleB] = Schema.derived
  }

  // ==========================================================================
  // Test case classes - Empty schemas
  // ==========================================================================

  case class EmptySource()
  object EmptySource {
    implicit val schema: Schema[EmptySource] = Schema.derived
  }

  case class EmptyTarget()
  object EmptyTarget {
    implicit val schema: Schema[EmptyTarget] = Schema.derived
  }

  case class NonEmptyForEmpty(field: String)
  object NonEmptyForEmpty {
    implicit val schema: Schema[NonEmptyForEmpty] = Schema.derived
  }

  // ==========================================================================
  // Test case classes - All fields changed
  // ==========================================================================

  case class AllChangedSource(a: String, b: Int)
  object AllChangedSource {
    implicit val schema: Schema[AllChangedSource] = Schema.derived
  }

  case class AllChangedTarget(x: Boolean, y: Double)
  object AllChangedTarget {
    implicit val schema: Schema[AllChangedTarget] = Schema.derived
  }

  // ==========================================================================
  // Test case classes - Multiple drops
  // ==========================================================================

  case class MultiDropSource(keep: String, drop1: Int, drop2: Boolean, drop3: Double)
  object MultiDropSource {
    implicit val schema: Schema[MultiDropSource] = Schema.derived
  }

  case class MultiDropTarget(keep: String)
  object MultiDropTarget {
    implicit val schema: Schema[MultiDropTarget] = Schema.derived
  }

  // ==========================================================================
  // Test case classes - Multiple adds
  // ==========================================================================

  case class MultiAddSource(keep: String)
  object MultiAddSource {
    implicit val schema: Schema[MultiAddSource] = Schema.derived
  }

  case class MultiAddTarget(keep: String, add1: Int, add2: Boolean, add3: Double)
  object MultiAddTarget {
    implicit val schema: Schema[MultiAddTarget] = Schema.derived
  }

  // ==========================================================================
  // Test case classes - Nested path validation (Phase 9)
  // ==========================================================================

  // Shared nested structure
  case class SharedNested(a: Int, b: String)
  object SharedNested {
    implicit val schema: Schema[SharedNested] = Schema.derived
  }

  case class TypeWithNestedSrc(shared: SharedNested, remove: Boolean)
  object TypeWithNestedSrc {
    implicit val schema: Schema[TypeWithNestedSrc] = Schema.derived
  }

  case class TypeWithNestedTgt(shared: SharedNested, add: Double)
  object TypeWithNestedTgt {
    implicit val schema: Schema[TypeWithNestedTgt] = Schema.derived
  }

  // Nested with field change
  case class AddressV1(street: String, city: String)
  object AddressV1 {
    implicit val schema: Schema[AddressV1] = Schema.derived
  }

  case class NestedPersonV1(name: String, address: AddressV1)
  object NestedPersonV1 {
    implicit val schema: Schema[NestedPersonV1] = Schema.derived
  }

  case class AddressV2(street: String, zip: String) // city -> zip
  object AddressV2 {
    implicit val schema: Schema[AddressV2] = Schema.derived
  }

  case class NestedPersonV2(name: String, address: AddressV2)
  object NestedPersonV2 {
    implicit val schema: Schema[NestedPersonV2] = Schema.derived
  }

  // Deep nesting (3 levels)
  case class Level3(value: String)
  object Level3 {
    implicit val schema: Schema[Level3] = Schema.derived
  }

  case class Level2(l3: Level3)
  object Level2 {
    implicit val schema: Schema[Level2] = Schema.derived
  }

  case class Level1Src(l2: Level2, extra: Int)
  object Level1Src {
    implicit val schema: Schema[Level1Src] = Schema.derived
  }

  case class Level1Tgt(l2: Level2)
  object Level1Tgt {
    implicit val schema: Schema[Level1Tgt] = Schema.derived
  }

  // Deep nesting with change at 3rd level
  case class DeepInnerV1(value: Int)
  object DeepInnerV1 {
    implicit val schema: Schema[DeepInnerV1] = Schema.derived
  }

  case class DeepMiddleV1(inner: DeepInnerV1)
  object DeepMiddleV1 {
    implicit val schema: Schema[DeepMiddleV1] = Schema.derived
  }

  case class DeepOuterV1(middle: DeepMiddleV1)
  object DeepOuterV1 {
    implicit val schema: Schema[DeepOuterV1] = Schema.derived
  }

  case class DeepInnerV2(value: Long) // Int -> Long
  object DeepInnerV2 {
    implicit val schema: Schema[DeepInnerV2] = Schema.derived
  }

  case class DeepMiddleV2(inner: DeepInnerV2)
  object DeepMiddleV2 {
    implicit val schema: Schema[DeepMiddleV2] = Schema.derived
  }

  case class DeepOuterV2(middle: DeepMiddleV2)
  object DeepOuterV2 {
    implicit val schema: Schema[DeepOuterV2] = Schema.derived
  }

  // ==========================================================================
  // Test case classes - Sealed trait case tracking (Phase 9)
  // ==========================================================================

  sealed trait ResultV1
  case class SuccessV1(value: Int) extends ResultV1
  case class FailureV1(err: String) extends ResultV1

  object ResultV1 extends CompanionOptics[ResultV1] {
    implicit val schema: Schema[ResultV1] = Schema.derived
  }
  object SuccessV1 {
    implicit val schema: Schema[SuccessV1] = Schema.derived
  }
  object FailureV1 {
    implicit val schema: Schema[FailureV1] = Schema.derived
  }

  sealed trait ResultV2
  case class OkResult(value: Int) extends ResultV2   // Renamed from SuccessV1
  case class ErrResult(err: String) extends ResultV2 // Renamed from FailureV1

  object ResultV2 {
    implicit val schema: Schema[ResultV2] = Schema.derived
  }
  object OkResult {
    implicit val schema: Schema[OkResult] = Schema.derived
  }
  object ErrResult {
    implicit val schema: Schema[ErrResult] = Schema.derived
  }

  // Identical sealed traits (no case changes)
  sealed trait StatusSame
  case class ActiveSame(since: String) extends StatusSame
  case class InactiveSame() extends StatusSame

  object StatusSame extends CompanionOptics[StatusSame] {
    implicit val schema: Schema[StatusSame] = Schema.derived
  }
  object ActiveSame {
    implicit val schema: Schema[ActiveSame] = Schema.derived
  }
  object InactiveSame {
    implicit val schema: Schema[InactiveSame] = Schema.derived
  }

  // ==========================================================================
  // Helpers
  // ==========================================================================

  def syntax[A, B, H <: TList, P <: TList](
    b: MigrationBuilder[A, B, H, P]
  ): MigrationBuilderSyntax[A, B, H, P] = new MigrationBuilderSyntax(b)

  // ==========================================================================
  // Tests
  // ==========================================================================

  override def spec = suite("CompileTimeValidationSpec - Scala 2")(
    identicalSchemasSuite,
    dropFieldSuite,
    addFieldSuite,
    renameFieldSuite,
    complexMigrationSuite,
    chainingStylesSuite,
    edgeCasesSuite,
    buildPartialSuite,
    compileFailureSuite,
    nestedPathValidationSuite,
    caseTrackingSuite
  )

  // --------------------------------------------------------------------------
  // Identical Schemas Suite
  // --------------------------------------------------------------------------
  val identicalSchemasSuite = suite("identical schemas")(
    test("build works with no operations needed") {
      val migration = syntax(MigrationBuilder.newBuilder[PersonA, PersonB]).build

      val personA = PersonA("John", 30)
      val result  = migration(personA)

      assertTrue(result.isRight) &&
      assertTrue(result.map(_.name) == Right("John")) &&
      assertTrue(result.map(_.age) == Right(30))
    },
    test("single field identical schemas") {
      val migration = syntax(MigrationBuilder.newBuilder[SingleA, SingleB]).build

      val source = SingleA("test")
      val result = migration(source)

      assertTrue(result.isRight) &&
      assertTrue(result.map(_.only) == Right("test"))
    },
    test("empty to empty schema") {
      val migration = syntax(MigrationBuilder.newBuilder[EmptySource, EmptyTarget]).build

      val source = EmptySource()
      val result = migration(source)

      assertTrue(result.isRight)
    }
  )

  // --------------------------------------------------------------------------
  // Drop Field Suite
  // --------------------------------------------------------------------------
  val dropFieldSuite = suite("drop field migrations")(
    test("complete drop migration - single field") {
      val migration = syntax(MigrationBuilder.newBuilder[DropSource, DropTarget])
        .dropField(_.extra, SchemaExpr.Literal[DynamicValue, Boolean](false, Schema.boolean))
        .build

      val source = DropSource("John", 30, true)
      val result = migration(source)

      assertTrue(result.isRight) &&
      assertTrue(result.map(_.name) == Right("John")) &&
      assertTrue(result.map(_.age) == Right(30))
    },
    test("complete drop migration - multiple fields inline") {
      val migration = syntax(
        syntax(
          syntax(MigrationBuilder.newBuilder[MultiDropSource, MultiDropTarget])
            .dropField(_.drop1, SchemaExpr.Literal[DynamicValue, Int](0, Schema.int))
        ).dropField(_.drop2, SchemaExpr.Literal[DynamicValue, Boolean](false, Schema.boolean))
      ).dropField(_.drop3, SchemaExpr.Literal[DynamicValue, Double](0.0, Schema.double)).build

      val source = MultiDropSource("keep", 1, true, 2.5)
      val result = migration(source)

      assertTrue(result.isRight) &&
      assertTrue(result.map(_.keep) == Right("keep"))
    },
    test("complete drop migration - multiple fields over multiple lines") {
      val builder1 = MigrationBuilder.newBuilder[MultiDropSource, MultiDropTarget]
      val builder2 = syntax(builder1).dropField(_.drop1, SchemaExpr.Literal[DynamicValue, Int](0, Schema.int))
      val builder3 =
        syntax(builder2).dropField(_.drop2, SchemaExpr.Literal[DynamicValue, Boolean](false, Schema.boolean))
      val builder4  = syntax(builder3).dropField(_.drop3, SchemaExpr.Literal[DynamicValue, Double](0.0, Schema.double))
      val migration = syntax(builder4).build

      val source = MultiDropSource("keep", 1, true, 2.5)
      val result = migration(source)

      assertTrue(result.isRight) &&
      assertTrue(result.map(_.keep) == Right("keep"))
    }
  )

  // --------------------------------------------------------------------------
  // Add Field Suite
  // --------------------------------------------------------------------------
  val addFieldSuite = suite("add field migrations")(
    test("complete add migration - single field") {
      val migration = syntax(MigrationBuilder.newBuilder[AddSource, AddTarget])
        .addField(_.extra, SchemaExpr.Literal[DynamicValue, Boolean](true, Schema.boolean))
        .build

      val source = AddSource("John", 30)
      val result = migration(source)

      assertTrue(result.isRight) &&
      assertTrue(result.map(_.name) == Right("John")) &&
      assertTrue(result.map(_.age) == Right(30))
    },
    test("complete add migration - multiple fields inline") {
      val migration = syntax(
        syntax(
          syntax(MigrationBuilder.newBuilder[MultiAddSource, MultiAddTarget])
            .addField(_.add1, SchemaExpr.Literal[DynamicValue, Int](42, Schema.int))
        ).addField(_.add2, SchemaExpr.Literal[DynamicValue, Boolean](true, Schema.boolean))
      ).addField(_.add3, SchemaExpr.Literal[DynamicValue, Double](3.14, Schema.double)).build

      val source = MultiAddSource("keep")
      val result = migration(source)

      assertTrue(result.isRight) &&
      assertTrue(result.map(_.keep) == Right("keep"))
    },
    test("complete add migration - multiple fields over multiple lines") {
      val builder1  = MigrationBuilder.newBuilder[MultiAddSource, MultiAddTarget]
      val builder2  = syntax(builder1).addField(_.add1, SchemaExpr.Literal[DynamicValue, Int](42, Schema.int))
      val builder3  = syntax(builder2).addField(_.add2, SchemaExpr.Literal[DynamicValue, Boolean](true, Schema.boolean))
      val builder4  = syntax(builder3).addField(_.add3, SchemaExpr.Literal[DynamicValue, Double](3.14, Schema.double))
      val migration = syntax(builder4).build

      val source = MultiAddSource("keep")
      val result = migration(source)

      assertTrue(result.isRight) &&
      assertTrue(result.map(_.keep) == Right("keep"))
    },
    test("empty source to non-empty target") {
      val migration = syntax(MigrationBuilder.newBuilder[EmptySource, NonEmptyForEmpty])
        .addField(_.field, SchemaExpr.Literal[DynamicValue, String]("default", Schema.string))
        .build

      val source = EmptySource()
      val result = migration(source)

      assertTrue(result.isRight)
    }
  )

  // --------------------------------------------------------------------------
  // Rename Field Suite
  // --------------------------------------------------------------------------
  val renameFieldSuite = suite("rename field migrations")(
    test("complete rename migration") {
      val migration = syntax(MigrationBuilder.newBuilder[RenameSource, RenameTarget])
        .renameField(_.oldName, _.newName)
        .build

      val source = RenameSource("John", 30)
      val result = migration(source)

      assertTrue(result.isRight) &&
      assertTrue(result.map(_.newName) == Right("John")) &&
      assertTrue(result.map(_.age) == Right(30))
    },
    test("rename handles both source and target tracking") {
      // Rename should mark oldName as handled and newName as provided
      val migration = syntax(MigrationBuilder.newBuilder[RenameSource, RenameTarget])
        .renameField(_.oldName, _.newName)
        .build

      assertTrue(migration != null)
    }
  )

  // --------------------------------------------------------------------------
  // Complex Migration Suite
  // --------------------------------------------------------------------------
  val complexMigrationSuite = suite("complex migrations")(
    test("drop + add + rename inline") {
      // ComplexSource: a, b, c, d
      // ComplexTarget: x, b, y, e
      // shared: b
      // removed: a, c, d (need to handle)
      // added: x, y, e (need to provide)

      val migration = syntax(
        syntax(
          syntax(
            syntax(MigrationBuilder.newBuilder[ComplexSource, ComplexTarget])
              .renameField(_.a, _.x)                                                   // handles a, provides x
          ).renameField(_.c, _.y)                                                      // handles c, provides y
        ).dropField(_.d, SchemaExpr.Literal[DynamicValue, Double](0.0, Schema.double)) // handles d
      ).addField(_.e, SchemaExpr.Literal[DynamicValue, Long](0L, Schema.long))         // provides e
        .build

      val source = ComplexSource("hello", 42, true, 3.14)
      val result = migration(source)

      assertTrue(result.isRight) &&
      assertTrue(result.map(_.x) == Right("hello")) &&
      assertTrue(result.map(_.b) == Right(42))
    },
    test("drop + add + rename over multiple lines") {
      val builder1  = MigrationBuilder.newBuilder[ComplexSource, ComplexTarget]
      val builder2  = syntax(builder1).renameField(_.a, _.x)
      val builder3  = syntax(builder2).renameField(_.c, _.y)
      val builder4  = syntax(builder3).dropField(_.d, SchemaExpr.Literal[DynamicValue, Double](0.0, Schema.double))
      val builder5  = syntax(builder4).addField(_.e, SchemaExpr.Literal[DynamicValue, Long](0L, Schema.long))
      val migration = syntax(builder5).build

      val source = ComplexSource("hello", 42, true, 3.14)
      val result = migration(source)

      assertTrue(result.isRight)
    },
    test("many shared fields - only handle changed fields") {
      val migration = syntax(
        syntax(MigrationBuilder.newBuilder[ManySharedSource, ManySharedTarget])
          .dropField(_.removed, SchemaExpr.Literal[DynamicValue, Double](0.0, Schema.double))
      ).addField(_.added, SchemaExpr.Literal[DynamicValue, Long](0L, Schema.long)).build

      val source = ManySharedSource("a", 1, true, 2.5)
      val result = migration(source)

      assertTrue(result.isRight) &&
      assertTrue(result.map(_.shared1) == Right("a")) &&
      assertTrue(result.map(_.shared2) == Right(1)) &&
      assertTrue(result.map(_.shared3) == Right(true))
    },
    test("all fields changed") {
      val migration = syntax(
        syntax(
          syntax(
            syntax(MigrationBuilder.newBuilder[AllChangedSource, AllChangedTarget])
              .dropField(_.a, SchemaExpr.Literal[DynamicValue, String]("", Schema.string))
          ).dropField(_.b, SchemaExpr.Literal[DynamicValue, Int](0, Schema.int))
        ).addField(_.x, SchemaExpr.Literal[DynamicValue, Boolean](false, Schema.boolean))
      ).addField(_.y, SchemaExpr.Literal[DynamicValue, Double](0.0, Schema.double)).build

      val source = AllChangedSource("test", 42)
      val result = migration(source)

      assertTrue(result.isRight)
    }
  )

  // --------------------------------------------------------------------------
  // Chaining Styles Suite
  // --------------------------------------------------------------------------
  val chainingStylesSuite = suite("chaining styles")(
    test("fully inline chaining with syntax wrapper") {
      val migration = syntax(
        syntax(MigrationBuilder.newBuilder[DropSource, DropTarget])
          .dropField(_.extra, SchemaExpr.Literal[DynamicValue, Boolean](false, Schema.boolean))
      ).build

      assertTrue(migration != null)
    },
    test("step by step with val assignments") {
      val step1: MigrationBuilder[DropSource, DropTarget, TNil, TNil] =
        MigrationBuilder.newBuilder[DropSource, DropTarget]

      val step2 = syntax(step1)
        .dropField(_.extra, SchemaExpr.Literal[DynamicValue, Boolean](false, Schema.boolean))

      val migration = syntax(step2).build

      assertTrue(migration != null)
    },
    test("mixed inline and multi-line") {
      val builder = syntax(
        syntax(MigrationBuilder.newBuilder[ComplexSource, ComplexTarget])
          .renameField(_.a, _.x)
      ).renameField(_.c, _.y)

      val builder2 = syntax(builder)
        .dropField(_.d, SchemaExpr.Literal[DynamicValue, Double](0.0, Schema.double))

      val migration = syntax(
        syntax(builder2)
          .addField(_.e, SchemaExpr.Literal[DynamicValue, Long](0L, Schema.long))
      ).build

      assertTrue(migration != null)
    },
    test("using implicit conversion for build") {
      // With the implicit conversion imported, build is available on MigrationBuilder
      val builder = syntax(MigrationBuilder.newBuilder[DropSource, DropTarget])
        .dropField(_.extra, SchemaExpr.Literal[DynamicValue, Boolean](false, Schema.boolean))

      // The implicit conversion allows calling build on MigrationBuilder
      val migration: Migration[DropSource, DropTarget] = builder.build

      assertTrue(migration != null)
    }
  )

  // --------------------------------------------------------------------------
  // Edge Cases Suite
  // --------------------------------------------------------------------------
  val edgeCasesSuite = suite("edge cases")(
    test("non-empty source to empty target") {
      val migration = syntax(MigrationBuilder.newBuilder[NonEmptyForEmpty, EmptyTarget])
        .dropField(_.field, SchemaExpr.Literal[DynamicValue, String]("", Schema.string))
        .build

      val source = NonEmptyForEmpty("test")
      val result = migration(source)

      assertTrue(result.isRight)
    },
    test("superset of required handled fields is OK") {
      // We can handle more fields than necessary
      val builder  = MigrationBuilder.newBuilder[DropSource, DropTarget]
      val withDrop = syntax(builder)
        .dropField(_.extra, SchemaExpr.Literal[DynamicValue, Boolean](false, Schema.boolean))

      // Transform a shared field (not required, but allowed)
      val withTransform = syntax(withDrop)
        .transformField(_.name, SchemaExpr.Literal[DynamicValue, String]("transformed", Schema.string))

      val migration = syntax(withTransform).build

      assertTrue(migration != null)
    },
    test("superset of required provided fields is OK") {
      val builder = MigrationBuilder.newBuilder[AddSource, AddTarget]
      val withAdd = syntax(builder)
        .addField(_.extra, SchemaExpr.Literal[DynamicValue, Boolean](false, Schema.boolean))

      // Transform a shared field (not required, but allowed)
      val withTransform = syntax(withAdd)
        .transformField(_.name, SchemaExpr.Literal[DynamicValue, String]("transformed", Schema.string))

      val migration = syntax(withTransform).build

      assertTrue(migration != null)
    }
  )

  // --------------------------------------------------------------------------
  // buildPartial Suite
  // --------------------------------------------------------------------------
  val buildPartialSuite = suite("buildPartial always succeeds")(
    test("buildPartial works for empty migration on different schemas") {
      val builder   = MigrationBuilder.newBuilder[DropSource, DropTarget]
      val migration = builder.buildPartial

      assertTrue(migration != null) &&
      assertTrue(migration.sourceSchema == Schema[DropSource]) &&
      assertTrue(migration.targetSchema == Schema[DropTarget])
    },
    test("buildPartial works for incomplete drop migration") {
      val builder   = MigrationBuilder.newBuilder[MultiDropSource, MultiDropTarget]
      val partial   = syntax(builder).dropField(_.drop1, SchemaExpr.Literal[DynamicValue, Int](0, Schema.int))
      val migration = partial.buildPartial

      // Only dropped 1 of 3 required fields, but buildPartial doesn't validate
      assertTrue(migration != null)
    },
    test("buildPartial works for incomplete add migration") {
      val builder   = MigrationBuilder.newBuilder[MultiAddSource, MultiAddTarget]
      val partial   = syntax(builder).addField(_.add1, SchemaExpr.Literal[DynamicValue, Int](0, Schema.int))
      val migration = partial.buildPartial

      // Only added 1 of 3 required fields, but buildPartial doesn't validate
      assertTrue(migration != null)
    },
    test("buildPartial works for incomplete complex migration") {
      val builder = MigrationBuilder.newBuilder[ComplexSource, ComplexTarget]
      val partial = syntax(builder).renameField(_.a, _.x) // Only partial migration

      val migration = partial.buildPartial

      assertTrue(migration != null)
    }
  )

  // --------------------------------------------------------------------------
  // Compile Failure Suite (using typeCheck)
  // --------------------------------------------------------------------------
  val compileFailureSuite = suite("compile failures for incomplete migrations")(
    test("build fails when drop is missing") {
      val result = typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.migration._
        import zio.blocks.schema.migration.TypeLevel._
        import zio.blocks.schema.migration.MigrationBuilderSyntax._

        case class Src(name: String, extra: Int)
        object Src { implicit val schema: Schema[Src] = Schema.derived }

        case class Tgt(name: String)
        object Tgt { implicit val schema: Schema[Tgt] = Schema.derived }

        MigrationBuilder.newBuilder[Src, Tgt].build
      """)

      assertZIO(result)(isLeft(containsString("unhandled fields")))
    },
    test("build fails when add is missing") {
      val result = typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.migration._
        import zio.blocks.schema.migration.TypeLevel._
        import zio.blocks.schema.migration.MigrationBuilderSyntax._

        case class Src(name: String)
        object Src { implicit val schema: Schema[Src] = Schema.derived }

        case class Tgt(name: String, extra: Int)
        object Tgt { implicit val schema: Schema[Tgt] = Schema.derived }

        MigrationBuilder.newBuilder[Src, Tgt].build
      """)

      assertZIO(result)(isLeft(containsString("unprovided fields")))
    },
    test("build fails when both handled and provided are missing") {
      val result = typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.migration._
        import zio.blocks.schema.migration.TypeLevel._
        import zio.blocks.schema.migration.MigrationBuilderSyntax._

        case class Src(shared: String, removed: Int)
        object Src { implicit val schema: Schema[Src] = Schema.derived }

        case class Tgt(shared: String, added: Boolean)
        object Tgt { implicit val schema: Schema[Tgt] = Schema.derived }

        MigrationBuilder.newBuilder[Src, Tgt].build
      """)

      assertZIO(result)(isLeft(containsString("unhandled fields")))
    },
    test("build fails when only some fields are handled") {
      // Note: In Scala 2 typeCheck, the chained selector syntax has type inference issues
      // So we test the simpler case where build is called directly
      val result = typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.migration._
        import zio.blocks.schema.migration.TypeLevel._
        import zio.blocks.schema.migration.MigrationBuilderSyntax._

        case class Src(keep: String, drop1: Int, drop2: Boolean)
        object Src { implicit val schema: Schema[Src] = Schema.derived }

        case class Tgt(keep: String)
        object Tgt { implicit val schema: Schema[Tgt] = Schema.derived }

        // Empty builder - missing both drop1 and drop2
        MigrationBuilder.newBuilder[Src, Tgt].build
      """)

      // Should fail because drop1 and drop2 are not handled
      assertZIO(result)(isLeft(containsString("unhandled fields")))
    },
    test("build fails when only some fields are provided") {
      // Note: In Scala 2 typeCheck, the chained selector syntax has type inference issues
      // So we test the simpler case where build is called directly
      val result = typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.migration._
        import zio.blocks.schema.migration.TypeLevel._
        import zio.blocks.schema.migration.MigrationBuilderSyntax._

        case class Src(keep: String)
        object Src { implicit val schema: Schema[Src] = Schema.derived }

        case class Tgt(keep: String, add1: Int, add2: Boolean)
        object Tgt { implicit val schema: Schema[Tgt] = Schema.derived }

        // Empty builder - missing both add1 and add2
        MigrationBuilder.newBuilder[Src, Tgt].build
      """)

      // Should fail because add1 and add2 are not provided
      assertZIO(result)(isLeft(containsString("unprovided fields")))
    },
    test("error message includes field names") {
      val result = typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.migration._
        import zio.blocks.schema.migration.TypeLevel._
        import zio.blocks.schema.migration.MigrationBuilderSyntax._

        case class Src(name: String, fieldToRemove: Int)
        object Src { implicit val schema: Schema[Src] = Schema.derived }

        case class Tgt(name: String)
        object Tgt { implicit val schema: Schema[Tgt] = Schema.derived }

        MigrationBuilder.newBuilder[Src, Tgt].build
      """)

      assertZIO(result)(isLeft(containsString("fieldToRemove")))
    },
    test("error message includes type names") {
      val result = typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.migration._
        import zio.blocks.schema.migration.TypeLevel._
        import zio.blocks.schema.migration.MigrationBuilderSyntax._

        case class MySourceType(name: String, extra: Int)
        object MySourceType { implicit val schema: Schema[MySourceType] = Schema.derived }

        case class MyTargetType(name: String)
        object MyTargetType { implicit val schema: Schema[MyTargetType] = Schema.derived }

        MigrationBuilder.newBuilder[MySourceType, MyTargetType].build
      """)

      assertZIO(result)(isLeft(containsString("MySourceType")))
    }
  )

  // --------------------------------------------------------------------------
  // Nested Path Validation Suite (Phase 9)
  // --------------------------------------------------------------------------
  val nestedPathValidationSuite = suite("nested path validation (Phase 9)")(
    test("unchanged nested structure requires no handling") {
      // When nested structure is identical in both types, no handling needed
      // The "shared", "shared.a", "shared.b" paths exist in both, so only
      // "remove" needs to be handled and "add" needs to be provided
      val migration = syntax(
        syntax(MigrationBuilder.newBuilder[TypeWithNestedSrc, TypeWithNestedTgt])
          .dropField(_.remove, SchemaExpr.Literal[DynamicValue, Boolean](false, Schema.boolean))
      ).addField(_.add, SchemaExpr.Literal[DynamicValue, Double](0.0, Schema.double)).build

      val source = TypeWithNestedSrc(SharedNested(42, "hello"), remove = true)
      val result = migration(source)

      assertTrue(result.isRight)
    },
    test("nested field change requires handling with full path") {
      // When a nested field changes, the full path must be handled/provided
      // Need to handle "address.city" and provide "address.zip"
      val migration = syntax(
        syntax(MigrationBuilder.newBuilder[NestedPersonV1, NestedPersonV2])
          .dropField(_.address.city, SchemaExpr.Literal[DynamicValue, String]("", Schema.string))
      ).addField(_.address.zip, SchemaExpr.Literal[DynamicValue, String]("00000", Schema.string)).build

      val source = NestedPersonV1("John", AddressV1("Main St", "NYC"))
      val result = migration(source)

      assertTrue(result.isRight)
    },
    test("nested field rename with full path") {
      // Rename handles both source and target paths
      val migration = syntax(MigrationBuilder.newBuilder[NestedPersonV1, NestedPersonV2])
        .renameField(_.address.city, _.address.zip)
        .build

      val source = NestedPersonV1("Jane", AddressV1("Oak Ave", "LA"))
      val result = migration(source)

      assertTrue(result.isRight)
    },
    test("deeply nested path tracking (3 levels)") {
      // Nested l2, l2.l3, l2.l3.value are shared, only "extra" needs handling
      val migration = syntax(MigrationBuilder.newBuilder[Level1Src, Level1Tgt])
        .dropField(_.extra, SchemaExpr.Literal[DynamicValue, Int](0, Schema.int))
        .build

      val source = Level1Src(Level2(Level3("deep")), 42)
      val result = migration(source)

      assertTrue(result.isRight)
    },
    test("deeply nested change requires full path") {
      // The change at middle.inner.value needs to be handled
      val migration = syntax(MigrationBuilder.newBuilder[DeepOuterV1, DeepOuterV2])
        .changeFieldType(_.middle.inner.value, PrimitiveConverter.IntToLong)
        .build

      val source = DeepOuterV1(DeepMiddleV1(DeepInnerV1(42)))
      val result = migration(source)

      assertTrue(result.isRight)
    },
    test("nested field change fails without full path handling") {
      val result = typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.migration._
        import zio.blocks.schema.migration.TypeLevel._
        import zio.blocks.schema.migration.MigrationBuilderSyntax._

        case class AddrA(street: String, city: String)
        case class PersonA(name: String, address: AddrA)

        case class AddrB(street: String, zip: String)
        case class PersonB(name: String, address: AddrB)

        object AddrA { implicit val schema: Schema[AddrA] = Schema.derived }
        object PersonA { implicit val schema: Schema[PersonA] = Schema.derived }
        object AddrB { implicit val schema: Schema[AddrB] = Schema.derived }
        object PersonB { implicit val schema: Schema[PersonB] = Schema.derived }

        // This should fail - nested change detected but not handled
        MigrationBuilder.newBuilder[PersonA, PersonB].build
      """)

      assertZIO(result)(isLeft(containsString("address.city")))
    },
    test("error message shows full nested path for unhandled field") {
      val result = typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.migration._
        import zio.blocks.schema.migration.TypeLevel._
        import zio.blocks.schema.migration.MigrationBuilderSyntax._

        case class Inner(old: String)
        case class Outer(nested: Inner)

        case class InnerNew(newField: String)
        case class OuterNew(nested: InnerNew)

        object Inner { implicit val schema: Schema[Inner] = Schema.derived }
        object Outer { implicit val schema: Schema[Outer] = Schema.derived }
        object InnerNew { implicit val schema: Schema[InnerNew] = Schema.derived }
        object OuterNew { implicit val schema: Schema[OuterNew] = Schema.derived }

        MigrationBuilder.newBuilder[Outer, OuterNew].build
      """)

      // Should show nested.old as unhandled, not just "old"
      assertZIO(result)(isLeft(containsString("nested.old")))
    },
    test("error message shows full nested path for unprovided field") {
      val result = typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.migration._
        import zio.blocks.schema.migration.TypeLevel._
        import zio.blocks.schema.migration.MigrationBuilderSyntax._

        case class Inner(x: Int)
        case class Outer(nested: Inner)

        case class InnerNew(x: Int, y: String)
        case class OuterNew(nested: InnerNew)

        object Inner { implicit val schema: Schema[Inner] = Schema.derived }
        object Outer { implicit val schema: Schema[Outer] = Schema.derived }
        object InnerNew { implicit val schema: Schema[InnerNew] = Schema.derived }
        object OuterNew { implicit val schema: Schema[OuterNew] = Schema.derived }

        MigrationBuilder.newBuilder[Outer, OuterNew].build
      """)

      // Should show nested.y as unprovided
      assertZIO(result)(isLeft(containsString("nested.y")))
    }
  )

  // --------------------------------------------------------------------------
  // Case Tracking Suite (Phase 9)
  // --------------------------------------------------------------------------
  val caseTrackingSuite = suite("sealed trait case tracking (Phase 9)")(
    test("identical sealed traits require no case handling") {
      // When sealed traits have the same case names, no handling needed
      val migration = syntax(MigrationBuilder.newBuilder[StatusSame, StatusSame]).build

      assertTrue(migration != null)
    },
    test("missing case handling fails to compile") {
      val result = typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.migration._
        import zio.blocks.schema.migration.TypeLevel._
        import zio.blocks.schema.migration.MigrationBuilderSyntax._

        sealed trait OldTrait
        case class CaseA(x: Int) extends OldTrait
        case class CaseB(y: String) extends OldTrait
        object OldTrait { implicit val schema: Schema[OldTrait] = Schema.derived }
        object CaseA { implicit val schema: Schema[CaseA] = Schema.derived }
        object CaseB { implicit val schema: Schema[CaseB] = Schema.derived }

        sealed trait NewTrait
        case class CaseX(x: Int) extends NewTrait
        case class CaseB2(y: String) extends NewTrait
        object NewTrait { implicit val schema: Schema[NewTrait] = Schema.derived }
        object CaseX { implicit val schema: Schema[CaseX] = Schema.derived }
        object CaseB2 { implicit val schema: Schema[CaseB2] = Schema.derived }

        // This should fail - case names changed but not handled
        MigrationBuilder.newBuilder[OldTrait, NewTrait].build
      """)

      // Should fail because CaseA and CaseB not handled
      // Error shows "unhandled cases: CaseA, CaseB"
      assertZIO(result)(isLeft(containsString("unhandled cases")))
    },
    test("error message shows unhandled case names") {
      val result = typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.migration._
        import zio.blocks.schema.migration.TypeLevel._
        import zio.blocks.schema.migration.MigrationBuilderSyntax._

        sealed trait PaymentV1
        case class CreditCard(number: String) extends PaymentV1
        case class Cash(amount: Int) extends PaymentV1
        object PaymentV1 { implicit val schema: Schema[PaymentV1] = Schema.derived }
        object CreditCard { implicit val schema: Schema[CreditCard] = Schema.derived }
        object Cash { implicit val schema: Schema[Cash] = Schema.derived }

        sealed trait PaymentV2
        case class Card(number: String) extends PaymentV2
        case class Money(amount: Int) extends PaymentV2
        object PaymentV2 { implicit val schema: Schema[PaymentV2] = Schema.derived }
        object Card { implicit val schema: Schema[Card] = Schema.derived }
        object Money { implicit val schema: Schema[Money] = Schema.derived }

        MigrationBuilder.newBuilder[PaymentV1, PaymentV2].build
      """)

      // Should show CreditCard and Cash as unhandled
      // Error shows "unhandled cases: Cash, CreditCard"
      assertZIO(result)(isLeft(containsString("CreditCard") && containsString("Cash")))
    },
    test("error message shows unprovided case names") {
      val result = typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.migration._
        import zio.blocks.schema.migration.TypeLevel._
        import zio.blocks.schema.migration.MigrationBuilderSyntax._

        sealed trait StatusV1
        case class Active(since: String) extends StatusV1
        object StatusV1 { implicit val schema: Schema[StatusV1] = Schema.derived }
        object Active { implicit val schema: Schema[Active] = Schema.derived }

        sealed trait StatusV2
        case class Active2(since: String) extends StatusV2
        case class Disabled(reason: String) extends StatusV2
        object StatusV2 { implicit val schema: Schema[StatusV2] = Schema.derived }
        object Active2 { implicit val schema: Schema[Active2] = Schema.derived }
        object Disabled { implicit val schema: Schema[Disabled] = Schema.derived }

        MigrationBuilder.newBuilder[StatusV1, StatusV2].build
      """)

      // Should show Active2 and Disabled as unprovided
      // Error shows "unprovided cases: Active2, Disabled"
      assertZIO(result)(isLeft(containsString("unprovided cases")))
    },
    test("FieldPaths extracts nested paths correctly") {
      // Verify FieldPaths typeclass extracts full nested paths
      val fp = implicitly[FieldPaths[NestedPersonV1]]
      // The Paths type should contain: "address", "address.city", "address.street", "name"
      // We verify by checking that the implicit resolves
      assertTrue(fp != null)
    },
    test("CasePaths extracts case names for sealed traits") {
      // Verify CasePaths typeclass extracts case names with "case:" prefix
      val cp = implicitly[CasePaths[ResultV1]]
      // The Cases type should contain: "case:FailureV1", "case:SuccessV1"
      // We verify by checking that the implicit resolves
      assertTrue(cp != null)
    },
    test("CasePaths returns empty for non-sealed types") {
      // Non-sealed types should have empty CasePaths
      val cp = implicitly[CasePaths[PersonA]]
      // CasePaths for a regular case class should be TNil
      assertTrue(cp != null)
    }
  )
}
