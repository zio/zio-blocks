package httpmodel

import zio.http._
import zio._

/**
 * HTTP Model — Form Submission and Cookies
 *
 * Demonstrates handling form data and cookies using Request, Response, Form, and Cookie types.
 * Shows multi-type composition for realistic form submission scenarios.
 *
 * Run with: sbt "http-model-examples/runMain httpmodel.FormAndCookies"
 */
object FormAndCookies extends App {

  // Create form data
  val formFields = Seq(
    ("username" -> "alice"),
    ("password" -> "secret123"),
    ("remember" -> "true")
  )
  val form = Form(formFields)
  println(s"Form Fields: ${form.fields.length}")
  println(s"  username: ${form.fields.find(_._1 == "username").map(_._2)}")
  println(s"  password: [hidden]")
  println()

  // Create POST request with form body
  val formUrl = URL.parse("https://example.com/login").toOption.get
  val formBody = Body.fromString(form.encode, Charset.UTF8)
  val formRequest = Request.post(formUrl, formBody)
    .addHeader("content-type", "application/x-www-form-urlencoded")

  println(s"Form Request: POST ${formRequest.url}")
  println(s"  Content-Type: ${formRequest.headers.toList.find(_._1.equalsIgnoreCase("content-type")).map(_._2)}")
  println()

  // Create response with cookies
  val response = Response.ok
    .addHeader("set-cookie", "session=abc123def456; Path=/; HttpOnly; Secure; Max-Age=3600")
    .addHeader("set-cookie", "preferences=theme=dark; Path=/")

  val cookies = response.headers.toList
    .filter { case (name, _) => name.equalsIgnoreCase("set-cookie") }

  println(s"Response Cookies:")
  for ((_, value) <- cookies) {
    println(s"  $value")
  }
  println()

  // Create request cookies
  val requestWithCookie = Request.get(
    URL.parse("https://example.com/dashboard").toOption.get
  ).addHeader("cookie", "session=abc123def456; preferences=theme=dark")

  println(s"Request with Cookies:")
  println(s"  Cookie header: ${requestWithCookie.headers.toList.find(_._1.equalsIgnoreCase("cookie")).map(_._2)}")
  println()

  // Demonstrate form field extraction
  println(s"Form Field Details:")
  for ((key, value) <- form.fields) {
    println(s"  $key = $value")
  }
  println()

  println("✓ Form and Cookies example complete")
}
