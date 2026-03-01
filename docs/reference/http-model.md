---
id: http-model
title: "HTTP Model"
---

`zio-http-model` is a **pure, zero-dependency HTTP data model** for building HTTP clients and servers. It provides immutable types representing requests, responses, headers, URLs, paths, query parameters, and all HTTP primitives.

The module is designed as a pure data layer:

- **Zero effects**: no streaming, no I/O, no mutable state (except monotonic lazy-parse caches in `Headers`)
- **Zero ZIO dependency**: uses `zio.blocks.chunk.Chunk`, not `zio.Chunk`
- **Single encoding contract**: `Path` and `QueryParams` store values decoded internally, encode only on output
- **Cross-platform**: JVM and Scala.js support
- **Cross-version**: Scala 2.13 and 3.x support

```scala
package zio.http

// Core request/response types
final case class Request(method: Method, url: URL, headers: Headers, body: Body, version: Version)
final case class Response(status: Status, headers: Headers, body: Body, version: Version)

// URL structure
final case class URL(scheme: Option[Scheme], host: Option[String], port: Option[Int],
                     path: Path, queryParams: QueryParams, fragment: Option[String])

final case class Path(segments: Chunk[String], hasLeadingSlash: Boolean, trailingSlash: Boolean)
final class QueryParams private[http] (...)

// HTTP primitives
sealed abstract class Method(val name: String, val ordinal: Int)
opaque type Status = Int  // Scala 3
sealed abstract class Version(val major: Int, val minor: Int)
sealed trait Scheme

// Headers and body
final class Headers private[http] (...)
sealed trait Header
final class Body private (val data: Array[Byte], val contentType: Option[ContentType])

// Supporting types
final case class ContentType(mediaType: MediaType, boundary: Option[Boundary], charset: Option[Charset])
final case class ResponseCookie(...), RequestCookie(name: String, value: String)
final case class Form(entries: Chunk[(String, String)])
```

## Motivation

HTTP libraries often couple protocol concerns with effects and streaming, making it difficult to:

- Share data structures across client and server implementations
- Serialize requests/responses for caching or testing
- Work with HTTP primitives without committing to a specific effect system

`zio-http-model` solves this by providing pure data types that:

- Represent all HTTP concepts as immutable values
- Encode only on output (decode on input, store decoded)
- Parse incrementally with lazy caching (`Headers` parses typed headers on first access and caches the result)
- Work with any effect system or none at all

## Installation

Add the following to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-http-model" % "<version>"
```

For cross-platform projects (Scala.js):

```scala
libraryDependencies += "dev.zio" %%% "zio-http-model" % "<version>"
```

Supported Scala versions: 2.13.x and 3.x

## Quick Start

Creating a request with query parameters:

```scala mdoc:compile-only
import zio.http._

val url = URL.parse("https://api.example.com/users?active=true").toOption.get
val request = Request.get(url)

val withHeader = request.copy(
  headers = request.headers.add("authorization", "Bearer token123")
)
```

Creating a JSON response:

```scala mdoc:compile-only
import zio.http._

val jsonBody = Body.fromString("""{"message":"ok"}""", Charset.UTF8)
val response = Response(
  status = Status.Ok,
  body = jsonBody,
  headers = Headers("content-type" -> "application/json")
)
```

## Method

`Method` represents standard HTTP methods as case objects:

```scala
sealed abstract class Method(val name: String, val ordinal: Int)
```

### Predefined Methods

```scala mdoc:compile-only
import zio.http.Method

val get     = Method.GET
val post    = Method.POST
val put     = Method.PUT
val delete  = Method.DELETE
val patch   = Method.PATCH
val head    = Method.HEAD
val options = Method.OPTIONS
val trace   = Method.TRACE
val connect = Method.CONNECT
```

### Parsing

```scala mdoc:compile-only
import zio.http.Method

Method.fromString("GET")    // Some(Method.GET)
Method.fromString("POST")   // Some(Method.POST)
Method.fromString("CUSTOM") // None (unknown method)
```

### Rendering

```scala mdoc:compile-only
import zio.http.Method

Method.render(Method.GET)  // "GET"
Method.GET.name            // "GET"
Method.GET.toString        // "GET"
```

## Status

`Status` is an opaque type alias for `Int` in Scala 3 (AnyVal wrapper in Scala 2.13), providing zero-allocation status codes with predefined constants.

```scala
opaque type Status = Int  // Scala 3
```

### Predefined Status Codes

Status codes are organized by category:

```scala mdoc:compile-only
import zio.http.Status

