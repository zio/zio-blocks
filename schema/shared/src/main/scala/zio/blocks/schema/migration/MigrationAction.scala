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
import zio.blocks.schema.{DynamicOptic, Schema, SchemaExpr, SchemaRepr}
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.Reflect
import zio.blocks.typeid.TypeId

/**
 * Pure-data algebraic action applied by the migration interpreter to a
 * [[zio.blocks.schema.DynamicValue]]. Every variant is a `final case class`
 * whose first field is `at: DynamicOptic`, carries no closures and no
 * `Schema[_]` fields, and exposes a total `reverse`.
 */
sealed trait MigrationAction { self =>
  def at: DynamicOptic
  def reverse: MigrationAction
}

object MigrationAction {

  /**
   * Adds `fieldName` to the record at `at`, using `default` to resolve the
   * inserted value. `default` is any [[SchemaExpr]] evaluable against the
   * current dynamic value (typically a [[SchemaExpr.DefaultValue]]).
   *
   * Reverse: [[DropField]] with `defaultForReverse = default`.
   */
  final case class AddField(at: DynamicOptic, fieldName: String, default: SchemaExpr[_, _]) extends MigrationAction {
    def reverse: MigrationAction = new DropField(at, fieldName, default)
  }

  /**
   * Removes `fieldName` from the record at `at`. `defaultForReverse` is carried
   * verbatim so [[reverse]] can restore the field.
   *
   * Reverse: [[AddField]] with `default = defaultForReverse`.
   */
  final case class DropField(at: DynamicOptic, fieldName: String, defaultForReverse: SchemaExpr[_, _])
      extends MigrationAction {
    def reverse: MigrationAction = new AddField(at, fieldName, defaultForReverse)
  }

  /**
   * Renames the field at `at` (whose last node must be a
   * [[DynamicOptic.Node.Field]]) to `to`, preserving its value.
   *
   * Reverse: [[Rename]] at the new path, swapping the names back. When
   * `at == DynamicOptic.root` the action reverses to itself.
   */
  final case class Rename(at: DynamicOptic, to: String) extends MigrationAction {
    def reverse: MigrationAction = {
      val nodes = at.nodes
      if (nodes.isEmpty) this
      else
        nodes.last match {
          case f: DynamicOptic.Node.Field =>
            val parent = new DynamicOptic(nodes.init.toIndexedSeq)
            new Rename(parent.field(to), f.name)
          case _ => this
        }
    }
  }

  /**
   * Applies the same-type `transform` to the value at `at`. Use this when the
   * transform's source and target types coincide; use [[ChangeType]] otherwise.
   * Reverse is involutive: the structural law `m.reverse.reverse == m` holds by
   * construction.
   */
  final case class TransformValue(at: DynamicOptic, transform: SchemaExpr[_, _]) extends MigrationAction {
    def reverse: MigrationAction = this
  }

  /**
   * Applies the type-changing `converter` to the value at `at`. Interpreter
   * shape matches [[TransformValue]]; only the builder layer enforces that
   * source and target types differ. Reverse is involutive.
   */
  final case class ChangeType(at: DynamicOptic, converter: SchemaExpr[_, _]) extends MigrationAction {
    def reverse: MigrationAction = this
  }

  /**
   * Converts `Option[T]` to `T` at `at` by unwrapping `Some(v)` and filling
   * `None` with `default`. Reverse returns [[Optionalize]] when `default` is a
   * [[SchemaExpr.DefaultValue]]; otherwise reverses to itself.
   */
  final case class Mandate(at: DynamicOptic, default: SchemaExpr[_, _]) extends MigrationAction {
    def reverse: MigrationAction = default match {
      case d: SchemaExpr.DefaultValue[_] => new Optionalize(at, d.targetSchemaRepr)
      case _                             => this
    }
  }

  /**
   * Converts `T` to `Option[T]` at `at` by wrapping the current value in
   * `Some(_)`. `sourceSchemaRepr` is structural metadata consumed only by
   * [[reverse]] to synthesise the canonical [[Mandate]] round-trip.
   */
  final case class Optionalize(at: DynamicOptic, sourceSchemaRepr: SchemaRepr) extends MigrationAction {
    def reverse: MigrationAction =
      new Mandate(at, new SchemaExpr.DefaultValue(at, sourceSchemaRepr))
  }

