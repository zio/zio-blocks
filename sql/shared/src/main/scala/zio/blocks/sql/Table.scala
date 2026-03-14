package zio.blocks.sql

import zio.blocks.schema._

final case class Table[A](name: String, codec: DbCodec[A], dialect: SqlDialect) {
  def columns: IndexedSeq[String] = codec.columns

  def createTable: Frag = {
    val columnDefs = codec.columns.map { col =>
      ColumnDef(col, dialect.typeName(DbValue.DbString("")), nullable = false)
    }
    Ddl.createTable(name, columnDefs)
  }

  def dropTable: Frag = Ddl.dropTable(name)
}

object Table {

  def derived[A](dialect: SqlDialect)(implicit schema: Schema[A]): Table[A] = {
    val codec     = schema.deriving(DbCodecDeriver).derive
    val tableName = deriveTableName(schema)
    Table(tableName, codec, dialect)
  }

  private def deriveTableName[A](schema: Schema[A]): String = {
    val configured = schema.reflect.modifiers.collectFirst { case Modifier.config("sql.table_name", value) =>
      value
    }
    configured.getOrElse {
      val typeName = schema.reflect.typeId.name
      pluralize(SqlNameMapper.SnakeCase(typeName))
    }
  }

  private[sql] def pluralize(s: String): String =
    if (s.isEmpty) s
    else if (s.endsWith("s") || s.endsWith("x") || s.endsWith("ch") || s.endsWith("sh") || s.endsWith("zz"))
      s + "es"
    else if (s.endsWith("z")) s + "zes" // quiz -> quizzes
    else if (s.endsWith("y") && s.length > 1 && !isVowel(s.charAt(s.length - 2))) s.dropRight(1) + "ies"
    else s + "s"

  private def isVowel(c: Char): Boolean = "aeiouAEIOU".indexOf(c) >= 0
}
