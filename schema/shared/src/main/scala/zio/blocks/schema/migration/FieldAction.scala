package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, Reflect, Schema}
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.typeid.TypeId

sealed trait FieldAction {
  def reverse: FieldAction
}

object FieldAction {

  final case class Add(name: String, defaultValue: DynamicValue) extends FieldAction {
    def reverse: FieldAction = Remove(name, defaultValue)
  }

  final case class Remove(name: String, defaultForReverse: DynamicValue) extends FieldAction {
    def reverse: FieldAction = Add(name, defaultForReverse)
  }

  final case class Rename(from: String, to: String) extends FieldAction {
    def reverse: FieldAction = Rename(to, from)
  }

  final case class Transform(
    name: String,
    forward: DynamicValueTransform,
    backward: DynamicValueTransform
  ) extends FieldAction {
    def reverse: FieldAction = Transform(name, backward, forward)
  }

  final case class MakeOptional(name: String, defaultForReverse: DynamicValue) extends FieldAction {
    def reverse: FieldAction = MakeRequired(name, defaultForReverse)
  }

  final case class MakeRequired(name: String, defaultForNone: DynamicValue) extends FieldAction {
    def reverse: FieldAction = MakeOptional(name, defaultForNone)
  }

  final case class ChangeType(
    name: String,
    forward: PrimitiveConversion,
    backward: PrimitiveConversion
  ) extends FieldAction {
    def reverse: FieldAction = ChangeType(name, backward, forward)
  }

  final case class JoinFields(
    targetName: String,
    sourceNames: Vector[String],
    combiner: DynamicValueTransform,
    splitter: DynamicValueTransform
  ) extends FieldAction {
    def reverse: FieldAction = SplitField(targetName, sourceNames, splitter, combiner)
  }

  final case class SplitField(
    sourceName: String,
    targetNames: Vector[String],
    splitter: DynamicValueTransform,
    combiner: DynamicValueTransform
  ) extends FieldAction {
    def reverse: FieldAction = JoinFields(sourceName, targetNames, combiner, splitter)
  }