  /**
   * Renames a variant case from `from` to `to` at the variant addressed by
   * `at`. Reverse swaps `from` and `to`. If the runtime case name does not
   * equal `from`, the interpreter surfaces [[MigrationError.SchemaMismatch]].
   */
  final case class RenameCase(at: DynamicOptic, from: String, to: String) extends MigrationAction {
    def reverse: MigrationAction = new RenameCase(at, to, from)
  }

  /**
   * Applies `actions` only when the variant at `at` selects the case named by
   * `at`'s terminal `Node.Case` (pass-through otherwise). Reverse is
   * `TransformCase(at, actions.reverse.map(_.reverse))`.
   */
  final case class TransformCase(at: DynamicOptic, actions: Chunk[MigrationAction]) extends MigrationAction {
    def reverse: MigrationAction = new TransformCase(at, actions.reverse.map(_.reverse))
  }

  /**
   * Applies `transform` to each element of the sequence at `at`. A failure at
   * index `i` surfaces as `ActionFailed(at ++ Elements ++ AtIndex(i), …)`.
   * Reverse is involutive.
   */
  final case class TransformElements(at: DynamicOptic, transform: SchemaExpr[_, _]) extends MigrationAction {
    def reverse: MigrationAction = this
  }

  /**
   * Applies `transform` to each map key at `at`. Fail-fast on the first
   * collision, surfacing [[MigrationError.KeyCollision]]. Requires `transform`
   * to be injective. Reverse is involutive.
   */
  final case class TransformKeys(at: DynamicOptic, transform: SchemaExpr[_, _]) extends MigrationAction {
    def reverse: MigrationAction = this
  }

  /**
   * Applies `transform` to each map value at `at`. An entry-`k` failure
   * surfaces as `ActionFailed(at ++ MapValues ++ AtMapKey(k), …)`. Reverse is
   * involutive.
   */
  final case class TransformValues(at: DynamicOptic, transform: SchemaExpr[_, _]) extends MigrationAction {
    def reverse: MigrationAction = this
  }

  /**
   * Reads `sourcePaths` from the current dynamic value, combines them via
   * `combiner`, and writes the resulting single value at `at`. `combiner` is
   * evaluated against the root dynamic value.
   *
   * Reverse: [[Split]] with `targetPaths = sourcePaths` and
   * `splitter = combiner`.
   */
  final case class Join(
    at: DynamicOptic,
    sourcePaths: Chunk[DynamicOptic],
    combiner: SchemaExpr[_, _]
  ) extends MigrationAction {
    def reverse: MigrationAction = new Split(at, sourcePaths, combiner)
  }

  /**
   * Evaluates `splitter` against the root dynamic value and dispatches each
   * field of the resulting composite (typically a
   * [[zio.blocks.schema.DynamicValue.Record]]) into `targetPaths` positionally.
   * Shape mismatches surface as [[MigrationError.Irreversible]] at apply time.
   *
   * Reverse: [[Join]] with `sourcePaths = targetPaths` and
   * `combiner = splitter`.
   */
  final case class Split(
    at: DynamicOptic,
    targetPaths: Chunk[DynamicOptic],
    splitter: SchemaExpr[_, _]
  ) extends MigrationAction {
    def reverse: MigrationAction = new Join(at, targetPaths, splitter)
  }

  private val schemaExprSchema: Schema[SchemaExpr[_, _]] = SchemaExpr.migrationSchema

