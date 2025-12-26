package zio.blocks.schema

import scala.util.control.NoStackTrace

/**
 * MigrationError represents errors that can occur during schema migration.
 * All errors carry path information via DynamicOptic to identify the exact
 * location where the error occurred.
 */
final case class MigrationError(errors: ::[MigrationError.Single]) extends Exception with NoStackTrace {
  def ++(other: MigrationError): MigrationError =
    MigrationError(new ::(errors.head, errors.tail ++ other.errors))

  override def getMessage: String = message

  def message: String = errors
    .foldLeft(new java.lang.StringBuilder) {
      var lineFeed = false
      (sb, e) =>
        if (lineFeed) sb.append('\n')
        else lineFeed = true
        sb.append(e.message)
    }
    .toString
}

object MigrationError {
  def pathNotFound(path: DynamicOptic): MigrationError =
    new MigrationError(new ::(new PathNotFound(path), Nil))

  def typeMismatch(path: DynamicOptic, expected: String, actual: String): MigrationError =
    new MigrationError(new ::(new TypeMismatch(path, expected, actual), Nil))

  def missingDefault(path: DynamicOptic, fieldName: String): MigrationError =
    new MigrationError(new ::(new MissingDefault(path, fieldName), Nil))

  def invalidTransform(path: DynamicOptic, reason: String): MigrationError =
    new MigrationError(new ::(new InvalidTransform(path, reason), Nil))

  def unknownCase(path: DynamicOptic, caseName: String): MigrationError =
    new MigrationError(new ::(new UnknownCase(path, caseName), Nil))

  def mandatoryFieldIsNone(path: DynamicOptic, fieldName: String): MigrationError =
    new MigrationError(new ::(new MandatoryFieldIsNone(path, fieldName), Nil))

  sealed trait Single {
    def message: String
    def path: DynamicOptic
  }

  case class PathNotFound(path: DynamicOptic) extends Single {
    override def message: String = s"Path not found: $path"
  }

  case class TypeMismatch(path: DynamicOptic, expected: String, actual: String) extends Single {
    override def message: String = s"Type mismatch at $path: expected $expected, got $actual"
  }

  case class MissingDefault(path: DynamicOptic, fieldName: String) extends Single {
    override def message: String = s"Missing default value for field '$fieldName' at $path"
  }

  case class InvalidTransform(path: DynamicOptic, reason: String) extends Single {
    override def message: String = s"Invalid transform at $path: $reason"
  }

  case class UnknownCase(path: DynamicOptic, caseName: String) extends Single {
    override def message: String = s"Unknown case '$caseName' at $path"
  }

  case class MandatoryFieldIsNone(path: DynamicOptic, fieldName: String) extends Single {
    override def message: String = s"Mandatory field '$fieldName' is None at $path"
  }
}

/**
 * MigrationAction represents a single, serializable migration operation.
 * All actions are pure data - no closures or functions - making them
 * suitable for serialization and introspection.
 */
sealed trait MigrationAction {
  def path: DynamicOptic
}

object MigrationAction {
  // ============================================================
  // Record field operations
  // ============================================================

  /**
   * Add a new field to a record with a default value.
   * The default value is represented as a DynamicValue for serializability.
   */
  final case class AddField(
    path: DynamicOptic,
    fieldName: String,
    defaultValue: DynamicValue
  ) extends MigrationAction

  /**
   * Drop a field from a record.
   * Optionally stores a reverse default for bidirectional migrations.
   */
  final case class DropField(
    path: DynamicOptic,
    fieldName: String,
    reverseDefault: Option[DynamicValue]
  ) extends MigrationAction

  /**
   * Rename a field in a record.
   */
  final case class RenameField(
    path: DynamicOptic,
    oldName: String,
    newName: String
  ) extends MigrationAction

  // ============================================================
  // Value transformation operations
  // ============================================================

  /**
   * Transform a value using a named, registered transform.
   * The transformId references a pure function that must be registered
   * in a TransformRegistry for execution.
   */
  final case class TransformValue(
    path: DynamicOptic,
    transformId: String,
    reverseTransformId: Option[String]
  ) extends MigrationAction

  /**
   * Change the type of a value using a named coercion.
   * The coercionId references a type coercion function in a registry.
   */
  final case class ChangeType(
    path: DynamicOptic,
    fromTypeName: String,
    toTypeName: String,
    coercionId: String,
    reverseCoercionId: Option[String]
  ) extends MigrationAction

  /**
   * Make a required field optional by wrapping it in Some.
   */
  final case class Optionalize(
    path: DynamicOptic,
    fieldName: String
  ) extends MigrationAction

