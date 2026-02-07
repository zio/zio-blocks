package zio.blocks.schema

/**
 * A typed wrapper around DynamicSchemaExpr that provides compile-time type
 * safety.
 *
 * @tparam A
 *   The input type
 * @tparam B
 *   The output type
 * @param dynamic
 *   The underlying serializable expression
 * @param inputSchema
 *   Schema for converting A to DynamicValue
 * @param outputSchema
 *   Schema for converting DynamicValue back to B
 */
final case class SchemaExpr[A, B](
  dynamic: DynamicSchemaExpr,
  inputSchema: Schema[A],
  outputSchema: Schema[B]
) {

  /**
   * Evaluate the expression on a typed input.
   */
  def eval[B1 >: B](input: A)(implicit schema: Schema[B1]): Either[OpticCheck, Seq[B1]] = {
    val dynamicInput = inputSchema.toDynamicValue(input)
    dynamic.eval(dynamicInput) match {
      case Right(results) =>
        val converted = results.map(dv => schema.fromDynamicValue(dv))
        val errors    = converted.collect { case Left(e) => e }
        if (errors.nonEmpty) {
          val errList = errors.map(e => OpticCheck.DynamicConversionError(e.message))
          Left(new OpticCheck(new ::(errList.head, errList.tail.toList)))
        } else {
          Right(converted.collect { case Right(v) => v })
        }
      case Left(error) =>
        Left(new OpticCheck(new ::(OpticCheck.DynamicConversionError(error.message), Nil)))
    }
  }

  /**
   * Evaluate the expression and return DynamicValue results.
   */
  def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] = {
    val dynamicInput = inputSchema.toDynamicValue(input)
    dynamic.eval(dynamicInput).left.map(e => new OpticCheck(new ::(OpticCheck.DynamicConversionError(e.message), Nil)))
  }

  /**
   * Get the underlying dynamic expression.
   */
  def toDynamic: DynamicSchemaExpr = dynamic

  // Logical combinators
  def &&[B2](
    that: SchemaExpr[A, B2]
  )(implicit ev: B <:< Boolean, ev2: B2 =:= Boolean): SchemaExpr[A, Boolean] =
    SchemaExpr(
      DynamicSchemaExpr.Logical(this.dynamic, that.dynamic, DynamicSchemaExpr.LogicalOperator.And),
      inputSchema,
      Schema[Boolean]
    )

  def ||[B2](
    that: SchemaExpr[A, B2]
  )(implicit ev: B <:< Boolean, ev2: B2 =:= Boolean): SchemaExpr[A, Boolean] =
    SchemaExpr(
      DynamicSchemaExpr.Logical(this.dynamic, that.dynamic, DynamicSchemaExpr.LogicalOperator.Or),
      inputSchema,
      Schema[Boolean]
    )
}

object SchemaExpr {

  /**
   * Create a Literal expression from the schema's default value. Gets the
   * default value from the schema and wraps it in a Literal. Returns None if
   * the schema has no default value.
   */
  def schemaDefault[A](implicit schema: Schema[A]): Option[SchemaExpr[Any, A]] =
    schema.getDefaultValue.map { defaultValue =>
      val dynValue = schema.toDynamicValue(defaultValue)
      SchemaExpr(
        DynamicSchemaExpr.Literal(dynValue),
        schema.asInstanceOf[Schema[Any]],
        schema
      )
    }

  def literal[S, A](value: A)(implicit schema: Schema[A]): SchemaExpr[S, A] =
    SchemaExpr(
      DynamicSchemaExpr.Literal(schema.toDynamicValue(value)),
      Schema[Unit].transform[S](_ => null.asInstanceOf[S], _ => ()),
      schema
    )

  def optic[S, A](path: DynamicOptic, sourceSchema: Schema[S]): SchemaExpr[S, A] =
    SchemaExpr(
      DynamicSchemaExpr.Select(path),
      sourceSchema,
      Schema[Unit].asInstanceOf[Schema[A]]
    )

