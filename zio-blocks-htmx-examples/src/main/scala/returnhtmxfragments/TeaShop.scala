package returnhtmxfragments

import java.util.concurrent.atomic.AtomicReference
import zio.blocks.html._
import zio.blocks.schema.json.{Json, WriterConfig}
import zio.http._
import zio.http.htmx._
import zio.http.htmx.headers._

/**
 * How to Return HTMX Fragments, Full Pages and Response Headers — the tea shop
 *
 * Five teas, one in-memory cart, the page layout with its cart badge and
 * summary, the full-page-or-fragment check, and the helpers that print a
 * response and start the server. Every step of the guide reuses these.
 */
object TeaShop {
  final case class Product(id: String, name: String, category: String, priceCents: Int)

  val products: List[Product] = List(
    Product("sencha", "Sencha", "Green", 1200),
    Product("matcha", "Matcha", "Green", 2400),
    Product("assam", "Assam", "Black", 900),
    Product("earl-grey", "Earl Grey", "Black", 1000),
    Product("rooibos", "Rooibos", "Herbal", 800)
  )

  val categories: List[String] = List("Green", "Black", "Herbal")

  def price(cents: Int): String = "$" + (cents / 100) + "." + f"${cents % 100}%02d"

  final class Cart {
    private val lines = new AtomicReference(Map.empty[String, Int])
    private val promo = new AtomicReference(Option.empty[String])

    def add(productId: String): Int =
      lines.updateAndGet(current => current.updated(productId, current.getOrElse(productId, 0) + 1))(productId)
    def quantity(productId: String): Int = lines.get.getOrElse(productId, 0)
    def count: Int                       = lines.get.values.sum
    def applyPromo(code: String): Unit   = promo.set(Some(code))
    def totalCents: Int                  = {
      val gross = lines.get.map { case (id, n) => products.find(_.id == id).fold(0)(_.priceCents) * n }.sum
      if (promo.get.contains("TEA10")) gross * 9 / 10 else gross
    }
    def clear(): Unit = { lines.set(Map.empty); promo.set(None) }
  }

  val cart = new Cart

  val htmxScript: Dom.Element = script(src := "https://cdn.jsdelivr.net/npm/htmx.org@2.0.11/dist/htmx.min.js")

  def catalogUrl(category: String, sort: String): String = {
    val params = List("category" -> category, "sort" -> sort).filter { case (k, v) =>
      v.nonEmpty && !(k == "sort" && v == "name")
    }
    if (params.isEmpty) "/" else params.map { case (k, v) => s"$k=$v" }.mkString("/?", "&", "")
  }

  def addButton(product: Product): Dom.Element = {
    val quantity = cart.quantity(product.id)
    button(
      id       := s"add-${product.id}",
      hxPost   := s"/cart/items/${product.id}",
      hxTarget := HxTarget.this_,
      hxSwap   := HxSwap.OuterHTML
    )(if (quantity == 0) "Add to cart" else s"In cart ($quantity)")
  }

  def catalog(category: String, sort: String): Dom.Element = {
    val shown  = products.filter(product => category.isEmpty || product.category == category)
    val sorted = if (sort == "price") shown.sortBy(_.priceCents) else shown.sortBy(_.name)
    div(id := "catalog")(
      p(
        "Sort by ",
        a(hxGet := catalogUrl(category, "name"), hxTarget := HxTarget.css("#catalog"), hxSwap := HxSwap.OuterHTML)(
          "name"
        ),
        " | ",
        a(hxGet := catalogUrl(category, "price"), hxTarget := HxTarget.css("#catalog"), hxSwap := HxSwap.OuterHTML)(
          "price"
        )
      ),
      ul(sorted.map(product => li(strong(product.name), s" ${price(product.priceCents)} ", addButton(product))))
    )
  }

  def categoryLinks: Dom.Element =
    p(
      ("" :: categories).map { category =>
        a(
          href      := catalogUrl(category, ""),
          hxGet     := catalogUrl(category, ""),
          hxTarget  := HxTarget.css("#catalog"),
          hxSwap    := HxSwap.OuterHTML,
          hxPushUrl := HxUrlUpdate(true)
        )(if (category.isEmpty) "All teas " else s"$category ")
      }
    )

  def catalogContent(category: String, sort: String): Dom.Element =
    div(h1("Teas"), categoryLinks, catalog(category, sort))

  def cartBadge: Dom.Element = span(id := "cart-count")(cart.count.toString)

  def cartSummary: Dom.Element =
    aside(
      id        := "cart-summary",
      hxGet     := "/cart/summary",
      hxTrigger := HxTrigger("cart-updated").from("body"),
      hxSwap    := HxSwap.OuterHTML
    )(s"${cart.count} items, ${price(cart.totalCents)}")

  def layout(heading: String, content: Dom.Element): Dom.Element =
    html(lang := "en")(
      head(meta(charset := "utf-8"), title(s"$heading - Tea Shop"), htmxScript),
      body(hxOn("show-message") := Js("document.getElementById('message').textContent = event.detail.text"))(
        header(nav(hxBoost := true)(a(href := "/")("Teas"), " ", a(href := "/cart")("Cart (", cartBadge, ")"))),
        p(id := "message")(""),
        main(id := "main")(content),
        cartSummary
      )
    )

  def htmlResponse(html: String): Response =
    Response.ok(Body.fromArray(html.getBytes("UTF-8"), ContentType.`text/html`))

  def get(target: String, headers: Header*): Request =
    headers.foldLeft(Request.get(URL.parse(target).toOption.get))((request, header) => request.addHeader(header))

  def post(target: String, form: Form, headers: Header*): Request =
    headers.foldLeft(Request.post(URL.parse(target).toOption.get, Body.fromString(form.encode)))((request, header) =>
      request.addHeader(header)
    )

  def show(response: Response): Unit = {
    println(s"status: ${response.status.code}")
    response.headers.toList.foreach { case (name, value) =>
      if (name.startsWith("hx-") || name == "vary") println(s"$name: $value")
    }
    println(response.body.asString())
  }

  def wantsFragment(request: Request): Boolean =
    request.header(HxRequest).exists(_.enabled) &&
      !request.header(HxBoosted).exists(_.enabled) &&
      !request.header(HxHistoryRestoreRequest).exists(_.enabled)

  def pageOrFragment(request: Request, heading: String)(fragment: => Dom.Element, content: => Dom.Element): Response = {
    val html =
      if (wantsFragment(request)) fragment.render
      else doctype.render + layout(heading, content).render
    htmlResponse(html).addHeader("vary", "hx-request, hx-boosted, hx-history-restore-request")
  }

  def catalogPage(request: Request): Response = {
    val category = request.queryParams.getFirst("category").getOrElse("")
    val sort     = request.queryParams.getFirst("sort").getOrElse("name")
    pageOrFragment(request, "Teas")(catalog(category, sort), catalogContent(category, sort))
  }

  /** Answers 404 for any route the step does not handle. */
  def notFound(request: Request): Response =
    Response.text(Status.NotFound, s"No route for ${request.method.name} ${request.path.encode}")

  /**
   * Starts the server on the port given as the first argument (8080 by default)
   * and returns the port. The server keeps running until the program is stopped
   * with Ctrl+C.
   */
  def serve(app: Request => Response, args: Array[String]): Int = {
    val port   = args.headOption.flatMap(_.toIntOption).getOrElse(8080)
    val server = JdkHttp.start(app, port)
    sys.addShutdownHook(server.stop(0))
    port
  }

  def openInBrowser(port: Int): Unit =
    println(s"Open http://localhost:$port/ in a browser. Press Ctrl+C to stop.")
}
