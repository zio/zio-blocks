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

import scala.util.Try

/**
 * An expression that computes values of type `B` from a value of type `A`, kept
 * as a tree of [[DynamicSchemaExpr]] nodes instead of a function, so it can be
 * evaluated and also inspected. A query library can translate it into SQL or
 * another query language, and migrations evaluate the tree directly.
 *
 * Build one with the operators of [[Optic]] (`===`, `>`, `+`, `&&`, `!`,
 * `concat`, `matches`, `length` and others), or with the constructors of the
 * companion, and run it with `eval`. An expression yields a `Seq`, because an
 * optic such as a [[Traversal]] can focus on several values: a unary operator
 * yields one result per value, and a binary operator one result for each pair
 * of left and right values.
 *
 * {{{
 * case class Book(title: String, year: Int)
 * object Book extends CompanionOptics[Book] {
 *   implicit val schema: Schema[Book] = Schema.derived
 *   val title: Lens[Book, String] = $(_.title)
 *   val year: Lens[Book, Int]     = $(_.year)
 * }
 *
 * val recentZio: SchemaExpr[Book, Boolean] = (Book.year >= 2020) && Book.title.matches("Zio.*")
 *
 * recentZio.eval(Book("Zionomicon", 2021))           // Right(Chunk(true))
 * recentZio.eval(Book("Programming in Scala", 2008)) // Right(Chunk(false))
 * }}}
 *
 * @tparam A
 *   the input type
 * @tparam B
 *   the type of the results
 * @param dynamic
 *   the expression tree
 * @param inputSchema
 *   converts the input to a [[DynamicValue]] before evaluation
 * @param outputSchema
 *   the schema of the results. `eval` converts them with its own implicit
 *   schema instead, and [[SchemaExpr.optic]] sets this to `Schema[Unit]`
 * @see
 *   [[https://zioblocks.com/reference/schema/schema-expr/ SchemaExpr]]
 */
