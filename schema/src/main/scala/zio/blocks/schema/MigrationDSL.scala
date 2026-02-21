package zio.blocks.schema

object MigrationDSL {
  def apply(): MigrationDSL = new MigrationDSL()
}

class MigrationDSL {
  def createTable(tableName: String): CreateTableCommand = new CreateTableCommand(tableName)
}

class CreateTableCommand(val tableName: String) {
  def column(name: String, dataType: String): ColumnDefinition = new ColumnDefinition(name, dataType)
}

class ColumnDefinition(val name: String, val dataType: String) {
  def primaryKey(): ColumnDefinition = this
  def notNull(): ColumnDefinition = this
}