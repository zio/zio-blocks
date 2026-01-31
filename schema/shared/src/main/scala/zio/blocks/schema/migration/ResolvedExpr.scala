/*
 * Copyright 2024 John A. De Goes and the ZIO Contributors
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
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue}

/**
 * A pure, serializable expression for value-level transformations.
 *
 * ResolvedExpr represents computations without closures or functions, making
 * migrations fully serializable and introspectable.
 *
 * Constraints for this implementation:
 *   - Primitive to primitive conversions only
 *   - No record/enum construction (out of scope)
 *   - Joins/splits must produce primitives
 */
sealed trait ResolvedExpr {

  /**
   * Evaluate this expression against a source DynamicValue.
   */
  def eval(source: DynamicValue, path: DynamicOptic): Either[MigrationError, DynamicValue]

  /**
   * Returns a best-effort reverse of this expression. Not all expressions are
   * reversible.
   */
  def reverse: Option[ResolvedExpr]
}

object ResolvedExpr {

  /**
   * A literal constant value.
   */
  final case class Literal(value: DynamicValue) extends ResolvedExpr {
    def eval(source: DynamicValue, path: DynamicOptic): Either[MigrationError, DynamicValue] =
      Right(value)

    def reverse: Option[ResolvedExpr] = Some(this)
  }

  /**
   * Identity - returns the input unchanged.
   */
  case object Identity extends ResolvedExpr {
    def eval(source: DynamicValue, path: DynamicOptic): Either[MigrationError, DynamicValue] =
      Right(source)

    def reverse: Option[ResolvedExpr] = Some(Identity)
  }

  /**
   * Access a field from a record.
   */
  final case class FieldAccess(fieldName: String) extends ResolvedExpr {
    def eval(source: DynamicValue, path: DynamicOptic): Either[MigrationError, DynamicValue] =
      source match {
        case DynamicValue.Record(fields) =>
          fields.find(_._1 == fieldName) match {
            case Some((_, value)) => Right(value)
            case None             => Left(MigrationError.missingField(path, fieldName))
          }
        case other =>
          Left(MigrationError.unexpectedStructure(path, "Record", other.getClass.getSimpleName))
      }

    def reverse: Option[ResolvedExpr] = None // Field access is not reversible
  }

  /**
   * Access a nested path using DynamicOptic.
   */
  final case class PathAccess(optic: DynamicOptic) extends ResolvedExpr {
    def eval(source: DynamicValue, path: DynamicOptic): Either[MigrationError, DynamicValue] =
      source.get(optic).one match {
        case Right(value) => Right(value)
        case Left(_)      => Left(MigrationError.unexpectedStructure(path, s"value at ${optic}", "nothing"))
      }

    def reverse: Option[ResolvedExpr] = None
  }

  /**
   * Convert between primitive types.
   */
  final case class Convert(fromType: String, toType: String) extends ResolvedExpr {
    def eval(source: DynamicValue, path: DynamicOptic): Either[MigrationError, DynamicValue] =
      PrimitiveConversions.convert(source, fromType, toType) match {
        case Right(value) => Right(value)
        case Left(reason) => Left(MigrationError.conversionFailed(path, fromType, toType, reason))
      }

    def reverse: Option[ResolvedExpr] = Some(Convert(toType, fromType))
  }

