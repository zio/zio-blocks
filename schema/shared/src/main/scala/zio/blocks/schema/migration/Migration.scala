package zio.blocks.schema.migration

import zio.blocks.schema._

/**
 * ZIO Schema Migration System Pure, algebraic migration with serializable
 * actions.
 */

sealed trait MigrationError {
  def message: String
}

object MigrationError {
  case class PathError(path: String, override val message: String) extends MigrationError
  case class TransformError(override val message: String)          extends MigrationError
  case class ValidationError(override val message: String)         extends MigrationError
  case class TypeError(expected: String, actual: String)           extends MigrationError {
    def message: String = s"Expected $expected but got $actual"
  }
}

/** All migration actions are path-based and reversible */
sealed trait MigrationAction {
  def at: DynamicOptic
  def reverse: MigrationAction
}

object MigrationAction {
  // =========================================================================
  // RECORD ACTIONS
  // =========================================================================

  /** Add a new field with a default value */
  case class AddField(at: DynamicOptic, fieldName: String, default: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, fieldName, default)
  }

  /** Remove a field (storing its value for reverse) */
  case class DropField(at: DynamicOptic, fieldName: String, defaultForReverse: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, fieldName, defaultForReverse)
  }

  /** Rename a field from one name to another */
  case class Rename(at: DynamicOptic, from: String, to: String) extends MigrationAction {
    def reverse: MigrationAction = Rename(at, to, from)
  }

  /** Transform a field value using a pure expression */
  case class TransformValue(at: DynamicOptic, fieldName: String, transform: DynamicExpr) extends MigrationAction {
    def reverse: MigrationAction = this // Best-effort - transform may not be invertible
  }

  /** Convert Option[A] to A with a default for None */
  case class Mandate(at: DynamicOptic, fieldName: String, default: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = Optionalize(at, fieldName)
  }

  /** Wrap a field value in Option (Some) */
  case class Optionalize(at: DynamicOptic, fieldName: String) extends MigrationAction {
    def reverse: MigrationAction = Mandate(at, fieldName, DynamicValue.Primitive(PrimitiveValue.Unit))
  }

  /** Join multiple fields into one */
  case class Join(at: DynamicOptic, sourceFields: Vector[String], targetField: String, joinExpr: DynamicExpr)
      extends MigrationAction {
    def reverse: MigrationAction = Split(at, targetField, sourceFields, DynamicExpr.Identity)
  }

  /** Split one field into multiple */
  case class Split(at: DynamicOptic, sourceField: String, targetFields: Vector[String], splitExpr: DynamicExpr)
      extends MigrationAction {
    def reverse: MigrationAction = Join(at, targetFields, sourceField, DynamicExpr.Identity)
  }

  /** Change field type using a converter (primitive-to-primitive only) */
  case class ChangeType(at: DynamicOptic, fieldName: String, converter: DynamicExpr) extends MigrationAction {
    def reverse: MigrationAction = this // Best-effort - needs inverse converter
  }

  /** Set a field to a specific value */
  case class SetValue(at: DynamicOptic, fieldName: String, newValue: DynamicValue) extends MigrationAction {
    def reverse: MigrationAction = this // Best-effort - original value unknown
  }

  // =========================================================================
  // ENUM ACTIONS
  // =========================================================================

  /** Rename an enum case */
  case class RenameCase(at: DynamicOptic, from: String, to: String) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(at, to, from)
  }

  /** Transform an enum case's payload */
  case class TransformCase(at: DynamicOptic, caseName: String, transform: DynamicMigration) extends MigrationAction {
    def reverse: MigrationAction = TransformCase(at, caseName, transform.reverse)
  }

  // =========================================================================
  // COLLECTION ACTIONS
  // =========================================================================

  /** Transform all elements in a collection */
  case class TransformElements(at: DynamicOptic, transform: DynamicExpr) extends MigrationAction {
    def reverse: MigrationAction = this // Best-effort
  }

  /** Transform all keys in a map */
  case class TransformKeys(at: DynamicOptic, transform: DynamicExpr) extends MigrationAction {
    def reverse: MigrationAction = this // Best-effort
  }

  /** Transform all values in a map */
  case class TransformValues(at: DynamicOptic, transform: DynamicExpr) extends MigrationAction {
    def reverse: MigrationAction = this // Best-effort
  }

}

