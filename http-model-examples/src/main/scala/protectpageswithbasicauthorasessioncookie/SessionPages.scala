package protectpageswithbasicauthorasessioncookie

import zio.blocks.html._
import zio.http._
import protectpageswithbasicauthorasessioncookie.Login._
import protectpageswithbasicauthorasessioncookie.Roastery._
import protectpageswithbasicauthorasessioncookie.SessionCookie._

/**
 * How to Protect Pages with Basic Auth or a Session Cookie — Reading the
 * Session Cookie
 *
 * The session lookup, the `signedIn` wrapper and the session-cookie pages,
 * shared by the logout section and the complete example.
 */
object SessionPages {
  def sessionId(request: Request): Option[String] =
    request.cookies.find(_.name == sessionCookieName).map(_.value)

  def signedIn(handler: (Session, Request) => Response): Request => Response =
    request =>
      sessionId(request).flatMap(Sessions.find) match {
        case Some(session) => handler(session, request)
        case None          => Response.seeOther("/login")
      }

  def signOutForm(user: String): Dom.Element =
    form(method := "post", action := "/logout")(s"Signed in as $user ", button("Sign out"))

  def pagesApp(request: Request): Response =
    (request.method, request.path.segments.toList) match {
      case (Method.GET, Nil)            => home
      case (Method.GET, List("login"))  => loginPage("Sign in with your staff account.")
      case (Method.POST, List("login")) => logIn(request)
      case (Method.GET, List("admin"))  => signedIn((session, _) => ordersPage(signOutForm(session.user)))(request)
      case _                            => notFound(request)
    }
}
