package returnhtmxfragments

import zio.blocks.html._
import zio.http._
import zio.http.htmx._
import zio.http.htmx.headers._

import TeaShop._

/**
 * How to Return HTMX Fragments, Full Pages and Response Headers — Answer One
 * URL with a Full Page or a Fragment
 *
 * Answers `/` with the full page, or with the `#catalog` fragment when an htmx
 * link asks for it. The `wantsFragment` check reads `HX-Request`, `HX-Boosted`
 * and `HX-History-Restore-Request`.
 *
 * Run with: cd zio-blocks-htmx-examples && sbt "runMain
 * returnhtmxfragments.Step1FullPageOrFragmentExample"
 */
object Step1FullPageOrFragmentExample {
  def app(request: Request): Response =
    (request.method, request.path.segments.toList) match {
      case (Method.GET, Nil) => catalogPage(request)
      case _                 => notFound(request)
    }

  def main(args: Array[String]): Unit = {
    println(wantsFragment(get("/?category=Green")))
    println(wantsFragment(get("/?category=Green", HxRequest(true))))
    println(wantsFragment(get("/cart", HxRequest(true), HxBoosted(true))))
    println(wantsFragment(get("/?category=Green", HxRequest(true), HxHistoryRestoreRequest(true))))
    println(get("/", Header.Custom("hx-request", "yes")).header(HxRequest))
    show(catalogPage(get("/?category=Green", HxRequest(true))))
    val port = serve(app, args)
    openInBrowser(port)
  }
}
