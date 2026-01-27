package zio.blocks.typeid

import zio.test._

/**
 * Comprehensive tests for StandardTypes predefined type definitions.
 * Covers: all primitive types, java time types, collections.
 */
object StandardTypesSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("StandardTypesSpec")(
    suite("Primitive types")(
      test("unit has correct owner and name") {
        val tid = StandardTypes.unit
        assertTrue(
          tid.dynamic.owner == Owner.pkg("scala"),
          tid.dynamic.name == "Unit",
          tid.dynamic.isClass,
          tid.dynamic.isValueClass
        )
      },
      test("boolean has correct owner and name") {
        val tid = StandardTypes.boolean
        assertTrue(
          tid.dynamic.owner == Owner.pkg("scala"),
          tid.dynamic.name == "Boolean",
          tid.dynamic.isClass,
          tid.dynamic.isValueClass
        )
      },
      test("byte has correct owner and name") {
        val tid = StandardTypes.byte
        assertTrue(
          tid.dynamic.owner == Owner.pkg("scala"),
          tid.dynamic.name == "Byte",
          tid.dynamic.isClass,
          tid.dynamic.isValueClass
        )
      },
      test("short has correct owner and name") {
        val tid = StandardTypes.short
        assertTrue(
          tid.dynamic.owner == Owner.pkg("scala"),
          tid.dynamic.name == "Short",
          tid.dynamic.isClass,
          tid.dynamic.isValueClass
        )
      },
      test("int has correct owner and name") {
        val tid = StandardTypes.int
        assertTrue(
          tid.dynamic.owner == Owner.pkg("scala"),
          tid.dynamic.name == "Int",
          tid.dynamic.isClass,
          tid.dynamic.isValueClass
        )
      },
      test("long has correct owner and name") {
        val tid = StandardTypes.long
        assertTrue(
          tid.dynamic.owner == Owner.pkg("scala"),
          tid.dynamic.name == "Long",
          tid.dynamic.isClass,
          tid.dynamic.isValueClass
        )
      },
      test("float has correct owner and name") {
        val tid = StandardTypes.float
        assertTrue(
          tid.dynamic.owner == Owner.pkg("scala"),
          tid.dynamic.name == "Float",
          tid.dynamic.isClass,
          tid.dynamic.isValueClass
        )
      },
      test("double has correct owner and name") {
        val tid = StandardTypes.double
        assertTrue(
          tid.dynamic.owner == Owner.pkg("scala"),
          tid.dynamic.name == "Double",
          tid.dynamic.isClass,
          tid.dynamic.isValueClass
        )
      },
      test("char has correct owner and name") {
        val tid = StandardTypes.char
        assertTrue(
          tid.dynamic.owner == Owner.pkg("scala"),
          tid.dynamic.name == "Char",
          tid.dynamic.isClass,
          tid.dynamic.isValueClass
        )
      },
      test("string has correct owner and name") {
        val tid = StandardTypes.string
        assertTrue(
          tid.dynamic.owner == Owner.pkgs("java", "lang"),
          tid.dynamic.name == "String",
          tid.dynamic.isClass
        )
      }
    ),
    suite("Big number types")(
      test("bigInt has correct owner and name") {
        val tid = StandardTypes.bigInt
        assertTrue(
          tid.dynamic.owner == Owner.pkgs("scala", "math"),
          tid.dynamic.name == "BigInt",
          tid.dynamic.isClass
        )
      },
      test("bigDecimal has correct owner and name") {
        val tid = StandardTypes.bigDecimal
        assertTrue(
          tid.dynamic.owner == Owner.pkgs("scala", "math"),
          tid.dynamic.name == "BigDecimal",
          tid.dynamic.isClass
        )
      }
    ),
    suite("Java Time types")(
      test("dayOfWeek is an enum") {
        val tid = StandardTypes.dayOfWeek
        assertTrue(
          tid.dynamic.owner == Owner.pkgs("java", "time"),
          tid.dynamic.name == "DayOfWeek",
          tid.dynamic.isEnum
        )
      },
      test("duration has correct owner") {
        val tid = StandardTypes.duration
        assertTrue(
          tid.dynamic.owner == Owner.pkgs("java", "time"),
          tid.dynamic.name == "Duration",
          tid.dynamic.isClass
        )
      },
      test("instant has correct owner") {
        val tid = StandardTypes.instant
        assertTrue(
          tid.dynamic.owner == Owner.pkgs("java", "time"),
          tid.dynamic.name == "Instant",
          tid.dynamic.isClass
        )
      },
      test("localDate has correct owner") {
        val tid = StandardTypes.localDate
        assertTrue(
          tid.dynamic.owner == Owner.pkgs("java", "time"),
          tid.dynamic.name == "LocalDate",
          tid.dynamic.isClass
        )
      },
      test("localDateTime has correct owner") {
        val tid = StandardTypes.localDateTime
        assertTrue(
          tid.dynamic.owner == Owner.pkgs("java", "time"),
          tid.dynamic.name == "LocalDateTime",
          tid.dynamic.isClass
        )
      },
      test("localTime has correct owner") {
        val tid = StandardTypes.localTime
        assertTrue(
          tid.dynamic.owner == Owner.pkgs("java", "time"),
          tid.dynamic.name == "LocalTime",
          tid.dynamic.isClass
        )
      },
      test("month is an enum") {
        val tid = StandardTypes.month
        assertTrue(
          tid.dynamic.owner == Owner.pkgs("java", "time"),
          tid.dynamic.name == "Month",
          tid.dynamic.isEnum
        )
      },
      test("monthDay has correct owner") {
        val tid = StandardTypes.monthDay
        assertTrue(
          tid.dynamic.owner == Owner.pkgs("java", "time"),
          tid.dynamic.name == "MonthDay",
          tid.dynamic.isClass
        )
      },
      test("offsetDateTime has correct owner") {
        val tid = StandardTypes.offsetDateTime
        assertTrue(
          tid.dynamic.owner == Owner.pkgs("java", "time"),
          tid.dynamic.name == "OffsetDateTime",
          tid.dynamic.isClass
        )
      },
      test("offsetTime has correct owner") {
        val tid = StandardTypes.offsetTime
        assertTrue(
          tid.dynamic.owner == Owner.pkgs("java", "time"),
          tid.dynamic.name == "OffsetTime",
          tid.dynamic.isClass
        )
      },
      test("period has correct owner") {
        val tid = StandardTypes.period
        assertTrue(
          tid.dynamic.owner == Owner.pkgs("java", "time"),
          tid.dynamic.name == "Period",
          tid.dynamic.isClass
        )
      },
      test("year has correct owner") {
        val tid = StandardTypes.year
        assertTrue(
          tid.dynamic.owner == Owner.pkgs("java", "time"),
          tid.dynamic.name == "Year",
          tid.dynamic.isClass
        )
      },
      test("yearMonth has correct owner") {
        val tid = StandardTypes.yearMonth
        assertTrue(
          tid.dynamic.owner == Owner.pkgs("java", "time"),
          tid.dynamic.name == "YearMonth",
          tid.dynamic.isClass
        )
      },
      test("zoneId has correct owner") {
        val tid = StandardTypes.zoneId
        assertTrue(
          tid.dynamic.owner == Owner.pkgs("java", "time"),
          tid.dynamic.name == "ZoneId",
          tid.dynamic.isClass
        )
      },
      test("zoneOffset has correct owner") {
        val tid = StandardTypes.zoneOffset
        assertTrue(
          tid.dynamic.owner == Owner.pkgs("java", "time"),
          tid.dynamic.name == "ZoneOffset",
          tid.dynamic.isClass
        )
      },
      test("zonedDateTime has correct owner") {
        val tid = StandardTypes.zonedDateTime
        assertTrue(
          tid.dynamic.owner == Owner.pkgs("java", "time"),
          tid.dynamic.name == "ZonedDateTime",
          tid.dynamic.isClass
        )
      }
    ),
    suite("Java Util types")(
      test("currency has correct owner") {
        val tid = StandardTypes.currency
        assertTrue(
          tid.dynamic.owner == Owner.pkgs("java", "util"),
          tid.dynamic.name == "Currency",
          tid.dynamic.isClass
        )
      },
      test("uuid has correct owner") {
        val tid = StandardTypes.uuid
        assertTrue(
          tid.dynamic.owner == Owner.pkgs("java", "util"),
          tid.dynamic.name == "UUID",
          tid.dynamic.isClass
        )
      }
    ),
    suite("Collection types")(
      test("ListId has covariant type parameter") {
        val tid = StandardTypes.ListId
        assertTrue(
          tid.dynamic.owner == Owner.pkgs("scala", "collection", "immutable"),
          tid.dynamic.name == "List",
          tid.dynamic.arity == 1,
          tid.dynamic.typeParams.head.variance == Variance.Covariant,
          tid.dynamic.isTrait,
          tid.dynamic.isSealed
        )
      },
      test("VectorId has covariant type parameter") {
        val tid = StandardTypes.VectorId
        assertTrue(
          tid.dynamic.owner == Owner.pkgs("scala", "collection", "immutable"),
          tid.dynamic.name == "Vector",
          tid.dynamic.arity == 1,
          tid.dynamic.typeParams.head.variance == Variance.Covariant,
          tid.dynamic.isClass
        )
      },
      test("SetId has covariant type parameter") {
        val tid = StandardTypes.SetId
        assertTrue(
          tid.dynamic.owner == Owner.pkgs("scala", "collection", "immutable"),
          tid.dynamic.name == "Set",
          tid.dynamic.arity == 1,
          tid.dynamic.typeParams.head.variance == Variance.Covariant,
          tid.dynamic.isTrait
        )
      },
      test("MapId has invariant key and covariant value") {
        val tid = StandardTypes.MapId
        assertTrue(
          tid.dynamic.owner == Owner.pkgs("scala", "collection", "immutable"),
          tid.dynamic.name == "Map",
          tid.dynamic.arity == 2,
          tid.dynamic.typeParams(0).variance == Variance.Invariant,
          tid.dynamic.typeParams(1).variance == Variance.Covariant,
          tid.dynamic.isTrait
        )
      },
      test("OptionId has covariant type parameter") {
        val tid = StandardTypes.OptionId
        assertTrue(
          tid.dynamic.owner == Owner.pkg("scala"),
          tid.dynamic.name == "Option",
          tid.dynamic.arity == 1,
          tid.dynamic.typeParams.head.variance == Variance.Covariant,
          tid.dynamic.isTrait,
          tid.dynamic.isSealed
        )
      },
      test("EitherId has two covariant type parameters") {
        val tid = StandardTypes.EitherId
        assertTrue(
          tid.dynamic.owner == Owner.pkg("scala"),
          tid.dynamic.name == "Either",
          tid.dynamic.arity == 2,
          tid.dynamic.typeParams.forall(_.variance == Variance.Covariant),
          tid.dynamic.isTrait,
          tid.dynamic.isSealed
        )
      },
      test("LeftId has two covariant type parameters") {
        val tid = StandardTypes.LeftId
        assertTrue(
          tid.dynamic.owner == Owner.pkg("scala"),
          tid.dynamic.name == "Left",
          tid.dynamic.arity == 2,
          tid.dynamic.typeParams.forall(_.variance == Variance.Covariant),
          tid.dynamic.isClass
        )
      },
      test("RightId has two covariant type parameters") {
        val tid = StandardTypes.RightId
        assertTrue(
          tid.dynamic.owner == Owner.pkg("scala"),
          tid.dynamic.name == "Right",
          tid.dynamic.arity == 2,
          tid.dynamic.typeParams.forall(_.variance == Variance.Covariant),
          tid.dynamic.isClass
        )
      },
      test("IndexedSeqId has covariant type parameter") {
        val tid = StandardTypes.IndexedSeqId
        assertTrue(
          tid.dynamic.owner == Owner.pkgs("scala", "collection", "immutable"),
          tid.dynamic.name == "IndexedSeq",
          tid.dynamic.arity == 1,
          tid.dynamic.typeParams.head.variance == Variance.Covariant,
          tid.dynamic.isTrait
        )
      },
      test("SeqId has covariant type parameter") {
        val tid = StandardTypes.SeqId
        assertTrue(
          tid.dynamic.owner == Owner.pkgs("scala", "collection", "immutable"),
          tid.dynamic.name == "Seq",
          tid.dynamic.arity == 1,
          tid.dynamic.typeParams.head.variance == Variance.Covariant,
          tid.dynamic.isTrait
        )
      }
    )
  )
}
