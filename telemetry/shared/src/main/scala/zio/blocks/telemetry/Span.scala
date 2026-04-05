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
 * A mutable, thread-safe span representing a unit of work in a trace.
 *
 * Spans are created via `SpanBuilder` and track attributes, events, and status
 * throughout their lifetime. Once `end()` is called, the span stops recording
 * and all mutating methods become no-ops.
 */
trait Span {
  def spanContext: SpanContext
  def name: String
  def kind: SpanKind
  def setAttribute[A](key: AttributeKey[A], value: A): Unit
  def setAttribute(key: String, value: String): Unit
  def setAttribute(key: String, value: Long): Unit
  def setAttribute(key: String, value: Double): Unit
  def setAttribute(key: String, value: Boolean): Unit
  def addEvent(name: String): Unit
  def addEvent(name: String, attributes: Attributes): Unit
  def addEvent(name: String, timestamp: Long, attributes: Attributes): Unit
  def setStatus(status: SpanStatus): Unit
  def end(): Unit
  def end(endTimeNanos: Long): Unit
  def isRecording: Boolean
  def toSpanData: SpanData
}

object Span {

  /**
   * A no-op span that performs zero allocations. All methods are no-ops.
   */
  object NoOp extends Span {
    val spanContext: SpanContext = SpanContext.invalid
    val name: String             = ""
    val kind: SpanKind           = SpanKind.Internal

    def setAttribute[A](key: AttributeKey[A], value: A): Unit                 = ()
    def setAttribute(key: String, value: String): Unit                        = ()
    def setAttribute(key: String, value: Long): Unit                          = ()
    def setAttribute(key: String, value: Double): Unit                        = ()
    def setAttribute(key: String, value: Boolean): Unit                       = ()
    def addEvent(name: String): Unit                                          = ()
    def addEvent(name: String, attributes: Attributes): Unit                  = ()
    def addEvent(name: String, timestamp: Long, attributes: Attributes): Unit = ()
    def setStatus(status: SpanStatus): Unit                                   = ()
    def end(): Unit                                                           = ()
    def end(endTimeNanos: Long): Unit                                         = ()
    val isRecording: Boolean                                                  = false

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

    def toSpanData: SpanData = emptySpanData
  }
}

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
    if (!ended.get()) eventEntries.add(SpanEvent(name, System.nanoTime(), Attributes.empty))

  def addEvent(name: String, attributes: Attributes): Unit =
    if (!ended.get()) eventEntries.add(SpanEvent(name, System.nanoTime(), attributes))

  def addEvent(name: String, timestamp: Long, attributes: Attributes): Unit =
    if (!ended.get()) eventEntries.add(SpanEvent(name, timestamp, attributes))

  def setStatus(status: SpanStatus): Unit =
    if (!ended.get()) currentStatus = status

  def end(): Unit =
    if (ended.compareAndSet(false, true)) {
      endTime = System.nanoTime()
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
        case AttributeValue.StringValue(s)  => builder.put(k, s)
        case AttributeValue.LongValue(l)    => builder.put(k, l)
        case AttributeValue.DoubleValue(d)  => builder.put(k, d)
        case AttributeValue.BooleanValue(b) => builder.put(k, b)
        case _                              => builder.put(AttributeKey.string(k), v.toString)
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
