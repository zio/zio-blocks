package smalljsonbackendfromblocks

/**
 * Build a Small JSON Backend from Blocks — Model the Books
 *
 * Encodes a book to JSON with the codec derived from its schema, decodes a
 * request body, and shows the error for a body with a missing field.
 *
 * Run with: cd capstone-examples && sbt "runMain
 * smalljsonbackendfromblocks.Concept1ModelExample"
 */
object Concept1ModelExample {
  def main(args: Array[String]): Unit = {
    val zionomicon = Book(1, "Zionomicon", "John A. De Goes", 2024, None, 3, 4500)
    println(Json.book.encodeToString(zionomicon))

    val body = """{"title":"Scala with Cats","firstAuthor":"Noel Welsh","year":2017,"stock":2,"priceCents":3500}"""
    println(Json.newBook.decode(body))

    Json.newBook.decode("""{"title":"Scala with Cats"}""") match {
      case Right(book) => println(book)
      case Left(error) => println(error.message)
    }
  }
}