final case class SchemaExpr[A, B](
  dynamic: DynamicSchemaExpr,
  inputSchema: Schema[A],
  outputSchema: Schema[B]
) {

  /**
   * Evaluates this expression on `input` and converts each result with `schema`
   * (not with `outputSchema`).
   *
   * Returns `Left` with an [[OpticCheck]]:
   *   - when an optic step fails: `UnexpectedCase` (the actual case of an
   *     `Option` is reported as `Option`), `SequenceIndexOutOfBounds`,
   *     `EmptySequence`, `MissingKey` or `EmptyMap`;
   *   - with a `DynamicConversionError` when an operator fails, for example on
   *     a division by zero, a value of the wrong type or an invalid regular
   *     expression;
   *   - with one `DynamicConversionError` for each result that `schema` cannot
   *     convert.
   *
   * {{{
   * case class Author(name: String)
   * case class Book(title: String, authors: List[Author])
   * object Book extends CompanionOptics[Book] {
   *   implicit val schema: Schema[Book] = Schema.derived
   *   val authorNames: Traversal[Book, String] = $(_.authors.each.name)
   * }
   *
   * val zionomicon = Book("Zionomicon", List(Author("John A. De Goes"), Author("Adam Fraser")))
   *
   * Book.authorNames.length.eval(zionomicon)                // Right(Chunk(15,11))
   * Book.authorNames.length.eval(zionomicon.copy(authors = Nil)).isLeft // true, an EmptySequence
   * }}}
   *
   * @tparam B1
   *   the type to convert the results to, `B` or a supertype
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
        Left(schemaErrorToOpticCheck(error, input, dynamic))
    }
  }

  /**
   * Evaluates this expression on `input` and returns the results as
   * [[DynamicValue]]s, without converting them. Errors are reported as in
   * `eval`.
   */
  def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] = {
    val dynamicInput = inputSchema.toDynamicValue(input)
    dynamic.eval(dynamicInput).left.map(e => schemaErrorToOpticCheck(e, input, dynamic))
  }

  private def schemaErrorToOpticCheck(error: SchemaError, input: A, expr: DynamicSchemaExpr): OpticCheck = {
    val opticError = error.errors.head match {
      case SchemaError.Message(source, details) if details.startsWith("UnexpectedCase:") =>
        val expectedCase = details.stripPrefix("UnexpectedCase: expected ").takeWhile(_ != ',')
        val actualCase   = details.dropWhile(_ != ',').stripPrefix(", got ")
        val fullPath     = extractFullPath(expr).getOrElse(source)
        OpticCheck.UnexpectedCase(expectedCase, actualCase, fullPath, source, input)

      case SchemaError.Message(source, "EmptySequence") =>
        val fullPath = extractFullPath(expr).getOrElse(source)
        OpticCheck.EmptySequence(fullPath, source)

      case SchemaError.Message(source, details) if details.startsWith("SequenceIndexOutOfBounds:") =>
        val fullPath = extractFullPath(expr).getOrElse(source)
        parseSequenceIndexOutOfBounds(details) match {
          case Some((index, size)) => OpticCheck.SequenceIndexOutOfBounds(fullPath, source, index, size)
          case None                => OpticCheck.DynamicConversionError(error.message)
        }

      case SchemaError.Message(source, details) if details.startsWith("MissingKey:") =>
        val fullPath = extractFullPath(expr).getOrElse(source)
        val keyStr   = details.stripPrefix("MissingKey:")
        OpticCheck.MissingKey(fullPath, source, keyStr)

      case SchemaError.Message(source, "EmptyMap") =>
        val fullPath = extractFullPath(expr).getOrElse(source)
        OpticCheck.EmptyMap(fullPath, source)

      case _ =>
        OpticCheck.DynamicConversionError(error.message)
    }
    new OpticCheck(new ::(opticError, Nil))
  }

  private def extractFullPath(expr: DynamicSchemaExpr): Option[DynamicOptic] = expr match {
    case DynamicSchemaExpr.Select(path)                  => Some(path)
    case DynamicSchemaExpr.Relational(left, _, _)        => extractFullPath(left)
    case DynamicSchemaExpr.Logical(left, _, _)           => extractFullPath(left)
    case DynamicSchemaExpr.Not(e)                        => extractFullPath(e)
    case DynamicSchemaExpr.Arithmetic(left, _, _, _)     => extractFullPath(left)
    case DynamicSchemaExpr.Bitwise(left, _, _)           => extractFullPath(left)
    case DynamicSchemaExpr.BitwiseNot(e)                 => extractFullPath(e)
    case DynamicSchemaExpr.StringConcat(left, _)         => extractFullPath(left)
    case DynamicSchemaExpr.StringRegexMatch(_, string)   => extractFullPath(string)
    case DynamicSchemaExpr.StringLength(string)          => extractFullPath(string)
    case DynamicSchemaExpr.StringSubstring(string, _, _) => extractFullPath(string)
    case DynamicSchemaExpr.StringTrim(string)            => extractFullPath(string)
    case DynamicSchemaExpr.StringToUpperCase(string)     => extractFullPath(string)
    case DynamicSchemaExpr.StringToLowerCase(string)     => extractFullPath(string)
    case DynamicSchemaExpr.StringReplace(string, _, _)   => extractFullPath(string)
    case DynamicSchemaExpr.StringStartsWith(string, _)   => extractFullPath(string)
    case DynamicSchemaExpr.StringEndsWith(string, _)     => extractFullPath(string)
    case DynamicSchemaExpr.StringContains(string, _)     => extractFullPath(string)
    case DynamicSchemaExpr.StringIndexOf(string, _)      => extractFullPath(string)
    case _                                               => None
  }

  private def parseSequenceIndexOutOfBounds(details: String): Option[(Int, Int)] = {
    val prefix = "SequenceIndexOutOfBounds: "
    if (!details.startsWith(prefix)) None
    else {
      val parts = details.stripPrefix(prefix).split(", ")
      if (parts.length != 2) None
      else {
        val maybeIndex = Try(parts(0).stripPrefix("index ").toInt).toOption
        val maybeSize  = Try(parts(1).stripPrefix("size ").toInt).toOption
        for {
          index <- maybeIndex
          size  <- maybeSize
        } yield (index, size)
      }
    }
  }

  /**
   * Returns `dynamic`, the expression tree.
   */
  def toDynamic: DynamicSchemaExpr = dynamic
}

/**
 * Constructors for [[SchemaExpr]] and the operator types they take. The
 * operators of [[Optic]] call these. A binary constructor converts the input
 * with the input schema of its left (or first) operand (`stringRegexMatch` uses
 * its `string` operand), so put a `literal` on the right: with a literal on the
 * left, the other side sees no input and the expression yields no results (see
 * `docs/dev/issues.md`).
 */
object SchemaExpr {

