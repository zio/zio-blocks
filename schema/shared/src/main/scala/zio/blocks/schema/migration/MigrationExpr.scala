package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue}

/**
 * A pure, serializable expression for use in migrations.
 *
 * Unlike `SchemaExpr`, `MigrationExpr` contains no closures or functions,
 * making it fully serializable. It supports:
 *   - Literal values
 *   - Field references (via DynamicOptic)
 *   - Primitive-to-primitive operations (arithmetic, string concat, etc.)
 *
 * Per the issue spec, migrations are constrained to:
 *   - primitive → primitive transformations only
 *   - joins/splits must produce primitives
 *   - no record/enum construction
 */
sealed trait MigrationExpr {

  /**
   * Evaluate this expression against a DynamicValue.
   *
   * @param input
   *   The input value to evaluate against
   * @return
   *   Either an error message or the resulting DynamicValue
   */
  def eval(input: DynamicValue): Either[String, DynamicValue]
}

object MigrationExpr {

  /**
   * A literal value expression - always returns the same value.
   */
  final case class Literal(value: DynamicValue) extends MigrationExpr {
    def eval(input: DynamicValue): Either[String, DynamicValue] = Right(value)
  }

  /**
   * A field reference expression - extracts a value at the given path.
   */
  final case class FieldRef(path: DynamicOptic) extends MigrationExpr {
    def eval(input: DynamicValue): Either[String, DynamicValue] =
      getAtPath(path, input)
  }

  /**
   * String concatenation of two expressions.
   */
  final case class StringConcat(left: MigrationExpr, right: MigrationExpr) extends MigrationExpr {
    def eval(input: DynamicValue): Either[String, DynamicValue] =
      for {
        l    <- left.eval(input)
        r    <- right.eval(input)
        lStr <- extractString(l)
        rStr <- extractString(r)
      } yield DynamicValue.Primitive(PrimitiveValue.String(lStr + rStr))
  }

  /**
   * Arithmetic operation on two numeric expressions.
   */
  final case class Arithmetic(
    left: MigrationExpr,
    right: MigrationExpr,
    op: ArithmeticOp
  ) extends MigrationExpr {
    def eval(input: DynamicValue): Either[String, DynamicValue] =
      for {
        l      <- left.eval(input)
        r      <- right.eval(input)
        result <- applyArithmetic(l, r, op)
      } yield result
  }

  sealed trait ArithmeticOp
  object ArithmeticOp {
    case object Add      extends ArithmeticOp
    case object Subtract extends ArithmeticOp
    case object Multiply extends ArithmeticOp
    case object Divide   extends ArithmeticOp
  }

  /**
   * Type conversion for primitive types.
   */
  final case class Convert(expr: MigrationExpr, targetType: PrimitiveTargetType) extends MigrationExpr {
    def eval(input: DynamicValue): Either[String, DynamicValue] =
      for {
        v      <- expr.eval(input)
        result <- convertPrimitive(v, targetType)
      } yield result
  }

  sealed trait PrimitiveTargetType
  object PrimitiveTargetType {
    case object ToString     extends PrimitiveTargetType
    case object ToInt        extends PrimitiveTargetType
    case object ToLong       extends PrimitiveTargetType
    case object ToDouble     extends PrimitiveTargetType
    case object ToFloat      extends PrimitiveTargetType
    case object ToBoolean    extends PrimitiveTargetType
    case object ToBigInt     extends PrimitiveTargetType
    case object ToBigDecimal extends PrimitiveTargetType
  }

  /**
   * Conditional expression - if/then/else.
   */
  final case class Conditional(
    condition: MigrationExpr,
    ifTrue: MigrationExpr,
    ifFalse: MigrationExpr
  ) extends MigrationExpr {
    def eval(input: DynamicValue): Either[String, DynamicValue] =
      for {
        cond   <- condition.eval(input)
        b      <- extractBoolean(cond)
        result <- if (b) ifTrue.eval(input) else ifFalse.eval(input)
      } yield result
  }

  /**
   * Comparison operation.
   */
  final case class Compare(
    left: MigrationExpr,
    right: MigrationExpr,
    op: CompareOp
  ) extends MigrationExpr {
    def eval(input: DynamicValue): Either[String, DynamicValue] =
      for {
        l      <- left.eval(input)
        r      <- right.eval(input)
        result <- applyCompare(l, r, op)
      } yield DynamicValue.Primitive(PrimitiveValue.Boolean(result))
  }

