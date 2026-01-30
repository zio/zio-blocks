package zio.blocks.schema

import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset

/**
 * A single migration action that transforms a `DynamicValue` at a specific
 * path.
 *
 * All actions operate at a path represented by `DynamicOptic`, enabling
 * path-based diagnostics and introspection. Each action supports structural
 * reversal via `reverse`.
 *
 * The ADT is fully serializable (no functions or closures) and can be used to
 * generate DDL, upgraders, downgraders, and offline data transforms.
 */
sealed trait MigrationAction {

  /**
   * The path in the value structure where this action operates.
   */
  def at: DynamicOptic

  /**
   * The structural reverse of this action.
   *
   * Satisfies the law: `action.reverse.reverse == action`
   */
  def reverse: MigrationAction
}

object MigrationAction {

  // ----- Record operations -----

  /**
   * Add a field at the given path with a default value.
   *
   * Reverse: `DropField` at the same path, using the default for reverse.
   */
  final case class AddField(
    at: DynamicOptic,
    fieldName: String,
    default: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = DropField(at, fieldName, default)
  }

  /**
   * Drop a field at the given path.
   *
   * Requires a `defaultForReverse` so that the reverse (`AddField`) can
   * reconstruct the dropped field.
   */
  final case class DropField(
    at: DynamicOptic,
    fieldName: String,
    defaultForReverse: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = AddField(at, fieldName, defaultForReverse)
  }

  /**
   * Rename a field from `fromName` to `toName` at the given path.
   *
   * Reverse: rename from `toName` back to `fromName`.
   */
  final case class Rename(
    at: DynamicOptic,
    fromName: String,
    toName: String
  ) extends MigrationAction {
    def reverse: MigrationAction = Rename(at, toName, fromName)
  }

  /**
   * Transform the value at a path using a serializable expression.
   *
   * The `transform` is a `DynamicValue` encoding of the transformation (e.g. a
   * primitive conversion). The `reverseTransform` enables structural reversal.
   */
  final case class TransformValue(
    at: DynamicOptic,
    transform: DynamicValue,
    reverseTransform: Option[DynamicValue]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformValue(at, reverseTransform.getOrElse(transform), Some(transform))
  }

  /**
   * Make an optional field mandatory, providing a default for `None` values.
   *
   * Reverse: `Optionalize`.
   */
  final case class Mandate(
    at: DynamicOptic,
    default: DynamicValue
  ) extends MigrationAction {
    def reverse: MigrationAction = Optionalize(at)
  }

  /**
   * Make a mandatory field optional (wraps values in `Some`).
   *
   * Reverse: `Mandate` (requires a default; uses `DynamicValue.Null` as
   * fallback).
   */
  final case class Optionalize(
    at: DynamicOptic
  ) extends MigrationAction {
    def reverse: MigrationAction = Mandate(at, DynamicValue.Null)
  }

  /**
   * Join multiple source fields into a single target field.
   *
   * Reverse: `Split` with the inverse mapping.
   */
  final case class Join(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: DynamicValue,
    splitter: Option[DynamicValue]
  ) extends MigrationAction {
    def reverse: MigrationAction = Split(at, sourcePaths, splitter.getOrElse(combiner), Some(combiner))
  }

  /**
   * Split a single source field into multiple target fields.
   *
   * Reverse: `Join` with the inverse mapping.
   */
  final case class Split(
    at: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: DynamicValue,
    combiner: Option[DynamicValue]
  ) extends MigrationAction {
    def reverse: MigrationAction = Join(at, targetPaths, combiner.getOrElse(splitter), Some(splitter))
  }

  /**
   * Change the type of a field (primitive-to-primitive only).
   *
   * The `converter` encodes how to convert the old type to the new type. The
   * `reverseConverter` enables structural reversal.
   */
  final case class ChangeType(
    at: DynamicOptic,
    converter: DynamicValue,
    reverseConverter: Option[DynamicValue]
  ) extends MigrationAction {
    def reverse: MigrationAction = ChangeType(at, reverseConverter.getOrElse(converter), Some(converter))
  }

  // ----- Enum operations -----

