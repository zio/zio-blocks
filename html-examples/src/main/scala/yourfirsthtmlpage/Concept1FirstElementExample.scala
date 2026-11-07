package yourfirsthtmlpage

import zio.blocks.html._

/**
 * Render Your First Server-Side HTML Page — Concept 1: Render Your First Element
 *
 * Renders a heading and a small div, compact and indented.
 *
 * Run with: cd html-examples && sbt "runMain yourfirsthtmlpage.Concept1FirstElementExample"
 */
object Concept1FirstElementExample extends App {

  val heading = h1("Zionomicon")

  println(heading.render)

  val summary = div(
    h1("Zionomicon"),
    p("by John A. De Goes, Adam Fraser and Milad Khajavi")
  )

  println(summary.render)

  println(summary.render(indent = 2))
}