  private def opticAndExpr[A <: MigrationAction](
    typeId: TypeId[A],
    make: (DynamicOptic, SchemaExpr[_, _]) => A,
    exprFieldName: String,
    exprOf: A => SchemaExpr[_, _]
  ): Schema[A] = new Schema(
    reflect = new Reflect.Record[Binding, A](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Reflect.Deferred(() => schemaExprSchema.reflect).asTerm(exprFieldName)
      ),
      typeId = typeId,
      recordBinding = new Binding.Record(
        constructor = new Constructor[A] {
          def usedRegisters: RegisterOffset                       = 2
          def construct(in: Registers, offset: RegisterOffset): A =
            make(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[SchemaExpr[_, _]]
            )
        },
        deconstructor = new Deconstructor[A] {
          def usedRegisters: RegisterOffset                                    = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: A): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, exprOf(in))
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val addFieldSchema: Schema[AddField] = new Schema(
    reflect = new Reflect.Record[Binding, AddField](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[String].reflect.asTerm("fieldName"),
        Reflect.Deferred(() => schemaExprSchema.reflect).asTerm("default")
      ),
      typeId = TypeId.of[AddField],
      recordBinding = new Binding.Record(
        constructor = new Constructor[AddField] {
          def usedRegisters: RegisterOffset                              = 3
          def construct(in: Registers, offset: RegisterOffset): AddField =
            new AddField(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[SchemaExpr[_, _]]
            )
        },
        deconstructor = new Deconstructor[AddField] {
          def usedRegisters: RegisterOffset                                           = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: AddField): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.fieldName)
            out.setObject(offset + 2, in.default)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val dropFieldSchema: Schema[DropField] = new Schema(
    reflect = new Reflect.Record[Binding, DropField](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[String].reflect.asTerm("fieldName"),
        Reflect.Deferred(() => schemaExprSchema.reflect).asTerm("defaultForReverse")
      ),
      typeId = TypeId.of[DropField],
      recordBinding = new Binding.Record(
        constructor = new Constructor[DropField] {
          def usedRegisters: RegisterOffset                               = 3
          def construct(in: Registers, offset: RegisterOffset): DropField =
            new DropField(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[SchemaExpr[_, _]]
            )
        },
        deconstructor = new Deconstructor[DropField] {
          def usedRegisters: RegisterOffset                                            = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: DropField): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.fieldName)
            out.setObject(offset + 2, in.defaultForReverse)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val renameSchema: Schema[Rename] = new Schema(
    reflect = new Reflect.Record[Binding, Rename](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[String].reflect.asTerm("to")
      ),
      typeId = TypeId.of[Rename],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Rename] {
          def usedRegisters: RegisterOffset                            = 2
          def construct(in: Registers, offset: RegisterOffset): Rename =
            new Rename(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[Rename] {
          def usedRegisters: RegisterOffset                                         = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: Rename): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.to)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val transformValueSchema: Schema[TransformValue] =
    opticAndExpr[TransformValue](
      TypeId.of[TransformValue],
      (at, e) => new TransformValue(at, e),
      "transform",
      _.transform
    )

  implicit lazy val changeTypeSchema: Schema[ChangeType] =
    opticAndExpr[ChangeType](
      TypeId.of[ChangeType],
      (at, e) => new ChangeType(at, e),
      "converter",
      _.converter
    )

  implicit lazy val mandateSchema: Schema[Mandate] =
    opticAndExpr[Mandate](
      TypeId.of[Mandate],
      (at, e) => new Mandate(at, e),
      "default",
      _.default
    )

  implicit lazy val optionalizeSchema: Schema[Optionalize] = new Schema(
    reflect = new Reflect.Record[Binding, Optionalize](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[SchemaRepr].reflect.asTerm("sourceSchemaRepr")
      ),
      typeId = TypeId.of[Optionalize],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Optionalize] {
          def usedRegisters: RegisterOffset                                 = 2
          def construct(in: Registers, offset: RegisterOffset): Optionalize =
            new Optionalize(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[SchemaRepr]
            )
        },
        deconstructor = new Deconstructor[Optionalize] {
          def usedRegisters: RegisterOffset                                              = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: Optionalize): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.sourceSchemaRepr)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val renameCaseSchema: Schema[RenameCase] = new Schema(
    reflect = new Reflect.Record[Binding, RenameCase](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[String].reflect.asTerm("from"),
        Schema[String].reflect.asTerm("to")
      ),
      typeId = TypeId.of[RenameCase],
      recordBinding = new Binding.Record(
        constructor = new Constructor[RenameCase] {
          def usedRegisters: RegisterOffset                                = 3
          def construct(in: Registers, offset: RegisterOffset): RenameCase =
            new RenameCase(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[RenameCase] {
          def usedRegisters: RegisterOffset                                             = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: RenameCase): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.from)
            out.setObject(offset + 2, in.to)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val transformCaseSchema: Schema[TransformCase] = new Schema(
    reflect = new Reflect.Record[Binding, TransformCase](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Reflect.Deferred(() => Schema[Chunk[MigrationAction]].reflect).asTerm("actions")
      ),
      typeId = TypeId.of[TransformCase],
      recordBinding = new Binding.Record(
        constructor = new Constructor[TransformCase] {
          def usedRegisters: RegisterOffset                                   = 2
          def construct(in: Registers, offset: RegisterOffset): TransformCase =
            new TransformCase(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[Chunk[MigrationAction]]
            )
        },
        deconstructor = new Deconstructor[TransformCase] {
          def usedRegisters: RegisterOffset                                                = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: TransformCase): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.actions)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val transformElementsSchema: Schema[TransformElements] =
    opticAndExpr[TransformElements](
      TypeId.of[TransformElements],
      (at, e) => new TransformElements(at, e),
      "transform",
      _.transform
    )

  implicit lazy val transformKeysSchema: Schema[TransformKeys] =
    opticAndExpr[TransformKeys](
      TypeId.of[TransformKeys],
      (at, e) => new TransformKeys(at, e),
      "transform",
      _.transform
    )

  implicit lazy val transformValuesSchema: Schema[TransformValues] =
    opticAndExpr[TransformValues](
      TypeId.of[TransformValues],
      (at, e) => new TransformValues(at, e),
      "transform",
      _.transform
    )

  implicit lazy val joinSchema: Schema[Join] = new Schema(
    reflect = new Reflect.Record[Binding, Join](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[Chunk[DynamicOptic]].reflect.asTerm("sourcePaths"),
        Reflect.Deferred(() => schemaExprSchema.reflect).asTerm("combiner")
      ),
      typeId = TypeId.of[Join],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Join] {
          def usedRegisters: RegisterOffset                          = 3
          def construct(in: Registers, offset: RegisterOffset): Join =
            new Join(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[Chunk[DynamicOptic]],
              in.getObject(offset + 2).asInstanceOf[SchemaExpr[_, _]]
            )
        },
        deconstructor = new Deconstructor[Join] {
          def usedRegisters: RegisterOffset                                       = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: Join): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.sourcePaths)
            out.setObject(offset + 2, in.combiner)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val splitSchema: Schema[Split] = new Schema(
    reflect = new Reflect.Record[Binding, Split](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[Chunk[DynamicOptic]].reflect.asTerm("targetPaths"),
        Reflect.Deferred(() => schemaExprSchema.reflect).asTerm("splitter")
      ),
      typeId = TypeId.of[Split],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Split] {
          def usedRegisters: RegisterOffset                           = 3
          def construct(in: Registers, offset: RegisterOffset): Split =
            new Split(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[Chunk[DynamicOptic]],
              in.getObject(offset + 2).asInstanceOf[SchemaExpr[_, _]]
            )
        },
        deconstructor = new Deconstructor[Split] {
          def usedRegisters: RegisterOffset                                        = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: Split): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.targetPaths)
            out.setObject(offset + 2, in.splitter)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  /**
   * Hand-rolled schema for the 14-variant migration ADT. Uses the narrow
   * migration-used `SchemaExpr` bridge at
   * [[zio.blocks.schema.SchemaExpr.migrationSchema]].
   */
  implicit lazy val schema: Schema[MigrationAction] = new Schema(
    reflect = new Reflect.Variant[Binding, MigrationAction](
      cases = Chunk(
        addFieldSchema.reflect.asTerm("AddField"),
        dropFieldSchema.reflect.asTerm("DropField"),
        renameSchema.reflect.asTerm("Rename"),
        transformValueSchema.reflect.asTerm("TransformValue"),
        changeTypeSchema.reflect.asTerm("ChangeType"),
        mandateSchema.reflect.asTerm("Mandate"),
        optionalizeSchema.reflect.asTerm("Optionalize"),
        renameCaseSchema.reflect.asTerm("RenameCase"),
        transformCaseSchema.reflect.asTerm("TransformCase"),
        transformElementsSchema.reflect.asTerm("TransformElements"),
        transformKeysSchema.reflect.asTerm("TransformKeys"),
        transformValuesSchema.reflect.asTerm("TransformValues"),
        joinSchema.reflect.asTerm("Join"),
        splitSchema.reflect.asTerm("Split")
      ),
      typeId = TypeId.of[MigrationAction],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[MigrationAction] {
          def discriminate(a: MigrationAction): Int = a match {
            case _: AddField          => 0
            case _: DropField         => 1
            case _: Rename            => 2
            case _: TransformValue    => 3
            case _: ChangeType        => 4
            case _: Mandate           => 5
            case _: Optionalize       => 6
            case _: RenameCase        => 7
            case _: TransformCase     => 8
            case _: TransformElements => 9
            case _: TransformKeys     => 10
            case _: TransformValues   => 11
            case _: Join              => 12
            case _: Split             => 13
          }
        },
        matchers = Matchers(
          new Matcher[AddField] {
            def downcastOrNull(a: Any): AddField = a match {
              case x: AddField => x
              case _           => null.asInstanceOf[AddField]
            }
          },
          new Matcher[DropField] {
            def downcastOrNull(a: Any): DropField = a match {
              case x: DropField => x
              case _            => null.asInstanceOf[DropField]
            }
          },
          new Matcher[Rename] {
            def downcastOrNull(a: Any): Rename = a match {
              case x: Rename => x
              case _         => null.asInstanceOf[Rename]
            }
          },
          new Matcher[TransformValue] {
            def downcastOrNull(a: Any): TransformValue = a match {
              case x: TransformValue => x
              case _                 => null.asInstanceOf[TransformValue]
            }
          },
          new Matcher[ChangeType] {
            def downcastOrNull(a: Any): ChangeType = a match {
              case x: ChangeType => x
              case _             => null.asInstanceOf[ChangeType]
            }
          },
          new Matcher[Mandate] {
            def downcastOrNull(a: Any): Mandate = a match {
              case x: Mandate => x
              case _          => null.asInstanceOf[Mandate]
            }
          },
          new Matcher[Optionalize] {
            def downcastOrNull(a: Any): Optionalize = a match {
              case x: Optionalize => x
              case _              => null.asInstanceOf[Optionalize]
            }
          },
          new Matcher[RenameCase] {
            def downcastOrNull(a: Any): RenameCase = a match {
              case x: RenameCase => x
              case _             => null.asInstanceOf[RenameCase]
            }
          },
          new Matcher[TransformCase] {
            def downcastOrNull(a: Any): TransformCase = a match {
              case x: TransformCase => x
              case _                => null.asInstanceOf[TransformCase]
            }
          },
          new Matcher[TransformElements] {
            def downcastOrNull(a: Any): TransformElements = a match {
              case x: TransformElements => x
              case _                    => null.asInstanceOf[TransformElements]
            }
          },
          new Matcher[TransformKeys] {
            def downcastOrNull(a: Any): TransformKeys = a match {
              case x: TransformKeys => x
              case _                => null.asInstanceOf[TransformKeys]
            }
          },
          new Matcher[TransformValues] {
            def downcastOrNull(a: Any): TransformValues = a match {
              case x: TransformValues => x
              case _                  => null.asInstanceOf[TransformValues]
            }
          },
          new Matcher[Join] {
            def downcastOrNull(a: Any): Join = a match {
              case x: Join => x
              case _       => null.asInstanceOf[Join]
            }
          },
          new Matcher[Split] {
            def downcastOrNull(a: Any): Split = a match {
              case x: Split => x
              case _        => null.asInstanceOf[Split]
            }
          }
        )
      ),
      modifiers = Chunk.empty
    )
  )
}
