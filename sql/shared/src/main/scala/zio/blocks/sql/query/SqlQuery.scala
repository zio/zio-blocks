/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.blocks.sql.query

import zio.blocks.sql.{DbValue, Frag, SqlDialect, SqlIdentifier, Table}

/**
 * An immutable `SELECT` query over a [[Table]], built step by step and rendered
 * to a [[Frag]]. Start with `SqlQuery.from(table)`, add joins along [[Rel]]s,
 * `WHERE` conditions, grouping, sorting and paging, then render it with
 * [[toFrag]] and run the `Frag` with `Frag#query` inside a
 * [[zio.blocks.sql.Transactor]].
 *
 * The `FROM` table has the alias `t0` and each join the next one, `t1`, `t2`
 * and so on, in the order the joins were added. The `SELECT` lists every column
 * of every table in that order, so a joined row decodes as a tuple with one row
 * type per table. Table and column names are written in double quotes; a
 * condition is written as given.
 *
 * {{{
 * import zio.blocks.schema.Schema
 * import zio.blocks.sql.query._
 *
 * case class Product(sku: String, name: String, priceCents: Long)
 * object Product { implicit val schema: Schema[Product] = Schema.derived }
 * case class OrderLine(lineId: Int, productSku: String, quantity: Int)
 * object OrderLine { implicit val schema: Schema[OrderLine] = Schema.derived }
 *
 * val products = Table.derived[Product]
 * val lines    = Table.derived[OrderLine]
 *
 * val bulkLines = SqlQuery
 *   .from(lines)
 *   .join(Rel.manyToOne(lines, _.productSku, products, _.sku))
 *   .where(sql"t0.quantity >= \${3}")
 *
 * val sqlText = bulkLines.sql(SqlDialect.SQLite)
 * // SELECT t0."line_id", t0."product_sku", t0."quantity", t1."sku", t1."name",
 * //   t1."price_cents" FROM "order_line" AS t0 INNER JOIN "product" AS t1
 * //   ON t0."product_sku" = t1."sku" WHERE t0.quantity >= ?
 *
 * val xa = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
 * val rows = xa.connect {
 *   products.createTable(SqlDialect.SQLite).update
 *   lines.createTable(SqlDialect.SQLite).update
 *   sql"INSERT INTO product VALUES ('MUG-1', 'Mug', 1200), ('POT-1', 'Teapot', 3500)".update
 *   sql"INSERT INTO order_line VALUES (1, 'MUG-1', 4), (2, 'POT-1', 1)".update
 *   bulkLines.toFrag(SqlDialect.SQLite).query[(OrderLine, Product)]
 * }
 * // List((OrderLine(1,MUG-1,4),Product(MUG-1,Mug,1200)))
 * }}}
 *
 * When two joined tables have a column with the same name, such as `id`, every
 * row type in the tuple reads the first column with that name, a known bug in
 * 0.0.56; give key columns names that differ across tables.
 *
 * @see
 *   [[https://zioblocks.com/reference/sql/sql-query/ SqlQuery]]
 */
