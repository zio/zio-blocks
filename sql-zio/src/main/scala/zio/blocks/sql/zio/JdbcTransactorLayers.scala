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

import javax.sql.DataSource

import zio.*
import zio.blocks.sql.*

/**
 * Layers of the synchronous [[zio.blocks.sql.Transactor]], built from a
 * `DataSource` in the environment, for code that uses `Transactor` inside a ZIO
 * application. They extend the `JdbcTransactor` companion, so they need
 * `import zio.blocks.sql.zio._`.
 *
 * {{{
 * import zio._
 * import zio.blocks.sql._
 *
 * val program: ZIO[Transactor, Throwable, Int] =
 *   ZIO.serviceWithZIO[Transactor](xa => ZIO.attemptBlocking(xa.connect(sql"SELECT 1 + 1".queryOne[Int].getOrElse(0))))
 *
 * def runnable(dataSource: javax.sql.DataSource): Task[Int] =
 *   program.provide(JdbcTransactor.sqliteLayer, ZLayer.succeed(dataSource))
 * // with an org.sqlite.SQLiteDataSource for "jdbc:sqlite::memory:", runnable succeeds with 2
 * }}}
 *
 * @see
 *   [[https://zioblocks.com/reference/sql-zio/ ZIO Integration]]
 */
extension (companion: JdbcTransactor.type) {

  /**
   * A layer that turns a `DataSource` into
   * `JdbcTransactor.postgres(dataSource)`.
   */
  def postgresLayer: ZLayer[DataSource, Nothing, Transactor] =
    ZLayer.fromFunction((dataSource: DataSource) =>
      JdbcTransactor.fromDataSource(dataSource, SqlDialect.PostgreSQL): Transactor
    )

  /**
   * A layer that turns a `DataSource` into `JdbcTransactor.sqlite(dataSource)`.
   */
  def sqliteLayer: ZLayer[DataSource, Nothing, Transactor] =
    ZLayer.fromFunction((dataSource: DataSource) =>
      JdbcTransactor.fromDataSource(dataSource, SqlDialect.SQLite): Transactor
    )

}
