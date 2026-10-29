package protectpageswithbasicauthorasessioncookie

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import zio.http._
import protectpageswithbasicauthorasessioncookie.BasicAuth._

/**
 * How to Protect Pages with Basic Auth or a Session Cookie — Reading Basic
 * Credentials
 *
 * Renders Ada's `Authorization` header, decodes it again, and describes the
 * credentials of several hand-built requests.
 */
// Run with: cd http-model-examples && sbt "runMain protectpageswithbasicauthorasessioncookie.Step5ReadBasicCredentialsExample"
object Step5ReadBasicCredentialsExample {
  def main(args: Array[String]): Unit = {
    val adaHeader = Header.Authorization.Basic("ada", "correct horse battery staple").renderedValue
    println(adaHeader)
    println(new String(Base64.getDecoder.decode(adaHeader.stripPrefix("Basic ")), UTF_8))

    def adminRequest(authorization: Option[String]): Request = {
      val request = Request.get(URL.parse("/admin").fold(error => sys.error(error), url => url))
      authorization.fold(request)(value => request.addHeader("authorization", value))
    }

    def describe(request: Request): String =
      basicCredentials(request).fold("no Basic credentials") { basic =>
        s"user ${basic.username}, password of ${basic.password.length} characters"
      }

    def base64(text: String): String = Base64.getEncoder.encodeToString(text.getBytes(UTF_8))

    println(describe(adminRequest(Some(adaHeader))))
    println(describe(adminRequest(Some("basic " + base64("ada:correct horse battery staple")))))
    println(describe(adminRequest(Some("Basic " + base64("grace:tea:and:biscuits")))))
    println(describe(adminRequest(Some("Basic not-base64!"))))
    println(describe(adminRequest(Some("Bearer eyJhbGciOiJIUzI1NiJ9.e30.c2ln"))))
    println(describe(adminRequest(None)))
  }
}
