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
import _root_.zio.blocks.maybe.Maybe
import _root_.zio.blocks.sql.*
import _root_.zio.test.*

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.sql.{Connection, PreparedStatement, ResultSet, ResultSetMetaData}
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource

object JdbcTransactorLayersSpec extends ZIOSpecDefault {

  private def h2DataSource: DataSource =
    Proxy
      .newProxyInstance(
        getClass.getClassLoader,
        Array(classOf[DataSource]),
        new InvocationHandler {
          override def invoke(proxy: Any, method: Method, args: Array[AnyRef] | Null): AnyRef = method.getName match {
            case "getConnection"   => connection()
            case "getLogWriter"    => null
            case "setLogWriter"    => null
            case "setLoginTimeout" => null
            case "getLoginTimeout" => Integer.valueOf(0)
            case "getParentLogger" => java.util.logging.Logger.getGlobal
            case "unwrap"          => null
            case "isWrapperFor"    => java.lang.Boolean.FALSE
            case "toString"        => "TestDataSource"
            case _                 => null
          }
        }
      )
      .asInstanceOf[DataSource]

  private def connection(): Connection =
    Proxy
      .newProxyInstance(
        getClass.getClassLoader,
        Array(classOf[Connection]),
        new InvocationHandler {
          private var closed     = false
          private var autoCommit = true

          override def invoke(proxy: Any, method: Method, args: Array[AnyRef] | Null): AnyRef = method.getName match {
            case "prepareStatement" => preparedStatement()
            case "getAutoCommit"    => java.lang.Boolean.valueOf(autoCommit)
            case "setAutoCommit"    => autoCommit = args.nn(0).asInstanceOf[java.lang.Boolean].booleanValue(); null
            case "commit"           => null
            case "rollback"         => null
            case "close"            => closed = true; null
            case "isClosed"         => java.lang.Boolean.valueOf(closed)
            case "unwrap"           => null
            case "isWrapperFor"     => java.lang.Boolean.FALSE
            case "toString"         => "TestConnection"
            case _                  => defaultValue(method.getReturnType)
          }
        }
      )
      .asInstanceOf[Connection]

  private def preparedStatement(): PreparedStatement =
    Proxy
      .newProxyInstance(
        getClass.getClassLoader,
        Array(classOf[PreparedStatement]),
        new InvocationHandler {
          override def invoke(proxy: Any, method: Method, args: Array[AnyRef] | Null): AnyRef = method.getName match {
            case "executeQuery" => resultSet()
            case "close"        => null
            case "unwrap"       => null
            case "isWrapperFor" => java.lang.Boolean.FALSE
            case "toString"     => "TestPreparedStatement"
            case _              => defaultValue(method.getReturnType)
          }
        }
      )
      .asInstanceOf[PreparedStatement]

  private def resultSet(): ResultSet =
    Proxy
      .newProxyInstance(
        getClass.getClassLoader,
        Array(classOf[ResultSet]),
        new InvocationHandler {
          private val firstRow = new AtomicBoolean(true)

          override def invoke(proxy: Any, method: Method, args: Array[AnyRef] | Null): AnyRef = method.getName match {
            case "next"         => java.lang.Boolean.valueOf(firstRow.getAndSet(false))
            case "getInt"       => Integer.valueOf(1)
            case "getMetaData"  => metaData()
            case "close"        => null
            case "wasNull"      => java.lang.Boolean.FALSE
            case "unwrap"       => null
            case "isWrapperFor" => java.lang.Boolean.FALSE
            case "toString"     => "TestResultSet"
            case _              => defaultValue(method.getReturnType)
          }
        }
      )
      .asInstanceOf[ResultSet]

  private def metaData(): ResultSetMetaData =
    Proxy
      .newProxyInstance(
        getClass.getClassLoader,
        Array(classOf[ResultSetMetaData]),
        new InvocationHandler {
          override def invoke(proxy: Any, method: Method, args: Array[AnyRef] | Null): AnyRef = method.getName match {
            case "getColumnCount" => Integer.valueOf(1)
            case "getColumnLabel" => "value"
            case "getColumnName"  => "value"
            case "isNullable"     => Integer.valueOf(ResultSetMetaData.columnNullable)
            case "unwrap"         => null
            case "isWrapperFor"   => java.lang.Boolean.FALSE
            case "toString"       => "TestResultSetMetaData"
            case _                => defaultValue(method.getReturnType)
          }
        }
      )
      .asInstanceOf[ResultSetMetaData]

  def spec: Spec[TestEnvironment, Any] = suite("JdbcTransactorLayersSpec")(
    test("sqliteLayer wires and SELECT 1 executes successfully") {
      val program = ZIO.serviceWith[Transactor] { transactor =>
        transactor.connect {
          sql"SELECT 1".queryOne[Int]
        }
      }

      program
        .provideLayer(ZLayer.succeed(h2DataSource) >>> JdbcTransactor.sqliteLayer)
        .map(result => assertTrue(result == Maybe(1)))
    }
  )

  private def defaultValue(returnType: Class[?]): AnyRef =
    if (returnType == java.lang.Boolean.TYPE) java.lang.Boolean.FALSE
    else if (returnType == java.lang.Integer.TYPE) Integer.valueOf(0)
    else if (returnType == java.lang.Long.TYPE) java.lang.Long.valueOf(0L)
    else if (returnType == java.lang.Double.TYPE) java.lang.Double.valueOf(0d)
    else if (returnType == java.lang.Float.TYPE) java.lang.Float.valueOf(0f)
    else if (returnType == java.lang.Short.TYPE) java.lang.Short.valueOf(0.toShort)
    else if (returnType == java.lang.Byte.TYPE) java.lang.Byte.valueOf(0.toByte)
    else null
}
