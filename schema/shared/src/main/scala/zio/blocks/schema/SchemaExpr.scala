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
  def eval[B1 >: B](input: A)(implicit schema: Schema[B1]): Either[OpticCheck, Seq[B1]]

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
   * Create a Literal expression from the schema's default value. Gets the
   * default value from the schema and wraps it in a Literal. Returns None if
   * the schema has no default value.
   */
  def schemaDefault[A](implicit schema: Schema[A]): Option[SchemaExpr[Any, A]] =
    schema.getDefaultValue.map { defaultValue =>
      Literal[Any, A](schema.toDynamicValue(defaultValue))
    }

  /**
   * Primitive type conversion expression. Converts a primitive value from one
   * type to another based on the ConversionType.
   */
  final case class PrimitiveConversion[S](conversionType: ConversionType) extends SchemaExpr[S, Any] {
    def eval[B1 >: Any](input: S)(implicit schema: Schema[B1]): Either[OpticCheck, Seq[B1]] =
      // Input is ignored - conversion is applied to a DynamicValue at the migration level
      throw new UnsupportedOperationException("PrimitiveConversion.eval requires DynamicValue context")

    def evalDynamic(input: S): Either[OpticCheck, Seq[DynamicValue]] =
      throw new UnsupportedOperationException("PrimitiveConversion.evalDynamic requires DynamicValue context")

    /**
     * Convert a DynamicValue using this conversion type.
     */
    def convert(value: DynamicValue): Either[String, DynamicValue] = conversionType.convert(value)
  }

  /**
   * Sum type representing all supported primitive-to-primitive conversions.
   */
  sealed trait ConversionType {
    def convert(value: DynamicValue): Either[String, DynamicValue]
  }

  object ConversionType {
    // Numeric widening conversions (lossless)
    case object ByteToShort extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Byte(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Short(v.toShort)))
        case _ => Left(s"Expected Byte, got $value")
      }
    }

    case object ByteToInt extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Byte(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Int(v.toInt)))
        case _ => Left(s"Expected Byte, got $value")
      }
    }

    case object ByteToLong extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Byte(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Long(v.toLong)))
        case _ => Left(s"Expected Byte, got $value")
      }
    }

    case object ShortToInt extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Short(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Int(v.toInt)))
        case _ => Left(s"Expected Short, got $value")
      }
    }

    case object ShortToLong extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Short(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Long(v.toLong)))
        case _ => Left(s"Expected Short, got $value")
      }
    }

    case object IntToLong extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Long(v.toLong)))
        case _ => Left(s"Expected Int, got $value")
      }
    }

    case object FloatToDouble extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Float(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Double(v.toDouble)))
        case _ => Left(s"Expected Float, got $value")
      }
    }

    // Numeric narrowing conversions (potentially lossy)
    case object ShortToByte extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Short(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Byte(v.toByte)))
        case _ => Left(s"Expected Short, got $value")
      }
    }

    case object IntToByte extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Byte(v.toByte)))
        case _ => Left(s"Expected Int, got $value")
      }
    }

    case object IntToShort extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Short(v.toShort)))
        case _ => Left(s"Expected Int, got $value")
      }
    }

    case object LongToByte extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Long(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Byte(v.toByte)))
        case _ => Left(s"Expected Long, got $value")
      }
    }

    case object LongToShort extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Long(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Short(v.toShort)))
        case _ => Left(s"Expected Long, got $value")
      }
    }

    case object LongToInt extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Long(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Int(v.toInt)))
        case _ => Left(s"Expected Long, got $value")
      }
    }

    case object DoubleToFloat extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Double(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Float(v.toFloat)))
        case _ => Left(s"Expected Double, got $value")
      }
    }

    // Integer to floating point
    case object IntToFloat extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Float(v.toFloat)))
        case _ => Left(s"Expected Int, got $value")
      }
    }

    case object IntToDouble extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Double(v.toDouble)))
        case _ => Left(s"Expected Int, got $value")
      }
    }

    case object LongToFloat extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Long(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Float(v.toFloat)))
        case _ => Left(s"Expected Long, got $value")
      }
    }

    case object LongToDouble extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Long(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Double(v.toDouble)))
        case _ => Left(s"Expected Long, got $value")
      }
    }

    // String conversions
    case object IntToString extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
        case _ => Left(s"Expected Int, got $value")
      }
    }

    case object LongToString extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Long(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
        case _ => Left(s"Expected Long, got $value")
      }
    }

    case object DoubleToString extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Double(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
        case _ => Left(s"Expected Double, got $value")
      }
    }

    case object BooleanToString extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Boolean(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
        case _ => Left(s"Expected Boolean, got $value")
      }
    }

    // String parsing (may fail)
    case object StringToInt extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.String(v)) =>
          try Right(DynamicValue.Primitive(PrimitiveValue.Int(v.toInt)))
          catch { case _: NumberFormatException => Left(s"Cannot parse '$v' as Int") }
        case _ => Left(s"Expected String, got $value")
      }
    }

    case object StringToLong extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.String(v)) =>
          try Right(DynamicValue.Primitive(PrimitiveValue.Long(v.toLong)))
          catch { case _: NumberFormatException => Left(s"Cannot parse '$v' as Long") }
        case _ => Left(s"Expected String, got $value")
      }
    }

    case object StringToDouble extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.String(v)) =>
          try Right(DynamicValue.Primitive(PrimitiveValue.Double(v.toDouble)))
          catch { case _: NumberFormatException => Left(s"Cannot parse '$v' as Double") }
        case _ => Left(s"Expected String, got $value")
      }
    }

    case object StringToBoolean extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.String(v)) =>
          v.toLowerCase match {
            case "true"  => Right(DynamicValue.Primitive(PrimitiveValue.Boolean(true)))
            case "false" => Right(DynamicValue.Primitive(PrimitiveValue.Boolean(false)))
            case _       => Left(s"Cannot parse '$v' as Boolean")
          }
        case _ => Left(s"Expected String, got $value")
      }
    }

    // Char conversions
    case object CharToInt extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Char(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Int(v.toInt)))
        case _ => Left(s"Expected Char, got $value")
      }
    }

    case object IntToChar extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Int(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Char(v.toChar)))
        case _ => Left(s"Expected Int, got $value")
      }
    }

    case object CharToString extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Char(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
        case _ => Left(s"Expected Char, got $value")
      }
    }

    // Byte/Short to Float/Double
    case object ByteToFloat extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Byte(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Float(v.toFloat)))
        case _ => Left(s"Expected Byte, got $value")
      }
    }

    case object ByteToDouble extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Byte(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Double(v.toDouble)))
        case _ => Left(s"Expected Byte, got $value")
      }
    }

    case object ShortToFloat extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Short(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Float(v.toFloat)))
        case _ => Left(s"Expected Short, got $value")
      }
    }

    case object ShortToDouble extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Short(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Double(v.toDouble)))
        case _ => Left(s"Expected Short, got $value")
      }
    }

    // Float/Double to Int/Long (truncation)
    case object FloatToInt extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Float(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Int(v.toInt)))
        case _ => Left(s"Expected Float, got $value")
      }
    }

    case object FloatToLong extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Float(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Long(v.toLong)))
        case _ => Left(s"Expected Float, got $value")
      }
    }

    case object DoubleToInt extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Double(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Int(v.toInt)))
        case _ => Left(s"Expected Double, got $value")
      }
    }

    case object DoubleToLong extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Double(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.Long(v.toLong)))
        case _ => Left(s"Expected Double, got $value")
      }
    }

    // More String conversions
    case object FloatToString extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Float(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
        case _ => Left(s"Expected Float, got $value")
      }
    }

    case object ShortToString extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Short(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
        case _ => Left(s"Expected Short, got $value")
      }
    }

    case object ByteToString extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.Byte(v)) =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
        case _ => Left(s"Expected Byte, got $value")
      }
    }

    case object StringToFloat extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.String(v)) =>
          try Right(DynamicValue.Primitive(PrimitiveValue.Float(v.toFloat)))
          catch { case _: NumberFormatException => Left(s"Cannot parse '$v' as Float") }
        case _ => Left(s"Expected String, got $value")
      }
    }

    case object StringToShort extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.String(v)) =>
          try Right(DynamicValue.Primitive(PrimitiveValue.Short(v.toShort)))
          catch { case _: NumberFormatException => Left(s"Cannot parse '$v' as Short") }
        case _ => Left(s"Expected String, got $value")
      }
    }

    case object StringToByte extends ConversionType {
      def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
        case DynamicValue.Primitive(PrimitiveValue.String(v)) =>
          try Right(DynamicValue.Primitive(PrimitiveValue.Byte(v.toByte)))
          catch { case _: NumberFormatException => Left(s"Cannot parse '$v' as Byte") }
        case _ => Left(s"Expected String, got $value")
      }
    }
  }

  final case class Literal[S, A](dynamicValue: DynamicValue) extends SchemaExpr[S, A] {
    def eval[B1 >: A](input: S)(implicit schema: Schema[B1]): Either[OpticCheck, Seq[B1]] =
      schema.fromDynamicValue(dynamicValue) match {
        case Right(value) => new Right(value :: Nil)
        case Left(error)  => new Left(new OpticCheck(::(OpticCheck.DynamicConversionError(error.message), Nil)))
      }

    def evalDynamic(input: S): Either[OpticCheck, Seq[DynamicValue]] = dynamicResult

    private[this] val dynamicResult = new Right(dynamicValue :: Nil)
  }

  object Literal {
    def apply[S, A](value: A)(implicit schema: Schema[A]): Literal[S, A] =
      new Literal(schema.toDynamicValue(value))
  }

  final case class Optic[A, B](path: DynamicOptic, sourceSchema: Schema[A]) extends SchemaExpr[A, B] {
    import zio.blocks.chunk.Chunk

    def eval[B1 >: B](input: A)(implicit schema: Schema[B1]): Either[OpticCheck, Seq[B1]] =
      evalWithStructuredErrors(input) match {
        case Right(chunk) =>
          val results   = chunk.toSeq.map(dv => schema.fromDynamicValue(dv))
          val errors    = results.collect { case Left(e) => e }
          val successes = results.collect { case Right(v) => v }
          if (errors.nonEmpty)
            Left(new OpticCheck(new ::(OpticCheck.DynamicConversionError(errors.head.message), Nil)))
          else
            Right(successes)
        case Left(check) => Left(check)
      }

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      evalWithStructuredErrors(input).map(_.toSeq)

    private def evalWithStructuredErrors(input: A): Either[OpticCheck, Chunk[DynamicValue]] = {
      val dynamicInput = sourceSchema.toDynamicValue(input)
      walkPath(Chunk(dynamicInput), path.nodes, 0, input)
    }

    private def walkPath(
      current: Chunk[DynamicValue],
      nodes: IndexedSeq[DynamicOptic.Node],
      idx: Int,
      originalInput: A
    ): Either[OpticCheck, Chunk[DynamicValue]] = {
      if (idx >= nodes.length) return Right(current)
      if (current.isEmpty) return Right(current)

      val node   = nodes(idx)
      val prefix = new DynamicOptic(nodes.take(idx + 1).toVector)
      val full   = path

      node match {
        case DynamicOptic.Node.Field(name) =>
          val next = current.flatMap {
            case r: DynamicValue.Record => r.fields.collect { case (n, v) if n == name => v }
            case _                      => Chunk.empty
          }
          walkPath(next, nodes, idx + 1, originalInput)

        case DynamicOptic.Node.Case(expectedCase) =>
          var result: Chunk[DynamicValue] = Chunk.empty
          var error: Option[OpticCheck]   = None

          current.foreach {
            case v: DynamicValue.Variant if v.caseNameValue == expectedCase =>
              result = result ++ Chunk(v.value)
            case v: DynamicValue.Variant =>
              val actualCase = v.caseNameValue match {
                case "None" | "Some" => "Option"
                case other           => other
              }
              error = Some(
                new OpticCheck(
                  new ::(
                    OpticCheck.UnexpectedCase(expectedCase, actualCase, full, prefix, originalInput),
                    Nil
                  )
                )
              )
            case _ =>
          }

          if (error.isDefined) Left(error.get)
          else walkPath(result, nodes, idx + 1, originalInput)

        case DynamicOptic.Node.Elements =>
          val hasSequence = current.exists(_.isInstanceOf[DynamicValue.Sequence])
          val next        = current.flatMap {
            case s: DynamicValue.Sequence => s.elements
            case _                        => Chunk.empty
          }
          if (next.isEmpty && hasSequence) {
            Left(new OpticCheck(new ::(OpticCheck.EmptySequence(full, prefix), Nil)))
          } else {
            walkPath(next, nodes, idx + 1, originalInput)
          }

        case DynamicOptic.Node.AtIndex(i) =>
          var outOfBoundsError: Option[OpticCheck] = None
          val next                                 = current.flatMap {
            case s: DynamicValue.Sequence if i >= 0 && i < s.elements.length =>
              Chunk(s.elements(i))
            case s: DynamicValue.Sequence =>
              outOfBoundsError = Some(
                new OpticCheck(
                  new ::(OpticCheck.SequenceIndexOutOfBounds(full, prefix, i, s.elements.length), Nil)
                )
              )
              Chunk.empty
            case _ => Chunk.empty
          }
          outOfBoundsError match {
            case Some(err) => Left(err)
            case None      => walkPath(next, nodes, idx + 1, originalInput)
          }

        case DynamicOptic.Node.AtIndices(indices) =>
          val next = current.flatMap {
            case s: DynamicValue.Sequence =>
              Chunk.from(indices.flatMap(i => if (i >= 0 && i < s.elements.length) Some(s.elements(i)) else None))
            case _ => Chunk.empty
          }
          walkPath(next, nodes, idx + 1, originalInput)

        case DynamicOptic.Node.AtMapKey(key) =>
          val next = current.flatMap {
            case m: DynamicValue.Map => m.entries.collect { case (k, v) if k == key => v }
            case _                   => Chunk.empty
          }
          if (next.isEmpty && current.exists(_.isInstanceOf[DynamicValue.Map])) {
            Left(new OpticCheck(new ::(OpticCheck.MissingKey(full, prefix, key), Nil)))
          } else {
            walkPath(next, nodes, idx + 1, originalInput)
          }

        case DynamicOptic.Node.AtMapKeys(keys) =>
          val next = current.flatMap {
            case m: DynamicValue.Map =>
              Chunk.from(keys.flatMap(key => m.entries.collect { case (k, v) if k == key => v }))
            case _ => Chunk.empty
          }
          walkPath(next, nodes, idx + 1, originalInput)

        case DynamicOptic.Node.MapKeys =>
          val next = current.flatMap {
            case m: DynamicValue.Map => m.entries.map(_._1)
            case _                   => Chunk.empty
          }
          if (next.isEmpty && current.exists(_.isInstanceOf[DynamicValue.Map])) {
            Left(new OpticCheck(new ::(OpticCheck.EmptyMap(full, prefix), Nil)))
          } else {
            walkPath(next, nodes, idx + 1, originalInput)
          }

        case DynamicOptic.Node.MapValues =>
          val next = current.flatMap {
            case m: DynamicValue.Map => m.entries.map(_._2)
            case _                   => Chunk.empty
          }
          if (next.isEmpty && current.exists(_.isInstanceOf[DynamicValue.Map])) {
            Left(new OpticCheck(new ::(OpticCheck.EmptyMap(full, prefix), Nil)))
          } else {
            walkPath(next, nodes, idx + 1, originalInput)
          }

        case DynamicOptic.Node.Wrapped =>
          walkPath(current, nodes, idx + 1, originalInput)
      }
    }
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
          xs <- left.evalDynamic(input)
          ys <- right.evalDynamic(input)
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
    def eval[B1 >: Boolean](input: A)(implicit schema: Schema[B1]): Either[OpticCheck, Seq[B1]] =
      for {
        xs <- left.eval[Boolean](input)(Schema[Boolean])
        ys <- right.eval[Boolean](input)(Schema[Boolean])
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
    def eval[B1 >: Boolean](input: A)(implicit schema: Schema[B1]): Either[OpticCheck, Seq[B1]] =
      for {
        xs <- expr.eval[Boolean](input)(Schema[Boolean])
      } yield xs.map(x => (!x).asInstanceOf[B1])

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
    numericType: NumericPrimitiveType[A]
  ) extends BinaryOp[S, A, A] {
    def eval[B1 >: A](input: S)(implicit schema: Schema[B1]): Either[OpticCheck, Seq[B1]] =
      for {
        xs <- left.eval(input)(schema.asInstanceOf[Schema[A]])
        ys <- right.eval(input)(schema.asInstanceOf[Schema[A]])
      } yield {
        val n = numericType.numeric
        operator match {
          case _: ArithmeticOperator.Add.type      => for { x <- xs; y <- ys } yield n.plus(x, y)
          case _: ArithmeticOperator.Subtract.type => for { x <- xs; y <- ys } yield n.minus(x, y)
          case _: ArithmeticOperator.Multiply.type => for { x <- xs; y <- ys } yield n.times(x, y)
          case _: ArithmeticOperator.Divide.type   => for { x <- xs; y <- ys } yield divide(x, y)
          case _: ArithmeticOperator.Pow.type      => for { x <- xs; y <- ys } yield pow(x, y)
          case _: ArithmeticOperator.Modulo.type   => for { x <- xs; y <- ys } yield modulo(x, y)
        }
      }

    def evalDynamic(input: S): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- left.eval(input)(numericType.schema)
        ys <- right.eval(input)(numericType.schema)
      } yield {
        val n = numericType.numeric
        operator match {
          case _: ArithmeticOperator.Add.type      => for { x <- xs; y <- ys } yield toDynamicValue(n.plus(x, y))
          case _: ArithmeticOperator.Subtract.type => for { x <- xs; y <- ys } yield toDynamicValue(n.minus(x, y))
          case _: ArithmeticOperator.Multiply.type => for { x <- xs; y <- ys } yield toDynamicValue(n.times(x, y))
          case _: ArithmeticOperator.Divide.type   => for { x <- xs; y <- ys } yield toDynamicValue(divide(x, y))
          case _: ArithmeticOperator.Pow.type      => for { x <- xs; y <- ys } yield toDynamicValue(pow(x, y))
          case _: ArithmeticOperator.Modulo.type   => for { x <- xs; y <- ys } yield toDynamicValue(modulo(x, y))
        }
      }

    private def divide(x: A, y: A): A = (x: Any) match {
      case xInt: Int           => (xInt / y.asInstanceOf[Int]).asInstanceOf[A]
      case xLong: Long         => (xLong / y.asInstanceOf[Long]).asInstanceOf[A]
      case xFloat: Float       => (xFloat / y.asInstanceOf[Float]).asInstanceOf[A]
      case xDouble: Double     => (xDouble / y.asInstanceOf[Double]).asInstanceOf[A]
      case xByte: Byte         => (xByte / y.asInstanceOf[Byte]).asInstanceOf[A]
      case xShort: Short       => (xShort / y.asInstanceOf[Short]).asInstanceOf[A]
      case xBigInt: BigInt     => (xBigInt / y.asInstanceOf[BigInt]).asInstanceOf[A]
      case xBigDec: BigDecimal => (xBigDec / y.asInstanceOf[BigDecimal]).asInstanceOf[A]
    }

    private def pow(x: A, y: A): A = (x: Any) match {
      case xInt: Int           => Math.pow(xInt.toDouble, y.asInstanceOf[Int].toDouble).toInt.asInstanceOf[A]
      case xLong: Long         => Math.pow(xLong.toDouble, y.asInstanceOf[Long].toDouble).toLong.asInstanceOf[A]
      case xFloat: Float       => Math.pow(xFloat.toDouble, y.asInstanceOf[Float].toDouble).toFloat.asInstanceOf[A]
      case xDouble: Double     => Math.pow(xDouble, y.asInstanceOf[Double]).asInstanceOf[A]
      case xByte: Byte         => Math.pow(xByte.toDouble, y.asInstanceOf[Byte].toDouble).toByte.asInstanceOf[A]
      case xShort: Short       => Math.pow(xShort.toDouble, y.asInstanceOf[Short].toDouble).toShort.asInstanceOf[A]
      case xBigInt: BigInt     => xBigInt.pow(y.asInstanceOf[BigInt].toInt).asInstanceOf[A]
      case xBigDec: BigDecimal =>
        xBigDec.pow(y.asInstanceOf[BigDecimal].toInt).asInstanceOf[A]
    }

    private def modulo(x: A, y: A): A = (x: Any) match {
      case xInt: Int           => (xInt % y.asInstanceOf[Int]).asInstanceOf[A]
      case xLong: Long         => (xLong % y.asInstanceOf[Long]).asInstanceOf[A]
      case xFloat: Float       => (xFloat % y.asInstanceOf[Float]).asInstanceOf[A]
      case xDouble: Double     => (xDouble % y.asInstanceOf[Double]).asInstanceOf[A]
      case xByte: Byte         => (xByte % y.asInstanceOf[Byte]).asInstanceOf[A]
      case xShort: Short       => (xShort % y.asInstanceOf[Short]).asInstanceOf[A]
      case xBigInt: BigInt     => (xBigInt % y.asInstanceOf[BigInt]).asInstanceOf[A]
      case xBigDec: BigDecimal => (xBigDec % y.asInstanceOf[BigDecimal]).asInstanceOf[A]
    }

    private[this] val toDynamicValue: A => DynamicValue = numericType.toDynamicValue
  }

  sealed trait ArithmeticOperator

  object ArithmeticOperator {
    case object Add      extends ArithmeticOperator
    case object Subtract extends ArithmeticOperator
    case object Multiply extends ArithmeticOperator
    case object Divide   extends ArithmeticOperator
    case object Pow      extends ArithmeticOperator
    case object Modulo   extends ArithmeticOperator
  }

  sealed trait BitwiseOperator

  object BitwiseOperator {
    case object And                extends BitwiseOperator
    case object Or                 extends BitwiseOperator
    case object Xor                extends BitwiseOperator
    case object LeftShift          extends BitwiseOperator
    case object RightShift         extends BitwiseOperator
    case object UnsignedRightShift extends BitwiseOperator
  }

  final case class Bitwise[S, A](
    left: SchemaExpr[S, A],
    right: SchemaExpr[S, A],
    operator: BitwiseOperator
  ) extends BinaryOp[S, A, A] {
    def eval[B1 >: A](input: S)(implicit schema: Schema[B1]): Either[OpticCheck, Seq[B1]] =
      for {
        xs <- left.evalDynamic(input)
        ys <- right.evalDynamic(input)
      } yield {
        for {
          x <- xs
          y <- ys
        } yield applyBitwise(x, y)
      }

    def evalDynamic(input: S): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- left.evalDynamic(input)
        ys <- right.evalDynamic(input)
      } yield {
        for {
          x <- xs
          y <- ys
        } yield applyBitwiseDynamic(x, y)
      }

    private def applyBitwise(x: DynamicValue, y: DynamicValue): A = {
      val result = applyBitwiseDynamic(x, y)
      result match {
        case DynamicValue.Primitive(PrimitiveValue.Byte(v))  => v.asInstanceOf[A]
        case DynamicValue.Primitive(PrimitiveValue.Short(v)) => v.asInstanceOf[A]
        case DynamicValue.Primitive(PrimitiveValue.Int(v))   => v.asInstanceOf[A]
        case DynamicValue.Primitive(PrimitiveValue.Long(v))  => v.asInstanceOf[A]
        case _                                               => throw new IllegalStateException(s"Unexpected bitwise result type: $result")
      }
    }

    private def applyBitwiseDynamic(x: DynamicValue, y: DynamicValue): DynamicValue = {
      val (lVal, lIsLong) = extractIntegral(x)
      val (rVal, rIsLong) = extractIntegral(y)

      val resultIsLong = operator match {
        case BitwiseOperator.LeftShift | BitwiseOperator.RightShift | BitwiseOperator.UnsignedRightShift =>
          lIsLong
        case _ =>
          lIsLong || rIsLong
      }

      val result = operator match {
        case BitwiseOperator.And                => lVal & rVal
        case BitwiseOperator.Or                 => lVal | rVal
        case BitwiseOperator.Xor                => lVal ^ rVal
        case BitwiseOperator.LeftShift          => lVal << rVal.toInt
        case BitwiseOperator.RightShift         => lVal >> rVal.toInt
        case BitwiseOperator.UnsignedRightShift => lVal >>> rVal.toInt
      }

      if (resultIsLong) DynamicValue.Primitive(PrimitiveValue.Long(result))
      else DynamicValue.Primitive(PrimitiveValue.Int(result.toInt))
    }

    private def extractIntegral(dv: DynamicValue): (Long, Boolean) = dv match {
      case DynamicValue.Primitive(PrimitiveValue.Byte(v))  => (v.toLong, false)
      case DynamicValue.Primitive(PrimitiveValue.Short(v)) => (v.toLong, false)
      case DynamicValue.Primitive(PrimitiveValue.Int(v))   => (v.toLong, false)
      case DynamicValue.Primitive(PrimitiveValue.Long(v))  => (v, true)
      case _                                               => throw new IllegalArgumentException(s"Bitwise operation requires integral types, got: $dv")
    }
  }

  final case class BitwiseNot[S, A](expr: SchemaExpr[S, A]) extends UnaryOp[S, A] {
    def eval[B1 >: A](input: S)(implicit schema: Schema[B1]): Either[OpticCheck, Seq[B1]] =
      for {
        xs <- expr.evalDynamic(input)
      } yield xs.map(applyNot)

    def evalDynamic(input: S): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- expr.evalDynamic(input)
      } yield xs.map(applyNotDynamic)

    private def applyNot(x: DynamicValue): A = {
      val result = applyNotDynamic(x)
      result match {
        case DynamicValue.Primitive(PrimitiveValue.Byte(v))  => v.asInstanceOf[A]
        case DynamicValue.Primitive(PrimitiveValue.Short(v)) => v.asInstanceOf[A]
        case DynamicValue.Primitive(PrimitiveValue.Int(v))   => v.asInstanceOf[A]
        case DynamicValue.Primitive(PrimitiveValue.Long(v))  => v.asInstanceOf[A]
        case _                                               => throw new IllegalStateException(s"Unexpected bitwise NOT result type: $result")
      }
    }

    private def applyNotDynamic(x: DynamicValue): DynamicValue = x match {
      case DynamicValue.Primitive(PrimitiveValue.Byte(v))  => DynamicValue.Primitive(PrimitiveValue.Byte((~v).toByte))
      case DynamicValue.Primitive(PrimitiveValue.Short(v)) => DynamicValue.Primitive(PrimitiveValue.Short((~v).toShort))
      case DynamicValue.Primitive(PrimitiveValue.Int(v))   => DynamicValue.Primitive(PrimitiveValue.Int(~v))
      case DynamicValue.Primitive(PrimitiveValue.Long(v))  => DynamicValue.Primitive(PrimitiveValue.Long(~v))
      case _                                               => throw new IllegalArgumentException(s"Bitwise NOT requires integral type, got: $x")
    }
  }

  final case class StringConcat[A](left: SchemaExpr[A, String], right: SchemaExpr[A, String])
      extends BinaryOp[A, String, String] {
    def eval[B1 >: String](input: A)(implicit schema: Schema[B1]): Either[OpticCheck, Seq[B1]] =
      for {
        xs <- left.eval[String](input)(Schema[String])
        ys <- right.eval[String](input)(Schema[String])
      } yield for { x <- xs; y <- ys } yield (x + y).asInstanceOf[B1]

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
    def eval[B1 >: Boolean](input: A)(implicit schema: Schema[B1]): Either[OpticCheck, Seq[B1]] =
      for {
        xs <- regex.eval(input)(Schema[String])
        ys <- string.eval(input)(Schema[String])
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
    def eval[B1 >: Int](input: A)(implicit schema: Schema[B1]): Either[OpticCheck, Seq[B1]] =
      for {
        xs <- string.eval(input)(Schema[String])
      } yield xs.map(_.length)

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- string.eval(input)
      } yield xs.map(x => toDynamicValue(x.length))

    private[this] def toDynamicValue(value: Int): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Int(value))
  }

  final case class StringSubstring[A](
    string: SchemaExpr[A, String],
    start: SchemaExpr[A, Int],
    end: SchemaExpr[A, Int]
  ) extends SchemaExpr[A, String] {
    def eval[B1 >: String](input: A)(implicit schema: Schema[B1]): Either[OpticCheck, Seq[B1]] =
      for {
        strings <- string.eval(input)(Schema[String])
        starts  <- start.eval(input)(Schema[Int])
        ends    <- end.eval(input)(Schema[Int])
      } yield
        for {
          s  <- strings
          st <- starts
          en <- ends
        } yield s.substring(st, en)

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        strings <- string.eval(input)
        starts  <- start.eval(input)
        ends    <- end.eval(input)
      } yield
        for {
          s  <- strings
          st <- starts
          en <- ends
        } yield toDynamicValue(s.substring(st, en))

    private[this] def toDynamicValue(value: String): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.String(value))
  }

  final case class StringTrim[A](string: SchemaExpr[A, String]) extends SchemaExpr[A, String] {
    def eval[B1 >: String](input: A)(implicit schema: Schema[B1]): Either[OpticCheck, Seq[B1]] =
      for {
        xs <- string.eval(input)(Schema[String])
      } yield xs.map(_.trim)

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- string.eval(input)
      } yield xs.map(x => toDynamicValue(x.trim))

    private[this] def toDynamicValue(value: String): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.String(value))
  }

  final case class StringToUpperCase[A](string: SchemaExpr[A, String]) extends SchemaExpr[A, String] {
    def eval[B1 >: String](input: A)(implicit schema: Schema[B1]): Either[OpticCheck, Seq[B1]] =
      for {
        xs <- string.eval(input)(Schema[String])
      } yield xs.map(_.toUpperCase)

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- string.eval(input)
      } yield xs.map(x => toDynamicValue(x.toUpperCase))

    private[this] def toDynamicValue(value: String): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.String(value))
  }

  final case class StringToLowerCase[A](string: SchemaExpr[A, String]) extends SchemaExpr[A, String] {
    def eval[B1 >: String](input: A)(implicit schema: Schema[B1]): Either[OpticCheck, Seq[B1]] =
      for {
        xs <- string.eval(input)(Schema[String])
      } yield xs.map(_.toLowerCase)

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        xs <- string.eval(input)
      } yield xs.map(x => toDynamicValue(x.toLowerCase))

    private[this] def toDynamicValue(value: String): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.String(value))
  }

  final case class StringReplace[A](
    string: SchemaExpr[A, String],
    target: SchemaExpr[A, String],
    replacement: SchemaExpr[A, String]
  ) extends SchemaExpr[A, String] {
    def eval[B1 >: String](input: A)(implicit schema: Schema[B1]): Either[OpticCheck, Seq[B1]] =
      for {
        strings      <- string.eval[String](input)(Schema[String])
        targets      <- target.eval[String](input)(Schema[String])
        replacements <- replacement.eval[String](input)(Schema[String])
      } yield
        for {
          s <- strings
          t <- targets
          r <- replacements
        } yield s.replace(t, r).asInstanceOf[B1]

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        strings      <- string.eval(input)
        targets      <- target.eval(input)
        replacements <- replacement.eval(input)
      } yield
        for {
          s <- strings
          t <- targets
          r <- replacements
        } yield toDynamicValue(s.replace(t, r))

    private[this] def toDynamicValue(value: String): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.String(value))
  }

  final case class StringStartsWith[A](
    string: SchemaExpr[A, String],
    prefix: SchemaExpr[A, String]
  ) extends SchemaExpr[A, Boolean] {
    def eval[B1 >: Boolean](input: A)(implicit schema: Schema[B1]): Either[OpticCheck, Seq[B1]] =
      for {
        strings  <- string.eval[String](input)(Schema[String])
        prefixes <- prefix.eval[String](input)(Schema[String])
      } yield
        for {
          s <- strings
          p <- prefixes
        } yield s.startsWith(p).asInstanceOf[B1]

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        strings  <- string.eval(input)
        prefixes <- prefix.eval(input)
      } yield
        for {
          s <- strings
          p <- prefixes
        } yield toDynamicValue(s.startsWith(p))

    private[this] def toDynamicValue(value: Boolean): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Boolean(value))
  }

  final case class StringEndsWith[A](
    string: SchemaExpr[A, String],
    suffix: SchemaExpr[A, String]
  ) extends SchemaExpr[A, Boolean] {
    def eval[B1 >: Boolean](input: A)(implicit schema: Schema[B1]): Either[OpticCheck, Seq[B1]] =
      for {
        strings  <- string.eval[String](input)(Schema[String])
        suffixes <- suffix.eval[String](input)(Schema[String])
      } yield
        for {
          s  <- strings
          sx <- suffixes
        } yield s.endsWith(sx).asInstanceOf[B1]

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        strings  <- string.eval(input)
        suffixes <- suffix.eval(input)
      } yield
        for {
          s  <- strings
          sx <- suffixes
        } yield toDynamicValue(s.endsWith(sx))

    private[this] def toDynamicValue(value: Boolean): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Boolean(value))
  }

  final case class StringContains[A](
    string: SchemaExpr[A, String],
    substring: SchemaExpr[A, String]
  ) extends SchemaExpr[A, Boolean] {
    def eval[B1 >: Boolean](input: A)(implicit schema: Schema[B1]): Either[OpticCheck, Seq[B1]] =
      for {
        strings    <- string.eval[String](input)(Schema[String])
        substrings <- substring.eval[String](input)(Schema[String])
      } yield
        for {
          s   <- strings
          sub <- substrings
        } yield s.contains(sub).asInstanceOf[B1]

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        strings    <- string.eval(input)
        substrings <- substring.eval(input)
      } yield
        for {
          s   <- strings
          sub <- substrings
        } yield toDynamicValue(s.contains(sub))

    private[this] def toDynamicValue(value: Boolean): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Boolean(value))
  }

  final case class StringIndexOf[A](
    string: SchemaExpr[A, String],
    substring: SchemaExpr[A, String]
  ) extends SchemaExpr[A, Int] {
    def eval[B1 >: Int](input: A)(implicit schema: Schema[B1]): Either[OpticCheck, Seq[B1]] =
      for {
        strings    <- string.eval[String](input)(Schema[String])
        substrings <- substring.eval[String](input)(Schema[String])
      } yield
        for {
          s   <- strings
          sub <- substrings
        } yield s.indexOf(sub).asInstanceOf[B1]

    def evalDynamic(input: A): Either[OpticCheck, Seq[DynamicValue]] =
      for {
        strings    <- string.eval(input)
        substrings <- substring.eval(input)
      } yield
        for {
          s   <- strings
          sub <- substrings
        } yield toDynamicValue(s.indexOf(sub))

    private[this] def toDynamicValue(value: Int): DynamicValue =
      new DynamicValue.Primitive(new PrimitiveValue.Int(value))
  }
}
