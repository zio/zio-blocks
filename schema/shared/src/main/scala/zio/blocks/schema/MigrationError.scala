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
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.schema.binding.Register
import zio.blocks.typeid.TypeId

/**
 * An error that occurs during migration execution.
 */
sealed trait MigrationError extends Product with Serializable {

  /** A human-readable message describing the error. */
  def message: String
}

object MigrationError {

  final case class NotFound(
    path: String,
    details: String
  ) extends MigrationError {
    def message: String =
      if (path.isEmpty) s"Value not found: $details"
      else s"Value not found at '$path': $details"
  }

  final case class TypeMismatch(
    path: String,
    expected: String,
    actual: String
  ) extends MigrationError {
    def message: String =
      if (path.isEmpty) s"Type mismatch: expected $expected but found $actual"
      else s"Type mismatch at '$path': expected $expected but found $actual"
  }

  final case class MissingField(
    path: String,
    fieldName: String
  ) extends MigrationError {
    def message: String =
      if (path.isEmpty) s"Missing field '$fieldName'"
      else s"Missing field '$fieldName' at '$path'"
  }

  final case class UnknownCase(
    path: String,
    caseName: String
  ) extends MigrationError {
    def message: String =
      if (path.isEmpty) s"Unknown case '$caseName'"
      else s"Unknown case '$caseName' at '$path'"
  }

  final case class TransformFailed(
    path: String,
    details: String
  ) extends MigrationError {
    def message: String =
      if (path.isEmpty) s"Transform failed: $details"
      else s"Transform failed at '$path': $details"
  }

  final case class IndexOutOfBounds(
    path: String,
    index: Int,
    size: Int
  ) extends MigrationError {
    def message: String =
      if (path.isEmpty) s"Index $index out of bounds (size: $size)"
      else s"Index $index out of bounds (size: $size) at '$path'"
  }

  final case class KeyNotFound(
    path: String,
    key: DynamicValue
  ) extends MigrationError {
    def message: String =
      if (path.isEmpty) s"Key not found: $key"
      else s"Key not found at '$path': $key"
  }

  final case class DefaultFailed(
    path: String,
    details: String
  ) extends MigrationError {
    def message: String =
      if (path.isEmpty) s"Failed to compute default value: $details"
      else s"Failed to compute default value at '$path': $details"
  }

  final case class InvalidAction(
    path: String,
    action: String,
    reason: String
  ) extends MigrationError {
    def message: String =
      if (path.isEmpty) s"Invalid action '$action': $reason"
      else s"Invalid action '$action' at '$path': $reason"
  }

