package golem.host

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

/**
 * Scala.js facade for `golem:api/context@1.3.0`.
 *
 * WIT interface:
 * {{{
 *   resource span { started-at, set-attribute, set-attributes, finish }
 *   resource invocation-context { trace-id, span-id, parent, get-attribute, get-attributes, ... }
 *   variant attribute-value { string(string) }
 *   record attribute { key: string, value: attribute-value }
 *   start-span: func(name: string) -> span
 *   current-context: func() -> invocation-context
 *   allow-forwarding-trace-context-headers: func(allow: bool) -> bool
 * }}}
 */
object ContextApi {

  // --- WIT: attribute-value variant ---

  sealed trait AttributeValue extends Product with Serializable
  object AttributeValue {
    final case class StringValue(value: String) extends AttributeValue

    def fromDynamic(raw: js.Dynamic): AttributeValue = {
      val v = raw.selectDynamic("val")
      StringValue(v.asInstanceOf[String])
    }

    def toDynamic(av: AttributeValue): js.Dynamic = av match {
      case StringValue(v) => js.Dynamic.literal(tag = "string", `val` = v)
    }
  }

  // --- WIT: attribute record ---

  final case class Attribute(key: String, value: AttributeValue)

  // --- WIT: attribute-chain record ---

  final case class AttributeChain(key: String, values: List[AttributeValue])

  // --- WIT: datetime record (shared timestamp type) ---

  final case class DateTime(seconds: BigInt, nanoseconds: Long)

  // --- WIT: span resource ---

  final class Span private[golem] (private[golem] val underlying: js.Dynamic) {

    def startedAt(): DateTime = {
      val raw   = underlying.startedAt().asInstanceOf[js.Dynamic]
      val secs  = BigInt(raw.seconds.toString)
      val nanos = raw.nanoseconds.asInstanceOf[Int].toLong
      DateTime(secs, nanos)
    }

    def setAttribute(name: String, value: AttributeValue): Unit =
      underlying.setAttribute(name, AttributeValue.toDynamic(value))

    def setAttributes(attributes: List[Attribute]): Unit = {
      val arr = js.Array[js.Dynamic]()
      attributes.foreach { a =>
        arr.push(js.Dynamic.literal(key = a.key, value = AttributeValue.toDynamic(a.value)))
      }
      underlying.setAttributes(arr)
    }

    def finish(): Unit =
      underlying.finish()
  }

  // --- WIT: invocation-context resource ---

  final class InvocationContext private[golem] (private[golem] val underlying: js.Dynamic) {

    def traceId(): String =
      underlying.traceId().asInstanceOf[String]

    def spanId(): String =
      underlying.spanId().asInstanceOf[String]

    def parent(): Option[InvocationContext] = {
      val raw = underlying.parent()
      if (js.isUndefined(raw) || raw == null) None
      else {
        val obj = raw.asInstanceOf[js.Object]
        if (js.Object.keys(obj).length == 0) None
        else Some(new InvocationContext(raw.asInstanceOf[js.Dynamic]))
      }
    }

    def getAttribute(key: String, inherited: Boolean): Option[AttributeValue] = {
      val raw = underlying.getAttribute(key, inherited)
      if (js.isUndefined(raw) || raw == null) None
      else Some(AttributeValue.fromDynamic(raw.asInstanceOf[js.Dynamic]))
    }

    def getAttributes(inherited: Boolean): List[Attribute] = {
      val arr = underlying.getAttributes(inherited).asInstanceOf[js.Array[js.Dynamic]]
      arr.toList.map { a =>
        Attribute(a.key.asInstanceOf[String], AttributeValue.fromDynamic(a.value.asInstanceOf[js.Dynamic]))
      }
    }

    def getAttributeChain(key: String): List[AttributeValue] = {
      val arr = underlying.getAttributeChain(key).asInstanceOf[js.Array[js.Dynamic]]
      arr.toList.map(AttributeValue.fromDynamic)
    }

    def getAttributeChains(): List[AttributeChain] = {
      val arr = underlying.getAttributeChains().asInstanceOf[js.Array[js.Dynamic]]
      arr.toList.map { c =>
        val key    = c.key.asInstanceOf[String]
        val values = c.values.asInstanceOf[js.Array[js.Dynamic]].toList.map(AttributeValue.fromDynamic)
        AttributeChain(key, values)
      }
    }

    def traceContextHeaders(): List[(String, String)] = {
      val arr = underlying.traceContextHeaders().asInstanceOf[js.Array[js.Tuple2[String, String]]]
      arr.toList.map(kv => (kv._1, kv._2))
    }
  }

  // --- Native bindings ---

  @js.native
  @JSImport("golem:api/context@1.3.0", JSImport.Namespace)
  private object ContextModule extends js.Object {
    def startSpan(name: String): js.Any                             = js.native
    def currentContext(): js.Any                                    = js.native
    def allowForwardingTraceContextHeaders(allow: Boolean): Boolean = js.native
  }

  // --- Typed public API ---

  def startSpan(name: String): Span =
    new Span(ContextModule.startSpan(name).asInstanceOf[js.Dynamic])

  def currentContext(): InvocationContext =
    new InvocationContext(ContextModule.currentContext().asInstanceOf[js.Dynamic])

  def allowForwardingTraceContextHeaders(allow: Boolean): Boolean =
    ContextModule.allowForwardingTraceContextHeaders(allow)

  def raw: Any = ContextModule
}
