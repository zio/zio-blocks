package zio.blocks.schema

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

  final def &&[B2](
    that: SchemaExpr[A, B2]
  )(implicit ev: B <:< Boolean, ev2: B2 =:= Boolean): SchemaExpr[A, Boolean] =
    SchemaExpr.Logical(
      self.asEquivalent[Boolean],
      that.asEquivalent[Boolean],
      SchemaExpr.LogicalOperator.And
    )

  final def ||[B2](
    that: SchemaExpr[A, B2]
  )(implicit ev: B <:< Boolean, ev2: B2 =:= Boolean): SchemaExpr[A, Boolean] =
    SchemaExpr.Logical(
      self.asEquivalent[Boolean],
      that.asEquivalent[Boolean],
      SchemaExpr.LogicalOperator.Or
    )

  private final def asEquivalent[B2](implicit ev: B <:< B2): SchemaExpr[A, B2] = self.asInstanceOf[SchemaExpr[A, B2]]
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
      case l: Lens[?, ?] =>
        new Right(l.get(input) :: Nil)
      case p: Prism[?, ?] =>
        p.getOrFail(input) match {
          case Right(x: B @scala.unchecked) => new Right(x :: Nil)
          case left                         => left.asInstanceOf[Either[OpticCheck, Seq[B]]]
        }
      case o: Optional[?, ?] =>
        o.getOrFail(input) match {
          case Right(x) => new Right(x :: Nil)
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
        new Right(toDynamicValue(l.get(input)) :: Nil)
      case p: Prism[?, ?] =>
        p.getOrFail(input) match {
          case Right(x: B @scala.unchecked) => new Right(toDynamicValue(x) :: Nil)
          case left                         => left.asInstanceOf[Either[OpticCheck, Seq[DynamicValue]]]
        }
      case o: Optional[?, ?] =>
        o.getOrFail(input) match {
          case Right(x) => new Right(toDynamicValue(x) :: Nil)
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
          case ArithmeticOperator.Divide   =>
            for { x <- xs; y <- ys } yield {
              // Use Fractional for division if available, otherwise integer division via Integral
              n match {
                case frac: Fractional[A] => frac.div(x, y)
                case int: Integral[A]    => int.quot(x, y)
                case _                   => n.times(x, n.fromInt(0)) // Fallback (shouldn't happen)
              }
            }
        }
      }

    def evalDynamic(input: S): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- left.evalDynamic(input)
        ys <- right.evalDynamic(input)
      } yield {
        // Extract primitive numeric values from DynamicValue
        def extractNumeric(dv: DynamicValue): Option[A] = dv match {
          case DynamicValue.Primitive(p) =>
            p match {
              case PrimitiveValue.Byte(v)       => Some(v.asInstanceOf[A])
              case PrimitiveValue.Short(v)      => Some(v.asInstanceOf[A])
              case PrimitiveValue.Int(v)        => Some(v.asInstanceOf[A])
              case PrimitiveValue.Long(v)       => Some(v.asInstanceOf[A])
              case PrimitiveValue.Float(v)      => Some(v.asInstanceOf[A])
              case PrimitiveValue.Double(v)     => Some(v.asInstanceOf[A])
              case PrimitiveValue.BigInt(v)     => Some(v.asInstanceOf[A])
              case PrimitiveValue.BigDecimal(v) => Some(v.asInstanceOf[A])
              case _                            => None
            }
          case _ => None
        }

        val xValues = xs.flatMap(extractNumeric)
        val yValues = ys.flatMap(extractNumeric)

        val n = isNumeric.numeric
        operator match {
          case ArithmeticOperator.Add      => for { x <- xValues; y <- yValues } yield toDynamicValue(n.plus(x, y))
          case ArithmeticOperator.Subtract => for { x <- xValues; y <- yValues } yield toDynamicValue(n.minus(x, y))
          case ArithmeticOperator.Multiply => for { x <- xValues; y <- yValues } yield toDynamicValue(n.times(x, y))
          case ArithmeticOperator.Divide   =>
            for { x <- xValues; y <- yValues } yield {
              val result = n match {
                case frac: Fractional[A] => frac.div(x, y)
                case int: Integral[A]    => int.quot(x, y)
                case _                   => n.times(x, n.fromInt(0)) // Fallback
              }
              toDynamicValue(result)
            }
        }
      }

    private[this] val toDynamicValue: A => DynamicValue = isNumeric.primitiveType.toDynamicValue
  }

  sealed trait ArithmeticOperator

  object ArithmeticOperator {
    case object Add      extends ArithmeticOperator
    case object Subtract extends ArithmeticOperator
    case object Multiply extends ArithmeticOperator
    case object Divide   extends ArithmeticOperator
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

  /**
   * Evaluates a DynamicOptic on a DynamicValue input. Used for migrations where
   * typed Optics are not available.
   */
  final case class Dynamic[A, B](optic: DynamicOptic) extends SchemaExpr[A, B] {
    def eval(input: A): Either[OpticCheck, Seq[B]] =
      evalDynamic(input).map { dynamicValues =>
        dynamicValues.map {
          case DynamicValue.Primitive(p) =>
            (p match {
              case PrimitiveValue.String(v)  => v
              case PrimitiveValue.Int(v)     => v
              case PrimitiveValue.Boolean(v) => v
              case PrimitiveValue.Short(v)   => v
              case PrimitiveValue.Long(v)    => v
              case PrimitiveValue.Float(v)   => v
              case PrimitiveValue.Double(v)  => v
              case PrimitiveValue.Char(v)    => v
              case PrimitiveValue.Byte(v)    => v
              case _                         => throw new RuntimeException(s"Cannot cast primitive $p to target type")
            }).asInstanceOf[B]
          case _ => throw new RuntimeException("Expected primitive value")
        }
      }

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] = {
      if (!input.isInstanceOf[DynamicValue]) {
        return Left(
          new OpticCheck(
            new ::(
              new OpticCheck.WrappingError(optic, optic, SchemaError.validationFailed("Input must be DynamicValue")),
              Nil
            )
          )
        )
      }
      val root = input.asInstanceOf[DynamicValue]

      var current: DynamicValue = root
      val nodes                 = optic.nodes
      var i                     = 0
      while (i < nodes.length) {
        nodes(i) match {
          case DynamicOptic.Node.Field(name) =>
            current match {
              case DynamicValue.Record(fields) =>
                fields.find(_._1 == name) match {
                  case Some((_, v)) => current = v
                  case None         =>
                    return Left(
                      new OpticCheck(
                        new ::(
                          new OpticCheck.WrappingError(optic, optic, SchemaError.validationFailed(s"Field $name not found")),
                          Nil
                        )
                      )
                    )
                }
              case _ =>
                return Left(
                  new OpticCheck(
                    new ::(
                      new OpticCheck.WrappingError(optic, optic, SchemaError.validationFailed("Expected Record")),
                      Nil
                    )
                  )
                )
            }
          case _ =>
            return Left(
              new OpticCheck(
                new ::(
                  new OpticCheck.WrappingError(optic, optic, SchemaError.validationFailed("Only Field access supported in Dynamic SchemaExpr")),
                  Nil
                )
              )
            )
        }
        i += 1
      }
      Right(Seq(current))
    }
  }

  /**
   * Converts a primitive value from one type to another using a
   * PrimitiveConverter.
   */
  final case class Convert[A, B](expr: SchemaExpr[A, ?], converter: PrimitiveConverter) extends SchemaExpr[A, B] {
    def eval(input: A): Either[OpticCheck, Seq[B]] =
      Left(
        new OpticCheck(
          new ::(
            new OpticCheck.WrappingError(
              DynamicOptic.root,
              DynamicOptic.root,
              SchemaError.validationFailed("Convert.eval not supported - use evalDynamic for type conversions")
            ),
            Nil
          )
        )
      )

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      expr.evalDynamic(input).flatMap { dynamicValues =>
        val results = dynamicValues.map { dv =>
          converter.convert(dv) match {
            case Right(converted) => Right(converted)
            case Left(err)        =>
              Left(
                new OpticCheck(
                  new ::(new OpticCheck.WrappingError(DynamicOptic.root, DynamicOptic.root, SchemaError.validationFailed(err)), Nil)
                )
              )
          }
        }

        // Check if any conversions failed
        val errors = results.collect { case Left(err) => err }
        if (errors.nonEmpty) {
          Left(errors.head)
        } else {
          Right(results.collect { case Right(v) => v })
        }
      }
  }

  /**
   * Splits a string by a delimiter, returning a sequence of string parts. Used
   * for migrations that split a single field into multiple fields.
   *
   * Example: "John Doe".split(" ") => ["John", "Doe"]
   */
  final case class StringSplit[A](string: SchemaExpr[A, String], delimiter: String) extends SchemaExpr[A, String] {
    def eval(input: A): Either[OpticCheck, Seq[String]] =
      for {
        xs <- string.eval(input)
      } yield xs.flatMap(_.split(delimiter, -1)) // -1 means keep trailing empty strings

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- string.eval(input)
      } yield xs.flatMap(_.split(delimiter, -1)).map(toDynamicValue)

    private[this] def toDynamicValue(value: String): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.String(value))
  }

  /**
   * Converts a string to uppercase. Used for collection transformations in
   * migrations.
   *
   * Example: "hello" => "HELLO"
   */
  final case class StringUppercase[A](string: SchemaExpr[A, String]) extends SchemaExpr[A, String] {
    def eval(input: A): Either[OpticCheck, Seq[String]] =
      for {
        xs <- string.eval(input)
      } yield xs.map(_.toUpperCase)

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- string.eval(input)
      } yield xs.map(s => toDynamicValue(s.toUpperCase))

    private[this] def toDynamicValue(value: String): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.String(value))
  }

  /**
   * Converts a string to lowercase. Used for collection transformations in
   * migrations.
   *
   * Example: "HELLO" => "hello"
   */
  final case class StringLowercase[A](string: SchemaExpr[A, String]) extends SchemaExpr[A, String] {
    def eval(input: A): Either[OpticCheck, Seq[String]] =
      for {
        xs <- string.eval(input)
      } yield xs.map(_.toLowerCase)

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- string.eval(input)
      } yield xs.map(s => toDynamicValue(s.toLowerCase))

    private[this] def toDynamicValue(value: String): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.String(value))
  }
}
