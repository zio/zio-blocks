package protectendpointswithjwt

import java.nio.charset.StandardCharsets.UTF_8
import zio.blocks.config.{Config, ConfigSource, Secret}
import zio.blocks.jwt._
import zio.blocks.schema.Schema
import zio.http._

import Bookstore._

/**
 * How to Protect an Endpoint with JWT Bearer Tokens — Complete Example
 *
 * A bookstore API with a public `GET /books`, a `GET /orders` for signed-in
 * users and a `PUT /books/{id}/price` for admins. The HMAC secret is loaded
 * from configuration as a `Secret`, every bearer token is verified with
 * `Jwt.decode`, and failures are answered with 401 or 403 and a
 * `WWW-Authenticate: Bearer` challenge. The program calls the API with no
 * token, bad tokens and good tokens, then stops the server.
 *
 * Run with: cd jwt-examples && sbt "runMain
 * protectendpointswithjwt.CompleteExample"
 */
object CompleteExample {

  final case class AuthConfig(issuer: String, audience: String, hmacSecret: Secret)

  object AuthConfig {
    implicit val schema: Schema[AuthConfig] = Schema.derived
  }

  final case class Caller(subject: String, roles: Set[String])

  type Verifier = String => Either[JwtError, Caller]

  def rolesOf(claims: JwtClaims): Set[String] =
    claims.extra.get("roles") match {
      case Some(JwtValue.Arr(items)) => items.collect { case JwtValue.Str(role) => role }.toSet
      case _                         => Set.empty
    }

  def toCaller(claims: JwtClaims): Either[JwtError, Caller] =
    (claims.sub, claims.exp) match {
      case (_, None)          => Left(JwtError.MissingClaim("exp"))
      case (None, _)          => Left(JwtError.MissingClaim("sub"))
      case (Some(subject), _) => Right(Caller(subject, rolesOf(claims)))
    }

  def verifierFrom(config: AuthConfig, nowSeconds: Option[Long] = None): Either[String, Verifier] = {
    val key     = Secret.unwrap(config.hmacSecret).getBytes(UTF_8)
    val options = JwtDecodeOptions(
      issuer = Some(config.issuer),
      expectedAudience = Some(config.audience),
      clockSkewSeconds = 30,
      nowSeconds = nowSeconds
    )
    if (key.length < 32) Left(s"hmacSecret has ${key.length} bytes; HS256 needs at least 32")
    else Right(token => Jwt.decode(token, key, Algorithm.HS256, options).flatMap(toCaller))
  }

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

  def bearerToken(request: Request): Option[String] =
    request.header(Header.Authorization).collect { case Header.Authorization.Bearer(token) => token }

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

  // In production these values come from the environment or a config file.
  val settings: Map[String, String] = Map(
    "issuer"     -> "bookstore-auth",
    "audience"   -> "bookstore-api",
    "hmacSecret" -> TestTokens.secret
  )

  def main(args: Array[String]): Unit = {
    val config = Config.load[AuthConfig](ConfigSource.fromMap(settings, "env")) match {
      case Right(loaded) => loaded
      case Left(errors)  => sys.error(errors.map(_.message).mkString("; "))
    }
    println(s"loaded $config")

    // A fixed clock keeps the output stable; leave `nowSeconds` unset in production.
    val verify = verifierFrom(config, nowSeconds = Some(TestTokens.now + 60)).fold(sys.error, identity)

    JdkHttp.withServer(bookstoreApp(verify)) { port =>
      def bearer(token: String) = s"Bearer $token"

      Client.call(port, "GET", "/books")
      Client.call(port, "GET", "/orders")
      Client.call(port, "GET", "/orders", authorization = "Basic cmVhZGVyOnNlY3JldA==")
      Client.call(port, "GET", "/orders", authorization = bearer(TestTokens.forged))
      Client.call(port, "GET", "/orders", authorization = bearer(TestTokens.expired))
      Client.call(port, "GET", "/orders", authorization = bearer(TestTokens.otherAudience))
      Client.call(port, "GET", "/orders", authorization = bearer(TestTokens.neverExpires))
      Client.call(port, "GET", "/orders", authorization = bearer(TestTokens.reader))
      Client.call(port, "PUT", "/books/1/price", "3999", authorization = bearer(TestTokens.reader))
      Client.call(port, "PUT", "/books/1/price", "3999", authorization = bearer(TestTokens.admin))
    }
  }
}
