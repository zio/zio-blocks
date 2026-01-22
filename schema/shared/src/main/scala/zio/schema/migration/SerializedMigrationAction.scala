package zio.schema.migration

import zio.blocks.schema._

sealed trait SerializedMigrationAction

object SerializedMigrationAction {
  final case class AddField(at: DynamicOptic, default: SerializedSchemaExpr) extends SerializedMigrationAction
  final case class DropField(at: DynamicOptic, defaultForReverse: SerializedSchemaExpr)
      extends SerializedMigrationAction
  final case class Rename(at: DynamicOptic, to: String) extends SerializedMigrationAction
  final case class TransformValue(
    at: DynamicOptic,
    transform: SerializedSchemaExpr,
    inverse: Option[SerializedSchemaExpr]
  ) extends SerializedMigrationAction
  final case class Mandate(at: DynamicOptic, default: SerializedSchemaExpr) extends SerializedMigrationAction
  final case class Optionalize(at: DynamicOptic)                            extends SerializedMigrationAction
  final case class Join(
    at: DynamicOptic,
    sourcePaths: Vector[DynamicOptic],
    combiner: SerializedSchemaExpr,
    splitter: Option[SerializedSchemaExpr]
  ) extends SerializedMigrationAction
  final case class Split(
    at: DynamicOptic,
    targetPaths: Vector[DynamicOptic],
    splitter: SerializedSchemaExpr,
    combiner: Option[SerializedSchemaExpr]
  ) extends SerializedMigrationAction
  final case class ChangeType(at: DynamicOptic, converter: SerializedSchemaExpr) extends SerializedMigrationAction
  final case class RenameCase(at: DynamicOptic, from: String, to: String)        extends SerializedMigrationAction
  final case class TransformCase(at: DynamicOptic, actions: Vector[SerializedMigrationAction])
      extends SerializedMigrationAction
  final case class TransformElements(at: DynamicOptic, migration: Vector[SerializedMigrationAction])
      extends SerializedMigrationAction
  final case class TransformKeys(at: DynamicOptic, migration: Vector[SerializedMigrationAction])
      extends SerializedMigrationAction
  final case class TransformValues(at: DynamicOptic, migration: Vector[SerializedMigrationAction])
      extends SerializedMigrationAction

