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

package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, DynamicValue}

/**
 * A migration action represents a single, atomic structural transformation on a
 * [[DynamicValue]]. All actions are path-based, operating at a location
 * specified by a [[DynamicOptic]].
 *
 * Migration actions form a sealed ADT that is fully serializable — no user
 * functions, closures, reflection, or runtime code generation.
 *
 * Each action has:
 *   - `at`: the path where the action operates
 *   - `reverse`: a structurally inverse action
 *   - `apply(value)`: execute the transformation on a DynamicValue
 */
sealed trait MigrationAction {

  /** The path at which this action operates. */
  def at: DynamicOptic

  /** Returns the structurally inverse action. */
  def reverse: MigrationAction

  /**
   * Applies this action to a DynamicValue.
   *
   * @param value
   *   the input value to transform
   * @return
   *   Right(transformed) on success, Left(error) on failure
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue]
}

object MigrationAction {

  // ─────────────────────────────────────────────────────────────────────────
  // Record Actions
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Adds a new field with a default value at the specified path. The reverse
   * action is [[DropField]].
   */
  final case class AddField(
    at: DynamicOptic,
    fieldName: String,
    default: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, fieldName, default)

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      val fieldPath = at.field(fieldName)
      value.insert(fieldPath, default) match {
        case dv if dv ne value => Right(dv)
        case _                 =>
          if (at.nodes.isEmpty) {
            value match {
              case r: DynamicValue.Record =>
                Right(DynamicValue.Record(r.fields :+ (fieldName -> default)))
              case _ =>
                Left(MigrationError.ActionFailed("AddField", at, s"Expected Record, got ${value.valueType}"))
            }
          } else {
            val modified = value.modify(at) {
              case r: DynamicValue.Record =>
                DynamicValue.Record(r.fields :+ (fieldName -> default))
              case other => other
            }
            if (modified ne value) Right(modified)
            else Left(MigrationError.PathNotFound(at))
          }
      }
    }
  }

  /**
   * Removes a field at the specified path. The reverse action is [[AddField]]
   * using the stored default.
   */
  final case class DropField(
    at: DynamicOptic,
    fieldName: String,
    defaultForReverse: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, fieldName, defaultForReverse)

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      if (at.nodes.isEmpty) {
        value match {
          case r: DynamicValue.Record =>
            val newFields = r.fields.filter(_._1 != fieldName)
            if (newFields.length == r.fields.length)
              Left(MigrationError.ActionFailed("DropField", at, s"Field '$fieldName' not found"))
            else
              Right(DynamicValue.Record(newFields))
          case _ =>
            Left(MigrationError.ActionFailed("DropField", at, s"Expected Record, got ${value.valueType}"))
        }
      } else {
        var found    = false
        val modified = value.modify(at) {
          case r: DynamicValue.Record =>
            found = true
            DynamicValue.Record(r.fields.filter(_._1 != fieldName))
          case other =>
            other
        }
        if (found) Right(modified)
        else Left(MigrationError.PathNotFound(at))
      }
  }

  /**
   * Renames a field from one name to another at the specified path. The reverse
   * action renames back.
   */
  final case class Rename(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {
    def reverse: MigrationAction = Rename(at, to, from)

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      def renameInRecord(r: DynamicValue.Record): DynamicValue.Record = {
        val newFields = r.fields.map {
          case (name, v) if name == from => (to, v)
          case other                     => other
        }
        DynamicValue.Record(newFields)
      }

      if (at.nodes.isEmpty) {
        value match {
          case r: DynamicValue.Record =>
            if (r.fields.exists(_._1 == from)) Right(renameInRecord(r))
            else Left(MigrationError.ActionFailed("Rename", at, s"Field '$from' not found"))
          case _ =>
            Left(MigrationError.ActionFailed("Rename", at, s"Expected Record, got ${value.valueType}"))
        }
      } else {
        var found    = false
        val modified = value.modify(at) {
          case r: DynamicValue.Record =>
            found = true
            renameInRecord(r)
          case other =>
            other // Don't set found=true for non-Record — this is intentional
        }
        if (found) Right(modified)
        else Left(MigrationError.PathNotFound(at))
      }
    }
  }

  /**
   * Transforms the value at a field by replacing it with a computed value. Both
   * `transform` and `reverseTransform` are DynamicValue constants for full
   * serializability.
   */
  final case class TransformValue(
    at: DynamicOptic,
    transform: DynamicValue,
    reverseTransform: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformValue(at, reverseTransform, transform)

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      val modified = value.set(at, transform)
      if (modified ne value) Right(modified)
      else Left(MigrationError.PathNotFound(at))
    }
  }

  /**
   * Makes an optional field mandatory, providing a default for None values. The
   * reverse action is [[Optionalize]].
   */
  final case class Mandate(
    at: DynamicOptic,
    default: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = Optionalize(at, default)

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      val modified = value.modify(at) {
        case DynamicValue.Null                                    => default
        case v: DynamicValue.Variant if v.caseNameValue == "None" => default
        case v: DynamicValue.Variant if v.caseNameValue == "Some" => v.value
        case other                                                => other
      }
      if (modified ne value) Right(modified)
      else Left(MigrationError.PathNotFound(at))
    }
  }

  /**
   * Makes a mandatory field optional (wraps in Some). The reverse action is
   * [[Mandate]] with the stored default.
   */
  final case class Optionalize(
    at: DynamicOptic,
    defaultForReverse: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = Mandate(at, defaultForReverse)

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      val modified = value.modify(at) {
        case DynamicValue.Null => DynamicValue.Null
        case v                 => DynamicValue.Variant("Some", v)
      }
      if (modified ne value) Right(modified)
      else Left(MigrationError.PathNotFound(at))
    }
  }

  /**
   * Changes the type of a value at the specified path by replacing it with a
   * pre-computed converted value.
   */
  final case class ChangeType(
    at: DynamicOptic,
    converter: DynamicValue,
    reverseConverter: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = ChangeType(at, reverseConverter, converter)

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      val modified = value.set(at, converter)
      if (modified ne value) Right(modified)
      else Left(MigrationError.PathNotFound(at))
    }
  }

  /**
   * Joins multiple source fields into a single target field using a combiner
   * value. The reverse is [[Split]].
   */
  final case class Join(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = Split(at, sourcePaths, combiner)

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      // Join sets the target path to the combiner value
      // (the combiner is a pre-computed DynamicValue constant)
      if (at.nodes.isEmpty) {
        // Replace root — unusual but supported
        Right(combiner)
      } else {
        val modified = value.set(at, combiner)
        if (modified ne value) Right(modified)
        else {
          // Path doesn't exist yet — insert it
          val inserted = value.insert(at, combiner)
          if (inserted ne value) Right(inserted)
          else Left(MigrationError.PathNotFound(at))
        }
      }
  }

  /**
   * Splits a single field into multiple target fields using a splitter value.
   * The reverse is [[Join]].
   */
  final case class Split(
    at: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = Join(at, targetPaths, splitter)

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      // Split sets the source path to the splitter value
      // (the splitter is a pre-computed DynamicValue constant)
      if (at.nodes.isEmpty) {
        Right(splitter)
      } else {
        val modified = value.set(at, splitter)
        if (modified ne value) Right(modified)
        else {
          val inserted = value.insert(at, splitter)
          if (inserted ne value) Right(inserted)
          else Left(MigrationError.PathNotFound(at))
        }
      }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Enum Actions
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Renames a case in a variant/enum at the specified path.
   */
  final case class RenameCase(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(at, to, from)

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      def renameCaseInVariant(dv: DynamicValue): DynamicValue = dv match {
        case v: DynamicValue.Variant if v.caseNameValue == from =>
          DynamicValue.Variant(to, v.value)
        case other => other
      }

      if (at.nodes.isEmpty) {
        val result = renameCaseInVariant(value)
        if (result ne value) Right(result)
        else Left(MigrationError.ActionFailed("RenameCase", at, s"Case '$from' not found"))
      } else {
        var found    = false
        val modified = value.modify(at) { dv =>
          val result = renameCaseInVariant(dv)
          if (result ne dv) found = true
          result
        }
        if (found) Right(modified)
        else Left(MigrationError.ActionFailed("RenameCase", at, s"Case '$from' not found at path"))
      }
    }
  }

  /**
   * Transforms a specific case in a variant by applying nested migration
   * actions to the case value.
   */
  final case class TransformCase(
    at: DynamicOptic,
    caseName: String,
    actions: Vector[MigrationAction]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformCase(at, caseName, actions.reverse.map(_.reverse))

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      def transformCaseValue(dv: DynamicValue): Either[MigrationError, DynamicValue] = dv match {
        case v: DynamicValue.Variant if v.caseNameValue == caseName =>
          var current: DynamicValue = v.value
          var error: MigrationError = null
          val iter                  = actions.iterator
          while (iter.hasNext && (error eq null)) {
            iter.next().apply(current) match {
              case Right(next) => current = next
              case Left(err)   => error = err
            }
          }
          if (error ne null) Left(error)
          else Right(DynamicValue.Variant(caseName, current))
        case other => Right(other)
      }

      if (at.nodes.isEmpty) {
        transformCaseValue(value)
      } else {
        var result: Either[MigrationError, DynamicValue] = null
        val modified                                     = value.modify(at) { dv =>
          transformCaseValue(dv) match {
            case Right(v) =>
              result = Right(v)
              v
            case Left(err) =>
              result = Left(err)
              dv
          }
        }
        if (result ne null) result.map(_ => modified)
        else Left(MigrationError.PathNotFound(at))
      }
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Collection / Map Actions
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Transforms all elements in a sequence at the specified path.
   */
  final case class TransformElements(
    at: DynamicOptic,
    elementActions: Vector[MigrationAction]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformElements(at, elementActions.reverse.map(_.reverse))

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      def transformSeq(dv: DynamicValue): Either[MigrationError, DynamicValue] = dv match {
        case s: DynamicValue.Sequence =>
          var error: MigrationError = null
          val newElements           = s.elements.map { elem =>
            if (error ne null) elem
            else {
              var current = elem
              val iter    = elementActions.iterator
              while (iter.hasNext && (error eq null)) {
                iter.next().apply(current) match {
                  case Right(next) => current = next
                  case Left(err)   => error = err
                }
              }
              current
            }
          }
          if (error ne null) Left(error)
          else Right(DynamicValue.Sequence(newElements))
        case _ => Left(MigrationError.ActionFailed("TransformElements", at, "Expected Sequence"))
      }

      if (at.nodes.isEmpty) transformSeq(value)
      else {
        var result: Either[MigrationError, DynamicValue] = null
        val modified                                     = value.modify(at) { dv =>
          transformSeq(dv) match {
            case Right(v) =>
              result = Right(v)
              v
            case Left(err) =>
              result = Left(err)
              dv
          }
        }
        if (result ne null) result.map(_ => modified)
        else Left(MigrationError.PathNotFound(at))
      }
    }
  }

  /**
   * Transforms all keys in a map at the specified path.
   */
  final case class TransformKeys(
    at: DynamicOptic,
    keyActions: Vector[MigrationAction]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformKeys(at, keyActions.reverse.map(_.reverse))

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      def transformMap(dv: DynamicValue): Either[MigrationError, DynamicValue] = dv match {
        case m: DynamicValue.Map =>
          var error: MigrationError = null
          val newEntries            = m.entries.map { case (k, v) =>
            if (error ne null) (k, v)
            else {
              var current = k
              val iter    = keyActions.iterator
              while (iter.hasNext && (error eq null)) {
                iter.next().apply(current) match {
                  case Right(next) => current = next
                  case Left(err)   => error = err
                }
              }
              (current, v)
            }
          }
          if (error ne null) Left(error)
          else Right(DynamicValue.Map(newEntries))
        case _ => Left(MigrationError.ActionFailed("TransformKeys", at, "Expected Map"))
      }

      if (at.nodes.isEmpty) transformMap(value)
      else {
        var result: Either[MigrationError, DynamicValue] = null
        val modified                                     = value.modify(at) { dv =>
          transformMap(dv) match {
            case Right(v) =>
              result = Right(v)
              v
            case Left(err) =>
              result = Left(err)
              dv
          }
        }
        if (result ne null) result.map(_ => modified)
        else Left(MigrationError.PathNotFound(at))
      }
    }
  }

  /**
   * Transforms all values in a map at the specified path.
   */
  final case class TransformValues(
    at: DynamicOptic,
    valueActions: Vector[MigrationAction]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformValues(at, valueActions.reverse.map(_.reverse))

    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      def transformMap(dv: DynamicValue): Either[MigrationError, DynamicValue] = dv match {
        case m: DynamicValue.Map =>
          var error: MigrationError = null
          val newEntries            = m.entries.map { case (k, v) =>
            if (error ne null) (k, v)
            else {
              var current = v
              val iter    = valueActions.iterator
              while (iter.hasNext && (error eq null)) {
                iter.next().apply(current) match {
                  case Right(next) => current = next
                  case Left(err)   => error = err
                }
              }
              (k, current)
            }
          }
          if (error ne null) Left(error)
          else Right(DynamicValue.Map(newEntries))
        case _ => Left(MigrationError.ActionFailed("TransformValues", at, "Expected Map"))
      }

      if (at.nodes.isEmpty) transformMap(value)
      else {
        var result: Either[MigrationError, DynamicValue] = null
        val modified                                     = value.modify(at) { dv =>
          transformMap(dv) match {
            case Right(v) =>
              result = Right(v)
              v
            case Left(err) =>
              result = Left(err)
              dv
          }
        }
        if (result ne null) result.map(_ => modified)
        else Left(MigrationError.PathNotFound(at))
      }
    }
  }
}
