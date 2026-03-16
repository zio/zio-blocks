package zio.blocks.sql.query

import zio.blocks.schema._
import zio.blocks.sql._

case class DeleteBuilder[A](
  table: Table[A],
  whereOpt: Option[Frag] = None
) {

  def where(expr: SchemaExpr[A, Boolean]): DeleteBuilder[A] = {
    val frag = ExprToSql.toSql(expr, table)
    val combined = whereOpt match {
      case Some(w) => w ++ Frag.const(" AND ") ++ frag
      case None => Frag.const(" WHERE ") ++ frag
    }
    this.copy(whereOpt = Some(combined))
  }

  def toFrag: Frag = {
    var f = Frag.const(s"DELETE FROM ${table.name}")
    whereOpt.foreach { w => f = f ++ w }
    f
  }
}

object Delete {
  def from[A](using dialect: SqlDialect, schema: Schema[A]): DeleteBuilder[A] = {
    DeleteBuilder(Table.derived[A](dialect)(schema))
  }
}
