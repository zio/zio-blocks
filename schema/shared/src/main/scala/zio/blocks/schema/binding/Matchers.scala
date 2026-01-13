package zio.blocks.schema.binding

final case class Matchers[+A](matchers: IndexedSeq[Matcher[? <: A]]) {
  def apply(index: Int): Matcher[? <: A] = matchers(index)
}

object Matchers {
  def apply[A](matchers: Matcher[? <: A]*): Matchers[A] = new Matchers(matchers.toIndexedSeq)
}
