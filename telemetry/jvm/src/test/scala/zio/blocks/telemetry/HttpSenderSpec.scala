package zio.blocks.telemetry

import zio.test._
import java.time.Duration

object HttpSenderSpec extends ZIOSpecDefault {
  def spec = suite("HttpSenderSpec")(
    test("HttpResponse case class creation") {
      val response = HttpResponse(
        statusCode = 200,
        body = "test body".getBytes(),
        headers = Map("content-type" -> "application/json")
      )
      assertTrue(
        response.statusCode == 200 &&
          response.body.sameElements("test body".getBytes()) &&
          response.headers.get("content-type").contains("application/json")
      )
    },
    test("HttpResponse case class with empty body") {
      val response = HttpResponse(
        statusCode = 204,
        body = Array.empty[Byte],
        headers = Map.empty[String, String]
      )
      assertTrue(
        response.statusCode == 204 &&
          response.body.isEmpty &&
          response.headers.isEmpty
      )
    },
    test("HttpResponse case class with multiple headers") {
      val response = HttpResponse(
        statusCode = 201,
        body = Array.empty[Byte],
        headers = Map(
          "x-header-1"   -> "value1",
          "x-header-2"   -> "value2",
          "content-type" -> "text/plain"
        )
      )
      assertTrue(
        response.headers.size == 3 &&
          response.headers.get("x-header-1").contains("value1") &&
          response.headers.get("x-header-2").contains("value2") &&
          response.headers.get("content-type").contains("text/plain")
      )
    },
    test("JdkHttpSender construction with default timeout") {
      val sender = new JdkHttpSender()
      assertTrue(sender != null)
    },
    test("JdkHttpSender construction with custom timeout") {
      val sender = new JdkHttpSender(timeout = Duration.ofSeconds(60))
      assertTrue(sender != null)
    },
    test("JdkHttpSender shutdown completes without error") {
      val sender = new JdkHttpSender()
      val result = sender.shutdown()
      assertTrue(result == ())
    }
  )
}