  sealed trait CompareOp
  object CompareOp {
    case object Eq extends CompareOp
    case object Ne extends CompareOp
    case object Lt extends CompareOp
    case object Le extends CompareOp
    case object Gt extends CompareOp
    case object Ge extends CompareOp
  }

  /**
   * Default value expression - returns the default for a type, or a fallback.
   */
  final case class DefaultValue(fallback: DynamicValue) extends MigrationExpr {
    def eval(input: DynamicValue): Either[String, DynamicValue] = Right(fallback)
  }

  // ==================== Helper Methods ====================

  private def getAtPath(path: DynamicOptic, value: DynamicValue): Either[String, DynamicValue] = {
    val nodes = path.nodes
    if (nodes.isEmpty) Right(value)
    else getAtPathRec(nodes, 0, value)
  }

  private def getAtPathRec(
    nodes: IndexedSeq[DynamicOptic.Node],
    idx: Int,
    value: DynamicValue
  ): Either[String, DynamicValue] =
    if (idx >= nodes.length) Right(value)
    else {
      nodes(idx) match {
        case DynamicOptic.Node.Field(name) =>
          value match {
            case DynamicValue.Record(fields) =>
              fields.find(_._1 == name) match {
                case Some((_, fieldValue)) => getAtPathRec(nodes, idx + 1, fieldValue)
                case None                  => Left(s"Field '$name' not found")
              }
            case _ => Left(s"Expected Record, got ${value.valueType}")
          }
        case _ =>
          Left(s"Unsupported path node for expression evaluation: ${nodes(idx)}")
      }
    }

  private def primitiveToString(pv: PrimitiveValue): String = pv match {
    case PrimitiveValue.String(s)         => s
    case PrimitiveValue.Int(i)            => i.toString
    case PrimitiveValue.Long(l)           => l.toString
    case PrimitiveValue.Short(s)          => s.toString
    case PrimitiveValue.Byte(b)           => b.toString
    case PrimitiveValue.Double(d)         => d.toString
    case PrimitiveValue.Float(f)          => f.toString
    case PrimitiveValue.Boolean(b)        => b.toString
    case PrimitiveValue.Char(c)           => c.toString
    case PrimitiveValue.BigInt(bi)        => bi.toString
    case PrimitiveValue.BigDecimal(bd)    => bd.toString
    case PrimitiveValue.UUID(uuid)        => uuid.toString
    case PrimitiveValue.Unit              => "()"
    case PrimitiveValue.DayOfWeek(d)      => d.toString
    case PrimitiveValue.Duration(d)       => d.toString
    case PrimitiveValue.Instant(i)        => i.toString
    case PrimitiveValue.LocalDate(d)      => d.toString
    case PrimitiveValue.LocalDateTime(d)  => d.toString
    case PrimitiveValue.LocalTime(t)      => t.toString
    case PrimitiveValue.Month(m)          => m.toString
    case PrimitiveValue.MonthDay(m)       => m.toString
    case PrimitiveValue.OffsetDateTime(o) => o.toString
    case PrimitiveValue.OffsetTime(o)     => o.toString
    case PrimitiveValue.Period(p)         => p.toString
    case PrimitiveValue.Year(y)           => y.toString
    case PrimitiveValue.YearMonth(y)      => y.toString
    case PrimitiveValue.ZoneId(z)         => z.toString
    case PrimitiveValue.ZoneOffset(z)     => z.toString
    case PrimitiveValue.ZonedDateTime(z)  => z.toString
    case PrimitiveValue.Currency(c)       => c.toString
  }

  private def extractString(v: DynamicValue): Either[String, String] = v match {
    case DynamicValue.Primitive(pv) => Right(primitiveToString(pv))
    case _                          => Left(s"Cannot convert ${v.valueType} to String")
  }

  private def extractBoolean(v: DynamicValue): Either[String, Boolean] = v match {
    case DynamicValue.Primitive(PrimitiveValue.Boolean(b)) => Right(b)
    case _                                                 => Left(s"Expected Boolean, got ${v.valueType}")
  }