final case class SqlQuery[A] private[query] (
  source: Table[A],
  joins: Vector[JoinNode],
  filters: Vector[Frag],
  groupBy: Vector[String],
  having: Option[Frag],
  orderBy: Vector[OrderBy],
  limit: Option[Int],
  offset: Option[Int]
) {

  /**
   * Adds an `INNER JOIN` along `rel`, which keeps only rows with a match in
   * both tables. The side of `rel` already in the query keeps its alias, and
   * the other table gets the next one; when both tables are the same, the
   * condition compares `t0` with the new alias.
   *
   * When neither table of `rel` is in the query yet, `rel.toTable` is joined
   * with the next alias and the condition reads `rel.fkColumn` from `t0`, so
   * `rel.fromTable` never appears in the SQL, a known bug in 0.0.56: the query
   * fails when it runs unless `t0` happens to have that column. Join a table of
   * `rel` first.
   */
  def innerJoin[From, To](rel: Rel[From, To]): SqlQuery[A] =
    addJoin(rel, JoinKind.Inner)

  /**
   * Adds a `LEFT JOIN` along `rel`, which keeps every row of the query so far;
   * the joined table's columns are SQL `NULL` where nothing matches. Read them
   * into a row type whose fields are `Option`s: a plain field reads `NULL` as
   * `0` or `null`.
   */
  def leftJoin[From, To](rel: Rel[From, To]): SqlQuery[A] =
    addJoin(rel, JoinKind.Left)

  /** Adds a join of `kind` along `rel`, for a join kind chosen at run time. */
  def join[From, To](rel: Rel[From, To], kind: JoinKind): SqlQuery[A] =
    addJoin(rel, kind)

  /**
   * The same as [[innerJoin]].
   *
   * {{{
   * import zio.blocks.schema.Schema
   * import zio.blocks.sql.query._
   *
   * case class Customer(customerId: Int, name: String)
   * object Customer { implicit val schema: Schema[Customer] = Schema.derived }
   * case class Invoice(invoiceId: Int, buyerId: Int, totalCents: Long)
   * object Invoice { implicit val schema: Schema[Invoice] = Schema.derived }
   *
   * val customers = Table.derived[Customer]
   * val invoices  = Table.derived[Invoice]
   *
   * val sqlText = SqlQuery
   *   .from(customers)
   *   .join(Rel.oneToMany(invoices, _.buyerId, customers, _.customerId))
   *   .sql(SqlDialect.PostgreSQL)
   * // SELECT t0."customer_id", t0."name", t1."invoice_id", t1."buyer_id",
   * //   t1."total_cents" FROM "customer" AS t0 INNER JOIN "invoice" AS t1
   * //   ON t1."buyer_id" = t0."customer_id"
   * }}}
   */
  def join[From, To](rel: Rel[From, To]): SqlQuery[A] =
    addJoin(rel, JoinKind.Inner)

  /** The same as [[leftJoin]]. */
  def joinLeft[From, To](rel: Rel[From, To]): SqlQuery[A] =
    addJoin(rel, JoinKind.Left)

  private def addJoin[From, To](rel: Rel[From, To], kind: JoinKind): SqlQuery[A] = {
    val nextAlias  = s"t${joins.size + 1}"
    val isSelfJoin = rel.fromTable.name == rel.toTable.name
    @scala.annotation.nowarn
    val pair = if (isSelfJoin) {
      // self-join: t0."fk" = tN."pk"
      val fk = SqlIdentifier.validate("column", rel.fkColumn)
      val pk = SqlIdentifier.validate("column", rel.pkColumn)
      val on = Frag.literal(s"""t0."$fk" = $nextAlias."$pk"""")
      (rel.toTable.asInstanceOf[Table[_]], on)
    } else {
      val fromOpt          = aliasOf(rel.fromTable)
      val toOpt            = aliasOf(rel.toTable)
      var target: Table[_] = null.asInstanceOf[Table[_]]
      var fkAlias: String  = ""
      var pkAlias: String  = ""
      (fromOpt, toOpt) match {
        case (Some(fa), None) =>
          target = rel.toTable.asInstanceOf[Table[_]]
          fkAlias = fa
          pkAlias = nextAlias
        case (None, Some(ta)) =>
          target = rel.fromTable.asInstanceOf[Table[_]]
          fkAlias = nextAlias
          pkAlias = ta
        case (None, None) =>
          // Neither side is yet in query — attach fk side to last alias (or t0) and introduce to side as new.
          // Prefer treating fromTable as existing (t0/last) and toTable as new when source matches fromTable by name heuristic.
          // Fallback: attach from side as existing t0, target is toTable.
          // Determine by checking if source name equals fromTable name -> then from is existing.
          // Otherwise treat to as existing if source matches toTable.
          if (source.name == rel.fromTable.name) {
            target = rel.toTable.asInstanceOf[Table[_]]
            fkAlias = "t0"
            pkAlias = nextAlias
          } else if (source.name == rel.toTable.name) {
            target = rel.fromTable.asInstanceOf[Table[_]]
            fkAlias = nextAlias
            pkAlias = "t0"
          } else if (joins.nonEmpty && joins.last.table.name == rel.fromTable.name) {
            target = rel.toTable.asInstanceOf[Table[_]]
            fkAlias = joins.last.alias
            pkAlias = nextAlias
          } else if (joins.nonEmpty && joins.last.table.name == rel.toTable.name) {
            target = rel.fromTable.asInstanceOf[Table[_]]
            fkAlias = nextAlias
            pkAlias = joins.last.alias
          } else {
            // default: from is existing (t0), to is new
            target = rel.toTable.asInstanceOf[Table[_]]
            fkAlias = "t0"
            pkAlias = nextAlias
          }
        case (Some(_), Some(_)) =>
          // Both sides already present — treat as joining duplicate alias for target? Use from as existing and create new alias for target duplicate.
          target = rel.toTable.asInstanceOf[Table[_]]
          fkAlias = fromOpt.get
          pkAlias = nextAlias
      }
      val fk = SqlIdentifier.validate("column", rel.fkColumn)
      val pk = SqlIdentifier.validate("column", rel.pkColumn)
      val on = Frag.literal(s"""$fkAlias."$fk" = $pkAlias."$pk"""")
      (target, on)
    }
    val (targetTable, onFrag) = pair

    val node = JoinNode(targetTable, nextAlias, kind, onFrag)
    copy(joins = joins :+ node)
  }

  /** The same as [[where]]. */
  def filter(frag: Frag): SqlQuery[A] =
    copy(filters = filters :+ frag)

  /**
   * Adds a `WHERE` condition. Several conditions are joined with ` AND `
   * without parentheses, so a condition that contains `OR` matches more rows
   * than intended unless it is wrapped in parentheses itself, a known bug in
   * 0.0.56. Name columns with their alias, as in `t0.price_cents`.
   *
   * {{{
   * import zio.blocks.schema.Schema
   * import zio.blocks.sql.query._
   *
   * case class Product(sku: String, name: String, priceCents: Long)
   * object Product { implicit val schema: Schema[Product] = Schema.derived }
   *
   * val cheap = SqlQuery
   *   .from(Table.derived[Product])
   *   .where(sql"t0.price_cents < \${1000L}")
   *   .where(sql"(t0.name = \${"Cup"} OR t0.name = \${"Mug"})")
   * val explained = cheap.explain(SqlDialect.SQLite)
   * // SELECT t0."sku", t0."name", t0."price_cents" FROM "product" AS t0
   * //   WHERE t0.price_cents < ?1 AND (t0.name = ?2 OR t0.name = ?3)
   * // -- params: 1:Long, 2:String, 3:String
   * }}}
   */
  def where(frag: Frag): SqlQuery[A] = filter(frag)

  /**
   * Sets the `GROUP BY` columns, replacing any set before. Each name is a
   * column of the `FROM` table and renders as `t0."<column>"`; a column of a
   * joined table can't be named, a known bug in 0.0.56.
   *
   * {{{
   * import zio.blocks.schema.Schema
   * import zio.blocks.sql.query._
   *
   * case class OrderLine(lineId: Int, productSku: String, quantity: Int)
   * object OrderLine { implicit val schema: Schema[OrderLine] = Schema.derived }
   *
   * val popular = SqlQuery
   *   .from(Table.derived[OrderLine])
   *   .groupBy("product_sku")
   *   .having(sql"SUM(t0.quantity) > \${10}")
   *   .sql(SqlDialect.SQLite)
   * // SELECT t0."line_id", t0."product_sku", t0."quantity" FROM "order_line" AS t0
   * //   GROUP BY t0."product_sku" HAVING SUM(t0.quantity) > ?
   * }}}
   *
   * @throws java.lang.IllegalArgumentException
   *   if a name is not an SQL identifier
   */
  def groupBy(cols: String*): SqlQuery[A] = {
    cols.foreach(c => SqlIdentifier.validate("column", c))
    copy(groupBy = cols.toVector)
  }

  /** Sets the `HAVING` condition, replacing any set before. */
  def having(frag: Frag): SqlQuery[A] =
    copy(having = Some(frag))

  /**
   * Adds a sort key after any added before. `col` is a column of the `FROM`
   * table and renders as `t0."<col>"`; a column of a joined table can't be
   * named, a known bug in 0.0.56.
   *
   * {{{
   * import zio.blocks.schema.Schema
   * import zio.blocks.sql.query._
   *
   * case class Product(sku: String, name: String, priceCents: Long)
   * object Product { implicit val schema: Schema[Product] = Schema.derived }
   *
   * val sqlText = SqlQuery
   *   .from(Table.derived[Product])
   *   .orderBy("price_cents", SortOrder.Desc)
   *   .orderBy("name")
   *   .sql(SqlDialect.SQLite)
   * // SELECT t0."sku", t0."name", t0."price_cents" FROM "product" AS t0
   * //   ORDER BY t0."price_cents" DESC, t0."name" ASC
   * }}}
   *
   * @throws java.lang.IllegalArgumentException
   *   if `col` is not an SQL identifier
   */
  def orderBy(col: String, dir: SortOrder = SortOrder.Asc): SqlQuery[A] = {
    SqlIdentifier.validate("column", col)
    copy(orderBy = orderBy :+ OrderBy(col, dir))
  }

  /**
   * Adds several sort keys after any added before. Unlike [[orderBy]], the
   * column names are checked only when the query is rendered.
   */
  def orderByMany(cols: OrderBy*): SqlQuery[A] =
    copy(orderBy = orderBy ++ cols.toVector)

  /**
   * Sets `LIMIT n`, replacing any limit set before; `n` is written into the
   * SQL, not bound.
   *
   * {{{
   * import zio.blocks.schema.Schema
   * import zio.blocks.sql.query._
   *
   * case class Product(sku: String, name: String, priceCents: Long)
   * object Product { implicit val schema: Schema[Product] = Schema.derived }
   *
   * val thirdPage = SqlQuery
   *   .from(Table.derived[Product])
   *   .orderBy("sku")
   *   .limit(20)
   *   .offset(40)
   *   .sql(SqlDialect.SQLite)
   * // SELECT t0."sku", t0."name", t0."price_cents" FROM "product" AS t0
   * //   ORDER BY t0."sku" ASC LIMIT 20 OFFSET 40
   * }}}
   *
   * @throws java.lang.IllegalArgumentException
   *   if `n` is negative
   */
  def limit(n: Int): SqlQuery[A] = {
    require(n >= 0, "limit must be >= 0")
    copy(limit = Some(n))
  }

  /**
   * Sets `OFFSET n`, the number of rows to skip, replacing any offset set
   * before. Without [[limit]] it renders `OFFSET n` alone, which SQLite
   * rejects, a known bug in 0.0.56: on SQLite, always set a limit too.
   *
   * @throws java.lang.IllegalArgumentException
   *   if `n` is negative
   */
  def offset(n: Int): SqlQuery[A] = {
    require(n >= 0, "offset must be >= 0")
    copy(offset = Some(n))
  }

  /**
   * Renders the query to a [[Frag]] whose parameters are the values of its
   * conditions. Run it with `Frag#query` or `Frag#queryOne`; `SqlQuery` has no
   * method that runs it.
   *
   * {{{
   * import zio.blocks.schema.Schema
   * import zio.blocks.sql.query._
   *
   * case class Product(sku: String, name: String, priceCents: Long)
   * object Product { implicit val schema: Schema[Product] = Schema.derived }
   *
   * val products = Table.derived[Product]
   * val priciest = SqlQuery.from(products).orderBy("price_cents", SortOrder.Desc).limit(1)
   *
   * val xa = JdbcTransactor.fromUrl("jdbc:sqlite::memory:", SqlDialect.SQLite)
   * val top = xa.connect {
   *   products.createTable(SqlDialect.SQLite).update
   *   sql"INSERT INTO product VALUES ('MUG-1', 'Mug', 1200), ('POT-1', 'Teapot', 3500)".update
   *   priciest.toFrag(SqlDialect.SQLite).query[Product]
   * }
   * // List(Product(POT-1,Teapot,3500))
   * }}}
   */
  def toFrag(dialect: SqlDialect): Frag = QueryRenderer.render(this, dialect)

  /**
   * Returns the SQL that [[toFrag]] sends to the database, with `?`
   * placeholders.
   */
  def sql(dialect: SqlDialect): String = toFrag(dialect).sql(dialect)

  /**
   * Returns the SQL for logs and review: the statement on one line with the
   * placeholders numbered `?1`, `?2`, ... in bind order, then a second line
   * `-- params: 1:Long, 2:String`, listing each parameter's position and type
   * but not its value (`-- params: (none)` when there are none).
   */
  def explain(dialect: SqlDialect): String = {
    val frag = toFrag(dialect)
    val sb   = new StringBuilder
    var idx  = 1
    var i    = 0
    while (i < frag.parts.length) {
      sb.append(frag.parts(i))
      if (i < frag.params.length) {
        sb.append(s"?$idx")
        idx += 1
      }
      i += 1
    }
    val sql = sb.toString()
    if (frag.params.isEmpty) s"$sql\n-- params: (none)"
    else {
      val types = frag.params.zipWithIndex.map { case (v, n) => s"${n + 1}:${SqlQuery.typeLabel(v)}" }.mkString(", ")
      s"$sql\n-- params: $types"
    }
  }

  private def aliasOf(table: Table[_]): Option[String] =
    if (table.name == source.name) Some("t0")
    else joins.find(_.table.name == table.name).map(_.alias)
}

/** Starts a [[SqlQuery]]. */
object SqlQuery {

  /**
   * Returns a query that selects every column of `table`, with the alias `t0`.
   *
   * {{{
   * import zio.blocks.schema.Schema
   * import zio.blocks.sql.query._
   *
   * case class Product(sku: String, name: String, priceCents: Long)
   * object Product { implicit val schema: Schema[Product] = Schema.derived }
   *
   * val sqlText = SqlQuery.from(Table.derived[Product]).sql(SqlDialect.SQLite)
   * // SELECT t0."sku", t0."name", t0."price_cents" FROM "product" AS t0
   * }}}
   */
  def from[A](table: Table[A]): SqlQuery[A] =
    SqlQuery(table, Vector.empty, Vector.empty, Vector.empty, None, Vector.empty, None, None)

  /** The type name [[SqlQuery#explain]] prints for a parameter. */
  private[sql] def typeLabel(v: DbValue): String = v match {
    case DbValue.DbNull             => "Null"
    case _: DbValue.DbInt           => "Int"
    case _: DbValue.DbLong          => "Long"
    case _: DbValue.DbDouble        => "Double"
    case _: DbValue.DbFloat         => "Float"
    case _: DbValue.DbBoolean       => "Boolean"
    case _: DbValue.DbString        => "String"
    case _: DbValue.DbBigDecimal    => "BigDecimal"
    case _: DbValue.DbBytes         => "Bytes"
    case _: DbValue.DbShort         => "Short"
    case _: DbValue.DbByte          => "Byte"
    case _: DbValue.DbChar          => "Char"
    case _: DbValue.DbLocalDate     => "LocalDate"
    case _: DbValue.DbLocalDateTime => "LocalDateTime"
    case _: DbValue.DbLocalTime     => "LocalTime"
    case _: DbValue.DbInstant       => "Instant"
    case _: DbValue.DbDuration      => "Duration"
    case _: DbValue.DbUUID          => "UUID"
    case _: DbValue.DbArray         => "Array"
  }
}

/**
 * One join of a [[SqlQuery]]: the joined table, its alias, kind and condition.
 */
private[sql] final case class JoinNode(
  table: Table[_],
  alias: String,
  kind: JoinKind,
  on: Frag
)

/**
 * A sort key of a [[SqlQuery]]: a column of the `FROM` table and its direction,
 * for [[SqlQuery#orderByMany]].
 */
final case class OrderBy(column: String, direction: SortOrder)
