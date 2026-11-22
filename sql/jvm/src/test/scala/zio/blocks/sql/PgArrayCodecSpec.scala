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

import java.sql.DriverManager

/**
 * Manual PostgreSQL integration coverage for [[DbCodec.arrayOf]].
 *
 * This suite is skipped unless `PG_ARRAY_INTEGRATION=1` (or `true`) is set,
 * because the default unit test environment has no PostgreSQL instance. The
 * rest of the array coverage runs at the codec/JDBC-adapter level with stubs.
 *
 * Connection variables mirror the other PostgreSQL suites: `DB_URL`,
 * `DB_USERNAME`, `DB_PASSWORD`.
 */
object PgArrayCodecSpec extends ZIOSpecDefault {

  private val enabled: Boolean =
    sys.env.get("PG_ARRAY_INTEGRATION").exists(v => v == "1" || v.equalsIgnoreCase("true"))

  private val url =
    sys.env.getOrElse("DB_URL", "jdbc:postgresql://localhost:32886/postgres")
  private val user     = sys.env.getOrElse("DB_USERNAME", "postgres")
  private val password = sys.env.getOrElse("DB_PASSWORD", "postgres")

  def spec: Spec[TestEnvironment, Any] = suite("PgArrayCodecSpec (manual; set PG_ARRAY_INTEGRATION=1)")(
    test("varchar[] and integer[] round-trip through DbCodec.arrayOf") {
      if (!enabled) assertCompletes
      else {
        Class.forName("org.postgresql.Driver")
        val conn = DriverManager.getConnection(url, user, password)
        try {
          val create = conn.createStatement()
          try {
            create.executeUpdate("DROP TABLE IF EXISTS pg_array_codec_spec")
            create.executeUpdate(
              "CREATE TABLE pg_array_codec_spec (id INTEGER PRIMARY KEY, tags VARCHAR(64)[], scores INTEGER[])"
            )
          } finally create.close()

          val stringCodec = DbCodec.arrayOf(DbCodec[String], "varchar")
          val intCodec    = DbCodec.arrayOf(DbCodec[Int], "integer")

          val insert = conn.prepareStatement(
            "INSERT INTO pg_array_codec_spec (id, tags, scores) VALUES (?, ?, ?)"
          )
          try {
            insert.setInt(1, 1)
            stringCodec.writeValue(new JdbcParamWriter(insert), 2, Array("organic", "vegan"))
            intCodec.writeValue(new JdbcParamWriter(insert), 3, Array(10, 20, 30))
            insert.executeUpdate()
          } finally insert.close()

          val select = conn.prepareStatement(
            "SELECT tags, scores FROM pg_array_codec_spec WHERE id = 1"
          )
          try {
            val rs = select.executeQuery()
            try {
              val hasRow = rs.next()
              val reader = new JdbcResultReader(rs)
              val tags   = if (hasRow) stringCodec.readValue(reader, 1) else null
              val scores = if (hasRow) intCodec.readValue(reader, 2) else null
              assertTrue(
                hasRow,
                tags.toList == List("organic", "vegan"),
                scores.toList == List(10, 20, 30)
              )
            } finally rs.close()
          } finally select.close()
        } finally conn.close()
      }
    }
  )
}