  /**
   * Make an optional field required, using a default for None values.
   */
  final case class Mandate(
    path: DynamicOptic,
    fieldName: String,
    defaultForNone: DynamicValue
  ) extends MigrationAction

  // ============================================================
  // Variant/enum operations
  // ============================================================

  /**
   * Rename a case in a variant/enum.
   */
  final case class RenameCase(
    path: DynamicOptic,
    oldCaseName: String,
    newCaseName: String
  ) extends MigrationAction

  /**
   * Transform a case's payload using a named transform.
   */
  final case class TransformCase(
    path: DynamicOptic,
    caseName: String,
    transformId: String,
    reverseTransformId: Option[String]
  ) extends MigrationAction

  /**
   * Add a new case to a variant/enum.
   */
  final case class AddCase(
    path: DynamicOptic,
    caseName: String
  ) extends MigrationAction

  /**
   * Drop a case from a variant/enum.
   * The migrateTo specifies which case existing values should migrate to.
   */
  final case class DropCase(
    path: DynamicOptic,
    caseName: String,
    migrateTo: String,
    payloadTransformId: Option[String]
  ) extends MigrationAction

  // ============================================================
  // Collection operations
  // ============================================================

  /**
   * Transform all elements in a sequence using a named transform.
   */
  final case class TransformElements(
    path: DynamicOptic,
    transformId: String,
    reverseTransformId: Option[String]
  ) extends MigrationAction

  /**
   * Transform all keys in a map using a named transform.
   */
  final case class TransformKeys(
    path: DynamicOptic,
    transformId: String,
    reverseTransformId: Option[String]
  ) extends MigrationAction

  /**
   * Transform all values in a map using a named transform.
   */
  final case class TransformValues(
    path: DynamicOptic,
    transformId: String,
    reverseTransformId: Option[String]
  ) extends MigrationAction

  /**
   * Filter elements in a sequence based on a named predicate.
   */
  final case class FilterElements(
    path: DynamicOptic,
    predicateId: String
  ) extends MigrationAction

  /**
   * Filter entries in a map based on a named predicate.
   */
  final case class FilterEntries(
    path: DynamicOptic,
    predicateId: String
  ) extends MigrationAction
}

/**
 * TransformRegistry provides named transforms for use in migrations.
 * All transforms must be registered by name, allowing migrations to
 * remain serializable while still supporting custom logic.
 */
trait TransformRegistry {
  def getTransform(id: String): Option[DynamicValue => Either[String, DynamicValue]]
  def getPredicate(id: String): Option[DynamicValue => Boolean]
}

object TransformRegistry {
  val empty: TransformRegistry = new TransformRegistry {
    def getTransform(id: String): Option[DynamicValue => Either[String, DynamicValue]] = None
    def getPredicate(id: String): Option[DynamicValue => Boolean] = None
  }

  def apply(
    transforms: scala.collection.immutable.Map[String, DynamicValue => Either[String, DynamicValue]],
    predicates: scala.collection.immutable.Map[String, DynamicValue => Boolean]
  ): TransformRegistry = new TransformRegistry {
    def getTransform(id: String): Option[DynamicValue => Either[String, DynamicValue]] = transforms.get(id)
    def getPredicate(id: String): Option[DynamicValue => Boolean] = predicates.get(id)
  }
}

/**
 * DynamicMigration represents a sequence of migration actions that can be
 * applied to DynamicValue instances. This is the untyped, serializable
 * representation of a migration.
 */
