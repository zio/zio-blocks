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
import zio.blocks.schema.{DynamicValue, PrimitiveValue, SchemaError}

/**
 * A pure-data, fully serializable schema migration from type `A` to type `B`.
 *
 * A [[Migration]] stores its logic as a sequence of [[MigrationAction]] values
 * — no opaque functions. This means migrations can be inspected, serialized,
 * and, in principle, composed or inverted.
 *
 * Construct migrations via [[MigrationBuilder]], which enforces at compile time
 * that every field in the source schema has been accounted for and every field
 * in the target schema has been provided.
 *
 * @tparam A
 *   source type
 * @tparam B
 *   target type
 * @param actions
 *   pure-data instruction list applied left-to-right
 */
final case class Migration[A, B](actions: List[MigrationAction]) {

  /**
   * Apply this migration to a [[DynamicValue]] and return the migrated
   * [[DynamicValue]] or a [[SchemaError]] if the value does not conform to the
   * expected structure.
   */
  def apply(dv: DynamicValue): Either[SchemaError, DynamicValue] =
    Migration.interpret(dv, actions)

  /**
   * Compose this migration with another migration `m` from `B` to `C`,
   * producing a migration from `A` to `C`.
   */
  def andThen[C](m: Migration[B, C]): Migration[A, C] =
    Migration(actions ++ m.actions)
}

object Migration {

  /** A migration that leaves the value unchanged. */
  def identity[A]: Migration[A, A] = Migration(List(MigrationAction.Identity))

  // ── Interpreter ─────────────────────────────────────────────────────────────

  private[migration] def interpret(
    dv: DynamicValue,
    actions: List[MigrationAction]
  ): Either[SchemaError, DynamicValue] =
    actions match {
      case Nil          => Right(dv)
      case head :: tail =>
        applyAction(dv, head) match {
          case Right(updated) => interpret(updated, tail)
          case left           => left
        }
    }

