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
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.DynamicValue.{Map => DVMap, Record, Sequence, Variant}
import zio.blocks.schema._
import zio.blocks.schema.migration.MigrationAction._
import zio.blocks.schema.migration.MigrationError.{InvalidValue, MissingField}
import zio.blocks.typeid.TypeId

final case class DynamicMigration(actions: Vector[MigrationAction]) {

  def apply(value: DynamicValue): Either[MigrationError, DynamicValue] =
    actions.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) { (acc, action) =>
      acc.flatMap(v => DynamicMigration.applyAction(v, action))
    }

  def ++(that: DynamicMigration): DynamicMigration = DynamicMigration(actions ++ that.actions)

  def reverse: DynamicMigration = DynamicMigration(actions.reverseIterator.map(_.reverse).toVector)

  def isEmpty: Boolean = actions.isEmpty
}

object DynamicMigration {
  val identity: DynamicMigration = DynamicMigration(Vector.empty)

  implicit lazy val schema: Schema[DynamicMigration] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicMigration](
      fields = Chunk.single(Reflect.Deferred(() => Schema[Vector[MigrationAction]].reflect).asTerm("actions")),
      typeId = TypeId.of[DynamicMigration],
      recordBinding = new Binding.Record(
        constructor = new Constructor[DynamicMigration] {
          def usedRegisters: RegisterOffset                                      = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): DynamicMigration =
            DynamicMigration(in.getObject(offset).asInstanceOf[Vector[MigrationAction]])
        },
        deconstructor = new Deconstructor[DynamicMigration] {
          def usedRegisters: RegisterOffset                                                   = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicMigration): Unit =
            out.setObject(offset, in.actions)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private[migration] def applyAction(
    value: DynamicValue,
    action: MigrationAction
  ): Either[MigrationError, DynamicValue] =
    action match {
      case AddField(at, default) =>
        default(value).flatMap(v => insert(value, at, v))
      case DropField(at, _) =>
        delete(value, at)
      case Rename(at, to) =>
        rename(value, at, to)
      case TransformValue(at, transform) =>
        modify(value, at, source => transform(source))
      case Mandate(at, default) =>
        modify(
          value,
          at,
          source =>
            source match {
              case Variant("Some", payload) => Right(payload)
              case Variant("None", _)       => default(value)
              case DynamicValue.Null        => default(value)
              case other                    => Right(other)
            }
        )
      case Optionalize(at) =>
        modify(value, at, source => Right(Variant("Some", source)))
      case Join(at, sourcePaths, combiner) =>
        val joined = sourcePaths
          .foldLeft[Either[MigrationError, DynamicValue]](Right(value)) { (acc, path) =>
            acc.flatMap(_ => value.get(path).one.left.map(_ => MissingField(path, path.toString)))
          }
          .flatMap(_ => combiner(value))
        joined.flatMap(v => insert(value, at, v))
      case Split(at, targetPaths, splitter) =>
        splitter(value).flatMap { out =>
          targetPaths.foldLeft[Either[MigrationError, DynamicValue]](Right(value)) { (acc, target) =>
            acc.flatMap(curr => insert(curr, target, out))
          }
        }
      case ChangeType(at, converter) =>
        modify(value, at, source => converter(source))
      case RenameCase(at, from, to) =>
        modify(
          value,
          at,
          {
            case Variant(name, payload) if name == from => Right(Variant(to, payload))
            case other                                  => Right(other)
          }
        )
      case TransformCase(at, caseName, actions) =>
        modify(
          value,
          at,
          {
            case Variant(name, payload) if name == caseName =>
              DynamicMigration(actions).apply(payload).map(updated => Variant(name, updated))
            case other =>
              Right(other)
          }
        )
      case TransformElements(at, transform) =>
        modify(
          value,
          at,
          {
            case Sequence(elements) =>
              elements
                .foldLeft[Either[MigrationError, Chunk[DynamicValue]]](Right(Chunk.empty)) { (acc, e) =>
                  for {
                    xs <- acc
                    v  <- transform(e)
                  } yield xs :+ v
                }
                .map(Sequence(_))
            case other => Right(other)
          }
        )
      case TransformKeys(at, transform) =>
        modify(
          value,
          at,
          {
            case DVMap(entries) =>
              entries
                .foldLeft[Either[MigrationError, Chunk[(DynamicValue, DynamicValue)]]](Right(Chunk.empty)) {
                  case (acc, (k, v)) =>
                    for {
                      xs <- acc
                      nk <- transform(k)
                    } yield xs :+ (nk -> v)
                }
                .map(DVMap(_))
            case other => Right(other)
          }
        )
      case TransformValues(at, transform) =>
        modify(
          value,
          at,
          {
            case DVMap(entries) =>
              entries
                .foldLeft[Either[MigrationError, Chunk[(DynamicValue, DynamicValue)]]](Right(Chunk.empty)) {
                  case (acc, (k, v)) =>
                    for {
                      xs <- acc
                      nv <- transform(v)
                    } yield xs :+ (k -> nv)
                }
                .map(DVMap(_))
            case other => Right(other)
          }
        )
      case NestedMigration(at, migration) =>
        modify(value, at, nested => migration(nested))
    }

  private[this] def rename(value: DynamicValue, at: DynamicOptic, to: String): Either[MigrationError, DynamicValue] = {
    val from = at.nodes.lastOption.collect { case DynamicOptic.Node.Field(name) => name }
    from match {
      case None           => Left(InvalidValue(at, "Rename only supports field paths"))
      case Some(fromName) =>
        value match {
          case Record(fields) =>
            val idx = fields.indexWhere(_._1 == fromName)
            if (idx < 0) Left(MissingField(at, fromName))
            else Right(Record(fields.updated(idx, (to, fields(idx)._2))))
          case _ => Left(InvalidValue(at, "Rename requires record at root"))
        }
    }
  }

  private[this] def modify(
    value: DynamicValue,
    at: DynamicOptic,
    f: DynamicValue => Either[MigrationError, DynamicValue]
  ): Either[MigrationError, DynamicValue] =
    value
      .get(at)
      .one
      .left
      .map(_ => MissingField(at, at.toString))
      .flatMap(f)
      .flatMap(v => set(value, at, v))

  private[this] def set(value: DynamicValue, at: DynamicOptic, v: DynamicValue): Either[MigrationError, DynamicValue] =
    value.setOrFail(at, v).left.map(err => InvalidValue(at, err.message))

  private[this] def delete(value: DynamicValue, at: DynamicOptic): Either[MigrationError, DynamicValue] =
    value.deleteOrFail(at).left.map(_ => MissingField(at, at.toString))

  private[this] def insert(
    value: DynamicValue,
    at: DynamicOptic,
    v: DynamicValue
  ): Either[MigrationError, DynamicValue] =
    value.insertOrFail(at, v).left.map(err => InvalidValue(at, err.message))
}
