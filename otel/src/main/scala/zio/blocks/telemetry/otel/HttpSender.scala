/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.telemetry.otel

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse => JdkHttpResponse}
import java.time.Duration

/**
 * The response to an [[HttpSender]] request: the status code, the body bytes
 * (empty when there is none) and the headers, each name with its values. Header
 * names keep the casing the sender gives them; `firstHeader` ignores case.
 */
final case class HttpResponse(
  statusCode: Int,
  body: Array[Byte],
  headers: Map[String, Seq[String]]
) {

  /**
   * Returns the first value of the header `name`, compared ignoring case, or
   * `None` when there is none.
   *
   * {{{
   * val throttled = HttpResponse(429, Array.emptyByteArray, Map("Retry-After" -> Seq("30")))
   * throttled.firstHeader("retry-after") // Some(30)
   * throttled.firstHeader("Content-Type") // None
   * }}}
   */
  def firstHeader(name: String): Option[String] =
    headers.find { case (k, _) => k.equalsIgnoreCase(name) }.flatMap(_._2.headOption)
}

/**
 * Sends one OTLP payload to a collector: an HTTP `POST` of `body` to `url` with
 * `headers`. Implement it to use another HTTP client, sign requests, go through
 * a proxy, or keep requests in memory in tests; `HttpSender.jdk` returns one
 * built on the JDK's HTTP client.
 *
 * `send` blocks until the response arrives and returns every status as an
 * [[HttpResponse]], leaving retries to the caller; [[ExportResult]] tells
 * whether a status is worth retrying.
 *
 * {{{
 * import zio.blocks.telemetry._
 * import scala.collection.mutable.ListBuffer
 *
 * val sent = ListBuffer.empty[String]
 * val recording = new HttpSender {
 *   def send(url: String, headers: Map[String, String], body: Array[Byte]): HttpResponse = {
 *     sent += url
 *     HttpResponse(200, Array.emptyByteArray, Map.empty)
 *   }
 *   def shutdown(): Unit = ()
 * }
 *
 * val record = LogRecord.builder.setSeverity(Severity.Warn).setBody("payment retried").build
 * val body   = OtlpJsonEncoder.encodeLogs(Seq(record), Resource.empty, InstrumentationScope("checkout"))
 * ExportResult.fromHttpResponse(
 *   recording.send("http://localhost:4318/v1/logs", Map("Content-Type" -> "application/json"), body)
 * ) // Success
 * sent.toList // List(http://localhost:4318/v1/logs)
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/otel/ OTLP Export]]
 */
trait HttpSender {

  /**
   * POSTs `body` to `url` with `headers` and returns the response, whatever its
   * status. Blocks until the response arrives, and throws when none arrives:
   * `HttpSender.jdk` throws a `java.io.IOException` such as
   * `java.net.ConnectException` or `java.net.http.HttpTimeoutException`.
   */
  def send(url: String, headers: Map[String, String], body: Array[Byte]): HttpResponse

  /**
   * Releases what the sender holds, such as connections or threads. The
   * module's exporters call it when they shut down.
   */
  def shutdown(): Unit
}

/** Holds `jdk`, the built-in sender. */
object HttpSender {

  /**
   * Returns a sender built on `java.net.http.HttpClient`, which ships with the
   * JDK. `timeout` limits both connecting and waiting for the response
   * (`java.net.http.HttpTimeoutException` when it runs out). It does not follow
   * redirects, so a `3xx` comes back as the response. Its `send` throws
   * `java.net.ConnectException` when nothing listens at `url`, and
   * `java.lang.IllegalArgumentException` when `url` has no `http` or `https`
   * scheme; its `shutdown` does nothing.
   *
   * {{{
   * import java.time.Duration
   *
   * val config = ExporterConfig(endpoint = "https://otlp.example.com:4318", timeout = Duration.ofSeconds(10))
   * val sender = HttpSender.jdk(config.timeout)
   * }}}
   */
  def jdk(timeout: Duration = Duration.ofSeconds(30)): HttpSender =
    new JdkHttpSender(timeout)
}

/** The [[HttpSender]] that `HttpSender.jdk` returns. */
private[otel] final class JdkHttpSender(
  timeout: Duration = Duration.ofSeconds(30)
) extends HttpSender {
  private val client = HttpClient
    .newBuilder()
    .connectTimeout(timeout)
    .build()

  def send(url: String, headers: Map[String, String], body: Array[Byte]): HttpResponse = {
    val builder = HttpRequest
      .newBuilder()
      .uri(URI.create(url))
      .POST(HttpRequest.BodyPublishers.ofByteArray(body))
      .timeout(timeout)

    headers.foreach { case (k, v) =>
      builder.header(k, v)
    }

    val request  = builder.build()
    val response = client.send(request, JdkHttpResponse.BodyHandlers.ofByteArray())

    val responseHeaders = scala.collection.mutable.Map[String, Seq[String]]()
    val it              = response.headers().map().entrySet().iterator()
    while (it.hasNext) {
      val entry  = it.next()
      val values = new scala.collection.mutable.ArrayBuffer[String](entry.getValue.size())
      val vit    = entry.getValue.iterator()
      while (vit.hasNext) values += vit.next()
      responseHeaders(entry.getKey) = values.toSeq
    }

    HttpResponse(
      statusCode = response.statusCode(),
      body = response.body(),
      headers = responseHeaders.toMap
    )
  }

  def shutdown(): Unit = ()
}
