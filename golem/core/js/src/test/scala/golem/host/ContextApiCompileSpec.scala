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
}
