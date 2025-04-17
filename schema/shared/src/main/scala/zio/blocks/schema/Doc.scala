package zio.blocks.schema

import scala.collection.immutable.ArraySeq

sealed trait Doc {
  def +(that: Doc): Doc = Doc.Concat(this, that)

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

  case class Text(value: String) extends Leaf {
    lazy val flatten: Seq[Leaf] = ArraySeq(this)

    override def hashCode: Int = value.hashCode

    override def equals(that: Any): Boolean = that match {
      case that: Text => value.equals(that.value)
      case that: Doc  => flatten.equals(that.flatten)
      case _          => false
    }
  }

  case class Concat(left: Doc, right: Doc) extends Doc {
    lazy val flatten: Seq[Leaf] = left.flatten ++ right.flatten
  }

  def apply(value: String): Doc = Text(value)
}