// 1xx Informational
Status.Continue           // 100
Status.SwitchingProtocols // 101

// 2xx Success
Status.Ok                 // 200
Status.Created            // 201
Status.NoContent          // 204

// 3xx Redirection
Status.MovedPermanently   // 301
Status.Found              // 302
Status.SeeOther           // 303
Status.NotModified        // 304

// 4xx Client Errors
Status.BadRequest         // 400
Status.Unauthorized       // 401
Status.Forbidden          // 403
Status.NotFound           // 404

// 5xx Server Errors
Status.InternalServerError // 500
Status.BadGateway          // 502
Status.ServiceUnavailable  // 503
```

### Creating Status Codes

```scala mdoc:compile-only
import zio.http.Status

val custom = Status(418)          // I'm a teapot
val ok     = Status.fromInt(200)  // Status.Ok
```

### Status Code Operations

```scala mdoc:compile-only
import zio.http.Status

val status = Status.Ok

status.code              // 200
status.text              // "OK"
status.isSuccess         // true (2xx)
status.isInformational   // false (1xx)
status.isRedirection     // false (3xx)
status.isClientError     // false (4xx)
status.isServerError     // false (5xx)
status.isError           // false (4xx or 5xx)
```

## Version

`Version` represents HTTP protocol versions:

```scala
sealed abstract class Version(val major: Int, val minor: Int)
```

### Predefined Versions

```scala mdoc:compile-only
import zio.http.Version

val v10 = Version.`Http/1.0`
val v11 = Version.`Http/1.1`
val v20 = Version.`Http/2.0`
val v30 = Version.`Http/3.0`
```

### Parsing and Rendering

```scala mdoc:compile-only
import zio.http.Version

Version.fromString("HTTP/1.1") // Some(Version.`Http/1.1`)
Version.fromString("HTTP/2")   // Some(Version.`Http/2.0`)
Version.fromString("HTTP/3")   // Some(Version.`Http/3.0`)

Version.render(Version.`Http/1.1`) // "HTTP/1.1"
Version.`Http/2.0`.text            // "HTTP/2.0"
```

## Scheme

`Scheme` represents URL schemes with support for HTTP, HTTPS, WebSocket (WS, WSS), and custom schemes:

```scala
sealed trait Scheme {
  def text: String
  def defaultPort: Option[Int]
  def isSecure: Boolean
  def isWebSocket: Boolean
}
```

### Predefined Schemes

```scala mdoc:compile-only
import zio.http.Scheme

val http  = Scheme.HTTP   // http://, port 80
val https = Scheme.HTTPS  // https://, port 443
val ws    = Scheme.WS     // ws://, port 80
val wss   = Scheme.WSS    // wss://, port 443
```

### Scheme Properties

```scala mdoc:compile-only
import zio.http.Scheme

Scheme.HTTPS.isSecure      // true
Scheme.WS.isWebSocket      // true
Scheme.HTTP.defaultPort    // Some(80)
```

### Custom Schemes

```scala mdoc:compile-only
import zio.http.Scheme

val custom = Scheme.Custom("git+ssh")
custom.text         // "git+ssh"
custom.defaultPort  // None
```

### Parsing

```scala mdoc:compile-only
import zio.http.Scheme

Scheme.fromString("https")    // Scheme.HTTPS
Scheme.fromString("wss")      // Scheme.WSS
Scheme.fromString("custom")   // Scheme.Custom("custom")
```

## Charset

`Charset` represents character encodings with JVM-only conversion to `java.nio.charset.Charset`:

```scala
sealed abstract class Charset(val name: String)
```

### Predefined Charsets

```scala mdoc:compile-only
import zio.http.Charset

val utf8      = Charset.UTF8       // "UTF-8"
val ascii     = Charset.ASCII      // "US-ASCII"
val iso88591  = Charset.ISO_8859_1 // "ISO-8859-1"
val utf16     = Charset.UTF16      // "UTF-16"
val utf16be   = Charset.UTF16BE    // "UTF-16BE"
val utf16le   = Charset.UTF16LE    // "UTF-16LE"
```

### Parsing

```scala mdoc:compile-only
import zio.http.Charset

