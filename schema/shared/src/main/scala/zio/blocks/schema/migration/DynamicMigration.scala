package zio.blocks.schema.migration

import zio.blocks.chunk.Chunk
import zio.blocks.schema._

/**
 * An untyped, pure data migration that operates on [[DynamicValue]].
 *
 * [[DynamicMigration]] is fully serializable and contains:
 *   - No user functions
 *   - No closures
 *   - No reflection
 *   - No runtime code generation
 *
 * This enables migrations to be:
 *   - Stored in registries
 *   - Applied dynamically
 *   - Inspected and transformed
 *   - Used to generate DDL, SQL, offline data transforms, etc.
 */
final case class DynamicMigration(actions: Vector[MigrationAction]) {

  /**
   * Apply this migration to transform a [[DynamicValue]].
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
    actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) {
      case (Right(v), action) => DynamicMigration.applyAction(v, action)
      case (left, _)          => left
    }

  /**
   * Compose this migration with another, applying actions sequentially.
   */
  def ++(that: DynamicMigration): DynamicMigration =
    new DynamicMigration(actions ++ that.actions)

  /**
   * Alias for `++`.
   */
  def andThen(that: DynamicMigration): DynamicMigration = this ++ that

  /**
   * Get the structural reverse of this migration.
   *
   * The reversed migration applies the reverse of each action in reverse order.
   */
  def reverse: DynamicMigration =
    new DynamicMigration(actions.map(_.reverse).reverse)

  /**
   * Check if this migration is empty (no actions).
   */
  def isEmpty: Boolean = actions.isEmpty

  /**
   * Check if this migration is non-empty.
   */
  def nonEmpty: Boolean = actions.nonEmpty
}

object DynamicMigration {

  private def wrapExprError(at: DynamicOptic, action: String)(error: MigrationError): MigrationError =
    MigrationError.single(MigrationError.ActionFailed(at, action, error.message))

  /**
   * An empty migration that performs no changes.
   */
  val empty: DynamicMigration = new DynamicMigration(Vector.empty)

  /**
   * Create a migration from a single action.
   */
  def apply(action: MigrationAction): DynamicMigration =
    new DynamicMigration(Vector(action))