// =============================================================================
// DYNAMIC EXPRESSION - Pure transformations on DynamicValue
// (Separate from typed SchemaExpr[A, B] which operates on schema-typed values)
// =============================================================================

/**
 * DynamicExpr represents pure, serializable transformations on DynamicValue.
 * Used for all value-level transformations in dynamic migrations.
 */
sealed trait DynamicExpr {
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue]
}

object DynamicExpr {

  /** Identity transformation - returns input unchanged */
  case object Identity extends DynamicExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = Right(value)
  }

  /** Constant value - ignores input, returns constant */
  case class Const(value: DynamicValue) extends DynamicExpr {
    def apply(input: DynamicValue): Either[MigrationError, DynamicValue] = Right(value)
  }

  /** String to Int conversion */
  case object StringToInt extends DynamicExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        s.toIntOption match {
          case Some(i) => Right(DynamicValue.Primitive(PrimitiveValue.Int(i)))
          case None    => Left(MigrationError.TransformError(s"Cannot convert '$s' to Int"))
        }
      case other => Left(MigrationError.TypeError("String", other.getClass.getSimpleName))
    }
  }

  /** Int to String conversion */
  case object IntToString extends DynamicExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Int(i)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.String(i.toString)))
      case other => Left(MigrationError.TypeError("Int", other.getClass.getSimpleName))
    }
  }

  /** String to Long conversion */
  case object StringToLong extends DynamicExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        s.toLongOption match {
          case Some(l) => Right(DynamicValue.Primitive(PrimitiveValue.Long(l)))
          case None    => Left(MigrationError.TransformError(s"Cannot convert '$s' to Long"))
        }
      case other => Left(MigrationError.TypeError("String", other.getClass.getSimpleName))
    }
  }

  /** Long to String conversion */
  case object LongToString extends DynamicExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Long(l)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.String(l.toString)))
      case other => Left(MigrationError.TypeError("Long", other.getClass.getSimpleName))
    }
  }

  /** Int to Long widening */
  case object IntToLong extends DynamicExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Int(i)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Long(i.toLong)))
      case other => Left(MigrationError.TypeError("Int", other.getClass.getSimpleName))
    }
  }

  /** Compose two expressions */
  case class Compose(first: DynamicExpr, second: DynamicExpr) extends DynamicExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      first(value).flatMap(second(_))
  }

  /** Extract a field from a record */
  case class GetField(fieldName: String) extends DynamicExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Record(fields) =>
        fields.find(_._1 == fieldName) match {
          case Some((_, v)) => Right(v)
          case None         => Left(MigrationError.ValidationError(s"Field '$fieldName' not found"))
        }
      case other => Left(MigrationError.TypeError("Record", other.getClass.getSimpleName))
    }
  }

  /** Concatenate string fields */
  case class ConcatStrings(separator: String, fields: Vector[String]) extends DynamicExpr {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = value match {
      case DynamicValue.Record(recordFields) =>
        val parts = fields.flatMap { name =>
          recordFields.find(_._1 == name).collect { case (_, DynamicValue.Primitive(PrimitiveValue.String(s))) =>
            s
          }
        }
        Right(DynamicValue.Primitive(PrimitiveValue.String(parts.mkString(separator))))
      case other => Left(MigrationError.TypeError("Record", other.getClass.getSimpleName))
    }
  }
}

