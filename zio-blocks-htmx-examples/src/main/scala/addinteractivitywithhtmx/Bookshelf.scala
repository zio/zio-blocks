package addinteractivitywithhtmx

import zio.blocks.html._
import zio.http._

/**
 * Add Interactivity to a Page with HTMX — the bookshelf model
 *
 * Eight Scala books, a search function, the HTML fragment that lists search
 * results, the htmx script tag and a helper that wraps rendered HTML in a
 * `text/html` response. Every step of the tutorial reuses these.
 */
final case class Book(title: String, author: String, category: String)

object Bookshelf {
  val books: List[Book] = List(
    Book("Zionomicon", "John A. De Goes and Adam Fraser", "Effects"),
    Book("Essential Effects", "Adam Rosien", "Effects"),
    Book("Practical FP in Scala", "Gabriel Volpe", "Effects"),
    Book("Functional Programming in Scala", "Paul Chiusano and Rúnar Bjarnason", "Functional Programming"),
    Book("Scala with Cats", "Noel Welsh and Dave Gurnell", "Functional Programming"),
    Book("Programming in Scala", "Martin Odersky, Lex Spoon and Bill Venners", "Language"),
    Book("Scala for the Impatient", "Cay S. Horstmann", "Language"),
    Book("Hands-on Scala Programming", "Li Haoyi", "Language")
  )

  val categories: List[String] = List("Effects", "Functional Programming", "Language")

  /**
   * Keeps the books whose title or author contains `query` (ignoring case) and
   * whose category is `category`. An empty query or category matches every
   * book.
   */
  def findBooks(query: String, category: String): List[Book] = {
    val needle = query.trim.toLowerCase
    books.filter { book =>
      val matchesText =
        needle.isEmpty || book.title.toLowerCase.contains(needle) || book.author.toLowerCase.contains(needle)
      val matchesCategory = category.isEmpty || book.category == category
      matchesText && matchesCategory
    }
  }

  /** The HTML fragment that lists the books, or says that nothing matched. */
  def resultList(found: List[Book]): Dom.Element =
    if (found.isEmpty) p("No books match your search.")
    else ul(found.map(book => li(strong(book.title), s" by ${book.author}")))

  /** Loads htmx 2.0.11 from the jsDelivr CDN. */
  val htmxScript: Dom.Element = script(src := "https://cdn.jsdelivr.net/npm/htmx.org@2.0.11/dist/htmx.min.js")

  /**
   * Wraps rendered HTML in a 200 OK response with `Content-Type: text/html`.
   */
  def htmlResponse(html: String): Response =
    Response.ok(Body.fromArray(html.getBytes("UTF-8"), ContentType.`text/html`))

  /** Answers 404 for any route the step does not handle. */
  def notFound(request: Request): Response =
    Response.text(Status.NotFound, s"No route for ${request.method.name} ${request.path.encode}")

  /**
   * Starts the server on the port given as the first argument (8080 by default)
   * and says where to open it. The server keeps running until the program is
   * stopped with Ctrl+C.
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
