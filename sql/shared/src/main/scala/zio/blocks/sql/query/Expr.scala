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
 * The second type parameter `Sc` is the query-bound scope: expressions built
 * from `SqlQuery.col`/`colAt` carry the singleton scope of the query value they
 * were built from, and literals are scope-neutral (`Nothing`). Query methods
 * (`where`/`select`/`groupBy`/`having` and the aggregate constructors) require
 * expressions whose scope matches the receiver, so columns from different query
 * values cannot be mixed.
 *
 * Combinators are declared on the trait with a bounded type parameter
 * `[Sc2 <: Sc]`: the receiver's scope is fixed by its static type (never
 * inferred, so it is never widened), while the right operand may be a
 * scope-neutral literal (`Nothing <: Sc`) or another expression of the same
 * query lineage. This keeps literals usable while rejecting cross-query
 * combinations.
 *
 * IN and aggregate are honest SQL-only extensions (DynamicSchemaExpr has no
 * IN/aggregate cases). Native predicate/logical/arithmetic/string cases carry
 * DynamicSchemaExpr and render via the single shared renderDynamic interpreter
 * adapted from docs/guides/query-dsl-sql.md.
 */
sealed trait Expr[A, Sc] { self =>

  /**
   * Relational comparison of this expression against another of the same value
   * type.
   */
  def ===[Sc2 <: Sc](right: Expr[A, Sc2]): Expr[Boolean, Sc] = {
    val dynOpt = for {
      l <- toDynamicOpt(self)
      r <- toDynamicOpt(right.asInstanceOf[Expr[A, Sc]])
    } yield DynamicSchemaExpr.Relational(l, r, DynamicSchemaExpr.RelationalOperator.Equal)
    Relational[A, Sc](self, right.asInstanceOf[Expr[A, Sc]], DynamicSchemaExpr.RelationalOperator.Equal, dynOpt)
  }

  /**
   * Option-aware comparison: `Expr[Option[A]]` compared to a plain `Expr[A]`
   * (LEFT JOIN columns, aggregates).
   */
  @scala.annotation.targetName("eqOptLeft")
  def ===[Sc2 <: Sc, B](right: Expr[B, Sc2])(using ev: A =:= Option[B]): Expr[Boolean, Sc] =
    Relational[A, Sc](
      self,
      right.asInstanceOf[Expr[B, Sc]].asInstanceOf[Expr[A, Sc]],
      DynamicSchemaExpr.RelationalOperator.Equal,
      None
    )

  def =!=[Sc2 <: Sc](right: Expr[A, Sc2]): Expr[Boolean, Sc] = {
    val dynOpt = for {
      l <- toDynamicOpt(self)
      r <- toDynamicOpt(right.asInstanceOf[Expr[A, Sc]])
    } yield DynamicSchemaExpr.Relational(l, r, DynamicSchemaExpr.RelationalOperator.NotEqual)
    Relational[A, Sc](self, right.asInstanceOf[Expr[A, Sc]], DynamicSchemaExpr.RelationalOperator.NotEqual, dynOpt)
  }

  @scala.annotation.targetName("notEqOptLeft")
  def =!=[Sc2 <: Sc, B](right: Expr[B, Sc2])(using ev: A =:= Option[B]): Expr[Boolean, Sc] =
    Relational[A, Sc](
      self,
      right.asInstanceOf[Expr[B, Sc]].asInstanceOf[Expr[A, Sc]],
      DynamicSchemaExpr.RelationalOperator.NotEqual,
      None
    )

  @scala.annotation.nowarn
  def >[Sc2 <: Sc](right: Expr[A, Sc2])(using Ordering[A], scala.util.NotGiven[A =:= Boolean]): Expr[Boolean, Sc] = {
    summon[Ordering[A]]
    summon[scala.util.NotGiven[A =:= Boolean]]
    val dynOpt = for {
      l <- toDynamicOpt(self)
      r <- toDynamicOpt(right.asInstanceOf[Expr[A, Sc]])
    } yield DynamicSchemaExpr.Relational(l, r, DynamicSchemaExpr.RelationalOperator.GreaterThan)
    Relational[A, Sc](self, right.asInstanceOf[Expr[A, Sc]], DynamicSchemaExpr.RelationalOperator.GreaterThan, dynOpt)
  }

