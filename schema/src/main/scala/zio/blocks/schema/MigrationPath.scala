package zio.blocks.schema

final case class MigrationPath[+A](path: List[String]) {
  def append[B >: A](next: String): MigrationPath[B] = copy(path :+ next)
}

object MigrationPath {
  def apply[A](head: String, tail: String*): MigrationPath[A] =
    MigrationPath(head :: tail.toList)
}