package returnhtmxfragments

import zio.blocks.html._
import zio.http._
import zio.http.htmx._
import zio.http.htmx.headers._

import TeaShop._

/**
 * How to Return HTMX Fragments, Full Pages and Response Headers — Send
 * Validation Errors Somewhere Else
 *
 * A wrong promo code is sent to `#promo-error` with `HX-Retarget` and
 * `HX-Reswap`; a right one updates the total.
 *
 * Run with: cd zio-blocks-htmx-examples && sbt "runMain
 * returnhtmxfragments.Step5ValidationErrorsExample"
 */
object Step5ValidationErrorsExample {
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

  def app(request: Request): Response =
    (request.method, request.path.segments.toList) match {
      case (Method.GET, Nil)                        => catalogPage(request)
      case (Method.POST, List("cart", "items", id)) => Step3TriggerEventsExample.addToCartAndAnnounce(id)
      case (Method.GET, List("cart", "summary"))    => Step3TriggerEventsExample.cartSummaryFragment
      case (Method.GET, List("cart"))               =>
        pageOrFragment(request, "Cart")(
          Step4RedirectAfterPostExample.cartContent,
          Step4RedirectAfterPostExample.cartContent
        )
      case (Method.POST, List("cart", "promo"))                                 => applyPromo(request)
      case (Method.POST, List("checkout"))                                      => Step4RedirectAfterPostExample.checkout()
      case (Method.POST, List("cart", "clear"))                                 => Step4RedirectAfterPostExample.clearCart()
      case (Method.GET, List("orders", number)) if number.toIntOption.isDefined =>
        Step4RedirectAfterPostExample.orderPage(request, number.toInt)
      case _ => notFound(request)
    }

  def main(args: Array[String]): Unit = {
    // Setup from sections 2 to 4: the order is placed and the cart is empty again
    cart.add("matcha")
    show(applyPromo(post("/cart/promo", Form("code" -> "TEA5"), HxRequest(true))))
    show(applyPromo(post("/cart/promo", Form("code" -> "tea10"), HxRequest(true))))
    val port = serve(app, args)
    openInBrowser(port)
  }
}
