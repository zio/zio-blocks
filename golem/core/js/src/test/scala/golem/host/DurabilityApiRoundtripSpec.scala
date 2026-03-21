package golem.host

import golem.HostApi
import golem.host.js._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.scalajs.js

class DurabilityApiRoundtripSpec extends AnyFunSuite with Matchers {
  import DurabilityApi._

  // --- DurableFunctionType round-trips ---

  test("ReadLocal round-trip") {
    val jsVal = DurableFunctionType.toJs(DurableFunctionType.ReadLocal)
    jsVal.asInstanceOf[js.Dynamic].tag.asInstanceOf[String] shouldBe "read-local"
    DurableFunctionType.fromJs(jsVal) shouldBe DurableFunctionType.ReadLocal
  }

  test("WriteLocal round-trip") {
    val jsVal = DurableFunctionType.toJs(DurableFunctionType.WriteLocal)
    jsVal.asInstanceOf[js.Dynamic].tag.asInstanceOf[String] shouldBe "write-local"
    DurableFunctionType.fromJs(jsVal) shouldBe DurableFunctionType.WriteLocal
  }

  test("ReadRemote round-trip") {
    val jsVal = DurableFunctionType.toJs(DurableFunctionType.ReadRemote)
    jsVal.asInstanceOf[js.Dynamic].tag.asInstanceOf[String] shouldBe "read-remote"
    DurableFunctionType.fromJs(jsVal) shouldBe DurableFunctionType.ReadRemote
  }

  test("WriteRemote round-trip") {
    val jsVal = DurableFunctionType.toJs(DurableFunctionType.WriteRemote)
    jsVal.asInstanceOf[js.Dynamic].tag.asInstanceOf[String] shouldBe "write-remote"
    DurableFunctionType.fromJs(jsVal) shouldBe DurableFunctionType.WriteRemote
  }

  test("WriteRemoteBatched with None round-trip") {
    val ft    = DurableFunctionType.WriteRemoteBatched(None)
    val jsVal = DurableFunctionType.toJs(ft)
    jsVal.asInstanceOf[js.Dynamic].tag.asInstanceOf[String] shouldBe "write-remote-batched"
    DurableFunctionType.fromJs(jsVal) shouldBe ft
  }

  test("WriteRemoteBatched with Some round-trip") {
    val ft     = DurableFunctionType.WriteRemoteBatched(Some(BigInt(42)))
    val jsVal  = DurableFunctionType.toJs(ft)
    val parsed = DurableFunctionType.fromJs(jsVal)
    parsed shouldBe a[DurableFunctionType.WriteRemoteBatched]
    parsed.asInstanceOf[DurableFunctionType.WriteRemoteBatched].begin shouldBe Some(BigInt(42))
  }

  test("WriteRemoteTransaction with None round-trip") {
    val ft    = DurableFunctionType.WriteRemoteTransaction(None)
    val jsVal = DurableFunctionType.toJs(ft)
    jsVal.asInstanceOf[js.Dynamic].tag.asInstanceOf[String] shouldBe "write-remote-transaction"
    DurableFunctionType.fromJs(jsVal) shouldBe ft
  }

  test("WriteRemoteTransaction with Some round-trip") {
    val ft     = DurableFunctionType.WriteRemoteTransaction(Some(BigInt(100)))
    val jsVal  = DurableFunctionType.toJs(ft)
    val parsed = DurableFunctionType.fromJs(jsVal)
    parsed shouldBe a[DurableFunctionType.WriteRemoteTransaction]
    parsed.asInstanceOf[DurableFunctionType.WriteRemoteTransaction].begin shouldBe Some(BigInt(100))
  }

  test("unknown DurableFunctionType tag throws") {
    val raw = js.Dynamic.literal(tag = "unknown")
    an[IllegalArgumentException] should be thrownBy DurableFunctionType.fromJs(raw.asInstanceOf[JsWrappedFunctionType])
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
      timestamp = Datetime(BigInt(1700000000L), 500000000),
      functionName = "golem:api/test.{invoke}",
      response = vat,
      functionType = DurableFunctionType.ReadRemote,
      entryVersion = OplogEntryVersion.V2
    )
    inv.timestamp.seconds shouldBe BigInt(1700000000L)
    inv.timestamp.nanoseconds shouldBe 500000000
    inv.functionName shouldBe "golem:api/test.{invoke}"
    inv.response.value.nodes.head shouldBe WitValueTypes.WitNode.PrimString("test")
    inv.functionType shouldBe DurableFunctionType.ReadRemote
    inv.entryVersion shouldBe OplogEntryVersion.V2
  }
}
