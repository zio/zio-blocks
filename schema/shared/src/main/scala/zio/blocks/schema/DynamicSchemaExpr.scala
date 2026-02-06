package zio.blocks.schema

import zio.blocks.chunk.Chunk

sealed trait DynamicSchemaExpr {
  def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]]
}

object DynamicSchemaExpr {

  // ==================== Leaf Expressions ====================

  final case class Literal(value: DynamicValue) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      Right(Seq(value))
  }

  final case class Select(path: DynamicOptic) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      walkPath(Chunk(input), path.nodes, 0)

    private def walkPath(
      current: Chunk[DynamicValue],
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int
    ): Either[SchemaError, Seq[DynamicValue]] = {
      if (idx >= nodes.length) return Right(current.toSeq)
      if (current.isEmpty) return Right(Seq.empty)

      val node = nodes(idx)
      node match {
        case DynamicOptic.Node.Field(name) =>
          val next = current.flatMap {
            case r: DynamicValue.Record => r.fields.collect { case (n, v) if n == name => v }
            case _                      => Chunk.empty
          }
          walkPath(next, nodes, idx + 1)

        case DynamicOptic.Node.Case(expectedCase) =>
          val next = current.flatMap {
            case v: DynamicValue.Variant if v.caseNameValue == expectedCase => Chunk(v.value)
            case _                                                          => Chunk.empty
          }
          walkPath(next, nodes, idx + 1)

        case DynamicOptic.Node.Elements =>
          val next = current.flatMap {
            case s: DynamicValue.Sequence => s.elements
            case _                        => Chunk.empty
          }
          walkPath(next, nodes, idx + 1)

        case DynamicOptic.Node.AtIndex(i) =>
          val next = current.flatMap {
            case s: DynamicValue.Sequence if i >= 0 && i < s.elements.length =>
              Chunk(s.elements(i))
            case _ => Chunk.empty
          }
          walkPath(next, nodes, idx + 1)

        case DynamicOptic.Node.AtIndices(indices) =>
          val next = current.flatMap {
            case s: DynamicValue.Sequence =>
              Chunk.from(indices.flatMap(i => if (i >= 0 && i < s.elements.length) Some(s.elements(i)) else None))
            case _ => Chunk.empty
          }
          walkPath(next, nodes, idx + 1)

        case DynamicOptic.Node.AtMapKey(key) =>
          val next = current.flatMap {
            case m: DynamicValue.Map => m.entries.collect { case (k, v) if k == key => v }
            case _                   => Chunk.empty
          }
          walkPath(next, nodes, idx + 1)

        case DynamicOptic.Node.AtMapKeys(keys) =>
          val next = current.flatMap {
            case m: DynamicValue.Map =>
              Chunk.from(keys.flatMap(key => m.entries.collect { case (k, v) if k == key => v }))
            case _ => Chunk.empty
          }
          walkPath(next, nodes, idx + 1)

        case DynamicOptic.Node.MapKeys =>
          val next = current.flatMap {
            case m: DynamicValue.Map => m.entries.map(_._1)
            case _                   => Chunk.empty
          }
          walkPath(next, nodes, idx + 1)

        case DynamicOptic.Node.MapValues =>
          val next = current.flatMap {
            case m: DynamicValue.Map => m.entries.map(_._2)
            case _                   => Chunk.empty
          }
          walkPath(next, nodes, idx + 1)

        case DynamicOptic.Node.Wrapped =>
          walkPath(current, nodes, idx + 1)
      }
    }
  }

  final case class PrimitiveConversion(conversionType: SchemaExpr.ConversionType) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      conversionType.convert(input).map(Seq(_)).left.map(SchemaError.conversionFailed(Nil, _))
  }

  // ==================== Relational Operators ====================

  final case class Relational(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr,
    operator: RelationalOperator
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        xs <- left.eval(input)
        ys <- right.eval(input)
      } yield for { x <- xs; y <- ys } yield operator.apply(x, y)
  }

  sealed trait RelationalOperator {
    def apply(x: DynamicValue, y: DynamicValue): DynamicValue
  }

  object RelationalOperator {
    case object LessThan extends RelationalOperator {
      def apply(x: DynamicValue, y: DynamicValue): DynamicValue =
        DynamicValue.Primitive(PrimitiveValue.Boolean(x < y))
    }

    case object LessThanOrEqual extends RelationalOperator {
      def apply(x: DynamicValue, y: DynamicValue): DynamicValue =
        DynamicValue.Primitive(PrimitiveValue.Boolean(x <= y))
    }

    case object GreaterThan extends RelationalOperator {
      def apply(x: DynamicValue, y: DynamicValue): DynamicValue =
        DynamicValue.Primitive(PrimitiveValue.Boolean(x > y))
    }

    case object GreaterThanOrEqual extends RelationalOperator {
      def apply(x: DynamicValue, y: DynamicValue): DynamicValue =
        DynamicValue.Primitive(PrimitiveValue.Boolean(x >= y))
    }

    case object Equal extends RelationalOperator {
      def apply(x: DynamicValue, y: DynamicValue): DynamicValue =
        DynamicValue.Primitive(PrimitiveValue.Boolean(x == y))
    }

    case object NotEqual extends RelationalOperator {
      def apply(x: DynamicValue, y: DynamicValue): DynamicValue =
        DynamicValue.Primitive(PrimitiveValue.Boolean(x != y))
    }
  }

  // ==================== Logical Operators ====================

  final case class Logical(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr,
    operator: LogicalOperator
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        xs <- left.eval(input)
        ys <- right.eval(input)
      } yield operator.apply(xs, ys)
  }

  sealed trait LogicalOperator {
    def apply(xs: Seq[DynamicValue], ys: Seq[DynamicValue]): Seq[DynamicValue]
  }

  object LogicalOperator {
    case object And extends LogicalOperator {
      def apply(xs: Seq[DynamicValue], ys: Seq[DynamicValue]): Seq[DynamicValue] =
        for {
          x <- xs
          y <- ys
        } yield {
          val xBool = extractBoolean(x)
          val yBool = extractBoolean(y)
          DynamicValue.Primitive(PrimitiveValue.Boolean(xBool && yBool))
        }

      private def extractBoolean(dv: DynamicValue): Boolean = dv match {
        case DynamicValue.Primitive(PrimitiveValue.Boolean(b)) => b
        case _                                                 => throw new IllegalArgumentException(s"Expected Boolean, got: $dv")
      }
    }

    case object Or extends LogicalOperator {
      def apply(xs: Seq[DynamicValue], ys: Seq[DynamicValue]): Seq[DynamicValue] =
        for {
          x <- xs
          y <- ys
        } yield {
          val xBool = extractBoolean(x)
          val yBool = extractBoolean(y)
          DynamicValue.Primitive(PrimitiveValue.Boolean(xBool || yBool))
        }

      private def extractBoolean(dv: DynamicValue): Boolean = dv match {
        case DynamicValue.Primitive(PrimitiveValue.Boolean(b)) => b
        case _                                                 => throw new IllegalArgumentException(s"Expected Boolean, got: $dv")
      }
    }
  }

  final case class Not(expr: DynamicSchemaExpr) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        xs <- expr.eval(input)
      } yield xs.map { x =>
        val xBool = x match {
          case DynamicValue.Primitive(PrimitiveValue.Boolean(b)) => b
          case _                                                 => throw new IllegalArgumentException(s"Expected Boolean, got: $x")
        }
        DynamicValue.Primitive(PrimitiveValue.Boolean(!xBool))
      }
  }

  // ==================== Arithmetic Operators ====================

  sealed trait NumericTypeTag {
    def add(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue]
    def subtract(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue]
    def multiply(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue]
    def divide(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue]
    def pow(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue]
    def modulo(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue]
  }

  object NumericTypeTag {
    case object ByteTag extends NumericTypeTag {
      def add(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Byte((a + b).toByte)) }
      def subtract(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Byte((a - b).toByte)) }
      def multiply(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Byte((a * b).toByte)) }
      def divide(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Byte((a / b).toByte)) }
      def pow(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Byte(Math.pow(a.toDouble, b.toDouble).toByte)) }
      def modulo(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Byte((a % b).toByte)) }

      private def extract2(x: DynamicValue, y: DynamicValue): Either[String, (Byte, Byte)] =
        (x, y) match {
          case (DynamicValue.Primitive(PrimitiveValue.Byte(a)), DynamicValue.Primitive(PrimitiveValue.Byte(b))) =>
            Right((a, b))
          case _ => Left(s"Expected Byte values, got: $x and $y")
        }
    }

    case object ShortTag extends NumericTypeTag {
      def add(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Short((a + b).toShort)) }
      def subtract(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Short((a - b).toShort)) }
      def multiply(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Short((a * b).toShort)) }
      def divide(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Short((a / b).toShort)) }
      def pow(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Short(Math.pow(a.toDouble, b.toDouble).toShort)) }
      def modulo(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Short((a % b).toShort)) }

      private def extract2(x: DynamicValue, y: DynamicValue): Either[String, (Short, Short)] =
        (x, y) match {
          case (DynamicValue.Primitive(PrimitiveValue.Short(a)), DynamicValue.Primitive(PrimitiveValue.Short(b))) =>
            Right((a, b))
          case _ => Left(s"Expected Short values, got: $x and $y")
        }
    }

    case object IntTag extends NumericTypeTag {
      def add(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Int(a + b)) }
      def subtract(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Int(a - b)) }
      def multiply(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Int(a * b)) }
      def divide(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Int(a / b)) }
      def pow(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Int(Math.pow(a.toDouble, b.toDouble).toInt)) }
      def modulo(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Int(a % b)) }

      private def extract2(x: DynamicValue, y: DynamicValue): Either[String, (Int, Int)] =
        (x, y) match {
          case (DynamicValue.Primitive(PrimitiveValue.Int(a)), DynamicValue.Primitive(PrimitiveValue.Int(b))) =>
            Right((a, b))
          case _ => Left(s"Expected Int values, got: $x and $y")
        }
    }

    case object LongTag extends NumericTypeTag {
      def add(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Long(a + b)) }
      def subtract(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Long(a - b)) }
      def multiply(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Long(a * b)) }
      def divide(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Long(a / b)) }
      def pow(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Long(Math.pow(a.toDouble, b.toDouble).toLong)) }
      def modulo(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Long(a % b)) }

      private def extract2(x: DynamicValue, y: DynamicValue): Either[String, (Long, Long)] =
        (x, y) match {
          case (DynamicValue.Primitive(PrimitiveValue.Long(a)), DynamicValue.Primitive(PrimitiveValue.Long(b))) =>
            Right((a, b))
          case _ => Left(s"Expected Long values, got: $x and $y")
        }
    }

    case object FloatTag extends NumericTypeTag {
      def add(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Float(a + b)) }
      def subtract(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Float(a - b)) }
      def multiply(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Float(a * b)) }
      def divide(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Float(a / b)) }
      def pow(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Float(Math.pow(a.toDouble, b.toDouble).toFloat)) }
      def modulo(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Float(a % b)) }

      private def extract2(x: DynamicValue, y: DynamicValue): Either[String, (Float, Float)] =
        (x, y) match {
          case (DynamicValue.Primitive(PrimitiveValue.Float(a)), DynamicValue.Primitive(PrimitiveValue.Float(b))) =>
            Right((a, b))
          case _ => Left(s"Expected Float values, got: $x and $y")
        }
    }

    case object DoubleTag extends NumericTypeTag {
      def add(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Double(a + b)) }
      def subtract(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Double(a - b)) }
      def multiply(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Double(a * b)) }
      def divide(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Double(a / b)) }
      def pow(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Double(Math.pow(a, b))) }
      def modulo(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Double(a % b)) }

      private def extract2(x: DynamicValue, y: DynamicValue): Either[String, (Double, Double)] =
        (x, y) match {
          case (DynamicValue.Primitive(PrimitiveValue.Double(a)), DynamicValue.Primitive(PrimitiveValue.Double(b))) =>
            Right((a, b))
          case _ => Left(s"Expected Double values, got: $x and $y")
        }
    }

    case object BigIntTag extends NumericTypeTag {
      def add(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.BigInt(a + b)) }
      def subtract(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.BigInt(a - b)) }
      def multiply(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.BigInt(a * b)) }
      def divide(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.BigInt(a / b)) }
      def pow(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.BigInt(a.pow(b.toInt))) }
      def modulo(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.BigInt(a % b)) }

      private def extract2(x: DynamicValue, y: DynamicValue): Either[String, (BigInt, BigInt)] =
        (x, y) match {
          case (DynamicValue.Primitive(PrimitiveValue.BigInt(a)), DynamicValue.Primitive(PrimitiveValue.BigInt(b))) =>
            Right((a, b))
          case _ => Left(s"Expected BigInt values, got: $x and $y")
        }
    }

    case object BigDecimalTag extends NumericTypeTag {
      def add(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.BigDecimal(a + b)) }
      def subtract(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.BigDecimal(a - b)) }
      def multiply(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.BigDecimal(a * b)) }
      def divide(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.BigDecimal(a / b)) }
      def pow(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.BigDecimal(a.pow(b.toInt))) }
      def modulo(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.BigDecimal(a % b)) }

      private def extract2(x: DynamicValue, y: DynamicValue): Either[String, (BigDecimal, BigDecimal)] =
        (x, y) match {
          case (DynamicValue.Primitive(PrimitiveValue.BigDecimal(a)), DynamicValue.Primitive(PrimitiveValue.BigDecimal(b))) =>
            Right((a, b))
          case _ => Left(s"Expected BigDecimal values, got: $x and $y")
        }
    }
  }

  final case class Arithmetic(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr,
    operator: ArithmeticOperator,
    numericType: NumericTypeTag
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        xs <- left.eval(input)
        ys <- right.eval(input)
        results <- {
          val computed = for {
            x <- xs
            y <- ys
          } yield operator.apply(x, y, numericType)
          
          val errors = computed.collect { case Left(e) => e }
          if (errors.nonEmpty) Left(SchemaError.conversionFailed(Nil, errors.head))
          else Right(computed.collect { case Right(v) => v })
        }
      } yield results
  }

  sealed trait ArithmeticOperator {
    def apply(x: DynamicValue, y: DynamicValue, numericType: NumericTypeTag): Either[String, DynamicValue]
  }

  object ArithmeticOperator {
    case object Add extends ArithmeticOperator {
      def apply(x: DynamicValue, y: DynamicValue, numericType: NumericTypeTag): Either[String, DynamicValue] =
        numericType.add(x, y)
    }

    case object Subtract extends ArithmeticOperator {
      def apply(x: DynamicValue, y: DynamicValue, numericType: NumericTypeTag): Either[String, DynamicValue] =
        numericType.subtract(x, y)
    }

    case object Multiply extends ArithmeticOperator {
      def apply(x: DynamicValue, y: DynamicValue, numericType: NumericTypeTag): Either[String, DynamicValue] =
        numericType.multiply(x, y)
    }

    case object Divide extends ArithmeticOperator {
      def apply(x: DynamicValue, y: DynamicValue, numericType: NumericTypeTag): Either[String, DynamicValue] =
        numericType.divide(x, y)
    }

    case object Pow extends ArithmeticOperator {
      def apply(x: DynamicValue, y: DynamicValue, numericType: NumericTypeTag): Either[String, DynamicValue] =
        numericType.pow(x, y)
    }

    case object Modulo extends ArithmeticOperator {
      def apply(x: DynamicValue, y: DynamicValue, numericType: NumericTypeTag): Either[String, DynamicValue] =
        numericType.modulo(x, y)
    }
  }

  // ==================== Bitwise Operators ====================

  final case class Bitwise(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr,
    operator: BitwiseOperator
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        xs <- left.eval(input)
        ys <- right.eval(input)
      } yield for { x <- xs; y <- ys } yield operator.apply(x, y)
  }

  sealed trait BitwiseOperator {
    def apply(x: DynamicValue, y: DynamicValue): DynamicValue
  }

  object BitwiseOperator {
    case object And extends BitwiseOperator {
      def apply(x: DynamicValue, y: DynamicValue): DynamicValue = applyOp(x, y, _ & _)
    }

    case object Or extends BitwiseOperator {
      def apply(x: DynamicValue, y: DynamicValue): DynamicValue = applyOp(x, y, _ | _)
    }

    case object Xor extends BitwiseOperator {
      def apply(x: DynamicValue, y: DynamicValue): DynamicValue = applyOp(x, y, _ ^ _)
    }

    case object LeftShift extends BitwiseOperator {
      def apply(x: DynamicValue, y: DynamicValue): DynamicValue = applyShift(x, y, (a, b) => a << b)
    }

    case object RightShift extends BitwiseOperator {
      def apply(x: DynamicValue, y: DynamicValue): DynamicValue = applyShift(x, y, (a, b) => a >> b)
    }

    case object UnsignedRightShift extends BitwiseOperator {
      def apply(x: DynamicValue, y: DynamicValue): DynamicValue = applyShift(x, y, (a, b) => a >>> b)
    }

    private def applyOp(x: DynamicValue, y: DynamicValue, op: (Long, Long) => Long): DynamicValue = {
      val (lVal, lIsLong) = extractIntegral(x)
      val (rVal, rIsLong) = extractIntegral(y)
      val result = op(lVal, rVal)
      
      if (lIsLong || rIsLong) DynamicValue.Primitive(PrimitiveValue.Long(result))
      else DynamicValue.Primitive(PrimitiveValue.Int(result.toInt))
    }

    private def applyShift(x: DynamicValue, y: DynamicValue, op: (Long, Int) => Long): DynamicValue = {
      val (lVal, lIsLong) = extractIntegral(x)
      val (rVal, _) = extractIntegral(y)
      val result = op(lVal, rVal.toInt)
      
      if (lIsLong) DynamicValue.Primitive(PrimitiveValue.Long(result))
      else DynamicValue.Primitive(PrimitiveValue.Int(result.toInt))
    }

    private def extractIntegral(dv: DynamicValue): (Long, Boolean) = dv match {
      case DynamicValue.Primitive(PrimitiveValue.Byte(v))  => (v.toLong, false)
      case DynamicValue.Primitive(PrimitiveValue.Short(v)) => (v.toLong, false)
      case DynamicValue.Primitive(PrimitiveValue.Int(v))   => (v.toLong, false)
      case DynamicValue.Primitive(PrimitiveValue.Long(v))  => (v, true)
      case _                                               => throw new IllegalArgumentException(s"Bitwise operation requires integral types, got: $dv")
    }
  }

  final case class BitwiseNot(expr: DynamicSchemaExpr) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        xs <- expr.eval(input)
      } yield xs.map(applyNot)

    private def applyNot(x: DynamicValue): DynamicValue = x match {
      case DynamicValue.Primitive(PrimitiveValue.Byte(v))  => DynamicValue.Primitive(PrimitiveValue.Byte((~v).toByte))
      case DynamicValue.Primitive(PrimitiveValue.Short(v)) => DynamicValue.Primitive(PrimitiveValue.Short((~v).toShort))
      case DynamicValue.Primitive(PrimitiveValue.Int(v))   => DynamicValue.Primitive(PrimitiveValue.Int(~v))
      case DynamicValue.Primitive(PrimitiveValue.Long(v))  => DynamicValue.Primitive(PrimitiveValue.Long(~v))
      case _                                               => throw new IllegalArgumentException(s"Bitwise NOT requires integral type, got: $x")
    }
  }

  // ==================== String Operations ====================

  final case class StringConcat(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        xs <- left.eval(input)
        ys <- right.eval(input)
      } yield for {
        x <- xs
        y <- ys
      } yield {
        val xStr = extractString(x)
        val yStr = extractString(y)
        DynamicValue.Primitive(PrimitiveValue.String(xStr + yStr))
      }

    private def extractString(dv: DynamicValue): String = dv match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) => s
      case _                                                => throw new IllegalArgumentException(s"Expected String, got: $dv")
    }
  }

  final case class StringRegexMatch(
    regex: DynamicSchemaExpr,
    string: DynamicSchemaExpr
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        xs <- regex.eval(input)
        ys <- string.eval(input)
      } yield for {
        x <- xs
        y <- ys
      } yield {
        val regexStr = extractString(x)
        val str = extractString(y)
        DynamicValue.Primitive(PrimitiveValue.Boolean(str.matches(regexStr)))
      }

    private def extractString(dv: DynamicValue): String = dv match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) => s
      case _                                                => throw new IllegalArgumentException(s"Expected String, got: $dv")
    }
  }

  final case class StringLength(string: DynamicSchemaExpr) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        xs <- string.eval(input)
      } yield xs.map { x =>
        val str = extractString(x)
        DynamicValue.Primitive(PrimitiveValue.Int(str.length))
      }

    private def extractString(dv: DynamicValue): String = dv match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) => s
      case _                                                => throw new IllegalArgumentException(s"Expected String, got: $dv")
    }
  }

  final case class StringSubstring(
    string: DynamicSchemaExpr,
    start: DynamicSchemaExpr,
    end: DynamicSchemaExpr
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        strings <- string.eval(input)
        starts <- start.eval(input)
        ends <- end.eval(input)
      } yield for {
        s <- strings
        st <- starts
        en <- ends
      } yield {
        val str = extractString(s)
        val startInt = extractInt(st)
        val endInt = extractInt(en)
        DynamicValue.Primitive(PrimitiveValue.String(str.substring(startInt, endInt)))
      }

    private def extractString(dv: DynamicValue): String = dv match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) => s
      case _                                                => throw new IllegalArgumentException(s"Expected String, got: $dv")
    }

    private def extractInt(dv: DynamicValue): Int = dv match {
      case DynamicValue.Primitive(PrimitiveValue.Int(i)) => i
      case _                                             => throw new IllegalArgumentException(s"Expected Int, got: $dv")
    }
  }

  final case class StringTrim(string: DynamicSchemaExpr) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        xs <- string.eval(input)
      } yield xs.map { x =>
        val str = extractString(x)
        DynamicValue.Primitive(PrimitiveValue.String(str.trim))
      }

    private def extractString(dv: DynamicValue): String = dv match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) => s
      case _                                                => throw new IllegalArgumentException(s"Expected String, got: $dv")
    }
  }

  final case class StringToUpperCase(string: DynamicSchemaExpr) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        xs <- string.eval(input)
      } yield xs.map { x =>
        val str = extractString(x)
        DynamicValue.Primitive(PrimitiveValue.String(str.toUpperCase))
      }

    private def extractString(dv: DynamicValue): String = dv match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) => s
      case _                                                => throw new IllegalArgumentException(s"Expected String, got: $dv")
    }
  }

  final case class StringToLowerCase(string: DynamicSchemaExpr) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        xs <- string.eval(input)
      } yield xs.map { x =>
        val str = extractString(x)
        DynamicValue.Primitive(PrimitiveValue.String(str.toLowerCase))
      }

    private def extractString(dv: DynamicValue): String = dv match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) => s
      case _                                                => throw new IllegalArgumentException(s"Expected String, got: $dv")
    }
  }

  final case class StringReplace(
    string: DynamicSchemaExpr,
    target: DynamicSchemaExpr,
    replacement: DynamicSchemaExpr
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        strings <- string.eval(input)
        targets <- target.eval(input)
        replacements <- replacement.eval(input)
      } yield for {
        s <- strings
        t <- targets
        r <- replacements
      } yield {
        val str = extractString(s)
        val targetStr = extractString(t)
        val replStr = extractString(r)
        DynamicValue.Primitive(PrimitiveValue.String(str.replace(targetStr, replStr)))
      }

    private def extractString(dv: DynamicValue): String = dv match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) => s
      case _                                                => throw new IllegalArgumentException(s"Expected String, got: $dv")
    }
  }

  final case class StringStartsWith(
    string: DynamicSchemaExpr,
    prefix: DynamicSchemaExpr
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        strings <- string.eval(input)
        prefixes <- prefix.eval(input)
      } yield for {
        s <- strings
        p <- prefixes
      } yield {
        val str = extractString(s)
        val prefixStr = extractString(p)
        DynamicValue.Primitive(PrimitiveValue.Boolean(str.startsWith(prefixStr)))
      }

    private def extractString(dv: DynamicValue): String = dv match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) => s
      case _                                                => throw new IllegalArgumentException(s"Expected String, got: $dv")
    }
  }

  final case class StringEndsWith(
    string: DynamicSchemaExpr,
    suffix: DynamicSchemaExpr
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        strings <- string.eval(input)
        suffixes <- suffix.eval(input)
      } yield for {
        s <- strings
        sx <- suffixes
      } yield {
        val str = extractString(s)
        val suffixStr = extractString(sx)
        DynamicValue.Primitive(PrimitiveValue.Boolean(str.endsWith(suffixStr)))
      }

    private def extractString(dv: DynamicValue): String = dv match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) => s
      case _                                                => throw new IllegalArgumentException(s"Expected String, got: $dv")
    }
  }

  final case class StringContains(
    string: DynamicSchemaExpr,
    substring: DynamicSchemaExpr
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        strings <- string.eval(input)
        substrings <- substring.eval(input)
      } yield for {
        s <- strings
        sub <- substrings
      } yield {
        val str = extractString(s)
        val subStr = extractString(sub)
        DynamicValue.Primitive(PrimitiveValue.Boolean(str.contains(subStr)))
      }

    private def extractString(dv: DynamicValue): String = dv match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) => s
      case _                                                => throw new IllegalArgumentException(s"Expected String, got: $dv")
    }
  }

  final case class StringIndexOf(
    string: DynamicSchemaExpr,
    substring: DynamicSchemaExpr
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        strings <- string.eval(input)
        substrings <- substring.eval(input)
      } yield for {
        s <- strings
        sub <- substrings
      } yield {
        val str = extractString(s)
        val subStr = extractString(sub)
        DynamicValue.Primitive(PrimitiveValue.Int(str.indexOf(subStr)))
      }

    private def extractString(dv: DynamicValue): String = dv match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) => s
      case _                                                => throw new IllegalArgumentException(s"Expected String, got: $dv")
    }
  }
}
