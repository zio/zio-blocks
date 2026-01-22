package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, DynamicOptic, PrimitiveValue}

/**
 * Represents pure, serializable expressions for value transformations in
 * migrations.
 *
 * SchemaExpr is used for:
 *   - Default values when adding fields
 *   - Transforming field values
 *   - Joining/splitting fields
 *
 * For this ticket, we only support primitive-to-primitive transformations. No
 * record/enum construction is allowed.
 */
sealed trait SchemaExpr {

  /**
   * Apply this expression to a DynamicValue context. Returns the computed value
   * or an error.
   */
  def apply(context: DynamicValue): Either[MigrationError, DynamicValue]
}

object SchemaExpr {

  /**
   * A literal constant value.
   */
  case class Literal(value: DynamicValue) extends SchemaExpr {
    def apply(context: DynamicValue): Either[MigrationError, DynamicValue] =
      Right(value)
  }

  /**
   * Special marker that uses the schema's default value. The macro captures the
   * field schema and calls schema.defaultValue.
   */
  case object DefaultValue extends SchemaExpr {
    def apply(context: DynamicValue): Either[MigrationError, DynamicValue] =
      Left(
        MigrationError.transformFailed(
          DynamicOptic.root,
          "DefaultValue must be resolved at compile time"
        )
      )
  }

  /**
   * Access a field from the context value.
   */
  case class GetField(path: DynamicOptic) extends SchemaExpr {
    def apply(context: DynamicValue): Either[MigrationError, DynamicValue] = {
      // Navigate through the path to get the value
      def navigate(value: DynamicValue, nodes: IndexedSeq[DynamicOptic.Node]): Either[MigrationError, DynamicValue] =
        if (nodes.isEmpty) Right(value)
        else {
          nodes.head match {
            case DynamicOptic.Node.Field(name) =>
              value match {
                case DynamicValue.Record(fields) =>
                  fields.find(_._1 == name) match {
                    case Some((_, fieldValue)) => navigate(fieldValue, nodes.tail)
                    case None                  => Left(MigrationError.fieldNotFound(path, name))
                  }
                case _ => Left(MigrationError.typeMismatch(path, "Record", value.getClass.getSimpleName))
              }
            case _ =>
              Left(MigrationError.invalidPath(path, s"Unsupported node type: ${nodes.head}"))
          }
        }
      navigate(context, path.nodes)
    }
  }

  /**
   * String concatenation of multiple expressions.
   */
  case class Concat(parts: Vector[SchemaExpr]) extends SchemaExpr {
    def apply(context: DynamicValue): Either[MigrationError, DynamicValue] = {
      val results = parts.map(_.apply(context))
      val errors  = results.collect { case Left(err) => err }

      if (errors.nonEmpty) {
        Left(errors.head) // return first error
      } else {
        val strings = results.collect {
          case Right(DynamicValue.Primitive(PrimitiveValue.String(s))) => s
          case Right(other)                                            => other.toString
        }
        Right(DynamicValue.Primitive(PrimitiveValue.String(strings.mkString)))
      }
    }
  }

  /**
   * Convert between primitive types. For example: Int -> String, String -> Int,
   * etc.
   */
  case class ConvertPrimitive(expr: SchemaExpr, targetType: String) extends SchemaExpr {
    def apply(context: DynamicValue): Either[MigrationError, DynamicValue] =
      expr.apply(context).flatMap { value =>
        value match {
          case DynamicValue.Primitive(prim) =>
            convertPrimitive(prim, targetType)
          case _ =>
            Left(
              MigrationError.typeMismatch(
                DynamicOptic.root,
                "Primitive",
                value.getClass.getSimpleName
              )
            )
        }
      }

    private def convertPrimitive(prim: PrimitiveValue, target: String): Either[MigrationError, DynamicValue] =
      // Basic primitive conversions
      (prim, target) match {
        case (PrimitiveValue.Int(i), "String") =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(i.toString)))
        case (PrimitiveValue.String(s), "Int") =>
          s.toIntOption match {
            case Some(i) => Right(DynamicValue.Primitive(PrimitiveValue.Int(i)))
            case None    => Left(MigrationError.transformFailed(DynamicOptic.root, s"Cannot convert '$s' to Int"))
          }
        case (PrimitiveValue.Long(l), "String") =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(l.toString)))
        case (PrimitiveValue.Boolean(b), "String") =>
          Right(DynamicValue.Primitive(PrimitiveValue.String(b.toString)))
        case _ =>
          Left(
            MigrationError.transformFailed(
              DynamicOptic.root,
              s"Unsupported conversion from ${prim.getClass.getSimpleName} to $target"
            )
          )
      }
  }

  // Helper constructors
  def literal(value: DynamicValue): SchemaExpr = Literal(value)

  def literalString(s: String): SchemaExpr =
    Literal(DynamicValue.Primitive(PrimitiveValue.String(s)))

  def literalInt(i: Int): SchemaExpr =
    Literal(DynamicValue.Primitive(PrimitiveValue.Int(i)))

  def literalBool(b: scala.Boolean): SchemaExpr =
    Literal(DynamicValue.Primitive(PrimitiveValue.Boolean(b)))

  def getField(path: DynamicOptic): SchemaExpr = GetField(path)

  def concat(parts: SchemaExpr*): SchemaExpr = Concat(parts.toVector)
}
