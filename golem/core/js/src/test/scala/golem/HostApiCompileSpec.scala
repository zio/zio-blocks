package golem

import org.scalatest.funsuite.AnyFunSuite

import scala.scalajs.js

final class HostApiCompileSpec extends AnyFunSuite {

  // ---------------------------------------------------------------------------
  // RetryPolicy
  // ---------------------------------------------------------------------------

  test("RetryPolicy construction with all fields") {
    val rp = HostApi.RetryPolicy(
      maxAttempts = 5,
      minDelayNanos = BigInt(1000000),
      maxDelayNanos = BigInt(60000000000L),
      multiplier = 2.0,
      maxJitterFactor = Some(0.1)
    )
    assert(rp.maxAttempts == 5)
    assert(rp.minDelayNanos == BigInt(1000000))
    assert(rp.maxDelayNanos == BigInt(60000000000L))
    assert(rp.multiplier == 2.0)
    assert(rp.maxJitterFactor.contains(0.1))
  }

  test("RetryPolicy with None maxJitterFactor") {
    val rp = HostApi.RetryPolicy(3, BigInt(0), BigInt(0), 1.0, None)
    assert(rp.maxJitterFactor.isEmpty)
  }

  // ---------------------------------------------------------------------------
  // PersistenceLevel
  // ---------------------------------------------------------------------------

  test("all PersistenceLevel variants construct") {
    val levels: List[HostApi.PersistenceLevel] = List(
      HostApi.PersistenceLevel.PersistNothing,
      HostApi.PersistenceLevel.PersistRemoteSideEffects,
      HostApi.PersistenceLevel.Smart,
      HostApi.PersistenceLevel.Unknown("future-level")
    )
    assert(levels.size == 4)
  }

  test("PersistenceLevel.fromTag roundtrips all known tags") {
    assert(HostApi.PersistenceLevel.fromTag("persist-nothing") == HostApi.PersistenceLevel.PersistNothing)
    assert(
      HostApi.PersistenceLevel.fromTag("persist-remote-side-effects") ==
        HostApi.PersistenceLevel.PersistRemoteSideEffects
    )
    assert(HostApi.PersistenceLevel.fromTag("smart") == HostApi.PersistenceLevel.Smart)
  }

  test("PersistenceLevel.fromTag returns Unknown for unrecognized tags") {
    val result = HostApi.PersistenceLevel.fromTag("new-level")
    assert(result == HostApi.PersistenceLevel.Unknown("new-level"))
  }

  test("PersistenceLevel exhaustive pattern match compiles") {
    def describe(level: HostApi.PersistenceLevel): String = level match {
      case HostApi.PersistenceLevel.PersistNothing           => "nothing"
      case HostApi.PersistenceLevel.PersistRemoteSideEffects => "remote"
      case HostApi.PersistenceLevel.Smart                    => "smart"
      case HostApi.PersistenceLevel.Unknown(tag)             => s"unknown($tag)"
    }
    assert(describe(HostApi.PersistenceLevel.Smart) == "smart")
  }

  // ---------------------------------------------------------------------------
  // ForkResult / ForkDetails
  // ---------------------------------------------------------------------------

  test("ForkDetails construction") {
    val details = HostApi.ForkDetails(
      forkedPhantomId = Uuid(BigInt(1), BigInt(2)),
      agentId = null.asInstanceOf[HostApi.AgentIdLiteral],
      oplogIndex = BigInt(100),
      componentRevision = BigInt(5)
    )
    assert(details.forkedPhantomId == Uuid(BigInt(1), BigInt(2)))
    assert(details.oplogIndex == BigInt(100))
    assert(details.componentRevision == BigInt(5))
  }

