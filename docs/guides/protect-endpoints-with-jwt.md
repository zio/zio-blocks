---
id: protect-endpoints-with-jwt
title: "How to Protect an Endpoint with JWT Bearer Tokens"
description: "Let only signed-in users call an http-model endpoint: read the bearer token, verify the JWT, and answer 401 or 403 correctly."
keywords:
  - "Authentication"
  - "Authorization"
  - "Bearer Token"
  - "JSON Web Token"
  - "AuthType"
  - "Jwt"
---

In this guide, we protect the endpoints of a small Scala bookstore API so that only signed-in users can call them. By the end, you will have a wrapper of about twenty lines that reads the `Authorization: Bearer <token>` header, verifies the JSON Web Token (JWT) with `Jwt.decode`, answers `401 Unauthorized` or `403 Forbidden` the way HTTP clients expect, and hands the verified caller to your handler.

We start from an API with no protection at all and add one piece at a time:

- read the token from the request with the typed header API,
- verify it, including issuer, audience and expiry,
- choose between 401 and 403, with the right `WWW-Authenticate` challenge,
- wrap handlers so they only ever run for a verified caller,
- load the signing key from configuration as a `Secret`.

Every section that serves requests starts a real server on a free loopback port, calls it, and prints what came back.

## The Problem

Our bookstore has a public book list, a page with the caller's own orders, and an admin-only route that changes a book's price. Without authentication, the server can only trust what the request says about the caller. Anyone who can reach the server can read anyone's orders and change any price.

A first attempt often checks that an `Authorization` header is present and stops there. Here is that handler, written against the JDK HTTP server API:

```scala mdoc:compile-only
import com.sun.net.httpserver.{HttpExchange, HttpHandler}
import java.nio.charset.StandardCharsets.UTF_8

val ordersHandler: HttpHandler = (exchange: HttpExchange) => {
  val authorization = Option(exchange.getRequestHeaders.getFirst("Authorization"))
  val (status, body) =
    if (authorization.exists(_.startsWith("Bearer "))) // Any string after "Bearer " is accepted
      (200, "Orders of reader-1024: Zionomicon, Functional Programming in Scala")
    else
      (403, "Forbidden") // Wrong status, and no WWW-Authenticate challenge for the client
  val bytes = body.getBytes(UTF_8)
  exchange.sendResponseHeaders(status, bytes.length.toLong)
  exchange.getResponseBody.write(bytes)
  exchange.close()
}
```

This handler lets in `Bearer hello`, never checks a signature or an expiry, has no idea *who* the caller is, and answers a missing token with 403, which tells a well-behaved client that signing in again will not help.

Declaring the requirement does not fix it either. ZIO Blocks' endpoint module has `AuthType.Bearer`, and it is easy to read `.auth(AuthType.Bearer)` as "this endpoint is protected". It is not: `AuthType` only *describes* the requirement and says how to *extract* the token. Nothing in ZIO Blocks verifies a JWT on your behalf. Verification is your code, and this guide shows that code.

## Prerequisites

Add the HTTP model, the JWT module and the config module to your `build.sbt`. The endpoint module is only needed for the `AuthType` section:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-http-model" % "@VERSION@",
  "dev.zio" %% "zio-blocks-jwt"        % "@VERSION@",
  "dev.zio" %% "zio-blocks-config"     % "@VERSION@",
  "dev.zio" %% "zio-blocks-endpoint"   % "@VERSION@" // optional
)

