package yourfirsthtmlpage

import zio.blocks.html._
import zio.blocks.schema.Schema

/**
 * Render Your First Server-Side HTML Page — Concept 3: Show User Input Safely
 *
 * Renders a hostile review and shows the escaped text and the unsafe: link.
 *
 * Run with: cd html-examples && sbt "runMain yourfirsthtmlpage.Concept3EscapingExample"
 */
object Concept3EscapingExample extends App {

  final case class Review(reviewer: String, text: String, website: String)

  object Review {
    implicit val schema: Schema[Review] = Schema.derived
  }

  val review = Review(
    reviewer = "mallory",
    text = "Loved it! <script>alert('stolen cookies')</script>",
    website = "javascript:alert('stolen cookies')"
  )

  def reviewBlock(review: Review): Dom.Element =
    div(
      className := "review",
      p(review.text),
      a(href := review.website, s"Visit ${review.reviewer}'s website")
    )

  println(reviewBlock(review).render(indent = 2))
}
