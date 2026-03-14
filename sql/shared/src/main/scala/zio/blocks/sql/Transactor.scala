package zio.blocks.sql

trait Transactor {
  def connect[A](f: DbCon ?=> A): A
  def transact[A](f: DbTx ?=> A): A
}
