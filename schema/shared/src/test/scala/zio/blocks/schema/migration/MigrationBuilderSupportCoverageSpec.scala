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
import zio.blocks.typeid.TypeId
import zio.test._

import java.time.{
  DayOfWeek,
  Duration,
  Instant,
  LocalDate,
  LocalDateTime,
  LocalTime,
  Month,
  MonthDay,
  OffsetDateTime,
  OffsetTime,
  Period,
  Year,
  YearMonth,
  ZoneId,
  ZoneOffset,
  ZonedDateTime
}

/**
 * Exercises [[MigrationBuilderSupport]]'s `primitiveName` for every primitive
 * Schema (all 30 `PrimitiveType` variants) and `schemaReprOf` across the
 * composite schema shapes it dispatches on: primitive, record, variant,
 * sequence, map, optional, and nominal (wrapper).
 *
 * Both helpers are private; coverage is driven through the public
 * [[MigrationBuilderSupport.optionalizeField]] entry point. Every
 * `.optionalizeField(_.x, _.x)` call invokes `schemaReprAt` on the field's
 * reflect, which in turn drives `schemaReprOf` (and, when the field is a
 * primitive leaf, `primitiveName`). Records aggregate primitive arms in one
 * call because `schemaReprOf` recurses into the record's field reflects.
 */
object MigrationBuilderSupportCoverageSpec extends SchemaBaseSpec {

  // --- Fixtures -------------------------------------------------------------

  /**
   * Flat record holding one field per primitive `PrimitiveType`. Calling
   * `optionalizeField` on `AllPrims` (as a record-valued field) fans out to
   * every `primitiveName` arm via the Record branch of `schemaReprOf`.
   */
  final case class AllPrims(
    pUnit: Unit,
    pBoolean: Boolean,
    pByte: Byte,
    pShort: Short,
    pInt: Int,
    pLong: Long,
    pFloat: Float,
    pDouble: Double,
    pChar: Char,
    pString: String,
    pBigInt: BigInt,
    pBigDecimal: BigDecimal,
    pDayOfWeek: DayOfWeek,
    pDuration: Duration,
    pInstant: Instant,
    pLocalDate: LocalDate,
    pLocalDateTime: LocalDateTime,
    pLocalTime: LocalTime,
    pMonth: Month,
    pMonthDay: MonthDay,
    pOffsetDateTime: OffsetDateTime,
    pOffsetTime: OffsetTime,
    pPeriod: Period,
    pYear: Year,
    pYearMonth: YearMonth,
    pZoneId: ZoneId,
    pZoneOffset: ZoneOffset,
    pZonedDateTime: ZonedDateTime,
    pUUID: java.util.UUID,
    pCurrency: java.util.Currency
  )
  object AllPrims {
    implicit val schema: Schema[AllPrims] = Schema.derived
  }

  /**
   * Small nominal wrapper — an `AnyVal` value class whose schema is produced
   * via `Schema[String].transform`. Its reflect does not match any of
   * `asPrimitive` / `isOption` / `asSequenceUnknown` / `asMapUnknown` /
   * `asRecord` / `asVariant`, so `schemaReprOf` falls through to the
   * `SchemaRepr.Nominal` arm.
   */
  final case class NominalTag(value: String) extends AnyVal
  object NominalTag {
    implicit lazy val typeId: TypeId[NominalTag] = TypeId.of[NominalTag]
    implicit lazy val schema: Schema[NominalTag] =
      Schema[String].transform[NominalTag](v => new NominalTag(v), _.value)
  }

  /** Sealed-trait fixture exercising the `Variant` arm of `schemaReprOf`. */
  sealed trait Shape
  object Shape {
    implicit val schema: Schema[Shape] = Schema.derived
  }
  final case class Circle(radius: Int) extends Shape
  object Circle {
    implicit val schema: Schema[Circle] = Schema.derived
  }
  final case class Square(side: Int) extends Shape
  object Square {
    implicit val schema: Schema[Square] = Schema.derived
  }

