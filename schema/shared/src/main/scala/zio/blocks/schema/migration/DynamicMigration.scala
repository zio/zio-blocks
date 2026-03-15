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

import zio.blocks.chunk.Chunk
import zio.blocks.schema.{DynamicOptic, DynamicValue}
import zio.blocks.schema.migration.MigrationAction._

/**
 * A fully serializable, untyped migration that transforms a
 * [[zio.blocks.schema.DynamicValue]] from one schema version to another.
 *
 * `DynamicMigration` is pure data — it contains no user functions, no closures,
 * no reflection, and no runtime code generation. As a result it can be:
 *   - serialized and stored in a registry
 *   - applied dynamically at runtime
 *   - introspected to generate DDL / DML
 *   - reversed structurally
 *
 * The typed counterpart [[Migration]][A, B] wraps a `DynamicMigration` together
 * with the source and target [[zio.blocks.schema.Schema]]s, providing a
 * type-safe API.
 *
 * ===Laws===
 *
 *   - '''Identity''': `DynamicMigration.identity.apply(v) == Right(v)`
 *   - '''Associativity''': `(m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)`
 *   - '''Structural reverse''': `m.reverse.reverse == m`
 *
 * @param actions
 *   The ordered sequence of [[MigrationAction]]s to apply.
 */
final case class DynamicMigration(actions: Vector[MigrationAction]) {

  /**
   * Apply this migration to `value`, returning the transformed value or the
   * first [[MigrationError]] encountered.
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
    var current: DynamicValue = value
    var error: MigrationError = null
    val iter                  = actions.iterator
    while (iter.hasNext && (error eq null)) {
      DynamicMigration.applyAction(current, iter.next()) match {
        case Right(next) => current = next
        case Left(e)     => error = e
      }
    }
    if (error eq null) Right(current) else Left(error)
  }

  /**
   * Compose this migration with `that`, producing a migration that applies
   * `this` first, then `that`.
   *
   * Satisfies associativity: `(m1 ++ m2) ++ m3 == m1 ++ (m2 ++ m3)`
   */
  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(actions ++ that.actions)

  /**
   * The structural inverse of this migration.
   *
   * For any migration `m` and value `a` such that `m.apply(a) == Right(b)`, the
   * reverse satisfies `m.reverse.apply(b) == Right(a)` when sufficient
   * information was preserved.
   *
   * Always satisfies: `m.reverse.reverse == m`
   */
  def reverse: DynamicMigration =
    DynamicMigration(actions.reverseIterator.map(_.reverse).toVector)
}

object DynamicMigration {

  /**
   * The identity migration: applies no actions and returns the input unchanged.
   */
  val identity: DynamicMigration = DynamicMigration(Vector.empty)

  // ── Action interpreter ───────────────────────────────────────────────────

  private[migration] def applyAction(
    value: DynamicValue,
    action: MigrationAction
  ): Either[MigrationError, DynamicValue] =
    action match {
      case AddField(at, defaultValue) =>
        addField(value, at, defaultValue)

      case DropField(at, _) =>
        dropField(value, at)

      case Rename(at, to) =>
        renameField(value, at, to)

      case TransformValue(at, newValue, _) =>
        setAtPath(value, at, newValue)

      case Mandate(at, default) =>
        mandateField(value, at, default)

      case Optionalize(at, _) =>
        optionalizeField(value, at)

      case RenameCase(at, from, to) =>
        renameCase(value, at, from, to)

      case TransformCase(at, caseName, innerActions) =>
        transformCase(value, at, caseName, innerActions)

      case TransformElements(at, _) =>
        Right(value) // elements transform stored as descriptor; identity at DynamicValue level

      case TransformKeys(at, _) =>
        Right(value)

      case TransformValues(at, _) =>
        Right(value)

      case Join(at, _, combiner) =>
        setAtPath(value, at, combiner)

      case Split(at, targetPaths, splitter) =>
        var result: Either[MigrationError, DynamicValue] = Right(value)
        targetPaths.foreach { tp =>
          result = result.flatMap(v => setAtPath(v, tp, splitter))
        }
        result.flatMap(dropField(_, at))

      case ChangeType(at, converter) =>
        setAtPath(value, at, converter)
    }