Charset.fromString("UTF-8")      // Some(Charset.UTF8)
Charset.fromString("utf8")       // Some(Charset.UTF8) (case-insensitive)
Charset.fromString("ISO-8859-1") // Some(Charset.ISO_8859_1)
Charset.fromString("LATIN1")     // Some(Charset.ISO_8859_1) (alias)
```

## Boundary

`Boundary` represents multipart form-data boundaries:

```scala mdoc:compile-only
import zio.http.Boundary

val boundary = Boundary("----WebKitFormBoundary7MA4YWxkTrZu0gW")
boundary.value    // "----WebKitFormBoundary7MA4YWxkTrZu0gW"
boundary.toString // "----WebKitFormBoundary7MA4YWxkTrZu0gW"
```

### Generating Boundaries

```scala mdoc:compile-only
import zio.http.Boundary

val generated = Boundary.generate  // random 24-character alphanumeric string
```

## PercentEncoder

`PercentEncoder` provides RFC 3986 percent-encoding for URL components. Each URL component has different encoding rules:

```scala mdoc:compile-only
import zio.http.PercentEncoder
import zio.http.PercentEncoder.ComponentType

val segment = PercentEncoder.encode("hello world", ComponentType.PathSegment)
// "hello%20world"

val queryKey = PercentEncoder.encode("filter[name]", ComponentType.QueryKey)
// "filter%5Bname%5D"

val decoded = PercentEncoder.decode("hello%20world")
// "hello world"
```

### Component Types

The encoder recognizes these component types:

- `PathSegment`: path segments between `/`
- `QueryKey`: query parameter names
- `QueryValue`: query parameter values
- `Fragment`: fragment identifiers after `#`
- `UserInfo`: userinfo in authority

Each type has specific rules for which characters must be percent-encoded.

## ContentType

`ContentType` combines a media type with optional charset and boundary parameters:

```scala mdoc:compile-only
import zio.http.{ContentType, Charset, Boundary}
import zio.blocks.mediatype.MediaTypes

val json = ContentType(MediaTypes.application.`json`)

val htmlUtf8 = ContentType(
  mediaType = MediaTypes.text.`html`,
  charset = Some(Charset.UTF8)
)

val multipart = ContentType(
  mediaType = MediaTypes.multipart.`form-data`,
  boundary = Some(Boundary("----boundary"))
)
```

### Parsing

```scala mdoc:compile-only
import zio.http.ContentType

ContentType.parse("application/json")
// Right(ContentType(MediaType("application", "json")))

ContentType.parse("text/html; charset=utf-8")
// Right(ContentType(..., charset = Some(Charset.UTF8)))

ContentType.parse("multipart/form-data; boundary=abc123")
// Right(ContentType(..., boundary = Some(Boundary("abc123"))))

ContentType.parse("")
// Left("Invalid content type: cannot be empty")
```

### Rendering

```scala mdoc:compile-only
import zio.http.{ContentType, Charset}
import zio.blocks.mediatype.MediaTypes

val ct = ContentType(
  MediaTypes.text.`plain`,
  charset = Some(Charset.UTF8)
)

ct.render  // "text/plain; charset=UTF-8"
```

### Predefined Content Types

```scala mdoc:compile-only
import zio.http.ContentType

val json   = ContentType.`application/json`
val plain  = ContentType.`text/plain`
val html   = ContentType.`text/html`
val binary = ContentType.`application/octet-stream`
```

## Path

`Path` represents URL paths with decoded segments stored internally:

```scala
final case class Path(
  segments: Chunk[String],
  hasLeadingSlash: Boolean,
  trailingSlash: Boolean
)
```

Paths use a **single encoding contract**: decode on input, store decoded, encode on output.

### Creating Paths

```scala mdoc:compile-only
import zio.http.Path

val empty = Path.empty                    // ""
val root  = Path.root                     // "/"
val users = Path("/users")                // segments: ["users"], leading slash: true
val api   = Path("api/v1/users/")         // segments: ["api", "v1", "users"], trailing slash: true
```

### Parsing Encoded Paths

`Path.fromEncoded` decodes percent-encoded segments:

```scala mdoc:compile-only
import zio.http.Path

val path = Path.fromEncoded("/hello%20world/foo%2Fbar")
// Path(Chunk("hello world", "foo/bar"), hasLeadingSlash = true, trailingSlash = false)

path.segments(0)  // "hello world" (decoded)
path.segments(1)  // "foo/bar" (decoded)
```

