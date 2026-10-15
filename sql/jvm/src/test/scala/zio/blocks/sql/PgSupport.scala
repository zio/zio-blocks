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
 * default port 32886, graceful skip).
 *
 * '''CI gating:''' When `CI=true` or `GITHUB_ACTIONS=true` and PostgreSQL is
 * unreachable, `pgGate` returns `Left(failMessage)` so callers can fail the
 * suite instead of silently skipping. Outside CI the same situation returns
 * `Left(skipMessage)` for a graceful skip.
 */
object PgSupport {
  val pgHost: String     = sys.env.getOrElse("PGHOST", "localhost")
  val pgPort: Int        = sys.env.getOrElse("PGPORT", "32886").toInt
  val pgUser: String     = sys.env.getOrElse("PGUSER", "postgres")
  val pgPassword: String = sys.env.getOrElse("PGPASSWORD", "postgres")
  val pgDb: String       = sys.env.getOrElse("PGDATABASE", "postgres")

  val pgConnStr: String = s"jdbc:postgresql://$pgHost:$pgPort/$pgDb"

  /** True when CI=true or GITHUB_ACTIONS=true. */
  val inCI: Boolean =
    sys.env.getOrElse("CI", "").toLowerCase == "true" ||
      sys.env.getOrElse("GITHUB_ACTIONS", "").toLowerCase == "true"

  lazy val pgAvailable: Boolean =
    try {
      Class.forName("org.postgresql.Driver")
      val conn = DriverManager.getConnection(pgConnStr, pgUser, pgPassword)
      conn.close()
      true
    } catch {
      case _: Throwable => false
    }

  /**
   * Returns `Right(pgTransactor)` when PostgreSQL is reachable, `Left(message)`
   * otherwise. The message distinguishes CI (must-fail) from local (graceful
   * skip).
   */
  def pgGate: Either[String, JdbcTransactor] =
    if (pgAvailable) Right(pgTransactor())
    else if (inCI)
      Left(
        s"FAIL: PostgreSQL is required in CI (CI=true) but unavailable at $pgConnStr. " +
          "Set PGHOST/PGPORT/PGUSER/PGPASSWORD/PGDATABASE to a reachable instance."
      )
    else
      Left(
        s"SKIPPED: PostgreSQL not available at $pgConnStr — " +
          "set PGHOST/PGPORT/PGUSER/PGPASSWORD/PGDATABASE to run these tests."
      )

  def pgTransactor(): JdbcTransactor =
    new JdbcTransactor(() => DriverManager.getConnection(pgConnStr, pgUser, pgPassword), SqlDialect.PostgreSQL)
}
