/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package zio.blocks.sql.query

import zio.blocks.sql.{Frag, SqlDialect, SqlIdentifier, Table}

object QueryRenderer {

  def render[A](query: SqlQuery[A], @scala.annotation.unused dialect: SqlDialect): Frag = {
    // SELECT clause: t0."col", t1."col", ...
    val allTables: Vector[(Table[_], String)] =
      Vector((query.source, "t0")) ++ query.joins.map(j => (j.table, j.alias))

    val selectCols: Vector[String] = allTables.flatMap { case (tbl, alias) =>
      tbl.columns.map { col =>
        val c = SqlIdentifier.validate("column", col)
        s"""$alias."$c""""
      }
    }

    val tableName = SqlIdentifier.validate("table", query.source.name)

    // Build SELECT via Frag composition to avoid string concatenation outside Frag
    val selectColFrags = selectCols.map(Frag.literal)
    val selectCombined = selectColFrags.reduce(_ ++ Frag.literal(", ") ++ _)
    var frag: Frag     = Frag.literal("SELECT ") ++ selectCombined ++ Frag.literal(s""" FROM "$tableName" AS t0""")

    // JOINs
    query.joins.foreach { j =>
      val tName   = SqlIdentifier.validate("table", j.table.name)
      val kindStr = j.kind match {
        case JoinKind.Inner => "INNER JOIN"
        case JoinKind.Left  => "LEFT JOIN"
      }
      frag = frag ++ Frag.literal(s""" $kindStr "$tName" AS ${j.alias} ON """) ++ j.on
    }

    // WHERE (filters combined with AND)
    if (query.filters.nonEmpty) {
      val combined = query.filters.reduce((a, b) => a ++ Frag.literal(" AND ") ++ b)
      frag = frag ++ Frag.literal(" WHERE ") ++ combined
    }

    // GROUP BY — composed via Frag to avoid outside concatenation
    if (query.groupBy.nonEmpty) {
      val gbFrags = query.groupBy.map { col =>
        val c = SqlIdentifier.validate("column", col)
        Frag.literal(s"""t0."$c"""")
      }
      val gbCombined = gbFrags.reduce(_ ++ Frag.literal(", ") ++ _)
      frag = frag ++ Frag.literal(" GROUP BY ") ++ gbCombined
    }

    // HAVING
    query.having.foreach { h =>
      frag = frag ++ Frag.literal(" HAVING ") ++ h
    }

    // ORDER BY — composed via Frag
    if (query.orderBy.nonEmpty) {
      val obFrags = query.orderBy.map { o =>
        val c   = SqlIdentifier.validate("column", o.column)
        val dir = o.direction match {
          case SortOrder.Asc  => "ASC"
          case SortOrder.Desc => "DESC"
        }
        Frag.literal(s"""t0."$c" $dir""")
      }
      val obCombined = obFrags.reduce(_ ++ Frag.literal(", ") ++ _)
      frag = frag ++ Frag.literal(" ORDER BY ") ++ obCombined
    }

    // LIMIT / OFFSET (literals, not params)
    query.limit.foreach { n =>
      frag = frag ++ Frag.literal(s" LIMIT $n")
    }
    query.offset.foreach { n =>
      frag = frag ++ Frag.literal(s" OFFSET $n")
    }

    frag
  }
}
