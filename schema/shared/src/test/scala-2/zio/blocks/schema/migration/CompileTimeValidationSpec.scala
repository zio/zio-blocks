package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.blocks.schema.CompanionOptics
import zio.test._
import zio.test.Assertion._
import TypeLevel._
import MigrationBuilderSyntax._

object CompileTimeValidationSpec extends ZIOSpecDefault {

  // Test case classes - Identical schemas
  case class PersonA(name: String, age: Int)
  object PersonA {
    implicit val schema: Schema[PersonA] = Schema.derived
  }

  case class PersonB(name: String, age: Int)
  object PersonB {
    implicit val schema: Schema[PersonB] = Schema.derived
  }

  // Test case classes - Field renamed
  case class RenameSource(oldName: String, age: Int)
  object RenameSource {
    implicit val schema: Schema[RenameSource] = Schema.derived
  }

  case class RenameTarget(newName: String, age: Int)
  object RenameTarget {
    implicit val schema: Schema[RenameTarget] = Schema.derived
  }

  // Test case classes - Field dropped
  case class DropSource(name: String, age: Int, extra: Boolean)
  object DropSource {
    implicit val schema: Schema[DropSource] = Schema.derived
  }

  case class DropTarget(name: String, age: Int)
  object DropTarget {
    implicit val schema: Schema[DropTarget] = Schema.derived
  }

  // Test case classes - Field added
  case class AddSource(name: String, age: Int)
  object AddSource {
    implicit val schema: Schema[AddSource] = Schema.derived
  }

  case class AddTarget(name: String, age: Int, extra: Boolean)
  object AddTarget {
    implicit val schema: Schema[AddTarget] = Schema.derived
  }

  // Test case classes - Complex: drop + add + rename
  case class ComplexSource(a: String, b: Int, c: Boolean, d: Double)
  object ComplexSource {
    implicit val schema: Schema[ComplexSource] = Schema.derived
  }

  case class ComplexTarget(x: String, b: Int, y: Boolean, e: Long)
  object ComplexTarget {
    implicit val schema: Schema[ComplexTarget] = Schema.derived
  }

  // Test case classes - Many shared fields
  case class ManySharedSource(shared1: String, shared2: Int, shared3: Boolean, removed: Double)
  object ManySharedSource {
    implicit val schema: Schema[ManySharedSource] = Schema.derived
  }

  case class ManySharedTarget(shared1: String, shared2: Int, shared3: Boolean, added: Long)
  object ManySharedTarget {
    implicit val schema: Schema[ManySharedTarget] = Schema.derived
  }

  // Test case classes - Single field
  case class SingleA(only: String)
  object SingleA {
    implicit val schema: Schema[SingleA] = Schema.derived
  }

  case class SingleB(only: String)
  object SingleB {
    implicit val schema: Schema[SingleB] = Schema.derived
  }

  // Test case classes - Empty schemas
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

  // Test case classes - All fields changed
  case class AllChangedSource(a: String, b: Int)
  object AllChangedSource {
    implicit val schema: Schema[AllChangedSource] = Schema.derived
  }

  case class AllChangedTarget(x: Boolean, y: Double)
  object AllChangedTarget {
    implicit val schema: Schema[AllChangedTarget] = Schema.derived
  }

  // Test case classes - Multiple drops and adds
  case class MultiDropSource(keep: String, drop1: Int, drop2: Boolean, drop3: Double)
  object MultiDropSource {
    implicit val schema: Schema[MultiDropSource] = Schema.derived
  }

  case class MultiDropTarget(keep: String)
  object MultiDropTarget {
    implicit val schema: Schema[MultiDropTarget] = Schema.derived
  }

  case class MultiAddSource(keep: String)
  object MultiAddSource {
    implicit val schema: Schema[MultiAddSource] = Schema.derived
  }

  case class MultiAddTarget(keep: String, add1: Int, add2: Boolean, add3: Double)
  object MultiAddTarget {
    implicit val schema: Schema[MultiAddTarget] = Schema.derived
  }

  // Test case classes - Join/Split field tracking
  case class FullNameSource(firstName: String, lastName: String, age: Int)
  object FullNameSource {
    implicit val schema: Schema[FullNameSource] = Schema.derived
  }

  case class FullNameTarget(fullName: String, age: Int)
  object FullNameTarget {
    implicit val schema: Schema[FullNameTarget] = Schema.derived
  }

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

  // Container types
  case class WithContainers(
    opt: Option[AddressV1],
    list: List[String],
    set: Set[Int],
    map: Map[String, AddressV1]
  )
  object WithContainers {
    implicit val schema: Schema[WithContainers] = Schema.derived
  }

  case class WithContainers2(
    opt: Option[AddressV2],
    list: List[String],
    set: Set[Int],
    map: Map[String, AddressV2]
  )
  object WithContainers2 {
    implicit val schema: Schema[WithContainers2] = Schema.derived
  }

  // Deeply nested containers
  case class DeeplyNestedContainers(items: List[Option[Map[String, Vector[Int]]]])
  object DeeplyNestedContainers {
    implicit val schema: Schema[DeeplyNestedContainers] = Schema.derived
  }

  // Sealed trait with case object
  sealed trait WithCaseObject
  case class SomeValue(x: Int) extends WithCaseObject
  case object NoneValue        extends WithCaseObject
  object WithCaseObject        extends CompanionOptics[WithCaseObject] {
    implicit val schema: Schema[WithCaseObject] = Schema.derived
  }
  object SomeValue {
    implicit val schema: Schema[SomeValue] = Schema.derived
  }

  // Sealed trait with only case objects (enum)
  sealed trait Color
  case object Red   extends Color
  case object Green extends Color
  case object Blue  extends Color
  object Color      extends CompanionOptics[Color] {
    implicit val schema: Schema[Color] = Schema.derived
  }

  sealed trait ResultV1
  case class SuccessV1(value: Int)  extends ResultV1
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
  case class OkResult(value: Int)   extends ResultV2 // Renamed from SuccessV1
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
  case class InactiveSame()            extends StatusSame

  object StatusSame extends CompanionOptics[StatusSame] {
    implicit val schema: Schema[StatusSame] = Schema.derived
  }
  object ActiveSame {
    implicit val schema: Schema[ActiveSame] = Schema.derived
  }
  object InactiveSame {
    implicit val schema: Schema[InactiveSame] = Schema.derived
  }

  // Helpers
  def syntax[A, B, H <: TList, P <: TList](
    b: MigrationBuilder[A, B, H, P]
  ): MigrationBuilderSyntax[A, B, H, P] = new MigrationBuilderSyntax(b)

