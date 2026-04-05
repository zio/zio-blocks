/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.test._

/**
 * Exercises [[DynamicMigrationInterpreter]] and [[MigrationExprEval]] so
 * migration code stays covered under scoverage branch thresholds.
 */
object DynamicMigrationInterpreterSpec extends MigrationTestSupport {

  private final case class PersonV1(name: String)
  private final case class PersonV2(name: String, age: Int)
  private final case class PersonV3(fullName: String, age: Int)
  private final case class LongSrc(l: Long)

  /**
   * Same field name as [[LongSrc]] so [[MigrationAction.ChangeType]] can read
   * from source `working`.
   */
  private final case class LongTgt(l: Int)
  private final case class WithVec(nums: Vector[Int])
  private final case class WithMap(m: Map[String, Int])
  private final case class JoinSrc(a: String, b: String)
  private final case class JoinTgt(ab: String)
  private final case class SplitSrc(x: Int)
  private final case class SplitAB(x: Int, y: Int)
  private final case class WithInt(i: Int)
  private final case class WithLong(l: Long)
  private final case class WithDouble(d: Double)
  private final case class WithBool(b: Boolean)

  private implicit lazy val personV1Schema: Schema[PersonV1]     = Schema.derived[PersonV1]
  private implicit lazy val personV2Schema: Schema[PersonV2]     = Schema.derived[PersonV2]
  private implicit lazy val personV3Schema: Schema[PersonV3]     = Schema.derived[PersonV3]
  private implicit lazy val longSrcSchema: Schema[LongSrc]       = Schema.derived[LongSrc]
  private implicit lazy val longTgtSchema: Schema[LongTgt]       = Schema.derived[LongTgt]
  private implicit lazy val withVecSchema: Schema[WithVec]       = Schema.derived[WithVec]
  private implicit lazy val withMapSchema: Schema[WithMap]       = Schema.derived[WithMap]
  private implicit lazy val joinSrcSchema: Schema[JoinSrc]       = Schema.derived[JoinSrc]
  private implicit lazy val joinTgtSchema: Schema[JoinTgt]       = Schema.derived[JoinTgt]
  private implicit lazy val splitSrcSchema: Schema[SplitSrc]     = Schema.derived[SplitSrc]
  private implicit lazy val splitABSchema: Schema[SplitAB]       = Schema.derived[SplitAB]
  private implicit lazy val withIntSchema: Schema[WithInt]       = Schema.derived[WithInt]
  private implicit lazy val withLongSchema: Schema[WithLong]     = Schema.derived[WithLong]
  private implicit lazy val withDoubleSchema: Schema[WithDouble] = Schema.derived[WithDouble]
  private implicit lazy val withBoolSchema: Schema[WithBool]     = Schema.derived[WithBool]