  private[migration] def applyAction(
    dv: DynamicValue,
    action: MigrationAction
  ): Either[SchemaError, DynamicValue] =
    action match {

      case MigrationAction.Identity => Right(dv)

      // ── Record field operations ─────────────────────────────────────────

      case MigrationAction.DropField(path) =>
        if (path.isEmpty) Left(SchemaError.expectationMismatch(Nil, "DropField path must not be empty"))
        else
          navigateAndModify(
            dv,
            path.init,
            {
              case r: DynamicValue.Record =>
                Right(DynamicValue.Record(r.fields.filter(_._1 != path.last)))
              case _ =>
                Left(SchemaError.expectationMismatch(Nil, "DropField: expected a record at path"))
            }
          )

      case MigrationAction.AddField(path, value) =>
        if (path.isEmpty) Left(SchemaError.expectationMismatch(Nil, "AddField path must not be empty"))
        else
          navigateAndModify(
            dv,
            path.init,
            {
              case r: DynamicValue.Record =>
                Right(DynamicValue.Record(r.fields :+ (path.last -> value)))
              case _ =>
                Left(SchemaError.expectationMismatch(Nil, "AddField: expected a record at path"))
            }
          )

      case MigrationAction.RenameField(srcPath, tgtPath) =>
        if (srcPath.isEmpty || tgtPath.isEmpty)
          Left(SchemaError.expectationMismatch(Nil, "RenameField paths must not be empty"))
        else {
          val srcName = srcPath.last
          val tgtName = tgtPath.last
          navigateAndModify(
            dv,
            srcPath.init,
            {
              case r: DynamicValue.Record =>
                var found   = false
                val errOpt  = Option.empty[SchemaError]
                val renamed = r.fields.map { kv =>
                  if (kv._1 == srcName && !found) { found = true; (tgtName, kv._2) }
                  else kv
                }
                if (!found) Left(SchemaError.missingField(Nil, srcName))
                else if (errOpt.isDefined) Left(errOpt.get)
                else Right(DynamicValue.Record(renamed))
              case _ =>
                Left(SchemaError.expectationMismatch(Nil, "RenameField: expected a record at path"))
            }
          )
        }

      case MigrationAction.TransformValue(path, transform) =>
        navigateAndModify(dv, path, v => applyFieldTransform(v, transform))

      // ── Variant case operations ─────────────────────────────────────────

      case MigrationAction.RenameCase(srcPath, tgtPath) =>
        if (srcPath.isEmpty || tgtPath.isEmpty)
          Left(SchemaError.expectationMismatch(Nil, "RenameCase paths must not be empty"))
        else {
          val srcCase = srcPath.last
          val tgtCase = tgtPath.last
          navigateAndModify(
            dv,
            srcPath.init,
            {
              case v: DynamicValue.Variant if v.caseNameValue == srcCase =>
                Right(DynamicValue.Variant(tgtCase, v.value))
              case v: DynamicValue.Variant => Right(v) // different case — no-op
              case _                       =>
                Left(SchemaError.expectationMismatch(Nil, "RenameCase: expected a variant at path"))
            }
          )
        }

      case MigrationAction.DropCase(casePath) =>
        if (casePath.isEmpty)
          Left(SchemaError.expectationMismatch(Nil, "DropCase path must not be empty"))
        else {
          val caseName = casePath.last
          navigateAndModify(
            dv,
            casePath.init,
            {
              case v: DynamicValue.Variant if v.caseNameValue == caseName =>
                Left(SchemaError.expectationMismatch(Nil, s"Case '$caseName' is no longer supported"))
              case v: DynamicValue.Variant => Right(v) // different case — no-op
              case _                       =>
                Left(SchemaError.expectationMismatch(Nil, "DropCase: expected a variant at path"))
            }
          )
        }

      // ── Option operations ───────────────────────────────────────────────

      case MigrationAction.Mandate(path, default) =>
        navigateAndModify(
          dv,
          path,
          {
            case v: DynamicValue.Variant if v.caseNameValue == "None" => Right(default)
            case v: DynamicValue.Variant if v.caseNameValue == "Some" =>
              v.value match {
                case r: DynamicValue.Record =>
                  r.fields.find(_._1 == "value") match {
                    case Some((_, inner)) => Right(inner)
                    case None             => Left(SchemaError.missingField(Nil, "value"))
                  }
                case other => Right(other)
              }
            case other => Right(other) // not an Option — pass through
          }
        )

      case MigrationAction.Optionalize(path) =>
        navigateAndModify(
          dv,
          path,
          v => Right(DynamicValue.Variant("Some", DynamicValue.Record(Chunk.single(("value", v)))))
        )

      // ── Collection operations ───────────────────────────────────────────

      case MigrationAction.TransformElements(path, elemAction) =>
        navigateAndModify(
          dv,
          path,
          {
            case s: DynamicValue.Sequence =>
              s.elements
                .foldLeft[Either[SchemaError, Chunk[DynamicValue]]](Right(Chunk.empty)) { (acc, elem) =>
                  acc.flatMap(chunk => applyAction(elem, elemAction).map(chunk :+ _))
                }
                .map(DynamicValue.Sequence(_))
            case _ =>
              Left(SchemaError.expectationMismatch(Nil, "TransformElements: expected a sequence at path"))
          }
        )

      case MigrationAction.TransformKeys(path, keyAction) =>
        navigateAndModify(
          dv,
          path,
          {
            case m: DynamicValue.Map =>
              m.entries
                .foldLeft[Either[SchemaError, Chunk[(DynamicValue, DynamicValue)]]](Right(Chunk.empty)) { (acc, kv) =>
                  acc.flatMap(chunk => applyAction(kv._1, keyAction).map(k => chunk :+ (k, kv._2)))
                }
                .map(DynamicValue.Map(_))
            case _ =>
              Left(SchemaError.expectationMismatch(Nil, "TransformKeys: expected a map at path"))
          }
        )

      case MigrationAction.TransformValues(path, valAction) =>
        navigateAndModify(
          dv,
          path,
          {
            case m: DynamicValue.Map =>
              m.entries
                .foldLeft[Either[SchemaError, Chunk[(DynamicValue, DynamicValue)]]](Right(Chunk.empty)) { (acc, kv) =>
                  acc.flatMap(chunk => applyAction(kv._2, valAction).map(v => chunk :+ (kv._1, v)))
                }
                .map(DynamicValue.Map(_))
            case _ =>
              Left(SchemaError.expectationMismatch(Nil, "TransformValues: expected a map at path"))
          }
        )

      // ── Composition ─────────────────────────────────────────────────────

      case MigrationAction.Sequence(subActions) => interpret(dv, subActions)
    }

