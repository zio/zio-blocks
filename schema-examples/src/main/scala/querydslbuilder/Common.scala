package querydslbuilder

import zio.blocks.schema._

// ---------------------------------------------------------------------------
// Shared domain type used across Steps 1–3 and CompleteFluentBuilder
// ---------------------------------------------------------------------------

case class Product(
  name: String,
  price: Double,
  category: String,
  inStock: Boolean,
  rating: Int
)

object Product extends CompanionOptics[Product] {
  implicit val schema: Schema[Product] = Schema.derived

  val table: Table[Product] = Table.derived

  val name: Lens[Product, String]     = optic(_.name)
  val price: Lens[Product, Double]    = optic(_.price)
  val category: Lens[Product, String] = optic(_.category)
  val inStock: Lens[Product, Boolean] = optic(_.inStock)
  val rating: Lens[Product, Int]      = optic(_.rating)
}

// OrderItem uses Modifier.config to override the auto-derived table name
case class OrderItem(
  orderId: Int,
  productId: Int,
  quantity: Int,
  unitPrice: Double
)

object OrderItem extends CompanionOptics[OrderItem] {
  implicit val schema: Schema[OrderItem] = Schema.derived
    .modifier(Modifier.config("sql.table_name", "order_items"))

  val table: Table[OrderItem] = Table.derived

  val orderId: Lens[OrderItem, Int]      = optic(_.orderId)
  val productId: Lens[OrderItem, Int]    = optic(_.productId)
  val quantity: Lens[OrderItem, Int]     = optic(_.quantity)
  val unitPrice: Lens[OrderItem, Double] = optic(_.unitPrice)
}

// ---------------------------------------------------------------------------
// Expr language
// ---------------------------------------------------------------------------

sealed trait Expr[S, A]

object Expr {

  // --- Core nodes (superset of SchemaExpr's nodes) ---
  final case class Column[S, A](path: DynamicOptic) extends Expr[S, A]
  final case class Lit[S, A](value: DynamicValue)   extends Expr[S, A]

  // Relational
  final case class Relational[S, A](left: Expr[S, A], right: Expr[S, A], op: RelOp) extends Expr[S, Boolean]

  // Logical
  final case class And[S](left: Expr[S, Boolean], right: Expr[S, Boolean]) extends Expr[S, Boolean]
  final case class Or[S](left: Expr[S, Boolean], right: Expr[S, Boolean])  extends Expr[S, Boolean]
  final case class Not[S](expr: Expr[S, Boolean])                          extends Expr[S, Boolean]

  // Arithmetic
  final case class Arithmetic[S, A](left: Expr[S, A], right: Expr[S, A], op: ArithOp) extends Expr[S, A]

  // String
  final case class StringConcat[S](left: Expr[S, String], right: Expr[S, String])       extends Expr[S, String]
  final case class StringRegexMatch[S](regex: Expr[S, String], string: Expr[S, String]) extends Expr[S, Boolean]
  final case class StringLength[S](string: Expr[S, String])                             extends Expr[S, Int]

  // --- SQL-specific extensions (no SchemaExpr equivalents) ---
  final case class In[S, A](expr: Expr[S, A], values: List[A], schema: Schema[A])      extends Expr[S, Boolean]
  final case class Between[S, A](expr: Expr[S, A], low: A, high: A, schema: Schema[A]) extends Expr[S, Boolean]
  final case class IsNull[S, A](expr: Expr[S, A])                                      extends Expr[S, Boolean]
  final case class Like[S](expr: Expr[S, String], pattern: String)                     extends Expr[S, Boolean]

  // --- Factory methods ---
  def col[S, A](optic: Optic[S, A]): Expr[S, A]                   = Column(optic.toDynamic)
  def lit[S, A](value: A)(implicit schema: Schema[A]): Expr[S, A] = Lit(schema.toDynamicValue(value))

  // --- Translation from SchemaExpr (one-way, not embedding) ---
  def fromSchemaExpr[S, A](se: SchemaExpr[S, A]): Expr[S, A] = {
    val result = fromDynamic[S](se.dynamic)
    result.asInstanceOf[Expr[S, A]]
  }