/** Pure data migration - fully serializable */
case class DynamicMigration(actions: Vector[MigrationAction]) {
  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(this.actions ++ that.actions)

  def reverse: DynamicMigration =
    DynamicMigration(actions.reverse.map(_.reverse))

  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
    actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) {
      case (Right(v), action) => applyAction(v, action)
      case (left, _)          => left
    }

  private def applyAction(value: DynamicValue, action: MigrationAction): Either[MigrationError, DynamicValue] = {
    val path = action.at.nodes
    if (path.isEmpty) applyAtRoot(value, action)
    else navigateAndApply(value, path, 0, action)
  }

  private def applyAtRoot(value: DynamicValue, action: MigrationAction): Either[MigrationError, DynamicValue] = {
    action match {
      case MigrationAction.AddField(_, fieldName, default) =>
        value match {
          case DynamicValue.Record(fields) =>
            if (fields.exists(_._1 == fieldName))
              Left(MigrationError.ValidationError(s"Field '$fieldName' already exists"))
            else Right(DynamicValue.Record(fields :+ (fieldName, default)))
          case other => Left(MigrationError.TypeError("Record", other.getClass.getSimpleName))
        }

      case MigrationAction.DropField(_, fieldName, _) =>
        value match {
          case DynamicValue.Record(fields) =>
            val newFields = fields.filterNot(_._1 == fieldName)
            if (newFields.length == fields.length)
              Left(MigrationError.ValidationError(s"Field '$fieldName' not found"))
            else Right(DynamicValue.Record(newFields))
          case other => Left(MigrationError.TypeError("Record", other.getClass.getSimpleName))
        }

      case MigrationAction.Rename(_, from, to) =>
        value match {
          case DynamicValue.Record(fields) =>
            val idx = fields.indexWhere(_._1 == from)
            if (idx < 0) Left(MigrationError.ValidationError(s"Field '$from' not found"))
            else if (fields.exists(_._1 == to)) Left(MigrationError.ValidationError(s"Field '$to' already exists"))
            else {
              val (_, fieldValue) = fields(idx)
              Right(DynamicValue.Record(fields.updated(idx, (to, fieldValue))))
            }
          case other => Left(MigrationError.TypeError("Record", other.getClass.getSimpleName))
        }

      case MigrationAction.SetValue(_, fieldName, newValue) =>
        value match {
          case DynamicValue.Record(fields) =>
            val idx = fields.indexWhere(_._1 == fieldName)
            if (idx < 0) Left(MigrationError.ValidationError(s"Field '$fieldName' not found"))
            else Right(DynamicValue.Record(fields.updated(idx, (fieldName, newValue))))
          case other => Left(MigrationError.TypeError("Record", other.getClass.getSimpleName))
        }

      case MigrationAction.Optionalize(_, fieldName) =>
        value match {
          case DynamicValue.Record(fields) =>
            val idx = fields.indexWhere(_._1 == fieldName)
            if (idx < 0) Left(MigrationError.ValidationError(s"Field '$fieldName' not found"))
            else {
              val (_, fieldValue) = fields(idx)
              val optionValue     = DynamicValue.Variant("Some", fieldValue)
              Right(DynamicValue.Record(fields.updated(idx, (fieldName, optionValue))))
            }
          case other => Left(MigrationError.TypeError("Record", other.getClass.getSimpleName))
        }

      case MigrationAction.TransformValue(_, fieldName, transform) =>
        value match {
          case DynamicValue.Record(fields) =>
            val idx = fields.indexWhere(_._1 == fieldName)
            if (idx < 0) Left(MigrationError.ValidationError(s"Field '$fieldName' not found"))
            else {
              val (_, fieldValue) = fields(idx)
              transform(fieldValue).map(t => DynamicValue.Record(fields.updated(idx, (fieldName, t))))
            }
          case other => Left(MigrationError.TypeError("Record", other.getClass.getSimpleName))
        }

      case MigrationAction.Mandate(_, fieldName, default) =>
        value match {
          case DynamicValue.Record(fields) =>
            val idx = fields.indexWhere(_._1 == fieldName)
            if (idx < 0) Left(MigrationError.ValidationError(s"Field '$fieldName' not found"))
            else {
              val (_, fieldValue) = fields(idx)
              val unwrapped       = fieldValue match {
                case DynamicValue.Variant("Some", inner) => inner
                case DynamicValue.Variant("None", _)     => default
                case other                               => other
              }
              Right(DynamicValue.Record(fields.updated(idx, (fieldName, unwrapped))))
            }
          case other => Left(MigrationError.TypeError("Record", other.getClass.getSimpleName))
        }

      case MigrationAction.Join(_, sourceFields, targetField, joinExpr) =>
        value match {
          case DynamicValue.Record(fields) =>
            joinExpr(value).flatMap { combined =>
              val remaining = fields.filterNot { case (name, _) => sourceFields.contains(name) }
              Right(DynamicValue.Record(remaining :+ (targetField, combined)))
            }
          case other => Left(MigrationError.TypeError("Record", other.getClass.getSimpleName))
        }

      case MigrationAction.Split(_, sourceField, targetFields, splitExpr) =>
        value match {
          case DynamicValue.Record(fields) =>
            val idx = fields.indexWhere(_._1 == sourceField)
            if (idx < 0) Left(MigrationError.ValidationError(s"Field '$sourceField' not found"))
            else {
              val (_, fieldValue) = fields(idx)
              splitExpr(fieldValue).flatMap {
                case DynamicValue.Sequence(splitValues) if splitValues.length >= targetFields.length =>
                  val remaining = fields.filterNot(_._1 == sourceField)
                  val newFields = targetFields.zip(splitValues).toVector
                  Right(DynamicValue.Record(remaining ++ newFields))
                case _ => Left(MigrationError.TransformError("Split expression produced unexpected result"))
              }
            }
          case other => Left(MigrationError.TypeError("Record", other.getClass.getSimpleName))
        }

      case MigrationAction.ChangeType(_, fieldName, converter) =>
        value match {
          case DynamicValue.Record(fields) =>
            val idx = fields.indexWhere(_._1 == fieldName)
            if (idx < 0) Left(MigrationError.ValidationError(s"Field '$fieldName' not found"))
            else {
              val (_, fieldValue) = fields(idx)
              converter(fieldValue).map(c => DynamicValue.Record(fields.updated(idx, (fieldName, c))))
            }
          case other => Left(MigrationError.TypeError("Record", other.getClass.getSimpleName))
        }

      case MigrationAction.RenameCase(_, from, to) =>
        value match {
          case DynamicValue.Variant(caseName, inner) if caseName == from =>
            Right(DynamicValue.Variant(to, inner))
          case v @ DynamicValue.Variant(_, _) => Right(v)
          case other                          => Left(MigrationError.TypeError("Variant", other.getClass.getSimpleName))
        }

      case MigrationAction.TransformCase(_, caseName, transform) =>
        value match {
          case DynamicValue.Variant(actualCase, inner) if actualCase == caseName =>
            transform(inner).map(DynamicValue.Variant(caseName, _))
          case v @ DynamicValue.Variant(_, _) => Right(v)
          case other                          => Left(MigrationError.TypeError("Variant", other.getClass.getSimpleName))
        }

      case MigrationAction.TransformElements(_, transform) =>
        value match {
          case DynamicValue.Sequence(elements) =>
            elements
              .foldLeft[Either[MigrationError, Vector[DynamicValue]]](Right(Vector.empty)) {
                case (Right(acc), elem) => transform(elem).map(acc :+ _)
                case (left, _)          => left
              }
              .map(DynamicValue.Sequence(_))
          case other => Left(MigrationError.TypeError("Sequence", other.getClass.getSimpleName))
        }

      case MigrationAction.TransformKeys(_, transform) =>
        value match {
          case DynamicValue.Map(entries) =>
            entries
              .foldLeft[Either[MigrationError, Vector[(DynamicValue, DynamicValue)]]](Right(Vector.empty)) {
                case (Right(acc), (k, v)) => transform(k).map(newK => acc :+ (newK, v))
                case (left, _)            => left
              }
              .map(DynamicValue.Map(_))
          case other => Left(MigrationError.TypeError("Map", other.getClass.getSimpleName))
        }

      case MigrationAction.TransformValues(_, transform) =>
        value match {
          case DynamicValue.Map(entries) =>
            entries
              .foldLeft[Either[MigrationError, Vector[(DynamicValue, DynamicValue)]]](Right(Vector.empty)) {
                case (Right(acc), (k, v)) => transform(v).map(newV => acc :+ (k, newV))
                case (left, _)            => left
              }
              .map(DynamicValue.Map(_))
          case other => Left(MigrationError.TypeError("Map", other.getClass.getSimpleName))
        }
    }
  }

  private def navigateAndApply(
    value: DynamicValue,
    path: IndexedSeq[DynamicOptic.Node],
    pathIdx: Int,
    action: MigrationAction
  ): Either[MigrationError, DynamicValue] = {
    val node   = path(pathIdx)
    val isLast = pathIdx == path.length - 1

    node match {
      case DynamicOptic.Node.Field(name) =>
        value match {
          case DynamicValue.Record(fields) =>
            val fieldIdx = fields.indexWhere(_._1 == name)
            if (fieldIdx < 0) Left(MigrationError.PathError(action.at.toString, s"Field '$name' not found"))
            else {
              val (fieldName, fieldValue) = fields(fieldIdx)
              val result                  =
                if (isLast) applyAtRoot(fieldValue, action)
                else navigateAndApply(fieldValue, path, pathIdx + 1, action)
              result.map(newValue => DynamicValue.Record(fields.updated(fieldIdx, (fieldName, newValue))))
            }
          case other => Left(MigrationError.TypeError("Record", other.getClass.getSimpleName))
        }

      case DynamicOptic.Node.AtIndex(index) =>
        value match {
          case DynamicValue.Sequence(elements) =>
            if (index < 0 || index >= elements.length)
              Left(MigrationError.PathError(action.at.toString, s"Index $index out of bounds"))
            else {
              val elem   = elements(index)
              val result =
                if (isLast) applyAtRoot(elem, action)
                else navigateAndApply(elem, path, pathIdx + 1, action)
              result.map(newElem => DynamicValue.Sequence(elements.updated(index, newElem)))
            }
          case other => Left(MigrationError.TypeError("Sequence", other.getClass.getSimpleName))
        }

      case DynamicOptic.Node.Case(caseName) =>
        value match {
          case DynamicValue.Variant(actualCase, innerValue) =>
            if (actualCase != caseName)
              Left(MigrationError.PathError(action.at.toString, s"Expected case '$caseName' but got '$actualCase'"))
            else {
              val result =
                if (isLast) applyAtRoot(innerValue, action)
                else navigateAndApply(innerValue, path, pathIdx + 1, action)
              result.map(DynamicValue.Variant(caseName, _))
            }
          case other => Left(MigrationError.TypeError("Variant", other.getClass.getSimpleName))
        }

      case DynamicOptic.Node.Elements =>
        value match {
          case DynamicValue.Sequence(elements) =>
            elements
              .foldLeft[Either[MigrationError, Vector[DynamicValue]]](Right(Vector.empty)) {
                case (Right(acc), elem) =>
                  val result =
                    if (isLast) applyAtRoot(elem, action)
                    else navigateAndApply(elem, path, pathIdx + 1, action)
                  result.map(acc :+ _)
                case (left, _) => left
              }
              .map(DynamicValue.Sequence(_))
          case other => Left(MigrationError.TypeError("Sequence", other.getClass.getSimpleName))
        }

      case other =>
        Left(MigrationError.PathError(action.at.toString, s"Unsupported path node: $other"))
    }
  }
}

