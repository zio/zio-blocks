package golem.host

import org.scalatest.funsuite.AnyFunSuite

class ContextApiCompileSpec extends AnyFunSuite {
  import ContextApi._

  private val stringAttr: AttributeValue     = AttributeValue.StringValue("hello")
  private val attribute: Attribute           = Attribute("key", stringAttr)
  private val attributeChain: AttributeChain =
    AttributeChain("key", List(stringAttr, AttributeValue.StringValue("world")))
  private val dateTime: DateTime = DateTime(BigInt(1700000000L), 500000000L)

  private def describeAttributeValue(av: AttributeValue): String = av match {
    case AttributeValue.StringValue(v) => s"string($v)"
  }

  test("AttributeValue exhaustive match") {
    assert(describeAttributeValue(stringAttr) == "string(hello)")
  }

  test("Attribute construction and field access") {
    assert(attribute.key == "key")
    assert(attribute.value == stringAttr)
  }

  test("AttributeChain construction and field access") {
    assert(attributeChain.key == "key")
    assert(attributeChain.values.size == 2)
  }

  test("DateTime construction and field access") {
    assert(dateTime.seconds == BigInt(1700000000L))
    assert(dateTime.nanoseconds == 500000000L)
  }

  test("Span method types compile") {
    type StartedAtType     = () => DateTime
    type SetAttributeType  = (String, AttributeValue) => Unit
    type SetAttributesType = List[Attribute] => Unit
    type FinishType        = () => Unit
    assert(true)
  }

  test("InvocationContext method types compile") {
    type TraceIdType             = () => String
    type SpanIdType              = () => String
    type ParentType              = () => Option[InvocationContext]
    type GetAttributeType        = (String, Boolean) => Option[AttributeValue]
    type GetAttributesType       = Boolean => List[Attribute]
    type GetAttributeChainType   = String => List[AttributeValue]
    type GetAttributeChainsType  = () => List[AttributeChain]
    type TraceContextHeadersType = () => List[(String, String)]
    assert(true)
  }

  test("top-level function return types compile") {
    val _: Span              = null.asInstanceOf[Span]
    val _: InvocationContext = null.asInstanceOf[InvocationContext]
    val _: Boolean           = true
    assert(true)
  }
}
