/*
  * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *   http://www.apache.org/licenses/LICENSE-2.0
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
  * A migration describes a transformation from one schema version to another.
  */
final case class Migration[A, B](
  dynamicMigration: DynamicMigration,
  sourceSchema: Schema[A],
  targetSchema: Schema[B]
) {
  def apply(value: A): Either[MigrationError, B] =
    dynamicMigration(sourceSchema.toDynamicValue(value))
      .flatMap(targetSchema.fromDynamicValue(_).left.map(MigrationError.SchemaError.apply))

  def ++[C](that: Migration[B, C]): Migration[A, C] =
    Migration(this.dynamicMigration ++ that.dynamicMigration, this.sourceSchema, that.targetSchema)

  def andThen[C](that: Migration[B, C]): Migration[A, C] = this ++ that

  def reverse: Migration[B, A] =
    Migration(this.dynamicMigration.reverse, this.targetSchema, this.sourceSchema)
}

final case class DynamicMigration(actions: Chunk[MigrationAction]) {
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
    actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) {
      case (acc, action) => acc.flatMap(action.apply)
    }

  def ++(that: DynamicMigration): DynamicMigration =
    DynamicMigration(this.actions ++ that.actions)

  def reverse: DynamicMigration =
    DynamicMigration(this.actions.reverse.map(_.reverse))
}

object DynamicMigration {
  val empty: DynamicMigration = DynamicMigration(Chunk.empty)
}

sealed trait MigrationAction {
  def at: DynamicOptic
  def apply(value: DynamicValue): Either[MigrationError, DynamicValue]
  def reverse: MigrationAction
}

object MigrationAction {
  case class AddField(at: DynamicOptic, default: SchemaExpr[DynamicValue, Any]) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      value match {
        case DynamicValue.Record(fields) =>
          at.nodes.lastOption match {
            case Some(DynamicOptic.Node.Field(fieldName)) =>
              default.evalDynamic(value).left.map(err => MigrationError.ActionFailed(this, at, err.toString)).map { results =>
                val defaultValue = results.headOption.getOrElse(DynamicValue.Null)
                DynamicValue.Record(fields :+ (fieldName -> defaultValue))
              }
            case _ => Left(MigrationError.ActionFailed(this, at, "Last optic node is not a field"))
          }
        case _ => Left(MigrationError.ActionFailed(this, at, "Target is not a record"))
      }
    def reverse: MigrationAction = DropField(at, default)
  }

  case class DropField(at: DynamicOptic, defaultForReverse: SchemaExpr[DynamicValue, Any]) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      value match {
        case DynamicValue.Record(fields) =>
          at.nodes.lastOption match {
            case Some(DynamicOptic.Node.Field(fieldName)) =>
              Right(DynamicValue.Record(fields.filterNot(_._1 == fieldName)))
            case _ => Left(MigrationError.ActionFailed(this, at, "Last optic node is not a field"))
          }
        case _ => Left(MigrationError.ActionFailed(this, at, "Target is not a record"))
      }
    def reverse: MigrationAction = AddField(at, defaultForReverse)
  }

  case class RenameField(at: DynamicOptic, to: String) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      value match {
        case DynamicValue.Record(fields) =>
          at.nodes.lastOption match {
            case Some(DynamicOptic.Node.Field(from)) =>
              Right(DynamicValue.Record(fields.map {
                case (name, val_) if name == from => (to, val_)
                case other => other
              }))
            case _ => Left(MigrationError.ActionFailed(this, at, "Last optic node is not a field"))
          }
        case _ => Left(MigrationError.ActionFailed(this, at, "Target is not a record"))
      }
    def reverse: MigrationAction = {
        val parent = if (at.nodes.length <= 1) DynamicOptic.root else new DynamicOptic(at.nodes.dropRight(1))
        at.nodes.lastOption match {
          case Some(DynamicOptic.Node.Field(from)) =>
            RenameField(parent.field(to), from)
          case _ => this
        }
    }
  }

  case class Join(at: DynamicOptic, sourcePaths: Chunk[DynamicOptic], combiner: SchemaExpr[DynamicValue, Any]) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      val inputs = sourcePaths.flatMap(p => value.get(p).toChunk)
      if (inputs.size != sourcePaths.size) {
        Left(MigrationError.ActionFailed(this, at, s"Could not resolve all source paths for join"))
      } else {
        val inputVal = if (inputs.size == 1) inputs.head else DynamicValue.Sequence(inputs)
        combiner.evalDynamic(inputVal).left.map(err => MigrationError.ActionFailed(this, at, err.toString)).map { results =>
          val result = results.headOption.getOrElse(DynamicValue.Null)
          value.modify(at)(_ => result)
        }
      }
    }
    def reverse: MigrationAction = Split(at, sourcePaths, combiner)
  }

  case class Split(at: DynamicOptic, targetPaths: Chunk[DynamicOptic], splitter: SchemaExpr[DynamicValue, Any]) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] = {
      val sourceVal = value.get(at).toChunk.headOption.getOrElse(DynamicValue.Null)
      splitter.evalDynamic(sourceVal).left.map(err => MigrationError.ActionFailed(this, at, err.toString)).flatMap { results =>
        if (results.size != targetPaths.size) {
          Left(MigrationError.ActionFailed(this, at, "Splitter did not produce expected number of outputs"))
        } else {
          targetPaths.zip(results).foldLeft[Either[MigrationError, DynamicValue]](Right(value)) {
            case (acc, (path, val_)) => acc.map(v => v.modify(path)(_ => val_))
          }
        }
      }
    }
    def reverse: MigrationAction = Join(at, targetPaths, splitter)
  }

  case class TransformValue(at: DynamicOptic, transform: SchemaExpr[DynamicValue, Any]) extends MigrationAction {
    def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
      transform.evalDynamic(value.get(at).toChunk.headOption.getOrElse(DynamicValue.Null)).left.map(err => MigrationError.ActionFailed(this, at, err.toString)).map { results =>
        val result = results.headOption.getOrElse(DynamicValue.Null)
        value.modify(at)(_ => result)
      }

    def reverse: MigrationAction = ???
  }
}

final class MigrationBuilder[A, B](
  val sourceSchema: Schema[A],
  val targetSchema: Schema[B],
  val actions: Chunk[MigrationAction]
) extends MigrationBuilderVersionSpecific[A, B] {

  def build: Migration[A, B] =
    Migration(DynamicMigration(actions), sourceSchema, targetSchema)
}

object Migration {
  def newBuilder[A, B](implicit source: Schema[A], target: Schema[B]): MigrationBuilder[A, B] =
    new MigrationBuilder(source, target, Chunk.empty)
}

sealed trait MigrationError
object MigrationError {
  case class ActionFailed(action: MigrationAction, path: DynamicOptic, message: String) extends MigrationError
  case class SchemaError(error: zio.blocks.schema.SchemaError) extends MigrationError
}
