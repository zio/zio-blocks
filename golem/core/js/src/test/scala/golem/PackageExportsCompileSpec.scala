package golem

import org.scalatest.funsuite.AnyFunSuite

final class PackageExportsCompileSpec extends AnyFunSuite {

  test("agentDefinition annotation type is accessible from golem package") {
    val _: golem.agentDefinition = new golem.runtime.annotations.agentDefinition()
    assert(true)
  }

  test("agentImplementation annotation type is accessible from golem package") {
    val _: golem.agentImplementation = new golem.runtime.annotations.agentImplementation()
    assert(true)
  }

  test("description annotation type is accessible from golem package") {
    val _: golem.description = new golem.runtime.annotations.description("test")
    assert(true)
  }

  test("prompt annotation type is accessible from golem package") {
    val _: golem.prompt = new golem.runtime.annotations.prompt("test")
    assert(true)
  }

  test("DurabilityMode is accessible from golem package") {
    val _: golem.DurabilityMode = golem.DurabilityMode.Durable
    val _: golem.DurabilityMode = golem.DurabilityMode.Ephemeral
    assert(true)
  }

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

  test("GolemSchema type alias resolves") {
    val _: golem.GolemSchema[String] = null.asInstanceOf[golem.data.GolemSchema[String]]
    assert(true)
  }

  test("StructuredSchema type alias resolves") {
    val _: golem.StructuredSchema = null.asInstanceOf[golem.data.StructuredSchema]
    assert(true)
  }

  test("StructuredValue type alias resolves") {
    val _: golem.StructuredValue = null.asInstanceOf[golem.data.StructuredValue]
    assert(true)
  }

  test("AgentImplementation object is accessible from golem package") {
    val _: golem.runtime.autowire.AgentImplementation.type = golem.AgentImplementation
    assert(true)
  }

  test("AgentDefinition type alias resolves") {
    val _: golem.AgentDefinition[Any] = null.asInstanceOf[golem.runtime.autowire.AgentDefinition[Any]]
    assert(true)
  }

  test("AgentMode type and companion are accessible") {
    val _: golem.AgentMode = golem.AgentMode.Durable
    val _: golem.AgentMode = golem.AgentMode.Ephemeral
    assert(true)
  }

  test("BaseAgent is accessible from golem package") {
    val _: golem.BaseAgent[Unit] = null.asInstanceOf[golem.BaseAgent[Unit]]
    assert(true)
  }

  test("Datetime is accessible from golem package") {
    val _: golem.Datetime = golem.Datetime.now
    assert(true)
  }

  test("Uuid is accessible from golem package") {
    val _: golem.Uuid = golem.Uuid(BigInt(0), BigInt(0))
    assert(true)
  }

  test("FutureInterop is accessible from golem package") {
    val _: golem.FutureInterop.type = golem.FutureInterop
    assert(true)
  }

  test("HostApi is accessible from golem package") {
    val _: golem.HostApi.type = golem.HostApi
    assert(true)
  }

  test("Guards is accessible from golem package") {
    val _: golem.Guards.type = golem.Guards
    assert(true)
  }

  test("Transactions is accessible from golem package") {
    val _: golem.Transactions.type = golem.Transactions
    assert(true)
  }
}
