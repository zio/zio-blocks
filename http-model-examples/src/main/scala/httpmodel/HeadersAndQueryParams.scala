/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
  params.toList.foreach { case (key, value) =>
    println(s"  $key: $value")
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
