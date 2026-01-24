package zio.blocks.schema

import zio.blocks.schema.patch.{DynamicPatch, Differ}

sealed trait DynamicValue {
  def typeIndex: Int

  def compare(that: DynamicValue): Int

  final def >(that: DynamicValue): Boolean = compare(that) > 0

  final def >=(that: DynamicValue): Boolean = compare(that) >= 0

  final def <(that: DynamicValue): Boolean = compare(that) < 0

  final def <=(that: DynamicValue): Boolean = compare(that) <= 0

  def diff(that: DynamicValue): DynamicPatch = DynamicValue.diff(this, that)

  def toEjson(indent: Int = 0): String = DynamicValue.toEjson(this, indent)

  override def toString: String = toEjson()
}

object DynamicValue {
  case class Primitive(value: PrimitiveValue) extends DynamicValue {
    override def equals(that: Any): Boolean = that match {
      case Primitive(thatValue) => value == thatValue
      case _                    => false
    }

    override def hashCode: Int = value.hashCode

    def typeIndex: Int = 0

    def compare(that: DynamicValue): Int = that match {
      case thatPrimitive: Primitive => value.compare(thatPrimitive.value)
      case _                        => -that.typeIndex
    }
  }

  final case class Record(fields: Vector[(String, DynamicValue)]) extends DynamicValue {
    override def equals(that: Any): Boolean = that match {
      case Record(thatFields) =>
        val len = fields.length
        if (len != thatFields.length) return false
        var idx = 0
        while (idx < len) {
          val kv1 = fields(idx)
          val kv2 = thatFields(idx)
          if (kv1._1 != kv2._1 || kv1._2 != kv2._2) return false
          idx += 1
        }
        true
      case _ => false
    }

    override def hashCode: Int = fields.hashCode

    def typeIndex: Int = 1

    def compare(that: DynamicValue): Int = that match {
      case thatRecord: Record =>
        val xs     = fields
        val ys     = thatRecord.fields
        val xLen   = xs.length
        val yLen   = ys.length
        val minLen = Math.min(xLen, yLen)
        var idx    = 0
        while (idx < minLen) {
          val kv1 = xs(idx)
          val kv2 = ys(idx)
          var cmp = kv1._1.compare(kv2._1)
          if (cmp != 0) return cmp
          cmp = kv1._2.compare(kv2._2)
          if (cmp != 0) return cmp
          idx += 1
        }
        xLen.compareTo(yLen)
      case _ => 1 - that.typeIndex
    }
  }

  final case class Variant(caseName: String, value: DynamicValue) extends DynamicValue {
    override def equals(that: Any): Boolean = that match {
      case Variant(thatCaseName, thatValue) => caseName == thatCaseName && value == thatValue
      case _                                => false
    }

    override def hashCode: Int = 31 * caseName.hashCode + value.hashCode

    def typeIndex: Int = 2

    def compare(that: DynamicValue): Int = that match {
      case thatVariant: Variant =>
        val cmp = caseName.compare(thatVariant.caseName)
        if (cmp != 0) return cmp
        value.compare(thatVariant.value)
      case _ => 2 - that.typeIndex
    }
  }

  final case class Sequence(elements: Vector[DynamicValue]) extends DynamicValue {
    override def equals(that: Any): Boolean = that match {
      case Sequence(thatElements) =>
        val len = elements.length
        if (len != thatElements.length) return false
        var idx = 0
        while (idx < len) {
          if (elements(idx) != thatElements(idx)) return false
          idx += 1
        }
        true
      case _ => false
    }

    override def hashCode: Int = elements.hashCode

    def typeIndex: Int = 3

    def compare(that: DynamicValue): Int = that match {
      case thatSequence: Sequence =>
        val xs     = elements
        val ys     = thatSequence.elements
        val xLen   = xs.length
        val yLen   = ys.length
        val minLen = Math.min(xLen, yLen)
        var idx    = 0
        while (idx < minLen) {
          val cmp = xs(idx).compare(ys(idx))
          if (cmp != 0) return cmp
          idx += 1
        }
        xLen.compareTo(yLen)
      case _ => 3 - that.typeIndex
    }
  }

