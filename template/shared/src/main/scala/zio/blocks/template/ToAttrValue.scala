package zio.blocks.template

trait ToAttrValue[-A] {
  def toAttrValue(a: A): String
}

object ToAttrValue {

  def apply[A](implicit ev: ToAttrValue[A]): ToAttrValue[A] = ev

  implicit val stringToAttrValue: ToAttrValue[String] = new ToAttrValue[String] {
    def toAttrValue(a: String): String = Escape.html(a)
  }

  implicit val intToAttrValue: ToAttrValue[Int] = new ToAttrValue[Int] {
    def toAttrValue(a: Int): String = a.toString
  }

  implicit val longToAttrValue: ToAttrValue[Long] = new ToAttrValue[Long] {
    def toAttrValue(a: Long): String = a.toString
  }

  implicit val doubleToAttrValue: ToAttrValue[Double] = new ToAttrValue[Double] {
    def toAttrValue(a: Double): String = a.toString
  }

  implicit val booleanToAttrValue: ToAttrValue[Boolean] = new ToAttrValue[Boolean] {
    def toAttrValue(a: Boolean): String = a.toString
  }

  implicit val charToAttrValue: ToAttrValue[Char] = new ToAttrValue[Char] {
    def toAttrValue(a: Char): String = Escape.html(a.toString)
  }

  implicit val jsToAttrValue: ToAttrValue[Js] = new ToAttrValue[Js] {
    def toAttrValue(a: Js): String = a.value
  }

  implicit val cssToAttrValue: ToAttrValue[Css] = new ToAttrValue[Css] {
    def toAttrValue(a: Css): String = Escape.html(a.value)
  }

  implicit def optionToAttrValue[A](implicit ev: ToAttrValue[A]): ToAttrValue[Option[A]] =
    new ToAttrValue[Option[A]] {
      def toAttrValue(a: Option[A]): String = a match {
        case Some(v) => ev.toAttrValue(v)
        case None    => ""
      }
    }

  implicit def iterableStringToAttrValue: ToAttrValue[Iterable[String]] =
    new ToAttrValue[Iterable[String]] {
      def toAttrValue(a: Iterable[String]): String = {
        val sb  = new java.lang.StringBuilder
        val it  = a.iterator
        var sep = false
        while (it.hasNext) {
          if (sep) sb.append(' ')
          sb.append(Escape.html(it.next()))
          sep = true
        }
        sb.toString
      }
    }
}