  implicit lazy val schema: Schema[SerializedMigrationAction] = {
    import zio.blocks.schema.binding._
    import zio.blocks.schema.binding.RegisterOffset.RegisterOffset

    // Use Reflect.Deferred to break circular initialization for recursive Vector references
    def deferVectorSelf: Reflect.Bound[Vector[SerializedMigrationAction]] =
      new Reflect.Deferred[binding.Binding, Vector[SerializedMigrationAction]](() => Schema.vector(schema).reflect)

    lazy val addFieldSchema = new Schema(
      reflect = new Reflect.Record[Binding, AddField](
        fields =
          Vector(Schema[DynamicOptic].reflect.asTerm("at"), Schema[SerializedSchemaExpr].reflect.asTerm("default")),
        typeName = TypeName(Namespace(List("zio", "schema", "migration")), "AddField"),
        recordBinding = new Binding.Record(
          constructor = new Constructor[AddField] {
            override def usedRegisters: RegisterOffset                     = RegisterOffset(objects = 2)
            def construct(in: Registers, offset: RegisterOffset): AddField =
              AddField(
                in.getObject(offset).asInstanceOf[DynamicOptic],
                in.getObject(offset + 1).asInstanceOf[SerializedSchemaExpr]
              )
          },
          deconstructor = new Deconstructor[AddField] {
            override def usedRegisters: RegisterOffset                                  = RegisterOffset(objects = 2)
            def deconstruct(out: Registers, offset: RegisterOffset, in: AddField): Unit = {
              out.setObject(offset, in.at)
              out.setObject(offset + 1, in.default)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )

    lazy val dropFieldSchema = new Schema(
      reflect = new Reflect.Record[Binding, DropField](
        fields = Vector(
          Schema[DynamicOptic].reflect.asTerm("at"),
          Schema[SerializedSchemaExpr].reflect.asTerm("defaultForReverse")
        ),
        typeName = TypeName(Namespace(List("zio", "schema", "migration")), "DropField"),
        recordBinding = new Binding.Record(
          constructor = new Constructor[DropField] {
            override def usedRegisters: RegisterOffset                      = RegisterOffset(objects = 2)
            def construct(in: Registers, offset: RegisterOffset): DropField =
              DropField(
                in.getObject(offset).asInstanceOf[DynamicOptic],
                in.getObject(offset + 1).asInstanceOf[SerializedSchemaExpr]
              )
          },
          deconstructor = new Deconstructor[DropField] {
            override def usedRegisters: RegisterOffset                                   = RegisterOffset(objects = 2)
            def deconstruct(out: Registers, offset: RegisterOffset, in: DropField): Unit = {
              out.setObject(offset, in.at)
              out.setObject(offset + 1, in.defaultForReverse)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )

    lazy val renameSchema = new Schema(
      reflect = new Reflect.Record[Binding, Rename](
        fields = Vector(Schema[DynamicOptic].reflect.asTerm("at"), Schema[String].reflect.asTerm("to")),
        typeName = TypeName(Namespace(List("zio", "schema", "migration")), "Rename"),
        recordBinding = new Binding.Record(
          constructor = new Constructor[Rename] {
            override def usedRegisters: RegisterOffset                   = RegisterOffset(objects = 2)
            def construct(in: Registers, offset: RegisterOffset): Rename =
              Rename(in.getObject(offset).asInstanceOf[DynamicOptic], in.getObject(offset + 1).asInstanceOf[String])
          },
          deconstructor = new Deconstructor[Rename] {
            override def usedRegisters: RegisterOffset                                = RegisterOffset(objects = 2)
            def deconstruct(out: Registers, offset: RegisterOffset, in: Rename): Unit = {
              out.setObject(offset, in.at)
              out.setObject(offset + 1, in.to)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )

    lazy val transformValueSchema = new Schema(
      reflect = new Reflect.Record[Binding, TransformValue](
        fields = Vector(
          Schema[DynamicOptic].reflect.asTerm("at"),
          Schema[SerializedSchemaExpr].reflect.asTerm("transform"),
          Schema[Option[SerializedSchemaExpr]].reflect.asTerm("inverse")
        ),
        typeName = TypeName(Namespace(List("zio", "schema", "migration")), "TransformValue"),
        recordBinding = new Binding.Record(
          constructor = new Constructor[TransformValue] {
            override def usedRegisters: RegisterOffset                           = RegisterOffset(objects = 3)
            def construct(in: Registers, offset: RegisterOffset): TransformValue =
              TransformValue(
                in.getObject(offset).asInstanceOf[DynamicOptic],
                in.getObject(offset + 1).asInstanceOf[SerializedSchemaExpr],
                in.getObject(offset + 2).asInstanceOf[Option[SerializedSchemaExpr]]
              )
          },
          deconstructor = new Deconstructor[TransformValue] {
            override def usedRegisters: RegisterOffset                                        = RegisterOffset(objects = 3)
            def deconstruct(out: Registers, offset: RegisterOffset, in: TransformValue): Unit = {
              out.setObject(offset, in.at)
              out.setObject(offset + 1, in.transform)
              out.setObject(offset + 2, in.inverse)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )

    lazy val mandateSchema = new Schema(
      reflect = new Reflect.Record[Binding, Mandate](
        fields =
          Vector(Schema[DynamicOptic].reflect.asTerm("at"), Schema[SerializedSchemaExpr].reflect.asTerm("default")),
        typeName = TypeName(Namespace(List("zio", "schema", "migration")), "Mandate"),
        recordBinding = new Binding.Record(
          constructor = new Constructor[Mandate] {
            override def usedRegisters: RegisterOffset                    = RegisterOffset(objects = 2)
            def construct(in: Registers, offset: RegisterOffset): Mandate =
              Mandate(
                in.getObject(offset).asInstanceOf[DynamicOptic],
                in.getObject(offset + 1).asInstanceOf[SerializedSchemaExpr]
              )
          },
          deconstructor = new Deconstructor[Mandate] {
            override def usedRegisters: RegisterOffset                                 = RegisterOffset(objects = 2)
            def deconstruct(out: Registers, offset: RegisterOffset, in: Mandate): Unit = {
              out.setObject(offset, in.at)
              out.setObject(offset + 1, in.default)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )

    lazy val optionalizeSchema = new Schema(
      reflect = new Reflect.Record[Binding, Optionalize](
        fields = Vector(Schema[DynamicOptic].reflect.asTerm("at")),
        typeName = TypeName(Namespace(List("zio", "schema", "migration")), "Optionalize"),
        recordBinding = new Binding.Record(
          constructor = new Constructor[Optionalize] {
            override def usedRegisters: RegisterOffset                        = RegisterOffset(objects = 1)
            def construct(in: Registers, offset: RegisterOffset): Optionalize =
              Optionalize(in.getObject(offset).asInstanceOf[DynamicOptic])
          },
          deconstructor = new Deconstructor[Optionalize] {
            override def usedRegisters: RegisterOffset                                     = RegisterOffset(objects = 1)
            def deconstruct(out: Registers, offset: RegisterOffset, in: Optionalize): Unit =
              out.setObject(offset, in.at)
          }
        ),
        modifiers = Vector.empty
      )
    )

    lazy val joinSchema = new Schema(
      reflect = new Reflect.Record[Binding, Join](
        fields = Vector(
          Schema[DynamicOptic].reflect.asTerm("at"),
          Schema[Vector[DynamicOptic]].reflect.asTerm("sourcePaths"),
          Schema[SerializedSchemaExpr].reflect.asTerm("combiner"),
          Schema[Option[SerializedSchemaExpr]].reflect.asTerm("splitter")
        ),
        typeName = TypeName(Namespace(List("zio", "schema", "migration")), "Join"),
        recordBinding = new Binding.Record(
          constructor = new Constructor[Join] {
            override def usedRegisters: RegisterOffset                 = RegisterOffset(objects = 4)
            def construct(in: Registers, offset: RegisterOffset): Join =
              Join(
                in.getObject(offset).asInstanceOf[DynamicOptic],
                in.getObject(offset + 1).asInstanceOf[Vector[DynamicOptic]],
                in.getObject(offset + 2).asInstanceOf[SerializedSchemaExpr],
                in.getObject(offset + 3).asInstanceOf[Option[SerializedSchemaExpr]]
              )
          },
          deconstructor = new Deconstructor[Join] {
            override def usedRegisters: RegisterOffset                              = RegisterOffset(objects = 4)
            def deconstruct(out: Registers, offset: RegisterOffset, in: Join): Unit = {
              out.setObject(offset, in.at)
              out.setObject(offset + 1, in.sourcePaths)
              out.setObject(offset + 2, in.combiner)
              out.setObject(offset + 3, in.splitter)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )

    lazy val splitSchema = new Schema(
      reflect = new Reflect.Record[Binding, Split](
        fields = Vector(
          Schema[DynamicOptic].reflect.asTerm("at"),
          Schema[Vector[DynamicOptic]].reflect.asTerm("targetPaths"),
          Schema[SerializedSchemaExpr].reflect.asTerm("splitter"),
          Schema[Option[SerializedSchemaExpr]].reflect.asTerm("combiner")
        ),
        typeName = TypeName(Namespace(List("zio", "schema", "migration")), "Split"),
        recordBinding = new Binding.Record(
          constructor = new Constructor[Split] {
            override def usedRegisters: RegisterOffset                  = RegisterOffset(objects = 4)
            def construct(in: Registers, offset: RegisterOffset): Split =
              Split(
                in.getObject(offset).asInstanceOf[DynamicOptic],
                in.getObject(offset + 1).asInstanceOf[Vector[DynamicOptic]],
                in.getObject(offset + 2).asInstanceOf[SerializedSchemaExpr],
                in.getObject(offset + 3).asInstanceOf[Option[SerializedSchemaExpr]]
              )
          },
          deconstructor = new Deconstructor[Split] {
            override def usedRegisters: RegisterOffset                               = RegisterOffset(objects = 4)
            def deconstruct(out: Registers, offset: RegisterOffset, in: Split): Unit = {
              out.setObject(offset, in.at)
              out.setObject(offset + 1, in.targetPaths)
              out.setObject(offset + 2, in.splitter)
              out.setObject(offset + 3, in.combiner)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )

    lazy val changeTypeSchema = new Schema(
      reflect = new Reflect.Record[Binding, ChangeType](
        fields =
          Vector(Schema[DynamicOptic].reflect.asTerm("at"), Schema[SerializedSchemaExpr].reflect.asTerm("converter")),
        typeName = TypeName(Namespace(List("zio", "schema", "migration")), "ChangeType"),
        recordBinding = new Binding.Record(
          constructor = new Constructor[ChangeType] {
            override def usedRegisters: RegisterOffset                       = RegisterOffset(objects = 2)
            def construct(in: Registers, offset: RegisterOffset): ChangeType =
              ChangeType(
                in.getObject(offset).asInstanceOf[DynamicOptic],
                in.getObject(offset + 1).asInstanceOf[SerializedSchemaExpr]
              )
          },
          deconstructor = new Deconstructor[ChangeType] {
            override def usedRegisters: RegisterOffset                                    = RegisterOffset(objects = 2)
            def deconstruct(out: Registers, offset: RegisterOffset, in: ChangeType): Unit = {
              out.setObject(offset, in.at)
              out.setObject(offset + 1, in.converter)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )

    lazy val renameCaseSchema = new Schema(
      reflect = new Reflect.Record[Binding, RenameCase](
        fields = Vector(
          Schema[DynamicOptic].reflect.asTerm("at"),
          Schema[String].reflect.asTerm("from"),
          Schema[String].reflect.asTerm("to")
        ),
        typeName = TypeName(Namespace(List("zio", "schema", "migration")), "RenameCase"),
        recordBinding = new Binding.Record(
          constructor = new Constructor[RenameCase] {
            override def usedRegisters: RegisterOffset                       = RegisterOffset(objects = 3)
            def construct(in: Registers, offset: RegisterOffset): RenameCase =
              RenameCase(
                in.getObject(offset).asInstanceOf[DynamicOptic],
                in.getObject(offset + 1).asInstanceOf[String],
                in.getObject(offset + 2).asInstanceOf[String]
              )
          },
          deconstructor = new Deconstructor[RenameCase] {
            override def usedRegisters: RegisterOffset                                    = RegisterOffset(objects = 3)
            def deconstruct(out: Registers, offset: RegisterOffset, in: RenameCase): Unit = {
              out.setObject(offset, in.at)
              out.setObject(offset + 1, in.from)
              out.setObject(offset + 2, in.to)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )

    lazy val transformCaseSchema = new Schema(
      reflect = new Reflect.Record[Binding, TransformCase](
        fields = Vector(
          Schema[DynamicOptic].reflect.asTerm("at"),
          deferVectorSelf.asTerm("actions")
        ),
        typeName = TypeName(Namespace(List("zio", "schema", "migration")), "TransformCase"),
        recordBinding = new Binding.Record(
          constructor = new Constructor[TransformCase] {
            override def usedRegisters: RegisterOffset                          = RegisterOffset(objects = 2)
            def construct(in: Registers, offset: RegisterOffset): TransformCase =
              TransformCase(
                in.getObject(offset).asInstanceOf[DynamicOptic],
                in.getObject(offset + 1).asInstanceOf[Vector[SerializedMigrationAction]]
              )
          },
          deconstructor = new Deconstructor[TransformCase] {
            override def usedRegisters: RegisterOffset                                       = RegisterOffset(objects = 2)
            def deconstruct(out: Registers, offset: RegisterOffset, in: TransformCase): Unit = {
              out.setObject(offset, in.at)
              out.setObject(offset + 1, in.actions)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )

    lazy val transformElementsSchema = new Schema(
      reflect = new Reflect.Record[Binding, TransformElements](
        fields = Vector(
          Schema[DynamicOptic].reflect.asTerm("at"),
          deferVectorSelf.asTerm("migration")
        ),
        typeName = TypeName(Namespace(List("zio", "schema", "migration")), "TransformElements"),
        recordBinding = new Binding.Record(
          constructor = new Constructor[TransformElements] {
            override def usedRegisters: RegisterOffset                              = RegisterOffset(objects = 2)
            def construct(in: Registers, offset: RegisterOffset): TransformElements =
              TransformElements(
                in.getObject(offset).asInstanceOf[DynamicOptic],
                in.getObject(offset + 1).asInstanceOf[Vector[SerializedMigrationAction]]
              )
          },
          deconstructor = new Deconstructor[TransformElements] {
            override def usedRegisters: RegisterOffset                                           = RegisterOffset(objects = 2)
            def deconstruct(out: Registers, offset: RegisterOffset, in: TransformElements): Unit = {
              out.setObject(offset, in.at)
              out.setObject(offset + 1, in.migration)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )

    lazy val transformKeysSchema = new Schema(
      reflect = new Reflect.Record[Binding, TransformKeys](
        fields = Vector(
          Schema[DynamicOptic].reflect.asTerm("at"),
          deferVectorSelf.asTerm("migration")
        ),
        typeName = TypeName(Namespace(List("zio", "schema", "migration")), "TransformKeys"),
        recordBinding = new Binding.Record(
          constructor = new Constructor[TransformKeys] {
            override def usedRegisters: RegisterOffset                          = RegisterOffset(objects = 2)
            def construct(in: Registers, offset: RegisterOffset): TransformKeys =
              TransformKeys(
                in.getObject(offset).asInstanceOf[DynamicOptic],
                in.getObject(offset + 1).asInstanceOf[Vector[SerializedMigrationAction]]
              )
          },
          deconstructor = new Deconstructor[TransformKeys] {
            override def usedRegisters: RegisterOffset                                       = RegisterOffset(objects = 2)
            def deconstruct(out: Registers, offset: RegisterOffset, in: TransformKeys): Unit = {
              out.setObject(offset, in.at)
              out.setObject(offset + 1, in.migration)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )

    lazy val transformValuesSchema = new Schema(
      reflect = new Reflect.Record[Binding, TransformValues](
        fields = Vector(
          Schema[DynamicOptic].reflect.asTerm("at"),
          deferVectorSelf.asTerm("migration")
        ),
        typeName = TypeName(Namespace(List("zio", "schema", "migration")), "TransformValues"),
        recordBinding = new Binding.Record(
          constructor = new Constructor[TransformValues] {
            override def usedRegisters: RegisterOffset                            = RegisterOffset(objects = 2)
            def construct(in: Registers, offset: RegisterOffset): TransformValues =
              TransformValues(
                in.getObject(offset).asInstanceOf[DynamicOptic],
                in.getObject(offset + 1).asInstanceOf[Vector[SerializedMigrationAction]]
              )
          },
          deconstructor = new Deconstructor[TransformValues] {
            override def usedRegisters: RegisterOffset                                         = RegisterOffset(objects = 2)
            def deconstruct(out: Registers, offset: RegisterOffset, in: TransformValues): Unit = {
              out.setObject(offset, in.at)
              out.setObject(offset + 1, in.migration)
            }
          }
        ),
        modifiers = Vector.empty
      )
    )

    new Schema(
      reflect = new Reflect.Variant[Binding, SerializedMigrationAction](
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
        typeName = TypeName(Namespace(List("zio", "schema", "migration")), "SerializedMigrationAction"),
        variantBinding = new Binding.Variant(
          discriminator = new Discriminator[SerializedMigrationAction] {
            def discriminate(a: SerializedMigrationAction): Int = a match {
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
            new Matcher[AddField]       { def downcastOrNull(a: Any) = a match { case x: AddField => x; case _ => null }  },
            new Matcher[DropField]      { def downcastOrNull(a: Any) = a match { case x: DropField => x; case _ => null } },
            new Matcher[Rename]         { def downcastOrNull(a: Any) = a match { case x: Rename => x; case _ => null }    },
            new Matcher[TransformValue] {
              def downcastOrNull(a: Any) = a match { case x: TransformValue => x; case _ => null }
            },
            new Matcher[Mandate]     { def downcastOrNull(a: Any) = a match { case x: Mandate => x; case _ => null } },
            new Matcher[Optionalize] {
              def downcastOrNull(a: Any) = a match { case x: Optionalize => x; case _ => null }
            },
            new Matcher[Join]       { def downcastOrNull(a: Any) = a match { case x: Join => x; case _ => null }  },
            new Matcher[Split]      { def downcastOrNull(a: Any) = a match { case x: Split => x; case _ => null } },
            new Matcher[ChangeType] {
              def downcastOrNull(a: Any) = a match { case x: ChangeType => x; case _ => null }
            },
            new Matcher[RenameCase] {
              def downcastOrNull(a: Any) = a match { case x: RenameCase => x; case _ => null }
            },
            new Matcher[TransformCase] {
              def downcastOrNull(a: Any) = a match { case x: TransformCase => x; case _ => null }
            },
            new Matcher[TransformElements] {
              def downcastOrNull(a: Any) = a match { case x: TransformElements => x; case _ => null }
            },
            new Matcher[TransformKeys] {
              def downcastOrNull(a: Any) = a match { case x: TransformKeys => x; case _ => null }
            },
            new Matcher[TransformValues] {
              def downcastOrNull(a: Any) = a match { case x: TransformValues => x; case _ => null }
            }
          )
        ),
        modifiers = Vector.empty
      )
    )
  }
}
