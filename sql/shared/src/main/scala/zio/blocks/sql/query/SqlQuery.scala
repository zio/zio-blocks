/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package zio.blocks.sql.query

import zio.blocks.sql.{Frag, SqlDialect, SqlIdentifier, Table}

/**
 * Immutable query IR starting from a source table.
 *
 * Alias allocation is deterministic: t0 = source, t1..tN in join order
 * (self-join safe — same Table joined twice gets distinct aliases). Rendering
 * is performed by [[QueryRenderer]] and produces a [[Frag]] via `Frag.++`
 * composition only, using quoted identifiers and dialect placeholders.
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

  def innerJoin[From, To](rel: Rel[From, To]): SqlQuery[A] =
    addJoin(rel, JoinKind.Inner)

  def leftJoin[From, To](rel: Rel[From, To]): SqlQuery[A] =
    addJoin(rel, JoinKind.Left)

  def join[From, To](rel: Rel[From, To], kind: JoinKind): SqlQuery[A] =
    addJoin(rel, kind)

  /** Alias for `innerJoin` — satisfies the `SqlQuery.join(rel)` contract. */
  def join[From, To](rel: Rel[From, To]): SqlQuery[A] =
    addJoin(rel, JoinKind.Inner)

  /** Alias for `leftJoin` — satisfies the `SqlQuery.joinLeft(rel)` contract. */
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

  def filter(frag: Frag): SqlQuery[A] =
    copy(filters = filters :+ frag)

  def where(frag: Frag): SqlQuery[A] = filter(frag)

  def groupBy(cols: String*): SqlQuery[A] = {
    cols.foreach(c => SqlIdentifier.validate("column", c))
    copy(groupBy = cols.toVector)
  }

  def having(frag: Frag): SqlQuery[A] =
    copy(having = Some(frag))

  def orderBy(col: String, dir: SortOrder = SortOrder.Asc): SqlQuery[A] = {
    SqlIdentifier.validate("column", col)
    copy(orderBy = orderBy :+ OrderBy(col, dir))
  }

  def orderByMany(cols: OrderBy*): SqlQuery[A] =
    copy(orderBy = orderBy ++ cols.toVector)

  def limit(n: Int): SqlQuery[A] = {
    require(n >= 0, "limit must be >= 0")
    copy(limit = Some(n))
  }

  def offset(n: Int): SqlQuery[A] = {
    require(n >= 0, "offset must be >= 0")
    copy(offset = Some(n))
  }

  def toFrag(dialect: SqlDialect): Frag = QueryRenderer.render(this, dialect)

  def sql(dialect: SqlDialect): String = toFrag(dialect).sql(dialect)

  private def aliasOf(table: Table[_]): Option[String] =
    if (table.name == source.name) Some("t0")
    else joins.find(_.table.name == table.name).map(_.alias)
}

object SqlQuery {
  def from[A](table: Table[A]): SqlQuery[A] =
    SqlQuery(table, Vector.empty, Vector.empty, Vector.empty, None, Vector.empty, None, None)
}

private[query] final case class JoinNode(
  table: Table[_],
  alias: String,
  kind: JoinKind,
  on: Frag
)

final case class OrderBy(column: String, direction: SortOrder)
