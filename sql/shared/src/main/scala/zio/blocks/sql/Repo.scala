package zio.blocks.sql

class Repo[E, ID](
  val table: Table[E],
  val idColumn: String,
  val idCodec: DbCodec[ID],
  val getId: E => ID
) {

  private val allCols: String   = table.columns.mkString(", ")
  private val tbl: String       = table.name
  private val codec: DbCodec[E] = table.codec

  // === Read Operations ===

  def findAll(using con: DbCon): List[E] = {
    val frag = Frag.const(s"SELECT $allCols FROM $tbl")
    SqlOps.query[E](frag)(using con, codec)
  }

  def findById(id: ID)(using con: DbCon): Option[E] = {
    val frag = Frag(
      IndexedSeq(s"SELECT $allCols FROM $tbl WHERE $idColumn = ", ""),
      idCodec.toDbValues(id)
    )
    SqlOps.queryOne[E](frag)(using con, codec)
  }

  def existsById(id: ID)(using con: DbCon): Boolean =
    findById(id).isDefined

  def count(using con: DbCon): Long = {
    val ps = con.connection.prepareStatement(s"SELECT COUNT(*) FROM $tbl")
    try {
      val rs = ps.executeQuery()
      try {
        if (rs.next()) rs.reader.getLong(1) else 0L
      } finally rs.close()
    } finally ps.close()
  }

  // === Write Operations ===

  def insert(entity: E)(using con: DbCon): Unit = {
    val values = codec.toDbValues(entity)
    val frag   = Repo.buildInsertFrag(tbl, allCols, values)
    SqlOps.update(frag)(using con)
    ()
  }

  def update(entity: E)(using con: DbCon): Unit = {
    val entityValues = codec.toDbValues(entity)
    val idValues     = idCodec.toDbValues(getId(entity))
    val frag         = Repo.buildUpdateFrag(tbl, table.columns, entityValues, idColumn, idValues)
    SqlOps.update(frag)(using con)
    ()
  }

  def deleteById(id: ID)(using con: DbCon): Unit = {
    val frag = Frag(
      IndexedSeq(s"DELETE FROM $tbl WHERE $idColumn = ", ""),
      idCodec.toDbValues(id)
    )
    SqlOps.update(frag)(using con)
    ()
  }

  def delete(entity: E)(using con: DbCon): Unit =
    deleteById(getId(entity))

  def truncate()(using con: DbCon): Unit = {
    SqlOps.update(Frag.const(s"DELETE FROM $tbl"))(using con)
    ()
  }
}

object Repo {

  def apply[E, ID](
    table: Table[E],
    idColumn: String,
    idCodec: DbCodec[ID],
    getId: E => ID
  ): Repo[E, ID] = new Repo(table, idColumn, idCodec, getId)

  private[sql] def buildInsertFrag(
    tableName: String,
    allColumns: String,
    values: IndexedSeq[DbValue]
  ): Frag =
    if (values.isEmpty) Frag.const(s"INSERT INTO $tableName ($allColumns) VALUES ()")
    else {
      val parts =
        IndexedSeq(s"INSERT INTO $tableName ($allColumns) VALUES (") ++
          IndexedSeq.fill(values.size - 1)(", ") :+
          ")"
      Frag(parts, values)
    }

  private[sql] def buildUpdateFrag(
    tableName: String,
    columns: IndexedSeq[String],
    entityValues: IndexedSeq[DbValue],
    idColumn: String,
    idValues: IndexedSeq[DbValue]
  ): Frag = {
    val allValues = entityValues ++ idValues
    val partsB    = IndexedSeq.newBuilder[String]

    partsB += s"UPDATE $tableName SET ${columns(0)} = "

    var i = 1
    while (i < columns.size) {
      partsB += s", ${columns(i)} = "
      i += 1
    }

    partsB += s" WHERE $idColumn = "
    partsB += ""

    Frag(partsB.result(), allValues)
  }
}
