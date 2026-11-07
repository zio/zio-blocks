package protectendpointswithjwt

import zio.http._

/**
 * How to Protect an Endpoint with JWT Bearer Tokens — the bookstore model
 *
 * Three Scala books, the orders of two fictional users, and the plain responses
 * that the bookstore API sends. Nothing here knows about authentication.
 */
final case class Book(id: Int, title: String, priceCents: Long)

object Bookstore {
  val books: List[Book] = List(
    Book(1, "Zionomicon", 4500),
    Book(2, "Programming in Scala", 5999),
    Book(3, "Functional Programming in Scala", 4999)
  )

  // Orders per user, keyed by the `sub` claim of their token.
  val orders: Map[String, List[String]] = Map(
    "reader-1024" -> List("Zionomicon", "Functional Programming in Scala"),
    "staff-0007"  -> List("Programming in Scala")
  )

  def price(cents: Long): String = f"$$${cents / 100}%d.${cents % 100}%02d"

  def listBooks: Response =
    Response.text(books.map(book => s"${book.id}. ${book.title} ${price(book.priceCents)}").mkString("\n"))

  def ordersOf(user: String): Response =
    Response.text(s"Orders of $user: ${orders.getOrElse(user, Nil).mkString(", ")}")

  // Answers with the new price; a real store would also save it.
  def setPrice(id: String, body: String): Response =
    (books.find(_.id.toString == id), body.trim.toLongOption.filter(_ > 0)) match {
      case (None, _)                 => Response.text(Status.NotFound, s"No book with id $id")
      case (_, None)                 => Response.text(Status.BadRequest, "The body must be a price in cents")
      case (Some(book), Some(cents)) => Response.text(s"${book.title} now costs ${price(cents)}")
    }

  def notFound(request: Request): Response =
    Response.text(Status.NotFound, s"No route for ${request.method.name} ${request.path.encode}")
}
