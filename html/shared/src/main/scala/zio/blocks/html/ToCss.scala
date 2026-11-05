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

/**
 * Converts a value to CSS text for the values interpolated into `css"..."` and
 * `selector"..."`, and for the value of `Css.Declaration(property, value)`.
 */
trait ToCss[-A] {

  /** Converts the value to CSS text. */
  def toCss(a: A): String
}

/**
 * Instances of [[ToCss]] for strings, numbers, [[Css]] values, lengths, colors
 * and options.
 */
object ToCss {

  /** Summons the implicit [[ToCss]] instance for `A`. */
  def apply[A](implicit ev: ToCss[A]): ToCss[A] = ev

  /**
   * Escapes backslashes, quotes, `<`, `>`, `&` and control characters in the
   * `String`, and adds no surrounding quotes.
   */
  implicit val stringToCss: ToCss[String] = new ToCss[String] {
    def toCss(a: String): String = Escape.cssString(a)
  }

  /** Renders an `Int` in decimal. */
  implicit val intToCss: ToCss[Int] = new ToCss[Int] {
    def toCss(a: Int): String = a.toString
  }

  /** Renders a `Long` in decimal. */
  implicit val longToCss: ToCss[Long] = new ToCss[Long] {
    def toCss(a: Long): String = a.toString
  }

  /**
   * Renders a `Double` with `Double#toString`: `1.0` on the JVM, `1` on
   * Scala.js.
   */
  implicit val doubleToCss: ToCss[Double] = new ToCss[Double] {
    def toCss(a: Double): String = a.toString
  }

  /** Renders a `Float` with `Float#toString`. */
  implicit val floatToCss: ToCss[Float] = new ToCss[Float] {
    def toCss(a: Float): String = a.toString
  }

  /** Renders a [[Css]] value with its `render` text. */
  implicit val cssToCss: ToCss[Css] = new ToCss[Css] {
    def toCss(a: Css): String = a.render
  }

  /**
   * Renders the wrapped value of a `Some` with the instance for `A`, and an
   * empty string for `None`.
   */
  implicit def optionToCss[A](implicit ev: ToCss[A]): ToCss[Option[A]] = new ToCss[Option[A]] {
    def toCss(a: Option[A]): String = a match {
      case Some(v) => ev.toCss(v)
      case None    => ""
    }
  }

  /** Renders a [[CssLength]] with its `render` text, such as `10px`. */
  implicit val cssLengthToCss: ToCss[CssLength] = new ToCss[CssLength] {
    def toCss(a: CssLength): String = a.render
  }

  /**
   * Renders a [[CssColor]] with its `render` text, such as `#ff0000` or
   * `rgb(1,2,3)`.
   */
  implicit val cssColorToCss: ToCss[CssColor] = new ToCss[CssColor] {
    def toCss(a: CssColor): String = a.render
  }
}

/**
 * A CSS length value with a numeric value and unit (e.g., `10px`, `2em`,
 * `50%`). The constructor throws `java.lang.IllegalArgumentException` for a
 * unit other than `px`, `em`, `rem`, `%`, `vh`, `vw`, `ch`, `ex`, `vmin`,
 * `vmax`, `cm`, `mm`, `in`, `pt` and `pc`.
 */
final case class CssLength(value: Double, unit: String) {
  require(CssLength.validUnits.contains(unit), s"Invalid CSS unit: $unit")

  /**
   * Renders the number followed by the unit. A whole number within the `Long`
   * range drops its decimal part (`10px`), any other value uses
   * `Double#toString` (`1.5em`), and `NaN` or an infinite value renders as `0`
   * plus the unit.
   */
  def render: String =
    if (value.isNaN || value.isInfinite) "0" + unit
    else {
      val v = if (value == value.toLong.toDouble) value.toLong.toString else value.toString
      v + unit
    }
}

/**
 * Companion of [[CssLength]] with the unit extension methods for `Int` and
 * `Double`. Bring them into scope with `import zio.blocks.html.CssLength._`.
 */
object CssLength {
  private[html] val validUnits: Set[String] =
    Set("px", "em", "rem", "%", "vh", "vw", "ch", "ex", "vmin", "vmax", "cm", "mm", "in", "pt", "pc")

