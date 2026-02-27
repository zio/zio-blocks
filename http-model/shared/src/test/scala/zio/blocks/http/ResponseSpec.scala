package zio.blocks.http

import zio.test._

object ResponseSpec extends HttpModelBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("Response")(
    suite("construction")(
      test("can be constructed with all fields") {
        val headers  = Headers("content-type" -> "text/html")
        val body     = Body.fromString("<h1>Hello</h1>")
        val response = Response(Status.Ok, headers, body, Version.`Http/1.1`)
        assertTrue(
          response.status == Status.Ok,
          response.headers == headers,
          response.body == body,
          response.version == Version.`Http/1.1`
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
        assertTrue(Response.ok.version == Version.`Http/1.1`)
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
        assertTrue(Response.notFound.version == Version.`Http/1.1`)
      }
    ),
    suite("Response.apply(status)")(
      test("creates response with given status") {
        val response = Response(Status.BadRequest)
        assertTrue(
          response.status == Status.BadRequest,
          response.headers == Headers.empty,
          response.body == Body.empty,
          response.version == Version.`Http/1.1`
        )
      }
    ),
    suite("header")(
      test("returns typed header from headers") {
        val headers  = Headers("content-length" -> "42")
        val response = Response(Status.Ok, headers, Body.empty, Version.`Http/1.1`)
        val cl       = response.header(Header.ContentLength)
        assertTrue(
          cl.isDefined,
          cl.get.length == 42L
        )
      },
      test("returns None for missing header") {
        assertTrue(Response.ok.header(Header.ContentLength).isEmpty)
      }
    ),
    suite("contentType")(
      test("returns ContentType from headers") {
        val headers  = Headers("content-type" -> "text/plain")
        val response = Response(Status.Ok, headers, Body.empty, Version.`Http/1.1`)
        val ct       = response.contentType
        assertTrue(
          ct.isDefined,
          ct.get == ContentType.`text/plain`
        )
      },
      test("returns None when no content-type header") {
        assertTrue(Response.ok.contentType.isEmpty)
      }
    )
  )
}
