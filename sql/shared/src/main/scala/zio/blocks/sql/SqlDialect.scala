package zio.blocks.sql

sealed trait SqlDialect {
  def name: String
  def typeName(dbValue: DbValue): String
  def paramPlaceholder(index: Int): String
}

object SqlDialect {
  case object PostgreSQL extends SqlDialect {
    val name: String = "PostgreSQL"

    def typeName(dbValue: DbValue): String = dbValue match {
      case DbValue.DbNull             => "NULL"
      case _: DbValue.DbInt           => "INTEGER"
      case _: DbValue.DbLong          => "BIGINT"
      case _: DbValue.DbDouble        => "DOUBLE PRECISION"
      case _: DbValue.DbFloat         => "REAL"
      case _: DbValue.DbBoolean       => "BOOLEAN"
      case _: DbValue.DbString        => "TEXT"
      case _: DbValue.DbBigDecimal    => "NUMERIC"
      case _: DbValue.DbBytes         => "BYTEA"
      case _: DbValue.DbShort         => "SMALLINT"
      case _: DbValue.DbByte          => "SMALLINT"
      case _: DbValue.DbChar          => "CHAR(1)"
      case _: DbValue.DbLocalDate     => "DATE"
      case _: DbValue.DbLocalDateTime => "TIMESTAMP"
      case _: DbValue.DbLocalTime     => "TIME"
      case _: DbValue.DbInstant       => "TIMESTAMPTZ"
      case _: DbValue.DbDuration      => "INTERVAL"
      case _: DbValue.DbUUID          => "UUID"
    }

    def paramPlaceholder(index: Int): String = s"$$$index"
  }

  case object SQLite extends SqlDialect {
    val name: String = "SQLite"

    def typeName(dbValue: DbValue): String = dbValue match {
      case DbValue.DbNull             => "NULL"
      case _: DbValue.DbInt           => "INTEGER"
      case _: DbValue.DbLong          => "INTEGER"
      case _: DbValue.DbDouble        => "REAL"
      case _: DbValue.DbFloat         => "REAL"
      case _: DbValue.DbBoolean       => "INTEGER"
      case _: DbValue.DbString        => "TEXT"
      case _: DbValue.DbBigDecimal    => "TEXT"
      case _: DbValue.DbBytes         => "BLOB"
      case _: DbValue.DbShort         => "INTEGER"
      case _: DbValue.DbByte          => "INTEGER"
      case _: DbValue.DbChar          => "TEXT"
      case _: DbValue.DbLocalDate     => "TEXT"
      case _: DbValue.DbLocalDateTime => "TEXT"
      case _: DbValue.DbLocalTime     => "TEXT"
      case _: DbValue.DbInstant       => "TEXT"
      case _: DbValue.DbDuration      => "TEXT"
      case _: DbValue.DbUUID          => "TEXT"
    }

    def paramPlaceholder(index: Int): String = "?"
  }
}
