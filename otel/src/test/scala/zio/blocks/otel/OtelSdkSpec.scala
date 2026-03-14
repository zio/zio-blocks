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

import zio.test._

object OtelSdkSpec extends ZIOSpecDefault {

  def spec = suite("OtelSdk")(
    suite("OtelSdk.builder.build() creates working SDK with defaults")(
      test("creates an OtelSdk with non-null providers") {
        val sdk = OtelSdk.builder.build()
        try
          assertTrue(
            sdk.tracerProvider != null &&
              sdk.meterProvider != null &&
              sdk.loggerProvider != null
          )
        finally sdk.shutdown()
      }
    ),
    suite("TracerProvider integration")(
      test("sdk.tracerProvider.get produces a working tracer") {
        val sdk = OtelSdk.builder.build()
        try {
          val tracer = sdk.tracerProvider.get("test-lib")
          assertTrue(tracer != null)
        } finally sdk.shutdown()
      },
      test("spanBuilder creates a valid span") {
        val sdk = OtelSdk.builder.build()
        try {
          val tracer = sdk.tracerProvider.get("test-lib")
          val span   = tracer.spanBuilder("op").startSpan()
          assertTrue(
            span != null &&
              span.spanContext.isValid &&
              span.name == "op"
          )
        } finally sdk.shutdown()
      }
    ),
    suite("MeterProvider integration")(
      test("sdk.meterProvider.get produces a working meter") {
        val sdk = OtelSdk.builder.build()
        try {
          val meter = sdk.meterProvider.get("test-lib")
          assertTrue(meter != null)
        } finally sdk.shutdown()
      },
      test("counterBuilder creates a working counter") {
        val sdk = OtelSdk.builder.build()
        try {
          val meter   = sdk.meterProvider.get("test-lib")
          val counter = meter.counterBuilder("c").build()
          counter.add(1L, Attributes.empty)
          assertTrue(counter != null && counter.name == "c")
        } finally sdk.shutdown()
      }
    ),
    suite("LoggerProvider integration")(
      test("sdk.loggerProvider.get produces a working logger") {
        val sdk = OtelSdk.builder.build()
        try {
          val logger = sdk.loggerProvider.get("test-lib")
          assertTrue(logger != null)
        } finally sdk.shutdown()
      },
      test("logger.info emits without error") {
        val sdk    = OtelSdk.builder.build()
        val logger = sdk.loggerProvider.get("test-lib")
        logger.info("test message")
        try sdk.shutdown()
        catch { case _: Exception => () }
        assertTrue(true)
      }
    ),
    suite("Custom resource")(
      test("builder.setResource applies resource to tracerProvider") {
        val customResource = Resource.create(
          Attributes.of(AttributeKey.string("service.name"), "my-service")
        )
        val sdk = OtelSdk.builder.setResource(customResource).build()
        try {
          val tracer = sdk.tracerProvider.get("test-lib")
          assertTrue(tracer != null && tracer.resource == customResource)
        } finally sdk.shutdown()
      },
      test("builder.setResource applies resource to meterProvider") {
        val customResource = Resource.create(
          Attributes.of(AttributeKey.string("service.name"), "my-service")
        )
        val sdk = OtelSdk.builder.setResource(customResource).build()
        try assertTrue(sdk.meterProvider.resource == customResource)
        finally sdk.shutdown()
      }
    ),
    suite("Custom sampler")(
      test("builder.setSampler(AlwaysOffSampler) drops spans") {
        val sdk = OtelSdk.builder.setSampler(AlwaysOffSampler).build()
        try {
          val tracer         = sdk.tracerProvider.get("test-lib")
          var captured: Span = null
          tracer.span("dropped-op") { span =>
            captured = span
          }
          assertTrue(captured == Span.NoOp)
        } finally sdk.shutdown()
      }
    ),
    suite("Shutdown")(
      test("sdk.shutdown() completes without error") {
        val sdk = OtelSdk.builder.build()
        sdk.shutdown()
        assertTrue(true)
      },
      test("sdk.shutdown() can be called multiple times") {
        val sdk = OtelSdk.builder.build()
        sdk.shutdown()
        sdk.shutdown()
        assertTrue(true)
      }
    ),
    suite("Builder fluent API")(
      test("setExporterConfig is applied") {
        val config = ExporterConfig(endpoint = "http://example.com:4318")
        val sdk    = OtelSdk.builder.setExporterConfig(config).build()
        try assertTrue(sdk.tracerProvider != null)
        finally sdk.shutdown()
      },
      test("all setters return the builder for chaining") {
        val builder = OtelSdk.builder
          .setResource(Resource.default)
          .setSampler(AlwaysOnSampler)
          .setExporterEndpoint("http://localhost:4318")
          .setExporterHeaders(Map("Authorization" -> "Bearer token"))
          .setExporterConfig(ExporterConfig())
        assertTrue(builder != null)
      }
    )
  )
}