  /** Adds unit methods such as `10.px` to `Int` values. */
  implicit class CssLengthIntOps(private val n: Int) extends AnyVal {

    /** Makes a length in `px` from this number. */
    def px: CssLength = CssLength(n.toDouble, "px")

    /** Makes a length in `em` from this number. */
    def em: CssLength = CssLength(n.toDouble, "em")

    /** Makes a length in `rem` from this number. */
    def rem: CssLength = CssLength(n.toDouble, "rem")

    /**
     * Makes a length in percent, rendered with the unit `%`, from this number.
     */
    def pct: CssLength = CssLength(n.toDouble, "%")

    /** Makes a length in `vh` from this number. */
    def vh: CssLength = CssLength(n.toDouble, "vh")

    /** Makes a length in `vw` from this number. */
    def vw: CssLength = CssLength(n.toDouble, "vw")
  }

  /** Adds unit methods such as `1.5.em` to `Double` values. */
  implicit class CssLengthDoubleOps(private val n: Double) extends AnyVal {

    /** Makes a length in `px` from this number. */
    def px: CssLength = CssLength(n, "px")

    /** Makes a length in `em` from this number. */
    def em: CssLength = CssLength(n, "em")

    /** Makes a length in `rem` from this number. */
    def rem: CssLength = CssLength(n, "rem")

    /**
     * Makes a length in percent, rendered with the unit `%`, from this number.
     */
    def pct: CssLength = CssLength(n, "%")

    /** Makes a length in `vh` from this number. */
    def vh: CssLength = CssLength(n, "vh")

    /** Makes a length in `vw` from this number. */
    def vw: CssLength = CssLength(n, "vw")
  }
}

/** A CSS color value: hex, RGB, RGBA, HSL or a named color. */
sealed trait CssColor extends Product with Serializable {

  /** Renders the color as CSS text. */
  def render: String
}

/**
 * Companion of [[CssColor]] with the hex, RGB, RGBA, HSL and named color forms.
 */
object CssColor {

  /** A hexadecimal color, stored without the leading `#`. */
  final case class Hex private (value: String) extends CssColor {

    /** Renders the digits with a leading `#`, such as `#ff0000`. */
    def render: String = "#" + value
  }

  /**
   * Companion of [[CssColor.Hex]] with the checked and unchecked constructors.
   */
  object Hex {

    /**
     * Accepts three to eight hexadecimal digits, with or without a leading `#`,
     * and lowercases them. Returns `None` for any other text. Five and seven
     * digits are accepted too, although CSS accepts only three, four, six or
     * eight, which is a known bug.
     */
    def apply(value: String): Option[CssColor] = {
      val v = if (value.startsWith("#")) value.drop(1) else value
      if (v.matches("[0-9a-fA-F]{3,8}")) Some(new Hex(v.toLowerCase))
      else None
    }

    /**
     * Makes a hex color from the text as it is, without checking it or
     * lowercasing it.
     */
    def unsafe(value: String): CssColor = new Hex(value)
  }

  /** An RGB color from three integer channels, with no range check. */
  final case class Rgb(r: Int, g: Int, b: Int) extends CssColor {

    /** Renders as `rgb(r,g,b)`, with no spaces after the commas. */
    def render: String = "rgb(" + r + "," + g + "," + b + ")"
  }

  /**
   * An RGB color from three integer channels and a `Double` alpha, with no
   * range check.
   */
  final case class Rgba(r: Int, g: Int, b: Int, a: Double) extends CssColor {

    /** Renders as `rgba(r,g,b,a)`, with no spaces after the commas. */
    def render: String = "rgba(" + r + "," + g + "," + b + "," + a + ")"
  }

  /**
   * An HSL color from a hue, a saturation and a lightness, with no range check.
   */
  final case class Hsl(h: Int, s: Int, l: Int) extends CssColor {

    /** Renders as `hsl(h,s%,l%)`, with no spaces after the commas. */
    def render: String = "hsl(" + h + "," + s + "%," + l + "%)"
  }

  /**
   * A color given by its CSS name or keyword, such as `red` or `transparent`.
   */
  final case class Named private (name: String) extends CssColor {

    /** Renders the name as stored. */
    def render: String = name
  }

  /**
   * Companion of [[CssColor.Named]] with the checked and unchecked
   * constructors.
   */
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

    /**
     * Lowercases the name and accepts it when it is in the built-in set of
     * color names and keywords. Returns `None` for any other name.
     */
    def apply(name: String): Option[CssColor] = {
      val lower = name.toLowerCase
      if (validColors.contains(lower)) Some(new Named(lower))
      else None
    }

    /**
     * Makes a named color from the text as it is, without checking it or
     * lowercasing it.
     */
    def unsafe(name: String): CssColor = new Named(name)
  }
}
