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

  private val longCodec: DbCodec[Long] = new DbCodec[Long] {
    val columns: IndexedSeq[String]                                           = IndexedSeq("count")
    def readValue(reader: DbResultReader, startIndex: Int): Long              = reader.getLong(startIndex)
    def writeValue(writer: DbParamWriter, startIndex: Int, value: Long): Unit =
      writer.setLong(startIndex, value)
    def toDbValues(value: Long): IndexedSeq[DbValue] = IndexedSeq(DbValue.DbLong(value))
  }

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
    val frag = Frag.const(s"SELECT COUNT(*) FROM $tbl")
    SqlOps.queryOne[Long](frag)(using con, longCodec).getOrElse(0L)
  }

  // === Write Operations ===

  def insert(entity: E)(using con: DbCon): Int = {
    val values = codec.toDbValues(entity)
    val frag   = Repo.buildInsertFrag(tbl, allCols, values)
    SqlOps.update(frag)(using con)
  }

  // Inserts the entity and returns it by re-reading from the database using its ID.
  // Note: for auto-generated IDs, use RETURNING clause or getGeneratedKeys instead.
  def insertReturning(entity: E)(using con: DbCon): E = {
    insert(entity)
    findById(getId(entity)).getOrElse(
      throw new NoSuchElementException(s"Entity not found after insert in table $tbl")
    )
  }

  def insertAll(entities: Iterable[E])(using con: DbCon): Int = {
    if (entities.isEmpty) return 0
    val first  = entities.head
    val values = codec.toDbValues(first)
    val sqlStr = Repo.buildInsertFrag(tbl, allCols, values).sql(con.dialect)
    val start  = System.nanoTime()
    try {
      val ps = con.connection.prepareStatement(sqlStr)
      try {
        entities.foreach { entity =>
          val vals = codec.toDbValues(entity)
          SqlOps.writeParams(ps.paramWriter, vals)
          ps.addBatch()
        }
        val counts   = ps.executeBatch()
        val total    = counts.sum
        val duration = java.time.Duration.ofNanos(System.nanoTime() - start)
        con.logger.onSuccess(SqlLogger.SuccessEvent(sqlStr, IndexedSeq.empty, duration, total))
        total
      } finally ps.close()
    } catch {
      case e: Throwable =>
        val duration = java.time.Duration.ofNanos(System.nanoTime() - start)
        con.logger.onError(SqlLogger.ErrorEvent(sqlStr, IndexedSeq.empty, duration, e))
        throw e
    }
  }

  def update(entity: E)(using con: DbCon): Int = {
    val entityValues = codec.toDbValues(entity)
    val idValues     = idCodec.toDbValues(getId(entity))
    val frag         = Repo.buildUpdateFrag(tbl, table.columns, entityValues, idColumn, idValues)
    SqlOps.update(frag)(using con)
  }

  def deleteById(id: ID)(using con: DbCon): Int = {
    val frag = Frag(
      IndexedSeq(s"DELETE FROM $tbl WHERE $idColumn = ", ""),
      idCodec.toDbValues(id)
    )
    SqlOps.update(frag)(using con)
  }

  def delete(entity: E)(using con: DbCon): Int =
    deleteById(getId(entity))

  def truncate()(using con: DbCon): Int =
    SqlOps.update(Frag.const(s"DELETE FROM $tbl"))(using con)
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
