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
import zio.blocks.schema.binding.*
import zio.blocks.typeid.TypeId

/**
 * A migration action represents a single transformation step in a migration.
 *
 * All actions operate at a path represented by [[DynamicOptic]], enabling
 * precise targeting of nested structures.
 *
 * Actions are fully serializable as pure data, containing no user functions or
 * closures.
 */
sealed trait MigrationAction extends Product with Serializable {

  /** The path where this action applies. */
  def at: DynamicOptic

  /** The structural reverse of this action. */
  def reverse: MigrationAction
}

object MigrationAction {

  // ═══════════════════════════════════════════════════════════════════════════════
  // Record Actions
  // ═══════════════════════════════════════════════════════════════════════════════

  /**
   * Adds a new field to a record with a default value.
   *
   * The reverse action is [[DropField]] with the same default.
   */
  final case class AddField(
    at: DynamicOptic,
    default: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, default)
  }

  /**
   * Removes a field from a record.
   *
   * The default value is used when reversing the migration to reconstruct the
   * dropped field.
   */
  final case class DropField(
    at: DynamicOptic,
    defaultForReverse: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, defaultForReverse)
  }

  /**
   * Renames a field within a record.
   *
   * The reverse action renames the field back to its original name.
   */
  final case class Rename(
    at: DynamicOptic,
    to: String
  ) extends MigrationAction {
    def reverse: MigrationAction = {
      val parentPath = DynamicOptic(at.nodes.dropRight(1))
      Rename(parentPath.field(to), fieldNameFromPath(at))
    }
  }

  /**
   * Transforms a value at the specified path using a pure expression.
   *
   * The transform expression must be a primitive-to-primitive transformation.
   */
  final case class TransformValue(
    at: DynamicOptic,
    transform: DynamicTransform
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformValue(at, transform.reverse)
  }

  /**
   * Converts an optional field to a required field with a default.
   *
   * If the optional value is None, the default is used.
   */
  final case class Mandate(
    at: DynamicOptic,
    default: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = Optionalize(at)
  }

  /**
   * Converts a required field to an optional field.
   *
   * The value is wrapped in Some.
   */
  final case class Optionalize(
    at: DynamicOptic
  ) extends MigrationAction {
    def reverse: MigrationAction = Mandate(at, DynamicValue.Null)
  }

  /**
   * Joins multiple source paths into a single value using a combiner.
   *
   * Used for combining fields (e.g., firstName + lastName → fullName).
   */
  final case class Join(
    at: DynamicOptic,
    sourcePaths: Chunk[DynamicOptic],
    combiner: DynamicTransform
  ) extends MigrationAction {
    def reverse: MigrationAction = Split(at, sourcePaths, combiner.reverse)
  }

  /**
   * Splits a single value into multiple target paths using a splitter.
   *
   * Used for decomposing fields (e.g., fullName → firstName, lastName).
   */
  final case class Split(
    at: DynamicOptic,
    targetPaths: Chunk[DynamicOptic],
    splitter: DynamicTransform
  ) extends MigrationAction {
    def reverse: MigrationAction = Join(at, targetPaths, splitter.reverse)
  }

  /**
   * Changes the type of a field using a converter expression.
   *
   * For primitive-to-primitive conversions only (e.g., String to Int).
   */
  final case class ChangeType(
    at: DynamicOptic,
    converter: DynamicTransform
  ) extends MigrationAction {
    def reverse: MigrationAction = ChangeType(at, converter.reverse)
  }

  // ═══════════════════════════════════════════════════════════════════════════════
  // Enum Actions
  // ═══════════════════════════════════════════════════════════════════════════════

  /**
   * Renames a case in a sum type (enum/sealed trait).
   *
   * The reverse action renames the case back.
   */
  final case class RenameCase(
    at: DynamicOptic,
    from: String,
    to: String
  ) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(at, to, from)
  }

  /**
   * Transforms the contents of a specific case in a sum type.
   *
   * The actions are applied to the case value.
   */
  final case class TransformCase(
    at: DynamicOptic,
    caseName: String,
    actions: Chunk[MigrationAction]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformCase(at, caseName, actions.map(_.reverse))
  }

  // ═══════════════════════════════════════════════════════════════════════════════
  // Collection / Map Actions
  // ═══════════════════════════════════════════════════════════════════════════════

  /**
   * Transforms each element in a sequence.
   *
   * The transform is applied to all elements at the path.
   */
  final case class TransformElements(
    at: DynamicOptic,
    transform: DynamicTransform
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformElements(at, transform.reverse)
  }

  /**
   * Transforms all keys in a map.
   */
  final case class TransformKeys(
    at: DynamicOptic,
    transform: DynamicTransform
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformKeys(at, transform.reverse)
  }

  /**
   * Transforms all values in a map.
   */
  final case class TransformValues(
    at: DynamicOptic,
    transform: DynamicTransform
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformValues(at, transform.reverse)
  }

  // ═══════════════════════════════════════════════════════════════════════════════
  // Helper Methods
  // ═══════════════════════════════════════════════════════════════════════════════

  private def fieldNameFromPath(optic: DynamicOptic): String =
    optic.nodes.lastOption match {
      case Some(DynamicOptic.Node.Field(name)) => name
      case _                                   => ""
    }

  // ═══════════════════════════════════════════════════════════════════════════════
  // Schema Instance
  // ═══════════════════════════════════════════════════════════════════════════════

  // Individual schemas for each case class
  private val addFieldSchema: Schema[AddField] = new Schema(
    reflect = new Reflect.Record[Binding, AddField](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[DynamicValue].reflect.asTerm("default")
      ),
      typeId = TypeId.of[AddField],
      recordBinding = new Binding.Record(
        constructor = new Constructor[AddField] {
          def construct(fields: Chunk[Any]): AddField = {
            val at = fields(0).asInstanceOf[DynamicOptic]
            val default = fields(1).asInstanceOf[DynamicValue]
            AddField(at, default)
          }
        },
        accessor = new RecordAccessor[AddField] {
          def get(record: AddField): Chunk[Any] = Chunk(record.at, record.default)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private val dropFieldSchema: Schema[DropField] = new Schema(
    reflect = new Reflect.Record[Binding, DropField](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[DynamicValue].reflect.asTerm("defaultForReverse")
      ),
      typeId = TypeId.of[DropField],
      recordBinding = new Binding.Record(
        constructor = new Constructor[DropField] {
          def construct(fields: Chunk[Any]): DropField = {
            val at = fields(0).asInstanceOf[DynamicOptic]
            val defaultForReverse = fields(1).asInstanceOf[DynamicValue]
            DropField(at, defaultForReverse)
          }
        },
        accessor = new RecordAccessor[DropField] {
          def get(record: DropField): Chunk[Any] = Chunk(record.at, record.defaultForReverse)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private val renameSchema: Schema[Rename] = new Schema(
    reflect = new Reflect.Record[Binding, Rename](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[String].reflect.asTerm("to")
      ),
      typeId = TypeId.of[Rename],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Rename] {
          def construct(fields: Chunk[Any]): Rename = {
            val at = fields(0).asInstanceOf[DynamicOptic]
            val to = fields(1).asInstanceOf[String]
            Rename(at, to)
          }
        },
        accessor = new RecordAccessor[Rename] {
          def get(record: Rename): Chunk[Any] = Chunk(record.at, record.to)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private val transformValueSchema: Schema[TransformValue] = new Schema(
    reflect = new Reflect.Record[Binding, TransformValue](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[DynamicTransform].reflect.asTerm("transform")
      ),
      typeId = TypeId.of[TransformValue],
      recordBinding = new Binding.Record(
        constructor = new Constructor[TransformValue] {
          def construct(fields: Chunk[Any]): TransformValue = {
            val at = fields(0).asInstanceOf[DynamicOptic]
            val transform = fields(1).asInstanceOf[DynamicTransform]
            TransformValue(at, transform)
          }
        },
        accessor = new RecordAccessor[TransformValue] {
          def get(record: TransformValue): Chunk[Any] = Chunk(record.at, record.transform)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private val mandateSchema: Schema[Mandate] = new Schema(
    reflect = new Reflect.Record[Binding, Mandate](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[DynamicValue].reflect.asTerm("default")
      ),
      typeId = TypeId.of[Mandate],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Mandate] {
          def construct(fields: Chunk[Any]): Mandate = {
            val at = fields(0).asInstanceOf[DynamicOptic]
            val default = fields(1).asInstanceOf[DynamicValue]
            Mandate(at, default)
          }
        },
        accessor = new RecordAccessor[Mandate] {
          def get(record: Mandate): Chunk[Any] = Chunk(record.at, record.default)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private val optionalizeSchema: Schema[Optionalize] = new Schema(
    reflect = new Reflect.Record[Binding, Optionalize](
      fields = Chunk.single(
        Schema[DynamicOptic].reflect.asTerm("at")
      ),
      typeId = TypeId.of[Optionalize],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Optionalize] {
          def construct(fields: Chunk[Any]): Optionalize = {
            val at = fields(0).asInstanceOf[DynamicOptic]
            Optionalize(at)
          }
        },
        accessor = new RecordAccessor[Optionalize] {
          def get(record: Optionalize): Chunk[Any] = Chunk(record.at)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private val joinSchema: Schema[Join] = new Schema(
    reflect = new Reflect.Record[Binding, Join](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[Chunk[DynamicOptic]].reflect.asTerm("sourcePaths"),
        Schema[DynamicTransform].reflect.asTerm("combiner")
      ),
      typeId = TypeId.of[Join],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Join] {
          def construct(fields: Chunk[Any]): Join = {
            val at = fields(0).asInstanceOf[DynamicOptic]
            val sourcePaths = fields(1).asInstanceOf[Chunk[DynamicOptic]]
            val combiner = fields(2).asInstanceOf[DynamicTransform]
            Join(at, sourcePaths, combiner)
          }
        },
        accessor = new RecordAccessor[Join] {
          def get(record: Join): Chunk[Any] = Chunk(record.at, record.sourcePaths, record.combiner)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private val splitSchema: Schema[Split] = new Schema(
    reflect = new Reflect.Record[Binding, Split](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[Chunk[DynamicOptic]].reflect.asTerm("targetPaths"),
        Schema[DynamicTransform].reflect.asTerm("splitter")
      ),
      typeId = TypeId.of[Split],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Split] {
          def construct(fields: Chunk[Any]): Split = {
            val at = fields(0).asInstanceOf[DynamicOptic]
            val targetPaths = fields(1).asInstanceOf[Chunk[DynamicOptic]]
            val splitter = fields(2).asInstanceOf[DynamicTransform]
            Split(at, targetPaths, splitter)
          }
        },
        accessor = new RecordAccessor[Split] {
          def get(record: Split): Chunk[Any] = Chunk(record.at, record.targetPaths, record.splitter)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private val changeTypeSchema: Schema[ChangeType] = new Schema(
    reflect = new Reflect.Record[Binding, ChangeType](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[DynamicTransform].reflect.asTerm("converter")
      ),
      typeId = TypeId.of[ChangeType],
      recordBinding = new Binding.Record(
        constructor = new Constructor[ChangeType] {
          def construct(fields: Chunk[Any]): ChangeType = {
            val at = fields(0).asInstanceOf[DynamicOptic]
            val converter = fields(1).asInstanceOf[DynamicTransform]
            ChangeType(at, converter)
          }
        },
        accessor = new RecordAccessor[ChangeType] {
          def get(record: ChangeType): Chunk[Any] = Chunk(record.at, record.converter)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private val renameCaseSchema: Schema[RenameCase] = new Schema(
    reflect = new Reflect.Record[Binding, RenameCase](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[String].reflect.asTerm("from"),
        Schema[String].reflect.asTerm("to")
      ),
      typeId = TypeId.of[RenameCase],
      recordBinding = new Binding.Record(
        constructor = new Constructor[RenameCase] {
          def construct(fields: Chunk[Any]): RenameCase = {
            val at = fields(0).asInstanceOf[DynamicOptic]
            val from = fields(1).asInstanceOf[String]
            val to = fields(2).asInstanceOf[String]
            RenameCase(at, from, to)
          }
        },
        accessor = new RecordAccessor[RenameCase] {
          def get(record: RenameCase): Chunk[Any] = Chunk(record.at, record.from, record.to)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private val transformCaseSchema: Schema[TransformCase] = new Schema(
    reflect = new Reflect.Record[Binding, TransformCase](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[String].reflect.asTerm("caseName"),
        Schema[Chunk[MigrationAction]].reflect.asTerm("actions")
      ),
      typeId = TypeId.of[TransformCase],
      recordBinding = new Binding.Record(
        constructor = new Constructor[TransformCase] {
          def construct(fields: Chunk[Any]): TransformCase = {
            val at = fields(0).asInstanceOf[DynamicOptic]
            val caseName = fields(1).asInstanceOf[String]
            val actions = fields(2).asInstanceOf[Chunk[MigrationAction]]
            TransformCase(at, caseName, actions)
          }
        },
        accessor = new RecordAccessor[TransformCase] {
          def get(record: TransformCase): Chunk[Any] = Chunk(record.at, record.caseName, record.actions)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private val transformElementsSchema: Schema[TransformElements] = new Schema(
    reflect = new Reflect.Record[Binding, TransformElements](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[DynamicTransform].reflect.asTerm("transform")
      ),
      typeId = TypeId.of[TransformElements],
      recordBinding = new Binding.Record(
        constructor = new Constructor[TransformElements] {
          def construct(fields: Chunk[Any]): TransformElements = {
            val at = fields(0).asInstanceOf[DynamicOptic]
            val transform = fields(1).asInstanceOf[DynamicTransform]
            TransformElements(at, transform)
          }
        },
        accessor = new RecordAccessor[TransformElements] {
          def get(record: TransformElements): Chunk[Any] = Chunk(record.at, record.transform)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private val transformKeysSchema: Schema[TransformKeys] = new Schema(
    reflect = new Reflect.Record[Binding, TransformKeys](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[DynamicTransform].reflect.asTerm("transform")
      ),
      typeId = TypeId.of[TransformKeys],
      recordBinding = new Binding.Record(
        constructor = new Constructor[TransformKeys] {
          def construct(fields: Chunk[Any]): TransformKeys = {
            val at = fields(0).asInstanceOf[DynamicOptic]
            val transform = fields(1).asInstanceOf[DynamicTransform]
            TransformKeys(at, transform)
          }
        },
        accessor = new RecordAccessor[TransformKeys] {
          def get(record: TransformKeys): Chunk[Any] = Chunk(record.at, record.transform)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private val transformValuesSchema: Schema[TransformValues] = new Schema(
    reflect = new Reflect.Record[Binding, TransformValues](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[DynamicTransform].reflect.asTerm("transform")
      ),
      typeId = TypeId.of[TransformValues],
      recordBinding = new Binding.Record(
        constructor = new Constructor[TransformValues] {
          def construct(fields: Chunk[Any]): TransformValues = {
            val at = fields(0).asInstanceOf[DynamicOptic]
            val transform = fields(1).asInstanceOf[DynamicTransform]
            TransformValues(at, transform)
          }
        },
        accessor = new RecordAccessor[TransformValues] {
          def get(record: TransformValues): Chunk[Any] = Chunk(record.at, record.transform)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  // Variant schema for MigrationAction
  implicit lazy val schema: Schema[MigrationAction] = new Schema(
    reflect = new Reflect.Variant[Binding, MigrationAction](
      cases = Chunk(
        addFieldSchema.reflect.asTerm("addField"),
        dropFieldSchema.reflect.asTerm("dropField"),
        renameSchema.reflect.asTerm("rename"),
        transformValueSchema.reflect.asTerm("transformValue"),
        mandateSchema.reflect.asTerm("mandate"),
        optionalizeSchema.reflect.asTerm("optionalize"),
        joinSchema.reflect.asTerm("join"),
        splitSchema.reflect.asTerm("split"),
        changeTypeSchema.reflect.asTerm("changeType"),
        renameCaseSchema.reflect.asTerm("renameCase"),
        transformCaseSchema.reflect.asTerm("transformCase"),
        transformElementsSchema.reflect.asTerm("transformElements"),
        transformKeysSchema.reflect.asTerm("transformKeys"),
        transformValuesSchema.reflect.asTerm("transformValues")
      ),
      typeId = TypeId.of[MigrationAction],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[MigrationAction] {
          def discriminate(a: MigrationAction): Int = a match {
            case _: AddField => 0
            case _: DropField => 1
            case _: Rename => 2
            case _: TransformValue => 3
            case _: Mandate => 4
            case _: Optionalize => 5
            case _: Join => 6
            case _: Split => 7
            case _: ChangeType => 8
            case _: RenameCase => 9
            case _: TransformCase => 10
            case _: TransformElements => 11
            case _: TransformKeys => 12
            case _: TransformValues => 13
          }
        },
        matchers = Matchers(
          new Matcher[AddField] {
            def downcastOrNull(a: Any): AddField = a match {
              case x: AddField => x
              case _ => null.asInstanceOf[AddField]
            }
          },
          new Matcher[DropField] {
            def downcastOrNull(a: Any): DropField = a match {
              case x: DropField => x
              case _ => null.asInstanceOf[DropField]
            }
          },
          new Matcher[Rename] {
            def downcastOrNull(a: Any): Rename = a match {
              case x: Rename => x
              case _ => null.asInstanceOf[Rename]
            }
          },
          new Matcher[TransformValue] {
            def downcastOrNull(a: Any): TransformValue = a match {
              case x: TransformValue => x
              case _ => null.asInstanceOf[TransformValue]
            }
          },
          new Matcher[Mandate] {
            def downcastOrNull(a: Any): Mandate = a match {
              case x: Mandate => x
              case _ => null.asInstanceOf[Mandate]
            }
          },
          new Matcher[Optionalize] {
            def downcastOrNull(a: Any): Optionalize = a match {
              case x: Optionalize => x
              case _ => null.asInstanceOf[Optionalize]
            }
          },
          new Matcher[Join] {
            def downcastOrNull(a: Any): Join = a match {
              case x: Join => x
              case _ => null.asInstanceOf[Join]
            }
          },
          new Matcher[Split] {
            def downcastOrNull(a: Any): Split = a match {
              case x: Split => x
              case _ => null.asInstanceOf[Split]
            }
          },
          new Matcher[ChangeType] {
            def downcastOrNull(a: Any): ChangeType = a match {
              case x: ChangeType => x
              case _ => null.asInstanceOf[ChangeType]
            }
          },
          new Matcher[RenameCase] {
            def downcastOrNull(a: Any): RenameCase = a match {
              case x: RenameCase => x
              case _ => null.asInstanceOf[RenameCase]
            }
          },
          new Matcher[TransformCase] {
            def downcastOrNull(a: Any): TransformCase = a match {
              case x: TransformCase => x
              case _ => null.asInstanceOf[TransformCase]
            }
          },
          new Matcher[TransformElements] {
            def downcastOrNull(a: Any): TransformElements = a match {
              case x: TransformElements => x
              case _ => null.asInstanceOf[TransformElements]
            }
          },
          new Matcher[TransformKeys] {
            def downcastOrNull(a: Any): TransformKeys = a match {
              case x: TransformKeys => x
              case _ => null.asInstanceOf[TransformKeys]
            }
          },
          new Matcher[TransformValues] {
            def downcastOrNull(a: Any): TransformValues = a match {
              case x: TransformValues => x
              case _ => null.asInstanceOf[TransformValues]
            }
          }
        )
      ),
      modifiers = Chunk.empty
    )
  )
}