  final case class Map(entries: Vector[(DynamicValue, DynamicValue)]) extends DynamicValue {
    override def equals(that: Any): Boolean = that match {
      case Map(thatEntries) =>
        val len = entries.length
        if (len != thatEntries.length) return false
        var idx = 0
        while (idx < len) {
          val kv1 = entries(idx)
          val kv2 = thatEntries(idx)
          if (kv1._1 != kv2._1 || kv1._2 != kv2._2) return false
          idx += 1
        }
        true
      case _ => false
    }

    override def hashCode: Int = entries.hashCode

    def typeIndex: Int = 4

    def compare(that: DynamicValue): Int = that match {
      case thatMap: Map =>
        val xs     = entries
        val ys     = thatMap.entries
        val xLen   = xs.length
        val yLen   = ys.length
        val minLen = Math.min(xLen, yLen)
        var idx    = 0
        while (idx < minLen) {
          val kv1 = xs(idx)
          val kv2 = ys(idx)
          var cmp = kv1._1.compare(kv2._1)
          if (cmp != 0) return cmp
          cmp = kv1._2.compare(kv2._2)
          if (cmp != 0) return cmp
          idx += 1
        }
        xLen.compareTo(yLen)
      case _ => 4 - that.typeIndex
    }
  }

  implicit val ordering: Ordering[DynamicValue] = new Ordering[DynamicValue] {
    def compare(x: DynamicValue, y: DynamicValue): Int = x.compare(y)
  }

  /**
   * Compute the difference between two DynamicValues. Returns a DynamicPatch
   * that transforms oldValue into newValue.
   */
  def diff(oldValue: DynamicValue, newValue: DynamicValue): DynamicPatch =
    Differ.diff(oldValue, newValue)

  /**
   * Convert a DynamicValue to EJSON (Extended JSON) format.
   *
   * EJSON is a superset of JSON that handles:
   *   - Non-string map keys
   *   - Tagged variants (using @ metadata)
   *   - Typed primitives (using @ metadata)
   *   - Records (unquoted keys) vs Maps (quoted string keys or unquoted
   *     non-string keys)
   *
   * @param value
   *   The DynamicValue to convert
   * @param indent
   *   Current indentation level
   * @return
   *   EJSON string representation
   */
  private def toEjson(value: DynamicValue, indent: Int): String = {
    val indentStr = "  " * indent

    value match {
      case Primitive(pv) =>
        primitiveToEjson(pv)

      case Record(fields) =>
        if (fields.isEmpty) {
          "{}"
        } else {
          val sb = new StringBuilder
          sb.append("{\n")
          fields.zipWithIndex.foreach { case ((name, value), idx) =>
            sb.append(indentStr).append("  ").append(escapeFieldName(name)).append(": ")
            sb.append(toEjson(value, indent + 1))
            if (idx < fields.length - 1) sb.append(",")
            sb.append("\n")
          }
          sb.append(indentStr).append("}")
          sb.toString
        }

      case Variant(caseName, value) =>
        // Variants use postfix @ metadata: { ... } @ {tag: "CaseName"}
        val valueEjson = toEjson(value, indent)
        s"$valueEjson @ {tag: ${quote(caseName)}}"

      case Sequence(elements) =>
        if (elements.isEmpty) {
          "[]"
        } else if (elements.length == 1 && elements(0).isInstanceOf[Primitive]) {
          // Only inline single-element sequences if the element is a primitive
          "[" + toEjson(elements(0), indent) + "]"
        } else {
          val sb = new StringBuilder
          sb.append("[\n")
          elements.zipWithIndex.foreach { case (elem, idx) =>
            sb.append(indentStr).append("  ")
            sb.append(toEjson(elem, indent + 1))
            if (idx < elements.length - 1) sb.append(",")
            sb.append("\n")
          }
          sb.append(indentStr).append("]")
          sb.toString
        }

      case Map(entries) =>
        if (entries.isEmpty) {
          "{}"
        } else {
          val sb = new StringBuilder
          sb.append("{\n")
          entries.zipWithIndex.foreach { case ((key, value), idx) =>
            sb.append(indentStr).append("  ")
            // For string keys in maps, we quote them. For non-string keys, we don't quote them.
            key match {
              case Primitive(PrimitiveValue.String(str)) =>
                sb.append(quote(str))
              case _ =>
                sb.append(toEjson(key, indent + 1))
            }
            sb.append(": ")
            sb.append(toEjson(value, indent + 1))
            if (idx < entries.length - 1) sb.append(",")
            sb.append("\n")
          }
          sb.append(indentStr).append("}")
          sb.toString
        }
    }
  }

