package zio.blocks.sql

final case class Frag(parts: IndexedSeq[String], params: IndexedSeq[DbValue]) {

  def ++(other: Frag): Frag =
    if (parts.isEmpty) other
    else if (other.parts.isEmpty) this
    else {
      val mergedParts = parts.init ++ IndexedSeq(parts.last + other.parts.head) ++ other.parts.tail
      Frag(mergedParts, params ++ other.params)
    }

  def sql(dialect: SqlDialect): String = {
    val sb       = new StringBuilder
    var paramIdx = 1
    var i        = 0
    while (i < parts.length) {
      sb.append(parts(i))
      if (i < params.length) {
        sb.append(dialect.paramPlaceholder(paramIdx))
        paramIdx += 1
      }
      i += 1
    }
    sb.toString()
  }

  def queryParams: IndexedSeq[DbValue] = params

  def isEmpty: Boolean = parts.forall(_.isEmpty) && params.isEmpty
}

object Frag {
  val empty: Frag = Frag(IndexedSeq(""), IndexedSeq.empty)

  def const(sqlStr: String): Frag = Frag(IndexedSeq(sqlStr), IndexedSeq.empty)
}