  implicit lazy val addSchema: Schema[Add] = new Schema(
    reflect = new Reflect.Record[Binding, Add](
      fields = Vector(
        Schema[String].reflect.asTerm("name"),
        Schema[DynamicValue].reflect.asTerm("defaultValue")
      ),
      typeId = TypeId.of[Add],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Add] {
          def usedRegisters: RegisterOffset                         = 2
          def construct(in: Registers, offset: RegisterOffset): Add =
            Add(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[DynamicValue]
            )
        },
        deconstructor = new Deconstructor[Add] {
          def usedRegisters: RegisterOffset                                      = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: Add): Unit = {
            out.setObject(offset + 0, in.name)
            out.setObject(offset + 1, in.defaultValue)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val removeSchema: Schema[Remove] = new Schema(
    reflect = new Reflect.Record[Binding, Remove](
      fields = Vector(
        Schema[String].reflect.asTerm("name"),
        Schema[DynamicValue].reflect.asTerm("defaultForReverse")
      ),
      typeId = TypeId.of[Remove],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Remove] {
          def usedRegisters: RegisterOffset                            = 2
          def construct(in: Registers, offset: RegisterOffset): Remove =
            Remove(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[DynamicValue]
            )
        },
        deconstructor = new Deconstructor[Remove] {
          def usedRegisters: RegisterOffset                                         = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: Remove): Unit = {
            out.setObject(offset + 0, in.name)
            out.setObject(offset + 1, in.defaultForReverse)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val renameSchema: Schema[Rename] = new Schema(
    reflect = new Reflect.Record[Binding, Rename](
      fields = Vector(
        Schema[String].reflect.asTerm("from"),
        Schema[String].reflect.asTerm("to")
      ),
      typeId = TypeId.of[Rename],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Rename] {
          def usedRegisters: RegisterOffset                            = 2
          def construct(in: Registers, offset: RegisterOffset): Rename =
            Rename(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[Rename] {
          def usedRegisters: RegisterOffset                                         = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: Rename): Unit = {
            out.setObject(offset + 0, in.from)
            out.setObject(offset + 1, in.to)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val transformSchema: Schema[Transform] = new Schema(
    reflect = new Reflect.Record[Binding, Transform](
      fields = Vector(
        Schema[String].reflect.asTerm("name"),
        DynamicValueTransform.schema.reflect.asTerm("forward"),
        DynamicValueTransform.schema.reflect.asTerm("backward")
      ),
      typeId = TypeId.of[Transform],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Transform] {
          def usedRegisters: RegisterOffset                               = 3
          def construct(in: Registers, offset: RegisterOffset): Transform =
            Transform(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[DynamicValueTransform],
              in.getObject(offset + 2).asInstanceOf[DynamicValueTransform]
            )
        },
        deconstructor = new Deconstructor[Transform] {
          def usedRegisters: RegisterOffset                                            = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: Transform): Unit = {
            out.setObject(offset + 0, in.name)
            out.setObject(offset + 1, in.forward)
            out.setObject(offset + 2, in.backward)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val makeOptionalSchema: Schema[MakeOptional] = new Schema(
    reflect = new Reflect.Record[Binding, MakeOptional](
      fields = Vector(
        Schema[String].reflect.asTerm("name"),
        Schema[DynamicValue].reflect.asTerm("defaultForReverse")
      ),
      typeId = TypeId.of[MakeOptional],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MakeOptional] {
          def usedRegisters: RegisterOffset                                  = 2
          def construct(in: Registers, offset: RegisterOffset): MakeOptional =
            MakeOptional(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[DynamicValue]
            )
        },
        deconstructor = new Deconstructor[MakeOptional] {
          def usedRegisters: RegisterOffset                                               = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: MakeOptional): Unit = {
            out.setObject(offset + 0, in.name)
            out.setObject(offset + 1, in.defaultForReverse)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val makeRequiredSchema: Schema[MakeRequired] = new Schema(
    reflect = new Reflect.Record[Binding, MakeRequired](
      fields = Vector(
        Schema[String].reflect.asTerm("name"),
        Schema[DynamicValue].reflect.asTerm("defaultForNone")
      ),
      typeId = TypeId.of[MakeRequired],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MakeRequired] {
          def usedRegisters: RegisterOffset                                  = 2
          def construct(in: Registers, offset: RegisterOffset): MakeRequired =
            MakeRequired(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[DynamicValue]
            )
        },
        deconstructor = new Deconstructor[MakeRequired] {
          def usedRegisters: RegisterOffset                                               = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: MakeRequired): Unit = {
            out.setObject(offset + 0, in.name)
            out.setObject(offset + 1, in.defaultForNone)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val changeTypeSchema: Schema[ChangeType] = new Schema(
    reflect = new Reflect.Record[Binding, ChangeType](
      fields = Vector(
        Schema[String].reflect.asTerm("name"),
        PrimitiveConversion.schema.reflect.asTerm("forward"),
        PrimitiveConversion.schema.reflect.asTerm("backward")
      ),
      typeId = TypeId.of[ChangeType],
      recordBinding = new Binding.Record(
        constructor = new Constructor[ChangeType] {
          def usedRegisters: RegisterOffset                                = 3
          def construct(in: Registers, offset: RegisterOffset): ChangeType =
            ChangeType(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[PrimitiveConversion],
              in.getObject(offset + 2).asInstanceOf[PrimitiveConversion]
            )
        },
        deconstructor = new Deconstructor[ChangeType] {
          def usedRegisters: RegisterOffset                                             = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: ChangeType): Unit = {
            out.setObject(offset + 0, in.name)
            out.setObject(offset + 1, in.forward)
            out.setObject(offset + 2, in.backward)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val joinFieldsSchema: Schema[JoinFields] = new Schema(
    reflect = new Reflect.Record[Binding, JoinFields](
      fields = Vector(
        Schema[String].reflect.asTerm("targetName"),
        Schema[Vector[String]].reflect.asTerm("sourceNames"),
        DynamicValueTransform.schema.reflect.asTerm("combiner"),
        DynamicValueTransform.schema.reflect.asTerm("splitter")
      ),
      typeId = TypeId.of[JoinFields],
      recordBinding = new Binding.Record(
        constructor = new Constructor[JoinFields] {
          def usedRegisters: RegisterOffset                                = 4
          def construct(in: Registers, offset: RegisterOffset): JoinFields =
            JoinFields(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[Vector[String]],
              in.getObject(offset + 2).asInstanceOf[DynamicValueTransform],
              in.getObject(offset + 3).asInstanceOf[DynamicValueTransform]
            )
        },
        deconstructor = new Deconstructor[JoinFields] {
          def usedRegisters: RegisterOffset                                             = 4
          def deconstruct(out: Registers, offset: RegisterOffset, in: JoinFields): Unit = {
            out.setObject(offset + 0, in.targetName)
            out.setObject(offset + 1, in.sourceNames)
            out.setObject(offset + 2, in.combiner)
            out.setObject(offset + 3, in.splitter)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val splitFieldSchema: Schema[SplitField] = new Schema(
    reflect = new Reflect.Record[Binding, SplitField](
      fields = Vector(
        Schema[String].reflect.asTerm("sourceName"),
        Schema[Vector[String]].reflect.asTerm("targetNames"),
        DynamicValueTransform.schema.reflect.asTerm("splitter"),
        DynamicValueTransform.schema.reflect.asTerm("combiner")
      ),
      typeId = TypeId.of[SplitField],
      recordBinding = new Binding.Record(
        constructor = new Constructor[SplitField] {
          def usedRegisters: RegisterOffset                                = 4
          def construct(in: Registers, offset: RegisterOffset): SplitField =
            SplitField(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[Vector[String]],
              in.getObject(offset + 2).asInstanceOf[DynamicValueTransform],
              in.getObject(offset + 3).asInstanceOf[DynamicValueTransform]
            )
        },
        deconstructor = new Deconstructor[SplitField] {
          def usedRegisters: RegisterOffset                                             = 4
          def deconstruct(out: Registers, offset: RegisterOffset, in: SplitField): Unit = {
            out.setObject(offset + 0, in.sourceName)
            out.setObject(offset + 1, in.targetNames)
            out.setObject(offset + 2, in.splitter)
            out.setObject(offset + 3, in.combiner)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val schema: Schema[FieldAction] = new Schema(
    reflect = new Reflect.Variant[Binding, FieldAction](
      cases = Vector(
        addSchema.reflect.asTerm("Add"),
        removeSchema.reflect.asTerm("Remove"),
        renameSchema.reflect.asTerm("Rename"),
        transformSchema.reflect.asTerm("Transform"),
        makeOptionalSchema.reflect.asTerm("MakeOptional"),
        makeRequiredSchema.reflect.asTerm("MakeRequired"),
        changeTypeSchema.reflect.asTerm("ChangeType"),
        joinFieldsSchema.reflect.asTerm("JoinFields"),
        splitFieldSchema.reflect.asTerm("SplitField")
      ),
      typeId = TypeId.of[FieldAction],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[FieldAction] {
          def discriminate(a: FieldAction): Int = a match {
            case _: Add          => 0
            case _: Remove       => 1
            case _: Rename       => 2
            case _: Transform    => 3
            case _: MakeOptional => 4
            case _: MakeRequired => 5
            case _: ChangeType   => 6
            case _: JoinFields   => 7
            case _: SplitField   => 8
          }
        },
        matchers = Matchers(
          new Matcher[Add] {
            def downcastOrNull(a: Any): Add = a match {
              case x: Add => x
              case _      => null.asInstanceOf[Add]
            }
          },
          new Matcher[Remove] {
            def downcastOrNull(a: Any): Remove = a match {
              case x: Remove => x
              case _         => null.asInstanceOf[Remove]
            }
          },
          new Matcher[Rename] {
            def downcastOrNull(a: Any): Rename = a match {
              case x: Rename => x
              case _         => null.asInstanceOf[Rename]
            }
          },
          new Matcher[Transform] {
            def downcastOrNull(a: Any): Transform = a match {
              case x: Transform => x
              case _            => null.asInstanceOf[Transform]
            }
          },
          new Matcher[MakeOptional] {
            def downcastOrNull(a: Any): MakeOptional = a match {
              case x: MakeOptional => x
              case _               => null.asInstanceOf[MakeOptional]
            }
          },
          new Matcher[MakeRequired] {
            def downcastOrNull(a: Any): MakeRequired = a match {
              case x: MakeRequired => x
              case _               => null.asInstanceOf[MakeRequired]
            }
          },
          new Matcher[ChangeType] {
            def downcastOrNull(a: Any): ChangeType = a match {
              case x: ChangeType => x
              case _             => null.asInstanceOf[ChangeType]
            }
          },
          new Matcher[JoinFields] {
            def downcastOrNull(a: Any): JoinFields = a match {
              case x: JoinFields => x
              case _             => null.asInstanceOf[JoinFields]
            }
          },
          new Matcher[SplitField] {
            def downcastOrNull(a: Any): SplitField = a match {
              case x: SplitField => x
              case _             => null.asInstanceOf[SplitField]
            }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )
}