final case class DynamicMigration(actions: Vector[MigrationAction]) {

  /**
   * Compose this migration with another, executing actions in sequence.
   */
  def andThen(that: DynamicMigration): DynamicMigration =
    DynamicMigration(this.actions ++ that.actions)

  /**
   * Alias for andThen.
   */
  def >>>(that: DynamicMigration): DynamicMigration = andThen(that)

  /**
   * Apply this migration to a DynamicValue.
   */
  def apply(value: DynamicValue)(implicit registry: TransformRegistry): Either[MigrationError, DynamicValue] =
    applyActions(value, actions, 0)

  private def applyActions(
    value: DynamicValue,
    actions: Vector[MigrationAction],
    idx: Int
  )(implicit registry: TransformRegistry): Either[MigrationError, DynamicValue] =
    if (idx >= actions.length) Right(value)
    else {
      applyAction(value, actions(idx)) match {
        case Right(newValue) => applyActions(newValue, actions, idx + 1)
        case left            => left
      }
    }

  private def applyAction(
    value: DynamicValue,
    action: MigrationAction
  )(implicit registry: TransformRegistry): Either[MigrationError, DynamicValue] =
    action match {
      case MigrationAction.AddField(path, fieldName, defaultValue) =>
        modifyAtPath(value, path) {
          case DynamicValue.Record(fields) =>
            Right(DynamicValue.Record(fields :+ (fieldName -> defaultValue)))
          case _ =>
            Left(MigrationError.typeMismatch(path, "Record", value.getClass.getSimpleName))
        }

      case MigrationAction.DropField(path, fieldName, _) =>
        modifyAtPath(value, path) {
          case DynamicValue.Record(fields) =>
            Right(DynamicValue.Record(fields.filterNot(_._1 == fieldName)))
          case _ =>
            Left(MigrationError.typeMismatch(path, "Record", value.getClass.getSimpleName))
        }

      case MigrationAction.RenameField(path, oldName, newName) =>
        modifyAtPath(value, path) {
          case DynamicValue.Record(fields) =>
            Right(DynamicValue.Record(fields.map {
              case (name, v) if name == oldName => (newName, v)
              case other                        => other
            }))
          case _ =>
            Left(MigrationError.typeMismatch(path, "Record", value.getClass.getSimpleName))
        }

      case MigrationAction.TransformValue(path, transformId, _) =>
        registry.getTransform(transformId) match {
          case Some(transform) =>
            modifyAtPath(value, path) { v =>
              transform(v).left.map(reason => MigrationError.invalidTransform(path, reason))
            }
          case None =>
            Left(MigrationError.invalidTransform(path, s"Transform '$transformId' not found"))
        }

      case MigrationAction.ChangeType(path, _, _, coercionId, _) =>
        registry.getTransform(coercionId) match {
          case Some(coerce) =>
            modifyAtPath(value, path) { v =>
              coerce(v).left.map(reason => MigrationError.invalidTransform(path, reason))
            }
          case None =>
            Left(MigrationError.invalidTransform(path, s"Coercion '$coercionId' not found"))
        }

      case MigrationAction.Optionalize(path, fieldName) =>
        modifyAtPath(value, path) {
          case DynamicValue.Record(fields) =>
            Right(DynamicValue.Record(fields.map {
              case (name, v) if name == fieldName =>
                // NOTE: This encoding (Variant("Some", Record(Vector("value" -> v)))) is
                // ZIO Schema's standard representation for Option[A]. The Option type is
                // represented as a variant with "Some" containing a record with the value,
                // or "None" containing an empty record.
                (name, DynamicValue.Variant("Some", DynamicValue.Record(Vector("value" -> v))))
              case other => other
            }))
          case _ =>
            Left(MigrationError.typeMismatch(path, "Record", value.getClass.getSimpleName))
        }

      case MigrationAction.Mandate(path, fieldName, defaultForNone) =>
        modifyAtPath(value, path) {
          case DynamicValue.Record(fields) =>
            val newFields = fields.map {
              case (name, v) if name == fieldName =>
                v match {
                  case DynamicValue.Variant("Some", DynamicValue.Record(innerFields)) =>
                    innerFields.find(_._1 == "value") match {
                      case Some((_, innerValue)) => (name, innerValue)
                      case None                  => (name, defaultForNone)
                    }
                  case DynamicValue.Variant("None", _) =>
                    (name, defaultForNone)
                  case other =>
                    (name, other)
                }
              case other => other
            }
            Right(DynamicValue.Record(newFields))
          case _ =>
            Left(MigrationError.typeMismatch(path, "Record", value.getClass.getSimpleName))
        }

      case MigrationAction.RenameCase(path, oldCaseName, newCaseName) =>
        modifyAtPath(value, path) {
          case DynamicValue.Variant(caseName, payload) if caseName == oldCaseName =>
            Right(DynamicValue.Variant(newCaseName, payload))
          case v @ DynamicValue.Variant(_, _) =>
            Right(v)
          case _ =>
            Left(MigrationError.typeMismatch(path, "Variant", value.getClass.getSimpleName))
        }

      case MigrationAction.TransformCase(path, caseName, transformId, _) =>
        registry.getTransform(transformId) match {
          case Some(transform) =>
            modifyAtPath(value, path) {
              case DynamicValue.Variant(cn, payload) if cn == caseName =>
                transform(payload).map(DynamicValue.Variant(cn, _)).left.map { reason =>
                  MigrationError.invalidTransform(path, reason)
                }
              case v @ DynamicValue.Variant(_, _) =>
                Right(v)
              case _ =>
                Left(MigrationError.typeMismatch(path, "Variant", value.getClass.getSimpleName))
            }
          case None =>
            Left(MigrationError.invalidTransform(path, s"Transform '$transformId' not found"))
        }

      case MigrationAction.AddCase(_, _) =>
        Right(value)

      case MigrationAction.DropCase(path, caseName, migrateTo, payloadTransformId) =>
        modifyAtPath(value, path) {
          case DynamicValue.Variant(cn, payload) if cn == caseName =>
            payloadTransformId match {
              case Some(tid) =>
                registry.getTransform(tid) match {
                  case Some(transform) =>
                    transform(payload).map(DynamicValue.Variant(migrateTo, _)).left.map { reason =>
                      MigrationError.invalidTransform(path, reason)
                    }
                  case None =>
                    Left(MigrationError.invalidTransform(path, s"Transform '$tid' not found"))
                }
              case None =>
                Right(DynamicValue.Variant(migrateTo, payload))
            }
          case v @ DynamicValue.Variant(_, _) =>
            Right(v)
          case _ =>
            Left(MigrationError.typeMismatch(path, "Variant", value.getClass.getSimpleName))
        }

      case MigrationAction.TransformElements(path, transformId, _) =>
        registry.getTransform(transformId) match {
          case Some(transform) =>
            modifyAtPath(value, path) {
              case DynamicValue.Sequence(elements) =>
                val results = elements.map(transform)
                val errors  = results.collect { case Left(e) => e }
                if (errors.nonEmpty) Left(MigrationError.invalidTransform(path, errors.mkString(", ")))
                else Right(DynamicValue.Sequence(results.collect { case Right(v) => v }))
              case _ =>
                Left(MigrationError.typeMismatch(path, "Sequence", value.getClass.getSimpleName))
            }
          case None =>
            Left(MigrationError.invalidTransform(path, s"Transform '$transformId' not found"))
        }

      case MigrationAction.TransformKeys(path, transformId, _) =>
        registry.getTransform(transformId) match {
          case Some(transform) =>
            modifyAtPath(value, path) {
              case DynamicValue.Map(entries) =>
                val results = entries.map { case (k, v) => transform(k).map(_ -> v) }
                val errors  = results.collect { case Left(e) => e }
                if (errors.nonEmpty) Left(MigrationError.invalidTransform(path, errors.mkString(", ")))
                else Right(DynamicValue.Map(results.collect { case Right(kv) => kv }))
              case _ =>
                Left(MigrationError.typeMismatch(path, "Map", value.getClass.getSimpleName))
            }
          case None =>
            Left(MigrationError.invalidTransform(path, s"Transform '$transformId' not found"))
        }

      case MigrationAction.TransformValues(path, transformId, _) =>
        registry.getTransform(transformId) match {
          case Some(transform) =>
            modifyAtPath(value, path) {
              case DynamicValue.Map(entries) =>
                val results = entries.map { case (k, v) => transform(v).map(k -> _) }
                val errors  = results.collect { case Left(e) => e }
                if (errors.nonEmpty) Left(MigrationError.invalidTransform(path, errors.mkString(", ")))
                else Right(DynamicValue.Map(results.collect { case Right(kv) => kv }))
              case _ =>
                Left(MigrationError.typeMismatch(path, "Map", value.getClass.getSimpleName))
            }
          case None =>
            Left(MigrationError.invalidTransform(path, s"Transform '$transformId' not found"))
        }

      case MigrationAction.FilterElements(path, predicateId) =>
        registry.getPredicate(predicateId) match {
          case Some(predicate) =>
            modifyAtPath(value, path) {
              case DynamicValue.Sequence(elements) =>
                Right(DynamicValue.Sequence(elements.filter(predicate)))
              case _ =>
                Left(MigrationError.typeMismatch(path, "Sequence", value.getClass.getSimpleName))
            }
          case None =>
            Left(MigrationError.invalidTransform(path, s"Predicate '$predicateId' not found"))
        }

      case MigrationAction.FilterEntries(path, predicateId) =>
        registry.getPredicate(predicateId) match {
          case Some(predicate) =>
            modifyAtPath(value, path) {
              case DynamicValue.Map(entries) =>
                Right(DynamicValue.Map(entries.filter { case (k, v) =>
                  predicate(DynamicValue.Record(Vector("key" -> k, "value" -> v)))
                }))
              case _ =>
                Left(MigrationError.typeMismatch(path, "Map", value.getClass.getSimpleName))
            }
          case None =>
            Left(MigrationError.invalidTransform(path, s"Predicate '$predicateId' not found"))
        }
    }

  private def modifyAtPath(
    value: DynamicValue,
    path: DynamicOptic
  )(f: DynamicValue => Either[MigrationError, DynamicValue]): Either[MigrationError, DynamicValue] =
    if (path.nodes.isEmpty) f(value)
    else modifyAtPathRec(value, path, 0)(f)

  private def modifyAtPathRec(
    value: DynamicValue,
    path: DynamicOptic,
    idx: Int
  )(f: DynamicValue => Either[MigrationError, DynamicValue]): Either[MigrationError, DynamicValue] =
    if (idx >= path.nodes.length) f(value)
    else {
      path.nodes(idx) match {
        case DynamicOptic.Node.Field(name) =>
          value match {
            case DynamicValue.Record(fields) =>
              val fieldIdx = fields.indexWhere(_._1 == name)
              if (fieldIdx < 0) Left(MigrationError.pathNotFound(path))
              else {
                modifyAtPathRec(fields(fieldIdx)._2, path, idx + 1)(f).map { newValue =>
                  DynamicValue.Record(fields.updated(fieldIdx, (name, newValue)))
                }
              }
            case _ =>
              Left(MigrationError.typeMismatch(path, "Record", value.getClass.getSimpleName))
          }

        case DynamicOptic.Node.Case(name) =>
          value match {
            case DynamicValue.Variant(caseName, payload) if caseName == name =>
              modifyAtPathRec(payload, path, idx + 1)(f).map(DynamicValue.Variant(caseName, _))
            case DynamicValue.Variant(_, _) =>
              Right(value)
            case _ =>
              Left(MigrationError.typeMismatch(path, "Variant", value.getClass.getSimpleName))
          }

        case DynamicOptic.Node.AtIndex(index) =>
          value match {
            case DynamicValue.Sequence(elements) =>
              if (index < 0 || index >= elements.length) Left(MigrationError.pathNotFound(path))
              else {
                modifyAtPathRec(elements(index), path, idx + 1)(f).map { newElement =>
                  DynamicValue.Sequence(elements.updated(index, newElement))
                }
              }
            case _ =>
              Left(MigrationError.typeMismatch(path, "Sequence", value.getClass.getSimpleName))
          }

        case DynamicOptic.Node.Elements =>
          value match {
            case DynamicValue.Sequence(elements) =>
              val results = elements.map(modifyAtPathRec(_, path, idx + 1)(f))
              val errors  = results.collect { case Left(e) => e }
              if (errors.nonEmpty) Left(errors.reduce(_ ++ _))
              else Right(DynamicValue.Sequence(results.collect { case Right(v) => v }))
            case _ =>
              Left(MigrationError.typeMismatch(path, "Sequence", value.getClass.getSimpleName))
          }

        case DynamicOptic.Node.MapKeys =>
          value match {
            case DynamicValue.Map(entries) =>
              val results = entries.map { case (k, v) =>
                modifyAtPathRec(k, path, idx + 1)(f).map(_ -> v)
              }
              val errors = results.collect { case Left(e) => e }
              if (errors.nonEmpty) Left(errors.reduce(_ ++ _))
              else Right(DynamicValue.Map(results.collect { case Right(kv) => kv }))
            case _ =>
              Left(MigrationError.typeMismatch(path, "Map", value.getClass.getSimpleName))
          }

        case DynamicOptic.Node.MapValues =>
          value match {
            case DynamicValue.Map(entries) =>
              val results = entries.map { case (k, v) =>
                modifyAtPathRec(v, path, idx + 1)(f).map(k -> _)
              }
              val errors = results.collect { case Left(e) => e }
              if (errors.nonEmpty) Left(errors.reduce(_ ++ _))
              else Right(DynamicValue.Map(results.collect { case Right(kv) => kv }))
            case _ =>
              Left(MigrationError.typeMismatch(path, "Map", value.getClass.getSimpleName))
          }

        case DynamicOptic.Node.AtMapKey(key) =>
          value match {
            case DynamicValue.Map(entries) =>
              // NOTE: The asInstanceOf is safe here because DynamicOptic.Node.AtMapKey[K]
              // in the context of DynamicValue operations is always created with K = DynamicValue.
              // The migration system only operates on DynamicValue, not typed values.
              val keyDV    = key.asInstanceOf[DynamicValue]
              val entryIdx = entries.indexWhere(_._1 == keyDV)
              if (entryIdx < 0) Left(MigrationError.pathNotFound(path))
              else {
                modifyAtPathRec(entries(entryIdx)._2, path, idx + 1)(f).map { newValue =>
                  DynamicValue.Map(entries.updated(entryIdx, (keyDV, newValue)))
                }
              }
            case _ =>
              Left(MigrationError.typeMismatch(path, "Map", value.getClass.getSimpleName))
          }

        case _: DynamicOptic.Node.AtIndices =>
          Left(MigrationError.invalidTransform(path, "AtIndices not supported in migrations"))

        case _: DynamicOptic.Node.AtMapKeys[?] =>
          Left(MigrationError.invalidTransform(path, "AtMapKeys not supported in migrations"))

        case DynamicOptic.Node.Wrapped =>
          Left(MigrationError.invalidTransform(path, "Wrapped not yet supported in migrations"))
      }
    }

  /**
   * Attempt to create a reverse migration.
   * Returns None if any action is not reversible.
   */
  def reverse: Option[DynamicMigration] = {
    val reversed = actions.reverse.map(reverseAction)
    if (reversed.forall(_.isDefined)) Some(DynamicMigration(reversed.flatten))
    else None
  }

  private def reverseAction(action: MigrationAction): Option[MigrationAction] =
    action match {
      case MigrationAction.AddField(path, fieldName, defaultValue) =>
        Some(MigrationAction.DropField(path, fieldName, Some(defaultValue)))

      case MigrationAction.DropField(path, fieldName, Some(reverseDefault)) =>
        Some(MigrationAction.AddField(path, fieldName, reverseDefault))

      case MigrationAction.DropField(_, _, None) =>
        None

      case MigrationAction.RenameField(path, oldName, newName) =>
        Some(MigrationAction.RenameField(path, newName, oldName))

      case MigrationAction.TransformValue(path, transformId, Some(reverseId)) =>
        Some(MigrationAction.TransformValue(path, reverseId, Some(transformId)))

      case MigrationAction.TransformValue(_, _, None) =>
        None

      case MigrationAction.ChangeType(path, from, to, coercionId, Some(reverseId)) =>
        Some(MigrationAction.ChangeType(path, to, from, reverseId, Some(coercionId)))

      case MigrationAction.ChangeType(_, _, _, _, None) =>
        None

      case MigrationAction.Optionalize(path, fieldName) =>
        None

      case MigrationAction.Mandate(_, _, _) =>
        None

      case MigrationAction.RenameCase(path, oldName, newName) =>
        Some(MigrationAction.RenameCase(path, newName, oldName))

      case MigrationAction.TransformCase(path, caseName, transformId, Some(reverseId)) =>
        Some(MigrationAction.TransformCase(path, caseName, reverseId, Some(transformId)))

      case MigrationAction.TransformCase(_, _, _, None) =>
        None

      case MigrationAction.AddCase(path, caseName) =>
        None

      case MigrationAction.DropCase(_, _, _, _) =>
        None

      case MigrationAction.TransformElements(path, transformId, Some(reverseId)) =>
        Some(MigrationAction.TransformElements(path, reverseId, Some(transformId)))

      case MigrationAction.TransformElements(_, _, None) =>
        None

      case MigrationAction.TransformKeys(path, transformId, Some(reverseId)) =>
        Some(MigrationAction.TransformKeys(path, reverseId, Some(transformId)))

      case MigrationAction.TransformKeys(_, _, None) =>
        None

      case MigrationAction.TransformValues(path, transformId, Some(reverseId)) =>
        Some(MigrationAction.TransformValues(path, reverseId, Some(transformId)))

      case MigrationAction.TransformValues(_, _, None) =>
        None

      case MigrationAction.FilterElements(_, _) =>
        None

      case MigrationAction.FilterEntries(_, _) =>
        None
    }
}