  final case class Multiple(
    errors: Chunk[MigrationError]
  ) extends MigrationError {
    require(errors.nonEmpty, "Multiple errors must contain at least one error")

    def message: String = {
      val sb = new StringBuilder
      errors.zipWithIndex.foreach { case (e, idx) =>
        if (idx > 0) sb.append('\n')
        sb.append(e.message)
      }
      sb.toString()
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════════
  // Smart Constructors
  // ═══════════════════════════════════════════════════════════════════════════════

  def notFound(details: String): MigrationError =
    NotFound("", details)

  def notFound(path: String, details: String): MigrationError =
    NotFound(path, details)

  def notFound(at: DynamicOptic, details: String): MigrationError =
    NotFound(at.toScalaString, details)

  def typeMismatch(expected: String, actual: String): MigrationError =
    TypeMismatch("", expected, actual)

  def typeMismatch(path: String, expected: String, actual: String): MigrationError =
    TypeMismatch(path, expected, actual)

  def typeMismatch(at: DynamicOptic, expected: String, actual: String): MigrationError =
    TypeMismatch(at.toScalaString, expected, actual)

  def missingField(fieldName: String): MigrationError =
    MissingField("", fieldName)

  def missingField(path: String, fieldName: String): MigrationError =
    MissingField(path, fieldName)

  def unknownCase(caseName: String): MigrationError =
    UnknownCase("", caseName)

  def unknownCase(path: String, caseName: String): MigrationError =
    UnknownCase(path, caseName)

  def unknownCase(at: DynamicOptic, caseName: String): MigrationError =
    UnknownCase(at.toScalaString, caseName)

  def transformFailed(details: String): MigrationError =
    TransformFailed("", details)

  def transformFailed(path: String, details: String): MigrationError =
    TransformFailed(path, details)

  def transformFailed(at: DynamicOptic, details: String): MigrationError =
    TransformFailed(at.toScalaString, details)

  def indexOutOfBounds(index: Int, size: Int): MigrationError =
    IndexOutOfBounds("", index, size)

  def indexOutOfBounds(path: String, index: Int, size: Int): MigrationError =
    IndexOutOfBounds(path, index, size)

  def keyNotFound(key: DynamicValue): MigrationError =
    KeyNotFound("", key)

  def keyNotFound(path: String, key: DynamicValue): MigrationError =
    KeyNotFound(path, key)

  def defaultFailed(details: String): MigrationError =
    DefaultFailed("", details)

  def defaultFailed(path: String, details: String): MigrationError =
    DefaultFailed(path, details)

  def invalidAction(action: String, reason: String): MigrationError =
    InvalidAction("", action, reason)

  def invalidAction(path: String, action: String, reason: String): MigrationError =
    InvalidAction(path, action, reason)

  def multiple(errors: Chunk[MigrationError]): MigrationError =
    if (errors.isEmpty) notFound("No errors")
    else if (errors.length == 1) errors.head
    else Multiple(errors)

  def multiple(errors: MigrationError*): MigrationError =
    multiple(Chunk.from(errors))

  def aggregate(errors: Chunk[MigrationError]): Option[MigrationError] =
    if (errors.isEmpty) None
    else if (errors.length == 1) Some(errors.head)
    else Some(Multiple(errors))

  def aggregate(errors: MigrationError*): Option[MigrationError] =
    aggregate(Chunk.from(errors))

  def fromSchemaError(schemaError: SchemaError): MigrationError =
    TransformFailed("", schemaError.message)

  // ═══════════════════════════════════════════════════════════════════════════════
  // Schema Instance
  // ═══════════════════════════════════════════════════════════════════════════════

  implicit lazy val schema: Schema[MigrationError] = new Schema(
    reflect = new Reflect.Variant[Binding, MigrationError](
      cases = Chunk(
        notFoundSchema.reflect.asTerm("NotFound"),
        typeMismatchSchema.reflect.asTerm("TypeMismatch"),
        missingFieldSchema.reflect.asTerm("MissingField"),
        unknownCaseSchema.reflect.asTerm("UnknownCase"),
        transformFailedSchema.reflect.asTerm("TransformFailed"),
        indexOutOfBoundsSchema.reflect.asTerm("IndexOutOfBounds"),
        keyNotFoundSchema.reflect.asTerm("KeyNotFound"),
        defaultFailedSchema.reflect.asTerm("DefaultFailed"),
        invalidActionSchema.reflect.asTerm("InvalidAction"),
        multipleErrorsSchema.reflect.asTerm("Multiple")
      ),
      typeId = TypeId.of[MigrationError],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[MigrationError] {
          def discriminate(a: MigrationError): Int = a match {
            case _: NotFound    => 0
            case _: TypeMismatch => 1
            case _: MissingField => 2
            case _: UnknownCase => 3
            case _: TransformFailed => 4
            case _: IndexOutOfBounds => 5
            case _: KeyNotFound => 6
            case _: DefaultFailed => 7
            case _: InvalidAction => 8
            case _: Multiple => 9
          }
        },
        matchers = Matchers(
          new Matcher[NotFound] {
            def downcastOrNull(a: Any): NotFound = a match {
              case x: NotFound => x
              case _           => null.asInstanceOf[NotFound]
            }
          },
          new Matcher[TypeMismatch] {
            def downcastOrNull(a: Any): TypeMismatch = a match {
              case x: TypeMismatch => x
              case _              => null.asInstanceOf[TypeMismatch]
            }
          },
          new Matcher[MissingField] {
            def downcastOrNull(a: Any): MissingField = a match {
              case x: MissingField => x
              case _               => null.asInstanceOf[MissingField]
            }
          },
          new Matcher[UnknownCase] {
            def downcastOrNull(a: Any): UnknownCase = a match {
              case x: UnknownCase => x
              case _              => null.asInstanceOf[UnknownCase]
            }
          },
          new Matcher[TransformFailed] {
            def downcastOrNull(a: Any): TransformFailed = a match {
              case x: TransformFailed => x
              case _                 => null.asInstanceOf[TransformFailed]
            }
          },
          new Matcher[IndexOutOfBounds] {
            def downcastOrNull(a: Any): IndexOutOfBounds = a match {
              case x: IndexOutOfBounds => x
              case _                   => null.asInstanceOf[IndexOutOfBounds]
            }
          },
          new Matcher[KeyNotFound] {
            def downcastOrNull(a: Any): KeyNotFound = a match {
              case x: KeyNotFound => x
              case _              => null.asInstanceOf[KeyNotFound]
            }
          },
          new Matcher[DefaultFailed] {
            def downcastOrNull(a: Any): DefaultFailed = a match {
              case x: DefaultFailed => x
              case _               => null.asInstanceOf[DefaultFailed]
            }
          },
          new Matcher[InvalidAction] {
            def downcastOrNull(a: Any): InvalidAction = a match {
              case x: InvalidAction => x
              case _               => null.asInstanceOf[InvalidAction]
            }
          },
          new Matcher[Multiple] {
            def downcastOrNull(a: Any): Multiple = a match {
              case x: Multiple => x
              case _          => null.asInstanceOf[Multiple]
            }
          }
        )
      ),
      modifiers = Chunk.empty
    )
  )

  // Individual schemas for each case class
  implicit lazy val notFoundSchema: Schema[NotFound] = new Schema(
    reflect = new Reflect.Record[Binding, NotFound](
      fields = Chunk(
        Schema[String].reflect.asTerm("path"),
        Schema[String].reflect.asTerm("details")
      ),
      typeId = TypeId.of[NotFound],
      recordBinding = new Binding.Record(
        constructor = new Constructor[NotFound] {
          def usedRegisters: RegisterOffset                            = 2
          def construct(in: Registers, offset: RegisterOffset): NotFound =
            new NotFound(
              in.getObject(offset).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[NotFound] {
          def usedRegisters: RegisterOffset                                         = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: NotFound): Unit = {
            out.setObject(offset, in.path)
            out.setObject(offset + 1, in.details)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val typeMismatchSchema: Schema[TypeMismatch] = new Schema(
    reflect = new Reflect.Record[Binding, TypeMismatch](
      fields = Chunk(
        Schema[String].reflect.asTerm("path"),
        Schema[String].reflect.asTerm("expected"),
        Schema[String].reflect.asTerm("actual")
      ),
      typeId = TypeId.of[TypeMismatch],
      recordBinding = new Binding.Record(
        constructor = new Constructor[TypeMismatch] {
          def usedRegisters: RegisterOffset                                = 3
          def construct(in: Registers, offset: RegisterOffset): TypeMismatch =
            new TypeMismatch(
              in.getObject(offset).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[TypeMismatch] {
          def usedRegisters: RegisterOffset                                              = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: TypeMismatch): Unit = {
            out.setObject(offset, in.path)
            out.setObject(offset + 1, in.expected)
            out.setObject(offset + 2, in.actual)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val missingFieldSchema: Schema[MissingField] = new Schema(
    reflect = new Reflect.Record[Binding, MissingField](
      fields = Chunk(
        Schema[String].reflect.asTerm("path"),
        Schema[String].reflect.asTerm("fieldName")
      ),
      typeId = TypeId.of[MissingField],
      recordBinding = new Binding.Record(
        constructor = new Constructor[MissingField] {
          def usedRegisters: RegisterOffset                              = 2
          def construct(in: Registers, offset: RegisterOffset): MissingField =
            new MissingField(
              in.getObject(offset).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[MissingField] {
          def usedRegisters: RegisterOffset                                              = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: MissingField): Unit = {
            out.setObject(offset, in.path)
            out.setObject(offset + 1, in.fieldName)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val unknownCaseSchema: Schema[UnknownCase] = new Schema(
    reflect = new Reflect.Record[Binding, UnknownCase](
      fields = Chunk(
        Schema[String].reflect.asTerm("path"),
        Schema[String].reflect.asTerm("caseName")
      ),
      typeId = TypeId.of[UnknownCase],
      recordBinding = new Binding.Record(
        constructor = new Constructor[UnknownCase] {
          def usedRegisters: RegisterOffset                            = 2
          def construct(in: Registers, offset: RegisterOffset): UnknownCase =
            new UnknownCase(
              in.getObject(offset).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[UnknownCase] {
          def usedRegisters: RegisterOffset                                              = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: UnknownCase): Unit = {
            out.setObject(offset, in.path)
            out.setObject(offset + 1, in.caseName)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val transformFailedSchema: Schema[TransformFailed] = new Schema(
    reflect = new Reflect.Record[Binding, TransformFailed](
      fields = Chunk(
        Schema[String].reflect.asTerm("path"),
        Schema[String].reflect.asTerm("details")
      ),
      typeId = TypeId.of[TransformFailed],
      recordBinding = new Binding.Record(
        constructor = new Constructor[TransformFailed] {
          def usedRegisters: RegisterOffset                                = 2
          def construct(in: Registers, offset: RegisterOffset): TransformFailed =
            new TransformFailed(
              in.getObject(offset).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[TransformFailed] {
          def usedRegisters: RegisterOffset                                                = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: TransformFailed): Unit = {
            out.setObject(offset, in.path)
            out.setObject(offset + 1, in.details)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val indexOutOfBoundsSchema: Schema[IndexOutOfBounds] = new Schema(
    reflect = new Reflect.Record[Binding, IndexOutOfBounds](
      fields = Chunk(
        Schema[String].reflect.asTerm("path"),
        Schema[Int].reflect.asTerm("index"),
        Schema[Int].reflect.asTerm("size")
      ),
      typeId = TypeId.of[IndexOutOfBounds],
      recordBinding = new Binding.Record(
        constructor = new Constructor[IndexOutOfBounds] {
          def usedRegisters: RegisterOffset                          = RegisterOffset.incrementObjects(RegisterOffset.incrementFloatsAndInts(0))
          def construct(in: Registers, offset: RegisterOffset): IndexOutOfBounds =
            new IndexOutOfBounds(
              in.getObject(offset).asInstanceOf[String],
              in.getInt(offset + 1),
              in.getInt(offset + 2)
            )
        },
        deconstructor = new Deconstructor[IndexOutOfBounds] {
          def usedRegisters: RegisterOffset                                                    = RegisterOffset.incrementObjects(RegisterOffset.incrementFloatsAndInts(0))
          def deconstruct(out: Registers, offset: RegisterOffset, in: IndexOutOfBounds): Unit = {
            out.setObject(offset, in.path)
            out.setInt(offset + 1, in.index)
            out.setInt(offset + 2, in.size)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val keyNotFoundSchema: Schema[KeyNotFound] = new Schema(
    reflect = new Reflect.Record[Binding, KeyNotFound](
      fields = Chunk(
        Schema[String].reflect.asTerm("path"),
        Schema[DynamicValue].reflect.asTerm("key")
      ),
      typeId = TypeId.of[KeyNotFound],
      recordBinding = new Binding.Record(
        constructor = new Constructor[KeyNotFound] {
          def usedRegisters: RegisterOffset                          = 2
          def construct(in: Registers, offset: RegisterOffset): KeyNotFound =
            new KeyNotFound(
              in.getObject(offset).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[DynamicValue]
            )
        },
        deconstructor = new Deconstructor[KeyNotFound] {
          def usedRegisters: RegisterOffset                                              = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: KeyNotFound): Unit = {
            out.setObject(offset, in.path)
            out.setObject(offset + 1, in.key)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val defaultFailedSchema: Schema[DefaultFailed] = new Schema(
    reflect = new Reflect.Record[Binding, DefaultFailed](
      fields = Chunk(
        Schema[String].reflect.asTerm("path"),
        Schema[String].reflect.asTerm("details")
      ),
      typeId = TypeId.of[DefaultFailed],
      recordBinding = new Binding.Record(
        constructor = new Constructor[DefaultFailed] {
          def usedRegisters: RegisterOffset                            = 2
          def construct(in: Registers, offset: RegisterOffset): DefaultFailed =
            new DefaultFailed(
              in.getObject(offset).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[DefaultFailed] {
          def usedRegisters: RegisterOffset                                                = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: DefaultFailed): Unit = {
            out.setObject(offset, in.path)
            out.setObject(offset + 1, in.details)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val invalidActionSchema: Schema[InvalidAction] = new Schema(
    reflect = new Reflect.Record[Binding, InvalidAction](
      fields = Chunk(
        Schema[String].reflect.asTerm("path"),
        Schema[String].reflect.asTerm("action"),
        Schema[String].reflect.asTerm("reason")
      ),
      typeId = TypeId.of[InvalidAction],
      recordBinding = new Binding.Record(
        constructor = new Constructor[InvalidAction] {
          def usedRegisters: RegisterOffset                            = 3
          def construct(in: Registers, offset: RegisterOffset): InvalidAction =
            new InvalidAction(
              in.getObject(offset).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[String],
              in.getObject(offset + 2).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[InvalidAction] {
          def usedRegisters: RegisterOffset                                                = 3
          def deconstruct(out: Registers, offset: RegisterOffset, in: InvalidAction): Unit = {
            out.setObject(offset, in.path)
            out.setObject(offset + 1, in.action)
            out.setObject(offset + 2, in.reason)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )

  implicit lazy val multipleErrorsSchema: Schema[Multiple] = new Schema(
    reflect = new Reflect.Record[Binding, Multiple](
      fields = Chunk(
        Schema[Chunk[MigrationError]].reflect.asTerm("errors")
      ),
      typeId = TypeId.of[Multiple],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Multiple] {
          def usedRegisters: RegisterOffset                        = 1
          def construct(in: Registers, offset: RegisterOffset): Multiple =
            new Multiple(
              in.getObject(offset).asInstanceOf[Chunk[MigrationError]]
            )
        },
        deconstructor = new Deconstructor[Multiple] {
          def usedRegisters: RegisterOffset                                            = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Multiple): Unit = {
            out.setObject(offset, in.errors)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )
}
