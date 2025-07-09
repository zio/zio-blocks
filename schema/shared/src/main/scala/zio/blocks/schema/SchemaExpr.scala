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
    def eval(input: S): Either[OpticCheck, Seq[A]] = result

    def evalDynamic(input: S): Either[OpticCheck, Seq[DynamicValue]] = dynamicResult

    private[this] val result        = new Right(value :: Nil)
    private[this] val dynamicResult = new Right(schema.toDynamicValue(value) :: Nil)
  }

  final case class Optic[A, B](optic: zio.blocks.schema.Optic[A, B]) extends SchemaExpr[A, B] {
    def eval(input: A): Either[OpticCheck, Seq[B]] = optic match {
      case l: Lens[_, _] =>
        new Right(l.get(input) :: Nil)
      case p: Prism[_, _] =>
        p.getOrFail(input) match {
          case Right(x: B @scala.unchecked) => new Right(x :: Nil)
          case left                         => left.asInstanceOf[Either[OpticCheck, Seq[B]]]
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

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] = optic match {
      case l: Lens[_, _] =>
        new Right(toDynamicValue(l.get(input)) :: Nil)
      case p: Prism[_, _] =>
        p.getOrFail(input) match {
          case Right(x: B @scala.unchecked) => new Right(toDynamicValue(x) :: Nil)
          case left                         => left.asInstanceOf[Either[OpticCheck, Seq[DynamicValue]]]
        }
      case o: Optional[_, _] =>
        o.getOrFail(input) match {
          case Right(x) => new Right(toDynamicValue(x) :: Nil)
          case left     => left.asInstanceOf[Either[OpticCheck, Seq[DynamicValue]]]
        }
      case t: Traversal[_, _] =>
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
      if (operator == RelationalOperator.Equal || operator == RelationalOperator.NotEqual) {
        for {
          xs <- left.eval(input)
          ys <- right.eval(input)
        } yield {
          if (operator == RelationalOperator.Equal) for { x <- xs; y <- ys } yield x == y
          else for { x <- xs; y <- ys } yield x != y
        }
      } else { // FIXME: Use Ordering to avoid converisons to dynamic values
        for {
          xs <- left.evalDynamic(input)
          ys <- right.evalDynamic(input)
        } yield {
          if (operator == RelationalOperator.LessThan) for { x <- xs; y <- ys } yield x < y
          else if (operator == RelationalOperator.LessThanOrEqual) for { x <- xs; y <- ys } yield x <= y
          else if (operator == RelationalOperator.GreaterThan) for { x <- xs; y <- ys } yield x > y
          else for { x <- xs; y <- ys } yield x >= y
        }
      }

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- left.evalDynamic(input)
        ys <- right.evalDynamic(input)
      } yield operator match {
        case RelationalOperator.LessThan           => for { x <- xs; y <- ys } yield toDynamicValue(x < y)
        case RelationalOperator.LessThanOrEqual    => for { x <- xs; y <- ys } yield toDynamicValue(x <= y)
        case RelationalOperator.GreaterThan        => for { x <- xs; y <- ys } yield toDynamicValue(x > y)
        case RelationalOperator.GreaterThanOrEqual => for { x <- xs; y <- ys } yield toDynamicValue(x >= y)
        case RelationalOperator.Equal              => for { x <- xs; y <- ys } yield toDynamicValue(x == y)
        case RelationalOperator.NotEqual           => for { x <- xs; y <- ys } yield toDynamicValue(x != y)
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
        case LogicalOperator.And => for { x <- xs; y <- ys } yield x && y
        case LogicalOperator.Or  => for { x <- xs; y <- ys } yield x || y
      }

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- left.eval(input)
        ys <- right.eval(input)
      } yield operator match {
        case LogicalOperator.And => for { x <- xs; y <- ys } yield toDynamicValue(x && y)
        case LogicalOperator.Or  => for { x <- xs; y <- ys } yield toDynamicValue(x || y)
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
          case ArithmeticOperator.Add      => for { x <- xs; y <- ys } yield n.plus(x, y)
          case ArithmeticOperator.Subtract => for { x <- xs; y <- ys } yield n.minus(x, y)
          case ArithmeticOperator.Multiply => for { x <- xs; y <- ys } yield n.times(x, y)
        }
      }

    def evalDynamic(input: S): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- left.eval(input)
        ys <- right.eval(input)
      } yield {
        val n = isNumeric.numeric
        operator match {
          case ArithmeticOperator.Add      => for { x <- xs; y <- ys } yield toDynamicValue(n.plus(x, y))
          case ArithmeticOperator.Subtract => for { x <- xs; y <- ys } yield toDynamicValue(n.minus(x, y))
          case ArithmeticOperator.Multiply => for { x <- xs; y <- ys } yield toDynamicValue(n.times(x, y))
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
      } yield for { x <- xs; y <- ys } yield x.matches(y)

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- regex.eval(input)
        ys <- string.eval(input)
      } yield for { x <- xs; y <- ys } yield toDynamicValue(x.matches(y))

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
}