  test("ForkResult.Original and Forked variants compile") {
    val details                      = HostApi.ForkDetails(Uuid(BigInt(0), BigInt(0)), null, BigInt(0), BigInt(0))
    val original: HostApi.ForkResult = HostApi.ForkResult.Original(details)
    val forked: HostApi.ForkResult   = HostApi.ForkResult.Forked(details)
    assert(original.details eq details)
    assert(forked.details eq details)
  }

  test("ForkResult pattern match compiles") {
    val result: HostApi.ForkResult =
      HostApi.ForkResult.Original(HostApi.ForkDetails(Uuid(BigInt(0), BigInt(0)), null, BigInt(0), BigInt(0)))
    val label = result match {
      case HostApi.ForkResult.Original(_) => "original"
      case HostApi.ForkResult.Forked(_)   => "forked"
    }
    assert(label == "original")
  }

  // ---------------------------------------------------------------------------
  // AgentMetadata
  // ---------------------------------------------------------------------------

  test("AgentMetadata construction with all fields") {
    val meta = HostApi.AgentMetadata(
      agentId = null.asInstanceOf[HostApi.AgentIdLiteral],
      args = List("arg1", "arg2"),
      env = Map("KEY" -> "VALUE"),
      configVars = Map("cfg" -> "val"),
      status = null.asInstanceOf[HostApi.AgentStatus],
      componentRevision = BigInt(3),
      retryCount = BigInt(0),
      agentType = "my-agent",
      agentName = "instance-1",
      componentId = null.asInstanceOf[HostApi.ComponentIdLiteral]
    )
    assert(meta.args == List("arg1", "arg2"))
    assert(meta.env == Map("KEY" -> "VALUE"))
    assert(meta.agentType == "my-agent")
    assert(meta.agentName == "instance-1")
    assert(meta.componentRevision == BigInt(3))
  }

  // ---------------------------------------------------------------------------
  // AgentStatus
  // ---------------------------------------------------------------------------

  test("all AgentStatus variants accessible") {
    val statuses = List(
      HostApi.AgentStatus.Running,
      HostApi.AgentStatus.Idle,
      HostApi.AgentStatus.Suspended,
      HostApi.AgentStatus.Interrupted,
      HostApi.AgentStatus.Retrying,
      HostApi.AgentStatus.Failed,
      HostApi.AgentStatus.Exited
    )
    assert(statuses.size == 7)
  }

  // ---------------------------------------------------------------------------
  // UpdateMode
  // ---------------------------------------------------------------------------

  test("UpdateMode variants accessible") {
    val modes = List(HostApi.UpdateMode.Automatic, HostApi.UpdateMode.SnapshotBased)
    assert(modes.size == 2)
  }

  // ---------------------------------------------------------------------------
  // Filter types
  // ---------------------------------------------------------------------------

  test("FilterComparator variants accessible") {
    val comparators = List(
      HostApi.FilterComparator.Equal,
      HostApi.FilterComparator.NotEqual,
      HostApi.FilterComparator.GreaterEqual,
      HostApi.FilterComparator.Greater,
      HostApi.FilterComparator.LessEqual,
      HostApi.FilterComparator.Less
    )
    assert(comparators.size == 6)
  }

  test("StringFilterComparator variants accessible") {
    val comparators = List(
      HostApi.StringFilterComparator.Equal,
      HostApi.StringFilterComparator.NotEqual,
      HostApi.StringFilterComparator.Like,
      HostApi.StringFilterComparator.NotLike,
      HostApi.StringFilterComparator.StartsWith
    )
    assert(comparators.size == 5)
  }

  // ---------------------------------------------------------------------------
  // RevertAgentTarget
  // ---------------------------------------------------------------------------

  test("RevertAgentTarget factory methods") {
    val byIndex: HostApi.RevertAgentTarget = HostApi.RevertAgentTarget.RevertToOplogIndex(BigInt(42))
    val byCount: HostApi.RevertAgentTarget = HostApi.RevertAgentTarget.RevertLastInvocations(BigInt(3))
    assert(byIndex != null)
    assert(byCount != null)
  }

