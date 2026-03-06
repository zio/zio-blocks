package zio.http.schema

import zio.blocks.chunk.Chunk
import zio.http._
import zio.test._

object RequestSchemaSpec extends ZIOSpecDefault {

  private def requestWithQuery(pairs: (String, String)*): Request = {
    val qp = QueryParams(pairs: _*)
    Request.get(URL.fromPath(Path.root).copy(queryParams = qp))
  }

  private def requestWithHeaders(pairs: (String, String)*): Request =
    Request.get(URL.fromPath(Path.root)).copy(headers = Headers(pairs: _*))

  def spec: Spec[TestEnvironment, Any] = suite("RequestSchemaOps")(
    suite("query delegation")(
      test("query[Int] delegates to QueryParamsSchemaOps") {
        val request = requestWithQuery("page" -> "42")
        assertTrue(request.query[Int]("page") == Right(42))
      },
      test("queryAll[String] delegates to QueryParamsSchemaOps") {
        val request = requestWithQuery("tag" -> "a", "tag" -> "b")
        assertTrue(request.queryAll[String]("tag") == Right(Chunk("a", "b")))
      },
      test("queryOrElse[Int] returns default when missing") {
        val request = Request.get(URL.fromPath(Path.root))
        assertTrue(request.queryOrElse[Int]("page", 1) == 1)
      }
    ),
    suite("header delegation")(
      test("header[String] delegates to HeadersSchemaOps") {
        val request = requestWithHeaders("x-custom" -> "hello")
        val ops     = new RequestSchemaOps(request)
        assertTrue(ops.header[String]("x-custom") == Right("hello"))
      },
      test("headerAll[String] returns all header values") {
        val request = requestWithHeaders("x-tag" -> "a", "x-tag" -> "b")
        assertTrue(request.headerAll[String]("x-tag") == Right(Chunk("a", "b")))
      },
      test("headerOrElse[Int] returns default when missing") {
        val request = Request.get(URL.fromPath(Path.root))
        assertTrue(request.headerOrElse[Int]("x-page", 1) == 1)
      }
    )
  ) @@ TestAspect.timeout(zio.Duration.fromSeconds(60))
}
