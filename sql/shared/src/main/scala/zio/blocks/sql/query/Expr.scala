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

package zio.blocks.sql.query

import zio.blocks.schema.{DynamicSchemaExpr, DynamicValue, IsNumeric, PrimitiveValue, Schema}
import zio.blocks.sql.{DbValue, Table}

/**
 * Typed expression tree backed by DynamicSchemaExpr for native cases.
 *
 * IN and aggregate are honest SQL-only extensions (DynamicSchemaExpr has no
 * IN/aggregate cases). Native predicate/logical/arithmetic/string cases carry
 * DynamicSchemaExpr and render via the single shared renderDynamic interpreter
 * adapted from docs/guides/query-dsl-sql.md.
 */
sealed trait Expr[A]

final case class Column[A, B](table: Table[A], column: String, alias: Option[String], dynamic: DynamicSchemaExpr)
    extends Expr[B]

final case class Lit[A](value: A, schema: Schema[A], dynamic: DynamicSchemaExpr) extends Expr[A]

final case class Relational[A](
  left: Expr[A],
  right: Expr[A],
  operator: DynamicSchemaExpr.RelationalOperator,
  dynamic: Option[DynamicSchemaExpr]
) extends Expr[Boolean]

final case class Logical(
  left: Expr[Boolean],
  right: Expr[Boolean],
  operator: DynamicSchemaExpr.LogicalOperator,
  dynamic: Option[DynamicSchemaExpr]
) extends Expr[Boolean]

final case class NotExpr(expr: Expr[Boolean], dynamic: Option[DynamicSchemaExpr]) extends Expr[Boolean]

final case class Arithmetic[A](
  left: Expr[A],
  right: Expr[A],
  operator: DynamicSchemaExpr.ArithmeticOperator,
  tag: DynamicSchemaExpr.NumericTypeTag,
  dynamic: Option[DynamicSchemaExpr]
) extends Expr[A]

final case class LikeExpr(left: Expr[String], pattern: Expr[String], dynamic: DynamicSchemaExpr) extends Expr[Boolean]

// SQL-only v1 extensions — honest extension nodes, not native DynamicSchemaExpr. They recursively contain native Exprs.
// IN is SQL's `col IN (?,?,?)`; aggregate is SQL's `COUNT(col)` etc. DynamicSchemaExpr has no IN/aggregate cases, so these remain
// explicit extension nodes. This is acceptable because native predicate/logical/arithmetic/string cases all render through the single
// shared renderDynamic interpreter; only IN/aggregate are extensions.

final case class InExpr[A](col: Expr[A], values: Seq[A], schema: Schema[A]) extends Expr[Boolean]

sealed trait AggFunc
object AggFunc {
  case object Count     extends AggFunc
  case object Sum       extends AggFunc
  case object Avg       extends AggFunc
  case object Min       extends AggFunc
  case object Max       extends AggFunc
  case object CountStar extends AggFunc
}

final case class Agg[A](func: AggFunc, arg: Expr[_]) extends Expr[A]
final case class AggStar(func: AggFunc)              extends Expr[Long]

final case class OptExpr[A](inner: Expr[A]) extends Expr[Option[A]]

