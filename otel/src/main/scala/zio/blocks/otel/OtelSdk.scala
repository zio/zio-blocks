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

import java.util.concurrent.{ScheduledFuture, TimeUnit}

final class OtelSdk private[otel] (
  val tracerProvider: TracerProvider,
  val meterProvider: MeterProvider,
  val loggerProvider: LoggerProvider,
  private val shutdownHook: Thread,
  private val metricExportFuture: ScheduledFuture[_]
) {

  def shutdown(): Unit = {
    tracerProvider.shutdown()
    meterProvider.shutdown()
    loggerProvider.shutdown()
    val _ = metricExportFuture.cancel(false)
    try Runtime.getRuntime.removeShutdownHook(shutdownHook)
    catch { case _: IllegalStateException => () }
  }
}

object OtelSdk {

  def builder: OtelSdkBuilder = new OtelSdkBuilder()
}

final class OtelSdkBuilder private[otel] () {
  private var resource: Resource             = Resource.default
  private var sampler: Sampler               = AlwaysOnSampler
  private var exporterConfig: ExporterConfig = ExporterConfig()

  def setResource(r: Resource): OtelSdkBuilder = {
    this.resource = r
    this
  }

  def setSampler(s: Sampler): OtelSdkBuilder = {
    this.sampler = s
    this
  }

  def setExporterEndpoint(url: String): OtelSdkBuilder = {
    this.exporterConfig = exporterConfig.copy(endpoint = url)
    this
  }

  def setExporterHeaders(h: Map[String, String]): OtelSdkBuilder = {
    this.exporterConfig = exporterConfig.copy(headers = h)
    this
  }

  def setExporterConfig(c: ExporterConfig): OtelSdkBuilder = {
    this.exporterConfig = c
    this
  }

  def build(): OtelSdk = {
    val httpSender     = new JdkHttpSender(exporterConfig.timeout)
    val contextStorage = ContextStorage.create[Option[SpanContext]](None)
    val sdkScope       = InstrumentationScope("otel-sdk")

    // Trace exporter as a SpanProcessor
    val traceExporter  = new OtlpJsonTraceExporter(exporterConfig, resource, sdkScope, httpSender)
    val tracerProvider = TracerProvider.builder
      .setResource(resource)
      .setSampler(sampler)
      .addSpanProcessor(traceExporter)
      .setContextStorage(contextStorage)
      .build()

    // Meter provider
    val meterProvider = MeterProvider.builder
      .setResource(resource)
      .build()

    // Schedule periodic metric export
    val metricExporter = new OtlpJsonMetricExporter(
      exporterConfig,
      resource,
      sdkScope,
      httpSender,
      () =>
        meterProvider.reader.collectAllMetrics().map { data =>
          NamedMetric("metric", "", "", data)
        }
    )

    val metricExportFuture = PlatformExecutor.schedule(
      exporterConfig.flushIntervalMillis,
      exporterConfig.flushIntervalMillis,
      TimeUnit.MILLISECONDS
    )(new Runnable { def run(): Unit = { metricExporter.exportMetrics(); () } })

    // Logger provider with log exporter
    val logExporter    = new OtlpJsonLogExporter(exporterConfig, resource, sdkScope, httpSender)
    val loggerProvider = LoggerProvider.builder
      .setResource(resource)
      .addLogRecordProcessor(logExporter)
      .setContextStorage(contextStorage)
      .build()

    // JVM shutdown hook
    val hook = new Thread(new Runnable {
      def run(): Unit = {
        tracerProvider.shutdown()
        meterProvider.shutdown()
        loggerProvider.shutdown()
        val _ = metricExportFuture.cancel(false)
      }
    })
    Runtime.getRuntime.addShutdownHook(hook)

    new OtelSdk(tracerProvider, meterProvider, loggerProvider, hook, metricExportFuture)
  }
}
