package protectpageswithbasicauthorasessioncookie

import zio.http._

/**
 * How to Protect Pages with Basic Auth or a Session Cookie — Reading Basic
 * Credentials and Asking for Credentials With a 401 Challenge
 *
 * The Basic-credentials reader, the 401 challenge and the `basicAuth` wrapper,
 * shared by the Basic-auth sections and the complete example.
 */
object BasicAuth {
  def basicCredentials(request: Request): Option[Header.Authorization.Basic] =
    request.header(Header.Authorization).collect { case basic: Header.Authorization.Basic => basic }

  val realm = "Roastery staff"

  val basicChallenge: Response =
    Response
      .text(Status.Unauthorized, "Sign in to see the roastery's orders")
      .addHeader(Header.WWWAuthenticate("Basic", Map("realm" -> realm, "charset" -> "UTF-8")))

  def basicAuth(handler: (String, Request) => Response): Request => Response =
    request =>
      basicCredentials(request) match {
        case None        => basicChallenge
        case Some(basic) =>
          Staff.authenticate(basic.username, basic.password) match {
            case Some(user) => handler(user, request)
            case None       =>
              println("auth: wrong Basic credentials") // Never log the password or the header.
              basicChallenge
          }
      }
}