  def logical[S](
    left: SchemaExpr[S, Boolean],
    right: SchemaExpr[S, Boolean],
    operator: LogicalOperator
  ): SchemaExpr[S, Boolean] = {
    val dynOp = operator match {
      case LogicalOperator.And => DynamicSchemaExpr.LogicalOperator.And
      case LogicalOperator.Or  => DynamicSchemaExpr.LogicalOperator.Or
    }
    SchemaExpr(
      DynamicSchemaExpr.Logical(left.dynamic, right.dynamic, dynOp),
      left.inputSchema,
      Schema[Boolean]
    )
  }

  def relational[S, A](
    left: SchemaExpr[S, A],
    right: SchemaExpr[S, A],
    operator: RelationalOperator
  ): SchemaExpr[S, Boolean] = {
    val dynOp = operator match {
      case RelationalOperator.LessThan           => DynamicSchemaExpr.RelationalOperator.LessThan
      case RelationalOperator.LessThanOrEqual    => DynamicSchemaExpr.RelationalOperator.LessThanOrEqual
      case RelationalOperator.GreaterThan        => DynamicSchemaExpr.RelationalOperator.GreaterThan
      case RelationalOperator.GreaterThanOrEqual => DynamicSchemaExpr.RelationalOperator.GreaterThanOrEqual
      case RelationalOperator.Equal              => DynamicSchemaExpr.RelationalOperator.Equal
      case RelationalOperator.NotEqual           => DynamicSchemaExpr.RelationalOperator.NotEqual
    }
    SchemaExpr(
      DynamicSchemaExpr.Relational(left.dynamic, right.dynamic, dynOp),
      left.inputSchema,
      Schema[Boolean]
    )
  }

  def not[S](expr: SchemaExpr[S, Boolean]): SchemaExpr[S, Boolean] =
    SchemaExpr(
      DynamicSchemaExpr.Not(expr.dynamic),
      expr.inputSchema,
      Schema[Boolean]
    )

  def arithmetic[S, A](
    left: SchemaExpr[S, A],
    right: SchemaExpr[S, A],
    operator: ArithmeticOperator,
    numericType: NumericPrimitiveType[A]
  ): SchemaExpr[S, A] = {
    val dynOp = operator match {
      case ArithmeticOperator.Add      => DynamicSchemaExpr.ArithmeticOperator.Add
      case ArithmeticOperator.Subtract => DynamicSchemaExpr.ArithmeticOperator.Subtract
      case ArithmeticOperator.Multiply => DynamicSchemaExpr.ArithmeticOperator.Multiply
      case ArithmeticOperator.Divide   => DynamicSchemaExpr.ArithmeticOperator.Divide
      case ArithmeticOperator.Pow      => DynamicSchemaExpr.ArithmeticOperator.Pow
      case ArithmeticOperator.Modulo   => DynamicSchemaExpr.ArithmeticOperator.Modulo
    }
    SchemaExpr(
      DynamicSchemaExpr.Arithmetic(left.dynamic, right.dynamic, dynOp, numericType.toTag),
      left.inputSchema,
      numericType.schema
    )
  }

  def bitwise[S, A](
    left: SchemaExpr[S, A],
    right: SchemaExpr[S, A],
    operator: BitwiseOperator
  ): SchemaExpr[S, A] = {
    val dynOp = operator match {
      case BitwiseOperator.And                => DynamicSchemaExpr.BitwiseOperator.And
      case BitwiseOperator.Or                 => DynamicSchemaExpr.BitwiseOperator.Or
      case BitwiseOperator.Xor                => DynamicSchemaExpr.BitwiseOperator.Xor
      case BitwiseOperator.LeftShift          => DynamicSchemaExpr.BitwiseOperator.LeftShift
      case BitwiseOperator.RightShift         => DynamicSchemaExpr.BitwiseOperator.RightShift
      case BitwiseOperator.UnsignedRightShift => DynamicSchemaExpr.BitwiseOperator.UnsignedRightShift
    }
    SchemaExpr(
      DynamicSchemaExpr.Bitwise(left.dynamic, right.dynamic, dynOp),
      left.inputSchema,
      left.outputSchema
    )
  }

  def bitwiseNot[S, A](expr: SchemaExpr[S, A]): SchemaExpr[S, A] =
    SchemaExpr(
      DynamicSchemaExpr.BitwiseNot(expr.dynamic),
      expr.inputSchema,
      expr.outputSchema
    )

