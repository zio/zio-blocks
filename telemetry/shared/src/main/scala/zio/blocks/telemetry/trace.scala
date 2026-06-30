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

import java.util.concurrent.atomic.AtomicReference

/**
 * Global tracing entry point. Works without setup — stores spans in memory by
 * default. Call `install` to wire production exporters.
 *
 * App code uses this directly. Library code should accept `Tracer` via DI.
 */
object trace {
  private val spanStore: InMemorySpanProcessor = new InMemorySpanProcessor()

  private val defaultProvider: TracerProvider =
    TracerProvider.builder
      .addSpanProcessor(spanStore)
      .build()

  private val ref: AtomicReference[TracerProvider] = new AtomicReference[TracerProvider](defaultProvider)

  private def provider: TracerProvider = ref.get()

  /** Creates a span using the default tracer. */
  def span[A](name: String)(f: Span => A): A = provider.get("default").span(name)(f)

  /** Creates a span with explicit kind. */
  def span[A](name: String, kind: SpanKind)(f: Span => A): A = provider.get("default").span(name, kind)(f)

  /** Creates a span with explicit kind and attributes. */
  def span[A](name: String, kind: SpanKind, attributes: Attributes)(f: Span => A): A =
    provider.get("default").span(name, kind, attributes)(f)

  /** Returns a named Tracer for a specific instrumentation scope. */
  def get(name: String): Tracer = provider.get(name)

  /** Replaces the default TracerProvider with a user-configured one. */
  def install(provider: TracerProvider): Unit = ref.set(provider)

  /**
   * Remove the installed TracerProvider. After this, spans are no-ops until you
   * install a provider.
   */
  def removeAll(): Unit = ref.set(TracerProvider.builder.setSampler(AlwaysOffSampler).build())

  /** Returns spans collected by the default in-memory processor. */
  def collectedSpans: List[SpanData] = spanStore.collectedSpans

  /** Clears all collected spans from the in-memory processor. */
  def clearSpans(): Unit = spanStore.clear()
}
