---
id: protect-pages-with-basic-auth-or-a-session-cookie
title: "How to Protect Pages with Basic Auth or a Session Cookie"
description: "Protect http-model pages two ways: check HTTP Basic credentials with a 401 challenge, or log in with a form and an HttpOnly session cookie."
keywords:
  - "Authentication"
  - "Basic Authentication"
  - "Session Cookie"
  - "Login Form"
  - "ResponseCookie"
  - "WWW-Authenticate"
---

In this guide, we protect the staff pages of a small coffee roastery's web shop so that only staff can read the open orders. We do it in the two ways most web applications use: HTTP Basic authentication, where the browser asks for a username and password and sends them with every request, and a login form that sets a session cookie. By the end, you will have a server that sends people to a sign-in page, keeps them signed in with an `HttpOnly` cookie, signs them out on request, and still lets scripts download the orders with Basic credentials.

We start from pages with no protection at all and add one piece at a time:

- Check a password against a stored hash.
- Read Basic credentials and answer `401 Unauthorized` with a `WWW-Authenticate` challenge.
- Issue a random session id in a `ResponseCookie` with the right security attributes.
- Log in with an HTML form, read the cookie back on every request, and log out.
- Choose between the two approaches.

Every section that serves requests starts a real server on a free loopback port, calls it, and prints what came back.

## The Problem

The roastery's staff page lists the open orders, with the customers' names. It must not be public, but the server has no idea who is asking: an HTTP request carries only what the client chooses to send.

A first attempt often lets the login form set a cookie such as `user=ada`, and lets the staff page trust it. Here is that page handler, written against the JDK HTTP server API:

```scala mdoc:compile-only
import com.sun.net.httpserver.{HttpExchange, HttpHandler}
import java.nio.charset.StandardCharsets.UTF_8

val adminHandler: HttpHandler = (exchange: HttpExchange) => {
  val cookies  = Option(exchange.getRequestHeaders.getFirst("Cookie")).getOrElse("")
  val signedIn = cookies.split(";").map(_.trim).contains("user=ada") // Set by the login form after a correct password
  val (status, body) =
    if (signedIn) (200, "Open orders: #101 Lena, #102 Marco, #103 Priya")
    else (403, "Forbidden")
  val bytes = body.getBytes(UTF_8)
  exchange.sendResponseHeaders(status, bytes.length.toLong)
  exchange.getResponseBody.write(bytes)
  exchange.close()
}
```

Anyone can send `Cookie: user=ada` without ever seeing the login form, so this page is not protected at all. The cookie also never expires on the server, signing out cannot revoke it, and a browser that is not signed in gets a bare 403 instead of the sign-in page.

ZIO Blocks gives us the typed pieces: `Header.Authorization.Basic` decodes Basic credentials, `Header.WWWAuthenticate` writes the challenge, `ResponseCookie` renders a cookie with its attributes, `Request#cookies` reads cookies back, and `Form` decodes a submitted form. It does not store users, check passwords or keep sessions: that is application code, and this guide shows it.

## Prerequisites

Add the HTTP model and the HTML module to your `build.sbt`. The HTML module builds the pages and escapes the text we put in them:

```scala
// Scala 2.13 and Scala 3.3 to 3.8
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-http-model" % "@VERSION@",
  "dev.zio" %% "zio-blocks-html"       % "@VERSION@"
)

// Scala 3.9 and newer
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-blocks-next-http-model" % "@VERSION@",
  "dev.zio" %% "zio-blocks-next-html"       % "@VERSION@"
)
scalacOptions += "-experimental"
```

The code is the same for every Scala version. Password hashing, random session ids and constant-time comparison come from the JDK (`javax.crypto`, `java.security`), because ZIO Blocks has no module for them. The HTTP model depends on the ZIO Blocks streams module, which is compiled for Java 21, so run the program on **JDK 21 or newer**.

You should know the request and response types from the [HTTP model reference](../reference/http-model/model.md), and the HTML DSL from [Render Your First Server-Side HTML Page](./your-first-html-page.md). The 401 answer follows the pattern of [How to Protect an Endpoint with JWT Bearer Tokens](./protect-endpoints-with-jwt.md).