  def stringConcat[S](left: SchemaExpr[S, String], right: SchemaExpr[S, String]): SchemaExpr[S, String] =
    SchemaExpr(
      DynamicSchemaExpr.StringConcat(left.dynamic, right.dynamic),
      left.inputSchema,
      Schema[String]
    )

  def stringRegexMatch[S](regex: SchemaExpr[S, String], string: SchemaExpr[S, String]): SchemaExpr[S, Boolean] =
    SchemaExpr(
      DynamicSchemaExpr.StringRegexMatch(regex.dynamic, string.dynamic),
      regex.inputSchema,
      Schema[Boolean]
    )

  def stringLength[S](string: SchemaExpr[S, String]): SchemaExpr[S, Int] =
    SchemaExpr(
      DynamicSchemaExpr.StringLength(string.dynamic),
      string.inputSchema,
      Schema[Int]
    )

  def stringSubstring[S](
    string: SchemaExpr[S, String],
    start: SchemaExpr[S, Int],
    end: SchemaExpr[S, Int]
  ): SchemaExpr[S, String] =
    SchemaExpr(
      DynamicSchemaExpr.StringSubstring(string.dynamic, start.dynamic, end.dynamic),
      string.inputSchema,
      Schema[String]
    )

  def stringTrim[S](string: SchemaExpr[S, String]): SchemaExpr[S, String] =
    SchemaExpr(
      DynamicSchemaExpr.StringTrim(string.dynamic),
      string.inputSchema,
      Schema[String]
    )

  def stringToUpperCase[S](string: SchemaExpr[S, String]): SchemaExpr[S, String] =
    SchemaExpr(
      DynamicSchemaExpr.StringToUpperCase(string.dynamic),
      string.inputSchema,
      Schema[String]
    )

  def stringToLowerCase[S](string: SchemaExpr[S, String]): SchemaExpr[S, String] =
    SchemaExpr(
      DynamicSchemaExpr.StringToLowerCase(string.dynamic),
      string.inputSchema,
      Schema[String]
    )

  def stringReplace[S](
    string: SchemaExpr[S, String],
    target: SchemaExpr[S, String],
    replacement: SchemaExpr[S, String]
  ): SchemaExpr[S, String] =
    SchemaExpr(
      DynamicSchemaExpr.StringReplace(string.dynamic, target.dynamic, replacement.dynamic),
      string.inputSchema,
      Schema[String]
    )

  def stringStartsWith[S](string: SchemaExpr[S, String], prefix: SchemaExpr[S, String]): SchemaExpr[S, Boolean] =
    SchemaExpr(
      DynamicSchemaExpr.StringStartsWith(string.dynamic, prefix.dynamic),
      string.inputSchema,
      Schema[Boolean]
    )

  def stringEndsWith[S](string: SchemaExpr[S, String], suffix: SchemaExpr[S, String]): SchemaExpr[S, Boolean] =
    SchemaExpr(
      DynamicSchemaExpr.StringEndsWith(string.dynamic, suffix.dynamic),
      string.inputSchema,
      Schema[Boolean]
    )

  def stringContains[S](string: SchemaExpr[S, String], substring: SchemaExpr[S, String]): SchemaExpr[S, Boolean] =
    SchemaExpr(
      DynamicSchemaExpr.StringContains(string.dynamic, substring.dynamic),
      string.inputSchema,
      Schema[Boolean]
    )

  def stringIndexOf[S](string: SchemaExpr[S, String], substring: SchemaExpr[S, String]): SchemaExpr[S, Int] =
    SchemaExpr(
      DynamicSchemaExpr.StringIndexOf(string.dynamic, substring.dynamic),
      string.inputSchema,
      Schema[Int]
    )

  sealed trait LogicalOperator
  object LogicalOperator {
    case object And extends LogicalOperator
    case object Or  extends LogicalOperator
  }

  sealed trait RelationalOperator
  object RelationalOperator {
    case object LessThan           extends RelationalOperator
    case object GreaterThan        extends RelationalOperator
    case object LessThanOrEqual    extends RelationalOperator
    case object GreaterThanOrEqual extends RelationalOperator
    case object Equal              extends RelationalOperator
    case object NotEqual           extends RelationalOperator
  }

