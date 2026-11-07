package correlatelogsandtraces

import zio.http._

/**
 * How to Correlate Logs and Traces Across Services: the request the checkout
 * service sends to the payment service.
 */
object PaymentApi {
  val chargesUrl: URL = URL.parse("http://payment.internal/charges").toOption.get

  def chargeRequest(orderId: String, amountCents: Long): Request =
    Request.post(chargesUrl, Body.fromString(s"""{"orderId":"$orderId","amountCents":$amountCents}"""))
}
