package deriveyourowntypeclass

import zio.blocks.schema._

sealed trait Format
object Format {
  case object Hardcover                  extends Format
  case object Paperback                  extends Format
  final case class Ebook(sizeMb: Double) extends Format

  implicit val schema: Schema[Format] = Schema.derived[Format]
}

final case class Book(title: String, firstAuthor: String, year: Int, isbn: Option[String], format: Format)
object Book {
  implicit val schema: Schema[Book] = Schema.derived[Book]
}

final case class Email(value: String)
object Email {
  implicit val schema: Schema[Email] = Schema[String].transform(Email(_), _.value)
}

final case class Customer(name: String, email: Email)
object Customer {
  implicit val schema: Schema[Customer] = Schema.derived[Customer]
}

final case class Order(
  id: Long,
  customer: Customer,
  books: List[Book],
  @Modifier.config("log.redact", "true") paymentToken: String
)
object Order {
  implicit val schema: Schema[Order] = Schema.derived[Order]
}

object Bookstore {
  val zionomicon         = Book("Zionomicon", "John A. De Goes", 2024, None, Format.Ebook(12.5))
  val programmingInScala = Book("Programming in Scala", "Martin Odersky", 2008, Some("9780981531601"), Format.Hardcover)
  val fpInScala          =
    Book("Functional Programming in Scala", "Paul Chiusano", 2014, Some("9781617290657"), Format.Paperback)

  val order = Order(1001L, Customer("Ada", Email("ada@example.com")), List(zionomicon), "tok_4242")
}
