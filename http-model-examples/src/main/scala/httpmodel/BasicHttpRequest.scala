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
 * HTTP Model — Basic HTTP Request/Response
 *
 * Demonstrates creating HTTP requests and responses with URLs, methods,
 * headers, and bodies. Shows how Request and Response types compose with
 * Method, URL, Headers, and Body.
 *
 * Run with: sbt "http-model-examples/runMain httpmodel.BasicHttpRequest"
 */
@main def BasicHttpRequest(): Unit = {

  // Create a URL with scheme, host, and path
  val url = URL.parse("https://api.example.com/users/42").toOption.get
  println(s"URL: $url")
  println(s"Host: ${url.host}")
  println()

  // Create a GET request to that URL
  val getRequest = Request.get(url)
  println(s"GET Request:")
  println(s"  Method: ${getRequest.method}")
  println(s"  URL: ${getRequest.url}")
  println()

  // Create a POST request with body
  val postBody    = Body.fromString("""{"name": "Alice", "email": "alice@example.com"}""", Charset.UTF8)
  val postRequest = Request
    .post(url, postBody)
    .addHeader("content-type", "application/json")
    .addHeader("authorization", "Bearer token123")

  println(s"POST Request:")
  println(s"  Method: ${postRequest.method}")
  println(s"  Headers count: ${postRequest.headers.toList.length}")
  println()

  // Create responses
  val okResponse = Response.ok
  println(s"OK Response: ${okResponse.status}")
  println()

  val createdResponse = Response
    .apply(Status.Created)
    .addHeader("location", "/users/123")
    .addHeader("content-type", "application/json")
  println(s"Created Response: ${createdResponse.status}")
  println()

  // Demonstrate different HTTP methods
  println(s"HTTP Methods:")
  println(s"  GET:    ${Method.GET}")
  println(s"  POST:   ${Method.POST}")
  println(s"  PUT:    ${Method.PUT}")
  println(s"  DELETE: ${Method.DELETE}")
  println()

  println("✓ Basic HTTP Request/Response example complete")
}
