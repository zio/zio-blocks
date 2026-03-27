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
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.{DynamicOptic, Reflect, Schema}
import zio.blocks.typeid.TypeId

sealed trait MigrationError {
  def path: DynamicOptic
  def message: String
}

object MigrationError {
  final case class MissingField(path: DynamicOptic, fieldName: String) extends MigrationError {
    def message: String = s"Missing field '$fieldName'"
  }

  final case class TypeMismatch(path: DynamicOptic, expected: String, actual: String) extends MigrationError {
    def message: String = s"Type mismatch. Expected: $expected, actual: $actual"
  }

  final case class InvalidValue(path: DynamicOptic, detail: String) extends MigrationError {
    def message: String = detail
  }

  final case class CompositeError(errors: Vector[MigrationError]) extends MigrationError {
    def path: DynamicOptic = errors.headOption.map(_.path).getOrElse(DynamicOptic.root)
    def message: String    = errors.map(_.message).mkString("; ")
  }

  implicit lazy val missingFieldSchema: Schema[MissingField] = new Schema(
    reflect = new Reflect.Record[Binding, MissingField](
      fields = Chunk(Schema[DynamicOptic].reflect.asTerm("path"), Schema[String].reflect.asTerm("fieldName")),
      typeId = TypeId.of[MissingField],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MissingField] {
          def usedRegisters: RegisterOffset                                     = RegisterOffset(objects = 2)
          def construct(in: Registers, offset: RegisterOffset): MissingField =
            MissingField(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getObject(RegisterOffset.incrementObjects(offset)).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[MissingField] {
          def usedRegisters: RegisterOffset                                                  = RegisterOffset(objects = 2)
          def deconstruct(out: Registers, offset: RegisterOffset, in: MissingField): Unit = {
            out.setObject(offset, in.path)
            out.setObject(RegisterOffset.incrementObjects(offset), in.fieldName)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val typeMismatchSchema: Schema[TypeMismatch] = new Schema(
    reflect = new Reflect.Record[Binding, TypeMismatch](
      fields = Chunk(
        Schema[DynamicOptic].reflect.asTerm("path"),
        Schema[String].reflect.asTerm("expected"),
        Schema[String].reflect.asTerm("actual")
      ),
      typeId = TypeId.of[TypeMismatch],
      recordBinding = new Binding.Record(
        constructor = new Constructor[TypeMismatch] {
          def usedRegisters: RegisterOffset                                      = RegisterOffset(objects = 3)
          def construct(in: Registers, offset: RegisterOffset): TypeMismatch =
            TypeMismatch(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getObject(RegisterOffset.incrementObjects(offset)).asInstanceOf[String],
              in.getObject(RegisterOffset.incrementObjects(RegisterOffset.incrementObjects(offset))).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[TypeMismatch] {
          def usedRegisters: RegisterOffset                                                   = RegisterOffset(objects = 3)
          def deconstruct(out: Registers, offset: RegisterOffset, in: TypeMismatch): Unit = {
            out.setObject(offset, in.path)
            out.setObject(RegisterOffset.incrementObjects(offset), in.expected)
            out.setObject(RegisterOffset.incrementObjects(RegisterOffset.incrementObjects(offset)), in.actual)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val invalidValueSchema: Schema[InvalidValue] = new Schema(
    reflect = new Reflect.Record[Binding, InvalidValue](
      fields = Chunk(Schema[DynamicOptic].reflect.asTerm("path"), Schema[String].reflect.asTerm("detail")),
      typeId = TypeId.of[InvalidValue],
      recordBinding = new Binding.Record(
        constructor = new Constructor[InvalidValue] {
          def usedRegisters: RegisterOffset                                     = RegisterOffset(objects = 2)
          def construct(in: Registers, offset: RegisterOffset): InvalidValue =
            InvalidValue(
              in.getObject(offset).asInstanceOf[DynamicOptic],
              in.getObject(RegisterOffset.incrementObjects(offset)).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[InvalidValue] {
          def usedRegisters: RegisterOffset                                                  = RegisterOffset(objects = 2)
          def deconstruct(out: Registers, offset: RegisterOffset, in: InvalidValue): Unit = {
            out.setObject(offset, in.path)
            out.setObject(RegisterOffset.incrementObjects(offset), in.detail)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val compositeErrorSchema: Schema[CompositeError] = new Schema(
    reflect = new Reflect.Record[Binding, CompositeError](
      fields = Chunk.single(Schema[Vector[MigrationError]].reflect.asTerm("errors")),
      typeId = TypeId.of[CompositeError],
      recordBinding = new Binding.Record(
        constructor = new Constructor[CompositeError] {
          def usedRegisters: RegisterOffset                                     = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): CompositeError =
            CompositeError(in.getObject(offset).asInstanceOf[Vector[MigrationError]])
        },
        deconstructor = new Deconstructor[CompositeError] {
          def usedRegisters: RegisterOffset                                                  = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: CompositeError): Unit =
            out.setObject(offset, in.errors)
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val schema: Schema[MigrationError] = new Schema(
    reflect = new Reflect.Variant[Binding, MigrationError](
      cases = Chunk(
        missingFieldSchema.reflect.asTerm("MissingField"),
        typeMismatchSchema.reflect.asTerm("TypeMismatch"),
        invalidValueSchema.reflect.asTerm("InvalidValue"),
        compositeErrorSchema.reflect.asTerm("CompositeError")
      ),
      typeId = TypeId.of[MigrationError],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[MigrationError] {
          def discriminate(a: MigrationError): Int = a match {
            case _: MissingField   => 0
            case _: TypeMismatch   => 1
            case _: InvalidValue   => 2
            case _: CompositeError => 3
          }
        },
        matchers = Matchers(
          new Matcher[MissingField] {
            def downcastOrNull(a: Any): MissingField = a match {
              case x: MissingField => x
              case _               => null.asInstanceOf[MissingField]
            }
          },
          new Matcher[TypeMismatch] {
            def downcastOrNull(a: Any): TypeMismatch = a match {
              case x: TypeMismatch => x
              case _               => null.asInstanceOf[TypeMismatch]
            }
          },
          new Matcher[InvalidValue] {
            def downcastOrNull(a: Any): InvalidValue = a match {
              case x: InvalidValue => x
              case _               => null.asInstanceOf[InvalidValue]
            }
          },
          new Matcher[CompositeError] {
            def downcastOrNull(a: Any): CompositeError = a match {
              case x: CompositeError => x
              case _                 => null.asInstanceOf[CompositeError]
            }
          }
        )
      ),
      modifiers = Chunk.empty
    )
  )
}