ZIO Blocks has no HTTP server. We serve requests with the small adapter from the guide [How to Serve http-model Requests with the JDK HTTP Server](./serve-http-model-requests.md): `JdkHttp.withServer(app)(use)` starts the JDK's built-in server on a free loopback port, runs `use` with the port, and stops the server. `Client.call(port, method, target, form, authorization, cookie)` sends one request and prints the status, the `WWW-Authenticate`, `Location` and `Set-Cookie` headers when there are any, and the body. `Client.logIn(port, username, password)` posts the login form and returns the session cookie, ready to send back. Both files are embedded at the end of this section; copy them into your project.

We use these imports throughout the guide:

```scala mdoc:silent
import java.nio.charset.StandardCharsets.UTF_8
import java.security.{MessageDigest, SecureRandom}
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import scala.collection.concurrent.TrieMap
import zio.blocks.html._
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
      try writeResponse(exchange, response)
      catch {
        case NonFatal(error) =>
          System.err.println(s"Writing the response failed: $error")
          if (exchange.getResponseCode == -1) {
            exchange.getResponseHeaders.clear()
            exchange.sendResponseHeaders(Status.InternalServerError.code, -1L)
          }
      }
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
```

<details>
  <summary>The shared adapter, unchanged from the serving guide except for its package: <code>JdkHttp.scala</code></summary>

```scala mdoc:embed:http-model-examples/src/main/scala/protectpageswithbasicauthorasessioncookie/JdkHttp.scala:show-line-numbers
```

</details>

<details>
  <summary>The shared test client, which sends a form, an <code>Authorization</code> header or a cookie: <code>Client.scala</code></summary>

```scala mdoc:embed:http-model-examples/src/main/scala/protectpageswithbasicauthorasessioncookie/Client.scala:show-line-numbers
```

</details>

## The Roastery Admin Area

The roastery has a public home page, a staff page at `/admin` that lists the open orders, and a CSV export of the same orders at `/orders.csv` for spreadsheets and scripts. The pages are built with the HTML DSL, which escapes every string we pass as text, so a customer's name cannot inject markup. Nothing in this model knows about authentication:

```scala mdoc:silent
final case class Order(id: Int, customer: String, coffee: String, grams: Int)

object Roastery {
  val openOrders: List[Order] = List(
    Order(101, "Lena", "Ethiopia Guji", 250),
    Order(102, "Marco", "Colombia Huila", 1000),
    Order(103, "Priya", "Kenya Nyeri", 500)
  )

  def htmlResponse(page: Dom.Element): Response =
    Response.ok(Body.fromArray((doctype.render + page.render(2)).getBytes(UTF_8), ContentType.`text/html`))

  def page(heading: String, content: Dom.Element*): Response =
    htmlResponse(
      html(lang := "en")(
        head(meta(charset := "utf-8"), title(heading)),
        body(h1(heading), content)
      )
    )

  val home: Response = page("Fresh Roasts Every Tuesday", a(href := "/admin")("Staff area"))

  // `note` says who is signed in; the session sections put a sign-out button in it.
  def ordersPage(note: Dom.Element): Response =
    page("Open Orders", ul(openOrders.map(o => li(s"#${o.id} ${o.customer}: ${o.coffee}, ${o.grams} g"))), note)

  val ordersCsv: Response =
    Response
      .text(("id,customer,coffee,grams" :: openOrders.map(o => s"${o.id},${o.customer},${o.coffee},${o.grams}")).mkString("\n"))
      .setHeader("content-type", "text/csv; charset=utf-8")

  def notFound(request: Request): Response =
    Response.text(Status.NotFound, s"No route for ${request.method.name} ${request.path.encode}")
}

import Roastery._
```

The routing matches on the method and the path segments:

```scala mdoc:silent
def unprotectedApp(request: Request): Response =
  (request.method, request.path.segments.toList) match {
    case (Method.GET, Nil)                => home
    case (Method.GET, List("admin"))      => ordersPage(p("Anyone can read this page."))
    case (Method.GET, List("orders.csv")) => ordersCsv
    case _                                => notFound(request)
  }
```

