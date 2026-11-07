package protectpageswithbasicauthorasessioncookie

import java.nio.charset.StandardCharsets.UTF_8
import zio.blocks.html._
import zio.http._

final case class Order(id: Int, customer: String, coffee: String, grams: Int)

/**
 * How to Protect Pages with Basic Auth or a Session Cookie — The Roastery Admin
 * Area
 *
 * The roastery's orders and pages, shared by every later file. Nothing here
 * knows about authentication.
 */
object Roastery {
  val openOrders: List[Order] = List(
    Order(101, "Lena", "Ethiopia Guji", 250),
    Order(102, "Marco", "Colombia Huila", 1000),
    Order(103, "Priya", "Kenya Nyeri", 500)
  )

  def htmlResponse(page: Dom.Element): Response =
    Response.ok(Body.fromArray((doctype.render + page.render(2)).getBytes(UTF_8), ContentType.`text/html`))

  def page(heading: String, content: Dom.Element*): Response =
    htmlResponse(
      html(lang := "en")(
        head(meta(charset := "utf-8"), title(heading)),
        body(h1(heading), content)
      )
    )

  val home: Response = page("Fresh Roasts Every Tuesday", a(href := "/admin")("Staff area"))

  // `note` says who is signed in; the session sections put a sign-out button in it.
  def ordersPage(note: Dom.Element): Response =
    page("Open Orders", ul(openOrders.map(o => li(s"#${o.id} ${o.customer}: ${o.coffee}, ${o.grams} g"))), note)

  val ordersCsv: Response =
    Response
      .text(
        ("id,customer,coffee,grams" :: openOrders.map(o => s"${o.id},${o.customer},${o.coffee},${o.grams}"))
          .mkString("\n")
      )
      .setHeader("content-type", "text/csv; charset=utf-8")

  def notFound(request: Request): Response =
    Response.text(Status.NotFound, s"No route for ${request.method.name} ${request.path.encode}")
}
