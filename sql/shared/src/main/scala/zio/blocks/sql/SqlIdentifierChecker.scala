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

package zio.blocks.sql

import scala.collection.mutable

/**
 * Pure identifier checker for SQL literal parts produced by the `sql`
 * interpolator.
 *
 * Tokenizes literal parts respecting single-quoted string literals,
 * double-quoted identifiers and interpolation holes, subtracts the SQL
 * keyword/function allowlist, tracks aliases introduced via `AS alias`, and
 * reports unknown identifiers with Levenshtein suggestions.
 */
object SqlIdentifierChecker {

  final case class Diagnostic(message: String, position: Int, suggestion: Option[String])

  /**
   * Default SQL allowlist — keywords, types, functions and pseudo-identifiers
   * that are never flagged as unknown. Comparison is case-insensitive.
   */
  val DefaultAllowlist: Set[String] = Set(
    // DML / query structure
    "select",
    "from",
    "where",
    "join",
    "inner",
    "left",
    "right",
    "full",
    "outer",
    "cross",
    "union",
    "all",
    "on",
    "as",
    "group",
    "by",
    "having",
    "order",
    "limit",
    "offset",
    "and",
    "or",
    "not",
    "in",
    "exists",
    "distinct",
    "between",
    "like",
    "ilike",
    "is",
    "null",
    "nulls",
    "first",
    "last",
    "asc",
    "desc",
    "case",
    "when",
    "then",
    "else",
    "end",
    "true",
    "false",
    "cast",
    "interval",
    "over",
    "partition",
    "window",
    "rows",
    "range",
    "preceding",
    "following",
    "current",
    "row",
    "unbounded",
    "within",
    "filter",
    "with",
    "recursive",
    "values",
    "insert",
    "into",
    "update",
    "set",
    "delete",
    "create",
    "table",
    "if",
    "not_exists",
    "exists_word",
    "drop",
    "alter",
    "add",
    "column",
    "primary",
    "key",
    "foreign",
    "references",
    "unique",
    "check",
    "default",
    "constraint",
    "index",
    "using",
    "explain",
    "analyze",
    "vacuum",
    "begin",
    "commit",
    "rollback",
    "transaction",
    "savepoint",
    "release",
    "grant",
    "revoke",
    // Aggregates / functions
    "count",
    "sum",
    "avg",
    "min",
    "max",
    "coalesce",
    "nullif",
    "greatest",
    "least",
    "length",
    "lower",
    "upper",
    "trim",
    "ltrim",
    "rtrim",
    "substring",
    "substr",
    "replace",
    "concat",
    "concat_ws",
    "coalesce",
    "now",
    "current_timestamp",
    "current_date",
    "current_time",
    "extract",
    "date_part",
    "age",
    "to_char",
    "to_date",
    "to_timestamp",
    "to_number",
    "abs",
    "ceil",
    "ceiling",
    "floor",
    "round",
    "trunc",
    "truncate",
    "mod",
    "power",
    "sqrt",
    "exp",
    "ln",
    "log",
    "pi",
    "random",
    "generate_series",
    "row_number",
    "rank",
    "dense_rank",
    "lead",
    "lag",
    "first_value",
    "last_value",
    "nth_value",
    "ntile",
    "percent_rank",
    "cume_dist",
    // Types / operators present as words
    "int",
    "integer",
    "bigint",
    "smallint",
    "serial",
    "bigserial",
    "numeric",
    "decimal",
    "real",
    "double",
    "precision",
    "float",
    "boolean",
    "bool",
    "text",
    "varchar",
    "char",
    "character",
    "varying",
    "bytea",
    "json",
    "jsonb",
    "uuid",
    "date",
    "time",
    "timestamp",
    "timestamptz",
    "interval_word",
    "array",
    "any",
    "some",
    "returning",
    "conflict",
    "do",
    "nothing",
    "update_word",
    "except",
    "intersect",
    "for",
    "share",
    "no",
    "key_word",
    "of",
    "only",
    "lateral",
    "natural",
    "fetch",
    "next",
    "exclusive",
    "deferrable",
    "initially",
    "deferred",
    "immediate",
    "match",
    "partial",
    "full_word",
    "simple",
    "action",
    "cascade",
    "restrict",
    "set_null",
    "set_default",
    "using_word"
  ).map(_.toLowerCase)

