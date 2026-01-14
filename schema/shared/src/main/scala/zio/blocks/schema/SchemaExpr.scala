package zio.blocks.schema

import scala.annotation.unchecked.uncheckedVariance

/**
 * A {{SchemaExpr}} is an expression on the value of a type fully described by a
 * {{Schema}}.
 *
 * {{SchemaExpr}} are used for persistence DSLs, implemented in third-party
 * libraries, as well as for validation, implemented in this library. In
 * addition, {{SchemaExpr}} could be used for data migration.
 */
sealed trait SchemaExpr[A, +B] { self =>

  /**
   * Evaluate the expression on the input value.
   *
   * @param input
   *   the input value
   *
   * @return
   *   the result of the expression
   */
  def eval(input: A)(implicit schema: Schema[B @uncheckedVariance]): Either[OpticCheck, Seq[B]]

  /**
   * Evaluate the expression on the input value.
   *
   * @param input
   *   the input value
   *
   * @return
   *   the result of the expression, converted to {{DynamicValue}} values.
   */
  def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]]

  final def &&[B2](
    that: SchemaExpr[A, B2]
  )(implicit ev: B <:< Boolean, ev2: B2 =:= Boolean): SchemaExpr[A, Boolean] =
    SchemaExpr.Logical(
      self.asEquivalent[Boolean],
      that.asEquivalent[Boolean],
      SchemaExpr.LogicalOperator.And
    )

  final def ||[B2](
    that: SchemaExpr[A, B2]
  )(implicit ev: B <:< Boolean, ev2: B2 =:= Boolean): SchemaExpr[A, Boolean] =
    SchemaExpr.Logical(
      self.asEquivalent[Boolean],
      that.asEquivalent[Boolean],
      SchemaExpr.LogicalOperator.Or
    )

  private final def asEquivalent[B2](implicit ev: B <:< B2): SchemaExpr[A, B2] = {
    val _ = ev // suppress unused warning
    self.asInstanceOf[SchemaExpr[A, B2]]
  }
}

object SchemaExpr {

  /**
   * Create a Literal expression from the schema's default value.
   * Gets the default value from the schema and wraps it in a Literal.
   * Returns None if the schema has no default value.
   */
  def schemaDefault[A](implicit schema: Schema[A]): Option[SchemaExpr[Any, A]] =
    schema.getDefaultValue.map { defaultValue =>
      Literal[Any, A](schema.toDynamicValue(defaultValue))
    }

  /**
   * Primitive type conversion expression.
   * Converts a primitive value from one type to another based on the ConversionType.
   */
  final case class PrimitiveConversion[S](conversionType: ConversionType) extends SchemaExpr[S, Any] {
    def eval(input: S)(implicit schema: Schema[Any]): Either[OpticCheck, Seq[Any]] = {
      // Input is ignored - conversion is applied to a DynamicValue at the migration level
      throw new UnsupportedOperationException("PrimitiveConversion.eval requires DynamicValue context")
    }

    def evalDynamic(input: S): Either[OpticCheck, Seq[DynamicValue]] = {
      throw new UnsupportedOperationException("PrimitiveConversion.evalDynamic requires DynamicValue context")
    }

    /**
     * Convert a DynamicValue using this conversion type.
     */
    def convert(value: DynamicValue): Either[String, DynamicValue] = conversionType.convert(value)
  }

  /**
   * Sum type representing all supported primitive-to-primitive conversions.
   */
  sealed trait ConversionType {
    def convert(value: DynamicValue): Either[String, DynamicValue]
  }