When we serve it and ask for the staff page and the export without any credentials, both answer with every customer's order:

```scala mdoc
JdkHttp.withServer(unprotectedApp) { port =>
  Client.call(port, "GET", "/admin")
  Client.call(port, "GET", "/orders.csv")
}
```

The rest of the guide keeps `/` public and lets only signed-in staff reach `/admin` and `/orders.csv`.

## Checking a Password

Both approaches end in the same question: is this the right password for this user? The server should never store the passwords themselves, only a slow, salted hash of each one, so that a leaked user table does not leak the passwords. The JDK ships PBKDF2, a standard password-hashing function: it runs HMAC-SHA256 hundreds of thousands of times over the password and a random salt. We use 600,000 iterations, the OWASP recommendation for PBKDF2-HMAC-SHA256:

```scala mdoc:silent
final case class PasswordHash(salt: Array[Byte], hash: Array[Byte])

object PasswordHash {
  val iterations: Int = 600000

  private val random = new SecureRandom()

  def derive(password: String, salt: Array[Byte]): Array[Byte] = {
    val spec = new PBEKeySpec(password.toCharArray, salt, iterations, 256)
    try SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded
    finally spec.clearPassword()
  }

  def create(password: String): PasswordHash = {
    val salt = new Array[Byte](16)
    random.nextBytes(salt)
    PasswordHash(salt, derive(password, salt))
  }

  // MessageDigest.isEqual compares every byte, however early the first difference is.
  def matches(password: String, stored: PasswordHash): Boolean =
    MessageDigest.isEqual(derive(password, stored.salt), stored.hash)
}
```

`MessageDigest.isEqual` takes the same time wherever the first differing byte is, so the response time does not tell an attacker how close a guess came. `==` on strings stops at the first difference.

The staff accounts map each username to its hash. When the username is unknown, `Staff.authenticate` still checks the password against a dummy hash, so that a wrong username takes as long as a wrong password and the response time does not reveal which usernames exist:

```scala mdoc:silent
object Staff {
  // Sample data only: a real service loads the salts and hashes from its user store and never sees the passwords.
  private val accounts: Map[String, PasswordHash] = Map(
    "ada"   -> PasswordHash.create("correct horse battery staple"),
    "grace" -> PasswordHash.create("tea and biscuits at four")
  )

  private val unknownUser: PasswordHash = PasswordHash.create("no such user")

  def authenticate(username: String, password: String): Option[String] = {
    val passwordMatches = PasswordHash.matches(password, accounts.getOrElse(username, unknownUser))
    if (passwordMatches && accounts.contains(username)) Some(username) else None
  }
}
```

The right password gives the username; a wrong password and an unknown user both give `None`:

```scala mdoc
println(Staff.authenticate("ada", "correct horse battery staple"))
println(Staff.authenticate("ada", "Correct horse battery staple"))
println(Staff.authenticate("mallory", "correct horse battery staple"))
```

:::note
Each check takes a noticeable fraction of a second on purpose: that cost is what slows down an attacker who has stolen the hashes. Keep it in mind for Basic authentication, which checks the password on every request.
:::

## Reading Basic Credentials

With HTTP Basic authentication, the client sends `Authorization: Basic <credentials>` on every request, where `<credentials>` is `username:password` in Base64. The HTTP model already decodes it: `Request#header(Header.Authorization)` returns a `Header.Authorization.Basic` with the username and the password, split at the first colon, so a password may contain colons. We only need to pick that case:

```scala mdoc:silent
def basicCredentials(request: Request): Option[Header.Authorization.Basic] =
  request.header(Header.Authorization).collect { case basic: Header.Authorization.Basic => basic }
```

Base64 is an encoding, not encryption. `Header.Authorization.Basic` also renders the header for a client:

```scala mdoc:silent
val adaHeader = Header.Authorization.Basic("ada", "correct horse battery staple").renderedValue
```

Printing the header and decoding it again shows the password in plain text:

