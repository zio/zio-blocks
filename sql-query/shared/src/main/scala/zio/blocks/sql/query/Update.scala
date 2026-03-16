package zio.blocks.sql.query

import zio.blocks.schema._
import zio.blocks.sql._

case class SetClause[A](expr: SchemaExpr[A, ?], dbValue: DbValue) {
  def toFrag(using t: Table[A]): Frag = {
    val col = ExprToSql.toSql(expr, t)
    col ++ Frag.const(" = ") ++ Frag(IndexedSeq("", ""), IndexedSeq(dbValue))
  }
}

extension [S, A](expr: SchemaExpr[S, A]) {
  def set(value: A)(using schema: Schema[A]): SetClause[S] = {
    val dbValue = ExprToSql.toDbValue(schema.toDynamicValue(value))
    SetClause(expr, dbValue)
  }
}

extension [S, A](optic: Optic[S, A]) {
  def set(value: A)(using schema: Schema[A]): SetClause[S] =
    SchemaExpr.Optic(optic).set(value)
}

case class UpdateBuilder[A](
  table: Table[A],
  setClauses: IndexedSeq[SetClause[A]] = IndexedSeq.empty,
  whereOpt: Option[Frag] = None
) {

  def set(clauses: SetClause[A]*): UpdateBuilder[A] =
    this.copy(setClauses = setClauses ++ clauses)

  def where(expr: SchemaExpr[A, Boolean]): UpdateBuilder[A] = {
    val frag = ExprToSql.toSql(expr, table)
    val combined = whereOpt match {
      case Some(w) => w ++ Frag.const(" AND ") ++ frag
      case None => Frag.const(" WHERE ") ++ frag
    }
    this.copy(whereOpt = Some(combined))
  }

  def toFrag: Frag = {
    if (setClauses.isEmpty) throw new IllegalStateException("UPDATE requires at least one SET clause")
    
    given Table[A] = table
    val sets = setClauses.map(_.toFrag).reduce(_ ++ Frag.const(", ") ++ _)
    var f = Frag.const(s"UPDATE ${table.name} SET ") ++ sets
    whereOpt.foreach { w => f = f ++ w }
    f
  }
}

object Update {
  def table[A](using dialect: SqlDialect, schema: Schema[A]): UpdateBuilder[A] = {
    UpdateBuilder[A](Table.derived[A](dialect)(schema), IndexedSeq.empty[SetClause[A]], None)
  }
}
