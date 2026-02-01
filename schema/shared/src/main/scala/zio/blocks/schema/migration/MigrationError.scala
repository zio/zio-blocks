package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicOptic, Schema}
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.Reflect
import zio.blocks.typeid.TypeId

sealed trait MigrationError {
  def message: String
  def path: DynamicOptic
}

object MigrationError {

  final case class FieldNotFound(fieldName: String, path: DynamicOptic) extends MigrationError {
    def message: String = s"Field '$fieldName' not found at path ${path.toString}"
  }

  final case class CaseNotFound(caseName: String, path: DynamicOptic) extends MigrationError {
    def message: String = s"Case '$caseName' not found at path ${path.toString}"
  }

  final case class TypeMismatch(expected: String, actual: String, path: DynamicOptic) extends MigrationError {
    def message: String = s"Type mismatch at ${path.toString}: expected $expected, got $actual"
  }

  final case class InvalidIndex(index: Int, size: Int, path: DynamicOptic) extends MigrationError {
    def message: String = s"Index $index out of bounds (size: $size) at ${path.toString}"
  }

  final case class TransformFailed(reason: String, path: DynamicOptic) extends MigrationError {
    def message: String = s"Transform failed at ${path.toString}: $reason"
  }

  final case class ExpressionEvalFailed(reason: String, path: DynamicOptic) extends MigrationError {
    def message: String = s"Expression evaluation failed at ${path.toString}: $reason"
  }

  final case class FieldAlreadyExists(fieldName: String, path: DynamicOptic) extends MigrationError {
    def message: String = s"Field '$fieldName' already exists at ${path.toString}"
  }

  final case class IncompatibleValue(reason: String, path: DynamicOptic) extends MigrationError {
    def message: String = s"Incompatible value at ${path.toString}: $reason"
  }

  def fieldNotFound(fieldName: String, path: DynamicOptic): MigrationError =
    FieldNotFound(fieldName, path)

  def caseNotFound(caseName: String, path: DynamicOptic): MigrationError =
    CaseNotFound(caseName, path)

  def typeMismatch(expected: String, actual: String, path: DynamicOptic): MigrationError =
    TypeMismatch(expected, actual, path)

  def invalidIndex(index: Int, size: Int, path: DynamicOptic): MigrationError =
    InvalidIndex(index, size, path)

  def transformFailed(reason: String, path: DynamicOptic): MigrationError =
    TransformFailed(reason, path)

  def expressionEvalFailed(reason: String, path: DynamicOptic): MigrationError =
    ExpressionEvalFailed(reason, path)

  def fieldAlreadyExists(fieldName: String, path: DynamicOptic): MigrationError =
    FieldAlreadyExists(fieldName, path)

  def incompatibleValue(reason: String, path: DynamicOptic): MigrationError =
    IncompatibleValue(reason, path)