  test("Uuid construction") {
    val u = Uuid(BigInt(123456789L), BigInt(987654321L))
    assert(u.highBits == BigInt(123456789L))
    assert(u.lowBits == BigInt(987654321L))
  }

  // ---------------------------------------------------------------------------
  // RegisteredAgentType (Scala case class, no js.Object)
  // ---------------------------------------------------------------------------

  test("RegisteredAgentType construction") {
    val rat = HostApi.RegisteredAgentType(
      typeName = "my-agent",
      implementedBy = null.asInstanceOf[HostApi.ComponentIdLiteral]
    )
    assert(rat.typeName == "my-agent")
  }

  // ---------------------------------------------------------------------------
  // AgentIdParts (Scala case class, no js.Dynamic)
  // ---------------------------------------------------------------------------

  test("AgentIdParts construction") {
    val parts = HostApi.AgentIdParts(
      agentTypeName = "counter",
      phantom = Some(Uuid(BigInt(1), BigInt(2)))
    )
    assert(parts.agentTypeName == "counter")
    assert(parts.phantom.isDefined)
  }

  test("AgentIdParts with no phantom") {
    val parts = HostApi.AgentIdParts("counter", None)
    assert(parts.phantom.isEmpty)
  }

  // ---------------------------------------------------------------------------
  // Filter construction APIs
  // ---------------------------------------------------------------------------

  test("AgentNameFilter construction with StringFilterComparator") {
    val f: HostApi.AgentNameFilter = HostApi.AgentNameFilter(HostApi.StringFilterComparator.Equal, "my-agent")
    assert(f != null)
  }

  test("AgentStatusFilter construction with FilterComparator") {
    val f: HostApi.AgentStatusFilter =
      HostApi.AgentStatusFilter(HostApi.FilterComparator.Equal, HostApi.AgentStatus.Running)
    assert(f != null)
  }

  test("AgentVersionFilter construction") {
    val f: HostApi.AgentVersionFilter = HostApi.AgentVersionFilter(HostApi.FilterComparator.GreaterEqual, BigInt(2))
    assert(f != null)
  }

  test("AgentCreatedAtFilter construction") {
    val f: HostApi.AgentCreatedAtFilter =
      HostApi.AgentCreatedAtFilter(HostApi.FilterComparator.Less, BigInt(1700000000))
    assert(f != null)
  }

  test("AgentEnvFilter construction") {
    val f: HostApi.AgentEnvFilter = HostApi.AgentEnvFilter("ENV_VAR", HostApi.StringFilterComparator.Like, "prod%")
    assert(f != null)
  }

  test("AgentConfigVarsFilter construction") {
    val f: HostApi.AgentConfigVarsFilter =
      HostApi.AgentConfigVarsFilter("config-key", HostApi.StringFilterComparator.StartsWith, "prefix")
    assert(f != null)
  }

  test("AgentPropertyFilter.name wraps AgentNameFilter") {
    val nameFilter                      = HostApi.AgentNameFilter(HostApi.StringFilterComparator.Equal, "test")
    val pf: HostApi.AgentPropertyFilter = HostApi.AgentPropertyFilter.name(nameFilter)
    assert(pf != null)
  }

  test("AgentPropertyFilter.status wraps AgentStatusFilter") {
    val statusFilter                    = HostApi.AgentStatusFilter(HostApi.FilterComparator.Equal, HostApi.AgentStatus.Idle)
    val pf: HostApi.AgentPropertyFilter = HostApi.AgentPropertyFilter.status(statusFilter)
    assert(pf != null)
  }

  test("AgentPropertyFilter.version wraps AgentVersionFilter") {
    val versionFilter                   = HostApi.AgentVersionFilter(HostApi.FilterComparator.GreaterEqual, BigInt(1))
    val pf: HostApi.AgentPropertyFilter = HostApi.AgentPropertyFilter.version(versionFilter)
    assert(pf != null)
  }

