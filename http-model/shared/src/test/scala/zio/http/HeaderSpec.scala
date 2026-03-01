package zio.http

import zio.test._

object HeaderSpec extends HttpModelBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("Header")(
    suite("Custom header")(
      test("construction and rendering") {
        val h = Header.Custom("x-request-id", "abc-123")
        assertTrue(
          h.headerName == "x-request-id",
          h.renderedValue == "abc-123"
        )
      },
      test("parse and render via Header.Typed") {
        val result = Header.Custom.parse("some-value")
        assertTrue(
          result == Right(Header.Custom("x-custom", "some-value")),
          result.map(_.renderedValue) == Right("some-value")
        )
      },
      test("render via Header.Typed") {
        val h = Header.Custom("x-trace-id", "trace-456")
        assertTrue(Header.Custom.render(h) == "trace-456")
      },
      test("Header.Typed name") {
        assertTrue(Header.Custom.name == "x-custom")
      }
    )
  )
}