  /**
   * Apply a single action to a DynamicValue.
   */
  private[migration] def applyAction(
    value: DynamicValue,
    action: MigrationAction
  ): Either[MigrationError, DynamicValue] = action match {
    case MigrationAction.Identity => Right(value)

    case MigrationAction.AddField(at, name, default) =>
      modifyAtPath(value, at) {
        case record @ DynamicValue.Record(fields) =>
          if (fields.exists(_._1 == name)) {
            Left(MigrationError.single(MigrationError.FieldAlreadyExists(at.field(name), name)))
          } else {
            default.eval(record).left.map(wrapExprError(at.field(name), "AddField")).map { defaultValue =>
              DynamicValue.Record(fields :+ (name -> defaultValue))
            }
          }
        case other =>
          Left(MigrationError.single(MigrationError.NotARecord(at, getDynamicValueTypeName(other))))
      }

    case MigrationAction.DropField(at, name, _) =>
      modifyAtPath(value, at) {
        case DynamicValue.Record(fields) =>
          if (!fields.exists(_._1 == name)) {
            Left(MigrationError.single(MigrationError.FieldNotFound(at.field(name), name)))
          } else {
            Right(DynamicValue.Record(fields.filterNot(_._1 == name)))
          }
        case other =>
          Left(MigrationError.single(MigrationError.NotARecord(at, getDynamicValueTypeName(other))))
      }

    case MigrationAction.RenameField(at, from, to) =>
      modifyAtPath(value, at) {
        case DynamicValue.Record(fields) =>
          if (!fields.exists(_._1 == from)) {
            Left(MigrationError.single(MigrationError.FieldNotFound(at.field(from), from)))
          } else if (fields.exists(_._1 == to)) {
            Left(MigrationError.single(MigrationError.FieldAlreadyExists(at.field(to), to)))
          } else {
            Right(DynamicValue.Record(fields.map {
              case (n, v) if n == from => (to, v)
              case other               => other
            }))
          }
        case other =>
          Left(MigrationError.single(MigrationError.NotARecord(at, getDynamicValueTypeName(other))))
      }

    case MigrationAction.TransformValue(at, transform, _) =>
      modifyAtPathWithParentContext(value, at) { (_, targetValue) =>
        transform.eval(targetValue).left.map(wrapExprError(at, "TransformValue"))
      }

    case MigrationAction.Mandate(at, default) =>
      modifyAtPathWithParentContext(value, at) { (context, targetValue) =>
        targetValue match {
          case DynamicValue.Variant("None", _) =>
            default.eval(context).left.map(wrapExprError(at, "Mandate"))
          case DynamicValue.Variant("Some", inner) =>
            Right(inner)
          case other =>
            // Already a non-optional value, return as-is
            Right(other)
        }
      }

    case MigrationAction.Optionalize(at, _) =>
      modifyAtPath(value, at) {
        case already @ DynamicValue.Variant("Some", _) => Right(already)
        case DynamicValue.Variant("None", _)           => Right(DynamicValue.Variant("None", DynamicValue.Record(Chunk.empty)))
        case targetValue                               => Right(DynamicValue.Variant("Some", targetValue))
      }

    case MigrationAction.ChangeType(at, converter, _) =>
      modifyAtPathWithParentContext(value, at) { (context, _) =>
        converter.eval(context).left.map(wrapExprError(at, "ChangeType"))
      }

    case MigrationAction.Join(at, sourcePaths, combiner, _) =>
      // For join, we need special handling - gather values from source paths and combine
      val parentPath = DynamicOptic(at.nodes.dropRight(1))
      modifyAtPath(value, parentPath) {
        case record @ DynamicValue.Record(fields) =>
          // Extract values from source paths
          val sourceResults = sourcePaths.map { path =>
            DynamicSchemaExpr.navigateDynamicValue(record, path).toRight(path)
          }
          val missingPaths = sourceResults.collect { case Left(path) => path.toString }
          if (missingPaths.nonEmpty) {
            Left(
              MigrationError.single(
                MigrationError.PathNavigationFailed(at, s"Source paths not found: ${missingPaths.mkString(", ")}")
              )
            )
          } else {
            val sourceValues = sourceResults.collect { case Right(v) => v }
            // Create a temporary record with the source values for the combiner
            val tempRecord = DynamicValue.Record(
              Chunk.from(
                sourceValues.zipWithIndex.map { case (v, i) => (s"_$i", v) }
              )
            )
            combiner.eval(tempRecord).left.map(wrapExprError(at, "Join")).flatMap { combined =>
              // Remove source fields and add combined field
              val sourceFieldNames = sourcePaths
                .flatMap(_.nodes.lastOption)
                .collect { case DynamicOptic.Node.Field(name) =>
                  name
                }
              if (sourceFieldNames.size != sourcePaths.size) {
                Left(
                  MigrationError.single(
                    MigrationError.ActionFailed(
                      at,
                      "Join",
                      s"All source paths must end with a Field node, but ${sourcePaths.size - sourceFieldNames.size} do not"
                    )
                  )
                )
              } else {
                val newFields       = fields.filterNot { case (name, _) => sourceFieldNames.toSet.contains(name) }
                val targetFieldName = at.nodes.lastOption match {
                  case Some(DynamicOptic.Node.Field(name)) => name
                  case _                                   => "combined"
                }
                Right(DynamicValue.Record(newFields :+ (targetFieldName -> combined)))
              }
            }
          }
        case other =>
          Left(MigrationError.single(MigrationError.NotARecord(at, getDynamicValueTypeName(other))))
      }

    case MigrationAction.Split(at, targetPaths, splitter, _) =>
      val parentPath = DynamicOptic(at.nodes.dropRight(1))
      modifyAtPath(value, parentPath) {
        case DynamicValue.Record(fields) =>
          at.nodes.lastOption match {
            case Some(DynamicOptic.Node.Field(sourceFieldName)) =>
              fields.find(_._1 == sourceFieldName) match {
                case Some((_, sourceValue)) =>
                  splitter.eval(sourceValue).left.map(wrapExprError(at, "Split")).flatMap {
                    case DynamicValue.Sequence(splitValues) =>
                      if (splitValues.length != targetPaths.length) {
                        Left(
                          MigrationError.single(
                            MigrationError.ActionFailed(
                              at,
                              "Split",
                              s"Expected ${targetPaths.length} values but got ${splitValues.length}"
                            )
                          )
                        )
                      } else {
                        val targetFieldNames = targetPaths.flatMap(_.nodes.lastOption).collect {
                          case DynamicOptic.Node.Field(name) => name
                        }
                        if (targetFieldNames.length != targetPaths.length) {
                          Left(
                            MigrationError.single(
                              MigrationError.ActionFailed(
                                at,
                                "Split",
                                s"All target paths must end with a Field node, but ${targetPaths.length - targetFieldNames.length} do not"
                              )
                            )
                          )
                        } else {
                          val newFields = fields.filterNot(_._1 == sourceFieldName) ++
                            targetFieldNames.zip(splitValues)
                          Right(DynamicValue.Record(newFields))
                        }
                      }
                    case other =>
                      Left(MigrationError.single(MigrationError.NotASequence(at, getDynamicValueTypeName(other))))
                  }
                case None =>
                  Left(MigrationError.single(MigrationError.FieldNotFound(at, sourceFieldName)))
              }
            case _ =>
              Left(MigrationError.single(MigrationError.PathNavigationFailed(at, "Invalid path")))
          }
        case other =>
          Left(MigrationError.single(MigrationError.NotARecord(at, getDynamicValueTypeName(other))))
      }

    case MigrationAction.RenameCase(at, from, to) =>
      modifyAtPath(value, at) {
        case DynamicValue.Variant(caseName, inner) if caseName == from =>
          Right(DynamicValue.Variant(to, inner))
        case variant: DynamicValue.Variant =>
          // If the case name doesn't match, pass through unchanged
          Right(variant)
        case other =>
          Left(MigrationError.single(MigrationError.NotAVariant(at, getDynamicValueTypeName(other))))
      }

    case MigrationAction.TransformCase(at, caseName, nestedActions) =>
      modifyAtPath(value, at) {
        case DynamicValue.Variant(cn, inner) if cn == caseName =>
          val nestedMigration = new DynamicMigration(nestedActions)
          nestedMigration.apply(inner).map(transformed => DynamicValue.Variant(cn, transformed))
        case variant: DynamicValue.Variant =>
          // If the case name doesn't match, pass through unchanged
          Right(variant)
        case other =>
          Left(MigrationError.single(MigrationError.NotAVariant(at, getDynamicValueTypeName(other))))
      }

    case MigrationAction.TransformElements(at, transform, _) =>
      modifyAtPath(value, at) {
        case DynamicValue.Sequence(elements) =>
          val transformedElements =
            elements.map(e => transform.eval(e).left.map(wrapExprError(at, "TransformElements")))
          val errors = transformedElements.collect { case Left(e) => e }
          if (errors.nonEmpty) {
            Left(errors.reduce(_ ++ _))
          } else {
            Right(DynamicValue.Sequence(transformedElements.collect { case Right(v) => v }))
          }
        case other =>
          Left(MigrationError.single(MigrationError.NotASequence(at, getDynamicValueTypeName(other))))
      }

    case MigrationAction.TransformKeys(at, transform, _) =>
      modifyAtPath(value, at) {
        case DynamicValue.Map(entries) =>
          val transformedEntries = entries.map { case (k, v) =>
            transform.eval(k).left.map(wrapExprError(at, "TransformKeys")).map(tk => (tk, v))
          }
          val errors = transformedEntries.collect { case Left(e) => e }
          if (errors.nonEmpty) {
            Left(errors.reduce(_ ++ _))
          } else {
            Right(DynamicValue.Map(transformedEntries.collect { case Right(entry) => entry }))
          }
        case other =>
          Left(MigrationError.single(MigrationError.NotAMap(at, getDynamicValueTypeName(other))))
      }

    case MigrationAction.TransformValues(at, transform, _) =>
      modifyAtPath(value, at) {
        case DynamicValue.Map(entries) =>
          val transformedEntries = entries.map { case (k, v) =>
            transform.eval(v).left.map(wrapExprError(at, "TransformValues")).map(tv => (k, tv))
          }
          val errors = transformedEntries.collect { case Left(e) => e }
          if (errors.nonEmpty) {
            Left(errors.reduce(_ ++ _))
          } else {
            Right(DynamicValue.Map(transformedEntries.collect { case Right(entry) => entry }))
          }
        case other =>
          Left(MigrationError.single(MigrationError.NotAMap(at, getDynamicValueTypeName(other))))
      }
  }

