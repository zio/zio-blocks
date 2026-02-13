package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Reflect, Schema}
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.typeid.TypeId

sealed trait MigrationError {
  def message: String
  def source: DynamicOptic
}

object MigrationError {

  case class FieldNotFound(source: DynamicOptic, fieldName: String) extends MigrationError {
    override def message: String = s"Field '$fieldName' not found at: ${source.toScalaString}"
  }

  case class CaseNotFound(source: DynamicOptic, caseName: String) extends MigrationError {
    override def message: String = s"Case '$caseName' not found at: ${source.toScalaString}"
  }

  case class TypeMismatch(source: DynamicOptic, expected: String, actual: String) extends MigrationError {
    override def message: String = s"Type mismatch at: ${source.toScalaString}: expected $expected, got $actual"
  }

  case class InvalidIndex(source: DynamicOptic, index: Int, size: Int) extends MigrationError {
    override def message: String = s"Index $index out of bounds (size: $size) at: ${source.toScalaString}"
  }

  case class TransformFailed(source: DynamicOptic, reason: String) extends MigrationError {
    override def message: String = s"Transform failed at: ${source.toScalaString}: $reason"
  }

  case class ExpressionEvalFailed(source: DynamicOptic, reason: String) extends MigrationError {
    override def message: String = s"Expression evaluation failed at: ${source.toScalaString}: $reason"
  }

  case class FieldAlreadyExists(source: DynamicOptic, fieldName: String) extends MigrationError {
    override def message: String = s"Field '$fieldName' already exists at: ${source.toScalaString}"
  }

  case class IncompatibleValue(source: DynamicOptic, reason: String) extends MigrationError {
    override def message: String = s"Incompatible value at: ${source.toScalaString}: $reason"
  }

  case class DuplicateMapKey(source: DynamicOptic) extends MigrationError {
    override def message: String = s"Migration produced duplicate map keys at: ${source.toScalaString}"
  }

  def fieldNotFound(source: DynamicOptic, fieldName: String): MigrationError =
    FieldNotFound(source, fieldName)

  def typeMismatch(source: DynamicOptic, expected: String, actual: String): MigrationError =
    TypeMismatch(source, expected, actual)

  def transformFailed(source: DynamicOptic, reason: String): MigrationError =
    TransformFailed(source, reason)

  def fieldAlreadyExists(source: DynamicOptic, fieldName: String): MigrationError =
    FieldAlreadyExists(source, fieldName)

  def incompatibleValue(source: DynamicOptic, reason: String): MigrationError =
    IncompatibleValue(source, reason)

  def duplicateMapKey(source: DynamicOptic): MigrationError =
    DuplicateMapKey(source)