// Scala 3.9 and newer
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-next-http-model" % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-jwt"        % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-config"     % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-endpoint"   % "@VERSION@" // optional
)
scalacOptions += "-experimental"
```

The code is the same for every Scala version. The HTTP model depends on the ZIO Blocks streams module, which is compiled for Java 21, so run the program on **JDK 21 or newer**.

You should know how to sign and verify a token with `Jwt.sign` and `Jwt.decode`, from [How to Issue and Verify JWTs](./issue-and-verify-jwts.md). The configuration section uses `Config.load`, introduced in [Build a Typed Configuration Loader](./typed-config-loader.md).

ZIO Blocks has no HTTP server. We serve requests with the small adapter from the guide "How to Serve http-model Requests with the JDK HTTP Server": `JdkHttp.withServer(app)(use)` starts the JDK's built-in server on a free loopback port, runs `use` with the port, and stops the server. `Client.call(port, method, target, body, authorization)` sends one request and prints the status, the `WWW-Authenticate` header when there is one, and the body. Both files are shown in full under [Running the Examples](#running-the-examples); copy them into your project.

We use these imports throughout the guide:

```scala mdoc:silent
import java.nio.charset.StandardCharsets.UTF_8
import zio.blocks.chunk.Chunk
import zio.blocks.config.{Config, ConfigSource, Secret}
import zio.blocks.jwt._
import zio.blocks.schema.Schema
import zio.http._
```

```scala mdoc:invisible
import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import java.net.{InetAddress, InetSocketAddress, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

object JdkHttp {
  def toRequest(exchange: HttpExchange): Either[Response, Request] = {
    val uri    = exchange.getRequestURI
    val target = uri.getRawPath + Option(uri.getRawQuery).fold("")("?" + _)
    for {
      method <- Method.fromString(exchange.getRequestMethod).toRight(Response(Status.NotImplemented))
      url    <- URL.parse(target).left.map(error => Response.text(Status.BadRequest, error))
    } yield {
      val headers = exchange.getRequestHeaders.asScala.foldLeft(Headers.empty) { case (acc, (name, values)) =>
        values.asScala.foldLeft(acc)((withValue, value) => withValue.add(name, value))
      }
      val contentType =
        headers.get(Header.ContentType).map(_.value).getOrElse(ContentType.`application/octet-stream`)
      val body    = Body.fromArray(exchange.getRequestBody.readAllBytes(), contentType)
      val version = Version.fromString(exchange.getProtocol).getOrElse(Version.`HTTP/1.1`)
      Request(method, url, headers, body, version)
    }
  }

  def writeResponse(exchange: HttpExchange, response: Response): Unit = {
    val out = exchange.getResponseHeaders
    response.headers.toList.foreach { case (name, value) =>
      if (name != "content-length" && name != "transfer-encoding") out.add(name, value)
    }
    val bytes = response.body.toArray
    if (bytes.nonEmpty && !response.headers.has("content-type"))
      out.set("content-type", response.body.contentType.render)
    val code   = response.status.code
    val isHead = exchange.getRequestMethod == "HEAD"
    if (isHead && bytes.nonEmpty) out.set("content-length", bytes.length.toString)
    val noBody = isHead || code == 204 || code == 304 || bytes.isEmpty
    exchange.sendResponseHeaders(code, if (noBody) -1L else bytes.length.toLong)
    if (!noBody) exchange.getResponseBody.write(bytes)
  }

  def handler(app: Request => Response): HttpHandler = exchange =>
    try {
      val response =
        try
          toRequest(exchange) match {
            case Left(error)                                     => error
            case Right(request) if request.method == Method.HEAD => app(request.copy(method = Method.GET))
            case Right(request)                                  => app(request)
          }
        catch {
          case NonFatal(error) =>
            System.err.println(s"Handler failed: $error")
            Response.text(Status.InternalServerError, "Internal Server Error")
        }
      writeResponse(exchange, response)
    } finally exchange.close()

  def start(app: Request => Response, port: Int = 0): HttpServer = {
    val server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress, port), 0)
    server.createContext("/", handler(app))
    server.start()
    server
  }

  def withServer[A](app: Request => Response)(use: Int => A): A = {
    val server = start(app)
    try use(server.getAddress.getPort)
    finally server.stop(0)
  }
}

object Client {
  private val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()

  def call(port: Int, method: String, target: String, body: String = "", authorization: String = ""): Unit = {
    val plain   = HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port$target"))
    val builder = if (authorization.isEmpty) plain else plain.header("authorization", authorization)
    val request =
      if (body.isEmpty) builder.method(method, HttpRequest.BodyPublishers.noBody()).build()
      else
        builder
          .method(method, HttpRequest.BodyPublishers.ofString(body))
          .header("content-type", "application/json")
          .build()
    val response                     = client.send(request, HttpResponse.BodyHandlers.ofString())
    def header(name: String): String = response.headers.firstValue(name).orElse("none")
    println(s"$method $target -> ${response.statusCode} [${header("content-type")}, ${header("content-length")} bytes]")
    response.headers.firstValue("www-authenticate").ifPresent(value => println(s"www-authenticate: $value"))
    if (response.body.nonEmpty) println(response.body)
  }
}
```

## The Bookstore API

The bookstore sells three Scala books and keeps the orders of two fictional users. The responses are plain text so that the output stays short; nothing in this model knows about authentication:

```scala mdoc:silent
final case class Book(id: Int, title: String, priceCents: Long)

