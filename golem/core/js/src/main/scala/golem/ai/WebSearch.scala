package golem.ai

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

/**
 * Scala.js wrapper for `golem:web-search/web-search@1.0.0`.
 *
 * Public API avoids `scala.scalajs.js.*` types.
 */
object WebSearch {
  // ----- Public Scala model types -----------------------------------------------------------

  sealed trait SafeSearchLevel extends Product with Serializable { def tag: String }
  object SafeSearchLevel {
    case object Off    extends SafeSearchLevel { val tag: String = "off"    }
    case object Medium extends SafeSearchLevel { val tag: String = "medium" }
    case object High   extends SafeSearchLevel { val tag: String = "high"   }

    def fromTag(tag: String): SafeSearchLevel =
      tag match {
        case "off"    => Off
        case "medium" => Medium
        case "high"   => High
        case _        => Off
      }
  }

  sealed trait TimeRange extends Product with Serializable { def tag: String }
  object TimeRange {
    case object Day   extends TimeRange { val tag: String = "day"   }
    case object Week  extends TimeRange { val tag: String = "week"  }
    case object Month extends TimeRange { val tag: String = "month" }
    case object Year  extends TimeRange { val tag: String = "year"  }

    def fromTag(tag: String): TimeRange =
      tag match {
        case "day"   => Day
        case "week"  => Week
        case "month" => Month
        case "year"  => Year
        case _       => Day
      }
  }

  sealed trait SearchError extends Product with Serializable
  object SearchError {
    case object InvalidQuery                             extends SearchError
    final case class RateLimited(limit: Int)             extends SearchError
    final case class UnsupportedFeature(message: String) extends SearchError
    final case class BackendError(message: String)       extends SearchError
  }

  final case class ImageResult(url: String, description: Option[String])

  final case class SearchResult(
    title: String,
    url: String,
    snippet: String,
    displayUrl: Option[String],
    source: Option[String],
    score: Option[Double],
    htmlSnippet: Option[String],
    datePublished: Option[String],
    images: Option[List[ImageResult]],
    contentChunks: Option[List[String]]
  )

  final case class RateLimitInfo(limit: Int, remaining: Int, resetTimestamp: BigInt)

  final case class SearchMetadata(
    query: String,
    totalResults: Option[BigInt],
    searchTimeMs: Option[Double],
    safeSearch: Option[SafeSearchLevel],
    language: Option[String],
    region: Option[String],
    nextPageToken: Option[String],
    rateLimits: Option[RateLimitInfo],
    currentPage: Int
  )

  final case class SearchParams(
    query: String,
    safeSearch: Option[SafeSearchLevel] = None,
    language: Option[String] = None,
    region: Option[String] = None,
    maxResults: Option[Int] = None,
    timeRange: Option[TimeRange] = None,
    includeDomains: Option[List[String]] = None,
    excludeDomains: Option[List[String]] = None,
    includeImages: Option[Boolean] = None,
    includeHtml: Option[Boolean] = None,
    advancedAnswer: Option[Boolean] = None
  )

  final class Session private[golem] (private val underlying: js.Dynamic) {
    def nextPageResult(): Either[SearchError, List[SearchResult]] =
      decodeResult(callSession("nextPage", "next-page"))(fromJsResults, fromJsError)

    def nextPage(): List[SearchResult] =
      nextPageResult() match {
        case Right(value) => value
        case Left(err)    => throw new IllegalStateException(renderError(err))
      }

    def metadata(): Option[SearchMetadata] =
      optionDynamic(callSession("getMetadata", "get-metadata")).map(fromJsMetadata)

    private def callSession(primary: String, secondary: String): js.Dynamic = {
      val fn =
        if (!js.isUndefined(underlying.selectDynamic(primary))) underlying.selectDynamic(primary)
        else underlying.selectDynamic(secondary)
      fn.call(underlying).asInstanceOf[js.Dynamic]
    }
  }

  // ----- Public API -----------------------------------------------------------------------

  def startSearchResult(params: SearchParams): Either[SearchError, Session] = {
    val raw = WebSearchModule.startSearch(toJsParams(params))
    decodeResult(raw)(session => new Session(session.asInstanceOf[js.Dynamic]), fromJsError)
  }

