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
 * Response from an HTTP sender. Headers are stored with their original casing;
 * lookups via `firstHeader` are case-insensitive. The body is the raw response
 * bytes (may be empty).
 */
final case class HttpResponse(
  statusCode: Int,
  body: Array[Byte],
  headers: Map[String, Seq[String]]
) {
  def firstHeader(name: String): Option[String] =
    headers.find { case (k, _) => k.equalsIgnoreCase(name) }.flatMap(_._2.headOption)
}

/**
 * SPI for sending OTLP export payloads over HTTP. Implementations must be
 * synchronous (blocking). Retry and backoff are handled by the caller
 * (`BatchProcessor`). `shutdown()` should release any held resources
 * (connections, thread pools).
 */
trait HttpSender {
  def send(url: String, headers: Map[String, String], body: Array[Byte]): HttpResponse
  def shutdown(): Unit
}

object HttpSender {
  def jdk(timeout: Duration = Duration.ofSeconds(30)): HttpSender =
    new JdkHttpSender(timeout)
}

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
    val it = response.headers().map().entrySet().iterator()
    while (it.hasNext) {
      val entry = it.next()
      val values = new scala.collection.mutable.ArrayBuffer[String](entry.getValue.size())
      val vit = entry.getValue.iterator()
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
