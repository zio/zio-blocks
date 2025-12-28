package zio.blocks.schema

private[schema] final class SelectableFromMap(private val map: collection.immutable.Map[String, Any]) extends scala.Selectable {
  def selectDynamic(name: String): Any = map(name)
  override def toString: String = s"Selectable(${map.toString})"
}

private[schema] object StructuralConverters {
  def toSelectable(dv: DynamicValue): scala.Selectable = toScala(dv) match {
    case s: scala.Selectable => s
    case other => new SelectableFromMap(Map("value" -> other))
  }

  private def toScala(dv: DynamicValue): Any = dv match {
    case DynamicValue.Primitive(p) => primitiveToScala(p)
    case DynamicValue.Record(fields) =>
      val m = fields.map { case (k, v) => k -> toScala(v) }.toMap
      new SelectableFromMap(m)
    case DynamicValue.Sequence(elems) => elems.map(toScala).toVector
    case DynamicValue.Map(entries) => entries.map { case (k, v) => toScala(k) -> toScala(v) }.toMap
    case DynamicValue.Variant(name, value) => (name, toScala(value))
  }

  private def primitiveToScala(p: PrimitiveValue): Any = p match {
    case PrimitiveValue.Unit           => ()
    case PrimitiveValue.Boolean(v)    => v
    case PrimitiveValue.Byte(v)       => v
    case PrimitiveValue.Short(v)      => v
    case PrimitiveValue.Int(v)        => v
    case PrimitiveValue.Long(v)       => v
    case PrimitiveValue.Float(v)      => v
    case PrimitiveValue.Double(v)     => v
    case PrimitiveValue.Char(v)       => v
    case PrimitiveValue.String(v)     => v
    case PrimitiveValue.BigInt(v)     => v
    case PrimitiveValue.BigDecimal(v) => v
    case PrimitiveValue.DayOfWeek(v)  => v
    case PrimitiveValue.Duration(v)   => v
    case PrimitiveValue.Instant(v)    => v
    case PrimitiveValue.LocalDate(v)  => v
    case PrimitiveValue.LocalDateTime(v) => v
    case PrimitiveValue.LocalTime(v)  => v
    case PrimitiveValue.Month(v)      => v
    case PrimitiveValue.MonthDay(v)   => v
    case PrimitiveValue.OffsetDateTime(v) => v
    case PrimitiveValue.OffsetTime(v) => v
    case PrimitiveValue.Period(v)     => v
    case PrimitiveValue.Year(v)       => v
    case PrimitiveValue.YearMonth(v)  => v
    case PrimitiveValue.ZoneId(v)     => v
    case PrimitiveValue.ZoneOffset(v) => v
    case PrimitiveValue.ZonedDateTime(v) => v
    case PrimitiveValue.Currency(v)   => v
    case PrimitiveValue.UUID(v)       => v
  }

  def fromSelectable(s: scala.Selectable, fieldNames: Seq[String]): DynamicValue = s match {
    case sm: SelectableFromMap =>
      val fields = sm.map.iterator.map { case (k, v) => k -> fromAny(v) }.toVector
      DynamicValue.Record(fields)
    case _ =>
      // Build record from expected field names by calling selectDynamic
      val fields = fieldNames.map { name => name -> fromAny(s.selectDynamic(name)) }.toVector
      DynamicValue.Record(fields)
  }

  private def fromAny(a: Any): DynamicValue = a match {
    case null => DynamicValue.Primitive(PrimitiveValue.Unit)
    case () => DynamicValue.Primitive(PrimitiveValue.Unit)
    case b: Boolean => DynamicValue.Primitive(PrimitiveValue.Boolean(b))
    case b: Byte => DynamicValue.Primitive(PrimitiveValue.Byte(b))
    case s: Short => DynamicValue.Primitive(PrimitiveValue.Short(s))
    case i: Int => DynamicValue.Primitive(PrimitiveValue.Int(i))
    case l: Long => DynamicValue.Primitive(PrimitiveValue.Long(l))
    case f: Float => DynamicValue.Primitive(PrimitiveValue.Float(f))
    case d: Double => DynamicValue.Primitive(PrimitiveValue.Double(d))
    case c: Char => DynamicValue.Primitive(PrimitiveValue.Char(c))
    case s: String => DynamicValue.Primitive(PrimitiveValue.String(s))
    case bi: BigInt => DynamicValue.Primitive(PrimitiveValue.BigInt(bi))
    case bd: BigDecimal => DynamicValue.Primitive(PrimitiveValue.BigDecimal(bd))
    case u: java.util.UUID => DynamicValue.Primitive(PrimitiveValue.UUID(u))
    case seq: Seq[?] => DynamicValue.Sequence(seq.toVector.map(fromAny))
    case map: collection.Map[?, ?] =>
      val entries = map.iterator.map { case (k, v) => fromAny(k) -> fromAny(v) }.toVector
      DynamicValue.Map(entries)
    case sel: scala.Selectable =>
      // Without field names we cannot extract fields; try to cast to SelectableFromMap
      sel match {
        case sm: SelectableFromMap =>
          val entries = sm.map.iterator.map { case (k, v) => k -> fromAny(v) }.toVector
          DynamicValue.Record(entries)
        case _ => DynamicValue.Record(Vector.empty)
      }
    case other =>
      // Fallback: convert to string
      DynamicValue.Primitive(PrimitiveValue.String(other.toString))
  }
}
