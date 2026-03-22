package zio.blocks.sql.query

import zio.blocks.schema._
import zio.blocks.sql._

case class SortOrder[S](expr: SchemaExpr[S, ?], isAsc: Boolean) {
  def toFrag(using t: Table[S]): Frag =
    ExprToSql.toSql(expr, t) ++ Frag.const(if (isAsc) " ASC" else " DESC")
}

extension [S, A](expr: SchemaExpr[S, A]) {
  def asc: SortOrder[S] = SortOrder(expr, true)
  def desc: SortOrder[S] = SortOrder(expr, false)
}

extension [S, A](optic: Optic[S, A]) {
  def asc: SortOrder[S] = SchemaExpr.Optic(optic).asc
  def desc: SortOrder[S] = SchemaExpr.Optic(optic).desc
}

case class SelectBuilder[A](
  table: Table[A],
  whereOpt: Option[Frag] = None,
  orderByOpt: Option[Frag] = None,
  limitOpt: Option[Int] = None
) {
  
  def where(expr: SchemaExpr[A, Boolean]): SelectBuilder[A] = {
    val frag = ExprToSql.toSql(expr, table)
    val combined = whereOpt match {
      case Some(w) => w ++ Frag.const(" AND ") ++ frag
      case None => Frag.const(" WHERE ") ++ frag
    }
    this.copy(whereOpt = Some(combined))
  }

  def orderBy(exprs: SortOrder[A]*): SelectBuilder[A] = {
    given Table[A] = table
    val frag = exprs.map(_.toFrag).reduce(_ ++ Frag.const(", ") ++ _)
    val combined = orderByOpt match {
      case Some(o) => o ++ Frag.const(", ") ++ frag
      case None => Frag.const(" ORDER BY ") ++ frag
    }
    this.copy(orderByOpt = Some(combined))
  }

  def limit(n: Int): SelectBuilder[A] =
    this.copy(limitOpt = Some(n))

  def toFrag: Frag = {
    val columns = table.columns.mkString(", ")
    var f = Frag.const(s"SELECT $columns FROM ${table.name}")
    whereOpt.foreach { w => f = f ++ w }
    orderByOpt.foreach { o => f = f ++ o }
    limitOpt.foreach { l => f = f ++ Frag.const(s" LIMIT $l") }
    f
  }
}

object Select {
  def from[A](using dialect: SqlDialect, schema: Schema[A]): SelectBuilder[A] = {
    SelectBuilder(Table.derived[A](dialect)(schema))
  }
}