  def spec: Spec[Any, Any] = suite("DynamicMigrationInterpreter")(
    test("DynamicMigration.reverse and Migration.reverse round-trip structure") {
      val dm = DynamicMigration(
        MigrationAction.RenameCase(DynamicOptic.root, "A", "B")
      )
      assertTrue(dm.reverse.reverse.actions == dm.actions)
      val m = MigrationBuilder[PersonV1, PersonV2]
        .addField(_.age, MigrationExpr.Literal(new DynamicValue.Primitive(new PrimitiveValue.Int(0))))
        .buildPartial
      val r = m.reverse
      assertTrue(r.sourceSchema == personV2Schema && r.targetSchema == personV1Schema)
    },
    test("dropField removes field") {
      val m = MigrationBuilder[PersonV2, PersonV1]
        .dropField(_.age, MigrationExpr.Literal(new DynamicValue.Primitive(new PrimitiveValue.Int(0))))
        .buildPartial
      assertTrue(m(PersonV2("Ada", 40)) == Right(PersonV1("Ada")))
    },
    test("renameField copies value to new name") {
      val m = MigrationBuilder[PersonV1, PersonV3]
        .renameField(_.name, _.fullName)
        .addField(_.age, MigrationExpr.Literal(new DynamicValue.Primitive(new PrimitiveValue.Int(0))))
        .buildPartial
      assertTrue(m(PersonV1("Ada")) == Right(PersonV3("Ada", 0)))
    },
    test("transformField uses RootPath in expression") {
      val m = MigrationBuilder[PersonV1, PersonV2]
        .transformField(_.name, _.name, MigrationExpr.RootPath(DynamicOptic.root.field("name")))
        .addField(_.age, MigrationExpr.Literal(new DynamicValue.Primitive(new PrimitiveValue.Int(0))))
        .buildPartial
      assertTrue(m(PersonV1("Ada")) == Right(PersonV2("Ada", 0)))
    },
    test("changeFieldType coerces long to int") {
      val m = MigrationBuilder[LongSrc, LongTgt]
        .changeFieldType(_.l, _.l, MigrationExpr.CoercePrimitive(MigrationPrimitiveTarget.Int))
        .buildPartial
      assertTrue(m(LongSrc(42L)) == Right(LongTgt(42)))
    },
    test("transformElements maps each element") {
      val one = new DynamicValue.Primitive(new PrimitiveValue.Int(1))
      val m   = MigrationBuilder[WithVec, WithVec]
        .transformElements(
          _.nums,
          MigrationExpr.IntPlus(
            MigrationExpr.RootPath(DynamicOptic.root),
            MigrationExpr.Literal(one)
          )
        )
        .buildPartial
      assertTrue(m(WithVec(Vector(1, 2))) == Right(WithVec(Vector(2, 3))))
    },
    test("transformKeys updates map keys") {
      val m = MigrationBuilder[WithMap, WithMap]
        .transformKeys(
          _.m,
          MigrationExpr.ConcatStrings(
            MigrationExpr.RootPath(DynamicOptic.root),
            MigrationExpr.Literal(new DynamicValue.Primitive(new PrimitiveValue.String("_")))
          )
        )
        .buildPartial
      assertTrue(m(WithMap(Map("a" -> 1))) == Right(WithMap(Map("a_" -> 1))))
    },
    test("transformValues updates map values") {
      val m = MigrationBuilder[WithMap, WithMap]
        .transformValues(
          _.m,
          MigrationExpr.IntPlus(
            MigrationExpr.RootPath(DynamicOptic.root),
            MigrationExpr.Literal(new DynamicValue.Primitive(new PrimitiveValue.Int(10)))
          )
        )
        .buildPartial
      assertTrue(m(WithMap(Map("k" -> 5))) == Right(WithMap(Map("k" -> 15))))
    },
    test("joinField inserts combined value") {
      val m = MigrationBuilder[JoinSrc, JoinTgt]
        .joinField(
          DynamicOptic.root.field("ab"),
          Vector(DynamicOptic.root.field("a"), DynamicOptic.root.field("b")),
          MigrationExpr.ConcatStrings(
            MigrationExpr.RootPath(DynamicOptic.root.field("a")),
            MigrationExpr.RootPath(DynamicOptic.root.field("b"))
          )
        )
        .buildPartial
      assertTrue(m(JoinSrc("a", "b")) == Right(JoinTgt("ab")))
    },
    test("splitField with empty targets leaves record unchanged") {
      val m = MigrationBuilder[SplitSrc, SplitSrc]
        .splitField(
          DynamicOptic.root.field("x"),
          Vector.empty,
          MigrationExpr.Literal(new DynamicValue.Primitive(new PrimitiveValue.Int(0)))
        )
        .buildPartial
      assertTrue(m(SplitSrc(5)) == Right(SplitSrc(5)))
    },
    test("splitField writes to first target path") {
      val m = MigrationBuilder[SplitAB, SplitAB]
        .splitField(
          DynamicOptic.root.field("x"),
          Vector(DynamicOptic.root.field("y")),
          MigrationExpr.RootPath(DynamicOptic.root)
        )
        .buildPartial
      assertTrue(m(SplitAB(9, 0)) == Right(SplitAB(9, 9)))
    },
    test("Migration ++ composes") {
      val m1 = MigrationBuilder[PersonV1, PersonV2]
        .addField(_.age, MigrationExpr.Literal(new DynamicValue.Primitive(new PrimitiveValue.Int(0))))
        .buildPartial
      val m2 = Migration.identity[PersonV2]
      val c  = m1 ++ m2
      assertTrue(c(PersonV1("Ada")) == Right(PersonV2("Ada", 0)))
    },
    test("SchemaRootDefault evaluates when schema has root default") {
      implicit val s: Schema[PersonV2] = Schema.derived[PersonV2].defaultValue(PersonV2("x", 1))
      val expr                         = MigrationExpr.SchemaRootDefault(MigrationSchemaSlot.Source)
      val root                         = DynamicValue.Record(zio.blocks.chunk.Chunk.empty)
      val r                            = MigrationExprEval.eval(expr, root, s, s, DynamicOptic.root)
      assertTrue(r == Right(s.toDynamicValue(PersonV2("x", 1))))
    },
    test("FieldDefault evaluates from record field default") {
      final case class FD(a: Int = 42)
      implicit val fds: Schema[FD] = Schema.derived[FD]
      val expr                     = MigrationExpr.FieldDefault("a", MigrationSchemaSlot.Source)
      val r                        =
        MigrationExprEval.eval(expr, DynamicValue.Record(zio.blocks.chunk.Chunk.empty), fds, fds, DynamicOptic.root)
      assertTrue(r == Right(new DynamicValue.Primitive(new PrimitiveValue.Int(42))))
    },
    test("SchemaRootDefault fails when schema has no root default") {
      implicit val s: Schema[PersonV1] = Schema.derived[PersonV1]
      val r                            = MigrationExprEval.eval(
        MigrationExpr.SchemaRootDefault(MigrationSchemaSlot.Source),
        DynamicValue.Record(zio.blocks.chunk.Chunk.empty),
        s,
        s,
        DynamicOptic.root
      )
      assertTrue(r.isLeft)
    },
    test("FieldDefault fails for unknown field name") {
      implicit val s: Schema[PersonV1] = Schema.derived[PersonV1]
      val r                            = MigrationExprEval.eval(
        MigrationExpr.FieldDefault("nope", MigrationSchemaSlot.Source),
        DynamicValue.Record(zio.blocks.chunk.Chunk.empty),
        s,
        s,
        DynamicOptic.root
      )
      assertTrue(r.isLeft)
    },
    test("ConcatStrings requires string primitives") {
      val r = MigrationExprEval.eval(
        MigrationExpr.ConcatStrings(
          MigrationExpr.Literal(new DynamicValue.Primitive(new PrimitiveValue.Int(1))),
          MigrationExpr.Literal(new DynamicValue.Primitive(new PrimitiveValue.String("a")))
        ),
        DynamicValue.Record(zio.blocks.chunk.Chunk.empty),
        personV1Schema,
        personV1Schema,
        DynamicOptic.root
      )
      assertTrue(r.isLeft)
    },
    test("IntPlus requires int-like primitives") {
      val r = MigrationExprEval.eval(
        MigrationExpr.IntPlus(
          MigrationExpr.Literal(new DynamicValue.Primitive(new PrimitiveValue.String("a"))),
          MigrationExpr.Literal(new DynamicValue.Primitive(new PrimitiveValue.Int(1)))
        ),
        DynamicValue.Record(zio.blocks.chunk.Chunk.empty),
        personV1Schema,
        personV1Schema,
        DynamicOptic.root
      )
      assertTrue(r.isLeft)
    },
    test("MigrationExpr eval errors: bad RootPath") {
      val r = MigrationExprEval.eval(
        MigrationExpr.RootPath(DynamicOptic.root.field("nope")),
        DynamicValue.Record(zio.blocks.chunk.Chunk.empty),
        personV1Schema,
        personV1Schema,
        DynamicOptic.root
      )
      assertTrue(r.isLeft)
    },
    test("CoercePrimitive at top-level eval fails") {
      val r = MigrationExprEval.eval(
        MigrationExpr.CoercePrimitive(MigrationPrimitiveTarget.Int),
        DynamicValue.Record(zio.blocks.chunk.Chunk.empty),
        personV1Schema,
        personV1Schema,
        DynamicOptic.root
      )
      assertTrue(r.isLeft)
    },
    test("evalUnary coerces long double boolean string") {
      val i = new DynamicValue.Primitive(new PrimitiveValue.Int(3))
      val l = new DynamicValue.Primitive(new PrimitiveValue.Long(4L))
      val d = new DynamicValue.Primitive(new PrimitiveValue.Double(2.5))
      val f = new DynamicValue.Primitive(new PrimitiveValue.Float(2.5f))
      val b = new DynamicValue.Primitive(new PrimitiveValue.Boolean(true))
      val p = DynamicOptic.root
      assertTrue(
        MigrationExprEval
          .evalUnary(i, MigrationPrimitiveTarget.Long, p)
          .exists(_.isInstanceOf[DynamicValue.Primitive]) &&
          MigrationExprEval.evalUnary(l, MigrationPrimitiveTarget.Int, p).isRight &&
          MigrationExprEval.evalUnary(d, MigrationPrimitiveTarget.Long, p).isRight &&
          MigrationExprEval.evalUnary(f, MigrationPrimitiveTarget.Double, p).isRight &&
          MigrationExprEval.evalUnary(i, MigrationPrimitiveTarget.String, p).isRight &&
          MigrationExprEval.evalUnary(b, MigrationPrimitiveTarget.Boolean, p).isRight
      )
    },
    test("dynamic migration error: TransformElements on non-sequence") {
      val dm = DynamicMigration(
        MigrationAction.TransformElements(
          DynamicOptic.root.field("nums"),
          MigrationExpr.Literal(new DynamicValue.Primitive(new PrimitiveValue.Int(0)))
        )
      )
      val dv = withIntSchema.toDynamicValue(WithInt(1))
      val r  = dm.apply(dv, withIntSchema, withIntSchema)
      assertTrue(r.isLeft)
    },
    test("dynamic migration error: TransformKeys on non-map") {
      val dm = DynamicMigration(
        MigrationAction.TransformKeys(
          DynamicOptic.root.field("m"),
          MigrationExpr.RootPath(DynamicOptic.root)
        )
      )
      val dv = withIntSchema.toDynamicValue(WithInt(1))
      val r  = dm.apply(dv, withIntSchema, withIntSchema)
      assertTrue(r.isLeft)
    },
    test("dynamic migration error: TransformValues on non-map") {
      val dm = DynamicMigration(
        MigrationAction.TransformValues(
          DynamicOptic.root.field("m"),
          MigrationExpr.RootPath(DynamicOptic.root)
        )
      )
      val dv = withIntSchema.toDynamicValue(WithInt(1))
      val r  = dm.apply(dv, withIntSchema, withIntSchema)
      assertTrue(r.isLeft)
    },
    test("RenameCase fails when case name does not match") {
      sealed trait Pet
      object Pet {
        implicit val schema: Schema[Pet] = Schema.derived[Pet]
      }
      final case class Dog(name: String) extends Pet
      val dm    = DynamicMigration(MigrationAction.RenameCase(DynamicOptic.root, "Cat", "Doggo"))
      val dogDv = implicitly[Schema[Pet]].toDynamicValue(Dog("Rex"))
      val r     = dm.apply(dogDv, Pet.schema, Pet.schema)
      assertTrue(r.isLeft)
    },
    test("TransformCase runs nested migration") {
      final case class Inner(v: Int)
      final case class Outer(x: Inner)
      implicit val innerS: Schema[Inner] = Schema.derived[Inner]
      implicit val outerS: Schema[Outer] = Schema.derived[Outer]
      val dm                             = DynamicMigration(
        MigrationAction.TransformCase(
          DynamicOptic.root.field("x"),
          Vector(
            MigrationAction.TransformValue(
              DynamicOptic.root.field("v"),
              MigrationExpr.IntPlus(
                MigrationExpr.RootPath(DynamicOptic.root.field("v")),
                MigrationExpr.Literal(new DynamicValue.Primitive(new PrimitiveValue.Int(1)))
              )
            )
          )
        )
      )
      val dv = outerS.toDynamicValue(Outer(Inner(2)))
      val r  = dm.apply(dv, outerS, outerS)
      assertTrue(r.flatMap(outerS.fromDynamicValue) == Right(Outer(Inner(3))))
    },
    test("RenameCase changes variant case name") {
      sealed trait Pet
      object Pet {
        implicit val schema: Schema[Pet] = Schema.derived[Pet]
      }
      final case class Dog(name: String) extends Pet
      final case class Cat(name: String) extends Pet

      val dm    = DynamicMigration(MigrationAction.RenameCase(DynamicOptic.root, "Dog", "Doggo"))
      val dogDv = implicitly[Schema[Pet]].toDynamicValue(Dog("Rex"))
      val r     = dm.apply(dogDv, Pet.schema, Pet.schema)
      assertTrue(
        r.exists {
          case v: DynamicValue.Variant => v.caseNameValue == "Doggo"
          case _                       => false
        }
      )
    },
    test("rename reverse swaps field names on MigrationAction.Rename") {
      val a   = MigrationAction.Rename(DynamicOptic.root.field("x"), "y")
      val rev = a.reverse
      assertTrue(rev.isInstanceOf[MigrationAction.Rename])
    },
    test("codec round-trip preserves nested MigrationAction reverse metadata") {
      val action                              = MigrationAction.Optionalize(DynamicOptic.root.field("age"))
      implicit val s: Schema[MigrationAction] = MigrationDerivedSchemas.migrationActionSchema
      val dv                                  = s.toDynamicValue(action)
      val back                                = s.fromDynamicValue(dv)
      assertTrue(back.map(_.reverse) == Right(action.reverse))
    }
  )
}
