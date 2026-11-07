package returnhtmxfragments

import java.util.concurrent.atomic.AtomicInteger
import zio.blocks.html._
import zio.http._
import zio.http.htmx._
import zio.http.htmx.headers._

import TeaShop._

/**
 * How to Return HTMX Fragments, Full Pages and Response Headers — Redirect,
 * Navigate or Refresh After a Form Post
 *
 * Checkout sends `HX-Location` (or `HX-Redirect` for an empty cart), and "Empty
 * cart" sends `HX-Refresh`.
 *
 * Run with: cd zio-blocks-htmx-examples && sbt "runMain
 * returnhtmxfragments.Step4RedirectAfterPostExample"
 */
object Step4RedirectAfterPostExample {
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

  def app(request: Request): Response =
    (request.method, request.path.segments.toList) match {
      case (Method.GET, Nil)                                                    => catalogPage(request)
      case (Method.POST, List("cart", "items", id))                             => Step3TriggerEventsExample.addToCartAndAnnounce(id)
      case (Method.GET, List("cart", "summary"))                                => Step3TriggerEventsExample.cartSummaryFragment
      case (Method.GET, List("cart"))                                           => pageOrFragment(request, "Cart")(cartContent, cartContent)
      case (Method.POST, List("checkout"))                                      => checkout()
      case (Method.POST, List("cart", "clear"))                                 => clearCart()
      case (Method.GET, List("orders", number)) if number.toIntOption.isDefined =>
        orderPage(request, number.toInt)
      case _ => notFound(request)
    }

  def main(args: Array[String]): Unit = {
    // Setup from sections 2 and 3: a Sencha and an Assam are in the cart
    Step2OutOfBandSwapExample.addToCart("sencha")
    Step2OutOfBandSwapExample.addToCart("assam")
    show(checkout())
    show(orderPage(get("/orders/1001", HxRequest(true), HxTargetId("main")), 1001))
    show(checkout())
    val port = serve(app, args)
    openInBrowser(port)
  }
}
