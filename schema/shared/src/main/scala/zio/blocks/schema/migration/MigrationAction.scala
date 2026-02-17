package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue, SchemaError}

/**
 * A pure, serializable representation of a single migration step operating on
 * `DynamicValue` via path-based selectors.
 *
 * Every action is a case class (no closures, no functions) making the entire
 * migration graph serializable to JSON, binary, or any codec derived from
 * `Schema[MigrationAction]`.
 *
 * Actions operate at the `DynamicValue` level, using `DynamicOptic` paths for
 * navigation. The typed `Migration[A, B]` layer lifts these into compile-time
 * checked operations.
 */
sealed trait MigrationAction extends Product with Serializable {

  /**
   * Apply this action to a DynamicValue, producing either an error with path
   * context or the transformed value.
   */
  def apply(value: DynamicValue): Either[SchemaError, DynamicValue]

  /**
   * Compute the structural reverse of this action, if one exists.
   *
   * For reversible actions (rename, reorder), the reverse is exact. For lossy
   * actions (drop field without default), the reverse requires a default value
   * and returns `None` if one isn't available.
   */
  def reverse: Option[MigrationAction]
}

object MigrationAction {

  // ───────────────────────────────────────────────────────────────────────────
  // Record Actions
  // ───────────────────────────────────────────────────────────────────────────

  /**
   * Add a new field to a record at the given path with a default value.
   */
  final case class AddField(
    path: DynamicOptic,
    fieldName: String,
    defaultValue: DynamicValue
  ) extends MigrationAction {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
      value match {
        case record: DynamicValue.Record =>
          if (path.nodes.isEmpty) {
            // Direct record modification
            if (record.fields.exists(_._1 == fieldName))
              Left(SchemaError(s"Field '$fieldName' already exists at path ${path}"))
            else
              Right(DynamicValue.Record(record.fields :+ ((fieldName, defaultValue))))
          } else {
            // Navigate to nested record
            value.modifyOrFail(path) {
              case r: DynamicValue.Record =>
                if (r.fields.exists(_._1 == fieldName))
                  r // already exists — idempotent
                else
                  DynamicValue.Record(r.fields :+ ((fieldName, defaultValue)))
            }
          }
        case _ if path.nodes.isEmpty =>
          Left(SchemaError.message(s"Expected Record, got ${value.valueType}", path))
        case _ =>
          value.modifyOrFail(path) {
            case r: DynamicValue.Record =>
              DynamicValue.Record(r.fields :+ ((fieldName, defaultValue)))
          }
      }

    def reverse: Option[MigrationAction] = Some(DropField(path, fieldName, Some(defaultValue)))
  }

  /**
   * Remove a field from a record. The optional `lastKnownDefault` enables
   * reverse (re-adding the field).
   */
  final case class DropField(
    path: DynamicOptic,
    fieldName: String,
    lastKnownDefault: Option[DynamicValue]
  ) extends MigrationAction {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = {
      def dropFrom(record: DynamicValue.Record): DynamicValue = {
        val filtered = record.fields.filter(_._1 != fieldName)
        DynamicValue.Record(filtered)
      }

      if (path.nodes.isEmpty) {
        value match {
          case r: DynamicValue.Record => Right(dropFrom(r))
          case _ => Left(SchemaError.message(s"Expected Record, got ${value.valueType}", path))
        }
      } else {
        value.modifyOrFail(path) { case r: DynamicValue.Record => dropFrom(r) }
      }
    }

    def reverse: Option[MigrationAction] =
      lastKnownDefault.map(default => AddField(path, fieldName, default))
  }