```scala mdoc
println(adaHeader)
println(new String(Base64.getDecoder.decode(adaHeader.stripPrefix("Basic ")), UTF_8))
```

Anyone who can read the request can read the password, so serve Basic authentication only over HTTPS.

To see how different headers come out, we build a few requests by hand, with no server involved. The `describe` helper prints the username and only the length of the password:

```scala mdoc:silent
def adminRequest(authorization: Option[String]): Request = {
  val request = Request.get(URL.parse("/admin").fold(error => sys.error(error), url => url))
  authorization.fold(request)(value => request.addHeader("authorization", value))
}

def describe(request: Request): String =
  basicCredentials(request).fold("no Basic credentials") { basic =>
    s"user ${basic.username}, password of ${basic.password.length} characters"
  }

def base64(text: String): String = Base64.getEncoder.encodeToString(text.getBytes(UTF_8))
```

The scheme name is case-insensitive and a colon in the password survives. Invalid Base64, another scheme and a missing header all give `None`:

```scala mdoc
println(describe(adminRequest(Some(adaHeader))))
println(describe(adminRequest(Some("basic " + base64("ada:correct horse battery staple")))))
println(describe(adminRequest(Some("Basic " + base64("grace:tea:and:biscuits")))))
println(describe(adminRequest(Some("Basic not-base64!"))))
println(describe(adminRequest(Some("Bearer eyJhbGciOiJIUzI1NiJ9.e30.c2ln"))))
println(describe(adminRequest(None)))
```

:::warning
`Header.Authorization.Basic` is a case class, so its `toString` prints the password. Never log the header, the request's headers, or a `Header.Authorization.Basic` value.
:::

If you describe your API with the [endpoint module](../reference/endpoint/index.md), [`AuthType.Basic`](../reference/endpoint/auth-type.md) declares that an endpoint needs Basic credentials and extracts them with the same `Header.Authorization.Basic`. Like `AuthType.Bearer` in the JWT guide, it checks nothing, and its default failure status is `404 Not Found`. Checking the credentials is the code in this guide.

## Asking for Credentials With a 401 Challenge

When a request has no credentials or wrong ones, the server answers `401 Unauthorized` with a `WWW-Authenticate` header that names the scheme. RFC 7617, which defines Basic authentication, adds a `realm`, the name of the protected area that a browser shows in its sign-in dialog, and an optional `charset="UTF-8"` that asks the client to encode non-ASCII usernames and passwords as UTF-8, which is what the HTTP model decodes. `Response.unauthorized` carries no headers, so we build the answer ourselves, as the JWT guide does:

```scala mdoc:silent
val realm = "Roastery staff"

val basicChallenge: Response =
  Response
    .text(Status.Unauthorized, "Sign in to see the roastery's orders")
    .addHeader(Header.WWWAuthenticate("Basic", Map("realm" -> realm, "charset" -> "UTF-8")))
```

The challenge renders like this:

```scala mdoc
println(basicChallenge.header(Header.WWWAuthenticate).map(_.renderedValue))
```

:::caution
`Header.WWWAuthenticate` writes each parameter value between double quotes as it is, without escaping, a known bug in 0.0.56. A realm that contains `"` or `\` produces an invalid header, and one that contains a comma does not parse back. Use a fixed realm such as the one above, and never copy request data into it.
:::

The `basicAuth` wrapper answers the challenge when there are no Basic credentials, checks them otherwise, and hands the authenticated username to the handler. Wrong credentials get the same challenge, so the browser asks again:

```scala mdoc:silent
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
```

We protect both the staff page and the export with it:

```scala mdoc:silent
def basicApp(request: Request): Response =
  (request.method, request.path.segments.toList) match {
    case (Method.GET, Nil)                => home
    case (Method.GET, List("admin"))      => basicAuth((user, _) => ordersPage(p(s"Signed in as $user")))(request)
    case (Method.GET, List("orders.csv")) => basicAuth((_, _) => ordersCsv)(request)
    case _                                => notFound(request)
  }

def basic(username: String, password: String): String =
  Header.Authorization.Basic(username, password).renderedValue
