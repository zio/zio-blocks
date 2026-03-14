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

  // ZLayer for dependency injection
  def layer(url: String, dialect: SqlDialect): ZLayer[Any, Nothing, TransactorZIO] =
    ZLayer.succeed(fromUrl(url, dialect))
}