### Building Paths

```scala mdoc:compile-only
import zio.http.Path

val base = Path("/api")
val extended = base / "users" / "123"  // "/api/users/123"

val combined = Path("/api") ++ Path("v1/users")  // "/api/v1/users"
```

### Encoding Paths

```scala mdoc:compile-only
import zio.http.Path
import zio.blocks.chunk.Chunk

val path = Path(Chunk("hello world", "foo/bar"), hasLeadingSlash = true, trailingSlash = false)

path.encode  // "/hello%20world/foo%2Fbar" (percent-encoded)
path.render  // "/hello world/foo/bar" (decoded for display)
```

### Path Properties

```scala mdoc:compile-only
import zio.http.Path

val path = Path("/api/v1/users/")

path.isEmpty           // false
path.nonEmpty          // true
path.length            // 3 (number of segments)
path.hasLeadingSlash   // true
path.trailingSlash     // true
```

## QueryParams

`QueryParams` stores query parameters with multiple values per key:

```scala
final class QueryParams private[http] (
  private val keys: Array[String],
  private val vals: Array[Chunk[String]],
  val size: Int
)
```

Like `Path`, query parameters store decoded values internally and encode only on output.

### Creating QueryParams

```scala mdoc:compile-only
import zio.http.QueryParams

val empty = QueryParams.empty

val params = QueryParams(
  "name" -> "Alice",
  "age" -> "30",
  "active" -> "true"
)
```

### Parsing Encoded Query Strings

```scala mdoc:compile-only
import zio.http.QueryParams

val params = QueryParams.fromEncoded("name=Alice%20Smith&age=30&active=true")

params.getFirst("name")    // Some("Alice Smith") (decoded)
params.getFirst("age")     // Some("30")
params.getFirst("active")  // Some("true")
```

### Accessing Values

```scala mdoc:compile-only
import zio.http.QueryParams

val params = QueryParams(
  "color" -> "red",
  "color" -> "blue",
  "size" -> "large"
)

params.get("color")       // Some(Chunk("red", "blue"))
params.getFirst("color")  // Some("red")
params.getFirst("size")   // Some("large")
params.getFirst("other")  // None
params.has("color")       // true
```

### Modifying QueryParams

```scala mdoc:compile-only
import zio.http.QueryParams

val params = QueryParams("a" -> "1", "b" -> "2")

val added = params.add("c", "3")         // adds "c=3"
val set   = params.set("a", "100")       // replaces all "a" values
val removed = params.remove("b")         // removes all "b" entries
```

### Encoding

```scala mdoc:compile-only
import zio.http.QueryParams

val params = QueryParams(
  "name" -> "Alice Smith",
  "filter[status]" -> "active"
)

params.encode  // "name=Alice%20Smith&filter%5Bstatus%5D=active"
```

### Converting to List

```scala mdoc:compile-only
import zio.http.QueryParams

val params = QueryParams("a" -> "1", "a" -> "2", "b" -> "3")
params.toList  // List(("a", "1"), ("a", "2"), ("b", "3"))
```

## URL

`URL` combines scheme, host, port, path, query parameters, and fragment:

```scala
final case class URL(
  scheme: Option[Scheme],
  host: Option[String],
  port: Option[Int],
  path: Path,
  queryParams: QueryParams,
  fragment: Option[String]
)
```

### Parsing URLs

```scala mdoc:compile-only
import zio.http.URL

val absolute = URL.parse("https://api.example.com:8080/users?active=true#results")
// Right(URL(
//   scheme = Some(Scheme.HTTPS),
//   host = Some("api.example.com"),
//   port = Some(8080),
//   path = Path("/users"),
//   queryParams = QueryParams("active" -> "true"),
//   fragment = Some("results")
// ))

val relative = URL.parse("/api/users?page=2")
// Right(URL(
//   scheme = None,
//   host = None,
//   port = None,
//   path = Path("/api/users"),
//   queryParams = QueryParams("page" -> "2"),
//   fragment = None
// ))
```

The parser handles:

- IPv6 hosts in brackets: `http://[::1]:8080/`
- Userinfo: `https://user:pass@example.com/` (userinfo is skipped)
- Relative URLs: `/path?query`
- Fragment identifiers: `#section`

### Building URLs