```

When we call it with no credentials, a wrong password and Grace's account, the server's log line appears just before the response it explains:

```scala mdoc
JdkHttp.withServer(basicApp) { port =>
  Client.call(port, "GET", "/admin")
  Client.call(port, "GET", "/admin", authorization = basic("grace", "coffee and cake"))
  Client.call(port, "GET", "/admin", authorization = basic("grace", "tea and biscuits at four"))
  Client.call(port, "GET", "/orders.csv", authorization = basic("ada", "correct horse battery staple"))
}
```

A browser that gets the 401 shows its own sign-in dialog, remembers what the user typed, and sends it with later requests to the same protected area. `curl -u ada:'correct horse battery staple' http://localhost:8080/orders.csv` does the same for a script.

## Issuing a Session Cookie

Basic authentication has no sign-in page of our own, and no way to sign out: the browser keeps sending the password until it is closed. A login form with a session cookie fixes both. After a correct password, the server creates a session, sends its id to the browser in a cookie, and the browser sends the cookie back with every request. The cookie holds only a random id, never the username or the password, so a client cannot forge or edit it, although anyone who steals a valid id can use it until the session ends.

The session store keeps each session's user and expiry time, keyed by its id. The id is 32 bytes from `SecureRandom`, encoded as Base64url, which uses only characters that are valid in a cookie value. `TrieMap` is a thread-safe map, which matters once requests run in parallel:

```scala mdoc:silent
final case class Session(user: String, expiresAtSeconds: Long)

object Sessions {
  val lifetimeSeconds: Long = 30 * 60

  private val random = new SecureRandom()
  private val active = TrieMap.empty[String, Session]

  private def nowSeconds: Long = System.currentTimeMillis() / 1000

  def start(user: String): String = {
    val bytes = new Array[Byte](32)
    random.nextBytes(bytes)
    val id = Base64.getUrlEncoder.withoutPadding.encodeToString(bytes)
    active.put(id, Session(user, nowSeconds + lifetimeSeconds))
    id
  }

  def find(id: String): Option[Session] =
    active.get(id) match {
      case Some(session) if session.expiresAtSeconds > nowSeconds => Some(session)
      case Some(_)                                                =>
        active.remove(id)
        None
      case None => None
    }

  def end(id: String): Unit = {
    active.remove(id)
    ()
  }
}
```

The session expires on the server after 30 minutes, whatever the browser does with the cookie.

`ResponseCookie` describes the cookie and its attributes; `Response#addCookie` renders it into a `Set-Cookie` header. Each attribute closes a door:

| Attribute      | `ResponseCookie` field           | Effect                                                                                                                                |
| -------------- | -------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| `HttpOnly`     | `isHttpOnly = true`              | JavaScript cannot read the cookie, so an injected script cannot steal the session.                                                    |
| `Secure`       | `isSecure = true`                | The browser sends the cookie only over HTTPS. Chrome and Firefox also treat `http://localhost` as secure, so the example works there. |
| `SameSite=Lax` | `sameSite = Some(SameSite.Lax)`  | The browser leaves the cookie off cross-site requests such as a form posted from another site.                                        |
| `Path=/`       | `path = Some(Path.root)`         | The cookie is sent to every path of the site, not only to the path that set it.                                                       |
| `Max-Age=1800` | `maxAge = Some(lifetimeSeconds)` | The browser drops the cookie after 30 minutes, when the server also expires the session.                                              |

The cookie for a session id:

```scala mdoc:silent
val sessionCookieName = "roastery_session"

def sessionCookie(id: String): ResponseCookie =
  ResponseCookie(
    sessionCookieName,
    id,
    path = Some(Path.root),
    maxAge = Some(Sessions.lifetimeSeconds),
    isSecure = true,
    isHttpOnly = true,
    sameSite = Some(SameSite.Lax)
  )
```

Starting a session for Ada and rendering its cookie shows the random id and every attribute:

```scala mdoc
println(Cookie.renderResponse(sessionCookie(Sessions.start("ada"))))
```

:::warning
Never put a password, or anything you would not show the user, in a cookie. The browser stores cookies in plain text, and every request carries them.
:::

