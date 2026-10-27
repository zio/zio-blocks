package migratetablerowsonline

import zio.blocks.sql.*

/**
 * How to Migrate Table Rows to a New Version Online — Shared PostgreSQL Setup
 *
 * The transactor for the `shop` database, the cleanup that lets the examples
 * run again, and the generated customers of the large table. It has no main
 * method.
 */
def shopTransactor(): JdbcTransactor =
  JdbcTransactor.fromUrl(
    sys.env.getOrElse("SHOP_DB_URL", "jdbc:postgresql://localhost:5432/shop"),
    sys.env.getOrElse("SHOP_DB_USER", "shop"),
    sys.env.getOrElse("SHOP_DB_PASSWORD", "shop"),
    SqlDialect.PostgreSQL
  )

def resetShop()(using DbCon): Unit = {
  Frag.literal("DROP TABLE IF EXISTS customers, customers_v2, customers_old_v2, customers_split_q CASCADE").update
  Frag.literal("DROP FUNCTION IF EXISTS customers_split_q_notify() CASCADE").update
}

val firstNames = Vector("Ada", "Alan", "Grace", "Edsger", "Barbara", "Donald", "Margaret", "Ken")
val lastNames  = Vector("Lovelace", "Turing", "Hopper", "Dijkstra", "Liskov", "Knuth", "Hamilton", "Thompson")

def sampleCustomer(id: Long): CustomerV1 =
  CustomerV1(id, s"${firstNames(((id - 1) % 8).toInt)} ${lastNames(((id - 1) / 8 % 8).toInt)}", s"customer$id@example.com")
