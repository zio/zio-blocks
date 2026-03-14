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
import zio.blocks.schema.{DynamicValue, Reflect, Schema}
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.typeid.{Owner, TypeId}

/**
 * Hand-rolled Schema instances for the algebraic migration types.
 *
 * Schema.derived is a Scala 2 macro whose implementation lives in the same
 * compilation unit, so it cannot be expanded here. These instances are
 * structurally equivalent to what the macro would produce.
 */
object MigrationSchemas {

  private[this] val migrationOwner: Owner         = Owner.fromPackagePath("zio.blocks.schema.migration")
  private[this] val dynamicOpticOwner: Owner      = migrationOwner.tpe("DynamicOptic")
  private[this] val dynamicSchemaExprOwner: Owner = migrationOwner.tpe("DynamicSchemaExpr")
  private[this] val migrationActionOwner: Owner   = migrationOwner.tpe("MigrationAction")

  // ===========================================================================
  // DynamicOptic — sealed trait, 5 cases, all recursive via Option[DynamicOptic]
  // ===========================================================================

  private[this] lazy val dynamicOpticFieldSchema: Schema[DynamicOptic.Field] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicOptic.Field](
      fields = Chunk(
        Schema[String].reflect.asTerm("name"),
        new Reflect.Deferred[Binding, Option[DynamicOptic]](() => Schema[Option[DynamicOptic]].reflect).asTerm("next")
      ),
      typeId = TypeId.nominal[DynamicOptic.Field]("Field", dynamicOpticOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[DynamicOptic.Field] {
          def usedRegisters: RegisterOffset                                        = 2
          def construct(in: Registers, offset: RegisterOffset): DynamicOptic.Field =
            new DynamicOptic.Field(
              in.getObject(offset).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[Option[DynamicOptic]]
            )
        },
        deconstructor = new Deconstructor[DynamicOptic.Field] {
          def usedRegisters: RegisterOffset                                                     = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicOptic.Field): Unit = {
            out.setObject(offset, in.name)
            out.setObject(offset + 1, in.next)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private[this] lazy val dynamicOpticCaseSchema: Schema[DynamicOptic.Case] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicOptic.Case](
      fields = Chunk(
        Schema[String].reflect.asTerm("name"),
        new Reflect.Deferred[Binding, Option[DynamicOptic]](() => Schema[Option[DynamicOptic]].reflect).asTerm("next")
      ),
      typeId = TypeId.nominal[DynamicOptic.Case]("Case", dynamicOpticOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[DynamicOptic.Case] {
          def usedRegisters: RegisterOffset                                       = 2
          def construct(in: Registers, offset: RegisterOffset): DynamicOptic.Case =
            new DynamicOptic.Case(
              in.getObject(offset).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[Option[DynamicOptic]]
            )
        },
        deconstructor = new Deconstructor[DynamicOptic.Case] {
          def usedRegisters: RegisterOffset                                                    = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicOptic.Case): Unit = {
            out.setObject(offset, in.name)
            out.setObject(offset + 1, in.next)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private[this] lazy val dynamicOpticElementSchema: Schema[DynamicOptic.Element] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicOptic.Element](
      fields = Chunk(
        new Reflect.Deferred[Binding, Option[DynamicOptic]](() => Schema[Option[DynamicOptic]].reflect).asTerm("next")
      ),
      typeId = TypeId.nominal[DynamicOptic.Element]("Element", dynamicOpticOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[DynamicOptic.Element] {
          def usedRegisters: RegisterOffset                                          = 1
          def construct(in: Registers, offset: RegisterOffset): DynamicOptic.Element =
            new DynamicOptic.Element(in.getObject(offset).asInstanceOf[Option[DynamicOptic]])
        },
        deconstructor = new Deconstructor[DynamicOptic.Element] {
          def usedRegisters: RegisterOffset                                                       = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicOptic.Element): Unit =
            out.setObject(offset, in.next)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private[this] lazy val dynamicOpticKeySchema: Schema[DynamicOptic.Key] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicOptic.Key](
      fields = Chunk(
        new Reflect.Deferred[Binding, Option[DynamicOptic]](() => Schema[Option[DynamicOptic]].reflect).asTerm("next")
      ),
      typeId = TypeId.nominal[DynamicOptic.Key]("Key", dynamicOpticOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[DynamicOptic.Key] {
          def usedRegisters: RegisterOffset                                      = 1
          def construct(in: Registers, offset: RegisterOffset): DynamicOptic.Key =
            new DynamicOptic.Key(in.getObject(offset).asInstanceOf[Option[DynamicOptic]])
        },
        deconstructor = new Deconstructor[DynamicOptic.Key] {
          def usedRegisters: RegisterOffset                                                   = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicOptic.Key): Unit =
            out.setObject(offset, in.next)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private[this] lazy val dynamicOpticValueSchema: Schema[DynamicOptic.Value] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicOptic.Value](
      fields = Chunk(
        new Reflect.Deferred[Binding, Option[DynamicOptic]](() => Schema[Option[DynamicOptic]].reflect).asTerm("next")
      ),
      typeId = TypeId.nominal[DynamicOptic.Value]("Value", dynamicOpticOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[DynamicOptic.Value] {
          def usedRegisters: RegisterOffset                                        = 1
          def construct(in: Registers, offset: RegisterOffset): DynamicOptic.Value =
            new DynamicOptic.Value(in.getObject(offset).asInstanceOf[Option[DynamicOptic]])
        },
        deconstructor = new Deconstructor[DynamicOptic.Value] {
          def usedRegisters: RegisterOffset                                                     = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicOptic.Value): Unit =
            out.setObject(offset, in.next)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val dynamicOpticSchema: Schema[DynamicOptic] = new Schema(
    reflect = new Reflect.Variant[Binding, DynamicOptic](
      cases = Chunk(
        dynamicOpticFieldSchema.reflect.asTerm("Field"),
        dynamicOpticCaseSchema.reflect.asTerm("Case"),
        dynamicOpticElementSchema.reflect.asTerm("Element"),
        dynamicOpticKeySchema.reflect.asTerm("Key"),
        dynamicOpticValueSchema.reflect.asTerm("Value")
      ),
      typeId = TypeId.nominal[DynamicOptic]("DynamicOptic", migrationOwner),
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[DynamicOptic] {
          def discriminate(a: DynamicOptic): Int = a match {
            case _: DynamicOptic.Field   => 0
            case _: DynamicOptic.Case    => 1
            case _: DynamicOptic.Element => 2
            case _: DynamicOptic.Key     => 3
            case _: DynamicOptic.Value   => 4
          }
        },
        matchers = Matchers(
          new Matcher[DynamicOptic.Field] {
            def downcastOrNull(a: Any): DynamicOptic.Field = a match {
              case v: DynamicOptic.Field => v
              case _                     => null
            }
          },
          new Matcher[DynamicOptic.Case] {
            def downcastOrNull(a: Any): DynamicOptic.Case = a match {
              case v: DynamicOptic.Case => v
              case _                    => null
            }
          },
          new Matcher[DynamicOptic.Element] {
            def downcastOrNull(a: Any): DynamicOptic.Element = a match {
              case v: DynamicOptic.Element => v
              case _                       => null
            }
          },
          new Matcher[DynamicOptic.Key] {
            def downcastOrNull(a: Any): DynamicOptic.Key = a match {
              case v: DynamicOptic.Key => v
              case _                   => null
            }
          },
          new Matcher[DynamicOptic.Value] {
            def downcastOrNull(a: Any): DynamicOptic.Value = a match {
              case v: DynamicOptic.Value => v
              case _                     => null
            }
          }
        )
      ),
      modifiers = Chunk.empty
    )
  )

  // ===========================================================================
  // DynamicSchemaExpr — sealed trait, 5 cases, BiTransform is recursive
  // ===========================================================================

  private[this] lazy val defaultValueSchema: Schema[DynamicSchemaExpr.DefaultValue.type] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicSchemaExpr.DefaultValue.type](
      fields = Chunk.empty,
      typeId = TypeId.nominal[DynamicSchemaExpr.DefaultValue.type]("DefaultValue", dynamicSchemaExprOwner),
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[DynamicSchemaExpr.DefaultValue.type](DynamicSchemaExpr.DefaultValue),
        deconstructor = new ConstantDeconstructor[DynamicSchemaExpr.DefaultValue.type]
      ),
      modifiers = Chunk.empty
    )
  )

  private[this] lazy val literalSchema: Schema[DynamicSchemaExpr.Literal] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicSchemaExpr.Literal](
      fields = Chunk(Schema[DynamicValue].reflect.asTerm("value")),
      typeId = TypeId.nominal[DynamicSchemaExpr.Literal]("Literal", dynamicSchemaExprOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[DynamicSchemaExpr.Literal] {
          def usedRegisters: RegisterOffset                                               = 1
          def construct(in: Registers, offset: RegisterOffset): DynamicSchemaExpr.Literal =
            new DynamicSchemaExpr.Literal(in.getObject(offset).asInstanceOf[DynamicValue])
        },
        deconstructor = new Deconstructor[DynamicSchemaExpr.Literal] {
          def usedRegisters: RegisterOffset                                                            = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicSchemaExpr.Literal): Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private[this] lazy val convertPrimitiveSchema: Schema[DynamicSchemaExpr.ConvertPrimitive] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicSchemaExpr.ConvertPrimitive](
      fields = Chunk(
        Schema[String].reflect.asTerm("fromTypeId"),
        Schema[String].reflect.asTerm("toTypeId")
      ),
      typeId = TypeId.nominal[DynamicSchemaExpr.ConvertPrimitive]("ConvertPrimitive", dynamicSchemaExprOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[DynamicSchemaExpr.ConvertPrimitive] {
          def usedRegisters: RegisterOffset                                                        = 2
          def construct(in: Registers, offset: RegisterOffset): DynamicSchemaExpr.ConvertPrimitive =
            new DynamicSchemaExpr.ConvertPrimitive(
              in.getObject(offset).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[DynamicSchemaExpr.ConvertPrimitive] {
          def usedRegisters: RegisterOffset                                                                     = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicSchemaExpr.ConvertPrimitive): Unit = {
            out.setObject(offset, in.fromTypeId)
            out.setObject(offset + 1, in.toTypeId)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private[this] lazy val biTransformSchema: Schema[DynamicSchemaExpr.BiTransform] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicSchemaExpr.BiTransform](
      fields = Chunk(
        new Reflect.Deferred[Binding, DynamicSchemaExpr](() => Schema[DynamicSchemaExpr].reflect).asTerm("forward"),
        new Reflect.Deferred[Binding, DynamicSchemaExpr](() => Schema[DynamicSchemaExpr].reflect).asTerm("backward")
      ),
      typeId = TypeId.nominal[DynamicSchemaExpr.BiTransform]("BiTransform", dynamicSchemaExprOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[DynamicSchemaExpr.BiTransform] {
          def usedRegisters: RegisterOffset                                                   = 2
          def construct(in: Registers, offset: RegisterOffset): DynamicSchemaExpr.BiTransform =
            new DynamicSchemaExpr.BiTransform(
              in.getObject(offset).asInstanceOf[DynamicSchemaExpr],
              in.getObject(offset + 1).asInstanceOf[DynamicSchemaExpr]
            )
        },
        deconstructor = new Deconstructor[DynamicSchemaExpr.BiTransform] {
          def usedRegisters: RegisterOffset                                                                = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicSchemaExpr.BiTransform): Unit = {
            out.setObject(offset, in.forward)
            out.setObject(offset + 1, in.backward)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private[this] lazy val failSchema: Schema[DynamicSchemaExpr.Fail] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicSchemaExpr.Fail](
      fields = Chunk(Schema[String].reflect.asTerm("reason")),
      typeId = TypeId.nominal[DynamicSchemaExpr.Fail]("Fail", dynamicSchemaExprOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[DynamicSchemaExpr.Fail] {
          def usedRegisters: RegisterOffset                                            = 1
          def construct(in: Registers, offset: RegisterOffset): DynamicSchemaExpr.Fail =
            new DynamicSchemaExpr.Fail(in.getObject(offset).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[DynamicSchemaExpr.Fail] {
          def usedRegisters: RegisterOffset                                                         = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicSchemaExpr.Fail): Unit =
            out.setObject(offset, in.reason)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val dynamicSchemaExprSchema: Schema[DynamicSchemaExpr] = new Schema(
    reflect = new Reflect.Variant[Binding, DynamicSchemaExpr](
      cases = Chunk(
        defaultValueSchema.reflect.asTerm("DefaultValue"),
        literalSchema.reflect.asTerm("Literal"),
        convertPrimitiveSchema.reflect.asTerm("ConvertPrimitive"),
        biTransformSchema.reflect.asTerm("BiTransform"),
        failSchema.reflect.asTerm("Fail")
      ),
      typeId = TypeId.nominal[DynamicSchemaExpr]("DynamicSchemaExpr", migrationOwner),
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[DynamicSchemaExpr] {
          def discriminate(a: DynamicSchemaExpr): Int = a match {
            case DynamicSchemaExpr.DefaultValue        => 0
            case _: DynamicSchemaExpr.Literal          => 1
            case _: DynamicSchemaExpr.ConvertPrimitive => 2
            case _: DynamicSchemaExpr.BiTransform      => 3
            case _: DynamicSchemaExpr.Fail             => 4
          }
        },
        matchers = Matchers(
          new Matcher[DynamicSchemaExpr.DefaultValue.type] {
            def downcastOrNull(a: Any): DynamicSchemaExpr.DefaultValue.type = a match {
              case DynamicSchemaExpr.DefaultValue => DynamicSchemaExpr.DefaultValue
              case _                              => null.asInstanceOf[DynamicSchemaExpr.DefaultValue.type]
            }
          },
          new Matcher[DynamicSchemaExpr.Literal] {
            def downcastOrNull(a: Any): DynamicSchemaExpr.Literal = a match {
              case v: DynamicSchemaExpr.Literal => v
              case _                            => null
            }
          },
          new Matcher[DynamicSchemaExpr.ConvertPrimitive] {
            def downcastOrNull(a: Any): DynamicSchemaExpr.ConvertPrimitive = a match {
              case v: DynamicSchemaExpr.ConvertPrimitive => v
              case _                                     => null
            }
          },
          new Matcher[DynamicSchemaExpr.BiTransform] {
            def downcastOrNull(a: Any): DynamicSchemaExpr.BiTransform = a match {
              case v: DynamicSchemaExpr.BiTransform => v
              case _                                => null
            }
          },
          new Matcher[DynamicSchemaExpr.Fail] {
            def downcastOrNull(a: Any): DynamicSchemaExpr.Fail = a match {
              case v: DynamicSchemaExpr.Fail => v
              case _                         => null
            }
          }
        )
      ),
      modifiers = Chunk.empty
    )
  )

  // ===========================================================================
  // MigrationAction — sealed trait, 14 cases, TransformCase is recursive
  // ===========================================================================

  private[this] lazy val addFieldSchema: Schema[MigrationAction.AddField] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.AddField](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[DynamicSchemaExpr].reflect.asTerm("default")
      ),
      typeId = TypeId.nominal[MigrationAction.AddField]("AddField", migrationActionOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.AddField] {
          def usedRegisters: RegisterOffset                                              = 2
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.AddField =
            new MigrationAction.AddField(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[DynamicSchemaExpr]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.AddField] {
          def usedRegisters: RegisterOffset                                                           = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.AddField): Unit = {
            out.setObject(offset, in.at)
            out.setObject(offset + 1, in.default)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private[this] lazy val dropFieldSchema: Schema[MigrationAction.DropField] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.DropField](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[DynamicSchemaExpr].reflect.asTerm("defaultForReverse")
      ),
      typeId = TypeId.nominal[MigrationAction.DropField]("DropField", migrationActionOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.DropField] {
          def usedRegisters: RegisterOffset                                               = 2
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.DropField =
            new MigrationAction.DropField(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[DynamicSchemaExpr]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.DropField] {
          def usedRegisters: RegisterOffset                                                            = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.DropField): Unit = {
            out.setObject(offset, in.at)
            out.setObject(offset + 1, in.defaultForReverse)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private[this] lazy val renameSchema: Schema[MigrationAction.Rename] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.Rename](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[String].reflect.asTerm("to")
      ),
      typeId = TypeId.nominal[MigrationAction.Rename]("Rename", migrationActionOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.Rename] {
          def usedRegisters: RegisterOffset                                            = 2
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.Rename =
            new MigrationAction.Rename(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.Rename] {
          def usedRegisters: RegisterOffset                                                         = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.Rename): Unit = {
            out.setObject(offset, in.at)
            out.setObject(offset + 1, in.to)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private[this] lazy val transformValueSchema: Schema[MigrationAction.TransformValue] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.TransformValue](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[DynamicSchemaExpr].reflect.asTerm("transform")
      ),
      typeId = TypeId.nominal[MigrationAction.TransformValue]("TransformValue", migrationActionOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.TransformValue] {
          def usedRegisters: RegisterOffset                                                    = 2
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.TransformValue =
            new MigrationAction.TransformValue(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[DynamicSchemaExpr]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.TransformValue] {
          def usedRegisters: RegisterOffset                                                                 = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.TransformValue): Unit = {
            out.setObject(offset, in.at)
            out.setObject(offset + 1, in.transform)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private[this] lazy val mandateSchema: Schema[MigrationAction.Mandate] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.Mandate](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[DynamicSchemaExpr].reflect.asTerm("default")
      ),
      typeId = TypeId.nominal[MigrationAction.Mandate]("Mandate", migrationActionOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.Mandate] {
          def usedRegisters: RegisterOffset                                             = 2
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.Mandate =
            new MigrationAction.Mandate(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[DynamicSchemaExpr]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.Mandate] {
          def usedRegisters: RegisterOffset                                                          = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.Mandate): Unit = {
            out.setObject(offset, in.at)
            out.setObject(offset + 1, in.default)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private[this] lazy val optionalizeSchema: Schema[MigrationAction.Optionalize] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.Optionalize](
      fields = Chunk(Schema[DynamicOptic].reflect.asTerm("at")),
      typeId = TypeId.nominal[MigrationAction.Optionalize]("Optionalize", migrationActionOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.Optionalize] {
          def usedRegisters: RegisterOffset                                                 = 1
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.Optionalize =
            new MigrationAction.Optionalize(in.getObject(offset).asInstanceOf[DynamicOptic])
        },
        deconstructor = new Deconstructor[MigrationAction.Optionalize] {
          def usedRegisters: RegisterOffset                                                              = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.Optionalize): Unit =
            out.setObject(offset, in.at)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private[this] lazy val changeTypeSchema: Schema[MigrationAction.ChangeType] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.ChangeType](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[DynamicSchemaExpr].reflect.asTerm("converter")
      ),
      typeId = TypeId.nominal[MigrationAction.ChangeType]("ChangeType", migrationActionOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.ChangeType] {
          def usedRegisters: RegisterOffset                                                = 2
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.ChangeType =
            new MigrationAction.ChangeType(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[DynamicSchemaExpr]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.ChangeType] {
          def usedRegisters: RegisterOffset                                                             = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.ChangeType): Unit = {
            out.setObject(offset, in.at)
            out.setObject(offset + 1, in.converter)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private[this] lazy val renameCaseSchema: Schema[MigrationAction.RenameCase] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.RenameCase](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[String].reflect.asTerm("from"),
        Schema[String].reflect.asTerm("to")
      ),
      typeId = TypeId.nominal[MigrationAction.RenameCase]("RenameCase", migrationActionOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.RenameCase] {
          def usedRegisters: RegisterOffset                                                = 3
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.RenameCase =
            new MigrationAction.RenameCase(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.RenameCase] {
          def usedRegisters: RegisterOffset                                                             = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.RenameCase): Unit = {
            out.setObject(offset, in.at)
            out.setObject(offset + 1, in.from)
            out.setObject(offset + 2, in.to)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private[this] lazy val transformCaseSchema: Schema[MigrationAction.TransformCase] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.TransformCase](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        new Reflect.Deferred[Binding, Vector[MigrationAction]](() => Schema[Vector[MigrationAction]].reflect)
          .asTerm("actions")
      ),
      typeId = TypeId.nominal[MigrationAction.TransformCase]("TransformCase", migrationActionOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.TransformCase] {
          def usedRegisters: RegisterOffset                                                   = 2
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.TransformCase =
            new MigrationAction.TransformCase(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[Vector[MigrationAction]]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.TransformCase] {
          def usedRegisters: RegisterOffset                                                                = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.TransformCase): Unit = {
            out.setObject(offset, in.at)
            out.setObject(offset + 1, in.actions)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private[this] lazy val transformElementsSchema: Schema[MigrationAction.TransformElements] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.TransformElements](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[DynamicSchemaExpr].reflect.asTerm("transform")
      ),
      typeId = TypeId.nominal[MigrationAction.TransformElements]("TransformElements", migrationActionOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.TransformElements] {
          def usedRegisters: RegisterOffset                                                       = 2
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.TransformElements =
            new MigrationAction.TransformElements(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[DynamicSchemaExpr]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.TransformElements] {
          def usedRegisters: RegisterOffset                                                                    = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.TransformElements): Unit = {
            out.setObject(offset, in.at)
            out.setObject(offset + 1, in.transform)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private[this] lazy val transformKeysSchema: Schema[MigrationAction.TransformKeys] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.TransformKeys](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[DynamicSchemaExpr].reflect.asTerm("transform")
      ),
      typeId = TypeId.nominal[MigrationAction.TransformKeys]("TransformKeys", migrationActionOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.TransformKeys] {
          def usedRegisters: RegisterOffset                                                   = 2
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.TransformKeys =
            new MigrationAction.TransformKeys(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[DynamicSchemaExpr]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.TransformKeys] {
          def usedRegisters: RegisterOffset                                                                = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.TransformKeys): Unit = {
            out.setObject(offset, in.at)
            out.setObject(offset + 1, in.transform)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  private[this] lazy val transformValuesSchema: Schema[MigrationAction.TransformValues] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.TransformValues](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("at"),
        Schema[DynamicSchemaExpr].reflect.asTerm("transform")
      ),
      typeId = TypeId.nominal[MigrationAction.TransformValues]("TransformValues", migrationActionOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.TransformValues] {
          def usedRegisters: RegisterOffset                                                     = 2
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.TransformValues =
            new MigrationAction.TransformValues(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[DynamicSchemaExpr]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.TransformValues] {
          def usedRegisters: RegisterOffset                                                                  = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.TransformValues): Unit = {
            out.setObject(offset, in.at)
            out.setObject(offset + 1, in.transform)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val migrationActionSchema: Schema[MigrationAction] = new Schema(
    reflect = new Reflect.Variant[Binding, MigrationAction](
      cases = Chunk(
        addFieldSchema.reflect.asTerm("AddField"),
        dropFieldSchema.reflect.asTerm("DropField"),
        renameSchema.reflect.asTerm("Rename"),
        transformValueSchema.reflect.asTerm("TransformValue"),
        mandateSchema.reflect.asTerm("Mandate"),
        optionalizeSchema.reflect.asTerm("Optionalize"),
        changeTypeSchema.reflect.asTerm("ChangeType"),
        renameCaseSchema.reflect.asTerm("RenameCase"),
        transformCaseSchema.reflect.asTerm("TransformCase"),
        transformElementsSchema.reflect.asTerm("TransformElements"),
        transformKeysSchema.reflect.asTerm("TransformKeys"),
        transformValuesSchema.reflect.asTerm("TransformValues")
      ),
      typeId = TypeId.nominal[MigrationAction]("MigrationAction", migrationOwner),
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[MigrationAction] {
          def discriminate(a: MigrationAction): Int = a match {
            case _: MigrationAction.AddField          => 0
            case _: MigrationAction.DropField         => 1
            case _: MigrationAction.Rename            => 2
            case _: MigrationAction.TransformValue    => 3
            case _: MigrationAction.Mandate           => 4
            case _: MigrationAction.Optionalize       => 5
            case _: MigrationAction.ChangeType        => 6
            case _: MigrationAction.RenameCase        => 7
            case _: MigrationAction.TransformCase     => 8
            case _: MigrationAction.TransformElements => 9
            case _: MigrationAction.TransformKeys     => 10
            case _: MigrationAction.TransformValues   => 11
          }
        },
        matchers = Matchers(
          new Matcher[MigrationAction.AddField] {
            def downcastOrNull(a: Any): MigrationAction.AddField = a match {
              case v: MigrationAction.AddField => v
              case _                           => null
            }
          },
          new Matcher[MigrationAction.DropField] {
            def downcastOrNull(a: Any): MigrationAction.DropField = a match {
              case v: MigrationAction.DropField => v
              case _                            => null
            }
          },
          new Matcher[MigrationAction.Rename] {
            def downcastOrNull(a: Any): MigrationAction.Rename = a match {
              case v: MigrationAction.Rename => v
              case _                         => null
            }
          },
          new Matcher[MigrationAction.TransformValue] {
            def downcastOrNull(a: Any): MigrationAction.TransformValue = a match {
              case v: MigrationAction.TransformValue => v
              case _                                 => null
            }
          },
          new Matcher[MigrationAction.Mandate] {
            def downcastOrNull(a: Any): MigrationAction.Mandate = a match {
              case v: MigrationAction.Mandate => v
              case _                          => null
            }
          },
          new Matcher[MigrationAction.Optionalize] {
            def downcastOrNull(a: Any): MigrationAction.Optionalize = a match {
              case v: MigrationAction.Optionalize => v
              case _                              => null
            }
          },
          new Matcher[MigrationAction.ChangeType] {
            def downcastOrNull(a: Any): MigrationAction.ChangeType = a match {
              case v: MigrationAction.ChangeType => v
              case _                             => null
            }
          },
          new Matcher[MigrationAction.RenameCase] {
            def downcastOrNull(a: Any): MigrationAction.RenameCase = a match {
              case v: MigrationAction.RenameCase => v
              case _                             => null
            }
          },
          new Matcher[MigrationAction.TransformCase] {
            def downcastOrNull(a: Any): MigrationAction.TransformCase = a match {
              case v: MigrationAction.TransformCase => v
              case _                                => null
            }
          },
          new Matcher[MigrationAction.TransformElements] {
            def downcastOrNull(a: Any): MigrationAction.TransformElements = a match {
              case v: MigrationAction.TransformElements => v
              case _                                    => null
            }
          },
          new Matcher[MigrationAction.TransformKeys] {
            def downcastOrNull(a: Any): MigrationAction.TransformKeys = a match {
              case v: MigrationAction.TransformKeys => v
              case _                                => null
            }
          },
          new Matcher[MigrationAction.TransformValues] {
            def downcastOrNull(a: Any): MigrationAction.TransformValues = a match {
              case v: MigrationAction.TransformValues => v
              case _                                  => null
            }
          }
        )
      ),
      modifiers = Chunk.empty
    )
  )

  // ===========================================================================
  // DynamicMigration — case class, 1 field
  // ===========================================================================

  implicit lazy val dynamicMigrationSchema: Schema[DynamicMigration] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicMigration](
      fields = Chunk(Schema[Vector[MigrationAction]].reflect.asTerm("actions")),
      typeId = TypeId.nominal[DynamicMigration]("DynamicMigration", migrationOwner),
      recordBinding = new Binding.Record(
        constructor = new Constructor[DynamicMigration] {
          def usedRegisters: RegisterOffset                                      = 1
          def construct(in: Registers, offset: RegisterOffset): DynamicMigration =
            new DynamicMigration(in.getObject(offset).asInstanceOf[Vector[MigrationAction]])
        },
        deconstructor = new Deconstructor[DynamicMigration] {
          def usedRegisters: RegisterOffset                                                   = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicMigration): Unit =
            out.setObject(offset, in.actions)
        }
      ),
      modifiers = Chunk.empty
    )
  )
}
