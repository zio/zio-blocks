package zio.blocks.schema.migration

import zio.blocks.schema.DynamicOptic
import zio.blocks.schema.Schema

sealed trait SchemaExpr[A, +B] {
  def apply(value: A): Either[MigrationError, B]

  def map[C](f: B => C): SchemaExpr[A, C] =
    SchemaExpr.Map(this, f)

  def flatMap[C](f: B => SchemaExpr[A, C]): SchemaExpr[A, C] =
    SchemaExpr.FlatMap(this, f)
}

object SchemaExpr {
    final case class Constant[B](value: B) extends SchemaExpr[Any, B] {
        def apply(value: Any): Either[MigrationError, B] = Right(this.value)
    }

    final case class DefaultValue[B]() extends SchemaExpr[Any, B] {
        def apply(value: Any): Either[MigrationError, B] = Left(MigrationError.NotYetImplemented) // Needs schema access
    }

    final case class StringTo[B](fromString: String => Either[String, B]) extends SchemaExpr[String, B] {
        def apply(value: String): Either[MigrationError, B] = fromString(value).left.map(MigrationError.ConversionError)
    }

    final case class Map[A, B, C](expr: SchemaExpr[A, B], f: B => C) extends SchemaExpr[A, C] {
        def apply(value: A): Either[MigrationError, C] = expr(value).map(f)
    }

    final case class FlatMap[A, B, C](expr: SchemaExpr[A, B], f: B => SchemaExpr[A, C]) extends SchemaExpr[A, C] {
        def apply(value: A): Either[MigrationError, C] = expr(value).flatMap(b => f(b)(value))
    }

    def substring(start: Int, end: Int): SchemaExpr[String, String] =
        StringTo(s => Right(s.substring(start, end)))
    
    def toUpperCase: SchemaExpr[String, String] =
        StringTo(s => Right(s.toUpperCase))

    def toLowerCase: SchemaExpr[String, String] =
        StringTo(s => Right(s.toLowerCase))

    final case class Path[A, B](optic: DynamicOptic)(implicit schemaA: Schema[A], schemaB: Schema[B]) extends SchemaExpr[A, B] {
        def apply(value: A): Either[MigrationError, B] = {
            val dynamicA = schemaA.toDynamicValue(value)
            DynamicOptic.get(optic, dynamicA).flatMap { dynamicB =>
                 schemaB.fromDynamicValue(dynamicB).left.map(e => MigrationError.ConversionError(e.toString))
            }
        }
    }
}