  implicit lazy val fieldNotFoundSchema: Schema[FieldNotFound] = new Schema(
    reflect = new Reflect.Record[Binding, FieldNotFound](
      fields = Vector(
        Schema[String].reflect.asTerm("fieldName"),
        Schema[DynamicOptic].reflect.asTerm("path")
      ),
      typeId = TypeId.of[FieldNotFound],
      recordBinding = new Binding.Record(
        constructor = new Constructor[FieldNotFound] {
          def usedRegisters: RegisterOffset                                   = 2
          def construct(in: Registers, offset: RegisterOffset): FieldNotFound =
            FieldNotFound(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[DynamicOptic]
            )
        },
        deconstructor = new Deconstructor[FieldNotFound] {
          def usedRegisters: RegisterOffset                                                = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: FieldNotFound): Unit = {
            out.setObject(offset + 0, in.fieldName)
            out.setObject(offset + 1, in.path)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val caseNotFoundSchema: Schema[CaseNotFound] = new Schema(
    reflect = new Reflect.Record[Binding, CaseNotFound](
      fields = Vector(
        Schema[String].reflect.asTerm("caseName"),
        Schema[DynamicOptic].reflect.asTerm("path")
      ),
      typeId = TypeId.of[CaseNotFound],
      recordBinding = new Binding.Record(
        constructor = new Constructor[CaseNotFound] {
          def usedRegisters: RegisterOffset                                  = 2
          def construct(in: Registers, offset: RegisterOffset): CaseNotFound =
            CaseNotFound(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[DynamicOptic]
            )
        },
        deconstructor = new Deconstructor[CaseNotFound] {
          def usedRegisters: RegisterOffset                                               = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: CaseNotFound): Unit = {
            out.setObject(offset + 0, in.caseName)
            out.setObject(offset + 1, in.path)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val typeMismatchSchema: Schema[TypeMismatch] = new Schema(
    reflect = new Reflect.Record[Binding, TypeMismatch](
      fields = Vector(
        Schema[String].reflect.asTerm("expected"),
        Schema[String].reflect.asTerm("actual"),
        Schema[DynamicOptic].reflect.asTerm("path")
      ),
      typeId = TypeId.of[TypeMismatch],
      recordBinding = new Binding.Record(
        constructor = new Constructor[TypeMismatch] {
          def usedRegisters: RegisterOffset                                  = 3
          def construct(in: Registers, offset: RegisterOffset): TypeMismatch =
            TypeMismatch(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[DynamicOptic]
            )
        },
        deconstructor = new Deconstructor[TypeMismatch] {
          def usedRegisters: RegisterOffset                                               = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: TypeMismatch): Unit = {
            out.setObject(offset + 0, in.expected)
            out.setObject(offset + 1, in.actual)
            out.setObject(offset + 2, in.path)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val invalidIndexSchema: Schema[InvalidIndex] = new Schema(
    reflect = new Reflect.Record[Binding, InvalidIndex](
      fields = Vector(
        Schema[Int].reflect.asTerm("index"),
        Schema[Int].reflect.asTerm("size"),
        Schema[DynamicOptic].reflect.asTerm("path")
      ),
      typeId = TypeId.of[InvalidIndex],
      recordBinding = new Binding.Record(
        constructor = new Constructor[InvalidIndex] {
          def usedRegisters: RegisterOffset                                  = 3
          def construct(in: Registers, offset: RegisterOffset): InvalidIndex =
            InvalidIndex(
              in.getInt(offset + 0),
              in.getInt(offset + 1),
              in.getObject(offset + 2).asInstanceOf[DynamicOptic]
            )
        },
        deconstructor = new Deconstructor[InvalidIndex] {
          def usedRegisters: RegisterOffset                                               = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: InvalidIndex): Unit = {
            out.setInt(offset + 0, in.index)
            out.setInt(offset + 1, in.size)
            out.setObject(offset + 2, in.path)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val transformFailedSchema: Schema[TransformFailed] = new Schema(
    reflect = new Reflect.Record[Binding, TransformFailed](
      fields = Vector(
        Schema[String].reflect.asTerm("reason"),
        Schema[DynamicOptic].reflect.asTerm("path")
      ),
      typeId = TypeId.of[TransformFailed],
      recordBinding = new Binding.Record(
        constructor = new Constructor[TransformFailed] {
          def usedRegisters: RegisterOffset                                     = 2
          def construct(in: Registers, offset: RegisterOffset): TransformFailed =
            TransformFailed(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[DynamicOptic]
            )
        },
        deconstructor = new Deconstructor[TransformFailed] {
          def usedRegisters: RegisterOffset                                                  = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: TransformFailed): Unit = {
            out.setObject(offset + 0, in.reason)
            out.setObject(offset + 1, in.path)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val expressionEvalFailedSchema: Schema[ExpressionEvalFailed] = new Schema(
    reflect = new Reflect.Record[Binding, ExpressionEvalFailed](
      fields = Vector(
        Schema[String].reflect.asTerm("reason"),
        Schema[DynamicOptic].reflect.asTerm("path")
      ),
      typeId = TypeId.of[ExpressionEvalFailed],
      recordBinding = new Binding.Record(
        constructor = new Constructor[ExpressionEvalFailed] {
          def usedRegisters: RegisterOffset                                          = 2
          def construct(in: Registers, offset: RegisterOffset): ExpressionEvalFailed =
            ExpressionEvalFailed(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[DynamicOptic]
            )
        },
        deconstructor = new Deconstructor[ExpressionEvalFailed] {
          def usedRegisters: RegisterOffset                                                       = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: ExpressionEvalFailed): Unit = {
            out.setObject(offset + 0, in.reason)
            out.setObject(offset + 1, in.path)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val fieldAlreadyExistsSchema: Schema[FieldAlreadyExists] = new Schema(
    reflect = new Reflect.Record[Binding, FieldAlreadyExists](
      fields = Vector(
        Schema[String].reflect.asTerm("fieldName"),
        Schema[DynamicOptic].reflect.asTerm("path")
      ),
      typeId = TypeId.of[FieldAlreadyExists],
      recordBinding = new Binding.Record(
        constructor = new Constructor[FieldAlreadyExists] {
          def usedRegisters: RegisterOffset                                        = 2
          def construct(in: Registers, offset: RegisterOffset): FieldAlreadyExists =
            FieldAlreadyExists(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[DynamicOptic]
            )
        },
        deconstructor = new Deconstructor[FieldAlreadyExists] {
          def usedRegisters: RegisterOffset                                                     = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: FieldAlreadyExists): Unit = {
            out.setObject(offset + 0, in.fieldName)
            out.setObject(offset + 1, in.path)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val incompatibleValueSchema: Schema[IncompatibleValue] = new Schema(
    reflect = new Reflect.Record[Binding, IncompatibleValue](
      fields = Vector(
        Schema[String].reflect.asTerm("reason"),
        Schema[DynamicOptic].reflect.asTerm("path")
      ),
      typeId = TypeId.of[IncompatibleValue],
      recordBinding = new Binding.Record(
        constructor = new Constructor[IncompatibleValue] {
          def usedRegisters: RegisterOffset                                       = 2
          def construct(in: Registers, offset: RegisterOffset): IncompatibleValue =
            IncompatibleValue(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[DynamicOptic]
            )
        },
        deconstructor = new Deconstructor[IncompatibleValue] {
          def usedRegisters: RegisterOffset                                                    = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: IncompatibleValue): Unit = {
            out.setObject(offset + 0, in.reason)
            out.setObject(offset + 1, in.path)
          }
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
        incompatibleValueSchema.reflect.asTerm("IncompatibleValue")
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
          }
        )
      ),
      modifiers = Vector.empty
    )
  )
}
