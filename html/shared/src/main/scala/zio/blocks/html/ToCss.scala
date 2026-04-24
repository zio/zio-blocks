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

package zio.blocks.html

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
  require(CssLength.validUnits.contains(unit), s"Invalid CSS unit: $unit")

  def render: String =
    if (value.isNaN || value.isInfinite) "0" + unit
    else {
      val v = if (value == value.toLong.toDouble) value.toLong.toString else value.toString
      v + unit
    }
}

object CssLength {
  private[html] val validUnits: Set[String] =
    Set("px", "em", "rem", "%", "vh", "vw", "ch", "ex", "vmin", "vmax", "cm", "mm", "in", "pt", "pc")

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
  final case class Hex private (value: String) extends CssColor {
    def render: String = "#" + value
  }

  object Hex {
    def apply(value: String): Option[CssColor] = {
      val v = if (value.startsWith("#")) value.drop(1) else value
      if (v.matches("[0-9a-fA-F]{3,8}")) Some(new Hex(v.toLowerCase))
      else None
    }

    def unsafe(value: String): CssColor = new Hex(value)
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

  final case class Named private (name: String) extends CssColor {
    def render: String = name
  }

  object Named {
    private val validColors: Set[String] = Set(
      "aliceblue",
      "antiquewhite",
      "aqua",
      "aquamarine",
      "azure",
      "beige",
      "bisque",
      "black",
      "blanchedalmond",
      "blue",
      "blueviolet",
      "brown",
      "burlywood",
      "cadetblue",
      "chartreuse",
      "chocolate",
      "coral",
      "cornflowerblue",
      "cornsilk",
      "crimson",
      "cyan",
      "darkblue",
      "darkcyan",
      "darkgoldenrod",
      "darkgray",
      "darkgreen",
      "darkgrey",
      "darkkhaki",
      "darkmagenta",
      "darkolivegreen",
      "darkorange",
      "darkorchid",
      "darkred",
      "darksalmon",
      "darkseagreen",
      "darkslateblue",
      "darkslategray",
      "darkslategrey",
      "darkturquoise",
      "darkviolet",
      "deeppink",
      "deepskyblue",
      "dimgray",
      "dimgrey",
      "dodgerblue",
      "firebrick",
      "floralwhite",
      "forestgreen",
      "fuchsia",
      "gainsboro",
      "ghostwhite",
      "gold",
      "goldenrod",
      "gray",
      "green",
      "greenyellow",
      "grey",
      "honeydew",
      "hotpink",
      "indianred",
      "indigo",
      "ivory",
      "khaki",
      "lavender",
      "lavenderblush",
      "lawngreen",
      "lemonchiffon",
      "lightblue",
      "lightcoral",
      "lightcyan",
      "lightgoldenrodyellow",
      "lightgray",
      "lightgreen",
      "lightgrey",
      "lightpink",
      "lightsalmon",
      "lightseagreen",
      "lightskyblue",
      "lightslategray",
      "lightslategrey",
      "lightsteelblue",
      "lightyellow",
      "lime",
      "limegreen",
      "linen",
      "magenta",
      "maroon",
      "mediumaquamarine",
      "mediumblue",
      "mediumorchid",
      "mediumpurple",
      "mediumseagreen",
      "mediumslateblue",
      "mediumspringgreen",
      "mediumturquoise",
      "mediumvioletred",
      "midnightblue",
      "mintcream",
      "mistyrose",
      "moccasin",
      "navajowhite",
      "navy",
      "oldlace",
      "olive",
      "olivedrab",
      "orange",
      "orangered",
      "orchid",
      "palegoldenrod",
      "palegreen",
      "paleturquoise",
      "palevioletred",
      "papayawhip",
      "peachpuff",
      "peru",
      "pink",
      "plum",
      "powderblue",
      "purple",
      "rebeccapurple",
      "red",
      "rosybrown",
      "royalblue",
      "saddlebrown",
      "salmon",
      "sandybrown",
      "seagreen",
      "seashell",
      "sienna",
      "silver",
      "skyblue",
      "slateblue",
      "slategray",
      "slategrey",
      "snow",
      "springgreen",
      "steelblue",
      "tan",
      "teal",
      "thistle",
      "tomato",
      "turquoise",
      "violet",
      "wheat",
      "white",
      "whitesmoke",
      "yellow",
      "yellowgreen",
      "transparent",
      "currentcolor",
      "inherit",
      "initial",
      "unset",
      "revert"
    )

    def apply(name: String): Option[CssColor] = {
      val lower = name.toLowerCase
      if (validColors.contains(lower)) Some(new Named(lower))
      else None
    }

    def unsafe(name: String): CssColor = new Named(name)
  }
}
