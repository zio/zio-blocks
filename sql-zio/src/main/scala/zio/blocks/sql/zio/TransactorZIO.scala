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

import zio._
import zio.blocks.sql._

class TransactorZIO(underlying: Transactor) {

  def connect[A](f: DbCon ?=> A): Task[A] =
    ZIO.attemptBlocking(underlying.connect(f))

  def transact[A](f: DbTx ?=> A): Task[A] =
    ZIO.attemptBlocking(underlying.transact(f))
}

object TransactorZIO {
  def fromTransactor(transactor: Transactor): TransactorZIO =
    new TransactorZIO(transactor)

  def fromUrl(url: String, dialect: SqlDialect): TransactorZIO =
    new TransactorZIO(JdbcTransactor.fromUrl(url, dialect))

  def fromUrl(
    url: String,
    user: String,
    password: String,
    dialect: SqlDialect
  ): TransactorZIO =
    new TransactorZIO(JdbcTransactor.fromUrl(url, user, password, dialect))

  def fromDataSource(dataSource: javax.sql.DataSource, dialect: SqlDialect): TransactorZIO =
    new TransactorZIO(JdbcTransactor.fromDataSource(dataSource, dialect))

  // ZLayer for dependency injection
  def layer(url: String, dialect: SqlDialect): ZLayer[Any, Nothing, TransactorZIO] =
    ZLayer.succeed(fromUrl(url, dialect))
}