object DynamicMigration {
  val identity: DynamicMigration = DynamicMigration(Vector.empty)

  def addField(fieldName: String, defaultValue: DynamicValue): DynamicMigration =
    DynamicMigration(Vector(MigrationAction.AddField(DynamicOptic.root, fieldName, defaultValue)))

  def addField(path: DynamicOptic, fieldName: String, defaultValue: DynamicValue): DynamicMigration =
    DynamicMigration(Vector(MigrationAction.AddField(path, fieldName, defaultValue)))

  def dropField(fieldName: String): DynamicMigration =
    DynamicMigration(Vector(MigrationAction.DropField(DynamicOptic.root, fieldName, None)))

  def dropField(path: DynamicOptic, fieldName: String): DynamicMigration =
    DynamicMigration(Vector(MigrationAction.DropField(path, fieldName, None)))

  def dropField(fieldName: String, reverseDefault: DynamicValue): DynamicMigration =
    DynamicMigration(Vector(MigrationAction.DropField(DynamicOptic.root, fieldName, Some(reverseDefault))))

  def renameField(oldName: String, newName: String): DynamicMigration =
    DynamicMigration(Vector(MigrationAction.RenameField(DynamicOptic.root, oldName, newName)))

  def renameField(path: DynamicOptic, oldName: String, newName: String): DynamicMigration =
    DynamicMigration(Vector(MigrationAction.RenameField(path, oldName, newName)))