  private val HolePlaceholder = "__HOLE__"

  private final case class Token(text: String, position: Int)

  def validate(parts: Seq[String], knownTables: Set[String], knownColumns: Set[String]): List[Diagnostic] =
    validate(parts, knownTables, knownColumns, DefaultAllowlist)

  def validate(
    parts: Seq[String],
    knownTables: Set[String],
    knownColumns: Set[String],
    allowlist: Set[String]
  ): List[Diagnostic] = {
    val knownTablesLower  = knownTables.map(_.toLowerCase)
    val knownColumnsLower = knownColumns.map(_.toLowerCase)
    val allowlistLower    = allowlist.map(_.toLowerCase)
    // For suggestion: keep map from lower to original (first occurrence)
    val suggestionPool: Map[String, String] = {
      val m                            = mutable.Map.empty[String, String]
      def putAll(s: Set[String]): Unit = s.foreach { v =>
        val l = v.toLowerCase
        if (!m.contains(l)) m.put(l, v)
      }
      putAll(knownTables)
      putAll(knownColumns)
      putAll(allowlist)
      m.toMap
    }
    val combined = parts.mkString(s" $HolePlaceholder ")
    val tokens   = tokenize(combined)
    validateTokens(tokens, knownTablesLower, knownColumnsLower, allowlistLower, suggestionPool)
  }

  def validate(parts: Seq[String], tables: Seq[Table[?]]): List[Diagnostic] = {
    val knownTables  = tables.map(_.name).toSet
    val knownColumns = tables.flatMap(_.columns).toSet
    validate(parts, knownTables, knownColumns, DefaultAllowlist)
  }

  def validate(parts: Seq[String], tables: Seq[Table[?]], allowlist: Set[String]): List[Diagnostic] = {
    val knownTables  = tables.map(_.name).toSet
    val knownColumns = tables.flatMap(_.columns).toSet
    validate(parts, knownTables, knownColumns, allowlist)
  }

  // Alternate signature requested: Set-based knownNames
  def validate(parts: Seq[String], knownNames: Set[String], allowlist: Set[String], dummy: Unit): List[Diagnostic] = {
    val _ = dummy
    validate(parts, knownNames, Set.empty, allowlist)
  }

  private def validateTokens(
    tokens: List[Token],
    knownTablesLower: Set[String],
    knownColumnsLower: Set[String],
    allowlistLower: Set[String],
    suggestionPool: Map[String, String]
  ): List[Diagnostic] = {
    // Collect aliases globally first so that alias usage in SELECT before FROM is not falsely flagged.
    // This is more permissive than strict "thereafter" but matches real SQL scoping.
    val aliases: Set[String] = {
      val s = mutable.Set.empty[String]
      var j = 1
      while (j < tokens.length) {
        if (tokens(j - 1).text.equalsIgnoreCase("as") && isIdentifier(tokens(j).text))
          s += tokens(j).text.toLowerCase
        j += 1
      }
      s.toSet
    }
    // Include aliases in suggestion pool for did-you-mean
    val extendedPool = suggestionPool ++ aliases.map(a => a -> a)

    val diagnostics = mutable.ListBuffer.empty[Diagnostic]
    var idx         = 0
    while (idx < tokens.length) {
      val token = tokens(idx)
      val lower = token.text.toLowerCase

      val isAliasDecl =
        idx > 0 && tokens(idx - 1).text.equalsIgnoreCase("as") && isIdentifier(token.text)

      if (isAliasDecl) {
        // declaration itself is not an error
      } else if (allowlistLower.contains(lower)) {
        // known keyword — skip
      } else if (knownTablesLower.contains(lower) || knownColumnsLower.contains(lower) || aliases.contains(lower)) {
        // known table/column/alias
      } else {
        if (lower != HolePlaceholder.toLowerCase && isIdentifier(token.text)) {
          val suggestion = findSuggestion(lower, extendedPool)
          val msg        = suggestion match {
            case Some(s) => s"Unknown identifier '${token.text}' at position ${token.position}; did you mean '$s'?"
            case None    => s"Unknown identifier '${token.text}' at position ${token.position}"
          }
          diagnostics += Diagnostic(msg, token.position, suggestion)
        }
      }
      idx += 1
    }
    diagnostics.toList
  }

  private def isIdentifier(s: String): Boolean =
    s.nonEmpty && (s.charAt(0).isLetter || s.charAt(0) == '_') && s.forall(c => c.isLetterOrDigit || c == '_')

