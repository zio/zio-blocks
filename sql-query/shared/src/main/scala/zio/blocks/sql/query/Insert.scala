package zio.blocks.sql.query

import zio.blocks.sql._

case class InsertBuilder[A](
  table: Table[A],
  valuesList: IndexedSeq[A] = IndexedSeq.empty
) {
  
  def values(vs: A*): InsertBuilder[A] =
    this.copy(valuesList = valuesList ++ vs)

  def toFrag: Frag = {
    if (valuesList.isEmpty) throw new IllegalStateException("INSERT requires at least one value")
    
    val columns = table.columns.mkString(", ")
    
    val valueFrags = valuesList.map { v =>
      val dbValues = table.codec.toDbValues(v)
      // Frag generation needs to interleave.
      // E.g. (?, ?, ?) -> parts = ("", ", ", ", ", ""), params = (a, b, c)
      val parts = IndexedSeq("(") ++ IndexedSeq.fill(dbValues.size - 1)(", ") ++ IndexedSeq(")")
      Frag(parts, dbValues)
    }
    
    val combinedValues = valueFrags.reduce(_ ++ Frag.const(", ") ++ _)
    
    Frag.const(s"INSERT INTO ${table.name} ($columns) VALUES ") ++ combinedValues
  }
}
