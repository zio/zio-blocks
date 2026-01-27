package zio.blocks.schema.patch

import zio.blocks.chunk.Chunk
import zio.blocks.schema._
import zio.test._

import java.time._

/**
 * Comprehensive tests for the Differ module to dramatically increase schemaJVM
 * coverage. Differ computes minimal patches between two DynamicValues.
 */
object DifferSpec extends SchemaBaseSpec {
  def spec: Spec[Any, Any] = suite("DifferSpec")(
    suite("identical values")(
      test("identical primitives return empty patch") {
        val value = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val patch = Differ.diff(value, value)
        assertTrue(patch.isEmpty)
      },
      test("identical strings return empty patch") {
        val value = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        val patch = Differ.diff(value, value)
        assertTrue(patch.isEmpty)
      },
      test("identical records return empty patch") {
        val value = DynamicValue.Record(Chunk(
          ("name", DynamicValue.Primitive(PrimitiveValue.String("Alice"))),
          ("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
        ))
        val patch = Differ.diff(value, value)
        assertTrue(patch.isEmpty)
      },
      test("identical sequences return empty patch") {
        val value = DynamicValue.Sequence(Chunk(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2))
        ))
        val patch = Differ.diff(value, value)
        assertTrue(patch.isEmpty)
      }
    ),
    suite("numeric deltas")(
      test("Int delta") {
        val old = DynamicValue.Primitive(PrimitiveValue.Int(10))
        val newVal = DynamicValue.Primitive(PrimitiveValue.Int(15))
        val patch = Differ.diff(old, newVal)
        assertTrue(!patch.isEmpty)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      },
      test("Long delta") {
        val old = DynamicValue.Primitive(PrimitiveValue.Long(100L))
        val newVal = DynamicValue.Primitive(PrimitiveValue.Long(200L))
        val patch = Differ.diff(old, newVal)
        assertTrue(!patch.isEmpty)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      },
      test("Double delta") {
        val old = DynamicValue.Primitive(PrimitiveValue.Double(1.5))
        val newVal = DynamicValue.Primitive(PrimitiveValue.Double(3.5))
        val patch = Differ.diff(old, newVal)
        assertTrue(!patch.isEmpty)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      },
      test("Float delta") {
        val old = DynamicValue.Primitive(PrimitiveValue.Float(2.0f))
        val newVal = DynamicValue.Primitive(PrimitiveValue.Float(4.5f))
        val patch = Differ.diff(old, newVal)
        assertTrue(!patch.isEmpty)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      },
      test("Short delta") {
        val old = DynamicValue.Primitive(PrimitiveValue.Short(10))
        val newVal = DynamicValue.Primitive(PrimitiveValue.Short(20))
        val patch = Differ.diff(old, newVal)
        assertTrue(!patch.isEmpty)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      },
      test("Byte delta") {
        val old = DynamicValue.Primitive(PrimitiveValue.Byte(5))
        val newVal = DynamicValue.Primitive(PrimitiveValue.Byte(10))
        val patch = Differ.diff(old, newVal)
        assertTrue(!patch.isEmpty)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      },
      test("BigInt delta") {
        val old = DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(1000)))
        val newVal = DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(2500)))
        val patch = Differ.diff(old, newVal)
        assertTrue(!patch.isEmpty)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      },
      test("BigDecimal delta") {
        val old = DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal("123.456")))
        val newVal = DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal("789.012")))
        val patch = Differ.diff(old, newVal)
        assertTrue(!patch.isEmpty)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      }
    ),
    suite("Float/Double NaN handling")(
      test("Double NaN to NaN returns empty patch") {
        val old = DynamicValue.Primitive(PrimitiveValue.Double(Double.NaN))
        val newVal = DynamicValue.Primitive(PrimitiveValue.Double(Double.NaN))
        val patch = Differ.diff(old, newVal)
        assertTrue(patch.isEmpty)
      },
      test("Float NaN to NaN returns empty patch") {
        val old = DynamicValue.Primitive(PrimitiveValue.Float(Float.NaN))
        val newVal = DynamicValue.Primitive(PrimitiveValue.Float(Float.NaN))
        val patch = Differ.diff(old, newVal)
        assertTrue(patch.isEmpty)
      },
      test("Double value to NaN uses Set") {
        val old = DynamicValue.Primitive(PrimitiveValue.Double(1.0))
        val newVal = DynamicValue.Primitive(PrimitiveValue.Double(Double.NaN))
        val patch = Differ.diff(old, newVal)
        assertTrue(!patch.isEmpty)
      },
      test("Float value to NaN uses Set") {
        val old = DynamicValue.Primitive(PrimitiveValue.Float(1.0f))
        val newVal = DynamicValue.Primitive(PrimitiveValue.Float(Float.NaN))
        val patch = Differ.diff(old, newVal)
        assertTrue(!patch.isEmpty)
      }
    ),
    suite("string edits")(
      test("simple string change") {
        val old = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        val newVal = DynamicValue.Primitive(PrimitiveValue.String("world"))
        val patch = Differ.diff(old, newVal)
        assertTrue(!patch.isEmpty)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      },
      test("string insertion") {
        val old = DynamicValue.Primitive(PrimitiveValue.String("ab"))
        val newVal = DynamicValue.Primitive(PrimitiveValue.String("axb"))
        val patch = Differ.diff(old, newVal)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      },
      test("string deletion") {
        val old = DynamicValue.Primitive(PrimitiveValue.String("abc"))
        val newVal = DynamicValue.Primitive(PrimitiveValue.String("ac"))
        val patch = Differ.diff(old, newVal)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      },
      test("empty to non-empty string") {
        val old = DynamicValue.Primitive(PrimitiveValue.String(""))
        val newVal = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        val patch = Differ.diff(old, newVal)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      },
      test("non-empty to empty string") {
        val old = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        val newVal = DynamicValue.Primitive(PrimitiveValue.String(""))
        val patch = Differ.diff(old, newVal)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      },
      test("long string with common subsequence") {
        val old = DynamicValue.Primitive(PrimitiveValue.String("The quick brown fox"))
        val newVal = DynamicValue.Primitive(PrimitiveValue.String("The slow brown dog"))
        val patch = Differ.diff(old, newVal)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      }
    ),
    suite("temporal deltas")(
      test("Instant delta") {
        val old = DynamicValue.Primitive(PrimitiveValue.Instant(Instant.parse("2023-01-01T00:00:00Z")))
        val newVal = DynamicValue.Primitive(PrimitiveValue.Instant(Instant.parse("2023-01-02T12:00:00Z")))
        val patch = Differ.diff(old, newVal)
        assertTrue(!patch.isEmpty)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      },
      test("LocalDate delta") {
        val old = DynamicValue.Primitive(PrimitiveValue.LocalDate(LocalDate.of(2023, 1, 1)))
        val newVal = DynamicValue.Primitive(PrimitiveValue.LocalDate(LocalDate.of(2023, 6, 15)))
        val patch = Differ.diff(old, newVal)
        assertTrue(!patch.isEmpty)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      },
      test("Duration delta") {
        val old = DynamicValue.Primitive(PrimitiveValue.Duration(Duration.ofHours(1)))
        val newVal = DynamicValue.Primitive(PrimitiveValue.Duration(Duration.ofHours(5)))
        val patch = Differ.diff(old, newVal)
        assertTrue(!patch.isEmpty)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      },
      test("Period delta") {
        val old = DynamicValue.Primitive(PrimitiveValue.Period(Period.ofMonths(1)))
        val newVal = DynamicValue.Primitive(PrimitiveValue.Period(Period.ofMonths(6)))
        val patch = Differ.diff(old, newVal)
        assertTrue(!patch.isEmpty)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      },
      test("LocalDateTime delta") {
        val old = DynamicValue.Primitive(PrimitiveValue.LocalDateTime(LocalDateTime.of(2023, 1, 1, 0, 0)))
        val newVal = DynamicValue.Primitive(PrimitiveValue.LocalDateTime(LocalDateTime.of(2024, 6, 15, 12, 30)))
        val patch = Differ.diff(old, newVal)
        assertTrue(!patch.isEmpty)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      }
    ),
    suite("record diffs")(
      test("single field change") {
        val old = DynamicValue.Record(Chunk(
          ("name", DynamicValue.Primitive(PrimitiveValue.String("Alice"))),
          ("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
        ))
        val newVal = DynamicValue.Record(Chunk(
          ("name", DynamicValue.Primitive(PrimitiveValue.String("Bob"))),
          ("age", DynamicValue.Primitive(PrimitiveValue.Int(30)))
        ))
        val patch = Differ.diff(old, newVal)
        assertTrue(!patch.isEmpty)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      },
      test("multiple field changes") {
        val old = DynamicValue.Record(Chunk(
          ("name", DynamicValue.Primitive(PrimitiveValue.String("Alice"))),
          ("age", DynamicValue.Primitive(PrimitiveValue.Int(30))),
          ("city", DynamicValue.Primitive(PrimitiveValue.String("NYC")))
        ))
        val newVal = DynamicValue.Record(Chunk(
          ("name", DynamicValue.Primitive(PrimitiveValue.String("Bob"))),
          ("age", DynamicValue.Primitive(PrimitiveValue.Int(25))),
          ("city", DynamicValue.Primitive(PrimitiveValue.String("LA")))
        ))
        val patch = Differ.diff(old, newVal)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      },
      test("nested record change") {
        val old = DynamicValue.Record(Chunk(
          ("user", DynamicValue.Record(Chunk(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("Alice")))
          )))
        ))
        val newVal = DynamicValue.Record(Chunk(
          ("user", DynamicValue.Record(Chunk(
            ("name", DynamicValue.Primitive(PrimitiveValue.String("Bob")))
          )))
        ))
        val patch = Differ.diff(old, newVal)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      }
    ),
    suite("variant diffs")(
      test("same case different value") {
        val old = DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val newVal = DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.Int(10)))
        val patch = Differ.diff(old, newVal)
        assertTrue(!patch.isEmpty)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      },
      test("different cases replaces whole variant") {
        val old = DynamicValue.Variant("Some", DynamicValue.Primitive(PrimitiveValue.Int(1)))
        val newVal = DynamicValue.Variant("None", DynamicValue.Record(Chunk.empty))
        val patch = Differ.diff(old, newVal)
        assertTrue(!patch.isEmpty)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      }
    ),
    suite("sequence diffs")(
      test("append element") {
        val old = DynamicValue.Sequence(Chunk(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2))
        ))
        val newVal = DynamicValue.Sequence(Chunk(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2)),
          DynamicValue.Primitive(PrimitiveValue.Int(3))
        ))
        val patch = Differ.diff(old, newVal)
        assertTrue(!patch.isEmpty)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      },
      test("remove element") {
        val old = DynamicValue.Sequence(Chunk(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2)),
          DynamicValue.Primitive(PrimitiveValue.Int(3))
        ))
        val newVal = DynamicValue.Sequence(Chunk(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(3))
        ))
        val patch = Differ.diff(old, newVal)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      },
      test("modify element in place") {
        val old = DynamicValue.Sequence(Chunk(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2)),
          DynamicValue.Primitive(PrimitiveValue.Int(3))
        ))
        val newVal = DynamicValue.Sequence(Chunk(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(200)),
          DynamicValue.Primitive(PrimitiveValue.Int(3))
        ))
        val patch = Differ.diff(old, newVal)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      },
      test("empty to non-empty sequence") {
        val old = DynamicValue.Sequence(Chunk.empty)
        val newVal = DynamicValue.Sequence(Chunk(
          DynamicValue.Primitive(PrimitiveValue.Int(1))
        ))
        val patch = Differ.diff(old, newVal)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      },
      test("non-empty to empty sequence") {
        val old = DynamicValue.Sequence(Chunk(
          DynamicValue.Primitive(PrimitiveValue.Int(1)),
          DynamicValue.Primitive(PrimitiveValue.Int(2))
        ))
        val newVal = DynamicValue.Sequence(Chunk.empty)
        val patch = Differ.diff(old, newVal)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      }
    ),
    suite("map diffs")(
      test("add key") {
        val old = DynamicValue.Map(Chunk(
          (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1)))
        ))
        val newVal = DynamicValue.Map(Chunk(
          (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1))),
          (DynamicValue.Primitive(PrimitiveValue.String("b")), DynamicValue.Primitive(PrimitiveValue.Int(2)))
        ))
        val patch = Differ.diff(old, newVal)
        assertTrue(!patch.isEmpty)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      },
      test("remove key") {
        val old = DynamicValue.Map(Chunk(
          (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1))),
          (DynamicValue.Primitive(PrimitiveValue.String("b")), DynamicValue.Primitive(PrimitiveValue.Int(2)))
        ))
        val newVal = DynamicValue.Map(Chunk(
          (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1)))
        ))
        val patch = Differ.diff(old, newVal)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      },
      test("modify value at key") {
        val old = DynamicValue.Map(Chunk(
          (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1)))
        ))
        val newVal = DynamicValue.Map(Chunk(
          (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(100)))
        ))
        val patch = Differ.diff(old, newVal)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      }
    ),
    suite("type mismatches")(
      test("different types use Set") {
        val old = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val newVal = DynamicValue.Primitive(PrimitiveValue.String("hello"))
        val patch = Differ.diff(old, newVal)
        assertTrue(!patch.isEmpty)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      },
      test("primitive to record uses Set") {
        val old = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val newVal = DynamicValue.Record(Chunk(
          ("value", DynamicValue.Primitive(PrimitiveValue.Int(42)))
        ))
        val patch = Differ.diff(old, newVal)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      },
      test("sequence to map uses Set") {
        val old = DynamicValue.Sequence(Chunk(DynamicValue.Primitive(PrimitiveValue.Int(1))))
        val newVal = DynamicValue.Map(Chunk(
          (DynamicValue.Primitive(PrimitiveValue.String("a")), DynamicValue.Primitive(PrimitiveValue.Int(1)))
        ))
        val patch = Differ.diff(old, newVal)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      }
    ),
    suite("non-delta primitive types")(
      test("Boolean change uses Set") {
        val old = DynamicValue.Primitive(PrimitiveValue.Boolean(true))
        val newVal = DynamicValue.Primitive(PrimitiveValue.Boolean(false))
        val patch = Differ.diff(old, newVal)
        assertTrue(!patch.isEmpty)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      },
      test("Char change uses Set") {
        val old = DynamicValue.Primitive(PrimitiveValue.Char('a'))
        val newVal = DynamicValue.Primitive(PrimitiveValue.Char('z'))
        val patch = Differ.diff(old, newVal)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      },
      test("UUID change uses Set") {
        val uuid1 = java.util.UUID.randomUUID()
        val uuid2 = java.util.UUID.randomUUID()
        val old = DynamicValue.Primitive(PrimitiveValue.UUID(uuid1))
        val newVal = DynamicValue.Primitive(PrimitiveValue.UUID(uuid2))
        val patch = Differ.diff(old, newVal)
        val result = patch.apply(old)
        assertTrue(result == Right(newVal))
      }
    ),
    suite("Null handling")(
      test("Null to Null returns empty patch") {
        val patch = Differ.diff(DynamicValue.Null, DynamicValue.Null)
        assertTrue(patch.isEmpty)
      },
      test("Null to value uses Set") {
        val newVal = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val patch = Differ.diff(DynamicValue.Null, newVal)
        assertTrue(!patch.isEmpty)
        val result = patch.apply(DynamicValue.Null)
        assertTrue(result == Right(newVal))
      },
      test("value to Null uses Set") {
        val old = DynamicValue.Primitive(PrimitiveValue.Int(42))
        val patch = Differ.diff(old, DynamicValue.Null)
        val result = patch.apply(old)
        assertTrue(result == Right(DynamicValue.Null))
      }
    )
  )
}