  @scala.annotation.nowarn
  @scala.annotation.targetName("gtOptLeft")
  def >[Sc2 <: Sc, B](right: Expr[B, Sc2])(using ev: A =:= Option[B], ord: Ordering[B]): Expr[Boolean, Sc] = {
    summon[Ordering[B]]
    Relational[A, Sc](
      self,
      right.asInstanceOf[Expr[B, Sc]].asInstanceOf[Expr[A, Sc]],
      DynamicSchemaExpr.RelationalOperator.GreaterThan,
      None
    )
  }

  @scala.annotation.nowarn
  def <[Sc2 <: Sc](right: Expr[A, Sc2])(using Ordering[A], scala.util.NotGiven[A =:= Boolean]): Expr[Boolean, Sc] = {
    summon[Ordering[A]]
    summon[scala.util.NotGiven[A =:= Boolean]]
    val dynOpt = for {
      l <- toDynamicOpt(self)
      r <- toDynamicOpt(right.asInstanceOf[Expr[A, Sc]])
    } yield DynamicSchemaExpr.Relational(l, r, DynamicSchemaExpr.RelationalOperator.LessThan)
    Relational[A, Sc](self, right.asInstanceOf[Expr[A, Sc]], DynamicSchemaExpr.RelationalOperator.LessThan, dynOpt)
  }

  @scala.annotation.nowarn
  @scala.annotation.targetName("ltOptLeft")
  def <[Sc2 <: Sc, B](right: Expr[B, Sc2])(using ev: A =:= Option[B], ord: Ordering[B]): Expr[Boolean, Sc] = {
    summon[Ordering[B]]
    Relational[A, Sc](
      self,
      right.asInstanceOf[Expr[B, Sc]].asInstanceOf[Expr[A, Sc]],
      DynamicSchemaExpr.RelationalOperator.LessThan,
      None
    )
  }

  @scala.annotation.nowarn
  def >=[Sc2 <: Sc](right: Expr[A, Sc2])(using Ordering[A], scala.util.NotGiven[A =:= Boolean]): Expr[Boolean, Sc] = {
    summon[Ordering[A]]
    summon[scala.util.NotGiven[A =:= Boolean]]
    val dynOpt = for {
      l <- toDynamicOpt(self)
      r <- toDynamicOpt(right.asInstanceOf[Expr[A, Sc]])
    } yield DynamicSchemaExpr.Relational(l, r, DynamicSchemaExpr.RelationalOperator.GreaterThanOrEqual)
    Relational[A, Sc](
      self,
      right.asInstanceOf[Expr[A, Sc]],
      DynamicSchemaExpr.RelationalOperator.GreaterThanOrEqual,
      dynOpt
    )
  }

  @scala.annotation.nowarn
  @scala.annotation.targetName("gteOptLeft")
  def >=[Sc2 <: Sc, B](right: Expr[B, Sc2])(using ev: A =:= Option[B], ord: Ordering[B]): Expr[Boolean, Sc] = {
    summon[Ordering[B]]
    Relational[A, Sc](
      self,
      right.asInstanceOf[Expr[B, Sc]].asInstanceOf[Expr[A, Sc]],
      DynamicSchemaExpr.RelationalOperator.GreaterThanOrEqual,
      None
    )
  }

  @scala.annotation.nowarn
  def <=[Sc2 <: Sc](right: Expr[A, Sc2])(using Ordering[A], scala.util.NotGiven[A =:= Boolean]): Expr[Boolean, Sc] = {
    summon[Ordering[A]]
    summon[scala.util.NotGiven[A =:= Boolean]]
    val dynOpt = for {
      l <- toDynamicOpt(self)
      r <- toDynamicOpt(right.asInstanceOf[Expr[A, Sc]])
    } yield DynamicSchemaExpr.Relational(l, r, DynamicSchemaExpr.RelationalOperator.LessThanOrEqual)
    Relational[A, Sc](
      self,
      right.asInstanceOf[Expr[A, Sc]],
      DynamicSchemaExpr.RelationalOperator.LessThanOrEqual,
      dynOpt
    )
  }

