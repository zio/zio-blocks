package protectpageswithbasicauthorasessioncookie

import zio.http._
import protectpageswithbasicauthorasessioncookie.SessionCookie._

/**
 * How to Protect Pages with Basic Auth or a Session Cookie — Issuing a Session
 * Cookie
 *
 * Starts a session for Ada and renders its cookie with every attribute.
 */
// Run with: cd http-model-examples && sbt "runMain protectpageswithbasicauthorasessioncookie.Step7SessionCookieExample"
object Step7SessionCookieExample {
  def main(args: Array[String]): Unit =
    println(Cookie.renderResponse(sessionCookie(Sessions.start("ada"))))
}
