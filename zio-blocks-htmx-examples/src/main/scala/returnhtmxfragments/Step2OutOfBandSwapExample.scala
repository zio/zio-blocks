package returnhtmxfragments

import zio.blocks.html._
import zio.http._
import zio.http.htmx._
import zio.http.htmx.headers._

import TeaShop._

/**
 * How to Return HTMX Fragments, Full Pages and Response Headers — Update a
 * Second Spot with an Out-of-Band Swap
 *
 * "Add to cart" answers with the new button and, out of band, the new cart
 * badge.
 *
 * Run with: cd zio-blocks-htmx-examples && sbt "runMain
 * returnhtmxfragments.Step2OutOfBandSwapExample"
 */
object Step2OutOfBandSwapExample {
  def addToCart(productId: String): Response =
    products.find(_.id == productId) match {
      case None          => Response.text(Status.NotFound, s"No product $productId")
      case Some(product) =>
        cart.add(product.id)
        val badge = cartBadge(hxSwapOob := HxSwapOob.True)
        htmlResponse(addButton(product).render + badge.render)
    }

  def app(request: Request): Response =
    (request.method, request.path.segments.toList) match {
      case (Method.GET, Nil)                        => catalogPage(request)
      case (Method.POST, List("cart", "items", id)) => addToCart(id)
      case _                                        => notFound(request)
    }

  def main(args: Array[String]): Unit = {
    show(addToCart("sencha"))
    val port = serve(app, args)
    openInBrowser(port)
  }
}