  sealed trait ArithmeticOperator
  object ArithmeticOperator {
    case object Add      extends ArithmeticOperator
    case object Subtract extends ArithmeticOperator
    case object Multiply extends ArithmeticOperator
    case object Divide   extends ArithmeticOperator
    case object Pow      extends ArithmeticOperator
    case object Modulo   extends ArithmeticOperator
  }

  sealed trait BitwiseOperator
  object BitwiseOperator {
    case object And                extends BitwiseOperator
    case object Or                 extends BitwiseOperator
    case object Xor                extends BitwiseOperator
    case object LeftShift          extends BitwiseOperator
    case object RightShift         extends BitwiseOperator
    case object UnsignedRightShift extends BitwiseOperator
  }

  def PrimitiveConversion[S](conversionType: ConversionType): ConversionType = conversionType

  sealed trait ConversionType {
    def convert(value: DynamicValue): Either[Predef.String, DynamicValue]
  }

  object ConversionType {
    // Numeric widening conversions (lossless)
    case object ByteToShort extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Byte(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Short(v.toShort)))
        case _ => Left(s"Expected Byte, got $value")
      }
    }

    case object ByteToInt extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Byte(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Int(v.toInt)))
        case _ => Left(s"Expected Byte, got $value")
      }
    }

    case object ByteToLong extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Byte(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Long(v.toLong)))
        case _ => Left(s"Expected Byte, got $value")
      }
    }

    case object ShortToInt extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Short(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Int(v.toInt)))
        case _ => Left(s"Expected Short, got $value")
      }
    }

    case object ShortToLong extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Short(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Long(v.toLong)))
        case _ => Left(s"Expected Short, got $value")
      }
    }

    case object IntToLong extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Long(v.toLong)))
        case _ => Left(s"Expected Int, got $value")
      }
    }

    case object FloatToDouble extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Float(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Double(v.toDouble)))
        case _ => Left(s"Expected Float, got $value")
      }
    }

    // Numeric narrowing conversions (potentially lossy)
    case object ShortToByte extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Short(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Byte(v.toByte)))
        case _ => Left(s"Expected Short, got $value")
      }
    }

    case object IntToByte extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Byte(v.toByte)))
        case _ => Left(s"Expected Int, got $value")
      }
    }

    case object IntToShort extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Short(v.toShort)))
        case _ => Left(s"Expected Int, got $value")
      }
    }

    case object LongToByte extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Long(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Byte(v.toByte)))
        case _ => Left(s"Expected Long, got $value")
      }
    }

    case object LongToShort extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Long(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Short(v.toShort)))
        case _ => Left(s"Expected Long, got $value")
      }
    }

    case object LongToInt extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Long(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Int(v.toInt)))
        case _ => Left(s"Expected Long, got $value")
      }
    }

    case object DoubleToFloat extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Double(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Float(v.toFloat)))
        case _ => Left(s"Expected Double, got $value")
      }
    }

    // Integer to floating point
    case object IntToFloat extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Float(v.toFloat)))
        case _ => Left(s"Expected Int, got $value")
      }
    }

    case object IntToDouble extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Double(v.toDouble)))
        case _ => Left(s"Expected Int, got $value")
      }
    }

    case object LongToFloat extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Long(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Float(v.toFloat)))
        case _ => Left(s"Expected Long, got $value")
      }
    }

    case object LongToDouble extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Long(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Double(v.toDouble)))
        case _ => Left(s"Expected Long, got $value")
      }
    }

    // String conversions
    case object IntToString extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
        case _ => Left(s"Expected Int, got $value")
      }
    }

    case object LongToString extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Long(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
        case _ => Left(s"Expected Long, got $value")
      }
    }

    case object DoubleToString extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Double(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
        case _ => Left(s"Expected Double, got $value")
      }
    }

    case object BooleanToString extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Boolean(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
        case _ => Left(s"Expected Boolean, got $value")
      }
    }

    // String parsing (may fail)
    case object StringToInt extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.String(v)) =>
          try Right(DynamicValue.Primitive(PrimitiveValue.Int(v.toInt)))
          catch { case _: NumberFormatException => Left(s"Cannot parse '$v' as Int") }
        case _ => Left(s"Expected String, got $value")
      }
    }

    case object StringToLong extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.String(v)) =>
          try Right(DynamicValue.Primitive(PrimitiveValue.Long(v.toLong)))
          catch { case _: NumberFormatException => Left(s"Cannot parse '$v' as Long") }
        case _ => Left(s"Expected String, got $value")
      }
    }

    case object StringToDouble extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.String(v)) =>
          try Right(DynamicValue.Primitive(PrimitiveValue.Double(v.toDouble)))
          catch { case _: NumberFormatException => Left(s"Cannot parse '$v' as Double") }
        case _ => Left(s"Expected String, got $value")
      }
    }

    case object StringToBoolean extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.String(v: Predef.String)) =>
          v.toLowerCase match {
            case "true"  => Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
            case "false" => Right(DynamicValue.Primitive(PrimitiveValue.Boolean(false)))
            case _       => Left(s"Cannot parse '$v' as Boolean")
          }
        case _ => Left(s"Expected String, got $value")
      }
    }

    // Char conversions
    case object CharToInt extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Char(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Int(v.toInt)))
        case _ => Left(s"Expected Char, got $value")
      }
    }

    case object IntToChar extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Char(v.toChar)))
        case _ => Left(s"Expected Int, got $value")
      }
    }

    case object CharToString extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Char(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
        case _ => Left(s"Expected Char, got $value")
      }
    }

    // Byte/Short to Float/Double
    case object ByteToFloat extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Byte(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Float(v.toFloat)))
        case _ => Left(s"Expected Byte, got $value")
      }
    }

    case object ByteToDouble extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Byte(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Double(v.toDouble)))
        case _ => Left(s"Expected Byte, got $value")
      }
    }

    case object ShortToFloat extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Short(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Float(v.toFloat)))
        case _ => Left(s"Expected Short, got $value")
      }
    }

    case object ShortToDouble extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Short(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Double(v.toDouble)))
        case _ => Left(s"Expected Short, got $value")
      }
    }

    // Float/Double to Int/Long (truncation)
    case object FloatToInt extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Float(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Int(v.toInt)))
        case _ => Left(s"Expected Float, got $value")
      }
    }

    case object FloatToLong extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Float(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Long(v.toLong)))
        case _ => Left(s"Expected Float, got $value")
      }
    }

    case object DoubleToInt extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Double(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Int(v.toInt)))
        case _ => Left(s"Expected Double, got $value")
      }
    }

    case object DoubleToLong extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Double(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Long(v.toLong)))
        case _ => Left(s"Expected Double, got $value")
      }
    }

    // More String conversions
    case object FloatToString extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Float(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
        case _ => Left(s"Expected Float, got $value")
      }
    }

    case object ShortToString extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Short(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
        case _ => Left(s"Expected Short, got $value")
      }
    }

    case object ByteToString extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Byte(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
        case _ => Left(s"Expected Byte, got $value")
      }
    }

    case object StringToFloat extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.String(v)) =>
          try Right(DynamicValue.Primitive(PrimitiveValue.Float(v.toFloat)))
          catch { case _: NumberFormatException => Left(s"Cannot parse '$v' as Float") }
        case _ => Left(s"Expected String, got $value")
      }
    }

    case object StringToShort extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.String(v)) =>
          try Right(DynamicValue.Primitive(PrimitiveValue.Short(v.toShort)))
          catch { case _: NumberFormatException => Left(s"Cannot parse '$v' as Short") }
        case _ => Left(s"Expected String, got $value")
      }
    }

    case object StringToByte extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.String(v)) =>
          try Right(DynamicValue.Primitive(PrimitiveValue.Byte(v.toByte)))
          catch { case _: NumberFormatException => Left(s"Cannot parse '$v' as Byte") }
        case _ => Left(s"Expected String, got $value")
      }
    }
  }
}
