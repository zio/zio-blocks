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
import zio.blocks.schema.{DynamicOptic, DynamicValue, PrimitiveValue, Reflect, Schema}
import zio.blocks.schema.migration.MigrationError.{InvalidValue, MissingField, TypeMismatch}
import zio.blocks.typeid.TypeId

sealed trait MigrationExpr { self =>
  import MigrationExpr._

  final def apply(source: DynamicValue): Either[MigrationError, DynamicValue] = self match {
    case Identity =>
      Right(source)
    case Literal(value) =>
      Right(value)
    case FieldAccess(path) =>
      source.get(path).one.left.map(_ => MissingField(path, path.toString))
    case Convert(expr, conversion) =>
      expr(source).flatMap(v => convert(v, conversion))
    case Concat(exprs, separator) =>
      val values = exprs.foldLeft[Either[MigrationError, Vector[String]]](Right(Vector.empty)) { (acc, expr) =>
        for {
          xs <- acc
          v  <- expr(source)
          s  <- asString(v)
        } yield xs :+ s
      }
      values.map(vs => DynamicValue.Primitive(PrimitiveValue.String(vs.mkString(separator))))
    case Compose(first, second) =>
      first(source).flatMap(second(_))
    case DefaultValue =>
      Right(DynamicValue.Null)
  }

  private[this] def asString(value: DynamicValue): Either[MigrationError, String] =
    value match {
      case DynamicValue.Primitive(PrimitiveValue.String(v))  => Right(v)
      case DynamicValue.Primitive(PrimitiveValue.Int(v))     => Right(v.toString)
      case DynamicValue.Primitive(PrimitiveValue.Long(v))    => Right(v.toString)
      case DynamicValue.Primitive(PrimitiveValue.Double(v))  => Right(v.toString)
      case DynamicValue.Primitive(PrimitiveValue.Float(v))   => Right(v.toString)
      case DynamicValue.Primitive(PrimitiveValue.Boolean(v)) => Right(v.toString)
      case _                                                 => Left(TypeMismatch(DynamicOptic.root, "String-like", value.valueType.toString))
    }

  private[this] def convert(
    value: DynamicValue,
    conversion: PrimitiveConversion
  ): Either[MigrationError, DynamicValue] = (value, conversion) match {
    case (DynamicValue.Primitive(PrimitiveValue.Int(v)), PrimitiveConversion.IntToLong) =>
      Right(DynamicValue.Primitive(PrimitiveValue.Long(v.toLong)))
    case (DynamicValue.Primitive(PrimitiveValue.Long(v)), PrimitiveConversion.LongToInt) =>
      Right(DynamicValue.Primitive(PrimitiveValue.Int(v.toInt)))
    case (DynamicValue.Primitive(PrimitiveValue.Int(v)), PrimitiveConversion.IntToString) =>
      Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
    case (DynamicValue.Primitive(PrimitiveValue.String(v)), PrimitiveConversion.StringToInt) =>
      scala.util
        .Try(v.toInt)
        .toEither
        .left
        .map(_ => InvalidValue(DynamicOptic.root, s"Cannot convert '$v' to Int"))
        .map { i =>
          DynamicValue.Primitive(PrimitiveValue.Int(i))
        }
    case (DynamicValue.Primitive(PrimitiveValue.Long(v)), PrimitiveConversion.LongToString) =>
      Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
    case (DynamicValue.Primitive(PrimitiveValue.String(v)), PrimitiveConversion.StringToLong) =>
      scala.util
        .Try(v.toLong)
        .toEither
        .left
        .map(_ => InvalidValue(DynamicOptic.root, s"Cannot convert '$v' to Long"))
        .map { i =>
          DynamicValue.Primitive(PrimitiveValue.Long(i))
        }
    case (DynamicValue.Primitive(PrimitiveValue.Double(v)), PrimitiveConversion.DoubleToString) =>
      Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
    case (DynamicValue.Primitive(PrimitiveValue.String(v)), PrimitiveConversion.StringToDouble) =>
      scala.util
        .Try(v.toDouble)
        .toEither
        .left
        .map(_ => InvalidValue(DynamicOptic.root, s"Cannot convert '$v' to Double"))
        .map(i => DynamicValue.Primitive(PrimitiveValue.Double(i)))
    case (DynamicValue.Primitive(PrimitiveValue.Float(v)), PrimitiveConversion.FloatToDouble) =>
      Right(DynamicValue.Primitive(PrimitiveValue.Double(v.toDouble)))
    case (DynamicValue.Primitive(PrimitiveValue.Double(v)), PrimitiveConversion.DoubleToFloat) =>
      Right(DynamicValue.Primitive(PrimitiveValue.Float(v.toFloat)))
    case (DynamicValue.Primitive(PrimitiveValue.Boolean(v)), PrimitiveConversion.BooleanToString) =>
      Right(DynamicValue.Primitive(PrimitiveValue.String(v.toString)))
    case (DynamicValue.Primitive(PrimitiveValue.String(v)), PrimitiveConversion.StringToBoolean) =>
      scala.util
        .Try(v.toBoolean)
        .toEither
        .left
        .map(_ => InvalidValue(DynamicOptic.root, s"Cannot convert '$v' to Boolean"))
        .map(i => DynamicValue.Primitive(PrimitiveValue.Boolean(i)))
    case _ =>
      Left(TypeMismatch(DynamicOptic.root, conversion.toString, value.valueType.toString))
  }
}

