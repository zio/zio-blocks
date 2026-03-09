package zio.http

import zio.test._

object ResponseSpec extends HttpModelBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("Response")(
    suite("construction")(
      test("can be constructed with all fields") {
        val headers  = Headers("content-type" -> "text/html")
        val body     = Body.fromString("<h1>Hello</h1>")
        val response = Response(Status.Ok, headers, body, Version.`HTTP/1.1`)
        assertTrue(
          response.status == Status.Ok,
          response.headers == headers,
          response.body == body,
          response.version == Version.`HTTP/1.1`
        )
      },
      test("is immutable via copy") {
        val response = Response.ok
        val modified = response.copy(status = Status.NotFound)
        assertTrue(
          response.status == Status.Ok,
          modified.status == Status.NotFound
        )
      }
    ),
    suite("Response.ok")(
      test("has status 200") {
        assertTrue(Response.ok.status == Status.Ok)
      },
      test("has empty body") {
        assertTrue(Response.ok.body == Body.empty)
      },
      test("has empty headers") {
        assertTrue(Response.ok.headers == Headers.empty)
      },
      test("has HTTP/1.1 version") {
        assertTrue(Response.ok.version == Version.`HTTP/1.1`)
      }
    ),
    suite("Response.notFound")(
      test("has status 404") {
        assertTrue(Response.notFound.status == Status.NotFound)
      },
      test("has empty body") {
        assertTrue(Response.notFound.body == Body.empty)
      },
      test("has empty headers") {
        assertTrue(Response.notFound.headers == Headers.empty)
      },
      test("has HTTP/1.1 version") {
        assertTrue(Response.notFound.version == Version.`HTTP/1.1`)
      }
    ),
    suite("Response.apply(status)")(
      test("creates response with given status") {
        val response = Response(Status.BadRequest)
        assertTrue(
          response.status == Status.BadRequest,
          response.headers == Headers.empty,
          response.body == Body.empty,
          response.version == Version.`HTTP/1.1`
        )
      }
    ),
    suite("header")(
      test("returns typed header from headers") {
        val headers  = Headers("content-length" -> "42")
        val response = Response(Status.Ok, headers, Body.empty, Version.`HTTP/1.1`)
        val cl       = response.header(zio.http.headers.ContentLength)
        assertTrue(
          cl.isDefined,
          cl.get.length == 42L
        )
      },
      test("returns None for missing header") {
        assertTrue(Response.ok.header(zio.http.headers.ContentLength).isEmpty)
      }
    ),
    suite("contentType")(
      test("returns ContentType from headers") {
        val headers  = Headers("content-type" -> "text/plain")
        val response = Response(Status.Ok, headers, Body.empty, Version.`HTTP/1.1`)
        val ct       = response.contentType
        assertTrue(
          ct.isDefined,
          ct.get == ContentType.`text/plain`
        )
      },
      test("returns None when no content-type header") {
        assertTrue(Response.ok.contentType.isEmpty)
      }
    ),
    suite("Response.badRequest")(
      test("has status 400") {
        assertTrue(Response.badRequest.status == Status.BadRequest)
      }
    ),
    suite("Response.unauthorized")(
      test("has status 401") {
        assertTrue(Response.unauthorized.status == Status.Unauthorized)
      }
    ),
    suite("Response.forbidden")(
      test("has status 403") {
        assertTrue(Response.forbidden.status == Status.Forbidden)
      }
    ),
    suite("Response.internalServerError")(
      test("has status 500") {
        assertTrue(Response.internalServerError.status == Status.InternalServerError)
      }
    ),
    suite("Response.serviceUnavailable")(
      test("has status 503") {
        assertTrue(Response.serviceUnavailable.status == Status.ServiceUnavailable)
      }
    ),
    suite("Response.text")(
      test("creates text response with correct body and status") {
        val response = Response.text("hello world")
        assertTrue(
          response.status == Status.Ok,
          response.body == Body.fromString("hello world")
        )
      }
    ),
    suite("Response.json")(
      test("creates json response with content-type header") {
        val response = Response.json("{\"key\": \"value\"}")
        assertTrue(
          response.status == Status.Ok,
          response.headers.rawGet("content-type") == Some("application/json")
        )
      }
    ),
    suite("Response.redirect")(
      test("temporary redirect by default") {
        val response = Response.redirect("/new-location")
        assertTrue(
          response.status == Status.TemporaryRedirect,
          response.headers.rawGet("location") == Some("/new-location")
        )
      },
      test("permanent redirect when isPermanent is true") {
        val response = Response.redirect("/new-location", isPermanent = true)
        assertTrue(
          response.status == Status.PermanentRedirect,
          response.headers.rawGet("location") == Some("/new-location")
        )
      }
    ),
    suite("Response.seeOther")(
      test("creates 303 response") {
        val response = Response.seeOther("/other")
        assertTrue(
          response.status == Status.SeeOther,
          response.headers.rawGet("location") == Some("/other")
        )
      }
    ),
    suite("Response addHeader")(
      test("adds a header") {
        val response = Response.ok.addHeader("X-Custom", "value")
        assertTrue(response.headers.rawGet("x-custom") == Some("value"))
      }
    ),
    suite("Response addHeaders")(
      test("adds multiple headers") {
        val extra    = Headers("X-A" -> "1", "X-B" -> "2")
        val response = Response.ok.addHeaders(extra)
        assertTrue(
          response.headers.rawGet("x-a") == Some("1"),
          response.headers.rawGet("x-b") == Some("2")
        )
      }
    ),
    suite("Response removeHeader")(
      test("removes a header") {
        val response = Response.ok.addHeader("X-Custom", "value").removeHeader("X-Custom")
        assertTrue(!response.headers.has("x-custom"))
      }
    ),
    suite("Response setHeader")(
      test("sets a header replacing existing") {
        val response = Response.ok
          .addHeader("X-Custom", "old")
          .setHeader("X-Custom", "new")
        assertTrue(response.headers.rawGet("x-custom") == Some("new"))
      }
    ),
    suite("Response body (setter)")(
      test("replaces body") {
        val newBody  = Body.fromString("new body")
        val response = Response.ok.body(newBody)
        assertTrue(response.body == newBody)
      }
    ),
    suite("Response status (setter)")(
      test("replaces status") {
        val response = Response.ok.status(Status.NotFound)
        assertTrue(response.status == Status.NotFound)
      }
    ),
    suite("Response version (setter)")(
      test("replaces version") {
        val response = Response.ok.version(Version.`HTTP/1.0`)
        assertTrue(response.version == Version.`HTTP/1.0`)
      }
    ),
    suite("Response updateHeaders")(
      test("transforms headers via function") {
        val response = Response.ok
          .addHeader("X-A", "1")
          .updateHeaders(_.add("X-B", "2"))
        assertTrue(
          response.headers.rawGet("x-a") == Some("1"),
          response.headers.rawGet("x-b") == Some("2")
        )
      }
    ),
    suite("Response addCookie")(
      test("adds Set-Cookie header") {
        val cookie   = ResponseCookie("session", "abc123")
        val response = Response.ok.addCookie(cookie)
        assertTrue(response.headers.has("set-cookie"))
      }
    )
  )
}
