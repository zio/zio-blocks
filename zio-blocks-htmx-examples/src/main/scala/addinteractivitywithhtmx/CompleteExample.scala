package addinteractivitywithhtmx

import scala.concurrent.duration._
import zio.blocks.html._
import zio.http._
import zio.http.htmx._
import zio.http.htmx.headers._

/**
 * Add Interactivity to a Page with HTMX — Complete Example
 *
 * A book search page served by the JDK HTTP server. The search box updates the
 * results 300 milliseconds after the reader stops typing, the category
 * drop-down filters them, and each search is pushed into the address bar. The
 * same `/search` URL answers an htmx request with the result list only and any
 * other request with the full page.
 *
 * Run with: cd zio-blocks-htmx-examples && sbt "runMain
 * addinteractivitywithhtmx.CompleteExample", then open http://localhost:8080/
 */
object CompleteExample {
  final case class Book(title: String, author: String, category: String)

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

  def findBooks(query: String, category: String): List[Book] = {
    val needle = query.trim.toLowerCase
    books.filter { book =>
      val matchesText =
        needle.isEmpty || book.title.toLowerCase.contains(needle) || book.author.toLowerCase.contains(needle)
      val matchesCategory = category.isEmpty || book.category == category
      matchesText && matchesCategory
    }
  }

  // The fragment htmx swaps into #results.
  def resultList(found: List[Book]): Dom.Element =
    if (found.isEmpty) p("No books match your search.")
    else ul(found.map(book => li(strong(book.title), s" by ${book.author}")))

  // Both controls send GET /search with every field inside #filters, swap the
  // answer into #results and push the request URL into the address bar.
  def searchBox(query: String): Dom.Element.Void =
    input(
      `type`      := "search",
      name        := "q",
      value       := query,
      placeholder := "Search by title or author",
      hxGet       := "/search",
      hxTrigger   := HxTrigger("keyup").changed.delay(300.millis),
      hxTarget    := HxTarget.css("#results"),
      hxInclude   := HxTarget.css("#filters"),
      hxPushUrl   := HxUrlUpdate(true)
    )

  def categoryFilter(current: String): Dom.Element =
    select(
      name      := "category",
      hxGet     := "/search",
      hxTarget  := HxTarget.css("#results"),
      hxInclude := HxTarget.css("#filters"),
      hxPushUrl := HxUrlUpdate(true)
    )(
      option(value := "")("All categories") ::
        categories.map(category => option(value := category)(category).when(category == current)(selected))
    )

  def page(query: String, category: String): Dom.Element =
    html(lang := "en")(
      head(
        meta(charset := "utf-8"),
        title("Scala Bookshelf"),
        script(src := "https://cdn.jsdelivr.net/npm/htmx.org@2.0.11/dist/htmx.min.js")
      ),
      body(
        h1("Scala Bookshelf"),
        div(id := "filters", searchBox(query), categoryFilter(category)),
        div(id := "results", resultList(findBooks(query, category)))
      )
    )

  // htmx 2 also sends HX-Request on a history cache miss, and then it needs the whole page.
  def wantsFragment(request: Request): Boolean =
    request.header(HxRequest).exists(_.enabled) &&
      !request.header(HxHistoryRestoreRequest).exists(_.enabled)

  def htmlResponse(html: String): Response =
    Response.ok(Body.fromArray(html.getBytes("UTF-8"), ContentType.`text/html`))

  def searchPage(request: Request): Response = {
    val query    = request.queryParams.getFirst("q").getOrElse("")
    val category = request.queryParams.getFirst("category").getOrElse("")
    val html     =
      if (wantsFragment(request)) resultList(findBooks(query, category)).render
      else doctype.render + page(query, category).render
    htmlResponse(html).addHeader("vary", "hx-request")
  }

  def app(request: Request): Response =
    (request.method, request.path.segments.toList) match {
      case (Method.GET, Nil) | (Method.GET, List("search")) => searchPage(request)
      case _                                                =>
        Response.text(Status.NotFound, s"No route for ${request.method.name} ${request.path.encode}")
    }

  def main(args: Array[String]): Unit = {
    val port   = args.headOption.flatMap(_.toIntOption).getOrElse(8080)
    val server = JdkHttp.start(app, port)
    sys.addShutdownHook(server.stop(0))
    println(s"Open http://localhost:$port/ in a browser. Press Ctrl+C to stop.")
  }
}
