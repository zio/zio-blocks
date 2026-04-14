---
id: http-model
title: "HTTP Model"
---

`zio-http-model` is a **pure, zero-dependency HTTP data model** for building HTTP clients and servers. It provides immutable types representing all HTTP concepts: requests, responses, headers, URLs, paths, query parameters, methods, status codes, versions, cookies, and forms.

Core types: `Request`, `Response`, `URL`, `Headers`, `Body`, `Method`, `Status`, `Version`, `Scheme`, `Path`, `QueryParams`, `ContentType`, `RequestCookie`, `ResponseCookie`, `Form`.

Here is a high-level overview of the core types and their relationships:

```scala
// Core request/response types
final case class Request(method: Method, url: URL, headers: Headers, body: Body, version: Version)
final case class Response(status: Status, headers: Headers, body: Body, version: Version)

// URL structure
final case class URL(scheme: Option[Scheme], host: Option[String], port: Option[Int],
                     path: Path, queryParams: QueryParams, fragment: Option[String])
final case class Path(segments: Chunk[String], hasLeadingSlash: Boolean, trailingSlash: Boolean)

// HTTP primitives
sealed abstract class Method(val name: String, val ordinal: Int)
opaque type Status = Int
sealed abstract class Version(val major: Int, val minor: Int)
sealed trait Scheme

// Headers and body
final class Headers(...)
sealed trait Header
final class Body(val data: Chunk[Byte], val contentType: ContentType)
```

## Motivation

### The Problem: Protocol, Effects, and Coupling

Imagine building a distributed system where you need an HTTP client to call external APIs and an HTTP server to handle incoming requests. Your first instinct is to reach for a popular HTTP library. But here's the trouble: most HTTP libraries bake "effects" (I/O operations, streaming, async) directly into their data structures.

This creates a coupling problem:

**Scenario 1: Sharing Types Across Layers**
You want your client request logic (building a request to send) to use the same types as your server request handling (receiving and parsing a request). But your HTTP library makes this difficult — the `Request` type is tied to async effects, file streams, or a specific Scala version's IO model. Sharing becomes messy.

**Scenario 2: Testing Without Effects**
You're writing unit tests for your request-building logic. You want to serialize a request to JSON for snapshots, or cache requests for debugging. But your `Request` type requires pulling in async runtimes, streaming libraries, or other baggage you don't need in tests. A simple unit test becomes a production-grade effect setup.

**Scenario 3: Lock-In**
You've built your entire API client around ZIO's HTTP library, but your team decides to use Akka for one microservice. Now your request/response types aren't portable — they're coupled to ZIO. Refactoring is painful.

### The Solution: Pure HTTP Data

`zio-http-model` separates **protocol concerns** (representing HTTP messages) from **effect concerns** (actually sending/receiving them). It provides:

- **Pure immutable data types** — `Request`, `Response`, `URL`, `Headers`, `Body` are just data. No effects, no I/O, no surprises. You can use them anywhere: tests, serialization, caching, multiple effect systems.

- **Zero dependencies beyond Chunk** — Not coupled to ZIO, Akka, or any runtime. Your HTTP data structures work in any Scala application.

- **Incremental, lazy parsing** — Headers are parsed on first access and cached. You pay only for what you use.

- **Efficient, composable** — Headers and QueryParams use parallel array-backed collections for fast lookups. Types compose naturally without forcing a single architectural path.

This separation is powerful: you can build, manipulate, serialize, and test HTTP messages using pure data, then hand them off to any HTTP client/server library (ZIO, Akka, Play, etc.) for the actual I/O work. Your domain logic stays portable and testable.


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

## How They Work Together

The HTTP model consists of **request/response messages** at the center, composed of smaller types that handle specific concerns:

```
Request ────────────────────────────────────────────────────
  ├─ method: Method (HTTP verb: GET, POST, PUT, DELETE, etc.)
  ├─ url: URL (target endpoint)
  │   ├─ scheme: Option[Scheme] (HTTP, HTTPS, WS, WSS, Custom)
  │   ├─ host: Option[String] (domain name)
  │   ├─ port: Option[Int] (port number)
  │   ├─ path: Path (URL path segments)
  │   ├─ queryParams: QueryParams (query string parameters)
  │   └─ fragment: Option[String] (anchor)
  ├─ headers: Headers (HTTP headers, lazily parsed for typed access)
  │   └─ Header (individual headers: Authorization, Content-Type, etc.)
  ├─ body: Body (message content)
  │   └─ contentType: ContentType (media type, charset, boundary)
  └─ version: Version (HTTP/1.0, HTTP/1.1, HTTP/2.0, HTTP/3.0)

Response ────────────────────────────────────────────────────
  ├─ status: Status (HTTP status code: 200, 404, 500, etc.)
  ├─ headers: Headers (same as Request)
  ├─ body: Body (response content)
  └─ version: Version (HTTP protocol version)
```

