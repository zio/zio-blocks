package zio.blocks.template

trait ToCss[-A] {
  def toCss(a: A): String
}

object ToCss {

  def apply[A](implicit ev: ToCss[A]): ToCss[A] = ev

  implicit val stringToCss: ToCss[String] = new ToCss[String] {
    def toCss(a: String): String = Escape.cssString(a)
  }

  implicit val intToCss: ToCss[Int] = new ToCss[Int] {
    def toCss(a: Int): String = a.toString
  }

  implicit val longToCss: ToCss[Long] = new ToCss[Long] {
    def toCss(a: Long): String = a.toString
  }

  implicit val doubleToCss: ToCss[Double] = new ToCss[Double] {
    def toCss(a: Double): String = a.toString
  }

  implicit val floatToCss: ToCss[Float] = new ToCss[Float] {
    def toCss(a: Float): String = a.toString
  }

  implicit val cssToCss: ToCss[Css] = new ToCss[Css] {
    def toCss(a: Css): String = a.render
  }

  implicit def optionToCss[A](implicit ev: ToCss[A]): ToCss[Option[A]] = new ToCss[Option[A]] {
    def toCss(a: Option[A]): String = a match {
      case Some(v) => ev.toCss(v)
      case None    => ""
    }
  }

  implicit val cssLengthToCss: ToCss[CssLength] = new ToCss[CssLength] {
    def toCss(a: CssLength): String = a.render
  }

  implicit val cssColorToCss: ToCss[CssColor] = new ToCss[CssColor] {
    def toCss(a: CssColor): String = a.render
  }
}

final case class CssLength(value: Double, unit: String) {
  def render: String = {
    val v = if (value == value.toLong.toDouble) value.toLong.toString else value.toString
    v + unit
  }
}

object CssLength {
  implicit class CssLengthIntOps(private val n: Int) extends AnyVal {
    def px: CssLength  = CssLength(n.toDouble, "px")
    def em: CssLength  = CssLength(n.toDouble, "em")
    def rem: CssLength = CssLength(n.toDouble, "rem")
    def pct: CssLength = CssLength(n.toDouble, "%")
    def vh: CssLength  = CssLength(n.toDouble, "vh")
    def vw: CssLength  = CssLength(n.toDouble, "vw")
  }

  implicit class CssLengthDoubleOps(private val n: Double) extends AnyVal {
    def px: CssLength  = CssLength(n, "px")
    def em: CssLength  = CssLength(n, "em")
    def rem: CssLength = CssLength(n, "rem")
    def pct: CssLength = CssLength(n, "%")
    def vh: CssLength  = CssLength(n, "vh")
    def vw: CssLength  = CssLength(n, "vw")
  }
}

sealed trait CssColor extends Product with Serializable {
  def render: String
}

object CssColor {
  final case class Hex(value: String) extends CssColor {
    def render: String = "#" + value
  }

  final case class Rgb(r: Int, g: Int, b: Int) extends CssColor {
    def render: String = "rgb(" + r + "," + g + "," + b + ")"
  }

  final case class Rgba(r: Int, g: Int, b: Int, a: Double) extends CssColor {
    def render: String = "rgba(" + r + "," + g + "," + b + "," + a + ")"
  }

  final case class Hsl(h: Int, s: Int, l: Int) extends CssColor {
    def render: String = "hsl(" + h + "," + s + "%," + l + "%)"
  }

  final case class Named(name: String) extends CssColor {
    def render: String = name
  }
}
