package zio.blocks.schema

import zio.blocks.chunk.Chunk

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
  def eval(input: A): Either[OpticCheck, Seq[B]]

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

  final def &&[B2](that: SchemaExpr[A, B2])(implicit ev: B <:< Boolean, ev2: B2 =:= Boolean): SchemaExpr[A, Boolean] =
    new SchemaExpr.Logical(self.asEquivalent[Boolean], that.asEquivalent[Boolean], SchemaExpr.LogicalOperator.And)

  final def ||[B2](that: SchemaExpr[A, B2])(implicit ev: B <:< Boolean, ev2: B2 =:= Boolean): SchemaExpr[A, Boolean] =
    new SchemaExpr.Logical(self.asEquivalent[Boolean], that.asEquivalent[Boolean], SchemaExpr.LogicalOperator.Or)

  private final def asEquivalent[B2](implicit ev: B <:< B2): SchemaExpr[A, B2] = {
    val _ = ev // suppress unused warning
    self.asInstanceOf[SchemaExpr[A, B2]]
  }
}

object SchemaExpr {

  /**
   * Creates a DefaultValue expression that uses the schema's default value.
   */
  def DefaultValue[A](implicit schema: Schema[A]): SchemaExpr[Unit, A] =
    new SchemaExpr.DefaultValue(schema)

  final case class Literal[S, A](value: A, schema: Schema[A]) extends SchemaExpr[S, A] {
    def eval(input: S): Either[OpticCheck, Seq[A]] = result

    def evalDynamic(input: S): Either[OpticCheck, Seq[DynamicValue]] = dynamicResult

    private[this] val result        = new Right(Chunk.single(value))
    private[this] val dynamicResult = new Right(Chunk.single(schema.toDynamicValue(value)))
  }