  /**
   * Rename a variant case from `fromName` to `toName`.
   *
   * Reverse: rename from `toName` back to `fromName`.
   */
  final case class RenameCase(
    at: DynamicOptic,
    fromName: String,
    toName: String
  ) extends MigrationAction {
    def reverse: MigrationAction = RenameCase(at, toName, fromName)
  }

  /**
   * Transform a variant case's inner value using nested migration actions.
   *
   * Reverse: apply reversed nested actions.
   */
  final case class TransformCase(
    at: DynamicOptic,
    caseName: String,
    actions: Vector[MigrationAction]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformCase(at, caseName, actions.reverse.map(_.reverse))
  }

  // ----- Collection / Map operations -----

  /**
   * Transform all elements in a sequence at the given path.
   *
   * The `transform` encodes the element-level transformation.
   */
  final case class TransformElements(
    at: DynamicOptic,
    transform: DynamicValue,
    reverseTransform: Option[DynamicValue]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformElements(at, reverseTransform.getOrElse(transform), Some(transform))
  }

  /**
   * Transform all keys in a map at the given path.
   */
  final case class TransformKeys(
    at: DynamicOptic,
    transform: DynamicValue,
    reverseTransform: Option[DynamicValue]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformKeys(at, reverseTransform.getOrElse(transform), Some(transform))
  }

  /**
   * Transform all values in a map at the given path.
   */
  final case class TransformValues(
    at: DynamicOptic,
    transform: DynamicValue,
    reverseTransform: Option[DynamicValue]
  ) extends MigrationAction {
    def reverse: MigrationAction = TransformValues(at, reverseTransform.getOrElse(transform), Some(transform))
  }

  // ----- Schema instances for serialization -----

  private val ns = new Namespace(List("zio", "blocks", "schema", "MigrationAction"))

  // Helper to increment object offset N times
  private def objOffset(base: RegisterOffset, n: Int): RegisterOffset = {
    var o = base
    var i = 0
    while (i < n) { o = RegisterOffset.incrementObjects(o); i += 1 }
    o
  }