  // Tests
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
    caseTrackingSuite,
    crossPathValidationSuite,
    joinSplitTrackingSuite,
    containerTypesSuite,
    additionalCaseTrackingSuite,
    shapeTreeEdgeCasesSuite,
    EdgeCasesSuite
  )

  // Identical Schemas Suite
  val identicalSchemasSuite = suite("identical schemas")(
    test("build works with no operations needed") {
      val migration = syntax(MigrationBuilder.newBuilder[PersonA, PersonB]).build

      val personA = PersonA("John", 30)
      val result  = migration(personA)

      assertTrue(result == Right(PersonB("John", 30)))
    },
    test("single field identical schemas") {
      val migration = syntax(MigrationBuilder.newBuilder[SingleA, SingleB]).build

      val source = SingleA("test")
      val result = migration(source)

      assertTrue(result == Right(SingleB("test")))
    },
    test("empty to empty schema") {
      val migration = syntax(MigrationBuilder.newBuilder[EmptySource, EmptyTarget]).build

      val source = EmptySource()
      val result = migration(source)

      assertTrue(result == Right(EmptyTarget()))
    }
  )

  // Drop Field Suite
  val dropFieldSuite = suite("drop field migrations")(
    test("complete drop migration - single field") {
      val migration = syntax(MigrationBuilder.newBuilder[DropSource, DropTarget])
        .dropField(_.extra, SchemaExpr.Literal[DynamicValue, Boolean](false, Schema.boolean))
        .build

      val source = DropSource("John", 30, true)
      val result = migration(source)

      assertTrue(result == Right(DropTarget("John", 30)))
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

      assertTrue(result == Right(MultiDropTarget("keep")))
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

      assertTrue(result == Right(MultiDropTarget("keep")))
    }
  )

  // Add Field Suite
  val addFieldSuite = suite("add field migrations")(
    test("complete add migration - single field") {
      val migration = syntax(MigrationBuilder.newBuilder[AddSource, AddTarget])
        .addField(_.extra, SchemaExpr.Literal[DynamicValue, Boolean](true, Schema.boolean))
        .build

      val source = AddSource("John", 30)
      val result = migration(source)

      assertTrue(result == Right(AddTarget("John", 30, true)))
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

      assertTrue(result == Right(MultiAddTarget("keep", 42, true, 3.14)))
    },
    test("complete add migration - multiple fields over multiple lines") {
      val builder1  = MigrationBuilder.newBuilder[MultiAddSource, MultiAddTarget]
      val builder2  = syntax(builder1).addField(_.add1, SchemaExpr.Literal[DynamicValue, Int](42, Schema.int))
      val builder3  = syntax(builder2).addField(_.add2, SchemaExpr.Literal[DynamicValue, Boolean](true, Schema.boolean))
      val builder4  = syntax(builder3).addField(_.add3, SchemaExpr.Literal[DynamicValue, Double](3.14, Schema.double))
      val migration = syntax(builder4).build

      val source = MultiAddSource("keep")
      val result = migration(source)

      assertTrue(result == Right(MultiAddTarget("keep", 42, true, 3.14)))
    },
    test("empty source to non-empty target") {
      val migration = syntax(MigrationBuilder.newBuilder[EmptySource, NonEmptyForEmpty])
        .addField(_.field, SchemaExpr.Literal[DynamicValue, String]("default", Schema.string))
        .build

      val source = EmptySource()
      val result = migration(source)

      assertTrue(result == Right(NonEmptyForEmpty("default")))
    }
  )

  // Rename Field Suite
  val renameFieldSuite = suite("rename field migrations")(
    test("complete rename migration") {
      val migration = syntax(MigrationBuilder.newBuilder[RenameSource, RenameTarget])
        .renameField(_.oldName, _.newName)
        .build

      val source = RenameSource("John", 30)
      val result = migration(source)

      assertTrue(result == Right(RenameTarget("John", 30)))
    },
    test("rename handles both source and target tracking") {
      // Rename should mark oldName as handled and newName as provided
      val migration = syntax(MigrationBuilder.newBuilder[RenameSource, RenameTarget])
        .renameField(_.oldName, _.newName)
        .build

      val source = RenameSource("test", 25)
      val result = migration(source)

      assertTrue(result == Right(RenameTarget("test", 25)))
    }
  )

  // Complex Migration Suite
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

      assertTrue(result == Right(ComplexTarget("hello", 42, true, 0L)))
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

      assertTrue(result == Right(ComplexTarget("hello", 42, true, 0L)))
    },
    test("many shared fields - only handle changed fields") {
      val migration = syntax(
        syntax(MigrationBuilder.newBuilder[ManySharedSource, ManySharedTarget])
          .dropField(_.removed, SchemaExpr.Literal[DynamicValue, Double](0.0, Schema.double))
      ).addField(_.added, SchemaExpr.Literal[DynamicValue, Long](0L, Schema.long)).build

      val source = ManySharedSource("a", 1, true, 2.5)
      val result = migration(source)

      assertTrue(result == Right(ManySharedTarget("a", 1, true, 0L)))
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

      assertTrue(result == Right(AllChangedTarget(false, 0.0)))
    }
  )

  // Chaining Styles Suite
  val chainingStylesSuite = suite("chaining styles")(
    test("fully inline chaining with syntax wrapper") {
      val migration = syntax(
        syntax(MigrationBuilder.newBuilder[DropSource, DropTarget])
          .dropField(_.extra, SchemaExpr.Literal[DynamicValue, Boolean](false, Schema.boolean))
      ).build

      val source = DropSource("test", 42, true)
      val result = migration(source)

      assertTrue(result == Right(DropTarget("test", 42)))
    },
    test("step by step with val assignments") {
      val step1: MigrationBuilder[DropSource, DropTarget, TNil, TNil] =
        MigrationBuilder.newBuilder[DropSource, DropTarget]

      val step2 = syntax(step1)
        .dropField(_.extra, SchemaExpr.Literal[DynamicValue, Boolean](false, Schema.boolean))

      val migration = syntax(step2).build

      val source = DropSource("test", 42, true)
      val result = migration(source)

      assertTrue(result == Right(DropTarget("test", 42)))
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

      val source = ComplexSource("hello", 42, true, 3.14)
      val result = migration(source)

      assertTrue(result == Right(ComplexTarget("hello", 42, true, 0L)))
    },
    test("using implicit conversion for build") {

      val builder = syntax(MigrationBuilder.newBuilder[DropSource, DropTarget])
        .dropField(_.extra, SchemaExpr.Literal[DynamicValue, Boolean](false, Schema.boolean))

      // The implicit conversion allows calling build on MigrationBuilder
      val migration: Migration[DropSource, DropTarget] = builder.build

      val source = DropSource("test", 42, true)
      val result = migration(source)

      assertTrue(result == Right(DropTarget("test", 42)))
    },

    test("syntax stored in variable - dropField then build without re-wrap") {
      // Store syntax wrapper in a variable and call methods directly on it
      val ops      = syntax(MigrationBuilder.newBuilder[DropSource, DropTarget])
      val withDrop = ops.dropField(_.extra, SchemaExpr.Literal[DynamicValue, Boolean](false, Schema.boolean))

      val migration = withDrop.build

      val source = DropSource("test", 42, true)
      val result = migration(source)

      assertTrue(result.isRight) &&
      assertTrue(result.map(_.name) == Right("test")) &&
      assertTrue(result.map(_.age) == Right(42))
    },
    test("syntax stored in variable - addField then build without re-wrap") {
      val ops     = syntax(MigrationBuilder.newBuilder[AddSource, AddTarget])
      val withAdd = ops.addField(_.extra, SchemaExpr.Literal[DynamicValue, Boolean](true, Schema.boolean))

      // Key: calling .build directly without re-wrapping
      val migration = withAdd.build

      val source = AddSource("test", 42)
      val result = migration(source)

      assertTrue(result == Right(AddTarget("test", 42, true)))
    },
    test("syntax stored in variable - renameField then build without re-wrap") {
      val ops        = syntax(MigrationBuilder.newBuilder[RenameSource, RenameTarget])
      val withRename = ops.renameField(_.oldName, _.newName)
      // Key: calling .build directly without re-wrapping
      val migration = withRename.build

      val source = RenameSource("John", 30)
      val result = migration(source)

      assertTrue(result.isRight) &&
      assertTrue(result.map(_.newName) == Right("John"))
    },
    test("syntax stored in variable - transformField then build without re-wrap") {
      val ops           = syntax(MigrationBuilder.newBuilder[PersonA, PersonB])
      val withTransform =
        ops.transformField(_.name, SchemaExpr.Literal[DynamicValue, String]("transformed", Schema.string))
      // Key: calling .build directly without re-wrapping
      val migration = withTransform.build

      val source = PersonA("John", 30)
      val result = migration(source)

      assertTrue(result == Right(PersonB("transformed", 30)))
    },
    test("syntax stored in variable - changeFieldType then build without re-wrap") {
      val ops        = syntax(MigrationBuilder.newBuilder[DeepOuterV1, DeepOuterV2])
      val withChange = ops.changeFieldType(_.middle.inner.value, PrimitiveConverter.IntToLong)
      // Key: calling .build directly without re-wrapping
      val migration = withChange.build

      val source = DeepOuterV1(DeepMiddleV1(DeepInnerV1(42)))
      val result = migration(source)

      assertTrue(result == Right(DeepOuterV2(DeepMiddleV2(DeepInnerV2(42L)))))
    },
    test("chained operations with syntax wrappers - build without final re-wrap") {
      // For chained operations, we need syntax() due to Scala 2 lambda type inference
      val step1 = syntax(MigrationBuilder.newBuilder[ComplexSource, ComplexTarget])
        .renameField(_.a, _.x)
      val step2 = syntax(step1).renameField(_.c, _.y)
      val step3 = syntax(step2).dropField(_.d, SchemaExpr.Literal[DynamicValue, Double](0.0, Schema.double))
      val step4 = syntax(step3).addField(_.e, SchemaExpr.Literal[DynamicValue, Long](0L, Schema.long))
      // Key: calling .build directly on step4 without wrapping in syntax()
      val migration = step4.build

      val source = ComplexSource("hello", 42, true, 3.14)
      val result = migration(source)

      assertTrue(result.isRight) &&
      assertTrue(result.map(_.x) == Right("hello")) &&
      assertTrue(result.map(_.b) == Right(42))
    },
    test("chained drops with syntax wrappers - build without final re-wrap") {
      val step1 = syntax(MigrationBuilder.newBuilder[MultiDropSource, MultiDropTarget])
        .dropField(_.drop1, SchemaExpr.Literal[DynamicValue, Int](0, Schema.int))
      val step2 = syntax(step1).dropField(_.drop2, SchemaExpr.Literal[DynamicValue, Boolean](false, Schema.boolean))
      val step3 = syntax(step2).dropField(_.drop3, SchemaExpr.Literal[DynamicValue, Double](0.0, Schema.double))
      // Key: calling .build directly without re-wrapping
      val migration = step3.build

      val source = MultiDropSource("keep", 1, true, 2.5)
      val result = migration(source)

      assertTrue(result.isRight) &&
      assertTrue(result.map(_.keep) == Right("keep"))
    },
    test("chained adds with syntax wrappers - build without final re-wrap") {
      val step1 = syntax(MigrationBuilder.newBuilder[MultiAddSource, MultiAddTarget])
        .addField(_.add1, SchemaExpr.Literal[DynamicValue, Int](42, Schema.int))
      val step2 = syntax(step1).addField(_.add2, SchemaExpr.Literal[DynamicValue, Boolean](true, Schema.boolean))
      val step3 = syntax(step2).addField(_.add3, SchemaExpr.Literal[DynamicValue, Double](3.14, Schema.double))
      // Key: calling .build directly without re-wrapping
      val migration = step3.build

      val source = MultiAddSource("keep")
      val result = migration(source)

      assertTrue(result == Right(MultiAddTarget("keep", 42, true, 3.14)))
    },
    test("nested path with syntax wrappers - build without final re-wrap") {
      val step1 = syntax(MigrationBuilder.newBuilder[NestedPersonV1, NestedPersonV2])
        .dropField(_.address.city, SchemaExpr.Literal[DynamicValue, String]("", Schema.string))
      val step2 =
        syntax(step1).addField(_.address.zip, SchemaExpr.Literal[DynamicValue, String]("00000", Schema.string))
      // Key: calling .build directly without re-wrapping
      val migration = step2.build

      val source = NestedPersonV1("John", AddressV1("Main St", "NYC"))
      val result = migration(source)

      assertTrue(result == Right(NestedPersonV2("John", AddressV2("Main St", "00000"))))
    }
  )

  // Edge Cases Suite
  val edgeCasesSuite = suite("edge cases")(
    test("non-empty source to empty target") {
      val migration = syntax(MigrationBuilder.newBuilder[NonEmptyForEmpty, EmptyTarget])
        .dropField(_.field, SchemaExpr.Literal[DynamicValue, String]("", Schema.string))
        .build

      val source = NonEmptyForEmpty("test")
      val result = migration(source)

      assertTrue(result == Right(EmptyTarget()))
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

      val source = DropSource("test", 42, true)
      val result = migration(source)

      assertTrue(result == Right(DropTarget("transformed", 42)))
    },
    test("superset of required provided fields is OK") {
      val builder = MigrationBuilder.newBuilder[AddSource, AddTarget]
      val withAdd = syntax(builder)
        .addField(_.extra, SchemaExpr.Literal[DynamicValue, Boolean](false, Schema.boolean))

      // Transform a shared field (not required, but allowed)
      val withTransform = syntax(withAdd)
        .transformField(_.name, SchemaExpr.Literal[DynamicValue, String]("transformed", Schema.string))

      val migration = syntax(withTransform).build

      val source = AddSource("test", 42)
      val result = migration(source)

      assertTrue(result == Right(AddTarget("transformed", 42, false)))
    }
  )

  // buildPartial Suite
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

  // Compile Failure Suite (using typeCheck)
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

      assertZIO(result)(isLeft(containsString("Unhandled paths from source")))
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

      assertZIO(result)(isLeft(containsString("Unprovided paths for target")))
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

      assertZIO(result)(isLeft(containsString("Unhandled paths from source")))
    },
    test("build fails when only some fields are handled") {
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
      assertZIO(result)(isLeft(containsString("Unhandled paths from source")))
    },
    test("build fails when only some fields are provided") {
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
      assertZIO(result)(isLeft(containsString("Unprovided paths for target")))
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

  // Nested Path Validation Suite
  val nestedPathValidationSuite = suite("nested path validation")(
    test("unchanged nested structure requires no handling") {

      val migration = syntax(
        syntax(MigrationBuilder.newBuilder[TypeWithNestedSrc, TypeWithNestedTgt])
          .dropField(_.remove, SchemaExpr.Literal[DynamicValue, Boolean](false, Schema.boolean))
      ).addField(_.add, SchemaExpr.Literal[DynamicValue, Double](0.0, Schema.double)).build

      val source = TypeWithNestedSrc(SharedNested(42, "hello"), remove = true)
      val result = migration(source)

      assertTrue(result == Right(TypeWithNestedTgt(SharedNested(42, "hello"), 0.0)))
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

      assertTrue(result == Right(NestedPersonV2("John", AddressV2("Main St", "00000"))))
    },
    test("nested field rename with full path") {
      // Rename handles both source and target paths
      val migration = syntax(MigrationBuilder.newBuilder[NestedPersonV1, NestedPersonV2])
        .renameField(_.address.city, _.address.zip)
        .build

      val source = NestedPersonV1("Jane", AddressV1("Oak Ave", "LA"))
      val result = migration(source)

      assertTrue(result == Right(NestedPersonV2("Jane", AddressV2("Oak Ave", "LA"))))
    },
    test("deeply nested path tracking (3 levels)") {
      // Nested l2, l2.l3, l2.l3.value are shared, only "extra" needs handling
      val migration = syntax(MigrationBuilder.newBuilder[Level1Src, Level1Tgt])
        .dropField(_.extra, SchemaExpr.Literal[DynamicValue, Int](0, Schema.int))
        .build

      val source = Level1Src(Level2(Level3("deep")), 42)
      val result = migration(source)

      assertTrue(result == Right(Level1Tgt(Level2(Level3("deep")))))
    },
    test("deeply nested change requires full path") {
      // The change at middle.inner.value needs to be handled
      val migration = syntax(MigrationBuilder.newBuilder[DeepOuterV1, DeepOuterV2])
        .changeFieldType(_.middle.inner.value, PrimitiveConverter.IntToLong)
        .build

      val source = DeepOuterV1(DeepMiddleV1(DeepInnerV1(42)))
      val result = migration(source)

      assertTrue(result == Right(DeepOuterV2(DeepMiddleV2(DeepInnerV2(42L)))))
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

  // Case Tracking Suite
  val caseTrackingSuite = suite("sealed trait case tracking")(
    test("identical sealed traits require no case handling") {
      // When sealed traits have the same case names, no handling needed
      val migration = syntax(MigrationBuilder.newBuilder[StatusSame, StatusSame]).build

      val activeSource: StatusSame   = ActiveSame("2024-01-01")
      val inactiveSource: StatusSame = InactiveSame()

      assertTrue(
        migration(activeSource) == Right(ActiveSame("2024-01-01")),
        migration(inactiveSource) == Right(InactiveSame())
      )
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
      // Error shows "Unhandled cases from source"
      assertZIO(result)(isLeft(containsString("Unhandled cases from source")))
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
      // Error shows "Unprovided cases for target"
      assertZIO(result)(isLeft(containsString("Unprovided cases for target")))
    },
    test("ShapeTree extracts nested structure correctly") {
      // Verify ShapeTree typeclass extracts full nested structure
      val st = implicitly[ShapeExtraction.ShapeTree[NestedPersonV1]]
      // The tree should contain the full nested structure
      assertTrue(
        st.tree == ShapeNode.RecordNode(
          Map(
            "name"    -> ShapeNode.PrimitiveNode,
            "address" -> ShapeNode.RecordNode(
              Map(
                "street" -> ShapeNode.PrimitiveNode,
                "city"   -> ShapeNode.PrimitiveNode
              )
            )
          )
        )
      )
    }
  )

  // Cross-Path Compile-Time Validation Suite
  val crossPathValidationSuite = suite("cross-path compile-time validation")(
    test("joinFields should fail at compile time when source paths have different parents") {
      val result = typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.migration._
        import zio.blocks.schema.migration.TypeLevel._
        import zio.blocks.schema.migration.MigrationBuilderSyntax._

        case class Meta(id: String)
        case class Data(value: Int)
        case class Source(meta: Meta, data: Data)
        case class Target(result: String)

        object Meta { implicit val schema: Schema[Meta] = Schema.derived }
        object Data { implicit val schema: Schema[Data] = Schema.derived }
        object Source { implicit val schema: Schema[Source] = Schema.derived }
        object Target { implicit val schema: Schema[Target] = Schema.derived }

        syntax(MigrationBuilder.newBuilder[Source, Target])
          .joinFields(
            (_: Target).result,
            Seq((_: Source).meta.id, (_: Source).data.value),
            SchemaExpr.Literal[DynamicValue, String]("combined", Schema.string)
          )
      """)

      assertZIO(result)(isLeft(containsString("joinFields source fields must share common parent")))
    },
    test("splitField should fail at compile time when target paths have different parents") {
      val result = typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.migration._
        import zio.blocks.schema.migration.TypeLevel._
        import zio.blocks.schema.migration.MigrationBuilderSyntax._

        case class Meta(part1: String)
        case class Info(part2: String)
        case class Source(data: String)
        case class Target(meta: Meta, info: Info)

        object Meta { implicit val schema: Schema[Meta] = Schema.derived }
        object Info { implicit val schema: Schema[Info] = Schema.derived }
        object Source { implicit val schema: Schema[Source] = Schema.derived }
        object Target { implicit val schema: Schema[Target] = Schema.derived }

        syntax(MigrationBuilder.newBuilder[Source, Target])
          .splitField(
            (_: Source).data,
            Seq((_: Target).meta.part1, (_: Target).info.part2),
            SchemaExpr.Literal[DynamicValue, String]("split", Schema.string)
          )
      """)

      assertZIO(result)(isLeft(containsString("splitField target fields must share common parent")))
    },
    test("joinFields should compile when source paths share common parent") {
      case class NameSrc(first: String, last: String)
      object NameSrc {
        implicit val schema: Schema[NameSrc] = Schema.derived
      }

      case class NameTgt(full: String)
      object NameTgt {
        implicit val schema: Schema[NameTgt] = Schema.derived
      }

      case class PersonSrc(name: NameSrc)
      object PersonSrc {
        implicit val schema: Schema[PersonSrc] = Schema.derived
      }

      case class PersonTgt(name: NameTgt)
      object PersonTgt {
        implicit val schema: Schema[PersonTgt] = Schema.derived
      }

      // This should compile since both paths share parent "name"
      // name.first and name.last are joined into name.full
      val migration = syntax(MigrationBuilder.newBuilder[PersonSrc, PersonTgt])
        .joinFields(
          (_: PersonTgt).name.full,
          Seq((_: PersonSrc).name.first, (_: PersonSrc).name.last),
          SchemaExpr.Literal[DynamicValue, String]("combined", Schema.string)
        )
        .build

      val source = PersonSrc(NameSrc("John", "Doe"))
      val result = migration(source)

      assertTrue(result == Right(PersonTgt(NameTgt("combined"))))
    },
    test("splitField should compile when target paths share common parent") {
      case class NameSrc(full: String)
      object NameSrc {
        implicit val schema: Schema[NameSrc] = Schema.derived
      }

      case class NameTgt(first: String, last: String)
      object NameTgt {
        implicit val schema: Schema[NameTgt] = Schema.derived
      }

      case class PersonSrc(name: NameSrc)
      object PersonSrc {
        implicit val schema: Schema[PersonSrc] = Schema.derived
      }

      case class PersonTgt(name: NameTgt)
      object PersonTgt {
        implicit val schema: Schema[PersonTgt] = Schema.derived
      }

      // This should compile since both paths share parent "name"
      // name.full is split into name.first and name.last
      val migration = syntax(MigrationBuilder.newBuilder[PersonSrc, PersonTgt])
        .splitField(
          (_: PersonSrc).name.full,
          Seq((_: PersonTgt).name.first, (_: PersonTgt).name.last),
          SchemaExpr.Literal[DynamicValue, String]("split", Schema.string)
        )
        .build

      // Verify migration was created successfully (compile-time validation passed)
      assertTrue(
        migration.sourceSchema == Schema[PersonSrc],
        migration.targetSchema == Schema[PersonTgt]
      )
    },
    test("joinFields with single source path should compile") {
      val migration = syntax(
        syntax(MigrationBuilder.newBuilder[FullNameSource, FullNameTarget])
          .joinFields(
            (_: FullNameTarget).fullName,
            Seq((_: FullNameSource).firstName),
            SchemaExpr.Literal[DynamicValue, String]("value", Schema.string)
          )
      ).dropField(
        (_: FullNameSource).lastName,
        SchemaExpr.Literal[DynamicValue, String]("", Schema.string)
      ).build

      val source = FullNameSource("John", "Doe", 30)
      val result = migration(source)

      assertTrue(result == Right(FullNameTarget("value", 30)))
    },
    test("splitField with single target path should compile") {
      val migration = syntax(
        syntax(MigrationBuilder.newBuilder[FullNameTarget, FullNameSource])
          .splitField(
            (_: FullNameTarget).fullName,
            Seq((_: FullNameSource).firstName),
            SchemaExpr.Literal[DynamicValue, String]("value", Schema.string)
          )
      ).addField(
        (_: FullNameSource).lastName,
        SchemaExpr.Literal[DynamicValue, String]("", Schema.string)
      ).build

      val source = FullNameTarget("John Doe", 30)
      val result = migration(source)

      assertTrue(result == Right(FullNameSource("value", "", 30)))
    }
  )

  // Join/Split Field Tracking Suite
  val joinSplitTrackingSuite = suite("joinFields and splitField tracking")(
    test("joinFields tracks all source fields in Handled") {
      // joinFields should handle firstName and lastName, and provide fullName
      val migration = syntax(MigrationBuilder.newBuilder[FullNameSource, FullNameTarget])
        .joinFields(
          (t: FullNameTarget) => t.fullName,
          Seq((s: FullNameSource) => s.firstName, (s: FullNameSource) => s.lastName),
          SchemaExpr.Literal[DynamicValue, String]("combined", Schema.string)
        )
        .build

      val source = FullNameSource("John", "Doe", 30)
      val result = migration(source)

      // If joinFields didn't track firstName and lastName as Handled, .build wouldn't compile
      assertTrue(result == Right(FullNameTarget("combined", 30)))
    },
    test("splitField tracks all target fields in Provided") {
      // splitField should handle fullName and provide firstName and lastName
      val migration = syntax(MigrationBuilder.newBuilder[FullNameTarget, FullNameSource])
        .splitField(
          (s: FullNameTarget) => s.fullName,
          Seq((t: FullNameSource) => t.firstName, (t: FullNameSource) => t.lastName),
          SchemaExpr.Literal[DynamicValue, String]("split", Schema.string)
        )
        .build

      // If splitField didn't track firstName and lastName as Provided, .build wouldn't compile
      assertTrue(
        migration.sourceSchema == Schema[FullNameTarget],
        migration.targetSchema == Schema[FullNameSource]
      )
    },
    test("joinFields without tracking all sources fails to compile") {
      val result = typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.migration._
        import zio.blocks.schema.migration.TypeLevel._
        import zio.blocks.schema.migration.MigrationBuilderSyntax._

        case class Source(a: String, b: String, shared: Int)
        object Source { implicit val schema: Schema[Source] = Schema.derived }

        case class Target(combined: String, shared: Int)
        object Target { implicit val schema: Schema[Target] = Schema.derived }

        // Without joinFields handling a and b, this should fail
        syntax(MigrationBuilder.newBuilder[Source, Target])
          .addField((_: Target).combined, SchemaExpr.Literal[DynamicValue, String]("default", Schema.string))
          .build
      """)

      assertZIO(result)(isLeft)
    },
    test("splitField without tracking all targets fails to compile") {
      val result = typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.migration._
        import zio.blocks.schema.migration.TypeLevel._
        import zio.blocks.schema.migration.MigrationBuilderSyntax._

        case class Source(combined: String, shared: Int)
        object Source { implicit val schema: Schema[Source] = Schema.derived }

        case class Target(a: String, b: String, shared: Int)
        object Target { implicit val schema: Schema[Target] = Schema.derived }

        // Without splitField providing a and b, this should fail
        syntax(MigrationBuilder.newBuilder[Source, Target])
          .dropField((_: Source).combined, SchemaExpr.Literal[DynamicValue, String]("default", Schema.string))
          .build
      """)

      assertZIO(result)(isLeft)
    }
  )

  // Container Types Suite=
  val containerTypesSuite = suite("container type migrations")(
    test("Option field migration works") {
      case class WithOptionSrc(name: String, data: Option[String])
      object WithOptionSrc {
        implicit val schema: Schema[WithOptionSrc] = Schema.derived
      }

      case class WithOptionTgt(name: String, data: Option[String], extra: Int)
      object WithOptionTgt {
        implicit val schema: Schema[WithOptionTgt] = Schema.derived
      }

      val migration = syntax(MigrationBuilder.newBuilder[WithOptionSrc, WithOptionTgt])
        .addField(_.extra, SchemaExpr.Literal[DynamicValue, Int](0, Schema.int))
        .build

      val result = migration(WithOptionSrc("test", Some("value")))
      assertTrue(result == Right(WithOptionTgt("test", Some("value"), 0)))
    },
    test("List field migration works") {
      case class WithListSrc(name: String, items: List[String])
      object WithListSrc {
        implicit val schema: Schema[WithListSrc] = Schema.derived
      }

      case class WithListTgt(name: String, items: List[String], count: Int)
      object WithListTgt {
        implicit val schema: Schema[WithListTgt] = Schema.derived
      }

      val migration = syntax(MigrationBuilder.newBuilder[WithListSrc, WithListTgt])
        .addField(_.count, SchemaExpr.Literal[DynamicValue, Int](0, Schema.int))
        .build

      val result = migration(WithListSrc("test", List("a", "b")))
      assertTrue(result == Right(WithListTgt("test", List("a", "b"), 0)))
    },
    test("Set field migration works") {
      case class WithSetSrc(name: String, tags: Set[String])
      object WithSetSrc {
        implicit val schema: Schema[WithSetSrc] = Schema.derived
      }

      case class WithSetTgt(name: String, tags: Set[String], active: Boolean)
      object WithSetTgt {
        implicit val schema: Schema[WithSetTgt] = Schema.derived
      }

      val migration = syntax(MigrationBuilder.newBuilder[WithSetSrc, WithSetTgt])
        .addField(_.active, SchemaExpr.Literal[DynamicValue, Boolean](true, Schema.boolean))
        .build

      val result = migration(WithSetSrc("test", Set("tag1", "tag2")))
      assertTrue(result == Right(WithSetTgt("test", Set("tag1", "tag2"), true)))
    },
    test("Map field migration works") {
      case class WithMapSrc(name: String, data: Map[String, Int])
      object WithMapSrc {
        implicit val schema: Schema[WithMapSrc] = Schema.derived
      }

      case class WithMapTgt(name: String, data: Map[String, Int], version: Long)
      object WithMapTgt {
        implicit val schema: Schema[WithMapTgt] = Schema.derived
      }

      val migration = syntax(MigrationBuilder.newBuilder[WithMapSrc, WithMapTgt])
        .addField(_.version, SchemaExpr.Literal[DynamicValue, Long](1L, Schema.long))
        .build

      val result = migration(WithMapSrc("test", Map("a" -> 1)))
      assertTrue(result == Right(WithMapTgt("test", Map("a" -> 1), 1L)))
    },
    test("Vector field migration works") {
      case class WithVectorSrc(name: String, values: Vector[Double])
      object WithVectorSrc {
        implicit val schema: Schema[WithVectorSrc] = Schema.derived
      }

      case class WithVectorTgt(name: String, values: Vector[Double], sum: Double)
      object WithVectorTgt {
        implicit val schema: Schema[WithVectorTgt] = Schema.derived
      }

      val migration = syntax(MigrationBuilder.newBuilder[WithVectorSrc, WithVectorTgt])
        .addField(_.sum, SchemaExpr.Literal[DynamicValue, Double](0.0, Schema.double))
        .build

      val result = migration(WithVectorSrc("test", Vector(1.0, 2.0)))
      assertTrue(result == Right(WithVectorTgt("test", Vector(1.0, 2.0), 0.0)))
    },
    test("nested case class in Option migration works") {
      case class Inner(x: Int, y: String)
      object Inner {
        implicit val schema: Schema[Inner] = Schema.derived
      }

      case class OuterSrc(name: String, inner: Option[Inner])
      object OuterSrc {
        implicit val schema: Schema[OuterSrc] = Schema.derived
      }

      case class OuterTgt(name: String, inner: Option[Inner], extra: Boolean)
      object OuterTgt {
        implicit val schema: Schema[OuterTgt] = Schema.derived
      }

      val migration = syntax(MigrationBuilder.newBuilder[OuterSrc, OuterTgt])
        .addField(_.extra, SchemaExpr.Literal[DynamicValue, Boolean](false, Schema.boolean))
        .build

      val result = migration(OuterSrc("test", Some(Inner(1, "hello"))))
      assertTrue(result == Right(OuterTgt("test", Some(Inner(1, "hello")), false)))
    },
    test("nested case class in List migration works") {
      case class Item(id: Int, name: String)
      object Item {
        implicit val schema: Schema[Item] = Schema.derived
      }

      case class ContainerSrc(items: List[Item])
      object ContainerSrc {
        implicit val schema: Schema[ContainerSrc] = Schema.derived
      }

      case class ContainerTgt(items: List[Item], count: Int)
      object ContainerTgt {
        implicit val schema: Schema[ContainerTgt] = Schema.derived
      }

      val migration = syntax(MigrationBuilder.newBuilder[ContainerSrc, ContainerTgt])
        .addField(_.count, SchemaExpr.Literal[DynamicValue, Int](0, Schema.int))
        .build

      val result = migration(ContainerSrc(List(Item(1, "a"), Item(2, "b"))))
      assertTrue(result == Right(ContainerTgt(List(Item(1, "a"), Item(2, "b")), 0)))
    },
    test("deeply nested containers migration works") {
      case class DeepContainerSrc(data: Option[List[Map[String, Int]]])
      object DeepContainerSrc {
        implicit val schema: Schema[DeepContainerSrc] = Schema.derived
      }

      case class DeepContainerTgt(data: Option[List[Map[String, Int]]], meta: String)
      object DeepContainerTgt {
        implicit val schema: Schema[DeepContainerTgt] = Schema.derived
      }

      val migration = syntax(MigrationBuilder.newBuilder[DeepContainerSrc, DeepContainerTgt])
        .addField(_.meta, SchemaExpr.Literal[DynamicValue, String]("", Schema.string))
        .build

      val result = migration(DeepContainerSrc(Some(List(Map("a" -> 1)))))
      assertTrue(result == Right(DeepContainerTgt(Some(List(Map("a" -> 1))), "")))
    }
  )

  // Additional Case Tracking Suite
  val additionalCaseTrackingSuite = suite("additional sealed trait case tracking")(
    test("ShapeTree returns RecordNode for non-sealed types") {
      // Verify ShapeTree extracts RecordNode for regular case classes
      val st = implicitly[ShapeExtraction.ShapeTree[PersonA]]
      // The tree should be a RecordNode (not SealedNode)
      assertTrue(
        st.tree == ShapeNode.RecordNode(
          Map(
            "name" -> ShapeNode.PrimitiveNode,
            "age"  -> ShapeNode.PrimitiveNode
          )
        )
      )
    },
    test("different case names require handling") {
      val result = typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.migration._
        import zio.blocks.schema.migration.TypeLevel._
        import zio.blocks.schema.migration.MigrationBuilderSyntax._

        sealed trait StatusV1
        case class Active(since: String) extends StatusV1
        case class Inactive() extends StatusV1
        object StatusV1 { implicit val schema: Schema[StatusV1] = Schema.derived }
        object Active { implicit val schema: Schema[Active] = Schema.derived }
        object Inactive { implicit val schema: Schema[Inactive] = Schema.derived }

        sealed trait StatusV2
        case class Active2(since: String) extends StatusV2
        case class Inactive2() extends StatusV2
        object StatusV2 { implicit val schema: Schema[StatusV2] = Schema.derived }
        object Active2 { implicit val schema: Schema[Active2] = Schema.derived }
        object Inactive2 { implicit val schema: Schema[Inactive2] = Schema.derived }

        // This should fail - case names changed but not handled
        MigrationBuilder.newBuilder[StatusV1, StatusV2].build
      """)

      assertZIO(result)(isLeft)
    },
    test("case rename requires handling") {
      val result = typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.migration._
        import zio.blocks.schema.migration.TypeLevel._
        import zio.blocks.schema.migration.MigrationBuilderSyntax._

        sealed trait PaymentV1
        case class CardPayment(number: String) extends PaymentV1
        case class CashPayment(amount: Int) extends PaymentV1
        object PaymentV1 { implicit val schema: Schema[PaymentV1] = Schema.derived }
        object CardPayment { implicit val schema: Schema[CardPayment] = Schema.derived }
        object CashPayment { implicit val schema: Schema[CashPayment] = Schema.derived }

        sealed trait PaymentV2
        case class CreditCard(number: String) extends PaymentV2
        case class CashPayment2(amount: Int) extends PaymentV2
        object PaymentV2 { implicit val schema: Schema[PaymentV2] = Schema.derived }
        object CreditCard { implicit val schema: Schema[CreditCard] = Schema.derived }
        object CashPayment2 { implicit val schema: Schema[CashPayment2] = Schema.derived }

        // This should fail - case renames not handled
        MigrationBuilder.newBuilder[PaymentV1, PaymentV2].build
      """)

      assertZIO(result)(isLeft)
    },
    test("mixed field and case changes fails without handling") {
      val result = typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.migration._
        import zio.blocks.schema.migration.TypeLevel._
        import zio.blocks.schema.migration.MigrationBuilderSyntax._

        sealed trait ContainerV1
        case class BoxV1(value: Int, label: String) extends ContainerV1
        object ContainerV1 { implicit val schema: Schema[ContainerV1] = Schema.derived }
        object BoxV1 { implicit val schema: Schema[BoxV1] = Schema.derived }

        sealed trait ContainerV2
        case class BoxV2(value: Int) extends ContainerV2 // Renamed case, removed field
        object ContainerV2 { implicit val schema: Schema[ContainerV2] = Schema.derived }
        object BoxV2 { implicit val schema: Schema[BoxV2] = Schema.derived }

        // This should fail - case rename and field changes not handled
        MigrationBuilder.newBuilder[ContainerV1, ContainerV2].build
      """)

      assertZIO(result)(isLeft)
    }
  )

  // ShapeTree Edge Cases Suite
  val shapeTreeEdgeCasesSuite = suite("ShapeTree edge cases")(
    test("empty case class ShapeTree extracts correctly") {
      // Verify ShapeTree extracts empty RecordNode for empty case classes
      val st = implicitly[ShapeExtraction.ShapeTree[EmptySource]]
      // The tree should be a RecordNode with empty fields map
      assertTrue(st.tree == ShapeNode.RecordNode(Map.empty))
    },
    test("single field case class ShapeTree extracts correctly") {
      // Verify ShapeTree extracts single field
      val st = implicitly[ShapeExtraction.ShapeTree[SingleA]]
      // The tree should be a RecordNode with one field
      assertTrue(
        st.tree == ShapeNode.RecordNode(
          Map("only" -> ShapeNode.PrimitiveNode)
        )
      )
    },
    test("ShapeTree extracts cases for sealed traits") {
      // Verify ShapeTree extracts case names for sealed traits as SealedNode
      val st = implicitly[ShapeExtraction.ShapeTree[ResultV1]]
      // The tree should be a SealedNode with "SuccessV1" and "FailureV1" cases
      assertTrue(
        st.tree == ShapeNode.SealedNode(
          Map(
            "SuccessV1" -> ShapeNode.RecordNode(
              Map("value" -> ShapeNode.PrimitiveNode)
            ),
            "FailureV1" -> ShapeNode.RecordNode(
              Map("err" -> ShapeNode.PrimitiveNode)
            )
          )
        )
      )
    }
  )

  // Edge Cases Suite
  val EdgeCasesSuite = suite("edge cases")(
    test("container types extract with ShapeTree") {
      // Container fields should have proper ShapeNode representation
      val st = implicitly[ShapeExtraction.ShapeTree[WithContainers]]
      // ShapeTree should show SeqNode, OptionNode, MapNode for container fields
      assertTrue(
        st.tree == ShapeNode.RecordNode(
          Map(
            "opt" -> ShapeNode.OptionNode(
              ShapeNode.RecordNode(
                Map(
                  "street" -> ShapeNode.PrimitiveNode,
                  "city"   -> ShapeNode.PrimitiveNode
                )
              )
            ),
            "list" -> ShapeNode.SeqNode(ShapeNode.PrimitiveNode),
            "set"  -> ShapeNode.SeqNode(ShapeNode.PrimitiveNode),
            "map"  -> ShapeNode.MapNode(
              ShapeNode.PrimitiveNode,
              ShapeNode.RecordNode(
                Map(
                  "street" -> ShapeNode.PrimitiveNode,
                  "city"   -> ShapeNode.PrimitiveNode
                )
              )
            )
          )
        )
      )
    },
    test("deeply nested containers extract correctly") {
      // List[Option[Map[String, Vector[Int]]]] should extract as nested ShapeNodes
      val st = implicitly[ShapeExtraction.ShapeTree[DeeplyNestedContainers]]
      assertTrue(
        st.tree == ShapeNode.RecordNode(
          Map(
            "items" -> ShapeNode.SeqNode(
              ShapeNode.OptionNode(
                ShapeNode.MapNode(
                  ShapeNode.PrimitiveNode,
                  ShapeNode.SeqNode(ShapeNode.PrimitiveNode)
                )
              )
            )
          )
        )
      )
    },
    test("migration with container fields works") {
      // Containers with different element types should still migrate if container field names match
      val migration = syntax(MigrationBuilder.newBuilder[WithContainers, WithContainers]).build
      val source    = WithContainers(
        Some(AddressV1("Main St", "NYC")),
        List("a", "b"),
        Set(1, 2),
        Map("k" -> AddressV1("Oak Ave", "LA"))
      )
      val result = migration(source)
      assertTrue(result == Right(source))
    },
    test("sealed trait with case objects extracts all cases") {
      // ShapeTree should include both case classes and case objects as SealedNode
      val st = implicitly[ShapeExtraction.ShapeTree[WithCaseObject]]
      // Should have "NoneValue" and "SomeValue" cases
      assertTrue(
        st.tree == ShapeNode.SealedNode(
          Map(
            "SomeValue" -> ShapeNode.RecordNode(
              Map("x" -> ShapeNode.PrimitiveNode)
            ),
            "NoneValue" -> ShapeNode.RecordNode(Map.empty)
          )
        )
      )
    },
    test("enum-like sealed trait with only case objects") {
      // All case objects should be extracted
      val st = implicitly[ShapeExtraction.ShapeTree[Color]]
      // Should have "Blue", "Green", "Red" cases
      assertTrue(
        st.tree == ShapeNode.SealedNode(
          Map(
            "Red"   -> ShapeNode.RecordNode(Map.empty),
            "Green" -> ShapeNode.RecordNode(Map.empty),
            "Blue"  -> ShapeNode.RecordNode(Map.empty)
          )
        )
      )
    },
    test("identical sealed traits with case objects require no handling") {
      // Same case object structure should compile without handling
      val migration                  = syntax(MigrationBuilder.newBuilder[WithCaseObject, WithCaseObject]).build
      val someSource: WithCaseObject = SomeValue(42)
      val noneSource: WithCaseObject = NoneValue

      assertTrue(
        migration(someSource) == Right(SomeValue(42)),
        migration(noneSource) == Right(NoneValue)
      )
    },
    test("identical enum-like sealed traits require no handling") {
      // Same enum structure should compile without handling
      val migration = syntax(MigrationBuilder.newBuilder[Color, Color]).build

      assertTrue(
        migration(Red) == Right(Red),
        migration(Green) == Right(Green),
        migration(Blue) == Right(Blue)
      )
    },
    test("empty case class migration works") {
      val migration = syntax(MigrationBuilder.newBuilder[EmptySource, EmptyTarget]).build
      val result    = migration(EmptySource())
      assertTrue(result == Right(EmptyTarget()))
    },
    test("single field case class migration works") {
      val migration = syntax(MigrationBuilder.newBuilder[SingleA, SingleB]).build
      val result    = migration(SingleA("test"))
      assertTrue(result == Right(SingleB("test")))
    },
    test("error message format is multi-line") {
      val result = typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.migration._
        import zio.blocks.schema.migration.TypeLevel._
        import zio.blocks.schema.migration.MigrationBuilderSyntax._

        case class Src(a: Int, b: String)
        object Src { implicit val schema: Schema[Src] = Schema.derived }

        case class Tgt(c: Boolean)
        object Tgt { implicit val schema: Schema[Tgt] = Schema.derived }

        MigrationBuilder.newBuilder[Src, Tgt].build
      """)

      // Error should contain multi-line format with "Unhandled paths" and "Unprovided paths"
      assertZIO(result)(isLeft(containsString("Unhandled paths from source")))
    },
    test("error message includes hint with example path") {
      val result = typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.migration._
        import zio.blocks.schema.migration.TypeLevel._
        import zio.blocks.schema.migration.MigrationBuilderSyntax._

        case class Src(fieldToRemove: Int)
        object Src { implicit val schema: Schema[Src] = Schema.derived }

        case class Tgt()
        object Tgt { implicit val schema: Schema[Tgt] = Schema.derived }

        MigrationBuilder.newBuilder[Src, Tgt].build
      """)

      // Error should contain hint with example selector path
      assertZIO(result)(isLeft(containsString("Hint: Use .dropField(_.fieldToRemove")))
    }
  )
}