  implicit lazy val fieldNotFoundSchema: Schema[FieldNotFound] = new Schema(
    reflect = new Reflect.Record[Binding, FieldNotFound](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("source"),
        Schema[String].reflect.asTerm("fieldName")
      ),
      typeId = TypeId.of[FieldNotFound],
      recordBinding = new Binding.Record(
        constructor = new Constructor[FieldNotFound] {
          def usedRegisters: RegisterOffset                                   = 2
          def construct(in: Registers, offset: RegisterOffset): FieldNotFound =
            FieldNotFound(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[FieldNotFound] {
          def usedRegisters: RegisterOffset                                                = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: FieldNotFound): Unit = {
            out.setObject(offset + 0, in.source)
            out.setObject(offset + 1, in.fieldName)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val caseNotFoundSchema: Schema[CaseNotFound] = new Schema(
    reflect = new Reflect.Record[Binding, CaseNotFound](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("source"),
        Schema[String].reflect.asTerm("caseName")
      ),
      typeId = TypeId.of[CaseNotFound],
      recordBinding = new Binding.Record(
        constructor = new Constructor[CaseNotFound] {
          def usedRegisters: RegisterOffset                                  = 2
          def construct(in: Registers, offset: RegisterOffset): CaseNotFound =
            CaseNotFound(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[CaseNotFound] {
          def usedRegisters: RegisterOffset                                               = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: CaseNotFound): Unit = {
            out.setObject(offset + 0, in.source)
            out.setObject(offset + 1, in.caseName)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val typeMismatchSchema: Schema[TypeMismatch] = new Schema(
    reflect = new Reflect.Record[Binding, TypeMismatch](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("source"),
        Schema[String].reflect.asTerm("expected"),
        Schema[String].reflect.asTerm("actual")
      ),
      typeId = TypeId.of[TypeMismatch],
      recordBinding = new Binding.Record(
        constructor = new Constructor[TypeMismatch] {
          def usedRegisters: RegisterOffset                                  = 3
          def construct(in: Registers, offset: RegisterOffset): TypeMismatch =
            TypeMismatch(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[TypeMismatch] {
          def usedRegisters: RegisterOffset                                               = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: TypeMismatch): Unit = {
            out.setObject(offset + 0, in.source)
            out.setObject(offset + 1, in.expected)
            out.setObject(offset + 2, in.actual)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val invalidIndexSchema: Schema[InvalidIndex] = new Schema(
    reflect = new Reflect.Record[Binding, InvalidIndex](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("source"),
        Schema[Int].reflect.asTerm("index"),
        Schema[Int].reflect.asTerm("size")
      ),
      typeId = TypeId.of[InvalidIndex],
      recordBinding = new Binding.Record(
        constructor = new Constructor[InvalidIndex] {
          def usedRegisters: RegisterOffset                                  = RegisterOffset(ints = 2, objects = 1)
          def construct(in: Registers, offset: RegisterOffset): InvalidIndex =
            InvalidIndex(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getInt(offset),
              in.getInt(RegisterOffset.incrementFloatsAndInts(offset))
            )
        },
        deconstructor = new Deconstructor[InvalidIndex] {
          def usedRegisters: RegisterOffset                                               = RegisterOffset(ints = 2, objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: InvalidIndex): Unit = {
            out.setObject(offset, in.source)
            out.setInt(offset, in.index)
            out.setInt(RegisterOffset.incrementFloatsAndInts(offset), in.size)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val transformFailedSchema: Schema[TransformFailed] = new Schema(
    reflect = new Reflect.Record[Binding, TransformFailed](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("source"),
        Schema[String].reflect.asTerm("reason")
      ),
      typeId = TypeId.of[TransformFailed],
      recordBinding = new Binding.Record(
        constructor = new Constructor[TransformFailed] {
          def usedRegisters: RegisterOffset                                     = 2
          def construct(in: Registers, offset: RegisterOffset): TransformFailed =
            TransformFailed(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[TransformFailed] {
          def usedRegisters: RegisterOffset                                                  = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: TransformFailed): Unit = {
            out.setObject(offset + 0, in.source)
            out.setObject(offset + 1, in.reason)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val expressionEvalFailedSchema: Schema[ExpressionEvalFailed] = new Schema(
    reflect = new Reflect.Record[Binding, ExpressionEvalFailed](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("source"),
        Schema[String].reflect.asTerm("reason")
      ),
      typeId = TypeId.of[ExpressionEvalFailed],
      recordBinding = new Binding.Record(
        constructor = new Constructor[ExpressionEvalFailed] {
          def usedRegisters: RegisterOffset                                          = 2
          def construct(in: Registers, offset: RegisterOffset): ExpressionEvalFailed =
            ExpressionEvalFailed(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[ExpressionEvalFailed] {
          def usedRegisters: RegisterOffset                                                       = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: ExpressionEvalFailed): Unit = {
            out.setObject(offset + 0, in.source)
            out.setObject(offset + 1, in.reason)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val fieldAlreadyExistsSchema: Schema[FieldAlreadyExists] = new Schema(
    reflect = new Reflect.Record[Binding, FieldAlreadyExists](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("source"),
        Schema[String].reflect.asTerm("fieldName")
      ),
      typeId = TypeId.of[FieldAlreadyExists],
      recordBinding = new Binding.Record(
        constructor = new Constructor[FieldAlreadyExists] {
          def usedRegisters: RegisterOffset                                        = 2
          def construct(in: Registers, offset: RegisterOffset): FieldAlreadyExists =
            FieldAlreadyExists(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[FieldAlreadyExists] {
          def usedRegisters: RegisterOffset                                                     = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: FieldAlreadyExists): Unit = {
            out.setObject(offset + 0, in.source)
            out.setObject(offset + 1, in.fieldName)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val incompatibleValueSchema: Schema[IncompatibleValue] = new Schema(
    reflect = new Reflect.Record[Binding, IncompatibleValue](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("source"),
        Schema[String].reflect.asTerm("reason")
      ),
      typeId = TypeId.of[IncompatibleValue],
      recordBinding = new Binding.Record(
        constructor = new Constructor[IncompatibleValue] {
          def usedRegisters: RegisterOffset                                       = 2
          def construct(in: Registers, offset: RegisterOffset): IncompatibleValue =
            IncompatibleValue(
              in.getObject(offset + 0).asInstanceOf[DynamicOptic],
              in.getObject(offset + 1).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[IncompatibleValue] {
          def usedRegisters: RegisterOffset                                                    = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: IncompatibleValue): Unit = {
            out.setObject(offset + 0, in.source)
            out.setObject(offset + 1, in.reason)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val duplicateMapKeySchema: Schema[DuplicateMapKey] = new Schema(
    reflect = new Reflect.Record[Binding, DuplicateMapKey](
      fields = Vector(
        Schema[DynamicOptic].reflect.asTerm("source")
      ),
      typeId = TypeId.of[DuplicateMapKey],
      recordBinding = new Binding.Record(
        constructor = new Constructor[DuplicateMapKey] {
          def usedRegisters: RegisterOffset                                     = 1
          def construct(in: Registers, offset: RegisterOffset): DuplicateMapKey =
            DuplicateMapKey(in.getObject(offset + 0).asInstanceOf[DynamicOptic])
        },
        deconstructor = new Deconstructor[DuplicateMapKey] {
          def usedRegisters: RegisterOffset                                                  = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: DuplicateMapKey): Unit =
            out.setObject(offset + 0, in.source)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val schema: Schema[MigrationError] = new Schema(
    reflect = new Reflect.Variant[Binding, MigrationError](
      cases = Vector(
        fieldNotFoundSchema.reflect.asTerm("FieldNotFound"),
        caseNotFoundSchema.reflect.asTerm("CaseNotFound"),
        typeMismatchSchema.reflect.asTerm("TypeMismatch"),
        invalidIndexSchema.reflect.asTerm("InvalidIndex"),
        transformFailedSchema.reflect.asTerm("TransformFailed"),
        expressionEvalFailedSchema.reflect.asTerm("ExpressionEvalFailed"),
        fieldAlreadyExistsSchema.reflect.asTerm("FieldAlreadyExists"),
        incompatibleValueSchema.reflect.asTerm("IncompatibleValue"),
        duplicateMapKeySchema.reflect.asTerm("DuplicateMapKey")
      ),
      typeId = TypeId.of[MigrationError],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[MigrationError] {
          def discriminate(a: MigrationError): Int = a match {
            case _: FieldNotFound        => 0
            case _: CaseNotFound         => 1
            case _: TypeMismatch         => 2
            case _: InvalidIndex         => 3
            case _: TransformFailed      => 4
            case _: ExpressionEvalFailed => 5
            case _: FieldAlreadyExists   => 6
            case _: IncompatibleValue    => 7
            case _: DuplicateMapKey      => 8
          }
        },
        matchers = Matchers(
          new Matcher[FieldNotFound] {
            def downcastOrNull(a: Any): FieldNotFound = a match {
              case x: FieldNotFound => x
              case _                => null.asInstanceOf[FieldNotFound]
            }
          },
          new Matcher[CaseNotFound] {
            def downcastOrNull(a: Any): CaseNotFound = a match {
              case x: CaseNotFound => x
              case _               => null.asInstanceOf[CaseNotFound]
            }
          },
          new Matcher[TypeMismatch] {
            def downcastOrNull(a: Any): TypeMismatch = a match {
              case x: TypeMismatch => x
              case _               => null.asInstanceOf[TypeMismatch]
            }
          },
          new Matcher[InvalidIndex] {
            def downcastOrNull(a: Any): InvalidIndex = a match {
              case x: InvalidIndex => x
              case _               => null.asInstanceOf[InvalidIndex]
            }
          },
          new Matcher[TransformFailed] {
            def downcastOrNull(a: Any): TransformFailed = a match {
              case x: TransformFailed => x
              case _                  => null.asInstanceOf[TransformFailed]
            }
          },
          new Matcher[ExpressionEvalFailed] {
            def downcastOrNull(a: Any): ExpressionEvalFailed = a match {
              case x: ExpressionEvalFailed => x
              case _                       => null.asInstanceOf[ExpressionEvalFailed]
            }
          },
          new Matcher[FieldAlreadyExists] {
            def downcastOrNull(a: Any): FieldAlreadyExists = a match {
              case x: FieldAlreadyExists => x
              case _                     => null.asInstanceOf[FieldAlreadyExists]
            }
          },
          new Matcher[IncompatibleValue] {
            def downcastOrNull(a: Any): IncompatibleValue = a match {
              case x: IncompatibleValue => x
              case _                    => null.asInstanceOf[IncompatibleValue]
            }
          },
          new Matcher[DuplicateMapKey] {
            def downcastOrNull(a: Any): DuplicateMapKey = a match {
              case x: DuplicateMapKey => x
              case _                  => null.asInstanceOf[DuplicateMapKey]
            }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )
}