**Typical flow:**
1. **Build URL** — Parse or construct a URL with path segments and query parameters
2. **Create Request** — Combine method, URL, headers, and body into a Request
3. **Send** — Transmit the request (handled by HTTP client/server library, not http-model)
4. **Receive Response** — Parse incoming Response with status, headers, and body
5. **Access Data** — Extract typed headers, cookies, content type from Headers
6. **Parse Content** — Body contains raw bytes; higher-level libraries deserialize based on content type

## Common Patterns

### Building Requests with Headers and Query Parameters

Create a request with headers and query parameters:

```scala mdoc:compile-only
import zio.http._

val url = URL.parse("https://api.example.com/users?role=admin").toOption.get
val req = Request
  .get(url)
  .addHeader("authorization", "Bearer token123")
  .addHeader("content-type", "application/json")
```

### Extracting and Manipulating Headers

Access headers by name or retrieve all headers:

```scala mdoc:compile-only
import zio.http._

val req: Request = ???
val contentType = req.headers.get("content-type")  // Get header by name (case-insensitive)
val allHeaders = req.headers.toList                // All headers as List[(String, String)]
val headerCount = req.headers.toList.length        // Count headers
```

### Creating Responses with Status and Content

Build a JSON response:

```scala mdoc:compile-only
import zio.http._

val body = Body.fromString("""{"message":"ok"}""", Charset.UTF8)
val response = Response(
  status = Status.Ok,
  body = body,
  headers = Headers("content-type" -> "application/json"),
  version = Version.`HTTP/1.1`
)
```

### URL Manipulation

Parse URLs and extract components:

```scala mdoc:compile-only
import zio.http._

val url = URL.parse("https://example.com/api/v1/users?page=1&limit=10").toOption.get

url.scheme                           // Some(Scheme.HTTPS)
url.host                             // Some("example.com")
url.port                             // None (defaults to 443 for HTTPS)
url.path.segments                    // Chunk("api", "v1", "users")
url.queryParams.getFirst("page")     // Some("1") (first value for key)
url ?? ("filter", "active")          // Add parameter using ?? operator
```

### Form Data Submission

Submit a form with key-value pairs:

```scala mdoc:compile-only
import zio.http._
import zio.blocks.chunk.Chunk

val url = URL.parse("https://example.com/login").toOption.get
val form = Form(
  Chunk(
    ("username" -> "alice"),
    ("password" -> "secret123"),
    ("remember" -> "true")
  )
)
val body = Body.fromString(form.encode, Charset.UTF8)
val req = Request.post(url, body)
  .addHeader("content-type", "application/x-www-form-urlencoded")
```

### Cookies in Requests and Responses

Send cookies to server and receive cookies from server:

```scala mdoc:compile-only
import zio.http._

val url = URL.parse("https://example.com/api").toOption.get

// Request: send cookies to server
val req = Request.get(url)
  .addHeader("cookie", "sessionId=abc123; userId=456")

// Response: server sets cookies for client to store
val setCookie = ResponseCookie(
  name = "sessionId",
  value = "abc123",
  maxAge = Some(3600),  // 1 hour
  isSecure = true,
  isHttpOnly = true
)
val response = Response(Status.Ok)
  .addHeader("set-cookie", setCookie.render)
```

---

## Method

`Method` represents the HTTP verb (request method) as sealed case objects:

```scala
sealed abstract class Method(val name: String, val ordinal: Int)
```

### Predefined Methods

The nine standard HTTP methods are predefined as case objects:

```scala mdoc:compile-only
import zio.http.Method

Method.GET      // Retrieve a resource
Method.POST     // Create a new resource
Method.PUT      // Replace a resource entirely
Method.DELETE   // Remove a resource
Method.PATCH    // Partially update a resource
Method.HEAD     // Retrieve headers only (no body)
Method.OPTIONS  // Describe communication options
Method.TRACE    // Echo the request back (for debugging)
Method.CONNECT  // Establish a tunnel (for proxies)
```

