/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package zio.blocks.sql.query

import zio.blocks.sql.{SqlIdentifier, Table}

import scala.quoted.*

/**
 * Relation between two tables: `fromTable.fkColumn = toTable.pkColumn`.
 */
final case class Rel[From, To](
  fromTable: Table[From],
  fkColumn: String,
  toTable: Table[To],
  pkColumn: String
) {
  private val fkValidated = SqlIdentifier.validate("column", fkColumn)
  private val pkValidated = SqlIdentifier.validate("column", pkColumn)

  if (!fromTable.columns.contains(fkValidated))
    throw new IllegalArgumentException(
      s"FK column ${fromTable.name}.$fkColumn (validated as '$fkValidated') not found in table '${fromTable.name}' columns: ${fromTable.columns.mkString(", ")}"
    )
  if (!toTable.columns.contains(pkValidated))
    throw new IllegalArgumentException(
      s"PK column ${toTable.name}.$pkColumn (validated as '$pkValidated') not found in table '${toTable.name}' columns: ${toTable.columns.mkString(", ")}"
    )
}

object Rel {

  /** String-based constructor for reflective/dynamic tables. */
  def manyToOne[From, To](
    fromTable: Table[From],
    fkColumn: String,
    toTable: Table[To],
    pkColumn: String
  ): Rel[From, To] =
    Rel(fromTable, fkColumn, toTable, pkColumn)

  /** String-based constructor symmetric to `manyToOne`. */
  def oneToMany[From, To](
    fromTable: Table[From],
    fkColumn: String,
    toTable: Table[To],
    pkColumn: String
  ): Rel[From, To] =
    Rel(fromTable, fkColumn, toTable, pkColumn)

  transparent inline def manyToOne[From, To](
    inline fromTable: Table[From],
    inline fkSelector: From => Any,
    inline toTable: Table[To],
    inline pkSelector: To => Any
  ): Rel[From, To] =
    ${ RelMacros.manyToOneImpl[From, To]('fromTable, 'fkSelector, 'toTable, 'pkSelector) }

  transparent inline def oneToMany[From, To](
    inline fromTable: Table[From],
    inline fkSelector: From => Any,
    inline toTable: Table[To],
    inline pkSelector: To => Any
  ): Rel[From, To] =
    ${ RelMacros.oneToManyImpl[From, To]('fromTable, 'fkSelector, 'toTable, 'pkSelector) }
}