private[query] object ExprInternal {
  def dynamicValueToDbValue(dv: DynamicValue): DbValue = dv match {
    case DynamicValue.Primitive(pv) =>
      pv match {
        case PrimitiveValue.String(s)        => DbValue.DbString(s)
        case PrimitiveValue.Boolean(b)       => DbValue.DbBoolean(b)
        case PrimitiveValue.Int(n)           => DbValue.DbInt(n)
        case PrimitiveValue.Long(n)          => DbValue.DbLong(n)
        case PrimitiveValue.Double(n)        => DbValue.DbDouble(n)
        case PrimitiveValue.Float(n)         => DbValue.DbFloat(n)
        case PrimitiveValue.Short(n)         => DbValue.DbShort(n)
        case PrimitiveValue.Byte(n)          => DbValue.DbByte(n)
        case PrimitiveValue.Char(c)          => DbValue.DbChar(c)
        case PrimitiveValue.BigDecimal(n)    => DbValue.DbBigDecimal(n)
        case PrimitiveValue.BigInt(n)        => DbValue.DbBigDecimal(BigDecimal(n))
        case PrimitiveValue.Unit             => throw new IllegalArgumentException("unsupported literal type: Unit")
        case PrimitiveValue.Duration(n)      => DbValue.DbDuration(n)
        case PrimitiveValue.Instant(n)       => DbValue.DbInstant(n)
        case PrimitiveValue.LocalDate(n)     => DbValue.DbLocalDate(n)
        case PrimitiveValue.LocalDateTime(n) => DbValue.DbLocalDateTime(n)
        case PrimitiveValue.LocalTime(n)     => DbValue.DbLocalTime(n)
        case PrimitiveValue.UUID(n)          => DbValue.DbUUID(n)
        case _                               => throw new IllegalArgumentException(s"unsupported literal PrimitiveValue: $pv")
      }
    case other => throw new IllegalArgumentException(s"unsupported literal DynamicValue: $other")
  }

  def numericTag[A](using IsNumeric[A]): DynamicSchemaExpr.NumericTypeTag =
    summon[IsNumeric[A]].numericPrimitiveType match {
      case zio.blocks.schema.PrimitiveType.Byte(_)       => DynamicSchemaExpr.NumericTypeTag.ByteTag
      case zio.blocks.schema.PrimitiveType.Short(_)      => DynamicSchemaExpr.NumericTypeTag.ShortTag
      case zio.blocks.schema.PrimitiveType.Int(_)        => DynamicSchemaExpr.NumericTypeTag.IntTag
      case zio.blocks.schema.PrimitiveType.Long(_)       => DynamicSchemaExpr.NumericTypeTag.LongTag
      case zio.blocks.schema.PrimitiveType.Float(_)      => DynamicSchemaExpr.NumericTypeTag.FloatTag
      case zio.blocks.schema.PrimitiveType.Double(_)     => DynamicSchemaExpr.NumericTypeTag.DoubleTag
      case zio.blocks.schema.PrimitiveType.BigDecimal(_) => DynamicSchemaExpr.NumericTypeTag.BigDecimalTag
      case zio.blocks.schema.PrimitiveType.BigInt(_)     => DynamicSchemaExpr.NumericTypeTag.BigIntTag
      case other                                         => throw new IllegalArgumentException(s"unsupported numeric type for arithmetic: $other")
    }
}

// Column builders preserving field type B

transparent inline def col[A]: ColumnBuilder[A] = new ColumnBuilder[A]

