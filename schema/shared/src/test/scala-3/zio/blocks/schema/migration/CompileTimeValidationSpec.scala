package zio.blocks.schema.migration

import zio.test._
import zio.test.Assertion
import zio.blocks.schema._
import zio.blocks.schema.migration.FieldExtraction._
import zio.blocks.schema.migration.TypeLevel._

/**
 * Tests for compile-time migration validation.
 *
 * These tests verify that the ValidationProof typeclass correctly enforces
 * migration completeness at compile time.
 */
object CompileTimeValidationSpec extends ZIOSpecDefault {

  // Test case classes - identical schemas
  case class PersonA(name: String, age: Int)
  case class PersonB(name: String, age: Int)

  // Test case classes - field renamed
  case class RenameSource(oldName: String, age: Int)
  case class RenameTarget(newName: String, age: Int)

  // Test case classes - field dropped
  case class DropSource(name: String, age: Int, extra: Boolean)
  case class DropTarget(name: String, age: Int)

  // Test case classes - field added
  case class AddSource(name: String, age: Int)
  case class AddTarget(name: String, age: Int, extra: Boolean)

  // Test case classes - complex: drop + add + rename
  case class ComplexSource(a: String, b: Int, c: Boolean, d: Double)
  case class ComplexTarget(x: String, b: Int, y: Boolean, e: Long)

  // Test case classes - many shared fields
  case class ManySharedSource(shared1: String, shared2: Int, shared3: Boolean, removed: Double)
  case class ManySharedTarget(shared1: String, shared2: Int, shared3: Boolean, added: Long)

  // Schemas
  implicit val personASchema: Schema[PersonA]                   = Schema.derived
  implicit val personBSchema: Schema[PersonB]                   = Schema.derived
  implicit val renameSourceSchema: Schema[RenameSource]         = Schema.derived
  implicit val renameTargetSchema: Schema[RenameTarget]         = Schema.derived
  implicit val dropSourceSchema: Schema[DropSource]             = Schema.derived
  implicit val dropTargetSchema: Schema[DropTarget]             = Schema.derived
  implicit val addSourceSchema: Schema[AddSource]               = Schema.derived
  implicit val addTargetSchema: Schema[AddTarget]               = Schema.derived
  implicit val complexSourceSchema: Schema[ComplexSource]       = Schema.derived
  implicit val complexTargetSchema: Schema[ComplexTarget]       = Schema.derived
  implicit val manySharedSourceSchema: Schema[ManySharedSource] = Schema.derived
  implicit val manySharedTargetSchema: Schema[ManySharedTarget] = Schema.derived

