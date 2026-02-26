package comptime

import zio.blocks.schema._
import zio.blocks.schema.comptime.Allows
import Allows.{Primitive, Record, `|`}
import Allows.{Map => AMap, Optional => AOptional}

// ---------------------------------------------------------------------------
// Realistic RDBMS example using Allows[A, S] compile-time shape constraints
//
// Demonstrates how a library can require that user-supplied types have a
// structure compatible with what a relational database can represent:
//   - Flat records of primitives, optional primitives, or primitive-valued maps
//   - Top-level variants (enum tables) whose cases are flat records
//
// Incompatible types (nested records, sequences of records, etc.) are
// rejected at the call site with a precise compile-time error message.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Domain types — vary from compatible to incompatible
// ---------------------------------------------------------------------------

// Compatible: flat record of primitives and optional primitives
case class UserRow(
  id: java.util.UUID,
  name: String,
  email: Option[String],
  age: Int,
  active: Boolean
)
object UserRow {
  implicit val schema: Schema[UserRow] = Schema.derived
}

// Compatible: flat record with a string-keyed map column (stored as JSON/JSONB)
case class ProductRow(
  id: java.util.UUID,
  name: String,
  price: BigDecimal,
  attributes: scala.collection.immutable.Map[String, String]
)
object ProductRow {
  implicit val schema: Schema[ProductRow] = Schema.derived
}

// Compatible: event table — a variant (sealed trait) of flat record cases
sealed trait DomainEvent
case class UserCreated(id: java.util.UUID, name: String, email: String)               extends DomainEvent
case class UserDeleted(id: java.util.UUID)                                            extends DomainEvent
case class OrderPlaced(id: java.util.UUID, userId: java.util.UUID, total: BigDecimal) extends DomainEvent
object DomainEvent {
  implicit val schema: Schema[DomainEvent] = Schema.derived
}

// Incompatible: contains a nested record (Address is not a primitive)
case class Address(street: String, city: String, zip: String)
object Address { implicit val schema: Schema[Address] = Schema.derived }

case class UserWithAddress(
  id: java.util.UUID,
  name: String,
  address: Address // ← incompatible: nested record
)
object UserWithAddress {
  implicit val schema: Schema[UserWithAddress] = Schema.derived
}

// ---------------------------------------------------------------------------
// Simulated RDBMS library API
//
// The Allows constraint is checked at the CALL SITE — the library author
// writes these signatures once. Users get a compile-time error if their type
// doesn't match, with a message pointing to the exact violating field.
// ---------------------------------------------------------------------------

object Rdbms {

  // A flat record row: primitives, optional primitives, or string-keyed maps
  type FlatRow = Primitive | AOptional[Primitive] | AMap[Primitive, Primitive]

  /** Generate a CREATE TABLE DDL statement for a flat record type. */
  def createTable[A](implicit
    schema: Schema[A],
    ev: Allows[A, Record[FlatRow]]
  ): String = {
    val fields = schema.reflect.asRecord.get.fields
    val cols   = fields.map { f =>
      val tpe = sqlType(f.value)
      s"  ${f.name} $tpe"
    }
    s"CREATE TABLE ${tableName(schema)} (\n${cols.mkString(",\n")}\n)"
  }

  /** Generate an INSERT statement for a single flat record row. */
  def insert[A](value: A)(implicit
    schema: Schema[A],
    ev: Allows[A, Record[FlatRow]]
  ): String = {
    val dv = schema.toDynamicValue(value)
    dv match {
      case DynamicValue.Record(fields) =>
        val cols = fields.map(_._1).mkString(", ")
        val vals = fields.map { case (_, v) => sqlLiteralDv(v) }.mkString(", ")
        s"INSERT INTO ${tableName(schema)} ($cols) VALUES ($vals)"
      case _ => s"INSERT INTO ${tableName(schema)} VALUES (?)"
    }
  }

  /**
   * Insert an event into an event-sourcing table.
   *
   * The type must be a sealed trait / enum whose cases are flat records. No
   * explicit Variant node is needed — sealed traits are auto-unwrapped by the
   * macro.
   */
  def insertEvent[A](event: A)(implicit
    schema: Schema[A],
    ev: Allows[A, Record[FlatRow]]
  ): String = {
    val typeName = schema.toDynamicValue(event) match {
      case DynamicValue.Variant(caseName, _) => caseName
      case _                                 => schema.reflect.typeId.name
    }
    val payload = schema.toDynamicValue(event).toJson.toString
    s"INSERT INTO events (type, payload) VALUES ('$typeName', '$payload')"
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private def tableName[A](schema: Schema[A]): String =
    schema.reflect.modifiers.collectFirst {
      case Modifier.config(k, v) if k == "sql.table_name" => v
    }.getOrElse(schema.reflect.typeId.name.toLowerCase + "s")

  private def sqlLiteralDv(dv: DynamicValue): String = dv match {
    case DynamicValue.Primitive(PrimitiveValue.String(s))  => s"'${s.replace("'", "''")}'"
    case DynamicValue.Primitive(PrimitiveValue.Boolean(b)) => if (b) "TRUE" else "FALSE"
    case DynamicValue.Primitive(v)                         => v.toString
    case DynamicValue.Null                                 => "NULL"
    case other                                             => s"'${other.toString.replace("'", "''")}'"
  }

  private def sqlType(reflect: Reflect.Bound[_]): String = reflect match {
    case p: Reflect.Primitive[_, _] =>
      p.primitiveType match {
        case PrimitiveType.Int(_)           => "INTEGER"
        case PrimitiveType.Long(_)          => "BIGINT"
        case PrimitiveType.String(_)        => "TEXT"
        case PrimitiveType.Boolean(_)       => "BOOLEAN"
        case PrimitiveType.Double(_)        => "DOUBLE PRECISION"
        case PrimitiveType.Float(_)         => "REAL"
        case PrimitiveType.BigDecimal(_)    => "NUMERIC"
        case PrimitiveType.UUID(_)          => "UUID"
        case PrimitiveType.Instant(_)       => "TIMESTAMPTZ"
        case PrimitiveType.LocalDate(_)     => "DATE"
        case PrimitiveType.LocalDateTime(_) => "TIMESTAMP"
        case _                              => "TEXT"
      }
    case _: Reflect.Map[_, _, _, _]   => "JSONB"
    case _: Reflect.Sequence[_, _, _] => "TEXT[]"
    case _                            => "TEXT"
  }
}

// ---------------------------------------------------------------------------
// Demonstration — these all compile
// ---------------------------------------------------------------------------

object RdbmsDemo {

  // Flat rows compile fine
  val createUser: String    = Rdbms.createTable[UserRow]
  val createProduct: String = Rdbms.createTable[ProductRow]
  val insertUser: String    = Rdbms.insert(UserRow(new java.util.UUID(0, 0), "Alice", Some("a@b.com"), 30, true))
  val insertEvent: String   = Rdbms.insertEvent[DomainEvent](UserCreated(new java.util.UUID(0, 0), "Alice", "a@b.com"))

  // The following would NOT compile — uncomment to see the error:
  //
  // val bad = Rdbms.createTable[UserWithAddress]
  //   [error] Schema shape violation at UserWithAddress.address: found Record(Address), required
  //           Primitive | Optional[Primitive] | Map[Primitive, Primitive]
}