object DynamicMigration {
  val empty: DynamicMigration = DynamicMigration(Vector.empty)

  def addField(fieldName: String, default: DynamicValue): DynamicMigration =
    DynamicMigration(Vector(MigrationAction.AddField(DynamicOptic.root, fieldName, default)))

  def dropField(fieldName: String, defaultForReverse: DynamicValue): DynamicMigration =
    DynamicMigration(Vector(MigrationAction.DropField(DynamicOptic.root, fieldName, defaultForReverse)))

  def renameField(from: String, to: String): DynamicMigration =
    DynamicMigration(Vector(MigrationAction.Rename(DynamicOptic.root, from, to)))
}

/** Typed migration - wraps DynamicMigration with schemas */
case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {

  /** Apply migration to transform A to B */
  def apply(value: A): Either[MigrationError, B] = {
    val dynamic = sourceSchema.toDynamicValue(value)
    dynamicMigration(dynamic).flatMap { dv =>
      targetSchema.fromDynamicValue(dv) match {
        case Right(b) => Right(b)
        case Left(e)  => Left(MigrationError.TransformError(e.toString))
      }
    }
  }

  /** Compose migrations sequentially */
  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(dynamicMigration ++ that.dynamicMigration, sourceSchema, that.targetSchema)

  /** Alias for ++ */
  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  /** Reverse migration (structural inverse) */
  def reverse: Migration[B, A] =
    Migration(dynamicMigration.reverse, targetSchema, sourceSchema)
}