```scala mdoc:compile-only
import zio.http.{URL, Path, Scheme}

val base = URL.root  // http://localhost/

val extended = base / "api" / "users"  // adds path segments

val withQuery = extended ?? ("active", "true") ?? ("page", "1")
// http://localhost/api/users?active=true&page=1
```

### URL from Path

```scala mdoc:compile-only
import zio.http.{URL, Path}

val path = Path("/api/users")
val url = URL.fromPath(path)  // relative URL with just path
```

### Encoding URLs

```scala mdoc:compile-only
import zio.http.URL

val url = URL.parse("https://example.com/hello world?name=Alice Smith").toOption.get

url.encode  // "https://example.com/hello%20world?name=Alice%20Smith"
url.toString  // same as encode
```

### URL Properties

```scala mdoc:compile-only
import zio.http.URL

val absolute = URL.parse("https://example.com/").toOption.get
val relative = URL.parse("/api/users").toOption.get

absolute.isAbsolute  // true (has scheme)
relative.isRelative  // true (no scheme)
```

## Header

`Header` is a trait representing typed HTTP headers:

```scala
trait Header {
  def headerName: String
  def renderedValue: String
}
```

Each header type has a companion object implementing `Header.Typed[H]` for parsing and rendering.

### Predefined Header Types

```scala mdoc:compile-only
import zio.http.{Header => _, *}
import zio.http.headers

val contentType   = headers.ContentType
val accept        = headers.Accept
val authorization = headers.Authorization
val host          = headers.Host
val userAgent     = headers.UserAgent
val cacheControl  = headers.CacheControl
val contentLength = headers.ContentLength
val location      = headers.Location
val setCookie     = headers.SetCookieHeader
val cookie        = headers.CookieHeader
```

### Creating Typed Headers

```scala mdoc:compile-only
import zio.http.{Header => _, ContentType, Charset, *}
import zio.http.headers
import zio.blocks.mediatype.MediaTypes

val ct = headers.ContentType(
  ContentType(MediaTypes.application.`json`, charset = Some(Charset.UTF8))
)

val auth = headers.Authorization.Bearer("token123")

val host = headers.Host("api.example.com", Some(8080))
```

### Parsing Headers

```scala mdoc:compile-only
import zio.http.{Header => _, *}
import zio.http.headers

headers.ContentType.parse("application/json; charset=utf-8")
// Right(headers.ContentType(...))

headers.Host.parse("example.com:443")
// Right(headers.Host("example.com", Some(443)))

headers.ContentLength.parse("1024")
// Right(headers.ContentLength(1024))

headers.ContentLength.parse("-1")
// Left("Invalid content-length: -1")
```

### Custom Headers

```scala mdoc:compile-only
import zio.http.Header

val custom = Header.Custom("x-request-id", "abc-123")
custom.headerName     // "x-request-id"
custom.renderedValue  // "abc-123"
```

## Headers

`Headers` is a flat array-based collection with lazy monotonic parsing:

```scala
final class Headers private[http] (
  private val names: Array[String],
  private val rawValues: Array[String],
  private val parsed: Array[AnyRef],  // null -> unparsed, value -> cached
  val size: Int
)
```

When you call `get[H]`, the header is parsed once and cached. Subsequent calls return the cached result.

### Creating Headers

```scala mdoc:compile-only
import zio.http.Headers

val empty = Headers.empty

val headers = Headers(
  "content-type" -> "application/json",
  "authorization" -> "Bearer token",
  "x-request-id" -> "abc-123"
)
```

### Getting Typed Headers

```scala mdoc:compile-only
import zio.http.{Headers, *}
import zio.http.{headers => h}

val headers = Headers(
  "content-type" -> "application/json",
  "content-length" -> "1024"
)

val ct = headers.get(h.ContentType)
// Some(h.ContentType(...)) (parsed and cached)

val cl = headers.get(h.ContentLength)
// Some(h.ContentLength(1024)) (parsed and cached)

val auth = headers.get(h.Authorization)
// None (not present)
```

### Getting Raw Values

```scala mdoc:compile-only
import zio.http.Headers

val headers = Headers("x-custom" -> "value")

headers.rawGet("x-custom")  // Some("value")
headers.rawGet("missing")   // None
```

### Getting All Headers of a Type

Some headers can appear multiple times (like `Set-Cookie`):

