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

package zio.blocks.sql

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.sql.{Connection, DriverManager, PreparedStatement, ResultSet}

import zio.test.*

/**
 * Unit tests for the conservative BigDecimal cross-dialect strategy:
 *
 *   - the SQLite JDBC driver binds `setBigDecimal` as TEXT, which breaks
 *     numeric comparisons (a REAL is always less than a TEXT under SQLite's
 *     type ordering), so the param writer binds BigDecimal as a double for
 *     the SQLite dialect and keeps the exact `setBigDecimal` for PostgreSQL;
 *   - the SQLite driver throws `column -1 out of bounds` instead of returning
 *     null when a NULL column is read via `getBigDecimal`, so the result
 *     reader records the NULL for both the normal null return and the
 *     SQLite exception path.
 *
 * The dialect is threaded explicitly into the writer, so wrapped or proxied
 * connections do not affect the binding decision.
 */
object JdbcBigDecimalSpec extends ZIOSpecDefault {
  private val _ = Class.forName("org.sqlite.JDBC")

  private def sqliteConnection(): Connection = {
    val c = DriverManager.getConnection("jdbc:sqlite::memory:")
    c
  }

  /** Proxy-wraps a real connection to prove the dialect-driven binding works through wrappers. */
  private def wrappedConnection(delegate: Connection): Connection =
    Proxy
      .newProxyInstance(
        getClass.getClassLoader,
        Array(classOf[Connection]),
        new InvocationHandler {
          override def invoke(proxy: Any, method: Method, args: Array[AnyRef] | Null): AnyRef =
            try method.invoke(delegate, args*)
            catch { case e: java.lang.reflect.InvocationTargetException => throw e.getCause }
        }
      )
      .asInstanceOf[Connection]

  private def bindTypeOf(conn: Connection, dialect: SqlDialect): String = {
    val ps     = conn.prepareStatement("SELECT typeof(?)")
    try {
new JdbcParamWriter(ps, dialect).setBigDecimal(1, java.math.BigDecimal.valueOf(10L))
      val rs = ps.executeQuery()
      try {
        rs.next()
        rs.getString(1)
      } finally rs.close()
    } finally ps.close()
  }

  def spec = suite("JdbcBigDecimalSpec")(
    suite("JdbcParamWriter dialect-driven BigDecimal binding")(
      test("SQLite dialect binds BigDecimal as REAL (double) so numeric comparisons stay truthful") {
        val conn = sqliteConnection()
        try {
          val t = bindTypeOf(conn, SqlDialect.SQLite)
          assertTrue(t == "real")
        }
        finally conn.close()
      },
      test("PostgreSQL dialect preserves the exact setBigDecimal binding") {
        val conn = sqliteConnection()
        try {
          // On the SQLite driver, setBigDecimal binds as TEXT; the PG dialect path must
          // use setBigDecimal untouched (verified here via the driver's own binding).
          val t = bindTypeOf(conn, SqlDialect.PostgreSQL)
          assertTrue(t == "text")
        } finally conn.close()
      },
      test("wrapped/proxied connection still binds correctly via the explicit dialect") {
        val conn = wrappedConnection(sqliteConnection())
        try {
          val t = bindTypeOf(conn, SqlDialect.SQLite)
          assertTrue(t == "real")
        }
        finally conn.close()
      }
    ),
    suite("JdbcResultReader BigDecimal NULL recording")(
      test("normal null return is recorded in the null bitmap and wasNull") {
        val rs = proxyResultSet(
          onGetBigDecimalIndex = () => null,
          onGetBigDecimalLabel = () => null,
          onWasNull = () => true
        )
        val reader = new JdbcResultReader(rs)
        reader.beginRow()
        val v = reader.getBigDecimal("value")
        assertTrue(v == null, reader.isNull("value"), reader.wasNull)
      },
      test("SQLite exception path (getBigDecimal throws) is recorded as NULL") {
        val rs = proxyResultSet(
          onGetBigDecimalIndex = () => throw new java.sql.SQLException("column -1 out of bounds [1,1]"),
          onGetBigDecimalLabel = () => throw new java.sql.SQLException("column -1 out of bounds [1,1]"),
          onWasNull = () => throw new java.sql.SQLException("column -1 out of bounds [1,1]")
        )
        val reader = new JdbcResultReader(rs)
        reader.beginRow()
        val v = reader.getBigDecimal("value")
        assertTrue(v == null, reader.isNull("value"), reader.wasNull)
      },
      test("non-null BigDecimal is returned and not recorded as NULL") {
        val rs = proxyResultSet(
          onGetBigDecimalIndex = () => java.math.BigDecimal.valueOf(105, 1),
          onGetBigDecimalLabel = () => java.math.BigDecimal.valueOf(105, 1),
          onWasNull = () => false
        )
        val reader = new JdbcResultReader(rs)
        reader.beginRow()
        val v = reader.getBigDecimal("value")
        assertTrue(v == java.math.BigDecimal.valueOf(105, 1), !reader.isNull("value"), !reader.wasNull)
      },
      test("index-based reads record the same null state") {
        val rs = proxyResultSet(
          onGetBigDecimalIndex = () => throw new java.sql.SQLException("column -1 out of bounds [1,1]"),
          onGetBigDecimalLabel = () => throw new java.sql.SQLException("column -1 out of bounds [1,1]"),
          onWasNull = () => throw new java.sql.SQLException("column -1 out of bounds [1,1]")
        )
        val reader = new JdbcResultReader(rs)
        reader.beginRow()
        val v = reader.getBigDecimal(1)
        assertTrue(v == null, reader.isNull(1), reader.wasNull)
      }
    )
  )

