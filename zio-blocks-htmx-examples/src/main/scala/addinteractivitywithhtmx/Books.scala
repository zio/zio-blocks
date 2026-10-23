package addinteractivitywithhtmx

/** A book in the sample catalog. */
final case class Book(title: String, authors: List[String], category: String)

/**
 * Add Interactivity to a Page with HTMX — the sample catalog
 *
 * Real Scala books, shared by every step of the tutorial.
 */
object Books {
  val all: List[Book] = List(
    Book("Zionomicon", List("John A. De Goes", "Adam Fraser", "Milad Khajavi"), "ZIO"),
    Book("Programming in Scala", List("Martin Odersky", "Lex Spoon", "Bill Venners"), "Language"),
    Book("Functional Programming in Scala", List("Paul Chiusano", "Rúnar Bjarnason"), "Functional programming"),
    Book("Scala with Cats", List("Noel Welsh", "Dave Gurnell"), "Functional programming"),
    Book("Essential Scala", List("Noel Welsh", "Dave Gurnell"), "Language")
  )

  /**
   * Books whose title contains `query` and whose category is `category`. An
   * empty value matches everything.
   */
  def search(query: String, category: String): List[Book] =
    all.filter { book =>
      book.title.toLowerCase.contains(query.trim.toLowerCase) &&
      (category.isEmpty || book.category == category)
    }
}