  def startSearch(params: SearchParams): Session =
    startSearchResult(params) match {
      case Right(value) => value
      case Left(err)    => throw new IllegalStateException(renderError(err))
    }

  def searchOnceResult(params: SearchParams): Either[SearchError, (List[SearchResult], Option[SearchMetadata])] = {
    val raw = WebSearchModule.searchOnce(toJsParams(params))
    decodeResult(raw)(fromJsSearchOnce, fromJsError)
  }

  def searchOnce(params: SearchParams): (List[SearchResult], Option[SearchMetadata]) =
    searchOnceResult(params) match {
      case Right(value) => value
      case Left(err)    => throw new IllegalStateException(renderError(err))
    }

  // ----- Private interop ------------------------------------------------------------------

  private type JObj = js.Dictionary[js.Any]

  private def toJsParams(params: SearchParams): JObj =
    js.Dictionary[js.Any](
      "query"           -> params.query,
      "safe-search"     -> params.safeSearch.fold[js.Any](js.undefined)(_.tag),
      "language"        -> params.language.fold[js.Any](js.undefined)(identity),
      "region"          -> params.region.fold[js.Any](js.undefined)(identity),
      "max-results"     -> params.maxResults.fold[js.Any](js.undefined)(identity),
      "time-range"      -> params.timeRange.fold[js.Any](js.undefined)(_.tag),
      "include-domains" -> params.includeDomains.fold[js.Any](js.undefined)(xs => js.Array(xs: _*)),
      "exclude-domains" -> params.excludeDomains.fold[js.Any](js.undefined)(xs => js.Array(xs: _*)),
      "include-images"  -> params.includeImages.fold[js.Any](js.undefined)(identity),
      "include-html"    -> params.includeHtml.fold[js.Any](js.undefined)(identity),
      "advanced-answer" -> params.advancedAnswer.fold[js.Any](js.undefined)(identity)
    )

  private def fromJsResults(value: js.Dynamic): List[SearchResult] =
    value.asInstanceOf[js.Array[js.Dynamic]].toList.map(fromJsResult)

  private def fromJsResult(value: js.Dynamic): SearchResult = {
    val obj = value.asInstanceOf[JObj]
    SearchResult(
      title = obj.getOrElse("title", "").toString,
      url = obj.getOrElse("url", "").toString,
      snippet = obj.getOrElse("snippet", "").toString,
      displayUrl = obj.get("display-url").map(_.toString),
      source = obj.get("source").map(_.toString),
      score = obj.get("score").map(_.toString.toDouble),
      htmlSnippet = obj.get("html-snippet").map(_.toString),
      datePublished = obj.get("date-published").map(_.toString),
      images = obj.get("images").map(asArray).map(_.toList.map(fromJsImageResult)),
      contentChunks = obj.get("content-chunks").map(asArray).map(_.toList.map(_.toString))
    )
  }

  private def fromJsImageResult(value: js.Dynamic): ImageResult = {
    val obj = value.asInstanceOf[JObj]
    ImageResult(
      url = obj.getOrElse("url", "").toString,
      description = obj.get("description").map(_.toString)
    )
  }

  private def fromJsMetadata(value: js.Dynamic): SearchMetadata = {
    val obj = value.asInstanceOf[JObj]
    SearchMetadata(
      query = obj.getOrElse("query", "").toString,
      totalResults = optionDynamic(obj.getOrElse("total-results", null)).map(toBigInt),
      searchTimeMs = optionDynamic(obj.getOrElse("search-time-ms", null)).map(_.toString.toDouble),
      safeSearch = optionDynamic(obj.getOrElse("safe-search", null)).map(_.toString).map(SafeSearchLevel.fromTag),
      language = optionDynamic(obj.getOrElse("language", null)).map(_.toString),
      region = optionDynamic(obj.getOrElse("region", null)).map(_.toString),
      nextPageToken = optionDynamic(obj.getOrElse("next-page-token", null)).map(_.toString),
      rateLimits = optionDynamic(obj.getOrElse("rate-limits", null)).map(fromJsRateLimit),
      currentPage = obj.getOrElse("current-page", 0).toString.toInt
    )
  }