object MigrationExpr {
  case object Identity                                                           extends MigrationExpr
  final case class Literal(value: DynamicValue)                                  extends MigrationExpr
  final case class FieldAccess(path: DynamicOptic)                               extends MigrationExpr
  final case class Convert(expr: MigrationExpr, conversion: PrimitiveConversion) extends MigrationExpr
  final case class Concat(exprs: Vector[MigrationExpr], separator: String)       extends MigrationExpr
  final case class Compose(first: MigrationExpr, second: MigrationExpr)          extends MigrationExpr
  case object DefaultValue                                                       extends MigrationExpr

  sealed trait PrimitiveConversion
  object PrimitiveConversion {
    case object IntToLong       extends PrimitiveConversion
    case object LongToInt       extends PrimitiveConversion
    case object IntToString     extends PrimitiveConversion
    case object StringToInt     extends PrimitiveConversion
    case object LongToString    extends PrimitiveConversion
    case object StringToLong    extends PrimitiveConversion
    case object DoubleToString  extends PrimitiveConversion
    case object StringToDouble  extends PrimitiveConversion
    case object FloatToDouble   extends PrimitiveConversion
    case object DoubleToFloat   extends PrimitiveConversion
    case object BooleanToString extends PrimitiveConversion
    case object StringToBoolean extends PrimitiveConversion

    private def singletonSchema[A](value: A, tid: TypeId[A]): Schema[A] = new Schema(
      reflect = new Reflect.Record[Binding, A](
        fields = Chunk.empty,
        typeId = tid,
        recordBinding = new Binding.Record(
          constructor = new ConstantConstructor[A](value),
          deconstructor = new ConstantDeconstructor[A]
        ),
        modifiers = Chunk.empty
      )
    )

    implicit lazy val intToLongSchema: Schema[IntToLong.type]     = singletonSchema(IntToLong, TypeId.of[IntToLong.type])
    implicit lazy val longToIntSchema: Schema[LongToInt.type]     = singletonSchema(LongToInt, TypeId.of[LongToInt.type])
    implicit lazy val intToStringSchema: Schema[IntToString.type] =
      singletonSchema(IntToString, TypeId.of[IntToString.type])
    implicit lazy val stringToIntSchema: Schema[StringToInt.type] =
      singletonSchema(StringToInt, TypeId.of[StringToInt.type])
    implicit lazy val longToStringSchema: Schema[LongToString.type] =
      singletonSchema(LongToString, TypeId.of[LongToString.type])
    implicit lazy val stringToLongSchema: Schema[StringToLong.type] =
      singletonSchema(StringToLong, TypeId.of[StringToLong.type])
    implicit lazy val doubleToStringSchema: Schema[DoubleToString.type] =
      singletonSchema(DoubleToString, TypeId.of[DoubleToString.type])
    implicit lazy val stringToDoubleSchema: Schema[StringToDouble.type] =
      singletonSchema(StringToDouble, TypeId.of[StringToDouble.type])
    implicit lazy val floatToDoubleSchema: Schema[FloatToDouble.type] =
      singletonSchema(FloatToDouble, TypeId.of[FloatToDouble.type])
    implicit lazy val doubleToFloatSchema: Schema[DoubleToFloat.type] =
      singletonSchema(DoubleToFloat, TypeId.of[DoubleToFloat.type])
    implicit lazy val booleanToStringSchema: Schema[BooleanToString.type] =
      singletonSchema(BooleanToString, TypeId.of[BooleanToString.type])
    implicit lazy val stringToBooleanSchema: Schema[StringToBoolean.type] =
      singletonSchema(StringToBoolean, TypeId.of[StringToBoolean.type])

