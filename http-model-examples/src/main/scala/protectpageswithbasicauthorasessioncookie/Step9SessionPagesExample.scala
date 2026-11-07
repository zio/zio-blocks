package protectpageswithbasicauthorasessioncookie

import protectpageswithbasicauthorasessioncookie.SessionCookie._
import protectpageswithbasicauthorasessioncookie.SessionPages._

/**
 * How to Protect Pages with Basic Auth or a Session Cookie — Reading the
 * Session Cookie
 *
 * Asks for the staff page without a cookie, signs in, asks again with the
 * returned cookie, and then with an id the server never issued.
 */
// Run with: cd http-model-examples && sbt "runMain protectpageswithbasicauthorasessioncookie.Step9SessionPagesExample"
object Step9SessionPagesExample {
  def main(args: Array[String]): Unit =
    JdkHttp.withServer(pagesApp) { port =>
      Client.call(port, "GET", "/admin")
      val cookie = Client.logIn(port, "ada", "correct horse battery staple")
      Client.call(port, "GET", "/admin", cookie = cookie)
      Client.call(port, "GET", "/admin", cookie = s"$sessionCookieName=made-up-session-id")
    }
}