  /**
   * Navigate to a path in the DynamicValue and apply a modification function.
   */
  private def modifyAtPath(
    value: DynamicValue,
    path: DynamicOptic
  )(modify: DynamicValue => Either[MigrationError, DynamicValue]): Either[MigrationError, DynamicValue] =
    if (path.nodes.isEmpty) {
      modify(value)
    } else {
      modifyAtPathRec(value, path, 0)(modify)
    }

  private def modifyAtPathWithParentContext(
    value: DynamicValue,
    path: DynamicOptic
  )(
    modify: (DynamicValue, DynamicValue) => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] =
    if (path.nodes.isEmpty) {
      modify(value, value)
    } else {
      modifyAtPathWithParentContextRec(value, path, 0)(modify)
    }

  private def modifyAtPathWithParentContextRec(
    value: DynamicValue,
    path: DynamicOptic,
    idx: Int
  )(
    modify: (DynamicValue, DynamicValue) => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] = {
    if (idx >= path.nodes.length) {
      // This should be unreachable for non-empty paths.
      modify(value, value)
    } else {
      val isLast = idx == path.nodes.length - 1
      path.nodes(idx) match {
        case DynamicOptic.Node.Field(name) =>
          value match {
            case DynamicValue.Record(fields) =>
              val fieldIdx = fields.indexWhere(_._1 == name)
              if (fieldIdx < 0) {
                Left(MigrationError.single(MigrationError.FieldNotFound(path, name)))
              } else if (isLast) {
                val targetValue = fields(fieldIdx)._2
                modify(value, targetValue).map { newValue =>
                  DynamicValue.Record(fields.updated(fieldIdx, (name, newValue)))
                }
              } else {
                modifyAtPathWithParentContextRec(fields(fieldIdx)._2, path, idx + 1)(modify).map { newValue =>
                  DynamicValue.Record(fields.updated(fieldIdx, (name, newValue)))
                }
              }
            case other =>
              Left(MigrationError.single(MigrationError.NotARecord(path, getDynamicValueTypeName(other))))
          }

        case DynamicOptic.Node.Case(caseName) =>
          value match {
            case DynamicValue.Variant(cn, inner) if cn == caseName =>
              if (isLast) {
                modify(value, inner).map { newInner =>
                  DynamicValue.Variant(cn, newInner)
                }
              } else {
                modifyAtPathWithParentContextRec(inner, path, idx + 1)(modify).map { newInner =>
                  DynamicValue.Variant(cn, newInner)
                }
              }
            case variant: DynamicValue.Variant =>
              // Case doesn't match, return unchanged
              Right(variant)
            case other =>
              Left(MigrationError.single(MigrationError.NotAVariant(path, getDynamicValueTypeName(other))))
          }

        case DynamicOptic.Node.AtIndex(i) =>
          value match {
            case DynamicValue.Sequence(elements) =>
              if (i < 0 || i >= elements.length) {
                Left(MigrationError.single(MigrationError.IndexOutOfBounds(path, i, elements.length)))
              } else if (isLast) {
                val targetValue = elements(i)
                modify(value, targetValue).map { newValue =>
                  DynamicValue.Sequence(elements.updated(i, newValue))
                }
              } else {
                modifyAtPathWithParentContextRec(elements(i), path, idx + 1)(modify).map { newValue =>
                  DynamicValue.Sequence(elements.updated(i, newValue))
                }
              }
            case other =>
              Left(MigrationError.single(MigrationError.NotASequence(path, getDynamicValueTypeName(other))))
          }

        case DynamicOptic.Node.AtMapKey(key) =>
          value match {
            case DynamicValue.Map(entries) =>
              val entryIdx = entries.indexWhere(_._1 == key)
              if (entryIdx < 0) {
                Left(MigrationError.single(MigrationError.KeyNotFound(path, key.toString)))
              } else if (isLast) {
                val targetValue = entries(entryIdx)._2
                modify(value, targetValue).map { newValue =>
                  DynamicValue.Map(entries.updated(entryIdx, (key, newValue)))
                }
              } else {
                modifyAtPathWithParentContextRec(entries(entryIdx)._2, path, idx + 1)(modify).map { newValue =>
                  DynamicValue.Map(entries.updated(entryIdx, (key, newValue)))
                }
              }
            case other =>
              Left(MigrationError.single(MigrationError.NotAMap(path, getDynamicValueTypeName(other))))
          }

        case DynamicOptic.Node.AtIndices(indices) =>
          value match {
            case DynamicValue.Sequence(elements) =>
              val unique  = indices.distinct.sorted
              val results = unique.map { i =>
                if (i < 0 || i >= elements.length) {
                  Left(MigrationError.single(MigrationError.IndexOutOfBounds(path, i, elements.length)))
                } else if (isLast) {
                  modify(value, elements(i)).map(v => (i, v))
                } else {
                  modifyAtPathWithParentContextRec(elements(i), path, idx + 1)(modify).map(v => (i, v))
                }
              }
              val errors = results.collect { case Left(e) => e }
              if (errors.nonEmpty) {
                Left(errors.reduce(_ ++ _))
              } else {
                val updates = results.collect { case Right(pair) => pair }.toMap
                Right(
                  DynamicValue.Sequence(
                    elements.zipWithIndex.map { case (v, i) => updates.getOrElse(i, v) }
                  )
                )
              }
            case other =>
              Left(MigrationError.single(MigrationError.NotASequence(path, getDynamicValueTypeName(other))))
          }

        case DynamicOptic.Node.AtMapKeys(keys) =>
          value match {
            case DynamicValue.Map(entries) =>
              val uniqueKeys = keys.distinct
              val results    = uniqueKeys.map { key =>
                val entryIdx = entries.indexWhere(_._1 == key)
                if (entryIdx < 0) {
                  Left(MigrationError.single(MigrationError.KeyNotFound(path, key.toString)))
                } else if (isLast) {
                  modify(value, entries(entryIdx)._2).map(v => (entryIdx, key, v))
                } else {
                  modifyAtPathWithParentContextRec(entries(entryIdx)._2, path, idx + 1)(modify).map(v =>
                    (entryIdx, key, v)
                  )
                }
              }
              val errors = results.collect { case Left(e) => e }
              if (errors.nonEmpty) {
                Left(errors.reduce(_ ++ _))
              } else {
                val updates    = results.collect { case Right((idx, k, v)) => (idx, (k, v)) }.toMap
                val newEntries =
                  entries.zipWithIndex.map { case (entry, i) => updates.getOrElse(i, entry) }
                Right(DynamicValue.Map(newEntries))
              }
            case other =>
              Left(MigrationError.single(MigrationError.NotAMap(path, getDynamicValueTypeName(other))))
          }

        case DynamicOptic.Node.Elements =>
          value match {
            case DynamicValue.Sequence(elements) =>
              val results =
                elements.map { e =>
                  if (isLast) modify(value, e) else modifyAtPathWithParentContextRec(e, path, idx + 1)(modify)
                }
              val errors = results.collect { case Left(e) => e }
              if (errors.nonEmpty) {
                Left(errors.reduce(_ ++ _))
              } else {
                Right(DynamicValue.Sequence(results.collect { case Right(v) => v }))
              }
            case other =>
              Left(MigrationError.single(MigrationError.NotASequence(path, getDynamicValueTypeName(other))))
          }

        case DynamicOptic.Node.MapKeys =>
          value match {
            case DynamicValue.Map(entries) =>
              val results = entries.map { case (k, v) =>
                if (isLast) modify(value, k).map(nk => (nk, v))
                else modifyAtPathWithParentContextRec(k, path, idx + 1)(modify).map(nk => (nk, v))
              }
              val errors = results.collect { case Left(e) => e }
              if (errors.nonEmpty) {
                Left(errors.reduce(_ ++ _))
              } else {
                Right(DynamicValue.Map(results.collect { case Right(entry) => entry }))
              }
            case other =>
              Left(MigrationError.single(MigrationError.NotAMap(path, getDynamicValueTypeName(other))))
          }

        case DynamicOptic.Node.MapValues =>
          value match {
            case DynamicValue.Map(entries) =>
              val results = entries.map { case (k, v) =>
                if (isLast) modify(value, v).map(nv => (k, nv))
                else modifyAtPathWithParentContextRec(v, path, idx + 1)(modify).map(nv => (k, nv))
              }
              val errors = results.collect { case Left(e) => e }
              if (errors.nonEmpty) {
                Left(errors.reduce(_ ++ _))
              } else {
                Right(DynamicValue.Map(results.collect { case Right(entry) => entry }))
              }
            case other =>
              Left(MigrationError.single(MigrationError.NotAMap(path, getDynamicValueTypeName(other))))
          }

        case DynamicOptic.Node.Wrapped =>
          value match {
            case DynamicValue.Record(fields) if fields.length == 1 =>
              if (isLast) {
                modify(value, fields.head._2).map { newValue =>
                  DynamicValue.Record(fields.head._1 -> newValue)
                }
              } else {
                modifyAtPathWithParentContextRec(fields.head._2, path, idx + 1)(modify).map { newValue =>
                  DynamicValue.Record(fields.head._1 -> newValue)
                }
              }
            case other =>
              Left(
                MigrationError.single(
                  MigrationError.PathNavigationFailed(path, s"Cannot unwrap ${getDynamicValueTypeName(other)}")
                )
              )
          }
      }
    }
  }