  private def tokenize(combined: String): List[Token] = {
    val tokens = mutable.ListBuffer.empty[Token]
    var i      = 0
    val len    = combined.length
    while (i < len) {
      val ch = combined.charAt(i)
      if (ch == '\'') {
        // single-quoted string literal — skip entirely, handling '' escape
        var j      = i + 1
        var closed = false
        while (j < len && !closed) {
          if (combined.charAt(j) == '\'') {
            if (j + 1 < len && combined.charAt(j + 1) == '\'') j += 2
            else {
              j += 1
              closed = true
            }
          } else j += 1
        }
        if (closed) i = j else i = len
      } else if (ch == '"') {
        // double-quoted identifier — extract content (handle "" escape)
        val start  = i
        val sb     = new StringBuilder
        var j      = i + 1
        var closed = false
        while (j < len && !closed) {
          if (combined.charAt(j) == '"') {
            if (j + 1 < len && combined.charAt(j + 1) == '"') {
              sb.append('"')
              j += 2
            } else {
              j += 1
              closed = true
            }
          } else {
            sb.append(combined.charAt(j))
            j += 1
          }
        }
        if (closed) {
          val ident = sb.toString
          // Split by dot inside quoted? shouldn't happen: "T"."c" produces two tokens separately
          // Here ident is inside one pair of quotes; if it contains dot, treat before/after as separate?
          // Actually dot outside quotes separates identifiers. Inside quotes dot is not separator.
          // So keep as one token if non-empty and identifier-like
          if (ident.nonEmpty) tokens += Token(ident, start)
          i = j
        } else {
          i = len
        }
      } else if (isIdentifierStart(ch)) {
        // check for hole placeholder
        if (combined.startsWith(HolePlaceholder, i)) {
          // ensure word boundaries around placeholder (we inserted spaces, so boundaries hold)
          // verify not part of larger identifier: already ensured because we inserted spaces
          i += HolePlaceholder.length
        } else {
          val start = i
          val sb    = new StringBuilder
          sb.append(ch)
          i += 1
          while (i < len && isIdentifierPart(combined.charAt(i))) {
            sb.append(combined.charAt(i))
            i += 1
          }
          val ident = sb.toString
          // If this ident equals hole placeholder with case variation, skip
          if (!ident.equalsIgnoreCase(HolePlaceholder))
            tokens += Token(ident, start)
        }
      } else {
        // handle hole placeholder even when not at identifier start (with underscores it is)
        // but also need to skip it if appears after non-identifier char
        if (combined.startsWith(HolePlaceholder, i)) i += HolePlaceholder.length
        else i += 1
      }
    }
    tokens.toList
  }

  private def isIdentifierStart(c: Char): Boolean = c.isLetter || c == '_'
  private def isIdentifierPart(c: Char): Boolean  = c.isLetterOrDigit || c == '_'

  private def findSuggestion(lower: String, pool: Map[String, String]): Option[String] = {
    var best: Option[String] = None
    var bestDist             = Int.MaxValue
    pool.foreach { case (candLower, candOrig) =>
      val d = levenshtein(lower, candLower)
      if (d <= 2 && d < bestDist) {
        bestDist = d
        best = Some(candOrig)
      } else if (d <= 2 && d == bestDist) {
        // tie-break lexicographically smaller original lower
        best.foreach { cur =>
          if (candLower < cur.toLowerCase) best = Some(candOrig)
        }
      }
    }
    best
  }

  // Classic DP Levenshtein distance, case-sensitive on already lowercased strings
  private[sql] def levenshtein(a: String, b: String): Int = {
    val n = a.length
    val m = b.length
    if (n == 0) return m
    if (m == 0) return n
    var prev = Array.tabulate(m + 1)(j => j)
    var curr = new Array[Int](m + 1)
    var i    = 1
    while (i <= n) {
      curr(0) = i
      var j = 1
      while (j <= m) {
        val cost = if (a.charAt(i - 1) == b.charAt(j - 1)) 0 else 1
        curr(j) = math.min(math.min(prev(j) + 1, curr(j - 1) + 1), prev(j - 1) + cost)
        j += 1
      }
      val tmp = prev
      prev = curr
      curr = tmp
      i += 1
    }
    prev(m)
  }
}
