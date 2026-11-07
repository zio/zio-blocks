package protectpageswithbasicauthorasessioncookie

import zio.http._
import protectpageswithbasicauthorasessioncookie.SessionCookie._
import protectpageswithbasicauthorasessioncookie.SessionPages._

/**
 * How to Protect Pages with Basic Auth or a Session Cookie — Logging Out
 *
 * The logout handler and `staffApp`, shared by the logout section and the
 * complete example.
 */
object Logout {
  val expiredSessionCookie: ResponseCookie = sessionCookie("").copy(maxAge = Some(0L))

  def logOut(request: Request): Response = {
    sessionId(request).foreach(Sessions.end)
    Response.seeOther("/").addCookie(expiredSessionCookie)
  }

  def staffApp(request: Request): Response =
    (request.method, request.path.segments.toList) match {
      case (Method.POST, List("logout")) => logOut(request)
      case _                             => pagesApp(request)
    }
}
