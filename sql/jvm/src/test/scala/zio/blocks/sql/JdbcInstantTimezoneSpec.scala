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

import zio.test.*

object JdbcInstantTimezoneSpec extends ZIOSpecDefault {

  private val berlinTimeZone = java.util.TimeZone.getTimeZone("Europe/Berlin")

  private def withDefaultTimeZone[A](timeZone: java.util.TimeZone)(thunk: => A): A = {
    val original = java.util.TimeZone.getDefault
    java.util.TimeZone.setDefault(timeZone)
    try thunk
    finally java.util.TimeZone.setDefault(original)
  }

  private def withPostgresConnection[A](f: java.sql.Connection => A): A = {
    val url =
      sys.env.getOrElse("DB_URL", "jdbc:postgresql://localhost:32886/postgres")
    val user = sys.env.getOrElse("DB_USERNAME", "postgres")
    val password = sys.env.getOrElse("DB_PASSWORD", "postgres")
    val conn = java.sql.DriverManager.getConnection(url, user, password)
    try f(conn)
    finally conn.close()
  }

  def spec: Spec[TestEnvironment, Any] = suite("JdbcInstantTimezoneSpec")(
    test("Instant round-trips through timestamp without time zone with non-UTC JVM timezone") {
      withDefaultTimeZone(berlinTimeZone) {
        withPostgresConnection { conn =>
          val instant = java.time.Instant.parse("2025-07-10T15:52:46.632293Z")

          val create = conn.createStatement()
          try {
            create.executeUpdate("DROP TABLE IF EXISTS jdbc_instant_timezone_spec")
            create.executeUpdate("CREATE TABLE jdbc_instant_timezone_spec (value TIMESTAMP WITHOUT TIME ZONE NOT NULL)")
          } finally create.close()

          val insert = conn.prepareStatement("INSERT INTO jdbc_instant_timezone_spec (value) VALUES (?)")
          try {
            new JdbcParamWriter(insert).setInstant(1, instant)
            insert.executeUpdate()
          } finally insert.close()

          val select = conn.prepareStatement("SELECT value FROM jdbc_instant_timezone_spec")
          try {
            val rs = select.executeQuery()
            try {
              val hasRow         = rs.next()
              val decodedInstant = if (hasRow) new JdbcResultReader(rs).getInstant(1) else null
              assertTrue(hasRow, decodedInstant == instant)
            } finally rs.close()
          } finally select.close()
        }
      }
    }
  )
}
