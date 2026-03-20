package zio.schema

trait Migration[A, B] {
  def migrate(a: A): B
}
