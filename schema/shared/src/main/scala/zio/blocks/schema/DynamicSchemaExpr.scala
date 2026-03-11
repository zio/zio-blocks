package zio.blocks.schema

import zio.blocks.chunk.Chunk

sealed trait DynamicSchemaExpr {
  def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]]
}

object DynamicSchemaExpr {

  private def extractBoolean(dv: DynamicValue): Either[SchemaError, Boolean] = dv match {
    case DynamicValue.Primitive(PrimitiveValue.Boolean(b)) => Right(b)
    case _                                                 => Left(SchemaError.conversionFailed(Nil, s"Expected Boolean, got: $dv"))
  }

  private def extractString(dv: DynamicValue): Either[SchemaError, String] = dv match {
    case DynamicValue.Primitive(PrimitiveValue.String(s)) => Right(s)
    case _                                                => Left(SchemaError.conversionFailed(Nil, s"Expected String, got: $dv"))
  }

  private def extractInt(dv: DynamicValue): Either[SchemaError, Int] = dv match {
    case DynamicValue.Primitive(PrimitiveValue.Int(i)) => Right(i)
    case _                                             => Left(SchemaError.conversionFailed(Nil, s"Expected Int, got: $dv"))
  }

  private def extractIntegral(dv: DynamicValue): Either[SchemaError, (Long, Boolean)] = dv match {
    case DynamicValue.Primitive(PrimitiveValue.Byte(v))  => Right((v.toLong, false))
    case DynamicValue.Primitive(PrimitiveValue.Short(v)) => Right((v.toLong, false))
    case DynamicValue.Primitive(PrimitiveValue.Int(v))   => Right((v.toLong, false))
    case DynamicValue.Primitive(PrimitiveValue.Long(v))  => Right((v, true))
    case _                                               => Left(SchemaError.conversionFailed(Nil, s"Bitwise operation requires integral types, got: $dv"))
  }

  private def sequence(results: Seq[Either[SchemaError, DynamicValue]]): Either[SchemaError, Seq[DynamicValue]] = {
    val errors = results.collect { case Left(e) => e }
    if (errors.nonEmpty) Left(errors.head)
    else Right(results.collect { case Right(v) => v })
  }
  // ==================== Leaf Expressions ====================