  def renameCase(oldCaseName: String, newCaseName: String): DynamicMigration =
    DynamicMigration(Vector(MigrationAction.RenameCase(DynamicOptic.root, oldCaseName, newCaseName)))

  def optionalize(fieldName: String): DynamicMigration =
    DynamicMigration(Vector(MigrationAction.Optionalize(DynamicOptic.root, fieldName)))

  def mandate(fieldName: String, defaultForNone: DynamicValue): DynamicMigration =
    DynamicMigration(Vector(MigrationAction.Mandate(DynamicOptic.root, fieldName, defaultForNone)))
}

/**
 * Migration[A, B] is a typed wrapper around DynamicMigration that carries
 * schema information for both source and target types.
 *
 * Laws:
 * - Identity: Migration.identity[A].apply(a) == Right(a)
 * - Associativity: (m1 >>> m2) >>> m3 == m1 >>> (m2 >>> m3)
 * - Structural Reverse: m.reverse.flatMap(r => m(a).flatMap(r(_))) == Right(a)
 *   (when reverse is defined and all transforms are reversible)
 */
final case class Migration[A, B](
  sourceSchema: Schema[A],
  targetSchema: Schema[B],
  dynamicMigration: DynamicMigration
) {

  /**
   * Apply this migration to a value of type A, producing Either[MigrationError, B].
   */
  def apply(value: A)(implicit registry: TransformRegistry): Either[MigrationError, B] = {
    val dynamicValue = sourceSchema.toDynamicValue(value)
    dynamicMigration(dynamicValue).flatMap { migratedDynamic =>
      targetSchema.fromDynamicValue(migratedDynamic).left.map { schemaError =>
        MigrationError.invalidTransform(
          DynamicOptic.root,
          s"Failed to convert migrated value to target type: ${schemaError.message}"
        )
      }
    }
  }

  /**
   * Compose this migration with another.
   */
  def andThen[C](that: Migration[B, C]): Migration[A, C] =
    Migration(this.sourceSchema, that.targetSchema, this.dynamicMigration >>> that.dynamicMigration)

  /**
   * Alias for andThen.
   */
  def >>>[C](that: Migration[B, C]): Migration[A, C] = andThen(that)

  /**
   * Attempt to create a reverse migration.
   */
  def reverse: Option[Migration[B, A]] =
    dynamicMigration.reverse.map(Migration(targetSchema, sourceSchema, _))
}