  /**
   * Logical combinators for boolean SchemaExprs.
   *
   * Provided as an implicit class rather than direct methods on SchemaExpr so
   * that downstream libraries can supply their own implicit class with &&/||
   * overloads for a different result type (e.g. a DDB-specific or SQL-specific
   * expression ADT).
   *
   * In Scala 2, a direct method found by name suppresses implicit receiver
   * views even when the argument type does not match (SLS §7.3). Moving &&/||
   * here lifts that suppression: when a downstream library brings in its own
   * ops class via an explicit import, that import-scope implicit takes priority
   * over this companion-scope implicit (Scala 2 implicit priority: local/import
   * > companion), so the downstream overload wins for `se && downstreamValue`
   * without ambiguity in that context.
   *
   * Note: two companion-scope implicits providing &&/|| would still be
   * ambiguous for `se && se2`. Downstream libraries should introduce their ops
   * via explicit import (or a user-facing object) rather than a companion to
   * avoid that scenario.
   */
  implicit final class BooleanOps[A, B](private val self: SchemaExpr[A, B]) extends AnyVal {

    /**
     * Returns an expression that is true where both this and `that` are true.
     * Every result of this is combined with every result of `that`, and a
     * result that is not a `Boolean` makes evaluation fail. There is no
     * short-circuit: the right side is evaluated whatever the left values are,
     * so `false && e` fails when `e` fails; when the left side fails, its error
     * is returned.
     */
    def and(that: SchemaExpr[A, Boolean])(implicit
      @scala.annotation.unused ev: B <:< Boolean
    ): SchemaExpr[A, Boolean] =
      SchemaExpr(
        DynamicSchemaExpr.Logical(self.dynamic, that.dynamic, DynamicSchemaExpr.LogicalOperator.And),
        self.inputSchema,
        Schema[Boolean]
      )

    /**
     * Returns an expression that is true where this or `that` is true. Every
     * result of this is combined with every result of `that`, and a result that
     * is not a `Boolean` makes evaluation fail. There is no short-circuit: the
     * right side is evaluated whatever the left values are, so `false && e`
     * fails when `e` fails; when the left side fails, its error is returned.
     */
    def or(that: SchemaExpr[A, Boolean])(implicit
      @scala.annotation.unused ev: B <:< Boolean
    ): SchemaExpr[A, Boolean] =
      SchemaExpr(
        DynamicSchemaExpr.Logical(self.dynamic, that.dynamic, DynamicSchemaExpr.LogicalOperator.Or),
        self.inputSchema,
        Schema[Boolean]
      )

    /**
     * The same as `and`.
     */
    def &&(that: SchemaExpr[A, Boolean])(implicit ev: B <:< Boolean): SchemaExpr[A, Boolean] = and(that)

    /**
     * The same as `or`.
     */
    def ||(that: SchemaExpr[A, Boolean])(implicit ev: B <:< Boolean): SchemaExpr[A, Boolean] = or(that)
  }

  private def outOfRange(value: Any, target: String): Either[Predef.String, DynamicValue] =
    Left(s"Value $value is out of range for $target")

  private def lossyConversion(value: Any, target: String): Either[Predef.String, DynamicValue] =
    Left(s"Value $value cannot be converted to $target without losing information")

  private def checkedIntegralNarrow(
    value: Long,
    min: Long,
    max: Long,
    target: String
  )(result: => DynamicValue): Either[Predef.String, DynamicValue] =
    if (value < min || value > max) outOfRange(value, target)
    else Right(result)

  private def checkedFiniteFloatToInt(value: Float): Either[Predef.String, DynamicValue] =
    if (!java.lang.Float.isFinite(value)) lossyConversion(value, "Int")
    else if (value < Int.MinValue.toFloat || value > Int.MaxValue.toFloat) outOfRange(value, "Int")
    else {
      val converted = value.toInt
      if (converted.toFloat != value) lossyConversion(value, "Int")
      else Right(DynamicValue.Primitive(PrimitiveValue.Int(converted)))
    }

  private def checkedFiniteFloatToLong(value: Float): Either[Predef.String, DynamicValue] =
    if (!java.lang.Float.isFinite(value)) lossyConversion(value, "Long")
    else if (value < Long.MinValue.toFloat || value > Long.MaxValue.toFloat) outOfRange(value, "Long")
    else {
      val converted = value.toLong
      if (converted.toFloat != value) lossyConversion(value, "Long")
      else Right(DynamicValue.Primitive(PrimitiveValue.Long(converted)))
    }

  private def checkedFiniteDoubleToInt(value: Double): Either[Predef.String, DynamicValue] =
    if (!java.lang.Double.isFinite(value)) lossyConversion(value, "Int")
    else if (value < Int.MinValue.toDouble || value > Int.MaxValue.toDouble) outOfRange(value, "Int")
    else {
      val converted = value.toInt
      if (converted.toDouble != value) lossyConversion(value, "Int")
      else Right(DynamicValue.Primitive(PrimitiveValue.Int(converted)))
    }

  private def checkedFiniteDoubleToLong(value: Double): Either[Predef.String, DynamicValue] =
    if (!java.lang.Double.isFinite(value)) lossyConversion(value, "Long")
    else if (value < Long.MinValue.toDouble || value > Long.MaxValue.toDouble) outOfRange(value, "Long")
    else {
      val converted = value.toLong
      if (converted.toDouble != value) lossyConversion(value, "Long")
      else Right(DynamicValue.Primitive(PrimitiveValue.Long(converted)))
    }

  private def checkedDoubleToFloat(value: Double): Either[Predef.String, DynamicValue] =
    if (!java.lang.Double.isFinite(value)) lossyConversion(value, "Float")
    else {
      val converted = value.toFloat
      if (!java.lang.Float.isFinite(converted) || converted.toDouble != value)
        lossyConversion(value, "Float")
      else Right(DynamicValue.Primitive(PrimitiveValue.Float(converted)))
    }

