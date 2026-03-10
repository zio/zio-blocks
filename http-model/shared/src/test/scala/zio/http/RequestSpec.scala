package zio.http

import zio.test._

object RequestSpec extends HttpModelBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("Request")(
    suite("construction")(
      test("can be constructed with all fields") {
        val url =
          URL(Some(Scheme.HTTPS), Some("example.com"), Some(443), Path("/api/users"), QueryParams("page" -> "1"), None)
        val headers = Headers("content-type" -> "application/json")
        val body    = Body.fromString("hello")
        val request = Request(Method.POST, url, headers, body, Version.`HTTP/1.1`)
        assertTrue(
          request.method == Method.POST,
          request.url == url,
          request.headers == headers,
          request.body == body,
          request.version == Version.`HTTP/1.1`
        )
      },
      test("is immutable via copy") {
        val request  = Request.get(URL.fromPath(Path("/a")))
        val modified = request.copy(method = Method.DELETE)
        assertTrue(
          request.method == Method.GET,
          modified.method == Method.DELETE
        )
      }
    ),
    suite("Request.get")(
      test("creates GET request with correct defaults") {
        val url     = URL.fromPath(Path("/test"))
        val request = Request.get(url)
        assertTrue(
          request.method == Method.GET,
          request.url == url,
          request.headers == Headers.empty,
          request.body == Body.empty,
          request.version == Version.`HTTP/1.1`
        )
      }
    ),
    suite("Request.post")(
      test("creates POST request with correct defaults") {
        val url     = URL.fromPath(Path("/submit"))
        val body    = Body.fromString("data")
        val request = Request.post(url, body)
        assertTrue(
          request.method == Method.POST,
          request.url == url,
          request.headers == Headers.empty,
          request.body == body,
          request.version == Version.`HTTP/1.1`
        )
      }
    ),
    suite("header")(
      test("returns typed header from headers") {
        val headers = Headers("host" -> "example.com:8080")
        val request = Request(Method.GET, URL.fromPath(Path.root), headers, Body.empty, Version.`HTTP/1.1`)
        val host    = request.header(zio.http.headers.Host)
        assertTrue(
          host.isDefined,
          host.get.host == "example.com",
          host.get.port == Some(8080)
        )
      },
      test("returns None for missing header") {
        val request = Request.get(URL.fromPath(Path.root))
        assertTrue(request.header(zio.http.headers.Host).isEmpty)
      }
    ),
    suite("contentType")(
      test("returns ContentType from headers") {
        val headers = Headers("content-type" -> "application/json")
        val request = Request(Method.POST, URL.fromPath(Path.root), headers, Body.empty, Version.`HTTP/1.1`)
        val ct      = request.contentType
        assertTrue(
          ct.isDefined,
          ct.get == ContentType.`application/json`
        )
      },
      test("returns None when no content-type header") {
        val request = Request.get(URL.fromPath(Path.root))
        assertTrue(request.contentType.isEmpty)
      }
    ),
    suite("path")(
      test("delegates to url.path") {
        val path    = Path("/api/v1/users")
        val url     = URL.fromPath(path)
        val request = Request.get(url)
        assertTrue(request.path == path)
      }
    ),
    suite("queryParams")(
      test("delegates to url.queryParams") {
        val url     = URL(None, None, None, Path.root, QueryParams("key" -> "value", "foo" -> "bar"), None)
        val request = Request.get(url)
        assertTrue(
          request.queryParams == url.queryParams,
          request.queryParams.getFirst("key") == Some("value"),
          request.queryParams.getFirst("foo") == Some("bar")
        )
      }
    ),
    suite("Request.delete")(
      test("creates DELETE request") {
        val url     = URL.fromPath(Path("/resource/1"))
        val request = Request.delete(url)
        assertTrue(
          request.method == Method.DELETE,
          request.url == url,
          request.body == Body.empty
        )
      }
    ),
    suite("Request.put")(
      test("creates PUT request with body") {
        val url     = URL.fromPath(Path("/resource/1"))
        val body    = Body.fromString("updated")
        val request = Request.put(url, body)
        assertTrue(
          request.method == Method.PUT,
          request.body == body
        )
      }
    ),
    suite("Request.patch")(
      test("creates PATCH request with body") {
        val url     = URL.fromPath(Path("/resource/1"))
        val body    = Body.fromString("{\"name\": \"updated\"}")
        val request = Request.patch(url, body)
        assertTrue(
          request.method == Method.PATCH,
          request.body == body
        )
      }
    ),
    suite("Request.head")(
      test("creates HEAD request") {
        val url     = URL.fromPath(Path("/resource"))
        val request = Request.head(url)
        assertTrue(
          request.method == Method.HEAD,
          request.body == Body.empty
        )
      }
    ),
    suite("Request.options")(
      test("creates OPTIONS request") {
        val url     = URL.fromPath(Path("/resource"))
        val request = Request.options(url)
        assertTrue(
          request.method == Method.OPTIONS,
          request.body == Body.empty
        )
      }
    ),
    suite("addHeader")(
      test("adds a header to request") {
        val request = Request.get(URL.fromPath(Path.root)).addHeader("Accept", "text/html")
        assertTrue(request.headers.rawGet("accept") == Some("text/html"))
      }
    ),
    suite("addHeaders")(
      test("adds multiple headers") {
        val extra   = Headers("Accept" -> "text/html", "X-Custom" -> "value")
        val request = Request.get(URL.fromPath(Path.root)).addHeaders(extra)
        assertTrue(
          request.headers.rawGet("accept") == Some("text/html"),
          request.headers.rawGet("x-custom") == Some("value")
        )
      }
    ),
    suite("removeHeader")(
      test("removes a header from request") {
        val request = Request
          .get(URL.fromPath(Path.root))
          .addHeader("Accept", "text/html")
          .removeHeader("Accept")
        assertTrue(!request.headers.has("accept"))
      }
    ),
    suite("setHeader")(
      test("sets a header on request") {
        val request = Request
          .get(URL.fromPath(Path.root))
          .addHeader("Accept", "text/html")
          .setHeader("Accept", "application/json")
        assertTrue(request.headers.rawGet("accept") == Some("application/json"))
      }
    ),
    suite("body (setter)")(
      test("replaces body") {
        val newBody = Body.fromString("new body")
        val request = Request.get(URL.fromPath(Path.root)).body(newBody)
        assertTrue(request.body == newBody)
      }
    ),
    suite("url (setter)")(
      test("replaces url") {
        val newUrl  = URL.fromPath(Path("/new"))
        val request = Request.get(URL.fromPath(Path("/old"))).url(newUrl)
        assertTrue(request.url == newUrl)
      }
    ),
    suite("method (setter)")(
      test("replaces method") {
        val request = Request.get(URL.fromPath(Path.root)).method(Method.POST)
        assertTrue(request.method == Method.POST)
      }
    ),
    suite("version (setter)")(
      test("replaces version") {
        val request = Request.get(URL.fromPath(Path.root)).version(Version.`HTTP/1.0`)
        assertTrue(request.version == Version.`HTTP/1.0`)
      }
    ),
    suite("updateHeaders")(
      test("transforms headers via function") {
        val request = Request
          .get(URL.fromPath(Path.root))
          .addHeader("Accept", "text/html")
          .updateHeaders(_.add("X-Custom", "value"))
        assertTrue(
          request.headers.rawGet("accept") == Some("text/html"),
          request.headers.rawGet("x-custom") == Some("value")
        )
      }
    ),
    suite("updateUrl")(
      test("transforms url via function") {
        val request = Request
          .get(URL.fromPath(Path("/api")))
          .updateUrl(_ / "users")
        assertTrue(request.url.path.segments.length == 2)
      }
    )
  )
}
