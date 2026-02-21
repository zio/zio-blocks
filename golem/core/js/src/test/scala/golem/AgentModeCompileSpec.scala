package golem

import golem.runtime.autowire.AgentMode
import org.scalatest.funsuite.AnyFunSuite

final class AgentModeCompileSpec extends AnyFunSuite {

  test("AgentMode.Durable has value 'durable'") {
    assert(AgentMode.Durable.value == "durable")
  }

  test("AgentMode.Ephemeral has value 'ephemeral'") {
    assert(AgentMode.Ephemeral.value == "ephemeral")
  }

  test("AgentMode.fromString parses 'durable'") {
    assert(AgentMode.fromString("durable").contains(AgentMode.Durable))
  }

  test("AgentMode.fromString parses 'ephemeral'") {
    assert(AgentMode.fromString("ephemeral").contains(AgentMode.Ephemeral))
  }

  test("AgentMode.fromString is case-insensitive") {
    assert(AgentMode.fromString("DURABLE").contains(AgentMode.Durable))
    assert(AgentMode.fromString("Ephemeral").contains(AgentMode.Ephemeral))
    assert(AgentMode.fromString("EPHEMERAL").contains(AgentMode.Ephemeral))
  }

  test("AgentMode.fromString returns None for unknown values") {
    assert(AgentMode.fromString("unknown").isEmpty)
    assert(AgentMode.fromString("").isEmpty)
  }

  test("AgentMode.fromString returns None for null") {
    assert(AgentMode.fromString(null).isEmpty)
  }

  test("AgentMode sealed trait is exhaustive") {
    def describe(mode: AgentMode): String = mode match {
      case AgentMode.Durable   => "durable"
      case AgentMode.Ephemeral => "ephemeral"
    }
    assert(describe(AgentMode.Durable) == "durable")
    assert(describe(AgentMode.Ephemeral) == "ephemeral")
  }

  test("AgentMode variants are distinct") {
    assert(AgentMode.Durable != AgentMode.Ephemeral)
  }

  test("AgentMode accessible via golem package alias") {
    val _: golem.AgentMode = golem.AgentMode.Durable
    val _: golem.AgentMode = golem.AgentMode.Ephemeral
    assert(true)
  }
}
