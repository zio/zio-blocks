package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, Schema}

final case class Migration[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  dynamicMigration: DynamicMigration
) {

  def apply(value: A): Either[MigrationError, B] = {
    val dynamicValue = sourceSchema.toDynamicValue(value)
    dynamicMigration(dynamicValue).flatMap { result =>
      targetSchema.fromDynamicValue(result) match {
        case Right(b)  => Right(b)
        case Left(err) => Left(MigrationError.fromSchemaError(err))
      }
    }
  }

  def applyDynamic(value: DynamicValue): Either[MigrationError, B] =
    dynamicMigration(value).flatMap { result =>
      targetSchema.fromDynamicValue(result) match {
        case Right(b)  => Right(b)
        case Left(err) => Left(MigrationError.fromSchemaError(err))
      }
    }

  def applyToDynamic(value: A): Either[MigrationError, DynamicValue] = {
    val dynamicValue = sourceSchema.toDynamicValue(value)
    dynamicMigration(dynamicValue)
  }

  def applyOption(value: A): Option[B] =
    apply(value).toOption

  def applyUnsafe(value: A): B =
    apply(value) match {
      case Right(b)  => b
      case Left(err) => throw err
    }

  def andThen[C](that: Migration[B, C]): Migration[A, C] =
    Migration(sourceSchema, that.targetSchema, dynamicMigration ++ that.dynamicMigration)

  def >>>[C](that: Migration[B, C]): Migration[A, C] = andThen(that)

  def reverse: Migration[B, A] =
    Migration(targetSchema, sourceSchema, dynamicMigration.reverse)

  def size: Int = dynamicMigration.size

  def isEmpty: Boolean = dynamicMigration.isEmpty

  def actions: Vector[MigrationAction] = dynamicMigration.actions

  override def toString: String =
    s"Migration[${sourceSchema.reflect.typeName.name} -> ${targetSchema.reflect.typeName.name}]" +
      s"(${dynamicMigration.size} actions)"
}

object Migration extends MigrationCompanionVersionSpecific {

  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration(schema, schema, DynamicMigration.identity)

  def newBuilder[A, B](implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): MigrationBuilder[A, B] =
    MigrationBuilder(sourceSchema, targetSchema, Vector.empty)

  def fromDynamic[A, B](
    dynamicMigration: DynamicMigration
  )(implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] =
    Migration(sourceSchema, targetSchema, dynamicMigration)

  def fromActions[A, B](
    actions: MigrationAction*
  )(implicit
    sourceSchema: Schema[A],
    targetSchema: Schema[B]
  ): Migration[A, B] =
    Migration(sourceSchema, targetSchema, DynamicMigration(actions.toVector))

  def compose[A, B, C](m1: Migration[A, B], m2: Migration[B, C]): Migration[A, C] =
    m1 andThen m2
}

trait MigrationCompanionVersionSpecific
