package zio.schema.migration

import zio.schema._
import zio.Chunk

/**
 * Enhanced support for nested field operations. Handles paths like
 * "person.address.street"
 */
object NestedFieldSupport {

  /**
   * Apply a migration action to a nested field within a DynamicValue
   */
  def applyNested(
    value: DynamicValue,
    path: FieldPath,
    operation: DynamicValue => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] =
    path match {
      case FieldPath.Root(name) =>
        // Base case: apply directly to the field
        value match {
          case DynamicValue.Record(typeId, values) =>
            values.find(_._1 == name) match {
              case Some((_, fieldValue)) =>
                operation(fieldValue).map { newValue =>
                  DynamicValue.Record(
                    typeId,
                    values.map {
                      case (n, v) if n == name => (n, newValue)
                      case other               => other
                    }
                  )
                }
              case None =>
                Left(MigrationError.FieldNotFound(name))
            }
          case _ =>
            Left(
              MigrationError.TypeMismatch(
                name,
                "Record",
                value.getClass.getSimpleName
              )
            )
        }

      case FieldPath.Nested(parent, child) =>
        // Recursive case: navigate to parent, then apply to child
        value match {
          case DynamicValue.Record(typeId, values) =>
            parent match {
              case FieldPath.Root(parentName) =>
                values.find(_._1 == parentName) match {
                  case Some((_, parentValue)) =>
                    // Recursively apply to nested path
                    val childPath = FieldPath.Root(child)
                    applyNested(parentValue, childPath, operation).map { newParentValue =>
                      DynamicValue.Record(
                        typeId,
                        values.map {
                          case (n, v) if n == parentName => (n, newParentValue)
                          case other                     => other
                        }
                      )
                    }
                  case None =>
                    Left(MigrationError.FieldNotFound(parentName))
                }
              case nestedParent: FieldPath.Nested =>
                // Further nesting: recurse on parent
                applyNested(
                  value,
                  nestedParent,
                  parentValue => applyNested(parentValue, FieldPath.Root(child), operation)
                )
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
    }

  /**
   * Get a nested field value
   */
  def getNested(
    value: DynamicValue,
    path: FieldPath
  ): Either[MigrationError, DynamicValue] =
    path match {
      case FieldPath.Root(name) =>
        value match {
          case DynamicValue.Record(_, values) =>
            values
              .find(_._1 == name)
              .map { case (_, v) => Right(v) }
              .getOrElse(Left(MigrationError.FieldNotFound(name)))
          case _ =>
            Left(
              MigrationError.TypeMismatch(
                name,
                "Record",
                value.getClass.getSimpleName
              )
            )
        }

      case FieldPath.Nested(parent, child) =>
        getNested(value, parent).flatMap { parentValue =>
          getNested(parentValue, FieldPath.Root(child))
        }
    }

  /**
   * Set a nested field value
   */
  def setNested(
    value: DynamicValue,
    path: FieldPath,
    newValue: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    applyNested(value, path, _ => Right(newValue))

  /**
   * Check if a nested field exists
   */
  def hasNested(value: DynamicValue, path: FieldPath): Boolean =
    getNested(value, path).isRight

  /**
   * Add a nested field (creating parent structure if needed)
   */
  def addNested(
    value: DynamicValue,
    path: FieldPath,
    defaultValue: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    path match {
      case FieldPath.Root(name) =>
        value match {
          case DynamicValue.Record(typeId, values) =>
            if (values.exists(_._1 == name)) {
              Left(
                MigrationError.InvalidMigration(
                  s"Field $name already exists"
                )
              )
            } else {
              Right(DynamicValue.Record(typeId, values + (name -> defaultValue)))
            }
          case _ =>
            Left(
              MigrationError.TypeMismatch(
                name,
                "Record",
                value.getClass.getSimpleName
              )
            )
        }

      case FieldPath.Nested(parent, child) =>
        // Ensure parent exists, then add child
        getNested(value, parent).flatMap { parentValue =>
          addNested(parentValue, FieldPath.Root(child), defaultValue).flatMap { newParentValue =>
            setNested(value, parent, newParentValue)
          }
        }
    }

  /**
   * Drop a nested field
   */
  def dropNested(
    value: DynamicValue,
    path: FieldPath
  ): Either[MigrationError, DynamicValue] =
    path match {
      case FieldPath.Root(name) =>
        value match {
          case DynamicValue.Record(typeId, values) =>
            Right(
              DynamicValue.Record(
                typeId,
                values.filter(_._1 != name)
              )
            )
          case _ =>
            Left(
              MigrationError.TypeMismatch(
                name,
                "Record",
                value.getClass.getSimpleName
              )
            )
        }

      case FieldPath.Nested(parent, child) =>
        getNested(value, parent).flatMap { parentValue =>
          dropNested(parentValue, FieldPath.Root(child)).flatMap { newParentValue =>
            setNested(value, parent, newParentValue)
          }
        }
    }
}
