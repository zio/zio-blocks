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

package golem

import org.scalatest.funsuite.AnyFunSuite

final class PackageExportsCompileSpec extends AnyFunSuite {

  test("DurabilityMode.wireValue returns correct strings") {
    assert(golem.DurabilityMode.Durable.wireValue() == "durable")
    assert(golem.DurabilityMode.Ephemeral.wireValue() == "ephemeral")
  }

  test("DurabilityMode.fromWireValue parses durable") {
    assert(golem.DurabilityMode.fromWireValue("durable").contains(golem.DurabilityMode.Durable))
  }

  test("DurabilityMode.fromWireValue parses ephemeral") {
    assert(golem.DurabilityMode.fromWireValue("ephemeral").contains(golem.DurabilityMode.Ephemeral))
  }

  test("DurabilityMode.fromWireValue is case-insensitive") {
    assert(golem.DurabilityMode.fromWireValue("DURABLE").contains(golem.DurabilityMode.Durable))
    assert(golem.DurabilityMode.fromWireValue("Ephemeral").contains(golem.DurabilityMode.Ephemeral))
  }

  test("DurabilityMode.fromWireValue returns None for unknown") {
    assert(golem.DurabilityMode.fromWireValue("unknown").isEmpty)
    assert(golem.DurabilityMode.fromWireValue("").isEmpty)
    assert(golem.DurabilityMode.fromWireValue(null).isEmpty)
  }

  test("DurabilityMode.toString matches wireValue") {
    assert(golem.DurabilityMode.Durable.toString == "durable")
    assert(golem.DurabilityMode.Ephemeral.toString == "ephemeral")
  }
}
