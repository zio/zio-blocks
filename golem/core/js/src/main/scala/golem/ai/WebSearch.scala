package golem.ai

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

/**
 * Scala.js wrapper for `golem:web-search/web-search@1.0.0`.
 *
 * Public API intentionally avoids `scala.scalajs.js.*` types.
 */
object WebSearch {
  // ----- Public Scala model types -----------------------------------------------------------

  final case class SearchParams(
    query: String,
    language: Option[String] = None,
    safeSearch: Option[String] = None,
    maxResults: Option[Int] = None,
    advancedAnswer: Option[Boolean] = None
  )

  final case class SearchResult(url: String, title: String, snippet: String)

  final class Session private[golem] (private val underlying: SearchSession) {
    def nextPage(): List[SearchResult] =
      underlying
        .nextPage()
        .toList
        .map { item =>
          SearchResult(
            url = item.getOrElse("url", "").toString,
            title = item.getOrElse("title", "").toString,
            snippet = item.getOrElse("snippet", "").toString
          )
        }
  }

  // ----- Public API -----------------------------------------------------------------------

  def startSearch(params: SearchParams): Session = {
    val jsParams = js.Dictionary[js.Any](
      "query"          -> params.query,
      "language"       -> params.language.fold[js.Any](js.undefined)(identity),
      "safeSearch"     -> params.safeSearch.fold[js.Any](js.undefined)(identity),
      "maxResults"     -> params.maxResults.fold[js.Any](js.undefined)(identity),
      "advancedAnswer" -> params.advancedAnswer.fold[js.Any](js.undefined)(identity)
    )

    new Session(WebSearchModule.startSearch(jsParams))
  }

  // ----- Private interop ------------------------------------------------------------------

  @js.native
  private[golem] trait SearchSession extends js.Object {
    def nextPage(): js.Array[js.Dictionary[js.Any]] = js.native
  }

  @js.native
  @JSImport("golem:web-search/web-search@1.0.0", JSImport.Namespace)
  private object WebSearchModule extends js.Object {
    def startSearch(params: js.Dictionary[js.Any]): SearchSession = js.native
  }
}

