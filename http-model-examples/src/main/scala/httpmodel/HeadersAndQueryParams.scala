package httpmodel

import zio.http._

/**
 * HTTP Model — Headers and Query Parameters
 *
 * Demonstrates working with headers and query parameters in URLs and requests.
 * Shows how Headers, QueryParams, and URL types work together for data extraction.
 *
 * Run with: sbt "http-model-examples/runMain httpmodel.HeadersAndQueryParams"
 */
object HeadersAndQueryParams extends App {

  // Create URL with query parameters
  val url = URL.parse("https://api.example.com/search?q=zio&limit=10&sort=date").toOption.get
  println(s"URL: $url")

  val params = url.queryParams
  println(s"Query Parameters:")
  println(s"  q: ${params.get("q")}")
  println(s"  limit: ${params.get("limit")}")
  println(s"  sort: ${params.get("sort")}")
  println()

  // Create request with headers
  val request = Request.get(url)
    .addHeader("user-agent", "MyApp/1.0")
    .addHeader("accept", "application/json")
    .addHeader("authorization", "Bearer secret-token")

  val headers = request.headers
  println(s"Request Headers:")
  println(s"  user-agent: ${headers.toList.find(_._1.equalsIgnoreCase("user-agent")).map(_._2)}")
  println(s"  accept: ${headers.toList.find(_._1.equalsIgnoreCase("accept")).map(_._2)}")
  println(s"  Total headers: ${headers.toList.length}")
  println()

  // Response with multiple headers
  val response = Response.ok
    .addHeader("content-type", "application/json")
    .addHeader("cache-control", "max-age=3600")
    .addHeader("etag", "\"abc123\"")
    .addHeader("set-cookie", "session=xyz789")

  val respHeaders = response.headers
  println(s"Response Headers:")
  println(s"  content-type: ${respHeaders.toList.find(_._1.equalsIgnoreCase("content-type")).map(_._2)}")
  println(s"  cache-control: ${respHeaders.toList.find(_._1.equalsIgnoreCase("cache-control")).map(_._2)}")
  println(s"  etag: ${respHeaders.toList.find(_._1.equalsIgnoreCase("etag")).map(_._2)}")
  println()

  // Build URL with query parameters
  val baseUrl = URL.parse("https://api.github.com/search/repositories").toOption.get
  val queryParams = QueryParams("q" -> "language:scala", "sort" -> "stars", "order" -> "desc")
  val searchUrl = baseUrl.copy(queryParams = queryParams)

  println(s"Built Search URL: $searchUrl")
  println(s"Query string has: ${searchUrl.queryParams.toList.length} parameters")
  println()

  println("✓ Headers and Query Parameters example complete")
}