  private def applyArithmetic(
    l: DynamicValue,
    r: DynamicValue,
    op: ArithmeticOp
  ): Either[String, DynamicValue] = (l, r) match {
    case (DynamicValue.Primitive(lp), DynamicValue.Primitive(rp)) =>
      (lp, rp) match {
        case (PrimitiveValue.Int(a), PrimitiveValue.Int(b)) =>
          val result = op match {
            case ArithmeticOp.Add      => a + b
            case ArithmeticOp.Subtract => a - b
            case ArithmeticOp.Multiply => a * b
            case ArithmeticOp.Divide   => if (b != 0) a / b else return Left("Division by zero")
          }
          Right(DynamicValue.Primitive(PrimitiveValue.Int(result)))

        case (PrimitiveValue.Long(a), PrimitiveValue.Long(b)) =>
          val result = op match {
            case ArithmeticOp.Add      => a + b
            case ArithmeticOp.Subtract => a - b
            case ArithmeticOp.Multiply => a * b
            case ArithmeticOp.Divide   => if (b != 0) a / b else return Left("Division by zero")
          }
          Right(DynamicValue.Primitive(PrimitiveValue.Long(result)))

        case (PrimitiveValue.Double(a), PrimitiveValue.Double(b)) =>
          val result = op match {
            case ArithmeticOp.Add      => a + b
            case ArithmeticOp.Subtract => a - b
            case ArithmeticOp.Multiply => a * b
            case ArithmeticOp.Divide   => a / b
          }
          Right(DynamicValue.Primitive(PrimitiveValue.Double(result)))

        case (PrimitiveValue.Float(a), PrimitiveValue.Float(b)) =>
          val result = op match {
            case ArithmeticOp.Add      => a + b
            case ArithmeticOp.Subtract => a - b
            case ArithmeticOp.Multiply => a * b
            case ArithmeticOp.Divide   => a / b
          }
          Right(DynamicValue.Primitive(PrimitiveValue.Float(result)))

        case _ => Left(s"Cannot perform arithmetic on ${lp.getClass.getSimpleName} and ${rp.getClass.getSimpleName}")
      }
    case _ => Left("Arithmetic requires primitive values")
  }

  private def applyCompare(l: DynamicValue, r: DynamicValue, op: CompareOp): Either[String, Boolean] =
    (l, r) match {
      case (DynamicValue.Primitive(lp), DynamicValue.Primitive(rp)) =>
        op match {
          case CompareOp.Eq => Right(lp == rp)
          case CompareOp.Ne => Right(lp != rp)
          case _            =>
            // For ordering comparisons, use DynamicValue's built-in ordering
            val cmp = l.compare(r)
            op match {
              case CompareOp.Lt => Right(cmp < 0)
              case CompareOp.Le => Right(cmp <= 0)
              case CompareOp.Gt => Right(cmp > 0)
              case CompareOp.Ge => Right(cmp >= 0)
              case _            => Right(false) // Already handled Eq/Ne above
            }
        }
      case _ => Left("Comparison requires primitive values")
    }

  private def convertPrimitive(v: DynamicValue, target: PrimitiveTargetType): Either[String, DynamicValue] =
    v match {
      case DynamicValue.Primitive(pv) =>
        target match {
          case PrimitiveTargetType.ToString =>
            val str = primitiveToString(pv)
            Right(DynamicValue.Primitive(PrimitiveValue.String(str)))

          case PrimitiveTargetType.ToInt =>
            convertToInt(pv).map(r => DynamicValue.Primitive(PrimitiveValue.Int(r)))

          case PrimitiveTargetType.ToLong =>
            convertToLong(pv).map(r => DynamicValue.Primitive(PrimitiveValue.Long(r)))

          case PrimitiveTargetType.ToDouble =>
            convertToDouble(pv).map(r => DynamicValue.Primitive(PrimitiveValue.Double(r)))

          case PrimitiveTargetType.ToFloat =>
            convertToFloat(pv).map(r => DynamicValue.Primitive(PrimitiveValue.Float(r)))

          case PrimitiveTargetType.ToBoolean =>
            convertToBoolean(pv).map(r => DynamicValue.Primitive(PrimitiveValue.Boolean(r)))

          case PrimitiveTargetType.ToBigInt =>
            convertToBigInt(pv).map(r => DynamicValue.Primitive(PrimitiveValue.BigInt(r)))

          case PrimitiveTargetType.ToBigDecimal =>
            convertToBigDecimal(pv).map(r => DynamicValue.Primitive(PrimitiveValue.BigDecimal(r)))
        }
      case _ => Left("Type conversion requires primitive value")
    }

