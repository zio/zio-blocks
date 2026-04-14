package httpmodel

import zio.http._

/**
 * HTTP Model — Form Submission and Cookies
 *
 * Demonstrates handling form data and cookies using Request, Response, and Form types.
 * Shows multi-type composition for realistic form submission scenarios.
 *
 * Run with: sbt "http-model-examples/runMain httpmodel.FormAndCookies"
 */
@main def FormAndCookies(): Unit = {

  println("=== Form Submission ===")
  println()

  // Create form data
  val form = Form("username" -> "alice", "password" -> "secret")
  println("Form created with 2 fields")
  println()

  // Create POST request with form body
  val formUrl = URL.parse("https://example.com/login").toOption.get
  val formBody = Body.fromString(form.encode, Charset.UTF8)
  val formRequest = Request.post(formUrl, formBody)
    .addHeader("content-type", "application/x-www-form-urlencoded")

  println(s"Form Request:")
  println(s"  Method: ${formRequest.method}")
  println(s"  URL: ${formRequest.url}")
  println(s"  Body size: ${formBody.asString(Charset.UTF8).length} bytes")
  println()

  // Create response with cookies
  val response = Response.ok
    .addHeader("set-cookie", "session=abc123def456; Path=/")
    .addHeader("set-cookie", "preferences=theme=dark")

  val cookieHeaders = response.headers.toList
    .filter { case (name, _) => name.equalsIgnoreCase("set-cookie") }

  println(s"Response Cookies:")
  println(s"  Number of cookies: ${cookieHeaders.length}")
  cookieHeaders.zipWithIndex.foreach { case ((_, value), idx) =>
    println(s"  Cookie ${idx + 1}: ${value.take(40)}...")
  }
  println()

  // Create request with cookies
  val requestWithCookie = Request.get(
    URL.parse("https://example.com/dashboard").toOption.get
  ).addHeader("cookie", "session=abc123; preferences=theme=dark")

  println(s"Request with Cookies:")
  requestWithCookie.headers.toList.find(_._1.equalsIgnoreCase("cookie")).foreach {
    case (_, value) => println(s"  Cookie header: ${value.take(40)}...")
  }
  println()

  println("✓ Form and Cookies example complete")
}
