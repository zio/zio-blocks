package httpmodel

import zio.http._
import zio._

/**
 * HTTP Model — Basic HTTP Request/Response
 *
 * Demonstrates creating HTTP requests and responses with URLs, methods, headers, and bodies.
 * Shows how Request and Response types compose with Method, URL, Headers, and Body.
 *
 * Run with: sbt "http-model-examples/runMain httpmodel.BasicHttpRequest"
 */
object BasicHttpRequest extends App {

  // Create a URL with scheme, host, and path
  val url = URL.parse("https://api.example.com/users/42").toOption.get
  println(s"URL: $url")
  println(s"Scheme: ${url.scheme}")
  println(s"Host: ${url.host}")
  println(s"Port: ${url.port}")
  println()

  // Create a GET request to that URL
  val getRequest = Request.get(url)
  println(s"GET Request: ${getRequest.method} ${getRequest.url}")
  println()

  // Create a POST request with body
  val postBody = Body.fromString("{\"name\": \"Alice\", \"email\": \"alice@example.com\"}")
  val postRequest = Request.post(url, postBody)
    .addHeader("content-type", "application/json")
    .addHeader("authorization", "Bearer token123")
  println(s"POST Request:")
  println(s"  Method: ${postRequest.method}")
  println(s"  URL: ${postRequest.url}")
  println(s"  Headers: ${postRequest.headers.toList.take(2)}")
  println()

  // Create responses
  val okResponse = Response.ok
  println(s"OK Response: ${okResponse.status}")

  val createdResponse = Response(Status.Created)
    .addHeader("location", "/users/123")
    .addHeader("content-type", "application/json")
  println(s"Created Response: ${createdResponse.status}")
  println(s"Location: ${createdResponse.headers.toList.find(_._1.equalsIgnoreCase("location")).map(_._2)}")
  println()

  // Demonstrate Request methods
  val request = Request.post(
    URL.parse("https://api.example.com/data").toOption.get,
    Body.fromString("request data")
  )
  println(s"Request Summary:")
  println(s"  URL path: ${request.url.path}")
  println(s"  Method: ${request.method}")
  println()

  println("✓ Basic HTTP Request/Response example complete")
}
