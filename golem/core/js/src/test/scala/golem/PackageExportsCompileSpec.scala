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
