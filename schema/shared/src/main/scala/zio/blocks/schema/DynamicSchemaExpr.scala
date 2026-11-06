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

package zio.blocks.schema

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

import zio.blocks.chunk.Chunk

/**
 * An untyped expression evaluated on a [[DynamicValue]]: the tree inside a
 * [[SchemaExpr]], and the form migrations use. Interpreters, such as a
 * translation to SQL, match on its cases.
 *
 * Every expression yields a sequence of values. A unary node yields one result
 * for each value of its operand, and a binary node one result for each pair of
 * left and right values, the left one varying slowest. A node fails with the
 * first error it meets: when the left operand fails, its error is returned, and
 * otherwise the right operand is evaluated whatever the left values are (there
 * is no short-circuit).
 */
sealed trait DynamicSchemaExpr {

  /**
   * Evaluates this expression on `input`, returning its results or the first
   * error. When the message of an error would include a NaN or infinite `Float`
   * or `Double`, such as `Not` applied to `Double.NaN`, `eval` throws a
   * `zio.blocks.schema.json.JsonCodecError` instead (a known bug).
   */
  def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]]
}

/**
 * The cases of [[DynamicSchemaExpr]] and the operators they take.
 */
object DynamicSchemaExpr {

  private val MaxSafeExponent: Int = 10000

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

  private def invalidOperation[A](message: String): Either[String, A] =
    Left(message)

  private def dynamicResult(value: DynamicValue): Either[String, DynamicValue] =
    Right(value)

  private def ensureNonZero[A](value: A, render: A => String = (_: A).toString)(
    isZero: A => Boolean
  ): Either[String, Unit] =
    if (isZero(value)) Left(s"Division by zero: divisor ${render(value)}") else Right(())

  private def checkedPowExponent(value: BigInt): Either[String, Int] =
    if (!value.isValidInt) invalidOperation[Int](s"Exponent out of range: $value")
    else {
      val exponent = value.toInt
      if (exponent < 0) invalidOperation[Int](s"Negative exponent not supported: $exponent")
      else if (exponent > MaxSafeExponent)
        invalidOperation[Int](s"Exponent exceeds safe limit ($MaxSafeExponent): $exponent")
      else Right(exponent)
    }

  private def safeRegexMatch(regex: String, value: String): Either[SchemaError, Boolean] =
    try Right(Pattern.matches(regex, value))
    catch {
      case e: PatternSyntaxException =>
        Left(SchemaError.conversionFailed(Nil, s"Invalid regex pattern: ${e.getDescription}"))
    }

  private def safeSubstring(value: String, start: Int, end: Int): Either[SchemaError, String] =
    if (start < 0 || end < 0)
      Left(SchemaError.conversionFailed(Nil, s"Substring indices must be non-negative: start=$start, end=$end"))
    else if (start > end)
      Left(SchemaError.conversionFailed(Nil, s"Substring start index $start exceeds end index $end"))
    else if (end > value.length)
      Left(SchemaError.conversionFailed(Nil, s"Substring end index $end exceeds string length ${value.length}"))
    else Right(value.substring(start, end))

  // ==================== Leaf Expressions ====================

  /**
   * Yields `value`, whatever the input. `schema` is the schema `value` was
   * encoded with.
   */
  final case class Literal(value: DynamicValue, schema: Schema[_]) extends DynamicSchemaExpr {

    /** Returns `value` as the only result, without reading `input`. */
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      Right(Seq(value))
  }