  private def fromDynamic[S](dse: DynamicSchemaExpr): Expr[S, ?] = dse match {
    case DynamicSchemaExpr.Select(path)   => Column[S, Any](path)
    case DynamicSchemaExpr.Literal(value) => Lit[S, Any](value)

    case DynamicSchemaExpr.Relational(l, r, op) =>
      val relOp = op match {
        case DynamicSchemaExpr.RelationalOperator.Equal              => RelOp.Equal
        case DynamicSchemaExpr.RelationalOperator.NotEqual           => RelOp.NotEqual
        case DynamicSchemaExpr.RelationalOperator.LessThan           => RelOp.LessThan
        case DynamicSchemaExpr.RelationalOperator.LessThanOrEqual    => RelOp.LessThanOrEqual
        case DynamicSchemaExpr.RelationalOperator.GreaterThan        => RelOp.GreaterThan
        case DynamicSchemaExpr.RelationalOperator.GreaterThanOrEqual => RelOp.GreaterThanOrEqual
      }
      Relational(fromDynamic[S](l).asInstanceOf[Expr[S, Any]], fromDynamic[S](r).asInstanceOf[Expr[S, Any]], relOp)

    case DynamicSchemaExpr.Logical(l, r, op) =>
      op match {
        case DynamicSchemaExpr.LogicalOperator.And =>
          And(fromDynamic[S](l).asInstanceOf[Expr[S, Boolean]], fromDynamic[S](r).asInstanceOf[Expr[S, Boolean]])
        case DynamicSchemaExpr.LogicalOperator.Or =>
          Or(fromDynamic[S](l).asInstanceOf[Expr[S, Boolean]], fromDynamic[S](r).asInstanceOf[Expr[S, Boolean]])
      }

    case DynamicSchemaExpr.Not(inner) => Not(fromDynamic[S](inner).asInstanceOf[Expr[S, Boolean]])

    case DynamicSchemaExpr.Arithmetic(l, r, op, _) =>
      val arithOp = op match {
        case DynamicSchemaExpr.ArithmeticOperator.Add      => ArithOp.Add
        case DynamicSchemaExpr.ArithmeticOperator.Subtract => ArithOp.Subtract
        case DynamicSchemaExpr.ArithmeticOperator.Multiply => ArithOp.Multiply
        case _                                             => ArithOp.Add
      }
      Arithmetic(fromDynamic[S](l).asInstanceOf[Expr[S, Any]], fromDynamic[S](r).asInstanceOf[Expr[S, Any]], arithOp)

    case DynamicSchemaExpr.StringConcat(l, r) =>
      StringConcat(fromDynamic[S](l).asInstanceOf[Expr[S, String]], fromDynamic[S](r).asInstanceOf[Expr[S, String]])
    case DynamicSchemaExpr.StringRegexMatch(regex, string) =>
      StringRegexMatch(
        fromDynamic[S](regex).asInstanceOf[Expr[S, String]],
        fromDynamic[S](string).asInstanceOf[Expr[S, String]]
      )
    case DynamicSchemaExpr.StringLength(string) => StringLength(fromDynamic[S](string).asInstanceOf[Expr[S, String]])
    case _                                      => Lit[S, Any](DynamicValue.Null)
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

object Table {
  def derived[S](implicit schema: Schema[S]): Table[S] = Table(tableName(schema))
}

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
  def set[A](optic: Optic[S, A], value: A)(implicit schema: Schema[A]): UpdateStmt[S] =
    copy(assignments = assignments :+ Assignment(columnName(optic), sqlLiteral(value, schema)))
  def where(cond: Expr[S, Boolean]): UpdateStmt[S] =
    copy(whereExpr = Some(cond))
  def where(cond: SchemaExpr[S, Boolean]): UpdateStmt[S] =
    copy(whereExpr = Some(Expr.fromSchemaExpr(cond)))
}

case class InsertStmt[S](
  table: Table[S],
  assignments: List[Assignment] = Nil
) {
  def set[A](optic: Optic[S, A], value: A)(implicit schema: Schema[A]): InsertStmt[S] =
    copy(assignments = assignments :+ Assignment(columnName(optic), sqlLiteral(value, schema)))
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