    implicit lazy val schema: Schema[PrimitiveConversion] = new Schema(
      reflect = new Reflect.Variant[Binding, PrimitiveConversion](
        cases = Chunk(
          intToLongSchema.reflect.asTerm("IntToLong"),
          longToIntSchema.reflect.asTerm("LongToInt"),
          intToStringSchema.reflect.asTerm("IntToString"),
          stringToIntSchema.reflect.asTerm("StringToInt"),
          longToStringSchema.reflect.asTerm("LongToString"),
          stringToLongSchema.reflect.asTerm("StringToLong"),
          doubleToStringSchema.reflect.asTerm("DoubleToString"),
          stringToDoubleSchema.reflect.asTerm("StringToDouble"),
          floatToDoubleSchema.reflect.asTerm("FloatToDouble"),
          doubleToFloatSchema.reflect.asTerm("DoubleToFloat"),
          booleanToStringSchema.reflect.asTerm("BooleanToString"),
          stringToBooleanSchema.reflect.asTerm("StringToBoolean")
        ),
        typeId = TypeId.of[PrimitiveConversion],
        variantBinding = new Binding.Variant(
          discriminator = new Discriminator[PrimitiveConversion] {
            def discriminate(a: PrimitiveConversion): Int = a match {
              case IntToLong       => 0
              case LongToInt       => 1
              case IntToString     => 2
              case StringToInt     => 3
              case LongToString    => 4
              case StringToLong    => 5
              case DoubleToString  => 6
              case StringToDouble  => 7
              case FloatToDouble   => 8
              case DoubleToFloat   => 9
              case BooleanToString => 10
              case StringToBoolean => 11
            }
          },
          matchers = Matchers(
            new Matcher[IntToLong.type] {
              def downcastOrNull(a: Any): IntToLong.type =
                if (a == IntToLong) IntToLong else null.asInstanceOf[IntToLong.type]
            },
            new Matcher[LongToInt.type] {
              def downcastOrNull(a: Any): LongToInt.type =
                if (a == LongToInt) LongToInt else null.asInstanceOf[LongToInt.type]
            },
            new Matcher[IntToString.type] {
              def downcastOrNull(a: Any): IntToString.type =
                if (a == IntToString) IntToString else null.asInstanceOf[IntToString.type]
            },
            new Matcher[StringToInt.type] {
              def downcastOrNull(a: Any): StringToInt.type =
                if (a == StringToInt) StringToInt else null.asInstanceOf[StringToInt.type]
            },
            new Matcher[LongToString.type] {
              def downcastOrNull(a: Any): LongToString.type =
                if (a == LongToString) LongToString else null.asInstanceOf[LongToString.type]
            },
            new Matcher[StringToLong.type] {
              def downcastOrNull(a: Any): StringToLong.type =
                if (a == StringToLong) StringToLong else null.asInstanceOf[StringToLong.type]
            },
            new Matcher[DoubleToString.type] {
              def downcastOrNull(a: Any): DoubleToString.type =
                if (a == DoubleToString) DoubleToString else null.asInstanceOf[DoubleToString.type]
            },
            new Matcher[StringToDouble.type] {
              def downcastOrNull(a: Any): StringToDouble.type =
                if (a == StringToDouble) StringToDouble else null.asInstanceOf[StringToDouble.type]
            },
            new Matcher[FloatToDouble.type] {
              def downcastOrNull(a: Any): FloatToDouble.type =
                if (a == FloatToDouble) FloatToDouble else null.asInstanceOf[FloatToDouble.type]
            },
            new Matcher[DoubleToFloat.type] {
              def downcastOrNull(a: Any): DoubleToFloat.type =
                if (a == DoubleToFloat) DoubleToFloat else null.asInstanceOf[DoubleToFloat.type]
            },
            new Matcher[BooleanToString.type] {
              def downcastOrNull(a: Any): BooleanToString.type =
                if (a == BooleanToString) BooleanToString else null.asInstanceOf[BooleanToString.type]
            },
            new Matcher[StringToBoolean.type] {
              def downcastOrNull(a: Any): StringToBoolean.type =
                if (a == StringToBoolean) StringToBoolean else null.asInstanceOf[StringToBoolean.type]
            }
          )
        ),
        modifiers = Chunk.empty
      )
    )
  }

