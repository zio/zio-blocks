package returnhtmxfragments

import zio.blocks.html._
import zio.blocks.schema.json.{Json, WriterConfig}
import zio.http._
import zio.http.htmx._
import zio.http.htmx.headers._

import TeaShop._

/**
 * How to Return HTMX Fragments, Full Pages and Response Headers — Tell the Page
 * Something Happened
 *
 * "Add to cart" also announces `cart-updated` and `show-message` with
 * `HX-Trigger` headers, and `/cart/summary` answers the summary's refresh
 * request.
 *
 * Run with: cd zio-blocks-htmx-examples && sbt "runMain
 * returnhtmxfragments.Step3TriggerEventsExample"
 */
object Step3TriggerEventsExample {
  def addToCartAndAnnounce(productId: String): Response = {
    val response = Step2OutOfBandSwapExample.addToCart(productId)
    products.find(_.id == productId).fold(response) { product =>
      val message = Json.Object("show-message" -> Json.Object("text" -> Json.String(s"Added ${product.name}")))
      response
        .addHeader(HxTriggerHeader(HxEventPayload.Event("cart-updated")))
        .addHeader(HxTriggerAfterSettle(HxEventPayload.JsonValue(message)))
    }
  }

  def cartSummaryFragment: Response = htmlResponse(cartSummary.render)

  def app(request: Request): Response =
    (request.method, request.path.segments.toList) match {
      case (Method.GET, Nil)                        => catalogPage(request)
      case (Method.POST, List("cart", "items", id)) => addToCartAndAnnounce(id)
      case (Method.GET, List("cart", "summary"))    => cartSummaryFragment
      case _                                        => notFound(request)
    }

  def main(args: Array[String]): Unit = {
    // Setup from section 2: a Sencha is already in the cart
    Step2OutOfBandSwapExample.addToCart("sencha")
    show(addToCartAndAnnounce("assam"))
    show(cartSummaryFragment)
    val greeting = Json.Object("show-message" -> Json.Object("text" -> Json.String("Added Sencha ✓")))
    val escaped  = htmlResponse("").addHeader(HxTriggerHeader.name, greeting.print(WriterConfig.withEscapeUnicode(true)))
    println(escaped.headers.rawGet(HxTriggerHeader.name))
    println(escaped.header(HxTriggerHeader).map(_.renderedValue))
    val port = serve(app, args)
    openInBrowser(port)
  }
}
