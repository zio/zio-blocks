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

import golem.HostApi
import golem.host.js._
import zio.test._

import scala.scalajs.js

object DurabilityApiRoundtripSpec extends ZIOSpecDefault {
  import DurabilityApi._

  def spec = suite("DurabilityApiRoundtripSpec")(
    // --- DurableFunctionType round-trips ---

    test("ReadLocal round-trip") {
      val jsVal = DurableFunctionType.toJs(DurableFunctionType.ReadLocal)
      assertTrue(
        jsVal.asInstanceOf[js.Dynamic].tag.asInstanceOf[String] == "read-local",
        DurableFunctionType.fromJs(jsVal) == DurableFunctionType.ReadLocal
      )
    },

    test("WriteLocal round-trip") {
      val jsVal = DurableFunctionType.toJs(DurableFunctionType.WriteLocal)
      assertTrue(
        jsVal.asInstanceOf[js.Dynamic].tag.asInstanceOf[String] == "write-local",
        DurableFunctionType.fromJs(jsVal) == DurableFunctionType.WriteLocal
      )
    },

    test("ReadRemote round-trip") {
      val jsVal = DurableFunctionType.toJs(DurableFunctionType.ReadRemote)
      assertTrue(
        jsVal.asInstanceOf[js.Dynamic].tag.asInstanceOf[String] == "read-remote",
        DurableFunctionType.fromJs(jsVal) == DurableFunctionType.ReadRemote
      )
    },

    test("WriteRemote round-trip") {
      val jsVal = DurableFunctionType.toJs(DurableFunctionType.WriteRemote)
      assertTrue(
        jsVal.asInstanceOf[js.Dynamic].tag.asInstanceOf[String] == "write-remote",
        DurableFunctionType.fromJs(jsVal) == DurableFunctionType.WriteRemote
      )
    },

    test("WriteRemoteBatched with None round-trip") {
      val ft    = DurableFunctionType.WriteRemoteBatched(None)
      val jsVal = DurableFunctionType.toJs(ft)
      assertTrue(
        jsVal.asInstanceOf[js.Dynamic].tag.asInstanceOf[String] == "write-remote-batched",
        DurableFunctionType.fromJs(jsVal) == ft
      )
    },

    test("WriteRemoteBatched with Some round-trip") {
      val ft     = DurableFunctionType.WriteRemoteBatched(Some(BigInt(42)))
      val jsVal  = DurableFunctionType.toJs(ft)
      val parsed = DurableFunctionType.fromJs(jsVal)
      assertTrue(
        parsed.isInstanceOf[DurableFunctionType.WriteRemoteBatched],
        parsed.asInstanceOf[DurableFunctionType.WriteRemoteBatched].begin == Some(BigInt(42))
      )
    },

    test("WriteRemoteTransaction with None round-trip") {
      val ft    = DurableFunctionType.WriteRemoteTransaction(None)
      val jsVal = DurableFunctionType.toJs(ft)
      assertTrue(
        jsVal.asInstanceOf[js.Dynamic].tag.asInstanceOf[String] == "write-remote-transaction",
        DurableFunctionType.fromJs(jsVal) == ft
      )
    },

    test("WriteRemoteTransaction with Some round-trip") {
      val ft     = DurableFunctionType.WriteRemoteTransaction(Some(BigInt(100)))
      val jsVal  = DurableFunctionType.toJs(ft)
      val parsed = DurableFunctionType.fromJs(jsVal)
      assertTrue(
        parsed.isInstanceOf[DurableFunctionType.WriteRemoteTransaction],
        parsed.asInstanceOf[DurableFunctionType.WriteRemoteTransaction].begin == Some(BigInt(100))
      )
    },

    test("unknown DurableFunctionType tag throws") {
      val raw = js.Dynamic.literal(tag = "unknown")
      assertTrue(scala.util.Try(DurableFunctionType.fromJs(raw.asInstanceOf[JsWrappedFunctionType])).isFailure)
    },

    // --- OplogEntryVersion ---

    test("OplogEntryVersion.fromString v1") {
      assertTrue(OplogEntryVersion.fromString("v1") == OplogEntryVersion.V1)
    },

    test("OplogEntryVersion.fromString v2") {
      assertTrue(OplogEntryVersion.fromString("v2") == OplogEntryVersion.V2)
    },

    test("OplogEntryVersion.fromString unknown defaults to V1") {
      assertTrue(OplogEntryVersion.fromString("v3") == OplogEntryVersion.V1)
    },

    // --- DurableExecutionState from mock js.Dynamic ---

    test("DurableExecutionState fields") {
      val state = DurableExecutionState(isLive = true, persistenceLevel = HostApi.PersistenceLevel.Smart)
      assertTrue(
        state.isLive == true,
        state.persistenceLevel == HostApi.PersistenceLevel.Smart
      )
    },

    test("DurableExecutionState with all persistence levels") {
      List(
        HostApi.PersistenceLevel.PersistNothing,
        HostApi.PersistenceLevel.PersistRemoteSideEffects,
        HostApi.PersistenceLevel.Smart
      ).foreach { pl =>
        val state = DurableExecutionState(isLive = false, persistenceLevel = pl)
        assertTrue(state.persistenceLevel == pl)
      }
      assertCompletes
    },

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
      assertTrue(
        inv.timestamp.seconds == BigInt(1700000000L),
        inv.timestamp.nanoseconds == 500000000,
        inv.functionName == "golem:api/test.{invoke}",
        inv.response.value.nodes.head == WitValueTypes.WitNode.PrimString("test"),
        inv.functionType == DurableFunctionType.ReadRemote,
        inv.entryVersion == OplogEntryVersion.V2
      )
    }
  )
}
