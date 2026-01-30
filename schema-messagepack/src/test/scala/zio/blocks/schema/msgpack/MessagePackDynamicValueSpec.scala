package zio.blocks.schema.msgpack

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicValue, PrimitiveValue, Schema}
import zio.blocks.schema.DynamicValue.{Primitive, Record, Sequence, Variant}
import zio.blocks.schema.DynamicValueGen.genDynamicValue
import zio.test._

object MessagePackDynamicValueSpec extends ZIOSpecDefault {

  private val codec = Schema[DynamicValue].derive(MessagePackFormat)

  def spec: Spec[Any, Nothing] = suite("MessagePackDynamicValueSpec")(
    suite("DynamicValue roundtrip")(
      test("arbitrary DynamicValue roundtrips") {
        check(genDynamicValue) { value =>
          val encoded    = codec.encode(value)
          val decoded    = codec.decode(encoded)
          val normalized = normalize(value)
          assertTrue(decoded == Right(normalized))
        }
      },
      test("Primitive Unit") {
        val value   = Primitive(PrimitiveValue.Unit)
        val encoded = codec.encode(value)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(normalize(value)))
      },
      test("Primitive Boolean") {
        check(Gen.boolean) { b =>
          val value   = Primitive(PrimitiveValue.Boolean(b))
          val encoded = codec.encode(value)
          val decoded = codec.decode(encoded)
          assertTrue(decoded == Right(normalize(value)))
        }
      },
      test("Primitive Long") {
        check(Gen.long) { l =>
          val value   = Primitive(PrimitiveValue.Long(l))
          val encoded = codec.encode(value)
          val decoded = codec.decode(encoded)
          assertTrue(decoded == Right(normalize(value)))
        }
      },
      test("Primitive String") {
        check(Gen.alphaNumericStringBounded(0, 100)) { s =>
          val value   = Primitive(PrimitiveValue.String(s))
          val encoded = codec.encode(value)
          val decoded = codec.decode(encoded)
          assertTrue(decoded == Right(normalize(value)))
        }
      },
      test("Primitive Float") {
        check(Gen.float) { f =>
          val value   = Primitive(PrimitiveValue.Float(f))
          val encoded = codec.encode(value)
          val decoded = codec.decode(encoded)
          assertTrue(decoded == Right(normalize(value)))
        }
      },
      test("Primitive Double") {
        check(Gen.double) { d =>
          val value   = Primitive(PrimitiveValue.Double(d))
          val encoded = codec.encode(value)
          val decoded = codec.decode(encoded)
          assertTrue(decoded == Right(normalize(value)))
        }
      },
      test("Primitive BigInt") {
        check(Gen.bigInt(BigInt(-1000000000), BigInt(1000000000))) { bi =>
          val value   = Primitive(PrimitiveValue.BigInt(bi))
          val encoded = codec.encode(value)
          val decoded = codec.decode(encoded)
          assertTrue(decoded == Right(normalize(value)))
        }
      },
      test("Primitive BigDecimal") {
        check(Gen.bigDecimal(BigDecimal(-1000000), BigDecimal(1000000))) { bd =>
          val value   = Primitive(PrimitiveValue.BigDecimal(bd))
          val encoded = codec.encode(value)
          val decoded = codec.decode(encoded)
          assertTrue(decoded == Right(normalize(value)))
        }
      },
      test("Record") {
        val value = Record(
          Chunk(
            ("name", Primitive(PrimitiveValue.String("test"))),
            ("age", Primitive(PrimitiveValue.Int(42)))
          )
        )
        val encoded = codec.encode(value)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(normalize(value)))
      },
      test("Sequence") {
        val value = Sequence(
          Chunk(
            Primitive(PrimitiveValue.Int(1)),
            Primitive(PrimitiveValue.String("two")),
            Primitive(PrimitiveValue.Boolean(true))
          )
        )
        val encoded = codec.encode(value)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(normalize(value)))
      },
      test("Variant") {
        val value   = Variant("SomeCase", Primitive(PrimitiveValue.String("inner")))
        val encoded = codec.encode(value)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(normalize(value)))
      },
      test("Map with string keys") {
        val value = DynamicValue.Map(
          Chunk(
            (Primitive(PrimitiveValue.String("key1")), Primitive(PrimitiveValue.Int(1))),
            (Primitive(PrimitiveValue.String("key2")), Primitive(PrimitiveValue.Int(2)))
          )
        )
        val encoded = codec.encode(value)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(normalize(value)))
      },
      test("Map with int keys") {
        val value = DynamicValue.Map(
          Chunk(
            (Primitive(PrimitiveValue.Int(1)), Primitive(PrimitiveValue.String("one"))),
            (Primitive(PrimitiveValue.Int(2)), Primitive(PrimitiveValue.String("two")))
          )
        )
        val encoded = codec.encode(value)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(normalize(value)))
      },
      test("nested Record") {
        val value = Record(
          Chunk(
            (
              "outer",
              Record(
                Chunk(
                  ("inner", Primitive(PrimitiveValue.String("value")))
                )
              )
            )
          )
        )
        val encoded = codec.encode(value)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(normalize(value)))
      },
      test("nested Sequence") {
        val value = Sequence(
          Chunk(
            Sequence(Chunk(Primitive(PrimitiveValue.Int(1)))),
            Sequence(Chunk(Primitive(PrimitiveValue.Int(2))))
          )
        )
        val encoded = codec.encode(value)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(normalize(value)))
      }
    )
  )

  def normalize(value: DynamicValue): DynamicValue = value match {
    case Primitive(PrimitiveValue.Unit)     => Record(Chunk.empty)
    case Primitive(PrimitiveValue.Byte(b))  => Primitive(PrimitiveValue.Long(b.toLong))
    case Primitive(PrimitiveValue.Short(s)) => Primitive(PrimitiveValue.Long(s.toLong))
    case Primitive(PrimitiveValue.Int(i))   => Primitive(PrimitiveValue.Long(i.toLong))

    case Primitive(PrimitiveValue.Char(c)) => Primitive(PrimitiveValue.String(c.toString))

    case Primitive(PrimitiveValue.BigDecimal(bd)) =>
      Record(
        Chunk(
          ("unscaled", Primitive(PrimitiveValue.BigInt(bd.underlying().unscaledValue()))),
          ("precision", Primitive(PrimitiveValue.Long(bd.precision.toLong))),
          ("scale", Primitive(PrimitiveValue.Long(bd.scale.toLong)))
        )
      )

    case Primitive(PrimitiveValue.DayOfWeek(d)) => Primitive(PrimitiveValue.Long(d.getValue.toLong))
    case Primitive(PrimitiveValue.Month(m))     => Primitive(PrimitiveValue.Long(m.getValue.toLong))
    case Primitive(PrimitiveValue.Year(y))      => Primitive(PrimitiveValue.Long(y.getValue.toLong))

    case Primitive(PrimitiveValue.Duration(d)) =>
      Record(
        Chunk(
          ("seconds", Primitive(PrimitiveValue.Long(d.getSeconds))),
          ("nanos", Primitive(PrimitiveValue.Long(d.getNano.toLong)))
        )
      )

    case Primitive(PrimitiveValue.Period(p)) =>
      Record(
        Chunk(
          ("years", Primitive(PrimitiveValue.Long(p.getYears.toLong))),
          ("months", Primitive(PrimitiveValue.Long(p.getMonths.toLong))),
          ("days", Primitive(PrimitiveValue.Long(p.getDays.toLong)))
        )
      )

    case Primitive(PrimitiveValue.MonthDay(m)) =>
      Record(
        Chunk(
          ("month", Primitive(PrimitiveValue.Long(m.getMonthValue.toLong))),
          ("day", Primitive(PrimitiveValue.Long(m.getDayOfMonth.toLong)))
        )
      )

    case Primitive(PrimitiveValue.YearMonth(y)) =>
      Record(
        Chunk(
          ("year", Primitive(PrimitiveValue.Long(y.getYear.toLong))),
          ("month", Primitive(PrimitiveValue.Long(y.getMonthValue.toLong)))
        )
      )

    case Primitive(PrimitiveValue.Instant(i))        => Primitive(PrimitiveValue.String(i.toString))
    case Primitive(PrimitiveValue.LocalDate(d))      => Primitive(PrimitiveValue.String(d.toString))
    case Primitive(PrimitiveValue.LocalDateTime(d))  => Primitive(PrimitiveValue.String(d.toString))
    case Primitive(PrimitiveValue.LocalTime(t))      => Primitive(PrimitiveValue.String(t.toString))
    case Primitive(PrimitiveValue.OffsetDateTime(d)) => Primitive(PrimitiveValue.String(d.toString))
    case Primitive(PrimitiveValue.OffsetTime(t))     => Primitive(PrimitiveValue.String(t.toString))
    case Primitive(PrimitiveValue.ZoneId(z))         => Primitive(PrimitiveValue.String(z.getId))
    case Primitive(PrimitiveValue.ZoneOffset(z))     => Primitive(PrimitiveValue.Long(z.getTotalSeconds.toLong))
    case Primitive(PrimitiveValue.ZonedDateTime(z))  => Primitive(PrimitiveValue.String(z.toString))
    case Primitive(PrimitiveValue.Currency(c))       => Primitive(PrimitiveValue.String(c.getCurrencyCode))
    case Primitive(PrimitiveValue.UUID(u))           => Primitive(PrimitiveValue.String(u.toString))

    case Variant(caseName, inner) =>
      Record(
        Chunk(
          ("_case", Primitive(PrimitiveValue.String(caseName))),
          ("_value", normalize(inner))
        )
      )

    case DynamicValue.Map(entries) =>
      val allStringKeys = entries.forall { case (k, _) =>
        k.isInstanceOf[DynamicValue.Primitive] &&
        k.asInstanceOf[DynamicValue.Primitive].value.isInstanceOf[PrimitiveValue.String]
      }
      if (allStringKeys) {
        Record(entries.map { case (k, v) =>
          val key = k.asInstanceOf[DynamicValue.Primitive].value.asInstanceOf[PrimitiveValue.String].value
          (key, normalize(v))
        })
      } else {
        DynamicValue.Map(entries.map { case (k, v) => (normalize(k), normalize(v)) })
      }

    case Record(fields)  => Record(fields.map { case (k, v) => (k, normalize(v)) })
    case Sequence(elems) => Sequence(elems.map(normalize))

    case other => other
  }
}