  /**
   * Returns a literal expression of the default value of `schema`, or `None`
   * when the schema has no default value. Its type says it takes any input, but
   * it converts its input with `schema`, so evaluating it on a value that is
   * not an `A` throws a `ClassCastException` (see `docs/dev/issues.md`).
   */
  def schemaDefault[A](implicit schema: Schema[A]): Option[SchemaExpr[Any, A]] =
    schema.getDefaultValue.map { defaultValue =>
      val dynValue = schema.toDynamicValue(defaultValue)
      SchemaExpr(
        DynamicSchemaExpr.Literal(dynValue, schema),
        schema.asInstanceOf[Schema[Any]],
        schema
      )
    }

  /**
   * Returns an expression that ignores its input and yields `value`, encoded
   * with `schema`. `S` is the input type of the expressions it is combined
   * with. Put it on the right of a binary constructor: its input schema
   * discards the input, so on the left it leaves the other side with no input
   * and the expression yields no results (see `docs/dev/issues.md`).
   */
  def literal[S, A](value: A)(implicit schema: Schema[A]): SchemaExpr[S, A] =
    SchemaExpr(
      DynamicSchemaExpr.Literal(schema.toDynamicValue(value), schema),
      Schema[Unit].transform[S](_ => null.asInstanceOf[S], _ => ()),
      schema
    )

  /**
   * Returns an expression that yields the values `path` reaches in its input,
   * which `sourceSchema` converts to a [[DynamicValue]] first. A field that the
   * value lacks yields nothing; a different case, an index out of bounds, a
   * missing map key, an `elements` step that finds only empty sequences and a
   * `mapKeys` or `mapValues` step that finds only empty maps make evaluation
   * fail (see [[DynamicSchemaExpr.Select]]), and so does a search step. Its
   * `outputSchema` is `Schema[Unit]` whatever `A` is; `eval` does not use it.
   */
  def optic[S, A](path: DynamicOptic, sourceSchema: Schema[S]): SchemaExpr[S, A] =
    SchemaExpr(
      DynamicSchemaExpr.Select(path),
      sourceSchema,
      Schema[Unit].asInstanceOf[Schema[A]]
    )

  /**
   * Returns `left && right` or `left || right`, as `operator` says.
   */
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

  /**
   * Returns an expression that compares every result of `left` with every
   * result of `right`. `Equal` and `NotEqual` use `==`; the others use the
   * order of `DynamicValue#compare`, so values of different primitive types
   * compare by type, not by number.
   */
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

  /**
   * Returns an expression that negates every result of `expr`.
   */
  def not[S](expr: SchemaExpr[S, Boolean]): SchemaExpr[S, Boolean] =
    SchemaExpr(
      DynamicSchemaExpr.Not(expr.dynamic),
      expr.inputSchema,
      Schema[Boolean]
    )

  /**
   * Returns an expression that applies `operator` to every pair of results of
   * `left` and `right`. Both must be primitives of `numericType`. `Byte`,
   * `Short`, `Int` and `Long` results of `Add`, `Subtract` and `Multiply` wrap
   * on overflow, and a division or modulo by zero fails evaluation. `Pow` on
   * `Byte` to `Double` computes `Math.pow` on `Double`s and converts the result
   * back: an `Int` or `Long` result that is too large or infinite (0 to a
   * negative power) saturates at the type's limit, `Byte` and `Short` results
   * go through `Int` and then wrap, and a negative exponent on an integer type
   * gives 0 except for the bases 1, -1 and 0. On `BigInt` and `BigDecimal` the
   * exponent must be a whole number from 0 to 10000.
   */
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

  /**
   * Returns an expression that applies `operator` to every pair of results of
   * `left` and `right`, which must be `Byte`, `Short`, `Int` or `Long` values.
   * The result is a `Long` when either value is a `Long` (for a shift, when the
   * left one is) and an `Int` otherwise, also for `Byte` and `Short`.
   */
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

  /**
   * Returns an expression that inverts the bits of every result of `expr`,
   * which must be a `Byte`, `Short`, `Int` or `Long`; the type is kept.
   */
  def bitwiseNot[S, A](expr: SchemaExpr[S, A]): SchemaExpr[S, A] =
    SchemaExpr(
      DynamicSchemaExpr.BitwiseNot(expr.dynamic),
      expr.inputSchema,
      expr.outputSchema
    )

  /**
   * Returns an expression that concatenates every pair of results of `left` and
   * `right`.
   */
  def stringConcat[S](left: SchemaExpr[S, String], right: SchemaExpr[S, String]): SchemaExpr[S, String] =
    SchemaExpr(
      DynamicSchemaExpr.StringConcat(left.dynamic, right.dynamic),
      left.inputSchema,
      Schema[String]
    )

