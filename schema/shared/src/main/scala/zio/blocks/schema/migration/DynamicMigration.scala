package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.DynamicValue

/**
 * A reified description of a schema migration.
 *
 * Unlike `Into` which is a function, `DynamicMigration` is a data structure
 * that describes *how* to transform one `DynamicValue` into another. This
 * allows for inspection, optimization, and serialization of migrations.
 */
sealed trait DynamicMigration { self =>

  /**
   * Applies this migration to a `DynamicValue`.
   */
  def migrate(value: DynamicValue): Either[String, DynamicValue]

  def +(that: DynamicMigration): DynamicMigration = DynamicMigration.Compose(self, that)
}

object DynamicMigration {

  case object Identity extends DynamicMigration {
    def migrate(value: DynamicValue): Either[String, DynamicValue] = Right(value)
  }

  final case class Fail(message: String) extends DynamicMigration {
    def migrate(value: DynamicValue): Either[String, DynamicValue] = Left(message)
  }

  final case class Compose(left: DynamicMigration, right: DynamicMigration) extends DynamicMigration {
    def migrate(value: DynamicValue): Either[String, DynamicValue] =
      left.migrate(value).flatMap(right.migrate)
  }

  /**
   * Renames a field in a Record.
   */
  final case class RenameField(oldName: String, newName: String) extends DynamicMigration {
    def migrate(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Record(fields) =>
        val idx = fields.indexWhere(_._1 == oldName)
        if (idx >= 0) {
          val (_, v) = fields(idx)
          Right(DynamicValue.Record(fields.updated(idx, (newName, v))))
        } else {
          // If strict, fail? For now, let's say if field not found, it's a no-op or error?
          // Usually strict migration requires field to exist.
          Left(s"Field '$oldName' not found for renaming to '$newName'")
        }
      case _ => Left("Cannot rename field on non-Record value")
    }
  }

  /**
   * Adds a new field with a constant value.
   */
  final case class AddClassField(name: String, defaultValue: DynamicValue) extends DynamicMigration {
    def migrate(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Record(fields) =>
        if (fields.exists(_._1 == name)) Left(s"Field '$name' already exists")
        else Right(DynamicValue.Record(fields :+ (name, defaultValue)))
      case _ => Left("Cannot add field to non-Record value")
    }
  }

  /**
   * Removes a field.
   */
  final case class RemoveField(name: String) extends DynamicMigration {
    def migrate(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Record(fields) =>
        Right(DynamicValue.Record(fields.filterNot(_._1 == name)))
      case _ => Left("Cannot remove field from non-Record value")
    }
  }

  /**
   * Transforms the value of a specific field.
   */
  final case class TransformField(name: String, migration: DynamicMigration) extends DynamicMigration {
    def migrate(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Record(fields) =>
        val idx = fields.indexWhere(_._1 == name)
        if (idx >= 0) {
          val (_, v) = fields(idx)
          migration.migrate(v).map { newVal =>
            DynamicValue.Record(fields.updated(idx, (name, newVal)))
          }
        } else {
          Left(s"Field '$name' not found for transformation")
        }
      case _ => Left("Cannot transform field on non-Record value")
    }
  }

  /**
   * Nests fields into a sub-record. Useful when moving `street`, `city` into
   * `address`.
   */
  final case class Nest(fieldNames: Chunk[String], intoField: String) extends DynamicMigration {
    def migrate(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Record(fields) =>
        val (toNest, keep) = fields.partition { case (k, _) => fieldNames.contains(k) }
        val nestedRecord   = DynamicValue.Record(toNest)
        Right(DynamicValue.Record(keep :+ (intoField, nestedRecord)))
      case _ => Left("Cannot nest fields on non-Record value")
    }
  }

  /**
   * Unnests a sub-record into the main record. Useful when moving
   * `address.street` back to top-level `street`.
   */
  final case class Unnest(fieldName: String) extends DynamicMigration {
    def migrate(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Record(fields) =>
        fields.find(_._1 == fieldName) match {
          case Some((_, DynamicValue.Record(nestedFields))) =>
            val others = fields.filterNot(_._1 == fieldName)
            Right(DynamicValue.Record(others ++ nestedFields))
          case Some(_) => Left(s"Field '$fieldName' is not a Record")
          case None    => Left(s"Field '$fieldName' not found for unnesting")
        }
      case _ => Left("Cannot unnest field on non-Record value")
    }
  }
}
