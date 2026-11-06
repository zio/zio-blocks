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
 * The global tracer. `trace.span("checkout") { span => ... }` opens a span,
 * runs the block with it, and ends the span when the block returns or throws,
 * with no set-up.
 *
 * A `trace.span` opened inside another one becomes its child, in the same
 * trace. The current span is kept per thread in the `ContextStorage` that the
 * global `log` also reads, so log records written inside a span carry its trace
 * and span ids. A span opened on a thread that the block starts, or in a
 * callback that runs after the block returns, starts a new trace.
 *
 * Out of the box, every span is sampled and kept in an in-memory buffer of the
 * last 1024 finished spans, which `trace.collectedSpans` returns.
 * `trace.install` replaces the backend with a [[TracerProvider]] of your own.
 * Application code calls `trace` directly; library code should take a
 * [[Tracer]] as a parameter instead.
 *
 * A known bug: after `trace.install` or `trace.removeAll()`, the buffer behind
 * `trace.collectedSpans` receives no new spans for the rest of the program,
 * except from tracers taken with `trace.get` before, because no call reconnects
 * it.
 *
 * {{{
 * trace.clearSpans()
 *
 * trace.span("checkout", SpanKind.Server) { _ =>
 *   trace.span("load-cart")(span => span.setAttribute("cart.items", 3L))
 *   trace.span("charge-card")(_ => ())
 * }
 *
 * val spans = trace.collectedSpans
 * spans.map(_.name) // List(load-cart, charge-card, checkout)
 * spans.head.parentSpanContext.spanId == spans.last.spanContext.spanId // true
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/tracing/ Tracing]]
 */
object trace {
  private val spanStore: InMemorySpanProcessor = new InMemorySpanProcessor()

  private val defaultProvider: TracerProvider =
    TracerProvider.builder
      .addSpanProcessor(spanStore)
      .build()

  private val ref: AtomicReference[TracerProvider] = new AtomicReference[TracerProvider](defaultProvider)

  private def provider: TracerProvider = ref.get()

  /**
   * Opens a span named `name` of kind `Internal`, runs `f` with it, ends the
   * span when `f` returns or throws, and returns the result of `f`.
   *
   * The span belongs to the scope `default` of the provider installed at the
   * time of the call. An exception from `f` propagates once the span has ended,
   * and the span's status stays `Unset` unless `f` sets one.
   *
   * {{{
   * trace.clearSpans()
   *
   * val total = trace.span("price-cart") { span =>
   *   span.setAttribute("cart.items", 3L)
   *   span.setStatus(SpanStatus.Ok)
   *   59.97
   * }
   *
   * total.toString // 59.97
   * trace.collectedSpans.map(_.status) // List(Ok)
   * }}}
   */
  def span[A](name: String)(f: Span => A): A = provider.get("default").span(name)(f)

  /** Opens a span named `name` of kind `kind`, as `span(name)(f)` does. */
  def span[A](name: String, kind: SpanKind)(f: Span => A): A = provider.get("default").span(name, kind)(f)

  /**
   * Opens a span named `name` of kind `kind` that starts with `attributes`, as
   * `span(name)(f)` does.
   *
   * The sampler sees `attributes` when it decides whether to record the span;
   * attributes that `f` sets come too late for that.
   *
   * {{{
   * trace.clearSpans()
   *
   * val dbSystem = AttributeKey.string("db.system")
   * trace.span("load-orders", SpanKind.Client, Attributes.of(dbSystem, "postgresql")) { span =>
   *   span.setAttribute("db.rows", 12L)
   * }
   *
   * trace.collectedSpans.head.attributes.get(dbSystem) // Some(postgresql)
   * }}}
   */
  def span[A](name: String, kind: SpanKind, attributes: Attributes)(f: Span => A): A =
    provider.get("default").span(name, kind, attributes)(f)

  /**
   * Returns a new [[Tracer]] whose spans carry the scope `name`, from the
   * provider installed now.
   *
   * The tracer keeps that provider: a tracer taken before `trace.install` still
   * records into the old one. Take tracers after installing, one per component,
   * and pass them to the code that opens spans.
   *
   * {{{
   * trace.clearSpans()
   *
   * val inventory = trace.get("com.example.inventory")
   * inventory.span("reserve-stock")(_ => ())
   * trace.span("unscoped-work")(_ => ())
   *
   * trace.collectedSpans.map(_.instrumentationScope.name) // List(com.example.inventory, default)
   * }}}
   */
  def get(name: String): Tracer = provider.get(name)

  /**
   * Replaces the provider behind `trace.span` and `trace.get` with `provider`,
   * for every thread. The previous provider is not shut down, and tracers taken
   * from it keep using it.
   *
   * A known bug: from then on, the buffer behind `trace.collectedSpans`
   * receives no new spans for the rest of the program, except from tracers
   * taken with `trace.get` before, because no call reconnects it. Give the
   * provider a processor of its own to read its spans.
   *
   * {{{
   * import scala.collection.mutable.ListBuffer
   *
   * val finished = ListBuffer.empty[SpanData]
   * val inMemory = new SpanProcessor {
   *   def onStart(span: Span): Unit = ()
   *   def onEnd(spanData: SpanData): Unit = finished += spanData
   *   def shutdown(): Unit = ()
   *   def forceFlush(): Unit = ()
   * }
   * trace.install(
   *   TracerProvider.builder
   *     .setResource(Resource.create(Attributes.of(Attributes.ServiceName, "order-service")))
   *     .addSpanProcessor(inMemory)
   *     .build()
   * )
   *
   * trace.span("handle-request", SpanKind.Server)(_.setAttribute("http.route", "/orders"))
   *
   * finished.map(_.name) // ListBuffer(handle-request)
   * finished.head.resource.attributes.get(Attributes.ServiceName) // Some(order-service)
   * }}}
   */
  def install(provider: TracerProvider): Unit = ref.set(provider)

  /**
   * Installs a provider whose sampler drops every span: the blocks passed to
   * `trace.span` still run, with [[Span.NoOp]], and nothing is recorded until
   * the next `trace.install`. The previous provider is not shut down, and
   * tracers taken from it keep recording.
   *
   * A known bug: the buffer behind `trace.collectedSpans` receives no new spans
   * afterwards, except from tracers taken with `trace.get` before; no call
   * brings the default in-memory buffer back.
   */
  def removeAll(): Unit = ref.set(TracerProvider.builder.setSampler(AlwaysOffSampler).build())

  /**
   * Returns the finished spans in the in-memory buffer of the default provider,
   * oldest first.
   *
   * The buffer keeps the last 1024 spans and overwrites older ones. A span is
   * added when it ends, so it comes after its children. Spans recorded through
   * another provider are not included.
   *
   * A known bug: after `trace.install` or `trace.removeAll()`, the buffer
   * receives no new spans for the rest of the program, except from tracers
   * taken with `trace.get` before.
   */
  def collectedSpans: List[SpanData] = spanStore.collectedSpans

  /**
   * Empties the in-memory buffer behind `trace.collectedSpans`; call it at the
   * start of a test to see only that test's spans.
   */
  def clearSpans(): Unit = spanStore.clear()
}