  @scala.annotation.nowarn
  @scala.annotation.targetName("lteOptLeft")
  def <=[Sc2 <: Sc, B](right: Expr[B, Sc2])(using ev: A =:= Option[B], ord: Ordering[B]): Expr[Boolean, Sc] = {
    summon[Ordering[B]]
    Relational[A, Sc](
      self,
      right.asInstanceOf[Expr[B, Sc]].asInstanceOf[Expr[A, Sc]],
      DynamicSchemaExpr.RelationalOperator.LessThanOrEqual,
      None
    )
  }

  /** SQL `IN` over a value list. */
  def in(values: Seq[A])(using schema: Schema[A]): Expr[Boolean, Sc] =
    InExpr[A, Sc](self, values, schema)

  /** Option-aware `IN` over plain values: `Expr[Option[A]] IN (?, ?)`. */
  @scala.annotation.targetName("inOptLeftValues")
  def in[B](values: Seq[B])(using ev: A =:= Option[B], schema: Schema[B]): Expr[Boolean, Sc] =
    InExpr[A, Sc](self, values.asInstanceOf[Seq[A]], schema.asInstanceOf[Schema[A]])

  /**
   * Option-aware `IN` over optional values. `None` entries are rejected with an
   * exact error instead of being silently dropped: a `NULL IN (...)` predicate
   * is neither true nor false in SQL, so dropping the value would change
   * semantics silently.
   */
  @scala.annotation.targetName("inOptLeft")
  def inOpt[B](values: Seq[Option[B]])(using ev: A =:= Option[B], schema: Schema[B]): Expr[Boolean, Sc] = {
    values.zipWithIndex.foreach {
      case (None, idx) =>
        throw new IllegalArgumentException(
          s"inOpt: value at index $idx is None — a SQL IN predicate over NULL is not well-defined and the None is never silently dropped; pass Some(v) entries or filter them explicitly"
        )
      case _ => ()
    }
    InExpr[A, Sc](self, values.collect { case Some(v) => v }.asInstanceOf[Seq[A]], schema.asInstanceOf[Schema[A]])
  }

  /** SQL `LIKE` against an expression pattern. */
  def like[Sc2 <: Sc](pattern: Expr[String, Sc2])(using ev: A =:= String): Expr[Boolean, Sc] = {
    val s   = self.asInstanceOf[Expr[String, Sc]]
    val pat = pattern.asInstanceOf[Expr[String, Sc]]
    val dyn = DynamicSchemaExpr.StringRegexMatch(toDynamic(pat), toDynamic(s))
    LikeExpr[Sc](s, pat, dyn)
  }

  /** SQL `LIKE` against a string pattern. */
  def like(pattern: String)(using ev: A =:= String): Expr[Boolean, Sc] =
    like(lit(pattern))

  /** Boolean AND. */
  def &&[Sc2 <: Sc](right: Expr[Boolean, Sc2])(using ev: A =:= Boolean): Expr[Boolean, Sc] = {
    val l      = self.asInstanceOf[Expr[Boolean, Sc]]
    val r      = right.asInstanceOf[Expr[Boolean, Sc]]
    val dynOpt = for {
      a <- toDynamicOpt(l)
      b <- toDynamicOpt(r)
    } yield DynamicSchemaExpr.Logical(a, b, DynamicSchemaExpr.LogicalOperator.And)
    Logical[Sc](l, r, DynamicSchemaExpr.LogicalOperator.And, dynOpt)
  }
  def and[Sc2 <: Sc](right: Expr[Boolean, Sc2])(using ev: A =:= Boolean): Expr[Boolean, Sc] = &&(right)

  /** Boolean OR. */
  def ||[Sc2 <: Sc](right: Expr[Boolean, Sc2])(using ev: A =:= Boolean): Expr[Boolean, Sc] = {
    val l      = self.asInstanceOf[Expr[Boolean, Sc]]
    val r      = right.asInstanceOf[Expr[Boolean, Sc]]
    val dynOpt = for {
      a <- toDynamicOpt(l)
      b <- toDynamicOpt(r)
    } yield DynamicSchemaExpr.Logical(a, b, DynamicSchemaExpr.LogicalOperator.Or)
    Logical[Sc](l, r, DynamicSchemaExpr.LogicalOperator.Or, dynOpt)
  }
  def or[Sc2 <: Sc](right: Expr[Boolean, Sc2])(using ev: A =:= Boolean): Expr[Boolean, Sc] = ||(right)

