package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, PrimitiveValue, Reflect, Schema}
import zio.blocks.schema.binding._
import zio.blocks.schema.binding.RegisterOffset.RegisterOffset
import zio.blocks.typeid.TypeId

sealed trait DynamicValueTransform {
  def apply(value: DynamicValue): Either[String, DynamicValue]
}

object DynamicValueTransform {

  case object Identity extends DynamicValueTransform {
    def apply(value: DynamicValue): Either[String, DynamicValue] = Right(value)
  }

  final case class Constant(result: DynamicValue) extends DynamicValueTransform {
    def apply(value: DynamicValue): Either[String, DynamicValue] = Right(result)
  }

  final case class StringAppend(suffix: String) extends DynamicValueTransform {
    def apply(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        Right(DynamicValue.string(s + suffix))
      case _ =>
        Left(s"StringAppend requires a String value, got ${value.valueType}")
    }
  }

  final case class StringPrepend(prefix: String) extends DynamicValueTransform {
    def apply(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        Right(DynamicValue.string(prefix + s))
      case _ =>
        Left(s"StringPrepend requires a String value, got ${value.valueType}")
    }
  }

  final case class StringReplace(target: String, replacement: String) extends DynamicValueTransform {
    def apply(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        Right(DynamicValue.string(s.replace(target, replacement)))
      case _ =>
        Left(s"StringReplace requires a String value, got ${value.valueType}")
    }
  }

