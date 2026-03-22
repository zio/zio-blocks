package zio.blocks.template

trait ToText[-A] {
  def toText(a: A): String
}

object ToText {

  def apply[A](implicit ev: ToText[A]): ToText[A] = ev

  implicit val stringToText: ToText[String] = new ToText[String] {
    def toText(a: String): String = a
  }

  implicit val intToText: ToText[Int] = new ToText[Int] {
    def toText(a: Int): String = a.toString
  }

  implicit val longToText: ToText[Long] = new ToText[Long] {
    def toText(a: Long): String = a.toString
  }

  implicit val doubleToText: ToText[Double] = new ToText[Double] {
    def toText(a: Double): String = a.toString
  }

  implicit val floatToText: ToText[Float] = new ToText[Float] {
    def toText(a: Float): String = a.toString
  }

  implicit val booleanToText: ToText[Boolean] = new ToText[Boolean] {
    def toText(a: Boolean): String = a.toString
  }

  implicit val charToText: ToText[Char] = new ToText[Char] {
    def toText(a: Char): String = a.toString
  }

  implicit val byteToText: ToText[Byte] = new ToText[Byte] {
    def toText(a: Byte): String = a.toString
  }

  implicit val shortToText: ToText[Short] = new ToText[Short] {
    def toText(a: Short): String = a.toString
  }

  implicit val bigIntToText: ToText[BigInt] = new ToText[BigInt] {
    def toText(a: BigInt): String = a.toString
  }

  implicit val bigDecimalToText: ToText[BigDecimal] = new ToText[BigDecimal] {
    def toText(a: BigDecimal): String = a.toString
  }
}
