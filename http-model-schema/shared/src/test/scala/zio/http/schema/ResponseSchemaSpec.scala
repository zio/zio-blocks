package zio.http.schema

import zio.blocks.chunk.Chunk
import zio.http._
import zio.test._

object ResponseSchemaSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment, Any] = suite("ResponseSchemaOps")(
    suite("header delegation")(
      test("header[String] delegates to HeadersSchemaOps") {
        val response = Response.ok.addHeader("x-custom", "value")
        val ops      = new ResponseSchemaOps(response)
        assertTrue(ops.header[String]("x-custom") == Right("value"))
      },
      test("headerAll[String] returns all header values") {
        val response = Response.ok.addHeader("x-tag", "a").addHeader("x-tag", "b")
        assertTrue(response.headerAll[String]("x-tag") == Right(Chunk("a", "b")))
      },
      test("headerOrElse[Int] returns default when missing") {
        assertTrue(Response.ok.headerOrElse[Int]("x-page", 1) == 1)
      },
      test("headerOrElse[Int] returns parsed value when present") {
        val response = Response.ok.addHeader("x-page", "5")
        assertTrue(response.headerOrElse[Int]("x-page", 1) == 5)
      }
    )
  ) @@ TestAspect.timeout(zio.Duration.fromSeconds(60))
}
