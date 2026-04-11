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
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveType, PrimitiveValue}

// ─────────────────────────────────────────────────────────────────────────────
// MigrationError
// ─────────────────────────────────────────────────────────────────────────────

/**
 * An error produced when interpreting a [[DynamicMigration]] against a
 * [[DynamicValue]].
 *
 * @param message
 *   A human-readable description of what went wrong.
 * @param path
 *   The [[DynamicOptic]] path at which the failure occurred. Defaults to
 *   [[DynamicOptic.root]] when the error is not path-specific.
 */
final case class MigrationError(message: String, path: DynamicOptic)

object MigrationError {
  def apply(message: String): MigrationError = new MigrationError(message, DynamicOptic.root)

}

// ─────────────────────────────────────────────────────────────────────────────
// DynamicMigration
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A fully serializable, schema-agnostic migration pipeline operating on
 * [[DynamicValue]].
 *
 * `DynamicMigration` is a sequence of [[MigrationAction]]s that are applied
 * left-to-right to transform a source [[DynamicValue]] into a target
 * [[DynamicValue]]. Because all actions are pure data (no closures, no
 * reflection), the migration itself can be serialized, stored, and replayed.
 *
 * The typed companion [[zio.blocks.schema.migration.Migration]] wraps a
 * `DynamicMigration` with source and target [[zio.blocks.schema.Schema]]s,
 * providing a type-safe interface. Use `DynamicMigration` directly when you
 * need schema-agnostic transformation or when working with external serialized
 * migrations.
 *
 * ==Laws==
 *   - Identity: `DynamicMigration.identity.apply(v) == Right(v)` for all `v`
 *   - Associativity: `(m1 ++ m2) ++ m3` and `m1 ++ (m2 ++ m3)` produce the same
 *     result when applied to any value
 *   - Best-effort reverse: `m.reverse` produces a migration that approximately
 *     inverts `m`; structural reverses (renames, type changes) are exact, while
 *     lossy operations (drop, constant) cannot be inverted without information
 *     recovery
 */
