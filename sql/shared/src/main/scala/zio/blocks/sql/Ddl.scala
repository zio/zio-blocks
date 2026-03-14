package zio.blocks.sql

final case class ColumnDef(name: String, sqlType: String, nullable: Boolean)

object Ddl {

  def createTable(tableName: String, columns: IndexedSeq[ColumnDef]): Frag = {
    val colDefs = columns.map { col =>
      val nullStr = if (col.nullable) "" else " NOT NULL"
      s"  ${col.name} ${col.sqlType}$nullStr"
    }
    Frag.const(s"CREATE TABLE IF NOT EXISTS $tableName (\n${colDefs.mkString(",\n")}\n)")
  }

  def dropTable(tableName: String): Frag =
    Frag.const(s"DROP TABLE IF EXISTS $tableName")
}
