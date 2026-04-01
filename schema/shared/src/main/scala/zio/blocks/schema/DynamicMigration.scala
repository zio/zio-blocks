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

/**
 * Untyped, serializable migration core. A list of MigrationAction values that
 * operate on DynamicValue. Fully serializable; no reflection or closures.
 */
final case class DynamicMigration(actions: Chunk[MigrationAction]) {

  def ++(other: DynamicMigration): DynamicMigration =
    DynamicMigration(actions ++ other.actions)

  def reverse: DynamicMigration =
    DynamicMigration(actions.reverse.map(MigrationAction.invert))

  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
    actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) { (acc, action) =>
      acc.flatMap(DynamicMigration.applyAction(action, _))
    }
}

object DynamicMigration {

  val empty: DynamicMigration = DynamicMigration(Chunk.empty)

  def applyAction(action: MigrationAction, value: DynamicValue): Either[MigrationError, DynamicValue] = {
    import MigrationAction._
    import DynamicValue.Variant

    def getOne(path: DynamicOptic): Either[MigrationError, DynamicValue] =
      value.get(path).one.left.map(se => MigrationError(se.message, path))

    def set(path: DynamicOptic, v: DynamicValue): Either[MigrationError, DynamicValue] =
      value.setOrFail(path, v).left.map(se => MigrationError(se.message, path))

    def insert(path: DynamicOptic, v: DynamicValue): Either[MigrationError, DynamicValue] =
      value.insertOrFail(path, v).left.map(se => MigrationError(se.message, path))

    def delete(path: DynamicOptic): Either[MigrationError, DynamicValue] =
      value.deleteOrFail(path).left.map(se => MigrationError(se.message, path))

    action match {
      case AddField(at, expr) =>
        expr.eval(value).flatMap(newVal => insert(at, newVal))

      case DropField(at, _) =>
        delete(at)

      case RenameField(from, to) =>
        for {
          fieldVal     <- getOne(from)
          withoutField <- delete(from)
          result       <- withoutField.insertOrFail(to, fieldVal).left.map(se => MigrationError(se.message, to))
        } yield result

      case TransformValue(at, transform, _) =>
        for {
          current     <- getOne(at)
          transformed <- transform.eval(current).left.map(e => e.copy(at = at))
          result      <- set(at, transformed)
        } yield result

      case Optionalize(at, _) =>
        for {
          current <- getOne(at)
          result  <- set(at, Variant("Some", current))
        } yield result

      case Mandate(at) =>
        getOne(at).flatMap {
          case DynamicValue.Variant("Some", inner) =>
            set(at, inner)
          case DynamicValue.Variant("None", _) =>
            Left(MigrationError("Mandate: field is None", at))
          case other =>
            Left(MigrationError(s"Mandate: expected Option variant (Some/None), got ${other.valueType}", at))
        }

      case Join(left, right, into, transform, _, _) =>
        for {
          lv      <- getOne(left)
          rv      <- getOne(right)
          combined = DynamicValue.Record(("_left", lv), ("_right", rv))
          joined  <- transform.eval(combined).left.map(e => e.copy(at = into))
          step1   <- delete(left)
          step2   <- step1.deleteOrFail(right).left.map(se => MigrationError(se.message, right))
          result  <- step2.insertOrFail(into, joined).left.map(se => MigrationError(se.message, into))
        } yield result

      case Split(from, intoLeft, intoRight, leftExpr, rightExpr, _) =>
        for {
          src        <- getOne(from)
          lv         <- leftExpr.eval(src).left.map(e => e.copy(at = intoLeft))
          rv         <- rightExpr.eval(src).left.map(e => e.copy(at = intoRight))
          withoutSrc <- delete(from)
          withLeft   <- withoutSrc.insertOrFail(intoLeft, lv).left.map(se => MigrationError(se.message, intoLeft))
          result     <- withLeft.insertOrFail(intoRight, rv).left.map(se => MigrationError(se.message, intoRight))
        } yield result

      case RenameCase(at, from, to) =>
        getOne(at).flatMap {
          case Variant(caseName, payload) if caseName == from =>
            set(at, Variant(to, payload))
          case _: Variant =>
            Right(value)
          case other =>
            Left(MigrationError(s"RenameCase: expected Variant, got ${other.valueType}", at))
        }

      case TransformElements(at, _) =>
        Left(MigrationError("TransformElements: not yet implemented", at))

      case TransformKeys(at, _) =>
        Left(MigrationError("TransformKeys: not yet implemented", at))

      case TransformValues(at, _) =>
        Left(MigrationError("TransformValues: not yet implemented", at))

      case ChangeFieldType(at, converter, _) =>
        getOne(at).flatMap { current =>
          converter.eval(current).left.map(e => e.copy(at = at)).flatMap(set(at, _))
        }

      case TransformCase(at, _, _, _) =>
        Left(MigrationError("TransformCase: not yet implemented", at))
    }
  }
}