  private def convertToInt(pv: PrimitiveValue): Either[String, Int] = pv match {
    case PrimitiveValue.Int(i)         => Right(i)
    case PrimitiveValue.Long(l)        => Right(l.toInt)
    case PrimitiveValue.Short(s)       => Right(s.toInt)
    case PrimitiveValue.Byte(b)        => Right(b.toInt)
    case PrimitiveValue.Double(d)      => Right(d.toInt)
    case PrimitiveValue.Float(f)       => Right(f.toInt)
    case PrimitiveValue.String(s)      => s.toIntOption.toRight(s"Cannot convert '$s' to Int")
    case PrimitiveValue.BigInt(bi)     => Right(bi.toInt)
    case PrimitiveValue.BigDecimal(bd) => Right(bd.toInt)
    case _                             => Left(s"Cannot convert ${pv.getClass.getSimpleName} to Int")
  }

  private def convertToLong(pv: PrimitiveValue): Either[String, Long] = pv match {
    case PrimitiveValue.Int(i)         => Right(i.toLong)
    case PrimitiveValue.Long(l)        => Right(l)
    case PrimitiveValue.Short(s)       => Right(s.toLong)
    case PrimitiveValue.Byte(b)        => Right(b.toLong)
    case PrimitiveValue.Double(d)      => Right(d.toLong)
    case PrimitiveValue.Float(f)       => Right(f.toLong)
    case PrimitiveValue.String(s)      => s.toLongOption.toRight(s"Cannot convert '$s' to Long")
    case PrimitiveValue.BigInt(bi)     => Right(bi.toLong)
    case PrimitiveValue.BigDecimal(bd) => Right(bd.toLong)
    case _                             => Left(s"Cannot convert ${pv.getClass.getSimpleName} to Long")
  }

  private def convertToDouble(pv: PrimitiveValue): Either[String, Double] = pv match {
    case PrimitiveValue.Int(i)         => Right(i.toDouble)
    case PrimitiveValue.Long(l)        => Right(l.toDouble)
    case PrimitiveValue.Short(s)       => Right(s.toDouble)
    case PrimitiveValue.Byte(b)        => Right(b.toDouble)
    case PrimitiveValue.Double(d)      => Right(d)
    case PrimitiveValue.Float(f)       => Right(f.toDouble)
    case PrimitiveValue.String(s)      => s.toDoubleOption.toRight(s"Cannot convert '$s' to Double")
    case PrimitiveValue.BigInt(bi)     => Right(bi.toDouble)
    case PrimitiveValue.BigDecimal(bd) => Right(bd.toDouble)
    case _                             => Left(s"Cannot convert ${pv.getClass.getSimpleName} to Double")
  }

  private def convertToFloat(pv: PrimitiveValue): Either[String, Float] = pv match {
    case PrimitiveValue.Int(i)         => Right(i.toFloat)
    case PrimitiveValue.Long(l)        => Right(l.toFloat)
    case PrimitiveValue.Short(s)       => Right(s.toFloat)
    case PrimitiveValue.Byte(b)        => Right(b.toFloat)
    case PrimitiveValue.Double(d)      => Right(d.toFloat)
    case PrimitiveValue.Float(f)       => Right(f)
    case PrimitiveValue.String(s)      => s.toFloatOption.toRight(s"Cannot convert '$s' to Float")
    case PrimitiveValue.BigInt(bi)     => Right(bi.toFloat)
    case PrimitiveValue.BigDecimal(bd) => Right(bd.toFloat)
    case _                             => Left(s"Cannot convert ${pv.getClass.getSimpleName} to Float")
  }

  private def convertToBoolean(pv: PrimitiveValue): Either[String, Boolean] = pv match {
    case PrimitiveValue.Boolean(b) => Right(b)
    case PrimitiveValue.String(s)  =>
      s.toLowerCase match {
        case "true" | "1" | "yes" => Right(true)
        case "false" | "0" | "no" => Right(false)
        case _                    => Left(s"Cannot convert '$s' to Boolean")
      }
    case PrimitiveValue.Int(i) => Right(i != 0)
    case _                     => Left(s"Cannot convert ${pv.getClass.getSimpleName} to Boolean")
  }