```scala mdoc:compile-only
import zio.http.{Headers, *}
import zio.http.{headers => h}

val headers = Headers(
  "set-cookie" -> "session=abc",
  "set-cookie" -> "preference=dark"
)

val cookies = headers.getAll(h.SetCookieHeader)
// Chunk(h.SetCookieHeader(...), h.SetCookieHeader(...))
```

### Modifying Headers

```scala mdoc:compile-only
import zio.http.Headers

val headers = Headers("a" -> "1", "b" -> "2")

val added = headers.add("c", "3")        // adds "c: 3"
val set   = headers.set("a", "100")      // replaces all "a" values
val removed = headers.remove("b")        // removes all "b" entries
val has = headers.has("a")               // true
```

### Converting to List

```scala mdoc:compile-only
import zio.http.Headers

val headers = Headers("a" -> "1", "b" -> "2")
headers.toList  // List(("a", "1"), ("b", "2"))
```

## Body

`Body` wraps a materialized byte array with optional content type:

```scala
final class Body private (
  val data: Array[Byte],
  val contentType: Option[ContentType]
)
```

Bodies are immutable and defensively copied on construction.

### Creating Bodies

```scala mdoc:compile-only
import zio.http.{Body, Charset}

val empty = Body.empty

val fromString = Body.fromString("Hello, World!", Charset.UTF8)
// Content-Type: text/plain; charset=UTF-8

val fromBytes = Body.fromArray(Array[Byte](1, 2, 3))
```

Using Chunk:

```scala mdoc:compile-only
import zio.http.Body
import zio.blocks.chunk.Chunk

val chunk = Chunk[Byte](1, 2, 3, 4, 5)
val body = Body.fromChunk(chunk)
```

### Reading Bodies

```scala mdoc:compile-only
import zio.http.{Body, Charset}

val body = Body.fromString("Hello!", Charset.UTF8)

body.length           // 6
body.isEmpty          // false
body.nonEmpty         // true
body.asString()       // "Hello!" (UTF-8 default)
body.asString(Charset.ASCII)  // "Hello!" (explicit charset)
body.asChunk          // Chunk[Byte](72, 101, 108, 108, 111, 33)
```

## Cookie

Cookies are split into `RequestCookie` and `ResponseCookie` with different structures:

```scala
final case class RequestCookie(name: String, value: String)

final case class ResponseCookie(
  name: String,
  value: String,
  domain: Option[String],
  path: Option[Path],
  maxAge: Option[Long],
  isSecure: Boolean,
  isHttpOnly: Boolean,
  sameSite: Option[SameSite]
)
```

### SameSite

```scala mdoc:compile-only
import zio.http.SameSite

val strict = SameSite.Strict
val lax    = SameSite.Lax
val none   = SameSite.None_  // underscore avoids conflict with scala.None
```

### Parsing Request Cookies

```scala mdoc:compile-only
import zio.http.Cookie

val cookies = Cookie.parseRequest("session=abc123; preference=dark")
// Chunk(RequestCookie("session", "abc123"), RequestCookie("preference", "dark"))
```

### Parsing Response Cookies

```scala mdoc:compile-only
import zio.http.Cookie

val cookie = Cookie.parseResponse("session=abc; Domain=example.com; Path=/; Secure; HttpOnly; SameSite=Strict")
// Right(ResponseCookie(
//   name = "session",
//   value = "abc",
//   domain = Some("example.com"),
//   path = Some(Path("/")),
//   isSecure = true,
//   isHttpOnly = true,
//   sameSite = Some(SameSite.Strict)
// ))
```

### Rendering Cookies

```scala mdoc:compile-only
import zio.http.{Cookie, RequestCookie, ResponseCookie, SameSite, Path}
import zio.blocks.chunk.Chunk

val requestCookies = Chunk(
  RequestCookie("session", "abc"),
  RequestCookie("theme", "dark")
)
Cookie.renderRequest(requestCookies)
// "session=abc; theme=dark"

val responseCookie = ResponseCookie(
  name = "session",
  value = "xyz",
  domain = Some("example.com"),
  path = Some(Path("/")),
  maxAge = Some(3600),
  isSecure = true,
  isHttpOnly = true,
  sameSite = Some(SameSite.Strict)
)
Cookie.renderResponse(responseCookie)
// "session=xyz; Domain=example.com; Path=/; Max-Age=3600; Secure; HttpOnly; SameSite=Strict"
```