  /**
   * Returns an expression that is true where the whole of a result of `string`
   * matches a result of `regex`, as `java.util.regex.Pattern.matches` does. An
   * invalid pattern makes evaluation fail.
   */
  def stringRegexMatch[S](regex: SchemaExpr[S, String], string: SchemaExpr[S, String]): SchemaExpr[S, Boolean] =
    SchemaExpr(
      DynamicSchemaExpr.StringRegexMatch(regex.dynamic, string.dynamic),
      string.inputSchema,
      Schema[Boolean]
    )

  /**
   * Returns an expression that yields the length of every result of `string`.
   */
  def stringLength[S](string: SchemaExpr[S, String]): SchemaExpr[S, Int] =
    SchemaExpr(
      DynamicSchemaExpr.StringLength(string.dynamic),
      string.inputSchema,
      Schema[Int]
    )

  /**
   * Returns an expression that yields the part of every result of `string` from
   * `start` (inclusive) to `end` (exclusive), for every combination of results.
   * A negative index, a `start` after `end` or an `end` past the length makes
   * evaluation fail.
   */
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

  /**
   * Returns an expression that removes leading and trailing whitespace from
   * every result of `string`, as `String#trim` does.
   */
  def stringTrim[S](string: SchemaExpr[S, String]): SchemaExpr[S, String] =
    SchemaExpr(
      DynamicSchemaExpr.StringTrim(string.dynamic),
      string.inputSchema,
      Schema[String]
    )

  /**
   * Returns an expression that upper-cases every result of `string` with the
   * default locale.
   */
  def stringToUpperCase[S](string: SchemaExpr[S, String]): SchemaExpr[S, String] =
    SchemaExpr(
      DynamicSchemaExpr.StringToUpperCase(string.dynamic),
      string.inputSchema,
      Schema[String]
    )

  /**
   * Returns an expression that lower-cases every result of `string` with the
   * default locale.
   */
  def stringToLowerCase[S](string: SchemaExpr[S, String]): SchemaExpr[S, String] =
    SchemaExpr(
      DynamicSchemaExpr.StringToLowerCase(string.dynamic),
      string.inputSchema,
      Schema[String]
    )

  /**
   * Returns an expression that replaces every occurrence of a result of
   * `target` (as plain text, not a pattern) with a result of `replacement`, in
   * every result of `string`.
   */
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

  /**
   * Returns an expression that is true where a result of `string` starts with a
   * result of `prefix`.
   */
  def stringStartsWith[S](string: SchemaExpr[S, String], prefix: SchemaExpr[S, String]): SchemaExpr[S, Boolean] =
    SchemaExpr(
      DynamicSchemaExpr.StringStartsWith(string.dynamic, prefix.dynamic),
      string.inputSchema,
      Schema[Boolean]
    )

  /**
   * Returns an expression that is true where a result of `string` ends with a
   * result of `suffix`.
   */
  def stringEndsWith[S](string: SchemaExpr[S, String], suffix: SchemaExpr[S, String]): SchemaExpr[S, Boolean] =
    SchemaExpr(
      DynamicSchemaExpr.StringEndsWith(string.dynamic, suffix.dynamic),
      string.inputSchema,
      Schema[Boolean]
    )

  /**
   * Returns an expression that is true where a result of `string` contains a
   * result of `substring`.
   */
  def stringContains[S](string: SchemaExpr[S, String], substring: SchemaExpr[S, String]): SchemaExpr[S, Boolean] =
    SchemaExpr(
      DynamicSchemaExpr.StringContains(string.dynamic, substring.dynamic),
      string.inputSchema,
      Schema[Boolean]
    )

  /**
   * Returns an expression that yields the index of the first occurrence of a
   * result of `substring` in a result of `string`, or -1.
   */
  def stringIndexOf[S](string: SchemaExpr[S, String], substring: SchemaExpr[S, String]): SchemaExpr[S, Int] =
    SchemaExpr(
      DynamicSchemaExpr.StringIndexOf(string.dynamic, substring.dynamic),
      string.inputSchema,
      Schema[Int]
    )

  /**
   * The operator of [[SchemaExpr.logical]]: `And` or `Or`.
   */
  sealed trait LogicalOperator

  /**
   * The cases of [[LogicalOperator]].
   */
  object LogicalOperator {
    case object And extends LogicalOperator
    case object Or  extends LogicalOperator
  }

  /**
   * The operator of [[SchemaExpr.relational]]: `LessThan` (`<`), `GreaterThan`
   * (`>`), `LessThanOrEqual` (`<=`), `GreaterThanOrEqual` (`>=`), `Equal` or
   * `NotEqual`.
   */
  sealed trait RelationalOperator

  /**
   * The cases of [[RelationalOperator]].
   */
  object RelationalOperator {
    case object LessThan           extends RelationalOperator
    case object GreaterThan        extends RelationalOperator
    case object LessThanOrEqual    extends RelationalOperator
    case object GreaterThanOrEqual extends RelationalOperator
    case object Equal              extends RelationalOperator
    case object NotEqual           extends RelationalOperator
  }