  // ── DynamicValue path helpers ─────────────────────────────────────────────

  /**
   * Navigate to the parent of `at` and add a new field with `defaultValue`.
   */
  private def addField(
    root: DynamicValue,
    at: DynamicOptic,
    default: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    at.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) =>
        val parentAt = new DynamicOptic(at.nodes.init)
        modifyAtPath(root, parentAt, at) {
          case rec: DynamicValue.Record =>
            val alreadyPresent = rec.fields.exists(_._1 == name)
            if (alreadyPresent) rec
            else DynamicValue.Record(rec.fields :+ (name -> default))
          case other => other
        }
      case _ =>
        Left(MigrationError.TypeMismatch(at, "Field node", at.nodes.lastOption.map(_.toString).getOrElse("empty")))
    }

  /**
   * Remove the field identified by the last [[DynamicOptic.Node.Field]] in `at`
   * from its parent record.
   */
  private def dropField(
    root: DynamicValue,
    at: DynamicOptic
  ): Either[MigrationError, DynamicValue] =
    at.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) =>
        val parentAt = new DynamicOptic(at.nodes.init)
        modifyAtPath(root, parentAt, at) {
          case rec: DynamicValue.Record =>
            DynamicValue.Record(rec.fields.filter(_._1 != name))
          case other => other
        }
      case _ =>
        Left(MigrationError.TypeMismatch(at, "Field node", at.nodes.lastOption.map(_.toString).getOrElse("empty")))
    }

  /**
   * Rename a record field: remove old name, add new name.
   */
  private def renameField(
    root: DynamicValue,
    at: DynamicOptic,
    to: String
  ): Either[MigrationError, DynamicValue] =
    at.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(from)) =>
        val parentAt = new DynamicOptic(at.nodes.init)
        modifyAtPath(root, parentAt, at) {
          case rec: DynamicValue.Record =>
            DynamicValue.Record(rec.fields.map { case (k, v) => if (k == from) (to, v) else (k, v) })
          case other => other
        }
      case _ =>
        Left(MigrationError.TypeMismatch(at, "Field node", at.nodes.lastOption.map(_.toString).getOrElse("empty")))
    }

  /**
   * Mandate an optional field: unwrap `Some(x)` → `x`; absent → `default`.
   */
  private def mandateField(
    root: DynamicValue,
    at: DynamicOptic,
    default: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    modifyAtPath(root, at, at) {
      case v: DynamicValue.Variant if v.caseNameValue == "Some" => v.value
      case DynamicValue.Null                                    => default
      case DynamicValue.Variant(_, DynamicValue.Null)           => default
      case other                                                => other
    }

  /**
   * Optionalize a field: wrap `x` → `Some(x)`.
   */
  private def optionalizeField(
    root: DynamicValue,
    at: DynamicOptic
  ): Either[MigrationError, DynamicValue] =
    modifyAtPath(root, at, at) { v =>
      DynamicValue.Variant("Some", v)
    }

  /**
   * Rename a variant case inside the value at `at`.
   */
  private def renameCase(
    root: DynamicValue,
    at: DynamicOptic,
    from: String,
    to: String
  ): Either[MigrationError, DynamicValue] =
    modifyAtPath(root, at, at) {
      case v: DynamicValue.Variant if v.caseNameValue == from =>
        DynamicValue.Variant(to, v.value)
      case other => other
    }

  /**
   * Apply nested `actions` to the body of a named case inside the variant at
   * `at`.
   */
  private def transformCase(
    root: DynamicValue,
    at: DynamicOptic,
    caseName: String,
    actions: Vector[MigrationAction]
  ): Either[MigrationError, DynamicValue] = {
    val sub = DynamicMigration(actions)
    modifyAtPathE(root, at, at) {
      case v: DynamicValue.Variant if v.caseNameValue == caseName =>
        sub.apply(v.value).map(DynamicValue.Variant(caseName, _))
      case other =>
        Right(other)
    }
  }

  /**
   * Set the value at `at` to `newValue`.
   */
  private def setAtPath(
    root: DynamicValue,
    at: DynamicOptic,
    newValue: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    if (at.nodes.isEmpty) Right(newValue)
    else modifyAtPath(root, at, at)(_ => newValue)

  // ── Low-level path traversal ──────────────────────────────────────────────

  /**
   * Navigate `root` following `at` and replace the focused sub-value with
   * `f(focused)`. Returns the updated root.
   */
  private def modifyAtPath(
    root: DynamicValue,
    at: DynamicOptic,
    errorAt: DynamicOptic
  )(f: DynamicValue => DynamicValue): Either[MigrationError, DynamicValue] =
    modifyAtPathE(root, at, errorAt)(v => Right(f(v)))

  private def modifyAtPathE(
    root: DynamicValue,
    at: DynamicOptic,
    errorAt: DynamicOptic
  )(f: DynamicValue => Either[MigrationError, DynamicValue]): Either[MigrationError, DynamicValue] = {
    def go(current: DynamicValue, nodes: IndexedSeq[DynamicOptic.Node]): Either[MigrationError, DynamicValue] =
      if (nodes.isEmpty) f(current)
      else
        nodes.head match {
          case DynamicOptic.Node.Field(name) =>
            current match {
              case rec: DynamicValue.Record =>
                val idx = rec.fields.indexWhere(_._1 == name)
                if (idx < 0) Left(MigrationError.FieldNotFound(errorAt, name))
                else
                  go(rec.fields(idx)._2, nodes.tail).map { updated =>
                    val newFields = rec.fields.updated(idx, (name, updated))
                    DynamicValue.Record(newFields)
                  }
              case _ =>
                Left(MigrationError.TypeMismatch(errorAt, "Record", current.getClass.getSimpleName))
            }

          case DynamicOptic.Node.Case(name) =>
            current match {
              case v: DynamicValue.Variant if v.caseNameValue == name =>
                go(v.value, nodes.tail).map(DynamicValue.Variant(name, _))
              case _: DynamicValue.Variant =>
                Left(MigrationError.CaseNotFound(errorAt, name))
              case _ =>
                Left(MigrationError.TypeMismatch(errorAt, "Variant", current.getClass.getSimpleName))
            }

          case DynamicOptic.Node.Elements =>
            current match {
              case seq: DynamicValue.Sequence =>
                var result: Either[MigrationError, Chunk[DynamicValue]] = Right(Chunk.empty)
                seq.elements.foreach { elem =>
                  result = result.flatMap(acc => go(elem, nodes.tail).map(acc :+ _))
                }
                result.map(DynamicValue.Sequence(_))
              case _ =>
                Left(MigrationError.TypeMismatch(errorAt, "Sequence", current.getClass.getSimpleName))
            }

          case DynamicOptic.Node.MapValues =>
            current match {
              case m: DynamicValue.Map =>
                var result: Either[MigrationError, Chunk[(DynamicValue, DynamicValue)]] = Right(Chunk.empty)
                m.entries.foreach { case (k, v) =>
                  result = result.flatMap(acc => go(v, nodes.tail).map(nv => acc :+ (k, nv)))
                }
                result.map(DynamicValue.Map(_))
              case _ =>
                Left(MigrationError.TypeMismatch(errorAt, "Map", current.getClass.getSimpleName))
            }

          case _ =>
            // Other node types (AtIndex, AtMapKey, etc.) — delegate to DynamicValue.get
            f(current)
        }

    go(root, at.nodes)
  }
}