  /**
   * Concatenate string values.
   */
  final case class Concat(exprs: Vector[ResolvedExpr], separator: String) extends ResolvedExpr {
    def eval(source: DynamicValue, path: DynamicOptic): Either[MigrationError, DynamicValue] = {
      val results = exprs.map(_.eval(source, path))
      val errors  = results.collect { case Left(e) => e }
      if (errors.nonEmpty) {
        Left(errors.head)
      } else {
        val strings = results.collect { case Right(v) => extractString(v) }
        Right(DynamicValue.Primitive(PrimitiveValue.String(strings.mkString(separator))))
      }
    }

    def reverse: Option[ResolvedExpr] = None // Concat is not reversible

    private def extractString(value: DynamicValue): String = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s))  => s
      case DynamicValue.Primitive(PrimitiveValue.Int(i))     => i.toString
      case DynamicValue.Primitive(PrimitiveValue.Long(l))    => l.toString
      case DynamicValue.Primitive(PrimitiveValue.Double(d))  => d.toString
      case DynamicValue.Primitive(PrimitiveValue.Float(f))   => f.toString
      case DynamicValue.Primitive(PrimitiveValue.Boolean(b)) => b.toString
      case DynamicValue.Primitive(PrimitiveValue.Char(c))    => c.toString
      case DynamicValue.Primitive(PrimitiveValue.Short(s))   => s.toString
      case DynamicValue.Primitive(PrimitiveValue.Byte(b))    => b.toString
      case DynamicValue.Primitive(pv)                        => pv.toString
      case other                                             => other.toString
    }
  }

  /**
   * Use the schema's default value for a type.
   */
  case object DefaultValue extends ResolvedExpr {
    def eval(source: DynamicValue, path: DynamicOptic): Either[MigrationError, DynamicValue] =
      Left(MigrationError.expressionFailed(path, "DefaultValue", "DefaultValue requires schema context"))

    def reverse: Option[ResolvedExpr] = Some(DefaultValue)
  }

  /**
   * Conditional expression.
   */
  final case class IfThenElse(condition: ResolvedExpr, thenExpr: ResolvedExpr, elseExpr: ResolvedExpr)
      extends ResolvedExpr {
    def eval(source: DynamicValue, path: DynamicOptic): Either[MigrationError, DynamicValue] =
      condition.eval(source, path).flatMap { condValue =>
        val isTruthy = condValue match {
          case DynamicValue.Primitive(PrimitiveValue.Boolean(b)) => b
          case DynamicValue.Null                                 => false
          case _                                                 => true
        }
        if (isTruthy) thenExpr.eval(source, path)
        else elseExpr.eval(source, path)
      }

    def reverse: Option[ResolvedExpr] = for {
      thenRev <- thenExpr.reverse
      elseRev <- elseExpr.reverse
    } yield IfThenElse(condition, thenRev, elseRev)
  }

  /**
   * Wrap value in Some (for Option fields).
   */
  final case class WrapSome(inner: ResolvedExpr) extends ResolvedExpr {
    def eval(source: DynamicValue, path: DynamicOptic): Either[MigrationError, DynamicValue] =
      inner.eval(source, path).map { value =>
        DynamicValue.Variant("Some", DynamicValue.Record(Chunk(("value", value))))
      }

    def reverse: Option[ResolvedExpr] = inner.reverse.map(UnwrapSome.apply)
  }

  /**
   * Unwrap value from Some (for Option fields).
   */
  final case class UnwrapSome(inner: ResolvedExpr) extends ResolvedExpr {
    def eval(source: DynamicValue, path: DynamicOptic): Either[MigrationError, DynamicValue] =
      inner.eval(source, path).flatMap {
        case DynamicValue.Variant("Some", DynamicValue.Record(fields)) =>
          fields.find(_._1 == "value").map(_._2) match {
            case Some(v) => Right(v)
            case None    => Left(MigrationError.unexpectedStructure(path, "Some(value)", "Some without value field"))
          }
        case DynamicValue.Null =>
          Left(MigrationError.unexpectedStructure(path, "Some", "None/Null"))
        case other =>
          Left(MigrationError.unexpectedStructure(path, "Some", other.getClass.getSimpleName))
      }

    def reverse: Option[ResolvedExpr] = inner.reverse.map(WrapSome.apply)
  }

  /**
   * Get None value.
   */
  case object GetNone extends ResolvedExpr {
    def eval(source: DynamicValue, path: DynamicOptic): Either[MigrationError, DynamicValue] =
      Right(DynamicValue.Variant("None", DynamicValue.Record(Chunk.empty)))

    def reverse: Option[ResolvedExpr] = None
  }

  // Smart constructors
  def literal[A](value: A)(implicit toDV: A => DynamicValue): ResolvedExpr =
    Literal(toDV(value))

  def literalDynamic(value: DynamicValue): ResolvedExpr =
    Literal(value)

  def identity: ResolvedExpr = Identity

  def field(name: String): ResolvedExpr = FieldAccess(name)

  def path(optic: DynamicOptic): ResolvedExpr = PathAccess(optic)

  def convert(fromType: String, toType: String): ResolvedExpr = Convert(fromType, toType)

  def concat(exprs: ResolvedExpr*): ResolvedExpr = Concat(exprs.toVector, "")

  def concatWith(separator: String)(exprs: ResolvedExpr*): ResolvedExpr = Concat(exprs.toVector, separator)

  def defaultValue: ResolvedExpr = DefaultValue

  def ifThenElse(condition: ResolvedExpr, thenExpr: ResolvedExpr, elseExpr: ResolvedExpr): ResolvedExpr =
    IfThenElse(condition, thenExpr, elseExpr)

  def wrapSome(inner: ResolvedExpr): ResolvedExpr = WrapSome(inner)

  def unwrapSome(inner: ResolvedExpr): ResolvedExpr = UnwrapSome(inner)

  def none: ResolvedExpr = GetNone
}