  /** Boolean negation. */
  def unary_!(using ev: A =:= Boolean): Expr[Boolean, Sc] = {
    val l      = self.asInstanceOf[Expr[Boolean, Sc]]
    val dynOpt = toDynamicOpt(l).map(DynamicSchemaExpr.Not(_))
    NotExpr[Sc](l, dynOpt)
  }

  /** Arithmetic addition. */
  def +[Sc2 <: Sc](right: Expr[A, Sc2])(using IsNumeric[A]): Expr[A, Sc] = {
    val tag    = ExprInternal.numericTag[A]
    val dynOpt = for {
      l <- toDynamicOpt(self)
      r <- toDynamicOpt(right)
    } yield DynamicSchemaExpr.Arithmetic(l, r, DynamicSchemaExpr.ArithmeticOperator.Add, tag)
    Arithmetic[A, Sc](self, right.asInstanceOf[Expr[A, Sc]], DynamicSchemaExpr.ArithmeticOperator.Add, tag, dynOpt)
  }
  def plus[Sc2 <: Sc](right: Expr[A, Sc2])(using IsNumeric[A]): Expr[A, Sc] = this.+(right)

  /** Arithmetic subtraction. */
  def -[Sc2 <: Sc](right: Expr[A, Sc2])(using IsNumeric[A]): Expr[A, Sc] = {
    val tag    = ExprInternal.numericTag[A]
    val dynOpt = for {
      l <- toDynamicOpt(self)
      r <- toDynamicOpt(right)
    } yield DynamicSchemaExpr.Arithmetic(l, r, DynamicSchemaExpr.ArithmeticOperator.Subtract, tag)
    Arithmetic[A, Sc](self, right.asInstanceOf[Expr[A, Sc]], DynamicSchemaExpr.ArithmeticOperator.Subtract, tag, dynOpt)
  }
  def minus[Sc2 <: Sc](right: Expr[A, Sc2])(using IsNumeric[A]): Expr[A, Sc] = this.-(right)

  /** Arithmetic multiplication. */
  def *[Sc2 <: Sc](right: Expr[A, Sc2])(using IsNumeric[A]): Expr[A, Sc] = {
    val tag    = ExprInternal.numericTag[A]
    val dynOpt = for {
      l <- toDynamicOpt(self)
      r <- toDynamicOpt(right)
    } yield DynamicSchemaExpr.Arithmetic(l, r, DynamicSchemaExpr.ArithmeticOperator.Multiply, tag)
    Arithmetic[A, Sc](self, right.asInstanceOf[Expr[A, Sc]], DynamicSchemaExpr.ArithmeticOperator.Multiply, tag, dynOpt)
  }
  def times[Sc2 <: Sc](right: Expr[A, Sc2])(using IsNumeric[A]): Expr[A, Sc] = this.*(right)
}

final case class Column[A, B, Sc] private[query] (
  table: Table[A],
  column: String,
  alias: Option[String],
  dynamic: DynamicSchemaExpr
) extends Expr[B, Sc]

final case class Lit[A] private[query] (value: A, schema: Schema[A], dynamic: DynamicSchemaExpr)
    extends Expr[A, Nothing]

final case class Relational[A, Sc] private[query] (
  left: Expr[A, Sc],
  right: Expr[A, Sc],
  operator: DynamicSchemaExpr.RelationalOperator,
  dynamic: Option[DynamicSchemaExpr]
) extends Expr[Boolean, Sc]

final case class Logical[Sc] private[query] (
  left: Expr[Boolean, Sc],
  right: Expr[Boolean, Sc],
  operator: DynamicSchemaExpr.LogicalOperator,
  dynamic: Option[DynamicSchemaExpr]
) extends Expr[Boolean, Sc]

final case class NotExpr[Sc] private[query] (expr: Expr[Boolean, Sc], dynamic: Option[DynamicSchemaExpr])
    extends Expr[Boolean, Sc]

final case class Arithmetic[A, Sc] private[query] (
  left: Expr[A, Sc],
  right: Expr[A, Sc],
  operator: DynamicSchemaExpr.ArithmeticOperator,
  tag: DynamicSchemaExpr.NumericTypeTag,
  dynamic: Option[DynamicSchemaExpr]
) extends Expr[A, Sc]

