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

package zio.blocks.otel

object OtelExample {
  def main(args: Array[String]): Unit = {
    // 1. Build SDK with resource metadata
    val sdk = OtelSdk.builder
      .setResource(
        Resource.default.merge(
          Resource(
            Attributes.builder
              .put(Attributes.ServiceName, "example-service")
              .build
          )
        )
      )
      .setExporterEndpoint("http://localhost:4318")
      .build()

    // 2. Get providers from SDK
    val tracer = sdk.tracerProvider.get("example-lib", "1.0.0")
    val meter  = sdk.meterProvider.get("example-lib", "1.0.0")
    val logger = sdk.loggerProvider.get("example-lib", "1.0.0")

    // 3. Create metric instruments
    val requestCounter   = meter.counterBuilder("http.requests").setUnit("1").build()
    val latencyHistogram =
      meter.histogramBuilder("http.request.duration").setUnit("ms").build()

    // 4. Simulate a request with tracing + metrics + logging
    tracer.span("handleRequest", SpanKind.Server) { span =>
      logger.info(
        "Handling request",
        "method" -> AttributeValue.StringValue("GET"),
        "path"   -> AttributeValue.StringValue("/api/users")
      )

      requestCounter.add(1, Attributes.builder.put("method", "GET").build)

      // Nested span for database call
      tracer.span("db.query", SpanKind.Client) { dbSpan =>
        dbSpan.setAttribute("db.system", "postgresql")
        dbSpan.setAttribute("db.operation", "SELECT")
        logger.debug(
          "Executing query",
          "table" -> AttributeValue.StringValue("users")
        )
        Thread.sleep(50) // simulate work
      }

      val latency = 75.0
      latencyHistogram.record(
        latency,
        Attributes.builder
          .put("method", "GET")
          .put("status", 200L)
          .build
      )

      span.setStatus(SpanStatus.Ok)
      logger.info("Request completed", "status" -> AttributeValue.LongValue(200))
    }

    println(
      "Example complete. Spans, metrics, and logs would be exported to http://localhost:4318"
    )
    sdk.shutdown()
  }
}
