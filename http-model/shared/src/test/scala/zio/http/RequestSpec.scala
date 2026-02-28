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
        val request = Request(Method.POST, url, headers, body, Version.`Http/1.1`)
        assertTrue(
          request.method == Method.POST,
          request.url == url,
          request.headers == headers,
          request.body == body,
          request.version == Version.`Http/1.1`
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
          request.version == Version.`Http/1.1`
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
          request.version == Version.`Http/1.1`
        )
      }
    ),
    suite("header")(
      test("returns typed header from headers") {
        val headers = Headers("host" -> "example.com:8080")
        val request = Request(Method.GET, URL.fromPath(Path.root), headers, Body.empty, Version.`Http/1.1`)
        val host    = request.header(Header.Host)
        assertTrue(
          host.isDefined,
          host.get.host == "example.com",
          host.get.port == Some(8080)
        )
      },
      test("returns None for missing header") {
        val request = Request.get(URL.fromPath(Path.root))
        assertTrue(request.header(Header.Host).isEmpty)
      }
    ),
    suite("contentType")(
      test("returns ContentType from headers") {
        val headers = Headers("content-type" -> "application/json")
        val request = Request(Method.POST, URL.fromPath(Path.root), headers, Body.empty, Version.`Http/1.1`)
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
    )
  )
}