  implicit lazy val addFieldSchema: Schema[AddField] = new Schema(
    reflect = new Reflect.Record[Binding, AddField](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[String].reflect.asTerm("fieldName"),
        Schema[DynamicValue].reflect.asTerm("default")
      ),
      typeName = new TypeName(ns, "AddField"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[AddField] {
          def usedRegisters: RegisterOffset                              = RegisterOffset(objects = 3)
          def construct(in: Registers, offset: RegisterOffset): AddField =
            AddField(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getObject(objOffset(offset, 1)).asInstanceOf[String],
              in.getObject(objOffset(offset, 2)).asInstanceOf[DynamicValue]
            )
        },
        deconstructor = new Deconstructor[AddField] {
          def usedRegisters: RegisterOffset                                           = RegisterOffset(objects = 3)
          def deconstruct(out: Registers, offset: RegisterOffset, in: AddField): Unit = {
            out.setObject(offset, in.at)
            out.setObject(objOffset(offset, 1), in.fieldName)
            out.setObject(objOffset(offset, 2), in.default)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val dropFieldSchema: Schema[DropField] = new Schema(
    reflect = new Reflect.Record[Binding, DropField](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[String].reflect.asTerm("fieldName"),
        Schema[DynamicValue].reflect.asTerm("defaultForReverse")
      ),
      typeName = new TypeName(ns, "DropField"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[DropField] {
          def usedRegisters: RegisterOffset                               = RegisterOffset(objects = 3)
          def construct(in: Registers, offset: RegisterOffset): DropField =
            DropField(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getObject(objOffset(offset, 1)).asInstanceOf[String],
              in.getObject(objOffset(offset, 2)).asInstanceOf[DynamicValue]
            )
        },
        deconstructor = new Deconstructor[DropField] {
          def usedRegisters: RegisterOffset                                            = RegisterOffset(objects = 3)
          def deconstruct(out: Registers, offset: RegisterOffset, in: DropField): Unit = {
            out.setObject(offset, in.at)
            out.setObject(objOffset(offset, 1), in.fieldName)
            out.setObject(objOffset(offset, 2), in.defaultForReverse)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val renameSchema: Schema[Rename] = new Schema(
    reflect = new Reflect.Record[Binding, Rename](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[String].reflect.asTerm("fromName"),
        Schema[String].reflect.asTerm("toName")
      ),
      typeName = new TypeName(ns, "Rename"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Rename] {
          def usedRegisters: RegisterOffset                            = RegisterOffset(objects = 3)
          def construct(in: Registers, offset: RegisterOffset): Rename =
            Rename(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getObject(objOffset(offset, 1)).asInstanceOf[String],
              in.getObject(objOffset(offset, 2)).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[Rename] {
          def usedRegisters: RegisterOffset                                         = RegisterOffset(objects = 3)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Rename): Unit = {
            out.setObject(offset, in.at)
            out.setObject(objOffset(offset, 1), in.fromName)
            out.setObject(objOffset(offset, 2), in.toName)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val transformValueSchema: Schema[TransformValue] = new Schema(
    reflect = new Reflect.Record[Binding, TransformValue](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[DynamicValue].reflect.asTerm("transform"),
        Schema[Option[DynamicValue]].reflect.asTerm("reverseTransform")
      ),
      typeName = new TypeName(ns, "TransformValue"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[TransformValue] {
          def usedRegisters: RegisterOffset                                    = RegisterOffset(objects = 3)
          def construct(in: Registers, offset: RegisterOffset): TransformValue =
            TransformValue(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getObject(objOffset(offset, 1)).asInstanceOf[DynamicValue],
              in.getObject(objOffset(offset, 2)).asInstanceOf[Option[DynamicValue]]
            )
        },
        deconstructor = new Deconstructor[TransformValue] {
          def usedRegisters: RegisterOffset                                                 = RegisterOffset(objects = 3)
          def deconstruct(out: Registers, offset: RegisterOffset, in: TransformValue): Unit = {
            out.setObject(offset, in.at)
            out.setObject(objOffset(offset, 1), in.transform)
            out.setObject(objOffset(offset, 2), in.reverseTransform)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val mandateSchema: Schema[Mandate] = new Schema(
    reflect = new Reflect.Record[Binding, Mandate](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[DynamicValue].reflect.asTerm("default")
      ),
      typeName = new TypeName(ns, "Mandate"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Mandate] {
          def usedRegisters: RegisterOffset                             = RegisterOffset(objects = 2)
          def construct(in: Registers, offset: RegisterOffset): Mandate =
            Mandate(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getObject(objOffset(offset, 1)).asInstanceOf[DynamicValue]
            )
        },
        deconstructor = new Deconstructor[Mandate] {
          def usedRegisters: RegisterOffset                                          = RegisterOffset(objects = 2)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Mandate): Unit = {
            out.setObject(offset, in.at)
            out.setObject(objOffset(offset, 1), in.default)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val optionalizeSchema: Schema[Optionalize] = new Schema(
    reflect = new Reflect.Record[Binding, Optionalize](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at")
      ),
      typeName = new TypeName(ns, "Optionalize"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Optionalize] {
          def usedRegisters: RegisterOffset                                 = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): Optionalize =
            Optionalize(in.getObject(offset).asInstanceOf[DynamicOptic])
        },
        deconstructor = new Deconstructor[Optionalize] {
          def usedRegisters: RegisterOffset                                              = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Optionalize): Unit =
            out.setObject(offset, in.at)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val joinSchema: Schema[Join] = new Schema(
    reflect = new Reflect.Record[Binding, Join](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[Vector[DynamicOptic]].reflect.asTerm("sourcePaths"),
        Schema[DynamicValue].reflect.asTerm("combiner"),
        Schema[Option[DynamicValue]].reflect.asTerm("splitter")
      ),
      typeName = new TypeName(ns, "Join"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Join] {
          def usedRegisters: RegisterOffset                          = RegisterOffset(objects = 4)
          def construct(in: Registers, offset: RegisterOffset): Join =
            Join(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getObject(objOffset(offset, 1)).asInstanceOf[Vector[DynamicOptic]],
              in.getObject(objOffset(offset, 2)).asInstanceOf[DynamicValue],
              in.getObject(objOffset(offset, 3)).asInstanceOf[Option[DynamicValue]]
            )
        },
        deconstructor = new Deconstructor[Join] {
          def usedRegisters: RegisterOffset                                       = RegisterOffset(objects = 4)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Join): Unit = {
            out.setObject(offset, in.at)
            out.setObject(objOffset(offset, 1), in.sourcePaths)
            out.setObject(objOffset(offset, 2), in.combiner)
            out.setObject(objOffset(offset, 3), in.splitter)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val splitSchema: Schema[Split] = new Schema(
    reflect = new Reflect.Record[Binding, Split](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[Vector[DynamicOptic]].reflect.asTerm("targetPaths"),
        Schema[DynamicValue].reflect.asTerm("splitter"),
        Schema[Option[DynamicValue]].reflect.asTerm("combiner")
      ),
      typeName = new TypeName(ns, "Split"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[Split] {
          def usedRegisters: RegisterOffset                           = RegisterOffset(objects = 4)
          def construct(in: Registers, offset: RegisterOffset): Split =
            Split(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getObject(objOffset(offset, 1)).asInstanceOf[Vector[DynamicOptic]],
              in.getObject(objOffset(offset, 2)).asInstanceOf[DynamicValue],
              in.getObject(objOffset(offset, 3)).asInstanceOf[Option[DynamicValue]]
            )
        },
        deconstructor = new Deconstructor[Split] {
          def usedRegisters: RegisterOffset                                        = RegisterOffset(objects = 4)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Split): Unit = {
            out.setObject(offset, in.at)
            out.setObject(objOffset(offset, 1), in.targetPaths)
            out.setObject(objOffset(offset, 2), in.splitter)
            out.setObject(objOffset(offset, 3), in.combiner)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val changeTypeSchema: Schema[ChangeType] = new Schema(
    reflect = new Reflect.Record[Binding, ChangeType](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[DynamicValue].reflect.asTerm("converter"),
        Schema[Option[DynamicValue]].reflect.asTerm("reverseConverter")
      ),
      typeName = new TypeName(ns, "ChangeType"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[ChangeType] {
          def usedRegisters: RegisterOffset                                = RegisterOffset(objects = 3)
          def construct(in: Registers, offset: RegisterOffset): ChangeType =
            ChangeType(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getObject(objOffset(offset, 1)).asInstanceOf[DynamicValue],
              in.getObject(objOffset(offset, 2)).asInstanceOf[Option[DynamicValue]]
            )
        },
        deconstructor = new Deconstructor[ChangeType] {
          def usedRegisters: RegisterOffset                                             = RegisterOffset(objects = 3)
          def deconstruct(out: Registers, offset: RegisterOffset, in: ChangeType): Unit = {
            out.setObject(offset, in.at)
            out.setObject(objOffset(offset, 1), in.converter)
            out.setObject(objOffset(offset, 2), in.reverseConverter)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val renameCaseSchema: Schema[RenameCase] = new Schema(
    reflect = new Reflect.Record[Binding, RenameCase](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[String].reflect.asTerm("fromName"),
        Schema[String].reflect.asTerm("toName")
      ),
      typeName = new TypeName(ns, "RenameCase"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[RenameCase] {
          def usedRegisters: RegisterOffset                                = RegisterOffset(objects = 3)
          def construct(in: Registers, offset: RegisterOffset): RenameCase =
            RenameCase(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getObject(objOffset(offset, 1)).asInstanceOf[String],
              in.getObject(objOffset(offset, 2)).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[RenameCase] {
          def usedRegisters: RegisterOffset                                             = RegisterOffset(objects = 3)
          def deconstruct(out: Registers, offset: RegisterOffset, in: RenameCase): Unit = {
            out.setObject(offset, in.at)
            out.setObject(objOffset(offset, 1), in.fromName)
            out.setObject(objOffset(offset, 2), in.toName)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val transformCaseSchema: Schema[TransformCase] = new Schema(
    reflect = new Reflect.Record[Binding, TransformCase](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[String].reflect.asTerm("caseName"),
        Schema[Vector[MigrationAction]].reflect.asTerm("actions")
      ),
      typeName = new TypeName(ns, "TransformCase"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[TransformCase] {
          def usedRegisters: RegisterOffset                                   = RegisterOffset(objects = 3)
          def construct(in: Registers, offset: RegisterOffset): TransformCase =
            TransformCase(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getObject(objOffset(offset, 1)).asInstanceOf[String],
              in.getObject(objOffset(offset, 2)).asInstanceOf[Vector[MigrationAction]]
            )
        },
        deconstructor = new Deconstructor[TransformCase] {
          def usedRegisters: RegisterOffset                                                = RegisterOffset(objects = 3)
          def deconstruct(out: Registers, offset: RegisterOffset, in: TransformCase): Unit = {
            out.setObject(offset, in.at)
            out.setObject(objOffset(offset, 1), in.caseName)
            out.setObject(objOffset(offset, 2), in.actions)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val transformElementsSchema: Schema[TransformElements] = new Schema(
    reflect = new Reflect.Record[Binding, TransformElements](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[DynamicValue].reflect.asTerm("transform"),
        Schema[Option[DynamicValue]].reflect.asTerm("reverseTransform")
      ),
      typeName = new TypeName(ns, "TransformElements"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[TransformElements] {
          def usedRegisters: RegisterOffset                                       = RegisterOffset(objects = 3)
          def construct(in: Registers, offset: RegisterOffset): TransformElements =
            TransformElements(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getObject(objOffset(offset, 1)).asInstanceOf[DynamicValue],
              in.getObject(objOffset(offset, 2)).asInstanceOf[Option[DynamicValue]]
            )
        },
        deconstructor = new Deconstructor[TransformElements] {
          def usedRegisters: RegisterOffset                                                    = RegisterOffset(objects = 3)
          def deconstruct(out: Registers, offset: RegisterOffset, in: TransformElements): Unit = {
            out.setObject(offset, in.at)
            out.setObject(objOffset(offset, 1), in.transform)
            out.setObject(objOffset(offset, 2), in.reverseTransform)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val transformKeysSchema: Schema[TransformKeys] = new Schema(
    reflect = new Reflect.Record[Binding, TransformKeys](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[DynamicValue].reflect.asTerm("transform"),
        Schema[Option[DynamicValue]].reflect.asTerm("reverseTransform")
      ),
      typeName = new TypeName(ns, "TransformKeys"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[TransformKeys] {
          def usedRegisters: RegisterOffset                                   = RegisterOffset(objects = 3)
          def construct(in: Registers, offset: RegisterOffset): TransformKeys =
            TransformKeys(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getObject(objOffset(offset, 1)).asInstanceOf[DynamicValue],
              in.getObject(objOffset(offset, 2)).asInstanceOf[Option[DynamicValue]]
            )
        },
        deconstructor = new Deconstructor[TransformKeys] {
          def usedRegisters: RegisterOffset                                                = RegisterOffset(objects = 3)
          def deconstruct(out: Registers, offset: RegisterOffset, in: TransformKeys): Unit = {
            out.setObject(offset, in.at)
            out.setObject(objOffset(offset, 1), in.transform)
            out.setObject(objOffset(offset, 2), in.reverseTransform)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val transformValuesSchema: Schema[TransformValues] = new Schema(
    reflect = new Reflect.Record[Binding, TransformValues](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[DynamicValue].reflect.asTerm("transform"),
        Schema[Option[DynamicValue]].reflect.asTerm("reverseTransform")
      ),
      typeName = new TypeName(ns, "TransformValues"),
      recordBinding = new Binding.Record(
        constructor = new Constructor[TransformValues] {
          def usedRegisters: RegisterOffset                                     = RegisterOffset(objects = 3)
          def construct(in: Registers, offset: RegisterOffset): TransformValues =
            TransformValues(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getObject(objOffset(offset, 1)).asInstanceOf[DynamicValue],
              in.getObject(objOffset(offset, 2)).asInstanceOf[Option[DynamicValue]]
            )
        },
        deconstructor = new Deconstructor[TransformValues] {
          def usedRegisters: RegisterOffset                                                  = RegisterOffset(objects = 3)
          def deconstruct(out: Registers, offset: RegisterOffset, in: TransformValues): Unit = {
            out.setObject(offset, in.at)
            out.setObject(objOffset(offset, 1), in.transform)
            out.setObject(objOffset(offset, 2), in.reverseTransform)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  // Schema for the MigrationAction sealed trait (Variant)
  implicit lazy val schema: Schema[MigrationAction] = new Schema(
    reflect = new Reflect.Variant[Binding, MigrationAction](
      cases = Vector(
        addFieldSchema.reflect.asTerm("AddField"),
        dropFieldSchema.reflect.asTerm("DropField"),
        renameSchema.reflect.asTerm("Rename"),
        transformValueSchema.reflect.asTerm("TransformValue"),
        mandateSchema.reflect.asTerm("Mandate"),
        optionalizeSchema.reflect.asTerm("Optionalize"),
        joinSchema.reflect.asTerm("Join"),
        splitSchema.reflect.asTerm("Split"),
        changeTypeSchema.reflect.asTerm("ChangeType"),
        renameCaseSchema.reflect.asTerm("RenameCase"),
        transformCaseSchema.reflect.asTerm("TransformCase"),
        transformElementsSchema.reflect.asTerm("TransformElements"),
        transformKeysSchema.reflect.asTerm("TransformKeys"),
        transformValuesSchema.reflect.asTerm("TransformValues")
      ),
      typeName = new TypeName(new Namespace(List("zio", "blocks", "schema")), "MigrationAction"),
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[MigrationAction] {
          def discriminate(a: MigrationAction): Int = a match {
            case _: AddField          => 0
            case _: DropField         => 1
            case _: Rename            => 2
            case _: TransformValue    => 3
            case _: Mandate           => 4
            case _: Optionalize       => 5
            case _: Join              => 6
            case _: Split             => 7
            case _: ChangeType        => 8
            case _: RenameCase        => 9
            case _: TransformCase     => 10
            case _: TransformElements => 11
            case _: TransformKeys     => 12
            case _: TransformValues   => 13
          }
        },
        matchers = Matchers(
          new Matcher[AddField] {
            def downcastOrNull(a: Any): AddField = a match {
              case x: AddField => x; case _ => null.asInstanceOf[AddField]
            }
          },
          new Matcher[DropField] {
            def downcastOrNull(a: Any): DropField = a match {
              case x: DropField => x; case _ => null.asInstanceOf[DropField]
            }
          },
          new Matcher[Rename] {
            def downcastOrNull(a: Any): Rename = a match { case x: Rename => x; case _ => null.asInstanceOf[Rename] }
          },
          new Matcher[TransformValue] {
            def downcastOrNull(a: Any): TransformValue = a match {
              case x: TransformValue => x; case _ => null.asInstanceOf[TransformValue]
            }
          },
          new Matcher[Mandate] {
            def downcastOrNull(a: Any): Mandate = a match { case x: Mandate => x; case _ => null.asInstanceOf[Mandate] }
          },
          new Matcher[Optionalize] {
            def downcastOrNull(a: Any): Optionalize = a match {
              case x: Optionalize => x; case _ => null.asInstanceOf[Optionalize]
            }
          },
          new Matcher[Join] {
            def downcastOrNull(a: Any): Join = a match { case x: Join => x; case _ => null.asInstanceOf[Join] }
          },
          new Matcher[Split] {
            def downcastOrNull(a: Any): Split = a match { case x: Split => x; case _ => null.asInstanceOf[Split] }
          },
          new Matcher[ChangeType] {
            def downcastOrNull(a: Any): ChangeType = a match {
              case x: ChangeType => x; case _ => null.asInstanceOf[ChangeType]
            }
          },
          new Matcher[RenameCase] {
            def downcastOrNull(a: Any): RenameCase = a match {
              case x: RenameCase => x; case _ => null.asInstanceOf[RenameCase]
            }
          },
          new Matcher[TransformCase] {
            def downcastOrNull(a: Any): TransformCase = a match {
              case x: TransformCase => x; case _ => null.asInstanceOf[TransformCase]
            }
          },
          new Matcher[TransformElements] {
            def downcastOrNull(a: Any): TransformElements = a match {
              case x: TransformElements => x; case _ => null.asInstanceOf[TransformElements]
            }
          },
          new Matcher[TransformKeys] {
            def downcastOrNull(a: Any): TransformKeys = a match {
              case x: TransformKeys => x; case _ => null.asInstanceOf[TransformKeys]
            }
          },
          new Matcher[TransformValues] {
            def downcastOrNull(a: Any): TransformValues = a match {
              case x: TransformValues => x; case _ => null.asInstanceOf[TransformValues]
            }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )
}