  private def proxyResultSet(
    onGetBigDecimalIndex: () => java.math.BigDecimal,
    onGetBigDecimalLabel: () => java.math.BigDecimal,
    onWasNull: () => Boolean
  ): ResultSet =
    Proxy
      .newProxyInstance(
        getClass.getClassLoader,
        Array(classOf[ResultSet]),
        new InvocationHandler {
          override def invoke(proxy: Any, method: Method, args: Array[AnyRef] | Null): AnyRef =
            method.getName match {
              case "getBigDecimal" if args != null && args.length == 1 && args(0).isInstanceOf[java.lang.Integer] =>
                onGetBigDecimalIndex().asInstanceOf[AnyRef]
              case "getBigDecimal" if args != null && args.length == 1 && args(0).isInstanceOf[String] =>
                onGetBigDecimalLabel().asInstanceOf[AnyRef]
              case "wasNull"    => java.lang.Boolean.valueOf(onWasNull())
              case "getMetaData" => new java.sql.ResultSetMetaData {
                  def getColumnCount: Int = 1
                  def getColumnLabel(i: Int): String = "value"
                  def isAutoIncrement(i: Int): Boolean = false
                  def isCaseSensitive(i: Int): Boolean = false
                  def isSearchable(i: Int): Boolean = false
                  def isCurrency(i: Int): Boolean = false
                  def isNullable(i: Int): Int = java.sql.ResultSetMetaData.columnNullable
                  def isSigned(i: Int): Boolean = false
                  def getColumnDisplaySize(i: Int): Int = 0
                  def getColumnName(i: Int): String = "value"
                  def getSchemaName(i: Int): String = ""
                  def getPrecision(i: Int): Int = 0
                  def getScale(i: Int): Int = 0
                  def getTableName(i: Int): String = ""
                  def getCatalogName(i: Int): String = ""
                  def getColumnType(i: Int): Int = java.sql.Types.DECIMAL
                  def getColumnTypeName(i: Int): String = "DECIMAL"
                  def isReadOnly(i: Int): Boolean = true
                  def isWritable(i: Int): Boolean = false
                  def isDefinitelyWritable(i: Int): Boolean = false
                  def getColumnClassName(i: Int): String = "java.math.BigDecimal"
                  def unwrap[T](iface: Class[T]): T = throw new java.sql.SQLException("unwrap unsupported")
                  def isWrapperFor(iface: Class[?]): Boolean = false
                }
              case "getColumnLabel" => "value"
              case "next"           => java.lang.Boolean.TRUE
              case "close"          => null
              case "getStatement"   => null
              case "getWarnings"    => null
              case "clearWarnings"  => null
              case "getCursorName"  => null
              case "getConcurrency" => Integer.valueOf(java.sql.ResultSet.CONCUR_READ_ONLY)
              case "getFetchDirection" => Integer.valueOf(java.sql.ResultSet.FETCH_FORWARD)
              case "getType"        => Integer.valueOf(java.sql.ResultSet.TYPE_FORWARD_ONLY)
              case "getFetchSize"   => Integer.valueOf(0)
              case "getRow"         => Integer.valueOf(0)
              case "getHoldability" => Integer.valueOf(0)
              case "isClosed"       => java.lang.Boolean.FALSE
              case "isFirst"        => java.lang.Boolean.FALSE
              case "isLast"         => java.lang.Boolean.FALSE
              case "isBeforeFirst"  => java.lang.Boolean.TRUE
              case "isAfterLast"    => java.lang.Boolean.FALSE
              case "toString"       => "ProxyResultSet"
              case _                => null
            }
        }
      )
      .asInstanceOf[ResultSet]
}