object Migration {

  /**
   * Create an identity migration that performs no changes.
   */
  def identity[A](implicit schema: Schema[A]): Migration[A, A] =
    Migration(schema, schema, DynamicMigration.identity)

  /**
   * Create a migration from a DynamicMigration.
   */
  def fromDynamic[A, B](
    dynamicMigration: DynamicMigration
  )(implicit sourceSchema: Schema[A], targetSchema: Schema[B]): Migration[A, B] =
    Migration(sourceSchema, targetSchema, dynamicMigration)

  /**
   * Builder for constructing migrations step by step.
   */
  final class Builder[A, B] private[Migration] (
    sourceSchema: Schema[A],
    targetSchema: Schema[B],
    actions: Vector[MigrationAction]
  ) {
    def addField(fieldName: String, defaultValue: DynamicValue): Builder[A, B] =
      new Builder(sourceSchema, targetSchema, actions :+ MigrationAction.AddField(DynamicOptic.root, fieldName, defaultValue))

    def addField(path: DynamicOptic, fieldName: String, defaultValue: DynamicValue): Builder[A, B] =
      new Builder(sourceSchema, targetSchema, actions :+ MigrationAction.AddField(path, fieldName, defaultValue))

    def dropField(fieldName: String): Builder[A, B] =
      new Builder(sourceSchema, targetSchema, actions :+ MigrationAction.DropField(DynamicOptic.root, fieldName, None))

    def dropField(path: DynamicOptic, fieldName: String): Builder[A, B] =
      new Builder(sourceSchema, targetSchema, actions :+ MigrationAction.DropField(path, fieldName, None))

    def renameField(oldName: String, newName: String): Builder[A, B] =
      new Builder(sourceSchema, targetSchema, actions :+ MigrationAction.RenameField(DynamicOptic.root, oldName, newName))

    def renameField(path: DynamicOptic, oldName: String, newName: String): Builder[A, B] =
      new Builder(sourceSchema, targetSchema, actions :+ MigrationAction.RenameField(path, oldName, newName))

    def transformValue(path: DynamicOptic, transformId: String): Builder[A, B] =
      new Builder(sourceSchema, targetSchema, actions :+ MigrationAction.TransformValue(path, transformId, None))

    def transformValue(path: DynamicOptic, transformId: String, reverseId: String): Builder[A, B] =
      new Builder(sourceSchema, targetSchema, actions :+ MigrationAction.TransformValue(path, transformId, Some(reverseId)))

    def renameCase(oldCaseName: String, newCaseName: String): Builder[A, B] =
      new Builder(sourceSchema, targetSchema, actions :+ MigrationAction.RenameCase(DynamicOptic.root, oldCaseName, newCaseName))

    def optionalize(fieldName: String): Builder[A, B] =
      new Builder(sourceSchema, targetSchema, actions :+ MigrationAction.Optionalize(DynamicOptic.root, fieldName))

    def mandate(fieldName: String, defaultForNone: DynamicValue): Builder[A, B] =
      new Builder(sourceSchema, targetSchema, actions :+ MigrationAction.Mandate(DynamicOptic.root, fieldName, defaultForNone))

    def changeType(
      path: DynamicOptic,
      fromTypeName: String,
      toTypeName: String,
      coercionId: String
    ): Builder[A, B] =
      new Builder(sourceSchema, targetSchema, actions :+ MigrationAction.ChangeType(path, fromTypeName, toTypeName, coercionId, None))

    def changeType(
      path: DynamicOptic,
      fromTypeName: String,
      toTypeName: String,
      coercionId: String,
      reverseCoercionId: String
    ): Builder[A, B] =
      new Builder(sourceSchema, targetSchema, actions :+ MigrationAction.ChangeType(path, fromTypeName, toTypeName, coercionId, Some(reverseCoercionId)))

    def transformCase(path: DynamicOptic, caseName: String, transformId: String): Builder[A, B] =
      new Builder(sourceSchema, targetSchema, actions :+ MigrationAction.TransformCase(path, caseName, transformId, None))

    def transformCase(path: DynamicOptic, caseName: String, transformId: String, reverseId: String): Builder[A, B] =
      new Builder(sourceSchema, targetSchema, actions :+ MigrationAction.TransformCase(path, caseName, transformId, Some(reverseId)))

    def addCase(caseName: String): Builder[A, B] =
      new Builder(sourceSchema, targetSchema, actions :+ MigrationAction.AddCase(DynamicOptic.root, caseName))

    def addCase(path: DynamicOptic, caseName: String): Builder[A, B] =
      new Builder(sourceSchema, targetSchema, actions :+ MigrationAction.AddCase(path, caseName))

    def dropCase(caseName: String, migrateTo: String): Builder[A, B] =
      new Builder(sourceSchema, targetSchema, actions :+ MigrationAction.DropCase(DynamicOptic.root, caseName, migrateTo, None))

    def dropCase(path: DynamicOptic, caseName: String, migrateTo: String): Builder[A, B] =
      new Builder(sourceSchema, targetSchema, actions :+ MigrationAction.DropCase(path, caseName, migrateTo, None))

    def dropCase(caseName: String, migrateTo: String, payloadTransformId: String): Builder[A, B] =
      new Builder(sourceSchema, targetSchema, actions :+ MigrationAction.DropCase(DynamicOptic.root, caseName, migrateTo, Some(payloadTransformId)))

    def transformElements(path: DynamicOptic, transformId: String): Builder[A, B] =
      new Builder(sourceSchema, targetSchema, actions :+ MigrationAction.TransformElements(path, transformId, None))

    def transformElements(path: DynamicOptic, transformId: String, reverseId: String): Builder[A, B] =
      new Builder(sourceSchema, targetSchema, actions :+ MigrationAction.TransformElements(path, transformId, Some(reverseId)))

    def transformKeys(path: DynamicOptic, transformId: String): Builder[A, B] =
      new Builder(sourceSchema, targetSchema, actions :+ MigrationAction.TransformKeys(path, transformId, None))

    def transformKeys(path: DynamicOptic, transformId: String, reverseId: String): Builder[A, B] =
      new Builder(sourceSchema, targetSchema, actions :+ MigrationAction.TransformKeys(path, transformId, Some(reverseId)))

    def transformValues(path: DynamicOptic, transformId: String): Builder[A, B] =
      new Builder(sourceSchema, targetSchema, actions :+ MigrationAction.TransformValues(path, transformId, None))

    def transformValues(path: DynamicOptic, transformId: String, reverseId: String): Builder[A, B] =
      new Builder(sourceSchema, targetSchema, actions :+ MigrationAction.TransformValues(path, transformId, Some(reverseId)))

    def filterElements(path: DynamicOptic, predicateId: String): Builder[A, B] =
      new Builder(sourceSchema, targetSchema, actions :+ MigrationAction.FilterElements(path, predicateId))

    def filterEntries(path: DynamicOptic, predicateId: String): Builder[A, B] =
      new Builder(sourceSchema, targetSchema, actions :+ MigrationAction.FilterEntries(path, predicateId))

    def build: Migration[A, B] =
      Migration(sourceSchema, targetSchema, DynamicMigration(actions))
  }

  def builder[A, B](implicit sourceSchema: Schema[A], targetSchema: Schema[B]): Builder[A, B] =
    new Builder(sourceSchema, targetSchema, Vector.empty)
}
