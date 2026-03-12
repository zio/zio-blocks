/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, SchemaError}

sealed trait DynamicSchemaExpr {
  def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]]
}

object DynamicSchemaExpr {

  final case class Literal(value: DynamicValue) extends DynamicSchemaExpr {
    private[this] val result: Either[SchemaError, Seq[DynamicValue]] = Right(Chunk.single(value))

    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] = result
  }

  final case class Select(path: DynamicOptic) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      walkPath(path.nodes, 0, input) match {
        case Some(v) => Right(Chunk.single(v))
        case None    => Left(SchemaError.message(s"Path not found: ${path.toScalaString}"))
      }

    private def walkPath(
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int,
      value: DynamicValue
    ): Option[DynamicValue] =
      if (idx >= nodes.length) Some(value)
      else
        nodes(idx) match {
          case DynamicOptic.Node.Field(name) =>
            value match {
              case DynamicValue.Record(fields) =>
                fields.find(_._1 == name).flatMap(kv => walkPath(nodes, idx + 1, kv._2))
              case _ => None
            }
          case DynamicOptic.Node.Case(caseName) =>
            value match {
              case DynamicValue.Variant(name, inner) if name == caseName =>
                walkPath(nodes, idx + 1, inner)
              case _ => None
            }
          case DynamicOptic.Node.AtIndex(index) =>
            value match {
              case DynamicValue.Sequence(elements) if index >= 0 && index < elements.length =>
                walkPath(nodes, idx + 1, elements(index))
              case _ => None
            }
          case DynamicOptic.Node.Wrapped =>
            walkPath(nodes, idx + 1, value)
          case _ => None
        }
  }

  final case class PrimitiveConversion(conversionType: ConversionType) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      input match {
        case DynamicValue.Primitive(pv) =>
          conversionType.convert(pv) match {
            case Some(result) => Right(Chunk.single(DynamicValue.Primitive(result)))
            case None         =>
              Left(SchemaError.message(s"Cannot convert ${pv.getClass.getSimpleName} via ${conversionType}"))
          }
        case _ =>
          Left(
            SchemaError.message(s"PrimitiveConversion requires a Primitive value, got: ${input.getClass.getSimpleName}")
          )
      }
  }

  final case class StringConcat(left: DynamicSchemaExpr, right: DynamicSchemaExpr) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        l <- left.eval(input).flatMap(extractString)
        r <- right.eval(input).flatMap(extractString)
      } yield Chunk.single(DynamicValue.Primitive(PrimitiveValue.String(l + r)))
  }

  final case class StringLength(expr: DynamicSchemaExpr) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        s <- expr.eval(input).flatMap(extractString)
      } yield Chunk.single(DynamicValue.Primitive(PrimitiveValue.Int(s.length)))
  }

  final case class Arithmetic(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr,
    operator: ArithmeticOperator
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        lv     <- left.eval(input).flatMap(firstValue)
        rv     <- right.eval(input).flatMap(firstValue)
        result <- (lv, rv) match {
                    case (DynamicValue.Primitive(l), DynamicValue.Primitive(r)) =>
                      operator.apply(l, r) match {
                        case Some(res) => Right(Chunk.single(DynamicValue.Primitive(res)))
                        case None      =>
                          Left(
                            SchemaError.message(
                              s"Cannot apply $operator to ${l.getClass.getSimpleName} and ${r.getClass.getSimpleName}"
                            )
                          )
                      }
                    case _ => Left(SchemaError.message("Arithmetic requires Primitive values"))
                  }
      } yield result
  }

  final case class Relational(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr,
    operator: RelationalOperator
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        lv <- left.eval(input).flatMap(firstValue)
        rv <- right.eval(input).flatMap(firstValue)
      } yield {
        val cmp    = DynamicValue.ordering.compare(lv, rv)
        val result = operator.test(cmp)
        Chunk.single(DynamicValue.Primitive(PrimitiveValue.Boolean(result)))
      }
  }

  final case class Logical(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr,
    operator: LogicalOperator
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        l <- left.eval(input).flatMap(extractBoolean)
        r <- right.eval(input).flatMap(extractBoolean)
      } yield {
        val result = operator match {
          case LogicalOperator.And => l && r
          case LogicalOperator.Or  => l || r
        }
        Chunk.single(DynamicValue.Primitive(PrimitiveValue.Boolean(result)))
      }
  }

  final case class Not(expr: DynamicSchemaExpr) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        b <- expr.eval(input).flatMap(extractBoolean)
      } yield Chunk.single(DynamicValue.Primitive(PrimitiveValue.Boolean(!b)))
  }

  sealed trait ArithmeticOperator {
    def apply(left: PrimitiveValue, right: PrimitiveValue): Option[PrimitiveValue]
  }

  object ArithmeticOperator {
    case object Add extends ArithmeticOperator {
      def apply(left: PrimitiveValue, right: PrimitiveValue): Option[PrimitiveValue] =
        (left, right) match {
          case (PrimitiveValue.Int(l), PrimitiveValue.Int(r))               => Some(PrimitiveValue.Int(l + r))
          case (PrimitiveValue.Long(l), PrimitiveValue.Long(r))             => Some(PrimitiveValue.Long(l + r))
          case (PrimitiveValue.Float(l), PrimitiveValue.Float(r))           => Some(PrimitiveValue.Float(l + r))
          case (PrimitiveValue.Double(l), PrimitiveValue.Double(r))         => Some(PrimitiveValue.Double(l + r))
          case (PrimitiveValue.BigInt(l), PrimitiveValue.BigInt(r))         => Some(PrimitiveValue.BigInt(l + r))
          case (PrimitiveValue.BigDecimal(l), PrimitiveValue.BigDecimal(r)) => Some(PrimitiveValue.BigDecimal(l + r))
          case _                                                            => None
        }
    }

    case object Subtract extends ArithmeticOperator {
      def apply(left: PrimitiveValue, right: PrimitiveValue): Option[PrimitiveValue] =
        (left, right) match {
          case (PrimitiveValue.Int(l), PrimitiveValue.Int(r))               => Some(PrimitiveValue.Int(l - r))
          case (PrimitiveValue.Long(l), PrimitiveValue.Long(r))             => Some(PrimitiveValue.Long(l - r))
          case (PrimitiveValue.Float(l), PrimitiveValue.Float(r))           => Some(PrimitiveValue.Float(l - r))
          case (PrimitiveValue.Double(l), PrimitiveValue.Double(r))         => Some(PrimitiveValue.Double(l - r))
          case (PrimitiveValue.BigInt(l), PrimitiveValue.BigInt(r))         => Some(PrimitiveValue.BigInt(l - r))
          case (PrimitiveValue.BigDecimal(l), PrimitiveValue.BigDecimal(r)) => Some(PrimitiveValue.BigDecimal(l - r))
          case _                                                            => None
        }
    }

    case object Multiply extends ArithmeticOperator {
      def apply(left: PrimitiveValue, right: PrimitiveValue): Option[PrimitiveValue] =
        (left, right) match {
          case (PrimitiveValue.Int(l), PrimitiveValue.Int(r))               => Some(PrimitiveValue.Int(l * r))
          case (PrimitiveValue.Long(l), PrimitiveValue.Long(r))             => Some(PrimitiveValue.Long(l * r))
          case (PrimitiveValue.Float(l), PrimitiveValue.Float(r))           => Some(PrimitiveValue.Float(l * r))
          case (PrimitiveValue.Double(l), PrimitiveValue.Double(r))         => Some(PrimitiveValue.Double(l * r))
          case (PrimitiveValue.BigInt(l), PrimitiveValue.BigInt(r))         => Some(PrimitiveValue.BigInt(l * r))
          case (PrimitiveValue.BigDecimal(l), PrimitiveValue.BigDecimal(r)) => Some(PrimitiveValue.BigDecimal(l * r))
          case _                                                            => None
        }
    }
  }

  sealed trait RelationalOperator {
    def test(comparison: Int): Boolean
  }

  object RelationalOperator {
    case object LessThan extends RelationalOperator {
      def test(comparison: Int): Boolean = comparison < 0
    }
    case object LessThanOrEqual extends RelationalOperator {
      def test(comparison: Int): Boolean = comparison <= 0
    }
    case object GreaterThan extends RelationalOperator {
      def test(comparison: Int): Boolean = comparison > 0
    }
    case object GreaterThanOrEqual extends RelationalOperator {
      def test(comparison: Int): Boolean = comparison >= 0
    }
    case object Equal extends RelationalOperator {
      def test(comparison: Int): Boolean = comparison == 0
    }
    case object NotEqual extends RelationalOperator {
      def test(comparison: Int): Boolean = comparison != 0
    }
  }

  sealed trait LogicalOperator
  object LogicalOperator {
    case object And extends LogicalOperator
    case object Or  extends LogicalOperator
  }

  sealed trait ConversionType {
    def convert(value: PrimitiveValue): Option[PrimitiveValue]
  }

  object ConversionType {
    case object ByteToShort extends ConversionType {
      def convert(v: PrimitiveValue): Option[PrimitiveValue] = v match {
        case PrimitiveValue.Byte(b) => Some(PrimitiveValue.Short(b.toShort))
        case _                      => None
      }
    }
    case object ByteToInt extends ConversionType {
      def convert(v: PrimitiveValue): Option[PrimitiveValue] = v match {
        case PrimitiveValue.Byte(b) => Some(PrimitiveValue.Int(b.toInt))
        case _                      => None
      }
    }
    case object ByteToLong extends ConversionType {
      def convert(v: PrimitiveValue): Option[PrimitiveValue] = v match {
        case PrimitiveValue.Byte(b) => Some(PrimitiveValue.Long(b.toLong))
        case _                      => None
      }
    }
    case object ShortToInt extends ConversionType {
      def convert(v: PrimitiveValue): Option[PrimitiveValue] = v match {
        case PrimitiveValue.Short(s) => Some(PrimitiveValue.Int(s.toInt))
        case _                       => None
      }
    }
    case object ShortToLong extends ConversionType {
      def convert(v: PrimitiveValue): Option[PrimitiveValue] = v match {
        case PrimitiveValue.Short(s) => Some(PrimitiveValue.Long(s.toLong))
        case _                       => None
      }
    }
    case object IntToLong extends ConversionType {
      def convert(v: PrimitiveValue): Option[PrimitiveValue] = v match {
        case PrimitiveValue.Int(i) => Some(PrimitiveValue.Long(i.toLong))
        case _                     => None
      }
    }
    case object IntToFloat extends ConversionType {
      def convert(v: PrimitiveValue): Option[PrimitiveValue] = v match {
        case PrimitiveValue.Int(i) => Some(PrimitiveValue.Float(i.toFloat))
        case _                     => None
      }
    }
    case object IntToDouble extends ConversionType {
      def convert(v: PrimitiveValue): Option[PrimitiveValue] = v match {
        case PrimitiveValue.Int(i) => Some(PrimitiveValue.Double(i.toDouble))
        case _                     => None
      }
    }
    case object LongToFloat extends ConversionType {
      def convert(v: PrimitiveValue): Option[PrimitiveValue] = v match {
        case PrimitiveValue.Long(l) => Some(PrimitiveValue.Float(l.toFloat))
        case _                      => None
      }
    }
    case object LongToDouble extends ConversionType {
      def convert(v: PrimitiveValue): Option[PrimitiveValue] = v match {
        case PrimitiveValue.Long(l) => Some(PrimitiveValue.Double(l.toDouble))
        case _                      => None
      }
    }
    case object FloatToDouble extends ConversionType {
      def convert(v: PrimitiveValue): Option[PrimitiveValue] = v match {
        case PrimitiveValue.Float(f) => Some(PrimitiveValue.Double(f.toDouble))
        case _                       => None
      }
    }
    case object IntToString extends ConversionType {
      def convert(v: PrimitiveValue): Option[PrimitiveValue] = v match {
        case PrimitiveValue.Int(i) => Some(PrimitiveValue.String(i.toString))
        case _                     => None
      }
    }
    case object LongToString extends ConversionType {
      def convert(v: PrimitiveValue): Option[PrimitiveValue] = v match {
        case PrimitiveValue.Long(l) => Some(PrimitiveValue.String(l.toString))
        case _                      => None
      }
    }
    case object DoubleToString extends ConversionType {
      def convert(v: PrimitiveValue): Option[PrimitiveValue] = v match {
        case PrimitiveValue.Double(d) => Some(PrimitiveValue.String(d.toString))
        case _                        => None
      }
    }
    case object StringToInt extends ConversionType {
      def convert(v: PrimitiveValue): Option[PrimitiveValue] = v match {
        case PrimitiveValue.String(s) =>
          try Some(PrimitiveValue.Int(s.toInt))
          catch { case _: NumberFormatException => None }
        case _ => None
      }
    }
    case object StringToLong extends ConversionType {
      def convert(v: PrimitiveValue): Option[PrimitiveValue] = v match {
        case PrimitiveValue.String(s) =>
          try Some(PrimitiveValue.Long(s.toLong))
          catch { case _: NumberFormatException => None }
        case _ => None
      }
    }
    case object StringToDouble extends ConversionType {
      def convert(v: PrimitiveValue): Option[PrimitiveValue] = v match {
        case PrimitiveValue.String(s) =>
          try Some(PrimitiveValue.Double(s.toDouble))
          catch { case _: NumberFormatException => None }
        case _ => None
      }
    }
    case object BooleanToString extends ConversionType {
      def convert(v: PrimitiveValue): Option[PrimitiveValue] = v match {
        case PrimitiveValue.Boolean(b) => Some(PrimitiveValue.String(b.toString))
        case _                         => None
      }
    }
    case object StringToBoolean extends ConversionType {
      def convert(v: PrimitiveValue): Option[PrimitiveValue] = v match {
        case PrimitiveValue.String("true")  => Some(PrimitiveValue.Boolean(true))
        case PrimitiveValue.String("false") => Some(PrimitiveValue.Boolean(false))
        case _                              => None
      }
    }
  }

  private def firstValue(values: Seq[DynamicValue]): Either[SchemaError, DynamicValue] =
    values.headOption match {
      case Some(v) => Right(v)
      case None    => Left(SchemaError.message("Expression returned no values"))
    }

  private def extractString(values: Seq[DynamicValue]): Either[SchemaError, String] =
    values.headOption match {
      case Some(DynamicValue.Primitive(PrimitiveValue.String(s))) => Right(s)
      case Some(other)                                            => Left(SchemaError.message(s"Expected String, got: ${other.getClass.getSimpleName}"))
      case None                                                   => Left(SchemaError.message("Expression returned no values"))
    }

  private def extractBoolean(values: Seq[DynamicValue]): Either[SchemaError, Boolean] =
    values.headOption match {
      case Some(DynamicValue.Primitive(PrimitiveValue.Boolean(b))) => Right(b)
      case Some(other)                                             => Left(SchemaError.message(s"Expected Boolean, got: ${other.getClass.getSimpleName}"))
      case None                                                    => Left(SchemaError.message("Expression returned no values"))
    }
}
