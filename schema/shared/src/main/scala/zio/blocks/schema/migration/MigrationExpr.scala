package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, Schema}

sealed trait MigrationExpr[-S, +A] {
  def evalDynamic(input: DynamicValue): Either[String, DynamicValue]
}

object MigrationExpr {

  final case class Literal[A](value: A, schema: Schema[A]) extends MigrationExpr[Any, A] {
    private val dynamicValue: DynamicValue = schema.toDynamicValue(value)

    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] =
      Right(dynamicValue)
  }

  final case class DefaultValue[S](fieldSchema: Schema[_]) extends MigrationExpr[S, Any] {
    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] =
      fieldSchema.getDefaultValue match {
        case Some(default) => Right(fieldSchema.asInstanceOf[Schema[Any]].toDynamicValue(default))
        case None          =>
          Left(
            s"No default value available for type ${fieldSchema.reflect.typeId}. " +
              "Use MigrationExpr.literal(value) to provide an explicit value instead."
          )
      }
  }

  private final case class FieldAccess[S, A](path: DynamicOptic, fieldSchema: Schema[A]) extends MigrationExpr[S, A] {
    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] =
      input.get(path).one match {
        case Right(value) => Right(value)
        case Left(err)    => Left(s"Field not found at path $path: ${err.message}")
      }
  }

  final case class Transform[S, A, B](
    source: MigrationExpr[S, A],
    transform: DynamicValueTransform
  ) extends MigrationExpr[S, B] {
    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] =
      source.evalDynamic(input).flatMap(transform.apply)
  }

  final case class Concat[S](
    left: MigrationExpr[S, String],
    right: MigrationExpr[S, String]
  ) extends MigrationExpr[S, String] {
    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] =
      for {
        l    <- left.evalDynamic(input)
        r    <- right.evalDynamic(input)
        lStr <- extractString(l).toRight("Expected string on left side of concat")
        rStr <- extractString(r).toRight("Expected string on right side of concat")
      } yield DynamicValue.string(lStr + rStr)

    private def extractString(dv: DynamicValue): Option[String] = dv match {
      case DynamicValue.Primitive(pv) =>
        pv match {
          case PrimitiveValue.String(s) => Some(s)
          case _                        => None
        }
      case _ => None
    }
  }

  private final case class PrimitiveConvert[S, A, B](
    source: MigrationExpr[S, A],
    conversion: PrimitiveConversion
  ) extends MigrationExpr[S, B] {
    def evalDynamic(input: DynamicValue): Either[String, DynamicValue] =
      source.evalDynamic(input).flatMap(conversion.apply)
  }

  def literal[A](value: A)(implicit schema: Schema[A]): MigrationExpr[Any, A] =
    Literal(value, schema)

  def field[S, A](path: DynamicOptic)(implicit fieldSchema: Schema[A]): MigrationExpr[S, A] =
    FieldAccess(path, fieldSchema)

  def transform[S, A, B](
    source: MigrationExpr[S, A],
    transform: DynamicValueTransform
  ): MigrationExpr[S, B] =
    Transform(source, transform)

  def concat[S](
    left: MigrationExpr[S, String],
    right: MigrationExpr[S, String]
  ): MigrationExpr[S, String] =
    Concat(left, right)

  def convert[S, A, B](
    source: MigrationExpr[S, A],
    conversion: PrimitiveConversion
  ): MigrationExpr[S, B] =
    PrimitiveConvert(source, conversion)
}