  private def fromJsRateLimit(value: js.Dynamic): RateLimitInfo = {
    val obj = value.asInstanceOf[JObj]
    RateLimitInfo(
      limit = obj.getOrElse("limit", 0).toString.toInt,
      remaining = obj.getOrElse("remaining", 0).toString.toInt,
      resetTimestamp = toBigInt(obj.getOrElse("reset-timestamp", 0))
    )
  }

  private def fromJsSearchOnce(value: js.Dynamic): (List[SearchResult], Option[SearchMetadata]) = {
    val tuple   = value.asInstanceOf[js.Array[js.Dynamic]]
    val results = tuple.headOption.map(fromJsResults).getOrElse(Nil)
    val meta    = tuple.lift(1).flatMap(optionDynamic).map(fromJsMetadata)
    (results, meta)
  }

  private def fromJsError(value: js.Dynamic): SearchError = {
    val tag = tagOf(value)
    tag match {
      case "invalid-query"       => SearchError.InvalidQuery
      case "rate-limited"        => SearchError.RateLimited(toInt(valOf(value)))
      case "unsupported-feature" => SearchError.UnsupportedFeature(String.valueOf(valOf(value)))
      case "backend-error"       => SearchError.BackendError(String.valueOf(valOf(value)))
      case _                     => SearchError.BackendError(String.valueOf(valOf(value)))
    }
  }

  private def decodeResult[A](
    raw: js.Dynamic
  )(ok: js.Dynamic => A, err: js.Dynamic => SearchError): Either[SearchError, A] = {
    val tag = tagOf(raw)
    tag match {
      case "ok" | "success" => Right(ok(valOf(raw)))
      case "err" | "error"  => Left(err(valOf(raw)))
      case _                => Right(ok(raw))
    }
  }

  private def tagOf(value: js.Dynamic): String = {
    val tag = value.asInstanceOf[js.Dynamic].selectDynamic("tag")
    if (js.isUndefined(tag) || tag == null) "" else tag.toString
  }

  private def valOf(value: js.Dynamic): js.Dynamic = {
    val dyn = value.asInstanceOf[js.Dynamic]
    if (!js.isUndefined(dyn.selectDynamic("val"))) dyn.selectDynamic("val").asInstanceOf[js.Dynamic]
    else if (!js.isUndefined(dyn.selectDynamic("value"))) dyn.selectDynamic("value").asInstanceOf[js.Dynamic]
    else dyn
  }

  private def optionDynamic(value: js.Any): Option[js.Dynamic] =
    if (value == null || js.isUndefined(value)) None
    else {
      val dyn = value.asInstanceOf[js.Dynamic]
      val tag = dyn.selectDynamic("tag")
      if (!js.isUndefined(tag) && tag != null) {
        tag.toString match {
          case "some" => Some(dyn.selectDynamic("val").asInstanceOf[js.Dynamic])
          case "none" => None
          case _      => Some(dyn)
        }
      } else Some(dyn)
    }

  private def asArray(value: js.Any): js.Array[js.Dynamic] =
    value.asInstanceOf[js.Array[js.Dynamic]]

  private def toInt(value: js.Dynamic): Int =
    value.toString.toInt

  private def toBigInt(value: js.Any): BigInt =
    BigInt(value.toString)

  private def renderError(err: SearchError): String =
    err match {
      case SearchError.InvalidQuery            => "Invalid query"
      case SearchError.RateLimited(limit)      => s"Rate limited (limit=$limit)"
      case SearchError.UnsupportedFeature(msg) => s"Unsupported feature: $msg"
      case SearchError.BackendError(msg)       => s"Backend error: $msg"
    }

  @js.native
  @JSImport("golem:web-search/web-search@1.0.0", JSImport.Namespace)
  private object WebSearchModule extends js.Object {
    def startSearch(params: js.Dictionary[js.Any]): js.Dynamic = js.native
    def searchOnce(params: js.Dictionary[js.Any]): js.Dynamic  = js.native
  }
}