  /**
   * Yields the values `path` reaches in the input. A field that a record lacks,
   * or that a value which is not a record cannot have, yields nothing, and so
   * do `atIndices` and `atKeys` entries that are missing. It fails with an
   * error whose message starts with `UnexpectedCase:` when a variant has
   * another case (an `Option` case is reported as `Option`),
   * `SequenceIndexOutOfBounds:` when an index is out of bounds, `MissingKey:`
   * when no map has the key, `EmptySequence` when `elements` finds sequences
   * but no element, and `EmptyMap` when `mapKeys` or `mapValues` finds maps but
   * no entry. [[SchemaExpr]] turns these into the matching [[OpticCheck]]
   * cases. A search step fails with a conversion error.
   */
  final case class Select(path: DynamicOptic) extends DynamicSchemaExpr {

    /**
     * Walks `path` from `input` and returns the values it reaches, in order, or
     * fails with one of the errors listed for [[Select]].
     */
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
          Left(SchemaError.conversionFailed(Nil, s"TypeSearch nodes are not supported in schema expressions"))

        case _: DynamicOptic.Node.SchemaSearch =>
          Left(SchemaError.conversionFailed(Nil, s"SchemaSearch nodes are not supported in schema expressions"))
      }
    }
  }

  /**
   * Converts the input itself with `conversionType`, failing with the message
   * of the conversion. It has no operand, so it applies to the whole input.
   */
  final case class PrimitiveConversion(conversionType: SchemaExpr.ConversionType) extends DynamicSchemaExpr {

    /**
     * Returns `conversionType.convert(input)` as the only result, or fails with
     * a conversion error that carries the message of the conversion, such as
     * `Expected Int, got "x"`.
     */
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      conversionType.convert(input).map(Seq(_)).left.map(SchemaError.conversionFailed(Nil, _))
  }

  // ==================== Relational Operators ====================

  /**
   * Compares every result of `left` with every result of `right` with
   * `operator`, yielding `Boolean` primitives.
   */
  final case class Relational(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr,
    operator: RelationalOperator
  ) extends DynamicSchemaExpr {

    /**
     * Evaluates `left`, then `right`, and returns `operator` applied to every
     * pair of their results, the `left` value varying slowest; an error from
     * either side is returned unchanged.
     */
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        xs <- left.eval(input)
        ys <- right.eval(input)
      } yield for { x <- xs; y <- ys } yield operator.apply(x, y)
  }

  /**
   * A comparison of two values, yielding a `Boolean` primitive.
   */
  sealed trait RelationalOperator {

    /**
     * Compares `x` with `y`.
     */
    def apply(x: DynamicValue, y: DynamicValue): DynamicValue
  }

  /**
   * The cases of [[RelationalOperator]].
   */
  object RelationalOperator {

    /**
     * `x < y`, in the order of `DynamicValue#compare`, where primitives of
     * different types compare by type.
     */
    case object LessThan extends RelationalOperator {

      /**
       * Returns a `Boolean` primitive that is `true` when `x.compare(y) < 0`.
       * Primitives of different types compare by type, not by number (`Int` 5
       * is less than `Long` 1), a NaN is greater than every other value of its
       * type, and `-0.0` is less than `0.0`. Two `Int` values more than
       * `Int.MaxValue` apart compare the wrong way round (a known bug).
       */
      def apply(x: DynamicValue, y: DynamicValue): DynamicValue =
        DynamicValue.Primitive(PrimitiveValue.Boolean(x < y))
    }

    /**
     * `x <= y`, in the order of `DynamicValue#compare`, where primitives of
     * different types compare by type.
     */
    case object LessThanOrEqual extends RelationalOperator {

      /**
       * Returns a `Boolean` primitive that is `true` when `x.compare(y) <= 0`,
       * so it holds for two NaNs, which `Equal` can find unequal. Primitives of
       * different types compare by type, not by number (`Int` 5 is less than
       * `Long` 1), a NaN is greater than every other value of its type, and
       * `-0.0` is less than `0.0`. Two `Int` values more than `Int.MaxValue`
       * apart compare the wrong way round (a known bug).
       */
      def apply(x: DynamicValue, y: DynamicValue): DynamicValue =
        DynamicValue.Primitive(PrimitiveValue.Boolean(x <= y))
    }

    /**
     * `x > y`, in the order of `DynamicValue#compare`, where primitives of
     * different types compare by type.
     */
    case object GreaterThan extends RelationalOperator {

      /**
       * Returns a `Boolean` primitive that is `true` when `x.compare(y) > 0`,
       * so `Int.MaxValue > -1` is `false` (a known bug: two `Int` values more
       * than `Int.MaxValue` apart compare the wrong way round). Primitives of
       * different types compare by type, not by number, a NaN is greater than
       * every other value of its type, and `0.0` is greater than `-0.0`.
       */
      def apply(x: DynamicValue, y: DynamicValue): DynamicValue =
        DynamicValue.Primitive(PrimitiveValue.Boolean(x > y))
    }

    /**
     * `x >= y`, in the order of `DynamicValue#compare`, where primitives of
     * different types compare by type.
     */
    case object GreaterThanOrEqual extends RelationalOperator {

      /**
       * Returns a `Boolean` primitive that is `true` when `x.compare(y) >= 0`,
       * so it holds for two NaNs, which `Equal` can find unequal, and
       * `-0.0 >= 0.0` is `false`. Primitives of different types compare by
       * type, not by number. Two `Int` values more than `Int.MaxValue` apart
       * compare the wrong way round (a known bug).
       */
      def apply(x: DynamicValue, y: DynamicValue): DynamicValue =
        DynamicValue.Primitive(PrimitiveValue.Boolean(x >= y))
    }

    /**
     * `x == y`, structural equality.
     */
    case object Equal extends RelationalOperator {

      /**
       * Returns a `Boolean` primitive that is `x == y`: primitives of different
       * types are not equal (`Int` 1 is not `Long` 1), two NaN values are equal
       * only when they are the same instance, and `-0.0` equals `0.0`. The
       * order of `DynamicValue#compare`, which `LessThan` and the other
       * comparisons use, treats NaN and `-0.0` differently, so `-0.0 < 0.0` and
       * `-0.0 == 0.0` both hold.
       */
      def apply(x: DynamicValue, y: DynamicValue): DynamicValue =
        DynamicValue.Primitive(PrimitiveValue.Boolean(x == y))
    }

    /**
     * `x != y`.
     */
    case object NotEqual extends RelationalOperator {

      /**
       * Returns a `Boolean` primitive that is `x != y`, the negation of
       * `Equal`: `true` for two NaN values that are not the same instance, and
       * `false` for `-0.0` and `0.0`.
       */
      def apply(x: DynamicValue, y: DynamicValue): DynamicValue =
        DynamicValue.Primitive(PrimitiveValue.Boolean(x != y))
    }
  }

  // ==================== Logical Operators ====================

  /**
   * Combines every result of `left` with every result of `right` with
   * `operator`, and a value that is not a `Boolean` makes it fail. There is no
   * short-circuit: the right side is evaluated whatever the left values are, so
   * `false && e` fails when `e` fails; when the left side fails, its error is
   * returned.
   */
  final case class Logical(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr,
    operator: LogicalOperator
  ) extends DynamicSchemaExpr {

    /**
     * Evaluates `left`, then `right`, and returns what `operator` gives for the
     * two sequences of results; an error from either side is returned
     * unchanged.
     */
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        xs     <- left.eval(input)
        ys     <- right.eval(input)
        result <- operator.apply(xs, ys)
      } yield result
  }

  /**
   * A logical operator applied to every pair of left and right values.
   */
  sealed trait LogicalOperator {

    /**
     * Combines every value of `xs` with every value of `ys`, failing when one
     * of them is not a `Boolean`.
     */
    def apply(xs: Seq[DynamicValue], ys: Seq[DynamicValue]): Either[SchemaError, Seq[DynamicValue]]
  }

  /**
   * The cases of [[LogicalOperator]].
   */
  object LogicalOperator {

    /**
     * Logical and.
     */
    case object And extends LogicalOperator {

      /**
       * Returns `x && y` as a `Boolean` primitive for every pair of a value of
       * `xs` and a value of `ys`, the `xs` value varying slowest. When a value
       * is not a `Boolean`, it returns `Left` with `Expected Boolean, got: `
       * and the first such value in that order.
       */
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

    /**
     * Logical or.
     */
    case object Or extends LogicalOperator {

      /**
       * Returns `x || y` as a `Boolean` primitive for every pair of a value of
       * `xs` and a value of `ys`, the `xs` value varying slowest. When a value
       * is not a `Boolean`, it returns `Left` with `Expected Boolean, got: `
       * and the first such value in that order.
       */
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

  /**
   * Negates every result of `expr`, failing when one is not a `Boolean`.
   */
  final case class Not(expr: DynamicSchemaExpr) extends DynamicSchemaExpr {

    /**
     * Returns the negation of each result of `expr`; a result that is not a
     * `Boolean` fails with `Expected Boolean, got: ` and the value.
     */
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

  /**
   * The numeric type an [[Arithmetic]] node works on. Each operation needs both
   * values to be primitives of that type and returns `Left` with a message
   * otherwise; when one of them is a NaN or infinite `Float` or `Double`,
   * building that message throws a `zio.blocks.schema.json.JsonCodecError`
   * instead (a known bug). `NumericPrimitiveType#toTag` gives the tag of a
   * type.
   */
  sealed trait NumericTypeTag {

    /**
     * Returns `x + y`; `Byte`, `Short`, `Int` and `Long` wrap on overflow.
     */
    def add(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue]

    /**
     * Returns `x - y`; `Byte`, `Short`, `Int` and `Long` wrap on overflow.
     */
    def subtract(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue]

    /**
     * Returns `x * y`; `Byte`, `Short`, `Int` and `Long` wrap on overflow.
     */
    def multiply(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue]

    /**
     * Returns `x / y`, failing when `y` is zero (for `Float` and `Double`,
     * positive zero).
     */
    def divide(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue]

    /**
     * Returns `x` to the power `y`. Up to `Double`, it computes `Math.pow` on
     * `Double`s and converts the result back: an `Int` or `Long` result that is
     * too large or infinite saturates at the type's limit, `Byte` and `Short`
     * results go through `Int` and then wrap, and a negative exponent on an
     * integer type gives 0 except for the bases 1, -1 and 0. For `BigInt` and
     * `BigDecimal`, `y` must be a whole number from 0 to 10000.
     */
    def pow(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue]

    /**
     * Returns `x % y`, failing when `y` is zero (for `Float` and `Double`,
     * positive zero).
     */
    def modulo(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue]
  }

  /**
   * The tags of the eight numeric types.
   */
  object NumericTypeTag {

    /**
     * Arithmetic on `Byte` values.
     */
    case object ByteTag extends NumericTypeTag {

      /**
       * Returns `x + y` as a `Byte`, wrapping on overflow (`127 + 1` gives
       * -128); a value that is not a `Byte` gives
       * `Left("Expected Byte values, got: " + x + " and " + y)`.
       */
      def add(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Byte((a + b).toByte)) }

      /**
       * Returns `x - y` as a `Byte`, wrapping on overflow (`-128 - 1` gives
       * 127); a value that is not a `Byte` gives
       * `Left("Expected Byte values, got: " + x + " and " + y)`.
       */
      def subtract(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Byte((a - b).toByte)) }

      /**
       * Returns `x * y` as a `Byte`, wrapping on overflow (`16 * 16` gives 0);
       * a value that is not a `Byte` gives
       * `Left("Expected Byte values, got: " + x + " and " + y)`.
       */
      def multiply(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Byte((a * b).toByte)) }

      /**
       * Returns `x / y` as a `Byte`, rounded toward zero (`-7 / 2` gives -3),
       * and -128 divided by -1 gives -128; a zero `y` gives
       * `Left("Division by zero: divisor 0")`, and a value that is not a `Byte`
       * gives `Left("Expected Byte values, got: " + x + " and " + y)`.
       */
      def divide(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).flatMap { case (a, b) =>
          ensureNonZero[Byte](b)(_ == 0).flatMap(_ =>
            dynamicResult(DynamicValue.Primitive(PrimitiveValue.Byte((a / b).toByte)))
          )
        }

      /**
       * Returns `Math.pow` of the two values as `Double`s, converted to an
       * `Int` (saturating at the `Int` limits) and then truncated to a `Byte`,
       * so 2 to the power 7 gives -128 and 2 to the power 10 gives 0; a
       * negative exponent gives 0 for bases other than 1, -1 and 0, and 0 to a
       * negative power gives -1. A value that is not a `Byte` gives
       * `Left("Expected Byte values, got: " + x + " and " + y)`.
       */
      def pow(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) =>
          DynamicValue.Primitive(PrimitiveValue.Byte(Math.pow(a.toDouble, b.toDouble).toByte))
        }

      /**
       * Returns `x % y` as a `Byte`, with the sign of `x` (`-7 % 3` gives -1);
       * a zero `y` gives `Left("Division by zero: divisor 0")`, and a value
       * that is not a `Byte` gives
       * `Left("Expected Byte values, got: " + x + " and " + y)`.
       */
      def modulo(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).flatMap { case (a, b) =>
          ensureNonZero[Byte](b)(_ == 0).flatMap(_ =>
            dynamicResult(DynamicValue.Primitive(PrimitiveValue.Byte((a % b).toByte)))
          )
        }

      private def extract2(x: DynamicValue, y: DynamicValue): Either[String, (Byte, Byte)] =
        (x, y) match {
          case (DynamicValue.Primitive(PrimitiveValue.Byte(a)), DynamicValue.Primitive(PrimitiveValue.Byte(b))) =>
            Right((a, b))
          case _ => Left(s"Expected Byte values, got: $x and $y")
        }
    }

    /**
     * Arithmetic on `Short` values.
     */
    case object ShortTag extends NumericTypeTag {

      /**
       * Returns `x + y` as a `Short`, wrapping on overflow (`32767 + 1` gives
       * -32768); a value that is not a `Short` gives
       * `Left("Expected Short values, got: " + x + " and " + y)`.
       */
      def add(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Short((a + b).toShort)) }

      /**
       * Returns `x - y` as a `Short`, wrapping on overflow (`-32768 - 1` gives
       * 32767); a value that is not a `Short` gives
       * `Left("Expected Short values, got: " + x + " and " + y)`.
       */
      def subtract(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Short((a - b).toShort)) }

      /**
       * Returns `x * y` as a `Short`, wrapping on overflow (`300 * 300` gives
       * 24464); a value that is not a `Short` gives
       * `Left("Expected Short values, got: " + x + " and " + y)`.
       */
      def multiply(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Short((a * b).toShort)) }

      /**
       * Returns `x / y` as a `Short`, rounded toward zero (`-7 / 2` gives -3),
       * and -32768 divided by -1 gives -32768; a zero `y` gives
       * `Left("Division by zero: divisor 0")`, and a value that is not a
       * `Short` gives `Left("Expected Short values, got: " + x + " and " + y)`.
       */
      def divide(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).flatMap { case (a, b) =>
          ensureNonZero[Short](b)(_ == 0).flatMap(_ =>
            dynamicResult(DynamicValue.Primitive(PrimitiveValue.Short((a / b).toShort)))
          )
        }

      /**
       * Returns `Math.pow` of the two values as `Double`s, converted to an
       * `Int` (saturating at the `Int` limits) and then truncated to a `Short`,
       * so 2 to the power 15 gives -32768; a negative exponent gives 0 for
       * bases other than 1, -1 and 0, and 0 to a negative power gives -1. A
       * value that is not a `Short` gives
       * `Left("Expected Short values, got: " + x + " and " + y)`.
       */
      def pow(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) =>
          DynamicValue.Primitive(PrimitiveValue.Short(Math.pow(a.toDouble, b.toDouble).toShort))
        }

      /**
       * Returns `x % y` as a `Short`, with the sign of `x` (`-7 % 3` gives -1);
       * a zero `y` gives `Left("Division by zero: divisor 0")`, and a value
       * that is not a `Short` gives
       * `Left("Expected Short values, got: " + x + " and " + y)`.
       */
      def modulo(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).flatMap { case (a, b) =>
          ensureNonZero[Short](b)(_ == 0).flatMap(_ =>
            dynamicResult(DynamicValue.Primitive(PrimitiveValue.Short((a % b).toShort)))
          )
        }

      private def extract2(x: DynamicValue, y: DynamicValue): Either[String, (Short, Short)] =
        (x, y) match {
          case (DynamicValue.Primitive(PrimitiveValue.Short(a)), DynamicValue.Primitive(PrimitiveValue.Short(b))) =>
            Right((a, b))
          case _ => Left(s"Expected Short values, got: $x and $y")
        }
    }

    /**
     * Arithmetic on `Int` values.
     */
    case object IntTag extends NumericTypeTag {

      /**
       * Returns `x + y` as an `Int`, wrapping on overflow (`Int.MaxValue + 1`
       * gives `Int.MinValue`); a value that is not an `Int` gives
       * `Left("Expected Int values, got: " + x + " and " + y)`.
       */
      def add(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Int(a + b)) }

      /**
       * Returns `x - y` as an `Int`, wrapping on overflow (`Int.MinValue - 1`
       * gives `Int.MaxValue`); a value that is not an `Int` gives
       * `Left("Expected Int values, got: " + x + " and " + y)`.
       */
      def subtract(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Int(a - b)) }

      /**
       * Returns `x * y` as an `Int`, wrapping on overflow (`65536 * 65536`
       * gives 0); a value that is not an `Int` gives
       * `Left("Expected Int values, got: " + x + " and " + y)`.
       */
      def multiply(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Int(a * b)) }

      /**
       * Returns `x / y` as an `Int`, rounded toward zero (`-7 / 2` gives -3),
       * and `Int.MinValue` divided by -1 gives `Int.MinValue`; a zero `y` gives
       * `Left("Division by zero: divisor 0")`, and a value that is not an `Int`
       * gives `Left("Expected Int values, got: " + x + " and " + y)`.
       */
      def divide(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).flatMap { case (a, b) =>
          ensureNonZero[Int](b)(_ == 0).flatMap(_ => dynamicResult(DynamicValue.Primitive(PrimitiveValue.Int(a / b))))
        }

      /**
       * Returns `Math.pow` of the two values as `Double`s, converted to an
       * `Int`: a result beyond the `Int` range saturates (2 to the power 31
       * gives `Int.MaxValue`), a fraction is truncated toward zero (2 to the
       * power -1 gives 0), and 0 to a negative power gives `Int.MaxValue`. A
       * value that is not an `Int` gives
       * `Left("Expected Int values, got: " + x + " and " + y)`.
       */
      def pow(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) =>
          DynamicValue.Primitive(PrimitiveValue.Int(Math.pow(a.toDouble, b.toDouble).toInt))
        }

      /**
       * Returns `x % y` as an `Int`, with the sign of `x` (`-7 % 3` gives -1);
       * a zero `y` gives `Left("Division by zero: divisor 0")`, and a value
       * that is not an `Int` gives
       * `Left("Expected Int values, got: " + x + " and " + y)`.
       */
      def modulo(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).flatMap { case (a, b) =>
          ensureNonZero[Int](b)(_ == 0).flatMap(_ => dynamicResult(DynamicValue.Primitive(PrimitiveValue.Int(a % b))))
        }

      private def extract2(x: DynamicValue, y: DynamicValue): Either[String, (Int, Int)] =
        (x, y) match {
          case (DynamicValue.Primitive(PrimitiveValue.Int(a)), DynamicValue.Primitive(PrimitiveValue.Int(b))) =>
            Right((a, b))
          case _ => Left(s"Expected Int values, got: $x and $y")
        }
    }

    /**
     * Arithmetic on `Long` values.
     */
    case object LongTag extends NumericTypeTag {

      /**
       * Returns `x + y` as a `Long`, wrapping on overflow (`Long.MaxValue + 1`
       * gives `Long.MinValue`); a value that is not a `Long` gives
       * `Left("Expected Long values, got: " + x + " and " + y)`.
       */
      def add(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Long(a + b)) }

      /**
       * Returns `x - y` as a `Long`, wrapping on overflow (`Long.MinValue - 1`
       * gives `Long.MaxValue`); a value that is not a `Long` gives
       * `Left("Expected Long values, got: " + x + " and " + y)`.
       */
      def subtract(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Long(a - b)) }

      /**
       * Returns `x * y` as a `Long`, wrapping on overflow (`Long.MaxValue * 2`
       * gives -2); a value that is not a `Long` gives
       * `Left("Expected Long values, got: " + x + " and " + y)`.
       */
      def multiply(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Long(a * b)) }

      /**
       * Returns `x / y` as a `Long`, rounded toward zero (`-7 / 2` gives -3),
       * and `Long.MinValue` divided by -1 gives `Long.MinValue`; a zero `y`
       * gives `Left("Division by zero: divisor 0")`, and a value that is not a
       * `Long` gives `Left("Expected Long values, got: " + x + " and " + y)`.
       */
      def divide(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).flatMap { case (a, b) =>
          ensureNonZero[Long](b)(_ == 0L).flatMap(_ =>
            dynamicResult(DynamicValue.Primitive(PrimitiveValue.Long(a / b)))
          )
        }

      /**
       * Returns `Math.pow` of the two values as `Double`s, converted to a
       * `Long`: a result beyond the `Long` range saturates (2 to the power 63
       * gives `Long.MaxValue`), a fraction is truncated toward zero, 0 to a
       * negative power gives `Long.MaxValue`, and a large result is rounded
       * through the `Double` (3 to the power 39 gives 4052555153018976256, not
       * 4052555153018976267). A value that is not a `Long` gives
       * `Left("Expected Long values, got: " + x + " and " + y)`.
       */
      def pow(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) =>
          DynamicValue.Primitive(PrimitiveValue.Long(Math.pow(a.toDouble, b.toDouble).toLong))
        }

      /**
       * Returns `x % y` as a `Long`, with the sign of `x` (`-7 % 3` gives -1);
       * a zero `y` gives `Left("Division by zero: divisor 0")`, and a value
       * that is not a `Long` gives
       * `Left("Expected Long values, got: " + x + " and " + y)`.
       */
      def modulo(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).flatMap { case (a, b) =>
          ensureNonZero[Long](b)(_ == 0L).flatMap(_ =>
            dynamicResult(DynamicValue.Primitive(PrimitiveValue.Long(a % b)))
          )
        }

      private def extract2(x: DynamicValue, y: DynamicValue): Either[String, (Long, Long)] =
        (x, y) match {
          case (DynamicValue.Primitive(PrimitiveValue.Long(a)), DynamicValue.Primitive(PrimitiveValue.Long(b))) =>
            Right((a, b))
          case _ => Left(s"Expected Long values, got: $x and $y")
        }
    }

    /**
     * Arithmetic on `Float` values.
     */
    case object FloatTag extends NumericTypeTag {

      /**
       * Returns `x + y` as a `Float`; a value that is not a `Float` gives
       * `Left("Expected Float values, got: " + x + " and " + y)`.
       */
      def add(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Float(a + b)) }

      /**
       * Returns `x - y` as a `Float` (`0.3f - 0.1f` gives 0.20000002); a value
       * that is not a `Float` gives
       * `Left("Expected Float values, got: " + x + " and " + y)`.
       */
      def subtract(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Float(a - b)) }

      /**
       * Returns `x * y` as a `Float`, which is an infinity past the `Float`
       * range (`3e38f * 10` gives `Infinity`); a value that is not a `Float`
       * gives `Left("Expected Float values, got: " + x + " and " + y)`.
       */
      def multiply(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Float(a * b)) }

      /**
       * Returns `x / y` as a `Float`. Only a positive zero `y` gives
       * `Left("Division by zero: divisor 0.0")`; `-0.0` divides like any other
       * number, so `1 / -0.0` gives `-Infinity`. A value that is not a `Float`
       * gives `Left("Expected Float values, got: " + x + " and " + y)`.
       */
      def divide(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).flatMap { case (a, b) =>
          ensureNonZero[Float](b)(_.compare(0.0f) == 0).flatMap(_ =>
            dynamicResult(DynamicValue.Primitive(PrimitiveValue.Float(a / b)))
          )
        }

      /**
       * Returns `Math.pow` of the two values as `Double`s, rounded to a
       * `Float`, so a result past the `Float` range gives an infinity (10 to
       * the power 39 gives `Infinity`); a value that is not a `Float` gives
       * `Left("Expected Float values, got: " + x + " and " + y)`.
       */
      def pow(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) =>
          DynamicValue.Primitive(PrimitiveValue.Float(Math.pow(a.toDouble, b.toDouble).toFloat))
        }

      /**
       * Returns `x % y` as a `Float` (`5.5 % 2` gives 1.5). Only a positive
       * zero `y` gives `Left("Division by zero: divisor 0.0")`; `-0.0` gives
       * NaN. A value that is not a `Float` gives
       * `Left("Expected Float values, got: " + x + " and " + y)`.
       */
      def modulo(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).flatMap { case (a, b) =>
          ensureNonZero[Float](b)(_.compare(0.0f) == 0).flatMap(_ =>
            dynamicResult(DynamicValue.Primitive(PrimitiveValue.Float(a % b)))
          )
        }

      private def extract2(x: DynamicValue, y: DynamicValue): Either[String, (Float, Float)] =
        (x, y) match {
          case (DynamicValue.Primitive(PrimitiveValue.Float(a)), DynamicValue.Primitive(PrimitiveValue.Float(b))) =>
            Right((a, b))
          case _ => Left(s"Expected Float values, got: $x and $y")
        }
    }

    /**
     * Arithmetic on `Double` values.
     */
    case object DoubleTag extends NumericTypeTag {

      /**
       * Returns `x + y` as a `Double` (`0.1 + 0.2` gives 0.30000000000000004);
       * a value that is not a `Double` gives
       * `Left("Expected Double values, got: " + x + " and " + y)`.
       */
      def add(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Double(a + b)) }

      /**
       * Returns `x - y` as a `Double`; a value that is not a `Double` gives
       * `Left("Expected Double values, got: " + x + " and " + y)`.
       */
      def subtract(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Double(a - b)) }

      /**
       * Returns `x * y` as a `Double`; a value that is not a `Double` gives
       * `Left("Expected Double values, got: " + x + " and " + y)`.
       */
      def multiply(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Double(a * b)) }

      /**
       * Returns `x / y` as a `Double`. Only a positive zero `y` gives
       * `Left("Division by zero: divisor 0.0")`; `-0.0` divides like any other
       * number, so `1 / -0.0` gives `-Infinity`. A value that is not a `Double`
       * gives `Left("Expected Double values, got: " + x + " and " + y)`.
       */
      def divide(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).flatMap { case (a, b) =>
          ensureNonZero[Double](b)(_.compare(0.0d) == 0).flatMap(_ =>
            dynamicResult(DynamicValue.Primitive(PrimitiveValue.Double(a / b)))
          )
        }

      /**
       * Returns `Math.pow(x, y)`, so a negative base with a fractional exponent
       * gives NaN and 10 to the power 400 gives `Infinity`; a value that is not
       * a `Double` gives
       * `Left("Expected Double values, got: " + x + " and " + y)`.
       */
      def pow(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.Double(Math.pow(a, b))) }

      /**
       * Returns `x % y` as a `Double` (`5.5 % 2` gives 1.5). Only a positive
       * zero `y` gives `Left("Division by zero: divisor 0.0")`; `-0.0` gives
       * NaN. A value that is not a `Double` gives
       * `Left("Expected Double values, got: " + x + " and " + y)`.
       */
      def modulo(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).flatMap { case (a, b) =>
          ensureNonZero[Double](b)(_.compare(0.0d) == 0).flatMap(_ =>
            dynamicResult(DynamicValue.Primitive(PrimitiveValue.Double(a % b)))
          )
        }

      private def extract2(x: DynamicValue, y: DynamicValue): Either[String, (Double, Double)] =
        (x, y) match {
          case (DynamicValue.Primitive(PrimitiveValue.Double(a)), DynamicValue.Primitive(PrimitiveValue.Double(b))) =>
            Right((a, b))
          case _ => Left(s"Expected Double values, got: $x and $y")
        }
    }

    /**
     * Arithmetic on `BigInt` values.
     */
    case object BigIntTag extends NumericTypeTag {

      /**
       * Returns `x + y` as a `BigInt`, which does not overflow; a value that is
       * not a `BigInt` gives
       * `Left("Expected BigInt values, got: " + x + " and " + y)`.
       */
      def add(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.BigInt(a + b)) }

      /**
       * Returns `x - y` as a `BigInt`, which does not overflow; a value that is
       * not a `BigInt` gives
       * `Left("Expected BigInt values, got: " + x + " and " + y)`.
       */
      def subtract(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.BigInt(a - b)) }

      /**
       * Returns `x * y` as a `BigInt`, which does not overflow; a value that is
       * not a `BigInt` gives
       * `Left("Expected BigInt values, got: " + x + " and " + y)`.
       */
      def multiply(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.BigInt(a * b)) }

      /**
       * Returns `x / y` as a `BigInt`, rounded toward zero (`-7 / 2` gives -3);
       * a zero `y` gives `Left("Division by zero: divisor 0")`, and a value
       * that is not a `BigInt` gives
       * `Left("Expected BigInt values, got: " + x + " and " + y)`.
       */
      def divide(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).flatMap { case (a, b) =>
          ensureNonZero[BigInt](b)(_ == BigInt(0)).flatMap(_ =>
            dynamicResult(DynamicValue.Primitive(PrimitiveValue.BigInt(a / b)))
          )
        }

      /**
       * Returns `x.pow(y)` for an exponent from 0 to 10000 (2 to the power 100
       * is exact); a negative exponent fails with
       * `Negative exponent not supported: ` and the exponent, one above 10000
       * with `Exponent exceeds safe limit (10000): ` and the exponent, and one
       * outside the `Int` range with `Exponent out of range: ` and the
       * exponent. A value that is not a `BigInt` gives
       * `Left("Expected BigInt values, got: " + x + " and " + y)`.
       */
      def pow(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).flatMap { case (a, b) =>
          checkedPowExponent(b).flatMap(exponent =>
            dynamicResult(DynamicValue.Primitive(PrimitiveValue.BigInt(a.pow(exponent))))
          )
        }

      /**
       * Returns `x % y` as a `BigInt`, with the sign of `x` (`-7 % 3` gives
       * -1); a zero `y` gives `Left("Division by zero: divisor 0")`, and a
       * value that is not a `BigInt` gives
       * `Left("Expected BigInt values, got: " + x + " and " + y)`.
       */
      def modulo(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).flatMap { case (a, b) =>
          ensureNonZero[BigInt](b)(_ == BigInt(0)).flatMap(_ =>
            dynamicResult(DynamicValue.Primitive(PrimitiveValue.BigInt(a % b)))
          )
        }

      private def extract2(x: DynamicValue, y: DynamicValue): Either[String, (BigInt, BigInt)] =
        (x, y) match {
          case (DynamicValue.Primitive(PrimitiveValue.BigInt(a)), DynamicValue.Primitive(PrimitiveValue.BigInt(b))) =>
            Right((a, b))
          case _ => Left(s"Expected BigInt values, got: $x and $y")
        }
    }

    /**
     * Arithmetic on `BigDecimal` values.
     */
    case object BigDecimalTag extends NumericTypeTag {

      /**
       * Returns `x + y` as a `BigDecimal` (`0.1 + 0.2` gives 0.3); a value that
       * is not a `BigDecimal` gives
       * `Left("Expected BigDecimal values, got: " + x + " and " + y)`.
       */
      def add(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.BigDecimal(a + b)) }

      /**
       * Returns `x - y` as a `BigDecimal`; a value that is not a `BigDecimal`
       * gives `Left("Expected BigDecimal values, got: " + x + " and " + y)`.
       */
      def subtract(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.BigDecimal(a - b)) }

      /**
       * Returns `x * y` as a `BigDecimal`, rounded with the `MathContext` of
       * `x` (34 significant digits by default); a value that is not a
       * `BigDecimal` gives
       * `Left("Expected BigDecimal values, got: " + x + " and " + y)`.
       */
      def multiply(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).map { case (a, b) => DynamicValue.Primitive(PrimitiveValue.BigDecimal(a * b)) }

      /**
       * Returns `x / y` as a `BigDecimal`, rounded with the `MathContext` of
       * `x` (34 significant digits by default, so `1 / 3` gives
       * 0.3333333333333333333333333333333333). A `y` equal to zero at any scale
       * fails with `Division by zero: divisor ` and `y` as written, such as
       * `0.00`. A value that is not a `BigDecimal` gives
       * `Left("Expected BigDecimal values, got: " + x + " and " + y)`.
       */
      def divide(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).flatMap { case (a, b) =>
          ensureNonZero[BigDecimal](b)(_ == BigDecimal(0)).flatMap(_ =>
            dynamicResult(DynamicValue.Primitive(PrimitiveValue.BigDecimal(a / b)))
          )
        }

      /**
       * Returns `x.pow(y)` for a whole-number exponent from 0 to 10000 (`2.0`
       * counts as 2); a negative exponent fails with
       * `Negative exponent not supported: ` and the exponent, one above 10000
       * with `Exponent exceeds safe limit (10000): ` and the exponent, and one
       * outside the `Int` range with `Exponent out of range: ` and the
       * exponent. A fractional exponent fails with
       * `Exponent exceeds safe limit (10000): 2147483647`. A value that is not
       * a `BigDecimal` gives
       * `Left("Expected BigDecimal values, got: " + x + " and " + y)`.
       */
      def pow(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).flatMap { case (a, b) =>
          checkedPowExponent(b.toBigIntExact.getOrElse(BigInt(Int.MaxValue))).flatMap { exponent =>
            dynamicResult(DynamicValue.Primitive(PrimitiveValue.BigDecimal(a.pow(exponent))))
          }
        }

      /**
       * Returns `x % y` as a `BigDecimal` (`7.5 % 2` gives 1.5); a `y` equal to
       * zero gives `Left` with `Division by zero: divisor ` and `y`, and a
       * value that is not a `BigDecimal` gives
       * `Left("Expected BigDecimal values, got: " + x + " and " + y)`.
       */
      def modulo(x: DynamicValue, y: DynamicValue): Either[String, DynamicValue] =
        extract2(x, y).flatMap { case (a, b) =>
          ensureNonZero[BigDecimal](b)(_ == BigDecimal(0)).flatMap(_ =>
            dynamicResult(DynamicValue.Primitive(PrimitiveValue.BigDecimal(a % b)))
          )
        }

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

  /**
   * Applies `operator` to every pair of results of `left` and `right`, with the
   * arithmetic of `numericType`.
   */
  final case class Arithmetic(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr,
    operator: ArithmeticOperator,
    numericType: NumericTypeTag
  ) extends DynamicSchemaExpr {

    /**
     * Evaluates `left`, then `right`, and returns `operator` applied to every
     * pair of their results with `numericType`, the `left` value varying
     * slowest. An error from either side is returned unchanged, and when a pair
     * fails, the message of the first failing pair becomes a conversion error,
     * such as `Division by zero: divisor 0`.
     */
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

  /**
   * An arithmetic operation, delegated to a [[NumericTypeTag]].
   */
  sealed trait ArithmeticOperator {

    /**
     * Applies this operation to `x` and `y` with the arithmetic of
     * `numericType`.
     */
    def apply(x: DynamicValue, y: DynamicValue, numericType: NumericTypeTag): Either[String, DynamicValue]
  }

  /**
   * The cases of [[ArithmeticOperator]].
   */
  object ArithmeticOperator {

    /**
     * Calls `NumericTypeTag#add`.
     */
    case object Add extends ArithmeticOperator {

      /** Returns `numericType.add(x, y)`. */
      def apply(x: DynamicValue, y: DynamicValue, numericType: NumericTypeTag): Either[String, DynamicValue] =
        numericType.add(x, y)
    }

    /**
     * Calls `NumericTypeTag#subtract`.
     */
    case object Subtract extends ArithmeticOperator {

      /** Returns `numericType.subtract(x, y)`. */
      def apply(x: DynamicValue, y: DynamicValue, numericType: NumericTypeTag): Either[String, DynamicValue] =
        numericType.subtract(x, y)
    }

    /**
     * Calls `NumericTypeTag#multiply`.
     */
    case object Multiply extends ArithmeticOperator {

      /** Returns `numericType.multiply(x, y)`. */
      def apply(x: DynamicValue, y: DynamicValue, numericType: NumericTypeTag): Either[String, DynamicValue] =
        numericType.multiply(x, y)
    }

    /**
     * Calls `NumericTypeTag#divide`.
     */
    case object Divide extends ArithmeticOperator {

      /** Returns `numericType.divide(x, y)`. */
      def apply(x: DynamicValue, y: DynamicValue, numericType: NumericTypeTag): Either[String, DynamicValue] =
        numericType.divide(x, y)
    }

    /**
     * Calls `NumericTypeTag#pow`.
     */
    case object Pow extends ArithmeticOperator {

      /** Returns `numericType.pow(x, y)`. */
      def apply(x: DynamicValue, y: DynamicValue, numericType: NumericTypeTag): Either[String, DynamicValue] =
        numericType.pow(x, y)
    }

    /**
     * Calls `NumericTypeTag#modulo`.
     */
    case object Modulo extends ArithmeticOperator {

      /** Returns `numericType.modulo(x, y)`. */
      def apply(x: DynamicValue, y: DynamicValue, numericType: NumericTypeTag): Either[String, DynamicValue] =
        numericType.modulo(x, y)
    }
  }

  // ==================== Bitwise Operators ====================

  /**
   * Applies `operator` to every pair of results of `left` and `right`, which
   * must be `Byte`, `Short`, `Int` or `Long` values.
   */
  final case class Bitwise(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr,
    operator: BitwiseOperator
  ) extends DynamicSchemaExpr {

    /**
     * Evaluates `left`, then `right`, and returns `operator` applied to every
     * pair of their results, the `left` value varying slowest; an error from
     * either side is returned unchanged, and otherwise the error of the first
     * failing pair.
     */
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

  /**
   * A bitwise operation. The result is a `Long` when either value is a `Long`
   * (for a shift, when the left one is) and an `Int` otherwise, also for `Byte`
   * and `Short` values.
   */
  sealed trait BitwiseOperator {

    /**
     * Applies this operation to `x` and `y`.
     */
    def apply(x: DynamicValue, y: DynamicValue): Either[SchemaError, DynamicValue]
  }

  /**
   * The cases of [[BitwiseOperator]].
   */
  object BitwiseOperator {

    /**
     * Bitwise and.
     */
    case object And extends BitwiseOperator {

      /**
       * Returns `x & y` of the two values widened to `Long`s with their sign,
       * as a `Long` when either is a `Long` and as an `Int` otherwise (`Byte` 6
       * and `Byte` 3 give `Int` 2, and `Byte` -1 and `Int` 255 give 255); a
       * value that is not a `Byte`, `Short`, `Int` or `Long` gives `Left` with
       * `Bitwise operation requires integral types, got: ` and the value.
       */
      def apply(x: DynamicValue, y: DynamicValue): Either[SchemaError, DynamicValue] = applyOp(x, y, _ & _)
    }

    /**
     * Bitwise or.
     */
    case object Or extends BitwiseOperator {

      /**
       * Returns `x | y` of the two values widened to `Long`s with their sign,
       * as a `Long` when either is a `Long` and as an `Int` otherwise; a value
       * that is not a `Byte`, `Short`, `Int` or `Long` gives `Left` with
       * `Bitwise operation requires integral types, got: ` and the value.
       */
      def apply(x: DynamicValue, y: DynamicValue): Either[SchemaError, DynamicValue] = applyOp(x, y, _ | _)
    }

    /**
     * Bitwise exclusive or.
     */
    case object Xor extends BitwiseOperator {

      /**
       * Returns the exclusive or of the two values widened to `Long`s with
       * their sign, as a `Long` when either is a `Long` and as an `Int`
       * otherwise; a value that is not a `Byte`, `Short`, `Int` or `Long` gives
       * `Left` with `Bitwise operation requires integral types, got: ` and the
       * value.
       */
      def apply(x: DynamicValue, y: DynamicValue): Either[SchemaError, DynamicValue] = applyOp(x, y, _ ^ _)
    }

    /**
     * `x << y`. A `Byte`, `Short` or `Int` is shifted as a `Long` and the
     * result truncated to an `Int`, so a shift by 32 to 63 gives 0 (a known
     * bug).
     */
    case object LeftShift extends BitwiseOperator {

      /**
       * Returns `x << y`, with `y` taken as an `Int`. A `Long` `x` gives a
       * `Long`, shifted by `y` modulo 64; any other `x` is shifted as a `Long`
       * and truncated to an `Int`, so `Int` 1 shifted by 31 gives
       * `Int.MinValue` and a shift by 32 to 63 gives 0 (a known bug). A value
       * that is not a `Byte`, `Short`, `Int` or `Long` gives `Left` with
       * `Bitwise operation requires integral types, got: ` and the value.
       */
      def apply(x: DynamicValue, y: DynamicValue): Either[SchemaError, DynamicValue] =
        applyShift(x, y, (a, b) => a << b)
    }

    /**
     * `x >> y`, keeping the sign. A `Byte`, `Short` or `Int` is shifted as a
     * `Long`, so a shift by 32 to 63 gives 0 or -1 (a known bug).
     */
    case object RightShift extends BitwiseOperator {

      /**
       * Returns `x >> y`, keeping the sign, with `y` taken as an `Int`. A
       * `Long` `x` gives a `Long`; any other `x` is shifted as a `Long` and
       * truncated to an `Int`, so a shift by 32 to 63 gives 0 or -1 (a known
       * bug). A value that is not a `Byte`, `Short`, `Int` or `Long` gives
       * `Left` with `Bitwise operation requires integral types, got: ` and the
       * value.
       */
      def apply(x: DynamicValue, y: DynamicValue): Either[SchemaError, DynamicValue] =
        applyShift(x, y, (a, b) => a >> b)
    }

    /**
     * `x >>> y`, filling a `Long` with zeros. A `Byte`, `Short` or `Int` is
     * shifted as a sign-extended `Long` and the result truncated to an `Int`,
     * so a negative value shifted by 1 to 32 stays negative (`-8 >>> 1` gives
     * -4, not 2147483644), and a shift by 32 to 63 is not reduced modulo 32 (a
     * known bug).
     */
    case object UnsignedRightShift extends BitwiseOperator {

      /**
       * Returns `x >>> y`, with `y` taken as an `Int`. A `Long` `x` gives a
       * `Long` filled with zeros (`-8L >>> 1` gives 9223372036854775804); any
       * other `x` is shifted as a sign-extended `Long` and truncated to an
       * `Int`, so `-8 >>> 1` gives -4 (a known bug). A value that is not a
       * `Byte`, `Short`, `Int` or `Long` gives `Left` with
       * `Bitwise operation requires integral types, got: ` and the value.
       */
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

  /**
   * Inverts the bits of every result of `expr`, which must be a `Byte`,
   * `Short`, `Int` or `Long`; the type is kept.
   */
  final case class BitwiseNot(expr: DynamicSchemaExpr) extends DynamicSchemaExpr {

    /**
     * Returns the bitwise complement of each result of `expr`, with the same
     * type (`Byte` 5 gives `Byte` -6); a result that is not a `Byte`, `Short`,
     * `Int` or `Long` fails with `Bitwise NOT requires integral type, got: `
     * and the value.
     */
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

  /**
   * Concatenates every pair of results of `left` and `right`.
   */
  final case class StringConcat(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr
  ) extends DynamicSchemaExpr {

    /**
     * Returns `x + y` for every pair of results of `left` and `right`, the
     * `left` value varying slowest; a result that is not a `String` fails with
     * `Expected String, got: ` and the value.
     */
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

  /**
   * Yields `true` where the whole of a result of `string` matches a result of
   * `regex`, as `java.util.regex.Pattern.matches` does. An invalid pattern
   * makes it fail.
   */
  final case class StringRegexMatch(
    regex: DynamicSchemaExpr,
    string: DynamicSchemaExpr
  ) extends DynamicSchemaExpr {

    /**
     * Returns, for every pair of results of `regex` and `string` (the `regex`
     * value varying slowest), whether the whole string matches the pattern, so
     * `b` does not match `abc`. An invalid pattern fails with
     * `Invalid regex pattern: ` and the description of the syntax error, such
     * as `Unclosed group`, and a result that is not a `String` fails with
     * `Expected String, got: ` and the value.
     */
    def eval(input: DynamicValue): Either[SchemaError, Seq[DynamicValue]] =
      for {
        xs      <- regex.eval(input)
        ys      <- string.eval(input)
        results <- {
          val computed = for { x <- xs; y <- ys } yield {
            for {
              regexStr <- extractString(x)
              str      <- extractString(y)
              matches  <- safeRegexMatch(regexStr, str)
            } yield DynamicValue.Primitive(PrimitiveValue.Boolean(matches))
          }
          sequence(computed)
        }
      } yield results
  }

  /**
   * Yields the length of every result of `string`.
   */
  final case class StringLength(string: DynamicSchemaExpr) extends DynamicSchemaExpr {

    /**
     * Returns the length of each result of `string` as an `Int`, in UTF-16 code
     * units, so an emoji counts as 2; a result that is not a `String` fails
     * with `Expected String, got: ` and the value.
     */
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

  /**
   * Yields the part of a result of `string` from `start` (inclusive) to `end`
   * (exclusive), for every combination of results. A negative index, a `start`
   * after `end` or an `end` past the length makes it fail.
   */
  final case class StringSubstring(
    string: DynamicSchemaExpr,
    start: DynamicSchemaExpr,
    end: DynamicSchemaExpr
  ) extends DynamicSchemaExpr {

    /**
     * Returns `s.substring(start, end)` for every combination of results, the
     * `string` value varying slowest, then `start`. A negative index fails with
     * `Substring indices must be non-negative: start=..., end=...`, a `start`
     * after `end` with `Substring start index ... exceeds end index ...`, and
     * an `end` past the length with
     * `Substring end index ... exceeds string length ...`. The indices must be
     * `Int` values (another value fails with `Expected Int, got: ` and the
     * value), and a result that is not a `String` fails with
     * `Expected String, got: ` and the value.
     */
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
              slice    <- safeSubstring(str, startInt, endInt)
            } yield DynamicValue.Primitive(PrimitiveValue.String(slice))
          }
          sequence(computed)
        }
      } yield results
  }

  /**
   * Removes leading and trailing whitespace from every result of `string`, as
   * `String#trim` does.
   */
  final case class StringTrim(string: DynamicSchemaExpr) extends DynamicSchemaExpr {

    /**
     * Returns `String#trim` of each result, which removes spaces and control
     * characters at both ends but keeps a non-breaking space; a result that is
     * not a `String` fails with `Expected String, got: ` and the value.
     */
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

  /**
   * Upper-cases every result of `string` with the default locale.
   */
  final case class StringToUpperCase(string: DynamicSchemaExpr) extends DynamicSchemaExpr {

    /**
     * Returns `String#toUpperCase` of each result, in the default locale, so
     * `straße` gives `STRASSE`; a result that is not a `String` fails with
     * `Expected String, got: ` and the value.
     */
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

  /**
   * Lower-cases every result of `string` with the default locale.
   */
  final case class StringToLowerCase(string: DynamicSchemaExpr) extends DynamicSchemaExpr {

    /**
     * Returns `String#toLowerCase` of each result, in the default locale; a
     * result that is not a `String` fails with `Expected String, got: ` and the
     * value.
     */
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

  /**
   * Replaces every occurrence of a result of `target` (as plain text) with a
   * result of `replacement` in a result of `string`, for every combination of
   * results.
   */
  final case class StringReplace(
    string: DynamicSchemaExpr,
    target: DynamicSchemaExpr,
    replacement: DynamicSchemaExpr
  ) extends DynamicSchemaExpr {

    /**
     * Returns `s.replace(target, replacement)` for every combination of
     * results, the `string` value varying slowest, then `target`. It replaces
     * each occurrence of `target` as plain text, from left to right without
     * overlap (`aaa` with `aa` replaced by `b` gives `ba`), and an empty
     * `target` inserts the replacement before every character and at the end; a
     * result that is not a `String` fails with `Expected String, got: ` and the
     * value.
     */
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

  /**
   * Yields `true` where a result of `string` starts with a result of `prefix`.
   */
  final case class StringStartsWith(
    string: DynamicSchemaExpr,
    prefix: DynamicSchemaExpr
  ) extends DynamicSchemaExpr {

    /**
     * Returns `s.startsWith(prefix)` for every pair of results, the `string`
     * value varying slowest, so an empty prefix gives `true`; a result that is
     * not a `String` fails with `Expected String, got: ` and the value.
     */
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

  /**
   * Yields `true` where a result of `string` ends with a result of `suffix`.
   */
  final case class StringEndsWith(
    string: DynamicSchemaExpr,
    suffix: DynamicSchemaExpr
  ) extends DynamicSchemaExpr {

    /**
     * Returns `s.endsWith(suffix)` for every pair of results, the `string`
     * value varying slowest, so an empty suffix gives `true`; a result that is
     * not a `String` fails with `Expected String, got: ` and the value.
     */
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

  /**
   * Yields `true` where a result of `string` contains a result of `substring`.
   */
  final case class StringContains(
    string: DynamicSchemaExpr,
    substring: DynamicSchemaExpr
  ) extends DynamicSchemaExpr {

    /**
     * Returns `s.contains(substring)` for every pair of results, the `string`
     * value varying slowest; the test is case-sensitive and an empty
     * `substring` gives `true`. A result that is not a `String` fails with
     * `Expected String, got: ` and the value.
     */
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

  /**
   * Yields the index of the first occurrence of a result of `substring` in a
   * result of `string`, or -1.
   */
  final case class StringIndexOf(
    string: DynamicSchemaExpr,
    substring: DynamicSchemaExpr
  ) extends DynamicSchemaExpr {

    /**
     * Returns `s.indexOf(substring)` for every pair of results, the `string`
     * value varying slowest: the index of the first occurrence, -1 when there
     * is none, and 0 for an empty `substring`. A result that is not a `String`
     * fails with `Expected String, got: ` and the value.
     */
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