final case class LikeExpr[Sc] private[query] (
  left: Expr[String, Sc],
  pattern: Expr[String, Sc],
  dynamic: DynamicSchemaExpr
) extends Expr[Boolean, Sc]

// SQL-only v1 extensions — honest extension nodes, not native DynamicSchemaExpr. They recursively contain native Exprs.
// IN is SQL's `col IN (?,?,?)`; aggregate is SQL's `COUNT(col)` etc. DynamicSchemaExpr has no IN/aggregate cases, so these remain
// explicit extension nodes. This is acceptable because native predicate/logical/arithmetic/string cases all render through the single
// shared renderDynamic interpreter; only IN/aggregate are extensions.

final case class InExpr[A, Sc] private[query] (col: Expr[A, Sc], values: Seq[A], schema: Schema[A])
    extends Expr[Boolean, Sc]

sealed trait AggFunc
object AggFunc {
  case object Count     extends AggFunc
  case object Sum       extends AggFunc
  case object Avg       extends AggFunc
  case object Min       extends AggFunc
  case object Max       extends AggFunc
  case object CountStar extends AggFunc
}

/**
 * Aggregate node. `A` is the declared result type (already widened/normalized
 * by the constructor), `Sc` the query scope. The Option-left comparison
 * overloads of [[Expr]] apply because aggregate results are nullable.
 */
final case class Agg[A, Sc] private[query] (func: AggFunc, arg: Expr[?, ?]) extends Expr[A, Sc]
final case class AggStar[Sc] private[query] (func: AggFunc)                 extends Expr[Long, Sc]

/**
 * Nullable wrapper for LEFT JOIN columns: the macro returns `OptExpr[B, Sc]`
 * (an `Expr[Option[B], Sc]`) for right-side slots of LEFT JOINs so projections
 * and predicates must account for nullability.
 */
final case class OptExpr[A, Sc] private[query] (inner: Expr[A, Sc]) extends Expr[Option[A], Sc]

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

/**
 * Compile-time normalization of SUM result types, truthful for both PostgreSQL
 * and SQLite:
 *
 *   - `SUM(int2/int4)` → `bigint` (PG) / `INTEGER` (SQLite) → `Long`
 *   - `SUM(int8)` → `numeric` (PG) / `INTEGER` (SQLite Int64) → `BigDecimal`
 *   - `SUM(numeric/bigint)` → `numeric` (PG) / `REAL`-independent exact
 *     (SQLite) → `BigDecimal`
 *   - `SUM(float4/float8)` → `double precision` (PG) / `REAL` (SQLite) →
 *     `Double`
 *
 * The match type reduces to a concrete type at compile time (verified by the
 * select macro via match-type reduction), so `Expr[Option[SumOut[Int]], Scope]`
 * is exactly `Expr[Option[Long], Scope]`.
 */
type SumOut[A] = A match {
  case Byte | Short | Int         => Long
  case Long | BigInt | BigDecimal => BigDecimal
  case Float | Double             => Double
}

/**
 * Compile-time normalization of AVG result types:
 *
 *   - integral / bigint / numeric → `numeric` (PG). SQLite returns `REAL` for
 *     AVG over INTEGER columns; `DbCodec[BigDecimal]` decodes that via
 *     `getBigDecimal`, so the API type is the PG-truthful `BigDecimal` and the
 *     SQLite side is proven by integration tests.
 *   - float / double → `double precision` (PG) / `REAL` (SQLite) → `Double`
 */
type AvgOut[A] = A match {
  case Byte | Short | Int | Long | BigInt | BigDecimal => BigDecimal
  case Float | Double                                  => Double
}

/**
 * Gate typeclass: types SUM is supported for (the result type comes from
 * [[SumOut]]).
 */
sealed trait Summable[A]
object Summable {
  given byteSummable: Summable[Byte]             = new Summable[Byte] {}
  given shortSummable: Summable[Short]           = new Summable[Short] {}
  given intSummable: Summable[Int]               = new Summable[Int] {}
  given longSummable: Summable[Long]             = new Summable[Long] {}
  given floatSummable: Summable[Float]           = new Summable[Float] {}
  given doubleSummable: Summable[Double]         = new Summable[Double] {}
  given bigDecimalSummable: Summable[BigDecimal] = new Summable[BigDecimal] {}
  given bigIntSummable: Summable[BigInt]         = new Summable[BigInt] {}
}

