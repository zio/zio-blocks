package zio.blocks.schema.migration

import zio.blocks.schema.{DynamicValue, PrimitiveValue, Reflect, Schema}
import zio.blocks.schema.binding._
import zio.blocks.typeid.TypeId

sealed trait PrimitiveConversion {
  def apply(value: DynamicValue): Either[String, DynamicValue]
}

object PrimitiveConversion {

  case object IntToLong extends PrimitiveConversion {
    def apply(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Int(n)) =>
        Right(DynamicValue.long(n.toLong))
      case _ =>
        Left(s"IntToLong requires Int, got ${value.valueType}")
    }
  }

  case object LongToInt extends PrimitiveConversion {
    def apply(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Long(n)) =>
        if (n < Int.MinValue || n > Int.MaxValue)
          Left(s"LongToInt: value $n is outside Int range [${Int.MinValue}, ${Int.MaxValue}]")
        else
          Right(DynamicValue.int(n.toInt))
      case _ =>
        Left(s"LongToInt requires Long, got ${value.valueType}")
    }
  }

  case object IntToString extends PrimitiveConversion {
    def apply(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Int(n)) =>
        Right(DynamicValue.string(n.toString))
      case _ =>
        Left(s"IntToString requires Int, got ${value.valueType}")
    }
  }

  case object StringToInt extends PrimitiveConversion {
    def apply(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        s.toIntOption match {
          case Some(n) => Right(DynamicValue.int(n))
          case None    => Left(s"Cannot parse '$s' as Int")
        }
      case _ =>
        Left(s"StringToInt requires String, got ${value.valueType}")
    }
  }

  case object LongToString extends PrimitiveConversion {
    def apply(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Long(n)) =>
        Right(DynamicValue.string(n.toString))
      case _ =>
        Left(s"LongToString requires Long, got ${value.valueType}")
    }
  }

  case object StringToLong extends PrimitiveConversion {
    def apply(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        s.toLongOption match {
          case Some(n) => Right(DynamicValue.long(n))
          case None    => Left(s"Cannot parse '$s' as Long")
        }
      case _ =>
        Left(s"StringToLong requires String, got ${value.valueType}")
    }
  }

  case object DoubleToString extends PrimitiveConversion {
    def apply(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Double(n)) =>
        Right(DynamicValue.string(n.toString))
      case _ =>
        Left(s"DoubleToString requires Double, got ${value.valueType}")
    }
  }

  case object StringToDouble extends PrimitiveConversion {
    def apply(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        s.toDoubleOption match {
          case Some(n) => Right(DynamicValue.double(n))
          case None    => Left(s"Cannot parse '$s' as Double")
        }
      case _ =>
        Left(s"StringToDouble requires String, got ${value.valueType}")
    }
  }

  case object FloatToDouble extends PrimitiveConversion {
    def apply(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Float(n)) =>
        Right(DynamicValue.double(n.toDouble))
      case _ =>
        Left(s"FloatToDouble requires Float, got ${value.valueType}")
    }
  }

  case object DoubleToFloat extends PrimitiveConversion {
    def apply(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Double(n)) =>
        if (n.isNaN || n.isInfinite)
          Right(DynamicValue.float(n.toFloat))
        else if (n.abs > Float.MaxValue)
          Left(s"DoubleToFloat: value $n is outside Float range [-${Float.MaxValue}, ${Float.MaxValue}]")
        else
          Right(DynamicValue.float(n.toFloat))
      case _ =>
        Left(s"DoubleToFloat requires Double, got ${value.valueType}")
    }
  }

  case object BooleanToString extends PrimitiveConversion {
    def apply(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Boolean(b)) =>
        Right(DynamicValue.string(b.toString))
      case _ =>
        Left(s"BooleanToString requires Boolean, got ${value.valueType}")
    }
  }

  case object StringToBoolean extends PrimitiveConversion {
    def apply(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(s)) =>
        s.toLowerCase match {
          case "true"  => Right(DynamicValue.boolean(true))
          case "false" => Right(DynamicValue.boolean(false))
          case _       => Left(s"Cannot parse '$s' as Boolean")
        }
      case _ =>
        Left(s"StringToBoolean requires String, got ${value.valueType}")
    }
  }

  case object IntToDouble extends PrimitiveConversion {
    def apply(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Int(n)) =>
        Right(DynamicValue.double(n.toDouble))
      case _ =>
        Left(s"IntToDouble requires Int, got ${value.valueType}")
    }
  }

  case object DoubleToInt extends PrimitiveConversion {
    def apply(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Double(n)) =>
        if (n.isNaN || n.isInfinite)
          Left(s"DoubleToInt: cannot convert $n to Int")
        else if (n < Int.MinValue || n > Int.MaxValue)
          Left(s"DoubleToInt: value $n is outside Int range [${Int.MinValue}, ${Int.MaxValue}]")
        else
          Right(DynamicValue.int(n.toInt))
      case _ =>
        Left(s"DoubleToInt requires Double, got ${value.valueType}")
    }
  }

  implicit lazy val intToLongSchema: Schema[IntToLong.type] = new Schema(
    reflect = new Reflect.Record[Binding, IntToLong.type](
      fields = Vector.empty,
      typeId = TypeId.of[IntToLong.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[IntToLong.type](IntToLong),
        deconstructor = new ConstantDeconstructor[IntToLong.type]
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val longToIntSchema: Schema[LongToInt.type] = new Schema(
    reflect = new Reflect.Record[Binding, LongToInt.type](
      fields = Vector.empty,
      typeId = TypeId.of[LongToInt.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[LongToInt.type](LongToInt),
        deconstructor = new ConstantDeconstructor[LongToInt.type]
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val intToStringSchema: Schema[IntToString.type] = new Schema(
    reflect = new Reflect.Record[Binding, IntToString.type](
      fields = Vector.empty,
      typeId = TypeId.of[IntToString.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[IntToString.type](IntToString),
        deconstructor = new ConstantDeconstructor[IntToString.type]
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val stringToIntSchema: Schema[StringToInt.type] = new Schema(
    reflect = new Reflect.Record[Binding, StringToInt.type](
      fields = Vector.empty,
      typeId = TypeId.of[StringToInt.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[StringToInt.type](StringToInt),
        deconstructor = new ConstantDeconstructor[StringToInt.type]
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val longToStringSchema: Schema[LongToString.type] = new Schema(
    reflect = new Reflect.Record[Binding, LongToString.type](
      fields = Vector.empty,
      typeId = TypeId.of[LongToString.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[LongToString.type](LongToString),
        deconstructor = new ConstantDeconstructor[LongToString.type]
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val stringToLongSchema: Schema[StringToLong.type] = new Schema(
    reflect = new Reflect.Record[Binding, StringToLong.type](
      fields = Vector.empty,
      typeId = TypeId.of[StringToLong.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[StringToLong.type](StringToLong),
        deconstructor = new ConstantDeconstructor[StringToLong.type]
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val doubleToStringSchema: Schema[DoubleToString.type] = new Schema(
    reflect = new Reflect.Record[Binding, DoubleToString.type](
      fields = Vector.empty,
      typeId = TypeId.of[DoubleToString.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[DoubleToString.type](DoubleToString),
        deconstructor = new ConstantDeconstructor[DoubleToString.type]
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val stringToDoubleSchema: Schema[StringToDouble.type] = new Schema(
    reflect = new Reflect.Record[Binding, StringToDouble.type](
      fields = Vector.empty,
      typeId = TypeId.of[StringToDouble.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[StringToDouble.type](StringToDouble),
        deconstructor = new ConstantDeconstructor[StringToDouble.type]
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val floatToDoubleSchema: Schema[FloatToDouble.type] = new Schema(
    reflect = new Reflect.Record[Binding, FloatToDouble.type](
      fields = Vector.empty,
      typeId = TypeId.of[FloatToDouble.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[FloatToDouble.type](FloatToDouble),
        deconstructor = new ConstantDeconstructor[FloatToDouble.type]
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val doubleToFloatSchema: Schema[DoubleToFloat.type] = new Schema(
    reflect = new Reflect.Record[Binding, DoubleToFloat.type](
      fields = Vector.empty,
      typeId = TypeId.of[DoubleToFloat.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[DoubleToFloat.type](DoubleToFloat),
        deconstructor = new ConstantDeconstructor[DoubleToFloat.type]
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val booleanToStringSchema: Schema[BooleanToString.type] = new Schema(
    reflect = new Reflect.Record[Binding, BooleanToString.type](
      fields = Vector.empty,
      typeId = TypeId.of[BooleanToString.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[BooleanToString.type](BooleanToString),
        deconstructor = new ConstantDeconstructor[BooleanToString.type]
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val stringToBooleanSchema: Schema[StringToBoolean.type] = new Schema(
    reflect = new Reflect.Record[Binding, StringToBoolean.type](
      fields = Vector.empty,
      typeId = TypeId.of[StringToBoolean.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[StringToBoolean.type](StringToBoolean),
        deconstructor = new ConstantDeconstructor[StringToBoolean.type]
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val intToDoubleSchema: Schema[IntToDouble.type] = new Schema(
    reflect = new Reflect.Record[Binding, IntToDouble.type](
      fields = Vector.empty,
      typeId = TypeId.of[IntToDouble.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[IntToDouble.type](IntToDouble),
        deconstructor = new ConstantDeconstructor[IntToDouble.type]
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val doubleToIntSchema: Schema[DoubleToInt.type] = new Schema(
    reflect = new Reflect.Record[Binding, DoubleToInt.type](
      fields = Vector.empty,
      typeId = TypeId.of[DoubleToInt.type],
      recordBinding = new Binding.Record(
        constructor = new ConstantConstructor[DoubleToInt.type](DoubleToInt),
        deconstructor = new ConstantDeconstructor[DoubleToInt.type]
      ),
      modifiers = Vector.empty
    )
  )

  implicit lazy val schema: Schema[PrimitiveConversion] = new Schema(
    reflect = new Reflect.Variant[Binding, PrimitiveConversion](
      cases = Vector(
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
        stringToBooleanSchema.reflect.asTerm("StringToBoolean"),
        intToDoubleSchema.reflect.asTerm("IntToDouble"),
        doubleToIntSchema.reflect.asTerm("DoubleToInt")
      ),
      typeId = TypeId.of[PrimitiveConversion],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[PrimitiveConversion] {
          def discriminate(a: PrimitiveConversion): Int = a match {
            case _: IntToLong.type       => 0
            case _: LongToInt.type       => 1
            case _: IntToString.type     => 2
            case _: StringToInt.type     => 3
            case _: LongToString.type    => 4
            case _: StringToLong.type    => 5
            case _: DoubleToString.type  => 6
            case _: StringToDouble.type  => 7
            case _: FloatToDouble.type   => 8
            case _: DoubleToFloat.type   => 9
            case _: BooleanToString.type => 10
            case _: StringToBoolean.type => 11
            case _: IntToDouble.type     => 12
            case _: DoubleToInt.type     => 13
          }
        },
        matchers = Matchers(
          new Matcher[IntToLong.type] {
            def downcastOrNull(a: Any): IntToLong.type = a match {
              case x: IntToLong.type => x
              case _                 => null.asInstanceOf[IntToLong.type]
            }
          },
          new Matcher[LongToInt.type] {
            def downcastOrNull(a: Any): LongToInt.type = a match {
              case x: LongToInt.type => x
              case _                 => null.asInstanceOf[LongToInt.type]
            }
          },
          new Matcher[IntToString.type] {
            def downcastOrNull(a: Any): IntToString.type = a match {
              case x: IntToString.type => x
              case _                   => null.asInstanceOf[IntToString.type]
            }
          },
          new Matcher[StringToInt.type] {
            def downcastOrNull(a: Any): StringToInt.type = a match {
              case x: StringToInt.type => x
              case _                   => null.asInstanceOf[StringToInt.type]
            }
          },
          new Matcher[LongToString.type] {
            def downcastOrNull(a: Any): LongToString.type = a match {
              case x: LongToString.type => x
              case _                    => null.asInstanceOf[LongToString.type]
            }
          },
          new Matcher[StringToLong.type] {
            def downcastOrNull(a: Any): StringToLong.type = a match {
              case x: StringToLong.type => x
              case _                    => null.asInstanceOf[StringToLong.type]
            }
          },
          new Matcher[DoubleToString.type] {
            def downcastOrNull(a: Any): DoubleToString.type = a match {
              case x: DoubleToString.type => x
              case _                      => null.asInstanceOf[DoubleToString.type]
            }
          },
          new Matcher[StringToDouble.type] {
            def downcastOrNull(a: Any): StringToDouble.type = a match {
              case x: StringToDouble.type => x
              case _                      => null.asInstanceOf[StringToDouble.type]
            }
          },
          new Matcher[FloatToDouble.type] {
            def downcastOrNull(a: Any): FloatToDouble.type = a match {
              case x: FloatToDouble.type => x
              case _                     => null.asInstanceOf[FloatToDouble.type]
            }
          },
          new Matcher[DoubleToFloat.type] {
            def downcastOrNull(a: Any): DoubleToFloat.type = a match {
              case x: DoubleToFloat.type => x
              case _                     => null.asInstanceOf[DoubleToFloat.type]
            }
          },
          new Matcher[BooleanToString.type] {
            def downcastOrNull(a: Any): BooleanToString.type = a match {
              case x: BooleanToString.type => x
              case _                       => null.asInstanceOf[BooleanToString.type]
            }
          },
          new Matcher[StringToBoolean.type] {
            def downcastOrNull(a: Any): StringToBoolean.type = a match {
              case x: StringToBoolean.type => x
              case _                       => null.asInstanceOf[StringToBoolean.type]
            }
          },
          new Matcher[IntToDouble.type] {
            def downcastOrNull(a: Any): IntToDouble.type = a match {
              case x: IntToDouble.type => x
              case _                   => null.asInstanceOf[IntToDouble.type]
            }
          },
          new Matcher[DoubleToInt.type] {
            def downcastOrNull(a: Any): DoubleToInt.type = a match {
              case x: DoubleToInt.type => x
              case _                   => null.asInstanceOf[DoubleToInt.type]
            }
          }
        )
      ),
      modifiers = Vector.empty
    )
  )
}
