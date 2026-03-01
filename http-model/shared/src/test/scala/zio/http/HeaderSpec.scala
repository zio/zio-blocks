package zio.http

import zio.test._

object HeaderSpec extends HttpModelBaseSpec {
  def spec: Spec[TestEnvironment, Any] = suite("Header")(
    suite("Custom header")(
      test("custom headers via rawGet") {
        val headers = Headers("x-request-id" -> "abc-123", "x-trace-id" -> "trace-456")
        assertTrue(
          headers.rawGet("x-request-id") == Some("abc-123"),
          headers.rawGet("x-trace-id") == Some("trace-456"),
          headers.rawGet("x-missing") == None
        )
      }
    )
  )
}