  object ConversionType {
    // Numeric widening conversions (lossless)
    case object ByteToShort extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Byte(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Short(v.toShort)))
        case _ => Left(s"Expected Byte, got $value")
      }
    }

    case object ByteToInt extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Byte(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Int(v.toInt)))
        case _ => Left(s"Expected Byte, got $value")
      }
    }

    case object ByteToLong extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Byte(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Long(v.toLong)))
        case _ => Left(s"Expected Byte, got $value")
      }
    }

    case object ShortToInt extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Short(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Int(v.toInt)))
        case _ => Left(s"Expected Short, got $value")
      }
    }

    case object ShortToLong extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Short(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Long(v.toLong)))
        case _ => Left(s"Expected Short, got $value")
      }
    }

    case object IntToLong extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Long(v.toLong)))
        case _ => Left(s"Expected Int, got $value")
      }
    }

    case object FloatToDouble extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Float(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Double(v.toDouble)))
        case _ => Left(s"Expected Float, got $value")
      }
    }

    // Numeric narrowing conversions (potentially lossy)
    case object ShortToByte extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Short(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Byte(v.toByte)))
        case _ => Left(s"Expected Short, got $value")
      }
    }

    case object IntToByte extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Byte(v.toByte)))
        case _ => Left(s"Expected Int, got $value")
      }
    }

    case object IntToShort extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Short(v.toShort)))
        case _ => Left(s"Expected Int, got $value")
      }
    }

    case object LongToByte extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Long(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Byte(v.toByte)))
        case _ => Left(s"Expected Long, got $value")
      }
    }

    case object LongToShort extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Long(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Short(v.toShort)))
        case _ => Left(s"Expected Long, got $value")
      }
    }

    case object LongToInt extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Long(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Int(v.toInt)))
        case _ => Left(s"Expected Long, got $value")
      }
    }

    case object DoubleToFloat extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Double(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Float(v.toFloat)))
        case _ => Left(s"Expected Double, got $value")
      }
    }

    // Integer to floating point
    case object IntToFloat extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Float(v.toFloat)))
        case _ => Left(s"Expected Int, got $value")
      }
    }

    case object IntToDouble extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Double(v.toDouble)))
        case _ => Left(s"Expected Int, got $value")
      }
    }

    case object LongToFloat extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Long(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Float(v.toFloat)))
        case _ => Left(s"Expected Long, got $value")
      }
    }

    case object LongToDouble extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Long(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Double(v.toDouble)))
        case _ => Left(s"Expected Long, got $value")
      }
    }

    // String conversions
    case object IntToString extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
        case _ => Left(s"Expected Int, got $value")
      }
    }

    case object LongToString extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Long(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
        case _ => Left(s"Expected Long, got $value")
      }
    }

    case object DoubleToString extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Double(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
        case _ => Left(s"Expected Double, got $value")
      }
    }

    case object BooleanToString extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Boolean(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
        case _ => Left(s"Expected Boolean, got $value")
      }
    }

    // String parsing (may fail)
    case object StringToInt extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.String(v)) =>
          try Right(DynamicValue.Primitive(PrimitiveValue.Int(v.toInt)))
          catch { case _: NumberFormatException => Left(s"Cannot parse '$v' as Int") }
        case _ => Left(s"Expected String, got $value")
      }
    }

    case object StringToLong extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.String(v)) =>
          try Right(DynamicValue.Primitive(PrimitiveValue.Long(v.toLong)))
          catch { case _: NumberFormatException => Left(s"Cannot parse '$v' as Long") }
        case _ => Left(s"Expected String, got $value")
      }
    }

    case object StringToDouble extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.String(v)) =>
          try Right(DynamicValue.Primitive(PrimitiveValue.Double(v.toDouble)))
          catch { case _: NumberFormatException => Left(s"Cannot parse '$v' as Double") }
        case _ => Left(s"Expected String, got $value")
      }
    }

    case object StringToBoolean extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.String(v)) =>
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
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Char(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Int(v.toInt)))
        case _ => Left(s"Expected Char, got $value")
      }
    }

    case object IntToChar extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Char(v.toChar)))
        case _ => Left(s"Expected Int, got $value")
      }
    }

    case object CharToString extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Char(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
        case _ => Left(s"Expected Char, got $value")
      }
    }
  }

  final case class Literal[S, A](dynamicValue: DynamicValue) extends SchemaExpr[S, A] {
    def eval(input: S)(implicit schema: Schema[A]): Either[OpticCheck, Seq[A]] =
       schema.fromDynamicValue(dynamicValue) match {
          case Right(value) => new Right(value :: Nil)
          case Left(error)  => new Left(new OpticCheck(::(OpticCheck.DynamicConversionError(error.message), Nil)))
        }

    def evalDynamic(input: S): Either[OpticCheck, Seq[DynamicValue]] = dynamicResult

    private[this] val dynamicResult = new Right(dynamicValue :: Nil)
  }

  object Literal {
    def apply[S, A](value: A)(implicit schema: Schema[A]): Literal[S, A] =
      new Literal(schema.toDynamicValue(value))
  }

  final case class Optic[A, B](optic: zio.blocks.schema.Optic[A, B]) extends SchemaExpr[A, B] {
    def eval(input: A)(implicit schema: Schema[B]): Either[OpticCheck, Seq[B]] = optic match {
      case l: Lens[?, ?] =>
        new Right(l.get(input) :: Nil)
      case p: Prism[?, ?] =>
        p.getOrFail(input) match {
          case Right(x: B @scala.unchecked) => new Right(x :: Nil)
          case left                         => left.asInstanceOf[Either[OpticCheck, Seq[B]]]
        }
      case o: Optional[?, ?] =>
        o.getOrFail(input) match {
          case Right(x) => new Right(x :: Nil)
          case left     => left.asInstanceOf[Either[OpticCheck, Seq[B]]]
        }
      case t: Traversal[?, ?] =>
        val sb = Seq.newBuilder[B]
        t.fold[Unit](input)((), (_, a) => sb.addOne(a))
        val r = sb.result()
        if (r.isEmpty) new Left(t.check(input).get)
        else new Right(r)
    }

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] = optic match {
      case l: Lens[?, ?] =>
        new Right(toDynamicValue(l.get(input)) :: Nil)
      case p: Prism[?, ?] =>
        p.getOrFail(input) match {
          case Right(x: B @scala.unchecked) => new Right(toDynamicValue(x) :: Nil)
          case left                         => left.asInstanceOf[Either[OpticCheck, Seq[DynamicValue]]]
        }
      case o: Optional[?, ?] =>
        o.getOrFail(input) match {
          case Right(x) => new Right(toDynamicValue(x) :: Nil)
          case left     => left.asInstanceOf[Either[OpticCheck, Seq[DynamicValue]]]
        }
      case t: Traversal[?, ?] =>
        val sb = Seq.newBuilder[DynamicValue]
        t.fold[Unit](input)((), (_, a) => sb.addOne(toDynamicValue(a)))
        val r = sb.result()
        if (r.isEmpty) new Left(t.check(input).get)
        else new Right(r)
    }

    private[this] val toDynamicValue: B => DynamicValue = optic.focus.toDynamicValue
  }

  sealed trait UnaryOp[A, +B] extends SchemaExpr[A, B] {
    def expr: SchemaExpr[A, B]
  }

  sealed trait BinaryOp[A, +B, +C] extends SchemaExpr[A, C] {
    def left: SchemaExpr[A, B]

    def right: SchemaExpr[A, B]
  }

  final case class Relational[A, B](left: SchemaExpr[A, B], right: SchemaExpr[A, B], operator: RelationalOperator)
      extends BinaryOp[A, B, Boolean] {
    def eval(input: A)(implicit schema: Schema[Boolean]): Either[OpticCheck, Seq[Boolean]] =
      if (operator == RelationalOperator.Equal || operator == RelationalOperator.NotEqual) {
        for {
          xs <- left.evalDynamic(input)
          ys <- right.evalDynamic(input)
        } yield {
          if (operator == RelationalOperator.Equal) for { x <- xs; y <- ys } yield x == y
          else for { x <- xs; y <- ys } yield x != y
        }
      } else { // FIXME: Use Ordering to avoid converisons to dynamic values
        for {
          xs <- left.evalDynamic(input)
          ys <- right.evalDynamic(input)
        } yield {
          if (operator == RelationalOperator.LessThan) for { x <- xs; y <- ys } yield x < y
          else if (operator == RelationalOperator.LessThanOrEqual) for { x <- xs; y <- ys } yield x <= y
          else if (operator == RelationalOperator.GreaterThan) for { x <- xs; y <- ys } yield x > y
          else for { x <- xs; y <- ys } yield x >= y
        }
      }

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- left.evalDynamic(input)
        ys <- right.evalDynamic(input)
      } yield operator match {
        case RelationalOperator.LessThan           => for { x <- xs; y <- ys } yield toDynamicValue(x < y)
        case RelationalOperator.LessThanOrEqual    => for { x <- xs; y <- ys } yield toDynamicValue(x <= y)
        case RelationalOperator.GreaterThan        => for { x <- xs; y <- ys } yield toDynamicValue(x > y)
        case RelationalOperator.GreaterThanOrEqual => for { x <- xs; y <- ys } yield toDynamicValue(x >= y)
        case RelationalOperator.Equal              => for { x <- xs; y <- ys } yield toDynamicValue(x == y)
        case RelationalOperator.NotEqual           => for { x <- xs; y <- ys } yield toDynamicValue(x != y)
      }

    private[this] def toDynamicValue(value: Boolean): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Boolean(value))
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

  final case class Logical[A](left: SchemaExpr[A, Boolean], right: SchemaExpr[A, Boolean], operator: LogicalOperator)
      extends BinaryOp[A, Boolean, Boolean] {
    def eval(input: A)(implicit schema: Schema[Boolean]): Either[OpticCheck, Seq[Boolean]] =
      for {
        xs <- left.eval(input)
        ys <- right.eval(input)
      } yield operator match {
        case LogicalOperator.And => for { x <- xs; y <- ys } yield x && y
        case LogicalOperator.Or  => for { x <- xs; y <- ys } yield x || y
      }

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- left.eval(input)
        ys <- right.eval(input)
      } yield operator match {
        case LogicalOperator.And => for { x <- xs; y <- ys } yield toDynamicValue(x && y)
        case LogicalOperator.Or  => for { x <- xs; y <- ys } yield toDynamicValue(x || y)
      }

    private[this] def toDynamicValue(value: Boolean): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Boolean(value))
  }

  sealed trait LogicalOperator

  object LogicalOperator {
    case object And extends LogicalOperator
    case object Or  extends LogicalOperator
  }

  final case class Not[A](expr: SchemaExpr[A, Boolean]) extends UnaryOp[A, Boolean] {
    def eval(input: A)(implicit schema: Schema[Boolean]): Either[OpticCheck, Seq[Boolean]] =
      for {
        xs <- expr.eval(input)
      } yield xs.map(!_)

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- expr.eval(input)
      } yield xs.map(x => toDynamicValue(!x))

    private[this] def toDynamicValue(value: Boolean): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Boolean(value))
  }

  final case class Arithmetic[S, A](
    left: SchemaExpr[S, A],
    right: SchemaExpr[S, A],
    operator: ArithmeticOperator,
    isNumeric: IsNumeric[A]
  ) extends BinaryOp[S, A, A] {
    def eval(input: S)(implicit schema: Schema[A]): Either[OpticCheck, Seq[A]] =
      for {
        xs <- left.eval(input)(isNumeric.schema)
        ys <- right.eval(input)(isNumeric.schema)
      } yield {
        val n = isNumeric.numeric
        operator match {
          case ArithmeticOperator.Add      => for { x <- xs; y <- ys } yield n.plus(x, y)
          case ArithmeticOperator.Subtract => for { x <- xs; y <- ys } yield n.minus(x, y)
          case ArithmeticOperator.Multiply => for { x <- xs; y <- ys } yield n.times(x, y)
        }
      }

    def evalDynamic(input: S): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- left.eval(input)(isNumeric.schema)
        ys <- right.eval(input)(isNumeric.schema)
      } yield {
        val n = isNumeric.numeric
        operator match {
          case ArithmeticOperator.Add      => for { x <- xs; y <- ys } yield toDynamicValue(n.plus(x, y))
          case ArithmeticOperator.Subtract => for { x <- xs; y <- ys } yield toDynamicValue(n.minus(x, y))
          case ArithmeticOperator.Multiply => for { x <- xs; y <- ys } yield toDynamicValue(n.times(x, y))
        }
      }

    private[this] val toDynamicValue: A => DynamicValue = isNumeric.primitiveType.toDynamicValue
  }

  sealed trait ArithmeticOperator

  object ArithmeticOperator {
    case object Add      extends ArithmeticOperator
    case object Subtract extends ArithmeticOperator
    case object Multiply extends ArithmeticOperator
  }

  final case class StringConcat[A](left: SchemaExpr[A, String], right: SchemaExpr[A, String])
      extends BinaryOp[A, String, String] {
    def eval(input: A)(implicit schema: Schema[String]): Either[OpticCheck, Seq[String]] =
      for {
        xs <- left.eval(input)
        ys <- right.eval(input)
      } yield for { x <- xs; y <- ys } yield x + y

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- left.eval(input)
        ys <- right.eval(input)
      } yield for { x <- xs; y <- ys } yield toDynamicValue(x + y)

    private[this] def toDynamicValue(value: String): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.String(value))
  }

  final case class StringRegexMatch[A](regex: SchemaExpr[A, String], string: SchemaExpr[A, String])
      extends SchemaExpr[A, Boolean] {
    def eval(input: A)(implicit schema: Schema[Boolean]): Either[OpticCheck, Seq[Boolean]] =
      for {
        xs <- regex.eval(input)(Schema[String])
        ys <- string.eval(input)(Schema[String])
      } yield for { x <- xs; y <- ys } yield x.matches(y)

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- regex.eval(input)
        ys <- string.eval(input)
      } yield for { x <- xs; y <- ys } yield toDynamicValue(x.matches(y))

    private[this] def toDynamicValue(value: Boolean): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Boolean(value))
  }

  final case class StringLength[A](string: SchemaExpr[A, String]) extends SchemaExpr[A, Int] {
    def eval(input: A)(implicit schema: Schema[Int]): Either[OpticCheck, Seq[Int]] =
      for {
        xs <- string.eval(input)(Schema[String])
      } yield xs.map(_.length)

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- string.eval(input)
      } yield xs.map(x => toDynamicValue(x.length))

    private[this] def toDynamicValue(value: Int): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Int(value))
  }
}