object Bookstore {
  val books: List[Book] = List(
    Book(1, "Zionomicon", 4500),
    Book(2, "Programming in Scala", 5999),
    Book(3, "Functional Programming in Scala", 4999)
  )

  // Orders per user, keyed by the `sub` claim of their token.
  val orders: Map[String, List[String]] = Map(
    "reader-1024" -> List("Zionomicon", "Functional Programming in Scala"),
    "staff-0007"  -> List("Programming in Scala")
  )

  def price(cents: Long): String = f"$$${cents / 100}%d.${cents % 100}%02d"

  def listBooks: Response =
    Response.text(books.map(book => s"${book.id}. ${book.title} ${price(book.priceCents)}").mkString("\n"))

  def ordersOf(user: String): Response =
    Response.text(s"Orders of $user: ${orders.getOrElse(user, Nil).mkString(", ")}")

  // Answers with the new price; a real store would also save it.
  def setPrice(id: String, body: String): Response =
    (books.find(_.id.toString == id), body.trim.toLongOption.filter(_ > 0)) match {
      case (None, _)                 => Response.text(Status.NotFound, s"No book with id $id")
      case (_, None)                 => Response.text(Status.BadRequest, "The body must be a price in cents")
      case (Some(book), Some(cents)) => Response.text(s"${book.title} now costs ${price(cents)}")
    }

  def notFound(request: Request): Response =
    Response.text(Status.NotFound, s"No route for ${request.method.name} ${request.path.encode}")
}

import Bookstore._
```

Without authentication, the `GET /orders` route has to take the user's name from the request itself, here from a `?user=` query parameter. The routing matches on the method and the path segments:

```scala mdoc:silent
def unprotectedApp(request: Request): Response =
  (request.method, request.path.segments.toList) match {
    case (Method.GET, List("books"))              => listBooks
    case (Method.GET, List("orders"))             => ordersOf(request.queryParams.getFirst("user").getOrElse(""))
    case (Method.PUT, List("books", id, "price")) => setPrice(id, request.body.text)
    case _                                        => notFound(request)
  }
```

When we serve it and call every route without any credentials, every call succeeds, including reading another user's orders and dropping the price of Zionomicon to one cent:

```scala mdoc
JdkHttp.withServer(unprotectedApp) { port =>
  Client.call(port, "GET", "/books")
  Client.call(port, "GET", "/orders?user=reader-1024")
  Client.call(port, "PUT", "/books/1/price", "1")
}
```

The rest of the guide keeps `GET /books` public, makes `GET /orders` require a signed-in user, and makes `PUT /books/{id}/price` require a signed-in admin.

## Declaring the Requirement With AuthType.Bearer

If you describe your API with the [endpoint module](../reference/endpoint/index.md), you declare the requirement with `Endpoint#auth`. [`AuthType.Bearer`](../reference/endpoint/auth-type.md) says that the client must send `Authorization: Bearer <token>`, and `AuthType.Scoped` adds the names of the scopes a caller needs:

```scala mdoc:silent
import zio.blocks.endpoint.{AuthType, Endpoint, PathCodec}
import zio.blocks.endpoint.RoutePattern._

val ordersEndpoint = Endpoint(Method.GET / "orders").auth(AuthType.Bearer)

val priceEndpoint =
  Endpoint(Method.PUT / "books" / PathCodec.int("id") / "price")
    .auth(AuthType.Scoped(AuthType.Bearer, List("admin")))
```

These are descriptions for documentation and for any interpreter that reads them. `AuthType.Bearer` carries a codec that parses the header into a `Header.Authorization.Bearer`, and a status for unauthorized calls:

