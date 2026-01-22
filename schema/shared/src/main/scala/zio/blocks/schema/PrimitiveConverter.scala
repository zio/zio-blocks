package zio.blocks.schema

/**
 * Represents a primitive type converter used for migration transformations. All
 * converters are reversible and operate on DynamicValue.Primitive.
 */
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
}
