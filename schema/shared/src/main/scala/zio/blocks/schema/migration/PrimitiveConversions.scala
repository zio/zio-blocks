package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, PrimitiveValue}

/**
 * Pure data conversions between primitive types.
 *
 * All conversions are statically defined lookup tables - no user-provided
 * functions. This ensures migrations remain fully serializable and can be
 * inspected, transformed, or used to generate code.
 *
 * Supported conversion categories:
 *   - Numeric widening (Int -> Long -> Double, etc.)
 *   - Numeric narrowing with bounds checking (Long -> Int, etc.)
 *   - To/from String for all types via parsing
 *   - Between BigInt/BigDecimal and regular numerics
 *   - Temporal type parsing from String
 *
 * Conversions are identified by type names (strings) rather than PrimitiveType
 * values to maintain serializability without requiring PrimitiveType instances
 * at runtime.
 */
object PrimitiveConversions {

  /**
   * Convert a DynamicValue between primitive types.
   *
   * @param value
   *   The value to convert
   * @param fromType
   *   Name of the source type (e.g., "Int", "String")
   * @param toType
   *   Name of the target type (e.g., "Long", "Double")
   * @return
   *   Right with converted value, or Left with error message
   */
  def convert(
    value: DynamicValue,
    fromType: String,
    toType: String
  ): Either[String, DynamicValue] =
    (fromType, toType, value) match {
      // ─────────────────────────────────────────────────────────────────────
      // Identity conversions
      // ─────────────────────────────────────────────────────────────────────
      case (from, to, v) if from == to => Right(v)

      // ─────────────────────────────────────────────────────────────────────
      // Numeric widening (lossless)
      // ─────────────────────────────────────────────────────────────────────

      // Byte -> wider types
      case ("Byte", "Short", DynamicValue.Primitive(PrimitiveValue.Byte(b))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Short(b.toShort)))
      case ("Byte", "Int", DynamicValue.Primitive(PrimitiveValue.Byte(b))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Int(b.toInt)))
      case ("Byte", "Long", DynamicValue.Primitive(PrimitiveValue.Byte(b))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Long(b.toLong)))
      case ("Byte", "Float", DynamicValue.Primitive(PrimitiveValue.Byte(b))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Float(b.toFloat)))
      case ("Byte", "Double", DynamicValue.Primitive(PrimitiveValue.Byte(b))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Double(b.toDouble)))
      case ("Byte", "BigInt", DynamicValue.Primitive(PrimitiveValue.Byte(b))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(b))))
      case ("Byte", "BigDecimal", DynamicValue.Primitive(PrimitiveValue.Byte(b))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(b))))

      // Short -> wider types
      case ("Short", "Int", DynamicValue.Primitive(PrimitiveValue.Short(s))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Int(s.toInt)))
      case ("Short", "Long", DynamicValue.Primitive(PrimitiveValue.Short(s))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Long(s.toLong)))
      case ("Short", "Float", DynamicValue.Primitive(PrimitiveValue.Short(s))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Float(s.toFloat)))
      case ("Short", "Double", DynamicValue.Primitive(PrimitiveValue.Short(s))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Double(s.toDouble)))
      case ("Short", "BigInt", DynamicValue.Primitive(PrimitiveValue.Short(s))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(s))))
      case ("Short", "BigDecimal", DynamicValue.Primitive(PrimitiveValue.Short(s))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(s))))

      // Int -> wider types
      case ("Int", "Long", DynamicValue.Primitive(PrimitiveValue.Int(i))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Long(i.toLong)))
      case ("Int", "Float", DynamicValue.Primitive(PrimitiveValue.Int(i))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Float(i.toFloat)))
      case ("Int", "Double", DynamicValue.Primitive(PrimitiveValue.Int(i))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Double(i.toDouble)))
      case ("Int", "BigInt", DynamicValue.Primitive(PrimitiveValue.Int(i))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(i))))
      case ("Int", "BigDecimal", DynamicValue.Primitive(PrimitiveValue.Int(i))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(i))))

      // Long -> wider types
      case ("Long", "Float", DynamicValue.Primitive(PrimitiveValue.Long(l))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Float(l.toFloat)))
      case ("Long", "Double", DynamicValue.Primitive(PrimitiveValue.Long(l))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Double(l.toDouble)))
      case ("Long", "BigInt", DynamicValue.Primitive(PrimitiveValue.Long(l))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(l))))
      case ("Long", "BigDecimal", DynamicValue.Primitive(PrimitiveValue.Long(l))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(l))))

      // Float -> wider types
      case ("Float", "Double", DynamicValue.Primitive(PrimitiveValue.Float(f))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Double(f.toDouble)))
      case ("Float", "BigDecimal", DynamicValue.Primitive(PrimitiveValue.Float(f))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(f.toDouble))))

      // Double -> BigDecimal
      case ("Double", "BigDecimal", DynamicValue.Primitive(PrimitiveValue.Double(d))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(d))))

      // BigInt -> BigDecimal
      case ("BigInt", "BigDecimal", DynamicValue.Primitive(PrimitiveValue.BigInt(bi))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(bi))))

      // ─────────────────────────────────────────────────────────────────────
      // Numeric narrowing (may fail for out-of-range values)
      // ─────────────────────────────────────────────────────────────────────

      // Short -> Byte
      case ("Short", "Byte", DynamicValue.Primitive(PrimitiveValue.Short(s))) =>
        if (s >= Byte.MinValue && s <= Byte.MaxValue)
          Right(DynamicValue.Primitive(PrimitiveValue.Byte(s.toByte)))
        else Left(s"Value $s out of Byte range")

      // Int -> smaller types
      case ("Int", "Byte", DynamicValue.Primitive(PrimitiveValue.Int(i))) =>
        if (i >= Byte.MinValue && i <= Byte.MaxValue)
          Right(DynamicValue.Primitive(PrimitiveValue.Byte(i.toByte)))
        else Left(s"Value $i out of Byte range")
      case ("Int", "Short", DynamicValue.Primitive(PrimitiveValue.Int(i))) =>
        if (i >= Short.MinValue && i <= Short.MaxValue)
          Right(DynamicValue.Primitive(PrimitiveValue.Short(i.toShort)))
        else Left(s"Value $i out of Short range")

      // Long -> smaller types
      case ("Long", "Byte", DynamicValue.Primitive(PrimitiveValue.Long(l))) =>
        if (l >= Byte.MinValue && l <= Byte.MaxValue)
          Right(DynamicValue.Primitive(PrimitiveValue.Byte(l.toByte)))
        else Left(s"Value $l out of Byte range")
      case ("Long", "Short", DynamicValue.Primitive(PrimitiveValue.Long(l))) =>
        if (l >= Short.MinValue && l <= Short.MaxValue)
          Right(DynamicValue.Primitive(PrimitiveValue.Short(l.toShort)))
        else Left(s"Value $l out of Short range")
      case ("Long", "Int", DynamicValue.Primitive(PrimitiveValue.Long(l))) =>
        if (l >= Int.MinValue && l <= Int.MaxValue)
          Right(DynamicValue.Primitive(PrimitiveValue.Int(l.toInt)))
        else Left(s"Value $l out of Int range")

      // Float -> Int/Long (truncation)
      case ("Float", "Int", DynamicValue.Primitive(PrimitiveValue.Float(f))) =>
        if (f >= Int.MinValue && f <= Int.MaxValue)
          Right(DynamicValue.Primitive(PrimitiveValue.Int(f.toInt)))
        else Left(s"Value $f out of Int range")
      case ("Float", "Long", DynamicValue.Primitive(PrimitiveValue.Float(f))) =>
        if (f >= Long.MinValue && f <= Long.MaxValue)
          Right(DynamicValue.Primitive(PrimitiveValue.Long(f.toLong)))
        else Left(s"Value $f out of Long range")

      // Double -> smaller numeric types
      case ("Double", "Float", DynamicValue.Primitive(PrimitiveValue.Double(d))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Float(d.toFloat)))
      case ("Double", "Int", DynamicValue.Primitive(PrimitiveValue.Double(d))) =>
        if (d >= Int.MinValue && d <= Int.MaxValue)
          Right(DynamicValue.Primitive(PrimitiveValue.Int(d.toInt)))
        else Left(s"Value $d out of Int range")
      case ("Double", "Long", DynamicValue.Primitive(PrimitiveValue.Double(d))) =>
        if (d >= Long.MinValue && d <= Long.MaxValue)
          Right(DynamicValue.Primitive(PrimitiveValue.Long(d.toLong)))
        else Left(s"Value $d out of Long range")

      // BigInt -> smaller types
      case ("BigInt", "Int", DynamicValue.Primitive(PrimitiveValue.BigInt(bi))) =>
        if (bi.isValidInt) Right(DynamicValue.Primitive(PrimitiveValue.Int(bi.toInt)))
        else Left(s"Value $bi out of Int range")
      case ("BigInt", "Long", DynamicValue.Primitive(PrimitiveValue.BigInt(bi))) =>
        if (bi.isValidLong) Right(DynamicValue.Primitive(PrimitiveValue.Long(bi.toLong)))
        else Left(s"Value $bi out of Long range")

      // BigDecimal -> smaller types
      case ("BigDecimal", "Int", DynamicValue.Primitive(PrimitiveValue.BigDecimal(bd))) =>
        if (bd.isValidInt) Right(DynamicValue.Primitive(PrimitiveValue.Int(bd.toInt)))
        else Left(s"Value $bd out of Int range")
      case ("BigDecimal", "Long", DynamicValue.Primitive(PrimitiveValue.BigDecimal(bd))) =>
        if (bd.isValidLong) Right(DynamicValue.Primitive(PrimitiveValue.Long(bd.toLong)))
        else Left(s"Value $bd out of Long range")
      case ("BigDecimal", "Double", DynamicValue.Primitive(PrimitiveValue.BigDecimal(bd))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Double(bd.toDouble)))
      case ("BigDecimal", "BigInt", DynamicValue.Primitive(PrimitiveValue.BigDecimal(bd))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.BigInt(bd.toBigInt)))

      // ─────────────────────────────────────────────────────────────────────
      // Any primitive -> String
      // ─────────────────────────────────────────────────────────────────────
      case (_, "String", DynamicValue.Primitive(pv)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.String(primitiveToString(pv))))

      // ─────────────────────────────────────────────────────────────────────
      // String -> numeric types (parsing)
      // ─────────────────────────────────────────────────────────────────────
      case ("String", "Byte", DynamicValue.Primitive(PrimitiveValue.String(s))) =>
        s.toByteOption
          .map(v => DynamicValue.Primitive(PrimitiveValue.Byte(v)))
          .toRight(s"Cannot parse '$s' as Byte")
      case ("String", "Short", DynamicValue.Primitive(PrimitiveValue.String(s))) =>
        s.toShortOption
          .map(v => DynamicValue.Primitive(PrimitiveValue.Short(v)))
          .toRight(s"Cannot parse '$s' as Short")
      case ("String", "Int", DynamicValue.Primitive(PrimitiveValue.String(s))) =>
        s.toIntOption
          .map(v => DynamicValue.Primitive(PrimitiveValue.Int(v)))
          .toRight(s"Cannot parse '$s' as Int")
      case ("String", "Long", DynamicValue.Primitive(PrimitiveValue.String(s))) =>
        s.toLongOption
          .map(v => DynamicValue.Primitive(PrimitiveValue.Long(v)))
          .toRight(s"Cannot parse '$s' as Long")
      case ("String", "Float", DynamicValue.Primitive(PrimitiveValue.String(s))) =>
        s.toFloatOption
          .map(v => DynamicValue.Primitive(PrimitiveValue.Float(v)))
          .toRight(s"Cannot parse '$s' as Float")
      case ("String", "Double", DynamicValue.Primitive(PrimitiveValue.String(s))) =>
        s.toDoubleOption
          .map(v => DynamicValue.Primitive(PrimitiveValue.Double(v)))
          .toRight(s"Cannot parse '$s' as Double")
      case ("String", "Boolean", DynamicValue.Primitive(PrimitiveValue.String(s))) =>
        s.toBooleanOption
          .map(v => DynamicValue.Primitive(PrimitiveValue.Boolean(v)))
          .toRight(s"Cannot parse '$s' as Boolean")
      case ("String", "BigInt", DynamicValue.Primitive(PrimitiveValue.String(s))) =>
        try Right(DynamicValue.Primitive(PrimitiveValue.BigInt(BigInt(s))))
        catch { case _: NumberFormatException => Left(s"Cannot parse '$s' as BigInt") }
      case ("String", "BigDecimal", DynamicValue.Primitive(PrimitiveValue.String(s))) =>
        try Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(BigDecimal(s))))
        catch { case _: NumberFormatException => Left(s"Cannot parse '$s' as BigDecimal") }
      case ("String", "Char", DynamicValue.Primitive(PrimitiveValue.String(s))) =>
        if (s.length == 1) Right(DynamicValue.Primitive(PrimitiveValue.Char(s.head)))
        else Left(s"Cannot convert string of length ${s.length} to Char")

      // ─────────────────────────────────────────────────────────────────────
      // String -> UUID
      // ─────────────────────────────────────────────────────────────────────
      case ("String", "UUID", DynamicValue.Primitive(PrimitiveValue.String(s))) =>
        try Right(DynamicValue.Primitive(PrimitiveValue.UUID(java.util.UUID.fromString(s))))
        catch { case _: IllegalArgumentException => Left(s"Cannot parse '$s' as UUID") }

      // ─────────────────────────────────────────────────────────────────────
      // String -> temporal types
      // ─────────────────────────────────────────────────────────────────────
      case ("String", "Instant", DynamicValue.Primitive(PrimitiveValue.String(s))) =>
        try Right(DynamicValue.Primitive(PrimitiveValue.Instant(java.time.Instant.parse(s))))
        catch { case _: java.time.format.DateTimeParseException => Left(s"Cannot parse '$s' as Instant") }
      case ("String", "LocalDate", DynamicValue.Primitive(PrimitiveValue.String(s))) =>
        try Right(DynamicValue.Primitive(PrimitiveValue.LocalDate(java.time.LocalDate.parse(s))))
        catch { case _: java.time.format.DateTimeParseException => Left(s"Cannot parse '$s' as LocalDate") }
      case ("String", "LocalTime", DynamicValue.Primitive(PrimitiveValue.String(s))) =>
        try Right(DynamicValue.Primitive(PrimitiveValue.LocalTime(java.time.LocalTime.parse(s))))
        catch { case _: java.time.format.DateTimeParseException => Left(s"Cannot parse '$s' as LocalTime") }
      case ("String", "LocalDateTime", DynamicValue.Primitive(PrimitiveValue.String(s))) =>
        try Right(DynamicValue.Primitive(PrimitiveValue.LocalDateTime(java.time.LocalDateTime.parse(s))))
        catch { case _: java.time.format.DateTimeParseException => Left(s"Cannot parse '$s' as LocalDateTime") }
      case ("String", "OffsetDateTime", DynamicValue.Primitive(PrimitiveValue.String(s))) =>
        try Right(DynamicValue.Primitive(PrimitiveValue.OffsetDateTime(java.time.OffsetDateTime.parse(s))))
        catch { case _: java.time.format.DateTimeParseException => Left(s"Cannot parse '$s' as OffsetDateTime") }
      case ("String", "OffsetTime", DynamicValue.Primitive(PrimitiveValue.String(s))) =>
        try Right(DynamicValue.Primitive(PrimitiveValue.OffsetTime(java.time.OffsetTime.parse(s))))
        catch { case _: java.time.format.DateTimeParseException => Left(s"Cannot parse '$s' as OffsetTime") }
      case ("String", "ZonedDateTime", DynamicValue.Primitive(PrimitiveValue.String(s))) =>
        try Right(DynamicValue.Primitive(PrimitiveValue.ZonedDateTime(java.time.ZonedDateTime.parse(s))))
        catch { case _: java.time.format.DateTimeParseException => Left(s"Cannot parse '$s' as ZonedDateTime") }
      case ("String", "Duration", DynamicValue.Primitive(PrimitiveValue.String(s))) =>
        try Right(DynamicValue.Primitive(PrimitiveValue.Duration(java.time.Duration.parse(s))))
        catch { case _: java.time.format.DateTimeParseException => Left(s"Cannot parse '$s' as Duration") }
      case ("String", "Period", DynamicValue.Primitive(PrimitiveValue.String(s))) =>
        try Right(DynamicValue.Primitive(PrimitiveValue.Period(java.time.Period.parse(s))))
        catch { case _: java.time.format.DateTimeParseException => Left(s"Cannot parse '$s' as Period") }
      case ("String", "Year", DynamicValue.Primitive(PrimitiveValue.String(s))) =>
        try Right(DynamicValue.Primitive(PrimitiveValue.Year(java.time.Year.parse(s))))
        catch { case _: java.time.format.DateTimeParseException => Left(s"Cannot parse '$s' as Year") }
      case ("String", "YearMonth", DynamicValue.Primitive(PrimitiveValue.String(s))) =>
        try Right(DynamicValue.Primitive(PrimitiveValue.YearMonth(java.time.YearMonth.parse(s))))
        catch { case _: java.time.format.DateTimeParseException => Left(s"Cannot parse '$s' as YearMonth") }
      case ("String", "MonthDay", DynamicValue.Primitive(PrimitiveValue.String(s))) =>
        try Right(DynamicValue.Primitive(PrimitiveValue.MonthDay(java.time.MonthDay.parse(s))))
        catch { case _: java.time.format.DateTimeParseException => Left(s"Cannot parse '$s' as MonthDay") }
      case ("String", "DayOfWeek", DynamicValue.Primitive(PrimitiveValue.String(s))) =>
        try Right(DynamicValue.Primitive(PrimitiveValue.DayOfWeek(java.time.DayOfWeek.valueOf(s.toUpperCase))))
        catch { case _: IllegalArgumentException => Left(s"Cannot parse '$s' as DayOfWeek") }
      case ("String", "Month", DynamicValue.Primitive(PrimitiveValue.String(s))) =>
        try Right(DynamicValue.Primitive(PrimitiveValue.Month(java.time.Month.valueOf(s.toUpperCase))))
        catch { case _: IllegalArgumentException => Left(s"Cannot parse '$s' as Month") }
      case ("String", "ZoneId", DynamicValue.Primitive(PrimitiveValue.String(s))) =>
        try Right(DynamicValue.Primitive(PrimitiveValue.ZoneId(java.time.ZoneId.of(s))))
        catch { case _: java.time.zone.ZoneRulesException => Left(s"Cannot parse '$s' as ZoneId") }
      case ("String", "ZoneOffset", DynamicValue.Primitive(PrimitiveValue.String(s))) =>
        try Right(DynamicValue.Primitive(PrimitiveValue.ZoneOffset(java.time.ZoneOffset.of(s))))
        catch { case _: java.time.DateTimeException => Left(s"Cannot parse '$s' as ZoneOffset") }

      // ─────────────────────────────────────────────────────────────────────
      // String -> Currency
      // ─────────────────────────────────────────────────────────────────────
      case ("String", "Currency", DynamicValue.Primitive(PrimitiveValue.String(s))) =>
        try Right(DynamicValue.Primitive(PrimitiveValue.Currency(java.util.Currency.getInstance(s))))
        catch {
          case _: IllegalArgumentException => Left(s"Cannot parse '$s' as Currency")
          case _: NoSuchElementException   => Left(s"Cannot parse '$s' as Currency")
        }

      // ─────────────────────────────────────────────────────────────────────
      // Char <-> Int conversions
      // ─────────────────────────────────────────────────────────────────────
      case ("Char", "Int", DynamicValue.Primitive(PrimitiveValue.Char(c))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Int(c.toInt)))
      case ("Int", "Char", DynamicValue.Primitive(PrimitiveValue.Int(i))) =>
        if (i >= Char.MinValue.toInt && i <= Char.MaxValue.toInt)
          Right(DynamicValue.Primitive(PrimitiveValue.Char(i.toChar)))
        else Left(s"Value $i out of Char range")

      // ─────────────────────────────────────────────────────────────────────
      // Boolean <-> Int conversions
      // ─────────────────────────────────────────────────────────────────────
      case ("Boolean", "Int", DynamicValue.Primitive(PrimitiveValue.Boolean(b))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Int(if (b) 1 else 0)))
      case ("Int", "Boolean", DynamicValue.Primitive(PrimitiveValue.Int(i))) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Boolean(i != 0)))

      // ─────────────────────────────────────────────────────────────────────
      // Unsupported conversion
      // ─────────────────────────────────────────────────────────────────────
      case _ =>
        Left(s"Unsupported conversion from $fromType to $toType")
    }

  /**
   * Convert a PrimitiveValue to its string representation.
   */
  private def primitiveToString(pv: PrimitiveValue): String = pv match {
    case PrimitiveValue.Unit              => "()"
    case PrimitiveValue.Boolean(b)        => b.toString
    case PrimitiveValue.Byte(b)           => b.toString
    case PrimitiveValue.Short(s)          => s.toString
    case PrimitiveValue.Int(i)            => i.toString
    case PrimitiveValue.Long(l)           => l.toString
    case PrimitiveValue.Float(f)          => f.toString
    case PrimitiveValue.Double(d)         => d.toString
    case PrimitiveValue.Char(c)           => c.toString
    case PrimitiveValue.String(s)         => s
    case PrimitiveValue.BigInt(bi)        => bi.toString
    case PrimitiveValue.BigDecimal(bd)    => bd.toString
    case PrimitiveValue.DayOfWeek(dow)    => dow.toString
    case PrimitiveValue.Duration(d)       => d.toString
    case PrimitiveValue.Instant(i)        => i.toString
    case PrimitiveValue.LocalDate(ld)     => ld.toString
    case PrimitiveValue.LocalDateTime(dt) => dt.toString
    case PrimitiveValue.LocalTime(lt)     => lt.toString
    case PrimitiveValue.Month(m)          => m.toString
    case PrimitiveValue.MonthDay(md)      => md.toString
    case PrimitiveValue.OffsetDateTime(o) => o.toString
    case PrimitiveValue.OffsetTime(ot)    => ot.toString
    case PrimitiveValue.Period(p)         => p.toString
    case PrimitiveValue.Year(y)           => y.toString
    case PrimitiveValue.YearMonth(ym)     => ym.toString
    case PrimitiveValue.ZoneId(z)         => z.toString
    case PrimitiveValue.ZoneOffset(zo)    => zo.toString
    case PrimitiveValue.ZonedDateTime(zd) => zd.toString
    case PrimitiveValue.Currency(c)       => c.getCurrencyCode
    case PrimitiveValue.UUID(u)           => u.toString
  }
}