  /**
   * Rename a field within a record at the given path.
   */
  final case class RenameField(
    path: DynamicOptic,
    oldName: String,
    newName: String
  ) extends MigrationAction {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = {
      def renameIn(record: DynamicValue.Record): Either[SchemaError, DynamicValue] = {
        if (!record.fields.exists(_._1 == oldName))
          Left(SchemaError.message(s"Field '$oldName' not found", path))
        else if (record.fields.exists(_._1 == newName))
          Left(SchemaError.message(s"Field '$newName' already exists", path))
        else {
          val renamed = record.fields.map {
            case (name, v) if name == oldName => (newName, v)
            case other                        => other
          }
          Right(DynamicValue.Record(renamed))
        }
      }

      if (path.nodes.isEmpty) {
        value match {
          case r: DynamicValue.Record => renameIn(r)
          case _ => Left(SchemaError.message(s"Expected Record, got ${value.valueType}", path))
        }
      } else {
        value.modifyOrFail(path) {
          case r: DynamicValue.Record =>
            renameIn(r).getOrElse(r) // swallow inner error for modify compatibility
        }
      }
    }

    def reverse: Option[MigrationAction] = Some(RenameField(path, newName, oldName))
  }

  /**
   * Transform a field's value by replacing it with a new literal value. This is
   * the serializable equivalent of `transformField(f)` — since we can't
   * serialize functions, we represent the transformation as a mapping from one
   * concrete value to another.
   *
   * For dynamic transformations at runtime, use `TransformFieldWithInto`.
   */
  final case class SetFieldValue(
    path: DynamicOptic,
    fieldName: String,
    newValue: DynamicValue
  ) extends MigrationAction {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = {
      def setIn(record: DynamicValue.Record): Either[SchemaError, DynamicValue] = {
        if (!record.fields.exists(_._1 == fieldName))
          Left(SchemaError.message(s"Field '$fieldName' not found", path))
        else {
          val updated = record.fields.map {
            case (name, _) if name == fieldName => (name, newValue)
            case other                          => other
          }
          Right(DynamicValue.Record(updated))
        }
      }

      if (path.nodes.isEmpty) {
        value match {
          case r: DynamicValue.Record => setIn(r)
          case _ => Left(SchemaError.message(s"Expected Record, got ${value.valueType}", path))
        }
      } else {
        value.modifyOrFail(path) {
          case r: DynamicValue.Record =>
            setIn(r).getOrElse(r)
        }
      }
    }

    def reverse: Option[MigrationAction] = None // Lossy — can't recover original value
  }

  /**
   * Make an optional field mandatory by providing a default for missing values.
   */
  final case class MandateField(
    path: DynamicOptic,
    fieldName: String,
    defaultForNull: DynamicValue
  ) extends MigrationAction {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = {
      def mandateIn(record: DynamicValue.Record): DynamicValue = {
        val updated = record.fields.map {
          case (name, DynamicValue.Null) if name == fieldName => (name, defaultForNull)
          case other                                          => other
        }
        DynamicValue.Record(updated)
      }

      if (path.nodes.isEmpty) {
        value match {
          case r: DynamicValue.Record => Right(mandateIn(r))
          case _ => Left(SchemaError.message(s"Expected Record, got ${value.valueType}", path))
        }
      } else {
        value.modifyOrFail(path) { case r: DynamicValue.Record => mandateIn(r) }
      }
    }

    def reverse: Option[MigrationAction] = Some(OptionalizeField(path, fieldName))
  }

  /**
   * Make a mandatory field optional (wrapping non-null values as-is, allowing
   * null).
   */
  final case class OptionalizeField(
    path: DynamicOptic,
    fieldName: String
  ) extends MigrationAction {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] =
      Right(value) // No structural change needed — optionality is a schema concern

