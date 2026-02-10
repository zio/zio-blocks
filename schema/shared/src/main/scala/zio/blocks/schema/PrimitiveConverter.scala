package zio.blocks.schema

import zio.blocks.chunk.Chunk
import zio.blocks.schema.binding._
import zio.blocks.typeid.TypeId

//Represents a primitive type converter used for migration transformations.
//All converters are reversible and operate on DynamicValue.Primitive.
sealed trait PrimitiveConverter {

  /**
   * Convert a DynamicValue.Primitive from one type to another. Returns Left
   * with error message if conversion fails.
   */
  def convert(value: DynamicValue): Either[String, DynamicValue]

  /**
   * Returns the reverse converter.
   */
  def reverse: PrimitiveConverter
}

object PrimitiveConverter {

  // String <-> Int
  case object StringToInt extends PrimitiveConverter {
    def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(str)) =>
        try {
          Right(DynamicValue.Primitive(PrimitiveValue.Int(str.toInt)))
        } catch {
          case _: NumberFormatException =>
            Left(s"Cannot convert string '$str' to Int")
        }
      case other =>
        Left(s"Expected String, got ${other.getClass.getSimpleName}")
    }

    def reverse: PrimitiveConverter = IntToString
  }

  case object IntToString extends PrimitiveConverter {
    def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Int(i)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.String(i.toString)))
      case other =>
        Left(s"Expected Int, got ${other.getClass.getSimpleName}")
    }

    def reverse: PrimitiveConverter = StringToInt
  }

  // String <-> Long
  case object StringToLong extends PrimitiveConverter {
    def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(str)) =>
        try {
          Right(DynamicValue.Primitive(PrimitiveValue.Long(str.toLong)))
        } catch {
          case _: NumberFormatException =>
            Left(s"Cannot convert string '$str' to Long")
        }
      case other =>
        Left(s"Expected String, got ${other.getClass.getSimpleName}")
    }

    def reverse: PrimitiveConverter = LongToString
  }

  case object LongToString extends PrimitiveConverter {
    def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Long(l)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.String(l.toString)))
      case other =>
        Left(s"Expected Long, got ${other.getClass.getSimpleName}")
    }

    def reverse: PrimitiveConverter = StringToLong
  }

  // String <-> Double
  case object StringToDouble extends PrimitiveConverter {
    def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.String(str)) =>
        try {
          Right(DynamicValue.Primitive(PrimitiveValue.Double(str.toDouble)))
        } catch {
          case _: NumberFormatException =>
            Left(s"Cannot convert string '$str' to Double")
        }
      case other =>
        Left(s"Expected String, got ${other.getClass.getSimpleName}")
    }

    def reverse: PrimitiveConverter = DoubleToString
  }

  case object DoubleToString extends PrimitiveConverter {
    def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Double(d)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.String(d.toString)))
      case other =>
        Left(s"Expected Double, got ${other.getClass.getSimpleName}")
    }

    def reverse: PrimitiveConverter = StringToDouble
  }

  // Int <-> Long
  case object IntToLong extends PrimitiveConverter {
    def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Int(i)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Long(i.toLong)))
      case other =>
        Left(s"Expected Int, got ${other.getClass.getSimpleName}")
    }

    def reverse: PrimitiveConverter = LongToInt
  }

  case object LongToInt extends PrimitiveConverter {
    def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Long(l)) =>
        if (l >= Int.MinValue && l <= Int.MaxValue) {
          Right(DynamicValue.Primitive(PrimitiveValue.Int(l.toInt)))
        } else {
          Left(s"Long value $l is out of Int range")
        }
      case other =>
        Left(s"Expected Long, got ${other.getClass.getSimpleName}")
    }

    def reverse: PrimitiveConverter = IntToLong
  }

  // Int <-> Double
  case object IntToDouble extends PrimitiveConverter {
    def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Int(i)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Double(i.toDouble)))
      case other =>
        Left(s"Expected Int, got ${other.getClass.getSimpleName}")
    }

    def reverse: PrimitiveConverter = DoubleToInt
  }

  case object DoubleToInt extends PrimitiveConverter {
    def convert(value: DynamicValue): Either[String, DynamicValue] = value match {
      case DynamicValue.Primitive(PrimitiveValue.Double(d)) =>
        Right(DynamicValue.Primitive(PrimitiveValue.Int(d.toInt)))
      case other =>
        Left(s"Expected Double, got ${other.getClass.getSimpleName}")
    }

    def reverse: PrimitiveConverter = IntToDouble
  }

  // --- Manual Schema derivations for Scala 2 ---

  private def caseObjectSchema[A <: PrimitiveConverter](instance: A, id: TypeId[A]): Schema[A] =
    new Schema(
      new Reflect.Record[Binding, A](
        fields = Chunk.empty,
        typeId = id,
        recordBinding = new Binding.Record(
          constructor = new ConstantConstructor[A](instance),
          deconstructor = new ConstantDeconstructor[A]
        ),
        modifiers = Chunk.empty
      )
    )

  implicit lazy val stringToIntSchema: Schema[StringToInt.type] =
    caseObjectSchema(StringToInt, TypeId.of[StringToInt.type])
  implicit lazy val intToStringSchema: Schema[IntToString.type] =
    caseObjectSchema(IntToString, TypeId.of[IntToString.type])
  implicit lazy val stringToLongSchema: Schema[StringToLong.type] =
    caseObjectSchema(StringToLong, TypeId.of[StringToLong.type])
  implicit lazy val longToStringSchema: Schema[LongToString.type] =
    caseObjectSchema(LongToString, TypeId.of[LongToString.type])
  implicit lazy val stringToDoubleSchema: Schema[StringToDouble.type] =
    caseObjectSchema(StringToDouble, TypeId.of[StringToDouble.type])
  implicit lazy val doubleToStringSchema: Schema[DoubleToString.type] =
    caseObjectSchema(DoubleToString, TypeId.of[DoubleToString.type])
  implicit lazy val intToLongSchema: Schema[IntToLong.type]     = caseObjectSchema(IntToLong, TypeId.of[IntToLong.type])
  implicit lazy val longToIntSchema: Schema[LongToInt.type]     = caseObjectSchema(LongToInt, TypeId.of[LongToInt.type])
  implicit lazy val intToDoubleSchema: Schema[IntToDouble.type] =
    caseObjectSchema(IntToDouble, TypeId.of[IntToDouble.type])
  implicit lazy val doubleToIntSchema: Schema[DoubleToInt.type] =
    caseObjectSchema(DoubleToInt, TypeId.of[DoubleToInt.type])

  implicit lazy val schema: Schema[PrimitiveConverter] = new Schema(
    new Reflect.Variant[Binding, PrimitiveConverter](
      cases = Chunk(
        stringToIntSchema.reflect.asTerm("StringToInt"),
        intToStringSchema.reflect.asTerm("IntToString"),
        stringToLongSchema.reflect.asTerm("StringToLong"),
        longToStringSchema.reflect.asTerm("LongToString"),
        stringToDoubleSchema.reflect.asTerm("StringToDouble"),
        doubleToStringSchema.reflect.asTerm("DoubleToString"),
        intToLongSchema.reflect.asTerm("IntToLong"),
        longToIntSchema.reflect.asTerm("LongToInt"),
        intToDoubleSchema.reflect.asTerm("IntToDouble"),
        doubleToIntSchema.reflect.asTerm("DoubleToInt")
      ),
      typeId = TypeId.of[PrimitiveConverter],
      variantBinding = new Binding.Variant(
        discriminator = new Discriminator[PrimitiveConverter] {
          def discriminate(a: PrimitiveConverter): Int = a match {
            case StringToInt    => 0
            case IntToString    => 1
            case StringToLong   => 2
            case LongToString   => 3
            case StringToDouble => 4
            case DoubleToString => 5
            case IntToLong      => 6
            case LongToInt      => 7
            case IntToDouble    => 8
            case DoubleToInt    => 9
          }
        },
        matchers = Matchers(
          new Matcher[StringToInt.type] {
            def downcastOrNull(a: Any): StringToInt.type =
              if (a.asInstanceOf[AnyRef] eq StringToInt) StringToInt else null.asInstanceOf[StringToInt.type]
          },
          new Matcher[IntToString.type] {
            def downcastOrNull(a: Any): IntToString.type =
              if (a.asInstanceOf[AnyRef] eq IntToString) IntToString else null.asInstanceOf[IntToString.type]
          },
          new Matcher[StringToLong.type] {
            def downcastOrNull(a: Any): StringToLong.type =
              if (a.asInstanceOf[AnyRef] eq StringToLong) StringToLong else null.asInstanceOf[StringToLong.type]
          },
          new Matcher[LongToString.type] {
            def downcastOrNull(a: Any): LongToString.type =
              if (a.asInstanceOf[AnyRef] eq LongToString) LongToString else null.asInstanceOf[LongToString.type]
          },
          new Matcher[StringToDouble.type] {
            def downcastOrNull(a: Any): StringToDouble.type =
              if (a.asInstanceOf[AnyRef] eq StringToDouble) StringToDouble else null.asInstanceOf[StringToDouble.type]
          },
          new Matcher[DoubleToString.type] {
            def downcastOrNull(a: Any): DoubleToString.type =
              if (a.asInstanceOf[AnyRef] eq DoubleToString) DoubleToString else null.asInstanceOf[DoubleToString.type]
          },
          new Matcher[IntToLong.type] {
            def downcastOrNull(a: Any): IntToLong.type =
              if (a.asInstanceOf[AnyRef] eq IntToLong) IntToLong else null.asInstanceOf[IntToLong.type]
          },
          new Matcher[LongToInt.type] {
            def downcastOrNull(a: Any): LongToInt.type =
              if (a.asInstanceOf[AnyRef] eq LongToInt) LongToInt else null.asInstanceOf[LongToInt.type]
          },
          new Matcher[IntToDouble.type] {
            def downcastOrNull(a: Any): IntToDouble.type =
              if (a.asInstanceOf[AnyRef] eq IntToDouble) IntToDouble else null.asInstanceOf[IntToDouble.type]
          },
          new Matcher[DoubleToInt.type] {
            def downcastOrNull(a: Any): DoubleToInt.type =
              if (a.asInstanceOf[AnyRef] eq DoubleToInt) DoubleToInt else null.asInstanceOf[DoubleToInt.type]
          }
        )
      ),
      modifiers = Chunk.empty
    )
  )
}