  private def modifyAtPathRec(
    value: DynamicValue,
    path: DynamicOptic,
    idx: Int
  )(modify: DynamicValue => Either[MigrationError, DynamicValue]): Either[MigrationError, DynamicValue] = {
    if (idx >= path.nodes.length) {
      modify(value)
    } else {
      path.nodes(idx) match {
        case DynamicOptic.Node.Field(name) =>
          value match {
            case DynamicValue.Record(fields) =>
              val fieldIdx = fields.indexWhere(_._1 == name)
              if (fieldIdx < 0) {
                Left(MigrationError.single(MigrationError.FieldNotFound(path, name)))
              } else {
                modifyAtPathRec(fields(fieldIdx)._2, path, idx + 1)(modify).map { newValue =>
                  DynamicValue.Record(fields.updated(fieldIdx, (name, newValue)))
                }
              }
            case other =>
              Left(MigrationError.single(MigrationError.NotARecord(path, getDynamicValueTypeName(other))))
          }

        case DynamicOptic.Node.Case(caseName) =>
          value match {
            case DynamicValue.Variant(cn, inner) if cn == caseName =>
              modifyAtPathRec(inner, path, idx + 1)(modify).map { newInner =>
                DynamicValue.Variant(cn, newInner)
              }
            case variant: DynamicValue.Variant =>
              // Case doesn't match, return unchanged
              Right(variant)
            case other =>
              Left(MigrationError.single(MigrationError.NotAVariant(path, getDynamicValueTypeName(other))))
          }

        case DynamicOptic.Node.AtIndex(i) =>
          value match {
            case DynamicValue.Sequence(elements) =>
              if (i < 0 || i >= elements.length) {
                Left(MigrationError.single(MigrationError.IndexOutOfBounds(path, i, elements.length)))
              } else {
                modifyAtPathRec(elements(i), path, idx + 1)(modify).map { newElement =>
                  DynamicValue.Sequence(elements.updated(i, newElement))
                }
              }
            case other =>
              Left(MigrationError.single(MigrationError.NotASequence(path, getDynamicValueTypeName(other))))
          }

        case DynamicOptic.Node.AtMapKey(key) =>
          value match {
            case DynamicValue.Map(entries) =>
              val entryIdx = entries.indexWhere(_._1 == key)
              if (entryIdx < 0) {
                Left(MigrationError.single(MigrationError.KeyNotFound(path, key.toString)))
              } else {
                modifyAtPathRec(entries(entryIdx)._2, path, idx + 1)(modify).map { newValue =>
                  DynamicValue.Map(entries.updated(entryIdx, (key, newValue)))
                }
              }
            case other =>
              Left(MigrationError.single(MigrationError.NotAMap(path, getDynamicValueTypeName(other))))
          }

        case DynamicOptic.Node.Elements =>
          value match {
            case DynamicValue.Sequence(elements) =>
              val results = elements.map(e => modifyAtPathRec(e, path, idx + 1)(modify))
              val errors  = results.collect { case Left(e) => e }
              if (errors.nonEmpty) {
                Left(errors.reduce(_ ++ _))
              } else {
                Right(DynamicValue.Sequence(results.collect { case Right(v) => v }))
              }
            case other =>
              Left(MigrationError.single(MigrationError.NotASequence(path, getDynamicValueTypeName(other))))
          }

        case DynamicOptic.Node.MapKeys =>
          value match {
            case DynamicValue.Map(entries) =>
              val results = entries.map { case (k, v) =>
                modifyAtPathRec(k, path, idx + 1)(modify).map(nk => (nk, v))
              }
              val errors = results.collect { case Left(e) => e }
              if (errors.nonEmpty) {
                Left(errors.reduce(_ ++ _))
              } else {
                Right(DynamicValue.Map(results.collect { case Right(entry) => entry }))
              }
            case other =>
              Left(MigrationError.single(MigrationError.NotAMap(path, getDynamicValueTypeName(other))))
          }

        case DynamicOptic.Node.MapValues =>
          value match {
            case DynamicValue.Map(entries) =>
              val results = entries.map { case (k, v) =>
                modifyAtPathRec(v, path, idx + 1)(modify).map(nv => (k, nv))
              }
              val errors = results.collect { case Left(e) => e }
              if (errors.nonEmpty) {
                Left(errors.reduce(_ ++ _))
              } else {
                Right(DynamicValue.Map(results.collect { case Right(entry) => entry }))
              }
            case other =>
              Left(MigrationError.single(MigrationError.NotAMap(path, getDynamicValueTypeName(other))))
          }

        case DynamicOptic.Node.Wrapped =>
          value match {
            case DynamicValue.Record(fields) if fields.length == 1 =>
              modifyAtPathRec(fields.head._2, path, idx + 1)(modify).map { newValue =>
                DynamicValue.Record(fields.head._1 -> newValue)
              }
            case other =>
              Left(
                MigrationError.single(
                  MigrationError.PathNavigationFailed(path, s"Cannot unwrap ${getDynamicValueTypeName(other)}")
                )
              )
          }

        case DynamicOptic.Node.AtIndices(indices) =>
          value match {
            case DynamicValue.Sequence(elements) =>
              val unique  = indices.distinct.sorted
              val results = unique.map { i =>
                if (i < 0 || i >= elements.length) {
                  Left(MigrationError.single(MigrationError.IndexOutOfBounds(path, i, elements.length)))
                } else {
                  modifyAtPathRec(elements(i), path, idx + 1)(modify).map(v => (i, v))
                }
              }
              val errors = results.collect { case Left(e) => e }
              if (errors.nonEmpty) {
                Left(errors.reduce(_ ++ _))
              } else {
                val updates = results.collect { case Right(pair) => pair }.toMap
                Right(
                  DynamicValue.Sequence(
                    elements.zipWithIndex.map { case (v, i) => updates.getOrElse(i, v) }
                  )
                )
              }
            case other =>
              Left(MigrationError.single(MigrationError.NotASequence(path, getDynamicValueTypeName(other))))
          }

        case DynamicOptic.Node.AtMapKeys(keys) =>
          value match {
            case DynamicValue.Map(entries) =>
              val uniqueKeys = keys.distinct
              val results    = uniqueKeys.map { key =>
                val entryIdx = entries.indexWhere(_._1 == key)
                if (entryIdx < 0) {
                  Left(MigrationError.single(MigrationError.KeyNotFound(path, key.toString)))
                } else {
                  modifyAtPathRec(entries(entryIdx)._2, path, idx + 1)(modify).map(v => (entryIdx, key, v))
                }
              }
              val errors = results.collect { case Left(e) => e }
              if (errors.nonEmpty) {
                Left(errors.reduce(_ ++ _))
              } else {
                val updates    = results.collect { case Right((idx, k, v)) => (idx, (k, v)) }.toMap
                val newEntries =
                  entries.zipWithIndex.map { case (entry, i) => updates.getOrElse(i, entry) }
                Right(DynamicValue.Map(newEntries))
              }
            case other =>
              Left(MigrationError.single(MigrationError.NotAMap(path, getDynamicValueTypeName(other))))
          }
      }
    }
  }

  private def getDynamicValueTypeName(dv: DynamicValue): String = dv match {
    case _: DynamicValue.Primitive => "Primitive"
    case _: DynamicValue.Record    => "Record"
    case _: DynamicValue.Variant   => "Variant"
    case _: DynamicValue.Sequence  => "Sequence"
    case _: DynamicValue.Map       => "Map"
    case DynamicValue.Null         => "Null"
  }
}