  /**
   * The operator of [[SchemaExpr.arithmetic]]: `Add`, `Subtract`, `Multiply`,
   * `Divide` (integer division truncates), `Pow` or `Modulo` (the remainder,
   * with the sign of the dividend).
   */
  sealed trait ArithmeticOperator

  /**
   * The cases of [[ArithmeticOperator]].
   */
  object ArithmeticOperator {
    case object Add      extends ArithmeticOperator
    case object Subtract extends ArithmeticOperator
    case object Multiply extends ArithmeticOperator
    case object Divide   extends ArithmeticOperator
    case object Pow      extends ArithmeticOperator
    case object Modulo   extends ArithmeticOperator
  }

  /**
   * The operator of [[SchemaExpr.bitwise]]: `And`, `Or`, `Xor`, `LeftShift`
   * (`<<`), `RightShift` (`>>`, keeping the sign) or `UnsignedRightShift`
   * (`>>>`, filling with zeros).
   */
  sealed trait BitwiseOperator

  /**
   * The cases of [[BitwiseOperator]].
   */
  object BitwiseOperator {
    case object And                extends BitwiseOperator
    case object Or                 extends BitwiseOperator
    case object Xor                extends BitwiseOperator
    case object LeftShift          extends BitwiseOperator
    case object RightShift         extends BitwiseOperator
    case object UnsignedRightShift extends BitwiseOperator
  }

  /**
   * Returns `conversionType` unchanged. To use a conversion in an expression,
   * wrap it in [[DynamicSchemaExpr.PrimitiveConversion]].
   */
  def PrimitiveConversion[S](conversionType: ConversionType): ConversionType = conversionType

  /**
   * A conversion of a primitive [[DynamicValue]] from one primitive type to
   * another, used by [[DynamicSchemaExpr.PrimitiveConversion]]. A conversion
   * that can lose information checks that it does not and fails otherwise,
   * except the conversions to `Float` from `Int`, `Long` and `String` and to
   * `Double` from `Long` and `String`, which round to the nearest representable
   * number; `StringToFloat` and `StringToDouble` also accept `NaN` and turn an
   * out-of-range number into an infinity.
   */
  sealed trait ConversionType {

    /**
     * Converts `value`, or returns `Left` with a message when `value` is not a
     * primitive of the source type or the conversion fails.
     */
    def convert(value: DynamicValue): Either[Predef.String, DynamicValue]
  }