  def spec = suite("CompileTimeValidationSpec")(
    suite("ValidationProof typeclass")(
      test("proof exists for empty migration with identical schemas") {
        // PersonA and PersonB have the same fields, so no handling/providing needed
        val fnA = summon[FieldNames[PersonA]]
        val fnB = summon[FieldNames[PersonB]]

        // Verify fields are the same
        summon[fnA.Labels =:= ("name", "age")]
        summon[fnB.Labels =:= ("name", "age")]

        // Empty tuples should be sufficient since there are no removed/added fields
        summon[ValidationProof[PersonA, PersonB, EmptyTuple, EmptyTuple]]
        assertTrue(true)
      },
      test("proof exists when all removed fields are handled") {
        // DropSource -> DropTarget requires handling "extra"
        val fnA = summon[FieldNames[DropSource]]
        val fnB = summon[FieldNames[DropTarget]]

        // Verify the difference
        type Removed = Difference[fnA.Labels, fnB.Labels]
        summon[Contains[Removed, "extra"] =:= true]

        // Proof should exist when "extra" is handled
        summon[ValidationProof[DropSource, DropTarget, Tuple1["extra"], EmptyTuple]]
        assertTrue(true)
      },
      test("proof exists when all added fields are provided") {
        // AddSource -> AddTarget requires providing "extra"
        val fnA = summon[FieldNames[AddSource]]
        val fnB = summon[FieldNames[AddTarget]]

        // Verify the difference
        type Added = Difference[fnB.Labels, fnA.Labels]
        summon[Contains[Added, "extra"] =:= true]

        // Proof should exist when "extra" is provided
        summon[ValidationProof[AddSource, AddTarget, EmptyTuple, Tuple1["extra"]]]
        assertTrue(true)
      },
      test("proof exists for complete rename migration") {
        // RenameSource -> RenameTarget requires handling "oldName" and providing "newName"
        summon[ValidationProof[RenameSource, RenameTarget, Tuple1["oldName"], Tuple1["newName"]]]
        assertTrue(true)
      },
      test("proof exists with superset of required fields") {
        // Having extra fields handled/provided beyond what's required is OK
        summon[ValidationProof[DropSource, DropTarget, ("extra", "name"), Tuple1["age"]]]
        assertTrue(true)
      }
    ),
    suite("ValidationProof compile failures")(
      test("no proof when required handled field is missing") {
        assertZIO(typeCheck("""
          import zio.blocks.schema.migration._
          import zio.blocks.schema.migration.FieldExtraction._
          import zio.blocks.schema._

          case class Src(a: String, removed: Int)
          case class Tgt(a: String)
          implicit val srcSchema: Schema[Src] = Schema.derived
          implicit val tgtSchema: Schema[Tgt] = Schema.derived

          // "removed" needs to be handled but EmptyTuple doesn't contain it
          summon[ValidationProof[Src, Tgt, EmptyTuple, EmptyTuple]]
        """))(Assertion.isLeft)
      },
      test("no proof when required provided field is missing") {
        assertZIO(typeCheck("""
          import zio.blocks.schema.migration._
          import zio.blocks.schema.migration.FieldExtraction._
          import zio.blocks.schema._

          case class Src(a: String)
          case class Tgt(a: String, added: Int)
          implicit val srcSchema: Schema[Src] = Schema.derived
          implicit val tgtSchema: Schema[Tgt] = Schema.derived

          // "added" needs to be provided but EmptyTuple doesn't contain it
          summon[ValidationProof[Src, Tgt, EmptyTuple, EmptyTuple]]
        """))(Assertion.isLeft)
      },
      test("no proof when both handled and provided are missing") {
        assertZIO(typeCheck("""
          import zio.blocks.schema.migration._
          import zio.blocks.schema.migration.FieldExtraction._
          import zio.blocks.schema._

          case class Src(shared: String, removed: Int)
          case class Tgt(shared: String, added: Boolean)
          implicit val srcSchema: Schema[Src] = Schema.derived
          implicit val tgtSchema: Schema[Tgt] = Schema.derived

          summon[ValidationProof[Src, Tgt, EmptyTuple, EmptyTuple]]
        """))(Assertion.isLeft)
      },
      test("no proof when handled exists but provided is missing") {
        assertZIO(typeCheck("""
          import zio.blocks.schema.migration._
          import zio.blocks.schema.migration.FieldExtraction._
          import zio.blocks.schema._

          case class Src(shared: String, removed: Int)
          case class Tgt(shared: String, added: Boolean)
          implicit val srcSchema: Schema[Src] = Schema.derived
          implicit val tgtSchema: Schema[Tgt] = Schema.derived

          // "removed" is handled, but "added" is not provided
          summon[ValidationProof[Src, Tgt, Tuple1["removed"], EmptyTuple]]
        """))(Assertion.isLeft)
      }
    ),
    suite("buildChecked method")(
      test("buildChecked compiles for identical schemas") {
        val migration = MigrationBuilder
          .newBuilder[PersonA, PersonB]
          .buildChecked

        val personA = PersonA("John", 30)
        val result  = migration(personA)

        assertTrue(result.isRight) &&
        assertTrue(result.map(_.name == "John").getOrElse(false))
      },
      test("buildChecked compiles for complete drop migration") {
        val migration = MigrationBuilder
          .newBuilder[DropSource, DropTarget]
          .dropField(_.extra, SchemaExpr.Literal[DynamicValue, Boolean](false, Schema.boolean))
          .buildChecked

        val source = DropSource("John", 30, true)
        val result = migration(source)

        assertTrue(result.isRight)
      },
      test("buildChecked compiles for complete add migration") {
        val migration = MigrationBuilder
          .newBuilder[AddSource, AddTarget]
          .addField(_.extra, SchemaExpr.Literal[DynamicValue, Boolean](false, Schema.boolean))
          .buildChecked

        val source = AddSource("John", 30)
        val result = migration(source)

        assertTrue(result.isRight)
      },
      test("buildChecked compiles for complete rename migration") {
        val migration = MigrationBuilder
          .newBuilder[RenameSource, RenameTarget]
          .renameField(_.oldName, _.newName)
          .buildChecked

        val source = RenameSource("John", 30)
        val result = migration(source)

        assertTrue(result.isRight)
      },
      test("buildChecked compiles for complex migration with multiple operations") {
        // ComplexSource: a, b, c, d
        // ComplexTarget: x, b, y, e
        // shared: b
        // removed: a, c, d (need to handle)
        // added: x, y, e (need to provide)

        val migration = MigrationBuilder
          .newBuilder[ComplexSource, ComplexTarget]
          .renameField(_.a, _.x)                                                        // handles a, provides x
          .renameField(_.c, _.y)                                                        // handles c, provides y
          .dropField(_.d, SchemaExpr.Literal[DynamicValue, Double](0.0, Schema.double)) // handles d
          .addField(_.e, SchemaExpr.Literal[DynamicValue, Long](0L, Schema.long))       // provides e
          .buildChecked

        val source = ComplexSource("hello", 42, true, 3.14)
        val result = migration(source)

        assertTrue(result.isRight)
      },
      test("buildChecked with many shared fields") {
        // ManySharedSource: shared1, shared2, shared3, removed
        // ManySharedTarget: shared1, shared2, shared3, added
        // Only need to handle "removed" and provide "added"

        val migration = MigrationBuilder
          .newBuilder[ManySharedSource, ManySharedTarget]
          .dropField(_.removed, SchemaExpr.Literal[DynamicValue, Double](0.0, Schema.double))
          .addField(_.added, SchemaExpr.Literal[DynamicValue, Long](0L, Schema.long))
          .buildChecked

        val source = ManySharedSource("a", 1, true, 2.5)
        val result = migration(source)

        assertTrue(result.isRight)
      }
    ),
    suite("buildChecked compile failures")(
      test("buildChecked fails to compile when drop is missing") {
        assertZIO(typeCheck("""
          import zio.blocks.schema.migration._
          import zio.blocks.schema._

          case class Src(name: String, extra: Int)
          case class Tgt(name: String)
          implicit val srcSchema: Schema[Src] = Schema.derived
          implicit val tgtSchema: Schema[Tgt] = Schema.derived

          MigrationBuilder.newBuilder[Src, Tgt].buildChecked
        """))(Assertion.isLeft)
      },
      test("buildChecked fails to compile when add is missing") {
        assertZIO(typeCheck("""
          import zio.blocks.schema.migration._
          import zio.blocks.schema._

          case class Src(name: String)
          case class Tgt(name: String, extra: Int)
          implicit val srcSchema: Schema[Src] = Schema.derived
          implicit val tgtSchema: Schema[Tgt] = Schema.derived

          MigrationBuilder.newBuilder[Src, Tgt].buildChecked
        """))(Assertion.isLeft)
      },
      test("buildChecked fails to compile for incomplete complex migration") {
        assertZIO(typeCheck("""
          import zio.blocks.schema.migration._
          import zio.blocks.schema._

          case class Src(a: String, b: Int, c: Boolean)
          case class Tgt(x: String, b: Int, y: Boolean)
          implicit val srcSchema: Schema[Src] = Schema.derived
          implicit val tgtSchema: Schema[Tgt] = Schema.derived

          // Only handles "a" -> "x", missing c -> y
          MigrationBuilder.newBuilder[Src, Tgt]
            .renameField(_.a, _.x)
            .buildChecked
        """))(Assertion.isLeft)
      }
    ),
    suite("buildPartial always succeeds")(
      test("buildPartial succeeds even for empty migration on different schemas") {
        val migration = MigrationBuilder
          .newBuilder[DropSource, DropTarget]
          .buildPartial

        // buildPartial doesn't validate, so this should compile
        assertTrue(migration != null)
      },
      test("buildPartial succeeds for incomplete migration") {
        val migration = MigrationBuilder
          .newBuilder[ComplexSource, ComplexTarget]
          .renameField(_.a, _.x) // Only partial migration
          .buildPartial

        assertTrue(migration != null)
      }
    ),
    suite("type-level validation logic")(
      test("unchanged fields are correctly computed") {
        // PersonA and PersonB have same fields
        val fnA = summon[FieldNames[PersonA]]
        val fnB = summon[FieldNames[PersonB]]

        type Unchanged = Intersect[fnA.Labels, fnB.Labels]
        summon[TupleEquals[Unchanged, fnA.Labels] =:= true]
        summon[TupleEquals[Unchanged, fnB.Labels] =:= true]
        assertTrue(true)
      },
      test("required handled is difference of source from target") {
        val fnA = summon[FieldNames[DropSource]]
        val fnB = summon[FieldNames[DropTarget]]

        type Required = Difference[fnA.Labels, fnB.Labels]
        summon[Contains[Required, "extra"] =:= true]
        summon[Contains[Required, "name"] =:= false]
        summon[Contains[Required, "age"] =:= false]
        assertTrue(true)
      },
      test("required provided is difference of target from source") {
        val fnA = summon[FieldNames[AddSource]]
        val fnB = summon[FieldNames[AddTarget]]

        type Required = Difference[fnB.Labels, fnA.Labels]
        summon[Contains[Required, "extra"] =:= true]
        summon[Contains[Required, "name"] =:= false]
        summon[Contains[Required, "age"] =:= false]
        assertTrue(true)
      },
      test("IsSubset validates handled fields correctly") {
        val fnA = summon[FieldNames[DropSource]]
        val fnB = summon[FieldNames[DropTarget]]

        type Required = Difference[fnA.Labels, fnB.Labels]

        // ("extra") is subset of ("extra")
        summon[IsSubset[Required, Tuple1["extra"]] =:= true]

        // ("extra") is subset of ("extra", "other")
        summon[IsSubset[Required, ("extra", "other")] =:= true]

        // Empty is not enough
        summon[IsSubset[Required, EmptyTuple] =:= false]

        assertTrue(true)
      }
    ),
    suite("chaining styles")(
      test("inline chaining works") {
        val migration = MigrationBuilder
          .newBuilder[ComplexSource, ComplexTarget]
          .renameField(_.a, _.x)
          .renameField(_.c, _.y)
          .dropField(_.d, SchemaExpr.Literal[DynamicValue, Double](0.0, Schema.double))
          .addField(_.e, SchemaExpr.Literal[DynamicValue, Long](0L, Schema.long))
          .buildChecked

        assertTrue(migration != null)
      },
      test("multi-line with val assignments works") {
        val builder1  = MigrationBuilder.newBuilder[ComplexSource, ComplexTarget]
        val builder2  = builder1.renameField(_.a, _.x)
        val builder3  = builder2.renameField(_.c, _.y)
        val builder4  = builder3.dropField(_.d, SchemaExpr.Literal[DynamicValue, Double](0.0, Schema.double))
        val builder5  = builder4.addField(_.e, SchemaExpr.Literal[DynamicValue, Long](0L, Schema.long))
        val migration = builder5.buildChecked

        val source = ComplexSource("hello", 42, true, 3.14)
        val result = migration(source)

        assertTrue(result.isRight)
      },
      test("mixed inline and multi-line works") {
        val builder = MigrationBuilder
          .newBuilder[ComplexSource, ComplexTarget]
          .renameField(_.a, _.x)
          .renameField(_.c, _.y)

        val builder2 = builder.dropField(_.d, SchemaExpr.Literal[DynamicValue, Double](0.0, Schema.double))

        val migration = builder2
          .addField(_.e, SchemaExpr.Literal[DynamicValue, Long](0L, Schema.long))
          .buildChecked

        assertTrue(migration != null)
      },
      test("multiple drops over multiple lines") {
        case class MultiDropSrc(keep: String, drop1: Int, drop2: Boolean, drop3: Double)
        case class MultiDropTgt(keep: String)

        given Schema[MultiDropSrc] = Schema.derived
        given Schema[MultiDropTgt] = Schema.derived

        val b1        = MigrationBuilder.newBuilder[MultiDropSrc, MultiDropTgt]
        val b2        = b1.dropField(_.drop1, SchemaExpr.Literal[DynamicValue, Int](0, Schema.int))
        val b3        = b2.dropField(_.drop2, SchemaExpr.Literal[DynamicValue, Boolean](false, Schema.boolean))
        val b4        = b3.dropField(_.drop3, SchemaExpr.Literal[DynamicValue, Double](0.0, Schema.double))
        val migration = b4.buildChecked

        val source = MultiDropSrc("keep", 1, true, 2.5)
        val result = migration(source)

        assertTrue(result.isRight) &&
        assertTrue(result.map(_.keep) == Right("keep"))
      },
      test("multiple adds over multiple lines") {
        case class MultiAddSrc(keep: String)
        case class MultiAddTgt(keep: String, add1: Int, add2: Boolean, add3: Double)

        given Schema[MultiAddSrc] = Schema.derived
        given Schema[MultiAddTgt] = Schema.derived

        val b1        = MigrationBuilder.newBuilder[MultiAddSrc, MultiAddTgt]
        val b2        = b1.addField(_.add1, SchemaExpr.Literal[DynamicValue, Int](42, Schema.int))
        val b3        = b2.addField(_.add2, SchemaExpr.Literal[DynamicValue, Boolean](true, Schema.boolean))
        val b4        = b3.addField(_.add3, SchemaExpr.Literal[DynamicValue, Double](3.14, Schema.double))
        val migration = b4.buildChecked

        val source = MultiAddSrc("keep")
        val result = migration(source)

        assertTrue(result.isRight) &&
        assertTrue(result.map(_.keep) == Right("keep"))
      }
    ),
    suite("edge cases")(
      test("empty source schema") {
        case class EmptySrc()
        case class NonEmptyTgt(field: String)

        implicit val emptySrcSchema: Schema[EmptySrc]       = Schema.derived
        implicit val nonEmptyTgtSchema: Schema[NonEmptyTgt] = Schema.derived

        // Only need to provide "field", nothing to handle
        summon[ValidationProof[EmptySrc, NonEmptyTgt, EmptyTuple, Tuple1["field"]]]

        val migration = MigrationBuilder
          .newBuilder[EmptySrc, NonEmptyTgt]
          .addField(_.field, SchemaExpr.Literal[DynamicValue, String]("default", Schema.string))
          .buildChecked

        assertTrue(migration(EmptySrc()).isRight)
      },
      test("empty target schema") {
        case class NonEmptySrc(field: String)
        case class EmptyTgt()

        implicit val nonEmptySrcSchema: Schema[NonEmptySrc] = Schema.derived
        implicit val emptyTgtSchema: Schema[EmptyTgt]       = Schema.derived

        // Only need to handle "field", nothing to provide
        summon[ValidationProof[NonEmptySrc, EmptyTgt, Tuple1["field"], EmptyTuple]]

        val migration = MigrationBuilder
          .newBuilder[NonEmptySrc, EmptyTgt]
          .dropField(_.field, SchemaExpr.Literal[DynamicValue, String]("default", Schema.string))
          .buildChecked

        assertTrue(migration(NonEmptySrc("test")).isRight)
      },
      test("all fields changed") {
        case class AllChangedSrc(a: String, b: Int)
        case class AllChangedTgt(x: Boolean, y: Double)

        implicit val allChangedSrcSchema: Schema[AllChangedSrc] = Schema.derived
        implicit val allChangedTgtSchema: Schema[AllChangedTgt] = Schema.derived

        // Need to handle a, b and provide x, y
        summon[ValidationProof[AllChangedSrc, AllChangedTgt, ("a", "b"), ("x", "y")]]

        val migration = MigrationBuilder
          .newBuilder[AllChangedSrc, AllChangedTgt]
          .dropField(_.a, SchemaExpr.Literal[DynamicValue, String]("", Schema.string))
          .dropField(_.b, SchemaExpr.Literal[DynamicValue, Int](0, Schema.int))
          .addField(_.x, SchemaExpr.Literal[DynamicValue, Boolean](false, Schema.boolean))
          .addField(_.y, SchemaExpr.Literal[DynamicValue, Double](0.0, Schema.double))
          .buildChecked

        assertTrue(migration(AllChangedSrc("test", 42)).isRight)
      },
      test("single field schemas") {
        case class SingleA(only: String)
        case class SingleB(only: String)

        implicit val singleASchema: Schema[SingleA] = Schema.derived
        implicit val singleBSchema: Schema[SingleB] = Schema.derived

        summon[ValidationProof[SingleA, SingleB, EmptyTuple, EmptyTuple]]

        val migration = MigrationBuilder.newBuilder[SingleA, SingleB].buildChecked
        assertTrue(migration(SingleA("test")).isRight)
      }
    )
  )
}