  test("AgentPropertyFilter.createdAt wraps AgentCreatedAtFilter") {
    val createdAtFilter                 = HostApi.AgentCreatedAtFilter(HostApi.FilterComparator.Greater, BigInt(0))
    val pf: HostApi.AgentPropertyFilter = HostApi.AgentPropertyFilter.createdAt(createdAtFilter)
    assert(pf != null)
  }

  test("AgentPropertyFilter.env wraps AgentEnvFilter") {
    val envFilter                       = HostApi.AgentEnvFilter("KEY", HostApi.StringFilterComparator.NotEqual, "val")
    val pf: HostApi.AgentPropertyFilter = HostApi.AgentPropertyFilter.env(envFilter)
    assert(pf != null)
  }

  test("AgentPropertyFilter.wasiConfigVars wraps AgentConfigVarsFilter") {
    val configFilter                    = HostApi.AgentConfigVarsFilter("cfg", HostApi.StringFilterComparator.Equal, "v")
    val pf: HostApi.AgentPropertyFilter = HostApi.AgentPropertyFilter.wasiConfigVars(configFilter)
    assert(pf != null)
  }

  test("AgentAllFilter combines multiple AgentPropertyFilters") {
    val nameFilter =
      HostApi.AgentPropertyFilter.name(HostApi.AgentNameFilter(HostApi.StringFilterComparator.Equal, "a"))
    val statusFilter = HostApi.AgentPropertyFilter.status(
      HostApi.AgentStatusFilter(HostApi.FilterComparator.Equal, HostApi.AgentStatus.Running)
    )
    val all: HostApi.AgentAllFilter = HostApi.AgentAllFilter(List(nameFilter, statusFilter))
    assert(all != null)
  }

  test("AgentAnyFilter combines multiple AgentAllFilters") {
    val all1 = HostApi.AgentAllFilter(
      List(HostApi.AgentPropertyFilter.name(HostApi.AgentNameFilter(HostApi.StringFilterComparator.Equal, "a")))
    )
    val all2 = HostApi.AgentAllFilter(
      List(HostApi.AgentPropertyFilter.name(HostApi.AgentNameFilter(HostApi.StringFilterComparator.Equal, "b")))
    )
    val any: HostApi.AgentAnyFilter = HostApi.AgentAnyFilter(List(all1, all2))
    assert(any != null)
  }

  // ---------------------------------------------------------------------------
  // Literal companion object construction
  // ---------------------------------------------------------------------------

  test("UuidLiteral companion constructs from js.BigInts") {
    val uuid: HostApi.UuidLiteral = HostApi.UuidLiteral(js.BigInt(123), js.BigInt(456))
    assert(uuid != null)
  }

  test("ComponentIdLiteral companion constructs from UuidLiteral") {
    val uuid                            = HostApi.UuidLiteral(js.BigInt(1), js.BigInt(2))
    val cid: HostApi.ComponentIdLiteral = HostApi.ComponentIdLiteral(uuid)
    assert(cid != null)
  }

  test("AgentIdLiteral companion constructs from ComponentIdLiteral and name") {
    val uuid                        = HostApi.UuidLiteral(js.BigInt(1), js.BigInt(2))
    val cid                         = HostApi.ComponentIdLiteral(uuid)
    val aid: HostApi.AgentIdLiteral = HostApi.AgentIdLiteral(cid, "my-agent")
    assert(aid != null)
  }

  test("PromiseIdLiteral companion constructs from AgentIdLiteral and oplog index") {
    val uuid = HostApi.UuidLiteral(js.BigInt(1), js.BigInt(2))
    val cid  = HostApi.ComponentIdLiteral(uuid)
    val aid  = HostApi.AgentIdLiteral(cid, "my-agent")
    val pid  = HostApi.PromiseIdLiteral(aid, js.BigInt(42))
    assert(pid != null)
  }
}
