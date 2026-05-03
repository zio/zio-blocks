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

package zio.blocks.sql.zio

import _root_.zio.*
import _root_.zio.blocks.sql.*
import _root_.zio.test.*

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.sql.Connection
import scala.collection.mutable.ListBuffer

object TransactorZIOSpec extends ZIOSpecDefault {

  private final case class ConnectionState(
    var autoCommit: Boolean = true,
    var closed: Boolean = false,
    var commitCalls: Int = 0,
    var rollbackCalls: Int = 0,
    val autoCommitValues: ListBuffer[Boolean] = ListBuffer.empty,
    var failCommit: Boolean = false,
    var failRollback: Boolean = false
  )

  private def connection(state: ConnectionState): Connection = {
    val handler = new InvocationHandler {
      override def invoke(proxy: Any, method: Method, args: Array[AnyRef] | Null): AnyRef =
        method.getName match {
          case "getAutoCommit" => java.lang.Boolean.valueOf(state.autoCommit)
          case "setAutoCommit" =>
            val value = args.nn(0).asInstanceOf[java.lang.Boolean].booleanValue()
            state.autoCommit = value
            state.autoCommitValues += value
            null
          case "commit"        =>
            state.commitCalls += 1
            if (state.failCommit) throw new java.sql.SQLException("commit failed")
            null
          case "rollback"      =>
            state.rollbackCalls += 1
            if (state.failRollback) throw new java.sql.SQLException("rollback failed")
            null
          case "close"         =>
            state.closed = true
            null
          case "isClosed"      => java.lang.Boolean.valueOf(state.closed)
          case "unwrap"        => null
          case "isWrapperFor"  => java.lang.Boolean.FALSE
          case "toString"      => "TestConnection"
          case "hashCode"      => Integer.valueOf(java.lang.System.identityHashCode(proxy.asInstanceOf[AnyRef]))
          case "equals"        => java.lang.Boolean.valueOf(proxy.asInstanceOf[AnyRef] eq args.nn(0))
          case _                => defaultValue(method.getReturnType)
        }
    }

    Proxy.newProxyInstance(getClass.getClassLoader, Array(classOf[Connection]), handler).asInstanceOf[Connection]
  }

  private def defaultValue(returnType: Class[?]): AnyRef =
    if (returnType == java.lang.Boolean.TYPE) java.lang.Boolean.FALSE
    else if (returnType == java.lang.Integer.TYPE) Integer.valueOf(0)
    else if (returnType == java.lang.Long.TYPE) java.lang.Long.valueOf(0L)
    else if (returnType == java.lang.Double.TYPE) java.lang.Double.valueOf(0d)
    else if (returnType == java.lang.Float.TYPE) java.lang.Float.valueOf(0f)
    else if (returnType == java.lang.Short.TYPE) java.lang.Short.valueOf(0.toShort)
    else if (returnType == java.lang.Byte.TYPE) java.lang.Byte.valueOf(0.toByte)
    else null

  def spec: Spec[TestEnvironment, Any] = suite("TransactorZIOSpec")(
    test("transactZIO rolls back when commit fails after a successful body") {
      val state      = ConnectionState(failCommit = true)
      val transactor = new TransactorZIO(() => connection(state), SqlDialect.SQLite)
      val program: DbTx ?=> _root_.zio.ZIO[Any, Nothing, Int] = _root_.zio.ZIO.succeed(42)

      for {
        exit <- transactor.transactZIO(program).exit
      } yield assertTrue(
        exit.isFailure,
        state.commitCalls == 1,
        state.rollbackCalls == 1,
        state.closed,
        state.autoCommit,
        state.autoCommitValues.toList == List(false, true)
      )
    },
    test("transactZIO commits once on success without rollback") {
      val state      = ConnectionState()
      val transactor = new TransactorZIO(() => connection(state), SqlDialect.SQLite)
      val program: DbTx ?=> _root_.zio.ZIO[Any, Nothing, Int] = _root_.zio.ZIO.succeed(42)

      for {
        value <- transactor.transactZIO(program)
      } yield assertTrue(
        value == 42,
        state.commitCalls == 1,
        state.rollbackCalls == 0,
        state.closed,
        state.autoCommit,
        state.autoCommitValues.toList == List(false, true)
      )
    }
  )
}
