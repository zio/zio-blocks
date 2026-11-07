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

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A unit of work in a trace: a name, a [[SpanKind]], attributes, timed events
 * and a [[SpanStatus]], with a [[SpanContext]] that identifies it. `trace.span`
 * and `Tracer#span` hand a live span to a block and end it when the block
 * finishes; the provider's processors then receive its [[SpanData]].
 *
 * Once a span ends, `setAttribute`, `addEvent` and `setStatus` do nothing. When
 * the sampler drops a span, the block receives [[Span.NoOp]], which accepts the
 * same calls and records nothing, so the code needs no checks.
 *
 * {{{
 * trace.clearSpans()
 *
 * trace.span("charge-card", SpanKind.Client) { span =>
 *   span.setAttribute("payment.provider", "stripe")
 *   span.setAttribute("payment.amount_cents", 4999L)
 *   span.addEvent("authorized")
 *   span.setStatus(SpanStatus.Ok)
 * }
 *
 * val charged = trace.collectedSpans.head
 * charged.attributes.get(AttributeKey.long("payment.amount_cents")) // Some(4999)
 * charged.events.map(_.name) // List(authorized)
 * charged.status.toString // Ok
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/telemetry/tracing/span/ Span]]
 */
trait Span {

  /**
   * Returns the ids of this span and its trace, which a propagator sends to
   * other services.
   */
  def spanContext: SpanContext

  /** Returns the name the span was opened with. */
  def name: String

  /** Returns the kind the span was opened with. */
  def kind: SpanKind

  /**
   * Sets the attribute `key` to `value`, replacing an earlier value with the
   * same name. Takes every key type, including the sequence keys such as
   * `AttributeKey.stringSeq`. Does nothing after the span ends.
   */
  def setAttribute[A](key: AttributeKey[A], value: A): Unit

  /**
   * Sets the string attribute `key`, replacing an earlier value with the same
   * name. Does nothing after the span ends.
   */
  def setAttribute(key: String, value: String): Unit

  /**
   * Sets the `Long` attribute `key`, replacing an earlier value with the same
   * name. Does nothing after the span ends.
   */
  def setAttribute(key: String, value: Long): Unit

  /**
   * Sets the `Double` attribute `key`, replacing an earlier value with the same
   * name. Does nothing after the span ends.
   */
  def setAttribute(key: String, value: Double): Unit

  /**
   * Sets the `Boolean` attribute `key`, replacing an earlier value with the
   * same name. Does nothing after the span ends.
   */
  def setAttribute(key: String, value: Boolean): Unit

  /**
   * Records an event named `name`, with no attributes, at the current time.
   * Does nothing after the span ends.
   */
  def addEvent(name: String): Unit

  /**
   * Records an event named `name` with `attributes` at the current time. Does
   * nothing after the span ends.
   */
  def addEvent(name: String, attributes: Attributes): Unit

  /**
   * Records an event named `name` with `attributes` at `timestamp`, in epoch
   * nanoseconds. Does nothing after the span ends.
   */
  def addEvent(name: String, timestamp: Long, attributes: Attributes): Unit

  /**
   * Sets the outcome of the span, replacing an earlier status. Does nothing
   * after the span ends. Nothing sets a status for you: a span whose block
   * throws keeps `Unset` unless the block sets `SpanStatus.Error`.
   */
  def setStatus(status: SpanStatus): Unit

  /**
   * Ends the span at the current time; only the first call to either `end`
   * counts. `trace.span` and `Tracer#span` end the span when the block
   * finishes. Ending it earlier inside the block stops the recording, and the
   * processors still receive it when the block finishes.
   */
  def end(): Unit

  /**
   * Ends the span at `endTimeNanos`, in epoch nanoseconds; only the first call
   * to either `end` counts.
   */
  def end(endTimeNanos: Long): Unit

  /**
   * Returns `true` until the span ends, and always `false` for [[Span.NoOp]].
   * Check it to skip work that only builds attributes or events.
   */
  def isRecording: Boolean

  /**
   * Returns an immutable snapshot of the span as it is now; its `endTimeNanos`
   * is `0` until the span ends.
   */
  def toSpanData: SpanData
}

/** Holds [[Span.NoOp]]. */
object Span {

  /**
   * The span that the block receives when the sampler drops a span. Every
   * method does nothing and allocates nothing.
   *
   * Its own context is [[SpanContext.invalid]], even though
   * `Tracer#currentSpan` inside the block returns a valid context with the
   * sampled flag cleared, which keeps child spans in the trace.
   */
  object NoOp extends Span {

    /** Returns [[SpanContext.invalid]]. */
    val spanContext: SpanContext = SpanContext.invalid

    /** Returns the empty string. */
    val name: String = ""

    /** Returns `SpanKind.Internal`. */
    val kind: SpanKind = SpanKind.Internal

    /** Does nothing. */
    def setAttribute[A](key: AttributeKey[A], value: A): Unit = ()

    /** Does nothing. */
    def setAttribute(key: String, value: String): Unit = ()

    /** Does nothing. */
    def setAttribute(key: String, value: Long): Unit = ()

    /** Does nothing. */
    def setAttribute(key: String, value: Double): Unit = ()

    /** Does nothing. */
    def setAttribute(key: String, value: Boolean): Unit = ()

    /** Does nothing. */
    def addEvent(name: String): Unit = ()

    /** Does nothing. */
    def addEvent(name: String, attributes: Attributes): Unit = ()

    /** Does nothing. */
    def addEvent(name: String, timestamp: Long, attributes: Attributes): Unit = ()

    /** Does nothing. */
    def setStatus(status: SpanStatus): Unit = ()

    /** Does nothing. */
    def end(): Unit = ()

    /** Does nothing. */
    def end(endTimeNanos: Long): Unit = ()

    /** Returns `false`. */
    val isRecording: Boolean = false

    private val emptySpanData: SpanData = SpanData(
      name = "",
      kind = SpanKind.Internal,
      spanContext = SpanContext.invalid,
      parentSpanContext = SpanContext.invalid,
      startTimeNanos = 0L,
      endTimeNanos = 0L,
      attributes = Attributes.empty,
      events = List.empty,
      links = List.empty,
      status = SpanStatus.Unset,
      resource = Resource.empty,
      instrumentationScope = InstrumentationScope("noop")
    )

    /**
     * Returns the same empty snapshot on every call: invalid contexts, times of
     * `0`, no attributes, `Resource.empty` and the scope `noop`.
     */
    def toSpanData: SpanData = emptySpanData
  }
}

/**
 * The span that `SpanBuilder#startSpan` and `Tracer#span` create: it keeps its
 * attributes and events in copy-on-write lists and ends once.
 */
private[telemetry] final class RecordingSpan(
  val spanContext: SpanContext,
  val name: String,
  val kind: SpanKind,
  val parentSpanContext: SpanContext,
  val startTimeNanos: Long,
  initialAttributes: Attributes,
  initialLinks: List[SpanLink],
  val resource: Resource,
  val instrumentationScope: InstrumentationScope
) extends Span {

  private val ended: AtomicBoolean = new AtomicBoolean(false)

  @volatile private var endTime: Long = 0L

  @volatile private var currentStatus: SpanStatus = SpanStatus.Unset

  private val attributeEntries: CopyOnWriteArrayList[(String, AttributeValue)] = {
    val list = new CopyOnWriteArrayList[(String, AttributeValue)]()
    initialAttributes.foreach { (k, v) =>
      list.add((k, v))
    }
    list
  }

  private val eventEntries: CopyOnWriteArrayList[SpanEvent] =
    new CopyOnWriteArrayList[SpanEvent]()

  def setAttribute[A](key: AttributeKey[A], value: A): Unit =
    if (!ended.get()) {
      val av = toAttributeValue(key, value)
      removeAndAdd(key.name, av)
    }

  def setAttribute(key: String, value: String): Unit =
    if (!ended.get()) removeAndAdd(key, AttributeValue.StringValue(value))

  def setAttribute(key: String, value: Long): Unit =
    if (!ended.get()) removeAndAdd(key, AttributeValue.LongValue(value))

  def setAttribute(key: String, value: Double): Unit =
    if (!ended.get()) removeAndAdd(key, AttributeValue.DoubleValue(value))

  def setAttribute(key: String, value: Boolean): Unit =
    if (!ended.get()) removeAndAdd(key, AttributeValue.BooleanValue(value))

  def addEvent(name: String): Unit =
    if (!ended.get()) eventEntries.add(SpanEvent(name, EpochClock.epochNanos(), Attributes.empty))

  def addEvent(name: String, attributes: Attributes): Unit =
    if (!ended.get()) eventEntries.add(SpanEvent(name, EpochClock.epochNanos(), attributes))

  def addEvent(name: String, timestamp: Long, attributes: Attributes): Unit =
    if (!ended.get()) eventEntries.add(SpanEvent(name, timestamp, attributes))

  def setStatus(status: SpanStatus): Unit =
    if (!ended.get()) currentStatus = status

  def end(): Unit =
    if (ended.compareAndSet(false, true)) {
      endTime = EpochClock.epochNanos()
    }

  def end(endTimeNanos: Long): Unit =
    if (ended.compareAndSet(false, true)) {
      endTime = endTimeNanos
    }

  def isRecording: Boolean = !ended.get()

  def toSpanData: SpanData = {
    val builder = Attributes.builder
    val iter    = attributeEntries.iterator()
    while (iter.hasNext) {
      val (k, v) = iter.next()
      v match {
        case AttributeValue.StringValue(s)      => builder.put(k, s)
        case AttributeValue.LongValue(l)        => builder.put(k, l)
        case AttributeValue.DoubleValue(d)      => builder.put(k, d)
        case AttributeValue.BooleanValue(b)     => builder.put(k, b)
        case AttributeValue.StringSeqValue(xs)  => builder.put(AttributeKey.stringSeq(k), xs)
        case AttributeValue.LongSeqValue(xs)    => builder.put(AttributeKey.longSeq(k), xs)
        case AttributeValue.DoubleSeqValue(xs)  => builder.put(AttributeKey.doubleSeq(k), xs)
        case AttributeValue.BooleanSeqValue(xs) => builder.put(AttributeKey.booleanSeq(k), xs)
      }
    }

    val events = {
      val buf     = List.newBuilder[SpanEvent]
      val evtIter = eventEntries.iterator()
      while (evtIter.hasNext) buf += evtIter.next()
      buf.result()
    }

    SpanData(
      name = name,
      kind = kind,
      spanContext = spanContext,
      parentSpanContext = parentSpanContext,
      startTimeNanos = startTimeNanos,
      endTimeNanos = endTime,
      attributes = builder.build,
      events = events,
      links = initialLinks,
      status = currentStatus,
      resource = resource,
      instrumentationScope = instrumentationScope
    )
  }

  private def removeAndAdd(key: String, value: AttributeValue): Unit = {
    val iter = attributeEntries.iterator()
    while (iter.hasNext) {
      val entry = iter.next()
      if (entry._1 == key) {
        attributeEntries.remove(entry)
      }
    }
    attributeEntries.add((key, value))
  }

  private def toAttributeValue[A](key: AttributeKey[A], value: A): AttributeValue =
    key.`type` match {
      case AttributeType.StringType     => AttributeValue.StringValue(value.asInstanceOf[String])
      case AttributeType.BooleanType    => AttributeValue.BooleanValue(value.asInstanceOf[Boolean])
      case AttributeType.LongType       => AttributeValue.LongValue(value.asInstanceOf[Long])
      case AttributeType.DoubleType     => AttributeValue.DoubleValue(value.asInstanceOf[Double])
      case AttributeType.StringSeqType  => AttributeValue.StringSeqValue(value.asInstanceOf[Seq[String]])
      case AttributeType.LongSeqType    => AttributeValue.LongSeqValue(value.asInstanceOf[Seq[Long]])
      case AttributeType.DoubleSeqType  => AttributeValue.DoubleSeqValue(value.asInstanceOf[Seq[Double]])
      case AttributeType.BooleanSeqType => AttributeValue.BooleanSeqValue(value.asInstanceOf[Seq[Boolean]])
    }
}
