package migratetablerowsonline

import zio.blocks.schema.*
import zio.blocks.schema.migration.Migration
import zio.blocks.data.migration.*
import zio.blocks.sql.*

/**
 * How to Migrate Table Rows to a New Version Online — Shared Model
 *
 * The old and new customer rows, their repositories on the `customers` table,
 * the migrations that split `fullName` into `firstName` and `lastName`, and the
 * DDL of the old table and the startup migration that adds the new columns. It
 * has no main method.
 */
final case class CustomerV1(id: Long, fullName: String, email: String)

object CustomerV1 extends CompanionOptics[CustomerV1] {
  implicit val schema: Schema[CustomerV1] = Schema.derived

  val fullName: Lens[CustomerV1, String] = $(_.fullName)
}

final case class CustomerV2(id: Long, firstName: String, lastName: String, email: String)

object CustomerV2 {
  implicit val schema: Schema[CustomerV2] = Schema.derived
}

val customersV1 = Repo.derived[CustomerV1, Long]("customers", "id", _.id)
val customersV2 = Repo.derived[CustomerV2, Long]("customers", "id", _.id)

val fullName   = SchemaExpr.optic[CustomerV1, String](CustomerV1.fullName.toDynamic, CustomerV1.schema)
val firstSpace = SchemaExpr.stringIndexOf(fullName, SchemaExpr.literal(" "))

val splitName: Migration[CustomerV1, CustomerV2] =
  Migration
    .newBuilder[CustomerV1, CustomerV2]
    .addField(_.firstName, SchemaExpr.stringSubstring(fullName, SchemaExpr.literal(0), firstSpace))
    .addField(
      _.lastName,
      SchemaExpr.stringTrim(SchemaExpr.stringSubstring(fullName, firstSpace, SchemaExpr.stringLength(fullName)))
    )
    .dropField(_.fullName, SchemaExpr.literal(""))
    .build

val paddedName  = SchemaExpr.stringConcat(fullName, SchemaExpr.literal(" "))
val paddedSpace = SchemaExpr.stringIndexOf(paddedName, SchemaExpr.literal(" "))

val splitNameSafely: Migration[CustomerV1, CustomerV2] =
  Migration
    .newBuilder[CustomerV1, CustomerV2]
    .addField(_.firstName, SchemaExpr.stringSubstring(paddedName, SchemaExpr.literal(0), paddedSpace))
    .addField(
      _.lastName,
      SchemaExpr.stringTrim(SchemaExpr.stringSubstring(paddedName, paddedSpace, SchemaExpr.stringLength(paddedName)))
    )
    .dropField(_.fullName, SchemaExpr.literal(""))
    .build

def createCustomersV1()(using DbCon): Unit =
  Frag
    .literal("CREATE TABLE customers (id BIGINT PRIMARY KEY, full_name TEXT NOT NULL, email TEXT NOT NULL)")
    .update

class AddNameColumns(transactor: Transactor) extends TinyMigrator(transactor) {
  def run()(using DbTx): Unit = {
    Frag.literal("ALTER TABLE customers ADD COLUMN first_name TEXT").update
    Frag.literal("ALTER TABLE customers ADD COLUMN last_name TEXT").update
  }
}