  implicit lazy val identitySchema: Schema[Identity.type] = new Schema(
    reflect = new Reflect.Record[Binding, Identity.type](
      fields = Chunk.empty,
      typeId = TypeId.of[Identity.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[Identity.type](Identity),
        deconstructor = new ConstantDeconstructor[Identity.type]
      ),
      modifiers = Chunk.empty
    )
  )
  implicit lazy val literalSchema: Schema[Literal] = new Schema(
    reflect = new Reflect.Record[Binding, Literal](
      fields = Chunk.single(Schema[DynamicValue].reflect.asTerm("value")),
      typeId = TypeId.of[Literal],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Literal] {
          def usedRegisters: RegisterOffset                             = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): Literal =
            Literal(in.getObject(offset).asInstanceOf[DynamicValue])
        },
        deconstructor = new Deconstructor[Literal] {
          def usedRegisters: RegisterOffset                                          = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Literal): Unit =
            out.setObject(offset, in.value)
        }
      ),
      modifiers = Chunk.empty
    )
  )
  implicit lazy val fieldAccessSchema: Schema[FieldAccess] = new Schema(
    reflect = new Reflect.Record[Binding, FieldAccess](
      fields = Chunk.single(Schema[DynamicOptic].reflect.asTerm("path")),
      typeId = TypeId.of[FieldAccess],
      recordBinding = new Binding.Record(
        constructor = new Constructor[FieldAccess] {
          def usedRegisters: RegisterOffset                                 = RegisterOffset(objects = 1)
          def construct(in: Registers, offset: RegisterOffset): FieldAccess =
            FieldAccess(in.getObject(offset).asInstanceOf[DynamicOptic])
        },
        deconstructor = new Deconstructor[FieldAccess] {
          def usedRegisters: RegisterOffset                                              = RegisterOffset(objects = 1)
          def deconstruct(out: Registers, offset: RegisterOffset, in: FieldAccess): Unit =
            out.setObject(offset, in.path)
        }
      ),
      modifiers = Chunk.empty
    )
  )
  implicit lazy val convertSchema: Schema[Convert] = new Schema(
    reflect = new Reflect.Record[Binding, Convert](
      fields = Chunk(
        Reflect.Deferred(() => schema.reflect).asTerm("expr"),
        PrimitiveConversion.schema.reflect.asTerm("conversion")
      ),
      typeId = TypeId.of[Convert],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Convert] {
          def usedRegisters: RegisterOffset                             = RegisterOffset(objects = 2)
          def construct(in: Registers, offset: RegisterOffset): Convert =
            Convert(
              in.getObject(offset).asInstanceOf[MigrationExpr],
              in.getObject(RegisterOffset.incrementObjects(offset)).asInstanceOf[PrimitiveConversion]
            )
        },
        deconstructor = new Deconstructor[Convert] {
          def usedRegisters: RegisterOffset                                          = RegisterOffset(objects = 2)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Convert): Unit = {
            out.setObject(offset, in.expr)
            out.setObject(RegisterOffset.incrementObjects(offset), in.conversion)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )
  implicit lazy val concatSchema: Schema[Concat] = new Schema(
    reflect = new Reflect.Record[Binding, Concat](
      fields = Chunk(
        Reflect.Deferred(() => Schema[Vector[MigrationExpr]].reflect).asTerm("exprs"),
        Schema[String].reflect.asTerm("separator")
      ),
      typeId = TypeId.of[Concat],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Concat] {
          def usedRegisters: RegisterOffset                            = RegisterOffset(objects = 2)
          def construct(in: Registers, offset: RegisterOffset): Concat =
            Concat(
              in.getObject(offset).asInstanceOf[Vector[MigrationExpr]],
              in.getObject(RegisterOffset.incrementObjects(offset)).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[Concat] {
          def usedRegisters: RegisterOffset                                         = RegisterOffset(objects = 2)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Concat): Unit = {
            out.setObject(offset, in.exprs)
            out.setObject(RegisterOffset.incrementObjects(offset), in.separator)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )
  implicit lazy val composeSchema: Schema[Compose] = new Schema(
    reflect = new Reflect.Record[Binding, Compose](
      fields = Chunk(
        Reflect.Deferred(() => schema.reflect).asTerm("first"),
        Reflect.Deferred(() => schema.reflect).asTerm("second")
      ),
      typeId = TypeId.of[Compose],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Compose] {
          def usedRegisters: RegisterOffset                             = RegisterOffset(objects = 2)
          def construct(in: Registers, offset: RegisterOffset): Compose =
            Compose(
              in.getObject(offset).asInstanceOf[MigrationExpr],
              in.getObject(RegisterOffset.incrementObjects(offset)).asInstanceOf[MigrationExpr]
            )
        },
        deconstructor = new Deconstructor[Compose] {
          def usedRegisters: RegisterOffset                                          = RegisterOffset(objects = 2)
          def deconstruct(out: Registers, offset: RegisterOffset, in: Compose): Unit = {
            out.setObject(offset, in.first)
            out.setObject(RegisterOffset.incrementObjects(offset), in.second)
          }
        }
      ),
      modifiers = Chunk.empty
    )
  )
  implicit lazy val defaultValueSchema: Schema[DefaultValue.type] = new Schema(
    reflect = new Reflect.Record[Binding, DefaultValue.type](
      fields = Chunk.empty,
      typeId = TypeId.of[DefaultValue.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[DefaultValue.type](DefaultValue),
        deconstructor = new ConstantDeconstructor[DefaultValue.type]
      ),
      modifiers = Chunk.empty
    )
  )
  implicit lazy val schema: Schema[MigrationExpr] = new Schema(
    reflect = new Reflect.Variant[Binding, MigrationExpr](
      cases = Chunk(
        identitySchema.reflect.asTerm("Identity"),
        literalSchema.reflect.asTerm("Literal"),
        fieldAccessSchema.reflect.asTerm("FieldAccess"),
        Reflect.Deferred(() => convertSchema.reflect).asTerm("Convert"),
        Reflect.Deferred(() => concatSchema.reflect).asTerm("Concat"),
        Reflect.Deferred(() => composeSchema.reflect).asTerm("Compose"),
        defaultValueSchema.reflect.asTerm("DefaultValue")
      ),
      typeId = TypeId.of[MigrationExpr],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[MigrationExpr] {
          def discriminate(a: MigrationExpr): Int = a match {
            case Identity       => 0
            case _: Literal     => 1
            case _: FieldAccess => 2
            case _: Convert     => 3
            case _: Concat      => 4
            case _: Compose     => 5
            case DefaultValue   => 6
          }
        },
        matchers = Matchers(
          new Matcher[Identity.type] {
            def downcastOrNull(a: Any): Identity.type =
              if (a == Identity) Identity else null.asInstanceOf[Identity.type]
          },
          new Matcher[Literal] {
            def downcastOrNull(a: Any): Literal = a match { case x: Literal => x; case _ => null.asInstanceOf[Literal] }
          },
          new Matcher[FieldAccess] {
            def downcastOrNull(a: Any): FieldAccess = a match {
              case x: FieldAccess => x; case _ => null.asInstanceOf[FieldAccess]
            }
          },
          new Matcher[Convert] {
            def downcastOrNull(a: Any): Convert = a match { case x: Convert => x; case _ => null.asInstanceOf[Convert] }
          },
          new Matcher[Concat] {
            def downcastOrNull(a: Any): Concat = a match { case x: Concat => x; case _ => null.asInstanceOf[Concat] }
          },
          new Matcher[Compose] {
            def downcastOrNull(a: Any): Compose = a match { case x: Compose => x; case _ => null.asInstanceOf[Compose] }
          },
          new Matcher[DefaultValue.type] {
            def downcastOrNull(a: Any): DefaultValue.type =
              if (a == DefaultValue) DefaultValue else null.asInstanceOf[DefaultValue.type]
          }
        )
      ),
      modifiers = Chunk.empty
    )
  )
}
