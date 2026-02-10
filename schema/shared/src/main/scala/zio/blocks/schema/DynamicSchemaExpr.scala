package zio.blocks.schema

import zio.blocks.chunk.Chunk
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.typeid.TypeId

/**
 * A type-parameter-free expression ADT that operates on `DynamicValue`.
 *
 * `DynamicSchemaExpr` is the serializable counterpart of `SchemaExpr[A, B]`.
 * Unlike `SchemaExpr`, it has no type parameters, making it fully serializable
 * and suitable for storage in registries, offline migration application, and
 * DDL generation.
 */
sealed trait DynamicSchemaExpr {

  /**
   * Evaluate this expression on a DynamicValue input.
   */
  def eval(input: DynamicValue): Either[String, Seq[DynamicValue]]

  /**
   * Return the structural inverse of this expression, if one exists.
   */
  def inverse: Option[DynamicSchemaExpr]
}

object DynamicSchemaExpr {

  /**
   * Represents the schema-defined default value for a field. The value is
   * already converted to DynamicValue at macro time.
   */
  final case class DefaultValue(value: DynamicValue) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[String, Seq[DynamicValue]] =
      Right(value :: Nil)

    def inverse: Option[DynamicSchemaExpr] = Some(this)
  }

  /**
   * A literal DynamicValue constant.
   */
  final case class Literal(value: DynamicValue) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[String, Seq[DynamicValue]] =
      Right(value :: Nil)

    def inverse: Option[DynamicSchemaExpr] = None
  }

  /**
   * Evaluates a DynamicOptic path on the input DynamicValue.
   */
  final case class Dynamic(optic: DynamicOptic) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[String, Seq[DynamicValue]] = {
      var current: Seq[DynamicValue] = Seq(input)
      val nodes                      = optic.nodes
      var i                          = 0
      while (i < nodes.length) {
        nodes(i) match {
          case DynamicOptic.Node.Field(name) =>
            val buf         = Seq.newBuilder[DynamicValue]
            var err: String = null
            val iter        = current.iterator
            while (iter.hasNext && (err eq null)) {
              iter.next() match {
                case DynamicValue.Record(fields) =>
                  fields.find(_._1 == name) match {
                    case Some((_, v)) => buf += v
                    case None         => err = s"Field '$name' not found"
                  }
                case _ => err = s"Expected Record for field '$name'"
              }
            }
            if (err ne null) return Left(err)
            current = buf.result()

          case DynamicOptic.Node.Case(name) =>
            val buf         = Seq.newBuilder[DynamicValue]
            var err: String = null
            val iter        = current.iterator
            while (iter.hasNext && (err eq null)) {
              iter.next() match {
                case DynamicValue.Variant(caseName, value) =>
                  if (caseName == name) buf += value
                  else err = s"UNEXPECTED_CASE:$name:$caseName:$i"
                case _ => err = s"Expected Variant for case '$name'"
              }
            }
            if (err ne null) return Left(err)
            current = buf.result()

          case DynamicOptic.Node.Elements =>
            val buf         = Seq.newBuilder[DynamicValue]
            var err: String = null
            val iter        = current.iterator
            while (iter.hasNext && (err eq null)) {
              iter.next() match {
                case DynamicValue.Sequence(elements) =>
                  if (elements.isEmpty) err = s"EMPTY_SEQUENCE:$i"
                  else buf ++= elements
                case _ => err = "Expected Sequence for elements traversal"
              }
            }
            if (err ne null) return Left(err)
            current = buf.result()

          case DynamicOptic.Node.MapKeys =>
            val buf         = Seq.newBuilder[DynamicValue]
            var err: String = null
            val iter        = current.iterator
            while (iter.hasNext && (err eq null)) {
              iter.next() match {
                case DynamicValue.Map(entries) =>
                  if (entries.isEmpty) err = "Empty map"
                  else entries.foreach { case (k, _) => buf += k }
                case _ => err = "Expected Map for mapKeys traversal"
              }
            }
            if (err ne null) return Left(err)
            current = buf.result()

          case DynamicOptic.Node.MapValues =>
            val buf         = Seq.newBuilder[DynamicValue]
            var err: String = null
            val iter        = current.iterator
            while (iter.hasNext && (err eq null)) {
              iter.next() match {
                case DynamicValue.Map(entries) =>
                  if (entries.isEmpty) err = "Empty map"
                  else entries.foreach { case (_, v) => buf += v }
                case _ => err = "Expected Map for mapValues traversal"
              }
            }
            if (err ne null) return Left(err)
            current = buf.result()

          case DynamicOptic.Node.Wrapped =>
            val buf         = Seq.newBuilder[DynamicValue]
            var err: String = null
            val iter        = current.iterator
            while (iter.hasNext && (err eq null)) {
              iter.next() match {
                case DynamicValue.Variant("Some", value) => buf += value
                case DynamicValue.Variant("None", _)     => () // skip None
                case _                                   => err = "Expected optional Variant (Some/None) for wrapped traversal"
              }
            }
            if (err ne null) return Left(err)
            current = buf.result()

          case DynamicOptic.Node.AtIndex(index) =>
            val buf         = Seq.newBuilder[DynamicValue]
            var err: String = null
            val iter        = current.iterator
            while (iter.hasNext && (err eq null)) {
              iter.next() match {
                case DynamicValue.Sequence(elements) =>
                  if (index >= 0 && index < elements.length) buf += elements(index)
                  else err = s"Index $index out of bounds (size ${elements.length})"
                case _ => err = s"Expected Sequence for atIndex($index)"
              }
            }
            if (err ne null) return Left(err)
            current = buf.result()

          case DynamicOptic.Node.AtIndices(indices) =>
            val buf         = Seq.newBuilder[DynamicValue]
            var err: String = null
            val iter        = current.iterator
            while (iter.hasNext && (err eq null)) {
              iter.next() match {
                case DynamicValue.Sequence(elements) =>
                  indices.foreach { idx =>
                    if (err eq null) {
                      if (idx >= 0 && idx < elements.length) buf += elements(idx)
                      else err = s"Index $idx out of bounds (size ${elements.length})"
                    }
                  }
                case _ => err = "Expected Sequence for atIndices"
              }
            }
            if (err ne null) return Left(err)
            current = buf.result()

          case DynamicOptic.Node.AtMapKey(key) =>
            val buf         = Seq.newBuilder[DynamicValue]
            var err: String = null
            val iter        = current.iterator
            while (iter.hasNext && (err eq null)) {
              iter.next() match {
                case DynamicValue.Map(entries) =>
                  entries.find(_._1 == key) match {
                    case Some((_, v)) => buf += v
                    case None         => err = s"Map key not found: $key"
                  }
                case _ => err = "Expected Map for atMapKey"
              }
            }
            if (err ne null) return Left(err)
            current = buf.result()

          case DynamicOptic.Node.AtMapKeys(keys) =>
            val buf         = Seq.newBuilder[DynamicValue]
            var err: String = null
            val iter        = current.iterator
            val keySet      = keys.toSet
            while (iter.hasNext && (err eq null)) {
              iter.next() match {
                case DynamicValue.Map(entries) =>
                  entries.foreach { case (k, v) =>
                    if (keySet.contains(k)) buf += v
                  }
                case _ => err = "Expected Map for atMapKeys"
              }
            }
            if (err ne null) return Left(err)
            current = buf.result()
        }
        i += 1
      }
      Right(current)
    }

    def inverse: Option[DynamicSchemaExpr] = Some(this)
  }

  /**
   * Arithmetic operation on numeric DynamicValues.
   */
  final case class Arithmetic(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr,
    operator: ArithmeticOperator,
    numericType: NumericType
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[String, Seq[DynamicValue]] =
      for {
        xs      <- left.eval(input)
        ys      <- right.eval(input)
        results <- {
          val buf         = Seq.newBuilder[DynamicValue]
          var err: String = null
          for { x <- xs; y <- ys; if err eq null } {
            numericType.apply(x, y, operator) match {
              case Right(v) => buf += v
              case Left(e)  => err = e
            }
          }
          if (err ne null) Left(err) else Right(buf.result())
        }
      } yield results

    def inverse: Option[DynamicSchemaExpr] = operator match {
      case ArithmeticOperator.Add      => Some(Arithmetic(left, right, ArithmeticOperator.Subtract, numericType))
      case ArithmeticOperator.Subtract => Some(Arithmetic(left, right, ArithmeticOperator.Add, numericType))
      case ArithmeticOperator.Multiply => Some(Arithmetic(left, right, ArithmeticOperator.Divide, numericType))
      case ArithmeticOperator.Divide   => Some(Arithmetic(left, right, ArithmeticOperator.Multiply, numericType))
    }
  }

  /**
   * Concatenates two string expressions.
   */
  final case class StringConcat(left: DynamicSchemaExpr, right: DynamicSchemaExpr) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[String, Seq[DynamicValue]] =
      for {
        xs <- left.eval(input)
        ys <- right.eval(input)
      } yield
        for { x <- xs; y <- ys } yield {
          val xStr = extractString(x)
          val yStr = extractString(y)
          DynamicValue.Primitive(PrimitiveValue.String(xStr + yStr))
        }

    def inverse: Option[DynamicSchemaExpr] = None
  }

  /**
   * Splits a string by a delimiter.
   */
  final case class StringSplit(string: DynamicSchemaExpr, delimiter: String) extends DynamicSchemaExpr {
    private val quotedDelimiter: String = java.util.regex.Pattern.quote(delimiter)

    def eval(input: DynamicValue): Either[String, Seq[DynamicValue]] =
      for {
        xs <- string.eval(input)
      } yield xs.flatMap { x =>
        extractString(x).split(quotedDelimiter, -1).map(s => DynamicValue.Primitive(PrimitiveValue.String(s))).toSeq
      }

    def inverse: Option[DynamicSchemaExpr] = None
  }

  /**
   * Converts a string to uppercase.
   */
  final case class StringUppercase(string: DynamicSchemaExpr) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[String, Seq[DynamicValue]] =
      for {
        xs <- string.eval(input)
      } yield xs.map(x => DynamicValue.Primitive(PrimitiveValue.String(extractString(x).toUpperCase)))

    def inverse: Option[DynamicSchemaExpr] = Some(StringLowercase(string))
  }

  /**
   * Converts a string to lowercase.
   */
  final case class StringLowercase(string: DynamicSchemaExpr) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[String, Seq[DynamicValue]] =
      for {
        xs <- string.eval(input)
      } yield xs.map(x => DynamicValue.Primitive(PrimitiveValue.String(extractString(x).toLowerCase)))

    def inverse: Option[DynamicSchemaExpr] = Some(StringUppercase(string))
  }

  /**
   * Returns the length of a string.
   */
  final case class StringLength(string: DynamicSchemaExpr) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[String, Seq[DynamicValue]] =
      for {
        xs <- string.eval(input)
      } yield xs.map(x => DynamicValue.Primitive(PrimitiveValue.Int(extractString(x).length)))

    def inverse: Option[DynamicSchemaExpr] = None
  }

  /**
   * Tests whether a string matches a regex pattern.
   */
  final case class StringRegexMatch(regex: DynamicSchemaExpr, string: DynamicSchemaExpr) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[String, Seq[DynamicValue]] =
      for {
        xs <- regex.eval(input)
        ys <- string.eval(input)
      } yield
        for { x <- xs; y <- ys } yield DynamicValue.Primitive(
          PrimitiveValue.Boolean(extractString(x).matches(extractString(y)))
        )

    def inverse: Option[DynamicSchemaExpr] = None
  }

  /**
   * Boolean negation.
   */
  final case class Not(expr: DynamicSchemaExpr) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[String, Seq[DynamicValue]] =
      for {
        xs <- expr.eval(input)
      } yield xs.map {
        case DynamicValue.Primitive(PrimitiveValue.Boolean(b)) =>
          DynamicValue.Primitive(PrimitiveValue.Boolean(!b))
        case other => throw new IllegalArgumentException(s"Not: expected Boolean, got $other")
      }

    def inverse: Option[DynamicSchemaExpr] = Some(Not(expr))
  }

  /**
   * Relational comparison.
   */
  final case class Relational(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr,
    operator: RelationalOperator
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[String, Seq[DynamicValue]] =
      for {
        xs <- left.eval(input)
        ys <- right.eval(input)
      } yield
        for { x <- xs; y <- ys } yield {
          val result = operator match {
            case RelationalOperator.Equal              => x == y
            case RelationalOperator.NotEqual           => x != y
            case RelationalOperator.LessThan           => x < y
            case RelationalOperator.LessThanOrEqual    => x <= y
            case RelationalOperator.GreaterThan        => x > y
            case RelationalOperator.GreaterThanOrEqual => x >= y
          }
          DynamicValue.Primitive(PrimitiveValue.Boolean(result))
        }

    def inverse: Option[DynamicSchemaExpr] = None
  }

  /**
   * Logical combination (And / Or).
   */
  final case class Logical(
    left: DynamicSchemaExpr,
    right: DynamicSchemaExpr,
    operator: LogicalOperator
  ) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[String, Seq[DynamicValue]] =
      for {
        xs <- left.eval(input)
        ys <- right.eval(input)
      } yield
        for { x <- xs; y <- ys } yield {
          val xb = x match {
            case DynamicValue.Primitive(PrimitiveValue.Boolean(b)) => b
            case _                                                 => throw new IllegalArgumentException(s"Logical: expected Boolean, got $x")
          }
          val yb = y match {
            case DynamicValue.Primitive(PrimitiveValue.Boolean(b)) => b
            case _                                                 => throw new IllegalArgumentException(s"Logical: expected Boolean, got $y")
          }
          val result = operator match {
            case LogicalOperator.And => xb && yb
            case LogicalOperator.Or  => xb || yb
          }
          DynamicValue.Primitive(PrimitiveValue.Boolean(result))
        }

    def inverse: Option[DynamicSchemaExpr] = None
  }

  /**
   * Type conversion using a PrimitiveConverter.
   */
  final case class Convert(expr: DynamicSchemaExpr, converter: PrimitiveConverter) extends DynamicSchemaExpr {
    def eval(input: DynamicValue): Either[String, Seq[DynamicValue]] =
      expr.eval(input).flatMap { dynamicValues =>
        val results = dynamicValues.map(converter.convert)
        results.collectFirst { case Left(err) => err } match {
          case Some(err) => Left(err)
          case None      => Right(results.collect { case Right(v) => v })
        }
      }

    def inverse: Option[DynamicSchemaExpr] = Some(Convert(expr, converter.reverse))
  }

  // --- Operator enums ---

  sealed trait ArithmeticOperator
  object ArithmeticOperator {
    case object Add      extends ArithmeticOperator
    case object Subtract extends ArithmeticOperator
    case object Multiply extends ArithmeticOperator
    case object Divide   extends ArithmeticOperator

    implicit lazy val addSchema: Schema[Add.type]           = caseObjectSchema(Add, TypeId.of[Add.type])
    implicit lazy val subtractSchema: Schema[Subtract.type] = caseObjectSchema(Subtract, TypeId.of[Subtract.type])
    implicit lazy val multiplySchema: Schema[Multiply.type] = caseObjectSchema(Multiply, TypeId.of[Multiply.type])
    implicit lazy val divideSchema: Schema[Divide.type]     = caseObjectSchema(Divide, TypeId.of[Divide.type])

    implicit lazy val schema: Schema[ArithmeticOperator] = new Schema(
      new Reflect.Variant[Binding, ArithmeticOperator](
        cases = Chunk(
          addSchema.reflect.asTerm("Add"),
          subtractSchema.reflect.asTerm("Subtract"),
          multiplySchema.reflect.asTerm("Multiply"),
          divideSchema.reflect.asTerm("Divide")
        ),
        typeId = TypeId.of[ArithmeticOperator],
        variantBinding = new Binding.Variant(
          discriminator = new Discriminator[ArithmeticOperator] {
            def discriminate(a: ArithmeticOperator): Int = a match {
              case Add => 0; case Subtract => 1; case Multiply => 2; case Divide => 3
            }
          },
          matchers = Matchers(
            new Matcher[Add.type] {
              def downcastOrNull(a: Any): Add.type =
                if (a.asInstanceOf[AnyRef] eq Add) Add else null.asInstanceOf[Add.type]
            },
            new Matcher[Subtract.type] {
              def downcastOrNull(a: Any): Subtract.type =
                if (a.asInstanceOf[AnyRef] eq Subtract) Subtract else null.asInstanceOf[Subtract.type]
            },
            new Matcher[Multiply.type] {
              def downcastOrNull(a: Any): Multiply.type =
                if (a.asInstanceOf[AnyRef] eq Multiply) Multiply else null.asInstanceOf[Multiply.type]
            },
            new Matcher[Divide.type] {
              def downcastOrNull(a: Any): Divide.type =
                if (a.asInstanceOf[AnyRef] eq Divide) Divide else null.asInstanceOf[Divide.type]
            }
          )
        ),
        modifiers = Chunk.empty
      )
    )
  }

  sealed trait RelationalOperator
  object RelationalOperator {
    case object LessThan           extends RelationalOperator
    case object GreaterThan        extends RelationalOperator
    case object LessThanOrEqual    extends RelationalOperator
    case object GreaterThanOrEqual extends RelationalOperator
    case object Equal              extends RelationalOperator
    case object NotEqual           extends RelationalOperator

    implicit lazy val lessThanSchema: Schema[LessThan.type]       = caseObjectSchema(LessThan, TypeId.of[LessThan.type])
    implicit lazy val greaterThanSchema: Schema[GreaterThan.type] =
      caseObjectSchema(GreaterThan, TypeId.of[GreaterThan.type])
    implicit lazy val lessThanOrEqualSchema: Schema[LessThanOrEqual.type] =
      caseObjectSchema(LessThanOrEqual, TypeId.of[LessThanOrEqual.type])
    implicit lazy val greaterThanOrEqualSchema: Schema[GreaterThanOrEqual.type] =
      caseObjectSchema(GreaterThanOrEqual, TypeId.of[GreaterThanOrEqual.type])
    implicit lazy val equalSchema: Schema[Equal.type]       = caseObjectSchema(Equal, TypeId.of[Equal.type])
    implicit lazy val notEqualSchema: Schema[NotEqual.type] = caseObjectSchema(NotEqual, TypeId.of[NotEqual.type])

    implicit lazy val schema: Schema[RelationalOperator] = new Schema(
      new Reflect.Variant[Binding, RelationalOperator](
        cases = Chunk(
          lessThanSchema.reflect.asTerm("LessThan"),
          greaterThanSchema.reflect.asTerm("GreaterThan"),
          lessThanOrEqualSchema.reflect.asTerm("LessThanOrEqual"),
          greaterThanOrEqualSchema.reflect.asTerm("GreaterThanOrEqual"),
          equalSchema.reflect.asTerm("Equal"),
          notEqualSchema.reflect.asTerm("NotEqual")
        ),
        typeId = TypeId.of[RelationalOperator],
        variantBinding = new Binding.Variant(
          discriminator = new Discriminator[RelationalOperator] {
            def discriminate(a: RelationalOperator): Int = a match {
              case LessThan           => 0; case GreaterThan => 1; case LessThanOrEqual => 2
              case GreaterThanOrEqual => 3; case Equal       => 4; case NotEqual        => 5
            }
          },
          matchers = Matchers(
            new Matcher[LessThan.type] {
              def downcastOrNull(a: Any): LessThan.type =
                if (a.asInstanceOf[AnyRef] eq LessThan) LessThan else null.asInstanceOf[LessThan.type]
            },
            new Matcher[GreaterThan.type] {
              def downcastOrNull(a: Any): GreaterThan.type =
                if (a.asInstanceOf[AnyRef] eq GreaterThan) GreaterThan else null.asInstanceOf[GreaterThan.type]
            },
            new Matcher[LessThanOrEqual.type] {
              def downcastOrNull(a: Any): LessThanOrEqual.type = if (a.asInstanceOf[AnyRef] eq LessThanOrEqual)
                LessThanOrEqual
              else null.asInstanceOf[LessThanOrEqual.type]
            },
            new Matcher[GreaterThanOrEqual.type] {
              def downcastOrNull(a: Any): GreaterThanOrEqual.type = if (a.asInstanceOf[AnyRef] eq GreaterThanOrEqual)
                GreaterThanOrEqual
              else null.asInstanceOf[GreaterThanOrEqual.type]
            },
            new Matcher[Equal.type] {
              def downcastOrNull(a: Any): Equal.type =
                if (a.asInstanceOf[AnyRef] eq Equal) Equal else null.asInstanceOf[Equal.type]
            },
            new Matcher[NotEqual.type] {
              def downcastOrNull(a: Any): NotEqual.type =
                if (a.asInstanceOf[AnyRef] eq NotEqual) NotEqual else null.asInstanceOf[NotEqual.type]
            }
          )
        ),
        modifiers = Chunk.empty
      )
    )
  }

  sealed trait LogicalOperator
  object LogicalOperator {
    case object And extends LogicalOperator
    case object Or  extends LogicalOperator

    implicit lazy val andSchema: Schema[And.type] = caseObjectSchema(And, TypeId.of[And.type])
    implicit lazy val orSchema: Schema[Or.type]   = caseObjectSchema(Or, TypeId.of[Or.type])

    implicit lazy val schema: Schema[LogicalOperator] = new Schema(
      new Reflect.Variant[Binding, LogicalOperator](
        cases = Chunk(andSchema.reflect.asTerm("And"), orSchema.reflect.asTerm("Or")),
        typeId = TypeId.of[LogicalOperator],
        variantBinding = new Binding.Variant(
          discriminator = new Discriminator[LogicalOperator] {
            def discriminate(a: LogicalOperator): Int = a match { case And => 0; case Or => 1 }
          },
          matchers = Matchers(
            new Matcher[And.type] {
              def downcastOrNull(a: Any): And.type =
                if (a.asInstanceOf[AnyRef] eq And) And else null.asInstanceOf[And.type]
            },
            new Matcher[Or.type] {
              def downcastOrNull(a: Any): Or.type = if (a.asInstanceOf[AnyRef] eq Or) Or else null.asInstanceOf[Or.type]
            }
          )
        ),
        modifiers = Chunk.empty
      )
    )
  }

  /**
   * Represents the numeric type for arithmetic operations, enabling
   * type-parameter-free arithmetic on DynamicValues.
   */
  sealed trait NumericType {
    def apply(x: DynamicValue, y: DynamicValue, op: ArithmeticOperator): Either[String, DynamicValue]
  }

  object NumericType {
    case object ByteType extends NumericType {
      def apply(x: DynamicValue, y: DynamicValue, op: ArithmeticOperator): Either[String, DynamicValue] =
        numericOp(
          x,
          y,
          op,
          { case DynamicValue.Primitive(PrimitiveValue.Byte(v)) => v },
          (v: Byte) => DynamicValue.Primitive(PrimitiveValue.Byte(v)),
          implicitly[Numeric[Byte]]
        )
    }

    case object ShortType extends NumericType {
      def apply(x: DynamicValue, y: DynamicValue, op: ArithmeticOperator): Either[String, DynamicValue] =
        numericOp(
          x,
          y,
          op,
          { case DynamicValue.Primitive(PrimitiveValue.Short(v)) => v },
          (v: Short) => DynamicValue.Primitive(PrimitiveValue.Short(v)),
          implicitly[Numeric[Short]]
        )
    }

    case object IntType extends NumericType {
      def apply(x: DynamicValue, y: DynamicValue, op: ArithmeticOperator): Either[String, DynamicValue] =
        numericOp(
          x,
          y,
          op,
          { case DynamicValue.Primitive(PrimitiveValue.Int(v)) => v },
          (v: Int) => DynamicValue.Primitive(PrimitiveValue.Int(v)),
          implicitly[Numeric[Int]]
        )
    }

    case object LongType extends NumericType {
      def apply(x: DynamicValue, y: DynamicValue, op: ArithmeticOperator): Either[String, DynamicValue] =
        numericOp(
          x,
          y,
          op,
          { case DynamicValue.Primitive(PrimitiveValue.Long(v)) => v },
          (v: Long) => DynamicValue.Primitive(PrimitiveValue.Long(v)),
          implicitly[Numeric[Long]]
        )
    }

    case object FloatType extends NumericType {
      def apply(x: DynamicValue, y: DynamicValue, op: ArithmeticOperator): Either[String, DynamicValue] =
        numericOp(
          x,
          y,
          op,
          { case DynamicValue.Primitive(PrimitiveValue.Float(v)) => v },
          (v: Float) => DynamicValue.Primitive(PrimitiveValue.Float(v)),
          implicitly[Numeric[Float]]
        )
    }

    case object DoubleType extends NumericType {
      def apply(x: DynamicValue, y: DynamicValue, op: ArithmeticOperator): Either[String, DynamicValue] =
        numericOp(
          x,
          y,
          op,
          { case DynamicValue.Primitive(PrimitiveValue.Double(v)) => v },
          (v: Double) => DynamicValue.Primitive(PrimitiveValue.Double(v)),
          implicitly[Numeric[Double]]
        )
    }

    case object BigIntType extends NumericType {
      def apply(x: DynamicValue, y: DynamicValue, op: ArithmeticOperator): Either[String, DynamicValue] =
        numericOp(
          x,
          y,
          op,
          { case DynamicValue.Primitive(PrimitiveValue.BigInt(v)) => v },
          (v: BigInt) => DynamicValue.Primitive(PrimitiveValue.BigInt(v)),
          implicitly[Numeric[BigInt]]
        )
    }

    case object BigDecimalType extends NumericType {
      def apply(x: DynamicValue, y: DynamicValue, op: ArithmeticOperator): Either[String, DynamicValue] =
        numericOp(
          x,
          y,
          op,
          { case DynamicValue.Primitive(PrimitiveValue.BigDecimal(v)) => v },
          (v: BigDecimal) => DynamicValue.Primitive(PrimitiveValue.BigDecimal(v)),
          implicitly[Numeric[BigDecimal]]
        )
    }

    private def numericOp[A](
      x: DynamicValue,
      y: DynamicValue,
      op: ArithmeticOperator,
      extract: PartialFunction[DynamicValue, A],
      wrap: A => DynamicValue,
      num: Numeric[A]
    ): Either[String, DynamicValue] = {
      val aOpt: Option[A] = extract.lift(x)
      val bOpt: Option[A] = extract.lift(y)
      (aOpt, bOpt) match {
        case (None, _)          => Left(s"Cannot extract numeric value from $x")
        case (_, None)          => Left(s"Cannot extract numeric value from $y")
        case (Some(a), Some(b)) =>
          val result = op match {
            case ArithmeticOperator.Add      => num.plus(a, b)
            case ArithmeticOperator.Subtract => num.minus(a, b)
            case ArithmeticOperator.Multiply => num.times(a, b)
            case ArithmeticOperator.Divide   =>
              num match {
                case frac: Fractional[A] => frac.div(a, b)
                case int: Integral[A]    => int.quot(a, b)
                case _                   => num.times(a, num.fromInt(0))
              }
          }
          Right(wrap(result))
      }
    }

    /**
     * Resolve the NumericType from an IsNumeric instance.
     */
    def fromIsNumeric[A](isNumeric: IsNumeric[A]): NumericType = isNumeric match {
      case IsNumeric.IsByte       => ByteType
      case IsNumeric.IsShort      => ShortType
      case IsNumeric.IsInt        => IntType
      case IsNumeric.IsLong       => LongType
      case IsNumeric.IsFloat      => FloatType
      case IsNumeric.IsDouble     => DoubleType
      case IsNumeric.IsBigInt     => BigIntType
      case IsNumeric.IsBigDecimal => BigDecimalType
      case _                      => throw new IllegalArgumentException(s"Unknown IsNumeric type: $isNumeric")
    }

    implicit lazy val byteTypeSchema: Schema[ByteType.type]     = caseObjectSchema(ByteType, TypeId.of[ByteType.type])
    implicit lazy val shortTypeSchema: Schema[ShortType.type]   = caseObjectSchema(ShortType, TypeId.of[ShortType.type])
    implicit lazy val intTypeSchema: Schema[IntType.type]       = caseObjectSchema(IntType, TypeId.of[IntType.type])
    implicit lazy val longTypeSchema: Schema[LongType.type]     = caseObjectSchema(LongType, TypeId.of[LongType.type])
    implicit lazy val floatTypeSchema: Schema[FloatType.type]   = caseObjectSchema(FloatType, TypeId.of[FloatType.type])
    implicit lazy val doubleTypeSchema: Schema[DoubleType.type] =
      caseObjectSchema(DoubleType, TypeId.of[DoubleType.type])
    implicit lazy val bigIntTypeSchema: Schema[BigIntType.type] =
      caseObjectSchema(BigIntType, TypeId.of[BigIntType.type])
    implicit lazy val bigDecimalTypeSchema: Schema[BigDecimalType.type] =
      caseObjectSchema(BigDecimalType, TypeId.of[BigDecimalType.type])

    implicit lazy val schema: Schema[NumericType] = new Schema(
      new Reflect.Variant[Binding, NumericType](
        cases = Chunk(
          byteTypeSchema.reflect.asTerm("ByteType"),
          shortTypeSchema.reflect.asTerm("ShortType"),
          intTypeSchema.reflect.asTerm("IntType"),
          longTypeSchema.reflect.asTerm("LongType"),
          floatTypeSchema.reflect.asTerm("FloatType"),
          doubleTypeSchema.reflect.asTerm("DoubleType"),
          bigIntTypeSchema.reflect.asTerm("BigIntType"),
          bigDecimalTypeSchema.reflect.asTerm("BigDecimalType")
        ),
        typeId = TypeId.of[NumericType],
        variantBinding = new Binding.Variant(
          discriminator = new Discriminator[NumericType] {
            def discriminate(a: NumericType): Int = a match {
              case ByteType  => 0; case ShortType  => 1; case IntType    => 2; case LongType       => 3
              case FloatType => 4; case DoubleType => 5; case BigIntType => 6; case BigDecimalType => 7
            }
          },
          matchers = Matchers(
            new Matcher[ByteType.type] {
              def downcastOrNull(a: Any): ByteType.type =
                if (a.asInstanceOf[AnyRef] eq ByteType) ByteType else null.asInstanceOf[ByteType.type]
            },
            new Matcher[ShortType.type] {
              def downcastOrNull(a: Any): ShortType.type =
                if (a.asInstanceOf[AnyRef] eq ShortType) ShortType else null.asInstanceOf[ShortType.type]
            },
            new Matcher[IntType.type] {
              def downcastOrNull(a: Any): IntType.type =
                if (a.asInstanceOf[AnyRef] eq IntType) IntType else null.asInstanceOf[IntType.type]
            },
            new Matcher[LongType.type] {
              def downcastOrNull(a: Any): LongType.type =
                if (a.asInstanceOf[AnyRef] eq LongType) LongType else null.asInstanceOf[LongType.type]
            },
            new Matcher[FloatType.type] {
              def downcastOrNull(a: Any): FloatType.type =
                if (a.asInstanceOf[AnyRef] eq FloatType) FloatType else null.asInstanceOf[FloatType.type]
            },
            new Matcher[DoubleType.type] {
              def downcastOrNull(a: Any): DoubleType.type =
                if (a.asInstanceOf[AnyRef] eq DoubleType) DoubleType else null.asInstanceOf[DoubleType.type]
            },
            new Matcher[BigIntType.type] {
              def downcastOrNull(a: Any): BigIntType.type =
                if (a.asInstanceOf[AnyRef] eq BigIntType) BigIntType else null.asInstanceOf[BigIntType.type]
            },
            new Matcher[BigDecimalType.type] {
              def downcastOrNull(a: Any): BigDecimalType.type =
                if (a.asInstanceOf[AnyRef] eq BigDecimalType) BigDecimalType else null.asInstanceOf[BigDecimalType.type]
            }
          )
        ),
        modifiers = Chunk.empty
      )
    )
  }

  // --- Private helpers ---

  private def extractString(dv: DynamicValue): String = dv match {
    case DynamicValue.Primitive(PrimitiveValue.String(s)) => s
    case other                                            => throw new IllegalArgumentException(s"Expected String, got $other")
  }

  // --- Schema definitions ---

  private def caseObjectSchema[A](instance: A, id: TypeId[A]): Schema[A] =
    new Schema(
      new Reflect.Record[Binding, A](
        fields = Chunk.empty,
        typeId = id,
        recordBinding = new Binding.Record(new ConstantConstructor[A](instance), new ConstantDeconstructor[A]),
        modifiers = Chunk.empty
      )
    )

  // DynamicSchemaExpr subtype schemas (recursive — uses Deferred for self-references)

  private def obj1Schema[A](id: TypeId[A], fieldName: String, fieldSchema: => Schema[_])(
    mk: AnyRef => A,
    get: A => AnyRef
  ): Schema[A] = new Schema(
    new Reflect.Record[Binding, A](
      fields = Chunk(Reflect.Deferred(() => fieldSchema.reflect.asInstanceOf[Reflect.Bound[Any]]).asTerm(fieldName)),
      typeId = id,
      recordBinding = new Binding.Record(
        constructor = new Constructor[A] {
          def usedRegisters: RegisterOffset                       = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): A = mk(in.getObject(offset))
        },
        deconstructor = new Deconstructor[A] {
          def usedRegisters: RegisterOffset                                    = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: A): Unit = out.setObject(offset, get(in))
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private def obj2Schema[A](id: TypeId[A], f1: String, s1: => Schema[_], f2: String, s2: => Schema[_])(
    mk: (AnyRef, AnyRef) => A,
    get: A => (AnyRef, AnyRef)
  ): Schema[A] = new Schema(
    new Reflect.Record[Binding, A](
      fields = Chunk(
        Reflect.Deferred(() => s1.reflect.asInstanceOf[Reflect.Bound[Any]]).asTerm(f1),
        Reflect.Deferred(() => s2.reflect.asInstanceOf[Reflect.Bound[Any]]).asTerm(f2)
      ),
      typeId = id,
      recordBinding = new Binding.Record(
        constructor = new Constructor[A] {
          def usedRegisters: RegisterOffset                       = RegisterOffset(objects = 2)
          def construct(in: Registers, offset: RegisterOffset): A =
            mk(in.getObject(offset), in.getObject(RegisterOffset.incrementObjects(offset)))
        },
        deconstructor = new Deconstructor[A] {
          def usedRegisters: RegisterOffset                                    = RegisterOffset(objects = 2)
          def deconstruct(out: Registers, offset: RegisterOffset, in: A): Unit = {
            val (a, b) = get(in); out.setObject(offset, a); out.setObject(RegisterOffset.incrementObjects(offset), b)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private def obj3Schema[A](
    id: TypeId[A],
    f1: String,
    s1: => Schema[_],
    f2: String,
    s2: => Schema[_],
    f3: String,
    s3: => Schema[_]
  )(
    mk: (AnyRef, AnyRef, AnyRef) => A,
    get: A => (AnyRef, AnyRef, AnyRef)
  ): Schema[A] = new Schema(
    new Reflect.Record[Binding, A](
      fields = Chunk(
        Reflect.Deferred(() => s1.reflect.asInstanceOf[Reflect.Bound[Any]]).asTerm(f1),
        Reflect.Deferred(() => s2.reflect.asInstanceOf[Reflect.Bound[Any]]).asTerm(f2),
        Reflect.Deferred(() => s3.reflect.asInstanceOf[Reflect.Bound[Any]]).asTerm(f3)
      ),
      typeId = id,
      recordBinding = new Binding.Record(
        constructor = new Constructor[A] {
          def usedRegisters: RegisterOffset                       = RegisterOffset(objects = 3)
          def construct(in: Registers, offset: RegisterOffset): A = {
            val off1 = RegisterOffset.incrementObjects(offset)
            mk(in.getObject(offset), in.getObject(off1), in.getObject(RegisterOffset.incrementObjects(off1)))
          }
        },
        deconstructor = new Deconstructor[A] {
          def usedRegisters: RegisterOffset                                    = RegisterOffset(objects = 3)
          def deconstruct(out: Registers, offset: RegisterOffset, in: A): Unit = {
            val (a, b, c) = get(in); val off1 = RegisterOffset.incrementObjects(offset)
            out.setObject(offset, a); out.setObject(off1, b); out.setObject(RegisterOffset.incrementObjects(off1), c)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private def obj4Schema[A](
    id: TypeId[A],
    f1: String,
    s1: => Schema[_],
    f2: String,
    s2: => Schema[_],
    f3: String,
    s3: => Schema[_],
    f4: String,
    s4: => Schema[_]
  )(
    mk: (AnyRef, AnyRef, AnyRef, AnyRef) => A,
    get: A => (AnyRef, AnyRef, AnyRef, AnyRef)
  ): Schema[A] = new Schema(
    new Reflect.Record[Binding, A](
      fields = Chunk(
        Reflect.Deferred(() => s1.reflect.asInstanceOf[Reflect.Bound[Any]]).asTerm(f1),
        Reflect.Deferred(() => s2.reflect.asInstanceOf[Reflect.Bound[Any]]).asTerm(f2),
        Reflect.Deferred(() => s3.reflect.asInstanceOf[Reflect.Bound[Any]]).asTerm(f3),
        Reflect.Deferred(() => s4.reflect.asInstanceOf[Reflect.Bound[Any]]).asTerm(f4)
      ),
      typeId = id,
      recordBinding = new Binding.Record(
        constructor = new Constructor[A] {
          def usedRegisters: RegisterOffset                       = RegisterOffset(objects = 4)
          def construct(in: Registers, offset: RegisterOffset): A = {
            val off1 = RegisterOffset.incrementObjects(offset); val off2 = RegisterOffset.incrementObjects(off1)
            mk(
              in.getObject(offset),
              in.getObject(off1),
              in.getObject(off2),
              in.getObject(RegisterOffset.incrementObjects(off2))
            )
          }
        },
        deconstructor = new Deconstructor[A] {
          def usedRegisters: RegisterOffset                                    = RegisterOffset(objects = 4)
          def deconstruct(out: Registers, offset: RegisterOffset, in: A): Unit = {
            val (a, b, c, d) = get(in); val off1 = RegisterOffset.incrementObjects(offset);
            val off2         = RegisterOffset.incrementObjects(off1)
            out.setObject(offset, a); out.setObject(off1, b); out.setObject(off2, c);
            out.setObject(RegisterOffset.incrementObjects(off2), d)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val defaultValueSchema: Schema[DefaultValue] =
    obj1Schema(TypeId.of[DefaultValue], "value", Schema[DynamicValue])(
      a => DefaultValue(a.asInstanceOf[DynamicValue]),
      _.value
    )

  implicit lazy val literalSchema: Schema[Literal] =
    obj1Schema(TypeId.of[Literal], "value", Schema[DynamicValue])(a => Literal(a.asInstanceOf[DynamicValue]), _.value)

  implicit lazy val dynamicExprSchema: Schema[Dynamic] =
    obj1Schema(TypeId.of[Dynamic], "optic", Schema[DynamicOptic])(a => Dynamic(a.asInstanceOf[DynamicOptic]), _.optic)

  implicit lazy val arithmeticSchema: Schema[Arithmetic] =
    obj4Schema(
      TypeId.of[Arithmetic],
      "left",
      schema,
      "right",
      schema,
      "operator",
      ArithmeticOperator.schema,
      "numericType",
      NumericType.schema
    )(
      (a, b, c, d) =>
        Arithmetic(
          a.asInstanceOf[DynamicSchemaExpr],
          b.asInstanceOf[DynamicSchemaExpr],
          c.asInstanceOf[ArithmeticOperator],
          d.asInstanceOf[NumericType]
        ),
      a => (a.left, a.right, a.operator, a.numericType)
    )

  implicit lazy val stringConcatSchema: Schema[StringConcat] =
    obj2Schema(TypeId.of[StringConcat], "left", schema, "right", schema)(
      (a, b) => StringConcat(a.asInstanceOf[DynamicSchemaExpr], b.asInstanceOf[DynamicSchemaExpr]),
      a => (a.left, a.right)
    )

  implicit lazy val stringSplitSchema: Schema[StringSplit] =
    obj2Schema(TypeId.of[StringSplit], "string", schema, "delimiter", Schema.string)(
      (a, b) => StringSplit(a.asInstanceOf[DynamicSchemaExpr], b.asInstanceOf[String]),
      a => (a.string, a.delimiter)
    )

  implicit lazy val stringUppercaseSchema: Schema[StringUppercase] =
    obj1Schema(TypeId.of[StringUppercase], "string", schema)(
      a => StringUppercase(a.asInstanceOf[DynamicSchemaExpr]),
      _.string
    )

  implicit lazy val stringLowercaseSchema: Schema[StringLowercase] =
    obj1Schema(TypeId.of[StringLowercase], "string", schema)(
      a => StringLowercase(a.asInstanceOf[DynamicSchemaExpr]),
      _.string
    )

  implicit lazy val stringLengthSchema: Schema[StringLength] =
    obj1Schema(TypeId.of[StringLength], "string", schema)(
      a => StringLength(a.asInstanceOf[DynamicSchemaExpr]),
      _.string
    )

  implicit lazy val stringRegexMatchSchema: Schema[StringRegexMatch] =
    obj2Schema(TypeId.of[StringRegexMatch], "regex", schema, "string", schema)(
      (a, b) => StringRegexMatch(a.asInstanceOf[DynamicSchemaExpr], b.asInstanceOf[DynamicSchemaExpr]),
      a => (a.regex, a.string)
    )

  implicit lazy val notSchema: Schema[Not] =
    obj1Schema(TypeId.of[Not], "expr", schema)(a => Not(a.asInstanceOf[DynamicSchemaExpr]), _.expr)

  implicit lazy val relationalSchema: Schema[Relational] =
    obj3Schema(TypeId.of[Relational], "left", schema, "right", schema, "operator", RelationalOperator.schema)(
      (a, b, c) =>
        Relational(
          a.asInstanceOf[DynamicSchemaExpr],
          b.asInstanceOf[DynamicSchemaExpr],
          c.asInstanceOf[RelationalOperator]
        ),
      a => (a.left, a.right, a.operator)
    )

  implicit lazy val logicalSchema: Schema[Logical] =
    obj3Schema(TypeId.of[Logical], "left", schema, "right", schema, "operator", LogicalOperator.schema)(
      (a, b, c) =>
        Logical(a.asInstanceOf[DynamicSchemaExpr], b.asInstanceOf[DynamicSchemaExpr], c.asInstanceOf[LogicalOperator]),
      a => (a.left, a.right, a.operator)
    )

  implicit lazy val convertSchema: Schema[Convert] =
    obj2Schema(TypeId.of[Convert], "expr", schema, "converter", PrimitiveConverter.schema)(
      (a, b) => Convert(a.asInstanceOf[DynamicSchemaExpr], b.asInstanceOf[PrimitiveConverter]),
      a => (a.expr, a.converter)
    )

  implicit lazy val schema: Schema[DynamicSchemaExpr] = new Schema(
    new Reflect.Variant[Binding, DynamicSchemaExpr](
      cases = Chunk(
        defaultValueSchema.reflect.asTerm("DefaultValue"),
        literalSchema.reflect.asTerm("Literal"),
        dynamicExprSchema.reflect.asTerm("Dynamic"),
        arithmeticSchema.reflect.asTerm("Arithmetic"),
        stringConcatSchema.reflect.asTerm("StringConcat"),
        stringSplitSchema.reflect.asTerm("StringSplit"),
        stringUppercaseSchema.reflect.asTerm("StringUppercase"),
        stringLowercaseSchema.reflect.asTerm("StringLowercase"),
        stringLengthSchema.reflect.asTerm("StringLength"),
        stringRegexMatchSchema.reflect.asTerm("StringRegexMatch"),
        notSchema.reflect.asTerm("Not"),
        relationalSchema.reflect.asTerm("Relational"),
        logicalSchema.reflect.asTerm("Logical"),
        convertSchema.reflect.asTerm("Convert")
      ),
      typeId = TypeId.of[DynamicSchemaExpr],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[DynamicSchemaExpr] {
          def discriminate(a: DynamicSchemaExpr): Int = a match {
            case _: DefaultValue    => 0; case _: Literal          => 1; case _: Dynamic         => 2; case _: Arithmetic  => 3
            case _: StringConcat    => 4; case _: StringSplit      => 5; case _: StringUppercase => 6;
            case _: StringLowercase => 7
            case _: StringLength    => 8; case _: StringRegexMatch => 9; case _: Not             => 10; case _: Relational => 11
            case _: Logical         => 12; case _: Convert         => 13
          }
        },
        matchers = Matchers(
          new Matcher[DefaultValue] {
            def downcastOrNull(a: Any): DefaultValue = a match {
              case x: DefaultValue => x; case _ => null.asInstanceOf[DefaultValue]
            }
          },
          new Matcher[Literal] {
            def downcastOrNull(a: Any): Literal = a match { case x: Literal => x; case _ => null.asInstanceOf[Literal] }
          },
          new Matcher[Dynamic] {
            def downcastOrNull(a: Any): Dynamic = a match { case x: Dynamic => x; case _ => null.asInstanceOf[Dynamic] }
          },
          new Matcher[Arithmetic] {
            def downcastOrNull(a: Any): Arithmetic = a match {
              case x: Arithmetic => x; case _ => null.asInstanceOf[Arithmetic]
            }
          },
          new Matcher[StringConcat] {
            def downcastOrNull(a: Any): StringConcat = a match {
              case x: StringConcat => x; case _ => null.asInstanceOf[StringConcat]
            }
          },
          new Matcher[StringSplit] {
            def downcastOrNull(a: Any): StringSplit = a match {
              case x: StringSplit => x; case _ => null.asInstanceOf[StringSplit]
            }
          },
          new Matcher[StringUppercase] {
            def downcastOrNull(a: Any): StringUppercase = a match {
              case x: StringUppercase => x; case _ => null.asInstanceOf[StringUppercase]
            }
          },
          new Matcher[StringLowercase] {
            def downcastOrNull(a: Any): StringLowercase = a match {
              case x: StringLowercase => x; case _ => null.asInstanceOf[StringLowercase]
            }
          },
          new Matcher[StringLength] {
            def downcastOrNull(a: Any): StringLength = a match {
              case x: StringLength => x; case _ => null.asInstanceOf[StringLength]
            }
          },
          new Matcher[StringRegexMatch] {
            def downcastOrNull(a: Any): StringRegexMatch = a match {
              case x: StringRegexMatch => x; case _ => null.asInstanceOf[StringRegexMatch]
            }
          },
          new Matcher[Not] {
            def downcastOrNull(a: Any): Not = a match { case x: Not => x; case _ => null.asInstanceOf[Not] }
          },
          new Matcher[Relational] {
            def downcastOrNull(a: Any): Relational = a match {
              case x: Relational => x; case _ => null.asInstanceOf[Relational]
            }
          },
          new Matcher[Logical] {
            def downcastOrNull(a: Any): Logical = a match { case x: Logical => x; case _ => null.asInstanceOf[Logical] }
          },
          new Matcher[Convert] {
            def downcastOrNull(a: Any): Convert = a match { case x: Convert => x; case _ => null.asInstanceOf[Convert] }
          }
        )
      ),
      modifiers = Chunk.empty
    )
  )
}