  // ── Navigation helper ────────────────────────────────────────────────────────

  /**
   * Descend through `path` (a sequence of record-field names), apply `f` at the
   * innermost value, and rebuild the enclosing records on the way out.
   */
  private[migration] def navigateAndModify(
    dv: DynamicValue,
    path: List[String],
    f: DynamicValue => Either[SchemaError, DynamicValue]
  ): Either[SchemaError, DynamicValue] =
    path match {
      case Nil          => f(dv)
      case head :: tail =>
        dv match {
          case r: DynamicValue.Record =>
            var found   = false
            var result  = Option.empty[Either[SchemaError, DynamicValue]]
            val updated = r.fields.map { kv =>
              if (kv._1 == head && !found) {
                found = true
                navigateAndModify(kv._2, tail, f) match {
                  case Right(v) => (kv._1, v)
                  case Left(e)  =>
                    result = Some(Left(e))
                    kv
                }
              } else kv
            }
            result match {
              case Some(err)      => err
              case None if !found =>
                Left(SchemaError.missingField(Nil, head))
              case None =>
                Right(DynamicValue.Record(updated))
            }
          case _ =>
            Left(SchemaError.expectationMismatch(Nil, s"Expected a record when navigating to field '$head'"))
        }
    }

  // ── FieldTransform interpreter ───────────────────────────────────────────────

  private[migration] def applyFieldTransform(
    dv: DynamicValue,
    transform: FieldTransform
  ): Either[SchemaError, DynamicValue] =
    transform match {

      case FieldTransform.IntToLong =>
        dv match {
          case DynamicValue.Primitive(PrimitiveValue.Int(n)) =>
            Right(DynamicValue.Primitive(new PrimitiveValue.Long(n.toLong)))
          case _ => Left(SchemaError.expectationMismatch(Nil, "IntToLong: expected an Int primitive"))
        }

      case FieldTransform.LongToInt =>
        dv match {
          case DynamicValue.Primitive(PrimitiveValue.Long(n)) =>
            Right(DynamicValue.Primitive(new PrimitiveValue.Int(n.toInt)))
          case _ => Left(SchemaError.expectationMismatch(Nil, "LongToInt: expected a Long primitive"))
        }

      case FieldTransform.IntToDouble =>
        dv match {
          case DynamicValue.Primitive(PrimitiveValue.Int(n)) =>
            Right(DynamicValue.Primitive(new PrimitiveValue.Double(n.toDouble)))
          case _ => Left(SchemaError.expectationMismatch(Nil, "IntToDouble: expected an Int primitive"))
        }

      case FieldTransform.LongToDouble =>
        dv match {
          case DynamicValue.Primitive(PrimitiveValue.Long(n)) =>
            Right(DynamicValue.Primitive(new PrimitiveValue.Double(n.toDouble)))
          case _ => Left(SchemaError.expectationMismatch(Nil, "LongToDouble: expected a Long primitive"))
        }

      case FieldTransform.IntToString(radix) =>
        dv match {
          case DynamicValue.Primitive(PrimitiveValue.Int(n)) =>
            Right(DynamicValue.Primitive(new PrimitiveValue.String(java.lang.Integer.toString(n, radix))))
          case _ => Left(SchemaError.expectationMismatch(Nil, "IntToString: expected an Int primitive"))
        }

      case FieldTransform.StringToInt(radix) =>
        dv match {
          case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
            try Right(DynamicValue.Primitive(new PrimitiveValue.Int(java.lang.Integer.parseInt(s, radix))))
            catch {
              case _: NumberFormatException =>
                Left(SchemaError.expectationMismatch(Nil, s"StringToInt: cannot parse '$s' as Int (radix $radix)"))
            }
          case _ => Left(SchemaError.expectationMismatch(Nil, "StringToInt: expected a String primitive"))
        }

      case FieldTransform.Constant(value) => Right(value)
    }
}
