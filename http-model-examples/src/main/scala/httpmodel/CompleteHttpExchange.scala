package httpmodel

import zio.http._

/**
 * HTTP Model — Complete HTTP Exchange
 *
 * Demonstrates a realistic HTTP exchange: creating a request with multiple headers,
 * query parameters, and body, then receiving a response with status codes, headers, and data.
 * Shows all core types working together in a practical scenario.
 *
 * Run with: sbt "http-model-examples/runMain httpmodel.CompleteHttpExchange"
 */
object CompleteHttpExchange extends App {

  println("=== HTTP Model: Complete Exchange ===\n")

  // 1. Build the request URL with query parameters
  println("1. Building request URL...")
  val apiBase = URL.parse("https://api.example.com/users").toOption.get
  val queryParams = QueryParams(
    "filter" -> "active",
    "page" -> "1",
    "limit" -> "50"
  )
  val requestUrl = apiBase.copy(queryParams = queryParams)

  println(s"   URL: $requestUrl\n")

  // 2. Create request with comprehensive headers and body
  println("2. Creating request with headers and body...")
  val requestBody = Body.fromString(
    """{"name":"Bob","email":"bob@example.com","department":"engineering"}""",
    Charset.UTF8
  )

  val request = Request.post(requestUrl, requestBody)
    .addHeader("content-type", "application/json")
    .addHeader("accept", "application/json")
    .addHeader("authorization", "Bearer eyJhbGc...")
    .addHeader("user-agent", "MyHttpClient/2.0")
    .addHeader("request-id", "req-12345-67890")

  println(s"   Method: ${request.method}")
  println(s"   Headers: content-type, accept, authorization, user-agent, request-id\n")

  // 3. Simulate server response
  println("3. Receiving response...")

  val responseBody = Body.fromString(
    """{"id":42,"name":"Bob","email":"bob@example.com","status":"created"}""",
    Charset.UTF8
  )

  val response = Response.apply(Status.Created).copy(body = responseBody)
    .addHeader("content-type", "application/json")
    .addHeader("location", "/users/42")
    .addHeader("content-length", "60")
    .addHeader("server", "nginx/1.20")
    .addHeader("date", "Mon, 14 Apr 2026 16:30:00 GMT")
    .addHeader("cache-control", "no-cache")

  println(s"   Status: ${response.status}")
  println(s"   Headers:")
  println(s"     content-type: ${response.headers.toList.find(_._1.equalsIgnoreCase("content-type")).map(_._2)}")
  println(s"     location: ${response.headers.toList.find(_._1.equalsIgnoreCase("location")).map(_._2)}")
  println(s"     server: ${response.headers.toList.find(_._1.equalsIgnoreCase("server")).map(_._2)}\n")

  // 4. Process the exchange
  println("4. Processing exchange...")
  val succeeded = response.status == Status.Created
  val resourceLocation = response.headers.toList.find(_._1.equalsIgnoreCase("location")).map(_._2)

  if (succeeded && resourceLocation.nonEmpty) {
    println(s"   ✓ Request successful")
    println(s"   ✓ New resource created at: ${resourceLocation.get}\n")
  }

  // 5. Handle errors with different status codes
  println("5. Error handling with different status codes...")

  val errors = List(
    (Status.BadRequest, "Invalid request format"),
    (Status.Unauthorized, "Missing or invalid token"),
    (Status.NotFound, "Resource not found"),
    (Status.TooManyRequests, "Rate limit exceeded"),
    (Status.InternalServerError, "Server error")
  )

  for ((status, description) <- errors) {
    println(s"   ${status.code} ${description}")
  }
  println()

  // 6. Summary
  println("=== Exchange Summary ===")
  println(s"Request:  POST ${request.url}")
  println(s"  Status: ${response.status.code}")
  println(s"  Location: ${resourceLocation.getOrElse("N/A")}")
  println(s"\n✓ Complete HTTP Exchange example done")
}
