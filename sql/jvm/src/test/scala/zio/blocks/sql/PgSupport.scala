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

import java.sql.DriverManager

/**
 * Environment-gated PostgreSQL helper for sql integration tests.
 *
 * Reuses the repository pattern from dataMigration's
 * PostgresMigrationIntegrationSpec (PGHOST/PGPORT/PGUSER/PGPASSWORD/PGDATABASE,
 * default port 32886, graceful skip). The sql-pg-ci-matrix plan is not yet
 * materialized in this workspace checkout; this is the smallest local gating
 * helper consistent with existing conventions, not a CI workflow change.
 */
object PgSupport {
  val pgHost: String     = sys.env.getOrElse("PGHOST", "localhost")
  val pgPort: Int        = sys.env.getOrElse("PGPORT", "32886").toInt
  val pgUser: String     = sys.env.getOrElse("PGUSER", "postgres")
  val pgPassword: String = sys.env.getOrElse("PGPASSWORD", "postgres")
  val pgDb: String       = sys.env.getOrElse("PGDATABASE", "postgres")

  val pgConnStr: String = s"jdbc:postgresql://$pgHost:$pgPort/$pgDb"

  lazy val pgAvailable: Boolean =
    try {
      Class.forName("org.postgresql.Driver")
      val conn = DriverManager.getConnection(pgConnStr, pgUser, pgPassword)
      conn.close()
      true
    } catch {
      case _: Throwable => false
    }

  def pgTransactor(): JdbcTransactor =
    new JdbcTransactor(() => DriverManager.getConnection(pgConnStr, pgUser, pgPassword), SqlDialect.PostgreSQL)
}