  /**
   * The available conversions.
   */
  object ConversionType {
    // Numeric widening conversions (lossless)
    /**
     * Converts a `Byte` to a `Short`.
     */
    case object ByteToShort extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Byte(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Short(v.toShort)))
        case _ => Left(s"Expected Byte, got $value")
      }
    }

    /**
     * Converts a `Byte` to an `Int`.
     */
    case object ByteToInt extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Byte(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Int(v.toInt)))
        case _ => Left(s"Expected Byte, got $value")
      }
    }

    /**
     * Converts a `Byte` to a `Long`.
     */
    case object ByteToLong extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Byte(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Long(v.toLong)))
        case _ => Left(s"Expected Byte, got $value")
      }
    }

    /**
     * Converts a `Short` to an `Int`.
     */
    case object ShortToInt extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Short(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Int(v.toInt)))
        case _ => Left(s"Expected Short, got $value")
      }
    }

    /**
     * Converts a `Short` to a `Long`.
     */
    case object ShortToLong extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Short(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Long(v.toLong)))
        case _ => Left(s"Expected Short, got $value")
      }
    }

    /**
     * Converts an `Int` to a `Long`.
     */
    case object IntToLong extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Long(v.toLong)))
        case _ => Left(s"Expected Int, got $value")
      }
    }

    /**
     * Converts a `Float` to a `Double`.
     */
    case object FloatToDouble extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Float(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Double(v.toDouble)))
        case _ => Left(s"Expected Float, got $value")
      }
    }

    // Numeric narrowing conversions (potentially lossy)
    /**
     * Converts a `Short` to a `Byte`, failing when the value is out of range.
     */
    case object ShortToByte extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Short(v)) =>
          checkedIntegralNarrow(v.toLong, Byte.MinValue.toLong, Byte.MaxValue.toLong, "Byte")(
            DynamicValue.Primitive(PrimitiveValue.Byte(v.toByte))
          )
        case _ => Left(s"Expected Short, got $value")
      }
    }

    /**
     * Converts an `Int` to a `Byte`, failing when the value is out of range.
     */
    case object IntToByte extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(v)) =>
          checkedIntegralNarrow(v.toLong, Byte.MinValue.toLong, Byte.MaxValue.toLong, "Byte")(
            DynamicValue.Primitive(PrimitiveValue.Byte(v.toByte))
          )
        case _ => Left(s"Expected Int, got $value")
      }
    }

    /**
     * Converts an `Int` to a `Short`, failing when the value is out of range.
     */
    case object IntToShort extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(v)) =>
          checkedIntegralNarrow(v.toLong, Short.MinValue.toLong, Short.MaxValue.toLong, "Short")(
            DynamicValue.Primitive(PrimitiveValue.Short(v.toShort))
          )
        case _ => Left(s"Expected Int, got $value")
      }
    }

    /**
     * Converts a `Long` to a `Byte`, failing when the value is out of range.
     */
    case object LongToByte extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Long(v)) =>
          checkedIntegralNarrow(v, Byte.MinValue.toLong, Byte.MaxValue.toLong, "Byte")(
            DynamicValue.Primitive(PrimitiveValue.Byte(v.toByte))
          )
        case _ => Left(s"Expected Long, got $value")
      }
    }

    /**
     * Converts a `Long` to a `Short`, failing when the value is out of range.
     */
    case object LongToShort extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Long(v)) =>
          checkedIntegralNarrow(v, Short.MinValue.toLong, Short.MaxValue.toLong, "Short")(
            DynamicValue.Primitive(PrimitiveValue.Short(v.toShort))
          )
        case _ => Left(s"Expected Long, got $value")
      }
    }

    /**
     * Converts a `Long` to an `Int`, failing when the value is out of range.
     */
    case object LongToInt extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Long(v)) =>
          checkedIntegralNarrow(v, Int.MinValue.toLong, Int.MaxValue.toLong, "Int")(
            DynamicValue.Primitive(PrimitiveValue.Int(v.toInt))
          )
        case _ => Left(s"Expected Long, got $value")
      }
    }

    /**
     * Converts a `Double` to a `Float`, failing when the value is not finite or
     * a `Float` cannot hold it exactly.
     */
    case object DoubleToFloat extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Double(v)) =>
          checkedDoubleToFloat(v)
        case _ => Left(s"Expected Double, got $value")
      }
    }

    // Integer to floating point
    /**
     * Converts an `Int` to a `Float`, rounding to the nearest representable
     * number.
     */
    case object IntToFloat extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Float(v.toFloat)))
        case _ => Left(s"Expected Int, got $value")
      }
    }

    /**
     * Converts an `Int` to a `Double`.
     */
    case object IntToDouble extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Double(v.toDouble)))
        case _ => Left(s"Expected Int, got $value")
      }
    }

    /**
     * Converts a `Long` to a `Float`, rounding to the nearest representable
     * number.
     */
    case object LongToFloat extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Long(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Float(v.toFloat)))
        case _ => Left(s"Expected Long, got $value")
      }
    }

    /**
     * Converts a `Long` to a `Double`, rounding to the nearest representable
     * number.
     */
    case object LongToDouble extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Long(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Double(v.toDouble)))
        case _ => Left(s"Expected Long, got $value")
      }
    }

    // String conversions
    /**
     * Converts an `Int` to a `String` with `toString`.
     */
    case object IntToString extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
        case _ => Left(s"Expected Int, got $value")
      }
    }

    /**
     * Converts a `Long` to a `String` with `toString`.
     */
    case object LongToString extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Long(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
        case _ => Left(s"Expected Long, got $value")
      }
    }

    /**
     * Converts a `Double` to a `String` with `toString`.
     */
    case object DoubleToString extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Double(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
        case _ => Left(s"Expected Double, got $value")
      }
    }

    /**
     * Converts a `Boolean` to a `String` with `toString`.
     */
    case object BooleanToString extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Boolean(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
        case _ => Left(s"Expected Boolean, got $value")
      }
    }

    // String parsing (may fail)
    /**
     * Converts a `String` to an `Int`, failing when the string is not a valid
     * number of that type.
     */
    case object StringToInt extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.String(v)) =>
          try Right(DynamicValue.Primitive(PrimitiveValue.Int(v.toInt)))
          catch { case _: NumberFormatException => Left(s"Cannot parse '$v' as Int") }
        case _ => Left(s"Expected String, got $value")
      }
    }

    /**
     * Converts a `String` to a `Long`, failing when the string is not a valid
     * number of that type.
     */
    case object StringToLong extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.String(v)) =>
          try Right(DynamicValue.Primitive(PrimitiveValue.Long(v.toLong)))
          catch { case _: NumberFormatException => Left(s"Cannot parse '$v' as Long") }
        case _ => Left(s"Expected String, got $value")
      }
    }

    /**
     * Converts a `String` to a `Double` with `String#toDouble`, failing when
     * the string is not a number. It rounds to the nearest `Double`, accepts
     * `NaN`, and turns an out-of-range number into an infinity.
     */
    case object StringToDouble extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.String(v)) =>
          try Right(DynamicValue.Primitive(PrimitiveValue.Double(v.toDouble)))
          catch { case _: NumberFormatException => Left(s"Cannot parse '$v' as Double") }
        case _ => Left(s"Expected String, got $value")
      }
    }

    /**
     * Converts a `String` to a `Boolean`: `true` or `false`, ignoring case, and
     * failing otherwise.
     */
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
    /**
     * Converts a `Char` to an `Int`: its UTF-16 code unit.
     */
    case object CharToInt extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Char(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Int(v.toInt)))
        case _ => Left(s"Expected Char, got $value")
      }
    }

    /**
     * Converts an `Int` to a `Char`, failing when the value is out of range.
     */
    case object IntToChar extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(v)) =>
          checkedIntegralNarrow(v.toLong, Char.MinValue.toLong, Char.MaxValue.toLong, "Char")(
            DynamicValue.Primitive(PrimitiveValue.Char(v.toChar))
          )
        case _ => Left(s"Expected Int, got $value")
      }
    }

    /**
     * Converts a `Char` to a `String` with `toString`.
     */
    case object CharToString extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Char(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
        case _ => Left(s"Expected Char, got $value")
      }
    }

    // Byte/Short to Float/Double
    /**
     * Converts a `Byte` to a `Float`.
     */
    case object ByteToFloat extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Byte(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Float(v.toFloat)))
        case _ => Left(s"Expected Byte, got $value")
      }
    }

    /**
     * Converts a `Byte` to a `Double`.
     */
    case object ByteToDouble extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Byte(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Double(v.toDouble)))
        case _ => Left(s"Expected Byte, got $value")
      }
    }

    /**
     * Converts a `Short` to a `Float`.
     */
    case object ShortToFloat extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Short(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Float(v.toFloat)))
        case _ => Left(s"Expected Short, got $value")
      }
    }

    /**
     * Converts a `Short` to a `Double`.
     */
    case object ShortToDouble extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Short(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Double(v.toDouble)))
        case _ => Left(s"Expected Short, got $value")
      }
    }

    /**
     * Converts a `Float` to an `Int`, failing when the value is not finite, out
     * of range or not a whole number.
     */
    case object FloatToInt extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Float(v)) =>
          checkedFiniteFloatToInt(v)
        case _ => Left(s"Expected Float, got $value")
      }
    }

    /**
     * Converts a `Float` to a `Long`, failing when the value is not finite, out
     * of range or not a whole number.
     */
    case object FloatToLong extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Float(v)) =>
          checkedFiniteFloatToLong(v)
        case _ => Left(s"Expected Float, got $value")
      }
    }

    /**
     * Converts a `Double` to an `Int`, failing when the value is not finite,
     * out of range or not a whole number.
     */
    case object DoubleToInt extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Double(v)) =>
          checkedFiniteDoubleToInt(v)
        case _ => Left(s"Expected Double, got $value")
      }
    }

    /**
     * Converts a `Double` to a `Long`, failing when the value is not finite,
     * out of range or not a whole number.
     */
    case object DoubleToLong extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Double(v)) =>
          checkedFiniteDoubleToLong(v)
        case _ => Left(s"Expected Double, got $value")
      }
    }

    // More String conversions
    /**
     * Converts a `Float` to a `String` with `toString`.
     */
    case object FloatToString extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Float(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
        case _ => Left(s"Expected Float, got $value")
      }
    }

    /**
     * Converts a `Short` to a `String` with `toString`.
     */
    case object ShortToString extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Short(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
        case _ => Left(s"Expected Short, got $value")
      }
    }

    /**
     * Converts a `Byte` to a `String` with `toString`.
     */
    case object ByteToString extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Byte(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
        case _ => Left(s"Expected Byte, got $value")
      }
    }

    /**
     * Converts a `String` to a `Float` with `String#toFloat`, failing when the
     * string is not a number. It rounds to the nearest `Float`, accepts `NaN`,
     * and turns an out-of-range number into an infinity.
     */
    case object StringToFloat extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.String(v)) =>
          try Right(DynamicValue.Primitive(PrimitiveValue.Float(v.toFloat)))
          catch { case _: NumberFormatException => Left(s"Cannot parse '$v' as Float") }
        case _ => Left(s"Expected String, got $value")
      }
    }

    /**
     * Converts a `String` to a `Short`, failing when the string is not a valid
     * number of that type.
     */
    case object StringToShort extends ConversionType {
      def convert(value: DynamicValue): Either[Predef.String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.String(v)) =>
          try Right(DynamicValue.Primitive(PrimitiveValue.Short(v.toShort)))
          catch { case _: NumberFormatException => Left(s"Cannot parse '$v' as Short") }
        case _ => Left(s"Expected String, got $value")
      }
    }

    /**
     * Converts a `String` to a `Byte`, failing when the string is not a valid
     * number of that type.
     */
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