## Form

`Form` represents URL-encoded form data:

```scala
final case class Form(entries: Chunk[(String, String)])
```

### Creating Forms

```scala mdoc:compile-only
import zio.http.Form

val empty = Form.empty

val form = Form(
  "username" -> "alice",
  "password" -> "secret",
  "remember" -> "true"
)
```

### Accessing Form Data

```scala mdoc:compile-only
import zio.http.Form

val form = Form(
  "tag" -> "scala",
  "tag" -> "functional",
  "page" -> "1"
)

form.get("tag")      // Some("scala") (first value)
form.getAll("tag")   // Chunk("scala", "functional")
form.get("page")     // Some("1")
form.get("missing")  // None
```

### Modifying Forms

```scala mdoc:compile-only
import zio.http.Form

val form = Form("a" -> "1")
val added = form.add("b", "2")  // Form(("a", "1"), ("b", "2"))
```

### Encoding and Parsing

```scala mdoc:compile-only
import zio.http.Form

val form = Form(
  "name" -> "Alice Smith",
  "email" -> "alice@example.com"
)

val encoded = form.encode
// "name=Alice%20Smith&email=alice%40example.com"

val parsed = Form.fromString(encoded)
// Form with decoded entries
```

## FormField

`FormField` is a sealed trait for multipart form fields, supporting simple key-value pairs, text parts with optional metadata, and binary parts:

```scala mdoc:compile-only
import zio.http._
import zio.blocks.chunk.Chunk
import zio.blocks.mediatype.MediaTypes

// Simple key-value field
val simple = FormField.Simple("username", "alice")

// Text field with optional content type and filename
val text = FormField.Text(
  name = "bio",
  value = "Hello world",
  contentType = Some(ContentType(MediaTypes.text.`plain`)),
  filename = None
)

// Binary field with content type and optional filename
val binary = FormField.Binary(
  name = "avatar",
  data = Chunk.fromArray(Array[Byte](1, 2, 3)),
  contentType = ContentType(MediaTypes.image.`png`),
  filename = Some("avatar.png")
)

// All variants share a common `name` accessor
simple.name  // "username"
text.name    // "bio"
binary.name  // "avatar"
```

## Request

`Request` combines all HTTP request components:

```scala
final case class Request(
  method: Method,
  url: URL,
  headers: Headers,
  body: Body,
  version: Version
)
```

### Creating Requests

```scala mdoc:compile-only
import zio.http._

val url = URL.parse("https://api.example.com/users").toOption.get
val getRequest = Request.get(url)

val jsonBody = Body.fromString("""{"name":"Alice"}""", Charset.UTF8)
val postRequest = Request.post(url, jsonBody)
```

Full control:

```scala mdoc:compile-only
import zio.http._

val url = URL.parse("https://api.example.com/users").toOption.get
val body = Body.fromString("""{"name":"Alice"}""", Charset.UTF8)

val request = Request(
  method = Method.POST,
  url = url,
  headers = Headers(
    "content-type" -> "application/json",
    "authorization" -> "Bearer token123"
  ),
  body = body,
  version = Version.`Http/1.1`
)
```

### Accessing Request Data

```scala mdoc:compile-only
import zio.http._
import zio.http.{headers => h}

val request = Request.get(URL.parse("/api/users?page=1").toOption.get)

request.path         // Path("/api/users")
request.queryParams  // QueryParams("page" -> "1")
request.contentType  // Option[ContentType]
request.header(h.Authorization)  // Option[h.Authorization]
```

## Response

`Response` represents HTTP responses:

```scala
final case class Response(
  status: Status,
  headers: Headers,
  body: Body,
  version: Version
)
```

### Creating Responses

```scala mdoc:compile-only
import zio.http._

val ok = Response.ok  // 200 OK, empty body

val notFound = Response.notFound  // 404 Not Found, empty body
```

Full control:

```scala mdoc:compile-only
import zio.http._

val jsonBody = Body.fromString("""{"message":"created"}""", Charset.UTF8)

val response = Response(
  status = Status.Created,
  headers = Headers(
    "content-type" -> "application/json",
    "location" -> "/users/123"
  ),
  body = jsonBody,
  version = Version.`Http/1.1`
)
```

### Accessing Response Data

