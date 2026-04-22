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

package comptime

import zio.blocks.schema._
import zio.blocks.schema.comptime.Allows
import Allows.{Primitive, Record, `|`}
import Allows.{Optional => AOptional}
import util.ShowExpr.show

// ---------------------------------------------------------------------------
// CSV serializer example using Allows[A, S] compile-time shape constraints
//
// A CSV row is a flat record: every field must be a primitive scalar or an
// optional primitive (for nullable columns). Nested records, sequences, and
// maps are all rejected at compile time.
// ---------------------------------------------------------------------------

// Compatible: flat record of primitives and optional primitives
case class Employee(name: String, department: String, salary: BigDecimal, active: Boolean)
object Employee { implicit val schema: Schema[Employee] = Schema.derived }

case class SensorReading(sensorId: String, timestamp: Long, value: Double, unit: Option[String])
object SensorReading { implicit val schema: Schema[SensorReading] = Schema.derived }

object CsvSerializer {

  type FlatRow = Primitive | AOptional[Primitive]

  /** Serialize a sequence of flat records to CSV format. */
  def toCsv[A](rows: Seq[A])(implicit schema: Schema[A], ev: Allows[A, Record[FlatRow]]): String = {
    val reflect = schema.reflect.asRecord.get
    val header  = reflect.fields.map(_.name).mkString(",")
    val lines   = rows.map { row =>
      val dv = schema.toDynamicValue(row)
      dv match {
        case DynamicValue.Record(fields) =>
          fields.map { case (_, v) => csvEscape(dvToString(v)) }.mkString(",")
        case _ => ""
      }
    }
    (header +: lines).mkString("\n")
  }

  private def dvToString(dv: DynamicValue): String = dv match {
    case DynamicValue.Primitive(PrimitiveValue.String(s))     => s
    case DynamicValue.Primitive(PrimitiveValue.Boolean(b))    => b.toString
    case DynamicValue.Primitive(PrimitiveValue.Int(n))        => n.toString
    case DynamicValue.Primitive(PrimitiveValue.Long(n))       => n.toString
    case DynamicValue.Primitive(PrimitiveValue.Double(n))     => n.toString
    case DynamicValue.Primitive(PrimitiveValue.Float(n))      => n.toString
    case DynamicValue.Primitive(PrimitiveValue.BigDecimal(n)) => n.toString
    case DynamicValue.Primitive(v)                            => v.toString
    case DynamicValue.Null                                    => ""
    case DynamicValue.Variant(tag, inner) if tag == "Some"    => dvToString(inner)
    case DynamicValue.Variant(tag, _) if tag == "None"        => ""
    case DynamicValue.Record(fields)                          =>
      fields.headOption.map { case (_, v) => dvToString(v) }.getOrElse("")
    case other => other.toString
  }

  private def csvEscape(s: String): String =
    if (s.contains(",") || s.contains("\"") || s.contains("\n"))
      "\"" + s.replace("\"", "\"\"") + "\""
    else s
}

// ---------------------------------------------------------------------------
// Demonstration
// ---------------------------------------------------------------------------

object AllowsCsvExample extends App {

  // Flat records of primitives — compiles fine
  val employees = Seq(
    Employee("Alice", "Engineering", BigDecimal("120000.00"), true),
    Employee("Bob", "Marketing", BigDecimal("95000.50"), true),
    Employee("Carol", "Engineering", BigDecimal("115000.00"), false)
  )

  // CSV output for a flat record of primitives
  show(CsvSerializer.toCsv(employees))

  // Flat record with optional fields — also compiles
  val readings = Seq(
    SensorReading("temp-01", 1709712000L, 23.5, Some("celsius")),
    SensorReading("temp-02", 1709712060L, 72.1, None)
  )

  // Optional fields become empty CSV cells when None
  show(CsvSerializer.toCsv(readings))

  // The following would NOT compile — uncomment to see the error:
  //
  // case class Nested(name: String, address: Address)
  // object Nested { implicit val schema: Schema[Nested] = Schema.derived }
  // CsvSerializer.toCsv(Seq(Nested("Alice", Address("1 Main St", "NY", "10001"))))
  //   [error] Schema shape violation at Nested.address: found Record(Address),
  //           required Primitive | Optional[Primitive]
}