### Parsing Methods

Parse HTTP method strings to `Method` objects:

```scala mdoc:compile-only
import zio.http.Method

Method.fromString("GET")    // Some(Method.GET)
Method.fromString("POST")   // Some(Method.POST)
Method.fromString("CUSTOM") // None (HTTP allows custom methods, but not predefined)
```

### Method Properties

```scala mdoc:compile-only
import zio.http.Method

val m = Method.GET
m.name       // "GET"
m.ordinal    // ordinal position for sorting
m.toString   // "GET"
```

---

## Status

`Status` is an opaque type alias for `Int` in Scala 3 (AnyVal wrapper in Scala 2.13), providing zero-allocation HTTP status codes with predefined constants for all standard codes (1xx–5xx).

```scala
opaque type Status = Int  // Scala 3
```

### Predefined Status Codes

Status codes organized by category:

```scala mdoc:compile-only
import zio.http.Status

// 1xx Informational
Status.Continue           // 100
Status.SwitchingProtocols // 101

// 2xx Success
Status.Ok                 // 200
Status.Created            // 201
Status.Accepted           // 202
Status.NoContent          // 204

// 3xx Redirection
Status.MovedPermanently   // 301
Status.Found              // 302 (temporary redirect)
Status.SeeOther           // 303
Status.NotModified        // 304 (response not modified since condition)
Status.TemporaryRedirect  // 307
Status.PermanentRedirect  // 308

// 4xx Client Errors
Status.BadRequest         // 400 (malformed request)
Status.Unauthorized       // 401 (authentication required)
Status.Forbidden          // 403 (authenticated but not authorized)
Status.NotFound           // 404 (resource not found)
Status.MethodNotAllowed   // 405
Status.Conflict           // 409
Status.Gone               // 410 (resource permanently removed)
Status.UnprocessableEntity // 422
Status.TooManyRequests    // 429 (rate limited)

// 5xx Server Errors
Status.InternalServerError // 500 (generic server error)
Status.BadGateway          // 502
Status.ServiceUnavailable  // 503 (server temporarily unavailable)
Status.GatewayTimeout      // 504
```

### Creating Custom Status Codes

Create arbitrary status codes:

```scala mdoc:compile-only
import zio.http.Status

val custom = Status(418)          // I'm a teapot (Easter egg)
val ok     = Status.fromInt(200)  // Parse from Int, returns Status
```

### Status Code Operations

Query status code properties:

```scala mdoc:compile-only
import zio.http.Status

val status = Status.Ok

status.code              // 200 (the underlying Int)
status.text              // "OK" (human-readable)
status.isSuccess         // true (2xx status codes)
status.isInformational   // false (1xx codes)
status.isRedirection     // false (3xx codes)
status.isClientError     // false (4xx codes)
status.isServerError     // false (5xx codes)
status.isError           // false (true for 4xx or 5xx)
```

---

## Version

`Version` represents HTTP protocol versions:

```scala
sealed abstract class Version(val major: Int, val minor: Int)
```

### Predefined Versions

The four standard HTTP versions are predefined:

```scala mdoc:compile-only
import zio.http.Version

Version.`HTTP/1.0`  // HTTP/1.0 (1996)
Version.`HTTP/1.1`  // HTTP/1.1 (1997, persistent connections)
Version.`HTTP/2.0`  // HTTP/2.0 (2015, multiplexing)
Version.`HTTP/3.0`  // HTTP/3.0 (2022, QUIC-based)
```

### Parsing and Rendering

Parse version strings and render to strings:

```scala mdoc:compile-only
import zio.http.Version

Version.fromString("HTTP/1.1") // Some(Version.`HTTP/1.1`)
Version.fromString("HTTP/2")   // Some(Version.`HTTP/2.0`) (suffix optional)
Version.fromString("HTTP/3")   // Some(Version.`HTTP/3.0`)

Version.render(Version.`HTTP/1.1`) // "HTTP/1.1"
Version.`HTTP/2.0`.text            // "HTTP/2.0"
```

### Version Properties

```scala mdoc:compile-only
import zio.http.Version

val v = Version.`HTTP/2.0`
v.major  // 2 (major version number)
v.minor  // 0 (minor version number)
```