/**
 * A single migration step operating at a path (DynamicOptic). All actions are
 * serializable; no user functions.
 */
sealed trait MigrationAction {

  def reverse: MigrationAction = MigrationAction.invert(this)
}

object MigrationAction {

  final case class AddField(at: DynamicOptic, value: MigrationExpr)              extends MigrationAction
  final case class DropField(at: DynamicOptic, defaultForReverse: MigrationExpr) extends MigrationAction
  final case class RenameField(from: DynamicOptic, to: DynamicOptic)             extends MigrationAction
  final case class TransformValue(at: DynamicOptic, transform: MigrationExpr, inverseTransform: MigrationExpr)
      extends MigrationAction
  final case class Optionalize(at: DynamicOptic, defaultForReverse: MigrationExpr) extends MigrationAction
  final case class Mandate(at: DynamicOptic)                                       extends MigrationAction

  final case class Join(
    left: DynamicOptic,
    right: DynamicOptic,
    into: DynamicOptic,
    transform: MigrationExpr,
    inverseLeft: MigrationExpr,
    inverseRight: MigrationExpr
  ) extends MigrationAction

  final case class Split(
    from: DynamicOptic,
    intoLeft: DynamicOptic,
    intoRight: DynamicOptic,
    leftExpr: MigrationExpr,
    rightExpr: MigrationExpr,
    inverseTransform: MigrationExpr
  ) extends MigrationAction

  final case class RenameCase(at: DynamicOptic, from: String, to: String) extends MigrationAction
  final case class TransformCase(at: DynamicOptic, from: String, to: String, adapt: Chunk[MigrationAction])
      extends MigrationAction

  final case class TransformElements(at: DynamicOptic, transform: MigrationExpr) extends MigrationAction
  final case class TransformKeys(at: DynamicOptic, transform: MigrationExpr)     extends MigrationAction
  final case class TransformValues(at: DynamicOptic, transform: MigrationExpr)   extends MigrationAction
  final case class ChangeFieldType(at: DynamicOptic, converter: MigrationExpr, inverseConverter: MigrationExpr)
      extends MigrationAction

  def invert(action: MigrationAction): MigrationAction =
    action match {
      case AddField(at, value)        => DropField(at, value)
      case DropField(at, default)     => AddField(at, default)
      case RenameField(from, to)      => RenameField(to, from)
      case TransformValue(at, t, inv) => TransformValue(at, inv, t)
      case Optionalize(at, _)         => Mandate(at)
      case Mandate(at)                =>
        Optionalize(at, MigrationExpr.Literal(DynamicValue.Variant("None", DynamicValue.Record(Chunk.empty))))
      case Join(left, right, into, t, invL, invR)           => Split(into, left, right, invL, invR, t)
      case Split(from, intoLeft, intoRight, lE, rE, inv)    => Join(intoLeft, intoRight, from, inv, lE, rE)
      case RenameCase(at, from, to)                         => RenameCase(at, to, from)
      case TransformCase(at, from, to, adapt)               => TransformCase(at, to, from, adapt.map(invert).reverse)
      case TransformElements(at, _)                         => action
      case TransformKeys(at, _)                             => action
      case TransformValues(at, _)                           => action
      case ChangeFieldType(at, converter, inverseConverter) =>
        ChangeFieldType(at, inverseConverter, converter)
    }
}