```scala mdoc
println(ordersEndpoint.auth.unauthorizedStatus)
println(priceEndpoint.auth.unauthorizedStatus)
```

The default is `404 Not Found`, chosen to hide that a protected endpoint exists (see [Answering 401, 403 or 404](#answering-401-403-or-404)). Neither value checks a signature, an expiry or a scope: ZIO Blocks ships no server interpreter that enforces them. The rest of the guide is the code that does.

:::warning
`.auth(AuthType.Bearer)` on its own does not protect anything. Treat it as documentation of what your handler enforces, and keep the two in sync.
:::

## Reading the Bearer Token

The HTTP model parses the `Authorization` header into one of four cases: `Header.Authorization.Basic`, `Bearer`, `Digest` or `Unparsed`. `Request#header` takes the header's companion object and returns an `Option`, so reading a bearer token is a `collect` on that `Option`:

```scala mdoc:silent
def bearerToken(request: Request): Option[String] =
  request.header(Header.Authorization).collect { case Header.Authorization.Bearer(token) => token }
```

To see how different headers come out, we build a few requests by hand, with no server involved:

```scala mdoc:silent
def ordersRequest(authorization: Option[String]): Request = {
  val request = Request.get(URL.parse("/orders").fold(error => sys.error(error), url => url))
  authorization.fold(request)(value => request.addHeader("authorization", value))
}
```

A bearer header gives the token whatever the case of the scheme name; a Basic header, a header with no token and a missing header all give `None`:

```scala mdoc
println(bearerToken(ordersRequest(Some("Bearer eyJhbGciOiJIUzI1NiJ9.e30.c2ln"))))
println(bearerToken(ordersRequest(Some("bearer eyJhbGciOiJIUzI1NiJ9.e30.c2ln"))))
println(bearerToken(ordersRequest(Some("Basic cmVhZGVyOnNlY3JldA=="))))
println(bearerToken(ordersRequest(Some("Bearer"))))
println(bearerToken(ordersRequest(None)))
```

`Bearer` with no token is not a valid header, and `Headers#get` skips values it cannot parse (see the [headers reference](../reference/http-model/headers.md)), so it reads as a missing header. All three `None` cases get the same answer: a client that sent no usable token is asked to sign in.

:::warning
Read the token only from the `Authorization` header. Tokens in query strings end up in access logs, browser history and `Referer` headers.
:::

## Verifying the Token With Jwt.decode

Before we can verify tokens, someone has to issue them. In production, an auth service does that. In this guide, a small helper plays the auth service and signs tokens with a shared HMAC secret, at a fixed time so that every result is the same on every run. Each token carries the user's roles in a custom `roles` claim:

```scala mdoc:silent
object TestTokens {
  // 2026-01-01T00:00:00Z. The API verifies tokens one minute later.
  val now: Long = 1767225600L

  // Sample data only. A real HS256 secret is at least 32 random bytes, loaded from a secret store.
  val secret: String = "sample-bookstore-secret-32-bytes"

  // Registered claims (iss, sub, aud, iat, exp) go in their own fields, never in `extra`.
  def issue(
    subject: String,
    roles: List[String],
    audience: String = "bookstore-api",
    expiresAt: Option[Long] = Some(now + 3600),
    key: String = secret
  ): String = {
    val claims = JwtClaims(
      iss = Some("bookstore-auth"),
      sub = Some(subject),
      aud = Some(JwtAudience.single(audience)),
      iat = Some(now),
      exp = expiresAt,
      extra = Map("roles" -> JwtValue.Arr(Chunk.fromIterable(roles.map(role => JwtValue.Str(role)))))
    )
    Jwt.sign(claims, key.getBytes(UTF_8), Algorithm.HS256).fold(error => throw error, token => token)
  }

  val reader: String        = issue("reader-1024", List("reader"))
  val admin: String         = issue("staff-0007", List("reader", "admin"))
  val expired: String       = issue("reader-1024", List("reader"), expiresAt = Some(now - 600))
  val otherAudience: String = issue("reader-1024", List("reader"), audience = "partner-api")
  val forged: String        = issue("staff-0007", List("admin"), key = "attacker-guessed-secret-32-bytes")
  val neverExpires: String  = issue("reader-1024", List("reader"), expiresAt = None)
}
```

:::caution
Keep registered claims such as `iss` and `exp` out of `JwtClaims#extra`. If `extra` also holds one, `Jwt.sign` writes the claim twice and verifiers read the `extra` value.
:::

On the API side, a handler should not work with raw claims. We turn a verified token into a `Caller`: who the user is and what roles they have. A `Verifier` is any function from a token to a `Caller` or a `JwtError`:

```scala mdoc:silent
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

def jwtVerifier(key: Array[Byte], options: JwtDecodeOptions): Verifier =
  token => Jwt.decode(token, key, Algorithm.HS256, options).flatMap(toCaller)
```

`Jwt.decode` checks the algorithm, the signature (in constant time), and then the claims you configure. It checks `exp` only when the token has one, so a token without `exp` never expires. `toCaller` closes that gap by requiring both `sub` and `exp`.

We build the verifier with the options our API needs: tokens from `bookstore-auth`, meant for `bookstore-api`, with 30 seconds of tolerance for clock differences between servers. `nowSeconds` fixes the clock one minute after the tokens were issued:

```scala mdoc:silent
val verify: Verifier = jwtVerifier(
  TestTokens.secret.getBytes(UTF_8),
  JwtDecodeOptions(
    issuer = Some("bookstore-auth"),
    expectedAudience = Some("bookstore-api"),
    clockSkewSeconds = 30,
    nowSeconds = Some(TestTokens.now + 60)
  )
)

def report(name: String, token: String): Unit =
  verify(token) match {
    case Right(caller) => println(s"$name: accepted $caller")
    case Left(error)   => println(s"$name: rejected ${error.getClass.getSimpleName}: ${error.message}")
  }
```

Running every test token through it accepts the two good ones and rejects each bad one with its own reason:

```scala mdoc
report("reader", TestTokens.reader)
report("admin", TestTokens.admin)
report("expired", TestTokens.expired)
report("other audience", TestTokens.otherAudience)
report("forged", TestTokens.forged)
report("never expires", TestTokens.neverExpires)
```

:::note
In production, leave `nowSeconds` unset. `Jwt.decode` then uses the system clock.
:::

## Answering 401, 403 or 404

A protected endpoint has three ways to say no, and HTTP clients react differently to each. RFC 6750, which defines bearer tokens over HTTP, sets the rules:

| Situation                                             | Status             | `WWW-Authenticate`                                          |
| ----------------------------------------------------- | ------------------ | ----------------------------------------------------------- |
| No token, or another scheme such as Basic             | `401 Unauthorized` | `Bearer realm="bookstore"`                                  |
| Token is forged, expired, for another audience, ...   | `401 Unauthorized` | `Bearer realm="bookstore", error="invalid_token"`           |
| Token is valid, but the caller lacks the needed role  | `403 Forbidden`    | `Bearer realm="bookstore", error="insufficient_scope"`      |

A 401 means "who are you?": the client should sign in, or refresh its token, and try again. Every 401 must carry a `WWW-Authenticate` challenge that names the scheme. A 403 means "I know who you are, and the answer is no": a new token for the same user does not help.

`Response.unauthorized` and `Response.forbidden` carry no headers, so we build the three responses ourselves and add the typed `Header.WWWAuthenticate` with `Response#addHeader`:

```scala mdoc:silent
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
```

The challenge for an invalid token renders like this:

```scala mdoc
println(invalidToken.header(Header.WWWAuthenticate).map(_.renderedValue))
```

The bodies are deliberately vague. Every bad token gets the same answer, so a caller cannot use the API to learn which check failed. The precise reason goes to the server log instead.

:::caution
`Header.WWWAuthenticate` writes each parameter value between double quotes as it is, without escaping, so a value that contains `"` produces an invalid header. Use fixed strings such as the ones above, and never copy a `JwtError` message or any request data into the challenge.
:::

Some APIs answer 404 instead, as if the endpoint did not exist, which is why `AuthType` defaults to `Status.NotFound`. That hides admin routes from someone probing the API, but it costs a lot: clients can no longer tell an expired token from a wrong URL, so they cannot refresh and retry, and debugging gets harder. If you hide endpoints, answer 404 for *every* rejection of that endpoint, including the 403 case, or the difference between the answers gives the secret away. For an API whose routes are documented anyway, prefer 401 and 403.

## Handing the Caller to the Handler

Now we combine the pieces into one wrapper. `authenticated` takes a verifier, an optional role, and a handler that receives the verified `Caller` along with the request. It returns a plain `Request => Response`, so it fits any router, and the handler cannot run without a verified caller:

```scala mdoc:silent
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
```

The rejection log names the kind of failure and, once the token is verified, the user. It never prints the token, which would let anyone who reads the log replay it. In a real service, send these lines to your logger instead of `println`.

The protected bookstore uses the caller's `subject` to pick the orders, so the `?user=` parameter is gone, and it asks for the `admin` role before changing a price:

```scala mdoc:silent
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
```

When we call it with no token, as a reader and as an admin, the server's log lines appear just before the response they explain:

```scala mdoc
JdkHttp.withServer(bookstoreApp(verify)) { port =>
  Client.call(port, "GET", "/books")
  Client.call(port, "GET", "/orders")
  Client.call(port, "GET", "/orders", authorization = s"Bearer ${TestTokens.reader}")
  Client.call(port, "PUT", "/books/1/price", "3999", authorization = s"Bearer ${TestTokens.reader}")
  Client.call(port, "PUT", "/books/1/price", "3999", authorization = s"Bearer ${TestTokens.admin}")
}
```

The public route still answers everyone, a missing token gets a challenge, the reader sees only their own orders and gets 403 on the admin route, and the admin can change the price.

## Loading the Key From Configuration

The signing key, the issuer and the audience belong in configuration, not in code. We describe them as a case class and keep the HMAC secret in a `Secret`, whose `toString` is always `<secret>`:

```scala mdoc:silent
final case class AuthConfig(issuer: String, audience: String, hmacSecret: Secret)

object AuthConfig {
  implicit val schema: Schema[AuthConfig] = Schema.derived
}
```

`Secret.unwrap` is the only way to read the value, and we call it exactly once, to turn the secret into key bytes. HS256 needs a key of at least 32 bytes, and `Jwt.decode` rejects a shorter one on every request, so we check the length once at startup and refuse to start instead:

```scala mdoc:silent
def verifierFrom(config: AuthConfig, nowSeconds: Option[Long] = None): Either[String, Verifier] = {
  val key = Secret.unwrap(config.hmacSecret).getBytes(UTF_8)
  if (key.length < 32) Left(s"hmacSecret has ${key.length} bytes; HS256 needs at least 32")
  else
    Right(
      jwtVerifier(
        key,
        JwtDecodeOptions(
          issuer = Some(config.issuer),
          expectedAudience = Some(config.audience),
          clockSkewSeconds = 30,
          nowSeconds = nowSeconds
        )
      )
    )
}
```

To keep the output stable, we load the configuration from a map with `ConfigSource.fromMap`. In production the same keys come from environment variables or a YAML or HOCON file, as [Build a Typed Configuration Loader](./typed-config-loader.md) shows. The `startWith` helper plays the startup of our service: it loads the configuration, builds the verifier and checks the reader's token:

```scala mdoc:silent
val settings: Map[String, String] = Map(
  "issuer"     -> "bookstore-auth",
  "audience"   -> "bookstore-api",
  "hmacSecret" -> TestTokens.secret
)

def startWith(settings: Map[String, String]): Unit =
  Config.load[AuthConfig](ConfigSource.fromMap(settings, "env")) match {
    case Left(errors)  => errors.foreach(error => println(s"cannot start: ${error.message}"))
    case Right(config) =>
      println(s"loaded $config")
      verifierFrom(config, nowSeconds = Some(TestTokens.now + 60)) match {
        case Left(problem) => println(s"cannot start: $problem")
        case Right(verify) => println(s"reader token: ${verify(TestTokens.reader)}")
      }
  }
```

Starting with the full settings, with no secret, and with a secret that is too short:

```scala mdoc
startWith(settings)
startWith(settings - "hmacSecret")
startWith(settings.updated("hmacSecret", "too-short"))
```

The loaded configuration prints with `<secret>` in place of the key, so it is safe to log at startup.

:::tip
For an RSA, EC or EdDSA key pair, the API needs only the public key, which is not secret: load it as a Base64-encoded X.509 DER `String`, decode it with `java.util.Base64`, and pass the matching `Algorithm` to `Jwt.decode`. Call `JvmJwtCryptoBackend.install()` once at startup to enable these algorithms. [How to Issue and Verify JWTs](./issue-and-verify-jwts.md) shows the key formats. The module has no JWK or JWKS support, so it cannot fetch keys from an identity provider's key-set URL: fetch and convert them yourself.
:::

## Calling the Protected API

With the verifier loaded from configuration, we can send the API every kind of request it will meet in practice: no token, a Basic header, each bad token from the test set, a reader's token and an admin's token:

```scala mdoc:silent
val configured: Verifier = Config
  .load[AuthConfig](ConfigSource.fromMap(settings, "env"))
  .left
  .map(_.map(_.message).mkString("; "))
  .flatMap(verifierFrom(_, nowSeconds = Some(TestTokens.now + 60)))
  .fold(sys.error, identity)

def bearer(token: String): String = s"Bearer $token"
```

Serving the bookstore with this verifier and calling `GET /orders` once per case:

```scala mdoc
JdkHttp.withServer(bookstoreApp(configured)) { port =>
  Client.call(port, "GET", "/orders")
  Client.call(port, "GET", "/orders", authorization = "Basic cmVhZGVyOnNlY3JldA==")
  Client.call(port, "GET", "/orders", authorization = bearer(TestTokens.forged))
  Client.call(port, "GET", "/orders", authorization = bearer(TestTokens.expired))
  Client.call(port, "GET", "/orders", authorization = bearer(TestTokens.otherAudience))
  Client.call(port, "GET", "/orders", authorization = bearer(TestTokens.neverExpires))
  Client.call(port, "GET", "/orders", authorization = bearer(TestTokens.reader))
}
```

A request with no usable token gets a plain challenge. Each bad token gets the same `invalid_token` answer, while the log records the precise reason: a forged signature, an expired token, the wrong audience, or a token with no expiry.

## What This Code Does Not Do

The wrapper verifies tokens; it is not a complete security setup. Before you rely on it, check these points:

- **Use HTTPS.** A bearer token works for whoever holds it. Over plain HTTP, anyone on the network path can copy it. The loopback server in this guide is for examples only.
- **No revocation.** A signed token stays valid until it expires. Keep `exp` short, minutes rather than days, and refresh tokens on the client.
- **No key rotation.** The verifier knows one key. To rotate, try the new key and then the old one until old tokens expire, and choose the key yourself: the module does not pick a key by the header's `kid`.
- **Roles are only as good as the issuer.** The API trusts the `roles` claim because the token is signed. Anyone who holds the signing key can mint an admin token, so prefer an asymmetric key pair when another service issues tokens: the API then holds only the public key.

## Putting It Together

The complete example loads the configuration, builds the verifier, serves the protected bookstore, and calls it with every token from this guide. It uses four shared files from [Running the Examples](#running-the-examples): the adapter `JdkHttp.scala`, the test client `Client.scala`, the model `Bookstore.scala` and the token issuer `TestTokens.scala`:

```scala mdoc:embed:jwt-examples/src/main/scala/protectendpointswithjwt/CompleteExample.scala
```

## Running the Examples

All code from this guide is available as runnable examples in the `jwt-examples` directory. It is a standalone sbt build that uses the published ZIO Blocks artifacts. To run them, clone the repository and move into that directory:

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks/jwt-examples
```

The examples share four files. `JdkHttp.scala` is the adapter from the JDK HTTP server guide, unchanged except for its package. `Client.scala` is that guide's test client with two additions: an `authorization` parameter, and printing the `WWW-Authenticate` header:

<details>
  <summary>jwt-examples/src/main/scala/protectendpointswithjwt/JdkHttp.scala</summary>

```scala mdoc:embed:jwt-examples/src/main/scala/protectendpointswithjwt/JdkHttp.scala:show-line-numbers
```

</details>

<details>
  <summary>jwt-examples/src/main/scala/protectendpointswithjwt/Client.scala</summary>

```scala mdoc:embed:jwt-examples/src/main/scala/protectendpointswithjwt/Client.scala:show-line-numbers
```

</details>

<details>
  <summary>jwt-examples/src/main/scala/protectendpointswithjwt/Bookstore.scala</summary>

```scala mdoc:embed:jwt-examples/src/main/scala/protectendpointswithjwt/Bookstore.scala:show-line-numbers
```

</details>

<details>
  <summary>jwt-examples/src/main/scala/protectendpointswithjwt/TestTokens.scala</summary>

```scala mdoc:embed:jwt-examples/src/main/scala/protectendpointswithjwt/TestTokens.scala:show-line-numbers
```

</details>

### Serve the Unprotected API

The first example serves the bookstore with no authentication and shows that anyone can read any user's orders and change a price:

<details>
  <summary>jwt-examples/src/main/scala/protectendpointswithjwt/Step1UnprotectedApiExample.scala</summary>

```scala mdoc:embed:jwt-examples/src/main/scala/protectendpointswithjwt/Step1UnprotectedApiExample.scala:show-line-numbers
```

</details>

To run it:

```bash
sbt "runMain protectendpointswithjwt.Step1UnprotectedApiExample"
```

### Read the Bearer Token

The second example reads the token from five different `Authorization` headers:

<details>
  <summary>jwt-examples/src/main/scala/protectendpointswithjwt/Step2ReadBearerTokenExample.scala</summary>

```scala mdoc:embed:jwt-examples/src/main/scala/protectendpointswithjwt/Step2ReadBearerTokenExample.scala:show-line-numbers
```

</details>

To run it:

```bash
sbt "runMain protectendpointswithjwt.Step2ReadBearerTokenExample"
```

### Verify the Token

The third example verifies every test token and prints the caller or the reason for the rejection:

<details>
  <summary>jwt-examples/src/main/scala/protectendpointswithjwt/Step3VerifyTokenExample.scala</summary>

```scala mdoc:embed:jwt-examples/src/main/scala/protectendpointswithjwt/Step3VerifyTokenExample.scala:show-line-numbers
```

</details>

To run it:

```bash
sbt "runMain protectendpointswithjwt.Step3VerifyTokenExample"
```

### Protect the Routes

The fourth example adds the 401 and 403 responses and the `authenticated` wrapper, and calls the protected API:

<details>
  <summary>jwt-examples/src/main/scala/protectendpointswithjwt/Step4ProtectedRoutesExample.scala</summary>

```scala mdoc:embed:jwt-examples/src/main/scala/protectendpointswithjwt/Step4ProtectedRoutesExample.scala:show-line-numbers
```

</details>

To run it:

```bash
sbt "runMain protectendpointswithjwt.Step4ProtectedRoutesExample"
```

### Load the Key From Configuration

The fifth example loads the issuer, the audience and the secret with `Config.load`, and refuses to start without a usable secret:

<details>
  <summary>jwt-examples/src/main/scala/protectendpointswithjwt/Step5KeyFromConfigExample.scala</summary>

```scala mdoc:embed:jwt-examples/src/main/scala/protectendpointswithjwt/Step5KeyFromConfigExample.scala:show-line-numbers
```

</details>

To run it:

```bash
sbt "runMain protectendpointswithjwt.Step5KeyFromConfigExample"
```

### The Complete Example

To run the complete example shown under [Putting It Together](#putting-it-together):

```bash
sbt "runMain protectendpointswithjwt.CompleteExample"
```

### Compile All Examples

To compile all the examples without running them:

```bash
sbt compile
```

## Going Further

- [How to Issue and Verify JWTs](./issue-and-verify-jwts.md) covers asymmetric keys, PEM files, hostile-token limits and every `JwtError` case.
- The [JWT reference](../reference/jwt.md) lists every algorithm, key size rule and decode option.
- The [`AuthType` reference](../reference/endpoint/auth-type.md) shows the other schemes (Basic, Digest, custom headers) and how to combine them.
- The [headers reference](../reference/http-model/headers.md) lists the typed headers, including `Header.Authorization` and `Header.WWWAuthenticate`.
- To protect many routes, give `authenticated` to your router once per route group, or route with [`RoutePattern`](../reference/endpoint/route-pattern.md) and wrap the matched handler.
