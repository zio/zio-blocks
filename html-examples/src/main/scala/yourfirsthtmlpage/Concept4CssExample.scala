package yourfirsthtmlpage

import zio.blocks.html._

/**
 * Render Your First Server-Side HTML Page — Concept 4: Style the Page With CSS
 *
 * Builds a style sheet with typed lengths and a color and renders it in a
 * style element.
 *
 * Run with: cd html-examples && sbt "runMain yourfirsthtmlpage.Concept4CssExample"
 */
object Concept4CssExample extends App {

  val pageStyle: Css =
    css"""body { font-family: sans-serif; max-width: ${CssLength(40, "em")}; margin: 0 auto; }
.review { border-left: ${CssLength(4, "px")} solid ${CssColor.Rgb(200, 60, 40)}; padding-left: ${CssLength(1, "em")}; }"""

  println(style(pageStyle).render)
}
