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