  final case class Literal(value: DynamicValue) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      Right(Seq(value))
  }

  final case class Select(path: DynamicOptic) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] = {
      val result = walkPath(Chunk(input), path.nodes, 0)
      result
    }

    private def walkPath(
      current: Chunk[DynamicValue],
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int
    ): Either[SchemaError, Seq[DynamicValue]] = {
      if (idx >= nodes.length) return Right(current.toSeq)
      if (current.isEmpty) return Right(Seq.empty)

      val node   = nodes(idx)
      val prefix = new DynamicOptic(nodes.take(idx + 1).toVector)

      node match {
        case DynamicOptic.Node.Field(name) =>
          val next = current.flatMap {
            case r: DynamicValue.Record => r.fields.collect { case (n, v) if n == name => v }
            case _                      => Chunk.empty
          }
          walkPath(next, nodes, idx + 1)

        case DynamicOptic.Node.Case(expectedCase) =>
          var result: Chunk[DynamicValue] = Chunk.empty
          var error: Option[SchemaError]  = None

          current.foreach {
            case v: DynamicValue.Variant if v.caseNameValue == expectedCase =>
              result = result ++ Chunk(v.value)
            case v: DynamicValue.Variant =>
              val actualCase = v.caseNameValue match {
                case "None" | "Some" => "Option"
                case other           => other
              }
              error = Some(
                SchemaError.message(
                  s"UnexpectedCase: expected $expectedCase, got $actualCase",
                  prefix
                )
              )
            case _ =>
          }

          if (error.isDefined) Left(error.get)
          else walkPath(result, nodes, idx + 1)

        case DynamicOptic.Node.Elements =>
          val hasSequence = current.exists(_.isInstanceOf[DynamicValue.Sequence])
          val next        = current.flatMap {
            case s: DynamicValue.Sequence => s.elements
            case _                        => Chunk.empty
          }
          if (next.isEmpty && hasSequence) {
            Left(SchemaError.message("EmptySequence", prefix))
          } else {
            walkPath(next, nodes, idx + 1)
          }

        case DynamicOptic.Node.AtIndex(i) =>
          var outOfBoundsError: Option[SchemaError] = None
          val next                                  = current.flatMap {
            case s: DynamicValue.Sequence if i >= 0 && i < s.elements.length =>
              Chunk(s.elements(i))
            case s: DynamicValue.Sequence =>
              outOfBoundsError = Some(
                SchemaError.message(
                  s"SequenceIndexOutOfBounds: index $i, size ${s.elements.length}",
                  prefix
                )
              )
              Chunk.empty
            case _ => Chunk.empty
          }
          outOfBoundsError match {
            case Some(err) => Left(err)
            case None      => walkPath(next, nodes, idx + 1)
          }

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
          if (next.isEmpty && current.exists(_.isInstanceOf[DynamicValue.Map])) {
            Left(SchemaError.message(s"MissingKey:${key.toString}", prefix))
          } else {
            walkPath(next, nodes, idx + 1)
          }

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
          if (next.isEmpty && current.exists(_.isInstanceOf[DynamicValue.Map])) {
            Left(SchemaError.message("EmptyMap", prefix))
          } else {
            walkPath(next, nodes, idx + 1)
          }

        case DynamicOptic.Node.MapValues =>
          val next = current.flatMap {
            case m: DynamicValue.Map => m.entries.map(_._2)
            case _                   => Chunk.empty
          }
          if (next.isEmpty && current.exists(_.isInstanceOf[DynamicValue.Map])) {
            Left(SchemaError.message("EmptyMap", prefix))
          } else {
            walkPath(next, nodes, idx + 1)
          }

        case DynamicOptic.Node.Wrapped =>
          walkPath(current, nodes, idx + 1)

        case _: DynamicOptic.Node.TypeSearch =>
          // TypeSearch requires Schema context - not supported in untyped operations
          Right(Seq.empty)

        case DynamicOptic.Node.SchemaSearch(_) =>
          // SchemaSearch requires schema context - not supported in expression evaluation
          Right(Seq.empty)
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
        xs     <- left.eval(input)
        ys     <- right.eval(input)
        result <- operator.apply(xs, ys)
      } yield result
  }

  sealed trait LogicalOperator {
    def apply(xs: Seq[DynamicValue], ys: Seq[DynamicValue]): Either[SchemaError, Seq[DynamicValue]]
  }

  object LogicalOperator {
    case object And extends LogicalOperator {
      def apply(xs: Seq[DynamicValue], ys: Seq[DynamicValue]): Either[SchemaError, Seq[DynamicValue]] = {
        val results = for { x <- xs; y <- ys } yield {
          for {
            xBool <- extractBoolean(x)
            yBool <- extractBoolean(y)
          } yield DynamicValue.Primitive(PrimitiveValue.Boolean(xBool && yBool))
        }
        sequence(results)
      }
    }

    case object Or extends LogicalOperator {
      def apply(xs: Seq[DynamicValue], ys: Seq[DynamicValue]): Either[SchemaError, Seq[DynamicValue]] = {
        val results = for { x <- xs; y <- ys } yield {
          for {
            xBool <- extractBoolean(x)
            yBool <- extractBoolean(y)
          } yield DynamicValue.Primitive(PrimitiveValue.Boolean(xBool || yBool))
        }
        sequence(results)
      }
    }
  }

  final case class Not(expr: DynamicSchemaExpr) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        xs     <- expr.eval(input)
        result <- {
          val mapped = xs.map { x =>
            extractBoolean(x).map(b => DynamicValue.Primitive(PrimitiveValue.Boolean(!b)))
          }
          sequence(mapped)
        }
      } yield result
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
        extract2(x, y).map { case (a, b) =>
          DynamicValue.Primitive(PrimitiveValue.Byte(Math.pow(a.toDouble, b.toDouble).toByte))
        }
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
        extract2(x, y).map { case (a, b) =>
          DynamicValue.Primitive(PrimitiveValue.Short(Math.pow(a.toDouble, b.toDouble).toShort))
        }
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
        extract2(x, y).map { case (a, b) =>
          DynamicValue.Primitive(PrimitiveValue.Int(Math.pow(a.toDouble, b.toDouble).toInt))
        }
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
        extract2(x, y).map { case (a, b) =>
          DynamicValue.Primitive(PrimitiveValue.Long(Math.pow(a.toDouble, b.toDouble).toLong))
        }
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
        extract2(x, y).map { case (a, b) =>
          DynamicValue.Primitive(PrimitiveValue.Float(Math.pow(a.toDouble, b.toDouble).toFloat))
        }
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
          case (
                DynamicValue.Primitive(PrimitiveValue.BigDecimal(a)),
                DynamicValue.Primitive(PrimitiveValue.BigDecimal(b))
              ) =>
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
        xs      <- left.eval(input)
        ys      <- right.eval(input)
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
        xs      <- left.eval(input)
        ys      <- right.eval(input)
        results <- {
          val computed = for { x <- xs; y <- ys } yield operator.apply(x, y)
          sequence(computed)
        }
      } yield results
  }

  sealed trait BitwiseOperator {
    def apply(x: DynamicValue, y: DynamicValue): Either[SchemaError, DynamicValue]
  }

  object BitwiseOperator {
    case object And extends BitwiseOperator {
      def apply(x: DynamicValue, y: DynamicValue): Either[SchemaError, DynamicValue] = applyOp(x, y, _ & _)
    }

    case object Or extends BitwiseOperator {
      def apply(x: DynamicValue, y: DynamicValue): Either[SchemaError, DynamicValue] = applyOp(x, y, _ | _)
    }

    case object Xor extends BitwiseOperator {
      def apply(x: DynamicValue, y: DynamicValue): Either[SchemaError, DynamicValue] = applyOp(x, y, _ ^ _)
    }

    case object LeftShift extends BitwiseOperator {
      def apply(x: DynamicValue, y: DynamicValue): Either[SchemaError, DynamicValue] =
        applyShift(x, y, (a, b) => a << b)
    }

    case object RightShift extends BitwiseOperator {
      def apply(x: DynamicValue, y: DynamicValue): Either[SchemaError, DynamicValue] =
        applyShift(x, y, (a, b) => a >> b)
    }

    case object UnsignedRightShift extends BitwiseOperator {
      def apply(x: DynamicValue, y: DynamicValue): Either[SchemaError, DynamicValue] =
        applyShift(x, y, (a, b) => a >>> b)
    }

    private def applyOp(x: DynamicValue, y: DynamicValue, op: (Long, Long) => Long): Either[SchemaError, DynamicValue] =
      for {
        lPair <- extractIntegral(x)
        rPair <- extractIntegral(y)
      } yield {
        val (lVal, lIsLong) = lPair
        val (rVal, rIsLong) = rPair
        val result          = op(lVal, rVal)
        if (lIsLong || rIsLong) DynamicValue.Primitive(PrimitiveValue.Long(result))
        else DynamicValue.Primitive(PrimitiveValue.Int(result.toInt))
      }

    private def applyShift(
      x: DynamicValue,
      y: DynamicValue,
      op: (Long, Int) => Long
    ): Either[SchemaError, DynamicValue] =
      for {
        lPair <- extractIntegral(x)
        rPair <- extractIntegral(y)
      } yield {
        val (lVal, lIsLong) = lPair
        val (rVal, _)       = rPair
        val result          = op(lVal, rVal.toInt)
        if (lIsLong) DynamicValue.Primitive(PrimitiveValue.Long(result))
        else DynamicValue.Primitive(PrimitiveValue.Int(result.toInt))
      }
  }

  final case class BitwiseNot(expr: DynamicSchemaExpr) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        xs     <- expr.eval(input)
        result <- {
          val mapped = xs.map(applyNot)
          sequence(mapped)
        }
      } yield result

    private def applyNot(x: DynamicValue): Either[SchemaError, DynamicValue] = x match {
      case DynamicValue.Primitive(PrimitiveValue.Byte(v)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Byte((~v).toByte)))
      case DynamicValue.Primitive(PrimitiveValue.Short(v)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Short((~v).toShort)))
      case DynamicValue.Primitive(PrimitiveValue.Int(v))  => Right(DynamicValue.Primitive(PrimitiveValue.Int(~v)))
      case DynamicValue.Primitive(PrimitiveValue.Long(v)) => Right(DynamicValue.Primitive(PrimitiveValue.Long(~v)))
      case _                                              => Left(SchemaError.conversionFailed(Nil, s"Bitwise NOT requires integral type, got: $x"))
    }
  }

  // ==================== String Operations ====================

  final case class StringConcat(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        xs      <- left.eval(input)
        ys      <- right.eval(input)
        results <- {
          val computed = for { x <- xs; y <- ys } yield {
            for {
              xStr <- extractString(x)
              yStr <- extractString(y)
            } yield DynamicValue.Primitive(PrimitiveValue.String(xStr + yStr))
          }
          sequence(computed)
        }
      } yield results
  }

  final case class StringRegexMatch(
    regex: DynamicSchemaExpr,
    string: DynamicSchemaExpr
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        xs      <- regex.eval(input)
        ys      <- string.eval(input)
        results <- {
          val computed = for { x <- xs; y <- ys } yield {
            for {
              regexStr <- extractString(x)
              str      <- extractString(y)
            } yield DynamicValue.Primitive(PrimitiveValue.Boolean(str.matches(regexStr)))
          }
          sequence(computed)
        }
      } yield results
  }

  final case class StringLength(string: DynamicSchemaExpr) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        xs     <- string.eval(input)
        result <- {
          val mapped = xs.map { x =>
            extractString(x).map(str => DynamicValue.Primitive(PrimitiveValue.Int(str.length)))
          }
          sequence(mapped)
        }
      } yield result
  }

  final case class StringSubstring(
    string: DynamicSchemaExpr,
    start: DynamicSchemaExpr,
    end: DynamicSchemaExpr
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        strings <- string.eval(input)
        starts  <- start.eval(input)
        ends    <- end.eval(input)
        results <- {
          val computed = for { s <- strings; st <- starts; en <- ends } yield {
            for {
              str      <- extractString(s)
              startInt <- extractInt(st)
              endInt   <- extractInt(en)
            } yield DynamicValue.Primitive(PrimitiveValue.String(str.substring(startInt, endInt)))
          }
          sequence(computed)
        }
      } yield results
  }

  final case class StringTrim(string: DynamicSchemaExpr) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        xs     <- string.eval(input)
        result <- {
          val mapped = xs.map { x =>
            extractString(x).map(str => DynamicValue.Primitive(PrimitiveValue.String(str.trim)))
          }
          sequence(mapped)
        }
      } yield result
  }

  final case class StringToUpperCase(string: DynamicSchemaExpr) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        xs     <- string.eval(input)
        result <- {
          val mapped = xs.map { x =>
            extractString(x).map(str => DynamicValue.Primitive(PrimitiveValue.String(str.toUpperCase)))
          }
          sequence(mapped)
        }
      } yield result
  }

  final case class StringToLowerCase(string: DynamicSchemaExpr) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        xs     <- string.eval(input)
        result <- {
          val mapped = xs.map { x =>
            extractString(x).map(str => DynamicValue.Primitive(PrimitiveValue.String(str.toLowerCase)))
          }
          sequence(mapped)
        }
      } yield result
  }

  final case class StringReplace(
    string: DynamicSchemaExpr,
    target: DynamicSchemaExpr,
    replacement: DynamicSchemaExpr
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        strings      <- string.eval(input)
        targets      <- target.eval(input)
        replacements <- replacement.eval(input)
        results      <- {
          val computed = for { s <- strings; t <- targets; r <- replacements } yield {
            for {
              str       <- extractString(s)
              targetStr <- extractString(t)
              replStr   <- extractString(r)
            } yield DynamicValue.Primitive(PrimitiveValue.String(str.replace(targetStr, replStr)))
          }
          sequence(computed)
        }
      } yield results
  }

  final case class StringStartsWith(
    string: DynamicSchemaExpr,
    prefix: DynamicSchemaExpr
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        strings  <- string.eval(input)
        prefixes <- prefix.eval(input)
        results  <- {
          val computed = for { s <- strings; p <- prefixes } yield {
            for {
              str       <- extractString(s)
              prefixStr <- extractString(p)
            } yield DynamicValue.Primitive(PrimitiveValue.Boolean(str.startsWith(prefixStr)))
          }
          sequence(computed)
        }
      } yield results
  }

  final case class StringEndsWith(
    string: DynamicSchemaExpr,
    suffix: DynamicSchemaExpr
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        strings  <- string.eval(input)
        suffixes <- suffix.eval(input)
        results  <- {
          val computed = for { s <- strings; sx <- suffixes } yield {
            for {
              str       <- extractString(s)
              suffixStr <- extractString(sx)
            } yield DynamicValue.Primitive(PrimitiveValue.Boolean(str.endsWith(suffixStr)))
          }
          sequence(computed)
        }
      } yield results
  }

  final case class StringContains(
    string: DynamicSchemaExpr,
    substring: DynamicSchemaExpr
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        strings    <- string.eval(input)
        substrings <- substring.eval(input)
        results    <- {
          val computed = for { s <- strings; sub <- substrings } yield {
            for {
              str    <- extractString(s)
              subStr <- extractString(sub)
            } yield DynamicValue.Primitive(PrimitiveValue.Boolean(str.contains(subStr)))
          }
          sequence(computed)
        }
      } yield results
  }

  final case class StringIndexOf(
    string: DynamicSchemaExpr,
    substring: DynamicSchemaExpr
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        strings    <- string.eval(input)
        substrings <- substring.eval(input)
        results    <- {
          val computed = for { s <- strings; sub <- substrings } yield {
            for {
              str    <- extractString(s)
              subStr <- extractString(sub)
            } yield DynamicValue.Primitive(PrimitiveValue.Int(str.indexOf(subStr)))
          }
          sequence(computed)
        }
      } yield results
  }
}
