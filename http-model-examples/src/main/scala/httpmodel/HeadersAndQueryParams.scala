package httpmodel

import zio.http._

/**
 * HTTP Model — Headers and Query Parameters
 *
 * Demonstrates working with headers and query parameters in URLs and requests.
 * Shows how Headers, QueryParams, and URL types work together for data
 * extraction.
 *
 * Run with: sbt "http-model-examples/runMain httpmodel.HeadersAndQueryParams"
 */
@main def HeadersAndQueryParams(): Unit = {

  // Create URL with query parameters
  val url = URL.parse("https://api.example.com/search?q=zio&limit=10&sort=date").toOption.get
  println(s"URL: $url")
  println(s"Query Parameters:")
  val params = url.queryParams
  params.toList.foreach { case (key, values) =>
    println(s"  $key: ${values.headOption}")
  }
  println()

  // Create request with headers
  val request = Request
    .get(url)
    .addHeader("user-agent", "MyApp/1.0")
    .addHeader("accept", "application/json")
    .addHeader("authorization", "Bearer secret-token")

  println(s"Request Headers:")
  val headers = request.headers
  println(s"  Total headers: ${headers.toList.length}")
  headers.toList.take(3).foreach { case (name, value) =>
    println(s"    $name: $value")
  }
  println()

  // Response with multiple headers
  val response = Response.ok
    .addHeader("content-type", "application/json")
    .addHeader("cache-control", "max-age=3600")
    .addHeader("etag", "\"abc123\"")

  println(s"Response Headers:")
  val respHeaders = response.headers
  println(s"  Total headers: ${respHeaders.toList.length}")
  respHeaders.toList.take(3).foreach { case (name, value) =>
    println(s"    $name: $value")
  }
  println()

  // Build URL with query parameters
  val baseUrl     = URL.parse("https://api.github.com/search/repositories").toOption.get
  val queryParams = QueryParams("q" -> "language:scala", "sort" -> "stars")
  val searchUrl   = baseUrl.copy(queryParams = queryParams)

  println(s"Built Search URL: $searchUrl")
  println(s"Query parameters: ${searchUrl.queryParams.toList.length}")
  println()

  println("✓ Headers and Query Parameters example complete")
}
