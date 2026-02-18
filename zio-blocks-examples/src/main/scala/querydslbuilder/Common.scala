package querydslbuilder

import zio.blocks.schema._

// ---------------------------------------------------------------------------
// Expr language
// ---------------------------------------------------------------------------

sealed trait Expr[S, A]

object Expr {

  // --- Core nodes (superset of SchemaExpr's nodes) ---
  final case class Column[S, A](optic: Optic[S, A]) extends Expr[S, A]
  final case class Lit[S, A](value: A, schema: Schema[A]) extends Expr[S, A]

  // Relational
  final case class Relational[S, A](left: Expr[S, A], right: Expr[S, A], op: RelOp) extends Expr[S, Boolean]

  // Logical
  final case class And[S](left: Expr[S, Boolean], right: Expr[S, Boolean]) extends Expr[S, Boolean]
  final case class Or[S](left: Expr[S, Boolean], right: Expr[S, Boolean]) extends Expr[S, Boolean]
  final case class Not[S](expr: Expr[S, Boolean]) extends Expr[S, Boolean]

  // Arithmetic
  final case class Arithmetic[S, A](left: Expr[S, A], right: Expr[S, A], op: ArithOp) extends Expr[S, A]

  // String
  final case class StringConcat[S](left: Expr[S, String], right: Expr[S, String]) extends Expr[S, String]
  final case class StringRegexMatch[S](regex: Expr[S, String], string: Expr[S, String]) extends Expr[S, Boolean]
  final case class StringLength[S](string: Expr[S, String]) extends Expr[S, Int]

  // --- SQL-specific extensions (no SchemaExpr equivalents) ---
  final case class In[S, A](expr: Expr[S, A], values: List[A]) extends Expr[S, Boolean]
  final case class Between[S, A](expr: Expr[S, A], low: A, high: A) extends Expr[S, Boolean]
  final case class IsNull[S, A](expr: Expr[S, A]) extends Expr[S, Boolean]
  final case class Like[S](expr: Expr[S, String], pattern: String) extends Expr[S, Boolean]

  // --- Factory methods ---
  def col[S, A](optic: Optic[S, A]): Expr[S, A] = Column(optic)
  def lit[S, A](value: A)(implicit schema: Schema[A]): Expr[S, A] = Lit(value, schema)

  // --- Translation from SchemaExpr (one-way, not embedding) ---
  def fromSchemaExpr[S, A](se: SchemaExpr[S, A]): Expr[S, A] = {
    val result: Expr[S, _] = se match {
      case SchemaExpr.Optic(optic)      => Column(optic)
      case SchemaExpr.Literal(value, s) => Lit(value, s)

      case SchemaExpr.Relational(l, r, op) =>
        val relOp = op match {
          case SchemaExpr.RelationalOperator.Equal              => RelOp.Equal
          case SchemaExpr.RelationalOperator.NotEqual           => RelOp.NotEqual
          case SchemaExpr.RelationalOperator.LessThan           => RelOp.LessThan
          case SchemaExpr.RelationalOperator.LessThanOrEqual    => RelOp.LessThanOrEqual
          case SchemaExpr.RelationalOperator.GreaterThan        => RelOp.GreaterThan
          case SchemaExpr.RelationalOperator.GreaterThanOrEqual => RelOp.GreaterThanOrEqual
        }
        Relational(fromSchemaExpr(l), fromSchemaExpr(r), relOp)

      case SchemaExpr.Logical(l, r, op) => op match {
        case SchemaExpr.LogicalOperator.And => And(fromSchemaExpr(l), fromSchemaExpr(r))
        case SchemaExpr.LogicalOperator.Or  => Or(fromSchemaExpr(l), fromSchemaExpr(r))
      }

      case SchemaExpr.Not(inner) => Not(fromSchemaExpr(inner))

      case SchemaExpr.Arithmetic(l, r, op, _) =>
        val arithOp = op match {
          case SchemaExpr.ArithmeticOperator.Add      => ArithOp.Add
          case SchemaExpr.ArithmeticOperator.Subtract => ArithOp.Subtract
          case SchemaExpr.ArithmeticOperator.Multiply => ArithOp.Multiply
        }
        Arithmetic(fromSchemaExpr(l), fromSchemaExpr(r), arithOp)

      case SchemaExpr.StringConcat(l, r)              => StringConcat(fromSchemaExpr(l), fromSchemaExpr(r))
      case SchemaExpr.StringRegexMatch(regex, string) => StringRegexMatch(fromSchemaExpr(regex), fromSchemaExpr(string))
      case SchemaExpr.StringLength(string)            => StringLength(fromSchemaExpr(string))
    }
    result.asInstanceOf[Expr[S, A]]
  }
}