  /**
   * Source record containing one field per composite dispatch branch that
   * `schemaReprOf` handles, plus the `AllPrims` record.
   */
  final case class CompositesSource(
    prims: AllPrims,
    seq: Vector[Int],
    mp: Map[String, Int],
    opt: Option[Int],
    shape: Shape,
    tag: NominalTag
  )
  object CompositesSource {
    implicit val schema: Schema[CompositesSource] = Schema.derived
  }

  /**
   * Target record — same field names, each wrapped in `Option`. Required by the
   * `optionalizeField[A, B, G]` macro signature
   * `(source: A => G, target: B => Option[G])`.
   */
  final case class CompositesTarget(
    prims: Option[AllPrims],
    seq: Option[Vector[Int]],
    mp: Option[Map[String, Int]],
    opt: Option[Option[Int]],
    shape: Option[Shape],
    tag: Option[NominalTag]
  )
  object CompositesTarget {
    implicit val schema: Schema[CompositesTarget] = Schema.derived
  }

  // --- Expected SchemaRepr helpers ------------------------------------------

  private val expectedAllPrimsRepr: SchemaRepr =
    SchemaRepr.Record(
      IndexedSeq(
        "pUnit"           -> SchemaRepr.Primitive("unit"),
        "pBoolean"        -> SchemaRepr.Primitive("boolean"),
        "pByte"           -> SchemaRepr.Primitive("byte"),
        "pShort"          -> SchemaRepr.Primitive("short"),
        "pInt"            -> SchemaRepr.Primitive("int"),
        "pLong"           -> SchemaRepr.Primitive("long"),
        "pFloat"          -> SchemaRepr.Primitive("float"),
        "pDouble"         -> SchemaRepr.Primitive("double"),
        "pChar"           -> SchemaRepr.Primitive("char"),
        "pString"         -> SchemaRepr.Primitive("string"),
        "pBigInt"         -> SchemaRepr.Primitive("bigint"),
        "pBigDecimal"     -> SchemaRepr.Primitive("bigdecimal"),
        "pDayOfWeek"      -> SchemaRepr.Primitive("dayofweek"),
        "pDuration"       -> SchemaRepr.Primitive("duration"),
        "pInstant"        -> SchemaRepr.Primitive("instant"),
        "pLocalDate"      -> SchemaRepr.Primitive("localdate"),
        "pLocalDateTime"  -> SchemaRepr.Primitive("localdatetime"),
        "pLocalTime"      -> SchemaRepr.Primitive("localtime"),
        "pMonth"          -> SchemaRepr.Primitive("month"),
        "pMonthDay"       -> SchemaRepr.Primitive("monthday"),
        "pOffsetDateTime" -> SchemaRepr.Primitive("offsetdatetime"),
        "pOffsetTime"     -> SchemaRepr.Primitive("offsettime"),
        "pPeriod"         -> SchemaRepr.Primitive("period"),
        "pYear"           -> SchemaRepr.Primitive("year"),
        "pYearMonth"      -> SchemaRepr.Primitive("yearmonth"),
        "pZoneId"         -> SchemaRepr.Primitive("zoneid"),
        "pZoneOffset"     -> SchemaRepr.Primitive("zoneoffset"),
        "pZonedDateTime"  -> SchemaRepr.Primitive("zoneddatetime"),
        "pUUID"           -> SchemaRepr.Primitive("uuid"),
        "pCurrency"       -> SchemaRepr.Primitive("currency")
      )
    )

  // --- Helpers --------------------------------------------------------------

  private def opticAt(action: MigrationAction): DynamicOptic = action.at

  private def optionalizeOf(action: MigrationAction): MigrationAction.Optionalize =
    action match {
      case o: MigrationAction.Optionalize => o
      case other                          => sys.error(s"expected Optionalize, got $other")
    }

  // --- Suite ----------------------------------------------------------------