  final case class Optic[A, B](optic: zio.blocks.schema.Optic[A, B]) extends SchemaExpr[A, B] {
    def eval(input: A): Either[OpticCheck, Seq[B]] = optic match {
      case l: Lens[?, ?] =>
        new Right(Chunk.single(l.get(input)))
      case p: Prism[?, ?] =>
        p.getOrFail(input) match {
          case Right(x: B @scala.unchecked) => new Right(Chunk.single(x))
          case left                         => left.asInstanceOf[Either[OpticCheck, Seq[B]]]
        }
      case o: Optional[?, ?] =>
        o.getOrFail(input) match {
          case Right(x) => new Right(Chunk.single(x))
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
        new Right(Chunk.single(toDynamicValue(l.get(input))))
      case p: Prism[?, ?] =>
        p.getOrFail(input) match {
          case Right(x: B @scala.unchecked) => new Right(Chunk.single(toDynamicValue(x)))
          case left                         => left.asInstanceOf[Either[OpticCheck, Seq[DynamicValue]]]
        }
      case o: Optional[?, ?] =>
        o.getOrFail(input) match {
          case Right(x) => new Right(Chunk.single(toDynamicValue(x)))
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
    def eval(input: A): Either[OpticCheck, Seq[Boolean]] =
      if ((operator eq RelationalOperator.Equal) || (operator eq RelationalOperator.NotEqual)) {
        for {
          xs <- left.eval(input)
          ys <- right.eval(input)
        } yield {
          if (operator eq RelationalOperator.Equal) for { x <- xs; y <- ys } yield x == y
          else for { x <- xs; y <- ys } yield x != y
        }
      } else { // FIXME: Use Ordering to avoid converisons to dynamic values
        for {
          xs <- left.evalDynamic(input)
          ys <- right.evalDynamic(input)
        } yield {
          if (operator eq RelationalOperator.LessThan) for { x <- xs; y <- ys } yield x < y
          else if (operator eq RelationalOperator.LessThanOrEqual) for { x <- xs; y <- ys } yield x <= y
          else if (operator eq RelationalOperator.GreaterThan) for { x <- xs; y <- ys } yield x > y
          else for { x <- xs; y <- ys } yield x >= y
        }
      }

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- left.evalDynamic(input)
        ys <- right.evalDynamic(input)
      } yield operator match {
        case _: RelationalOperator.LessThan.type           => for { x <- xs; y <- ys } yield toDynamicValue(x < y)
        case _: RelationalOperator.LessThanOrEqual.type    => for { x <- xs; y <- ys } yield toDynamicValue(x <= y)
        case _: RelationalOperator.GreaterThan.type        => for { x <- xs; y <- ys } yield toDynamicValue(x > y)
        case _: RelationalOperator.GreaterThanOrEqual.type => for { x <- xs; y <- ys } yield toDynamicValue(x >= y)
        case _: RelationalOperator.Equal.type              => for { x <- xs; y <- ys } yield toDynamicValue(x == y)
        case _: RelationalOperator.NotEqual.type           => for { x <- xs; y <- ys } yield toDynamicValue(x != y)
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
    def eval(input: A): Either[OpticCheck, Seq[Boolean]] =
      for {
        xs <- left.eval(input)
        ys <- right.eval(input)
      } yield operator match {
        case _: LogicalOperator.And.type => for { x <- xs; y <- ys } yield x && y
        case _: LogicalOperator.Or.type  => for { x <- xs; y <- ys } yield x || y
      }

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- left.eval(input)
        ys <- right.eval(input)
      } yield operator match {
        case _: LogicalOperator.And.type => for { x <- xs; y <- ys } yield toDynamicValue(x && y)
        case _: LogicalOperator.Or.type  => for { x <- xs; y <- ys } yield toDynamicValue(x || y)
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
    def eval(input: A): Either[OpticCheck, Seq[Boolean]] =
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
    def eval(input: S): Either[OpticCheck, Seq[A]] =
      for {
        xs <- left.eval(input)
        ys <- right.eval(input)
      } yield {
        val n = isNumeric.numeric
        operator match {
          case _: ArithmeticOperator.Add.type      => for { x <- xs; y <- ys } yield n.plus(x, y)
          case _: ArithmeticOperator.Subtract.type => for { x <- xs; y <- ys } yield n.minus(x, y)
          case _: ArithmeticOperator.Multiply.type => for { x <- xs; y <- ys } yield n.times(x, y)
        }
      }

    def evalDynamic(input: S): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- left.eval(input)
        ys <- right.eval(input)
      } yield {
        val n = isNumeric.numeric
        operator match {
          case _: ArithmeticOperator.Add.type      => for { x <- xs; y <- ys } yield toDynamicValue(n.plus(x, y))
          case _: ArithmeticOperator.Subtract.type => for { x <- xs; y <- ys } yield toDynamicValue(n.minus(x, y))
          case _: ArithmeticOperator.Multiply.type => for { x <- xs; y <- ys } yield toDynamicValue(n.times(x, y))
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
    def eval(input: A): Either[OpticCheck, Seq[String]] =
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
    def eval(input: A): Either[OpticCheck, Seq[Boolean]] =
      for {
        xs <- regex.eval(input)
        ys <- string.eval(input)
      } yield for { x <- xs; y <- ys } yield y.matches(x)

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- regex.eval(input)
        ys <- string.eval(input)
      } yield for { x <- xs; y <- ys } yield toDynamicValue(y.matches(x))

    private[this] def toDynamicValue(value: Boolean): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Boolean(value))
  }

  final case class StringLength[A](string: SchemaExpr[A, String]) extends SchemaExpr[A, Int] {
    def eval(input: A): Either[OpticCheck, Seq[Int]] =
      for {
        xs <- string.eval(input)
      } yield xs.map(_.length)

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- string.eval(input)
      } yield xs.map(x => toDynamicValue(x.length))

    private[this] def toDynamicValue(value: Int): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Int(value))
  }

  /**
   * A special expression that uses the schema's default value.
   * This is used in migrations for AddField, DropField reverse, etc.
   */
  final case class DefaultValue[A](schema: Schema[A]) extends SchemaExpr[Unit, A] {
    def eval(input: Unit): Either[OpticCheck, Seq[A]] =
      schema match {
        case s: Schema[?] =>
          s.getDefaultValue match {
            case Some(value) => Right(Seq(value.asInstanceOf[A]))
            case None => Left(OpticCheck.Missing)
          }
        case _ => Left(OpticCheck.Missing)
      }

    def evalDynamic(input: Unit): Either[OpticCheck, Seq[DynamicValue]] =
      schema match {
        case s: Schema[?] =>
          s.getDefaultValue match {
            case Some(value) => Right(Seq(s.toDynamicValue(value)))
            case None => Left(OpticCheck.Missing)
          }
        case _ => Left(OpticCheck.Missing)
      }
  }
}
