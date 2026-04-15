package zio.blocks.schema.migration

import zio.blocks.schema._
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.chunk.Chunk
import zio.blocks.typeid.TypeId

/**
 * Schema definitions for migration types to enable serialization.
 */
object MigrationSchema {

  // ============================================================================
  // MigrationError Schemas
  // ============================================================================

  implicit lazy val fieldNotFoundSchema: Schema[MigrationError.FieldNotFound] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationError.FieldNotFound](
      fields = Chunk(
        Schema[String].reflect.asTerm("fieldName"),
        Schema[Option[DynamicOptic]].reflect.asTerm("path")
      ),
      typeId = TypeId.of[MigrationError.FieldNotFound],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationError.FieldNotFound] {
          def usedRegisters: RegisterOffset = 2
          def construct(in: Registers, offset: RegisterOffset): MigrationError.FieldNotFound =
            new MigrationError.FieldNotFound(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[Option[DynamicOptic]]
            )
        },
        deconstructor = new Deconstructor[MigrationError.FieldNotFound] {
          def usedRegisters: RegisterOffset = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationError.FieldNotFound): Unit = {
            out.setObject(offset + 0, in.fieldName)
            out.setObject(offset + 1, in.path)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val caseNotFoundSchema: Schema[MigrationError.CaseNotFound] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationError.CaseNotFound](
      fields = Chunk(
        Schema[String].reflect.asTerm("caseName"),
        Schema[Option[DynamicOptic]].reflect.asTerm("path")
      ),
      typeId = TypeId.of[MigrationError.CaseNotFound],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationError.CaseNotFound] {
          def usedRegisters: RegisterOffset = 2
          def construct(in: Registers, offset: RegisterOffset): MigrationError.CaseNotFound =
            new MigrationError.CaseNotFound(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[Option[DynamicOptic]]
            )
        },
        deconstructor = new Deconstructor[MigrationError.CaseNotFound] {
          def usedRegisters: RegisterOffset = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationError.CaseNotFound): Unit = {
            out.setObject(offset + 0, in.caseName)
            out.setObject(offset + 1, in.path)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val typeMismatchSchema: Schema[MigrationError.TypeMismatch] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationError.TypeMismatch](
      fields = Chunk(
        Schema[String].reflect.asTerm("expected"),
        Schema[String].reflect.asTerm("actual"),
        Schema[Option[DynamicOptic]].reflect.asTerm("path")
      ),
      typeId = TypeId.of[MigrationError.TypeMismatch],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationError.TypeMismatch] {
          def usedRegisters: RegisterOffset = 3
          def construct(in: Registers, offset: RegisterOffset): MigrationError.TypeMismatch =
            new MigrationError.TypeMismatch(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[Option[DynamicOptic]]
            )
        },
        deconstructor = new Deconstructor[MigrationError.TypeMismatch] {
          def usedRegisters: RegisterOffset = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationError.TypeMismatch): Unit = {
            out.setObject(offset + 0, in.expected)
            out.setObject(offset + 1, in.actual)
            out.setObject(offset + 2, in.path)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val transformFailedSchema: Schema[MigrationError.TransformFailed] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationError.TransformFailed](
      fields = Chunk(
        Schema[String].reflect.asTerm("message"),
        Schema[Option[DynamicOptic]].reflect.asTerm("path")
      ),
      typeId = TypeId.of[MigrationError.TransformFailed],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationError.TransformFailed] {
          def usedRegisters: RegisterOffset = 2
          def construct(in: Registers, offset: RegisterOffset): MigrationError.TransformFailed =
            new MigrationError.TransformFailed(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[Option[DynamicOptic]]
            )
        },
        deconstructor = new Deconstructor[MigrationError.TransformFailed] {
          def usedRegisters: RegisterOffset = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationError.TransformFailed): Unit = {
            out.setObject(offset + 0, in.message)
            out.setObject(offset + 1, in.path)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val valueNotFoundSchema: Schema[MigrationError.ValueNotFound] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationError.ValueNotFound](
      fields = Chunk(
        Schema[Option[DynamicOptic]].reflect.asTerm("path")
      ),
      typeId = TypeId.of[MigrationError.ValueNotFound],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationError.ValueNotFound] {
          def usedRegisters: RegisterOffset = 1
          def construct(in: Registers, offset: RegisterOffset): MigrationError.ValueNotFound =
            new MigrationError.ValueNotFound(
              in.getObject(offset + 0).asInstanceOf[Option[DynamicOptic]]
            )
        },
        deconstructor = new Deconstructor[MigrationError.ValueNotFound] {
          def usedRegisters: RegisterOffset = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationError.ValueNotFound): Unit = {
            out.setObject(offset + 0, in.path)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val invalidOperationSchema: Schema[MigrationError.InvalidOperation] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationError.InvalidOperation](
      fields = Chunk(
        Schema[String].reflect.asTerm("message"),
        Schema[Option[DynamicOptic]].reflect.asTerm("path")
      ),
      typeId = TypeId.of[MigrationError.InvalidOperation],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationError.InvalidOperation] {
          def usedRegisters: RegisterOffset = 2
          def construct(in: Registers, offset: RegisterOffset): MigrationError.InvalidOperation =
            new MigrationError.InvalidOperation(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[Option[DynamicOptic]]
            )
        },
        deconstructor = new Deconstructor[MigrationError.InvalidOperation] {
          def usedRegisters: RegisterOffset = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationError.InvalidOperation): Unit = {
            out.setObject(offset + 0, in.message)
            out.setObject(offset + 1, in.path)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val compositionFailedSchema: Schema[MigrationError.CompositionFailed] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationError.CompositionFailed](
      fields = Chunk(
        Schema[String].reflect.asTerm("message"),
        Schema[Option[DynamicOptic]].reflect.asTerm("path")
      ),
      typeId = TypeId.of[MigrationError.CompositionFailed],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationError.CompositionFailed] {
          def usedRegisters: RegisterOffset = 2
          def construct(in: Registers, offset: RegisterOffset): MigrationError.CompositionFailed =
            new MigrationError.CompositionFailed(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[Option[DynamicOptic]]
            )
        },
        deconstructor = new Deconstructor[MigrationError.CompositionFailed] {
          def usedRegisters: RegisterOffset = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationError.CompositionFailed): Unit = {
            out.setObject(offset + 0, in.message)
            out.setObject(offset + 1, in.path)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val serializationFailedSchema: Schema[MigrationError.SerializationFailed] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationError.SerializationFailed](
      fields = Chunk(
        Schema[String].reflect.asTerm("message"),
        Schema[Option[DynamicOptic]].reflect.asTerm("path")
      ),
      typeId = TypeId.of[MigrationError.SerializationFailed],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationError.SerializationFailed] {
          def usedRegisters: RegisterOffset = 2
          def construct(in: Registers, offset: RegisterOffset): MigrationError.SerializationFailed =
            new MigrationError.SerializationFailed(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[Option[DynamicOptic]]
            )
        },
        deconstructor = new Deconstructor[MigrationError.SerializationFailed] {
          def usedRegisters: RegisterOffset = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationError.SerializationFailed): Unit = {
            out.setObject(offset + 0, in.message)
            out.setObject(offset + 1, in.path)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val genericErrorSchema: Schema[MigrationError.Generic] = new Schema(
    reflect = new Reflect.Record[Binding, MigrationError.Generic](
      fields = Chunk(
        Schema[String].reflect.asTerm("message"),
        Schema[Option[DynamicOptic]].reflect.asTerm("path")
      ),
      typeId = TypeId.of[MigrationError.Generic],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MigrationError.Generic] {
          def usedRegisters: RegisterOffset = 2
          def construct(in: Registers, offset: RegisterOffset): MigrationError.Generic =
            new MigrationError.Generic(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[Option[DynamicOptic]]
            )
        },
        deconstructor = new Deconstructor[MigrationError.Generic] {
          def usedRegisters: RegisterOffset = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: MigrationError.Generic): Unit = {
            out.setObject(offset + 0, in.message)
            out.setObject(offset + 1, in.path)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  // MigrationError is a sealed trait with multiple cases
  implicit lazy val migrationErrorSchema: Schema[MigrationError] = new Schema(
    reflect = new Reflect.Variant[Binding, MigrationError](
      cases = Chunk(
        fieldNotFoundSchema.reflect.asTerm("FieldNotFound"),
        caseNotFoundSchema.reflect.asTerm("CaseNotFound"),
        typeMismatchSchema.reflect.asTerm("TypeMismatch"),
        transformFailedSchema.reflect.asTerm("TransformFailed"),
        valueNotFoundSchema.reflect.asTerm("ValueNotFound"),
        invalidOperationSchema.reflect.asTerm("InvalidOperation"),
        compositionFailedSchema.reflect.asTerm("CompositionFailed"),
        serializationFailedSchema.reflect.asTerm("SerializationFailed"),
        genericErrorSchema.reflect.asTerm("Generic")
      ),
      typeId = TypeId.of[MigrationError],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[MigrationError] {
          def discriminate(a: MigrationError): Int = a match {
            case _: MigrationError.FieldNotFound => 0
            case _: MigrationError.CaseNotFound => 1
            case _: MigrationError.TypeMismatch => 2
            case _: MigrationError.TransformFailed => 3
            case _: MigrationError.ValueNotFound => 4
            case _: MigrationError.InvalidOperation => 5
            case _: MigrationError.CompositionFailed => 6
            case _: MigrationError.SerializationFailed => 7
            case _: MigrationError.Generic => 8
          }
        },
        matchers = Matchers(
          new Matcher[MigrationError.FieldNotFound] {
            def downcastOrNull(a: Any): MigrationError.FieldNotFound = a match {
              case x: MigrationError.FieldNotFound => x
              case _ => null
            }
          },
          new Matcher[MigrationError.CaseNotFound] {
            def downcastOrNull(a: Any): MigrationError.CaseNotFound = a match {
              case x: MigrationError.CaseNotFound => x
              case _ => null
            }
          },
          new Matcher[MigrationError.TypeMismatch] {
            def downcastOrNull(a: Any): MigrationError.TypeMismatch = a match {
              case x: MigrationError.TypeMismatch => x
              case _ => null
            }
          },
          new Matcher[MigrationError.TransformFailed] {
            def downcastOrNull(a: Any): MigrationError.TransformFailed = a match {
              case x: MigrationError.TransformFailed => x
              case _ => null
            }
          },
          new Matcher[MigrationError.ValueNotFound] {
            def downcastOrNull(a: Any): MigrationError.ValueNotFound = a match {
              case x: MigrationError.ValueNotFound => x
              case _ => null
            }
          },
          new Matcher[MigrationError.InvalidOperation] {
            def downcastOrNull(a: Any): MigrationError.InvalidOperation = a match {
              case x: MigrationError.InvalidOperation => x
              case _ => null
            }
          },
          new Matcher[MigrationError.CompositionFailed] {
            def downcastOrNull(a: Any): MigrationError.CompositionFailed = a match {
              case x: MigrationError.CompositionFailed => x
              case _ => null
            }
          },
          new Matcher[MigrationError.SerializationFailed] {
            def downcastOrNull(a: Any): MigrationError.SerializationFailed = a match {
              case x: MigrationError.SerializationFailed => x
              case _ => null
            }
          },
          new Matcher[MigrationError.Generic] {
            def downcastOrNull(a: Any): MigrationError.Generic = a match {
              case x: MigrationError.Generic => x
              case _ => null
            }
          }
        )
      ),
      modifiers = Chunk.empty
    )
  )
}