  def spec: Spec[TestEnvironment, Any] = suite("MigrationBuilderSupportCoverageSpec")(
    test(
      "optionalizeField on an `AllPrims` record drives primitiveName across every PrimitiveType arm"
    ) {
      // Exercises the Record branch of schemaReprOf, which recurses into each
      // field and hits every primitiveName arm (30 primitives).
      val builder = Migration
        .builder[CompositesSource, CompositesTarget]
        .optionalizeField(_.prims, _.prims)
      val Optionalize = builder.actions.toSeq.collect { case o: MigrationAction.Optionalize => o }.head

      assertTrue(Optionalize.at == DynamicOptic.root.field("prims")) &&
      assertTrue(Optionalize.sourceSchemaRepr == expectedAllPrimsRepr)
    },
    test("optionalizeField on a Vector[Int] field drives schemaReprOf's Sequence arm") {
      val builder = Migration
        .builder[CompositesSource, CompositesTarget]
        .optionalizeField(_.seq, _.seq)
      val o = optionalizeOf(builder.actions.toSeq.head)

      assertTrue(o.at == DynamicOptic.root.field("seq")) &&
      assertTrue(o.sourceSchemaRepr == SchemaRepr.Sequence(SchemaRepr.Primitive("int")))
    },
    test("optionalizeField on a Map[String, Int] field drives schemaReprOf's Map arm") {
      val builder = Migration
        .builder[CompositesSource, CompositesTarget]
        .optionalizeField(_.mp, _.mp)
      val o = optionalizeOf(builder.actions.toSeq.head)

      assertTrue(o.at == DynamicOptic.root.field("mp")) &&
      assertTrue(
        o.sourceSchemaRepr == SchemaRepr.Map(SchemaRepr.Primitive("string"), SchemaRepr.Primitive("int"))
      )
    },
    test("optionalizeField on an Option[Int] field drives schemaReprOf's Optional arm") {
      val builder = Migration
        .builder[CompositesSource, CompositesTarget]
        .optionalizeField(_.opt, _.opt)
      val o = optionalizeOf(builder.actions.toSeq.head)

      assertTrue(o.at == DynamicOptic.root.field("opt")) &&
      assertTrue(o.sourceSchemaRepr == SchemaRepr.Optional(SchemaRepr.Primitive("int")))
    },
    test("optionalizeField on a sealed-trait field drives schemaReprOf's Variant arm") {
      val builder = Migration
        .builder[CompositesSource, CompositesTarget]
        .optionalizeField(_.shape, _.shape)
      val o = optionalizeOf(builder.actions.toSeq.head)

      // Variant arm: expect a SchemaRepr.Variant with the declared cases.
      val reprIsVariant = o.sourceSchemaRepr match {
        case _: SchemaRepr.Variant => true
        case _                     => false
      }
      assertTrue(o.at == DynamicOptic.root.field("shape")) &&
      assertTrue(reprIsVariant)
    },
    test(
      "optionalizeField on a transform-wrapped field drives schemaReprOf's Nominal fall-through arm"
    ) {
      val builder = Migration
        .builder[CompositesSource, CompositesTarget]
        .optionalizeField(_.tag, _.tag)
      val o = optionalizeOf(builder.actions.toSeq.head)

      // Nominal arm: fall-through when reflect is neither primitive / option /
      // sequence / map / record / variant (here it's a Reflect.Wrapper).
      val reprIsNominal = o.sourceSchemaRepr match {
        case _: SchemaRepr.Nominal => true
        case _                     => false
      }
      assertTrue(o.at == DynamicOptic.root.field("tag")) &&
      assertTrue(reprIsNominal)
    },
    test("caseName derives the simple type-id name from an implicit Schema") {
      // Complements the optionalize-driven coverage: caseName is the only other
      // public entry point on MigrationBuilderSupport and is exercised by
      // `transformCase` in the macros. Here we pin its value directly.
      val name = MigrationBuilderSupport.caseName[Circle](Circle.schema)
      assertTrue(name == Circle.schema.reflect.typeId.name)
    }
  )
}