object Migration {

  /** Identity migration - no changes */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration(DynamicMigration.empty, schema, schema)

  /** Create a builder for migrating from A to B */
  def builder[A, B](implicit sourceSchema: Schema[A], targetSchema: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, Vector.empty)
}

// =============================================================================
// MIGRATION BUILDER - Fluent API for building migrations
// =============================================================================
// NOTE: Scala 3 macros for selector expressions are in scala-3/migration/MigrationMacros.scala

/**
 * MigrationBuilder provides a fluent API for constructing migrations.
 *
 * Selector-accepting methods use macros for compile-time path extraction.
 * Example: `.addField(_.address.street, default)`
 */
class MigrationBuilder[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  actions: Vector[MigrationAction]
) {

  // =========================================================================
  // RECORD OPERATIONS
  // =========================================================================

  /** Add a new field with a default value */
  def addField(path: DynamicOptic, fieldName: String, default: DynamicValue): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.AddField(path, fieldName, default))

  /** Add a new field at root with default */
  def addField(fieldName: String, default: DynamicValue): MigrationBuilder[A, B] =
    addField(DynamicOptic.root, fieldName, default)

  /** Drop a field */
  def dropField(path: DynamicOptic, fieldName: String, defaultForReverse: DynamicValue): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.DropField(path, fieldName, defaultForReverse)
    )

  /** Drop a field at root */
  def dropField(fieldName: String, defaultForReverse: DynamicValue): MigrationBuilder[A, B] =
    dropField(DynamicOptic.root, fieldName, defaultForReverse)

  /** Rename a field */
  def renameField(path: DynamicOptic, from: String, to: String): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.Rename(path, from, to))

  /** Rename a field at root */
  def renameField(from: String, to: String): MigrationBuilder[A, B] =
    renameField(DynamicOptic.root, from, to)

  /** Transform a field's value using a pure expression */
  def transformField(path: DynamicOptic, fieldName: String, transform: DynamicExpr): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformValue(path, fieldName, transform)
    )

  /** Transform a field at root */
  def transformField(fieldName: String, transform: DynamicExpr): MigrationBuilder[A, B] =
    transformField(DynamicOptic.root, fieldName, transform)

  /** Convert Option[T] field to T with default for None */
  def mandateField(path: DynamicOptic, fieldName: String, default: DynamicValue): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.Mandate(path, fieldName, default))

  /** Mandate a field at root */
  def mandateField(fieldName: String, default: DynamicValue): MigrationBuilder[A, B] =
    mandateField(DynamicOptic.root, fieldName, default)

  /** Convert T field to Option[T] (wrap in Some) */
  def optionalizeField(path: DynamicOptic, fieldName: String): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.Optionalize(path, fieldName))

  /** Optionalize a field at root */
  def optionalizeField(fieldName: String): MigrationBuilder[A, B] =
    optionalizeField(DynamicOptic.root, fieldName)

  /** Change field type using a converter */
  def changeFieldType(path: DynamicOptic, fieldName: String, converter: DynamicExpr): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.ChangeType(path, fieldName, converter))

  /** Change field type at root */
  def changeFieldType(fieldName: String, converter: DynamicExpr): MigrationBuilder[A, B] =
    changeFieldType(DynamicOptic.root, fieldName, converter)

  /** Join multiple fields into one */
  def joinFields(
    path: DynamicOptic,
    sourceFields: Vector[String],
    targetField: String,
    joinExpr: DynamicExpr
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.Join(path, sourceFields, targetField, joinExpr)
    )

  /** Split one field into multiple */
  def splitField(
    path: DynamicOptic,
    sourceField: String,
    targetFields: Vector[String],
    splitExpr: DynamicExpr
  ): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.Split(path, sourceField, targetFields, splitExpr)
    )

  // =========================================================================
  // ENUM OPERATIONS
  // =========================================================================

  /** Rename an enum case */
  def renameCase(path: DynamicOptic, from: String, to: String): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.RenameCase(path, from, to))

  /** Rename an enum case at root */
  def renameCase(from: String, to: String): MigrationBuilder[A, B] =
    renameCase(DynamicOptic.root, from, to)

  /** Transform an enum case's payload */
  def transformCase(path: DynamicOptic, caseName: String, transform: DynamicMigration): MigrationBuilder[A, B] =
    new MigrationBuilder(
      sourceSchema,
      targetSchema,
      actions :+ MigrationAction.TransformCase(path, caseName, transform)
    )

  /** Transform an enum case at root */
  def transformCase(caseName: String, transform: DynamicMigration): MigrationBuilder[A, B] =
    transformCase(DynamicOptic.root, caseName, transform)

  // =========================================================================
  // COLLECTION OPERATIONS
  // =========================================================================

  /** Transform all elements in a collection */
  def transformElements(path: DynamicOptic, transform: DynamicExpr): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.TransformElements(path, transform))

  /** Transform elements at root */
  def transformElements(transform: DynamicExpr): MigrationBuilder[A, B] =
    transformElements(DynamicOptic.root, transform)

  /** Transform all keys in a map */
  def transformKeys(path: DynamicOptic, transform: DynamicExpr): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.TransformKeys(path, transform))

  /** Transform keys at root */
  def transformKeys(transform: DynamicExpr): MigrationBuilder[A, B] =
    transformKeys(DynamicOptic.root, transform)

  /** Transform all values in a map */
  def transformValues(path: DynamicOptic, transform: DynamicExpr): MigrationBuilder[A, B] =
    new MigrationBuilder(sourceSchema, targetSchema, actions :+ MigrationAction.TransformValues(path, transform))

  /** Transform values at root */
  def transformValues(transform: DynamicExpr): MigrationBuilder[A, B] =
    transformValues(DynamicOptic.root, transform)

  // NOTE: Selector-based methods (inline def with _.field syntax) are available
  // in Scala 3 only via scala-3/migration/MigrationMacros.scala

  // =========================================================================
  // BUILD
  // =========================================================================

  /** Build the migration */
  def build: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)

  /** Build the migration without full validation */
  def buildPartial: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)

  /** Get the accumulated actions (for inspection/testing) */
  def getActions: Vector[MigrationAction] = actions
}
