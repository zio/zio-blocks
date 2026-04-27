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

package zio.blocks.rpc

import zio.test.*
import zio.blocks.rpc.fixtures.*
import zio.blocks.typeid.TypeId

object RPCMacroSpec extends ZIOSpecDefault {
  def spec = suite("RPCMacroSpec")(
    suite("Macro derivation")(
      test("GreeterService - single method") {
        val rpc = RPC.derived[GreeterService]
        assertTrue(
          rpc.label == "GreeterService",
          rpc.operations.length == 1,
          rpc.operations(0).name == "greet",
          rpc.operations(0).parameterNames.length == 1,
          rpc.operations(0).parameterNames(0) == "name"
        )
      },
      test("TodoService - multi-method") {
        val rpc = RPC.derived[TodoService]
        assertTrue(
          rpc.operations.length == 3,
          rpc.operations.map(_.name).toSet == Set("getTodo", "createTodo", "listTodos")
        )
      },
      test("EmptyService - no methods") {
        val rpc = RPC.derived[EmptyService]
        assertTrue(rpc.operations.isEmpty)
      },
      test("HealthService - zero params") {
        val rpc = RPC.derived[HealthService]
        assertTrue(
          rpc.operations.length == 1,
          rpc.operations(0).parameterNames.isEmpty
        )
      },
      test("SearchService - multi params") {
        val rpc = RPC.derived[SearchService]
        assertTrue(
          rpc.operations(0).parameterNames.length == 3,
          rpc.operations(0).parameterNames(0) == "query",
          rpc.operations(0).parameterNames(1) == "limit",
          rpc.operations(0).parameterNames(2) == "offset"
        )
      },
      test("ErrorService - Either return type has error schema") {
        val rpc = RPC.derived[ErrorService]
        assertTrue(rpc.operations(0).errorSchema.isDefined)
      },
      test("AnnotatedService - method annotations") {
        val rpc      = RPC.derived[AnnotatedService]
        val lookupOp = rpc.operations.find(_.name == "lookup").get
        assertTrue(lookupOp.annotations.exists(_.isInstanceOf[Idempotent]))
      },
      test("GreeterService - plain return type has no error schema") {
        val rpc = RPC.derived[GreeterService]
        assertTrue(rpc.operations(0).errorSchema.isEmpty)
      },
      test("TodoService - Either has error schema") {
        val rpc = RPC.derived[TodoService]
        assertTrue(rpc.operations(0).errorSchema.isDefined)
      },
      test("TodoService - createTodo has 2 params") {
        val rpc      = RPC.derived[TodoService]
        val createOp = rpc.operations.find(_.name == "createTodo").get
        assertTrue(
          createOp.parameterNames.length == 2,
          createOp.parameterNames(0) == "title",
          createOp.parameterNames(1) == "description"
        )
      },
      test("AnnotatedService - subscribe has no extra annotations") {
        val rpc         = RPC.derived[AnnotatedService]
        val subscribeOp = rpc.operations.find(_.name == "subscribe").get
        assertTrue(subscribeOp.annotations.isEmpty)
      },
      test("ErrorService - operation error schema from Either") {
        val rpc = RPC.derived[ErrorService]
        assertTrue(rpc.operations(0).errorSchema.isDefined)
      },
      test("AnnotatedService - Either methods have error schema") {
        val rpc = RPC.derived[AnnotatedService]
        assertTrue(rpc.operations.forall(_.errorSchema.isDefined))
      },
      test("InheritedService - includes parent methods") {
        val rpc = RPC.derived[InheritedService]
        assertTrue(
          rpc.operations.map(_.name).toSet == Set("parentMethod", "childMethod")
        )
      },
      test("MultiAnnotatedService - multiple annotations on same method") {
        val rpc = RPC.derived[MultiAnnotatedService]
        val op  = rpc.operations(0)
        assertTrue(
          op.annotations.length == 2,
          op.annotations.exists(_.isInstanceOf[Idempotent]),
          op.annotations.exists(_.isInstanceOf[RpcDeprecated])
        )
      }
    ),
    suite("Schema round-trip tests")(
      test("GreeterService - outputSchema has correct TypeId for String") {
        val rpc    = RPC.derived[GreeterService]
        val actual = rpc.operations(0).outputSchema.reflect.typeId
        assertTrue(actual == TypeId.of[String])
      },
      test("TodoService - inputSchema captures parameter type Int for getTodo") {
        val rpc    = RPC.derived[TodoService]
        val actual = rpc.operations.find(_.name == "getTodo").get.inputSchema.reflect.typeId
        assertTrue(actual == TypeId.of[Int])
      },
      test("SearchService - inputSchema is tuple for multi-params") {
        val rpc    = RPC.derived[SearchService]
        val actual = rpc.operations(0).inputSchema.reflect.typeId
        assertTrue(actual == TypeId.of[(String, Int, Int)])
      },
      test("HealthService - inputSchema is Unit for zero params") {
        val rpc    = RPC.derived[HealthService]
        val actual = rpc.operations(0).inputSchema.reflect.typeId
        assertTrue(actual == TypeId.of[Unit])
      },
      test("TodoService - output schema for getTodo is Todo") {
        val rpc    = RPC.derived[TodoService]
        val actual = rpc.operations.find(_.name == "getTodo").get.outputSchema.reflect.typeId
        assertTrue(actual == TypeId.of[Todo])
      },
      test("TodoService - listTodos output is List[Todo]") {
        val rpc    = RPC.derived[TodoService]
        val actual = rpc.operations.find(_.name == "listTodos").get.outputSchema.reflect.typeId
        assertTrue(actual == TypeId.of[List[Todo]])
      },
      test("typeId captures service trait identity") {
        val rpc = RPC.derived[GreeterService]
        assertTrue(rpc.typeId == TypeId.of[GreeterService])
      }
    ),
    suite("RPC.derive integration")(
      test("RPC.derive convenience method compiles and works") {
        val rpc         = RPC.derived[GreeterService]
        val testDeriver = new RpcDeriver[List] {
          def deriveService[T](rpc: RPC[T]): List[T] = Nil
        }
        val result = rpc.derive(testDeriver)
        assertTrue(result == Nil)
      },
      test("RPC.derived label matches trait name") {
        val rpc = RPC.derived[GreeterService]
        assertTrue(rpc.label == "GreeterService")
      }
    )
  )
}