---

## Scheme

`Scheme` represents URL schemes with support for HTTP, HTTPS, WebSocket protocols, and custom schemes:

```scala
sealed trait Scheme {
  def text: String
  def defaultPort: Option[Int]
  def isSecure: Boolean
  def isWebSocket: Boolean
}
```

### Predefined Schemes

Standard schemes for HTTP, HTTPS, and WebSocket:

```scala mdoc:compile-only
import zio.http.Scheme

Scheme.HTTP   // http://, default port 80 (unencrypted)
Scheme.HTTPS  // https://, default port 443 (encrypted)
Scheme.WS     // ws://, default port 80 (WebSocket, unencrypted)
Scheme.WSS    // wss://, default port 443 (WebSocket, encrypted)
```

### Scheme Properties

```scala mdoc:compile-only
import zio.http.Scheme

Scheme.HTTPS.isSecure      // true
Scheme.HTTP.isSecure       // false
Scheme.WS.isWebSocket      // true
Scheme.HTTP.isWebSocket    // false

Scheme.HTTP.defaultPort    // Some(80)
Scheme.HTTPS.defaultPort   // Some(443)
Scheme.WS.defaultPort      // Some(80)
Scheme.WSS.defaultPort     // Some(443)

Scheme.HTTPS.text          // "https"
```

### Custom Schemes

Create custom schemes (e.g., FTP):

```scala mdoc:compile-only
import zio.http.Scheme

val ftp = Scheme.custom("ftp")
ftp.text        // "ftp"
ftp.isSecure    // false (custom schemes assumed insecure)
```

---

## URL

`URL` represents a complete Uniform Resource Locator with scheme, host, port, path, query parameters, and fragment:

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

Parse URL strings into `URL` objects:

```scala mdoc:compile-only
import zio.http.URL

URL.parse("https://example.com/api/users?id=123&role=admin")
// Returns: Either[ParseError, URL]

URL.parse("https://api.example.com:8080/v1/users?page=1#results")
// Parses: scheme, host, port (explicit), path, query params, fragment
```

### Constructing URLs

Build URLs programmatically:

```scala mdoc:compile-only
import zio.http._

val url = URL(
  scheme = Some(Scheme.HTTPS),
  host = Some("api.example.com"),
  port = None,  // Uses default port 443 for HTTPS
  path = Path("api", "users", "123"),
  queryParams = QueryParams("filter" -> "active", "sort" -> "name"),
  fragment = None
)
```

### URL Operations

Access URL components and modify them:

```scala mdoc:compile-only
import zio.http.URL

val url = URL.parse("https://example.com:8080/api/v1/users?page=1#section").toOption.get

url.scheme                        // Some(Scheme.HTTPS)
url.host                          // Some("example.com")
url.port                          // Some(8080)
url.path.segments                 // Chunk("api", "v1", "users")
url.queryParams.get("page")       // Some("1")
url.fragment                      // Some("section")

url.addQueryParam("sort", "name") // Add query parameter
url.withFragment(Some("top"))     // Set fragment
```

---

## Path

`Path` represents the URL path (e.g., `/api/v1/users`) stored as segments for efficient manipulation:

```scala
final case class Path(
  segments: Chunk[String],
  hasLeadingSlash: Boolean,
  trailingSlash: Boolean
)
```

### Creating Paths

Create paths from segments or parse from strings:

```scala mdoc:compile-only
import zio.http.Path

Path("api", "v1", "users")           // Path with segments (leading slash by default)
Path.decode("/api/v1/users")         // Parse from string
Path.root                             // Empty path "/"
```

### Path Operations

Access path segments and build modified paths:

```scala mdoc:compile-only
import zio.http.Path

val path = Path("api", "v1", "users")

path.segments             // Chunk("api", "v1", "users")
path.hasLeadingSlash      // true
path.trailingSlash        // false
path.encode               // "/api/v1/users" (encoded string)

path.append("123")        // Append segment: /api/v1/users/123
path.dropRight(1)         // Remove last segment: /api/v1
```

---

## QueryParams

`QueryParams` is an immutable, case-insensitive multi-map for URL query parameters, optimized for performance:

```scala
final class QueryParams private[http] (...)
```

### Creating QueryParams

Create query parameters from key-value pairs:

```scala mdoc:compile-only
import zio.http.QueryParams

QueryParams("page" -> "1", "limit" -> "10")
QueryParams.empty
QueryParams.fromChunk(Chunk(("page", "1"), ("limit", "10")))
```

### QueryParams Operations

Access and modify query parameters:

```scala mdoc:compile-only
import zio.http.QueryParams
import zio.blocks.chunk.Chunk

val params = QueryParams("page" -> "1", "role" -> "admin", "role" -> "user")

params.getFirst("page")         // Some("1") (first value for key)
params.get("role")              // Some(Chunk("admin", "user")) (all values for key as Chunk)
params.has("page")              // true (check if key exists)
params.add("sort", "name")      // Add parameter (supports duplicates)
params.remove("page")           // Remove all values for key
params.toChunk                  // Chunk((key, value), ...) of all entries
params.encode                   // "page=1&role=admin&role=user" (percent-encoded)
```

### Multi-Value Parameters

`QueryParams` supports multiple values for the same key:

```scala mdoc:compile-only
import zio.http.QueryParams
import zio.blocks.chunk.Chunk

val params = QueryParams("id" -> "1", "id" -> "2", "id" -> "3")
params.getFirst("id")  // Some("1") (first value)
params.get("id")       // Some(Chunk("1", "2", "3")) (all values as Chunk)
```

---

## Headers

`Headers` is an immutable, case-insensitive collection of HTTP headers with lazy parsing for typed header access:

```scala
final class Headers private[http] (...)
```

### Creating Headers

Create header collections:

```scala mdoc:compile-only
import zio.http.Headers

Headers("content-type" -> "application/json", "authorization" -> "Bearer token123")
Headers.empty
Headers.fromChunk(Chunk(("content-type", "application/json")))
```

### Headers Operations

Access and modify headers:

```scala mdoc:compile-only
import zio.http.Headers

val headers = Headers("content-type" -> "application/json", "cache-control" -> "no-cache")

headers.get("content-type")       // Some("application/json") (case-insensitive key lookup)
headers.get("Content-Type")       // Some("application/json") (case-insensitive)
headers.has("cache-control")      // true
headers.add("authorization", "Bearer token") // Add header
headers.remove("cache-control")   // Remove header
headers.toList                    // List[(String, String)] of all headers
```

### Typed Headers

`Headers` supports lazy parsing and caching of typed headers for efficient, type-safe header access:

```scala mdoc:compile-only
import zio.http.{Headers, Header}

val headers = Headers("content-type" -> "application/json; charset=utf-8")

// Access typed header (if Header.ContentType is defined)
// headers.header(Header.ContentType)  // Lazily parsed and cached
```

---

## Body

`Body` represents HTTP message content as immutable bytes with content type metadata:

```scala
final class Body private (val data: Chunk[Byte], val contentType: ContentType)
```

### Creating Bodies

Create bodies from strings, bytes, or files:

```scala mdoc:compile-only
import zio.http._

Body.fromString("""{"message":"ok"}""", Charset.UTF8)
Body.empty
Body.fromBytes(bytes: Chunk[Byte])
Body.fromFile("path/to/file.txt")
```

### Body Operations

Access body content and metadata:

```scala mdoc:compile-only
import zio.http._

val body = Body.fromString("Hello World", Charset.UTF8)

body.data              // Chunk[Byte] raw content
body.contentType       // ContentType metadata
body.asString          // "Hello World" (if UTF8)
body.asBytes           // Chunk[Byte] data
```

---

## ContentType

`ContentType` represents the MIME type, character encoding, and multipart boundary:

```scala
final case class ContentType(
  mediaType: MediaType,
  boundary: Option[Boundary] = None,
  charset: Option[Charset] = None
)
```

### Predefined Content Types

Common MIME types are predefined:

```scala mdoc:compile-only
import zio.http.ContentType

ContentType.ApplicationJson         // application/json
ContentType.TextPlain               // text/plain; charset=utf-8
ContentType.TextHtml                // text/html; charset=utf-8
ContentType.ApplicationXml          // application/xml; charset=utf-8
ContentType.ApplicationXmlUtf8      // application/xml; charset=utf-8
ContentType.ImagePng                // image/png
ContentType.ImageJpeg               // image/jpeg
ContentType.ImageGif                // image/gif
ContentType.ApplicationOctetStream  // application/octet-stream
ContentType.ApplicationFormUrlEncoded // application/x-www-form-urlencoded
ContentType.MultipartFormData       // multipart/form-data
```

