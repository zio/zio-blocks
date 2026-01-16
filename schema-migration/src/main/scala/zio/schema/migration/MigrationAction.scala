package zio.schema.migration

import zio.schema._
import zio.Chunk
import scala.collection.immutable.ListMap

/**
 * An action that transforms a DynamicValue from one shape to another. All
 * actions are serializable and can be reversed if possible.
 */
sealed trait MigrationAction {
  def reverse: Option[MigrationAction]

  def apply(value: DynamicValue): Either[MigrationError, DynamicValue]
}

object MigrationAction {

  /** Add a field with a default value */
  case class AddField(
    path: FieldPath,
    defaultValue: DynamicValue
  ) extends MigrationAction {

    def reverse: Option[MigrationAction] = Some(DropField(path))

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      path match {
        case FieldPath.Root(name) =>
          value match {
            case DynamicValue.Record(typeId, values) =>
              // Use existing typeId, convert to ListMap with new field
              Right(
                DynamicValue.Record(
                  typeId,
                  values + (name -> defaultValue)
                )
              )
            case _ =>
              Left(
                MigrationError.TypeMismatch(
                  path.serialize,
                  "Record",
                  value.getClass.getSimpleName
                )
              )
          }
        case nested: FieldPath.Nested =>
          // Use NestedFieldSupport for nested paths
          NestedFieldSupport.addNested(value, path, defaultValue)
      }
  }

  /** Drop a field from the record */
  case class DropField(path: FieldPath) extends MigrationAction {
    def reverse: Option[MigrationAction] = None // Lossy - cannot recover dropped data

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      path match {
        case FieldPath.Root(name) =>
          value match {
            case DynamicValue.Record(typeId, values) =>
              Right(
                DynamicValue.Record(
                  typeId,
                  values - name
                )
              )
            case _ =>
              Left(
                MigrationError.TypeMismatch(
                  path.serialize,
                  "Record",
                  value.getClass.getSimpleName
                )
              )
          }
        case nested: FieldPath.Nested =>
          // Use NestedFieldSupport for nested paths
          NestedFieldSupport.dropNested(value, path)
      }
  }

  /** Rename a field */
  case class RenameField(
    oldPath: FieldPath,
    newPath: FieldPath
  ) extends MigrationAction {

    def reverse: Option[MigrationAction] = Some(RenameField(newPath, oldPath))

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      (oldPath, newPath) match {
        case (FieldPath.Root(oldName), FieldPath.Root(newName)) =>
          value match {
            case DynamicValue.Record(typeId, values) =>
              values.get(oldName) match {
                case Some(fieldValue) =>
                  // Remove old field, add with new name
                  val updated = (values - oldName) + (newName -> fieldValue)
                  Right(DynamicValue.Record(typeId, updated))
                case None =>
                  Left(MigrationError.FieldNotFound(oldPath.serialize))
              }
            case _ =>
              Left(
                MigrationError.TypeMismatch(
                  oldPath.serialize,
                  "Record",
                  value.getClass.getSimpleName
                )
              )
          }
        case _ =>
          // Handle nested paths - get old value, drop old, add new
          for {
            oldValue  <- NestedFieldSupport.getNested(value, oldPath)
            afterDrop <- NestedFieldSupport.dropNested(value, oldPath)
            result    <- NestedFieldSupport.addNested(afterDrop, newPath, oldValue)
          } yield result
      }
  }

  /** Transform a field using a serializable transformation */
  case class TransformField(
    path: FieldPath,
    transformation: SerializableTransformation
  ) extends MigrationAction {

    def reverse: Option[MigrationAction] = None // Cannot reverse arbitrary transformation

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      path match {
        case FieldPath.Root(name) =>
          value match {
            case DynamicValue.Record(typeId, values) =>
              values.get(name) match {
                case Some(fieldValue) =>
                  transformation(fieldValue) match {
                    case Right(newValue) =>
                      Right(DynamicValue.Record(typeId, values + (name -> newValue)))
                    case Left(error) =>
                      Left(MigrationError.TransformationFailed(path.serialize, error))
                  }
                case None =>
                  Left(MigrationError.FieldNotFound(path.serialize))
              }
            case _ =>
              Left(
                MigrationError.TypeMismatch(
                  path.serialize,
                  "Record",
                  value.getClass.getSimpleName
                )
              )
          }
        case nested: FieldPath.Nested =>
          // Use NestedFieldSupport for nested transformations
          NestedFieldSupport.applyNested(
            value,
            path,
            v => transformation(v).left.map(err => MigrationError.TransformationFailed(path.serialize, err))
          )
      }
  }

  /** Compose multiple field migrations */
  case class Composite(
    actions: Chunk[MigrationAction]
  ) extends MigrationAction {

    def reverse: Option[MigrationAction] = {
      val reversed = actions.reverse.map(_.reverse)
      if (reversed.forall(_.isDefined)) {
        Some(Composite(Chunk.fromIterable(reversed.flatten)))
      } else {
        None
      }
    }

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) {
        case (Right(v), action) => action(v)
        case (left, _)          => left
      }
  }

  // Schema for serialization
  implicit val schema: Schema[MigrationAction] = DeriveSchema.gen[MigrationAction]
}
