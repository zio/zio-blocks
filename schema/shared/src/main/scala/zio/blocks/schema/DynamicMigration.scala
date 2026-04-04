/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.schema

import zio.blocks.chunk.Chunk
import zio.blocks.schema.DynamicOptic.Node

/**
 * An untyped, fully serializable migration that operates on [[DynamicValue]].
 *
 * DynamicMigration is the pure, serializable core of the migration system. It
 * represents structural transformations between schema versions as first-class,
 * serializable data.
 *
 * ==Properties==
 *
 *   - '''Pure Data''': No user functions, closures, or reflection
 *   - '''Serializable'': Can be stored in registries and applied dynamically
 *   - '''Introspectable'': The ADT is fully inspectable for DDL generation
 *   - '''Composable'': Migrations can be composed sequentially
 *   - '''Reversible'': Structural reverse is supported (best-effort semantic
 *     inverse)
 *
 * @see
 *   [[MigrationAction]] for individual transformation steps
 * @see
 *   [[MigrationError]] for error handling
 */
final case class DynamicMigration(actions: Chunk[MigrationAction]) { self =>

  /**
   * Applies this migration to a DynamicValue.
   *
   * @param value
   *   the input value to migrate
   * @return
   *   Either a MigrationError or the migrated value
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
    var result: Either[MigrationError, DynamicValue] = Right(value)
    val iterator                                     = actions.iterator
    while (iterator.hasNext && result.isRight) {
      val action = iterator.next()
      result = result.flatMap(v => applyAction(action, v))
    }
    result
  }

  /**
   * Composes this migration with another, creating a migration that applies
   * this first, then `that`.
   *
   * This operation is associative:
   * {{{
   *   (m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)
   * }}}
   */
  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(actions ++ that.actions)

  /**
   * Alias for `++`.
   */
  def andThen(that: DynamicMigration): DynamicMigration = this ++ that

  /**
   * Returns the structural reverse of this migration.
   *
   * The reverse migration satisfies:
   * {{{
   *   m.reverse.reverse == m
   * }}}
   *
   * For semantic invertibility:
   * {{{
   *   m.apply(a) == Right(b) ⇒ m.reverse.apply(b) == Right(a)
   * }}}
   * (when sufficient information exists)
   */
  def reverse: DynamicMigration =
    DynamicMigration(actions.reverse.map(_.reverse))

  /**
   * Returns the number of actions in this migration.
   */
  def size: Int = actions.length

  /**
   * Returns true if this migration has no actions.
   */
  def isEmpty: Boolean = actions.isEmpty

  /**
   * Returns true if this migration has at least one action.
   */
  def nonEmpty: Boolean = actions.nonEmpty

  // ═══════════════════════════════════════════════════════════════════════════════
  // Private Implementation
  // ═══════════════════════════════════════════════════════════════════════════════

  private def applyAction(action: MigrationAction, value: DynamicValue): Either[MigrationError, DynamicValue] =
    action match {
      case a: MigrationAction.AddField          => addField(a, value)
      case a: MigrationAction.DropField         => dropField(a, value)
      case a: MigrationAction.Rename            => rename(a, value)
      case a: MigrationAction.TransformValue    => transformValue(a, value)
      case a: MigrationAction.Mandate           => mandate(a, value)
      case a: MigrationAction.Optionalize       => optionalize(a, value)
      case a: MigrationAction.Join              => join(a, value)
      case a: MigrationAction.Split             => split(a, value)
      case a: MigrationAction.ChangeType        => changeType(a, value)
      case a: MigrationAction.RenameCase        => renameCase(a, value)
      case a: MigrationAction.TransformCase     => transformCase(a, value)
      case a: MigrationAction.TransformElements => transformElements(a, value)
      case a: MigrationAction.TransformKeys     => transformKeys(a, value)
      case a: MigrationAction.TransformValues   => transformValues(a, value)
    }

  private def addField(action: MigrationAction.AddField, value: DynamicValue): Either[MigrationError, DynamicValue] = {
    val fieldName  = action.at.nodes.lastOption.collect { case Node.Field(name) => name }.getOrElse("")
    val parentPath = DynamicOptic(action.at.nodes.dropRight(1))

    if (parentPath.nodes.isEmpty) {
      // Adding at root
      value match {
        case DynamicValue.Record(fields) =>
          if (fields.exists(_._1 == fieldName)) Right(value)
          else Right(DynamicValue.Record(fields :+ (fieldName -> action.default)))
        case _ =>
          Right(action.default)
      }
    } else {
      value
        .modifyOrFail(parentPath) {
          case DynamicValue.Record(fields) if !fields.exists(_._1 == fieldName) =>
            DynamicValue.Record(fields :+ (fieldName -> action.default))
        }
        .left
        .map(err => MigrationError.TransformFailed(parentPath.toScalaString, err.message))
    }
  }

  private def dropField(
    action: MigrationAction.DropField,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] = {
    val fieldName  = action.at.nodes.lastOption.collect { case Node.Field(name) => name }.getOrElse("")
    val parentPath = DynamicOptic(action.at.nodes.dropRight(1))

    if (parentPath.nodes.isEmpty) {
      value match {
        case DynamicValue.Record(fields) =>
          Right(DynamicValue.Record(fields.filterNot(_._1 == fieldName)))
        case other => Right(other)
      }
    } else {
      value
        .modifyOrFail(parentPath) { case DynamicValue.Record(fields) =>
          DynamicValue.Record(fields.filterNot(_._1 == fieldName))
        }
        .left
        .map(err => MigrationError.TransformFailed(parentPath.toScalaString, err.message))
    }
  }

  private def rename(action: MigrationAction.Rename, value: DynamicValue): Either[MigrationError, DynamicValue] = {
    val oldName    = action.at.nodes.lastOption.collect { case Node.Field(name) => name }.getOrElse("")
    val parentPath = DynamicOptic(action.at.nodes.dropRight(1))

    if (parentPath.nodes.isEmpty) {
      value match {
        case DynamicValue.Record(fields) =>
          Right(DynamicValue.Record(fields.map {
            case (name, v) if name == oldName => (action.to, v)
            case field                        => field
          }))
        case other => Right(other)
      }
    } else {
      value
        .modifyOrFail(parentPath) { case DynamicValue.Record(fields) =>
          DynamicValue.Record(fields.map {
            case (name, v) if name == oldName => (action.to, v)
            case field                        => field
          })
        }
        .left
        .map(err => MigrationError.TransformFailed(parentPath.toScalaString, err.message))
    }
  }

  private def transformValue(
    action: MigrationAction.TransformValue,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    value.get(action.at).one match {
      case Right(targetValue) =>
        action.transform(targetValue) match {
          case Right(transformed) => Right(value.set(action.at, transformed))
          case Left(err)          => Left(err)
        }
      case Left(_) =>
        // Path doesn't exist - return value unchanged
        Right(value)
    }

  private def mandate(action: MigrationAction.Mandate, value: DynamicValue): Either[MigrationError, DynamicValue] =
    value.get(action.at).one match {
      case Right(DynamicValue.Variant("None", _)) =>
        Right(value.set(action.at, action.default))
      case Right(DynamicValue.Variant("Some", DynamicValue.Record(fields))) =>
        val innerValue = fields.find(_._1 == "value").map(_._2).getOrElse(action.default)
        Right(value.set(action.at, innerValue))
      case Right(_) =>
        Right(value)
      case Left(_) =>
        Right(value)
    }

  private def optionalize(
    action: MigrationAction.Optionalize,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    value.get(action.at).one match {
      case Right(v) =>
        Right(value.set(action.at, DynamicValue.Variant("Some", DynamicValue.Record(Chunk("value" -> v)))))
      case Left(_) =>
        Right(value)
    }

  private def join(action: MigrationAction.Join, value: DynamicValue): Either[MigrationError, DynamicValue] = {
    val collected = action.sourcePaths.foldLeft[Either[MigrationError, Chunk[DynamicValue]]](Right(Chunk.empty)) {
      case (Right(acc), path) =>
        value.get(path).one match {
          case Right(v) => Right(acc :+ v)
          case Left(_)  => Right(acc) // Skip missing paths
        }
      case (Left(err), _) => Left(err)
    }

    collected.flatMap { values =>
      action.combiner(DynamicValue.Sequence(values)) match {
        case Right(joined) => Right(value.insert(action.at, joined))
        case Left(err)     => Left(err)
      }
    }
  }

  private def split(action: MigrationAction.Split, value: DynamicValue): Either[MigrationError, DynamicValue] =
    value.get(action.at).one match {
      case Right(sourceValue) =>
        action.splitter(sourceValue) match {
          case Right(DynamicValue.Sequence(parts)) =>
            var result = value
            parts.zip(action.targetPaths).foreach { case (part, path) =>
              result = result.insert(path, part)
            }
            Right(result)

          case Right(other) =>
            Left(
              MigrationError.TransformFailed(
                action.at.toScalaString,
                s"Split must produce a Sequence, found ${other.valueType}"
              )
            )

          case Left(err) => Left(err)
        }
      case Left(err) =>
        Left(MigrationError.NotFound(action.at.toScalaString, err.message))
    }

  private def changeType(
    action: MigrationAction.ChangeType,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    value.get(action.at).one match {
      case Right(targetValue) =>
        action.converter(targetValue) match {
          case Right(converted) => Right(value.set(action.at, converted))
          case Left(err)        => Left(err)
        }
      case Left(_) =>
        Right(value)
    }

  private def renameCase(
    action: MigrationAction.RenameCase,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    value
      .modifyOrFail(action.at) { case v @ DynamicValue.Variant(caseName, caseValue) =>
        if (caseName == action.from) DynamicValue.Variant(action.to, caseValue)
        else v
      }
      .left
      .map(err => MigrationError.TransformFailed(action.at.toScalaString, err.message))

  private def transformCase(
    action: MigrationAction.TransformCase,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    value.get(action.at).one match {
      case Right(DynamicValue.Variant(caseName, caseValue)) =>
        if (caseName == action.caseName) {
          val caseMigration = DynamicMigration(action.actions)
          caseMigration(caseValue) match {
            case Right(transformed) => Right(value.set(action.at, DynamicValue.Variant(caseName, transformed)))
            case Left(err)          => Left(err)
          }
        } else {
          Right(value)
        }
      case Right(_) =>
        Right(value)
      case Left(_) =>
        Right(value)
    }

  private def transformElements(
    action: MigrationAction.TransformElements,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    value.get(action.at).one match {
      case Right(DynamicValue.Sequence(elements)) =>
        val results = elements.map(action.transform(_))
        val errors  = results.collect { case Left(e) => e }
        if (errors.nonEmpty) {
          Left(MigrationError.multiple(errors))
        } else {
          Right(value.set(action.at, DynamicValue.Sequence(results.collect { case Right(v) => v })))
        }
      case Right(_) =>
        Right(value)
      case Left(_) =>
        Right(value)
    }

  private def transformKeys(
    action: MigrationAction.TransformKeys,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    value.get(action.at).one match {
      case Right(DynamicValue.Map(entries)) =>
        val results = entries.map { case (k, v) =>
          action.transform(k).map(_ -> v)
        }
        val errors = results.collect { case Left(e) => e }
        if (errors.nonEmpty) {
          Left(MigrationError.multiple(errors))
        } else {
          Right(value.set(action.at, DynamicValue.Map(results.collect { case Right(entry) => entry })))
        }
      case Right(_) =>
        Right(value)
      case Left(_) =>
        Right(value)
    }

  private def transformValues(
    action: MigrationAction.TransformValues,
    value: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    value.get(action.at).one match {
      case Right(DynamicValue.Map(entries)) =>
        val results = entries.map { case (k, v) =>
          action.transform(v).map(k -> _)
        }
        val errors = results.collect { case Left(e) => e }
        if (errors.nonEmpty) {
          Left(MigrationError.multiple(errors))
        } else {
          Right(value.set(action.at, DynamicValue.Map(results.collect { case Right(entry) => entry })))
        }
      case Right(_) =>
        Right(value)
      case Left(_) =>
        Right(value)
    }
}

object DynamicMigration extends MigrationSchemaInstances {

  /** The empty migration that returns the input unchanged. */
  val empty: DynamicMigration = DynamicMigration(Chunk.empty)

  /** Creates a migration from a single action. */
  def single(action: MigrationAction): DynamicMigration = DynamicMigration(Chunk.single(action))

  /** Creates a migration from a sequence of actions. */
  def apply(actions: MigrationAction*): DynamicMigration = DynamicMigration(Chunk.from(actions))

  /** Creates an identity migration that returns the input unchanged. */
  def identity[A]: DynamicMigration = empty

  /**
   * Creates a migration that adds a field at the specified path with a default
   * value.
   */
  def addField(at: DynamicOptic, default: DynamicValue): DynamicMigration =
    single(MigrationAction.AddField(at, default))

  /**
   * Creates a migration that drops a field at the specified path.
   */
  def dropField(at: DynamicOptic, defaultForReverse: DynamicValue): DynamicMigration =
    single(MigrationAction.DropField(at, defaultForReverse))

  /**
   * Creates a migration that renames a field.
   */
  def rename(at: DynamicOptic, to: String): DynamicMigration =
    single(MigrationAction.Rename(at, to))

  /**
   * Creates a migration that transforms a value at the specified path.
   */
  def transformValue(at: DynamicOptic, transform: DynamicTransform): DynamicMigration =
    single(MigrationAction.TransformValue(at, transform))

  /**
   * Creates a migration that converts an optional field to required.
   */
  def mandate(at: DynamicOptic, default: DynamicValue): DynamicMigration =
    single(MigrationAction.Mandate(at, default))

  /**
   * Creates a migration that converts a required field to optional.
   */
  def optionalize(at: DynamicOptic): DynamicMigration =
    single(MigrationAction.Optionalize(at))

  /**
   * Creates a migration that joins multiple source paths into one.
   */
  def join(at: DynamicOptic, sourcePaths: Chunk[DynamicOptic], combiner: DynamicTransform): DynamicMigration =
    single(MigrationAction.Join(at, sourcePaths, combiner))

  /**
   * Creates a migration that splits one value into multiple target paths.
   */
  def split(at: DynamicOptic, targetPaths: Chunk[DynamicOptic], splitter: DynamicTransform): DynamicMigration =
    single(MigrationAction.Split(at, targetPaths, splitter))

  /**
   * Creates a migration that changes the type of a field.
   */
  def changeType(at: DynamicOptic, converter: DynamicTransform): DynamicMigration =
    single(MigrationAction.ChangeType(at, converter))

  /**
   * Creates a migration that renames a case in a sum type.
   */
  def renameCase(at: DynamicOptic, from: String, to: String): DynamicMigration =
    single(MigrationAction.RenameCase(at, from, to))

  /**
   * Creates a migration that transforms the contents of a case.
   */
  def transformCase(at: DynamicOptic, caseName: String, actions: Chunk[MigrationAction]): DynamicMigration =
    single(MigrationAction.TransformCase(at, caseName, actions))

  /**
   * Creates a migration that transforms each element in a sequence.
   */
  def transformElements(at: DynamicOptic, transform: DynamicTransform): DynamicMigration =
    single(MigrationAction.TransformElements(at, transform))

  /**
   * Creates a migration that transforms all keys in a map.
   */
  def transformKeys(at: DynamicOptic, transform: DynamicTransform): DynamicMigration =
    single(MigrationAction.TransformKeys(at, transform))

  /**
   * Creates a migration that transforms all values in a map.
   */
  def transformValues(at: DynamicOptic, transform: DynamicTransform): DynamicMigration =
    single(MigrationAction.TransformValues(at, transform))

  // ═══════════════════════════════════════════════════════════════════════════════
  // Schema Instance
  // ═══════════════════════════════════════════════════════════════════════════════

  implicit lazy val schema: Schema[DynamicMigration] = dynamicMigrationSchema
}
