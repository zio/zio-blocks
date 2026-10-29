package protectpageswithbasicauthorasessioncookie

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import zio.http._

/**
 * How to Protect Pages with Basic Auth or a Session Cookie — a test client
 *
 * Sends one request with the JDK's `java.net.http.HttpClient`, optionally with
 * a form body, an `Authorization` header or a `Cookie` header, and prints the
 * status code, the `WWW-Authenticate`, `Location` and `Set-Cookie` headers, and
 * the body.
 */
object Client {
  private val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()

  private def send(
    port: Int,
    method: String,
    target: String,
    form: Form,
    authorization: String,
    cookie: String
  ): HttpResponse[String] = {
    val headers = List("authorization" -> authorization, "cookie" -> cookie).filter(_._2.nonEmpty)
    val builder = headers.foldLeft(HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port$target"))) {
      case (withHeaders, (name, value)) => withHeaders.header(name, value)
    }
    val request =
      if (form.isEmpty) builder.method(method, HttpRequest.BodyPublishers.noBody()).build()
      else
        builder
          .method(method, HttpRequest.BodyPublishers.ofString(form.encode))
          .header("content-type", "application/x-www-form-urlencoded")
          .build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    println(s"$method $target -> ${response.statusCode}")
    List("www-authenticate", "location", "set-cookie").foreach { name =>
      response.headers.allValues(name).forEach(value => println(s"$name: $value"))
    }
    if (response.body.nonEmpty) println(response.body)
    response
  }

  def call(
    port: Int,
    method: String,
    target: String,
    form: Form = Form.empty,
    authorization: String = "",
    cookie: String = ""
  ): Unit = {
    send(port, method, target, form, authorization, cookie)
    ()
  }

  // Posts the login form and returns the session cookie as `name=value`, ready for a `Cookie` header.
  def logIn(port: Int, username: String, password: String): String = {
    val form     = Form("username" -> username, "password" -> password)
    val response = send(port, "POST", "/login", form, "", "")
    response.headers.firstValue("set-cookie").orElse("").takeWhile(_ != ';')
  }
}
