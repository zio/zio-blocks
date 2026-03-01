package zio.http.headers

import zio.test._

object CookieHeadersSpec extends ZIOSpecDefault {
  def spec: Spec[TestEnvironment, Any] = suite("CookieHeaders")(
    suite("CookieHeader")(
      test("parse and render") {
        val result = CookieHeader.parse("session=abc123; theme=dark")
        assertTrue(
          result == Right(CookieHeader("session=abc123; theme=dark")),
          result.map(_.headerName) == Right("cookie")
        )
      },
      test("render") {
        assertTrue(CookieHeader.render(CookieHeader("a=b")) == "a=b")
      }
    ),
    suite("SetCookieHeader")(
      test("parse and render") {
        val result = SetCookieHeader.parse("session=abc123; Path=/; HttpOnly")
        assertTrue(
          result == Right(SetCookieHeader("session=abc123; Path=/; HttpOnly")),
          result.map(_.headerName) == Right("set-cookie")
        )
      },
      test("render") {
        assertTrue(SetCookieHeader.render(SetCookieHeader("a=b; Path=/")) == "a=b; Path=/")
      }
    )
  )
}
