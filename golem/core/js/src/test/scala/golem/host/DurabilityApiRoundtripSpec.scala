package golem.host

import golem.HostApi
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.scalajs.js

class DurabilityApiRoundtripSpec extends AnyFunSuite with Matchers {
  import DurabilityApi._

  // --- DurableFunctionType round-trips ---

  test("ReadLocal round-trip") {
    val dyn = DurableFunctionType.toDynamic(DurableFunctionType.ReadLocal)
    dyn.tag.asInstanceOf[String] shouldBe "read-local"
    DurableFunctionType.fromDynamic(dyn) shouldBe DurableFunctionType.ReadLocal
  }

  test("WriteLocal round-trip") {
    val dyn = DurableFunctionType.toDynamic(DurableFunctionType.WriteLocal)
    dyn.tag.asInstanceOf[String] shouldBe "write-local"
    DurableFunctionType.fromDynamic(dyn) shouldBe DurableFunctionType.WriteLocal
  }

  test("ReadRemote round-trip") {
    val dyn = DurableFunctionType.toDynamic(DurableFunctionType.ReadRemote)
    dyn.tag.asInstanceOf[String] shouldBe "read-remote"
    DurableFunctionType.fromDynamic(dyn) shouldBe DurableFunctionType.ReadRemote
  }

  test("WriteRemote round-trip") {
    val dyn = DurableFunctionType.toDynamic(DurableFunctionType.WriteRemote)
    dyn.tag.asInstanceOf[String] shouldBe "write-remote"
    DurableFunctionType.fromDynamic(dyn) shouldBe DurableFunctionType.WriteRemote
  }

  test("WriteRemoteBatched with None round-trip") {
    val ft  = DurableFunctionType.WriteRemoteBatched(None)
    val dyn = DurableFunctionType.toDynamic(ft)
    dyn.tag.asInstanceOf[String] shouldBe "write-remote-batched"
    DurableFunctionType.fromDynamic(dyn) shouldBe ft
  }

  test("WriteRemoteBatched with Some round-trip") {
    val ft     = DurableFunctionType.WriteRemoteBatched(Some(BigInt(42)))
    val dyn    = DurableFunctionType.toDynamic(ft)
    val parsed = DurableFunctionType.fromDynamic(dyn)
    parsed shouldBe a[DurableFunctionType.WriteRemoteBatched]
    parsed.asInstanceOf[DurableFunctionType.WriteRemoteBatched].begin shouldBe Some(BigInt(42))
  }

  test("WriteRemoteTransaction with None round-trip") {
    val ft  = DurableFunctionType.WriteRemoteTransaction(None)
    val dyn = DurableFunctionType.toDynamic(ft)
    dyn.tag.asInstanceOf[String] shouldBe "write-remote-transaction"
    DurableFunctionType.fromDynamic(dyn) shouldBe ft
  }

  test("WriteRemoteTransaction with Some round-trip") {
    val ft     = DurableFunctionType.WriteRemoteTransaction(Some(BigInt(100)))
    val dyn    = DurableFunctionType.toDynamic(ft)
    val parsed = DurableFunctionType.fromDynamic(dyn)
    parsed shouldBe a[DurableFunctionType.WriteRemoteTransaction]
    parsed.asInstanceOf[DurableFunctionType.WriteRemoteTransaction].begin shouldBe Some(BigInt(100))
  }

  test("unknown DurableFunctionType tag throws") {
    val raw = js.Dynamic.literal(tag = "unknown")
    an[IllegalArgumentException] should be thrownBy DurableFunctionType.fromDynamic(raw)
  }

  // --- OplogEntryVersion ---

  test("OplogEntryVersion.fromString v1") {
    OplogEntryVersion.fromString("v1") shouldBe OplogEntryVersion.V1
  }

  test("OplogEntryVersion.fromString v2") {
    OplogEntryVersion.fromString("v2") shouldBe OplogEntryVersion.V2
  }

  test("OplogEntryVersion.fromString unknown defaults to V1") {
    OplogEntryVersion.fromString("v3") shouldBe OplogEntryVersion.V1
  }

  // --- DurableExecutionState from mock js.Dynamic ---

  test("DurableExecutionState fields") {
    val state = DurableExecutionState(isLive = true, persistenceLevel = HostApi.PersistenceLevel.Smart)
    state.isLive shouldBe true
    state.persistenceLevel shouldBe HostApi.PersistenceLevel.Smart
  }

  test("DurableExecutionState with all persistence levels") {
    List(
      HostApi.PersistenceLevel.PersistNothing,
      HostApi.PersistenceLevel.PersistRemoteSideEffects,
      HostApi.PersistenceLevel.Smart
    ).foreach { pl =>
      val state = DurableExecutionState(isLive = false, persistenceLevel = pl)
      state.persistenceLevel shouldBe pl
    }
  }

  // --- PersistedDurableFunctionInvocation ---

  test("PersistedDurableFunctionInvocation construction") {
    val vat = WitValueTypes.ValueAndType(
      WitValueTypes.WitValue(List(WitValueTypes.WitNode.PrimString("test"))),
      WitValueTypes.WitType(List(WitValueTypes.NamedWitTypeNode(None, None, WitValueTypes.WitTypeNode.PrimStringType)))
    )
    val inv = PersistedDurableFunctionInvocation(
      timestampSeconds = BigInt(1700000000L),
      timestampNanos = 500000000L,
      functionName = "golem:api/test.{invoke}",
      response = vat,
      functionType = DurableFunctionType.ReadRemote,
      entryVersion = OplogEntryVersion.V2
    )
    inv.timestampSeconds shouldBe BigInt(1700000000L)
    inv.timestampNanos shouldBe 500000000L
    inv.functionName shouldBe "golem:api/test.{invoke}"
    inv.response.value.nodes.head shouldBe WitValueTypes.WitNode.PrimString("test")
    inv.functionType shouldBe DurableFunctionType.ReadRemote
    inv.entryVersion shouldBe OplogEntryVersion.V2
  }
}