  final case class NumericAdd(delta: BigDecimal) extends DynamicValueTransform {
    def apply(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Int(n)) =>
        val result = BigDecimal(n) + delta
        if (result < Int.MinValue || result > Int.MaxValue)
          Left(s"NumericAdd: result $result is outside Int range [${Int.MinValue}, ${Int.MaxValue}]")
        else
          Right(DynamicValue.int(result.toInt))
      case DynamicValue.Primitive(PrimitiveValue.Long(n)) =>
        val result = BigDecimal(n) + delta
        if (result < Long.MinValue || result > Long.MaxValue)
          Left(s"NumericAdd: result $result is outside Long range [${Long.MinValue}, ${Long.MaxValue}]")
        else
          Right(DynamicValue.long(result.toLong))
      case DynamicValue.Primitive(PrimitiveValue.Double(n)) =>
        Right(DynamicValue.double((BigDecimal(n) + delta).toDouble))
      case DynamicValue.Primitive(PrimitiveValue.Float(n)) =>
        Right(DynamicValue.float((BigDecimal(n.toDouble) + delta).toFloat))
      case DynamicValue.Primitive(PrimitiveValue.BigDecimal(n)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(n + delta)))
      case DynamicValue.Primitive(PrimitiveValue.BigInt(n)) =>
        Right(DynamicValue.bigInt(n + delta.toBigInt))
      case _ =>
        Left(s"NumericAdd requires a numeric value, got ${value.valueType}")
    }
  }

  final case class NumericMultiply(factor: BigDecimal) extends DynamicValueTransform {
    def apply(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Int(n)) =>
        val result = BigDecimal(n) * factor
        if (result < Int.MinValue || result > Int.MaxValue)
          Left(s"NumericMultiply: result $result is outside Int range [${Int.MinValue}, ${Int.MaxValue}]")
        else
          Right(DynamicValue.int(result.toInt))
      case DynamicValue.Primitive(PrimitiveValue.Long(n)) =>
        val result = BigDecimal(n) * factor
        if (result < Long.MinValue || result > Long.MaxValue)
          Left(s"NumericMultiply: result $result is outside Long range [${Long.MinValue}, ${Long.MaxValue}]")
        else
          Right(DynamicValue.long(result.toLong))
      case DynamicValue.Primitive(PrimitiveValue.Double(n)) =>
        Right(DynamicValue.double((BigDecimal(n) * factor).toDouble))
      case DynamicValue.Primitive(PrimitiveValue.Float(n)) =>
        Right(DynamicValue.float((BigDecimal(n.toDouble) * factor).toFloat))
      case DynamicValue.Primitive(PrimitiveValue.BigDecimal(n)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.BigDecimal(n * factor)))
      case DynamicValue.Primitive(PrimitiveValue.BigInt(n)) =>
        Right(DynamicValue.bigInt(n * factor.toBigInt))
      case _ =>
        Left(s"NumericMultiply requires a numeric value, got ${value.valueType}")
    }
  }

  case object WrapInSome extends DynamicValueTransform {
    def apply(value: DynamicValue): Either[String, DynamicValue] =
      Right(DynamicValue.Variant("Some", DynamicValue.Record("value" -> value)))
  }

  final case class UnwrapSome(defaultForNone: DynamicValue) extends DynamicValueTransform {
    def apply(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Variant("Some", inner) =>
        inner.get("value").one match {
          case Right(v) => Right(v)
          case Left(_)  => Left(s"UnwrapSome: Some variant is missing 'value' field")
        }
      case DynamicValue.Variant("None", _) =>
        Right(defaultForNone)
      case DynamicValue.Null =>
        Right(defaultForNone)
      case _ =>
        Left(s"UnwrapSome requires an Option-like value (Some/None or Null), got ${value.valueType}")
    }
  }

  final case class Sequence(transforms: Vector[DynamicValueTransform]) extends DynamicValueTransform {
    def apply(value: DynamicValue): Either[String, DynamicValue] =
      transforms.foldLeft[Either[String, DynamicValue]](Right(value)) {
        case (Right(current), transform) => transform(current)
        case (left @ Left(_), _)         => left
      }
  }

  final case class StringJoinFields(fieldNames: Vector[String], separator: String) extends DynamicValueTransform {
    def apply(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Record(fields) =>
        val fieldMap         = fields.toMap
        val (errors, values) = fieldNames.foldLeft[(Vector[String], Vector[String])]((Vector.empty, Vector.empty)) {
          case ((errs, acc), name) =>
            fieldMap.get(name) match {
              case None =>
                (errs :+ s"field '$name' not found", acc)
              case Some(DynamicValue.Primitive(PrimitiveValue.String(s))) =>
                (errs, acc :+ s)
              case Some(other) =>
                (errs :+ s"field '$name' is not a String (got ${other.valueType})", acc)
            }
        }
        if (errors.nonEmpty)
          Left(s"StringJoinFields: ${errors.mkString("; ")}")
        else
          Right(DynamicValue.string(values.mkString(separator)))
      case _ =>
        Left(s"StringJoinFields requires a Record value, got ${value.valueType}")
    }
  }

  final case class StringSplitToFields(fieldNames: Vector[String], separator: String, limit: Int = -1)
      extends DynamicValueTransform {
    def apply(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        val parts  = if (limit > 0) s.split(separator, limit) else s.split(separator)
        val fields = fieldNames.zipWithIndex.map { case (name, idx) =>
          val part = if (idx < parts.length) parts(idx) else ""
          (name, DynamicValue.string(part))
        }
        Right(DynamicValue.Record(zio.blocks.chunk.Chunk.from(fields)))
      case _ =>
        Left(s"StringSplitToFields requires a String value, got ${value.valueType}")
    }
  }

  def identity: DynamicValueTransform = Identity

  def constant(value: DynamicValue): DynamicValueTransform = Constant(value)

  def stringAppend(suffix: String): DynamicValueTransform = StringAppend(suffix)

  def stringPrepend(prefix: String): DynamicValueTransform = StringPrepend(prefix)

  def stringReplace(target: String, replacement: String): DynamicValueTransform =
    StringReplace(target, replacement)

  def numericAdd(delta: BigDecimal): DynamicValueTransform = NumericAdd(delta)

  def numericMultiply(factor: BigDecimal): DynamicValueTransform = NumericMultiply(factor)

  def wrapInSome: DynamicValueTransform = WrapInSome

  def unwrapSome(defaultForNone: DynamicValue): DynamicValueTransform = UnwrapSome(defaultForNone)

  def sequence(transforms: DynamicValueTransform*): DynamicValueTransform =
    Sequence(transforms.toVector)

  def stringJoinFields(fieldNames: Vector[String], separator: String = " "): DynamicValueTransform =
    StringJoinFields(fieldNames, separator)

  def stringSplitToFields(fieldNames: Vector[String], separator: String = " ", limit: Int = -1): DynamicValueTransform =
    StringSplitToFields(fieldNames, separator, limit)

  implicit lazy val identityTransformSchema: Schema[Identity.type] = new Schema(
    reflect = new Reflect.Record[Binding, Identity.type](
      fields = Vector.empty,
      typeId = TypeId.of[Identity.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[Identity.type](Identity),
        deconstructor = new ConstantDeconstructor[Identity.type]
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val wrapInSomeSchema: Schema[WrapInSome.type] = new Schema(
    reflect = new Reflect.Record[Binding, WrapInSome.type](
      fields = Vector.empty,
      typeId = TypeId.of[WrapInSome.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[WrapInSome.type](WrapInSome),
        deconstructor = new ConstantDeconstructor[WrapInSome.type]
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val constantSchema: Schema[Constant] = new Schema(
    reflect = new Reflect.Record[Binding, Constant](
      fields = Vector(
        Schema[DynamicValue].reflect.asTerm("result")
      ),
      typeId = TypeId.of[Constant],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Constant] {
          def usedRegisters: RegisterOffset                              = 1
          def construct(in: Registers, offset: RegisterOffset): Constant =
            Constant(in.getObject(offset + 0).asInstanceOf[DynamicValue])
        },
        deconstructor = new Deconstructor[Constant] {
          def usedRegisters: RegisterOffset                                           = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Constant): Unit =
            out.setObject(offset + 0, in.result)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val stringAppendSchema: Schema[StringAppend] = new Schema(
    reflect = new Reflect.Record[Binding, StringAppend](
      fields = Vector(
        Schema[String].reflect.asTerm("suffix")
      ),
      typeId = TypeId.of[StringAppend],
      recordBinding = new Binding.Record(
        constructor = new Constructor[StringAppend] {
          def usedRegisters: RegisterOffset                                  = 1
          def construct(in: Registers, offset: RegisterOffset): StringAppend =
            StringAppend(in.getObject(offset + 0).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[StringAppend] {
          def usedRegisters: RegisterOffset                                               = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: StringAppend): Unit =
            out.setObject(offset + 0, in.suffix)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val stringPrependSchema: Schema[StringPrepend] = new Schema(
    reflect = new Reflect.Record[Binding, StringPrepend](
      fields = Vector(
        Schema[String].reflect.asTerm("prefix")
      ),
      typeId = TypeId.of[StringPrepend],
      recordBinding = new Binding.Record(
        constructor = new Constructor[StringPrepend] {
          def usedRegisters: RegisterOffset                                   = 1
          def construct(in: Registers, offset: RegisterOffset): StringPrepend =
            StringPrepend(in.getObject(offset + 0).asInstanceOf[String])
        },
        deconstructor = new Deconstructor[StringPrepend] {
          def usedRegisters: RegisterOffset                                                = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: StringPrepend): Unit =
            out.setObject(offset + 0, in.prefix)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val stringReplaceSchema: Schema[StringReplace] = new Schema(
    reflect = new Reflect.Record[Binding, StringReplace](
      fields = Vector(
        Schema[String].reflect.asTerm("target"),
        Schema[String].reflect.asTerm("replacement")
      ),
      typeId = TypeId.of[StringReplace],
      recordBinding = new Binding.Record(
        constructor = new Constructor[StringReplace] {
          def usedRegisters: RegisterOffset                                   = 2
          def construct(in: Registers, offset: RegisterOffset): StringReplace =
            StringReplace(
              in.getObject(offset + 0).asInstanceOf[String],
              in.getObject(offset + 1).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[StringReplace] {
          def usedRegisters: RegisterOffset                                                = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: StringReplace): Unit = {
            out.setObject(offset + 0, in.target)
            out.setObject(offset + 1, in.replacement)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val numericAddSchema: Schema[NumericAdd] = new Schema(
    reflect = new Reflect.Record[Binding, NumericAdd](
      fields = Vector(
        Schema[BigDecimal].reflect.asTerm("delta")
      ),
      typeId = TypeId.of[NumericAdd],
      recordBinding = new Binding.Record(
        constructor = new Constructor[NumericAdd] {
          def usedRegisters: RegisterOffset                                = 1
          def construct(in: Registers, offset: RegisterOffset): NumericAdd =
            NumericAdd(in.getObject(offset + 0).asInstanceOf[BigDecimal])
        },
        deconstructor = new Deconstructor[NumericAdd] {
          def usedRegisters: RegisterOffset                                             = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: NumericAdd): Unit =
            out.setObject(offset + 0, in.delta)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val numericMultiplySchema: Schema[NumericMultiply] = new Schema(
    reflect = new Reflect.Record[Binding, NumericMultiply](
      fields = Vector(
        Schema[BigDecimal].reflect.asTerm("factor")
      ),
      typeId = TypeId.of[NumericMultiply],
      recordBinding = new Binding.Record(
        constructor = new Constructor[NumericMultiply] {
          def usedRegisters: RegisterOffset                                     = 1
          def construct(in: Registers, offset: RegisterOffset): NumericMultiply =
            NumericMultiply(in.getObject(offset + 0).asInstanceOf[BigDecimal])
        },
        deconstructor = new Deconstructor[NumericMultiply] {
          def usedRegisters: RegisterOffset                                                  = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: NumericMultiply): Unit =
            out.setObject(offset + 0, in.factor)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val unwrapSomeSchema: Schema[UnwrapSome] = new Schema(
    reflect = new Reflect.Record[Binding, UnwrapSome](
      fields = Vector(
        Schema[DynamicValue].reflect.asTerm("defaultForNone")
      ),
      typeId = TypeId.of[UnwrapSome],
      recordBinding = new Binding.Record(
        constructor = new Constructor[UnwrapSome] {
          def usedRegisters: RegisterOffset                                = 1
          def construct(in: Registers, offset: RegisterOffset): UnwrapSome =
            UnwrapSome(in.getObject(offset + 0).asInstanceOf[DynamicValue])
        },
        deconstructor = new Deconstructor[UnwrapSome] {
          def usedRegisters: RegisterOffset                                             = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: UnwrapSome): Unit =
            out.setObject(offset + 0, in.defaultForNone)
        }
      ),
      modifiers = Vector.empty
    )
  )

  private lazy val vectorOfTransformReflect: Reflect[Binding, Vector[DynamicValueTransform]] =
    Reflect.Deferred(() => Reflect.vector(schema.reflect))

  implicit lazy val sequenceTransformSchema: Schema[Sequence] = new Schema(
    reflect = new Reflect.Record[Binding, Sequence](
      fields = Vector(
        vectorOfTransformReflect.asTerm("transforms")
      ),
      typeId = TypeId.of[Sequence],
      recordBinding = new Binding.Record(
        constructor = new Constructor[Sequence] {
          def usedRegisters: RegisterOffset                              = 1
          def construct(in: Registers, offset: RegisterOffset): Sequence =
            Sequence(in.getObject(offset + 0).asInstanceOf[Vector[DynamicValueTransform]])
        },
        deconstructor = new Deconstructor[Sequence] {
          def usedRegisters: RegisterOffset                                           = 1
          def deconstruct(out: Registers, offset: RegisterOffset, in: Sequence): Unit =
            out.setObject(offset + 0, in.transforms)
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val stringJoinFieldsSchema: Schema[StringJoinFields] = new Schema(
    reflect = new Reflect.Record[Binding, StringJoinFields](
      fields = Vector(
        Schema[Vector[String]].reflect.asTerm("fieldNames"),
        Schema[String].reflect.asTerm("separator")
      ),
      typeId = TypeId.of[StringJoinFields],
      recordBinding = new Binding.Record(
        constructor = new Constructor[StringJoinFields] {
          def usedRegisters: RegisterOffset                                      = 2
          def construct(in: Registers, offset: RegisterOffset): StringJoinFields =
            StringJoinFields(
              in.getObject(offset + 0).asInstanceOf[Vector[String]],
              in.getObject(offset + 1).asInstanceOf[String]
            )
        },
        deconstructor = new Deconstructor[StringJoinFields] {
          def usedRegisters: RegisterOffset                                                   = 2
          def deconstruct(out: Registers, offset: RegisterOffset, in: StringJoinFields): Unit = {
            out.setObject(offset + 0, in.fieldNames)
            out.setObject(offset + 1, in.separator)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val stringSplitToFieldsSchema: Schema[StringSplitToFields] = new Schema(
    reflect = new Reflect.Record[Binding, StringSplitToFields](
      fields = Vector(
        Schema[Vector[String]].reflect.asTerm("fieldNames"),
        Schema[String].reflect.asTerm("separator"),
        Schema[Int].reflect.asTerm("limit")
      ),
      typeId = TypeId.of[StringSplitToFields],
      recordBinding = new Binding.Record(
        constructor = new Constructor[StringSplitToFields] {
          def usedRegisters: RegisterOffset                                         = RegisterOffset(ints = 1, objects = 2)
          def construct(in: Registers, offset: RegisterOffset): StringSplitToFields =
            StringSplitToFields(
              in.getObject(offset).asInstanceOf[Vector[String]],
              in.getObject(RegisterOffset.incrementObjects(offset)).asInstanceOf[String],
              in.getInt(offset)
            )
        },
        deconstructor = new Deconstructor[StringSplitToFields] {
          def usedRegisters: RegisterOffset                                                      = RegisterOffset(ints = 1, objects = 2)
          def deconstruct(out: Registers, offset: RegisterOffset, in: StringSplitToFields): Unit = {
            out.setObject(offset, in.fieldNames)
            out.setObject(RegisterOffset.incrementObjects(offset), in.separator)
            out.setInt(offset, in.limit)
          }
        }
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val schema: Schema[DynamicValueTransform] = new Schema(
    reflect = new Reflect.Variant[Binding, DynamicValueTransform](
      cases = Vector(
        identityTransformSchema.reflect.asTerm("Identity"),
        constantSchema.reflect.asTerm("Constant"),
        stringAppendSchema.reflect.asTerm("StringAppend"),
        stringPrependSchema.reflect.asTerm("StringPrepend"),
        stringReplaceSchema.reflect.asTerm("StringReplace"),
        numericAddSchema.reflect.asTerm("NumericAdd"),
        numericMultiplySchema.reflect.asTerm("NumericMultiply"),
        wrapInSomeSchema.reflect.asTerm("WrapInSome"),
        unwrapSomeSchema.reflect.asTerm("UnwrapSome"),
        sequenceTransformSchema.reflect.asTerm("Sequence"),
        stringJoinFieldsSchema.reflect.asTerm("StringJoinFields"),
        stringSplitToFieldsSchema.reflect.asTerm("StringSplitToFields")
      ),
      typeId = TypeId.of[DynamicValueTransform],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[DynamicValueTransform] {
          def discriminate(a: DynamicValueTransform): Int = a match {
            case _: Identity.type       => 0
            case _: Constant            => 1
            case _: StringAppend        => 2
            case _: StringPrepend       => 3
            case _: StringReplace       => 4
            case _: NumericAdd          => 5
            case _: NumericMultiply     => 6
            case _: WrapInSome.type     => 7
            case _: UnwrapSome          => 8
            case _: Sequence            => 9
            case _: StringJoinFields    => 10
            case _: StringSplitToFields => 11
          }
        },
        matchers = Matchers(
          new Matcher[Identity.type] {
            def downcastOrNull(a: Any): Identity.type = a match {
              case x: Identity.type => x
              case _                => null.asInstanceOf[Identity.type]
            }
          },
          new Matcher[Constant] {
            def downcastOrNull(a: Any): Constant = a match {
              case x: Constant => x
              case _           => null.asInstanceOf[Constant]
            }
          },
          new Matcher[StringAppend] {
            def downcastOrNull(a: Any): StringAppend = a match {
              case x: StringAppend => x
              case _               => null.asInstanceOf[StringAppend]
            }
          },
          new Matcher[StringPrepend] {
            def downcastOrNull(a: Any): StringPrepend = a match {
              case x: StringPrepend => x
              case _                => null.asInstanceOf[StringPrepend]
            }
          },
          new Matcher[StringReplace] {
            def downcastOrNull(a: Any): StringReplace = a match {
              case x: StringReplace => x
              case _                => null.asInstanceOf[StringReplace]
            }
          },
          new Matcher[NumericAdd] {
            def downcastOrNull(a: Any): NumericAdd = a match {
              case x: NumericAdd => x
              case _             => null.asInstanceOf[NumericAdd]
            }
          },
          new Matcher[NumericMultiply] {
            def downcastOrNull(a: Any): NumericMultiply = a match {
              case x: NumericMultiply => x
              case _                  => null.asInstanceOf[NumericMultiply]
            }
          },
          new Matcher[WrapInSome.type] {
            def downcastOrNull(a: Any): WrapInSome.type = a match {
              case x: WrapInSome.type => x
              case _                  => null.asInstanceOf[WrapInSome.type]
            }
          },
          new Matcher[UnwrapSome] {
            def downcastOrNull(a: Any): UnwrapSome = a match {
              case x: UnwrapSome => x
              case _             => null.asInstanceOf[UnwrapSome]
            }
          },
          new Matcher[Sequence] {
            def downcastOrNull(a: Any): Sequence = a match {
              case x: Sequence => x
              case _           => null.asInstanceOf[Sequence]
            }
          },
          new Matcher[StringJoinFields] {
            def downcastOrNull(a: Any): StringJoinFields = a match {
              case x: StringJoinFields => x
              case _                   => null.asInstanceOf[StringJoinFields]
            }
          },
          new Matcher[StringSplitToFields] {
            def downcastOrNull(a: Any): StringSplitToFields = a match {
              case x: StringSplitToFields => x
              case _                      => null.asInstanceOf[StringSplitToFields]
            }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )
}