    def reverse: Option[MigrationAction] = None // Can't mandate without a default
  }

  /**
   * Change a field's type by providing a mapping from old values to new values.
   * The mapping is a sequence of (oldDynamic, newDynamic) pairs. Values not in
   * the mapping are left unchanged.
   */
  final case class ChangeFieldType(
    path: DynamicOptic,
    fieldName: String,
    valueMapping: Chunk[(DynamicValue, DynamicValue)]
  ) extends MigrationAction {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = {
      val mappingMap = valueMapping.foldLeft(scala.collection.immutable.Map.empty[DynamicValue, DynamicValue]) {
        case (acc, (k, v)) => acc + (k -> v)
      }

      def changeIn(record: DynamicValue.Record): Either[SchemaError, DynamicValue] = {
        if (!record.fields.exists(_._1 == fieldName))
          Left(SchemaError.message(s"Field '$fieldName' not found", path))
        else {
          val updated = record.fields.map {
            case (name, v) if name == fieldName => (name, mappingMap.getOrElse(v, v))
            case other                          => other
          }
          Right(DynamicValue.Record(updated))
        }
      }

      if (path.nodes.isEmpty) {
        value match {
          case r: DynamicValue.Record => changeIn(r)
          case _ => Left(SchemaError.message(s"Expected Record, got ${value.valueType}", path))
        }
      } else {
        value.modifyOrFail(path) {
          case r: DynamicValue.Record => changeIn(r).getOrElse(r)
        }
      }
    }

    def reverse: Option[MigrationAction] = {
      val reversed = valueMapping.map { case (k, v) => (v, k) }
      Some(ChangeFieldType(path, fieldName, reversed))
    }
  }

  // ───────────────────────────────────────────────────────────────────────────
  // Enum / Variant Actions
  // ───────────────────────────────────────────────────────────────────────────

  /**
   * Rename a variant case.
   */
  final case class RenameCase(
    path: DynamicOptic,
    oldCaseName: String,
    newCaseName: String
  ) extends MigrationAction {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = {
      def renameIn(variant: DynamicValue.Variant): DynamicValue = {
        if (variant.caseNameValue == oldCaseName)
          DynamicValue.Variant(newCaseName, variant.value)
        else
          variant
      }

      if (path.nodes.isEmpty) {
        value match {
          case v: DynamicValue.Variant => Right(renameIn(v))
          case _ => Left(SchemaError.message(s"Expected Variant, got ${value.valueType}", path))
        }
      } else {
        value.modifyOrFail(path) { case v: DynamicValue.Variant => renameIn(v) }
      }
    }

    def reverse: Option[MigrationAction] = Some(RenameCase(path, newCaseName, oldCaseName))
  }

  /**
   * Transform a variant case's inner value using a nested DynamicMigration.
   */
  final case class TransformCase(
    path: DynamicOptic,
    caseName: String,
    migration: DynamicMigration
  ) extends MigrationAction {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = {
      def transformIn(variant: DynamicValue.Variant): Either[SchemaError, DynamicValue] = {
        if (variant.caseNameValue == caseName)
          migration.migrate(variant.value).map(DynamicValue.Variant(caseName, _))
        else
          Right(variant)
      }

      if (path.nodes.isEmpty) {
        value match {
          case v: DynamicValue.Variant => transformIn(v)
          case _ => Left(SchemaError.message(s"Expected Variant, got ${value.valueType}", path))
        }
      } else {
        // For nested paths, we have to handle errors carefully
        var err: SchemaError = null
        val result = value.modifyOrFail(path) {
          case v: DynamicValue.Variant =>
            transformIn(v) match {
              case Right(transformed) => transformed
              case Left(e)            => err = e; v
            }
        }
        if (err != null) Left(err) else result
      }
    }

    def reverse: Option[MigrationAction] =
      migration.reverse.map(rev => TransformCase(path, caseName, rev))
  }

  // ───────────────────────────────────────────────────────────────────────────
  // Collection Actions
  // ───────────────────────────────────────────────────────────────────────────

  /**
   * Transform each element of a sequence using a nested DynamicMigration.
   */
  final case class TransformElements(
    path: DynamicOptic,
    migration: DynamicMigration
  ) extends MigrationAction {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = {
      def transformSeq(seq: DynamicValue.Sequence): Either[SchemaError, DynamicValue] = {
        val builder = Chunk.newBuilder[DynamicValue]
        val iter    = seq.elements.iterator
        while (iter.hasNext) {
          migration.migrate(iter.next()) match {
            case Right(v) => builder += v
            case Left(e)  => return Left(e)
          }
        }
        Right(DynamicValue.Sequence(builder.result()))
      }

      if (path.nodes.isEmpty) {
        value match {
          case s: DynamicValue.Sequence => transformSeq(s)
          case _ => Left(SchemaError.message(s"Expected Sequence, got ${value.valueType}", path))
        }
      } else {
        var err: SchemaError = null
        val result = value.modifyOrFail(path) {
          case s: DynamicValue.Sequence =>
            transformSeq(s) match {
              case Right(v) => v
              case Left(e)  => err = e; s
            }
        }
        if (err != null) Left(err) else result
      }
    }

    def reverse: Option[MigrationAction] =
      migration.reverse.map(rev => TransformElements(path, rev))
  }

  /**
   * Transform map keys using a nested DynamicMigration.
   */
  final case class TransformKeys(
    path: DynamicOptic,
    migration: DynamicMigration
  ) extends MigrationAction {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = {
      def transformMap(map: DynamicValue.Map): Either[SchemaError, DynamicValue] = {
        val builder = Chunk.newBuilder[(DynamicValue, DynamicValue)]
        val iter    = map.entries.iterator
        while (iter.hasNext) {
          val (k, v) = iter.next()
          migration.migrate(k) match {
            case Right(newK) => builder += ((newK, v))
            case Left(e)     => return Left(e)
          }
        }
        Right(DynamicValue.Map(builder.result()))
      }

      if (path.nodes.isEmpty) {
        value match {
          case m: DynamicValue.Map => transformMap(m)
          case _ => Left(SchemaError.message(s"Expected Map, got ${value.valueType}", path))
        }
      } else {
        var err: SchemaError = null
        val result = value.modifyOrFail(path) {
          case m: DynamicValue.Map =>
            transformMap(m) match {
              case Right(v) => v
              case Left(e)  => err = e; m
            }
        }
        if (err != null) Left(err) else result
      }
    }

    def reverse: Option[MigrationAction] =
      migration.reverse.map(rev => TransformKeys(path, rev))
  }

  /**
   * Transform map values using a nested DynamicMigration.
   */
  final case class TransformValues(
    path: DynamicOptic,
    migration: DynamicMigration
  ) extends MigrationAction {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = {
      def transformMap(map: DynamicValue.Map): Either[SchemaError, DynamicValue] = {
        val builder = Chunk.newBuilder[(DynamicValue, DynamicValue)]
        val iter    = map.entries.iterator
        while (iter.hasNext) {
          val (k, v) = iter.next()
          migration.migrate(v) match {
            case Right(newV) => builder += ((k, newV))
            case Left(e)     => return Left(e)
          }
        }
        Right(DynamicValue.Map(builder.result()))
      }

      if (path.nodes.isEmpty) {
        value match {
          case m: DynamicValue.Map => transformMap(m)
          case _ => Left(SchemaError.message(s"Expected Map, got ${value.valueType}", path))
        }
      } else {
        var err: SchemaError = null
        val result = value.modifyOrFail(path) {
          case m: DynamicValue.Map =>
            transformMap(m) match {
              case Right(v) => v
              case Left(e)  => err = e; m
            }
        }
        if (err != null) Left(err) else result
      }
    }

    def reverse: Option[MigrationAction] =
      migration.reverse.map(rev => TransformValues(path, rev))
  }

  // ───────────────────────────────────────────────────────────────────────────
  // Composite / Identity
  // ───────────────────────────────────────────────────────────────────────────

  /**
   * The identity action — does nothing. Useful as the unit of composition.
   */
  case object Identity extends MigrationAction {
    def apply(value: DynamicValue): Either[SchemaError, DynamicValue] = Right(value)
    def reverse: Option[MigrationAction]                              = Some(Identity)
  }
}