## Logging In With a Form

The sign-in page is an HTML form that posts the username and the password to `/login`. The `password` input type keeps the password hidden while it is typed:

```scala mdoc:silent
def loginPage(message: String): Response =
  page(
    "Staff Sign-In",
    p(message),
    form(method := "post", action := "/login")(
      label("Username ", input(name := "username")),
      label("Password ", input(name := "password", typeAttr := "password")),
      button("Sign in")
    )
  )
```

The browser sends the form as an `application/x-www-form-urlencoded` body, which `Form.fromString` decodes. After a correct password, `logIn` starts a session and answers `303 See Other` with the session cookie, which sends the browser on to `/admin` with a `GET`:

```scala mdoc:silent
def logIn(request: Request): Response = {
  val submitted = Form.fromString(request.body.text)
  val user      = for {
    username <- submitted.get("username")
    password <- submitted.get("password")
    user     <- Staff.authenticate(username, password)
  } yield user
  user match {
    case Some(name) => Response.seeOther("/admin").addCookie(sessionCookie(Sessions.start(name)))
    case None       => loginPage("Wrong username or password.").copy(status = Status.Forbidden)
  }
}
```

Every successful login starts a new session with a new id, even when the browser already sent one. A server that kept an id it got before login would let an attacker who planted that id in a victim's browser share the victim's session. A failed login gets the form back with `403 Forbidden`, not `401 Unauthorized`: a 401 must carry a `WWW-Authenticate` challenge, and a login form is not an HTTP authentication scheme. The message does not say whether the username or the password was wrong.

To try the handler on its own, we serve `logIn` for every path and post a wrong password, then the right one:

```scala mdoc
JdkHttp.withServer(logIn) { port =>
  Client.call(port, "POST", "/login", Form("username" -> "ada", "password" -> "espresso"))
  Client.call(port, "POST", "/login", Form("username" -> "ada", "password" -> "correct horse battery staple"))
}
```

:::tip
Use `Response.seeOther` after a form post. `Response.redirect` answers `307 Temporary Redirect`, which makes the browser post the form, password included, to the new location again.
:::

## Reading the Session Cookie

On every later request, `Request#cookies` gives the cookies the browser sent. We look for ours and find its session:

```scala mdoc:silent
def sessionId(request: Request): Option[String] =
  request.cookies.find(_.name == sessionCookieName).map(_.value)
```

The `signedIn` wrapper hands the session to the handler. A request with no cookie, an unknown id or an expired session is sent to the sign-in page with `303 See Other`, which is what a person in a browser needs; a 401 would only show them an error:

```scala mdoc:silent
def signedIn(handler: (Session, Request) => Response): Request => Response =
  request =>
    sessionId(request).flatMap(Sessions.find) match {
      case Some(session) => handler(session, request)
      case None          => Response.seeOther("/login")
    }
```

The staff page now shows who is signed in, next to a sign-out button that posts to `/logout`, which the next section implements:

```scala mdoc:silent
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
```

We ask for the staff page without a cookie, sign in, ask again with the cookie from the login, and then with an id that the server never issues:

```scala mdoc
JdkHttp.withServer(pagesApp) { port =>
  Client.call(port, "GET", "/admin")
  val cookie = Client.logIn(port, "ada", "correct horse battery staple")
  Client.call(port, "GET", "/admin", cookie = cookie)
  Client.call(port, "GET", "/admin", cookie = s"$sessionCookieName=made-up-session-id")
}
```

Only the id from the login opens the page. A made-up id finds no session, so the cookie cannot be guessed or forged.

## Logging Out

Signing out has two parts: forget the session on the server, and tell the browser to drop the cookie. A `Set-Cookie` with the same name, domain and path, an empty value and `Max-Age=0` replaces the cookie and expires it at once:

```scala mdoc:silent
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
```

Sign-out is a `POST`, not a link. With `SameSite=Lax`, the browser leaves the cookie off a `POST` from another site, so a malicious page cannot sign the user out; a `GET` route could be triggered by any `<img>` tag. Ending the session on the server matters most: the cookie the browser had stops working even if a copy of it survives somewhere, as the last request shows:

```scala mdoc
JdkHttp.withServer(staffApp) { port =>
  val cookie = Client.logIn(port, "grace", "tea and biscuits at four")
  Client.call(port, "POST", "/logout", cookie = cookie)
  Client.call(port, "GET", "/admin", cookie = cookie)
}
```

## Choosing Between Basic Auth and a Session Cookie

Both approaches check the same password; they differ in how the client proves who it is on the next request:

| Question                        | Basic authentication                                   | Session cookie                                            |
| ------------------------------- | ------------------------------------------------------ | --------------------------------------------------------- |
| Who is the client?              | Scripts, `curl`, internal tools                        | People in a browser                                       |
| Sign-in page                    | The browser's own dialog                               | Your own HTML form                                        |
| What travels on every request   | The password                                           | A random session id                                       |
| Password check                  | On every request                                       | Once, at login                                            |
| Sign out                        | Not possible; the browser forgets when it is closed    | Yes, and the server revokes the session                   |
| State on the server             | None                                                   | A session store                                           |
| Unauthenticated answer          | `401` with `WWW-Authenticate: Basic`                   | `303 See Other` to the sign-in page                       |

Many applications use both, as the complete example does: a login form for people, and Basic credentials for the script that downloads the export.

:::tip
A signed JWT can stand in for the random id: the cookie then carries the user and the expiry itself, and the server needs no session store. The price is that the server cannot revoke one token before it expires, so signing out only deletes the browser's copy. If you go that way, sign and verify the token as [How to Issue and Verify JWTs](./issue-and-verify-jwts.md) and [How to Protect an Endpoint with JWT Bearer Tokens](./protect-endpoints-with-jwt.md) show, and keep the cookie attributes from this guide.
:::

## What This Code Does Not Do

The code in this guide checks credentials; it is not a complete security setup. Before you rely on it, check these points:

- **Use HTTPS.** Basic credentials and session ids work for whoever holds them, and over plain HTTP anyone on the network path can copy them. The loopback server in this guide is for examples only.
- **No rate limiting.** Nothing stops a client from trying thousands of passwords. Limit failed logins per account and per address.
- **One server, in memory.** The session store lives in one JVM: a restart signs everyone out, and two instances behind a load balancer do not share sessions. Use a shared store in that case.
- **Cross-site requests.** `SameSite=Lax` keeps the cookie off cross-site `POST` requests in current browsers, which protects the form routes. It still sends the cookie on cross-site links, so never change data in a `GET` route. For older browsers, add a CSRF token to every form.
- **Caching.** A shared cache could store a private page. Add `Cache-Control: no-store` to responses that show private data.

## Putting It Together

The complete example serves the whole roastery on port 8080 until you stop it with Ctrl+C: `staffApp` from the last section answers `/`, `/login`, `/admin` and `/logout`, and `/orders.csv` goes through `basicAuth` instead. Its `main` starts the server with `JdkHttp.start(app, 8080)` and blocks the main thread with `Thread.currentThread.join()`. It builds on the adapter and the client embedded under Prerequisites, and on the code of the earlier sections, which the examples keep in one file per section: `Roastery.scala`, `Staff.scala`, `BasicAuth.scala`, `Sessions.scala`, `Login.scala`, `SessionPages.scala` and `Logout.scala`. The complete example:

```scala mdoc:embed:http-model-examples/src/main/scala/protectpageswithbasicauthorasessioncookie/CompleteExample.scala
```

While it runs, open `http://localhost:8080/admin` in a browser to be sent to the sign-in page, or call it with `curl`. `curl` does not store cookies unless you give it a cookie jar, so the session cookie is passed by hand with `-b`. One run, with the `Date` headers removed:

