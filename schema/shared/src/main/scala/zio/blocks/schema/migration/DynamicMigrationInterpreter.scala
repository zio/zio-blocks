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
import zio.blocks.schema._
import zio.blocks.schema.DynamicOptic.Node

private[migration] object DynamicMigrationInterpreter {

  def apply(
    migration: DynamicMigration,
    root: DynamicValue,
    sourceSchema: Schema[?],
    targetSchema: Schema[?]
  ): Either[SchemaError, DynamicValue] = {
    var current = root
    var idx     = 0
    val len     = migration.actions.length
    while (idx < len) {
      applyAction(migration.actions(idx), current, sourceSchema, targetSchema) match {
        case Right(next) =>
          current = next
          idx += 1
        case Left(err) => return Left(err)
      }
    }
    Right(current)
  }

  private def applyAction(
    action: MigrationAction,
    working: DynamicValue,
    sourceSchema: Schema[?],
    targetSchema: Schema[?]
  ): Either[SchemaError, DynamicValue] =
    action match {
      case MigrationAction.AddField(at, defaultExpr) =>
        for {
          dv <- MigrationExprEval.eval(defaultExpr, working, sourceSchema, targetSchema, at)
          r  <- working.insertOrFail(at, dv)
        } yield r

      case MigrationAction.DropField(at, _) =>
        working.deleteOrFail(at)

      case MigrationAction.Rename(at, to) =>
        applyRename(working, at, to)

      case MigrationAction.TransformValue(at, transform) =>
        for {
          newVal <- MigrationExprEval.eval(transform, working, sourceSchema, targetSchema, at)
          r      <- working.setOrFail(at, newVal)
        } yield r

      case MigrationAction.Mandate(at, defaultExpr) =>
        working.get(at).one match {
          case Right(v) =>
            v match {
              case DynamicValue.Null =>
                MigrationExprEval
                  .eval(defaultExpr, working, sourceSchema, targetSchema, at)
                  .flatMap(dv => working.setOrFail(at, dv))
              case v: DynamicValue.Variant if v.caseNameValue == "None" =>
                MigrationExprEval
                  .eval(defaultExpr, working, sourceSchema, targetSchema, at)
                  .flatMap(dv => working.setOrFail(at, dv))
              case other => working.setOrFail(at, other)
            }
          case Left(_) =>
            MigrationExprEval
              .eval(defaultExpr, working, sourceSchema, targetSchema, at)
              .flatMap(dv => working.insertOrFail(at, dv))
        }

      case MigrationAction.Optionalize(at) =>
        working.get(at).one match {
          case Right(value) => working.setOrFail(at, optionalSome(value))
          case Left(_)      => Right(working)
        }

      case MigrationAction.ChangeType(at, converter) =>
        for {
          cur <- working.get(at).one.left.map(_ => SchemaError.message("Path not found for ChangeType", at))
          out <- converter match {
                   case c: MigrationExpr.CoercePrimitive =>
                     MigrationExprEval.evalUnary(cur, c.to, at)
                   case other =>
                     MigrationExprEval.eval(other, cur, sourceSchema, targetSchema, at)
                 }
          r <- working.setOrFail(at, out)
        } yield r

      case MigrationAction.Join(at, sourcePaths, combiner) =>
        MigrationExprEval
          .eval(combiner, working, sourceSchema, targetSchema, at)
          .flatMap(dv => working.insertOrFail(at, dv))

      case MigrationAction.Split(at, targetPaths, splitter) =>
        for {
          cur <- working.get(at).one.left.map(_ => SchemaError.message("Path not found for Split", at))
          out <- MigrationExprEval.eval(splitter, cur, sourceSchema, targetSchema, at)
          r   <- splitFallback(working, targetPaths, out)
        } yield r

      case MigrationAction.RenameCase(at, from, to) =>
        renameVariantCase(working, at, from, to)

      case MigrationAction.TransformCase(at, nested) =>
        working.get(at).one match {
          case Right(v) =>
            val innerMigration = DynamicMigration(nested)
            innerMigration.apply(v, sourceSchema, targetSchema) match {
              case Right(out) => working.setOrFail(at, out)
              case Left(err)  => Left(err)
            }
          case Left(err) => Left(err)
        }

      case MigrationAction.TransformElements(at, transform) =>
        transformSequenceElements(working, at, transform, sourceSchema, targetSchema)

      case MigrationAction.TransformKeys(at, transform) =>
        transformMapKeys(working, at, transform, sourceSchema, targetSchema)

      case MigrationAction.TransformValues(at, transform) =>
        transformMapValues(working, at, transform, sourceSchema, targetSchema)
    }

  private def splitFallback(
    working: DynamicValue,
    targetPaths: Vector[DynamicOptic],
    splitterResult: DynamicValue
  ): Either[SchemaError, DynamicValue] =
    targetPaths.headOption match {
      case Some(first) =>
        working.setOrFail(first, splitterResult)
      case None => Right(working)
    }

  private def optionalSome(value: DynamicValue): DynamicValue =
    new DynamicValue.Variant("Some", value)

  private def transformSequenceElements(
    working: DynamicValue,
    at: DynamicOptic,
    transform: MigrationExpr,
    sourceSchema: Schema[?],
    targetSchema: Schema[?]
  ): Either[SchemaError, DynamicValue] =
    working.get(at).one match {
      case Right(seq: DynamicValue.Sequence) =>
        val out = seq.elements.zipWithIndex.map { case (element, index) =>
          MigrationExprEval.eval(transform, element, sourceSchema, targetSchema, at.at(index))
        }
        out
          .foldLeft[Either[SchemaError, Chunk[DynamicValue]]](Right(Chunk.empty)) { case (acc, item) =>
            for {
              chunk <- acc
              dv    <- item
            } yield chunk :+ dv
          }
          .flatMap { chunk =>
            working.setOrFail(at, DynamicValue.Sequence(chunk))
          }
      case Right(other) =>
        Left(SchemaError.message(s"TransformElements requires a sequence at path, got ${other.valueType}", at))
      case Left(err) => Left(err)
    }

  private def transformMapKeys(
    working: DynamicValue,
    at: DynamicOptic,
    transform: MigrationExpr,
    sourceSchema: Schema[?],
    targetSchema: Schema[?]
  ): Either[SchemaError, DynamicValue] =
    working.get(at).one match {
      case Right(map: DynamicValue.Map) =>
        val rebuilt =
          map.entries.foldLeft[Either[SchemaError, Chunk[(DynamicValue, DynamicValue)]]](Right(Chunk.empty)) {
            case (acc, (key, value)) =>
              for {
                chunk  <- acc
                newKey <- MigrationExprEval.eval(transform, key, sourceSchema, targetSchema, at)
              } yield chunk :+ ((newKey, value))
          }
        rebuilt.flatMap(entries => working.setOrFail(at, DynamicValue.Map(entries)))
      case Right(other) =>
        Left(SchemaError.message(s"TransformKeys requires a map at path, got ${other.valueType}", at))
      case Left(err) => Left(err)
    }

  private def transformMapValues(
    working: DynamicValue,
    at: DynamicOptic,
    transform: MigrationExpr,
    sourceSchema: Schema[?],
    targetSchema: Schema[?]
  ): Either[SchemaError, DynamicValue] =
    working.get(at).one match {
      case Right(map: DynamicValue.Map) =>
        val rebuilt =
          map.entries.foldLeft[Either[SchemaError, Chunk[(DynamicValue, DynamicValue)]]](Right(Chunk.empty)) {
            case (acc, (key, value)) =>
              for {
                chunk  <- acc
                newVal <- MigrationExprEval.eval(transform, value, sourceSchema, targetSchema, at)
              } yield chunk :+ ((key, newVal))
          }
        rebuilt.flatMap(entries => working.setOrFail(at, DynamicValue.Map(entries)))
      case Right(other) =>
        Left(SchemaError.message(s"TransformValues requires a map at path, got ${other.valueType}", at))
      case Left(err) => Left(err)
    }

  private def renameVariantCase(
    working: DynamicValue,
    at: DynamicOptic,
    from: String,
    to: String
  ): Either[SchemaError, DynamicValue] =
    working.get(at).one match {
      case Right(v: DynamicValue.Variant) if v.caseNameValue == from =>
        working.setOrFail(at, DynamicValue.Variant(to, v.value))
      case Right(other) =>
        Left(SchemaError.message(s"Expected variant case $from at path, got ${other.valueType}", at))
      case Left(err) => Left(err)
    }

  private def applyRename(
    working: DynamicValue,
    path: DynamicOptic,
    newName: String
  ): Either[SchemaError, DynamicValue] =
    path.nodes.lastOption match {
      case Some(oldField: Node.Field) =>
        val parentPath = new DynamicOptic(path.nodes.dropRight(1))
        val oldName    = oldField.name
        working.get(parentPath).one match {
          case Right(rec: DynamicValue.Record) =>
            renameInRecord(rec, oldName, newName).flatMap(updated => working.setOrFail(parentPath, updated))
          case Right(other) =>
            Left(SchemaError.message(s"Rename parent must be a record, got ${other.valueType}", parentPath))
          case Left(err) => Left(err)
        }
      case _ =>
        Left(SchemaError.message("Rename path must end with a field node", path))
    }

  private def renameInRecord(
    record: DynamicValue.Record,
    oldName: String,
    newName: String
  ): Either[SchemaError, DynamicValue] = {
    val fields = record.fields
    val idx    = fields.indexWhere(_._1 == oldName)
    if (idx < 0) Left(SchemaError.message(s"Field '$oldName' not found in record", DynamicOptic.root))
    else {
      val value = fields(idx)._2
      val rest  = fields.patch(idx, Chunk.empty, 1)
      Right(DynamicValue.Record(rest :+ ((newName, value))))
    }
  }
}
