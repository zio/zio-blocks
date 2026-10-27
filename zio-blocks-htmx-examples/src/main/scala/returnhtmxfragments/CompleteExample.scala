package returnhtmxfragments

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import zio.blocks.html._
import zio.blocks.schema.json.Json
import zio.http._
import zio.http.htmx._
import zio.http.htmx.headers._

/**
 * How to Return HTMX Fragments, Full Pages and Response Headers — Complete
 * Example
 *
 * The whole tea shop in one file: the catalog with category and sort links,
 * "Add to cart" with an out-of-band badge and trigger events, the cart page
 * with a promo code, checkout, and the address-bar updates. The shop lives in
 * its own object because an object that defines `main` would make the
 * `main(...)` element in the layout call that method.
 *
 * Run with: cd zio-blocks-htmx-examples && sbt "runMain
 * returnhtmxfragments.CompleteExample", then open http://localhost:8080/
 */
object CompleteShop {
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

  def addToCart(productId: String): Response =
    products.find(_.id == productId) match {
      case None          => Response.text(Status.NotFound, s"No product $productId")
      case Some(product) =>
        cart.add(product.id)
        val badge = cartBadge(hxSwapOob := HxSwapOob.True)
        htmlResponse(addButton(product).render + badge.render)
    }

  def addToCartAndAnnounce(productId: String): Response = {
    val response = addToCart(productId)
    products.find(_.id == productId).fold(response) { product =>
      val message = Json.Object("show-message" -> Json.Object("text" -> Json.String(s"Added ${product.name}")))
      response
        .addHeader(HxTriggerHeader(HxEventPayload.Event("cart-updated")))
        .addHeader(HxTriggerAfterSettle(HxEventPayload.JsonValue(message)))
    }
  }

  def cartSummaryFragment: Response = htmlResponse(cartSummary.render)

  val orderNumbers = new AtomicInteger(1000)

  def cartContent: Dom.Element =
    div(
      h1("Your Cart"),
      ul(products.filter(product => cart.quantity(product.id) > 0).map { product =>
        li(s"${cart.quantity(product.id)} x ${product.name}")
      }),
      p(id := "cart-total")(s"Total: ${price(cart.totalCents)}"),
      form(hxPost := "/cart/promo", hxTarget := HxTarget.css("#cart-total"), hxSwap := HxSwap.OuterHTML)(
        input(name := "code", placeholder := "Promo code"),
        button(`type` := "submit")("Apply"),
        span(id := "promo-error")("")
      ),
      button(hxPost := "/checkout")("Place order"),
      button(hxPost := "/cart/clear")("Empty cart")
    )

  def orderContent(number: Int): Dom.Element =
    div(h1(s"Order $number Placed"), p("Thank you! Your tea is on its way."), a(href := "/")("Keep shopping"))

  def checkout(): Response =
    if (cart.count == 0) htmlResponse("").addHeader(HxRedirect("/"))
    else {
      val number = orderNumbers.incrementAndGet()
      cart.clear()
      htmlResponse("")
        .addHeader(HxLocation(s"/orders/$number", target = Some(HxTarget.css("#main"))))
        .addHeader(HxTriggerHeader(HxEventPayload.Event("cart-updated")))
    }

  def clearCart(): Response = {
    cart.clear()
    htmlResponse("").addHeader(HxRefresh(true))
  }

  def orderPage(request: Request, number: Int): Response =
    pageOrFragment(request, s"Order $number")(
      div(orderContent(number), cartBadge(hxSwapOob := HxSwapOob.True)),
      orderContent(number)
    )

  def applyPromo(request: Request): Response = {
    val code = Form.fromString(request.body.asString()).get("code").getOrElse("").trim.toUpperCase
    if (code == "TEA10") {
      cart.applyPromo(code)
      val total      = p(id := "cart-total")(s"Total: ${price(cart.totalCents)} (TEA10 applied)")
      val clearError = span(id := "promo-error", hxSwapOob := HxSwapOob.True)("")
      htmlResponse(total.render + clearError.render).addHeader(HxTriggerHeader(HxEventPayload.Event("cart-updated")))
    } else
      htmlResponse(span(s"$code is not a valid promo code").render)
        .addHeader(HxRetarget(CssSelector.id("promo-error")))
        .addHeader(HxReswap(HxSwap.InnerHTML))
  }

  def catalogRoute(request: Request): Response = {
    val response = catalogPage(request)
    request.queryParams.getFirst("sort") match {
      case Some(sort) if wantsFragment(request) =>
        val category = request.queryParams.getFirst("category").getOrElse("")
        response.addHeader(HxReplaceUrl(HxUrlUpdate(catalogUrl(category, sort))))
      case _ => response
    }
  }

  def notFound(request: Request): Response =
    Response.text(Status.NotFound, s"No route for ${request.method.name} ${request.path.encode}")

  def app(request: Request): Response =
    (request.method, request.path.segments.toList) match {
      case (Method.GET, Nil)                                                    => catalogRoute(request)
      case (Method.POST, List("cart", "items", id))                             => addToCartAndAnnounce(id)
      case (Method.GET, List("cart", "summary"))                                => cartSummaryFragment
      case (Method.GET, List("cart"))                                           => pageOrFragment(request, "Cart")(cartContent, cartContent)
      case (Method.POST, List("cart", "promo"))                                 => applyPromo(request)
      case (Method.POST, List("checkout"))                                      => checkout()
      case (Method.POST, List("cart", "clear"))                                 => clearCart()
      case (Method.GET, List("orders", number)) if number.toIntOption.isDefined =>
        orderPage(request, number.toInt)
      case _ => notFound(request)
    }
}

object CompleteExample {
  def main(args: Array[String]): Unit = {
    val port   = args.headOption.flatMap(_.toIntOption).getOrElse(8080)
    val server = JdkHttp.start(CompleteShop.app, port)
    sys.addShutdownHook(server.stop(0))
    println(s"Open http://localhost:$port/ in a browser. Press Ctrl+C to stop.")
  }
}