```text
$ curl -s -i http://localhost:8080/admin
HTTP/1.1 303 See Other
Content-length: 0
Location: /login

$ curl -s -i --data-urlencode username=ada --data-urlencode "password=correct horse battery staple" http://localhost:8080/login
HTTP/1.1 303 See Other
Content-length: 0
Location: /admin
Set-cookie: roastery_session=IHUNG5YYpbQrXnh9LySKLtUKrME9QUl2H-QNCbDkt3c; Path=/; Max-Age=1800; Secure; HttpOnly; SameSite=Lax

$ curl -s -i -b roastery_session=IHUNG5YYpbQrXnh9LySKLtUKrME9QUl2H-QNCbDkt3c http://localhost:8080/admin
HTTP/1.1 200 OK
Content-type: text/html
Content-length: 433

<!DOCTYPE html><html lang="en">
  <head>
    <meta charset="utf-8"/>
    <title>Open Orders</title>
  </head>
  <body>
    <h1>Open Orders</h1>
    <ul>
      <li>#101 Lena: Ethiopia Guji, 250 g</li>
      <li>#102 Marco: Colombia Huila, 1000 g</li>
      <li>#103 Priya: Kenya Nyeri, 500 g</li>
    </ul>
    <form method="post" action="/logout">
      Signed in as ada 
      <button>Sign out</button>
    </form>
  </body>
</html>
$ curl -s -i http://localhost:8080/orders.csv
HTTP/1.1 401 Unauthorized
Www-authenticate: Basic realm="Roastery staff", charset="UTF-8"
Content-type: text/plain; charset=UTF-8
Content-length: 36

Sign in to see the roastery's orders
$ curl -s -i -u ada:wrong http://localhost:8080/orders.csv
HTTP/1.1 401 Unauthorized
Www-authenticate: Basic realm="Roastery staff", charset="UTF-8"
Content-type: text/plain; charset=UTF-8
Content-length: 36

Sign in to see the roastery's orders
$ curl -s -i -u "ada:correct horse battery staple" http://localhost:8080/orders.csv
HTTP/1.1 200 OK
Content-type: text/csv; charset=utf-8
Content-length: 107

id,customer,coffee,grams
101,Lena,Ethiopia Guji,250
102,Marco,Colombia Huila,1000
103,Priya,Kenya Nyeri,500
$ curl -s -i -X POST -b roastery_session=IHUNG5YYpbQrXnh9LySKLtUKrME9QUl2H-QNCbDkt3c http://localhost:8080/logout
HTTP/1.1 303 See Other
Content-length: 0
Location: /
Set-cookie: roastery_session=; Path=/; Max-Age=0; Secure; HttpOnly; SameSite=Lax

$ curl -s -i -b roastery_session=IHUNG5YYpbQrXnh9LySKLtUKrME9QUl2H-QNCbDkt3c http://localhost:8080/admin
HTTP/1.1 303 See Other
Content-length: 0
Location: /login
```

The server prints `Serving http://localhost:8080/ (Ctrl+C to stop)` at startup and `auth: wrong Basic credentials` for the `ada:wrong` request. The JDK server capitalizes header names, so `www-authenticate` arrives as `Www-authenticate`; header names are case-insensitive. The last request shows the server-side logout: the old cookie no longer opens the staff page.

## Running the Examples

All runnable code from this guide is in [`http-model-examples/src/main/scala/protectpageswithbasicauthorasessioncookie/`](https://github.com/zio/zio-blocks/tree/main/http-model-examples/src/main/scala/protectpageswithbasicauthorasessioncookie). Run any file from `http-model-examples` with `sbt "runMain protectpageswithbasicauthorasessioncookie.<ObjectName>"`.

## Going Further

- [How to Protect an Endpoint with JWT Bearer Tokens](./protect-endpoints-with-jwt.md) protects a JSON API with bearer tokens and explains when to answer 401, 403 or 404.
- [How to Issue and Verify JWTs](./issue-and-verify-jwts.md) covers signing keys and token checks, if you carry a JWT in the session cookie.
- The [HTTP model reference](../reference/http-model/model.md) documents `RequestCookie`, `ResponseCookie`, `SameSite` and `Form`.
- The [headers reference](../reference/http-model/headers.md) lists the typed headers, including `Header.Authorization` and `Header.WWWAuthenticate`.
- The [`AuthType` reference](../reference/endpoint/auth-type.md) shows how to declare Basic authentication on an endpoint description.
