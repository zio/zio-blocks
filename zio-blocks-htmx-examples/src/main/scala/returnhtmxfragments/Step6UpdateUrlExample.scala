package returnhtmxfragments

import zio.blocks.html._
import zio.http._
import zio.http.htmx._
import zio.http.htmx.headers._

import TeaShop._

/**
 * How to Return HTMX Fragments, Full Pages and Response Headers — Update the
 * Address Bar from the Server
 *
 * A sort change on an htmx request replaces the address with `HX-Replace-Url`
 * instead of adding a history entry.
 *
 * Run with: cd zio-blocks-htmx-examples && sbt "runMain
 * returnhtmxfragments.Step6UpdateUrlExample"
 */
object Step6UpdateUrlExample {
  def catalogRoute(request: Request): Response = {
    val response = catalogPage(request)
    request.queryParams.getFirst("sort") match {
      case Some(sort) if wantsFragment(request) =>
        val category = request.queryParams.getFirst("category").getOrElse("")
        response.addHeader(HxReplaceUrl(HxUrlUpdate(catalogUrl(category, sort))))
      case _ => response
    }
  }

  def app(request: Request): Response =
    (request.method, request.path.segments.toList) match {
      case (Method.GET, Nil)                        => catalogRoute(request)
      case (Method.POST, List("cart", "items", id)) => Step3TriggerEventsExample.addToCartAndAnnounce(id)
      case (Method.GET, List("cart", "summary"))    => Step3TriggerEventsExample.cartSummaryFragment
      case _                                        => notFound(request)
    }

  def main(args: Array[String]): Unit = {
    show(catalogRoute(get("/?category=Green&sort=price", HxRequest(true))))
    val port = serve(app, args)
    openInBrowser(port)
  }
}
