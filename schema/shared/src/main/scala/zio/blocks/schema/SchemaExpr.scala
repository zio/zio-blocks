package zio.blocks.schema

/**
 * A {{SchemaExpr}} is an expression on the value of a type fully described by a
 * {{Schema}}.
 *
 * {{SchemaExpr}} are used for persistence DSLs, implemented in third-party
 * libraries, as well as for validation, implemented in this library. In
 * addition, {{SchemaExpr}} could be used for data migration.
 */
sealed trait SchemaExpr[A, +B] {

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
}

object SchemaExpr {
  final case class Literal[S, A](value: A, schema: Schema[A]) extends SchemaExpr[S, A] {
    def eval(input: S): Either[OpticCheck, Seq[A]] = Right(Seq(value))

    def evalDynamic(input: S): Either[OpticCheck, Seq[DynamicValue]] =
      eval(input).map(_.map(schema.toDynamicValue))
  }

  final case class Optic[A, B](optic: zio.blocks.schema.Optic[A, B]) extends SchemaExpr[A, B] {
    def eval(input: A): Either[OpticCheck, Seq[B]] = optic match {
      case l: Lens[_, _] =>
        new Right(l.get(input) :: Nil)
      case p: Prism[_, _] =>
        p.getOrFail(input) match {
          case Right(x) => new Right(x.asInstanceOf[B] :: Nil)
          case left     => left.asInstanceOf[Either[OpticCheck, Seq[B]]]
        }
      case o: Optional[_, _] =>
        o.getOrFail(input) match {
          case Right(x) => new Right(x :: Nil)
          case left     => left.asInstanceOf[Either[OpticCheck, Seq[B]]]
        }
      case t: Traversal[_, _] =>
        val sb = Seq.newBuilder[B]
        t.fold[Unit](input)((), (_, a) => sb.addOne(a))
        val r = sb.result()
        if (r.isEmpty) new Left(t.check(input).get)
        else new Right(r)
    }

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      eval(input).map(_.map(optic.focus.toDynamicValue(_)))
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
      for {
        left  <- left.evalDynamic(input)
        right <- right.evalDynamic(input)
      } yield operator match {
        case RelationalOperator.LessThan           => for { x <- left; y <- right } yield x < y
        case RelationalOperator.LessThanOrEqual    => for { x <- left; y <- right } yield x <= y
        case RelationalOperator.GreaterThan        => for { x <- left; y <- right } yield x > y
        case RelationalOperator.GreaterThanOrEqual => for { x <- left; y <- right } yield x >= y
        case RelationalOperator.Equal              => for { x <- left; y <- right } yield x == y
        case RelationalOperator.NotEqual           => for { x <- left; y <- right } yield x != y
      }

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      eval(input).map(_.map(Schema[Boolean].toDynamicValue(_)))
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
        left  <- left.eval(input)
        right <- right.eval(input)
      } yield operator match {
        case LogicalOperator.And => for { x <- left; y <- right } yield x && y
        case LogicalOperator.Or  => for { x <- left; y <- right } yield x || y
      }

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      eval(input).map(_.map(Schema[Boolean].toDynamicValue))
  }

  sealed trait LogicalOperator

  object LogicalOperator {
    case object And extends LogicalOperator
    case object Or  extends LogicalOperator
  }

  final case class Not[A](expr: SchemaExpr[A, Boolean]) extends UnaryOp[A, Boolean] {
    def eval(input: A): Either[OpticCheck, Seq[Boolean]] =
      for {
        result <- expr.eval(input)
      } yield result.map(!_)

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      eval(input).map(_.map(Schema[Boolean].toDynamicValue))
  }

  final case class Arithmetic[S, A](
    left: SchemaExpr[S, A],
    right: SchemaExpr[S, A],
    operator: ArithmeticOperator,
    isNumeric: IsNumeric[A]
  ) extends BinaryOp[S, A, A] {
    def eval(input: S): Either[OpticCheck, Seq[A]] =
      for {
        left  <- left.eval(input)
        right <- right.eval(input)
      } yield {
        val n = isNumeric.numeric
        operator match {
          case ArithmeticOperator.Add      => for { x <- left; y <- right } yield n.plus(x, y)
          case ArithmeticOperator.Subtract => for { x <- left; y <- right } yield n.minus(x, y)
          case ArithmeticOperator.Multiply => for { x <- left; y <- right } yield n.times(x, y)
        }
      }

    def evalDynamic(input: S): Either[OpticCheck, Seq[DynamicValue]] =
      eval(input).map(_.map(isNumeric.primitiveType.toDynamicValue))
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
        left  <- left.eval(input)
        right <- right.eval(input)
      } yield for { x <- left; y <- right } yield x + y

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      eval(input).map(_.map(Schema[String].toDynamicValue))
  }

  final case class StringRegexMatch[A](regex: SchemaExpr[A, String], string: SchemaExpr[A, String])
      extends SchemaExpr[A, Boolean] {
    def eval(input: A): Either[OpticCheck, Seq[Boolean]] =
      for {
        regex  <- regex.eval(input)
        string <- string.eval(input)
      } yield for { x <- regex; y <- string } yield x.matches(y)

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      eval(input).map(_.map(Schema[Boolean].toDynamicValue))
  }

  final case class StringLength[A](string: SchemaExpr[A, String]) extends SchemaExpr[A, Int] {
    def eval(input: A): Either[OpticCheck, Seq[Int]] =
      for {
        string <- string.eval(input)
      } yield string.map(_.length)

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      eval(input).map(_.map(Schema[Int].toDynamicValue))
  }
}