final class ColumnBuilder[A] {
  @scala.annotation.nowarn
  transparent inline def apply[B](inline selector: A => B)(using Schema[A]): Expr[B] =
    ${ ExprMacros.colImpl[A, B]('selector) }
}

transparent inline def colAt[A]: ColumnAtBuilder[A] = new ColumnAtBuilder[A]

final class ColumnAtBuilder[A] {
  @scala.annotation.nowarn
  transparent inline def apply[B](alias: String, inline selector: A => B)(using Schema[A]): Expr[B] =
    ${ ExprMacros.colAtImpl[A, B]('alias, 'selector) }
}

// Nullable wrapper for LEFT JOIN — renders as underlying column but decodes via Option codec
extension [A](expr: Expr[A]) {
  def asOption: Expr[Option[A]] = OptExpr(expr)
  def opt: Expr[Option[A]]      = asOption
}

// Literal helper — requires Schema, rejects unsupported types at runtime via exhaustive mapping

def lit[A](value: A)(using schema: Schema[A]): Expr[A] = {
  val dv = schema.toDynamicValue(value)
  // Validate immediately so unsupported types like List fail fast
  ExprInternal.dynamicValueToDbValue(dv)
  val dynamic = DynamicSchemaExpr.Literal(dv, schema)
  Lit(value, schema, dynamic)
}

// Aggregate constructors — countStar is COUNT(*)
//
// AVG cross-dialect choice: SQLite and PostgreSQL both promote AVG to floating
// point (SQLite returns REAL/double, PostgreSQL returns numeric/double precision).
// To avoid silent truncation of integral averages (e.g. AVG(10,11)=10.5 truncated
// to 10 when decoded as Int), avg always returns Expr[Double] regardless of
// input numeric type. This is truthful for both dialects and matches PostgreSQL's
// AVG(int) -> numeric promotion semantics.

def count[A](col: Expr[A]): Expr[Long] = Agg(AggFunc.Count, col)
val countStar: Expr[Long]              = AggStar(AggFunc.CountStar)
@scala.annotation.nowarn
def sum[A: IsNumeric](col: Expr[A]): Expr[A] = Agg(AggFunc.Sum, col)
@scala.annotation.nowarn
def avg[A: IsNumeric](col: Expr[A]): Expr[Double] = Agg(AggFunc.Avg, col)
@scala.annotation.nowarn
def min[A: Ordering](col: Expr[A]): Expr[A] = Agg(AggFunc.Min, col)
@scala.annotation.nowarn
def max[A: Ordering](col: Expr[A]): Expr[A] = Agg(AggFunc.Max, col)

def not(expr: Expr[Boolean]): Expr[Boolean] = {
  val dynOpt = toDynamicOpt(expr).map(DynamicSchemaExpr.Not(_))
  NotExpr(expr, dynOpt)
}

private def toDynamicOpt[A](expr: Expr[A]): Option[DynamicSchemaExpr] = expr match {
  case c: Column[_, _]  => Some(c.dynamic)
  case l: Lit[_]        => Some(l.dynamic)
  case r: Relational[_] => r.dynamic
  case l: Logical       => l.dynamic
  case n: NotExpr       => n.dynamic
  case a: Arithmetic[_] => a.dynamic
  case l: LikeExpr      => Some(l.dynamic)
  case _: InExpr[_]     => None
  case _: Agg[_]        => None
  case _: AggStar       => None
  case o: OptExpr[_]    => toDynamicOpt(o.inner)
}

private def toDynamic[A](expr: Expr[A]): DynamicSchemaExpr =
  toDynamicOpt(expr).getOrElse(throw new IllegalArgumentException(s"Expr has no native DynamicSchemaExpr: $expr"))

// Extensions for comparison, boolean, string, in, arithmetic

extension [A](left: Expr[A]) {

  def equalTo(right: Expr[A]): Expr[Boolean] = {
    val dynOpt = for {
      l <- toDynamicOpt(left)
      r <- toDynamicOpt(right)
    } yield DynamicSchemaExpr.Relational(l, r, DynamicSchemaExpr.RelationalOperator.Equal)
    Relational(left, right, DynamicSchemaExpr.RelationalOperator.Equal, dynOpt)
  }
  def ===(right: Expr[A]): Expr[Boolean] = equalTo(right)

  def notEqualTo(right: Expr[A]): Expr[Boolean] = {
    val dynOpt = for {
      l <- toDynamicOpt(left)
      r <- toDynamicOpt(right)
    } yield DynamicSchemaExpr.Relational(l, r, DynamicSchemaExpr.RelationalOperator.NotEqual)
    Relational(left, right, DynamicSchemaExpr.RelationalOperator.NotEqual, dynOpt)
  }
  def =!=(right: Expr[A]): Expr[Boolean] = notEqualTo(right)

  @scala.annotation.nowarn
  def greaterThan(right: Expr[A])(using Ordering[A], scala.util.NotGiven[A =:= Boolean]): Expr[Boolean] = {
    summon[Ordering[A]]
    summon[scala.util.NotGiven[A =:= Boolean]]
    val dynOpt = for {
      l <- toDynamicOpt(left)
      r <- toDynamicOpt(right)
    } yield DynamicSchemaExpr.Relational(l, r, DynamicSchemaExpr.RelationalOperator.GreaterThan)
    Relational(left, right, DynamicSchemaExpr.RelationalOperator.GreaterThan, dynOpt)
  }
  def >(right: Expr[A])(using Ordering[A], scala.util.NotGiven[A =:= Boolean]): Expr[Boolean] = greaterThan(right)

  @scala.annotation.nowarn
  def lessThan(right: Expr[A])(using Ordering[A], scala.util.NotGiven[A =:= Boolean]): Expr[Boolean] = {
    summon[Ordering[A]]
    summon[scala.util.NotGiven[A =:= Boolean]]
    val dynOpt = for {
      l <- toDynamicOpt(left)
      r <- toDynamicOpt(right)
    } yield DynamicSchemaExpr.Relational(l, r, DynamicSchemaExpr.RelationalOperator.LessThan)
    Relational(left, right, DynamicSchemaExpr.RelationalOperator.LessThan, dynOpt)
  }
  def <(right: Expr[A])(using Ordering[A], scala.util.NotGiven[A =:= Boolean]): Expr[Boolean] = lessThan(right)

  @scala.annotation.nowarn
  def greaterThanOrEqualTo(right: Expr[A])(using Ordering[A], scala.util.NotGiven[A =:= Boolean]): Expr[Boolean] = {
    summon[Ordering[A]]
    summon[scala.util.NotGiven[A =:= Boolean]]
    val dynOpt = for {
      l <- toDynamicOpt(left)
      r <- toDynamicOpt(right)
    } yield DynamicSchemaExpr.Relational(l, r, DynamicSchemaExpr.RelationalOperator.GreaterThanOrEqual)
    Relational(left, right, DynamicSchemaExpr.RelationalOperator.GreaterThanOrEqual, dynOpt)
  }
  def >=(right: Expr[A])(using Ordering[A], scala.util.NotGiven[A =:= Boolean]): Expr[Boolean] = greaterThanOrEqualTo(
    right
  )

  @scala.annotation.nowarn
  def lessThanOrEqualTo(right: Expr[A])(using Ordering[A], scala.util.NotGiven[A =:= Boolean]): Expr[Boolean] = {
    summon[Ordering[A]]
    summon[scala.util.NotGiven[A =:= Boolean]]
    val dynOpt = for {
      l <- toDynamicOpt(left)
      r <- toDynamicOpt(right)
    } yield DynamicSchemaExpr.Relational(l, r, DynamicSchemaExpr.RelationalOperator.LessThanOrEqual)
    Relational(left, right, DynamicSchemaExpr.RelationalOperator.LessThanOrEqual, dynOpt)
  }
  def <=(right: Expr[A])(using Ordering[A], scala.util.NotGiven[A =:= Boolean]): Expr[Boolean] = lessThanOrEqualTo(
    right
  )

  def in(values: Seq[A])(using schema: Schema[A]): Expr[Boolean] =
    InExpr(left, values, schema)

  def plus(right: Expr[A])(using IsNumeric[A]): Expr[A] = {
    val tag    = ExprInternal.numericTag[A]
    val dynOpt = for {
      l <- toDynamicOpt(left)
      r <- toDynamicOpt(right)
    } yield DynamicSchemaExpr.Arithmetic(l, r, DynamicSchemaExpr.ArithmeticOperator.Add, tag)
    Arithmetic(left, right, DynamicSchemaExpr.ArithmeticOperator.Add, tag, dynOpt)
  }
  def +(right: Expr[A])(using IsNumeric[A]): Expr[A] = plus(right)

  def minus(right: Expr[A])(using IsNumeric[A]): Expr[A] = {
    val tag    = ExprInternal.numericTag[A]
    val dynOpt = for {
      l <- toDynamicOpt(left)
      r <- toDynamicOpt(right)
    } yield DynamicSchemaExpr.Arithmetic(l, r, DynamicSchemaExpr.ArithmeticOperator.Subtract, tag)
    Arithmetic(left, right, DynamicSchemaExpr.ArithmeticOperator.Subtract, tag, dynOpt)
  }
  def -(right: Expr[A])(using IsNumeric[A]): Expr[A] = minus(right)

  def times(right: Expr[A])(using IsNumeric[A]): Expr[A] = {
    val tag    = ExprInternal.numericTag[A]
    val dynOpt = for {
      l <- toDynamicOpt(left)
      r <- toDynamicOpt(right)
    } yield DynamicSchemaExpr.Arithmetic(l, r, DynamicSchemaExpr.ArithmeticOperator.Multiply, tag)
    Arithmetic(left, right, DynamicSchemaExpr.ArithmeticOperator.Multiply, tag, dynOpt)
  }
  def *(right: Expr[A])(using IsNumeric[A]): Expr[A] = times(right)
}

extension (left: Expr[Boolean]) {
  def and(right: Expr[Boolean]): Expr[Boolean] = {
    val dynOpt = for {
      l <- toDynamicOpt(left)
      r <- toDynamicOpt(right)
    } yield DynamicSchemaExpr.Logical(l, r, DynamicSchemaExpr.LogicalOperator.And)
    Logical(left, right, DynamicSchemaExpr.LogicalOperator.And, dynOpt)
  }
  def &&(right: Expr[Boolean]): Expr[Boolean] = and(right)

  def or(right: Expr[Boolean]): Expr[Boolean] = {
    val dynOpt = for {
      l <- toDynamicOpt(left)
      r <- toDynamicOpt(right)
    } yield DynamicSchemaExpr.Logical(l, r, DynamicSchemaExpr.LogicalOperator.Or)
    Logical(left, right, DynamicSchemaExpr.LogicalOperator.Or, dynOpt)
  }
  def ||(right: Expr[Boolean]): Expr[Boolean] = or(right)

  def unary_! : Expr[Boolean] = not(left)
}

extension (left: Expr[String]) {
  def like(pattern: Expr[String]): Expr[Boolean] = {
    val dyn = DynamicSchemaExpr.StringRegexMatch(toDynamic(pattern), toDynamic(left))
    LikeExpr(left, pattern, dyn)
  }
  def like(pattern: String): Expr[Boolean] = {
    val patExpr = lit(pattern)
    val dyn     = DynamicSchemaExpr.StringRegexMatch(toDynamic(patExpr), toDynamic(left))
    LikeExpr(left, patExpr, dyn)
  }
}
