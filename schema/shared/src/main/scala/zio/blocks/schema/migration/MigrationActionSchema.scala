package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.chunk.Chunk
import zio.blocks.typeid.TypeId

/**
 * Schema definitions for MigrationAction types to enable serialization.
 */
object MigrationActionSchema {

  // ============================================================================
  // Helper Schemas
  // ============================================================================

  // Schema for SchemaExpr - simplified placeholder
  // In a full implementation, this would need proper SchemaExpr schema derivation
  private def schemaExprSchema: Schema[SchemaExpr[?]] = new Schema[SchemaExpr[?]](
    reflect = new Reflect.Record[Binding, SchemaExpr[?]](
      fields = Chunk(
        Schema[String].reflect.asTerm("description")
      ),
      typeId = TypeId.of[SchemaExpr[?]],
      recordBinding = new Binding.Record(
        constructor = new Constructor[SchemaExpr[?]] {
          def usedRegisters: RegisterOffset = 1
          def construct(in: Registers, offset: RegisterOffset): SchemaExpr[?] = 
            SchemaExpr.Literal((), Schema[Unit])
        },
        deconstructor = new Deconstructor[SchemaExpr[?]] {
          def usedRegisters: RegisterOffset = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: SchemaExpr[?]): Unit = {
            out.setObject(offset + 0, in.toString)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  // ============================================================================
  // Record Action Schemas
  // ============================================================================

  implicit lazy val addFieldSchema: Schema[MigrationAction.AddField] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.AddField](
      fields = Chunk(
        DynamicOptic.schema.reflect.asTerm("at"),
        Schema[String].reflect.asTerm("fieldName"),
        schemaExprSchema.reflect.asTerm("default")
      ),
      typeId = TypeId.of[MigrationAction.AddField],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.AddField] {
          def usedRegisters: RegisterOffset = 3
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.AddField =
            new MigrationAction.AddField(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[SchemaExpr[?]]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.AddField] {
          def usedRegisters: RegisterOffset = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.AddField): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.fieldName)
            out.setObject(offset + 2, in.default)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val dropFieldSchema: Schema[MigrationAction.DropField] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.DropField](
      fields = Chunk(
        DynamicOptic.schema.reflect.asTerm("at"),
        Schema[String].reflect.asTerm("fieldName"),
        schemaExprSchema.reflect.asTerm("defaultForReverse")
      ),
      typeId = TypeId.of[MigrationAction.DropField],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.DropField] {
          def usedRegisters: RegisterOffset = 3
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.DropField =
            new MigrationAction.DropField(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[SchemaExpr[?]]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.DropField] {
          def usedRegisters: RegisterOffset = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.DropField): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.fieldName)
            out.setObject(offset + 2, in.defaultForReverse)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val renameFieldSchema: Schema[MigrationAction.RenameField] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.RenameField](
      fields = Chunk(
        DynamicOptic.schema.reflect.asTerm("at"),
        Schema[String].reflect.asTerm("from"),
        Schema[String].reflect.asTerm("to")
      ),
      typeId = TypeId.of[MigrationAction.RenameField],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.RenameField] {
          def usedRegisters: RegisterOffset = 3
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.RenameField =
            new MigrationAction.RenameField(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.RenameField] {
          def usedRegisters: RegisterOffset = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.RenameField): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.from)
            out.setObject(offset + 2, in.to)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val transformValueSchema: Schema[MigrationAction.TransformValue] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.TransformValue](
      fields = Chunk(
        DynamicOptic.schema.reflect.asTerm("at"),
        Schema[String].reflect.asTerm("fieldName"),
        schemaExprSchema.reflect.asTerm("transform")
      ),
      typeId = TypeId.of[MigrationAction.TransformValue],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.TransformValue] {
          def usedRegisters: RegisterOffset = 3
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.TransformValue =
            new MigrationAction.TransformValue(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[SchemaExpr[?]]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.TransformValue] {
          def usedRegisters: RegisterOffset = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.TransformValue): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.fieldName)
            out.setObject(offset + 2, in.transform)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val mandateFieldSchema: Schema[MigrationAction.MandateField] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.MandateField](
      fields = Chunk(
        DynamicOptic.schema.reflect.asTerm("at"),
        Schema[String].reflect.asTerm("fieldName"),
        schemaExprSchema.reflect.asTerm("default")
      ),
      typeId = TypeId.of[MigrationAction.MandateField],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.MandateField] {
          def usedRegisters: RegisterOffset = 3
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.MandateField =
            new MigrationAction.MandateField(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[SchemaExpr[?]]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.MandateField] {
          def usedRegisters: RegisterOffset = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.MandateField): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.fieldName)
            out.setObject(offset + 2, in.default)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val optionalizeFieldSchema: Schema[MigrationAction.OptionalizeField] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.OptionalizeField](
      fields = Chunk(
        DynamicOptic.schema.reflect.asTerm("at"),
        Schema[String].reflect.asTerm("fieldName")
      ),
      typeId = TypeId.of[MigrationAction.OptionalizeField],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.OptionalizeField] {
          def usedRegisters: RegisterOffset = 2
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.OptionalizeField =
            new MigrationAction.OptionalizeField(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.OptionalizeField] {
          def usedRegisters: RegisterOffset = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.OptionalizeField): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.fieldName)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val changeFieldTypeSchema: Schema[MigrationAction.ChangeFieldType] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.ChangeFieldType](
      fields = Chunk(
        DynamicOptic.schema.reflect.asTerm("at"),
        Schema[String].reflect.asTerm("fieldName"),
        schemaExprSchema.reflect.asTerm("converter")
      ),
      typeId = TypeId.of[MigrationAction.ChangeFieldType],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.ChangeFieldType] {
          def usedRegisters: RegisterOffset = 3
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.ChangeFieldType =
            new MigrationAction.ChangeFieldType(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[SchemaExpr[?]]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.ChangeFieldType] {
          def usedRegisters: RegisterOffset = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.ChangeFieldType): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.fieldName)
            out.setObject(offset + 2, in.converter)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val joinFieldsSchema: Schema[MigrationAction.JoinFields] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.JoinFields](
      fields = Chunk(
        DynamicOptic.schema.reflect.asTerm("at"),
        Schema[String].reflect.asTerm("targetField"),
        Schema[Vector[DynamicOptic]].reflect.asTerm("sourcePaths"),
        schemaExprSchema.reflect.asTerm("combiner")
      ),
      typeId = TypeId.of[MigrationAction.JoinFields],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.JoinFields] {
          def usedRegisters: RegisterOffset = 4
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.JoinFields =
            new MigrationAction.JoinFields(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[Vector[DynamicOptic]],
              in.getObject(offset + 3).asInstanceOf[SchemaExpr[?]]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.JoinFields] {
          def usedRegisters: RegisterOffset = 4
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.JoinFields): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.targetField)
            out.setObject(offset + 2, in.sourcePaths)
            out.setObject(offset + 3, in.combiner)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val splitFieldSchema: Schema[MigrationAction.SplitField] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.SplitField](
      fields = Chunk(
        DynamicOptic.schema.reflect.asTerm("at"),
        Schema[String].reflect.asTerm("sourceField"),
        Schema[Vector[DynamicOptic]].reflect.asTerm("targetPaths"),
        schemaExprSchema.reflect.asTerm("splitter")
      ),
      typeId = TypeId.of[MigrationAction.SplitField],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.SplitField] {
          def usedRegisters: RegisterOffset = 4
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.SplitField =
            new MigrationAction.SplitField(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[Vector[DynamicOptic]],
              in.getObject(offset + 3).asInstanceOf[SchemaExpr[?]]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.SplitField] {
          def usedRegisters: RegisterOffset = 4
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.SplitField): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.sourceField)
            out.setObject(offset + 2, in.targetPaths)
            out.setObject(offset + 3, in.splitter)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  // ============================================================================
  // Enum Action Schemas
  // ============================================================================

  implicit lazy val renameCaseSchema: Schema[MigrationAction.RenameCase] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.RenameCase](
      fields = Chunk(
        DynamicOptic.schema.reflect.asTerm("at"),
        Schema[String].reflect.asTerm("from"),
        Schema[String].reflect.asTerm("to")
      ),
      typeId = TypeId.of[MigrationAction.RenameCase],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.RenameCase] {
          def usedRegisters: RegisterOffset = 3
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.RenameCase =
            new MigrationAction.RenameCase(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.RenameCase] {
          def usedRegisters: RegisterOffset = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.RenameCase): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.from)
            out.setObject(offset + 2, in.to)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  // ============================================================================
  // Collection/Map Action Schemas
  // ============================================================================

  implicit lazy val transformElementsSchema: Schema[MigrationAction.TransformElements] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.TransformElements](
      fields = Chunk(
        DynamicOptic.schema.reflect.asTerm("at"),
        schemaExprSchema.reflect.asTerm("transform")
      ),
      typeId = TypeId.of[MigrationAction.TransformElements],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.TransformElements] {
          def usedRegisters: RegisterOffset = 2
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.TransformElements =
            new MigrationAction.TransformElements(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[SchemaExpr[?]]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.TransformElements] {
          def usedRegisters: RegisterOffset = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.TransformElements): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.transform)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val transformKeysSchema: Schema[MigrationAction.TransformKeys] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.TransformKeys](
      fields = Chunk(
        DynamicOptic.schema.reflect.asTerm("at"),
        schemaExprSchema.reflect.asTerm("transform")
      ),
      typeId = TypeId.of[MigrationAction.TransformKeys],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.TransformKeys] {
          def usedRegisters: RegisterOffset = 2
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.TransformKeys =
            new MigrationAction.TransformKeys(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[SchemaExpr[?]]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.TransformKeys] {
          def usedRegisters: RegisterOffset = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.TransformKeys): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.transform)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val transformValuesSchema: Schema[MigrationAction.TransformValues] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationAction.TransformValues](
      fields = Chunk(
        DynamicOptic.schema.reflect.asTerm("at"),
        schemaExprSchema.reflect.asTerm("transform")
      ),
      typeId = TypeId.of[MigrationAction.TransformValues],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationAction.TransformValues] {
          def usedRegisters: RegisterOffset = 2
          def construct(in: Registers, offset: RegisterOffset): MigrationAction.TransformValues =
            new MigrationAction.TransformValues(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[SchemaExpr[?]]
            )
        },
        deconstructor = new Deconstructor[MigrationAction.TransformValues] {
          def usedRegisters: RegisterOffset = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationAction.TransformValues): Unit = {
            out.setObject(offset + 0, in.at)
            out.setObject(offset + 1, in.transform)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  // ============================================================================
  // MigrationAction Sealed Trait Schema
  // ============================================================================

  // Note: TransformCase has recursive dependency on MigrationAction
  // In a full implementation, this would need lazy initialization
  
  implicit lazy val migrationActionSchema: Schema[MigrationAction] = new Schema(
    reflect = new Reflect.Variant[Binding, MigrationAction](
      cases = Chunk(
        addFieldSchema.reflect.asTerm("AddField"),
        dropFieldSchema.reflect.asTerm("DropField"),
        renameFieldSchema.reflect.asTerm("RenameField"),
        transformValueSchema.reflect.asTerm("TransformValue"),
        mandateFieldSchema.reflect.asTerm("MandateField"),
        optionalizeFieldSchema.reflect.asTerm("OptionalizeField"),
        changeFieldTypeSchema.reflect.asTerm("ChangeFieldType"),
        joinFieldsSchema.reflect.asTerm("JoinFields"),
        splitFieldSchema.reflect.asTerm("SplitField"),
        renameCaseSchema.reflect.asTerm("RenameCase"),
        // transformCaseSchema would go here but needs recursive schema support
        transformElementsSchema.reflect.asTerm("TransformElements"),
        transformKeysSchema.reflect.asTerm("TransformKeys"),
        transformValuesSchema.reflect.asTerm("TransformValues")
      ),
      typeId = TypeId.of[MigrationAction],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[MigrationAction] {
          def discriminate(a: MigrationAction): Int = a match {
            case _: MigrationAction.AddField => 0
            case _: MigrationAction.DropField => 1
            case _: MigrationAction.RenameField => 2
            case _: MigrationAction.TransformValue => 3
            case _: MigrationAction.MandateField => 4
            case _: MigrationAction.OptionalizeField => 5
            case _: MigrationAction.ChangeFieldType => 6
            case _: MigrationAction.JoinFields => 7
            case _: MigrationAction.SplitField => 8
            case _: MigrationAction.RenameCase => 9
            case _: MigrationAction.TransformCase => 10
            case _: MigrationAction.TransformElements => 11
            case _: MigrationAction.TransformKeys => 12
            case _: MigrationAction.TransformValues => 13
          }
        },
        matchers = Matchers(
          new Matcher[MigrationAction.AddField] {
            def downcastOrNull(a: Any): MigrationAction.AddField = a match {
              case x: MigrationAction.AddField => x
              case _ => null
            }
          },
          new Matcher[MigrationAction.DropField] {
            def downcastOrNull(a: Any): MigrationAction.DropField = a match {
              case x: MigrationAction.DropField => x
              case _ => null
            }
          },
          new Matcher[MigrationAction.RenameField] {
            def downcastOrNull(a: Any): MigrationAction.RenameField = a match {
              case x: MigrationAction.RenameField => x
              case _ => null
            }
          },
          new Matcher[MigrationAction.TransformValue] {
            def downcastOrNull(a: Any): MigrationAction.TransformValue = a match {
              case x: MigrationAction.TransformValue => x
              case _ => null
            }
          },
          new Matcher[MigrationAction.MandateField] {
            def downcastOrNull(a: Any): MigrationAction.MandateField = a match {
              case x: MigrationAction.MandateField => x
              case _ => null
            }
          },
          new Matcher[MigrationAction.OptionalizeField] {
            def downcastOrNull(a: Any): MigrationAction.OptionalizeField = a match {
              case x: MigrationAction.OptionalizeField => x
              case _ => null
            }
          },
          new Matcher[MigrationAction.ChangeFieldType] {
            def downcastOrNull(a: Any): MigrationAction.ChangeFieldType = a match {
              case x: MigrationAction.ChangeFieldType => x
              case _ => null
            }
          },
          new Matcher[MigrationAction.JoinFields] {
            def downcastOrNull(a: Any): MigrationAction.JoinFields = a match {
              case x: MigrationAction.JoinFields => x
              case _ => null
            }
          },
          new Matcher[MigrationAction.SplitField] {
            def downcastOrNull(a: Any): MigrationAction.SplitField = a match {
              case x: MigrationAction.SplitField => x
              case _ => null
            }
          },
          new Matcher[MigrationAction.RenameCase] {
            def downcastOrNull(a: Any): MigrationAction.RenameCase = a match {
              case x: MigrationAction.RenameCase => x
              case _ => null
            }
          },
          new Matcher[MigrationAction.TransformCase] {
            def downcastOrNull(a: Any): MigrationAction.TransformCase = a match {
              case x: MigrationAction.TransformCase => x
              case _ => null
            }
          },
          new Matcher[MigrationAction.TransformElements] {
            def downcastOrNull(a: Any): MigrationAction.TransformElements = a match {
              case x: MigrationAction.TransformElements => x
              case _ => null
            }
          },
          new Matcher[MigrationAction.TransformKeys] {
            def downcastOrNull(a: Any): MigrationAction.TransformKeys = a match {
              case x: MigrationAction.TransformKeys => x
              case _ => null
            }
          },
          new Matcher[MigrationAction.TransformValues] {
            def downcastOrNull(a: Any): MigrationAction.TransformValues = a match {
              case x: MigrationAction.TransformValues => x
              case _ => null
            }
          }
        )
      ),
      modifiers = Chunk.empty
    )
  )

  // ============================================================================
  // DynamicMigration Schema
  // ============================================================================

  implicit lazy val dynamicMigrationSchema: Schema[DynamicMigration] = new Schema(
    reflect = new Reflect.Record[Binding, DynamicMigration](
      fields = Chunk(
        Schema[Vector[MigrationAction]].reflect.asTerm("actions")
      ),
      typeId = TypeId.of[DynamicMigration],
      recordBinding = new Binding.Record(
        constructor = new Constructor[DynamicMigration] {
          def usedRegisters: RegisterOffset = 1
          def construct(in: Registers, offset: RegisterOffset): DynamicMigration =
            new DynamicMigration(in.getObject(offset + 0).asInstanceOf[Vector[MigrationAction]])
        },
        deconstructor = new Deconstructor[DynamicMigration] {
          def usedRegisters: RegisterOffset = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: DynamicMigration): Unit = {
            out.setObject(offset + 0, in.actions)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )
}