  /**
   * Convert a PrimitiveValue to EJSON format. Most primitives render as their
   * JSON equivalent. Some primitives need @ metadata for type information.
   */
  private def primitiveToEjson(pv: PrimitiveValue): String = pv match {
    case PrimitiveValue.Unit                  => "null"
    case PrimitiveValue.Boolean(value)        => value.toString
    case PrimitiveValue.Byte(value)           => value.toString
    case PrimitiveValue.Short(value)          => value.toString
    case PrimitiveValue.Int(value)            => value.toString
    case PrimitiveValue.Long(value)           => value.toString
    case PrimitiveValue.Float(value)          => value.toString
    case PrimitiveValue.Double(value)         => value.toString
    case PrimitiveValue.Char(value)           => quote(value.toString)
    case PrimitiveValue.String(value)         => quote(value)
    case PrimitiveValue.BigInt(value)         => value.toString
    case PrimitiveValue.BigDecimal(value)     => value.toString
    case PrimitiveValue.DayOfWeek(value)      => quote(value.toString)
    case PrimitiveValue.Month(value)          => quote(value.toString)
    case PrimitiveValue.Instant(value)        => s"${value.toEpochMilli} @ {type: ${quote("instant")}}"
    case PrimitiveValue.LocalDate(value)      => s"${quote(value.toString)} @ {type: ${quote("localDate")}}"
    case PrimitiveValue.LocalDateTime(value)  => quote(value.toString)
    case PrimitiveValue.LocalTime(value)      => quote(value.toString)
    case PrimitiveValue.OffsetDateTime(value) => quote(value.toString)
    case PrimitiveValue.OffsetTime(value)     => quote(value.toString)
    case PrimitiveValue.Year(value)           => value.getValue.toString
    case PrimitiveValue.YearMonth(value)      => quote(value.toString)
    case PrimitiveValue.ZoneOffset(value)     => quote(value.toString)
    case PrimitiveValue.ZonedDateTime(value)  => quote(value.toString)
    case PrimitiveValue.MonthDay(value)       => quote(value.toString)
    case PrimitiveValue.Period(value)         => s"${quote(value.toString)} @ {type: ${quote("period")}}"
    case PrimitiveValue.Duration(value)       => s"${quote(value.toString)} @ {type: ${quote("duration")}}"
    case PrimitiveValue.ZoneId(value)         => quote(value.toString)
    case PrimitiveValue.Currency(value)       => quote(value.getCurrencyCode)
    case PrimitiveValue.UUID(value)           => quote(value.toString)
  }

  /**
   * Escape a field name for EJSON output. Valid Scala identifiers are left
   * as-is; invalid identifiers are wrapped in backticks, with backticks doubled
   * for escaping.
   */
  private def escapeFieldName(name: String): String =
    if (isValidIdentifier(name)) name
    else {
      val escaped = name.replace("`", "``")
      s"`$escaped`"
    }

  /**
   * Check if a string is a valid Scala identifier. A valid identifier:
   *   - Must start with a letter or underscore
   *   - Can contain letters, digits, or underscores
   *   - Cannot be a Scala keyword
   *   - Cannot contain $ (discouraged in user-written code)
   */
  private def isValidIdentifier(s: String): Boolean = {
    if (s.isEmpty) return false
    if (scalaKeywords.contains(s)) return false

    val first = s.charAt(0)
    if (!Character.isLetter(first) && first != '_') return false

    var i = 1
    while (i < s.length) {
      val c = s.charAt(i)
      if (!Character.isLetterOrDigit(c) && c != '_') return false
      i += 1
    }
    true
  }

  /**
   * Scala keywords that cannot be used as identifiers without backticks.
   */
  private val scalaKeywords: Set[String] = Set(
    "abstract",
    "case",
    "catch",
    "class",
    "def",
    "do",
    "else",
    "extends",
    "false",
    "final",
    "finally",
    "for",
    "forSome",
    "if",
    "implicit",
    "import",
    "lazy",
    "macro",
    "match",
    "new",
    "null",
    "object",
    "override",
    "package",
    "private",
    "protected",
    "return",
    "sealed",
    "super",
    "this",
    "throw",
    "trait",
    "true",
    "try",
    "type",
    "val",
    "var",
    "while",
    "with",
    "yield",
    "_",
    ":",
    "=",
    "=>",
    "<-",
    "<:",
    "<%",
    ">:",
    "#",
    "@"
  )

  /**
   * Quote a string for JSON/EJSON output, escaping special characters.
   */
  private def quote(s: String): String = {
    val sb = new StringBuilder
    sb.append('"')
    var i = 0
    while (i < s.length) {
      s.charAt(i) match {
        case '"'  => sb.append("\\\"")
        case '\\' => sb.append("\\\\")
        case '\b' => sb.append("\\b")
        case '\f' => sb.append("\\f")
        case '\n' => sb.append("\\n")
        case '\r' => sb.append("\\r")
        case '\t' => sb.append("\\t")
        case c    =>
          if (c < ' ') {
            sb.append("\\u")
            sb.append(String.format("%04x", Integer.valueOf(c.toInt)))
          } else {
            sb.append(c)
          }
      }
      i += 1
    }
    sb.append('"')
    sb.toString
  }
}
