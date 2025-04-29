package zio.blocks.schema

import scala.collection.immutable.ArraySeq

sealed trait Doc {
  def +(that: Doc): Doc = Doc.Concat(this.flatten.toVector ++ that.flatten.toVector)

  def flatten: Seq[Doc.Leaf]

  override def hashCode: Int = flatten.hashCode

  override def equals(that: Any): Boolean = that match {
    case that: Doc => flatten.equals(that.flatten)
    case _         => false
  }
}

object Doc {
  sealed trait Leaf extends Doc

  case object Empty extends Leaf {
    def flatten: Seq[Leaf] = Nil
  }

  final case class Text(value: String) extends Leaf {
    lazy val flatten: Seq[Leaf] = ArraySeq(this)

    override def hashCode: Int = value.hashCode

    override def equals(that: Any): Boolean = that match {
      case that: Text => value.equals(that.value)
      case that: Doc  => flatten.equals(that.flatten)
      case _          => false
    }
  }

  final case class Concat(flatten: Vector[Leaf]) extends Doc
  object Concat {
    def apply(docs: Doc*): Concat = new Concat(docs.toVector.flatMap(_.flatten))
  }

  def apply(value: String): Doc = Text(value)
}
