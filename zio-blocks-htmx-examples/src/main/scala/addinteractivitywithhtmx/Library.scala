package addinteractivitywithhtmx

import zio.blocks.html._
import zio.http._
import zio.http.htmx.headers.{HxHistoryRestoreRequest, HxRequest}

/**
 * Add Interactivity to a Page with HTMX — the library model
 *
 * Seven Scala books, the filtering rule, the HTML fragment that lists books,
 * the page shell that loads htmx, and small helpers shared by every step.
 */
final case class Book(title: String, author: String, category: String)

object Library {
  val htmxScript: String = "https://cdn.jsdelivr.net/npm/htmx.org@2.0.4/dist/htmx.min.js"

  val books: List[Book] = List(
    Book("Zionomicon", "John A. De Goes and Adam Fraser", "Concurrency"),
    Book("Learning Concurrent Programming in Scala", "Aleksandar Prokopec", "Concurrency"),
    Book("Akka in Action", "Raymond Roestenburg, Rob Bakker and Rob Williams", "Concurrency"),
    Book("Functional Programming in Scala", "Paul Chiusano and Rúnar Bjarnason", "Functional"),
    Book("Scala with Cats", "Noel Welsh and Dave Gurnell", "Functional"),
    Book("Programming in Scala", "Martin Odersky, Lex Spoon and Bill Venners", "Language"),
    Book("Scala Cookbook", "Alvin Alexander", "Language")
  )

  val categories: List[String] = List("Concurrency", "Functional", "Language")

  /**
   * Books whose title or author contains `query`; an empty `category` means
   * all.
   */
  def matching(query: String, category: String): List[Book] = {
    val needle = query.trim.toLowerCase
    books.filter { book =>
      (needle.isEmpty || book.title.toLowerCase.contains(needle) || book.author.toLowerCase.contains(needle)) &&
      (category.isEmpty || book.category == category)
    }
  }

  /**
   * The fragment the server sends back: only the list, no `<html>` around it.
   */
  def bookList(found: List[Book]): Dom =
    if (found.isEmpty) p("No books found.")
    else ul(found.map(book => li(strong(book.title), s" by ${book.author} (${book.category})")))

  /** The full page around `content`, with the htmx script in the head. */
  def shell(content: Dom*): Dom.Element =
    html(lang := "en")(
      head(
        meta(charset := "utf-8"),
        title("Scala Bookshelf"),
        script(src := htmxScript)
      ),
      body(h1("Scala Bookshelf"), content)
    )

  def htmlResponse(dom: Dom): Response =
    Response.ok(Body.fromArray(dom.render.getBytes("UTF-8"), ContentType.`text/html`))

  def notFound(request: Request): Response =
    Response.text(Status.NotFound, s"No route for ${request.method.name} ${request.path.encode}")

  /**
   * Reads a query value. `QueryParams` in 0.0.56 keeps `+` as a plus sign, but
   * browsers and htmx write a space as `+`, so we turn it back into a space.
   */
  def queryValue(request: Request, name: String): String =
    request.queryParams.getFirst(name).fold("")(_.replace('+', ' '))

  /**
   * True when htmx wants a fragment: the request carries `HX-Request: true` and
   * is not htmx restoring a page from history, which needs the full page.
   */
  def wantsFragment(request: Request): Boolean =
    request.header(HxRequest).exists(_.enabled) && !request.header(HxHistoryRestoreRequest).exists(_.enabled)

  /**
   * Calls `app` with a GET for `target` and prints the status, headers of note
   * and body.
   */
  def call(app: Request => Response, target: String, headers: Headers = Headers.empty): Unit = {
    val request  = Request.get(URL.parse(target).toOption.get).addHeaders(headers)
    val response = app(request)
    val tag      = headers.toList.map { case (name, value) => s"$name: $value" }.mkString(", ")
    println(s"GET $target${if (tag.isEmpty) "" else s" [$tag]"} -> ${response.status.code}")
    println(response.body.asString())
    println()
  }
}