final case class DynamicMigration(actions: Chunk[MigrationAction]) {

  /**
   * Appends all actions from `that` after the actions of this migration,
   * producing a new migration that applies both in sequence.
   */
  def ++(that: DynamicMigration): DynamicMigration =
    new DynamicMigration(this.actions ++ that.actions)

  /**
   * Applies all actions in sequence to `value`, short-circuiting on the first
   * [[MigrationError]].
   */
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
    actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) {
      case (Right(v), action) => applyAction(v, action)
      case (left, _)          => left
    }

  /**
   * Produces a best-effort structural inverse of this migration.
   *
   * Actions are reversed in order and each is individually inverted:
   *   - `AddField` ↔ `DropField`
   *   - `RenameField(path, newName)` ↔ `RenameField(newPath, oldName)`
   *   - `Mandate` ↔ `Optionalize`
   *   - `ChangeType(path, PrimitiveConvert(a, b))` ↔
   *     `ChangeType(path, PrimitiveConvert(b, a))`
   *   - `Join` ↔ `Split`
   *   - `RenameCase(at, a, b)` ↔ `RenameCase(at, b, a)`
   *   - Lossy operations (`DropField`, `Constant`) reverse to `DefaultValue`,
   *     which requires schema context to evaluate.
   */
  def reverse: DynamicMigration =
    new DynamicMigration(actions.reverse.map(reverseAction))

  // ─────────────────────────────────────────────────────────────────────────
  // Action interpreter
  // ─────────────────────────────────────────────────────────────────────────

  private def applyAction(
    value: DynamicValue,
    action: MigrationAction
  ): Either[MigrationError, DynamicValue] = action match {

    case MigrationAction.AddField(path, defaultExpr) =>
      evalUnaryExpr(defaultExpr, value).flatMap { dv =>
        value.insertOrFail(path, dv).left.map(e => MigrationError(e.message, path))
      }

    case MigrationAction.DropField(path) =>
      value.deleteOrFail(path).left.map(e => MigrationError(e.message, path))

    case MigrationAction.RenameField(path, newName) =>
      val nodes = path.nodes
      if (nodes.isEmpty)
        Left(MigrationError("RenameField: path must not be empty", path))
      else
        nodes.last match {
          case DynamicOptic.Node.Field(oldName) =>
            val parentPath                                                  = new DynamicOptic(nodes.dropRight(1))
            val renameInRecord: PartialFunction[DynamicValue, DynamicValue] = { case rec: DynamicValue.Record =>
              new DynamicValue.Record(rec.fields.map { case (n, v) =>
                if (n == oldName) (newName, v) else (n, v)
              })
            }
            if (parentPath.nodes.isEmpty)
              renameInRecord.lift(value) match {
                case Some(v) => Right(v)
                case None    => Left(MigrationError("RenameField: expected a Record at root", path))
              }
            else
              value.modifyOrFail(parentPath)(renameInRecord).left.map(e => MigrationError(e.message, parentPath))

          case node =>
            Left(
              MigrationError(
                s"RenameField: path must end in a Field node, got: $node",
                path
              )
            )
        }

    case MigrationAction.TransformValue(path, expr) =>
      for {
        focal  <- value.get(path).one.left.map(e => MigrationError(e.message, path))
        newVal <- evalUnaryExpr(expr, focal)
        result <- value.setOrFail(path, newVal).left.map(e => MigrationError(e.message, path))
      } yield result

    case MigrationAction.Mandate(path, defaultExpr) =>
      for {
        focal  <- value.get(path).one.left.map(e => MigrationError(e.message, path))
        newVal <- focal match {
                    // Some(x) is encoded as Variant("Some", Record(Chunk(("value", x))))
                    case DynamicValue.Variant("Some", inner: DynamicValue.Record) =>
                      inner.get("value").one.left.map(e => MigrationError(e.message, path))
                    // None is encoded as Variant("None", Record(Chunk.empty))
                    case DynamicValue.Variant("None", _) =>
                      evalUnaryExpr(defaultExpr, value)
                    case other =>
                      Left(
                        MigrationError(
                          s"Mandate: expected an Option variant (Some/None), got: ${other.valueType}",
                          path
                        )
                      )
                  }
        result <- value.setOrFail(path, newVal).left.map(e => MigrationError(e.message, path))
      } yield result

    case MigrationAction.Optionalize(path) =>
      for {
        focal <- value.get(path).one.left.map(e => MigrationError(e.message, path))
        some   = new DynamicValue.Variant(
                 "Some",
                 new DynamicValue.Record(Chunk.single(("value", focal)))
               )
        result <- value.setOrFail(path, some).left.map(e => MigrationError(e.message, path))
      } yield result

    case MigrationAction.ChangeType(path, expr) =>
      for {
        focal  <- value.get(path).one.left.map(e => MigrationError(e.message, path))
        newVal <- evalUnaryExpr(expr, focal)
        result <- value.setOrFail(path, newVal).left.map(e => MigrationError(e.message, path))
      } yield result

    case MigrationAction.Join(left, right, target, combiner) =>
      for {
        leftVal  <- value.get(left).one.left.map(e => MigrationError(e.message, left))
        rightVal <- value.get(right).one.left.map(e => MigrationError(e.message, right))
        combined <- evalBinaryExpr(combiner, leftVal, rightVal)
        // Delete both source fields before inserting the combined target
        afterLeft  <- value.deleteOrFail(left).left.map(e => MigrationError(e.message, left))
        afterRight <- afterLeft.deleteOrFail(right).left.map(e => MigrationError(e.message, right))
        result     <- afterRight.insertOrFail(target, combined).left.map(e => MigrationError(e.message, target))
      } yield result

    case MigrationAction.Split(from, toLeft, toRight, splitter) =>
      for {
        focal     <- value.get(from).one.left.map(e => MigrationError(e.message, from))
        pair      <- evalSplitExpr(splitter, focal)
        afterDrop <- value.deleteOrFail(from).left.map(e => MigrationError(e.message, from))
        afterLeft <- afterDrop.insertOrFail(toLeft, pair._1).left.map(e => MigrationError(e.message, toLeft))
        result    <- afterLeft.insertOrFail(toRight, pair._2).left.map(e => MigrationError(e.message, toRight))
      } yield result

    case MigrationAction.TransformElements(path, expr) =>
      for {
        focal  <- value.get(path).one.left.map(e => MigrationError(e.message, path))
        newSeq <- focal match {
                    case seq: DynamicValue.Sequence =>
                      seq.elements
                        .foldLeft[Either[MigrationError, Chunk[DynamicValue]]](Right(Chunk.empty)) {
                          case (Right(acc), elem) => evalUnaryExpr(expr, elem).map(acc :+ _)
                          case (left, _)          => left
                        }
                        .map(elems => new DynamicValue.Sequence(elems))
                    case other =>
                      Left(
                        MigrationError(
                          s"TransformElements: expected a Sequence, got: ${other.valueType}",
                          path
                        )
                      )
                  }
        result <- value.setOrFail(path, newSeq).left.map(e => MigrationError(e.message, path))
      } yield result

    case MigrationAction.TransformKeys(path, expr) =>
      for {
        focal  <- value.get(path).one.left.map(e => MigrationError(e.message, path))
        newMap <- focal match {
                    case m: DynamicValue.Map =>
                      m.entries
                        .foldLeft[Either[MigrationError, Chunk[(DynamicValue, DynamicValue)]]](Right(Chunk.empty)) {
                          case (Right(acc), (k, v)) => evalUnaryExpr(expr, k).map(nk => acc :+ ((nk, v)))
                          case (left, _)            => left
                        }
                        .map(entries => new DynamicValue.Map(entries))
                    case other =>
                      Left(
                        MigrationError(
                          s"TransformKeys: expected a Map, got: ${other.valueType}",
                          path
                        )
                      )
                  }
        result <- value.setOrFail(path, newMap).left.map(e => MigrationError(e.message, path))
      } yield result

    case MigrationAction.TransformValues(path, expr) =>
      for {
        focal  <- value.get(path).one.left.map(e => MigrationError(e.message, path))
        newMap <- focal match {
                    case m: DynamicValue.Map =>
                      m.entries
                        .foldLeft[Either[MigrationError, Chunk[(DynamicValue, DynamicValue)]]](Right(Chunk.empty)) {
                          case (Right(acc), (k, v)) => evalUnaryExpr(expr, v).map(nv => acc :+ ((k, nv)))
                          case (left, _)            => left
                        }
                        .map(entries => new DynamicValue.Map(entries))
                    case other =>
                      Left(
                        MigrationError(
                          s"TransformValues: expected a Map, got: ${other.valueType}",
                          path
                        )
                      )
                  }
        result <- value.setOrFail(path, newMap).left.map(e => MigrationError(e.message, path))
      } yield result

    case MigrationAction.RenameCase(at, fromName, toName) =>
      if (at.nodes.isEmpty) {
        value match {
          case v: DynamicValue.Variant if v.caseNameValue == fromName =>
            Right(new DynamicValue.Variant(toName, v.value))
          case _ =>
            Right(value)
        }
      } else {
        for {
          focal <- value.get(at).one.left.map(e => MigrationError(e.message, at))
          out <- focal match {
                   case v: DynamicValue.Variant if v.caseNameValue == fromName =>
                     Right(new DynamicValue.Variant(toName, v.value))
                   case _ =>
                     Right(focal)
                 }
          result <- value.setOrFail(at, out).left.map(e => MigrationError(e.message, at))
        } yield result
      }

    case MigrationAction.TransformCase(at, caseName, inner) =>
      if (at.nodes.isEmpty) {
        value match {
          case v: DynamicValue.Variant if v.caseNameValue == caseName =>
            inner
              .apply(v.value)
              .map(newVal => new DynamicValue.Variant(caseName, newVal))
              .left
              .map(e => MigrationError(s"TransformCase: ${e.message}", at))
          case _ =>
            Right(value)
        }
      } else {
        for {
          focal <- value.get(at).one.left.map(e => MigrationError(e.message, at))
          out <- focal match {
                   case v: DynamicValue.Variant if v.caseNameValue == caseName =>
                     inner
                       .apply(v.value)
                       .map(newVal => new DynamicValue.Variant(caseName, newVal))
                       .left
                       .map(e => MigrationError(s"TransformCase at ${at.toScalaString}: ${e.message}", at))
                   case _ =>
                     Right(focal)
                 }
          result <- value.setOrFail(at, out).left.map(e => MigrationError(e.message, at))
        } yield result
      }

    case MigrationAction.ApplyMigration(path, migration) =>
      for {
        focal  <- value.get(path).one.left.map(e => MigrationError(e.message, path))
        newVal <- migration
                    .apply(focal)
                    .left
                    .map(e => MigrationError(s"ApplyMigration at ${path.toScalaString}: ${e.message}", path))
        result <- value.setOrFail(path, newVal).left.map(e => MigrationError(e.message, path))
      } yield result

    case MigrationAction.CopyField(from, to) =>
      for {
        focal  <- value.get(from).one.left.map(e => MigrationError(e.message, from))
        result <- value.insertOrFail(to, focal).left.map(e => MigrationError(e.message, to))
      } yield result

    case MigrationAction.MoveField(from, to) =>
      for {
        focal     <- value.get(from).one.left.map(e => MigrationError(e.message, from))
        afterDrop <- value.deleteOrFail(from).left.map(e => MigrationError(e.message, from))
        result    <- afterDrop.insertOrFail(to, focal).left.map(e => MigrationError(e.message, to))
      } yield result
  }

  // ─────────────────────────────────────────────────────────────────────────
  // ValueExpr interpreters
  // ─────────────────────────────────────────────────────────────────────────

  /**
   * Evaluates a [[ValueExpr]] against a single focal [[DynamicValue]].
   *
   * `focal` is the value at the action's target path. For [[ValueExpr.Concat]]
   * and [[ValueExpr.StringSplit]] acting as unary transforms, see the notes on
   * each case below.
   */
  private def evalUnaryExpr(
    expr: ValueExpr,
    focal: DynamicValue
  ): Either[MigrationError, DynamicValue] = expr match {

    case ValueExpr.DefaultValue =>
      // DefaultValue has no meaning without a target Schema. Callers that
      // resolve defaults from Schema (i.e. Migration[A, B]) should substitute
      // a Constant before delegating to DynamicMigration.
      Left(
        MigrationError(
          "DefaultValue requires target schema context; " +
            "use Migration[A, B] instead of DynamicMigration directly"
        )
      )

    case ValueExpr.Constant(dv) =>
      Right(dv)

    case conv: ValueExpr.PrimitiveConvert =>
      focal match {
        case prim: DynamicValue.Primitive =>
          applyPrimitiveConvert(conv.from, conv.to, prim.value)
        case other =>
          Left(
            MigrationError(
              s"PrimitiveConvert: expected a Primitive value, got: ${other.valueType}"
            )
          )
      }

    case ValueExpr.Concat(_) =>
      Left(
        MigrationError(
          "Concat is a binary combiner; use it as the `combiner` of a Join action, not as a unary transform"
        )
      )

    case ValueExpr.StringSplit(sep) =>
      focal match {
        case DynamicValue.Primitive(sv: PrimitiveValue.String) =>
          val parts = sv.value.split(java.util.regex.Pattern.quote(sep), -1)
          Right(
            new DynamicValue.Sequence(
              Chunk.from(parts.map(p => new DynamicValue.Primitive(new PrimitiveValue.String(p)): DynamicValue))
            )
          )
        case other =>
          Left(
            MigrationError(
              s"StringSplit: expected a String primitive, got: ${other.valueType}"
            )
          )
      }
  }

  /**
   * Evaluates a [[ValueExpr]] as a binary combiner, producing one output from
   * two inputs. Used by [[MigrationAction.Join]].
   */
  private def evalBinaryExpr(
    expr: ValueExpr,
    left: DynamicValue,
    right: DynamicValue
  ): Either[MigrationError, DynamicValue] = expr match {

    case ValueExpr.Concat(sep) =>
      (left, right) match {
        case (DynamicValue.Primitive(lv: PrimitiveValue.String), DynamicValue.Primitive(rv: PrimitiveValue.String)) =>
          Right(new DynamicValue.Primitive(new PrimitiveValue.String(lv.value + sep + rv.value)))
        case _ =>
          Left(MigrationError("Concat: both source fields must be String primitives"))
      }

    case other =>
      Left(MigrationError(s"${other.getClass.getSimpleName} is not a supported binary combiner for Join"))
  }

  /**
   * Evaluates a [[ValueExpr]] as a splitter, producing two outputs from one
   * input. Used by [[MigrationAction.Split]].
   */
  private def evalSplitExpr(
    expr: ValueExpr,
    focal: DynamicValue
  ): Either[MigrationError, (DynamicValue, DynamicValue)] = expr match {

    case ValueExpr.StringSplit(sep) =>
      focal match {
        case DynamicValue.Primitive(sv: PrimitiveValue.String) =>
          val idx = sv.value.indexOf(sep)
          if (idx < 0) {
            // Separator not found: left = full string, right = empty string
            Right((focal, new DynamicValue.Primitive(new PrimitiveValue.String(""))))
          } else {
            Right(
              (
                new DynamicValue.Primitive(new PrimitiveValue.String(sv.value.substring(0, idx))),
                new DynamicValue.Primitive(new PrimitiveValue.String(sv.value.substring(idx + sep.length)))
              )
            )
          }
        case other =>
          Left(
            MigrationError(
              s"StringSplit splitter: expected a String primitive, got: ${other.valueType}"
            )
          )
      }

    case other =>
      Left(MigrationError(s"${other.getClass.getSimpleName} is not a supported splitter for Split"))
  }

  // ─────────────────────────────────────────────────────────────────────────
  // PrimitiveConvert interpreter
  // ─────────────────────────────────────────────────────────────────────────

  private def applyPrimitiveConvert(
    from: PrimitiveType[_],
    to: PrimitiveType[_],
    pv: PrimitiveValue
  ): Either[MigrationError, DynamicValue] = {

    def prim(v: PrimitiveValue): Either[MigrationError, DynamicValue] =
      Right(new DynamicValue.Primitive(v))

    def unsupported: Either[MigrationError, DynamicValue] =
      Left(
        MigrationError(
          s"Unsupported PrimitiveConvert: ${from.typeId.name} → ${to.typeId.name}"
        )
      )

    def parseOrFail[A](
      raw: String,
      parse: String => A,
      wrap: A => PrimitiveValue
    ): Either[MigrationError, DynamicValue] =
      try prim(wrap(parse(raw)))
      catch {
        case _: Exception =>
          Left(MigrationError(s"Cannot parse '$raw' as ${to.typeId.name}"))
      }

    pv match {
      // ── Byte ──────────────────────────────────────────────────────────────
      case v: PrimitiveValue.Byte =>
        to match {
          case _: PrimitiveType.Short      => prim(new PrimitiveValue.Short(v.value.toShort))
          case _: PrimitiveType.Int        => prim(new PrimitiveValue.Int(v.value.toInt))
          case _: PrimitiveType.Long       => prim(new PrimitiveValue.Long(v.value.toLong))
          case _: PrimitiveType.Float      => prim(new PrimitiveValue.Float(v.value.toFloat))
          case _: PrimitiveType.Double     => prim(new PrimitiveValue.Double(v.value.toDouble))
          case _: PrimitiveType.BigInt     => prim(new PrimitiveValue.BigInt(scala.math.BigInt(v.value.toInt)))
          case _: PrimitiveType.BigDecimal => prim(new PrimitiveValue.BigDecimal(scala.math.BigDecimal(v.value.toInt)))
          case _: PrimitiveType.String     => prim(new PrimitiveValue.String(v.value.toString))
          case _                           => unsupported
        }
      // ── Short ─────────────────────────────────────────────────────────────
      case v: PrimitiveValue.Short =>
        to match {
          case _: PrimitiveType.Int        => prim(new PrimitiveValue.Int(v.value.toInt))
          case _: PrimitiveType.Long       => prim(new PrimitiveValue.Long(v.value.toLong))
          case _: PrimitiveType.Float      => prim(new PrimitiveValue.Float(v.value.toFloat))
          case _: PrimitiveType.Double     => prim(new PrimitiveValue.Double(v.value.toDouble))
          case _: PrimitiveType.BigInt     => prim(new PrimitiveValue.BigInt(scala.math.BigInt(v.value.toInt)))
          case _: PrimitiveType.BigDecimal => prim(new PrimitiveValue.BigDecimal(scala.math.BigDecimal(v.value.toInt)))
          case _: PrimitiveType.String     => prim(new PrimitiveValue.String(v.value.toString))
          case _                           => unsupported
        }
      // ── Int ───────────────────────────────────────────────────────────────
      case v: PrimitiveValue.Int =>
        to match {
          case _: PrimitiveType.Long       => prim(new PrimitiveValue.Long(v.value.toLong))
          case _: PrimitiveType.Float      => prim(new PrimitiveValue.Float(v.value.toFloat))
          case _: PrimitiveType.Double     => prim(new PrimitiveValue.Double(v.value.toDouble))
          case _: PrimitiveType.BigInt     => prim(new PrimitiveValue.BigInt(scala.math.BigInt(v.value)))
          case _: PrimitiveType.BigDecimal => prim(new PrimitiveValue.BigDecimal(scala.math.BigDecimal(v.value)))
          case _: PrimitiveType.String     => prim(new PrimitiveValue.String(v.value.toString))
          case _: PrimitiveType.Char       => prim(new PrimitiveValue.Char(v.value.toChar))
          case _                           => unsupported
        }
      // ── Long ──────────────────────────────────────────────────────────────
      case v: PrimitiveValue.Long =>
        to match {
          case _: PrimitiveType.Float      => prim(new PrimitiveValue.Float(v.value.toFloat))
          case _: PrimitiveType.Double     => prim(new PrimitiveValue.Double(v.value.toDouble))
          case _: PrimitiveType.BigInt     => prim(new PrimitiveValue.BigInt(scala.math.BigInt(v.value)))
          case _: PrimitiveType.BigDecimal => prim(new PrimitiveValue.BigDecimal(scala.math.BigDecimal(v.value)))
          case _: PrimitiveType.String     => prim(new PrimitiveValue.String(v.value.toString))
          case _                           => unsupported
        }
      // ── Float ─────────────────────────────────────────────────────────────
      case v: PrimitiveValue.Float =>
        to match {
          case _: PrimitiveType.Double     => prim(new PrimitiveValue.Double(v.value.toDouble))
          case _: PrimitiveType.BigDecimal =>
            prim(new PrimitiveValue.BigDecimal(scala.math.BigDecimal(v.value.toDouble)))
          case _: PrimitiveType.String => prim(new PrimitiveValue.String(v.value.toString))
          case _                       => unsupported
        }
      // ── Double ────────────────────────────────────────────────────────────
      case v: PrimitiveValue.Double =>
        to match {
          case _: PrimitiveType.BigDecimal => prim(new PrimitiveValue.BigDecimal(scala.math.BigDecimal(v.value)))
          case _: PrimitiveType.String     => prim(new PrimitiveValue.String(v.value.toString))
          case _                           => unsupported
        }
      // ── BigInt ────────────────────────────────────────────────────────────
      case v: PrimitiveValue.BigInt =>
        to match {
          case _: PrimitiveType.BigDecimal => prim(new PrimitiveValue.BigDecimal(scala.math.BigDecimal(v.value)))
          case _: PrimitiveType.String     => prim(new PrimitiveValue.String(v.value.toString))
          case _                           => unsupported
        }
      // ── BigDecimal ────────────────────────────────────────────────────────
      case v: PrimitiveValue.BigDecimal =>
        to match {
          case _: PrimitiveType.String => prim(new PrimitiveValue.String(v.value.toString))
          case _                       => unsupported
        }
      // ── Boolean ───────────────────────────────────────────────────────────
      case v: PrimitiveValue.Boolean =>
        to match {
          case _: PrimitiveType.String => prim(new PrimitiveValue.String(v.value.toString))
          case _                       => unsupported
        }
      // ── Char ──────────────────────────────────────────────────────────────
      case v: PrimitiveValue.Char =>
        to match {
          case _: PrimitiveType.Int    => prim(new PrimitiveValue.Int(v.value.toInt))
          case _: PrimitiveType.String => prim(new PrimitiveValue.String(v.value.toString))
          case _                       => unsupported
        }
      // ── String (parse to numeric) ─────────────────────────────────────────
      case v: PrimitiveValue.String =>
        to match {
          case _: PrimitiveType.Byte       => parseOrFail(v.value, _.toByte, new PrimitiveValue.Byte(_))
          case _: PrimitiveType.Short      => parseOrFail(v.value, _.toShort, new PrimitiveValue.Short(_))
          case _: PrimitiveType.Int        => parseOrFail(v.value, _.toInt, new PrimitiveValue.Int(_))
          case _: PrimitiveType.Long       => parseOrFail(v.value, _.toLong, new PrimitiveValue.Long(_))
          case _: PrimitiveType.Float      => parseOrFail(v.value, _.toFloat, new PrimitiveValue.Float(_))
          case _: PrimitiveType.Double     => parseOrFail(v.value, _.toDouble, new PrimitiveValue.Double(_))
          case _: PrimitiveType.BigInt     => parseOrFail(v.value, scala.math.BigInt(_), new PrimitiveValue.BigInt(_))
          case _: PrimitiveType.BigDecimal =>
            parseOrFail(v.value, scala.math.BigDecimal(_), new PrimitiveValue.BigDecimal(_))
          case _: PrimitiveType.Boolean => parseOrFail(v.value, _.toBoolean, new PrimitiveValue.Boolean(_))
          case _                        => unsupported
        }
      case _ => unsupported
    }
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Reverse helpers
  // ─────────────────────────────────────────────────────────────────────────

  private def reverseAction(action: MigrationAction): MigrationAction = action match {

    case MigrationAction.AddField(path, _) =>
      MigrationAction.DropField(path)

    case MigrationAction.DropField(path) =>
      // Original value is lost; Migration[A, B] can supply the default from schema.
      MigrationAction.AddField(path, ValueExpr.DefaultValue)

    case MigrationAction.RenameField(path, newName) =>
      val nodes = path.nodes
      if (nodes.isEmpty) action // malformed; leave unchanged
      else
        nodes.last match {
          case DynamicOptic.Node.Field(oldName) =>
            val newPath = new DynamicOptic(nodes.dropRight(1) :+ new DynamicOptic.Node.Field(newName))
            MigrationAction.RenameField(newPath, oldName)
          case _ => action // malformed; leave unchanged
        }

    case MigrationAction.TransformValue(path, expr) =>
      MigrationAction.TransformValue(path, reverseExpr(expr))

    case MigrationAction.Mandate(path, _) =>
      MigrationAction.Optionalize(path)

    case MigrationAction.Optionalize(path) =>
      MigrationAction.Mandate(path, ValueExpr.DefaultValue)

    case MigrationAction.ChangeType(path, ValueExpr.PrimitiveConvert(from, to)) =>
      MigrationAction.ChangeType(path, ValueExpr.PrimitiveConvert(to, from))

    case MigrationAction.Join(left, right, target, combiner) =>
      MigrationAction.Split(target, left, right, reverseExpr(combiner))

    case MigrationAction.Split(from, toLeft, toRight, splitter) =>
      MigrationAction.Join(toLeft, toRight, from, reverseExpr(splitter))

    case MigrationAction.TransformElements(path, expr) =>
      MigrationAction.TransformElements(path, reverseExpr(expr))

    case MigrationAction.TransformKeys(path, expr) =>
      MigrationAction.TransformKeys(path, reverseExpr(expr))

    case MigrationAction.TransformValues(path, expr) =>
      MigrationAction.TransformValues(path, reverseExpr(expr))

    case MigrationAction.RenameCase(at, fromName, toName) =>
      MigrationAction.RenameCase(at, toName, fromName)

    case MigrationAction.TransformCase(at, caseName, inner) =>
      MigrationAction.TransformCase(at, caseName, inner.reverse)

    case MigrationAction.ApplyMigration(path, migration) =>
      MigrationAction.ApplyMigration(path, migration.reverse)

    case MigrationAction.CopyField(_, to) =>
      // Reverse drops the copy; the original at `from` is untouched.
      MigrationAction.DropField(to)

    case MigrationAction.MoveField(from, to) =>
      MigrationAction.MoveField(to, from)
  }

  private def reverseExpr(expr: ValueExpr): ValueExpr = expr match {
    case ValueExpr.DefaultValue               => ValueExpr.DefaultValue
    case ValueExpr.Constant(_)                => ValueExpr.DefaultValue // original value is lost
    case ValueExpr.PrimitiveConvert(from, to) => ValueExpr.PrimitiveConvert(to, from)
    case ValueExpr.Concat(sep)                => ValueExpr.StringSplit(sep)
    case ValueExpr.StringSplit(sep)           => ValueExpr.Concat(sep)
  }
}

object DynamicMigration {

  /** A migration that applies no actions; the identity element for `++`. */
  val identity: DynamicMigration = new DynamicMigration(Chunk.empty)

  /**
   * A single-step migration that runs `expr` on the focal value (paths inside
   * `expr`'s action use [[DynamicOptic.root]] relative to that value). Used to
   * implement `transformCase` sugar from a [[ValueExpr]].
   */
  def transformPayload(expr: ValueExpr): DynamicMigration =
    new DynamicMigration(Chunk.single(MigrationAction.TransformValue(DynamicOptic.root, expr)))

}