// --- Operators ---

sealed trait RelOp
object RelOp {
  case object Equal              extends RelOp
  case object NotEqual           extends RelOp
  case object LessThan           extends RelOp
  case object LessThanOrEqual    extends RelOp
  case object GreaterThan        extends RelOp
  case object GreaterThanOrEqual extends RelOp
}

sealed trait ArithOp
object ArithOp {
  case object Add      extends ArithOp
  case object Subtract extends ArithOp
  case object Multiply extends ArithOp
}

// --- Builder types ---

case class Table[S](name: String)

sealed trait SortOrder
object SortOrder {
  case object Asc  extends SortOrder
  case object Desc extends SortOrder
}

case class Assignment(column: String, value: String)

case class SelectStmt[S](
  table: Table[S],
  columnList: List[String] = List("*"),
  whereExpr: Option[Expr[S, Boolean]] = None,
  orderByList: List[(String, SortOrder)] = Nil,
  limitCount: Option[Int] = None
) {
  def columns(optics: Optic[S, _]*): SelectStmt[S] =
    copy(columnList = optics.map(columnName).toList)
  def where(cond: Expr[S, Boolean]): SelectStmt[S] =
    copy(whereExpr = Some(cond))
  def where(cond: SchemaExpr[S, Boolean]): SelectStmt[S] =
    copy(whereExpr = Some(Expr.fromSchemaExpr(cond)))
  def orderBy(optic: Optic[S, _], order: SortOrder = SortOrder.Asc): SelectStmt[S] =
    copy(orderByList = orderByList :+ (columnName(optic), order))
  def limit(n: Int): SelectStmt[S] =
    copy(limitCount = Some(n))
}

case class UpdateStmt[S](
  table: Table[S],
  assignments: List[Assignment] = Nil,
  whereExpr: Option[Expr[S, Boolean]] = None
) {
  def set[A](optic: Optic[S, A], value: A): UpdateStmt[S] =
    copy(assignments = assignments :+ Assignment(columnName(optic), sqlLiteralUntyped(value)))
  def where(cond: Expr[S, Boolean]): UpdateStmt[S] =
    copy(whereExpr = Some(cond))
  def where(cond: SchemaExpr[S, Boolean]): UpdateStmt[S] =
    copy(whereExpr = Some(Expr.fromSchemaExpr(cond)))
}

case class InsertStmt[S](
  table: Table[S],
  assignments: List[Assignment] = Nil
) {
  def set[A](optic: Optic[S, A], value: A): InsertStmt[S] =
    copy(assignments = assignments :+ Assignment(columnName(optic), sqlLiteralUntyped(value)))
}

case class DeleteStmt[S](
  table: Table[S],
  whereExpr: Option[Expr[S, Boolean]] = None
) {
  def where(cond: Expr[S, Boolean]): DeleteStmt[S] =
    copy(whereExpr = Some(cond))
  def where(cond: SchemaExpr[S, Boolean]): DeleteStmt[S] =
    copy(whereExpr = Some(Expr.fromSchemaExpr(cond)))
}
