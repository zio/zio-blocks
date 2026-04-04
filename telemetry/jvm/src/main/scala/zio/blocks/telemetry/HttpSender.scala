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

package zio.blocks.telemetry

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse => JdkHttpResponse}
import java.time.Duration

final case class HttpResponse(
  statusCode: Int,
  body: Array[Byte],
  headers: Map[String, String]
)

trait HttpSender {
  def send(url: String, headers: Map[String, String], body: Array[Byte]): HttpResponse
  def shutdown(): Unit
}

final class JdkHttpSender(
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

    val responseHeaders = scala.collection.mutable.Map[String, String]()
    response.headers().map().forEach { case (name, values) =>
      if (!values.isEmpty) {
        responseHeaders(name) = values.get(0)
      }
    }

    HttpResponse(
      statusCode = response.statusCode(),
      body = response.body(),
      headers = responseHeaders.toMap
    )
  }

  def shutdown(): Unit = ()
}