### Creating Content Types

Create custom content types:

```scala mdoc:compile-only
import zio.http._

ContentType(MediaType.ApplicationJson)
ContentType(MediaType.TextPlain, charset = Some(Charset.UTF8))
ContentType(MediaType.MultipartFormData, boundary = Some(Boundary.generate))
```

---

## Charset

`Charset` represents character encoding for text content:

```scala
sealed abstract class Charset(val name: String)
```

### Predefined Charsets

Standard character encodings:

```scala mdoc:compile-only
import zio.http.Charset

Charset.UTF8      // UTF-8 (Unicode, variable-length encoding)
Charset.ASCII     // ASCII (7-bit encoding)
Charset.ISO_8859_1 // ISO-8859-1 (Latin-1)
Charset.UTF16     // UTF-16 (with BOM detection)
Charset.UTF16BE   // UTF-16 Big-Endian
Charset.UTF16LE   // UTF-16 Little-Endian
```

### Charset Properties

```scala mdoc:compile-only
import zio.http.Charset

Charset.UTF8.name   // "UTF-8"
Charset.ISO_8859_1.name // "ISO-8859-1"
```

---

## RequestCookie

`RequestCookie` represents a simple cookie sent from client to server in the Cookie header:

```scala
final case class RequestCookie(name: String, value: String)
```

### Creating Request Cookies

```scala mdoc:compile-only
import zio.http.RequestCookie

val cookie = RequestCookie("sessionId", "abc123xyz")
cookie.name   // "sessionId"
cookie.value  // "abc123xyz"
```

### Sending Cookies

Include cookies in requests:

```scala mdoc:compile-only
import zio.http._

val req = Request.get(url)
  .addHeader("cookie", "sessionId=abc123; userId=456")

// Or build from RequestCookie objects
val cookies = Chunk(
  RequestCookie("sessionId", "abc123"),
  RequestCookie("userId", "456")
)
// Render as Cookie header value (see Header operations)
```

---

## ResponseCookie

`ResponseCookie` represents a cookie set by server for client storage, with full RFC 6265 attributes:

```scala
final case class ResponseCookie(
  name: String,
  value: String,
  domain: Option[String] = None,
  path: Option[String] = None,
  maxAge: Option[Long] = None,
  secure: Boolean = false,
  httpOnly: Boolean = false,
  sameSite: Option[SameSite] = None
)
```

### Creating Response Cookies

Create cookies with full control over attributes:

```scala mdoc:compile-only
import zio.http._

val sessionCookie = ResponseCookie(
  name = "sessionId",
  value = "abc123xyz",
  maxAge = Some(3600),          // 1 hour (in seconds)
  isSecure = true,              // HTTPS only (prevents transmission over HTTP)
  isHttpOnly = true,            // No JavaScript access (prevents XSS theft)
  sameSite = Some(SameSite.Strict) // Prevent CSRF attacks
)
```

### Setting Cookies in Responses

Include cookies in responses:

```scala mdoc:compile-only
import zio.http._

val response = Response(Status.Ok)
  .addHeader("set-cookie", sessionCookie.render)
```

### SameSite Attribute

Control cross-site request behavior:

```scala mdoc:compile-only
import zio.http.SameSite

SameSite.Strict  // Only same-site requests (default, safest)
SameSite.Lax     // Top-level navigations allowed (links, forms)
SameSite.None_   // Cross-site allowed (requires Secure flag)
```

---

## Form

`Form` represents URL-encoded form data as key-value pairs:

```scala
final case class Form(entries: Chunk[(String, String)])
```

### Creating Forms

Create forms with key-value pairs:

```scala mdoc:compile-only
import zio.http.Form

val form = Form(
  Chunk(
    ("username" -> "alice"),
    ("email" -> "alice@example.com"),
    ("subscribe" -> "true")
  )
)
```

### Submitting Forms

Send forms as request body:

```scala mdoc:compile-only
import zio.http._

val form = Form(Chunk(("username", "alice"), ("password", "secret")))
val body = Body.fromString(form.encode, Charset.UTF8)

val request = Request.post(url, body)
  .addHeader("content-type", "application/x-www-form-urlencoded")
```

### Form Operations

Access and encode form data:

```scala mdoc:compile-only
import zio.http.Form
import zio.blocks.chunk.Chunk

val form = Form(Chunk(("key1", "value1"), ("key2", "value2")))

form.entries      // Chunk[(String, String)] of all entries
form.encode       // "key1=value1&key2=value2" (percent-encoded for URL)
```

---

## FormField

`FormField` represents individual fields in multipart form data (used for file uploads):

```scala
sealed trait FormField

case class FormField.Simple(name: String, value: String)
case class FormField.Text(name: String, filename: Option[String], value: String, contentType: Option[ContentType])
case class FormField.Binary(name: String, filename: Option[String], data: Chunk[Byte], contentType: Option[ContentType])
```

### Multipart Form Fields

Create fields for multipart form submissions:

```scala mdoc:compile-only
import zio.http._
import zio.blocks.chunk.Chunk

val textField = FormField.Simple("username", "alice")

val imageBytes = Chunk.empty[Byte]  // In real code, this would be actual image data
val fileField = FormField.Binary(
  name = "avatar",
  filename = Some("profile.png"),
  data = imageBytes,
  contentType = Some(ContentType.ImagePng)
)
```

---

## Request

`Request` is the core type representing an HTTP request message with method, URL, headers, body, and version:

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

Create requests using convenience methods or constructors:

```scala mdoc:compile-only
import zio.http._

// Using convenience methods (recommended)
Request.get(url)
Request.post(url, body)
Request.put(url, body)
Request.delete(url)
Request.patch(url, body)
Request.head(url)
Request.options(url)

// Using constructor
Request(
  method = Method.POST,
  url = URL.parse("https://api.example.com/users").toOption.get,
  headers = Headers("content-type" -> "application/json"),
  body = Body.fromString("""{"name":"Alice"}""", Charset.UTF8),
  version = Version.`HTTP/1.1`
)
```

### Request Operations

Access and modify request components:

```scala mdoc:compile-only
import zio.http._

val req = Request.get(url)

req.method              // Method.GET
req.url                 // URL
req.headers             // Headers collection
req.body                // Body
req.version             // Version

req.addHeader("authorization", "Bearer token")  // Add header
req.updateHeaders(_.add("x-custom", "value"))   // Transform headers
```

---

## Response

`Response` is the core type representing an HTTP response message with status, headers, body, and version:

```scala
final case class Response(
  status: Status,
  headers: Headers,
  body: Body,
  version: Version
)
```

### Creating Responses

Create responses with status, headers, and body:

```scala mdoc:compile-only
import zio.http._

Response(
  status = Status.Ok,
  headers = Headers("content-type" -> "application/json"),
  body = Body.fromString("""{"message":"ok"}""", Charset.UTF8),
  version = Version.`HTTP/1.1`
)
```

### Response Convenience Methods

Quick constructors for common responses:

```scala mdoc:compile-only
import zio.http._

Response.ok(body)              // 200 OK with body
Response.text("Hello")         // 200 OK with text body
Response.json(jsonString)      // 200 OK with JSON content type
Response.redirect(url)         // 302 Found redirect
Response.notFound              // 404 Not Found
Response.internalServerError   // 500 Internal Server Error
```

### Response Operations

Access and modify response components:

```scala mdoc:compile-only
import zio.http._

val resp = Response.ok(body)

resp.status                     // Status.Ok
resp.headers                    // Headers
resp.body                       // Body
resp.version                    // Version

resp.withStatus(Status.Created) // Change status code
resp.addHeader("cache-control", "no-cache") // Add header
```

---

## Integration Points

The HTTP model types integrate with each other in a natural composition hierarchy:

- **Request & Response** are the top-level message types, containing all other types
- **URL** decomposes into Scheme, Host, Port, Path, QueryParams, and Fragment
- **Headers** is a collection of individual Header objects (generic strings and typed headers)
- **Body** carries ContentType metadata describing its content
- **Method & Status** are enumerations representing HTTP verbs and response codes
- **Version** describes the HTTP protocol version being used
- **Cookies** (Request & Response variants) appear as header values in Cookie and Set-Cookie headers
- **Form** is serialized as a Body with `application/x-www-form-urlencoded` content type

The module is designed for **composition without coupling** — higher-level libraries (HTTP clients, servers, middleware) can import and use only the types they need without being forced to depend on unrelated types. The zero-dependency design means this module can be used in any Scala project without dragging in the ZIO ecosystem.