/**
 * Gate typeclass: types AVG is supported for (the result type comes from
 * [[AvgOut]]).
 */
sealed trait Averagable[A]
object Averagable {
  given byteAveragable: Averagable[Byte]             = new Averagable[Byte] {}
  given shortAveragable: Averagable[Short]           = new Averagable[Short] {}
  given intAveragable: Averagable[Int]               = new Averagable[Int] {}
  given longAveragable: Averagable[Long]             = new Averagable[Long] {}
  given floatAveragable: Averagable[Float]           = new Averagable[Float] {}
  given doubleAveragable: Averagable[Double]         = new Averagable[Double] {}
  given bigDecimalAveragable: Averagable[BigDecimal] = new Averagable[BigDecimal] {}
  given bigIntAveragable: Averagable[BigInt]         = new Averagable[BigInt] {}
}

// Literal helper — requires Schema, rejects unsupported types at runtime via exhaustive mapping

def lit[A](value: A)(using schema: Schema[A]): Expr[A, Nothing] = {
  val dv = schema.toDynamicValue(value)
  // Validate immediately so unsupported types like List fail fast
  ExprInternal.dynamicValueToDbValue(dv)
  val dynamic = DynamicSchemaExpr.Literal(dv, schema)
  Lit(value, schema, dynamic)
}

/**
 * Scope-neutral aggregate constructors for literal-only expressions (`Expr[A,
 * Nothing]`). Query-bound aggregates are methods on
 * [[zio.blocks.sql.query.SqlQuery]] (`q.sum(q.col(...))` etc.) so the result
 * carries the query scope; these global variants exist for literal aggregates.
 */
@scala.annotation.nowarn
def sum[A](col: Expr[A, Nothing])(using Summable[A]): Expr[Option[SumOut[A]], Nothing] =
  Agg[Option[SumOut[A]], Nothing](AggFunc.Sum, col).asInstanceOf[Expr[Option[SumOut[A]], Nothing]]

@scala.annotation.nowarn
def avg[A](col: Expr[A, Nothing])(using Averagable[A]): Expr[Option[AvgOut[A]], Nothing] =
  Agg[Option[AvgOut[A]], Nothing](AggFunc.Avg, col).asInstanceOf[Expr[Option[AvgOut[A]], Nothing]]

@scala.annotation.nowarn
def min[A: Ordering](col: Expr[A, Nothing]): Expr[Option[A], Nothing] =
  Agg[Option[A], Nothing](AggFunc.Min, col).asInstanceOf[Expr[Option[A], Nothing]]

@scala.annotation.nowarn
def max[A: Ordering](col: Expr[A, Nothing]): Expr[Option[A], Nothing] =
  Agg[Option[A], Nothing](AggFunc.Max, col).asInstanceOf[Expr[Option[A], Nothing]]

def count[A](col: Expr[A, Nothing]): Expr[Long, Nothing] = Agg[Long, Nothing](AggFunc.Count, col)

/** COUNT(*) — scope-neutral, usable in any query projection/having. */
val countStar: Expr[Long, Nothing] = AggStar[Nothing](AggFunc.CountStar)

private def toDynamicOpt[A, Sc](expr: Expr[A, Sc]): Option[DynamicSchemaExpr] = expr match {
  case c: Column[_, _, _]  => Some(c.dynamic)
  case l: Lit[_]           => Some(l.dynamic)
  case r: Relational[_, _] => r.dynamic
  case l: Logical[_]       => l.dynamic
  case n: NotExpr[_]       => n.dynamic
  case a: Arithmetic[_, _] => a.dynamic
  case l: LikeExpr[_]      => Some(l.dynamic)
  case _: InExpr[_, _]     => None
  case _: Agg[_, _]        => None
  case _: AggStar[_]       => None
  case o: OptExpr[_, _]    => toDynamicOpt(o.inner)
}

private def toDynamic[A, Sc](expr: Expr[A, Sc]): DynamicSchemaExpr =
  toDynamicOpt(expr).getOrElse(throw new IllegalArgumentException(s"Expr has no native DynamicSchemaExpr: $expr"))