  private def convertToBigInt(pv: PrimitiveValue): Either[String, BigInt] = pv match {
    case PrimitiveValue.Int(i)     => Right(BigInt(i))
    case PrimitiveValue.Long(l)    => Right(BigInt(l))
    case PrimitiveValue.Short(s)   => Right(BigInt(s))
    case PrimitiveValue.Byte(b)    => Right(BigInt(b))
    case PrimitiveValue.BigInt(bi) => Right(bi)
    case PrimitiveValue.String(s)  => scala.util.Try(BigInt(s)).toOption.toRight(s"Cannot convert '$s' to BigInt")
    case _                         => Left(s"Cannot convert ${pv.getClass.getSimpleName} to BigInt")
  }

  private def convertToBigDecimal(pv: PrimitiveValue): Either[String, BigDecimal] = pv match {
    case PrimitiveValue.Int(i)         => Right(BigDecimal(i))
    case PrimitiveValue.Long(l)        => Right(BigDecimal(l))
    case PrimitiveValue.Short(s)       => Right(BigDecimal(s))
    case PrimitiveValue.Byte(b)        => Right(BigDecimal(b))
    case PrimitiveValue.Double(d)      => Right(BigDecimal(d))
    case PrimitiveValue.Float(f)       => Right(BigDecimal(f.toDouble))
    case PrimitiveValue.BigInt(bi)     => Right(BigDecimal(bi))
    case PrimitiveValue.BigDecimal(bd) => Right(bd)
    case PrimitiveValue.String(s)      =>
      scala.util.Try(BigDecimal(s)).toOption.toRight(s"Cannot convert '$s' to BigDecimal")
    case _ => Left(s"Cannot convert ${pv.getClass.getSimpleName} to BigDecimal")
  }

  // ==================== DSL for building expressions ====================

  /** Create a literal expression from a value. */
  def literal[A](value: A)(implicit schema: zio.blocks.schema.Schema[A]): MigrationExpr =
    Literal(schema.toDynamicValue(value))

  /** Create a field reference expression. */
  def field(path: DynamicOptic): MigrationExpr =
    FieldRef(path)

  /** Create a default value expression. */
  def default[A](value: A)(implicit schema: zio.blocks.schema.Schema[A]): MigrationExpr =
    DefaultValue(schema.toDynamicValue(value))

  // Implicit class for DSL operations
  implicit class MigrationExprOps(private val self: MigrationExpr) extends AnyVal {
    def +(other: MigrationExpr): MigrationExpr  = Arithmetic(self, other, ArithmeticOp.Add)
    def -(other: MigrationExpr): MigrationExpr  = Arithmetic(self, other, ArithmeticOp.Subtract)
    def *(other: MigrationExpr): MigrationExpr  = Arithmetic(self, other, ArithmeticOp.Multiply)
    def /(other: MigrationExpr): MigrationExpr  = Arithmetic(self, other, ArithmeticOp.Divide)
    def ++(other: MigrationExpr): MigrationExpr = StringConcat(self, other)

    def ===(other: MigrationExpr): MigrationExpr = Compare(self, other, CompareOp.Eq)
    def =!=(other: MigrationExpr): MigrationExpr = Compare(self, other, CompareOp.Ne)
    def <(other: MigrationExpr): MigrationExpr   = Compare(self, other, CompareOp.Lt)
    def <=(other: MigrationExpr): MigrationExpr  = Compare(self, other, CompareOp.Le)
    def >(other: MigrationExpr): MigrationExpr   = Compare(self, other, CompareOp.Gt)
    def >=(other: MigrationExpr): MigrationExpr  = Compare(self, other, CompareOp.Ge)

    def toInt: MigrationExpr        = Convert(self, PrimitiveTargetType.ToInt)
    def toLong: MigrationExpr       = Convert(self, PrimitiveTargetType.ToLong)
    def toDouble: MigrationExpr     = Convert(self, PrimitiveTargetType.ToDouble)
    def toFloat: MigrationExpr      = Convert(self, PrimitiveTargetType.ToFloat)
    def toBoolean: MigrationExpr    = Convert(self, PrimitiveTargetType.ToBoolean)
    def toBigInt: MigrationExpr     = Convert(self, PrimitiveTargetType.ToBigInt)
    def toBigDecimal: MigrationExpr = Convert(self, PrimitiveTargetType.ToBigDecimal)
    def asString: MigrationExpr     = Convert(self, PrimitiveTargetType.ToString)
  }
}