```scala mdoc:compile-only
import zio.http._
import zio.http.{headers => h}

val response = Response.ok

response.status.code                      // 200
response.status.isSuccess                 // true
response.contentType                      // Option[ContentType]
response.header(h.ContentType)       // Option[h.ContentType]
response.header(h.Location)          // Option[h.Location]
```

## Advanced Usage

### Building a Complete HTTP Exchange

```scala mdoc:compile-only
import zio.http._

// Build request
val url = URL.parse("https://api.example.com/users").toOption.get
val requestBody = Body.fromString("""{"name":"Alice","age":30}""", Charset.UTF8)

val request = Request(
  method = Method.POST,
  url = url,
  headers = Headers(
    "content-type" -> "application/json",
    "authorization" -> "Bearer abc123",
    "user-agent" -> "MyClient/1.0"
  ),
  body = requestBody,
  version = Version.`Http/1.1`
)

// Build response
val responseBody = Body.fromString("""{"id":123,"name":"Alice","age":30}""", Charset.UTF8)

val response = Response(
  status = Status.Created,
  headers = Headers(
    "content-type" -> "application/json",
    "location" -> "/users/123"
  ),
  body = responseBody,
  version = Version.`Http/1.1`
)
```

### URL Building with Fluent API

```scala mdoc:compile-only
import zio.http._

val url = URL.parse("https://api.example.com").toOption.get

val extended = (url / "v1" / "users" / "123") ?? ("include", "profile") ?? ("include", "posts")

extended.encode
// "https://api.example.com/v1/users/123?include=profile&include=posts"
```

### Typed Header Access

```scala mdoc:compile-only
import zio.http._
import zio.http.{headers => h}

val headers = Headers(
  "content-type" -> "application/json; charset=utf-8",
  "content-length" -> "1024",
  "authorization" -> "Bearer token"
)

// Type-safe header access with parsing
val ct = headers.get(h.ContentType)
ct.map(_.value.charset)  // Some(Some(Charset.UTF8))

val cl = headers.get(h.ContentLength)
cl.map(_.length)  // Some(1024)

// Raw access
headers.rawGet("authorization")  // Some("Bearer token")
```

### Cookie Management

```scala mdoc:compile-only
import zio.http._
import zio.blocks.chunk.Chunk

// Parse cookies from request header
val cookieHeader = "session=abc; theme=dark"
val requestCookies = Cookie.parseRequest(cookieHeader)

// Create response with Set-Cookie headers
val sessionCookie = ResponseCookie(
  name = "session",
  value = "xyz123",
  path = Some(Path("/")),
  maxAge = Some(3600),
  isSecure = true,
  isHttpOnly = true,
  sameSite = Some(SameSite.Strict)
)

val response = Response(
  status = Status.Ok,
  headers = Headers(
    "set-cookie" -> Cookie.renderResponse(sessionCookie)
  ),
  body = Body.empty
)
```

### Form Submission

```scala mdoc:compile-only
import zio.http._

val form = Form(
  "username" -> "alice",
  "password" -> "secret",
  "remember" -> "true"
)

val formBody = Body.fromString(form.encode, Charset.UTF8)

val request = Request(
  method = Method.POST,
  url = URL.parse("/login").toOption.get,
  headers = Headers(
    "content-type" -> "application/x-www-form-urlencoded"
  ),
  body = formBody,
  version = Version.`Http/1.1`
)
```

## Design Principles

### Single Encoding Contract

`Path` and `QueryParams` store decoded values internally. Encoding happens only at output boundaries:

- `Path.fromEncoded(s)` decodes, stores decoded segments
- `Path.encode` encodes segments for transmission
- `QueryParams.fromEncoded(s)` decodes, stores decoded key-value pairs
- `QueryParams.encode` encodes for transmission

This eliminates double-encoding bugs and clarifies responsibilities.

### Lazy Header Parsing

`Headers` stores raw string values and parses typed headers on first access. Parsed results are cached in a parallel `Array[AnyRef]` for O(1) subsequent lookups. This design:

- Avoids parsing headers that are never accessed
- Avoids re-parsing the same header multiple times
- Supports unknown/custom headers without parsing failures

### No Streaming

Bodies are fully materialized `Array[Byte]`. Streaming is left to higher-level HTTP libraries that compose with this data model. This keeps the model simple and effect-free.

### Zero ZIO Dependency

The module uses `zio.blocks.chunk.Chunk` instead of `zio.Chunk`, making it usable in any Scala project without ZIO.
