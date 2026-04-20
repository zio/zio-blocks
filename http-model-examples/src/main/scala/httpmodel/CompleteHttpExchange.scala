package httpmodel

import zio.http._

/**
 * HTTP Model — Complete HTTP Exchange
 *
 * Demonstrates a realistic HTTP exchange: creating a request with headers and
 * body, then receiving a response with status codes and headers. Shows core
 * types working together in a practical scenario.
 *
 * Run with: sbt "http-model-examples/runMain httpmodel.CompleteHttpExchange"
 */
@main def CompleteHttpExchange(): Unit = {

  println("=== HTTP Model: Complete Exchange ===\n")

  // 1. Build the request URL with query parameters
  println("1. Building request URL...")
  val apiBase     = URL.parse("https://api.example.com/users").toOption.get
  val queryParams = QueryParams("filter" -> "active", "page" -> "1")
  val requestUrl  = apiBase.copy(queryParams = queryParams)
  println(s"   URL: $requestUrl\n")

  // 2. Create request with headers and body
  println("2. Creating request...")
  val requestBody = Body.fromString(
    """{"name":"Bob","email":"bob@example.com"}""",
    Charset.UTF8
  )

  val request = Request
    .post(requestUrl, requestBody)
    .addHeader("content-type", "application/json")
    .addHeader("authorization", "Bearer token")

  println(s"   Method: ${request.method}")
  println(s"   URL: ${request.url}")
  println(s"   Headers: ${request.headers.toList.length}\n")

  // 3. Simulate server response
  println("3. Receiving response...")
  val responseBody = Body.fromString(
    """{"id":42,"name":"Bob","status":"created"}""",
    Charset.UTF8
  )

  val response = Response
    .apply(Status.Created)
    .copy(body = responseBody)
    .addHeader("content-type", "application/json")
    .addHeader("location", "/users/42")

  println(s"   Status: ${response.status.code}")
  println(s"   Headers: ${response.headers.toList.length}\n")

  // 4. Process the exchange
  println("4. Processing exchange...")
  val succeeded = response.status == Status.Created
  if (succeeded) {
    println(s"   ✓ Request successful (201 Created)\n")
  }

  // 5. Show common HTTP status codes
  println("5. Common HTTP Status Codes:")
  println(s"   200: ${Status.Ok}")
  println(s"   201: ${Status.Created}")
  println(s"   400: ${Status.BadRequest}")
  println(s"   401: ${Status.Unauthorized}")
  println(s"   404: ${Status.NotFound}")
  println(s"   500: ${Status.InternalServerError}")
  println()

  // 6. Summary
  println("=== Exchange Summary ===")
  println(s"Request  Method: ${request.method}")
  println(s"Request  URL: ${request.url.host}")
  println(s"Response Status: ${response.status.code}")
  println(s"\n✓ Complete HTTP Exchange example done")
}
