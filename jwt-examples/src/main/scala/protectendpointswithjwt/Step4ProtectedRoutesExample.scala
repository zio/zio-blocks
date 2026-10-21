package protectendpointswithjwt

import zio.blocks.jwt._
import zio.http._

import Bookstore._
import Step2ReadBearerTokenExample.bearerToken
import Step3VerifyTokenExample.{Caller, Verifier, verify}

/**
 * How to Protect an Endpoint with JWT Bearer Tokens: Step 4 — Protecting the
 * Routes
 *
 * Wraps the handlers of `GET /orders` and `PUT /books/{id}/price` in
 * `authenticated`, which answers 401 with a `WWW-Authenticate: Bearer`
 * challenge for a missing or bad token, 403 when the caller lacks the role, and
 * otherwise passes the verified caller to the handler.
 *
 * Run with: cd jwt-examples && sbt "runMain
 * protectendpointswithjwt.Step4ProtectedRoutesExample"
 */
object Step4ProtectedRoutesExample {

  val realm = "bookstore"

  val missingToken: Response =
    Response
      .text(Status.Unauthorized, "Sign in to continue")
      .addHeader(Header.WWWAuthenticate("Bearer", Map("realm" -> realm)))

  val invalidToken: Response =
    Response
      .text(Status.Unauthorized, "The access token is invalid or expired")
      .addHeader(Header.WWWAuthenticate("Bearer", Map("realm" -> realm, "error" -> "invalid_token")))

  def missingRole(role: String): Response =
    Response
      .text(Status.Forbidden, s"This action needs the $role role")
      .addHeader(Header.WWWAuthenticate("Bearer", Map("realm" -> realm, "error" -> "insufficient_scope")))

  def authenticated(verify: Verifier, requiredRole: Option[String] = None)(
    handler: (Caller, Request) => Response
  ): Request => Response =
    request =>
      bearerToken(request) match {
        case None        => missingToken
        case Some(token) =>
          verify(token) match {
            case Left(error) =>
              // Log why, never the token itself.
              println(s"auth: rejected token: ${error.getClass.getSimpleName}")
              invalidToken
            case Right(caller) if requiredRole.forall(caller.roles.contains) => handler(caller, request)
            case Right(caller)                                               =>
              println(s"auth: ${caller.subject} lacks role ${requiredRole.getOrElse("")}")
              missingRole(requiredRole.getOrElse(""))
          }
      }

  def bookstoreApp(verify: Verifier): Request => Response = {
    val showOrders = authenticated(verify)((caller, _) => ordersOf(caller.subject))

    def changePrice(id: String) =
      authenticated(verify, requiredRole = Some("admin"))((_, request) => setPrice(id, request.body.text))

    request =>
      (request.method, request.path.segments.toList) match {
        case (Method.GET, List("books"))              => listBooks
        case (Method.GET, List("orders"))             => showOrders(request)
        case (Method.PUT, List("books", id, "price")) => changePrice(id)(request)
        case _                                        => notFound(request)
      }
  }

  def main(args: Array[String]): Unit =
    JdkHttp.withServer(bookstoreApp(verify)) { port =>
      Client.call(port, "GET", "/books")
      Client.call(port, "GET", "/orders")
      Client.call(port, "GET", "/orders", authorization = s"Bearer ${TestTokens.reader}")
      Client.call(port, "PUT", "/books/1/price", "3999", authorization = s"Bearer ${TestTokens.reader}")
      Client.call(port, "PUT", "/books/1/price", "3999", authorization = s"Bearer ${TestTokens.admin}")
    }
}
