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
import org.scalatest.funsuite.AnyFunSuite

class DurabilityApiCompileSpec extends AnyFunSuite {
  import DurabilityApi._

  private val allFunctionTypes: List[DurableFunctionType] = List(
    DurableFunctionType.ReadLocal,
    DurableFunctionType.WriteLocal,
    DurableFunctionType.ReadRemote,
    DurableFunctionType.WriteRemote,
    DurableFunctionType.WriteRemoteBatched(None),
    DurableFunctionType.WriteRemoteBatched(Some(BigInt(42))),
    DurableFunctionType.WriteRemoteTransaction(None),
    DurableFunctionType.WriteRemoteTransaction(Some(BigInt(100)))
  )

  private def describeFunctionType(ft: DurableFunctionType): String = ft match {
    case DurableFunctionType.ReadLocal                 => s"read-local(${ft.tag})"
    case DurableFunctionType.WriteLocal                => s"write-local(${ft.tag})"
    case DurableFunctionType.ReadRemote                => s"read-remote(${ft.tag})"
    case DurableFunctionType.WriteRemote               => s"write-remote(${ft.tag})"
    case DurableFunctionType.WriteRemoteBatched(b)     => s"write-remote-batched($b,${ft.tag})"
    case DurableFunctionType.WriteRemoteTransaction(b) => s"write-remote-transaction($b,${ft.tag})"
  }

  private val state: DurableExecutionState =
    DurableExecutionState(isLive = true, persistenceLevel = HostApi.PersistenceLevel.Smart)

  private val entryVersions: List[OplogEntryVersion] =
    List(OplogEntryVersion.V1, OplogEntryVersion.V2)

  private val sampleVat: WitValueTypes.ValueAndType = WitValueTypes.ValueAndType(
    WitValueTypes.WitValue(List(WitValueTypes.WitNode.PrimString("test"))),
    WitValueTypes.WitType(
      List(
        WitValueTypes.NamedWitTypeNode(None, None, WitValueTypes.WitTypeNode.PrimStringType)
      )
    )
  )

  private val invocation: PersistedDurableFunctionInvocation = PersistedDurableFunctionInvocation(
    timestampSeconds = BigInt(1700000000L),
    timestampNanos = 500000000L,
    functionName = "test-func",
    response = sampleVat,
    functionType = DurableFunctionType.ReadLocal,
    entryVersion = OplogEntryVersion.V1
  )

  test("all DurableFunctionType variants constructed") {
    assert(allFunctionTypes.size == 8)
  }

  test("exhaustive DurableFunctionType match compiles") {
    allFunctionTypes.foreach(ft => assert(describeFunctionType(ft).nonEmpty))
  }

  test("DurableExecutionState construction") {
    assert(state.isLive)
    assert(state.persistenceLevel == HostApi.PersistenceLevel.Smart)
  }

  test("OplogEntryVersion exhaustive") {
    entryVersions.foreach {
      case OplogEntryVersion.V1 => assert(true)
      case OplogEntryVersion.V2 => assert(true)
    }
  }

  test("PersistedDurableFunctionInvocation field access") {
    assert(invocation.timestampSeconds == BigInt(1700000000L))
    assert(invocation.timestampNanos == 500000000L)
    assert(invocation.functionName == "test-func")
    assert(invocation.response.value.nodes.nonEmpty)
    assert(invocation.functionType == DurableFunctionType.ReadLocal)
    assert(invocation.entryVersion == OplogEntryVersion.V1)
  }

  test("OplogIndex type alias") {
    val idx: OplogIndex = BigInt(99)
    assert(idx == BigInt(99))
  }
}
