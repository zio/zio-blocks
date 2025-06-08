package zio.blocks.schema

object OptionSchemas {
  implicit val noneSchema: Schema[None.type]                = Schema.derived
  implicit val byteSomeSchema: Schema[Some[Byte]]           = Schema.derived
  implicit val byteOptionSchema: Schema[Option[Byte]]       = Schema.derived
  implicit val booleanSomeSchema: Schema[Some[Boolean]]     = Schema.derived
  implicit val boolenaOptionSchema: Schema[Option[Boolean]] = Schema.derived
  implicit val shortSomeSchema: Schema[Some[Short]]         = Schema.derived
  implicit val shortOptionSchema: Schema[Option[Short]]     = Schema.derived
  implicit val charSomeSchema: Schema[Some[Char]]           = Schema.derived
  implicit val charOptionSchema: Schema[Option[Char]]       = Schema.derived
  implicit val intSomeSchema: Schema[Some[Int]]             = Schema.derived
  implicit val intOptionSchema: Schema[Option[Int]]         = Schema.derived
  implicit val floatSomeSchema: Schema[Some[Float]]         = Schema.derived
  implicit val floatOptionSchema: Schema[Option[Float]]     = Schema.derived
  implicit val longSomeSchema: Schema[Some[Long]]           = Schema.derived
  implicit val longOptionSchema: Schema[Option[Long]]       = Schema.derived
  implicit val doubleSomeSchema: Schema[Some[Double]]       = Schema.derived
  implicit val doubleOptionSchema: Schema[Option[Double]]   = Schema.derived

  implicit def stringSomeSchema[A <: AnyRef: Schema]: Schema[Some[A]] = Schema.derived

  implicit def stringOptionSchema[A <: AnyRef: Schema]: Schema[Option[A]] = Schema.derived
}
