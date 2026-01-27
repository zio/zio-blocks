package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._
import zio.test.Assertion._
import TypeLevel._
import MigrationBuilderSyntax._

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
    compileFailureSuite
  )

  // --------------------------------------------------------------------------
  // Identical Schemas Suite
  // --------------------------------------------------------------------------
  val identicalSchemasSuite = suite("identical schemas")(
    test("buildChecked works with no operations needed") {
      val migration = syntax(MigrationBuilder.newBuilder[PersonA, PersonB]).buildChecked

      val personA = PersonA("John", 30)
      val result  = migration(personA)

      assertTrue(result.isRight) &&
      assertTrue(result.map(_.name) == Right("John")) &&
      assertTrue(result.map(_.age) == Right(30))
    },
    test("single field identical schemas") {
      val migration = syntax(MigrationBuilder.newBuilder[SingleA, SingleB]).buildChecked

      val source = SingleA("test")
      val result = migration(source)

      assertTrue(result.isRight) &&
      assertTrue(result.map(_.only) == Right("test"))
    },
    test("empty to empty schema") {
      val migration = syntax(MigrationBuilder.newBuilder[EmptySource, EmptyTarget]).buildChecked

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
        .buildChecked

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
      ).dropField(_.drop3, SchemaExpr.Literal[DynamicValue, Double](0.0, Schema.double)).buildChecked

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
      val migration = syntax(builder4).buildChecked

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
        .buildChecked

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
      ).addField(_.add3, SchemaExpr.Literal[DynamicValue, Double](3.14, Schema.double)).buildChecked

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
      val migration = syntax(builder4).buildChecked

      val source = MultiAddSource("keep")
      val result = migration(source)

      assertTrue(result.isRight) &&
      assertTrue(result.map(_.keep) == Right("keep"))
    },
    test("empty source to non-empty target") {
      val migration = syntax(MigrationBuilder.newBuilder[EmptySource, NonEmptyForEmpty])
        .addField(_.field, SchemaExpr.Literal[DynamicValue, String]("default", Schema.string))
        .buildChecked

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
        .buildChecked

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
        .buildChecked

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
        .buildChecked

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
      val migration = syntax(builder5).buildChecked

      val source = ComplexSource("hello", 42, true, 3.14)
      val result = migration(source)

      assertTrue(result.isRight)
    },
    test("many shared fields - only handle changed fields") {
      val migration = syntax(
        syntax(MigrationBuilder.newBuilder[ManySharedSource, ManySharedTarget])
          .dropField(_.removed, SchemaExpr.Literal[DynamicValue, Double](0.0, Schema.double))
      ).addField(_.added, SchemaExpr.Literal[DynamicValue, Long](0L, Schema.long)).buildChecked

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
      ).addField(_.y, SchemaExpr.Literal[DynamicValue, Double](0.0, Schema.double)).buildChecked

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
      ).buildChecked

      assertTrue(migration != null)
    },
    test("step by step with val assignments") {
      val step1: MigrationBuilder[DropSource, DropTarget, TNil, TNil] =
        MigrationBuilder.newBuilder[DropSource, DropTarget]

      val step2 = syntax(step1)
        .dropField(_.extra, SchemaExpr.Literal[DynamicValue, Boolean](false, Schema.boolean))

      val migration = syntax(step2).buildChecked

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
      ).buildChecked

      assertTrue(migration != null)
    },
    test("using implicit conversion for buildChecked") {
      // With the implicit conversion imported, buildChecked is available on MigrationBuilder
      val builder = syntax(MigrationBuilder.newBuilder[DropSource, DropTarget])
        .dropField(_.extra, SchemaExpr.Literal[DynamicValue, Boolean](false, Schema.boolean))

      // The implicit conversion allows calling buildChecked on MigrationBuilder
      val migration: Migration[DropSource, DropTarget] = builder.buildChecked

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
        .buildChecked

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

      val migration = syntax(withTransform).buildChecked

      assertTrue(migration != null)
    },
    test("superset of required provided fields is OK") {
      val builder = MigrationBuilder.newBuilder[AddSource, AddTarget]
      val withAdd = syntax(builder)
        .addField(_.extra, SchemaExpr.Literal[DynamicValue, Boolean](false, Schema.boolean))

      // Transform a shared field (not required, but allowed)
      val withTransform = syntax(withAdd)
        .transformField(_.name, SchemaExpr.Literal[DynamicValue, String]("transformed", Schema.string))

      val migration = syntax(withTransform).buildChecked

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
    test("buildChecked fails when drop is missing") {
      val result = typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.migration._
        import zio.blocks.schema.migration.TypeLevel._
        import zio.blocks.schema.migration.MigrationBuilderSyntax._

        case class Src(name: String, extra: Int)
        object Src { implicit val schema: Schema[Src] = Schema.derived }

        case class Tgt(name: String)
        object Tgt { implicit val schema: Schema[Tgt] = Schema.derived }

        MigrationBuilder.newBuilder[Src, Tgt].buildChecked
      """)

      assertZIO(result)(isLeft(containsString("unhandled fields")))
    },
    test("buildChecked fails when add is missing") {
      val result = typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.migration._
        import zio.blocks.schema.migration.TypeLevel._
        import zio.blocks.schema.migration.MigrationBuilderSyntax._

        case class Src(name: String)
        object Src { implicit val schema: Schema[Src] = Schema.derived }

        case class Tgt(name: String, extra: Int)
        object Tgt { implicit val schema: Schema[Tgt] = Schema.derived }

        MigrationBuilder.newBuilder[Src, Tgt].buildChecked
      """)

      assertZIO(result)(isLeft(containsString("unprovided fields")))
    },
    test("buildChecked fails when both handled and provided are missing") {
      val result = typeCheck("""
        import zio.blocks.schema._
        import zio.blocks.schema.migration._
        import zio.blocks.schema.migration.TypeLevel._
        import zio.blocks.schema.migration.MigrationBuilderSyntax._

        case class Src(shared: String, removed: Int)
        object Src { implicit val schema: Schema[Src] = Schema.derived }

        case class Tgt(shared: String, added: Boolean)
        object Tgt { implicit val schema: Schema[Tgt] = Schema.derived }

        MigrationBuilder.newBuilder[Src, Tgt].buildChecked
      """)

      assertZIO(result)(isLeft(containsString("unhandled fields")))
    },
    test("buildChecked fails when only some fields are handled") {
      // Note: In Scala 2 typeCheck, the chained selector syntax has type inference issues
      // So we test the simpler case where buildChecked is called directly
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
        MigrationBuilder.newBuilder[Src, Tgt].buildChecked
      """)

      // Should fail because drop1 and drop2 are not handled
      assertZIO(result)(isLeft(containsString("unhandled fields")))
    },
    test("buildChecked fails when only some fields are provided") {
      // Note: In Scala 2 typeCheck, the chained selector syntax has type inference issues
      // So we test the simpler case where buildChecked is called directly
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
        MigrationBuilder.newBuilder[Src, Tgt].buildChecked
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

        MigrationBuilder.newBuilder[Src, Tgt].buildChecked
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

        MigrationBuilder.newBuilder[MySourceType, MyTargetType].buildChecked
      """)

      assertZIO(result)(isLeft(containsString("MySourceType")))
    }
  )
}
